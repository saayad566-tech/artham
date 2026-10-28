package `in`.artham.fundamentals

import `in`.artham.fundamentals.data.AnnualFinancial
import `in`.artham.fundamentals.data.Company
import `in`.artham.fundamentals.data.CompanyRecord
import `in`.artham.fundamentals.data.DocChunk
import `in`.artham.fundamentals.data.Export
import `in`.artham.fundamentals.data.Quote
import `in`.artham.fundamentals.data.ScreenHit
import `in`.artham.fundamentals.data.ScreenResult
import `in`.artham.fundamentals.data.Txn
import `in`.artham.fundamentals.data.TxnKind
import `in`.artham.fundamentals.domain.Backtester
import `in`.artham.fundamentals.domain.DcfInputs
import `in`.artham.fundamentals.domain.DocumentIndex
import `in`.artham.fundamentals.domain.Financials
import `in`.artham.fundamentals.domain.PortfolioEngine
import `in`.artham.fundamentals.domain.Valuation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Tests for the second wave of engines: portfolio cost basis and XIRR, valuation,
 * point-in-time backtesting, document retrieval and CSV export.
 */
class V2EngineTest {

    private val day = 24L * 60 * 60 * 1000

    private fun annual(
        companyId: Long,
        fyEnd: Int,
        sales: Double,
        netProfit: Double,
        equity: Double = 100.0,
        reserves: Double = 400.0,
        borrowings: Double = 0.0,
        cfo: Double = 150.0,
        cfi: Double = -50.0
    ) = AnnualFinancial(
        companyId = companyId, fyEnd = fyEnd, sales = sales, expenses = sales * 0.8,
        operatingProfit = sales * 0.2, opmPercent = 20.0, otherIncome = 0.0, interest = 0.0,
        depreciation = sales * 0.02, profitBeforeTax = netProfit / 0.75, taxPercent = 25.0,
        netProfit = netProfit, eps = netProfit / 100.0, dividend = 0.0,
        equityCapital = equity, reserves = reserves, borrowings = borrowings,
        totalAssets = sales * 0.8, totalLiabilities = sales * 0.8,
        receivables = sales * 0.1, inventory = sales * 0.1, payables = sales * 0.05,
        cfo = cfo, cfi = cfi, cff = 0.0, netCashFlow = cfo + cfi
    )

    private fun record(
        id: Long,
        code: String,
        rows: List<AnnualFinancial>,
        price: Double = 10.0,
        shares: Double = 100.0,
        sector: String = "Test"
    ) = CompanyRecord(
        company = Company(id = id, code = code, name = "$code Ltd", sector = sector),
        annual = rows,
        quarterly = emptyList(),
        shareholding = emptyList(),
        quote = Quote(companyId = id, price = price, marketCap = price * shares, source = "test")
    )

    // ------------------------------------------------------------------ XIRR

    @Test
    fun xirrSolvesAKnownTenPercentCase() {
        val start = 1_600_000_000_000L
        val flows = listOf(start to -1000.0, (start + 365 * day) to 1100.0)
        val rate = PortfolioEngine.xirr(flows)
        assertNotNull(rate)
        assertTrue("expected about 10%, was $rate", abs(rate!! - 10.0) < 0.05)
    }

    @Test
    fun xirrReturnsNullWithoutASignChange() {
        val start = 1_600_000_000_000L
        assertNull(PortfolioEngine.xirr(listOf(start to 100.0, (start + day) to 200.0)))
        assertNull(PortfolioEngine.xirr(listOf(start to -100.0, (start + day) to -50.0)))
        assertNull("one flow is not enough", PortfolioEngine.xirr(listOf(start to -100.0)))
    }

    @Test
    fun xirrHandlesALoss() {
        val start = 1_600_000_000_000L
        val flows = listOf(start to -1000.0, (start + 365 * day) to 500.0)
        val rate = PortfolioEngine.xirr(flows)
        assertNotNull(rate)
        assertTrue("expected about -50%, was $rate", abs(rate!! + 50.0) < 0.2)
    }

    // ------------------------------------------------------------- portfolio

