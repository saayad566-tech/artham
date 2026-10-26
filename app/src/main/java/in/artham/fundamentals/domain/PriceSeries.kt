package `in`.artham.fundamentals.domain

import `in`.artham.fundamentals.data.AnnualFinancial

/** One daily candle. */
data class PriceBar(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)

data class SeriesStats(
    val first: Double,
    val last: Double,
    val change: Double,
    val changePercent: Double?,
    val periodHigh: Double,
    val periodLow: Double,
    val averageVolume: Long,
    val maxDrawdownPercent: Double?,
    val barCount: Int
)

/** One point of a price-to-earnings band. */
data class PePoint(val time: Long, val pe: Double)

/**
 * Chart and time-series maths over daily bars.
 *
 * All of it is pure functions on a list, so the arithmetic is unit-tested without a
 * device, a network or a chart.
 */
object PriceSeries {

    /** Simple moving average. The first `window - 1` points are null because they are undefined. */
    fun sma(bars: List<PriceBar>, window: Int): List<Double?> {
        if (window <= 0) return bars.map { null }
        val out = ArrayList<Double?>(bars.size)
        var sum = 0.0
        for (i in bars.indices) {
            sum += bars[i].close
            if (i >= window) sum -= bars[i - window].close
            out.add(if (i >= window - 1) sum / window else null)
        }
        return out
    }

    fun stats(bars: List<PriceBar>): SeriesStats? {
        if (bars.isEmpty()) return null
        val first = bars.first().close
        val last = bars.last().close
        var high = Double.NEGATIVE_INFINITY
        var low = Double.POSITIVE_INFINITY
        var volumeSum = 0L
        for (bar in bars) {
            if (bar.high > high) high = bar.high
            if (bar.low < low) low = bar.low
            volumeSum += bar.volume
        }
        return SeriesStats(
            first = first,
            last = last,
            change = last - first,
            changePercent = if (first > 0) (last - first) / first * 100.0 else null,
            periodHigh = high,
            periodLow = low,
            averageVolume = if (bars.isEmpty()) 0L else volumeSum / bars.size,
            maxDrawdownPercent = maxDrawdown(bars),
            barCount = bars.size
        )
    }

    /** Largest peak-to-trough fall, as a positive percentage. */
    fun maxDrawdown(bars: List<PriceBar>): Double? {
        if (bars.size < 2) return null
        var peak = bars.first().close
        var worst = 0.0
        for (bar in bars) {
            if (bar.close > peak) peak = bar.close
            if (peak > 0) {
                val fall = (peak - bar.close) / peak * 100.0
                if (fall > worst) worst = fall
            }
        }
        return worst
    }

    fun totalReturn(bars: List<PriceBar>): Double? {
        if (bars.size < 2) return null
        val first = bars.first().close
        if (first <= 0) return null
        return (bars.last().close / first - 1.0) * 100.0
    }

    /**
     * Price divided by the trailing annual EPS that had actually been published at that
     * date. A 120-day reporting lag is assumed, because a March year-end is not public
     * knowledge on 1 April. Without that lag every historical point would quietly use
     * results the market did not yet have.
     */
    fun peBand(bars: List<PriceBar>, annual: List<AnnualFinancial>): List<PePoint> {
        if (bars.isEmpty() || annual.isEmpty()) return emptyList()
        val sorted = annual.sortedBy { it.fyEnd }
        val out = ArrayList<PePoint>(bars.size)
        for (bar in bars) {
            val published = sorted.filter { row ->
                val fiscalEnd = fiscalYearEndMillis(row.fyEnd)
                fiscalEnd + REPORTING_LAG_MILLIS <= bar.time
            }.lastOrNull() ?: continue
            val eps = published.eps ?: continue
            if (eps <= 0.0) continue
            out.add(PePoint(bar.time, bar.close / eps))
        }
        return out
    }

    /** Summary of a PE band: where today's multiple sits against its own history. */
    fun pePercentile(band: List<PePoint>): Double? {
        if (band.size < 20) return null
        val current = band.last().pe
        val below = band.count { it.pe < current }
        return below.toDouble() / band.size * 100.0
    }

    fun medianPe(band: List<PePoint>): Double? {
        if (band.isEmpty()) return null
        val sorted = band.map { it.pe }.sorted()
        return sorted[sorted.size / 2]
    }

    /** Trims the series to the last N calendar days. */
    fun since(bars: List<PriceBar>, days: Int): List<PriceBar> {
        if (bars.isEmpty()) return bars
        val cutoff = bars.last().time - days.toLong() * 24 * 60 * 60 * 1000
        return bars.filter { it.time >= cutoff }
    }

    /** Down-samples to at most [maxPoints] by taking every nth bar, always keeping the last. */
    fun downsample(bars: List<PriceBar>, maxPoints: Int = 400): List<PriceBar> {
        if (bars.size <= maxPoints) return bars
        val step = (bars.size + maxPoints - 1) / maxPoints
        val out = ArrayList<PriceBar>(maxPoints + 1)
        var i = 0
        while (i < bars.size) {
            out.add(bars[i])
            i += step
        }
        if (out.last().time != bars.last().time) out.add(bars.last())
        return out
    }

    fun fiscalYearEndMillis(fyEnd: Int): Long = java.util.Calendar.getInstance().apply {
        clear()
        set(fyEnd, java.util.Calendar.MARCH, 31, 15, 30, 0)
    }.timeInMillis

    private const val REPORTING_LAG_MILLIS = 120L * 24 * 60 * 60 * 1000
}
