package `in`.artham.fundamentals.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import `in`.artham.fundamentals.core.Logx
import `in`.artham.fundamentals.domain.MetricCatalog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes a CSV the user can actually open in a spreadsheet, then hands it to the
 * system share sheet. Files go to the app cache and are shared through a FileProvider,
 * so nothing is written to shared storage without the user choosing a destination.
 */
object Export {

    private fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    fun screenToCsv(result: ScreenResult): String {
        val columns = result.hits.firstOrNull()?.values?.keys?.toList() ?: MetricCatalog.defaultColumns
        val sb = StringBuilder()
        sb.append("Code,Company")
        for (id in columns) sb.append(',').append(escape(MetricCatalog.get(id)?.label ?: id))
        sb.append('\n')
        for (hit in result.hits) {
            sb.append(escape(hit.company.code)).append(',').append(escape(hit.company.name))
            for (id in columns) {
                val value = hit.values[id]
                sb.append(',').append(if (value == null) "" else String.format(Locale.US, "%.4f", value))
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    fun annualToCsv(record: CompanyRecord): String {
        val sb = StringBuilder()
        sb.append("Company,").append(escape(record.company.name)).append('\n')
        sb.append("Code,").append(escape(record.company.code)).append('\n')
        sb.append("Units,rupees crore unless stated\n\n")
        val header = listOf(
            "Fiscal year", "Sales", "Expenses", "Operating profit", "OPM %", "Other income", "Interest",
            "Depreciation", "Profit before tax", "Tax %", "Net profit", "EPS", "Dividend",
            "Equity capital", "Reserves", "Borrowings", "Total liabilities", "Fixed assets", "CWIP",
            "Investments", "Total assets", "Receivables", "Inventory", "Payables",
            "Cash from operations", "Cash from investing", "Cash from financing", "Net cash flow"
        )
        sb.append(header.joinToString(",") { escape(it) }).append('\n')
        for (row in record.annual) {
            val values = listOf(
                row.fyEnd.toString(), row.sales, row.expenses, row.operatingProfit, row.opmPercent,
                row.otherIncome, row.interest, row.depreciation, row.profitBeforeTax, row.taxPercent,
                row.netProfit, row.eps, row.dividend, row.equityCapital, row.reserves, row.borrowings,
                row.totalLiabilities, row.fixedAssets, row.cwip, row.investments, row.totalAssets,
                row.receivables, row.inventory, row.payables, row.cfo, row.cfi, row.cff, row.netCashFlow
            )
            sb.append(values.joinToString(",") { v ->
                when (v) {
                    is String -> escape(v)
                    is Double -> String.format(Locale.US, "%.4f", v)
                    else -> ""
                }
            }).append('\n')
        }
        return sb.toString()
    }

    fun holdingsToCsv(summary: `in`.artham.fundamentals.domain.PortfolioSummary): String {
        val sb = StringBuilder()
        sb.append("Code,Company,Units,Average cost,Invested,Market value,Unrealised,Unrealised %,Realised,Dividends\n")
        for (h in summary.holdings) {
            sb.append(escape(h.company.code)).append(',')
                .append(escape(h.company.name)).append(',')
                .append(String.format(Locale.US, "%.4f", h.units)).append(',')
                .append(String.format(Locale.US, "%.4f", h.averageCost)).append(',')
                .append(String.format(Locale.US, "%.2f", h.invested)).append(',')
                .append(String.format(Locale.US, "%.2f", h.marketValue)).append(',')
                .append(String.format(Locale.US, "%.2f", h.unrealised)).append(',')
                .append(h.unrealisedPercent?.let { String.format(Locale.US, "%.2f", it) } ?: "").append(',')
                .append(String.format(Locale.US, "%.2f", h.realised)).append(',')
                .append(String.format(Locale.US, "%.2f", h.dividends)).append('\n')
        }
        return sb.toString()
    }

    /** Writes the payload and opens the share sheet. Returns the file that was written. */
    fun share(context: Context, suggestedName: String, mime: String, body: String): Result<File> = runCatching {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val safe = suggestedName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(dir, "$safe-$stamp.csv")
        file.writeText(body, Charsets.UTF_8)
        Logx.i("Export", "Wrote ${file.name} (${body.length} chars)")

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, suggestedName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export $safe").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        file
    }
}
