package ca.schippers.hfm.books

import ca.schippers.hfm.calc.schedule.Recurrence
import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.domain.TaxFlag
import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.DocumentKind
import ca.schippers.hfm.ocr.MeterReadings
import ca.schippers.hfm.ocr.OcrLine
import ca.schippers.hfm.ocr.OcrResult
import ca.schippers.hfm.ocr.TaxName
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** BILL-13 to BILL-20: bill lists, account numbers, statements, meter readings, bills from captured documents, business bills. */
class BillStatementsTest {

    @TempDir
    lateinit var temp: Path

    private lateinit var books: Books
    private lateinit var group: String
    private lateinit var chequing: Account
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun d(m: Int, day: Int) = LocalDate(2026, m, day)
    private fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id
    private val today = LocalDate(2026, 10, 2)

    @BeforeEach
    fun setUp() {
        val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
        books = Books(store.create(temp.resolve("S.hfm"), "S", "perry", "Perry", "admin-pass".toCharArray()).session)
        group = books.groups().single().id
        chequing = books.accounts.create(AccountDraft(group, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("1000.00"), d(1, 1)))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private val electricity = "home.essential.electricity"

    private fun hydro(accountNumber: String? = "6 1234 5678 9") = books.bills.create(
        BillDraft(
            BillKind.BILL, "Hydro Ottawa", cad("130.00"), chequing.id, Recurrence.MONTHLY, d(1, 12), payeeName = "Hydro Ottawa",
            payeeAccountNumber = accountNumber, amountKind = AmountKind.VARIABLE, type = BillType.HOME, categoryKey = "home.essential", subcategoryKey = electricity,
        ),
    )

    private fun importBill(text: String): VaultDocument {
        val doc = books.documents.import(group, text.encodeToByteArray(), "bill.jpg", "image/jpeg").document
        return books.documents.recordText(doc.id, 1, OcrResult(text.lines().map { OcrLine(it, 0.97f) }, 10), "test", today)
    }

    @Test
    fun `the built-in lists are the owner's, in English and French, each subcategory with its spending category (BILL-13)`() {
        val lists = books.billLists.lists()
        assertEquals(5, lists.categories(BillType.HOME).size)
        assertEquals(6, lists.categories(BillType.BUSINESS).size)
        val sub = lists.subcategory(electricity)!!
        assertEquals("Utilities: Electricity", lists.label(sub, Language.ENGLISH))
        assertEquals("Services publics : Électricité", lists.label(sub, Language.FRENCH))
        assertEquals(cat("utilities.electricity"), sub.spendingCategoryId)
        assertEquals(MeterKind.ELECTRICITY, sub.meterKind)
        assertTrue(lists.isUtility(electricity))
        assertFalse(lists.isUtility("home.essential.rent"))
        assertEquals(cat("housing.condo_fees"), lists.subcategory("home.essential.condo_fees")!!.spendingCategoryId)
        assertEquals(cat("business.software"), lists.subcategory("business.technology.software_licenses")!!.spendingCategoryId)
        assertEquals("Other", lists.label(lists.subcategory("home.other.other")!!, Language.ENGLISH), "a heading the same as the name is not repeated")
        // Every built-in subcategory finds its spending category in a new household's tree.
        assertEquals(emptyList(), lists.subcategories.filter { it.spendingCategoryId == null }.map { it.key })
        assertEquals(TaxFlag.BUSINESS, books.categories.list().first { it.systemKey == "business.rent" }.taxFlag)
    }

    @Test
    fun `the lists are renamed, hidden, added to and restored (BILL-14)`() {
        val l = books.billLists
        l.rename(electricity, "Power", "Courant")
        l.setHidden("home.essential.trash", true)
        l.setSpendingCategory(electricity, cat("utilities"))
        val added = l.addSubcategory("home.essential", "home.essential.utilities", "Propane", "", cat("utilities.heating"))
        var lists = l.lists()
        assertEquals("Utilities: Power", lists.label(lists.subcategory(electricity)!!, Language.ENGLISH))
        assertTrue(lists.subcategory(electricity)!!.changed)
        assertFalse(lists.subcategories("home.essential").any { it.key == "home.essential.trash" }, "hidden from the choices")
        assertTrue(lists.subcategories("home.essential", includeHidden = true).any { it.key == "home.essential.trash" })
        assertEquals("Propane", lists.subcategory(added.key)!!.nameFr, "a blank name takes the other language's")
        assertEquals(cat("utilities.heating"), lists.subcategory(added.key)!!.spendingCategoryId)
        val mine = l.addCategory(BillType.BUSINESS, "Farm", "Ferme")
        assertEquals(BillType.BUSINESS, l.lists().category(mine.key)!!.type)

        l.restore(electricity)
        lists = l.lists()
        assertEquals("Electricity", lists.subcategory(electricity)!!.nameEn)
        assertEquals(cat("utilities.electricity"), lists.subcategory(electricity)!!.spendingCategoryId)
        l.restoreAll()
        lists = l.lists()
        assertFalse(lists.subcategory("home.essential.trash")!!.hidden)
        assertNotNull(lists.subcategory(added.key), "what the household added stays")
        assertFailsWith<ValidationException> { l.rename("nothing.here", "x", "y") }
    }

    @Test
    fun `a classified bill pays into its subcategory's spending category, and must be consistent (BILL-13)`() {
        val bill = hydro()
        assertEquals(cat("utilities.electricity"), bill.categoryId)
        assertEquals(BillType.HOME, bill.type)
        assertEquals(electricity, bill.subcategoryKey)
        assertFailsWith<ValidationException> {
            books.bills.create(BillDraft(BillKind.BILL, "X", cad("1.00"), chequing.id, Recurrence.MONTHLY, d(1, 1), type = BillType.BUSINESS, categoryKey = "home.essential", subcategoryKey = electricity))
        }
        assertFailsWith<ValidationException> {
            books.bills.create(BillDraft(BillKind.BILL, "X", cad("1.00"), chequing.id, Recurrence.MONTHLY, d(1, 1), type = BillType.HOME, categoryKey = "home.transportation", subcategoryKey = electricity))
        }
    }

    @Test
    fun `the account number is masked and shown in full only with the password (BILL-15)`() {
        val bill = hydro()
        assertEquals("•••• 6789", bill.payeeAccountMasked)
        assertFailsWith<AccessDeniedException> { books.bills.revealAccountNumber(bill.id, "wrong".toCharArray()) }
        assertEquals("6 1234 5678 9", books.bills.revealAccountNumber(bill.id, "admin-pass".toCharArray()))
        books.bills.update(bill.copy(name = "Hydro"))
        assertEquals("6 1234 5678 9", books.bills.revealAccountNumber(bill.id, "admin-pass".toCharArray()), "saving the bill keeps the number")
        books.bills.update(books.bills.get(bill.id), newAccountNumber = "9999 0000")
        assertEquals("9999 0000", books.bills.revealAccountNumber(bill.id, "admin-pass".toCharArray()))
        books.bills.update(books.bills.get(bill.id), newAccountNumber = "")
        assertNull(books.bills.get(bill.id).payeeAccountMasked)
    }

    @Test
    fun `a statement's due date becomes that due date, with its amount, and can be paid (BILL-16)`() {
        val bill = hydro(null)
        val s = books.bills.recordStatement(bill.id, StatementDraft(d(10, 15), cad("142.37"), "2026-0914", d(9, 24)))
        val october = books.bills.occurrences(d(10, 1), d(10, 31), setOf(bill.id))
        assertEquals(listOf(d(10, 15)), october.map { it.dueDate }, "the 12th moved to the 15th")
        assertEquals(cad("142.37"), october.single().amount)
        assertEquals(listOf(d(11, 12)), books.bills.occurrences(d(11, 1), d(11, 30), setOf(bill.id)).map { it.dueDate }, "next month is as scheduled")
        assertEquals(listOf(s.id), books.bills.statements(bill.id).map { it.id })
        assertEquals("2026-0914", s.statementNumber)

        // Corrected: another due date moves the same occurrence again.
        books.bills.recordStatement(bill.id, StatementDraft(d(10, 16), cad("142.37"), "2026-0914", d(9, 24)), s.id)
        assertEquals(listOf(d(10, 16)), books.bills.occurrences(d(10, 1), d(10, 31), setOf(bill.id)).map { it.dueDate })
        assertEquals(1, books.bills.statements(bill.id).size)

        val due = books.bills.occurrenceOf(books.bills.statements(bill.id).single())!!
        books.bills.markPaid(bill.id, due.dueDate, d(10, 14))
        val paid = books.bills.occurrences(d(10, 1), d(10, 31), setOf(bill.id)).single()
        assertEquals(OccurrenceStatus.PAID, paid.status)
        assertEquals(cad("-142.37"), books.transactions.get(paid.transactionId!!).amount)

        // A second copy of a paid statement adds no due date.
        books.bills.recordStatement(bill.id, StatementDraft(d(10, 20), cad("142.37")))
        assertEquals(1, books.bills.occurrences(d(10, 1), d(10, 31), setOf(bill.id)).size)
    }

    @Test
    fun `a utility statement adds its readings to the bill's meter once (BILL-17)`() {
        val bill = hydro()
        val meter = books.utilities.saveMeter(UtilityMeter("", group, "House", MeterKind.ELECTRICITY))
        books.utilities.linkBill(bill.id, meter.id)
        assertEquals(meter.id, books.utilities.meterFor(bill.id)?.id)
        books.utilities.addReading(meter.id, d(8, 12), BigDecimal(45678))
        val readings = MeterReadings(BigDecimal(45678), d(8, 12), BigDecimal(46321), d(9, 11))
        val s = books.bills.recordStatement(bill.id, StatementDraft(d(10, 12), cad("138.91"), readings = readings))
        assertEquals(meter.id, s.meterId)
        assertEquals(BigDecimal(643), s.readings.used, "the amount used, from the readings")
        assertEquals(listOf(d(8, 12), d(9, 11)), books.utilities.meter(meter.id).readings.map { it.date })
        books.bills.recordStatement(bill.id, StatementDraft(d(10, 12), cad("138.91"), readings = readings), s.id)
        assertEquals(2, books.utilities.meter(meter.id).readings.size, "no duplicates")

        // Another meter: the bill moves to it.
        val other = books.utilities.saveMeter(UtilityMeter("", group, "Cottage", MeterKind.ELECTRICITY))
        books.utilities.linkBill(bill.id, other.id)
        assertNull(books.utilities.meter(meter.id).billId)
        books.utilities.linkBill(bill.id, null)
        assertNull(books.utilities.meterFor(bill.id))
    }

    @Test
    fun `a captured bill that matches no bill creates one, with its statement (BILL-18)`() {
        val meter = books.utilities.saveMeter(UtilityMeter("", group, "House", MeterKind.ELECTRICITY))
        val doc = importBill(
            """
            Hydro Ottawa
            Your electricity bill
            Account number: 6 1234 5678 9
            Statement number: 2026-0914-1187
            Issue date: Sep 14, 2026
            Previous reading (Aug 12, 2026): 45 678
            Current reading (Sep 11, 2026): 46 321
            Amount due ${'$'}138.91
            Due date: Oct 6, 2026
            """.trimIndent(),
        )
        assertEquals(DocumentKind.BILL, doc.kind)
        assertNull(books.documents.billFor(doc.id))
        val proposal = books.documents.billProposal(doc.id)
        val draft = proposal.bill!!
        assertEquals("Hydro Ottawa", draft.name)
        assertEquals("6 1234 5678 9", draft.payeeAccountNumber)
        assertEquals(cad("138.91"), draft.amount)
        assertEquals(d(10, 6), draft.startDate)
        assertEquals(electricity, draft.subcategoryKey, "guessed from the company's name")
        assertEquals(cat("utilities.electricity"), draft.categoryId)
        assertEquals("2026-0914-1187", proposal.statement.statementNumber)
        assertEquals(d(9, 14), proposal.statement.issuedDate)

        val bill = books.documents.createBillFrom(doc.id, draft, proposal.statement.copy(meterId = meter.id))
        assertTrue(books.bills.list().any { it.id == bill.id }, "the bill is in the Bills list")
        assertEquals(DocumentStatus.FILED, books.documents.get(doc.id).status)
        val statement = books.bills.statements(bill.id).single()
        assertEquals(doc.id, statement.documentId)
        assertEquals(cad("138.91"), books.bills.occurrences(d(10, 6), d(10, 6), setOf(bill.id)).single().amount)
        assertEquals(listOf(d(8, 12), d(9, 11)), books.utilities.meter(meter.id).readings.map { it.date })

        // Next month's bill from the same company matches it (BILL-03) by its account number.
        val next = importBill("Hydro Ottawa\nAccount number: 6 1234 5678 9\nAmount due ${'$'}120.00\nDue date: Nov 5, 2026")
        assertEquals(bill.id, books.documents.billFor(next.id)?.id)
        assertEquals(d(11, 5), books.documents.fileWithBill(next.id, bill.id))
        assertEquals(listOf(d(11, 5)), books.bills.occurrences(d(11, 1), d(11, 30), setOf(bill.id)).map { it.dueDate })
        assertEquals(2, books.bills.statements(bill.id).size)
        // A later bill from the same company is classified like the earlier one.
        assertEquals(electricity, books.billLists.guess("Hydro Ottawa Inc.")?.key)
    }

    @Test
    fun `a captured bill can be attached to a bill chosen by the user (BILL-18)`() {
        val phone = books.bills.create(BillDraft(BillKind.BILL, "Cell phone", cad("65.00"), chequing.id, Recurrence.MONTHLY, d(1, 20)))
        val doc = importBill("Koodo\nYour bill\nAmount due ${'$'}65.00\nDue date: Oct 20, 2026")
        assertNull(books.documents.billFor(doc.id))
        assertEquals("home.essential.cell_phone", books.documents.billProposal(doc.id).bill?.subcategoryKey)
        books.documents.fileWithBill(doc.id, phone.id)
        assertEquals(1, books.bills.statements(phone.id).size)
        assertEquals(cad("65.00"), books.bills.occurrences(d(10, 20), d(10, 20), setOf(phone.id)).single().amount)
    }

    @Test
    fun `a business bill's payments are its person's business expenses, with the sales taxes paid (BILL-20)`() {
        val sam = books.members.create("Sam", MemberKind.ADULT)
        assertFailsWith<ValidationException>("a Business bill names its person") {
            books.bills.create(BillDraft(BillKind.BILL, "X", cad("1.00"), chequing.id, Recurrence.MONTHLY, d(1, 1), type = BillType.BUSINESS, categoryKey = "business.technology"))
        }
        val software = books.bills.create(
            BillDraft(
                BillKind.BILL, "Accounting software", cad("33.90"), chequing.id, Recurrence.MONTHLY, d(9, 5), payeeName = "LedgerCloud",
                type = BillType.BUSINESS, categoryKey = "business.technology", subcategoryKey = "business.technology.software_licenses", memberId = sam.id,
            ),
        )
        assertEquals(cat("business.software"), software.categoryId)
        // September's statement, with its HST, then paid.
        val doc = importBill("LedgerCloud\nInvoice\nSubtotal ${'$'}30.00\nHST ${'$'}3.90\nAmount due ${'$'}33.90\nDue date: Sep 5, 2026")
        books.documents.fileWithBill(doc.id, software.id)
        books.bills.markPaid(software.id, d(9, 5), d(9, 5))
        // October's paid with a transaction recorded earlier, in no category at all.
        val earlier = books.transactions.create(TransactionDraft(chequing.id, d(10, 5), cad("-33.90"), "LedgerCloud"))
        books.bills.markPaid(software.id, d(10, 5), d(10, 5), existingTransactionId = earlier.id)

        val person = books.taxPackage.build(2026).people.first { it.memberId == sam.id }
        assertEquals(cad("67.80"), person.total(PackageItem.BUSINESS_EXPENSES))
        assertEquals(cad("3.90"), person.total(PackageItem.SALES_TAX_PAID))
        assertEquals(TaxName.HST.name, person.lines.first { it.item == PackageItem.SALES_TAX_PAID }.detail)
        // A home bill's payment is not a business expense.
        val home = hydro(null)
        books.bills.markPaid(home.id, d(9, 12), d(9, 12), cad("100.00"))
        assertEquals(cad("67.80"), books.taxPackage.build(2026).people.first { it.memberId == sam.id }.total(PackageItem.BUSINESS_EXPENSES))
    }
}
