package `in`.artham.fundamentals.llm

import `in`.artham.fundamentals.core.Fmt
import `in`.artham.fundamentals.data.CompanyRecord
import `in`.artham.fundamentals.data.ScreenResult
import `in`.artham.fundamentals.domain.Financials
import `in`.artham.fundamentals.domain.Insight
import `in`.artham.fundamentals.domain.MetricCatalog
import `in`.artham.fundamentals.domain.Ratios

/**
 * Builds the prompt for a local model. Everything the model is allowed to use is
 * placed in the prompt as explicit, already-computed numbers, so a small on-device
 * model does not have to do arithmetic (which it is bad at) and cannot invent
 * figures it was never given.
 */
object PromptBuilder {

    const val SYSTEM = """You are Artham, a careful equity research assistant that runs entirely on the user's device.
Rules you must follow:
1. Use only the figures in the CONTEXT block. Never invent a number, a date or a company fact.
2. If the context does not contain what is needed, say exactly what is missing.
3. Show the arithmetic when you compare or combine numbers.
4. Be concise: at most 200 words unless asked for more.
5. Never give a buy, sell or hold recommendation and never predict a price. You explain what the reported numbers show.
6. End with one sentence naming the main risk visible in the data."""

    private val contextMetrics = listOf(
        "market_cap", "price", "pe", "pb", "book_value", "dividend_yield", "eps",
        "sales", "net_profit", "operating_profit", "opm", "roe", "roce", "roa",
        "sales_growth", "profit_growth", "sales_growth_5y", "profit_growth_5y",
        "debt_to_equity", "interest_coverage", "cfo", "fcf", "cfo_to_op",
        "debtor_days", "inventory_days", "ccc", "piotroski", "graham_number",
        "intrinsic_value", "margin_of_safety", "promoter_holding", "fii_holding", "dii_holding"
    )

    fun companyContext(record: CompanyRecord, insights: List<Insight>): String {
        val f = Financials(record)
        val a = f.latest
        val sb = StringBuilder()
        sb.appendLine("CONTEXT")
        sb.appendLine("Company: ${record.company.name} (${record.company.code})")
        if (record.company.sector.isNotBlank()) sb.appendLine("Sector: ${record.company.sector} / ${record.company.industry}")
        if (record.company.isDemo) sb.appendLine("NOTE: this is a synthetic demo entity, not a real company.")
        sb.appendLine("Latest period: FY${a?.fyEnd ?: "unknown"} (values in Rs crore unless stated)")

        sb.appendLine()
        sb.appendLine("Annual history:")
        for (row in f.annual.takeLast(6)) {
            sb.appendLine(
                "  FY${row.fyEnd}: sales ${Fmt.num(row.sales)}, operating profit ${Fmt.num(row.operatingProfit)}, " +
                    "OPM ${Fmt.pct(row.opmPercent)}, net profit ${Fmt.num(row.netProfit)}, " +
                    "EPS ${Fmt.num(row.eps, 2)}, borrowings ${Fmt.num(row.borrowings)}, " +
                    "CFO ${Fmt.num(row.cfo)}"
            )
        }

        if (f.quarterly.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Recent quarters:")
            for (q in f.quarterly.takeLast(4)) {
                sb.appendLine("  ${q.label}: sales ${Fmt.num(q.sales)}, net profit ${Fmt.num(q.netProfit)}, OPM ${Fmt.pct(q.opmPercent)}")
            }
        }

        sb.appendLine()
        sb.appendLine("Computed ratios:")
        for (id in contextMetrics) {
            val def = MetricCatalog.get(id) ?: continue
            val value = runCatching { def.compute(f) }.getOrNull() ?: continue
            sb.appendLine("  ${def.label}: ${Fmt.metric(value, def.unit.key)}")
        }

        val sh = f.latestShareholding
        if (sh != null) {
            sb.appendLine("  Shareholding (${sh.label}): promoters ${Fmt.pct(sh.promoters)}, FII ${Fmt.pct(sh.fii)}, DII ${Fmt.pct(sh.dii)}, public ${Fmt.pct(sh.publicHolding)}")
        }

        if (insights.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Checklist observations produced by the app:")
            for (i in insights) sb.appendLine("  - ${if (i.positive) "[positive]" else "[concern]"} ${i.text}")
        }
        return sb.toString()
    }

