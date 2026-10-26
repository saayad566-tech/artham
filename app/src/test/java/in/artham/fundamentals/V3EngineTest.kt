package `in`.artham.fundamentals

import `in`.artham.fundamentals.data.AnnualFinancial
import `in`.artham.fundamentals.data.net.NseUniverse
import `in`.artham.fundamentals.data.net.YahooProvider
import `in`.artham.fundamentals.domain.PriceBar
import `in`.artham.fundamentals.domain.PriceSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Tests for the live-data layer. The two Yahoo fixtures in `src/test/resources` are real
 * responses captured from the public endpoints, not hand-written JSON, so the parser is
 * tested against the payload it will actually meet.
 */
class V3EngineTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/$name")?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("missing fixture $name")

    // ------------------------------------------------------------------ chart

    @Test
    fun realChartPayloadParsesIntoAscendingBars() {
        val snapshot = YahooProvider.parseChart(fixture("yahoo_chart_tcs.json"))
        assertNotNull(snapshot)
        val s = snapshot!!
        assertEquals("TCS.NS", s.symbol)
        assertEquals("INR", s.currency)
        assertTrue("expected a real price", (s.price ?: 0.0) > 100.0)
        assertTrue("52 week high should exceed the low", (s.high52 ?: 0.0) > (s.low52 ?: 0.0))
        assertTrue("bars should parse", s.bars.size > 30)
        assertTrue("every close must be positive", s.bars.all { it.close > 0.0 })
        assertTrue("every bar must have a positive high", s.bars.all { it.high >= it.low })
        for (i in 1 until s.bars.size) {
            assertTrue("timestamps must ascend", s.bars[i].time > s.bars[i - 1].time)
        }
    }

    @Test
    fun malformedChartJsonReturnsNullRatherThanThrowing() {
        assertNull(YahooProvider.parseChart("{ not json at all"))
        assertNull(YahooProvider.parseChart("""{"chart":{"result":[],"error":null}}"""))
    }

    // ------------------------------------------------------------ fundamentals

    @Test
    fun realFundamentalsPayloadMapsToAnnualPeriods() {
        val parsed = YahooProvider.parseFundamentals(fixture("yahoo_fundamentals_tcs.json"), faceValue = 1.0)
        assertTrue("expected annual periods, got ${parsed.annual.size}", parsed.annual.size >= 3)
        assertTrue("expected quarterly periods", parsed.quarterly.isNotEmpty())
        assertTrue(parsed.annual.all { it.sales != null && it.sales!! > 0 })
        assertTrue(parsed.annual.all { it.netProfit != null })
    }

    @Test
    fun rupeeFiguresAreConvertedToCrore() {
        val parsed = YahooProvider.parseFundamentals(fixture("yahoo_fundamentals_tcs.json"), faceValue = 1.0)
        val fy2023 = parsed.annual.firstOrNull { it.fyEnd == 2023 }
        assertNotNull("the fixture contains FY2023", fy2023)
        val sales = fy2023!!.sales!!
        // raw revenue is about 2.25 lakh crore rupees; in crore that is about 225,458
        assertTrue("sales should be in crore, was $sales", sales in 150_000.0..300_000.0)
        assertTrue("net profit should be in crore", fy2023.netProfit!! in 10_000.0..80_000.0)
    }

    @Test
    fun derivedFieldsAreConsistentWithTheRawSeries() {
        val parsed = YahooProvider.parseFundamentals(fixture("yahoo_fundamentals_tcs.json"), faceValue = 1.0)
        val row = parsed.annual.first { it.fyEnd == 2023 }
        // operating margin must be a plausible percentage, not a rupee figure
        val opm = row.opmPercent!!
        assertTrue("operating margin looks wrong: $opm", opm in 5.0..60.0)
        // tax rate must be a percentage
        val tax = row.taxPercent!!
        assertTrue("tax rate looks wrong: $tax", tax in 5.0..45.0)
        // depreciation is derived as EBITDA minus operating income and must not be negative
        val dep = row.depreciation
        assertNotNull("depreciation should be derivable from EBITDA and EBIT", dep)
        assertTrue("derived depreciation must be positive", dep!! >= 0.0)
        // share capital is shares x face value; with a face value of 1 it is ~366 crore
        val capital = row.equityCapital!!
        assertTrue("share capital looks wrong: $capital", capital in 100.0..1000.0)
        assertTrue("reserves should dominate net worth", (row.reserves ?: 0.0) > capital)
    }

    @Test
    fun quarterlyLabelsMapToIndianFiscalQuarters() {
        val parsed = YahooProvider.parseFundamentals(fixture("yahoo_fundamentals_tcs.json"), faceValue = 1.0)
        assertTrue(parsed.quarterly.isNotEmpty())
        assertTrue(
            "every quarter must have a label and a valid index",
            parsed.quarterly.all { it.label.isNotBlank() && it.quarter in 1..4 }
        )
        // a June quarter belongs to the fiscal year that ends the following March
        parsed.quarterly.filter { it.quarter == 1 }.forEach { q ->
            assertEquals("a Jun quarter's fyEnd should be the following year", q.fyEnd, q.label.substringAfter(" ").toInt() + 1)
        }
    }

    @Test
    fun parsedFundamentalsProduceSensibleRatiosEndToEnd() {
        // the whole chain: real JSON -> AnnualFinancial -> Financials -> Ratios
        val parsed = YahooProvider.parseFundamentals(fixture("yahoo_fundamentals_tcs.json"), faceValue = 1.0)
        val company = `in`.artham.fundamentals.data.Company(id = 1, code = "TCS", name = "TCS", faceValue = 1.0)
        val quote = `in`.artham.fundamentals.data.Quote(companyId = 1, price = 2535.0, source = "test")
        val record = `in`.artham.fundamentals.data.CompanyRecord(
            company = company,
            annual = parsed.annual.map { it.copy(companyId = 1) },
            quarterly = parsed.quarterly.map { it.copy(companyId = 1) },
            shareholding = emptyList(),
            quote = quote
        )
        val f = `in`.artham.fundamentals.domain.Financials(record)
        val latest = f.latest!!
        val roe = `in`.artham.fundamentals.domain.Ratios.roe(latest)
        val roce = `in`.artham.fundamentals.domain.Ratios.roce(latest)
        val opm = `in`.artham.fundamentals.domain.Ratios.opm(latest)
        val pe = `in`.artham.fundamentals.domain.Ratios.pe(f)
        assertNotNull(roe); assertNotNull(roce); assertNotNull(opm); assertNotNull(pe)
        assertTrue("ROE should be a high-quality-services figure, was $roe", roe!! in 20.0..80.0)
        assertTrue("ROCE should be high for an asset-light business, was $roce", roce!! in 25.0..90.0)
        assertTrue("operating margin should be plausible, was $opm", opm!! in 15.0..35.0)
        assertTrue("P/E should be plausible, was $pe", pe!! in 5.0..60.0)
        // share count derived from stored equity capital and face value must be realistic
        val shares = f.sharesOutstanding()!!
        assertTrue("derived share count looks wrong: $shares", shares > 1e9)
    }

    // ---------------------------------------------------------------- universe

    @Test
    fun exchangeMasterFileParsesIncludingQuotedNames() {
        val entries = NseUniverse.parse(fixture("nse_equity_master_sample.csv"))
        assertEquals(5, entries.size)
        val bajaj = entries.first { it.symbol == "BAJAJ-AUTO" }
        assertEquals("Bajaj Auto Limited", bajaj.name)
        assertEquals(10.0, bajaj.faceValue, 1e-9)
        assertEquals("INE917I01010", bajaj.isin)
        val tcs = entries.first { it.symbol == "TCS" }
        assertEquals(1.0, tcs.faceValue, 1e-9)
    }

    @Test
    fun nonEquitySeriesAreFilteredOut() {
        val entries = NseUniverse.parse(fixture("nse_equity_master_sample.csv"))
        val equities = NseUniverse.equitiesOnly(entries)
        assertEquals(4, equities.size)
        assertTrue(equities.none { it.series == "N1" })
    }

    @Test
    fun emptyOrHeaderOnlyCsvYieldsNothing() {
        assertTrue(NseUniverse.parse("").isEmpty())
        assertTrue(NseUniverse.parse("SYMBOL,NAME OF COMPANY").isEmpty())
    }

    // ------------------------------------------------------------- price maths

    private fun bars(closes: List<Double>): List<PriceBar> =
        closes.mapIndexed { i, c ->
            PriceBar(
                time = 1_600_000_000_000L + i * 86_400_000L,
                open = c, high = c * 1.01, low = c * 0.99, close = c, volume = 1000L + i
            )
        }

    @Test
    fun simpleMovingAverageIsCorrect() {
        val series = bars(listOf(10.0, 20.0, 30.0, 40.0, 50.0))
        val sma3 = PriceSeries.sma(series, 3)
        assertNull("the first two points are undefined", sma3[0])
        assertNull(sma3[1])
        assertEquals(20.0, sma3[2]!!, 1e-9)
        assertEquals(30.0, sma3[3]!!, 1e-9)
        assertEquals(40.0, sma3[4]!!, 1e-9)
    }

    @Test
    fun movingAverageLongerThanTheSeriesIsAllNull() {
        val sma = PriceSeries.sma(bars(listOf(1.0, 2.0)), 50)
        assertTrue(sma.all { it == null })
    }

    @Test
    fun maxDrawdownFindsTheWorstPeakToTroughFall() {
        val series = bars(listOf(100.0, 120.0, 60.0, 80.0, 40.0, 90.0))
        val dd = PriceSeries.maxDrawdown(series)
        assertNotNull(dd)
        // peak 120 falls to 40, which is a 66.67% fall
        assertTrue("expected about 66.7%, was $dd", abs(dd!! - 66.666) < 0.01)
    }

    @Test
    fun statsSummariseTheSeries() {
        val series = bars(listOf(100.0, 110.0, 90.0, 120.0))
        val stats = PriceSeries.stats(series)!!
        assertEquals(100.0, stats.first, 1e-9)
        assertEquals(120.0, stats.last, 1e-9)
        assertEquals(20.0, stats.changePercent!!, 1e-9)
        assertEquals(4, stats.barCount)
        assertTrue(stats.periodHigh > stats.periodLow)
    }

    @Test
    fun peBandIgnoresEarningsThatWereNotYetPublished() {
        val fyEnd = 2023
        val fiscalEnd = PriceSeries.fiscalYearEndMillis(fyEnd)
        val beforePublication = fiscalEnd + 30L * 86_400_000L      // one month later: not yet filed
        val afterPublication = fiscalEnd + 200L * 86_400_000L      // well after the lag
        val series = listOf(
            PriceBar(beforePublication, 100.0, 100.0, 100.0, 100.0, 1),
            PriceBar(afterPublication, 200.0, 200.0, 200.0, 200.0, 1)
        )
        val annual = listOf(AnnualFinancial(companyId = 1, fyEnd = fyEnd, eps = 10.0))
        val band = PriceSeries.peBand(series, annual)
        assertEquals("only the post-publication bar should produce a multiple", 1, band.size)
        assertEquals(20.0, band.first().pe, 1e-9)
    }

    @Test
    fun peBandSkipsLossMakingPeriods() {
        val series = bars(listOf(100.0, 200.0))
        val annual = listOf(AnnualFinancial(companyId = 1, fyEnd = 2020, eps = -5.0))
        assertTrue("a negative EPS has no meaningful multiple", PriceSeries.peBand(series, annual).isEmpty())
    }

    @Test
    fun downsamplingKeepsTheLastBarAndBoundsThePointCount() {
        val series = bars((1..1000).map { it.toDouble() })
        val reduced = PriceSeries.downsample(series, 100)
        assertTrue("should be bounded, was ${reduced.size}", reduced.size <= 102)
        assertEquals(series.last().time, reduced.last().time)
    }

    @Test
    fun sinceTrimsToACalendarWindow() {
        val series = bars((1..100).map { it.toDouble() })
        val trimmed = PriceSeries.since(series, 30)
        assertTrue(trimmed.size in 29..32)
        assertEquals(series.last().time, trimmed.last().time)
    }
}
