package `in`.artham.fundamentals.domain

import `in`.artham.fundamentals.core.Num
import kotlin.math.pow

data class DcfInputs(
    val growthRate: Double = 12.0,
    val years: Int = 5,
    val discountRate: Double = 13.0,
    val terminalGrowth: Double = 4.0
)

data class DcfResult(
    val valuePerShare: Double,
    val equityValue: Double,
    val pvExplicit: Double,
    val pvTerminal: Double,
    val baseFreeCashFlow: Double,
    val netDebt: Double,
    val shares: Double,
    val price: Double?,
    val marginOfSafetyPercent: Double?,
    val terminalSharePercent: Double,
    val inputs: DcfInputs
)

data class SensitivityRow(val discountRate: Double, val values: List<Double?>)

data class RelativeValuation(
    val peerCount: Int,
    val pePercentile: Double?,
    val pbPercentile: Double?,
    val medianPe: Double?,
    val medianPb: Double?,
    val verdict: String
)

/**
 * Valuation models with every input exposed.
 *
 * A discounted cash flow is an argument, not an answer: the output moves further for
 * a one-point change in the discount rate than for almost any change in the business.
 * That is why [sensitivity] exists and why the UI always shows the inputs next to the
 * result.
 */
object Valuation {

    fun dcf(f: Financials, inputs: DcfInputs): DcfResult? {
        val a = f.latest ?: return null
        val fcf = Ratios.freeCashFlow(a) ?: return null
        if (fcf <= 0.0) return null
        val shares = f.sharesOutstanding() ?: return null
        if (shares <= 0.0) return null
        val g = inputs.growthRate / 100.0
        val r = inputs.discountRate / 100.0
        val tg = inputs.terminalGrowth / 100.0
        if (r <= tg) return null

        var pvExplicit = 0.0
        var cash = fcf
        for (t in 1..inputs.years) {
            cash *= (1 + g)
            pvExplicit += cash / (1 + r).pow(t.toDouble())
        }
        val terminal = cash * (1 + tg) / (r - tg)
        val pvTerminal = terminal / (1 + r).pow(inputs.years.toDouble())
        val netDebt = a.borrowings ?: 0.0
        val equityValue = pvExplicit + pvTerminal - netDebt
        val perShare = equityValue / shares
        val price = f.quote?.price
        return DcfResult(
            valuePerShare = perShare,
            equityValue = equityValue,
            pvExplicit = pvExplicit,
            pvTerminal = pvTerminal,
            baseFreeCashFlow = fcf,
            netDebt = netDebt,
            shares = shares,
            price = price,
            marginOfSafetyPercent = Ratios.marginOfSafety(perShare, price),
            terminalSharePercent = if (equityValue != 0.0) pvTerminal / equityValue * 100.0 else 0.0,
            inputs = inputs
        )
    }

    /**
     * Value per share across a grid of discount rates and growth rates. Reading a
     * single DCF number without this is the most common valuation mistake.
     */
    fun sensitivity(
        f: Financials,
        base: DcfInputs,
        discountSteps: List<Double> = listOf(-3.0, -1.5, 0.0, 1.5, 3.0),
        growthSteps: List<Double> = listOf(-4.0, -2.0, 0.0, 2.0, 4.0)
    ): Pair<List<Double>, List<SensitivityRow>> {
        val growthAxis = growthSteps.map { base.growthRate + it }
        val rows = discountSteps.map { discountDelta ->
            val discount = base.discountRate + discountDelta
            val values = growthAxis.map { growth ->
                dcf(f, base.copy(growthRate = growth, discountRate = discount))?.valuePerShare
            }
            SensitivityRow(discount, values)
        }
        return growthAxis to rows
    }

    /**
     * Reverse DCF: the growth rate the current price already implies.
     *
     * This is the more useful direction of the model. Instead of asking what a company
     * is worth, it asks what the market must believe, which is a question you can
     * actually disagree with.
     */
    fun reverseDcf(f: Financials, base: DcfInputs): Double? {
        val price = f.quote?.price ?: return null
        if (price <= 0.0) return null
        var low = -30.0
        var high = 80.0
        val atLow = dcf(f, base.copy(growthRate = low))?.valuePerShare ?: return null
        val atHigh = dcf(f, base.copy(growthRate = high))?.valuePerShare ?: return null
        if (atLow > price) return low
        if (atHigh < price) return null

        repeat(80) {
            val mid = (low + high) / 2.0
            val value = dcf(f, base.copy(growthRate = mid))?.valuePerShare ?: return null
            if (value < price) low = mid else high = mid
            if (high - low < 1e-6) return (low + high) / 2.0
        }
        return (low + high) / 2.0
    }

    /** Where a company sits on P/E and P/B against its own sector. */
    fun relative(f: Financials, peers: List<Financials>): RelativeValuation {
        val pe = Ratios.pe(f)
        val pb = Ratios.pb(f)
        val peerPes = peers.mapNotNull { Ratios.pe(it) }.sorted()
        val peerPbs = peers.mapNotNull { Ratios.pb(it) }.sorted()
        fun percentile(value: Double?, series: List<Double>): Double? {
            if (value == null || series.isEmpty()) return null
            val below = series.count { it < value }
            return below.toDouble() / series.size * 100.0
        }
        fun median(series: List<Double>): Double? =
            if (series.isEmpty()) null else series[series.size / 2]

        val pePercentile = percentile(pe, peerPes)
        val pbPercentile = percentile(pb, peerPbs)
        val verdict = when {
            peerPes.size < 2 -> "Not enough peers to rank this company."
            pePercentile == null -> "No price to earnings figure available."
            pePercentile <= 25 -> "Cheaper than three quarters of its sector on earnings."
            pePercentile >= 75 -> "Dearer than three quarters of its sector on earnings."
            else -> "Mid-pack against its sector on earnings."
        }
        return RelativeValuation(
            peerCount = peers.size,
            pePercentile = pePercentile,
            pbPercentile = pbPercentile,
            medianPe = median(peerPes),
            medianPb = median(peerPbs),
            verdict = verdict
        )
    }

    /** Compound annual growth needed for the DCF value to equal the price, as a sanity check. */
    fun impliedExitMultiple(f: Financials, base: DcfInputs): Double? {
        val result = dcf(f, base) ?: return null
        val a = f.latest ?: return null
        val ebitda = a.operatingProfit ?: return null
        if (ebitda <= 0.0) return null
        val terminalValue = result.pvTerminal * (1 + base.discountRate / 100.0).pow(base.years.toDouble())
        return terminalValue / ebitda
    }

    @Suppress("unused")
    private fun growthGuard(value: Double?): Double? = Num.div(value, 1.0)
}
