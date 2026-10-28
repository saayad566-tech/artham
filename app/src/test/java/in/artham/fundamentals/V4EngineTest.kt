package `in`.artham.fundamentals

import `in`.artham.fundamentals.data.AnnualFinancial
import `in`.artham.fundamentals.data.Company
import `in`.artham.fundamentals.data.CompanyRecord
import `in`.artham.fundamentals.data.Quote
import `in`.artham.fundamentals.data.Txn
import `in`.artham.fundamentals.data.TxnKind
import `in`.artham.fundamentals.data.import.Csv
import `in`.artham.fundamentals.data.import.SheetParsing
import `in`.artham.fundamentals.domain.Calculators
import `in`.artham.fundamentals.domain.Financials
import `in`.artham.fundamentals.domain.MetricCatalog
import `in`.artham.fundamentals.domain.PortfolioAnalytics
import `in`.artham.fundamentals.domain.PortfolioEngine
import `in`.artham.fundamentals.domain.PriceBar
import `in`.artham.fundamentals.domain.Technical
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Tests for the audit-wave modules: technicals, calculators, portfolio analytics, new metrics. */
class V4EngineTest {

    private fun bars(closes: List<Double>): List<PriceBar> =
        closes.mapIndexed { i, c ->
            PriceBar(1_600_000_000_000L + i * 86_400_000L, c, c * 1.01, c * 0.99, c, 1000L + i)
        }

    // ------------------------------------------------------------- technicals

    @Test
    fun rsiIsBoundedAndSaturatesOnARelentlessRise() {
        val rising = bars((1..60).map { 100.0 + it })
        val rsi = Technical.rsi(rising)
        val last = rsi.lastOrNull { it != null }!!
        assertTrue("RSI must stay within 0..100, was $last", last in 0.0..100.0)
        assertTrue("an unbroken rise should read as overbought, was $last", last > 95.0)
        assertTrue("values before the period are undefined", rsi.take(13).all { it == null })
    }

    @Test
    fun rsiSaturatesLowOnARelentlessFall() {
        val falling = bars((1..60).map { 200.0 - it })
        val last = Technical.rsi(falling).lastOrNull { it != null }!!
        assertTrue("an unbroken fall should read as oversold, was $last", last < 5.0)
    }

    @Test
    fun macdIsZeroOnAFlatSeries() {
        val flat = bars(List(80) { 100.0 })
        val point = Technical.macd(flat).lastOrNull { it != null }!!
        assertEquals(0.0, point.macd, 1e-6)
        assertEquals(0.0, point.histogram, 1e-6)
    }

    @Test
    fun macdIsPositiveWhenTheShortAverageLeadsTheLongOne() {
        val rising = bars((1..100).map { 100.0 + it * 1.5 })
        val point = Technical.macd(rising).lastOrNull { it != null }!!
        assertTrue("MACD should be positive on a sustained rise, was ${point.macd}", point.macd > 0)
    }

    @Test
    fun bollingerCollapsesToTheMeanOnAFlatSeries() {
        val flat = bars(List(40) { 250.0 })
        val point = Technical.bollinger(flat).lastOrNull { it != null }!!
        assertEquals(250.0, point.middle, 1e-6)
        assertEquals(250.0, point.upper, 1e-6)
        assertEquals(250.0, point.lower, 1e-6)
    }

    @Test
    fun bollingerWidensWithVolatility() {
        val jumpy = bars((1..40).map { if (it % 2 == 0) 100.0 else 140.0 })
        val point = Technical.bollinger(jumpy).lastOrNull { it != null }!!
        assertTrue("the band must widen when prices swing", point.upper - point.lower > 20.0)
    }

    @Test
    fun atrIsPositiveAndUndefinedEarly() {
        val series = bars((1..40).map { 100.0 + it })
        val atr = Technical.atr(series)
        assertTrue(atr.take(13).all { it == null })
        assertTrue(atr.lastOrNull { it != null }!! > 0)
    }

    @Test
    fun momentumMeasuresTheChangeOverTheWindow() {
        val series = bars((1..200).map { if (it <= 74) 100.0 else 110.0 })
        val value = Technical.momentum(series, 126)
        assertNotNull(value)
        assertTrue("expected roughly +10%, was $value", abs(value!! - 10.0) < 2.0)
    }

    @Test
    fun volatilityIsZeroForAConstantSeriesAndPositiveForAMovingOne() {
        assertEquals(0.0, Technical.annualisedVolatility(bars(List(60) { 100.0 }))!!, 1e-9)
        val moving = Technical.annualisedVolatility(bars((1..60).map { 100.0 + (it % 7) * 3 }))!!
        assertTrue("a moving series must have volatility, was $moving", moving > 0)
    }

