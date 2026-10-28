package `in`.artham.fundamentals.domain

data class Concentration(
    val herfindahl: Double,
    val effectiveHoldings: Double,
    val topWeightPercent: Double,
    val topFiveWeightPercent: Double,
    val topPositions: List<Pair<String, Double>>,
    val reading: String
)

data class Contribution(val company: String, val gain: Double, val sharePercent: Double)

/**
 * Concentration and contribution analysis for a portfolio.
 *
 * The Herfindahl index is the sum of squared weights; its reciprocal is the number of
 * equally-weighted positions that would carry the same concentration, which is far easier
 * to reason about than the raw index.
 */
object PortfolioAnalytics {

    fun concentration(summary: PortfolioSummary): Concentration? {
        val priced = summary.holdings.filter { it.marketValue != null && (it.marketValue ?: 0.0) > 0 }
        val total = priced.sumOf { it.marketValue ?: 0.0 }
        if (priced.isEmpty() || total <= 0) return null
        val weights = priced.map { (it.marketValue ?: 0.0) / total }
        val hhi = weights.sumOf { it * it }
        val effective = if (hhi > 0) 1.0 / hhi else 0.0
        val sorted = priced.sortedByDescending { it.marketValue ?: 0.0 }
        val top = sorted.first().let { it.company.code to (it.marketValue ?: 0.0) / total * 100.0 }
        val topFive = sorted.take(5).sumOf { (it.marketValue ?: 0.0) } / total * 100.0
        val reading = when {
            priced.size < 3 -> "Too few positions to judge concentration."
            effective < 3 -> "Highly concentrated: the portfolio behaves like roughly ${"%.1f".format(effective)} equal positions."
            effective < 8 -> "Concentrated but workable: about ${"%.1f".format(effective)} equal-sized positions."
            else -> "Well spread: about ${"%.0f".format(effective)} equal-sized positions."
        }
        return Concentration(
            herfindahl = hhi,
            effectiveHoldings = effective,
            topWeightPercent = top.second,
            topFiveWeightPercent = topFive,
            topPositions = sorted.take(5).map { it.company.code to (it.marketValue ?: 0.0) / total * 100.0 },
            reading = reading
        )
    }

    fun contributions(summary: PortfolioSummary): List<Contribution> {
        val priced = summary.holdings.filter { it.unrealised != null }
        val totalGain = priced.sumOf { it.unrealised ?: 0.0 }
        if (priced.isEmpty()) return emptyList()
        return priced
            .sortedByDescending { it.unrealised ?: 0.0 }
            .map { holding ->
                val gain = holding.unrealised ?: 0.0
                Contribution(
                    company = holding.company.code,
                    gain = gain,
                    sharePercent = if (totalGain != 0.0) gain / totalGain * 100.0 else 0.0
                )
            }
    }

    /** Dividends received as a percentage of what is still invested. */
    fun yieldOnCost(summary: PortfolioSummary): Double? {
        if (summary.invested <= 0 || summary.dividends <= 0) return null
        return summary.dividends / summary.invested * 100.0
    }

    /** Realised profit as a share of everything the investor has put in. */
    fun realisedToInvested(summary: PortfolioSummary): Double? {
        if (summary.invested <= 0) return null
        return summary.realised / summary.invested * 100.0
    }
}
