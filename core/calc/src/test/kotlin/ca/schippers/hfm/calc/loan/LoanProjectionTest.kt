package ca.schippers.hfm.calc.loan

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Reference figures were computed separately with Python's decimal module at 40 digits, using a
 * plain period-by-period loop. $639.81 per $100,000 at 6% over 25 years is the long-published
 * Canadian mortgage table figure.
 */
class LoanProjectionTest {

    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun d(s: String) = LocalDate.parse(s)

    private fun plan(principal: String, rate: String, changes: List<LoanChange> = emptyList(), frequency: PaymentFrequency = PaymentFrequency.MONTHLY) =
        LoanPlan(LoanTerms(cad(principal), BigDecimal(rate), Compounding.SEMI_ANNUAL, 300, frequency), d("2026-01-01"), changes = changes)

    @Test
    fun `published mortgage table payments`() {
        assertEquals(cad("639.81"), Amortization.payment(plan("100000", "0.06").terms))
        assertEquals(cad("1717.59"), Amortization.payment(plan("300000", "0.0484").terms))
    }

    @Test
    fun `dated schedule matches the reference totals`() {
        val p = LoanProjection.project(plan("500000", "0.05"))
        assertEquals(301, p.rows.size)
        assertEquals(cad("372409.01"), p.totalInterest)
        assertEquals(d("2051-01-01"), p.payoffDate)
        assertEquals(cad("442537.91"), p.balanceOn(d("2030-12-15")))
        assertEquals(cad("500000"), p.balanceOn(d("2025-12-31")))
    }

    @Test
    fun `a lump sum saves the reference interest`() {
        val saved = LoanProjection.interestSaved(plan("500000", "0.05", listOf(LoanChange.Prepayment(d("2026-12-01"), cad("10000")))))
        assertEquals(cad("22044.79"), saved.interestSaved)
        assertEquals(12, saved.paymentsSaved)
        assertEquals(12, saved.monthsSooner)
        assertEquals(cad("10000"), saved.alternative.rows[11].prepayment)
    }

    @Test
    fun `renewal at a new rate recalculates the payment over the remaining amortization`() {
        val p = LoanProjection.project(plan("500000", "0.05", listOf(LoanChange.RateChange(d("2030-12-15"), BigDecimal("0.04"), recalculatePayment = true))))
        assertEquals(cad("2908.02"), p.rows[59].payment)
        assertEquals(cad("2674.02"), p.rows[60].payment)
        assertEquals(BigDecimal("0.04"), p.rows[60].annualRate)
        assertEquals(300, p.rows.size)
        assertEquals(cad("316245.80"), p.totalInterest)
    }

    @Test
    fun `a variable rate rise keeps the payment and lengthens the loan`() {
        val base = LoanProjection.project(plan("500000", "0.05"))
        val p = LoanProjection.project(plan("500000", "0.05", listOf(LoanChange.RateChange(d("2027-06-15"), BigDecimal("0.06"), recalculatePayment = false))))
        assertEquals(cad("2908.02"), p.rows[30].payment)
        assertTrue(p.rows.size > base.rows.size)
    }

    @Test
    fun `a payment that no longer covers the interest is refused`() {
        assertFailsWith<LoanNeverRepaysException> {
            LoanProjection.project(plan("500000", "0.05", listOf(LoanChange.PaymentChange(d("2026-06-01"), cad("1000")))))
        }
    }

    @Test
    fun `every payment splits exactly and the principal is repaid`() {
        val p = LoanProjection.project(
            plan("250000", "0.0539", listOf(LoanChange.Prepayment(d("2027-03-10"), cad("5000"))), PaymentFrequency.ACCELERATED_BI_WEEKLY),
        )
        p.rows.forEach { assertEquals(it.payment, it.interest + it.principal) }
        assertEquals(cad("250000"), p.rows.fold(cad("0")) { a, r -> a + r.principal + r.prepayment })
        assertTrue(p.rows.last().balance.isZero)
    }

    @Test
    fun `payment dates per frequency`() {
        fun dates(first: String, f: PaymentFrequency) = LoanProjection.paymentDates(d(first), f).take(4).map { it.toString() }.toList()
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"), dates("2026-01-31", PaymentFrequency.MONTHLY))
        assertEquals(listOf("2026-01-15", "2026-01-30", "2026-02-15", "2026-02-28"), dates("2026-01-15", PaymentFrequency.SEMI_MONTHLY))
        assertEquals(listOf("2026-01-16", "2026-02-01", "2026-02-16", "2026-03-01"), dates("2026-01-16", PaymentFrequency.SEMI_MONTHLY))
        assertEquals(listOf("2026-01-02", "2026-01-16", "2026-01-30", "2026-02-13"), dates("2026-01-02", PaymentFrequency.ACCELERATED_BI_WEEKLY))
        assertEquals(listOf("2026-01-02", "2026-01-09", "2026-01-16", "2026-01-23"), dates("2026-01-02", PaymentFrequency.WEEKLY))
    }

    @Test
    fun `what-if from today's balance`() {
        val loan = plan("500000", "0.05")
        val today = d("2030-12-15")
        val balance = cad("442537.91")
        val same = LoanProjection.whatIf(loan, today, balance)
        assertEquals(cad("0"), same.interestSaved)
        assertEquals(cad("2908.02"), same.base.rows.first().payment)
        assertEquals(d("2031-01-01"), same.base.rows.first().date)

        val extra = LoanProjection.whatIf(loan, today, balance, extraPerPayment = cad("200"))
        assertEquals(cad("3108.02"), extra.alternative.rows.first().payment)
        assertTrue(extra.interestSaved.isPositive && extra.monthsSooner > 0)

        val lowerRate = LoanProjection.whatIf(loan, today, balance, annualRate = BigDecimal("0.04"))
        assertEquals(cad("2674.02"), lowerRate.alternative.rows.first().payment)

        val shorter = LoanProjection.whatIf(loan, today, balance, remainingAmortizationMonths = 180)
        assertTrue(shorter.alternative.rows.first().payment > cad("2908.02"))
        assertTrue(shorter.interestSaved.isPositive)
    }
}