    @Test
    fun distanceFromSmaHasTheExpectedSign() {
        val above = bars((1..250).map { 100.0 + it })
        assertTrue(Technical.distanceFromSma(above, 200)!! > 0)
        val below = bars((1..250).map { 400.0 - it })
        assertTrue(Technical.distanceFromSma(below, 200)!! < 0)
        assertNull("needs a full window", Technical.distanceFromSma(bars((1..10).map { 100.0 }), 200))
    }

    @Test
    fun volumeSpikeComparesTheLastSessionToTheAverage() {
        val series = (1..30).map { i ->
            PriceBar(1_600_000_000_000L + i * 86_400_000L, 100.0, 101.0, 99.0, 100.0, if (i == 30) 3000L else 1000L)
        }
        val spike = Technical.volumeSpike(series)!!
        assertTrue("expected about 3x, was $spike", abs(spike - 3.0) < 0.2)
    }

    @Test
    fun snapshotIsCompleteWhenEnoughHistoryExists() {
        val series = bars((1..300).map { 100.0 + it * 0.8 })
        val snapshot = Technical.snapshot(series)
        assertNotNull(snapshot.rsi14)
        assertNotNull(snapshot.macd)
        assertNotNull(snapshot.atr14)
        assertNotNull(snapshot.momentum12m)
        assertNotNull(snapshot.annualisedVolatility)
        assertNotNull(snapshot.distanceFromSma200)
    }

    @Test
    fun rsiLabelsReadCorrectly() {
        assertEquals("overbought", Technical.rsiLabel(75.0))
        assertEquals("oversold", Technical.rsiLabel(25.0))
        assertEquals("neutral", Technical.rsiLabel(50.0))
        assertEquals("not enough history", Technical.rsiLabel(null))
    }

    // ------------------------------------------------------------- calculators

    @Test
    fun sipFutureValueMatchesAHandComputedCase() {
        // 1000 a month for 12 months at 12% a year compounded monthly = 12,682.50
        val fv = Calculators.sipFutureValue(1000.0, 1, 12.0)
        assertTrue("expected about 12682.50, was $fv", abs(fv - 12682.50) < 0.5)
    }

    @Test
    fun sipRequiredIsTheInverseOfSipFutureValue() {
        val fv = Calculators.sipFutureValue(5000.0, 10, 11.0)
        val required = Calculators.sipRequired(fv, 10, 11.0)!!
        assertTrue("round trip should return the contribution, was $required", abs(required - 5000.0) < 0.5)
    }

    @Test
    fun lumpsumDoublingTakesAboutSevenYearsAtTenPercent() {
        val fv = Calculators.lumpsumFutureValue(100_000.0, 10, 10.0)
        assertTrue("expected about 259,374, was $fv", abs(fv - 259_374.0) < 5.0)
        val doubling = Calculators.yearsToDouble(10.0)!!
        assertTrue("expected about 7.27 years, was $doubling", abs(doubling - 7.2725) < 0.01)
    }

    @Test
    fun stepUpSipBeatsAFlatSip() {
        val flat = Calculators.sipFutureValue(10_000.0, 10, 12.0)
        val stepped = Calculators.stepUpSip(10_000.0, 10.0, 10, 12.0)
        assertTrue("a step-up plan must end higher, was $stepped vs $flat", stepped > flat)
    }

    @Test
    fun withdrawalDepletesACorpusAndReportsWhen() {
        val result = Calculators.swpDepletion(1_000_000.0, 20_000.0, 0.0)
        assertEquals("1,000,000 at 20,000 a month with no return lasts 50 months", 50, result.monthsLasted)
        assertTrue(result.exhausted)
        val sustainable = Calculators.swpDepletion(10_000_000.0, 20_000.0, 9.0)
        assertTrue("a large corpus at 9% should not run out quickly", !sustainable.exhausted)
    }

    @Test
    fun shortTermAndLongTermTaxUseDifferentRegimes() {
        val longTerm = Calculators.equityCapitalGains(100_000.0, 250_000.0, 18)
        assertTrue(longTerm.longTerm)
        assertEquals(150_000.0, longTerm.gain, 1e-6)
        assertEquals("only the excess over the exemption is taxable", 25_000.0, longTerm.taxableGain, 1e-6)
        assertEquals(3125.0, longTerm.tax, 1e-6)

        val shortTerm = Calculators.equityCapitalGains(100_000.0, 250_000.0, 6)
        assertTrue(!shortTerm.longTerm)
        assertEquals("the whole gain is taxable short term", 150_000.0, shortTerm.taxableGain, 1e-6)
        assertEquals(30_000.0, shortTerm.tax, 1e-6)
    }

