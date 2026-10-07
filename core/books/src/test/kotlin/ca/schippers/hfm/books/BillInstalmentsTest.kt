package ca.schippers.hfm.books

import ca.schippers.hfm.calc.schedule.Recurrence
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.OcrLine
import ca.schippers.hfm.ocr.OcrResult
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

/** BILL-23 to BILL-25: instalments on set dates, rolled over to next year, read from a tax bill. */
class BillInstalmentsTest {

    @TempDir
    lateinit var temp: Path

    private lateinit var books: Books
    private lateinit var group: String
    private lateinit var chequing: Account
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)

    @BeforeEach
    fun setUp() {
        val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
        books = Books(store.create(temp.resolve("I.hfm"), "I", "perry", "Perry", "admin-pass".toCharArray()).session)
        group = books.groups().single().id
        chequing = books.accounts.create(AccountDraft(group, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("9000.00"), d(2026, 1, 1)))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun taxes() = books.bills.create(
        BillDraft(
            BillKind.BILL, "Property taxes", cad("4207.00"), chequing.id, Recurrence.INSTALMENTS, d(2026, 2, 27), payeeName = "Ville de Québec",
            reminderDays = listOf(7), type = BillType.HOME, categoryKey = "home.essential", subcategoryKey = "home.essential.property_taxes",
        ),
    )

    /** Three versements, Quebec style. 2026-02-27 is a Friday; a year later it is a Saturday. */
    private val year2026 = listOf(
        BillInstalment(d(2026, 2, 27), cad("1402.33")),
        BillInstalment(d(2026, 6, 30), cad("1402.33")),
        BillInstalment(d(2026, 9, 29), cad("1402.34")),
    )

    private fun inYear(bill: Bill, year: Int) = books.bills.occurrences(d(year, 1, 1), d(year, 12, 31), setOf(bill.id))

    @Test
    fun `a statement's instalments are due dates of the bill, numbered, with reminders (BILL-23)`() {
        val bill = taxes()
        assertTrue(inYear(bill, 2026).isEmpty(), "instalments on set dates have no dates of their own")
        val s = books.bills.recordStatement(bill.id, StatementDraft(d(2026, 1, 1), statementNumber = "2026-0001", instalments = year2026.reversed()))
        assertEquals(d(2026, 2, 27), s.dueDate, "the first instalment is the statement's due date")
        assertEquals(cad("4207.00"), s.amount, "their total is its amount")
        assertEquals(year2026, s.instalments)

        val due = inYear(bill, 2026)
        assertEquals(year2026.map { it.dueDate }, due.map { it.dueDate })
        assertEquals(year2026.map { it.amount }, due.map { it.amount })
        assertEquals(listOf(1, 2, 3), due.map { it.instalment })
        assertTrue(due.all { it.instalments == 3 && it.amountKnown && !it.estimated })
        // Reminded like any bill, by its lead time.
        assertEquals(7, books.bills.reminders(d(2026, 6, 23)).single { it.occurrence.dueDate == d(2026, 6, 30) }.daysBefore)
        // Paid one at a time.
        books.bills.markPaid(bill.id, d(2026, 2, 27), d(2026, 2, 27))
        assertEquals(listOf(OccurrenceStatus.PAID, OccurrenceStatus.DUE, OccurrenceStatus.DUE), inYear(bill, 2026).map { it.status })
        assertTrue(books.billLists.lists().suggestsInstalments("home.essential.property_taxes"))
        assertTrue(books.billLists.lists().suggestsInstalments("business.facilities.property_taxes"))
        assertFalse(books.billLists.lists().suggestsInstalments("home.essential.rent"))
    }

    @Test
    fun `next year's instalments are proposed from this year's until the new statement replaces them (BILL-24)`() {
        val bill = taxes()
        books.bills.recordStatement(bill.id, StatementDraft(d(2026, 1, 1), instalments = year2026))
        val proposed = inYear(bill, 2027)
        // The same dates moved to a business day (Saturday 2027-02-27 to Monday 2027-03-01), this year's amounts.
        assertEquals(listOf(d(2027, 3, 1), d(2027, 6, 30), d(2027, 9, 29)), proposed.map { it.dueDate })
        assertEquals(year2026.map { it.amount }, proposed.map { it.amount })
        assertTrue(proposed.all { it.estimated && !it.amountKnown })
        assertEquals(listOf(1, 2, 3), proposed.map { it.instalment })
        assertEquals(3, inYear(bill, 2028).size, "and every year after")

        // An estimate paid in part, then next year's tax bill arrives with its own dates and amounts.
        books.bills.markPaid(bill.id, d(2027, 3, 1), d(2027, 2, 20), cad("500.00"))
        val year2027 = listOf(
            BillInstalment(d(2027, 3, 4), cad("1450.00")),
            BillInstalment(d(2027, 6, 3), cad("1450.00")),
            BillInstalment(d(2027, 9, 2), cad("1450.00")),
        )
        books.bills.recordStatement(bill.id, StatementDraft(d(2027, 1, 1), instalments = year2027))
        val actual = inYear(bill, 2027)
        assertEquals(year2027.map { it.dueDate }, actual.map { it.dueDate }, "the estimates are replaced")
        assertTrue(actual.none { it.estimated })
        assertEquals(cad("950.00"), actual.first().outstanding, "the payment made toward the estimate counts")
        assertEquals(listOf(d(2028, 3, 6), d(2028, 6, 5), d(2028, 9, 5)), inYear(bill, 2028).map { it.dueDate }, "2028 follows 2027's dates")
        assertTrue(inYear(bill, 2028).all { it.estimated && it.amount == cad("1450.00") })
        // 2026 is untouched.
        assertEquals(year2026.map { it.dueDate }, inYear(bill, 2026).map { it.dueDate })
    }

    @Test
    fun `interim and final bills each number their instalments, and roll over together (BILL-23, BILL-24)`() {
        val bill = taxes()
        books.bills.recordStatement(bill.id, StatementDraft(d(2026, 1, 1), statementNumber = "Interim", instalments = listOf(BillInstalment(d(2026, 2, 19), cad("1190.00")), BillInstalment(d(2026, 4, 16), cad("1190.00")))))
        // Before the final bill, only the interim instalments of 2026 are known; last year had none.
        assertEquals(2, inYear(bill, 2026).size)
        books.bills.recordStatement(bill.id, StatementDraft(d(2026, 5, 1), statementNumber = "Final", instalments = listOf(BillInstalment(d(2026, 6, 18), cad("1216.00")), BillInstalment(d(2026, 9, 17), cad("1216.00")))))
        val due = inYear(bill, 2026)
        assertEquals(listOf(1 to 2, 2 to 2, 1 to 2, 2 to 2), due.map { it.instalment to it.instalments })
        assertEquals(4, inYear(bill, 2027).count { it.estimated })
        // Next year's interim bill replaces its two estimates; the final ones stay proposed.
        books.bills.recordStatement(bill.id, StatementDraft(d(2027, 1, 1), instalments = listOf(BillInstalment(d(2027, 2, 18), cad("1240.00")), BillInstalment(d(2027, 4, 15), cad("1240.00")))))
        val next = inYear(bill, 2027)
        assertEquals(listOf(false, false, true, true), next.map { it.estimated })
        assertEquals(listOf(cad("1240.00"), cad("1240.00"), cad("1216.00"), cad("1216.00")), next.map { it.amount })
    }

    @Test
    fun `changing an instalment's date on the statement moves that due date (BILL-23)`() {
        val bill = taxes()
        val s = books.bills.recordStatement(bill.id, StatementDraft(d(2026, 1, 1), instalments = year2026))
        val changed = listOf(year2026[0], year2026[1].copy(dueDate = d(2026, 7, 2)), year2026[2])
        books.bills.recordStatement(bill.id, StatementDraft(d(2026, 1, 1), instalments = changed), s.id)
        assertEquals(changed.map { it.dueDate }, inYear(bill, 2026).map { it.dueDate })
        assertEquals(changed, books.bills.statements(bill.id).single().instalments)
    }

    @Test
    fun `a captured tax bill fills in its instalments, and proposes a bill paid in instalments (BILL-25)`() {
        val text = """
            Ville de Québec
            Compte de taxes municipales 2026
            Date du compte : 2026-02-02
            Total des taxes 4 207,00 $
            1er versement   échéance 2026-02-27   1 402,33 $
            2e versement    échéance 2026-06-30   1 402,33 $
            3e versement    échéance 2026-09-29   1 402,34 $
        """.trimIndent()
        val imported = books.documents.import(group, text.encodeToByteArray(), "taxes.jpg", "image/jpeg").document
        val doc = books.documents.recordText(imported.id, 1, OcrResult(text.lines().map { OcrLine(it, 0.97f) }, 10), "test", d(2026, 2, 10))
        assertEquals(year2026.map { it.dueDate }, books.documents.get(doc.id).draft?.instalments?.value?.map { it.dueDate }, "kept with the document")
        val proposal = books.documents.billProposal(doc.id)
        assertEquals(Recurrence.INSTALMENTS, proposal.bill?.recurrence)
        assertEquals(year2026, proposal.statement.instalments)
        assertEquals(d(2026, 2, 27), proposal.statement.dueDate)

        val bill = taxes()
        assertEquals(year2026, books.documents.statementFrom(books.documents.get(doc.id), bill).instalments)
        books.documents.fileWithBill(doc.id, bill.id)
        assertEquals(year2026.map { it.dueDate }, inYear(bill, 2026).map { it.dueDate })
    }
}
