package `in`.artham.fundamentals.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import `in`.artham.fundamentals.core.Logx

class CompanyDao(private val db: ArthamDb) {

    private val w: SQLiteDatabase get() = db.writableDatabase
    private val r: SQLiteDatabase get() = db.readableDatabase

    fun upsert(company: Company): Long {
        val cv = valuesOf(
            "code" to company.code.uppercase(),
            "name" to company.name,
            "sector" to company.sector,
            "industry" to company.industry,
            "about" to company.about,
            "consolidated" to company.consolidated,
            "is_demo" to company.isDemo,
            "updated_at" to System.currentTimeMillis(),
            "face_value" to company.faceValue
        )
        val existing = findByCode(company.code, company.consolidated)
        return if (existing != null) {
            w.update("companies", cv, "id = ?", arrayOf(existing.id.toString()))
            existing.id
        } else {
            w.insert("companies", null, cv)
        }
    }

    fun replaceAnnual(companyId: Long, rows: List<AnnualFinancial>) {
        w.beginTransaction()
        try {
            w.delete("annual", "company_id = ?", arrayOf(companyId.toString()))
            for (a in rows) {
                w.insertWithOnConflict("annual", null, annualValues(companyId, a), SQLiteDatabase.CONFLICT_REPLACE)
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    fun replaceQuarterly(companyId: Long, rows: List<QuarterlyFinancial>) {
        w.beginTransaction()
        try {
            w.delete("quarterly", "company_id = ?", arrayOf(companyId.toString()))
            for (q in rows) {
                w.insertWithOnConflict("quarterly", null, quarterlyValues(companyId, q), SQLiteDatabase.CONFLICT_REPLACE)
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    fun replaceShareholding(companyId: Long, rows: List<ShareholdingRow>) {
        w.beginTransaction()
        try {
            w.delete("shareholding", "company_id = ?", arrayOf(companyId.toString()))
            for (s in rows) {
                w.insertWithOnConflict(
                    "shareholding", null,
                    valuesOf(
                        "company_id" to companyId, "label" to s.label, "sort_key" to s.sortKey,
                        "promoters" to s.promoters, "fii" to s.fii, "dii" to s.dii,
                        "government" to s.government, "public_holding" to s.publicHolding
                    ),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    fun upsertQuote(q: Quote) {
        w.insertWithOnConflict(
            "quotes", null,
            valuesOf(
                "company_id" to q.companyId, "price" to q.price, "change_percent" to q.changePercent,
                "high_52" to q.high52, "low_52" to q.low52, "market_cap" to q.marketCap,
                "updated_at" to q.updatedAt, "source" to q.source
            ),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun findByCode(code: String, consolidated: Boolean = true): Company? {
        var out: Company? = null
        r.rawQuery("SELECT * FROM companies WHERE code = ? AND consolidated = ? LIMIT 1", arrayOf(code.uppercase(), if (consolidated) "1" else "0")).use { c ->
            if (c.moveToFirst()) out = c.toCompany()
        }
        return out
    }

    fun byId(id: Long): Company? {
        var out: Company? = null
        r.rawQuery("SELECT * FROM companies WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) out = c.toCompany()
        }
        return out
    }

    fun all(): List<Company> {
        val out = ArrayList<Company>()
        r.rawQuery("SELECT * FROM companies ORDER BY name", null).use { c ->
            while (c.moveToNext()) out.add(c.toCompany())
        }
        return out
    }

    fun search(q: String, limit: Int = 40): List<Company> {
        if (q.isBlank()) return all().take(limit)
        val like = "%" + q.trim() + "%"
        val out = ArrayList<Company>()
        r.rawQuery(
            "SELECT * FROM companies WHERE name LIKE ? OR code LIKE ? ORDER BY CASE WHEN code LIKE ? THEN 0 ELSE 1 END, name LIMIT ?",
            arrayOf(like, like, q.trim() + "%", limit.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(c.toCompany())
        }
        return out
    }

    fun record(company: Company): CompanyRecord = CompanyRecord(
        company = company,
        annual = annualFor(company.id),
        quarterly = quarterlyFor(company.id),
        shareholding = shareholdingFor(company.id),
        quote = quoteFor(company.id)
    )

    fun recordById(id: Long): CompanyRecord? = byId(id)?.let { record(it) }

    fun annualFor(companyId: Long): List<AnnualFinancial> {
        val out = ArrayList<AnnualFinancial>()
        r.rawQuery("SELECT * FROM annual WHERE company_id = ? ORDER BY fy_end", arrayOf(companyId.toString())).use { c ->
            while (c.moveToNext()) out.add(c.toAnnual(companyId))
        }
        return out
    }

    fun quarterlyFor(companyId: Long): List<QuarterlyFinancial> {
        val out = ArrayList<QuarterlyFinancial>()
        r.rawQuery("SELECT * FROM quarterly WHERE company_id = ? ORDER BY fy_end, quarter", arrayOf(companyId.toString())).use { c ->
            while (c.moveToNext()) out.add(c.toQuarterly(companyId))
        }
        return out
    }

    fun shareholdingFor(companyId: Long): List<ShareholdingRow> {
        val out = ArrayList<ShareholdingRow>()
        r.rawQuery("SELECT * FROM shareholding WHERE company_id = ? ORDER BY sort_key", arrayOf(companyId.toString())).use { c ->
            while (c.moveToNext()) {
                out.add(
                    ShareholdingRow(
                        companyId = companyId, label = c.s("label"), sortKey = c.l("sort_key").toInt(),
                        promoters = c.d("promoters"), fii = c.d("fii"), dii = c.d("dii"),
                        government = c.d("government"), publicHolding = c.d("public_holding")
                    )
                )
            }
        }
        return out
    }

    fun quoteFor(companyId: Long): Quote? {
        var out: Quote? = null
        r.rawQuery("SELECT * FROM quotes WHERE company_id = ?", arrayOf(companyId.toString())).use { c ->
            if (c.moveToFirst()) {
                out = Quote(
                    companyId = companyId, price = c.d("price"), changePercent = c.d("change_percent"),
                    high52 = c.d("high_52"), low52 = c.d("low_52"), marketCap = c.d("market_cap"),
                    updatedAt = c.l("updated_at"), source = c.s("source")
                )
            }
        }
        return out
    }

    /** Loads every company with its full history. Used by the screening engine. */
    fun allRecords(): List<CompanyRecord> {
        val companies = all()
        if (companies.isEmpty()) return emptyList()
        val annualByCompany = HashMap<Long, MutableList<AnnualFinancial>>()
        r.rawQuery("SELECT * FROM annual ORDER BY company_id, fy_end", null).use { c ->
            while (c.moveToNext()) {
                val id = c.l("company_id")
                annualByCompany.getOrPut(id) { ArrayList() }.add(c.toAnnual(id))
            }
        }
        val quarterlyByCompany = HashMap<Long, MutableList<QuarterlyFinancial>>()
        r.rawQuery("SELECT * FROM quarterly ORDER BY company_id, fy_end, quarter", null).use { c ->
            while (c.moveToNext()) {
                val id = c.l("company_id")
                quarterlyByCompany.getOrPut(id) { ArrayList() }.add(c.toQuarterly(id))
            }
        }
        val shareByCompany = HashMap<Long, MutableList<ShareholdingRow>>()
        r.rawQuery("SELECT * FROM shareholding ORDER BY company_id, sort_key", null).use { c ->
            while (c.moveToNext()) {
                val id = c.l("company_id")
                shareByCompany.getOrPut(id) { ArrayList() }.add(
                    ShareholdingRow(
                        companyId = id, label = c.s("label"), sortKey = c.l("sort_key").toInt(),
                        promoters = c.d("promoters"), fii = c.d("fii"), dii = c.d("dii"),
                        government = c.d("government"), publicHolding = c.d("public_holding")
                    )
                )
            }
        }
        val quotes = HashMap<Long, Quote>()
        r.rawQuery("SELECT * FROM quotes", null).use { c ->
            while (c.moveToNext()) {
                val id = c.l("company_id")
                quotes[id] = Quote(
                    companyId = id, price = c.d("price"), changePercent = c.d("change_percent"),
                    high52 = c.d("high_52"), low52 = c.d("low_52"), marketCap = c.d("market_cap"),
                    updatedAt = c.l("updated_at"), source = c.s("source")
                )
            }
        }
        return companies.map { co ->
            CompanyRecord(
                company = co,
                annual = annualByCompany[co.id] ?: emptyList(),
                quarterly = quarterlyByCompany[co.id] ?: emptyList(),
                shareholding = shareByCompany[co.id] ?: emptyList(),
                quote = quotes[co.id]
            )
        }
    }

    fun stats(): DatasetStats {
        fun count(sql: String): Int {
            var n = 0
            r.rawQuery(sql, null).use { c -> if (c.moveToFirst()) n = c.getInt(0) }
            return n
        }
        return DatasetStats(
            companies = count("SELECT COUNT(*) FROM companies"),
            annualRows = count("SELECT COUNT(*) FROM annual"),
            quarterlyRows = count("SELECT COUNT(*) FROM quarterly"),
            demoCompanies = count("SELECT COUNT(*) FROM companies WHERE is_demo = 1"),
            lastImportAt = run {
                var v = 0L
                r.rawQuery("SELECT MAX(created_at) FROM import_batches", null).use { c -> if (c.moveToFirst() && !c.isNull(0)) v = c.getLong(0) }
                v
            },
            withQuotes = count("SELECT COUNT(*) FROM quotes WHERE price IS NOT NULL")
        )
    }

    fun deleteAll(includeDemo: Boolean = true) {
        w.beginTransaction()
        try {
            w.delete("annual", null, null)
            w.delete("quarterly", null, null)
            w.delete("shareholding", null, null)
            w.delete("quotes", null, null)
            w.delete("watchlist_items", null, null)
            if (includeDemo) w.delete("companies", null, null) else w.delete("companies", "is_demo = 1", null)
            w.setTransactionSuccessful()
            Logx.i("Db", if (includeDemo) "Cleared all local company data" else "Cleared demo data only")
        } finally {
            w.endTransaction()
        }
    }

    private fun annualValues(companyId: Long, a: AnnualFinancial) = valuesOf(
        "company_id" to companyId, "fy_end" to a.fyEnd, "sales" to a.sales, "expenses" to a.expenses,
        "operating_profit" to a.operatingProfit, "opm_percent" to a.opmPercent, "other_income" to a.otherIncome,
        "interest" to a.interest, "depreciation" to a.depreciation, "profit_before_tax" to a.profitBeforeTax,
        "tax_percent" to a.taxPercent, "net_profit" to a.netProfit, "eps" to a.eps, "dividend" to a.dividend,
        "equity_capital" to a.equityCapital, "reserves" to a.reserves, "borrowings" to a.borrowings,
        "other_liabilities" to a.otherLiabilities, "total_liabilities" to a.totalLiabilities,
        "fixed_assets" to a.fixedAssets, "cwip" to a.cwip, "investments" to a.investments,
        "other_assets" to a.otherAssets, "total_assets" to a.totalAssets, "receivables" to a.receivables,
        "inventory" to a.inventory, "payables" to a.payables, "cfo" to a.cfo, "cfi" to a.cfi,
        "cff" to a.cff, "net_cash_flow" to a.netCashFlow
    )

    private fun quarterlyValues(companyId: Long, q: QuarterlyFinancial) = valuesOf(
        "company_id" to companyId, "fy_end" to q.fyEnd, "quarter" to q.quarter, "label" to q.label,
        "sales" to q.sales, "expenses" to q.expenses, "operating_profit" to q.operatingProfit,
        "opm_percent" to q.opmPercent, "other_income" to q.otherIncome, "interest" to q.interest,
        "depreciation" to q.depreciation, "profit_before_tax" to q.profitBeforeTax,
        "tax_percent" to q.taxPercent, "net_profit" to q.netProfit, "eps" to q.eps
    )
}

private fun android.database.Cursor.toCompany() = Company(
    id = l("id"), code = s("code"), name = s("name"), sector = s("sector"), industry = s("industry"),
    about = s("about"), consolidated = b("consolidated"), isDemo = b("is_demo"), updatedAt = l("updated_at"),
    faceValue = d("face_value") ?: 10.0
)

private fun android.database.Cursor.toAnnual(companyId: Long) = AnnualFinancial(
    companyId = companyId, fyEnd = l("fy_end").toInt(), sales = d("sales"), expenses = d("expenses"),
    operatingProfit = d("operating_profit"), opmPercent = d("opm_percent"), otherIncome = d("other_income"),
    interest = d("interest"), depreciation = d("depreciation"), profitBeforeTax = d("profit_before_tax"),
    taxPercent = d("tax_percent"), netProfit = d("net_profit"), eps = d("eps"), dividend = d("dividend"),
    equityCapital = d("equity_capital"), reserves = d("reserves"), borrowings = d("borrowings"),
    otherLiabilities = d("other_liabilities"), totalLiabilities = d("total_liabilities"),
    fixedAssets = d("fixed_assets"), cwip = d("cwip"), investments = d("investments"),
    otherAssets = d("other_assets"), totalAssets = d("total_assets"), receivables = d("receivables"),
    inventory = d("inventory"), payables = d("payables"), cfo = d("cfo"), cfi = d("cfi"),
    cff = d("cff"), netCashFlow = d("net_cash_flow")
)

private fun android.database.Cursor.toQuarterly(companyId: Long) = QuarterlyFinancial(
    companyId = companyId, fyEnd = l("fy_end").toInt(), quarter = l("quarter").toInt(), label = s("label"),
    sales = d("sales"), expenses = d("expenses"), operatingProfit = d("operating_profit"),
    opmPercent = d("opm_percent"), otherIncome = d("other_income"), interest = d("interest"),
    depreciation = d("depreciation"), profitBeforeTax = d("profit_before_tax"), taxPercent = d("tax_percent"),
    netProfit = d("net_profit"), eps = d("eps")
)

class WatchlistDao(private val db: ArthamDb) {
    private val w: SQLiteDatabase get() = db.writableDatabase
    private val r: SQLiteDatabase get() = db.readableDatabase

    fun create(name: String): Long = w.insert("watchlists", null, valuesOf("name" to name, "created_at" to System.currentTimeMillis()))

    fun all(): List<Watchlist> {
        val out = ArrayList<Watchlist>()
        r.rawQuery("SELECT * FROM watchlists ORDER BY created_at", null).use { c ->
            while (c.moveToNext()) out.add(Watchlist(c.l("id"), c.s("name"), c.l("created_at")))
        }
        return out
    }

    fun ensureDefault(): Watchlist {
        val existing = all()
        if (existing.isNotEmpty()) return existing.first()
        create("My watchlist")
        return all().first()
    }

    fun rename(id: Long, name: String) = w.update("watchlists", valuesOf("name" to name), "id = ?", arrayOf(id.toString()))

    fun delete(id: Long) {
        w.delete("watchlist_items", "watchlist_id = ?", arrayOf(id.toString()))
        w.delete("watchlists", "id = ?", arrayOf(id.toString()))
    }

    fun add(watchlistId: Long, companyId: Long, note: String = ""): Boolean {
        val cv = valuesOf("watchlist_id" to watchlistId, "company_id" to companyId, "note" to note, "added_at" to System.currentTimeMillis())
        return w.insertWithOnConflict("watchlist_items", null, cv, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun remove(watchlistId: Long, companyId: Long) =
        w.delete("watchlist_items", "watchlist_id = ? AND company_id = ?", arrayOf(watchlistId.toString(), companyId.toString()))

    fun contains(watchlistId: Long, companyId: Long): Boolean {
        var found = false
        r.rawQuery("SELECT 1 FROM watchlist_items WHERE watchlist_id = ? AND company_id = ?", arrayOf(watchlistId.toString(), companyId.toString())).use { c ->
            found = c.moveToFirst()
        }
        return found
    }

    fun members(watchlistId: Long): List<Company> {
        val out = ArrayList<Company>()
        r.rawQuery(
            "SELECT c.* FROM companies c JOIN watchlist_items i ON i.company_id = c.id WHERE i.watchlist_id = ? ORDER BY c.name",
            arrayOf(watchlistId.toString())
        ).use { c -> while (c.moveToNext()) out.add(c.toCompany()) }
        return out
    }

    fun count(watchlistId: Long): Int {
        var n = 0
        r.rawQuery("SELECT COUNT(*) FROM watchlist_items WHERE watchlist_id = ?", arrayOf(watchlistId.toString())).use { c -> if (c.moveToFirst()) n = c.getInt(0) }
        return n
    }
}

class ScreenDao(private val db: ArthamDb) {
    private val w: SQLiteDatabase get() = db.writableDatabase
    private val r: SQLiteDatabase get() = db.readableDatabase

    fun save(name: String, query: String, description: String = ""): Long = w.insert(
        "saved_screens", null,
        valuesOf("name" to name, "query" to query, "description" to description, "created_at" to System.currentTimeMillis())
    )

    fun all(): List<SavedScreen> {
        val out = ArrayList<SavedScreen>()
        r.rawQuery("SELECT * FROM saved_screens ORDER BY last_run_at DESC, created_at DESC", null).use { c ->
            while (c.moveToNext()) out.add(
                SavedScreen(
                    id = c.l("id"), name = c.s("name"), query = c.s("query"), description = c.s("description"),
                    createdAt = c.l("created_at"), lastRunAt = c.l("last_run_at"), lastCount = c.l("last_count").toInt()
                )
            )
        }
        return out
    }

    fun touch(id: Long, count: Int) = w.update(
        "saved_screens", valuesOf("last_run_at" to System.currentTimeMillis(), "last_count" to count), "id = ?", arrayOf(id.toString())
    )

    fun delete(id: Long) = w.delete("saved_screens", "id = ?", arrayOf(id.toString()))
}

class AiDao(private val db: ArthamDb) {
    private val w: SQLiteDatabase get() = db.writableDatabase
    private val r: SQLiteDatabase get() = db.readableDatabase

    fun append(sessionKey: String, role: String, content: String, engine: String): Long = w.insert(
        "chat_messages", null,
        valuesOf("session_key" to sessionKey, "role" to role, "content" to content, "created_at" to System.currentTimeMillis(), "engine" to engine)
    )

    fun messages(sessionKey: String, limit: Int = 200): List<ChatMessage> {
        val out = ArrayList<ChatMessage>()
        r.rawQuery("SELECT * FROM chat_messages WHERE session_key = ? ORDER BY created_at ASC, id ASC LIMIT ?", arrayOf(sessionKey, limit.toString())).use { c ->
            while (c.moveToNext()) out.add(
                ChatMessage(c.l("id"), c.s("session_key"), c.s("role"), c.s("content"), c.l("created_at"), c.s("engine"))
            )
        }
        return out
    }

    fun clearSession(sessionKey: String) = w.delete("chat_messages", "session_key = ?", arrayOf(sessionKey))

    fun clearAll() = w.delete("chat_messages", null, null)
}

class ModelPackDao(private val db: ArthamDb) {
    private val w: SQLiteDatabase get() = db.writableDatabase
    private val r: SQLiteDatabase get() = db.readableDatabase

    fun insert(rec: ModelPackRecord): Long {
        w.update("model_packs", valuesOf("is_active" to false), "engine = ?", arrayOf(rec.engine))
        return w.insert(
            "model_packs", null,
            valuesOf(
                "name" to rec.name, "engine" to rec.engine, "path" to rec.path, "size_bytes" to rec.sizeBytes,
                "sha256" to rec.sha256, "imported_at" to System.currentTimeMillis(), "is_active" to true
            )
        )
    }

    fun all(): List<ModelPackRecord> {
        val out = ArrayList<ModelPackRecord>()
        r.rawQuery("SELECT * FROM model_packs ORDER BY imported_at DESC", null).use { c ->
            while (c.moveToNext()) out.add(
                ModelPackRecord(
                    id = c.l("id"), name = c.s("name"), engine = c.s("engine"), path = c.s("path"),
                    sizeBytes = c.l("size_bytes"), sha256 = c.s("sha256"), importedAt = c.l("imported_at"),
                    isActive = c.b("is_active")
                )
            )
        }
        return out
    }

    fun active(engine: String): ModelPackRecord? = all().firstOrNull { it.engine == engine && it.isActive }

    fun delete(id: Long) = w.delete("model_packs", "id = ?", arrayOf(id.toString()))
}

class LogDao(private val db: ArthamDb) {
    private val w: SQLiteDatabase get() = db.writableDatabase
    private val r: SQLiteDatabase get() = db.readableDatabase

    fun append(level: String, tag: String, message: String) {
        w.insert("activity_log", null, valuesOf("created_at" to System.currentTimeMillis(), "level" to level, "tag" to tag, "message" to message))
    }

    fun recent(limit: Int = 300): List<String> {
        val out = ArrayList<String>()
        r.rawQuery("SELECT * FROM activity_log ORDER BY created_at DESC, id DESC LIMIT ?", arrayOf(limit.toString())).use { c ->
            while (c.moveToNext()) out.add("${c.l("created_at")} | ${c.s("level")} | ${c.s("tag")} | ${c.s("message")}")
        }
        return out
    }

    fun clear() = w.delete("activity_log", null, null)
}

class ImportDao(private val db: ArthamDb) {
    private val w: SQLiteDatabase get() = db.writableDatabase
    private val r: SQLiteDatabase get() = db.readableDatabase

    fun record(fileName: String, companies: Int, annual: Int, quarterly: Int, isDemo: Boolean, note: String): Long = w.insert(
        "import_batches", null,
        valuesOf(
            "created_at" to System.currentTimeMillis(), "file_name" to fileName, "companies" to companies,
            "annual_rows" to annual, "quarterly_rows" to quarterly, "is_demo" to isDemo, "note" to note
        )
    )

    fun all(): List<ImportBatch> {
        val out = ArrayList<ImportBatch>()
        r.rawQuery("SELECT * FROM import_batches ORDER BY created_at DESC", null).use { c ->
            while (c.moveToNext()) out.add(
                ImportBatch(
                    id = c.l("id"), createdAt = c.l("created_at"), fileName = c.s("file_name"),
                    companies = c.l("companies").toInt(), annualRows = c.l("annual_rows").toInt(),
                    quarterlyRows = c.l("quarterly_rows").toInt(), isDemo = c.b("is_demo"), note = c.s("note")
                )
            )
        }
        return out
    }
}
