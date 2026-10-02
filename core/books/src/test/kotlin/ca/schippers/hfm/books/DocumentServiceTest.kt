package ca.schippers.hfm.books

import ca.schippers.hfm.calc.schedule.Recurrence
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.DocumentKind
import ca.schippers.hfm.ocr.OcrLine
import ca.schippers.hfm.ocr.OcrResult
import ca.schippers.hfm.ocr.TaxName
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentServiceTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var group: String
    private lateinit var visa: Account
    private fun d(m: Int, day: Int) = LocalDate(2026, m, day)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private val today = LocalDate(2026, 10, 2)

    private val receipt = """
        IGA Extra Famille Jodoin
        SOUS-TOTAL 39,84
        TPS 0,77
        TVQ 1,55
        TOTAL 42,16
        2026/09/28 14:32
    """.trimIndent()

    private fun ocr(text: String) = OcrResult(text.lines().map { OcrLine(it, 0.97f) }, 700)

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("D.hfm"), "D", "perry", "Perry", "pw".toCharArray()).session)
        group = books.groups().single().id
        visa = books.accounts.create(AccountDraft(group, "Visa", AccountType.CREDIT_CARD, Currency.CAD, cad("0"), d(1, 1)))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun importReceipt(bytes: ByteArray = "photo-1".encodeToByteArray(), text: String = receipt): VaultDocument {
        val doc = books.documents.import(group, bytes, "IMG_2041.jpg", "image/jpeg").document
        return books.documents.recordText(doc.id, 1, ocr(text), "test", today)
    }

    @Test
    fun `import, read, and the inbox`() {
        val doc = importReceipt()
        assertEquals(DocumentStatus.INBOX, doc.status)
        assertEquals("IGA Extra Famille Jodoin", doc.merchant)
        assertEquals(cad("42.16"), doc.amount)
        assertEquals(d(9, 28), doc.date)
        assertEquals(DocumentKind.RECEIPT, doc.kind)
        assertEquals(listOf(TaxName.GST, TaxName.QST), doc.draft!!.taxes.map { it.first }, "the extraction is stored with the document")
        assertTrue(doc.draft.total!!.confidence > 0.9f)
        assertContentEquals("photo-1".encodeToByteArray(), books.documents.content(doc.id))
        assertEquals(listOf(doc.id), books.documents.inbox().map { it.id })
        assertEquals(1, books.documents.inboxCount())

        val again = books.documents.import(group, "photo-1".encodeToByteArray(), "copy.jpg", "image/jpeg")
        assertTrue(again.alreadyInVault, "OCR-10: the same file is not stored twice")
        assertEquals(doc.id, again.document.id)
    }

    @Test
    fun `search by text, date and amount`() {
        val doc = importReceipt()
        assertEquals(listOf(doc.id), books.documents.search(DocumentQuery(text = "jodoin")).map { it.id })
        assertTrue(books.documents.search(DocumentQuery(text = "metro")).isEmpty())
        assertEquals(1, books.documents.search(DocumentQuery(from = d(9, 1), to = d(9, 30), minAmount = cad("40"), maxAmount = cad("50"))).size)
        assertTrue(books.documents.search(DocumentQuery(minAmount = cad("100"))).isEmpty())
        assertTrue(books.documents.search(DocumentQuery(text = "50%")).isEmpty(), "wildcards are matched literally")
    }

    @Test
    fun `a receipt is matched to the card transaction and filed with it`() {
        val doc = importReceipt()
        val txn = books.transactions.create(TransactionDraft(visa.id, d(9, 29), cad("-42.16"), "IGA"))
        books.transactions.create(TransactionDraft(visa.id, d(9, 29), cad("-12.00"), "Tim Hortons"))
        val matches = books.documents.matches(doc.id)
        assertEquals(listOf(txn.id), matches.map { it.transaction.id })
        assertEquals(1, matches.single().daysApart)

        books.documents.fileWithTransaction(doc.id, txn.id)
        assertEquals(DocumentStatus.FILED, books.documents.get(doc.id).status)
        assertEquals(listOf(doc.id), books.documents.documentsFor(DocumentEntity.TRANSACTION, txn.id).map { it.id })
        assertTrue(books.documents.inbox().isEmpty())
        assertTrue(books.documents.matches(doc.id).isEmpty(), "already attached")
    }

    @Test
    fun `a receipt can create its own transaction`() {
        val doc = importReceipt()
        val txn = books.documents.fileAsTransaction(doc.id, TransactionDraft(visa.id, doc.date!!, -doc.amount!!, doc.merchant))
        assertEquals(cad("-42.16"), txn.amount)
        assertEquals(DocumentEntity.TRANSACTION to txn.id, books.documents.get(doc.id).links.single().let { it.entity to it.entityId })
    }

    @Test
    fun `a captured bill updates its bill (BILL-03)`() {
        val chequing = books.accounts.create(AccountDraft(group, "Chèques", AccountType.CHEQUING, Currency.CAD, cad("1000"), d(1, 1)))
        val hydro = books.bills.create(
            BillDraft(BillKind.BILL, "Électricité", cad("130"), chequing.id, Recurrence.MONTHLY, d(1, 6), payeeName = "Hydro-Québec", amountKind = AmountKind.VARIABLE),
        )
        val doc = importReceipt(
            "bill".encodeToByteArray(),
            "Hydro-Québec\nDate de facturation : 15 septembre 2026\nMontant à payer 142,37 \$\nDate d'échéance : 2026-10-06",
        )
        assertEquals(DocumentKind.BILL, doc.kind)
        assertEquals(hydro.id, books.documents.billFor(doc.id)?.id)
        assertEquals(d(10, 6), books.documents.fileWithBill(doc.id, hydro.id))
        val occurrence = books.bills.occurrences(d(10, 1), d(10, 31), setOf(hydro.id)).single()
        assertEquals(cad("142.37"), occurrence.amount)
        assertTrue(occurrence.amountKnown)
        assertEquals(listOf(doc.id), books.documents.documentsFor(DocumentEntity.BILL, hydro.id).map { it.id })
    }

    @Test
    fun `the same receipt photographed twice is flagged (OCR-10)`() {
        val first = importReceipt("photo-1".encodeToByteArray())
        val second = importReceipt("photo-2, another angle".encodeToByteArray(), receipt.replace("IGA Extra Famille Jodoin", "IGA Extra"))
        assertEquals(listOf(first.id), books.documents.duplicates(second.id).map { it.document.id })
        assertFalse(books.documents.duplicates(second.id).single().identical)
        val other = importReceipt("photo-3".encodeToByteArray(), receipt.replace("IGA Extra Famille Jodoin", "Metro"))
        assertTrue(books.documents.duplicates(other.id).isEmpty(), "another store on the same day for the same amount")
    }

    @Test
    fun `details, retention and deletion`() {
        val old = importReceipt()
        books.documents.update(old.id, DocumentDetails("Épicerie", DocumentKind.RECEIPT, LocalDate(2019, 5, 4), "IGA", cad("42.16"), false, null))
        books.documents.setStatus(old.id, DocumentStatus.FILED)
        assertEquals(listOf(old.id), books.documents.discardable(today).map { it.id }, "older than six years")
        books.documents.update(old.id, DocumentDetails("Épicerie", DocumentKind.RECEIPT, LocalDate(2019, 5, 4), "IGA", cad("42.16"), true, "Garantie"))
        assertTrue(books.documents.discardable(today).isEmpty(), "kept on purpose")
        assertEquals("Épicerie", books.documents.get(old.id).label)

        books.documents.delete(old.id)
        assertTrue(books.documents.search(DocumentQuery()).isEmpty())
    }

    @Test
    fun `merchant names`() {
        assertTrue(DocumentService.similarNames("IGA Extra Famille Jodoin", "IGA"))
        assertTrue(DocumentService.similarNames("HYDRO-QUÉBEC", "Hydro-Québec"))
        assertFalse(DocumentService.similarNames("Metro", "IGA"))
    }
}
