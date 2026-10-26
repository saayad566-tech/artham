package `in`.artham.fundamentals.domain

import `in`.artham.fundamentals.core.Logx
import `in`.artham.fundamentals.data.CompanyRecord
import `in`.artham.fundamentals.data.ScreenHit
import `in`.artham.fundamentals.data.ScreenResult

/**
 * Runs a compiled query across the local dataset. Evaluation happens entirely on
 * the device against stored rows: no network, no server, no rate limit.
 */
class ScreenEngine(private val catalog: MetricCatalog = MetricCatalog) {

    fun run(query: String, records: List<CompanyRecord>, limit: Int = 500): ScreenResult {
        val started = System.currentTimeMillis()
        val compiled = try {
            QueryCompiler.compile(query)
        } catch (e: QueryException) {
            return ScreenResult(query, emptyList(), 0, 0, System.currentTimeMillis() - started, e.message)
        }
        val expr = compiled.first
        val refs = compiled.second
        val defs = refs.mapNotNull { catalog.get(it) }
        val hits = ArrayList<ScreenHit>()
        var evaluated = 0
        var skipped = 0
        for (record in records) {
            val financials = Financials(record)
            if (!financials.hasData) { skipped++; continue }
            evaluated++
            val values = HashMap<String, Double?>(defs.size * 2)
            for (def in defs) {
                values[def.id] = try {
                    def.compute(financials)
                } catch (e: Exception) {
                    Logx.w("Screen", "Metric ${def.id} failed for ${record.company.code}: ${e.message}")
                    null
                }
            }
            if (QueryEvaluator.eval(expr, values)) {
                hits.add(ScreenHit(record.company, values))
            }
        }
        val sorted = hits.sortedWith(
            compareByDescending<ScreenHit> { it.values[refs.firstOrNull() ?: ""] ?: Double.NEGATIVE_INFINITY }
                .thenBy { it.company.name }
        ).take(limit)
        val duration = System.currentTimeMillis() - started
        Logx.i("Screen", "'$query' matched ${sorted.size} of $evaluated companies in ${duration}ms")
        return ScreenResult(query, sorted, evaluated, skipped, duration)
    }

    /** Computes one metric for one company, used by the company page and the AI context builder. */
    fun metric(record: CompanyRecord, id: String): Double? =
        catalog.get(id)?.compute(Financials(record))

    fun metricValues(record: CompanyRecord, ids: List<String>): Map<String, Double?> {
        val f = Financials(record)
        return ids.associateWith { id -> runCatching { catalog.get(id)?.compute(f) }.getOrNull() }
    }
}
