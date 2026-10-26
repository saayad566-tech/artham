package `in`.artham.fundamentals.data.net

import `in`.artham.fundamentals.data.AnnualFinancial
import `in`.artham.fundamentals.data.QuarterlyFinancial
import org.json.JSONObject

/**
 * Parsers for the two public Yahoo Finance endpoints this app uses.
 *
 * Everything here is a pure function of a JSON string, so the mapping from their field
 * names to our financial model is unit-tested without touching the network.
 *
 * Units: Yahoo reports rupees; the app stores rupees crore, so every monetary figure is
 * divided by 10^7 on the way in. Getting that wrong by a factor of ten million is the
 * classic bug in this kind of integration, which is why it is stated here and asserted in
 * a test.
 */
object YahooProvider {

    private const val CRORE = 10_000_000.0

    data class Bar(
        val time: Long,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        val volume: Long
    )

    data class ChartSnapshot(
        val symbol: String,
        val name: String?,
        val currency: String,
        val price: Double?,
        val high52: Double?,
        val low52: Double?,
        val previousClose: Double?,
        val bars: List<Bar>
    )

    data class ParsedFundamentals(
        val annual: List<AnnualFinancial>,
        val quarterly: List<QuarterlyFinancial>,
        val shareCount: Double?,
        val annualPeriods: Int
    )

    // ------------------------------------------------------------------ prices

