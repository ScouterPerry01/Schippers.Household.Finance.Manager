package ca.schippers.hfm.books

import ca.schippers.hfm.calc.schedule.BusinessDayAdjust
import ca.schippers.hfm.calc.schedule.Frequency
import ca.schippers.hfm.calc.schedule.MonthDay
import ca.schippers.hfm.calc.schedule.Recurrence
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.ClearedStatus
import ca.schippers.hfm.importers.ImportedLine
import ca.schippers.hfm.importers.ImportedStatement
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BillServiceTest {

    @TempDir
    lateinit var temp: Path

    private lateinit var books: Books
    private lateinit var chequing: Account
    private lateinit var savings: Account
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun d(m: Int, day: Int) = LocalDate(2026, m, day)
    private fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id

    @BeforeEach
    fun setUp() {
        val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
        books = Books(store.create(temp.resolve("B.hfm"), "B", "perry", "Perry", "admin-pass".toCharArray()).session)
        val group = books.groups().single().id
        chequing = books.accounts.create(AccountDraft(group, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("1000.00"), d(1, 1)))
        savings = books.accounts.create(AccountDraft(group, "Savings", AccountType.SAVINGS, Currency.CAD, cad("0"), d(1, 1)))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun hydro() = books.bills.create(
        BillDraft(
            BillKind.BILL, "Hydro-Québec", cad("130.00"), chequing.id, Recurrence(Frequency.MONTHLY, interval = 2), d(1, 12),
            payeeName = "Hydro-Québec", amountKind = AmountKind.VARIABLE, categoryId = cat("utilities.electricity"),
            paymentMethod = PaymentMethod.PAD,
        ),
    )

    @Test
    fun `agenda groups overdue, today, upcoming and paid`() {
        val rent = books.bills.create(BillDraft(BillKind.BILL, "Rent", cad("1450.00"), chequing.id, Recurrence.MONTHLY, d(1, 1)))
        books.bills.markPaid(rent.id, d(9, 1), d(9, 1))
        val agenda = books.bills.agenda(today = d(10, 1), days = 30)
        assertEquals(listOf(d(10, 1)), agenda.dueToday.map { it.dueDate })
        assertEquals((1..8).map { d(it, 1) }, agenda.overdue.map { it.dueDate }, "earlier months were never marked paid")
        assertEquals(listOf(d(9, 1)), agenda.paid.map { it.dueDate })
        assertTrue(agenda.upcoming.isEmpty())
    }

    @Test
    fun `mark as paid creates the payment, which the statement import then matches`() {
        val bill = hydro()
        val paid = books.bills.markPaid(bill.id, d(3, 12), d(3, 12), cad("142.37"))
        assertEquals(OccurrenceStatus.PAID, paid.status)
        val txn = books.transactions.get(paid.transactionId!!)
        assertEquals(cad("-142.37"), txn.amount)
        assertEquals(cat("utilities.electricity"), txn.splits.single().categoryId)

        val statement = ImportedStatement("OFX", null, Currency.CAD, d(3, 1), d(3, 31), null, null, listOf(ImportedLine("H1", d(3, 13), cad("-142.37"), "HYDRO-QUEBEC", null, null)))
        assertEquals(1, books.statements.import(chequing.id, statement).matched)
        assertEquals(ClearedStatus.CLEARED, books.transactions.get(txn.id).cleared)

        books.bills.unmarkPaid(bill.id, d(3, 12), deleteTransaction = false)
        assertEquals(OccurrenceStatus.DUE, books.bills.occurrences(d(3, 1), d(3, 31)).single().status)
    }

    @Test
    fun `variable bills are expected at the average of recent amounts, and unusual ones are flagged`() {
        val bill = hydro()
        listOf(1 to "120.00", 3 to "130.00", 5 to "140.00", 7 to "210.00").forEach { (m, amount) ->
            books.bills.markPaid(bill.id, d(m, 12), d(m, 12), cad(amount))
        }
        val next = books.bills.occurrences(d(9, 1), d(9, 30)).single()
        assertEquals(cad("160.00"), next.amount, "average of 130, 140 and 210")
        assertFalse(next.amountKnown)

        books.bills.setAmount(bill.id, d(9, 12), cad("155.10"))
        assertTrue(books.bills.occurrences(d(9, 1), d(9, 30)).single().amountKnown)

        val history = books.bills.history(bill.id)
        assertEquals(d(7, 12), history.first().occurrence.dueDate)
        assertTrue(history.first().unusual, "210 is more than 25% above the average of 130")
        assertFalse(history.last().unusual)
    }

    @Test
    fun `transfers and income in the forecast, with a warning for an overdraft`() {
        books.bills.create(BillDraft(BillKind.INCOME, "Pay", cad("2000.00"), chequing.id, Recurrence.BI_WEEKLY, d(10, 9)))
        books.bills.create(BillDraft(BillKind.TRANSFER, "Saving", cad("300.00"), chequing.id, Recurrence.MONTHLY, d(10, 5), transferAccountId = savings.id))
        books.bills.create(
            BillDraft(BillKind.BILL, "Car loan", cad("900.00"), chequing.id, Recurrence(Frequency.MONTHLY, adjust = BusinessDayAdjust.NEXT), d(10, 3), paymentMethod = PaymentMethod.PAD),
        )
        val forecast = books.bills.forecast(today = d(10, 1), days = 30).associateBy { it.account.name }
        val cheq = forecast.getValue("Chequing")
        // Oct 5 (Mon): car loan 900 (Oct 3 is a Saturday) leaves 100, saving 300 leaves -200, pay on Oct 9 and 23.
        assertEquals(listOf(d(10, 1), d(10, 5), d(10, 5), d(10, 9), d(10, 23)), cheq.points.map { it.date })
        assertEquals(cad("-200.00"), cheq.lowest)
        assertEquals(listOf("Saving"), cheq.shortfalls.map { it.occurrence!!.bill.name })
        assertEquals(cad("3800.00"), cheq.points.last().balance)
        assertEquals(cad("300.00"), forecast.getValue("Savings").points.last().balance)
    }

    @Test
    fun `scheduled transfer marked paid moves the money`() {
        val bill = books.bills.create(BillDraft(BillKind.TRANSFER, "Saving", cad("300.00"), chequing.id, Recurrence.MONTHLY, d(10, 5), transferAccountId = savings.id))
        books.bills.markPaid(bill.id, d(10, 5), d(10, 5))
        assertEquals(cad("300.00"), books.accounts.list().first { it.account.id == savings.id }.balance)
    }

    @Test
    fun `reminders follow lead times and subscription cancellation deadlines`() {
        books.bills.create(BillDraft(BillKind.BILL, "Insurance", cad("80.00"), chequing.id, Recurrence.MONTHLY, d(10, 8), reminderDays = listOf(7, 1)))
        books.bills.create(
            BillDraft(BillKind.BILL, "Streaming", cad("16.99"), chequing.id, Recurrence.MONTHLY, d(10, 20), isSubscription = true, cancelBy = d(10, 4)),
        )
        val reminders = books.bills.reminders(today = d(10, 1))
        assertEquals(setOf("Insurance" to 7, "Streaming" to 3), reminders.map { it.occurrence.bill.name to it.daysBefore }.toSet())
        assertTrue(books.bills.reminders(today = d(10, 2)).none { it.occurrence.bill.name == "Insurance" })
    }

    @Test
    fun `subscriptions show their yearly cost`() {
        books.bills.create(BillDraft(BillKind.BILL, "Streaming", cad("16.99"), chequing.id, Recurrence.MONTHLY, d(1, 20), isSubscription = true))
        books.bills.create(BillDraft(BillKind.BILL, "Antivirus", cad("59.99"), chequing.id, Recurrence.ANNUAL, d(3, 1), isSubscription = true))
        val subs = books.bills.subscriptions(today = d(10, 1))
        assertEquals(listOf(cad("203.88"), cad("59.99")), subs.map { it.annualCost })
        assertEquals(d(10, 20), subs.first().nextDue)
        assertEquals(LocalDate(2027, 3, 1), subs.last().nextDue)
    }

    @Test
    fun `last business day bills and skipping`() {
        val pay = books.bills.create(
            BillDraft(BillKind.INCOME, "Pension", cad("1200.00"), chequing.id, Recurrence(Frequency.MONTHLY, monthDay = MonthDay.LAST_BUSINESS_DAY), d(1, 1)),
        )
        assertEquals(listOf(d(10, 30), d(11, 30), d(12, 31)), books.bills.occurrences(d(10, 1), d(12, 31)).map { it.dueDate })
        books.bills.skip(pay.id, d(11, 30))
        assertEquals(OccurrenceStatus.SKIPPED, books.bills.occurrences(d(11, 1), d(11, 30)).single().status)
    }

    @Test
    fun `calendar export`() {
        books.bills.create(BillDraft(BillKind.BILL, "Rent; main", cad("1450.00"), chequing.id, Recurrence.MONTHLY, d(10, 1)))
        val ics = books.bills.iCalendar(d(10, 1), d(11, 30)) { it.bill.name }
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertTrue("DTSTART;VALUE=DATE:20261101" in ics)
        assertTrue("SUMMARY:Rent\\; main" in ics)
    }

    @Test
    fun `validation`() {
        val e = runCatching { books.bills.create(BillDraft(BillKind.TRANSFER, "X", cad("1"), chequing.id, Recurrence.MONTHLY, d(1, 1))) }
        assertEquals("error.transferSameAccount", (e.exceptionOrNull() as ValidationException).key)
        val usd = runCatching { books.bills.create(BillDraft(BillKind.BILL, "X", Money.parse("1", Currency.USD), chequing.id, Recurrence.MONTHLY, d(1, 1))) }
        assertEquals("error.currencyMismatch", (usd.exceptionOrNull() as ValidationException).key)
    }
}
