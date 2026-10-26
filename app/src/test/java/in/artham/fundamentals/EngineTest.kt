package `in`.artham.fundamentals

import `in`.artham.fundamentals.core.Fmt
import `in`.artham.fundamentals.core.Num
import `in`.artham.fundamentals.data.AnnualFinancial
import `in`.artham.fundamentals.data.Company
import `in`.artham.fundamentals.data.CompanyRecord
import `in`.artham.fundamentals.data.Quote
import `in`.artham.fundamentals.data.import.SheetParsing
import `in`.artham.fundamentals.domain.Financials
import `in`.artham.fundamentals.domain.MetricCatalog
import `in`.artham.fundamentals.domain.ProsConsEngine
import `in`.artham.fundamentals.domain.QueryCompiler
import `in`.artham.fundamentals.domain.QueryEvaluator
import `in`.artham.fundamentals.domain.QueryException
import `in`.artham.fundamentals.domain.Ratios
import `in`.artham.fundamentals.domain.ScreenEngine
import `in`.artham.fundamentals.domain.Templates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * Unit tests for the logic that can be wrong silently: number formatting, the
 * query language, the ratio maths and the spreadsheet parser. None of these need
 * an Android device.
 */
class EngineTest {

    // ---------------------------------------------------------------- formatting

    @Test
    fun indianGroupingMatchesTheLakhCroreConvention() {
        assertEquals("999", Fmt.indianGroup(999))
        assertEquals("1,234", Fmt.indianGroup(1234))
        assertEquals("9,17,547", Fmt.indianGroup(917547))
        assertEquals("-1,23,456", Fmt.indianGroup(-123456))
        assertEquals("1,00,00,000", Fmt.indianGroup(10000000))
    }

    @Test
    fun plainGroupingUsesThousands() {
        assertEquals("1,234,567", Fmt.plain(1234567))
        assertEquals("-42", Fmt.plain(-42))
    }

    @Test
    fun croreCompactSwitchesToLakhCroreAboveOneHundredThousand() {
        assertEquals("9 L Cr", Fmt.croreCompact(917547.0)) // 9.18 lakh crore
        assertEquals("12 L Cr", Fmt.croreCompact(1_200_000.0))
    }

    // ------------------------------------------------------------------ arithmetic

    @Test
    fun cagrIsCorrectAndGuarded() {
        val cagr = Num.cagr(100.0, 161.051, 5)
        assertNotNull(cagr)
        assertTrue(abs(cagr!! - 10.0) < 0.01)
        assertNull("a zero base has no CAGR", Num.cagr(0.0, 100.0, 5))
        assertNull("a negative base has no CAGR", Num.cagr(-5.0, 100.0, 5))
        assertNull("zero years is undefined", Num.cagr(100.0, 200.0, 0))
    }

    @Test
    fun divisionByZeroReturnsNullRatherThanInfinity() {
        assertNull(Num.div(1.0, 0.0))
        assertNull(Num.div(null, 2.0))
        assertEquals(2.5, Num.div(5.0, 2.0))
    }

    // ------------------------------------------------------------ spreadsheet rules

    @Test
    fun numbersWithIndianGroupingAndParenthesesParse() {
        assertEquals(-1234.0, SheetParsing.parseNumber("(1,234)")!!, 0.001) // parentheses mean negative
        assertEquals(123456.0, SheetParsing.parseNumber("1,23,456")!!, 0.001)
        assertEquals(-42.5, SheetParsing.parseNumber("-42.5")!!, 0.001)
        assertEquals(12.0, SheetParsing.parseNumber("12%")!!, 0.001)
        assertNull(SheetParsing.parseNumber("-"))
        assertNull(SheetParsing.parseNumber("n/a"))
        assertNull(SheetParsing.parseNumber(null))
    }

    @Test
    fun columnLabelsMapOntoStoredFields() {
        assertEquals("net_profit", SheetParsing.metricKey("Net Profit"))
        assertEquals("net_profit", SheetParsing.metricKey("Profit After Tax"))
        assertEquals("opm_percent", SheetParsing.metricKey("OPM %"))
        assertEquals("sales", SheetParsing.metricKey("Sales +"))
        assertEquals("borrowings", SheetParsing.metricKey("Total Debt"))
        assertEquals("cfo", SheetParsing.metricKey("Cash from Operating Activity"))
        assertNull(SheetParsing.metricKey("Something Unrelated"))
    }