    @Test
    fun fifoCostBasisIsUsedForRealisedProfit() {
        val start = 1_600_000_000_000L
        val record = record(1, "AAA", listOf(annual(1, 2025, 1000.0, 100.0)), price = 300.0)
        val txns = listOf(
            Txn(id = 1, portfolioId = 1, companyId = 1, kind = TxnKind.BUY, tradedOn = start, units = 10.0, price = 100.0, amount = 1000.0),
            Txn(id = 2, portfolioId = 1, companyId = 1, kind = TxnKind.BUY, tradedOn = start + 10 * day, units = 10.0, price = 200.0, amount = 2000.0),
            Txn(id = 3, portfolioId = 1, companyId = 1, kind = TxnKind.SELL, tradedOn = start + 20 * day, units = 10.0, price = 300.0, amount = 3000.0)
        )
        val summary = PortfolioEngine.compute(txns, mapOf(1L to record), today = start + 30 * day)
        // the first lot (cost 100) is sold, so realised = 10 * (300 - 100)
        assertEquals(2000.0, summary.realised, 1e-6)
        assertEquals(1, summary.holdings.size)
        val holding = summary.holdings.first()
        assertEquals(10.0, holding.units, 1e-6)
        assertEquals("remaining lot is the 200 one", 200.0, holding.averageCost, 1e-6)
        assertEquals(3000.0, holding.marketValue!!, 1e-6)
    }

    @Test
    fun dividendsAreSeparateFromRealisedGains() {
        val start = 1_600_000_000_000L
        val record = record(1, "AAA", listOf(annual(1, 2025, 1000.0, 100.0)), price = 100.0)
        val txns = listOf(
            Txn(id = 1, portfolioId = 1, companyId = 1, kind = TxnKind.BUY, tradedOn = start, units = 10.0, price = 100.0, amount = 1000.0),
            Txn(id = 2, portfolioId = 1, companyId = 1, kind = TxnKind.DIVIDEND, tradedOn = start + 5 * day, units = 10.0, price = 20.0, amount = 200.0)
        )
        val summary = PortfolioEngine.compute(txns, mapOf(1L to record), today = start + 10 * day)
        assertEquals(200.0, summary.dividends, 1e-6)
        assertEquals(0.0, summary.realised, 1e-6)
        assertEquals(200.0, summary.totalGain, 1e-6)
    }

    @Test
    fun bonusUnitsReduceAverageCostWithoutCash() {
        val start = 1_600_000_000_000L
        val record = record(1, "AAA", listOf(annual(1, 2025, 1000.0, 100.0)), price = 100.0)
        val txns = listOf(
            Txn(id = 1, portfolioId = 1, companyId = 1, kind = TxnKind.BUY, tradedOn = start, units = 10.0, price = 100.0, amount = 1000.0),
            Txn(id = 2, portfolioId = 1, companyId = 1, kind = TxnKind.BONUS, tradedOn = start + day, units = 10.0, price = 0.0, amount = 0.0)
        )
        val summary = PortfolioEngine.compute(txns, mapOf(1L to record), today = start + 2 * day)
        val holding = summary.holdings.first()
        assertEquals(20.0, holding.units, 1e-6)
        assertEquals(50.0, holding.averageCost, 1e-6)
        assertEquals(1000.0, holding.invested, 1e-6)
    }

    @Test
    fun allocationBySectorSumsToHundredPercent() {
        val start = 1_600_000_000_000L
        val a = record(1, "AAA", listOf(annual(1, 2025, 1000.0, 100.0)), price = 100.0, sector = "Tech")
        val b = record(2, "BBB", listOf(annual(2, 2025, 1000.0, 100.0)), price = 100.0, sector = "Banks")
        val txns = listOf(
            Txn(id = 1, portfolioId = 1, companyId = 1, kind = TxnKind.BUY, tradedOn = start, units = 10.0, price = 100.0, amount = 1000.0),
            Txn(id = 2, portfolioId = 1, companyId = 2, kind = TxnKind.BUY, tradedOn = start, units = 30.0, price = 100.0, amount = 3000.0)
        )
        val summary = PortfolioEngine.compute(txns, mapOf(1L to a, 2L to b), today = start + day)
        assertEquals(2, summary.allocation.size)
        assertEquals(100.0, summary.allocation.sumOf { it.weightPercent }, 1e-6)
        assertEquals("Banks", summary.allocation.first().sector)
    }

