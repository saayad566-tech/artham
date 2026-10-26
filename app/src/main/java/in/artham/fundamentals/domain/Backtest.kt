package `in`.artham.fundamentals.domain

import `in`.artham.fundamentals.core.Logx
import `in`.artham.fundamentals.core.Num
import `in`.artham.fundamentals.data.Company
import `in`.artham.fundamentals.data.CompanyRecord
import java.util.Calendar

/** What one historical year of a screen looked like. */
data class BacktestYear(
    val fyEnd: Int,
    val matched: List<Company>,
    val universe: Int
)

data class BacktestOutcome(val label: String, val value: Double?, val unit: String, val detail: String)

data class BacktestResult(
    val query: String,
    val years: List<BacktestYear>,
    val fundamental: List<BacktestOutcome>,
    val price: List<BacktestOutcome>,
    val warnings: List<String>,
    val error: String? = null
) {
    val totalMatches: Int get() = years.sumOf { it.matched.size }
    val averageMatches: Double get() = if (years.isEmpty()) 0.0 else totalMatches.toDouble() / years.size
}

/**
 * Point-in-time backtesting.
 *
 * The rule that makes this honest: when the screen is evaluated for fiscal year Y, the
 * company record is truncated to periods ending on or before Y, and the current quote is
 * removed. A metric that needs a price therefore returns null for historical years
 * instead of silently using today's price, which is the single most common way a
 * backtest lies to you.
 *
 * Two separate questions are answered:
 *  - fundamentals: did the businesses selected in the earliest year actually grow?
 *  - price: if price history has been imported, what did an equal-weight holding return?
 */
object Backtester {

