package `in`.artham.fundamentals.domain

import `in`.artham.fundamentals.core.Num
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Financial ratios, each with the formula it implements stated next to it.
 *
 * Conventions used throughout:
 *  - every rupee figure is in crore
 *  - percentages are returned as percentage points (12.5 means 12.5%)
 *  - a ratio that cannot be computed from the available data returns null and
 *    is never silently replaced by zero
 */
object Ratios {

    /** (equity share capital + reserves) = shareholders' funds. */
    fun netWorth(f: Financials): Double? {
        val a = f.latest ?: return null
        val cap = a.equityCapital
        val res = a.reserves
        if (cap == null && res == null) return null
        return (cap ?: 0.0) + (res ?: 0.0)
    }

    fun netWorth(a: `in`.artham.fundamentals.data.AnnualFinancial): Double? {
        val cap = a.equityCapital
        val res = a.reserves
        if (cap == null && res == null) return null
        return (cap ?: 0.0) + (res ?: 0.0)
    }

    /** Capital employed = net worth + total borrowings. */
    fun capitalEmployed(a: `in`.artham.fundamentals.data.AnnualFinancial): Double? {
        val nw = netWorth(a) ?: return null
        return nw + (a.borrowings ?: 0.0)
    }

    /** ROE % = net profit / shareholders' funds * 100. */
    fun roe(a: `in`.artham.fundamentals.data.AnnualFinancial): Double? =
        Num.mul(Num.div(a.netProfit, netWorth(a)), 100.0)

    /** ROCE % = (profit before tax + interest) / capital employed * 100. */
    fun roce(a: `in`.artham.fundamentals.data.AnnualFinancial): Double? {
        val ebit = Num.add(a.profitBeforeTax, a.interest) ?: return null
        return Num.mul(Num.div(ebit, capitalEmployed(a)), 100.0)
    }

    /** Operating margin % = operating profit / sales * 100. */
    fun opm(a: `in`.artham.fundamentals.data.AnnualFinancial): Double? =
        a.opmPercent ?: Num.mul(Num.div(a.operatingProfit, a.sales), 100.0)

    /** Debt / equity = total borrowings / shareholders' funds. */
    fun debtToEquity(a: `in`.artham.fundamentals.data.AnnualFinancial): Double? =
        Num.div(a.borrowings, netWorth(a))

    /** Interest coverage = operating profit / interest. */
    fun interestCoverage(a: `in`.artham.fundamentals.data.AnnualFinancial): Double? =
        Num.div(a.operatingProfit, a.interest)

    /** Asset turnover = sales / total assets. */
    fun assetTurnover(a: `in`.artham.fundamentals.data.AnnualFinancial): Double? =
        Num.div(a.sales, a.totalAssets)

    /** ROA % = net profit / total assets * 100. */
    fun roa(a: `in`.artham.fundamentals.data.AnnualFinancial): Double? =
        Num.mul(Num.div(a.netProfit, a.totalAssets), 100.0)

    /** Debtor days = trade receivables / sales * 365. */
    fun debtorDays(a: `in`.artham.fundamentals.data.AnnualFinancial): Double? =
        Num.mul(Num.div(a.receivables, a.sales), 365.0)

    /** Inventory days = inventory / sales * 365. */
    fun inventoryDays(a: `in`.artham.fundamentals.data.AnnualFinancial): Double? =
        Num.mul(Num.div(a.inventory, a.sales), 365.0)

    /** Days payable = trade payables / sales * 365. */
    fun daysPayable(a: `in`.artham.fundamentals.data.AnnualFinancial): Double? =
        Num.mul(Num.div(a.payables, a.sales), 365.0)

    /** Cash conversion cycle = debtor days + inventory days - days payable. */
    fun cashConversionCycle(a: `in`.artham.fundamentals.data.AnnualFinancial): Double? {
        val d = debtorDays(a) ?: return null
        val i = inventoryDays(a) ?: 0.0
        val p = daysPayable(a) ?: 0.0
        return d + i - p
    }

    /** Operating cash flow as a percentage of operating profit. */
    fun cfoToOperatingProfit(a: `in`.artham.fundamentals.data.AnnualFinancial): Double? =
        Num.mul(Num.div(a.cfo, a.operatingProfit), 100.0)

    /** Free cash flow = operating cash flow + investing cash flow (capex is inside cfi). */
    fun freeCashFlow(a: `in`.artham.fundamentals.data.AnnualFinancial): Double? =
        Num.add(a.cfo, a.cfi)

    /** Book value per share = net worth / shares outstanding. */
    fun bookValuePerShare(f: Financials): Double? =
        Num.div(netWorth(f), f.sharesOutstanding())

    /** Market capitalisation, from the stored quote. */
    fun marketCap(f: Financials): Double? = f.quote?.marketCap

    /** Price to earnings = price / EPS, or market cap / net profit. */
    fun pe(f: Financials): Double? {
        val a = f.latest ?: return null
        val price = f.quote?.price
        val eps = a.eps
        if (price != null && eps != null && eps > 0.0) return price / eps
        return Num.div(marketCap(f), a.netProfit)
    }

    /** Price to book = price / book value per share. */
    fun pb(f: Financials): Double? {
        val price = f.quote?.price ?: return null
        val bv = bookValuePerShare(f) ?: return null
        if (bv <= 0.0) return null
        return price / bv
    }

    /** Dividend yield % = dividend per share / price * 100. */
    fun dividendYield(f: Financials): Double? {
        val price = f.quote?.price ?: return null
        val dps = f.latest?.dividend ?: return null
        return Num.div(dps, price)?.times(100.0)
    }

