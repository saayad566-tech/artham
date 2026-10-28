package `in`.artham.fundamentals.domain

import kotlin.math.ln
import kotlin.math.pow

data class TaxResult(
    val gain: Double,
    val holdingMonths: Int,
    val longTerm: Boolean,
    val taxableGain: Double,
    val tax: Double,
    val effectiveRatePercent: Double,
    val note: String
)

data class SwpResult(val monthsLasted: Int, val exhausted: Boolean, val finalBalance: Double)

/**
 * Planning calculators. Every function states its assumption in the name or the doc,
 * and none of them is a projection dressed up as a promise.
 *
 * The capital-gains defaults reflect the Indian equity regime as it stood after the
 * July 2024 change (20% short term, 12.5% long term above a Rs 1.25 lakh annual
 * exemption). Tax law changes; the rates are parameters and the UI says so.
 */
object Calculators {

    /** Future value of a monthly SIP with contributions at the end of each month. */
    fun sipFutureValue(monthly: Double, years: Int, annualRatePercent: Double): Double {
        if (monthly <= 0 || years <= 0) return 0.0
        val months = years * 12
        val r = annualRatePercent / 100.0 / 12.0
        if (r == 0.0) return monthly * months
        return monthly * (((1 + r).pow(months) - 1) / r)
    }

    /** Monthly contribution needed to reach a target. */
    fun sipRequired(target: Double, years: Int, annualRatePercent: Double): Double? {
        if (target <= 0 || years <= 0) return null
        val months = years * 12
        val r = annualRatePercent / 100.0 / 12.0
        if (r == 0.0) return target / months
        val factor = ((1 + r).pow(months) - 1) / r
        if (factor <= 0) return null
        return target / factor
    }

    /** SIP where the contribution rises by a fixed percentage every year. */
    fun stepUpSip(initialMonthly: Double, stepUpPercent: Double, years: Int, annualRatePercent: Double): Double {
        if (initialMonthly <= 0 || years <= 0) return 0.0
        val r = annualRatePercent / 100.0 / 12.0
        var balance = 0.0
        var monthly = initialMonthly
        for (year in 1..years) {
            for (month in 1..12) {
                balance = balance * (1 + r) + monthly
            }
            monthly *= (1 + stepUpPercent / 100.0)
        }
        return balance
    }

    fun lumpsumFutureValue(principal: Double, years: Int, annualRatePercent: Double): Double {
        if (principal <= 0 || years <= 0) return 0.0
        return principal * (1 + annualRatePercent / 100.0).pow(years.toDouble())
    }

    /** How long a corpus lasts when a fixed amount is withdrawn monthly. */
    fun swpDepletion(corpus: Double, monthlyWithdrawal: Double, annualRatePercent: Double, maxMonths: Int = 1200): SwpResult {
        if (corpus <= 0 || monthlyWithdrawal <= 0) return SwpResult(0, true, corpus)
        val r = annualRatePercent / 100.0 / 12.0
        var balance = corpus
        var months = 0
        while (balance > 0 && months < maxMonths) {
            balance = balance * (1 + r) - monthlyWithdrawal
            months++
        }
        return SwpResult(months, balance <= 0, if (balance > 0) balance else 0.0)
    }

    /** Equity capital gains tax, with the holding period deciding the regime. */
    fun equityCapitalGains(
        buyValue: Double,
        sellValue: Double,
        holdingMonths: Int,
        shortTermRatePercent: Double = 20.0,
        longTermRatePercent: Double = 12.5,
        longTermExemption: Double = 125_000.0
    ): TaxResult {
        val gain = sellValue - buyValue
        val longTerm = holdingMonths > 12
        if (gain <= 0) {
            return TaxResult(gain, holdingMonths, longTerm, 0.0, 0.0, 0.0, "A loss attracts no tax; it can be set off against other capital gains.")
        }
        val taxable = if (longTerm) (gain - longTermExemption).coerceAtLeast(0.0) else gain
        val rate = if (longTerm) longTermRatePercent else shortTermRatePercent
        val tax = taxable * rate / 100.0
        val note = if (longTerm) {
            "Held more than twelve months: long term. Rs ${longTermExemption.toLong()} of gains is exempt each year."
        } else {
            "Held twelve months or less: short term, taxed at the flat rate."
        }
        return TaxResult(gain, holdingMonths, longTerm, taxable, tax, if (gain > 0) tax / gain * 100.0 else 0.0, note)
    }

    /** Units to buy so that a stop loss costs no more than a set share of capital. */
    fun positionSize(capital: Double, riskPercent: Double, entry: Double, stopLoss: Double): Double? {
        if (capital <= 0 || entry <= 0 || riskPercent <= 0) return null
        val riskPerUnit = entry - stopLoss
        if (riskPerUnit <= 0) return null
        val riskAmount = capital * riskPercent / 100.0
        return riskAmount / riskPerUnit
    }

    /** The price at which a round trip breaks even after percentage costs. */
    fun breakEvenPrice(buyPrice: Double, totalCostPercent: Double): Double? {
        if (buyPrice <= 0) return null
        return buyPrice * (1 + totalCostPercent / 100.0)
    }

    /** Converts a total percentage gain into a compound annual rate. */
    fun absoluteToCagr(absolutePercent: Double, years: Double): Double? {
        if (years <= 0) return null
        val growth = 1 + absolutePercent / 100.0
        if (growth <= 0) return null
        return ((growth).pow(1.0 / years) - 1.0) * 100.0
    }

    /** Corpus needed so that withdrawals keep pace with inflation. */
    fun retirementCorpus(
        monthlyExpenseToday: Double,
        yearsToRetirement: Int,
        inflationPercent: Double,
        postRetirementReturnPercent: Double
    ): Double? {
        if (monthlyExpenseToday <= 0 || yearsToRetirement < 0) return null
        val futureMonthly = monthlyExpenseToday * (1 + inflationPercent / 100.0).pow(yearsToRetirement.toDouble())
        val annual = futureMonthly * 12
        val realReturn = (1 + postRetirementReturnPercent / 100.0) / (1 + inflationPercent / 100.0) - 1
        if (realReturn <= 0) return null
        return annual / realReturn
    }

    /** Years for an investment to double at a given rate (exact, not the rule of 72). */
    fun yearsToDouble(annualRatePercent: Double): Double? {
        if (annualRatePercent <= 0) return null
        return ln(2.0) / ln(1 + annualRatePercent / 100.0)
    }
}
