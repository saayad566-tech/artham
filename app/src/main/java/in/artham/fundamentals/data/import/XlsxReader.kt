package `in`.artham.fundamentals.data.import

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Minimal, read-only XLSX reader built on the platform ZIP and XML parsers.
 *
 * An .xlsx file is a ZIP of XML parts, so a spreadsheet export can be read
 * without pulling in a 10 MB office-suite dependency. Supports shared strings,
 * inline strings, cached formula values and sparse cells.
 */
object XlsxReader {

    data class Sheet(val name: String, val rows: List<List<String>>)

    fun read(file: File): List<Sheet> {
        ZipFile(file).use { zip ->
            val shared = zip.entry("xl/sharedStrings.xml")?.let { entry ->
                zip.getInputStream(entry).use { parseSharedStrings(it) }
            } ?: emptyList()

            val names = zip.entry("xl/workbook.xml")?.let { entry ->
                zip.getInputStream(entry).use { parseSheetNames(it) }
            } ?: emptyList()

            val sheetEntries = zip.entries().asSequence()
                .filter { it.name.startsWith("xl/worksheets/sheet") && it.name.endsWith(".xml") }
                .sortedBy { it.name }
                .toList()

            return sheetEntries.mapIndexed { index, entry ->
                val rows = zip.getInputStream(entry).use { parseSheet(it, shared) }
                Sheet(names.getOrElse(index) { "Sheet${index + 1}" }, rows)
            }
        }
    }

    private fun ZipFile.entry(name: String) = getEntry(name)

    private fun parseSharedStrings(input: InputStream): List<String> {
        val out = ArrayList<String>()
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        var current: StringBuilder? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when {
                event == XmlPullParser.START_TAG && parser.name == "si" -> current = StringBuilder()
                event == XmlPullParser.START_TAG && parser.name == "t" && current != null ->
                    current.append(parser.nextText())
                event == XmlPullParser.END_TAG && parser.name == "si" -> {
                    out.add(current?.toString() ?: "")
                    current = null
                }
            }
            event = parser.next()
        }
        return out
    }

    private fun parseSheetNames(input: InputStream): List<String> {
        val out = ArrayList<String>()
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name")
                if (name != null) out.add(name)
            }
            event = parser.next()
        }
        return out
    }

    private fun parseSheet(input: InputStream, shared: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        var event = parser.eventType
        var row: MutableList<String>? = null
        var cellRef: String? = null
        var cellType: String? = null
        var value: String? = null
        var inValue = false
        var inInline = false

        while (event != XmlPullParser.END_DOCUMENT) {
            when {
                event == XmlPullParser.START_TAG && parser.name == "row" -> row = ArrayList()
                event == XmlPullParser.START_TAG && parser.name == "c" -> {
                    cellRef = parser.getAttributeValue(null, "r")
                    cellType = parser.getAttributeValue(null, "t")
                    value = null
                }
                event == XmlPullParser.START_TAG && parser.name == "v" -> inValue = true
                event == XmlPullParser.START_TAG && parser.name == "is" -> inInline = true
                event == XmlPullParser.START_TAG && parser.name == "t" && inInline -> {
                    value = (value ?: "") + parser.nextText()
                }
                event == XmlPullParser.TEXT && inValue -> value = (value ?: "") + parser.text
                event == XmlPullParser.END_TAG && parser.name == "v" -> inValue = false
                event == XmlPullParser.END_TAG && parser.name == "is" -> inInline = false
                event == XmlPullParser.END_TAG && parser.name == "c" -> {
                    val target = row
                    if (target != null) {
                        val col = cellRef?.let { columnIndex(it) } ?: target.size
                        while (target.size < col) target.add("")
                        val raw = value ?: ""
                        val resolved = if (cellType == "s") {
                            raw.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                        } else raw
                        if (target.size == col) target.add(resolved) else target[col] = resolved
                    }
                    cellRef = null
                    cellType = null
                    value = null
                }
                event == XmlPullParser.END_TAG && parser.name == "row" -> {
                    row?.let { rows.add(it) }
                    row = null
                }
            }
            event = parser.next()
        }
        return rows
    }

    /** "AB12" -> 27 (zero-based). */
    internal fun columnIndex(ref: String): Int {
        var col = 0
        for (ch in ref) {
            if (ch.isLetter()) col = col * 26 + (ch.uppercaseChar() - 'A' + 1) else break
        }
        return (col - 1).coerceAtLeast(0)
    }
}