    // ------------------------------------------------------------- valuation

    private fun flatRecord(): CompanyRecord {
        val rows = (2020..2025).map { annual(1, it, 1000.0, 100.0) }
        return record(1, "FLAT", rows, price = 10.0, shares = 100.0)
    }

    @Test
    fun dcfMatchesAHandComputedCase() {
        // fcf = cfo 150 + cfi -50 = 100; growth 0; discount 10%; terminal growth 0
        // pvExplicit = 100/1.1 = 90.909..., terminal = 100/0.10 = 1000, pvTerminal = 909.09...
        // equity value = 1000, net debt = 0, shares = 100 -> 10.0 per share
        val f = Financials(flatRecord())
        val result = Valuation.dcf(f, DcfInputs(growthRate = 0.0, years = 1, discountRate = 10.0, terminalGrowth = 0.0))
        assertNotNull(result)
        assertEquals(10.0, result!!.valuePerShare, 0.01)
        assertEquals(100.0, result.baseFreeCashFlow, 1e-6)
        assertEquals(0.0, result.netDebt, 1e-6)
    }

    @Test
    fun dcfRejectsANonPositiveDiscountSpread() {
        val f = Financials(flatRecord())
        assertNull(Valuation.dcf(f, DcfInputs(discountRate = 4.0, terminalGrowth = 4.0)))
        assertNull(Valuation.dcf(f, DcfInputs(discountRate = 3.0, terminalGrowth = 5.0)))
    }

    @Test
    fun reverseDcfRecoversTheGrowthThatReproducesThePrice() {
        val f = Financials(flatRecord())
        val base = DcfInputs(growthRate = 0.0, years = 1, discountRate = 10.0, terminalGrowth = 0.0)
        // the price of 10.0 is exactly what zero growth produces, so the implied growth is ~0
        val implied = Valuation.reverseDcf(f, base)
        assertNotNull(implied)
        assertTrue("expected about 0%, was $implied", abs(implied!!) < 0.5)
    }

