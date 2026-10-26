package `in`.artham.fundamentals.core

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/** Numeric formatting that follows Indian conventions (lakh / crore grouping). */
object Fmt {

    /** Indian digit grouping: 91,75,470 instead of 9,175,470. */
    fun indianGroup(value: Long): String {
        val neg = value < 0
        val digits = abs(value).toString()
        if (digits.length <= 3) return if (neg) "-$digits" else digits
        val last3 = digits.substring(digits.length - 3)
        var head = digits.substring(0, digits.length - 3)
        val sb = StringBuilder()
        while (head.length > 2) {
            sb.insert(0, "," + head.substring(head.length - 2))
            head = head.substring(0, head.length - 2)
        }
        if (head.isNotEmpty()) sb.insert(0, head)
        val grouped = sb.toString() + "," + last3
        return if (neg) "-$grouped" else grouped
    }

    /** Plain grouped number, used inside dense data tables where alignment matters. */
    fun plain(value: Long): String {
        val neg = value < 0
        val s = abs(value).toString()
        val sb = StringBuilder()
        for ((i, c) in s.withIndex()) {
            if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
            sb.append(c)
        }
        return if (neg) "-$sb" else sb.toString()
    }

    fun num(v: Double?, decimals: Int = 0, blank: String = "-"): String {
        if (v == null || v.isNaN() || v.isInfinite()) return blank
        val factor = 10.0.pow(decimals)
        val rounded = (v * factor).roundToLong() / factor
        return if (decimals == 0) plain(rounded.toLong())
        else String.format("%,.${decimals}f", rounded)
    }

    fun cr(v: Double?, decimals: Int = 0): String = num(v, decimals)

    fun pct(v: Double?, decimals: Int = 1): String {
        if (v == null || v.isNaN() || v.isInfinite()) return "-"
        return String.format("%.${decimals}f%%", v)
    }

    fun times(v: Double?, decimals: Int = 2): String {
        if (v == null || v.isNaN() || v.isInfinite()) return "-"
        return String.format("%.${decimals}fx", v)
    }

    fun days(v: Double?, decimals: Int = 0): String {
        if (v == null || v.isNaN() || v.isInfinite()) return "-"
        return String.format("%.${decimals}f d", v)
    }

    fun rupees(v: Double?, decimals: Int = 0): String {
        if (v == null || v.isNaN() || v.isInfinite()) return "-"
        return "\u20B9 " + num(v, decimals)
    }

    /** Compact Indian magnitude, e.g. 9,17,547 Cr. */
    fun croreCompact(v: Double?): String {
        if (v == null || v.isNaN() || v.isInfinite()) return "-"
        return when {
            abs(v) >= 100_000 -> indianGroup((v / 100_000).roundToLong()) + " L Cr"
            else -> indianGroup(v.roundToLong()) + " Cr"
        }
    }

    fun metric(v: Double?, unit: String): String = when (unit) {
        "PERCENT" -> pct(v)
        "TIMES" -> times(v)
        "DAYS" -> days(v)
        "RUPEE" -> rupees(v)
        "CRORE" -> cr(v)
        "SCORE" -> num(v, 0)
        else -> num(v, 2)
    }
}

/** Null-safe arithmetic. Every financial metric can legitimately be unavailable. */
object Num {
    fun div(a: Double?, b: Double?): Double? {
        if (a == null || b == null) return null
        if (b == 0.0 || b.isNaN() || b.isInfinite()) return null
        val r = a / b
        return if (r.isNaN() || r.isInfinite()) null else r
    }

    fun sub(a: Double?, b: Double?): Double? = if (a == null || b == null) null else a - b

    fun add(a: Double?, b: Double?): Double? = if (a == null || b == null) null else a + b

    fun mul(a: Double?, b: Double?): Double? = if (a == null || b == null) null else a * b

    /** Compound annual growth rate as a percentage. */
    fun cagr(begin: Double?, end: Double?, years: Int): Double? {
        if (begin == null || end == null || years <= 0) return null
        if (begin <= 0.0 || end <= 0.0) return null
        val r = (end / begin).pow(1.0 / years) - 1.0
        if (r.isNaN() || r.isInfinite()) return null
        return r * 100.0
    }

    /** Simple growth percentage, guarded against a zero/negative base. */
    fun growth(prev: Double?, curr: Double?): Double? {
        if (prev == null || curr == null || prev <= 0.0) return null
        return (curr - prev) / prev * 100.0
    }

    fun avg(values: List<Double?>): Double? {
        val present = values.filterNotNull()
        if (present.isEmpty()) return null
        return present.sum() / present.size
    }

    fun meanOfLast(values: List<Double?>, n: Int): Double? {
        val present = values.filterNotNull().takeLast(n)
        if (present.isEmpty()) return null
        return present.sum() / present.size
    }
}
