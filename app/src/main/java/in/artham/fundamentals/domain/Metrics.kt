package `in`.artham.fundamentals.domain

import `in`.artham.fundamentals.core.Num

enum class MetricUnit(val key: String) {
    CRORE("CRORE"), PERCENT("PERCENT"), TIMES("TIMES"), DAYS("DAYS"),
    RUPEE("RUPEE"), SCORE("SCORE"), COUNT("COUNT")
}

/**
 * One screenable quantity. [id] is the token used in the query language and is
 * stable: saved screens depend on it. [aliases] let a natural-language phrase
 * resolve to the same metric without changing the stored query.
 */
data class MetricDef(
    val id: String,
    val label: String,
    val unit: MetricUnit,
    val group: String,
    val formula: String,
    val compute: (Financials) -> Double?,
    val aliases: List<String> = emptyList()
)

object MetricCatalog {

    private fun yoy(f: Financials, pick: (Financials, Int) -> Double?): Double? {
        val now = pick(f, 0) ?: return null
        val prev = pick(f, 1) ?: return null
        return Num.growth(prev, now)
    }

    private fun cagrOver(f: Financials, years: Int, pick: (Financials, Int) -> Double?): Double? {
        val now = pick(f, 0) ?: return null
        val then = pick(f, years) ?: return null
        return Num.cagr(then, now, years)
    }

    private fun salesOf(f: Financials, back: Int): Double? = f.year(back)?.sales
    private fun profitOf(f: Financials, back: Int): Double? = f.year(back)?.netProfit
    private fun epsOf(f: Financials, back: Int): Double? = f.year(back)?.eps