    @Test
    fun sensitivityGridHasTheRequestedShapeAndPositiveValues() {
        val f = Financials(flatRecord())
        val (growthAxis, rows) = Valuation.sensitivity(
            f, DcfInputs(0.0, 1, 10.0, 0.0),
            discountSteps = listOf(-1.0, 0.0, 1.0),
            growthSteps = listOf(-2.0, 0.0, 2.0)
        )
        assertEquals(3, growthAxis.size)
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.values.size == 3 })
        // a higher discount rate must produce a lower value
        val atLowerDiscount = rows[0].values[1]!!
        val atHigherDiscount = rows[2].values[1]!!
        assertTrue("higher discount must lower the value", atHigherDiscount < atLowerDiscount)
    }

    // ------------------------------------------------------------- backtesting

    @Test
    fun backtestIsPointInTimeAndIgnoresFutureYears() {
        // AAA earns a high ROE only from 2023 onwards
        val aaaRows = listOf(
            annual(1, 2021, 1000.0, 20.0, reserves = 400.0),
            annual(1, 2022, 1000.0, 20.0, reserves = 400.0),
            annual(1, 2023, 1000.0, 300.0, reserves = 400.0),
            annual(1, 2024, 1000.0, 300.0, reserves = 400.0)
        )
        val bbbRows = listOf(annual(2, 2021, 1000.0, 20.0, reserves = 400.0))
        val records = listOf(
            record(1, "AAA", aaaRows),
            record(2, "BBB", bbbRows)
        )
        val result = Backtester.run("roe > 30", records, fromYear = 2021, toYear = 2024)
        assertNull(result.error)
        val byYear = result.years.associateBy { it.fyEnd }
        assertTrue("2021 must not see the future", byYear[2021]!!.matched.isEmpty())
        assertTrue("2022 must not see the future", byYear[2022]!!.matched.isEmpty())
        assertEquals(1, byYear[2023]!!.matched.size)
        assertEquals("AAA", byYear[2023]!!.matched.first().code)
        assertEquals(1, byYear[2024]!!.matched.size)
    }

    @Test
    fun backtestReportsAPriceBasedScreenHonestly() {
        val records = listOf(record(1, "AAA", (2021..2024).map { annual(1, it, 1000.0, 300.0) }))
        val result = Backtester.run("pe < 5", records, fromYear = 2021, toYear = 2024)
        assertNull(result.error)
        assertTrue("quotes are removed for historical years", result.years.all { it.matched.isEmpty() })
        assertTrue(
            "the warning must explain why",
            result.warnings.any { it.contains("price-based") }
        )
        assertTrue(
            "the absence of price history must be stated",
            result.warnings.any { it.contains("No price history") }
        )
    }

    @Test
    fun backtestRejectsAnInvalidQuery() {
        val result = Backtester.run("nonsense > 1", listOf(record(1, "AAA", listOf(annual(1, 2024, 1000.0, 100.0)))))
        assertNotNull(result.error)
        assertTrue(result.years.isEmpty())
    }

    // --------------------------------------------------------------- documents

    @Test
    fun chunkingSplitsLongTextAndKeepsShortTextWhole() {
        val short = "A short note about margins."
        assertEquals(1, DocumentIndex.chunk(short).size)

        val paragraph = "The company reported revenue growth driven by volume expansion in the export segment. "
        val long = (1..60).joinToString("\n\n") { paragraph.repeat(2) }
        val chunks = DocumentIndex.chunk(long, targetChars = 600, overlapChars = 100)
        assertTrue("expected several chunks, got ${chunks.size}", chunks.size > 3)
        assertTrue("no chunk should be far over the target", chunks.all { it.length <= 900 })
    }

    @Test
    fun retrievalRanksTheChunkThatContainsTheQueryTerms() {
        val chunks = listOf(
            DocChunk(id = 1, docId = 1, ord = 0, body = "Revenue from the chemicals division grew strongly."),
            DocChunk(id = 2, docId = 1, ord = 1, body = "The company discussed capex on a new plant in Gujarat."),
            DocChunk(id = 3, docId = 1, ord = 2, body = "Dividend policy remains unchanged at thirty percent.")
        ).map { it to "" }
        val titles = mapOf(1L to "Annual Report")
        val hits = DocumentIndex.search(chunks, titles, "capex plant Gujarat", topK = 2)
        // only the capex passage contains any of those terms, so exactly one chunk can score
        assertEquals(1, hits.size)
        assertEquals(1, hits.first().ord)
        assertTrue(hits.first().score > 0.0)
    }

    @Test
    fun retrievalReturnsNothingForAnUnrelatedQuestion() {
        val chunks = listOf(
            DocChunk(id = 1, docId = 1, ord = 0, body = "Revenue grew strongly in the chemicals division.")
        ).map { it to "" }
        val hits = DocumentIndex.search(chunks, mapOf(1L to "AR"), "zebra helicopter", topK = 3)
        assertTrue(hits.isEmpty())
    }

    // ------------------------------------------------------------------ export

    @Test
    fun screenExportProducesAHeaderAndOneRowPerMatch() {
        val result = ScreenResult(
            query = "roe > 15",
            hits = listOf(
                ScreenHit(Company(id = 1, code = "AAA", name = "Alpha, Ltd"), mapOf("roe" to 21.5, "pe" to null)),
                ScreenHit(Company(id = 2, code = "BBB", name = "Beta Ltd"), mapOf("roe" to 18.0, "pe" to 12.0))
            ),
            evaluated = 2, skipped = 0, durationMs = 1
        )
        val csv = Export.screenToCsv(result)
        val lines = csv.trim().split("\n")
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("Code,Company"))
        assertTrue("a comma in the name must be quoted", lines[1].contains("\"Alpha, Ltd\""))
        assertTrue("a null metric must export as empty, not zero", lines[1].endsWith(","))
    }

    @Test
    fun annualExportCoversEveryStoredPeriod() {
        val record = record(1, "AAA", (2021..2025).map { annual(1, it, 1000.0, 100.0) })
        val csv = Export.annualToCsv(record)
        val lines = csv.trim().split("\n")
        // 3 header lines + blank + column header + 5 years
        assertTrue("expected at least 9 lines, got ${lines.size}", lines.size >= 9)
        assertTrue(lines.any { it.startsWith("2021,") })
        assertTrue(lines.any { it.startsWith("2025,") })
    }
}