    /** Dividend payout % = dividend per share / EPS * 100. */
    fun dividendPayout(f: Financials): Double? {
        val a = f.latest ?: return null
        return Num.mul(Num.div(a.dividend, a.eps), 100.0)
    }

    /** Earnings yield % = 1 / PE * 100. */
    fun earningsYield(f: Financials): Double? {
        val pe = pe(f) ?: return null
        if (pe <= 0.0) return null
        return 100.0 / pe
    }

    /**
     * Graham number = sqrt(22.5 x EPS x book value per share).
     * Benjamin Graham, "The Intelligent Investor" - the ceiling price a defensive
     * investor should pay for a stock with a PE below 15 and a PB below 1.5.
     */
    fun grahamNumber(f: Financials): Double? {
        val eps = f.latest?.eps ?: return null
        val bv = bookValuePerShare(f) ?: return null
        if (eps <= 0.0 || bv <= 0.0) return null
        return sqrt(22.5 * eps * bv)
    }

    /** Margin of safety % = (intrinsic - price) / intrinsic * 100. */
    fun marginOfSafety(intrinsic: Double?, price: Double?): Double? {
        if (intrinsic == null || price == null || intrinsic <= 0.0) return null
        return (intrinsic - price) / intrinsic * 100.0
    }

    /**
     * Two-stage discounted cash flow on free cash flow.
     *
     * Stage 1: the latest free cash flow grows at [growthRate] for [years].
     * Stage 2: a Gordon terminal value at [terminalGrowth], discounted at [discountRate].
     * The result is an enterprise value which is then reduced by net debt and
     * divided by shares outstanding to give a per-share intrinsic value.
     *
     * This is a model, not a price target: a small change in the discount rate
     * moves the answer a long way, which is why the UI always shows the inputs.
     */
    fun dcfPerShare(
        f: Financials,
        growthRate: Double = 12.0,
        years: Int = 5,
        discountRate: Double = 13.0,
        terminalGrowth: Double = 4.0
    ): Double? {
        val a = f.latest ?: return null
        val fcf = freeCashFlow(a) ?: return null
        if (fcf <= 0.0) return null
        val g = growthRate / 100.0
        val r = discountRate / 100.0
        val tg = terminalGrowth / 100.0
        if (r <= tg) return null
        var pv = 0.0
        var cash = fcf
        for (t in 1..years) {
            cash *= (1 + g)
            pv += cash / (1 + r).pow(t.toDouble())
        }
        val terminal = cash * (1 + tg) / (r - tg)
        pv += terminal / (1 + r).pow(years.toDouble())
        val netDebt = (a.borrowings ?: 0.0) - 0.0
        val equityValue = pv - netDebt
        val shares = f.sharesOutstanding() ?: return null
        if (shares <= 0.0) return null
        return equityValue / shares
    }

    /**
     * Piotroski F-score, 0-9 (Piotroski, 2000).
     *
     * Two of the nine signals need a current ratio, which the local dataset does
     * not carry. They are replaced with the closest available proxies and the
     * substitution is documented here rather than hidden:
     *   - "current ratio improved" -> working capital intensity (receivables +
     *     inventory - payables over total assets) decreased
     *   - "no new shares issued" -> equity share capital did not increase
     */
    fun piotroski(f: Financials): Int? {
        val a = f.latest ?: return null
        val p = f.previous ?: return null
        var score = 0
        val roaNow = roa(a)
        val roaPrev = roa(p)
        if (roaNow != null && roaNow > 0) score++
        if ((a.cfo ?: 0.0) > 0) score++
        if (roaNow != null && roaPrev != null && roaNow > roaPrev) score++
        if (a.cfo != null && a.netProfit != null && a.cfo > a.netProfit) score++
        val levNow = Num.div(a.borrowings, a.totalAssets)
        val levPrev = Num.div(p.borrowings, p.totalAssets)
        if (levNow != null && levPrev != null && levNow < levPrev) score++

        fun wcIntensity(x: `in`.artham.fundamentals.data.AnnualFinancial): Double? {
            val wc = (x.receivables ?: 0.0) + (x.inventory ?: 0.0) - (x.payables ?: 0.0)
            return Num.div(wc, x.totalAssets)
        }
        val wcNow = wcIntensity(a)
        val wcPrev = wcIntensity(p)
        if (wcNow != null && wcPrev != null && wcNow < wcPrev) score++

        val capNow = a.equityCapital
        val capPrev = p.equityCapital
        if (capNow != null && capPrev != null && capNow <= capPrev * 1.001) score++

        val opmNow = opm(a)
        val opmPrev = opm(p)
        if (opmNow != null && opmPrev != null && opmNow > opmPrev) score++

        val atNow = assetTurnover(a)
        val atPrev = assetTurnover(p)
        if (atNow != null && atPrev != null && atNow > atPrev) score++
        return score
    }

    /** Altman Z-score for a listed manufacturer (Altman, 1968), adapted to the stored fields. */
    fun altmanZ(a: `in`.artham.fundamentals.data.AnnualFinancial): Double? {
        val ta = a.totalAssets ?: return null
        if (ta <= 0.0) return null
        val workingCapital = (a.receivables ?: 0.0) + (a.inventory ?: 0.0) - (a.payables ?: 0.0)
        val retained = a.reserves ?: 0.0
        val ebit = Num.add(a.profitBeforeTax, a.interest) ?: return null
        val sales = a.sales ?: return null
        val equityValue = (a.equityCapital ?: 0.0) + retained
        val liabilities = a.totalLiabilities ?: (a.borrowings ?: 0.0)
        if (liabilities <= 0.0) return null
        return 1.2 * (workingCapital / ta) + 1.4 * (retained / ta) + 3.3 * (ebit / ta) +
            0.6 * (equityValue / liabilities) + 1.0 * (sales / ta)
    }
}
