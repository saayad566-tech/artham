package `in`.artham.fundamentals.domain

import `in`.artham.fundamentals.core.Fmt
import `in`.artham.fundamentals.core.Num

data class Insight(val text: String, val positive: Boolean, val ruleId: String)

/**
 * A deterministic checklist that turns stored financials into plain-English
 * observations. Every rule is a stated threshold on a computed number, so any
 * sentence can be traced back to the figures on the company page.
 *
 * These are observations about reported numbers, not a recommendation.
 */
object ProsConsEngine {

    fun analyse(f: Financials): List<Insight> {
        val out = ArrayList<Insight>()
        val a = f.latest ?: return out

        // ---- profitability
        val roe = Ratios.roe(a)
        val roe3 = listOfNotNull(f.year(0)?.let { Ratios.roe(it) }, f.year(1)?.let { Ratios.roe(it) }, f.year(2)?.let { Ratios.roe(it) })
        val roeAvg3 = if (roe3.size >= 2) roe3.average() else null
        if (roeAvg3 != null && roeAvg3 >= 20.0) {
            out.add(Insight("Good return on equity: the 3-year average ROE is ${Fmt.pct(roeAvg3)}.", true, "roe_high"))
        } else if (roe != null && roe < 8.0) {
            out.add(Insight("Low return on equity at ${Fmt.pct(roe)}; the business earns little on shareholder capital.", false, "roe_low"))
        }

        val roce = Ratios.roce(a)
        if (roce != null && roce >= 20.0) {
            out.add(Insight("Return on capital employed is ${Fmt.pct(roce)}, comfortably above the cost of capital.", true, "roce_high"))
        } else if (roce != null && roce < 10.0) {
            out.add(Insight("Return on capital employed is only ${Fmt.pct(roce)}.", false, "roce_low"))
        }

        // ---- growth
        val sales5 = Num.cagr(f.year(5)?.sales, a.sales, 5)
        if (sales5 != null && sales5 >= 15.0) {
            out.add(Insight("Sales have compounded at ${Fmt.pct(sales5)} a year over the last five years.", true, "sales_growth_high"))
        } else if (sales5 != null && sales5 < 8.0) {
            out.add(Insight("Sales growth has been modest at ${Fmt.pct(sales5)} a year over five years.", false, "sales_growth_low"))
        }
        val profit5 = Num.cagr(f.year(5)?.netProfit, a.netProfit, 5)
        if (profit5 != null && profit5 >= 18.0) {
            out.add(Insight("Profit has compounded at ${Fmt.pct(profit5)} a year over five years.", true, "profit_growth_high"))
        } else if (profit5 != null && profit5 < 0.0) {
            out.add(Insight("Profit has shrunk at ${Fmt.pct(profit5)} a year over five years.", false, "profit_growth_negative"))
        }

        // ---- margins
        val opm = Ratios.opm(a)
        val opmPrev = f.previous?.let { Ratios.opm(it) }
        if (opm != null && opm >= 20.0) {
            out.add(Insight("Operating margin is a healthy ${Fmt.pct(opm)}.", true, "opm_high"))
        }
        if (opm != null && opmPrev != null && opm - opmPrev < -2.0) {
            out.add(Insight("Operating margin fell from ${Fmt.pct(opmPrev)} to ${Fmt.pct(opm)} in the latest year.", false, "opm_falling"))
        }

        // ---- balance sheet
        val de = Ratios.debtToEquity(a)
        if (de != null && de <= 0.3) {
            out.add(Insight("Almost debt free: borrowings are ${Fmt.times(de)} of net worth.", true, "debt_low"))
        } else if (de != null && de >= 1.5) {
            out.add(Insight("Borrowings are ${Fmt.times(de)} of net worth, which is highly leveraged.", false, "debt_high"))
        }
        val icr = Ratios.interestCoverage(a)
        if (icr != null && icr < 2.0) {
            out.add(Insight("Interest coverage is only ${Fmt.times(icr)}; a small fall in profit would strain debt servicing.", false, "icr_low"))
        }
        if (de != null && f.year(1)?.let { Ratios.debtToEquity(it) } != null) {
            val dePrev = Ratios.debtToEquity(f.year(1)!!)!!
            if (de < dePrev * 0.8) {
                out.add(Insight("Leverage has come down: debt to equity improved from ${Fmt.times(dePrev)} to ${Fmt.times(de)}.", true, "deleveraging"))
            }
        }

        // ---- cash quality
        val conv = Ratios.cfoToOperatingProfit(a)
        if (conv != null && conv >= 80.0) {
            out.add(Insight("Cash conversion is strong: operating cash flow is ${Fmt.pct(conv)} of operating profit.", true, "cash_conversion_high"))
        } else if (conv != null && conv < 50.0) {
            out.add(Insight("Operating cash flow is only ${Fmt.pct(conv)} of operating profit, so reported profit is not turning into cash.", false, "cash_conversion_low"))
        }
        val fcf = Ratios.freeCashFlow(a)
        if (fcf != null && fcf < 0.0) {
            out.add(Insight("Free cash flow is negative at ${Fmt.cr(fcf)} crore after capital expenditure.", false, "fcf_negative"))
        }

        // ---- working capital
        val ccc = Ratios.cashConversionCycle(a)
        val cccPrev = f.previous?.let { Ratios.cashConversionCycle(it) }
        if (ccc != null && cccPrev != null && ccc - cccPrev > 20.0) {
            out.add(Insight("The cash conversion cycle stretched from ${Fmt.days(cccPrev)} to ${Fmt.days(ccc)}.", false, "wc_stretch"))
        }

        // ---- valuation
        val pe = Ratios.pe(f)
        if (pe != null && pe > 45.0) {
            out.add(Insight("The stock trades at ${Fmt.times(pe)} earnings, which prices in a lot of future growth.", false, "pe_high"))
        } else if (pe != null && pe in 0.1..12.0) {
            out.add(Insight("The stock trades at ${Fmt.times(pe)} earnings, low in absolute terms.", true, "pe_low"))
        }
        val pb = Ratios.pb(f)
        if (pb != null && pb > 5.0) {
            out.add(Insight("The stock trades at ${Fmt.times(pb)} book value.", false, "pb_high"))
        }
        val graham = Ratios.grahamNumber(f)
        val price = f.quote?.price
        if (graham != null && price != null && price < graham) {
            out.add(Insight("Price of ${Fmt.rupees(price)} is below the Graham number of ${Fmt.rupees(graham)}.", true, "below_graham"))
        }

        // ---- dividend
        val payout = Ratios.dividendPayout(f)
        if (payout != null && payout in 20.0..80.0) {
            out.add(Insight("A healthy dividend payout of ${Fmt.pct(payout)} of earnings.", true, "payout_healthy"))
        } else if (payout != null && payout > 100.0) {
            out.add(Insight("The dividend is ${Fmt.pct(payout)} of earnings, more than the company earns.", false, "payout_high"))
        }

        // ---- scores
        val piotroski = Ratios.piotroski(f)
        if (piotroski != null && piotroski >= 7) {
            out.add(Insight("Piotroski F-score of $piotroski out of 9: the fundamental trend is improving.", true, "piotroski_high"))
        } else if (piotroski != null && piotroski <= 3) {
            out.add(Insight("Piotroski F-score of $piotroski out of 9: the fundamental trend is deteriorating.", false, "piotroski_low"))
        }

        // ---- ownership
        val sh = f.latestShareholding
        if (sh?.promoters != null && sh.promoters!! >= 50.0) {
            out.add(Insight("Promoters hold ${Fmt.pct(sh.promoters)}, aligning their interests with minority shareholders.", true, "promoter_high"))
        } else if (sh?.promoters != null && sh.promoters!! < 30.0) {
            out.add(Insight("Promoter holding is only ${Fmt.pct(sh.promoters)}.", false, "promoter_low"))
        }
        if (f.shareholding.size >= 5 && sh?.promoters != null) {
            val fourBack = f.shareholding[f.shareholding.size - 5].promoters
            if (fourBack != null && sh.promoters!! - fourBack < -1.0) {
                out.add(Insight("Promoters have reduced their stake by ${Fmt.pct(fourBack - sh.promoters!!)} over four quarters.", false, "promoter_selling"))
            }
        }
        return out
    }
}
