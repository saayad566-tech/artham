package `in`.artham.fundamentals.domain

import `in`.artham.fundamentals.data.AnnualFinancial
import `in`.artham.fundamentals.data.CompanyRecord
import `in`.artham.fundamentals.data.QuarterlyFinancial
import `in`.artham.fundamentals.data.ShareholdingRow

/**
 * Read-only view over a company's stored history with the navigation helpers
 * every metric needs. Annual rows are ascending by fiscal year.
 */
class Financials(val record: CompanyRecord) {

    val company get() = record.company
    val annual: List<AnnualFinancial> get() = record.annual
    val quarterly: List<QuarterlyFinancial> get() = record.quarterly
    val shareholding: List<ShareholdingRow> get() = record.shareholding
    val quote get() = record.quote

    val hasData: Boolean get() = annual.isNotEmpty()

    val latest: AnnualFinancial? get() = annual.lastOrNull()
    val previous: AnnualFinancial? get() = annual.getOrNull(annual.size - 2)

    /** n years before the latest period; 0 == latest. */
    fun year(back: Int): AnnualFinancial? = annual.getOrNull(annual.size - 1 - back)

    fun lastN(n: Int): List<AnnualFinancial> = annual.takeLast(n)

    val latestQuarter: QuarterlyFinancial? get() = quarterly.lastOrNull()

    /** n quarters before the latest reported quarter; 0 == latest. */
    fun quarter(back: Int): QuarterlyFinancial? = quarterly.getOrNull(quarterly.size - 1 - back)

    val latestShareholding: ShareholdingRow? get() = shareholding.lastOrNull()

    /** Number of annual periods available, capped at the years actually stored. */
    val historyYears: Int get() = annual.size

    /**
     * Shares outstanding. Taken from the quote when the app has one; otherwise
     * derived from equity capital assuming a face value of Rs 10, which is the
     * Indian market default. The assumption is surfaced in the UI wherever a
     * per-share figure depends on it.
     */
    fun sharesOutstanding(): Double? {
        val price = quote?.price
        val cap = quote?.marketCap
        if (price != null && cap != null && price > 0.0) return cap / price
        val equityCrore = latest?.equityCapital ?: return null
        if (equityCrore <= 0.0) return null
        // Equity capital is stored in crore, so it has to be scaled back to rupees before
        // dividing by the face value. The company's own face value is used when known; the
        // Rs 10 fallback only applies to rows imported without one.
        val face = company.faceValue.takeIf { it > 0.0 } ?: FACE_VALUE_ASSUMED
        return equityCrore * 10_000_000.0 / face
    }

    /** Latest year TTM-style aggregate of the four most recent quarters, if present. */
    fun trailingFourQuarters(): List<QuarterlyFinancial> = quarterly.takeLast(4)

    companion object {
        const val FACE_VALUE_ASSUMED = 10.0
    }
}
