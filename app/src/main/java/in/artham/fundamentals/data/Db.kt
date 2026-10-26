package `in`.artham.fundamentals.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import `in`.artham.fundamentals.core.Logx

/**
 * Local SQLite store. Deliberately hand-written SQL with no annotation processor:
 * it keeps the build lean (no KSP/kapt), keeps the APK small, and makes every
 * query visible and auditable.
 */
class ArthamDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        Logx.i("Db", "Creating schema v$VERSION")
        db.execSQL(
            """
            CREATE TABLE companies (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                code TEXT NOT NULL,
                name TEXT NOT NULL,
                sector TEXT NOT NULL DEFAULT '',
                industry TEXT NOT NULL DEFAULT '',
                about TEXT NOT NULL DEFAULT '',
                consolidated INTEGER NOT NULL DEFAULT 1,
                is_demo INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0,
                UNIQUE(code, consolidated)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_companies_name ON companies(name)")

        db.execSQL(
            """
            CREATE TABLE annual (
                company_id INTEGER NOT NULL,
                fy_end INTEGER NOT NULL,
                sales REAL, expenses REAL, operating_profit REAL, opm_percent REAL,
                other_income REAL, interest REAL, depreciation REAL, profit_before_tax REAL,
                tax_percent REAL, net_profit REAL, eps REAL, dividend REAL,
                equity_capital REAL, reserves REAL, borrowings REAL, other_liabilities REAL,
                total_liabilities REAL, fixed_assets REAL, cwip REAL, investments REAL,
                other_assets REAL, total_assets REAL, receivables REAL, inventory REAL,
                payables REAL, cfo REAL, cfi REAL, cff REAL, net_cash_flow REAL,
                PRIMARY KEY(company_id, fy_end)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE quarterly (
                company_id INTEGER NOT NULL,
                fy_end INTEGER NOT NULL,
                quarter INTEGER NOT NULL,
                label TEXT NOT NULL,
                sales REAL, expenses REAL, operating_profit REAL, opm_percent REAL,
                other_income REAL, interest REAL, depreciation REAL, profit_before_tax REAL,
                tax_percent REAL, net_profit REAL, eps REAL,
                PRIMARY KEY(company_id, fy_end, quarter)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE shareholding (
                company_id INTEGER NOT NULL,
                label TEXT NOT NULL,
                sort_key INTEGER NOT NULL,
                promoters REAL, fii REAL, dii REAL, government REAL, public_holding REAL,
                PRIMARY KEY(company_id, sort_key)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE quotes (
                company_id INTEGER PRIMARY KEY,
                price REAL, change_percent REAL, high_52 REAL, low_52 REAL,
                market_cap REAL, updated_at INTEGER NOT NULL DEFAULT 0, source TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )

        db.execSQL("CREATE TABLE watchlists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, created_at INTEGER NOT NULL DEFAULT 0)")
        db.execSQL(
            """
            CREATE TABLE watchlist_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                watchlist_id INTEGER NOT NULL,
                company_id INTEGER NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                added_at INTEGER NOT NULL DEFAULT 0,
                UNIQUE(watchlist_id, company_id)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE saved_screens (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                query TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL DEFAULT 0,
                last_run_at INTEGER NOT NULL DEFAULT 0,
                last_count INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_key TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL DEFAULT 0,
                engine TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_chat_session ON chat_messages(session_key, created_at)")

        db.execSQL(
            """
            CREATE TABLE model_packs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                engine TEXT NOT NULL,
                path TEXT NOT NULL,
                size_bytes INTEGER NOT NULL DEFAULT 0,
                sha256 TEXT NOT NULL DEFAULT '',
                imported_at INTEGER NOT NULL DEFAULT 0,
                is_active INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE import_batches (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at INTEGER NOT NULL DEFAULT 0,
                file_name TEXT NOT NULL,
                companies INTEGER NOT NULL DEFAULT 0,
                annual_rows INTEGER NOT NULL DEFAULT 0,
                quarterly_rows INTEGER NOT NULL DEFAULT 0,
                is_demo INTEGER NOT NULL DEFAULT 0,
                note TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )


        db.execSQL(
            """
            CREATE TABLE portfolios (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                created_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                portfolio_id INTEGER NOT NULL,
                company_id INTEGER NOT NULL,
                kind TEXT NOT NULL,
                traded_on INTEGER NOT NULL,
                units REAL NOT NULL DEFAULT 0,
                price REAL NOT NULL DEFAULT 0,
                amount REAL NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_txn_portfolio ON transactions(portfolio_id, traded_on)")

        db.execSQL(
            """
            CREATE TABLE prices (
                company_id INTEGER NOT NULL,
                on_date INTEGER NOT NULL,
                close REAL NOT NULL,
                PRIMARY KEY(company_id, on_date)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE documents (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                company_id INTEGER,
                added_at INTEGER NOT NULL DEFAULT 0,
                char_count INTEGER NOT NULL DEFAULT 0,
                chunk_count INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE doc_chunks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                doc_id INTEGER NOT NULL,
                ord INTEGER NOT NULL,
                body TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_chunk_doc ON doc_chunks(doc_id, ord)")

        db.execSQL(
            """
            CREATE TABLE alerts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                query TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                last_checked INTEGER NOT NULL DEFAULT 0,
                last_count INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE activity_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at INTEGER NOT NULL,
                level TEXT NOT NULL,
                tag TEXT NOT NULL,
                message TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_log_time ON activity_log(created_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Logx.w("Db", "Upgrade from v$oldVersion to v$newVersion; preserving user data")
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS activity_log (id INTEGER PRIMARY KEY AUTOINCREMENT, created_at INTEGER NOT NULL, level TEXT NOT NULL, tag TEXT NOT NULL, message TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_log_time ON activity_log(created_at)")
        }
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS portfolios (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, created_at INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE IF NOT EXISTS transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, portfolio_id INTEGER NOT NULL, company_id INTEGER NOT NULL, kind TEXT NOT NULL, traded_on INTEGER NOT NULL, units REAL NOT NULL DEFAULT 0, price REAL NOT NULL DEFAULT 0, amount REAL NOT NULL DEFAULT 0, notes TEXT NOT NULL DEFAULT '')")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_txn_portfolio ON transactions(portfolio_id, traded_on)")
            db.execSQL("CREATE TABLE IF NOT EXISTS prices (company_id INTEGER NOT NULL, on_date INTEGER NOT NULL, close REAL NOT NULL, PRIMARY KEY(company_id, on_date))")
            db.execSQL("CREATE TABLE IF NOT EXISTS documents (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, company_id INTEGER, added_at INTEGER NOT NULL DEFAULT 0, char_count INTEGER NOT NULL DEFAULT 0, chunk_count INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE IF NOT EXISTS doc_chunks (id INTEGER PRIMARY KEY AUTOINCREMENT, doc_id INTEGER NOT NULL, ord INTEGER NOT NULL, body TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_chunk_doc ON doc_chunks(doc_id, ord)")
            db.execSQL("CREATE TABLE IF NOT EXISTS alerts (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, query TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1, last_checked INTEGER NOT NULL DEFAULT 0, last_count INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL DEFAULT 0)")
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    companion object {
        const val NAME = "artham.db"
        const val VERSION = 3

        @Volatile
        private var instance: ArthamDb? = null

        fun get(context: Context): ArthamDb =
            instance ?: synchronized(this) {
                instance ?: ArthamDb(context).also { instance = it }
            }
    }
}

/** Small helpers so every DAO reads the same way and never leaks a cursor. */
internal fun SQLiteDatabase.rows(sql: String, args: Array<String> = emptyArray(), mapper: (Cursor) -> Unit) {
    useCursor(rawQuery(sql, args), mapper)
}

internal fun SQLiteDatabase.useCursor(c: Cursor, mapper: (Cursor) -> Unit) {
    try {
        while (c.moveToNext()) mapper(c)
    } finally {
        c.close()
    }
}

internal fun Cursor.d(name: String): Double? = getColumnIndex(name).let { if (it < 0 || isNull(it)) null else getDouble(it) }
internal fun Cursor.l(name: String): Long = getColumnIndex(name).let { if (it < 0 || isNull(it)) 0L else getLong(it) }
internal fun Cursor.s(name: String): String = getColumnIndex(name).let { if (it < 0 || isNull(it)) "" else getString(it) ?: "" }
internal fun Cursor.b(name: String): Boolean = l(name) != 0L

internal fun valuesOf(vararg pairs: Pair<String, Any?>): ContentValues {
    val cv = ContentValues()
    for ((k, v) in pairs) {
        when (v) {
            null -> cv.putNull(k)
            is String -> cv.put(k, v)
            is Boolean -> cv.put(k, if (v) 1 else 0)
            is Int -> cv.put(k, v)
            is Long -> cv.put(k, v)
            is Double -> cv.put(k, v)
            is Float -> cv.put(k, v)
            else -> cv.put(k, v.toString())
        }
    }
    return cv
}
