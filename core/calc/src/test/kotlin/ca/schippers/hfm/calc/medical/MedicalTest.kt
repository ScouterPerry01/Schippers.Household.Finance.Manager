package ca.schippers.hfm.calc.medical

import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MedicalTest {

    private fun n(s: String) = BigDecimal(s)
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `what a plan pays`() {
        val massage = CoverageRule(n("80"), deductible = n("25"), annualMax = n("500"), perVisitMax = n("60"))
        // (120 − 25 deductible) × 80 % = 76, capped at 60 a visit.
        assertEquals(n("60.00"), Medical.expected(n("120"), massage))
        // Deductible already met: 50 × 80 % = 40.
        assertEquals(n("40.00"), Medical.expected(n("50"), massage, CoverageUse(deductibleMet = n("25"))))
        // Only 30 left of the yearly 500.
        assertEquals(n("30.00"), Medical.expected(n("120"), massage, CoverageUse(paid = n("470"), deductibleMet = n("25"))))
        assertEquals(n("0.00"), Medical.expected(n("120"), massage, CoverageUse(paid = n("500"))))
        assertEquals(n("25"), Medical.deductibleUsed(n("120"), massage, CoverageUse()))
        assertEquals(n("0"), Medical.deductibleUsed(n("120"), massage, CoverageUse(deductibleMet = n("25"))))
    }

    @Test
    fun `coordination of benefits between two plans`() {
        val primary = CoverageRule(n("80"))
        val secondary = CoverageRule(n("100"))
        val first = Medical.expected(n("100"), primary)
        assertEquals(n("80.00"), first)
        assertEquals(n("20.00"), Medical.expected(n("100") - first, secondary), "the second plan sees only what is left")
    }

    @Test
    fun `next eligible date, plan year and claim deadline`() {
        assertEquals(d("2027-03-15"), Medical.nextEligible(d("2025-03-15"), 24), "one eye exam every 24 months")
        assertNull(Medical.nextEligible(d("2025-03-15"), null))
        assertEquals(d("2025-07-01"), Medical.planYearStart(d("2026-03-01"), 7, 1))
        assertEquals(d("2026-07-01"), Medical.planYearStart(d("2026-08-01"), 7, 1))
        assertEquals(d("2026-01-01"), Medical.planYearStart(d("2026-08-01")))
        assertEquals(d("2027-01-12"), Medical.claimDeadline(d("2026-01-12"), 365))
    }

    /**
     * The federal credit allows any 12-month period ending in the year (CRA, line 33099): here the
     * period ending February 1, 2026 holds more than the calendar year 2026.
     */
    @Test
    fun `best 12-month window`() {
        val expenses = listOf(d("2025-05-01") to n("1000"), d("2025-11-01") to n("500"), d("2026-02-01") to n("300"), d("2026-09-01") to n("200"))
        val best = Medical.bestWindow(expenses, 2026)!!
        assertEquals(Window(d("2025-02-02"), d("2026-02-01"), n("1800")), best)
        assertEquals(n("500"), Medical.calendarYear(expenses, 2026).total)
        assertNull(Medical.bestWindow(expenses, 2024))
        // A tie goes to the earlier period.
        val tie = Medical.bestWindow(listOf(d("2026-01-10") to n("100"), d("2026-06-10") to n("0")), 2026)!!
        assertEquals(d("2026-01-10"), tie.end)
    }
}
