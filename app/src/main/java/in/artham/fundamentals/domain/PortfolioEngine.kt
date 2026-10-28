package `in`.artham.fundamentals.domain

import `in`.artham.fundamentals.core.Num
import `in`.artham.fundamentals.data.Company
import `in`.artham.fundamentals.data.CompanyRecord
import `in`.artham.fundamentals.data.Txn
import `in`.artham.fundamentals.data.TxnKind
import kotlin.math.abs
import kotlin.math.pow

/** One open position after replaying every transaction. */
data class Holding(
    val company: Company,
    val units: Double,
    val averageCost: Double,
    val invested: Double,
    /** Null when the app has no price for this company. Never a substituted estimate. */
    val marketValue: Double?,
    val unrealised: Double?,
    val unrealisedPercent: Double?,
    val realised: Double,
    val dividends: Double
) {
    val priced: Boolean get() = marketValue != null
}

data class SectorSlice(val sector: String, val value: Double, val weightPercent: Double)

data class PortfolioSummary(
    val holdings: List<Holding>,
    val invested: Double,
    val marketValue: Double,
    val unpricedHoldings: Int,
    val unrealised: Double,
    val realised: Double,
    val dividends: Double,
    val totalGain: Double,
    val totalGainPercent: Double?,
    val xirr: Double?,
    val allocation: List<SectorSlice>
)

/**
 * Replays a transaction ledger into positions using first-in-first-out cost basis,
 * which is the convention Indian tax rules use for equity delivery.
 *
 * Cash-flow sign convention: money leaving the investor's pocket is negative, money
 * arriving is positive. XIRR is solved on that stream plus the current market value
 * as a final inflow today.
 */
object PortfolioEngine {

