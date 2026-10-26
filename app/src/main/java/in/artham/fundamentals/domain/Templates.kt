package `in`.artham.fundamentals.domain

/**
 * Ready-made screens. Each query is written in this app's own query language and
 * the idea behind it is credited. Thresholds are deliberate, documented choices -
 * they are starting points to adjust, not rules that guarantee an outcome.
 */
data class ScreenTemplate(
    val id: String,
    val name: String,
    val description: String,
    val query: String,
    val source: String
)

object Templates {

    val all: List<ScreenTemplate> = listOf(
        ScreenTemplate(
            "quality_compounders",
            "Quality compounders",
            "High returns on capital, low debt, and cash that backs up the reported profit.",
            "roe > 18 AND roce > 18 AND debt_to_equity < 0.5 AND cfo_to_op > 70",
            "Composite of the quality-investing literature (Fisher, Buffett); thresholds are this app's defaults."
        ),
        ScreenTemplate(
            "graham_value",
            "Graham value",
            "Cheap on earnings and book value, with a margin to the Graham number.",
            "pe < 15 AND pb < 1.5 AND graham_number > price",
            "Benjamin Graham, The Intelligent Investor - defensive investor criteria."
        ),
        ScreenTemplate(
            "piotroski_nines",
            "Piotroski 9s",
            "Every one of the nine Piotroski fundamental signals is positive.",
            "piotroski >= 8",
            "Joseph Piotroski, Value Investing: The Use of Historical Financial Statement Information (2000)."
        ),
        ScreenTemplate(
            "growth_at_price",
            "Growth at a reasonable price",
            "Sales and profit compounding fast without paying an extreme multiple.",
            "sales_growth_5y > 15 AND profit_growth_5y > 15 AND pe < 35",
            "GARP, as practised by Peter Lynch; PEG-style reasoning without a PEG column."
        ),
        ScreenTemplate(
            "debt_reduction",
            "Paying down debt",
            "Companies actively reducing borrowings while still growing.",
            "debt_reduction > 10 AND sales_growth > 5 AND interest_coverage > 3",
            "Deleveraging screen - the cash-flow logic in Howard Marks' credit writing."
        ),
        ScreenTemplate(
            "cash_kings",
            "Cash kings",
            "Profit that converts to cash and survives capital expenditure.",
            "cfo_to_op > 90 AND fcf > 0 AND opm > 15",
            "Cash-conversion quality check; CFO/OP above 90% is the commonly used bar."
        ),
        ScreenTemplate(
            "dividend_payers",
            "Consistent dividend payers",
            "Paying out a meaningful share of earnings with a real yield.",
            "dividend_yield > 2 AND dividend_yield < 8 AND roe > 12",
            "Income-oriented screen; the upper yield bound filters distressed payouts."
        ),
        ScreenTemplate(
            "near_52w_high",
            "Near 52-week high",
            "Within 10% of the 52-week high with positive profit growth.",
            "pct_from_high < 10 AND profit_growth > 10",
            "Momentum plus fundamentals, after the Darvas / O'Neil tradition."
        ),
        ScreenTemplate(
            "margin_of_safety",
            "Trading below DCF value",
            "A two-stage DCF at default assumptions values the stock above its price.",
            "margin_of_safety > 20 AND debt_to_equity < 1",
            "Discounted cash flow (Williams, 1938) with this app's default inputs - see the formula on the metric."
        ),
        ScreenTemplate(
            "turnaround",
            "Loss to profit turnaround",
            "Profitable now, previously loss making, with improving operating margin.",
            "net_profit > 0 AND q_profit_growth > 0 AND opm > 10 AND debt_to_equity < 1.5",
            "Turnaround pattern; verify the prior-year loss on the company page before relying on it."
        ),
        ScreenTemplate(
            "promoter_conviction",
            "Promoter conviction",
            "Promoters holding a large stake and not selling it down.",
            "promoter_holding > 50 AND promoter_change > -0.5 AND roe > 12",
            "Indian-market ownership screen; promoter holding is a locally specific signal."
        ),
        ScreenTemplate(
            "working_capital_tight",
            "Tight working capital",
            "Short cash conversion cycle and strong cash conversion.",
            "ccc < 60 AND cfo_to_op > 70 AND roce > 15",
            "Working-capital efficiency screen."
        )
    )

    fun byId(id: String): ScreenTemplate? = all.firstOrNull { it.id == id }
}
