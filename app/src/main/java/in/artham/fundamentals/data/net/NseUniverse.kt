package `in`.artham.fundamentals.data.net

import `in`.artham.fundamentals.data.Company

/** One row of the exchange master file. */
data class UniverseEntry(
    val symbol: String,
    val name: String,
    val series: String,
    val isin: String,
    val faceValue: Double
) {
    fun toCompany(): Company = Company(
        code = symbol,
        name = name,
        consolidated = true,
        isDemo = false
    )
}

/**
 * Parser for the exchange's published equity master list
 * (`SYMBOL,NAME OF COMPANY,SERIES,DATE OF LISTING,PAID UP VALUE,MARKET LOT,ISIN,FACE VALUE`).
 *
 * Face value comes from here and is the reason the app no longer has to assume Rs 10 when
 * converting equity capital into share count.
 */
object NseUniverse {

    fun parse(csv: String): List<UniverseEntry> {
        val lines = csv.removePrefix("\uFEFF").split('\n')
        if (lines.size < 2) return emptyList()
        val out = ArrayList<UniverseEntry>(lines.size)
        for (index in 1 until lines.size) {
            val line = lines[index].trim()
            if (line.isEmpty()) continue
            val cells = splitCsv(line)
            if (cells.size < 8) continue
            val symbol = cells[0].trim()
            val name = cells[1].trim()
            if (symbol.isEmpty() || name.isEmpty()) continue
            val series = cells[2].trim()
            val isin = cells[6].trim()
            val faceValue = cells[7].trim().toDoubleOrNull() ?: 10.0
            out.add(UniverseEntry(symbol, name, series, isin, faceValue))
        }
        return out
    }

    /** Keeps only ordinary equity series, which is what a fundamentals screener wants. */
    fun equitiesOnly(entries: List<UniverseEntry>): List<UniverseEntry> =
        entries.filter { it.series.equals("EQ", true) || it.series.equals("BE", true) }

    private fun splitCsv(line: String): List<String> {
        val cells = ArrayList<String>(8)
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                inQuotes && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    cells.add(current.toString()); current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        cells.add(current.toString())
        return cells
    }
}