    fun compute(
        transactions: List<Txn>,
        records: Map<Long, CompanyRecord>,
        today: Long = System.currentTimeMillis()
    ): PortfolioSummary {
        data class Lot(var units: Double, var price: Double)
        data class Book(
            val lots: MutableList<Lot> = ArrayList(),
            var realised: Double = 0.0,
            var dividends: Double = 0.0
        )

        val books = LinkedHashMap<Long, Book>()
        val cashflows = ArrayList<Pair<Long, Double>>()
        var invested = 0.0
        var realisedTotal = 0.0
        var dividendTotal = 0.0

        for (t in transactions.sortedWith(compareBy({ it.tradedOn }, { it.id }))) {
            val book = books.getOrPut(t.companyId) { Book() }
            when (t.kind) {
                TxnKind.BUY -> {
                    val cash = if (t.amount > 0.0) t.amount else t.units * t.price
                    book.lots.add(Lot(t.units, if (t.units > 0.0) cash / t.units else t.price))
                    cashflows.add(t.tradedOn to -cash)
                    invested += cash
                }
                TxnKind.SELL -> {
                    var remaining = t.units
                    var cost = 0.0
                    while (remaining > 1e-9 && book.lots.isNotEmpty()) {
                        val lot = book.lots.first()
                        val take = minOf(lot.units, remaining)
                        cost += take * lot.price
                        lot.units -= take
                        remaining -= take
                        if (lot.units <= 1e-9) book.lots.removeAt(0)
                    }
                    val proceeds = if (t.amount > 0.0) t.amount else t.units * t.price
                    val gain = proceeds - cost
                    book.realised += gain
                    realisedTotal += gain
                    cashflows.add(t.tradedOn to proceeds)
                }
                TxnKind.DIVIDEND -> {
                    val cash = if (t.amount > 0.0) t.amount else t.units * t.price
                    book.dividends += cash
                    dividendTotal += cash
                    cashflows.add(t.tradedOn to cash)
                }
                TxnKind.BONUS, TxnKind.SPLIT -> {
                    // Units are granted at zero cost, so the average cost per unit falls.
                    book.lots.add(Lot(t.units, 0.0))
                }
            }
        }

        val holdings = books.mapNotNull { (companyId, book) ->
            val record = records[companyId] ?: return@mapNotNull null
            val units = book.lots.sumOf { it.units }
            if (units <= 1e-9) return@mapNotNull null
            val cost = book.lots.sumOf { it.units * it.price }
            // A holding with no stored quote is reported as unpriced. Substituting an
            // assumed multiple here would put an invented number into a profit figure.
            val price = record.quote?.price
            val value = if (price != null && price > 0.0) units * price else null
            Holding(
                company = record.company,
                units = units,
                averageCost = if (units > 0) cost / units else 0.0,
                invested = cost,
                marketValue = value,
                unrealised = if (value != null) value - cost else null,
                unrealisedPercent = if (value != null && cost > 0) (value - cost) / cost * 100.0 else null,
                realised = book.realised,
                dividends = book.dividends
            )
        }.sortedByDescending { it.marketValue ?: 0.0 }

        val marketValue = holdings.sumOf { it.marketValue ?: 0.0 }
        val openCost = holdings.sumOf { it.invested }
        val unpriced = holdings.count { it.marketValue == null }
        val pricedCost = holdings.filter { it.marketValue != null }.sumOf { it.invested }
        val unrealised = marketValue - pricedCost

        if (marketValue > 0) cashflows.add(today to marketValue)
        val xirr = xirr(cashflows)

        val bySector = holdings.groupBy { it.company.sector.ifBlank { "Unclassified" } }
            .map { (sector, list) ->
                val value = list.sumOf { it.marketValue ?: 0.0 }
                SectorSlice(sector, value, if (marketValue > 0) value / marketValue * 100.0 else 0.0)
            }
            .sortedByDescending { it.value }

        val totalGain = unrealised + realisedTotal + dividendTotal
        return PortfolioSummary(
            holdings = holdings,
            invested = openCost,
            marketValue = marketValue,
            unpricedHoldings = unpriced,
            unrealised = unrealised,
            realised = realisedTotal,
            dividends = dividendTotal,
            totalGain = totalGain,
            totalGainPercent = if (invested > 0) totalGain / invested * 100.0 else null,
            xirr = xirr,
            allocation = bySector
        )
    }

    /**
     * Extended internal rate of return, solved by bisection on the net present value.
     *
     * Bisection rather than Newton-Raphson on purpose: Newton can diverge on the
     * irregular, sign-changing streams a real ledger produces, and a wrong XIRR is
     * worse than an honest null.
     */
    fun xirr(cashflows: List<Pair<Long, Double>>, guessLow: Double = -0.99, guessHigh: Double = 10.0): Double? {
        val flows = cashflows.filter { abs(it.second) > 1e-9 }
        if (flows.size < 2) return null
        if (flows.none { it.second < 0 } || flows.none { it.second > 0 }) return null

        val start = flows.minOf { it.first }
        fun npv(rate: Double): Double = flows.sumOf { (date, amount) ->
            val years = (date - start).toDouble() / (365.0 * 24 * 60 * 60 * 1000)
            amount / (1.0 + rate).pow(years)
        }

        var low = guessLow
        var high = guessHigh
        var fLow = npv(low)
        var fHigh = npv(high)
        if (fLow * fHigh > 0) return null

        repeat(200) {
            val mid = (low + high) / 2.0
            val fMid = npv(mid)
            if (abs(fMid) < 1e-7 || (high - low) < 1e-10) return mid * 100.0
            if (fLow * fMid < 0) {
                high = mid
                fHigh = fMid
            } else {
                low = mid
                fLow = fMid
            }
        }
        return ((low + high) / 2.0) * 100.0
    }

    /** Total return on a single position, used in the holdings table. */
    fun positionReturn(h: Holding): Double? {
        val value = h.marketValue ?: return null
        return Num.div(value - h.invested + h.realised + h.dividends, h.invested)?.times(100.0)
    }
}