    @Test
    fun aCapitalLossAttractsNoTax() {
        val loss = Calculators.equityCapitalGains(250_000.0, 100_000.0, 6)
        assertEquals(0.0, loss.tax, 1e-9)
        assertTrue(loss.gain < 0)
    }

    @Test
    fun positionSizingFixesTheLossBeforeTheQuantity() {
        val units = Calculators.positionSize(500_000.0, 1.0, 1500.0, 1400.0)!!
        assertEquals("risk 5000 over 100 per unit is 50 units", 50.0, units, 1e-6)
        assertNull("a stop above entry is not a stop", Calculators.positionSize(500_000.0, 1.0, 1400.0, 1500.0))
    }

    @Test
    fun breakEvenRisesWithCosts() {
        val price = Calculators.breakEvenPrice(100.0, 1.5)!!
        assertEquals(101.5, price, 1e-6)
    }

    @Test
    fun absoluteReturnConvertsToACompoundRate() {
        val cagr = Calculators.absoluteToCagr(100.0, 5.0)!!
        assertTrue("doubling in five years is about 14.87%, was $cagr", abs(cagr - 14.87) < 0.05)
        assertNull(Calculators.absoluteToCagr(50.0, 0.0))
    }

    @Test
    fun retirementCorpusIsLargerThanTodaysAnnualExpense() {
        val corpus = Calculators.retirementCorpus(80_000.0, 20, 6.0, 8.0)!!
        assertTrue("corpus must exceed a year of today's spending, was $corpus", corpus > 960_000.0)
        assertTrue("and be finite", corpus < 1_000_000_000.0)
        assertNull("a return below inflation has no finite answer", Calculators.retirementCorpus(80_000.0, 20, 8.0, 6.0))
    }

    // --------------------------------------------------- portfolio analytics

    private fun record(id: Long, code: String, price: Double, sector: String = "Test") = CompanyRecord(
        company = Company(id = id, code = code, name = "$code Ltd", sector = sector),
        annual = listOf(
            AnnualFinancial(
                companyId = id, fyEnd = 2025, sales = 1000.0, operatingProfit = 200.0,
                opmPercent = 20.0, netProfit = 100.0, eps = 10.0, equityCapital = 100.0,
                reserves = 400.0, borrowings = 0.0, totalAssets = 800.0, cfo = 120.0, cfi = -20.0,
                profitBeforeTax = 133.33, interest = 0.0, taxPercent = 25.0,
                receivables = 100.0, inventory = 80.0, payables = 60.0
            )
        ),
        quarterly = emptyList(),
        shareholding = emptyList(),
        quote = Quote(companyId = id, price = price, marketCap = price * 100, source = "test")
    )

    private fun summaryOf(vararg positions: Pair<Long, Double>): `in`.artham.fundamentals.domain.PortfolioSummary {
        val start = 1_600_000_000_000L
        val records = positions.associate { (id, _) -> id to record(id, "C$id", 100.0) }
        val txns = positions.mapIndexed { index, (id, units) ->
            Txn(id = index + 1L, portfolioId = 1, companyId = id, kind = TxnKind.BUY,
                tradedOn = start, units = units, price = 50.0, amount = units * 50.0)
        }
        return PortfolioEngine.compute(txns, records, today = start + 30L * 86_400_000L)
    }

    @Test
    fun twoEqualHoldingsGiveAHerfindahlOfOneHalf() {
        val summary = summaryOf(1L to 10.0, 2L to 10.0)
        val concentration = PortfolioAnalytics.concentration(summary)!!
        assertEquals(0.5, concentration.herfindahl, 1e-6)
        assertEquals("two equal positions means two effective holdings", 2.0, concentration.effectiveHoldings, 1e-6)
        assertEquals(50.0, concentration.topWeightPercent, 1e-6)
    }

    @Test
    fun oneDominantHoldingReadsAsConcentrated() {
        val summary = summaryOf(1L to 90.0, 2L to 3.0, 3L to 3.0, 4L to 2.0, 5L to 2.0)
        val concentration = PortfolioAnalytics.concentration(summary)!!
        assertTrue("90% in one name must read as concentrated", concentration.effectiveHoldings < 1.3)
        assertTrue("the reading should say so: ${concentration.reading}", concentration.reading.contains("concentrated", true))
    }

