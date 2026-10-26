package `in`.artham.fundamentals.data

import android.database.sqlite.SQLiteDatabase
import `in`.artham.fundamentals.core.Logx

class PortfolioDao(private val db: ArthamDb) {
    private val w: SQLiteDatabase get() = db.writableDatabase
    private val r: SQLiteDatabase get() = db.readableDatabase

    fun create(name: String): Long =
        w.insert("portfolios", null, valuesOf("name" to name, "created_at" to System.currentTimeMillis()))

    fun all(): List<Portfolio> {
        val out = ArrayList<Portfolio>()
        r.rawQuery("SELECT * FROM portfolios ORDER BY created_at", null).use { c ->
            while (c.moveToNext()) out.add(Portfolio(c.l("id"), c.s("name"), c.l("created_at")))
        }
        return out
    }

    fun ensureDefault(): Portfolio {
        val existing = all()
        if (existing.isNotEmpty()) return existing.first()
        create("My portfolio")
        return all().first()
    }

    fun delete(id: Long) {
        w.delete("transactions", "portfolio_id = ?", arrayOf(id.toString()))
        w.delete("portfolios", "id = ?", arrayOf(id.toString()))
    }

    fun addTransaction(t: Txn): Long = w.insert(
        "transactions", null,
        valuesOf(
            "portfolio_id" to t.portfolioId, "company_id" to t.companyId, "kind" to t.kind.name,
            "traded_on" to t.tradedOn, "units" to t.units, "price" to t.price,
            "amount" to t.amount, "notes" to t.notes
        )
    )

    fun transactions(portfolioId: Long): List<Txn> {
        val out = ArrayList<Txn>()
        r.rawQuery(
            "SELECT * FROM transactions WHERE portfolio_id = ? ORDER BY traded_on, id",
            arrayOf(portfolioId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Txn(
                        id = c.l("id"), portfolioId = portfolioId, companyId = c.l("company_id"),
                        kind = runCatching { TxnKind.valueOf(c.s("kind")) }.getOrDefault(TxnKind.BUY),
                        tradedOn = c.l("traded_on"), units = c.d("units") ?: 0.0,
                        price = c.d("price") ?: 0.0, amount = c.d("amount") ?: 0.0, notes = c.s("notes")
                    )
                )
            }
        }
        return out
    }

    fun deleteTransaction(id: Long) = w.delete("transactions", "id = ?", arrayOf(id.toString()))

    // ---- price history

    fun upsertPrices(companyId: Long, points: List<PricePoint>) {
        w.beginTransaction()
        try {
            for (p in points) {
                w.insertWithOnConflict(
                    "prices", null,
                    valuesOf("company_id" to companyId, "on_date" to p.onDate, "close" to p.close),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    fun prices(companyId: Long): List<PricePoint> {
        val out = ArrayList<PricePoint>()
        r.rawQuery("SELECT * FROM prices WHERE company_id = ? ORDER BY on_date", arrayOf(companyId.toString())).use { c ->
            while (c.moveToNext()) out.add(PricePoint(companyId, c.l("on_date"), c.d("close") ?: 0.0))
        }
        return out
    }

    fun priceOnOrBefore(companyId: Long, date: Long): Double? {
        var value: Double? = null
        r.rawQuery(
            "SELECT close FROM prices WHERE company_id = ? AND on_date <= ? ORDER BY on_date DESC LIMIT 1",
            arrayOf(companyId.toString(), date.toString())
        ).use { c -> if (c.moveToFirst()) value = c.getDouble(0) }
        return value
    }

    fun priceHistoryCount(): Int {
        var n = 0
        r.rawQuery("SELECT COUNT(*) FROM prices", null).use { c -> if (c.moveToFirst()) n = c.getInt(0) }
        return n
    }
}

class DocumentDao(private val db: ArthamDb) {
    private val w: SQLiteDatabase get() = db.writableDatabase
    private val r: SQLiteDatabase get() = db.readableDatabase

    fun insert(title: String, companyId: Long?, chunks: List<String>): Long {
        val charCount = chunks.sumOf { it.length }
        val docId = w.insert(
            "documents", null,
            valuesOf(
                "title" to title, "company_id" to companyId,
                "added_at" to System.currentTimeMillis(), "char_count" to charCount,
                "chunk_count" to chunks.size
            )
        )
        w.beginTransaction()
        try {
            chunks.forEachIndexed { index, body ->
                w.insert("doc_chunks", null, valuesOf("doc_id" to docId, "ord" to index, "body" to body))
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        Logx.i("Docs", "Indexed \"$title\": ${chunks.size} chunks, $charCount characters")
        return docId
    }

    fun all(): List<DocumentRecord> {
        val out = ArrayList<DocumentRecord>()
        r.rawQuery("SELECT * FROM documents ORDER BY added_at DESC", null).use { c ->
            while (c.moveToNext()) {
                out.add(
                    DocumentRecord(
                        id = c.l("id"), title = c.s("title"),
                        companyId = if (c.isNull(c.getColumnIndex("company_id"))) null else c.l("company_id"),
                        addedAt = c.l("added_at"), charCount = c.l("char_count").toInt(),
                        chunkCount = c.l("chunk_count").toInt()
                    )
                )
            }
        }
        return out
    }

    fun chunks(docId: Long? = null): List<DocChunk> {
        val out = ArrayList<DocChunk>()
        val sql = if (docId == null) "SELECT * FROM doc_chunks ORDER BY doc_id, ord"
        else "SELECT * FROM doc_chunks WHERE doc_id = ? ORDER BY ord"
        r.rawQuery(sql, if (docId == null) null else arrayOf(docId.toString())).use { c ->
            while (c.moveToNext()) out.add(DocChunk(c.l("id"), c.l("doc_id"), c.l("ord").toInt(), c.s("body")))
        }
        return out
    }

    fun delete(id: Long) {
        w.delete("doc_chunks", "doc_id = ?", arrayOf(id.toString()))
        w.delete("documents", "id = ?", arrayOf(id.toString()))
    }

    fun clearAll() {
        w.delete("doc_chunks", null, null)
        w.delete("documents", null, null)
    }
}

class AlertDao(private val db: ArthamDb) {
    private val w: SQLiteDatabase get() = db.writableDatabase
    private val r: SQLiteDatabase get() = db.readableDatabase

    fun create(name: String, query: String): Long = w.insert(
        "alerts", null,
        valuesOf("name" to name, "query" to query, "enabled" to true, "created_at" to System.currentTimeMillis())
    )

    fun all(): List<AlertRule> {
        val out = ArrayList<AlertRule>()
        r.rawQuery("SELECT * FROM alerts ORDER BY created_at DESC", null).use { c ->
            while (c.moveToNext()) {
                out.add(
                    AlertRule(
                        id = c.l("id"), name = c.s("name"), query = c.s("query"),
                        enabled = c.b("enabled"), lastChecked = c.l("last_checked"),
                        lastCount = c.l("last_count").toInt(), createdAt = c.l("created_at")
                    )
                )
            }
        }
        return out
    }

    fun setResult(id: Long, count: Int) = w.update(
        "alerts", valuesOf("last_checked" to System.currentTimeMillis(), "last_count" to count), "id = ?", arrayOf(id.toString())
    )

    fun toggle(id: Long, enabled: Boolean) =
        w.update("alerts", valuesOf("enabled" to enabled), "id = ?", arrayOf(id.toString()))

    fun delete(id: Long) = w.delete("alerts", "id = ?", arrayOf(id.toString()))
}
