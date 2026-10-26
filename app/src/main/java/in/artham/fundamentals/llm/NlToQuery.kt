package `in`.artham.fundamentals.llm

import `in`.artham.fundamentals.core.Logx
import `in`.artham.fundamentals.domain.MetricCatalog
import `in`.artham.fundamentals.domain.QueryCompiler
import `in`.artham.fundamentals.domain.QueryException
import kotlinx.coroutines.flow.toList

/**
 * Turns a sentence into a screen query.
 *
 * Two independent paths, in this order:
 *  1. A deterministic phrase parser that needs no model at all, so screening by
 *     description still works on a phone with no LLM installed.
 *  2. The local model, whose reply is compiled and rejected if it is not valid.
 *
 * A model reply is never trusted blindly: it must parse against the real metric
 * catalog before it is accepted.
 */
class NlToQuery(private val engineProvider: () -> LlmEngine) {

    sealed interface Outcome {
        data class Query(val query: String, val origin: String) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    private data class Alias(val text: String, val id: String)

    private val aliases: List<Alias> = MetricCatalog.all
        .flatMap { m -> (listOf(m.id, m.label.lowercase()) + m.aliases).map { Alias(it.lowercase(), m.id) } }
        .distinctBy { it.text }
        .sortedByDescending { it.text.length }

    private val phrases: List<Pair<String, String>> = listOf(
        "debt free" to "debt_to_equity < 0.3",
        "almost debt free" to "debt_to_equity < 0.3",
        "low debt" to "debt_to_equity < 0.5",
        "high debt" to "debt_to_equity > 1",
        "undervalued" to "pe < 20",
        "cheap" to "pe < 20",
        "expensive" to "pe > 40",
        "high roe" to "roe > 18",
        "high roce" to "roce > 18",
        "high growth" to "sales_growth_5y > 15",
        "fast growing" to "sales_growth_5y > 15",
        "dividend paying" to "dividend_yield > 2",
        "high dividend" to "dividend_yield > 3",
        "large cap" to "market_cap > 20000",
        "mid cap" to "market_cap between 5000 and 20000",
        "small cap" to "market_cap < 5000",
        "profitable" to "net_profit > 0",
        "loss making" to "net_profit < 0",
        "cash rich" to "cfo_to_op > 80",
        "quality" to "roe > 18 AND roce > 18",
        "consistent" to "sales_growth_5y > 10 AND profit_growth_5y > 10",
        "turnaround" to "net_profit > 0 AND q_profit_growth > 0",
        "promoter holding" to "promoter_holding > 50",
        "high margin" to "opm > 20"
    )

    private val comparators: List<Pair<String, String>> = listOf(
        "greater than or equal to" to ">=", "at least" to ">=", "minimum" to ">=", "min" to ">=",
        "less than or equal to" to "<=", "at most" to "<=", "maximum" to "<=", "max" to "<=",
        "greater than" to ">", "more than" to ">", "above" to ">", "over" to ">", "higher than" to ">", ">" to ">",
        "less than" to "<", "lower than" to "<", "below" to "<", "under" to "<", "<" to "<"
    )

    /** Model-first translation with the deterministic parser as the safety net. */
    suspend fun translate(question: String, config: LlmConfig): Outcome {
        val local = deterministic(question)
        val engine = engineProvider()
        val status = engine.status()
        if (status is EngineStatus.Ready) {
            val reply = try {
                engine.generate(PromptBuilder.SYSTEM, PromptBuilder.nlToQueryPrompt(question), config).toList().joinToString("")
            } catch (e: Exception) {
                Logx.w("NlToQuery", "Model translation failed: ${e.message}")
                ""
            }
            val candidate = PromptBuilder.extractQuery(reply)
            if (candidate.isNotBlank()) {
                val valid = try {
                    QueryCompiler.compile(candidate)
                    true
                } catch (e: QueryException) {
                    Logx.w("NlToQuery", "Model produced an invalid query '$candidate': ${e.message}")
                    false
                }
                if (valid) return Outcome.Query(candidate, "local model")
            }
        }
        return if (local != null) {
            Outcome.Query(local, "offline phrase parser")
        } else {
            Outcome.Failed(
                "I could not turn that into a screen. Try naming a metric and a number, for example: " +
                    "\"roe above 18 and debt to equity below 0.5\"."
            )
        }
    }

    /** Model-free parser. Returns null when nothing recognisable was found. */
    fun deterministic(question: String): String? {
        val text = question.lowercase().trim()
        if (text.isBlank()) return null
        val clauses = ArrayList<String>()

        for ((phrase, query) in phrases) {
            if (text.contains(phrase)) clauses.add(query)
        }

        val numberRegex = Regex("-?\\d+(?:\\.\\d+)?")
        val chunks = text.split(Regex("\\band\\b|,|;|\\bwith\\b|\\bplus\\b"))
        for (chunk in chunks) {
            val number = numberRegex.find(chunk)?.value?.toDoubleOrNull() ?: continue
            val comparator = comparators.firstOrNull { chunk.contains(it.first) }?.second ?: continue
            val metric = aliases.firstOrNull { chunk.contains(it.text) }?.id ?: continue
            clauses.add("$metric $comparator $number")
        }

        if (clauses.isEmpty()) return null
        val deduped = clauses.distinct()
        val query = deduped.joinToString(" AND ")
        return try {
            QueryCompiler.compile(query)
            query
        } catch (e: QueryException) {
            Logx.w("NlToQuery", "Deterministic query '$query' did not compile: ${e.message}")
            null
        }
    }
}