    @Test
    fun periodHeadersAreRecognised() {
        assertEquals(2024, SheetParsing.parsePeriod("Mar 2024")!!.year)
        assertEquals(2024, SheetParsing.parsePeriod("Mar-24")!!.year)
        assertEquals(2024, SheetParsing.parsePeriod("FY24")!!.year)
        assertEquals(2024, SheetParsing.parsePeriod("2024")!!.year)
        assertNull("TTM is not a period we import", SheetParsing.parsePeriod("TTM"))
        assertNull(SheetParsing.parsePeriod(""))
    }

    // --------------------------------------------------------------- query language

    @Test
    fun simpleComparisonCompilesAndEvaluates() {
        val (expr, refs) = QueryCompiler.compile("roe > 20")
        assertEquals(listOf("roe"), refs)
        assertTrue(QueryEvaluator.eval(expr, mapOf("roe" to 25.0)))
        assertFalse(QueryEvaluator.eval(expr, mapOf("roe" to 15.0)))
        assertFalse("a missing metric never matches", QueryEvaluator.eval(expr, mapOf("roe" to null)))
    }

    @Test
    fun booleanPrecedenceAndParentheses() {
        val (expr, _) = QueryCompiler.compile("roe > 20 OR pe < 10 AND debt_to_equity < 0.5")
        // AND binds tighter than OR: true when roe is high regardless of the rest
        assertTrue(QueryEvaluator.eval(expr, mapOf("roe" to 30.0, "pe" to 50.0, "debt_to_equity" to 2.0)))
        // and true when the AND group holds
        assertTrue(QueryEvaluator.eval(expr, mapOf("roe" to 5.0, "pe" to 8.0, "debt_to_equity" to 0.1)))
        // false when neither side holds
        assertFalse(QueryEvaluator.eval(expr, mapOf("roe" to 5.0, "pe" to 8.0, "debt_to_equity" to 3.0)))
    }

    @Test
    fun betweenAndNotAndArithmetic() {
        val (between, _) = QueryCompiler.compile("opm BETWEEN 10 AND 30")
        assertTrue(QueryEvaluator.eval(between, mapOf("opm" to 10.0)))
        assertTrue(QueryEvaluator.eval(between, mapOf("opm" to 30.0)))
        assertFalse(QueryEvaluator.eval(between, mapOf("opm" to 31.0)))

        val (negated, _) = QueryCompiler.compile("NOT debt_to_equity > 1")
        assertTrue(QueryEvaluator.eval(negated, mapOf("debt_to_equity" to 0.4)))
        assertFalse(QueryEvaluator.eval(negated, mapOf("debt_to_equity" to 1.5)))

        val (arith, _) = QueryCompiler.compile("sales / net_profit > 5")
        assertTrue(QueryEvaluator.eval(arith, mapOf("sales" to 1000.0, "net_profit" to 100.0)))
        assertFalse(QueryEvaluator.eval(arith, mapOf("sales" to 1000.0, "net_profit" to 400.0)))
    }

    @Test
    fun unknownMetricProducesAHelpfulError() {
        try {
            QueryCompiler.compile("roe_typo > 5")
            throw AssertionError("expected a QueryException")
        } catch (e: QueryException) {
            assertTrue("message should name the token", e.message!!.contains("roe_typo"))
        }
    }

    @Test
    fun emptyQueryIsRejected() {
        try {
            QueryCompiler.compile("   ")
            throw AssertionError("expected a QueryException")
        } catch (e: QueryException) {
            assertTrue(e.message!!.isNotBlank())
        }
    }

    @Test
    fun everyShippedTemplateCompiles() {
        for (template in Templates.all) {
            try {
                val (_, refs) = QueryCompiler.compile(template.query)
                assertTrue("template ${template.id} references no metric", refs.isNotEmpty())
                for (ref in refs) {
                    assertNotNull("template ${template.id} uses unknown metric $ref", MetricCatalog.get(ref))
                }
            } catch (e: QueryException) {
                throw AssertionError("template ${template.id} does not compile: ${e.message}")
            }
        }
    }

    // --------------------------------------------------------------------- ratios