    val all: List<MetricDef> = listOf(
        // ---------------------------------------------------------------- market
        MetricDef("market_cap", "Market cap", MetricUnit.CRORE, "Market", "price x shares outstanding", { Ratios.marketCap(it) }, listOf("mcap", "marketcap")),
        MetricDef("price", "Price", MetricUnit.RUPEE, "Market", "latest quote", { it.quote?.price }, listOf("cmp", "ltp")),
        MetricDef("pe", "Stock P/E", MetricUnit.TIMES, "Market", "price / EPS", { Ratios.pe(it) }, listOf("p/e", "pe_ratio", "stock_pe")),
        MetricDef("pb", "Price to book", MetricUnit.TIMES, "Market", "price / book value per share", { Ratios.pb(it) }, listOf("p/b", "price_to_book")),
        MetricDef("book_value", "Book value", MetricUnit.RUPEE, "Market", "net worth / shares outstanding", { Ratios.bookValuePerShare(it) }, listOf("bv", "bvps")),
        MetricDef("dividend_yield", "Dividend yield", MetricUnit.PERCENT, "Market", "dividend per share / price x 100", { Ratios.dividendYield(it) }, listOf("div_yield", "yield")),
        MetricDef("earnings_yield", "Earnings yield", MetricUnit.PERCENT, "Market", "1 / PE x 100", { Ratios.earningsYield(it) }, listOf("ey")),
        MetricDef("high_52", "52 week high", MetricUnit.RUPEE, "Market", "stored quote", { it.quote?.high52 }, listOf("high52")),
        MetricDef("low_52", "52 week low", MetricUnit.RUPEE, "Market", "stored quote", { it.quote?.low52 }, listOf("low52")),
        MetricDef("pct_from_high", "Distance from 52w high", MetricUnit.PERCENT, "Market", "(high52 - price) / high52 x 100", { f ->
            val hi = f.quote?.high52
            val p = f.quote?.price
            if (hi == null || p == null || hi <= 0.0) null else (hi - p) / hi * 100.0
        }, listOf("from_high")),

        // ------------------------------------------------------------------ size
        MetricDef("sales", "Sales", MetricUnit.CRORE, "Size", "latest annual revenue", { it.latest?.sales }, listOf("revenue", "turnover")),
        MetricDef("net_profit", "Net profit", MetricUnit.CRORE, "Size", "latest annual profit after tax", { it.latest?.netProfit }, listOf("pat", "profit")),
        MetricDef("operating_profit", "Operating profit", MetricUnit.CRORE, "Size", "latest annual EBITDA", { it.latest?.operatingProfit }, listOf("ebitda", "op")),
        MetricDef("eps", "EPS", MetricUnit.RUPEE, "Size", "latest annual earnings per share", { it.latest?.eps }, listOf("earnings_per_share")),
        MetricDef("total_assets", "Total assets", MetricUnit.CRORE, "Size", "latest balance sheet total", { it.latest?.totalAssets }, listOf("assets")),

        // ---------------------------------------------------------------- growth
        MetricDef("sales_growth", "Sales growth (1y)", MetricUnit.PERCENT, "Growth", "YoY sales growth", { yoy(it) { f, b -> salesOf(f, b) } }, listOf("sales_growth_1y", "revenue_growth")),
        MetricDef("profit_growth", "Profit growth (1y)", MetricUnit.PERCENT, "Growth", "YoY net profit growth", { yoy(it) { f, b -> profitOf(f, b) } }, listOf("profit_growth_1y", "pat_growth")),
        MetricDef("sales_growth_3y", "Sales growth (3y)", MetricUnit.PERCENT, "Growth", "3 year sales CAGR", { cagrOver(it, 3) { f, b -> salesOf(f, b) } }, listOf("sales_cagr_3y")),
        MetricDef("sales_growth_5y", "Sales growth (5y)", MetricUnit.PERCENT, "Growth", "5 year sales CAGR", { cagrOver(it, 5) { f, b -> salesOf(f, b) } }, listOf("sales_cagr_5y", "sales_growth_5")),
        MetricDef("profit_growth_3y", "Profit growth (3y)", MetricUnit.PERCENT, "Growth", "3 year profit CAGR", { cagrOver(it, 3) { f, b -> profitOf(f, b) } }, listOf("profit_cagr_3y")),
        MetricDef("profit_growth_5y", "Profit growth (5y)", MetricUnit.PERCENT, "Growth", "5 year profit CAGR", { cagrOver(it, 5) { f, b -> profitOf(f, b) } }, listOf("profit_cagr_5y")),
        MetricDef("eps_growth_3y", "EPS growth (3y)", MetricUnit.PERCENT, "Growth", "3 year EPS CAGR", { cagrOver(it, 3) { f, b -> epsOf(f, b) } }, listOf("eps_cagr_3y")),
        MetricDef("q_sales_growth", "Quarterly sales growth", MetricUnit.PERCENT, "Growth", "latest quarter vs same quarter last year", { f ->
            val now = f.quarter(0)?.sales
            val prev = f.quarter(4)?.sales
            if (now == null || prev == null) null else Num.growth(prev, now)
        }, listOf("qoq_sales", "quarter_sales_growth")),
        MetricDef("q_profit_growth", "Quarterly profit growth", MetricUnit.PERCENT, "Growth", "latest quarter vs same quarter last year", { f ->
            val now = f.quarter(0)?.netProfit
            val prev = f.quarter(4)?.netProfit
            if (now == null || prev == null) null else Num.growth(prev, now)
        }, listOf("qoq_profit", "quarter_profit_growth")),

        // --------------------------------------------------------------- quality
        MetricDef("opm", "Operating margin", MetricUnit.PERCENT, "Quality", "operating profit / sales x 100", { it.latest?.let { a -> Ratios.opm(a) } }, listOf("operating_margin", "opm_percent")),
        MetricDef("roe", "Return on equity", MetricUnit.PERCENT, "Quality", "net profit / net worth x 100", { it.latest?.let { a -> Ratios.roe(a) } }, listOf("return_on_equity")),
        MetricDef("roce", "Return on capital employed", MetricUnit.PERCENT, "Quality", "(PBT + interest) / capital employed x 100", { it.latest?.let { a -> Ratios.roce(a) } }, listOf("return_on_capital_employed")),
        MetricDef("roa", "Return on assets", MetricUnit.PERCENT, "Quality", "net profit / total assets x 100", { it.latest?.let { a -> Ratios.roa(a) } }, listOf("return_on_assets")),
        MetricDef("cfo_to_op", "Cash conversion", MetricUnit.PERCENT, "Quality", "operating cash flow / operating profit x 100", { it.latest?.let { a -> Ratios.cfoToOperatingProfit(a) } }, listOf("cfo_op", "cash_conversion")),
        MetricDef("tax_rate", "Effective tax rate", MetricUnit.PERCENT, "Quality", "tax / profit before tax x 100", { it.latest?.taxPercent }, listOf("tax")),
        MetricDef("asset_turnover", "Asset turnover", MetricUnit.TIMES, "Quality", "sales / total assets", { it.latest?.let { a -> Ratios.assetTurnover(a) } }),

        // -------------------------------------------------------------- leverage
        MetricDef("debt_to_equity", "Debt to equity", MetricUnit.TIMES, "Leverage", "borrowings / net worth", { it.latest?.let { a -> Ratios.debtToEquity(a) } }, listOf("de", "d/e", "debt_equity")),
        MetricDef("interest_coverage", "Interest coverage", MetricUnit.TIMES, "Leverage", "operating profit / interest", { it.latest?.let { a -> Ratios.interestCoverage(a) } }, listOf("icr")),
        MetricDef("borrowings", "Borrowings", MetricUnit.CRORE, "Leverage", "latest total debt", { it.latest?.borrowings }, listOf("debt")),
        MetricDef("debt_reduction", "Debt reduction (1y)", MetricUnit.PERCENT, "Leverage", "fall in borrowings over one year", { f ->
            val now = f.year(0)?.borrowings
            val prev = f.year(1)?.borrowings
            if (now == null || prev == null || prev <= 0.0) null else (prev - now) / prev * 100.0
        }, listOf("deleveraging")),

        // ------------------------------------------------------------- efficiency
        MetricDef("debtor_days", "Debtor days", MetricUnit.DAYS, "Efficiency", "receivables / sales x 365", { it.latest?.let { a -> Ratios.debtorDays(a) } }),
        MetricDef("inventory_days", "Inventory days", MetricUnit.DAYS, "Efficiency", "inventory / sales x 365", { it.latest?.let { a -> Ratios.inventoryDays(a) } }),
        MetricDef("days_payable", "Days payable", MetricUnit.DAYS, "Efficiency", "payables / sales x 365", { it.latest?.let { a -> Ratios.daysPayable(a) } }),
        MetricDef("ccc", "Cash conversion cycle", MetricUnit.DAYS, "Efficiency", "debtor days + inventory days - days payable", { it.latest?.let { a -> Ratios.cashConversionCycle(a) } }, listOf("working_capital_days", "wc_days")),

        // ------------------------------------------------------------------ cash
        MetricDef("cfo", "Operating cash flow", MetricUnit.CRORE, "Cash", "latest annual CFO", { it.latest?.cfo }, listOf("operating_cash_flow")),
        MetricDef("fcf", "Free cash flow", MetricUnit.CRORE, "Cash", "CFO + CFI", { it.latest?.let { a -> Ratios.freeCashFlow(a) } }, listOf("free_cash_flow")),

        // ------------------------------------------------------------- valuation
        MetricDef("graham_number", "Graham number", MetricUnit.RUPEE, "Valuation", "sqrt(22.5 x EPS x BVPS)", { Ratios.grahamNumber(it) }, listOf("graham")),
        MetricDef("intrinsic_value", "DCF value per share", MetricUnit.RUPEE, "Valuation", "two-stage DCF at 12% growth, 13% discount, 4% terminal", { Ratios.dcfPerShare(it) }, listOf("dcf", "fair_value")),
        MetricDef("margin_of_safety", "Margin of safety", MetricUnit.PERCENT, "Valuation", "(DCF value - price) / DCF value x 100", {
            Ratios.marginOfSafety(Ratios.dcfPerShare(it), it.quote?.price)
        }, listOf("mos")),

        // ---------------------------------------------------------------- scores
        MetricDef("piotroski", "Piotroski F-score", MetricUnit.SCORE, "Scores", "nine fundamental signals, 0-9", { Ratios.piotroski(it)?.toDouble() }, listOf("piotroski_score", "f_score")),
        MetricDef("quality_score", "Quality score", MetricUnit.SCORE, "Scores", "composite of ROE, ROCE, cash conversion and leverage, 0-100", { f ->
            val a = f.latest
            if (a == null) null else {
                val parts = listOfNotNull(
                    Ratios.roe(a)?.coerceIn(0.0, 40.0)?.div(40.0)?.times(100.0),
                    Ratios.roce(a)?.coerceIn(0.0, 50.0)?.div(50.0)?.times(100.0),
                    Ratios.cfoToOperatingProfit(a)?.coerceIn(0.0, 150.0)?.div(150.0)?.times(100.0),
                    Ratios.debtToEquity(a)?.let { v -> (1.0 - v.coerceIn(0.0, 2.0) / 2.0) * 100.0 }
                )
                if (parts.isEmpty()) null else parts.average()
            }
        }, listOf("quality")),

        // ----------------------------------------------------------- shareholding
        MetricDef("promoter_holding", "Promoter holding", MetricUnit.PERCENT, "Shareholding", "latest promoter stake", { it.latestShareholding?.promoters }, listOf("promoter")),
        MetricDef("fii_holding", "FII holding", MetricUnit.PERCENT, "Shareholding", "latest foreign institutional stake", { it.latestShareholding?.fii }, listOf("fii")),
        MetricDef("dii_holding", "DII holding", MetricUnit.PERCENT, "Shareholding", "latest domestic institutional stake", { it.latestShareholding?.dii }, listOf("dii")),
        MetricDef("public_holding", "Public holding", MetricUnit.PERCENT, "Shareholding", "latest public stake", { it.latestShareholding?.publicHolding }),
        MetricDef("promoter_change", "Promoter stake change", MetricUnit.PERCENT, "Shareholding", "latest promoter stake minus four quarters earlier", { f ->
            val sh = f.shareholding
            val now = sh.lastOrNull()?.promoters
            val prev = sh.getOrNull(sh.size - 5)?.promoters
            if (now == null || prev == null) null else now - prev
        })
    )

    val byId: Map<String, MetricDef> = all.associateBy { it.id }

    private val aliasMap: Map<String, String> = buildMap {
        for (m in all) {
            put(m.id, m.id)
            put(m.label.lowercase().replace(" ", "_"), m.id)
            for (a in m.aliases) put(a, m.id)
        }
    }

    fun get(id: String): MetricDef? = byId[id]

    /** Resolves an id, label or alias to a canonical metric. */
    fun resolve(token: String): MetricDef? {
        val key = token.lowercase().trim()
        val id = aliasMap[key] ?: aliasMap[key.replace(" ", "_")] ?: return null
        return byId[id]
    }

    val groups: List<String> = all.map { it.group }.distinct()

    /** Suggestion list for an unknown token, used in error messages. */
    fun suggestions(token: String, limit: Int = 4): List<String> {
        val t = token.lowercase()
        return all.map { it.id }.filter { id -> t.length > 2 && (id.contains(t) || t.contains(id)) }.take(limit)
    }

    /** Columns shown by default in a screen result table. */
    val defaultColumns: List<String> = listOf("sales", "net_profit", "opm", "roe", "pe", "debt_to_equity")
}
