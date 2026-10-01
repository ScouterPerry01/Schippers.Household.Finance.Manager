package ca.schippers.hfm.calc.loan

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reference figures were computed independently with Python's decimal module at 40 digits;
 * the 500k / 5% / 25-year monthly payment of $2,908.02 also matches published Canadian tables.
 */
class AmortizationTest {

    private fun cad(s: String) = Money.parse(s, Currency.CAD)

    private fun mortgage(principal: String, rate: String, frequency: PaymentFrequency = PaymentFrequency.MONTHLY) =
        LoanTerms(cad(principal), BigDecimal(rate), Compounding.SEMI_ANNUAL, 300, frequency)

    @Test
    fun `semi-annual compounding gives the Canadian monthly rate`() {
        val rate = Amortization.periodicRate(BigDecimal("0.05"), Compounding.SEMI_ANNUAL, 12)
        assertEquals("0.004123915465144271401093578688687", rate.round(java.math.MathContext(34)).toPlainString())
    }

    @Test
    fun `monthly payments match reference figures`() {
        assertEquals(cad("2908.02"), Amortization.payment(mortgage("500000", "0.05")))
        assertEquals(cad("2213.89"), Amortization.payment(mortgage("400000", "0.045")))
    }

    @Test
    fun `weekly and accelerated payments`() {
        assertEquals(cad("670.02"), Amortization.payment(mortgage("500000", "0.05", PaymentFrequency.WEEKLY)))
        assertEquals(cad("1454.01"), Amortization.payment(mortgage("500000", "0.05", PaymentFrequency.ACCELERATED_BI_WEEKLY)))
        assertEquals(cad("727.01"), Amortization.payment(mortgage("500000", "0.05", PaymentFrequency.ACCELERATED_WEEKLY)))
    }

    @Test
    fun `schedule splits payments and matches the balance after five years`() {
        val schedule = Amortization.schedule(mortgage("500000", "0.05"))
        val afterFiveYears = schedule.rows[59]
        assertEquals(cad("442537.91"), afterFiveYears.balance)
        assertEquals(cad("117019.11"), schedule.rows.take(60).fold(cad("0")) { a, r -> a + r.interest })
        schedule.rows.forEach { assertEquals(it.payment, it.interest + it.principal) }
    }

    @Test
    fun `schedule repays exactly the principal`() {
        val terms = mortgage("500000", "0.05")
        val schedule = Amortization.schedule(terms)
        assertEquals(terms.principal, schedule.rows.fold(cad("0")) { a, r -> a + r.principal })
        assertTrue(schedule.rows.last().balance.isZero)
        assertTrue(schedule.rows.size in 299..301, "about 300 payments, was ${schedule.rows.size}")
    }

    @Test
    fun `accelerated bi-weekly pays off years sooner`() {
        val schedule = Amortization.schedule(mortgage("500000", "0.05", PaymentFrequency.ACCELERATED_BI_WEEKLY))
        val years = schedule.rows.size / 26.0
        assertTrue(years in 21.0..23.0, "expected roughly 22 years, was $years")
    }

    @Test
    fun `zero interest loan divides evenly`() {
        val terms = LoanTerms(cad("1200"), BigDecimal.ZERO, Compounding.MONTHLY, 12, PaymentFrequency.MONTHLY)
        assertEquals(cad("100.00"), Amortization.payment(terms))
        assertEquals(12, Amortization.schedule(terms).rows.size)
    }
}
