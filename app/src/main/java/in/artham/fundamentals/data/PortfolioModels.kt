package `in`.artham.fundamentals.data

/** What a transaction did. Splits and bonuses change units without cash moving. */
enum class TxnKind { BUY, SELL, DIVIDEND, SPLIT, BONUS }

data class Portfolio(val id: Long = 0, val name: String, val createdAt: Long = 0L)

/**
 * One portfolio event. [amount] is the cash that moved (positive = cash in to the
 * investor), so a buy stores a positive amount and the engine treats it as an outflow.
 */
data class Txn(
    val id: Long = 0,
    val portfolioId: Long,
    val companyId: Long,
    val kind: TxnKind,
    val tradedOn: Long,
    val units: Double = 0.0,
    val price: Double = 0.0,
    val amount: Double = 0.0,
    val notes: String = ""
)

/** A dated close price, used for historical valuation and backtesting. */
data class PricePoint(val companyId: Long, val onDate: Long, val close: Double)

data class DocumentRecord(
    val id: Long = 0,
    val title: String,
    val companyId: Long? = null,
    val addedAt: Long = 0L,
    val charCount: Int = 0,
    val chunkCount: Int = 0
)

data class DocChunk(val id: Long = 0, val docId: Long, val ord: Int, val body: String)

data class AlertRule(
    val id: Long = 0,
    val name: String,
    val query: String,
    val enabled: Boolean = true,
    val lastChecked: Long = 0L,
    val lastCount: Int = 0,
    val createdAt: Long = 0L
)
