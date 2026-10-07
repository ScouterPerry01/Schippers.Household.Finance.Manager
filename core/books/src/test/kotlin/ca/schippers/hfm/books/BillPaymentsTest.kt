package ca.schippers.hfm.books

import ca.schippers.hfm.calc.schedule.Recurrence
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.MemberKind
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** BILL-21, BILL-22: bills paid in part, each payment linked to its due date, undone one at a time. */
class BillPaymentsTest {

    @TempDir
    lateinit var temp: Path

    private lateinit var books: Books
    private lateinit var group: String
    private lateinit var chequing: Account
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun d(m: Int, day: Int) = LocalDate(2026, m, day)

    @BeforeEach
    fun setUp() {
        val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
        books = Books(store.create(temp.resolve("P.hfm"), "P", "perry", "Perry", "admin-pass".toCharArray()).session)
        group = books.groups().single().id
        chequing = books.accounts.create(AccountDraft(group, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("3000.00"), d(1, 1)))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun rent() = books.bills.create(BillDraft(BillKind.BILL, "Rent", cad("1450.00"), chequing.id, Recurrence.MONTHLY, d(1, 1), reminderDays = listOf(7)))

    private fun october(bill: Bill) = books.bills.occurrences(d(10, 1), d(10, 1), setOf(bill.id)).single()

    @Test
    fun `paying less leaves the rest due on the same due date, everywhere (BILL-22)`() {
        val bill = rent()
        val part = books.bills.markPaid(bill.id, d(10, 1), d(9, 28), cad("500.00"))
        assertEquals(OccurrenceStatus.DUE, part.status)
        assertEquals(cad("1450.00"), part.amount, "the amount due stays")
        assertEquals(cad("500.00"), part.paidSoFar)
        assertEquals(cad("950.00"), part.outstanding)
        val payment = part.payments.single()
        assertEquals(d(9, 28), payment.paidDate)
        assertEquals(cad("-500.00"), books.transactions.get(payment.transactionId!!).amount, "each payment is a transaction")
        assertNull(part.transactionId, "not paid yet")

        // The agenda, reminders and forecast count what is still due.
        val agenda = books.bills.agenda(today = d(9, 28), days = 10)
        assertEquals(cad("950.00"), agenda.upcoming.single { it.bill.id == bill.id && it.dueDate == d(10, 1) }.outstanding)
        assertEquals(cad("950.00"), books.bills.reminders(d(9, 24)).single { it.occurrence.bill.id == bill.id && it.occurrence.dueDate == d(10, 1) }.occurrence.shownAmount)
        val forecast = books.bills.forecast(today = d(9, 28), days = 10).single { it.account.id == chequing.id }
        val at = forecast.points.indexOfFirst { it.occurrence?.dueDate == d(10, 1) }
        assertEquals(cad("-950.00"), forecast.points[at].balance - forecast.points[at - 1].balance)
        assertEquals(cad("-950.00"), october(bill).signedAmount)

        // The rest pays it in full; the due date then points at the last payment.
        val full = books.bills.markPaid(bill.id, d(10, 1), d(10, 1))
        assertEquals(OccurrenceStatus.PAID, full.status)
        assertEquals(2, full.payments.size)
        assertEquals(cad("950.00"), full.payments.last().amount, "by default the outstanding amount is paid")
        assertEquals(full.payments.last().transactionId, full.transactionId)
        assertEquals(cad("1450.00"), full.paidSoFar)
        assertEquals(Money.zero(Currency.CAD), full.outstanding)
        assertEquals(listOf(cad("500.00"), cad("950.00")), books.bills.payments(bill.id).map { it.amount })
    }

    @Test
    fun `payments within a cent of the amount due pay it in full, and paying more asks first (BILL-22)`() {
        val bill = rent()
        assertEquals(OccurrenceStatus.PAID, books.bills.markPaid(bill.id, d(10, 1), d(10, 1), cad("1449.99")).status)
        books.bills.markPaid(bill.id, d(11, 1), d(11, 1), cad("1000.00"))
        val e = assertFailsWith<ValidationException> { books.bills.markPaid(bill.id, d(11, 1), d(11, 1), cad("500.00")) }
        assertEquals("error.billOverpaid", e.key)
        assertEquals(1, books.bills.occurrences(d(11, 1), d(11, 1)).single().payments.size, "nothing was recorded")
        val over = books.bills.markPaid(bill.id, d(11, 1), d(11, 1), cad("500.00"), confirmOverpay = true)
        assertEquals(OccurrenceStatus.PAID, over.status)
        assertEquals(cad("1500.00"), over.paidSoFar)
        assertFailsWith<ValidationException> { books.bills.markPaid(bill.id, d(11, 1), d(11, 1), cad("1.00")) }
    }

    @Test
    fun `undo removes one payment at a time, and its transaction (BILL-22)`() {
        val bill = rent()
        val first = books.bills.markPaid(bill.id, d(10, 1), d(9, 20), cad("700.00")).payments.single()
        val paid = books.bills.markPaid(bill.id, d(10, 1), d(10, 1))
        val second = paid.payments.last()
        books.bills.unmarkPaid(bill.id, d(10, 1), deleteTransaction = true)
        val back = october(bill)
        assertEquals(OccurrenceStatus.DUE, back.status)
        assertEquals(listOf(first.id), back.payments.map { it.id })
        assertEquals(cad("750.00"), back.outstanding)
        assertFailsWith<Exception> { books.transactions.get(second.transactionId!!) }
        assertEquals(cad("-700.00"), books.transactions.get(first.transactionId!!).amount, "the other payment stays")
        // A chosen payment, keeping its transaction.
        books.bills.unmarkPaid(bill.id, d(10, 1), deleteTransaction = false, paymentId = first.id)
        assertEquals(cad("1450.00"), october(bill).outstanding)
        assertTrue(october(bill).payments.isEmpty())
        assertEquals(cad("-700.00"), books.transactions.get(first.transactionId).amount)
    }

    @Test
    fun `a variable bill with no amount set is paid in full by its payment, which gives its amount`() {
        val hydro = books.bills.create(BillDraft(BillKind.BILL, "Hydro", cad("130.00"), chequing.id, Recurrence.MONTHLY, d(1, 12), amountKind = AmountKind.VARIABLE))
        val paid = books.bills.markPaid(hydro.id, d(10, 12), d(10, 12), cad("98.40"))
        assertEquals(OccurrenceStatus.PAID, paid.status)
        assertEquals(cad("98.40"), paid.amount)
        // With the bill's amount set, a smaller payment is a part payment.
        books.bills.setAmount(hydro.id, d(11, 12), cad("142.37"))
        val part = books.bills.markPaid(hydro.id, d(11, 12), d(11, 12), cad("100.00"))
        assertEquals(OccurrenceStatus.DUE, part.status)
        assertEquals(cad("42.37"), part.outstanding)
    }

    @Test
    fun `a statement moving a due date paid in part takes its payments along (BILL-22, BILL-16)`() {
        val bill = rent()
        books.bills.markPaid(bill.id, d(10, 1), d(9, 25), cad("400.00"))
        books.bills.recordStatement(bill.id, StatementDraft(d(10, 3), cad("1450.00")))
        val moved = books.bills.occurrences(d(10, 1), d(10, 31), setOf(bill.id)).single()
        assertEquals(d(10, 3), moved.dueDate)
        assertEquals(cad("1050.00"), moved.outstanding)
        assertEquals(d(10, 3), moved.payments.single().dueDate)
    }

    @Test
    fun `every part payment of a business bill is a business expense (BILL-20, BILL-22)`() {
        val sam = books.members.create("Sam", MemberKind.ADULT)
        val office = books.bills.create(
            BillDraft(
                BillKind.BILL, "Office rent", cad("600.00"), chequing.id, Recurrence.MONTHLY, d(1, 1), type = BillType.BUSINESS,
                categoryKey = "business.facilities", memberId = sam.id,
            ),
        )
        books.bills.markPaid(office.id, d(10, 1), d(10, 1), cad("200.00"))
        books.bills.markPaid(office.id, d(10, 1), d(10, 5), cad("400.00"))
        val person = books.taxPackage.build(2026).people.first { it.memberId == sam.id }
        assertEquals(cad("600.00"), person.total(PackageItem.BUSINESS_EXPENSES))
    }
}
