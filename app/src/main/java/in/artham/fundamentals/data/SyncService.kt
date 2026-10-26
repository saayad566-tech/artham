package `in`.artham.fundamentals.data

import `in`.artham.fundamentals.core.Logx
import `in`.artham.fundamentals.data.net.Http
import `in`.artham.fundamentals.data.net.NseUniverse
import `in`.artham.fundamentals.data.net.YahooProvider
import `in`.artham.fundamentals.domain.PriceBar
import java.util.Calendar

/**
 * Pulls real market data into the local database.
 *
 * Two public endpoints, both called only when the user asks:
 *  - the exchange's published equity master, for the company universe and face values
 *  - a public quote endpoint for daily candles and annual/quarterly fundamentals
 *
 * Everything is written locally and then read from the local database, so the app keeps
 * working with the network off. Each company is committed on its own, which makes a long
 * sync resumable: stop it halfway and the companies already fetched are still there.
 */
class SyncService(
    private val companyDao: CompanyDao,
    private val syncDao: SyncDao,
    private val importDao: ImportDao,
    private val logDao: LogDao
) {

    data class Progress(
        val stage: String,
        val done: Int,
        val total: Int,
        val detail: String = "",
        val failed: Int = 0
    )

    data class Summary(val ok: Int, val failed: Int, val bars: Int, val withFundamentals: Int)

    data class CompanyResult(val code: String, val ok: Boolean, val message: String)

    private val annualTypes = listOf(
        "annualTotalRevenue", "annualNetIncome", "annualDilutedEPS", "annualBasicEPS",
        "annualStockholdersEquity", "annualTotalAssets", "annualTotalDebt",
        "annualOperatingIncome", "annualEBITDA", "annualPretaxIncome", "annualTaxProvision",
        "annualInterestExpense", "annualCashFlowFromContinuingOperatingActivities",
        "annualCapitalExpenditure", "annualCashDividendsPaid", "annualOrdinarySharesNumber",
        "annualAccountsReceivable", "annualInventory", "annualAccountsPayable"
    )
    private val quarterlyTypes = listOf("quarterlyTotalRevenue", "quarterlyNetIncome", "quarterlyDilutedEPS")

    /** Downloads the exchange master file and creates or refreshes every listed company. */
    fun syncUniverse(): Result<Int> = runCatching {
        val response = Http.get(UNIVERSE_URL, accept = "text/csv,*/*", timeoutMs = 30_000)
        val body = when (response) {
            is Http.Result.Ok -> response.body
            is Http.Result.Failed -> throw IllegalStateException("The exchange master file could not be downloaded (${response.message}).")
        }
        val entries = NseUniverse.equitiesOnly(NseUniverse.parse(body))
        if (entries.isEmpty()) throw IllegalStateException("The master file was downloaded but no companies could be read from it.")
        var created = 0
        for (entry in entries) {
            val existing = companyDao.findByCode(entry.symbol, true)
            companyDao.upsert(
                Company(
                    code = entry.symbol,
                    name = entry.name,
                    sector = existing?.sector.orEmpty(),
                    industry = existing?.industry.orEmpty(),
                    about = existing?.about.orEmpty(),
                    consolidated = true,
                    isDemo = false,
                    faceValue = entry.faceValue
                )
            )
            created++
        }
        importDao.record("Exchange master file", created, 0, 0, false, "universe-sync")
        logDao.append("INFO", "Sync", "Universe synced: $created companies")
        Logx.i("Sync", "Universe synced: $created companies")
        created
    }

    /** Fetches fundamentals and price history for one company. */
    fun syncCompany(company: Company, withFundamentals: Boolean = true): CompanyResult {
        var fundamentalsOk = false
        var barsWritten = 0
        var message = ""

        if (withFundamentals) {
            val url = fundamentalsUrl(company.code)
            when (val response = Http.get(url, timeoutMs = 20_000)) {
                is Http.Result.Ok -> {
                    val parsed = YahooProvider.parseFundamentals(response.body, company.faceValue)
                    if (parsed.annual.isEmpty() && parsed.quarterly.isEmpty()) {
                        message = "no fundamentals published"
                    } else {
                        if (parsed.annual.isNotEmpty()) {
                            companyDao.replaceAnnual(company.id, parsed.annual.map { it.copy(companyId = company.id) })
                        }
                        if (parsed.quarterly.isNotEmpty()) {
                            companyDao.replaceQuarterly(company.id, parsed.quarterly.map { it.copy(companyId = company.id) })
                        }
                        fundamentalsOk = true
                    }
                }
                is Http.Result.Failed -> message = "fundamentals: ${response.message}"
            }
        }

        val chartUrl = chartUrl(company.code)
        var quotePrice: Double? = null
        var high52: Double? = null
        var low52: Double? = null
        var changePercent: Double? = null
        when (val response = Http.get(chartUrl, timeoutMs = 20_000)) {
            is Http.Result.Ok -> {
                val snapshot = YahooProvider.parseChart(response.body)
                if (snapshot == null || snapshot.bars.isEmpty()) {
                    message = (message + " price history unavailable").trim()
                } else {
                    val bars = snapshot.bars.map { PriceBar(it.time, it.open, it.high, it.low, it.close, it.volume) }
                    syncDao.upsertBars(company.id, bars)
                    barsWritten = bars.size
                    quotePrice = snapshot.price ?: bars.last().close
                    high52 = snapshot.high52
                    low52 = snapshot.low52
                    if (bars.size >= 2) {
                        val prev = bars[bars.size - 2].close
                        if (prev > 0) changePercent = (bars.last().close - prev) / prev * 100.0
                    }
                }
            }
            is Http.Result.Failed -> message = (message + " prices: ${response.message}").trim()
        }

        if (quotePrice != null) {
            val shares = sharesOutstanding(company)
            companyDao.upsertQuote(
                Quote(
                    companyId = company.id,
                    price = quotePrice,
                    changePercent = changePercent,
                    high52 = high52,
                    low52 = low52,
                    marketCap = if (shares != null) quotePrice * shares else null,
                    updatedAt = System.currentTimeMillis(),
                    source = "yahoo"
                )
            )
        }

        val ok = fundamentalsOk || barsWritten > 0
        syncDao.markSync(company.id, "all", if (ok) "OK" else "FAILED", message)
        return CompanyResult(company.code, ok, message)
    }

    /**
     * Syncs a batch sequentially. Cancellation is checked between companies, never
     * mid-write, so the database is always left consistent.
     */
    fun syncMany(
        companies: List<Company>,
        withFundamentals: Boolean = true,
        onProgress: (Progress) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): Summary {
        var ok = 0
        var failed = 0
        var bars = 0
        var fundamentalsCount = 0
        companies.forEachIndexed { index, company ->
            if (isCancelled()) return@forEachIndexed
            onProgress(Progress("Syncing", index, companies.size, company.code, failed))
            val result = syncCompany(company, withFundamentals)
            if (result.ok) {
                ok++
                if (withFundamentals) fundamentalsCount++
                bars += syncDao.barCount(company.id)
            } else {
                failed++
                Logx.w("Sync", "${company.code}: ${result.message}")
            }
        }
        onProgress(Progress("Done", companies.size, companies.size, "", failed))
        logDao.append("INFO", "Sync", "Batch finished: $ok ok, $failed failed")
        return Summary(ok, failed, bars, fundamentalsCount)
    }

    /** Share count from stored equity capital and the exchange face value. */
    private fun sharesOutstanding(company: Company): Double? {
        val annual = companyDao.annualFor(company.id)
        val latest = annual.lastOrNull() ?: return null
        val equityCapital = latest.equityCapital ?: return null
        val face = company.faceValue.takeIf { it > 0 } ?: 10.0
        if (equityCapital <= 0) return null
        return equityCapital * 10_000_000.0 / face
    }

    private fun chartUrl(symbol: String): String =
        "https://query1.finance.yahoo.com/v8/finance/chart/${symbol.uppercase()}.NS?range=5y&interval=1d"

    private fun fundamentalsUrl(symbol: String): String {
        val period1 = Calendar.getInstance().apply { add(Calendar.YEAR, -8) }.timeInMillis / 1000
        val period2 = System.currentTimeMillis() / 1000
        val types = (annualTypes + quarterlyTypes).joinToString(",")
        return "https://query2.finance.yahoo.com/ws/fundamentals-timeseries/v1/finance/timeseries/" +
            "${symbol.uppercase()}.NS?symbol=${symbol.uppercase()}.NS&type=$types&period1=$period1&period2=$period2"
    }

    companion object {
        private const val UNIVERSE_URL = "https://archives.nseindia.com/content/equities/EQUITY_L.csv"
    }
}
