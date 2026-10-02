package ca.schippers.hfm.calc.invest

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaxSlipsTest {

    private fun n(s: String) = BigDecimal(s)

    /**
     * CRA line 12000 and 40425 examples: $100 of eligible dividends is $138 taxable with a federal
     * credit of 15.0198 % of that ($20.73); $100 of other dividends in 2019 or later is $115
     * taxable with a credit of 9.0301 % ($10.38).
     */
    @Test
    fun `T5 boxes with gross-up and federal credit`() {
        val income = InvestmentIncome(eligibleDividends = n("100"), ordinaryDividends = n("100"), interest = n("42.17"), foreignIncome = n("50"), foreignTax = n("7.50"))
        assertEquals(
            mapOf(
                "10" to n("100.00"), "11" to n("115.00"), "12" to n("10.38"), "13" to n("42.17"), "15" to n("50.00"), "16" to n("7.50"),
                "24" to n("100.00"), "25" to n("138.00"), "26" to n("20.73"),
            ),
            TaxSlips.boxes(SlipKind.T5, income, 2026),
        )
        assertEquals(n("116.00"), TaxSlips.boxes(SlipKind.T5, InvestmentIncome(ordinaryDividends = n("100")), 2018)["11"], "16 % gross-up in 2018")
    }

    @Test
    fun `T3 and the Quebec slips carry the same amounts in their own boxes`() {
        val income = InvestmentIncome(eligibleDividends = n("200"), otherIncome = n("30"), interest = n("20"), capitalGains = n("75"), returnOfCapital = n("12"), foreignIncome = n("40"), foreignTax = n("6"))
        val t3 = TaxSlips.boxes(SlipKind.T3, income, 2026)
        assertEquals(n("75.00"), t3["21"])
        assertEquals(n("50.00"), t3["26"], "interest from a trust is other income")
        assertEquals(n("276.00"), t3["50"])
        assertEquals(n("12.00"), t3["42"])
        assertEquals(n("6.00"), t3["34"])
        val rl16 = TaxSlips.boxes(SlipKind.RL16, income, 2026)
        assertEquals(mapOf("A" to n("75.00"), "C1" to n("200.00"), "F" to n("40.00"), "G" to n("50.00"), "I" to n("276.00"), "L" to n("6.00"), "M" to n("12.00")), rl16)
        val rl3 = TaxSlips.boxes(SlipKind.RL3, InvestmentIncome(eligibleDividends = n("100"), ordinaryDividends = n("100"), interest = n("5")), 2026)
        assertEquals(mapOf("A1" to n("100.00"), "A2" to n("100.00"), "B" to n("253.00"), "D" to n("5.00")), rl3)
        assertTrue(TaxSlips.boxCodes(SlipKind.RL3).containsAll(rl3.keys))
        assertEquals(SlipKind.RL16, TaxSlips.quebecOf(SlipKind.T3))
    }

    @Test
    fun `joint accounts are shared`() {
        val half = InvestmentIncome(interest = n("100.01"), eligibleDividends = n("50")).share(2)
        assertEquals(n("50.01"), half.interest)
        assertEquals(n("25.00"), half.eligibleDividends)
        assertTrue(InvestmentIncome().isZero)
    }
}