    fun run(
        query: String,
        records: List<CompanyRecord>,
        fromYear: Int? = null,
        toYear: Int? = null,
        priceLookup: ((companyId: Long, epochMillis: Long) -> Double?)? = null
    ): BacktestResult {
        val compiled = try {
            QueryCompiler.compile(query)
        } catch (e: QueryException) {
            return BacktestResult(query, emptyList(), emptyList(), emptyList(), emptyList(), e.message)
        }
        val expr = compiled.first
        val refs = compiled.second
        val defs = refs.mapNotNull { MetricCatalog.get(it) }
        val priceMetrics = setOf("price", "pe", "pb", "market_cap", "dividend_yield", "earnings_yield", "high_52", "low_52", "pct_from_high", "book_value", "graham_number", "margin_of_safety", "intrinsic_value")
        val usesPrice = refs.any { it in priceMetrics }

        val availableYears = records.flatMap { r -> r.annual.map { it.fyEnd } }.distinct().sorted()
        if (availableYears.isEmpty()) {
            return BacktestResult(query, emptyList(), emptyList(), emptyList(),
                listOf("No annual data is stored, so there is nothing to backtest."), null)
        }
        val start = (fromYear ?: availableYears.first()).coerceIn(availableYears.first(), availableYears.last())
        val end = (toYear ?: availableYears.last()).coerceIn(start, availableYears.last())

        val warnings = ArrayList<String>()
        if (usesPrice) {
            warnings.add(
                "This screen uses a price-based metric. Quotes are removed for historical years, " +
                    "so those conditions are treated as unknown and the screen will match fewer companies."
            )
        }

        val years = ArrayList<BacktestYear>()
        for (year in start..end) {
            if (year !in availableYears) continue
            val matched = ArrayList<Company>()
            var universe = 0
            for (record in records) {
                val pointInTime = record.copy(
                    annual = record.annual.filter { it.fyEnd <= year },
                    quarterly = record.quarterly.filter { it.fyEnd <= year },
                    shareholding = record.shareholding.filter { it.sortKey <= year * 4 + 4 },
                    quote = null
                )
                val financials = Financials(pointInTime)
                if (!financials.hasData) continue
                universe++
                val values = HashMap<String, Double?>(defs.size * 2)
                for (def in defs) {
                    values[def.id] = try {
                        def.compute(financials)
                    } catch (e: Exception) {
                        Logx.w("Backtest", "Metric ${def.id} failed for ${record.company.code} in FY$year")
                        null
                    }
                }
                if (QueryEvaluator.eval(expr, values)) matched.add(record.company)
            }
            years.add(BacktestYear(year, matched, universe))
        }

        val fundamental = ArrayList<BacktestOutcome>()
        val firstCohort = years.firstOrNull()
        if (firstCohort != null && firstCohort.matched.isNotEmpty()) {
            val ids = firstCohort.matched.map { it.id }.toSet()
            val salesCagrs = records.filter { it.company.id in ids }.mapNotNull { record ->
                val startRow = record.annual.firstOrNull { it.fyEnd == firstCohort.fyEnd }
                val endRow = record.annual.lastOrNull()
                if (startRow == null || endRow == null) null
                else Num.cagr(startRow.sales, endRow.sales, endRow.fyEnd - startRow.fyEnd)
            }
            val profitCagrs = records.filter { it.company.id in ids }.mapNotNull { record ->
                val startRow = record.annual.firstOrNull { it.fyEnd == firstCohort.fyEnd }
                val endRow = record.annual.lastOrNull()
                if (startRow == null || endRow == null) null
                else Num.cagr(startRow.netProfit, endRow.netProfit, endRow.fyEnd - startRow.fyEnd)
            }
            fundamental.add(
                BacktestOutcome(
                    "Median sales CAGR after selection", median(salesCagrs), "PERCENT",
                    "From FY${firstCohort.fyEnd} to each company's latest year, for the ${firstCohort.matched.size} companies this screen picked then."
                )
            )
            fundamental.add(
                BacktestOutcome(
                    "Median profit CAGR after selection", median(profitCagrs), "PERCENT",
                    "Same cohort. A screen that finds growing businesses should show a positive number here."
                )
            )
        }

        val price = ArrayList<BacktestOutcome>()
        if (priceLookup == null) {
            warnings.add(
                "No price history is stored, so returns cannot be computed. The figures below describe " +
                    "the businesses that were selected, not the share price you would have paid."
            )
        } else if (firstCohort != null && firstCohort.matched.isNotEmpty()) {
            val startMillis = fiscalYearEndMillis(firstCohort.fyEnd)
            val endMillis = fiscalYearEndMillis(availableYears.last())
            val returns = firstCohort.matched.mapNotNull { company ->
                val buy = priceLookup(company.id, startMillis) ?: return@mapNotNull null
                val sell = priceLookup(company.id, endMillis) ?: return@mapNotNull null
                if (buy <= 0.0) null else (sell / buy - 1.0) * 100.0
            }
            price.add(
                BacktestOutcome(
                    "Equal-weight total return", if (returns.isEmpty()) null else returns.average(), "PERCENT",
                    "Buying every match at the FY${firstCohort.fyEnd} close and holding to FY${availableYears.last()}, " +
                        "averaged across ${returns.size} companies with price data."
                )
            )
            price.add(
                BacktestOutcome(
                    "Share of matches that made money", if (returns.isEmpty()) null else returns.count { it > 0 }.toDouble() / returns.size * 100.0, "PERCENT",
                    "Hit rate of the cohort."
                )
            )
        }

        if (years.any { it.matched.isEmpty() }) {
            warnings.add("Some years matched nothing. That is information, not a bug: the screen was too strict for those years.")
        }
        warnings.add(
            "This is a historical simulation on the data stored in this app. It ignores survivorship in your " +
                "dataset, transaction costs, taxes and dividends unless price history includes them."
        )

        return BacktestResult(query, years, fundamental, price, warnings, null)
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    /** 31 March of the fiscal year, as epoch millis. */
    fun fiscalYearEndMillis(fyEnd: Int): Long = Calendar.getInstance().apply {
        clear()
        set(fyEnd, Calendar.MARCH, 31, 15, 30, 0)
    }.timeInMillis
}