    fun companyPrompt(record: CompanyRecord, insights: List<Insight>, question: String): String = buildString {
        appendLine(companyContext(record, insights))
        appendLine()
        appendLine("QUESTION")
        appendLine(question.trim())
        appendLine()
        appendLine("Answer using only the context above. Show the numbers you used.")
    }

    fun screenContext(result: ScreenResult): String {
        val sb = StringBuilder()
        sb.appendLine("CONTEXT")
        sb.appendLine("A screen was run with the query: ${result.query}")
        sb.appendLine("${result.hits.size} companies matched out of ${result.evaluated} evaluated.")
        sb.appendLine()
        sb.appendLine("Matches (values are in Rs crore or percent as labelled):")
        for (hit in result.hits.take(25)) {
            val values = hit.values.entries.joinToString(", ") { (k, v) ->
                val def = MetricCatalog.get(k)
                "$k=${if (def != null) Fmt.metric(v, def.unit.key) else Fmt.num(v, 2)}"
            }
            sb.appendLine("  ${hit.company.name} (${hit.company.code}): $values")
        }
        if (result.hits.size > 25) sb.appendLine("  ...and ${result.hits.size - 25} more not shown.")
        return sb.toString()
    }

    fun screenPrompt(result: ScreenResult, question: String): String = buildString {
        appendLine(screenContext(result))
        appendLine()
        appendLine("QUESTION")
        appendLine(question.trim())
        appendLine()
        appendLine("Answer using only the context above.")
    }

    fun nlToQueryPrompt(question: String): String = buildString {
        appendLine("Translate the user's request into a stock-screen query.")
        appendLine()
        appendLine("AVAILABLE METRICS (use these exact identifiers):")
        appendLine(MetricCatalog.all.joinToString(", ") { it.id })
        appendLine()
        appendLine("SYNTAX: comparisons with > < >= <= = !=, combine with AND / OR, negate with NOT,")
        appendLine("group with parentheses, and use BETWEEN low AND high for ranges.")
        appendLine("Example: sales > 1000 AND roe > 15 AND debt_to_equity < 0.5")
        appendLine()
        appendLine("USER REQUEST: ${question.trim()}")
        appendLine()
        appendLine("Reply with the query only. No explanation, no code fences.")
    }

    /** Turns the model's reply into a bare query string. */
    fun extractQuery(reply: String): String {
        var text = reply.trim()
        text = text.removePrefix("```sql").removePrefix("```")
        text = text.removeSuffix("```")
        text = text.substringBefore('\n').ifBlank { text }
        if (text.contains("```")) text = text.substringBefore("```")
        return text.trim().trim('`').trim()
    }

    /** Compact snapshot used by the company overview tab. */
    fun quickFacts(record: CompanyRecord): List<Pair<String, String>> {
        val f = Financials(record)
        val a = f.latest ?: return emptyList()
        return listOfNotNull(
            "Market cap" to Fmt.croreCompact(Ratios.marketCap(f)),
            "Price" to Fmt.rupees(f.quote?.price),
            "P/E" to Fmt.times(Ratios.pe(f)),
            "Book value" to Fmt.rupees(Ratios.bookValuePerShare(f)),
            "ROE" to Fmt.pct(Ratios.roe(a)),
            "ROCE" to Fmt.pct(Ratios.roce(a)),
            "OPM" to Fmt.pct(Ratios.opm(a)),
            "Debt/Equity" to Fmt.times(Ratios.debtToEquity(a)),
            "Dividend yield" to Fmt.pct(Ratios.dividendYield(f)),
            "Piotroski" to (Ratios.piotroski(f)?.toString() ?: "-")
        )
    }
}
