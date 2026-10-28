package `in`.artham.fundamentals.data

import android.database.sqlite.SQLiteDatabase
import `in`.artham.fundamentals.domain.PriceBar

/** Storage for daily candles and the per-company sync ledger. */
class SyncDao(private val db: ArthamDb) {
    private val w: SQLiteDatabase get() = db.writableDatabase
    private val r: SQLiteDatabase get() = db.readableDatabase

    fun upsertBars(companyId: Long, bars: List<PriceBar>) {
        if (bars.isEmpty()) return
        w.beginTransaction()
        try {
            for (bar in bars) {
                w.insertWithOnConflict(
                    "price_bars", null,
                    valuesOf(
                        "company_id" to companyId, "on_date" to bar.time, "open" to bar.open,
                        "high" to bar.high, "low" to bar.low, "close" to bar.close, "volume" to bar.volume
                    ),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    fun bars(companyId: Long, fromMillis: Long = 0L): List<PriceBar> {
        val out = ArrayList<PriceBar>()
        r.rawQuery(
            "SELECT * FROM price_bars WHERE company_id = ? AND on_date >= ? ORDER BY on_date",
            arrayOf(companyId.toString(), fromMillis.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    PriceBar(
                        time = c.l("on_date"),
                        open = c.d("open") ?: 0.0,
                        high = c.d("high") ?: 0.0,
                        low = c.d("low") ?: 0.0,
                        close = c.d("close") ?: 0.0,
                        volume = c.l("volume")
                    )
                )
            }
        }
        return out
    }

    fun barCount(companyId: Long): Int {
        var n = 0
        r.rawQuery("SELECT COUNT(*) FROM price_bars WHERE company_id = ?", arrayOf(companyId.toString())).use { c ->
            if (c.moveToFirst()) n = c.getInt(0)
        }
        return n
    }

    fun totalBars(): Int {
        var n = 0
        r.rawQuery("SELECT COUNT(*) FROM price_bars", null).use { c -> if (c.moveToFirst()) n = c.getInt(0) }
        return n
    }

    fun companiesWithBars(): Int {
        var n = 0
        r.rawQuery("SELECT COUNT(DISTINCT company_id) FROM price_bars", null).use { c -> if (c.moveToFirst()) n = c.getInt(0) }
        return n
    }

    fun lastBarTime(companyId: Long): Long {
        var t = 0L
        r.rawQuery("SELECT MAX(on_date) FROM price_bars WHERE company_id = ?", arrayOf(companyId.toString())).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) t = c.getLong(0)
        }
        return t
    }

    fun priceOnOrBefore(companyId: Long, date: Long): Double? {
        var value: Double? = null
        r.rawQuery(
            "SELECT close FROM price_bars WHERE company_id = ? AND on_date <= ? ORDER BY on_date DESC LIMIT 1",
            arrayOf(companyId.toString(), date.toString())
        ).use { c -> if (c.moveToFirst()) value = c.getDouble(0) }
        return value
    }

    fun markSync(companyId: Long, kind: String, status: String, message: String) {
        w.insertWithOnConflict(
            "sync_state", null,
            valuesOf(
                "company_id" to companyId, "kind" to kind,
                "last_synced" to System.currentTimeMillis(), "status" to status, "message" to message
            ),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun lastSync(companyId: Long, kind: String): Long {
        var t = 0L
        r.rawQuery(
            "SELECT last_synced FROM sync_state WHERE company_id = ? AND kind = ?",
            arrayOf(companyId.toString(), kind)
        ).use { c -> if (c.moveToFirst() && !c.isNull(0)) t = c.getLong(0) }
        return t
    }

    data class Failure(val companyId: Long, val message: String, val lastSynced: Long)

    fun failures(limit: Int = 40): List<Failure> {
        val out = ArrayList<Failure>()
        r.rawQuery(
            "SELECT company_id, message, last_synced FROM sync_state WHERE status = 'FAILED' ORDER BY last_synced DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(Failure(c.l("company_id"), c.s("message"), c.l("last_synced")))
        }
        return out
    }

    fun failedCount(): Int {
        var n = 0
        r.rawQuery("SELECT COUNT(*) FROM sync_state WHERE status = 'FAILED'", null).use { c ->
            if (c.moveToFirst()) n = c.getInt(0)
        }
        return n
    }
}
