package `in`.artham.fundamentals.data.import

import java.io.File

/** RFC 4180-ish CSV reader: quoted fields, embedded commas, CRLF and BOM. */
object Csv {

    fun read(file: File): List<List<String>> = read(file.readText(Charsets.UTF_8))

    fun read(text: String): List<List<String>> {
        val clean = text.removePrefix("\uFEFF")
        val rows = ArrayList<List<String>>()
        var field = StringBuilder()
        var row = ArrayList<String>()
        var inQuotes = false
        var i = 0
        while (i < clean.length) {
            val ch = clean[i]
            when {
                inQuotes && ch == '"' && i + 1 < clean.length && clean[i + 1] == '"' -> {
                    field.append('"'); i++
                }
                ch == '"' -> inQuotes = !inQuotes
                !inQuotes && (ch == ',' || ch == ';') -> {
                    row.add(field.toString().trim()); field = StringBuilder()
                }
                !inQuotes && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < clean.length && clean[i + 1] == '\n') i++
                    row.add(field.toString().trim()); field = StringBuilder()
                    if (row.any { it.isNotEmpty() }) rows.add(row)
                    row = ArrayList()
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString().trim())
            if (row.any { it.isNotEmpty() }) rows.add(row)
        }
        return rows
    }
}
