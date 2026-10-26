package `in`.artham.fundamentals.data

/** A listed company (or a clearly-labelled demo entity). */
data class Company(
    val id: Long = 0,
    val code: String,
    val name: String,
    val sector: String = "",
    val industry: String = "",
    val about: String = "",
    val consolidated: Boolean = true,
    val isDemo: Boolean = false,
    val updatedAt: Long = 0L,
    /** Face value per share, from the exchange master file. Used to derive share count. */
    val faceValue: Double = 10.0
) {
    val displayCode: String get() = code.uppercase()
}

/** One audited annual period, values in rupees crore unless stated. */
data class AnnualFinancial(
    val companyId: Long,
    val fyEnd: Int,
    val sales: Double? = null,
    val expenses: Double? = null,
    val operatingProfit: Double? = null,
    val opmPercent: Double? = null,
    val otherIncome: Double? = null,
    val interest: Double? = null,
    val depreciation: Double? = null,
    val profitBeforeTax: Double? = null,
    val taxPercent: Double? = null,
    val netProfit: Double? = null,
    val eps: Double? = null,
    val dividend: Double? = null,
    val equityCapital: Double? = null,
    val reserves: Double? = null,
    val borrowings: Double? = null,
    val otherLiabilities: Double? = null,
    val totalLiabilities: Double? = null,
    val fixedAssets: Double? = null,
    val cwip: Double? = null,
    val investments: Double? = null,
    val otherAssets: Double? = null,
    val totalAssets: Double? = null,
    val receivables: Double? = null,
    val inventory: Double? = null,
    val payables: Double? = null,
    val cfo: Double? = null,
    val cfi: Double? = null,
    val cff: Double? = null,
    val netCashFlow: Double? = null
)

/** One reported quarter. [sortKey] = fyEnd * 4 + quarter for stable ordering. */
data class QuarterlyFinancial(
    val companyId: Long,
    val fyEnd: Int,
    val quarter: Int,
    val label: String,
    val sales: Double? = null,
    val expenses: Double? = null,
    val operatingProfit: Double? = null,
    val opmPercent: Double? = null,
    val otherIncome: Double? = null,
    val interest: Double? = null,
    val depreciation: Double? = null,
    val profitBeforeTax: Double? = null,
    val taxPercent: Double? = null,
    val netProfit: Double? = null,
    val eps: Double? = null
) {
    val sortKey: Int get() = fyEnd * 4 + quarter
}

/** Shareholding pattern snapshot. Percentages of total equity. */
data class ShareholdingRow(
    val companyId: Long,
    val label: String,
    val sortKey: Int,
    val promoters: Double? = null,
    val fii: Double? = null,
    val dii: Double? = null,
    val government: Double? = null,
    val publicHolding: Double? = null
)

/** Latest market quote. Optional: the app works fully offline without it. */
data class Quote(
    val companyId: Long,
    val price: Double? = null,
    val changePercent: Double? = null,
    val high52: Double? = null,
    val low52: Double? = null,
    val marketCap: Double? = null,
    val updatedAt: Long = 0L,
    val source: String = ""
)

data class Watchlist(val id: Long = 0, val name: String, val createdAt: Long = 0L)

data class WatchlistItem(
    val id: Long = 0,
    val watchlistId: Long,
    val companyId: Long,
    val note: String = "",
    val addedAt: Long = 0L
)

data class SavedScreen(
    val id: Long = 0,
    val name: String,
    val query: String,
    val description: String = "",
    val createdAt: Long = 0L,
    val lastRunAt: Long = 0L,
    val lastCount: Int = 0
)

data class ChatMessage(
    val id: Long = 0,
    val sessionKey: String,
    val role: String,
    val content: String,
    val createdAt: Long = 0L,
    val engine: String = ""
)

data class ModelPackRecord(
    val id: Long = 0,
    val name: String,
    val engine: String,
    val path: String,
    val sizeBytes: Long = 0L,
    val sha256: String = "",
    val importedAt: Long = 0L,
    val isActive: Boolean = false
)

data class ImportBatch(
    val id: Long = 0,
    val createdAt: Long = 0L,
    val fileName: String,
    val companies: Int,
    val annualRows: Int,
    val quarterlyRows: Int,
    val isDemo: Boolean = false,
    val note: String = ""
)

/** A company's complete local record, assembled for analysis. */
data class CompanyRecord(
    val company: Company,
    val annual: List<AnnualFinancial>,
    val quarterly: List<QuarterlyFinancial>,
    val shareholding: List<ShareholdingRow>,
    val quote: Quote?
)

/** One row of a screen result. */
data class ScreenHit(
    val company: Company,
    val values: Map<String, Double?>,
    val score: Double = 0.0
)

/** Result of evaluating a screen over the local dataset. */
data class ScreenResult(
    val query: String,
    val hits: List<ScreenHit>,
    val evaluated: Int,
    val skipped: Int,
    val durationMs: Long,
    val error: String? = null
)

data class DatasetStats(
    val companies: Int,
    val annualRows: Int,
    val quarterlyRows: Int,
    val demoCompanies: Int,
    val lastImportAt: Long,
    val withQuotes: Int
)
