package `in`.artham.fundamentals.domain

import kotlin.math.abs
import kotlin.math.sqrt

data class MacdPoint(val time: Long, val macd: Double, val signal: Double, val histogram: Double)
data class BollingerPoint(val time: Long, val middle: Double, val upper: Double, val lower: Double)
data class TechnicalSnapshot(
    val rsi14: Double?,
    val macd: Double?,
    val macdSignal: Double?,
    val macdHistogram: Double?,
    val bollingerUpper: Double?,
    val bollingerLower: Double?,
    val atr14: Double?,
    val momentum6m: Double?,
    val momentum12m: Double?,
    val distanceFromSma200: Double?,
    val annualisedVolatility: Double?,
    val volumeSpikeRatio: Double?
)

/**
 * Standard technical indicators over daily bars.
 *
 * Formulas follow the conventional definitions (Wilder's RSI and ATR, EMA-based MACD,
 * 20-period Bollinger at two standard deviations) so a number here matches what any
 * charting package shows for the same series. Every indicator returns null where the
 * series is too short rather than emitting a half-formed value.
 */
object Technical {

    /** Wilder's RSI. First value appears at index `period`. */
    fun rsi(bars: List<PriceBar>, period: Int = 14): List<Double?> {
        val out = ArrayList<Double?>(bars.size)
        if (bars.size <= period) return bars.map { null }
        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..period) {
            val change = bars[i].close - bars[i - 1].close
            if (change >= 0) gainSum += change else lossSum -= change
        }
        var avgGain = gainSum / period
        var avgLoss = lossSum / period
        for (i in 0 until bars.size) {
            if (i <= period) {
                out.add(null)
            } else {
                val change = bars[i].close - bars[i - 1].close
                val gain = if (change > 0) change else 0.0
                val loss = if (change < 0) -change else 0.0
                avgGain = (avgGain * (period - 1) + gain) / period
                avgLoss = (avgLoss * (period - 1) + loss) / period
                out.add(if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + avgGain / avgLoss))
            }
        }
        return out
    }

    private fun ema(values: List<Double>, period: Int): List<Double?> {
        val out = ArrayList<Double?>(values.size)
        val k = 2.0 / (period + 1.0)
        var current: Double? = null
        for (i in values.indices) {
            current = if (current == null) {
                if (i >= period - 1) values.take(i + 1).average() else null
            } else {
                values[i] * k + current * (1 - k)
            }
            out.add(current)
        }
        return out
    }

    fun macd(bars: List<PriceBar>, fast: Int = 12, slow: Int = 26, signalPeriod: Int = 9): List<MacdPoint?> {
        if (bars.size < slow + signalPeriod) return bars.map { null }
        val closes = bars.map { it.close }
        val fastEma = ema(closes, fast)
        val slowEma = ema(closes, slow)
        val macdLine = ArrayList<Double?>(closes.size)
        for (i in closes.indices) {
            val f = fastEma[i]
            val s = slowEma[i]
            macdLine.add(if (f != null && s != null) f - s else null)
        }
        val present = macdLine.filterNotNull()
        if (present.size < signalPeriod) return bars.map { null }
        val signalOnPresent = ema(present, signalPeriod)
        val out = ArrayList<MacdPoint?>(bars.size)
        var signalIndex = 0
        for (i in macdLine.indices) {
            val macdValue = macdLine[i]
            if (macdValue == null) {
                out.add(null)
            } else {
                val signal = signalOnPresent.getOrNull(signalIndex)
                signalIndex++
                out.add(
                    if (signal == null) null
                    else MacdPoint(bars[i].time, macdValue, signal, macdValue - signal)
                )
            }
        }
        return out
    }

    fun bollinger(bars: List<PriceBar>, period: Int = 20, deviations: Double = 2.0): List<BollingerPoint?> {
        val out = ArrayList<BollingerPoint?>(bars.size)
        for (i in bars.indices) {
            if (i < period - 1) {
                out.add(null)
                continue
            }
            val window = bars.subList(i - period + 1, i + 1).map { it.close }
            val mean = window.average()
            val variance = window.sumOf { (it - mean) * (it - mean) } / window.size
            val sd = sqrt(variance)
            out.add(BollingerPoint(bars[i].time, mean, mean + deviations * sd, mean - deviations * sd))
        }
        return out
    }

    /** Wilder's average true range. */
    fun atr(bars: List<PriceBar>, period: Int = 14): List<Double?> {
        if (bars.size <= period) return bars.map { null }
        val trueRanges = ArrayList<Double>(bars.size)
        trueRanges.add(bars[0].high - bars[0].low)
        for (i in 1 until bars.size) {
            val previousClose = bars[i - 1].close
            val tr = maxOf(
                bars[i].high - bars[i].low,
                abs(bars[i].high - previousClose),
                abs(bars[i].low - previousClose)
            )
            trueRanges.add(tr)
        }
        val out = ArrayList<Double?>(bars.size)
        var current: Double? = null
        for (i in trueRanges.indices) {
            current = if (i < period - 1) {
                null
            } else if (i == period - 1) {
                trueRanges.take(period).average()
            } else {
                (current!! * (period - 1) + trueRanges[i]) / period
            }
            out.add(current)
        }
        return out
    }

    /** Percentage change over roughly [tradingDays] sessions. */
    fun momentum(bars: List<PriceBar>, tradingDays: Int): Double? {
        if (bars.size <= tradingDays) return null
        val then = bars[bars.size - 1 - tradingDays].close
        val now = bars.last().close
        if (then <= 0) return null
        return (now / then - 1.0) * 100.0
    }

    /** Annualised standard deviation of daily log-ish returns, as a percentage. */
    fun annualisedVolatility(bars: List<PriceBar>, window: Int = 250): Double? {
        if (bars.size < 30) return null
        val slice = bars.takeLast(window)
        val returns = ArrayList<Double>(slice.size)
        for (i in 1 until slice.size) {
            val prev = slice[i - 1].close
            if (prev > 0) returns.add((slice[i].close - prev) / prev)
        }
        if (returns.size < 20) return null
        val mean = returns.average()
        val variance = returns.sumOf { (it - mean) * (it - mean) } / returns.size
        return sqrt(variance) * sqrt(252.0) * 100.0
    }

    /** How far the last close sits from its own moving average, in percent. */
    fun distanceFromSma(bars: List<PriceBar>, window: Int): Double? {
        if (bars.size < window) return null
        val average = bars.takeLast(window).map { it.close }.average()
        if (average <= 0) return null
        return (bars.last().close / average - 1.0) * 100.0
    }

    /** Last session's volume against its own recent average. */
    fun volumeSpike(bars: List<PriceBar>, window: Int = 20): Double? {
        if (bars.size < window + 1) return null
        val average = bars.dropLast(1).takeLast(window).map { it.volume }.average()
        if (average <= 0) return null
        return bars.last().volume / average
    }

    /** Everything the technical tab needs, computed once. */
    fun snapshot(bars: List<PriceBar>): TechnicalSnapshot {
        val rsi = rsi(bars)
        val macdSeries = macd(bars)
        val boll = bollinger(bars)
        val atr = atr(bars)
        val lastMacd = macdSeries.lastOrNull { it != null }
        val lastBoll = boll.lastOrNull { it != null }
        return TechnicalSnapshot(
            rsi14 = rsi.lastOrNull { it != null },
            macd = lastMacd?.macd,
            macdSignal = lastMacd?.signal,
            macdHistogram = lastMacd?.histogram,
            bollingerUpper = lastBoll?.upper,
            bollingerLower = lastBoll?.lower,
            atr14 = atr.lastOrNull { it != null },
            momentum6m = momentum(bars, 126),
            momentum12m = momentum(bars, 252),
            distanceFromSma200 = distanceFromSma(bars, 200),
            annualisedVolatility = annualisedVolatility(bars),
            volumeSpikeRatio = volumeSpike(bars)
        )
    }

    /** Plain-language reading of an RSI, used in the UI and in prompts. */
    fun rsiLabel(value: Double?): String = when {
        value == null -> "not enough history"
        value >= 70 -> "overbought"
        value <= 30 -> "oversold"
        value >= 55 -> "firm"
        value <= 45 -> "soft"
        else -> "neutral"
    }
}