    fun parseChart(json: String): ChartSnapshot? = try {
        val result = JSONObject(json).optJSONObject("chart")?.optJSONArray("result")
        if (result == null || result.length() == 0) null
        else {
            val first = result.optJSONObject(0) ?: return null
            val meta = first.optJSONObject("meta") ?: JSONObject()
            val timestamps = first.optJSONArray("timestamp")
            val quote = first.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)

            val bars = ArrayList<Bar>()
            if (timestamps != null && quote != null) {
                val opens = quote.optJSONArray("open")
                val highs = quote.optJSONArray("high")
                val lows = quote.optJSONArray("low")
                val closes = quote.optJSONArray("close")
                val volumes = quote.optJSONArray("volume")
                for (i in 0 until timestamps.length()) {
                    val close = closes?.optDoubleOrNull(i) ?: continue
                    val open = opens?.optDoubleOrNull(i) ?: close
                    val high = highs?.optDoubleOrNull(i) ?: close
                    val low = lows?.optDoubleOrNull(i) ?: close
                    val volume = volumes?.optLongOrNull(i) ?: 0L
                    if (close <= 0.0) continue
                    bars.add(Bar(timestamps.optLong(i), open, high, low, close, volume))
                }
            }
            ChartSnapshot(
                symbol = meta.optString("symbol", ""),
                name = meta.optString("longName").ifBlank { meta.optString("shortName") }.ifBlank { null },
                currency = meta.optString("currency", "INR"),
                price = meta.optDoubleOrNull("regularMarketPrice"),
                high52 = meta.optDoubleOrNull("fiftyTwoWeekHigh"),
                low52 = meta.optDoubleOrNull("fiftyTwoWeekLow"),
                previousClose = meta.optDoubleOrNull("chartPreviousClose"),
                bars = bars
            )
        }
    } catch (e: Exception) {
        null
    }

    // ------------------------------------------------------------ fundamentals

    fun parseFundamentals(json: String, faceValue: Double = 10.0): ParsedFundamentals {
        val series = extractSeries(json)
        val annualDates = series.keys
            .filter { it.startsWith("annual") }
            .flatMap { type -> series[type]?.keys ?: emptySet() }
            .filter { it.length >= 10 }
            .distinct()
            .sorted()

        fun value(type: String, date: String): Double? = series[type]?.get(date)

        val annual = ArrayList<AnnualFinancial>()
        for (date in annualDates) {
            val fyEnd = date.take(4).toIntOrNull() ?: continue
            val salesRaw = value("annualTotalRevenue", date) ?: continue
            val netRaw = value("annualNetIncome", date) ?: 0.0
            val ebitdaRaw = value("annualEBITDA", date)
            val operatingIncomeRaw = value("annualOperatingIncome", date)
            val opRaw = ebitdaRaw ?: operatingIncomeRaw
            // EBITDA - EBIT is depreciation and amortisation, which the endpoint does not
            // publish separately. Derived, and labelled as derived in the docs.
            val depreciationRaw = if (ebitdaRaw != null && operatingIncomeRaw != null) ebitdaRaw - operatingIncomeRaw else null
            val interestRaw = value("annualInterestExpense", date)
            val pretaxRaw = value("annualPretaxIncome", date)
            val taxRaw = value("annualTaxProvision", date)
            val equityRaw = value("annualStockholdersEquity", date)
            val assetsRaw = value("annualTotalAssets", date)
            val debtRaw = value("annualTotalDebt", date)
            val sharesRaw = value("annualOrdinarySharesNumber", date)
            val cfoRaw = value("annualCashFlowFromContinuingOperatingActivities", date)
                ?: value("annualFreeCashFlow", date)
            val capexRaw = value("annualCapitalExpenditure", date)
            val dividendPaidRaw = value("annualCashDividendsPaid", date)
            val epsRaw = value("annualDilutedEPS", date) ?: value("annualBasicEPS", date)

            val sales = salesRaw / CRORE
            val op = opRaw?.div(CRORE)
            val equity = equityRaw?.div(CRORE)
            val shares = sharesRaw
            val equityCapital = if (shares != null) shares * faceValue / CRORE else null
            val reserves = if (equity != null && equityCapital != null) equity - equityCapital else equity

            annual.add(
                AnnualFinancial(
                    companyId = 0L,
                    fyEnd = fyEnd,
                    sales = sales,
                    expenses = if (op != null) sales - op else null,
                    operatingProfit = op,
                    opmPercent = if (op != null && sales > 0) op / sales * 100.0 else null,
                    otherIncome = 0.0,
                    interest = interestRaw?.div(CRORE),
                    depreciation = depreciationRaw?.div(CRORE),
                    profitBeforeTax = pretaxRaw?.div(CRORE),
                    taxPercent = if (pretaxRaw != null && taxRaw != null && pretaxRaw != 0.0) taxRaw / pretaxRaw * 100.0 else null,
                    netProfit = netRaw / CRORE,
                    eps = epsRaw,
                    dividend = if (dividendPaidRaw != null && shares != null && shares > 0) kotlin.math.abs(dividendPaidRaw) / shares else null,
                    equityCapital = equityCapital,
                    reserves = reserves,
                    borrowings = debtRaw?.div(CRORE),
                    otherLiabilities = null,
                    totalLiabilities = assetsRaw?.div(CRORE),
                    fixedAssets = null,
                    cwip = null,
                    investments = null,
                    otherAssets = null,
                    totalAssets = assetsRaw?.div(CRORE),
                    receivables = value("annualAccountsReceivable", date)?.div(CRORE),
                    inventory = value("annualInventory", date)?.div(CRORE),
                    payables = value("annualAccountsPayable", date)?.div(CRORE),
                    cfo = cfoRaw?.div(CRORE),
                    // Capital expenditure is the only investing line this endpoint gives, so
                    // it stands in for the whole investing section. Free cash flow is then
                    // correct even though the breakdown is not.
                    cfi = capexRaw?.div(CRORE),
                    cff = null,
                    netCashFlow = if (cfoRaw != null && capexRaw != null) (cfoRaw + capexRaw) / CRORE else null
                )
            )
        }

        val quarterly = ArrayList<QuarterlyFinancial>()
        val quarterDates = (series["quarterlyTotalRevenue"]?.keys ?: emptySet())
            .filter { it.length >= 10 }
            .sorted()
        for (date in quarterDates) {
            val month = date.substring(5, 7).toIntOrNull() ?: continue
            val year = date.take(4).toIntOrNull() ?: continue
            val quarter = when (month) {
                6 -> 1
                9 -> 2
                12 -> 3
                3 -> 4
                else -> continue
            }
            val salesRaw = series["quarterlyTotalRevenue"]?.get(date) ?: continue
            val netRaw = series["quarterlyNetIncome"]?.get(date) ?: 0.0
            val epsRaw = series["quarterlyDilutedEPS"]?.get(date)
            quarterly.add(
                QuarterlyFinancial(
                    companyId = 0L,
                    fyEnd = if (month <= 3) year else year + 1,
                    quarter = quarter,
                    label = when (quarter) {
                        1 -> "Jun ${if (month <= 3) year - 1 else year}"
                        2 -> "Sep $year"
                        3 -> "Dec $year"
                        else -> "Mar $year"
                    },
                    sales = salesRaw / CRORE,
                    netProfit = netRaw / CRORE,
                    eps = epsRaw
                )
            )
        }

        return ParsedFundamentals(
            annual = annual,
            quarterly = quarterly,
            shareCount = annual.lastOrNull()?.let { series["annualOrdinarySharesNumber"]?.get(annualDates.last()) },
            annualPeriods = annual.size
        )
    }

    /** type -> (asOfDate -> raw value). Missing or empty series are skipped. */
    private fun extractSeries(json: String): Map<String, Map<String, Double>> {
        val out = HashMap<String, Map<String, Double>>()
        try {
            val result = JSONObject(json).optJSONObject("timeseries")?.optJSONArray("result") ?: return out
            for (i in 0 until result.length()) {
                val item = result.optJSONObject(i) ?: continue
                val typeArray = item.optJSONObject("meta")?.optJSONArray("type") ?: continue
                val type = typeArray.optString(0).takeIf { it.isNotBlank() } ?: continue
                val rows = item.optJSONArray(type) ?: continue
                val values = HashMap<String, Double>()
                for (r in 0 until rows.length()) {
                    val row = rows.optJSONObject(r) ?: continue
                    val date = row.optString("asOfDate").takeIf { it.isNotBlank() } ?: continue
                    val raw = row.optJSONObject("reportedValue")?.optDoubleOrNull("raw") ?: continue
                    values[date] = raw
                }
                if (values.isNotEmpty()) out[type] = values
            }
        } catch (e: Exception) {
            // A malformed payload yields whatever was parsed before the failure.
        }
        return out
    }

    private fun org.json.JSONArray.optDoubleOrNull(index: Int): Double? {
        if (index < 0 || index >= length() || isNull(index)) return null
        val v = optDouble(index, Double.NaN)
        return if (v.isNaN()) null else v
    }

    private fun org.json.JSONArray.optLongOrNull(index: Int): Long? {
        if (index < 0 || index >= length() || isNull(index)) return null
        return optLong(index, -1L).takeIf { it >= 0 }
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        val v = optDouble(name, Double.NaN)
        return if (v.isNaN()) null else v
    }
}