    @Test
    fun contributionSharesSumToAHundredPercent() {
        val summary = summaryOf(1L to 10.0, 2L to 10.0)
        val contributions = PortfolioAnalytics.contributions(summary)
        assertEquals(2, contributions.size)
        assertEquals(100.0, contributions.sumOf { it.sharePercent }, 1e-6)
    }

    // --------------------------------------------------------------- metrics

    @Test
    fun everyNewMetricIsRegisteredAndResolvable() {
        val expected = listOf(
            "price_to_sales", "ev_ebitda", "peg", "cash_to_market_cap", "net_margin", "roic",
            "operating_leverage", "altman_z", "revenue_per_share", "net_worth", "working_capital",
            "inventory_turnover", "receivable_turnover", "capex_to_sales", "debt_to_assets",
            "debt_to_ebitda", "book_value_growth_3y", "dividend_growth_3y", "asset_growth",
            "rsi14", "macd_histogram", "momentum_6m", "momentum_12m", "distance_sma200",
            "volatility", "volume_spike", "bollinger_position"
        )
        for (id in expected) {
            assertNotNull("metric $id is missing from the catalog", MetricCatalog.get(id))
            assertNotNull("metric $id does not resolve from its own id", MetricCatalog.resolve(id))
        }
        assertTrue("the catalog should now be large", MetricCatalog.all.size >= 70)
    }

    @Test
    fun financialMetricsComputeOnARealRecord() {
        val f = Financials(record(1, "AAA", 100.0))
        for (id in listOf("net_margin", "roic", "net_worth", "revenue_per_share", "debt_to_assets", "price_to_sales")) {
            val def = MetricCatalog.get(id)!!
            val value = def.compute(f)
            assertNotNull("$id should compute on a complete record", value)
        }
    }

    @Test
    fun technicalMetricsAreNullWithoutBarsAndPresentWithThem() {
        val withoutBars = Financials(record(1, "AAA", 100.0))
        assertNull("RSI needs prices", MetricCatalog.get("rsi14")!!.compute(withoutBars))
        assertNull(MetricCatalog.get("momentum_6m")!!.compute(withoutBars))

        val withBars = Financials(record(1, "AAA", 100.0), bars((1..300).map { 100.0 + it * 0.5 }))
        assertNotNull("RSI should compute once bars exist", MetricCatalog.get("rsi14")!!.compute(withBars))
        assertNotNull(MetricCatalog.get("distance_sma200")!!.compute(withBars))
    }

    // ------------------------------------------------------------ price import

    @Test
    fun datesParseInTheFormatsSpreadsheetsActuallyUse() {
        assertNotNull(SheetParsing.parseAnyDate("2025-03-31"))
        assertNotNull(SheetParsing.parseAnyDate("31/03/2025"))
        assertNotNull(SheetParsing.parseAnyDate("31-03-2025"))
        assertNotNull(SheetParsing.parseAnyDate("31-Mar-2025"))
        assertNull(SheetParsing.parseAnyDate("not a date"))
        assertNull(SheetParsing.parseAnyDate(""))
    }

    @Test
    fun priceCsvImportsEveryValidRow() {
        val file = File.createTempFile("prices", ".csv")
        file.writeText(
            "Date,Open,High,Low,Close,Volume\n" +
                "2025-01-01,100,102,99,101,10000\n" +
                "2025-01-02,101,103,100,102,11000\n" +
                "2025-01-03,bad,103,100,103,12000\n" +
                "2025-01-04,103,105,102,104,13000\n"
        )
        var count = 0
        val closes = ArrayList<Double>()
        val outcome = SheetParsing.parsePriceRows(Csv.read(file)) { _, _, _, _, close, _ ->
            count++
            closes.add(close)
        }
        // every row has a readable date and a positive close; a bad open falls back to the close
        assertEquals(4, count)
        assertEquals(4, outcome.bars)
        assertTrue(outcome.warnings.any { it.contains("at least 20") })
        assertEquals(listOf(101.0, 102.0, 103.0, 104.0), closes)
        file.delete()
    }

    @Test
    fun priceCsvWithoutADateOrCloseColumnIsRejected() {
        val file = File.createTempFile("prices", ".csv")
        file.writeText("foo,bar\n1,2\n")
        val outcome = SheetParsing.parsePriceRows(Csv.read(file)) { _, _, _, _, _, _ -> }
        assertEquals(0, outcome.bars)
        assertTrue(outcome.warnings.isNotEmpty())
        file.delete()
    }
}
