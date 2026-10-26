package `in`.artham.fundamentals.data.import

import `in`.artham.fundamentals.core.Logx
import `in`.artham.fundamentals.data.AnnualFinancial
import `in`.artham.fundamentals.data.Company
import `in`.artham.fundamentals.data.CompanyDao
import `in`.artham.fundamentals.data.ImportDao
import `in`.artham.fundamentals.data.QuarterlyFinancial
import java.io.File
import java.util.Calendar

/**
 * Reads a spreadsheet the user already owns and turns it into the local store.
 *
 * Three shapes are recognised, in this order:
 *  1. Transposed company sheet - metrics down the rows, periods across the columns
 *     (this is the shape a single-company export produces).
 *  2. Long sheet - one row per company-period, with explicit period and metric columns.
 *  3. Wide snapshot - one row per company with metric columns and no period column;
 *     imported as a single period, which is stated in the outcome warnings.
 *
 * Nothing here talks to a network and nothing is scraped: the input is a file the
 * user supplies.
 */
class ImportService(
    private val companyDao: CompanyDao,
    private val importDao: ImportDao
) {

    data class Outcome(
        val companies: Int,
        val annualRows: Int,
        val quarterlyRows: Int,
        val warnings: List<String>,
        val mode: String
    )


    fun import(file: File, displayName: String): Outcome {
        val warnings = ArrayList<String>()
        val sheets: List<Pair<String, List<List<String>>>> = if (displayName.lowercase().endsWith(".csv")) {
            listOf("CSV" to Csv.read(file))
        } else {
            XlsxReader.read(file).map { it.name to it.rows }
        }
        if (sheets.isEmpty() || sheets.all { it.second.isEmpty() }) {
            return Outcome(0, 0, 0, listOf("The file contained no readable rows."), "empty")
        }

        var companies = 0
        var annualRows = 0
        var quarterlyRows = 0
        var mode = "unknown"
        val baseName = displayName.substringBeforeLast('.').trim().ifBlank { "Imported" }

        for ((sheetName, rows) in sheets) {
            if (rows.isEmpty()) continue
            val headerIdx = rows.indexOfFirst { row -> row.count { it.isNotBlank() } >= 2 }
            if (headerIdx < 0) continue
            val header = rows[headerIdx]
            val periodCols = header.mapIndexedNotNull { i, v -> SheetParsing.parsePeriod(v)?.let { i to it } }
            val keys = header.map { SheetParsing.metricKey(it) }
            val nameCol = header.indexOfFirst { isNameHeader(it) }
            val codeCol = header.indexOfFirst { isCodeHeader(it) }
            val periodCol = header.indexOfFirst { isPeriodColumnHeader(it) }

            when {
                periodCols.size >= 2 -> {
                    val labelCol = periodCols.first().first.let { firstPeriodCol ->
                        (0 until firstPeriodCol).firstOrNull { col ->
                            rows.drop(headerIdx + 1).any { it.getOrNull(col)?.isNotBlank() == true }
                        } ?: 0
                    }
                    val isQuarterly = sheetName.contains("quarter", true) ||
                        periodCols.map { it.second.month }.distinct().size > 1
                    val companyName = findCompanyName(rows, headerIdx) ?: baseName
                    val code = companyName.uppercase().replace(Regex("[^A-Z0-9]"), "").take(12).ifBlank { baseName.uppercase().take(12) }
                    val companyId = companyDao.upsert(
                        Company(code = code, name = companyName, sector = "", industry = "", consolidated = true, isDemo = false)
                    )
                    companies++

                    if (isQuarterly) {
                        val byPeriod = LinkedHashMap<Int, MutableQuarterly>()
                        for (r in (headerIdx + 1) until rows.size) {
                            val row = rows[r]
                            val key = SheetParsing.metricKey(row.getOrNull(labelCol) ?: "") ?: continue
                            for ((col, period) in periodCols) {
                                val value = SheetParsing.parseNumber(row.getOrNull(col)) ?: continue
                                val q = byPeriod.getOrPut(period.year * 4 + SheetParsing.quarterIndex(period.month)) {
                                    MutableQuarterly(
                                        fyEnd = if (period.month <= 3) period.year else period.year + 1,
                                        quarter = SheetParsing.quarterIndex(period.month),
                                        label = period.label
                                    )
                                }
                                q.set(key, value)
                            }
                        }
                        val out = byPeriod.values.map { it.toQuarterly(companyId) }
                        companyDao.replaceQuarterly(companyId, out)
                        quarterlyRows += out.size
                    } else {
                        val byPeriod = LinkedHashMap<Int, MutableAnnual>()
                        for (r in (headerIdx + 1) until rows.size) {
                            val row = rows[r]
                            val key = SheetParsing.metricKey(row.getOrNull(labelCol) ?: "") ?: continue
                            for ((col, period) in periodCols) {
                                val value = SheetParsing.parseNumber(row.getOrNull(col)) ?: continue
                                byPeriod.getOrPut(period.year) { MutableAnnual(period.year) }.set(key, value)
                            }
                        }
                        val out = byPeriod.values.map { it.toAnnual(companyId) }
                        companyDao.replaceAnnual(companyId, out)
                        annualRows += out.size
                    }
                    mode = if (isQuarterly) "transposed-quarterly" else "transposed-annual"
                }

                nameCol >= 0 && periodCol >= 0 -> {
                    val byCompany = LinkedHashMap<String, MutableList<MutableAnnual>>()
                    val codes = HashMap<String, String>()
                    for (r in (headerIdx + 1) until rows.size) {
                        val row = rows[r]
                        val name = row.getOrNull(nameCol)?.trim().orEmpty()
                        if (name.isBlank()) continue
                        val period = SheetParsing.parsePeriod(row.getOrNull(periodCol) ?: "") ?: continue
                        val code = row.getOrNull(codeCol)?.trim()?.takeIf { it.isNotBlank() }
                            ?: name.uppercase().replace(Regex("[^A-Z0-9]"), "").take(12)
                        codes[name] = code
                        val holder = MutableAnnual(period.year)
                        for ((i, key) in keys.withIndex()) {
                            if (key == null) continue
                            val v = SheetParsing.parseNumber(row.getOrNull(i)) ?: continue
                            holder.set(key, v)
                        }
                        byCompany.getOrPut(name) { ArrayList() }.add(holder)
                    }
                    for ((name, list) in byCompany) {
                        val code = codes[name] ?: name.take(12)
                        val companyId = companyDao.upsert(Company(code = code, name = name, isDemo = false))
                        val merged = list.groupBy { it.fyEnd }.map { (_, holders) ->
                            val base = MutableAnnual(holders.first().fyEnd)
                            holders.forEach { h -> h.copyInto(base) }
                            base.toAnnual(companyId)
                        }
                        companyDao.replaceAnnual(companyId, merged)
                        companies++
                        annualRows += merged.size
                    }
                    mode = "long-annual"
                }

                nameCol >= 0 -> {
                    val fyEnd = Calendar.getInstance().get(Calendar.YEAR) - 1
                    warnings.add("No period column found, so rows were imported as a single period (FY$fyEnd). Verify the year on the company page.")
                    for (r in (headerIdx + 1) until rows.size) {
                        val row = rows[r]
                        val name = row.getOrNull(nameCol)?.trim().orEmpty()
                        if (name.isBlank()) continue
                        val code = row.getOrNull(codeCol)?.trim()?.takeIf { it.isNotBlank() }
                            ?: name.uppercase().replace(Regex("[^A-Z0-9]"), "").take(12)
                        val companyId = companyDao.upsert(Company(code = code, name = name, isDemo = false))
                        val holder = MutableAnnual(fyEnd)
                        for ((i, key) in keys.withIndex()) {
                            if (key == null) continue
                            val v = SheetParsing.parseNumber(row.getOrNull(i)) ?: continue
                            holder.set(key, v)
                        }
                        companyDao.replaceAnnual(companyId, listOf(holder.toAnnual(companyId)))
                        companies++
                        annualRows++
                    }
                    mode = "wide-snapshot"
                }

                else -> warnings.add("Sheet '$sheetName' was skipped: no company name column and no period columns were recognised.")
            }
        }

        if (companies > 0) {
            importDao.record(displayName, companies, annualRows, quarterlyRows, false, mode)
            Logx.i("Import", "Imported $companies companies / $annualRows annual rows / $quarterlyRows quarterly rows from $displayName")
        }
        return Outcome(companies, annualRows, quarterlyRows, warnings, mode)
    }

    // ---------------------------------------------------------------- parsing

    private fun findCompanyName(rows: List<List<String>>, headerIdx: Int): String? {
        for (i in 0 until headerIdx.coerceAtMost(6)) {
            val row = rows[i]
            val joined = row.filter { it.isNotBlank() }
            if (joined.isEmpty()) continue
            val first = joined.first()
            if (first.length in 4..90 && !first.startsWith("http") && SheetParsing.parseNumber(first) == null) {
                val looksLikeLabel = first.lowercase().replace(Regex("[^a-z ]"), "").trim() in
                    setOf("report", "date", "company", "name", "screener", "export", "generated", "as of")
                if (!looksLikeLabel) return first
            }
        }
        return null
    }

    private fun isNameHeader(s: String): Boolean {
        val n = SheetParsing.normalize(s)
        return n == "name" || n == "company" || n == "company name" || n == "particulars" && false
    }

    private fun isCodeHeader(s: String): Boolean {
        val n = SheetParsing.normalize(s)
        return n == "code" || n == "symbol" || n == "nse code" || n == "bse code" || n == "ticker"
    }

    private fun isPeriodColumnHeader(s: String): Boolean {
        val n = SheetParsing.normalize(s)
        return n == "fy" || n == "year" || n == "financial year" || n == "period" || n == "fy end"
    }

    // ------------------------------------------------------------- accumulators

    private class MutableAnnual(val fyEnd: Int) {
        var sales: Double? = null
        var expenses: Double? = null
        var operatingProfit: Double? = null
        var opmPercent: Double? = null
        var otherIncome: Double? = null
        var interest: Double? = null
        var depreciation: Double? = null
        var profitBeforeTax: Double? = null
        var taxPercent: Double? = null
        var netProfit: Double? = null
        var eps: Double? = null
        var dividend: Double? = null
        var equityCapital: Double? = null
        var reserves: Double? = null
        var borrowings: Double? = null
        var otherLiabilities: Double? = null
        var totalLiabilities: Double? = null
        var fixedAssets: Double? = null
        var cwip: Double? = null
        var investments: Double? = null
        var otherAssets: Double? = null
        var totalAssets: Double? = null
        var receivables: Double? = null
        var inventory: Double? = null
        var payables: Double? = null
        var cfo: Double? = null
        var cfi: Double? = null
        var cff: Double? = null
        var netCashFlow: Double? = null

        fun set(key: String, v: Double) {
            when (key) {
                "sales" -> sales = v
                "expenses" -> expenses = v
                "operating_profit" -> operatingProfit = v
                "opm_percent" -> opmPercent = v
                "other_income" -> otherIncome = v
                "interest" -> interest = v
                "depreciation" -> depreciation = v
                "profit_before_tax" -> profitBeforeTax = v
                "tax_percent" -> taxPercent = v
                "net_profit" -> netProfit = v
                "eps" -> eps = v
                "dividend" -> dividend = v
                "equity_capital" -> equityCapital = v
                "reserves" -> reserves = v
                "borrowings" -> borrowings = v
                "other_liabilities" -> otherLiabilities = v
                "total_liabilities" -> totalLiabilities = v
                "fixed_assets" -> fixedAssets = v
                "cwip" -> cwip = v
                "investments" -> investments = v
                "other_assets" -> otherAssets = v
                "total_assets" -> totalAssets = v
                "receivables" -> receivables = v
                "inventory" -> inventory = v
                "payables" -> payables = v
                "cfo" -> cfo = v
                "cfi" -> cfi = v
                "cff" -> cff = v
                "net_cash_flow" -> netCashFlow = v
            }
        }

        fun copyInto(other: MutableAnnual) {
            sales?.let { other.sales = it }
            expenses?.let { other.expenses = it }
            operatingProfit?.let { other.operatingProfit = it }
            opmPercent?.let { other.opmPercent = it }
            otherIncome?.let { other.otherIncome = it }
            interest?.let { other.interest = it }
            depreciation?.let { other.depreciation = it }
            profitBeforeTax?.let { other.profitBeforeTax = it }
            taxPercent?.let { other.taxPercent = it }
            netProfit?.let { other.netProfit = it }
            eps?.let { other.eps = it }
            dividend?.let { other.dividend = it }
            equityCapital?.let { other.equityCapital = it }
            reserves?.let { other.reserves = it }
            borrowings?.let { other.borrowings = it }
            otherLiabilities?.let { other.otherLiabilities = it }
            totalLiabilities?.let { other.totalLiabilities = it }
            fixedAssets?.let { other.fixedAssets = it }
            cwip?.let { other.cwip = it }
            investments?.let { other.investments = it }
            otherAssets?.let { other.otherAssets = it }
            totalAssets?.let { other.totalAssets = it }
            receivables?.let { other.receivables = it }
            inventory?.let { other.inventory = it }
            payables?.let { other.payables = it }
            cfo?.let { other.cfo = it }
            cfi?.let { other.cfi = it }
            cff?.let { other.cff = it }
            netCashFlow?.let { other.netCashFlow = it }
        }

        fun toAnnual(companyId: Long) = AnnualFinancial(
            companyId = companyId, fyEnd = fyEnd, sales = sales, expenses = expenses,
            operatingProfit = operatingProfit, opmPercent = opmPercent, otherIncome = otherIncome,
            interest = interest, depreciation = depreciation, profitBeforeTax = profitBeforeTax,
            taxPercent = taxPercent, netProfit = netProfit, eps = eps, dividend = dividend,
            equityCapital = equityCapital, reserves = reserves, borrowings = borrowings,
            otherLiabilities = otherLiabilities, totalLiabilities = totalLiabilities,
            fixedAssets = fixedAssets, cwip = cwip, investments = investments, otherAssets = otherAssets,
            totalAssets = totalAssets, receivables = receivables, inventory = inventory,
            payables = payables, cfo = cfo, cfi = cfi, cff = cff, netCashFlow = netCashFlow
        )
    }

    private class MutableQuarterly(val fyEnd: Int, val quarter: Int, val label: String) {
        var sales: Double? = null
        var expenses: Double? = null
        var operatingProfit: Double? = null
        var opmPercent: Double? = null
        var otherIncome: Double? = null
        var interest: Double? = null
        var depreciation: Double? = null
        var profitBeforeTax: Double? = null
        var taxPercent: Double? = null
        var netProfit: Double? = null
        var eps: Double? = null

        fun set(key: String, v: Double) {
            when (key) {
                "sales" -> sales = v
                "expenses" -> expenses = v
                "operating_profit" -> operatingProfit = v
                "opm_percent" -> opmPercent = v
                "other_income" -> otherIncome = v
                "interest" -> interest = v
                "depreciation" -> depreciation = v
                "profit_before_tax" -> profitBeforeTax = v
                "tax_percent" -> taxPercent = v
                "net_profit" -> netProfit = v
                "eps" -> eps = v
            }
        }

        fun toQuarterly(companyId: Long) = QuarterlyFinancial(
            companyId = companyId, fyEnd = fyEnd, quarter = quarter, label = label, sales = sales,
            expenses = expenses, operatingProfit = operatingProfit, opmPercent = opmPercent,
            otherIncome = otherIncome, interest = interest, depreciation = depreciation,
            profitBeforeTax = profitBeforeTax, taxPercent = taxPercent, netProfit = netProfit, eps = eps
        )
    }
}