    private fun sampleRecord(): CompanyRecord {
        val annual = (2019..2025).map { year ->
            val growth = (year - 2019)
            val sales = 1000.0 * (1.15.pow(growth))
            val opm = 0.20
            val op = sales * opm
            val dep = sales * 0.03
            val interest = sales * 0.01
            val other = sales * 0.01
            val pbt = op - dep - interest + other
            val net = pbt * 0.75
            AnnualFinancial(
                companyId = 1, fyEnd = year, sales = sales, expenses = sales - op,
                operatingProfit = op, opmPercent = opm * 100, otherIncome = other,
                interest = interest, depreciation = dep, profitBeforeTax = pbt, taxPercent = 25.0,
                netProfit = net, eps = net / 10.0, dividend = net / 10.0 * 0.3,
                equityCapital = 100.0, reserves = net * 5, borrowings = sales * 0.1,
                totalAssets = sales * 0.8, totalLiabilities = sales * 0.8,
                receivables = sales * 0.12, inventory = sales * 0.10, payables = sales * 0.08,
                cfo = net * 1.1, cfi = -sales * 0.04, cff = 0.0, netCashFlow = net * 1.1 - sales * 0.04
            )
        }
        return CompanyRecord(
            company = Company(id = 1, code = "TEST", name = "Test Co Ltd", sector = "Test"),
            annual = annual,
            quarterly = emptyList(),
            shareholding = emptyList(),
            quote = Quote(companyId = 1, price = 300.0, marketCap = 300.0 * 10.0, high52 = 380.0, low52 = 210.0, source = "test")
        )
    }

    @Test
    fun roeAndRocAreComputedFromTheStoredRows() {
        val f = Financials(sampleRecord())
        val latest = f.latest!!
        val netWorth = latest.equityCapital!! + latest.reserves!!
        val expectedRoe = latest.netProfit!! / netWorth * 100.0
        assertEquals(expectedRoe, Ratios.roe(latest)!!, 1e-9)

        val ebit = latest.profitBeforeTax!! + latest.interest!!
        val expectedRoce = ebit / (netWorth + latest.borrowings!!) * 100.0
        assertEquals(expectedRoce, Ratios.roce(latest)!!, 1e-9)
    }

    @Test
    fun missingInputsProduceNullRatiosNotZero() {
        val empty = Financials(
            CompanyRecord(Company(id = 2, code = "EMPTY", name = "Empty"), emptyList(), emptyList(), emptyList(), null)
        )
        assertNull(Ratios.roe(AnnualFinancial(companyId = 2, fyEnd = 2025)))
        assertNull(Ratios.pe(empty))
        assertNull(Ratios.dcfPerShare(empty))
        assertFalse(empty.hasData)
    }

    @Test
    fun grahamNumberMatchesItsFormula() {
        val f = Financials(sampleRecord())
        val eps = f.latest!!.eps!!
        val bvps = Ratios.bookValuePerShare(f)!!
        val expected = kotlin.math.sqrt(22.5 * eps * bvps)
        assertEquals(expected, Ratios.grahamNumber(f)!!, 1e-9)
    }

    @Test
    fun piotroskiStaysInRange() {
        val score = Ratios.piotroski(Financials(sampleRecord()))
        assertNotNull(score)
        assertTrue("score must be 0..9, was $score", score!! in 0..9)
    }

    // ----------------------------------------------------------------- screening

    @Test
    fun screenEngineRunsOverTheLocalDataset() {
        val engine = ScreenEngine()
        val result = engine.run("roe > 1 AND opm > 15", listOf(sampleRecord()))
        assertNull(result.error)
        assertEquals(1, result.evaluated)
        assertEquals(1, result.hits.size)
        assertEquals("TEST", result.hits.first().company.code)
        assertNotNull("the matched metric values are carried for the table", result.hits.first().values["roe"])
    }

    @Test
    fun screenEngineReportsAQueryErrorInsteadOfThrowing() {
        val result = ScreenEngine().run("nonsense_metric > 3", listOf(sampleRecord()))
        assertNotNull(result.error)
        assertTrue(result.hits.isEmpty())
    }

    @Test
    fun screenEngineSkipsCompaniesWithNoAnnualRows() {
        val empty = CompanyRecord(Company(id = 9, code = "NONE", name = "No Data"), emptyList(), emptyList(), emptyList(), null)
        val result = ScreenEngine().run("roe > 1", listOf(empty, sampleRecord()))
        assertEquals(1, result.evaluated)
        assertEquals(1, result.skipped)
    }

    // ------------------------------------------------------------------ checklist

    @Test
    fun checklistProducesTraceableObservations() {
        val insights = ProsConsEngine.analyse(Financials(sampleRecord()))
        assertTrue("the checklist should say something about this company", insights.isNotEmpty())
        assertTrue("every insight needs a rule id", insights.all { it.ruleId.isNotBlank() })
        assertTrue("every insight needs text", insights.all { it.text.length > 10 })
    }
}
