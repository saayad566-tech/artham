package `in`.artham.fundamentals.data.import

import java.util.Locale

/**
 * Pure spreadsheet parsing rules, kept free of Android and database types so they
 * can be unit-tested directly. [ImportService] is the only caller.
 */
object SheetParsing {

    /** A period label recognised in a header row. Month defaults to March (fiscal year end). */
    data class Period(val label: String, val year: Int, val month: Int)

    fun normalize(s: String): String = s.lowercase(Locale.US)
        .replace("+", " ")
        .replace(Regex("[^a-z0-9% ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Maps a spreadsheet row/column label onto a stored field. */
    fun metricKey(label: String): String? {
        val n = normalize(label)
        if (n.isBlank()) return null
        return when (n) {
            "sales", "revenue", "sales revenue", "total revenue", "net sales", "income from operations" -> "sales"
            "expenses", "total expenses", "operating expenses" -> "expenses"
            "operating profit", "ebitda", "op" -> "operating_profit"
            "opm", "opm %", "operating margin", "operating margin %", "opm percent" -> "opm_percent"
            "other income" -> "other_income"
            "interest", "finance cost", "interest expense" -> "interest"
            "depreciation", "depreciation and amortisation" -> "depreciation"
            "profit before tax", "pbt", "profit before exceptional" -> "profit_before_tax"
            "tax %", "tax percent", "tax rate" -> "tax_percent"
            "net profit", "profit after tax", "pat", "net profit total" -> "net_profit"
            "eps", "eps in rs", "earnings per share", "eps rs" -> "eps"
            "dividend", "dividend amount", "dividend per share" -> "dividend"
            "equity capital", "share capital", "equity share capital" -> "equity_capital"
            "reserves", "reserves and surplus" -> "reserves"
            "borrowings", "debt", "total debt", "borrowings total" -> "borrowings"
            "other liabilities" -> "other_liabilities"
            "total liabilities", "total equity and liabilities" -> "total_liabilities"
            "fixed assets", "net block", "property plant and equipment", "ppe" -> "fixed_assets"
            "cwip", "capital work in progress" -> "cwip"
            "investments" -> "investments"
            "other assets" -> "other_assets"
            "total assets" -> "total_assets"
            "receivables", "trade receivables", "debtors" -> "receivables"
            "inventory", "inventories" -> "inventory"
            "payables", "trade payables", "creditors" -> "payables"
            "cash from operating activity", "operating cash flow", "cfo", "net cash from operating activities" -> "cfo"
            "cash from investing activity", "investing cash flow", "cfi", "net cash from investing activities" -> "cfi"
            "cash from financing activity", "financing cash flow", "cff", "net cash from financing activities" -> "cff"
            "net cash flow" -> "net_cash_flow"
            else -> null
        }
    }

    fun parseNumber(raw: String?): Double? {
        if (raw == null) return null
        var s = raw.trim()
        if (s.isEmpty() || s == "-" || s.equals("na", true) || s.equals("n/a", true) || s.equals("null", true)) return null
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true
            s = s.substring(1, s.length - 1)
        }
        s = s.replace(",", "").replace("\u20B9", "").replace("%", "").replace(" ", "")
        if (s.startsWith("-")) { negative = true; s = s.substring(1) }
        if (s.startsWith("+")) s = s.substring(1)
        val v = s.toDoubleOrNull() ?: return null
        return if (negative) -v else v
    }

    fun parsePeriod(raw: String?): Period? {
        if (raw == null) return null
        val s = raw.trim()
        if (s.isEmpty()) return null
        val up = s.uppercase(Locale.US)
        if (up.contains("TTM") || up.contains("TRAILING")) return null
        val months = mapOf(
            "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAY" to 5, "JUN" to 6,
            "JUL" to 7, "AUG" to 8, "SEP" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12
        )
        val yearMatch = Regex("(19|20)\\d{2}").find(up)
        val monthEntry = months.entries.firstOrNull { up.contains(it.key) }
        if (yearMatch != null) {
            val year = yearMatch.value.toInt()
            return Period(s, year, monthEntry?.value ?: 3)
        }
        val fy = Regex("FY\\s?(\\d{2})").find(up)
        if (fy != null) {
            val yy = fy.groupValues[1].toInt()
            return Period(s, 2000 + yy, 3)
        }
        // "Mar-24" / "Mar 24" / "MAR24": a month followed by a two digit year.
        val shortYear = Regex("(\\d{2})\\s*$").find(up)
        if (monthEntry != null && shortYear != null) {
            val yy = shortYear.groupValues[1].toInt()
            if (yy in 0..99) return Period(s, 2000 + yy, monthEntry.value)
        }
        val bare = up.toIntOrNull()
        if (bare != null && bare in 1990..2100) return Period(s, bare, 3)
        return null
    }

    fun quarterIndex(month: Int): Int = when (month) {
        6 -> 1
        9 -> 2
        12 -> 3
        3 -> 4
        else -> 1
    }

    /** Date formats that actually appear in exported spreadsheets. */
    fun parseAnyDate(text: String): Long? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        val patterns = listOf(
            "yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "yyyy/MM/dd", "dd-MMM-yyyy", "dd MMM yyyy", "MM/dd/yyyy"
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                val format = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                format.isLenient = false
                format.parse(clean)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    data class PriceOutcome(val bars: Int, val from: String, val to: String, val warnings: List<String>)

    /**
     * Parses a dated close series from already-read rows. Kept free of file and database
     * types so the parser is unit-testable on its own.
     */
    fun parsePriceRows(
        rows: List<List<String>>,
        onBar: (Long, Double, Double, Double, Double, Long) -> Unit
    ): PriceOutcome {
        val warnings = ArrayList<String>()
        if (rows.isEmpty()) return PriceOutcome(0, "", "", listOf("The file contained no readable rows."))
        val lower = rows.first().map { it.lowercase().trim() }
        val dateCol = lower.indexOfFirst { it.contains("date") || it.contains("time") }
        val closeCol = lower.indexOfFirst { it == "close" || it == "price" || it.contains("close") }
        if (dateCol < 0 || closeCol < 0) {
            return PriceOutcome(0, "", "", listOf("Expected a date column and a close (or price) column in the header."))
        }
        val openCol = lower.indexOfFirst { it == "open" }
        val highCol = lower.indexOfFirst { it == "high" }
        val lowCol = lower.indexOfFirst { it == "low" }
        val volumeCol = lower.indexOfFirst { it == "volume" || it == "vol" }

        var count = 0
        var first = ""
        var last = ""
        for (index in 1 until rows.size) {
            val row = rows[index]
            val dateText = row.getOrNull(dateCol)?.trim().orEmpty()
            val millis = parseAnyDate(dateText) ?: continue
            val close = parseNumber(row.getOrNull(closeCol)) ?: continue
            if (close <= 0) continue
            val open = row.getOrNull(openCol)?.let { parseNumber(it) } ?: close
            val high = row.getOrNull(highCol)?.let { parseNumber(it) } ?: close
            val low = row.getOrNull(lowCol)?.let { parseNumber(it) } ?: close
            val volume = row.getOrNull(volumeCol)?.let { parseNumber(it) }?.toLong() ?: 0L
            onBar(millis, open, high, low, close, volume)
            count++
            if (first.isEmpty()) first = dateText
            last = dateText
        }
        if (count == 0) warnings.add("No row had both a readable date and a positive close.")
        if (count in 1..19) warnings.add("Only $count bars imported. Charts and indicators need at least 20.")
        return PriceOutcome(count, first, last, warnings)
    }
}
