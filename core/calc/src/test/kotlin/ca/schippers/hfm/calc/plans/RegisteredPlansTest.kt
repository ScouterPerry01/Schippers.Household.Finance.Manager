package ca.schippers.hfm.calc.plans

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reference figures: the CRA's prescribed RRIF factors, the published LIF maximum percentages at
 * a 6% reference rate (7.38% at 65, 12.82% at 80), the CRA's cumulative TFSA limit ($109,000 for
 * someone eligible since 2009, in 2026), and the CESG and QESI rules of Employment and Social
 * Development Canada and Revenu Québec.
 */
class RegisteredPlansTest {

    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun pct(v: BigDecimal, places: Int = 2) = v.movePointRight(2).setScale(places, RoundingMode.HALF_UP)

    @Test
    fun `RRIF minimum factors`() {
        assertEquals(BigDecimal("4.00"), pct(RegisteredPlans.rrifFactor(65)))
        assertEquals(BigDecimal("3.33"), pct(RegisteredPlans.rrifFactor(60)))
        assertEquals(BigDecimal("5.28"), pct(RegisteredPlans.rrifFactor(71)))
        assertEquals(BigDecimal("6.82"), pct(RegisteredPlans.rrifFactor(80)))
        assertEquals(BigDecimal("18.79"), pct(RegisteredPlans.rrifFactor(94)))
        assertEquals(BigDecimal("20.00"), pct(RegisteredPlans.rrifFactor(99)))
        assertEquals(cad("5400.00"), RegisteredPlans.rrifMinimum(cad("100000"), 72))
        assertEquals(cad("10000.00"), RegisteredPlans.rrifMinimum(cad("250000"), 65))
    }

    @Test
    fun `Quebec LIF maximum at the 6 percent reference rate`() {
        assertEquals(BigDecimal("7.38"), pct(RegisteredPlans.lifMaximumFactor(65)))
        assertEquals(BigDecimal("6.51"), pct(RegisteredPlans.lifMaximumFactor(55)))
        assertEquals(BigDecimal("12.82"), pct(RegisteredPlans.lifMaximumFactor(80)))
        assertEquals(BigDecimal("100.00"), pct(RegisteredPlans.lifMaximumFactor(89)))
        assertEquals(cad("7379.88"), RegisteredPlans.lifMaximum(cad("100000"), 65))
        assertEquals(cad("9000"), RegisteredPlans.lifMaximum(cad("100000"), 65, lastYearEarnings = cad("9000")), "last year's earnings when higher")
        assertTrue(RegisteredPlans.lifMaximumFactor(65, BigDecimal("0.08")) > RegisteredPlans.lifMaximumFactor(65))
    }

    @Test
    fun `TFSA limits`() {
        assertEquals(109000, RegisteredPlans.tfsaCumulativeLimit(1980, 2026))
        assertEquals(102000, RegisteredPlans.tfsaCumulativeLimit(1980, 2025))
        assertEquals(7000 + 7000 + 7000 + 6500, RegisteredPlans.tfsaCumulativeLimit(2005, 2026), "eligible from 2023, the year of turning 18")
        assertEquals(10000, RegisteredPlans.tfsaLimit(2015))
        assertEquals(7000, RegisteredPlans.tfsaLimit(2030), "unpublished years repeat the last known limit")
    }

    @Test
    fun `FHSA room with carry-forward and the lifetime limit`() {
        val rooms = RegisteredPlans.fhsaRoom(2023, 2028, mapOf(2023 to 3000_00L, 2024 to 0L, 2025 to 16000_00L, 2026 to 8000_00L, 2027 to 8000_00L))
        assertEquals(listOf(8000_00L, 13000_00L, 16000_00L, 8000_00L, 8000_00L, 5000_00L), rooms.map { it.available })
        assertEquals(0L, rooms.last { it.year == 2027 }.left)
        assertEquals(5000_00L, rooms.last().available, "only 5,000 of the 40,000 lifetime limit is left")
    }

    @Test
    fun `CESG with carry-forward`() {
        val years = RegisteredPlans.grants(RegisteredPlans.CESG, 2020, mapOf(2022 to 5000_00L, 2023 to 2500_00L), 2023)
        assertEquals(listOf(0L, 0L, 1000_00L, 500_00L), years.map { it.grant })
        assertEquals(500_00L, years.last().roomLeft, "1,500 accrued by 2022, 1,000 paid; 500 more in 2023, 500 paid")
    }

    @Test
    fun `QESI and the lifetime maximum`() {
        val steady = (2010..2027).associateWith { 2500_00L }
        val q = RegisteredPlans.grants(RegisteredPlans.QESI, 2010, steady, 2027)
        assertEquals(250_00L, q.first().grant)
        assertEquals(3600_00L, q.sumOf { it.grant }, "the lifetime maximum is reached")
        val c = RegisteredPlans.grants(RegisteredPlans.CESG, 2010, steady, 2027)
        assertEquals(7200_00L, c.sumOf { it.grant })
    }

    @Test
    fun `no grant at 16 and 17 without earlier contributions`() {
        val late = RegisteredPlans.grants(RegisteredPlans.CESG, 2008, mapOf(2024 to 2500_00L), 2025)
        assertFalse(late.first { it.year == 2024 }.eligible)
        assertEquals(0L, late.sumOf { it.grant })
        val early = RegisteredPlans.grants(RegisteredPlans.CESG, 2008, mapOf(2020 to 2000_00L, 2024 to 2500_00L), 2025)
        assertEquals(500_00L, early.first { it.year == 2024 }.grant, "20% of 2,500")
    }
}
