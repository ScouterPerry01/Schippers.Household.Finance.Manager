package ca.schippers.hfm.books

import ca.schippers.hfm.calc.loan.PaymentFrequency
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoanServiceTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var group: String
    private lateinit var chequing: Account
    private lateinit var mortgage: Account
    private fun d(s: String) = LocalDate.parse(s)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id
    private fun balance(a: Account) = books.accounts.list().first { it.account.id == a.id }.balance

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("L.hfm"), "L", "perry", "Perry", "pw".toCharArray()).session)
        group = books.groups().single().id
        chequing = books.accounts.create(AccountDraft(group, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("20000"), d("2026-01-01")))
        mortgage = books.accounts.create(AccountDraft(group, "Mortgage", AccountType.MORTGAGE, Currency.CAD, cad("-500000"), d("2026-01-01")))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun terms() = LoanDetails(
        mortgage.id, cad("500000"), BigDecimal("0.05"), amortizationMonths = 300, firstPaymentDate = d("2026-02-01"),
        termEnd = d("2031-01-01"), propertyTax = cad("250"), paymentAccountId = chequing.id,
    )

    @Test
    fun `terms give the schedule and today's status`() {
        books.loans.save(terms())
        val status = books.loans.status(mortgage.id, d("2026-01-15"))
        assertEquals(cad("2908.02"), status.payment)
        assertEquals(cad("3158.02"), status.totalPayment, "payment plus property tax")
        assertEquals(cad("500000"), status.owed)
        assertEquals(d("2026-02-01"), status.nextPayment)
        assertEquals(d("2051-02-01"), status.payoffDate)
        assertEquals(cad("372409.01"), status.interestRemaining)
        assertEquals(1812, status.daysToRenewal)
        assertEquals(301, books.loans.projection(mortgage.id).rows.size)
        assertEquals(listOf(mortgage.id), books.loans.accounts().filter { it.second != null }.map { it.first.account.id })
    }

    @Test
    fun `recording a payment moves one amount and charges interest and tax to the loan`() {
        books.loans.save(terms())
        val split = books.loans.nextPayment(mortgage.id, d("2026-01-20"))!!
        assertEquals(d("2026-02-01"), split.date)
        assertEquals(cad("2061.96"), split.interest)
        assertEquals(cad("846.06"), split.principal)
        assertEquals(cad("3158.02"), split.total)
        val charge = books.loans.recordPayment(mortgage.id, chequing.id, split)

        assertEquals(cad("16841.98"), balance(chequing), "one line of 3,158.02 left the chequing account")
        assertEquals(cad("-499153.94"), balance(mortgage), "only the principal reduced the mortgage")
        assertEquals(setOf(cat("housing.mortgage_interest"), cat("housing.municipal_tax")), charge.splits.map { it.categoryId }.toSet())
        assertEquals(d("2026-03-01"), books.loans.nextPayment(mortgage.id, d("2026-02-02"))!!.date, "the next one follows the last recorded")
        assertEquals(d("2026-02-01"), books.loans.details(mortgage.id)!!.lastPaidDate)
    }

    @Test
    fun `a prepayment moves money and shows the interest saved`() {
        books.loans.save(terms())
        books.loans.addPrepayment(mortgage.id, d("2027-01-01"), cad("10000"), chequing.id, "Bonus")
        assertEquals(cad("10000"), balance(chequing))
        assertEquals(cad("-490000"), balance(mortgage))
        val status = books.loans.status(mortgage.id, d("2027-01-15"))
        assertEquals(cad("22044.79"), status.interestSaved)
        assertEquals(12, status.monthsSooner)
        assertEquals(LoanChangeKind.PREPAYMENT, books.loans.changes(mortgage.id).single().kind)
    }

    @Test
    fun `renewal recalculates the payment and moves the term end`() {
        books.loans.save(terms())
        books.loans.renew(mortgage.id, d("2031-01-01"), BigDecimal("0.04"), d("2036-01-01"))
        val rows = books.loans.projection(mortgage.id).rows
        assertEquals(cad("2908.02"), rows[59].payment)
        assertEquals(cad("2674.02"), rows[60].payment)
        assertEquals(d("2036-01-01"), books.loans.details(mortgage.id)!!.termEnd)
    }

    @Test
    fun `deleting a renewal puts the term end back (M-30)`() {
        books.loans.save(terms())
        books.loans.changeRate(mortgage.id, d("2028-01-01"), BigDecimal("0.045"), recalculatePayment = false)
        books.loans.renew(mortgage.id, d("2031-01-01"), BigDecimal("0.04"), d("2036-01-01"))
        books.loans.renew(mortgage.id, d("2036-01-01"), BigDecimal("0.035"), d("2041-01-01"))
        val changes = books.loans.changes(mortgage.id)
        assertEquals(listOf(false, true, true), changes.map { it.renewal })
        assertEquals(d("2031-01-01"), changes[1].previousTermEnd)
        assertNull(changes[1].amount, "a renewal shows no amount")

        books.loans.deleteChange(mortgage.id, changes[0].id)
        assertEquals(d("2041-01-01"), books.loans.details(mortgage.id)!!.termEnd, "a plain rate change leaves the term end")
        books.loans.deleteChange(mortgage.id, changes[1].id)
        assertEquals(d("2041-01-01"), books.loans.details(mortgage.id)!!.termEnd, "an older renewal leaves the latest term end")
        books.loans.deleteChange(mortgage.id, changes[2].id)
        assertEquals(d("2036-01-01"), books.loans.details(mortgage.id)!!.termEnd)
        assertTrue(books.loans.changes(mortgage.id).isEmpty())
    }

    @Test
    fun `renewal reminders use the loan's own lead time`() {
        books.loans.save(terms())
        assertTrue(books.renewals(d("2030-08-01")).none { it.kind == RenewalKind.LOAN_RENEWAL }, "153 days ahead")
        val due = books.renewals(d("2030-09-15")).single { it.kind == RenewalKind.LOAN_RENEWAL }
        assertEquals(108, due.daysLeft)
        assertEquals("Mortgage", due.subjectName)
        assertTrue(books.calendar.items(d("2031-01-01"), d("2031-01-31")).any { it is CalendarItem.Renewal })
    }

    @Test
    fun `what-if from the actual balance`() {
        books.loans.save(terms())
        val extra = books.loans.whatIf(mortgage.id, d("2026-01-15"), extraPerPayment = cad("500"))
        assertEquals(cad("3408.02"), extra.alternative.rows.first().payment)
        assertTrue(extra.interestSaved > cad("80000"))
        assertTrue(extra.monthsSooner > 60)
        assertFailsWith<ValidationException> { books.loans.whatIf(mortgage.id, d("2026-01-15"), annualRate = BigDecimal("1.5")) }
    }

    @Test
    fun `terms that never repay are refused`() {
        assertFailsWith<ValidationException> { books.loans.save(terms().copy(payment = cad("1000"))) }
        assertNull(books.loans.details(mortgage.id))
        books.loans.save(terms())
        assertFailsWith<ValidationException> { books.loans.changePayment(mortgage.id, d("2026-06-01"), cad("1000")) }
        assertTrue(books.loans.changes(mortgage.id).isEmpty())
        assertFailsWith<ValidationException> { books.loans.save(terms().copy(accountId = chequing.id)) }
    }

    @Test
    fun `debt payoff goes from today's balance to zero, with the interest of each year`() {
        books.loans.save(terms())
        val today = d("2026-01-15")
        val payoff = books.loans.debtPayoffs(today).single()
        assertEquals(mortgage.id, payoff.account.id)
        assertEquals(cad("500000"), payoff.balanceAtEndOf(2025), "nothing paid yet")
        assertTrue(payoff.balanceAtEndOf(2026) < cad("500000"))
        assertEquals(cad("0"), payoff.balanceAtEndOf(2051))
        assertEquals(books.loans.status(mortgage.id, today).payoffDate, payoff.payoffDate)
        val interest = payoff.interestByYear
        assertEquals(2026, interest.keys.first())
        assertEquals(books.loans.status(mortgage.id, today).interestRemaining, interest.values.reduce { a, b -> a + b }, "the years add up to the interest left")
        assertTrue(interest.getValue(2027) > interest.getValue(2040), "less interest each year as the balance falls")
        // A card has no payoff schedule.
        books.accounts.create(AccountDraft(group, "Visa", AccountType.CREDIT_CARD, Currency.CAD, cad("-1200"), d("2026-01-01")))
        assertEquals(1, books.loans.debtPayoffs(today).size)
    }

    @Test
    fun `debt summary lists every liability`() {
        books.loans.save(terms().copy(frequency = PaymentFrequency.ACCELERATED_BI_WEEKLY, firstPaymentDate = d("2026-01-16")))
        val visa = books.accounts.create(AccountDraft(group, "Visa", AccountType.CREDIT_CARD, Currency.CAD, cad("-1200"), d("2026-01-01")))
        books.creditCards.saveTerms(visa.id, CreditCardTerms(purchaseRate = BigDecimal("0.1999"), minPaymentPercent = BigDecimal("0.03"), minPaymentFloor = cad("10")))
        val lines = books.loans.debtSummary(d("2026-01-10"))
        assertEquals(listOf("Mortgage", "Visa"), lines.map { it.account.name })
        assertEquals(cad("1454.01"), lines[0].payment)
        assertTrue(lines[0].payoffDate!! < d("2049-01-01"), "accelerated payments finish years early")
        assertEquals(cad("36.00"), lines[1].payment)
        assertEquals(BigDecimal("0.1999"), lines[1].annualRate)
    }
}
