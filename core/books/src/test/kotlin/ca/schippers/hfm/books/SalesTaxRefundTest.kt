package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.TaxFlag
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** CAT-05, TX-04 and TX-05: tax flags on the default categories, sales tax per transaction, refunds linked to their purchase. */
class SalesTaxRefundTest {

    @TempDir
    lateinit var temp: Path

    private lateinit var books: Books
    private lateinit var visa: Account
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun cat(key: String) = books.categories.list().first { it.systemKey == key }

    @BeforeEach
    fun setUp() {
        books = Books(HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING).create(temp.resolve("T.hfm"), "T", "perry", "Perry", "password1".toCharArray()).session)
        visa = books.accounts.create(AccountDraft(books.groups().single().id, "Visa", AccountType.CREDIT_CARD, Currency.CAD, cad("0"), LocalDate(2026, 1, 1)))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    @Test
    fun `default categories carry their tax flags, moving and employment expenses included`() {
        assertEquals(TaxFlag.MOVING, cat("housing.moving").taxFlag)
        assertEquals(TaxFlag.EMPLOYMENT, cat("work.union_dues").taxFlag)
        assertEquals(TaxFlag.MEDICAL, cat("health.dental").taxFlag)
        assertEquals(TaxFlag.CHARITABLE, cat("gifts.charity").taxFlag)
        assertEquals(TaxFlag.CHILD_CARE, cat("children.childcare").taxFlag)
    }

    @Test
    fun `sales taxes are kept with the transaction and survive ordinary edits`() {
        val supplies = cat("business.supplies").id
        val txn = books.transactions.create(TransactionDraft(visa.id, LocalDate(2026, 9, 3), cad("-113.00"), "Bureau en Gros", listOf(SplitDraft(supplies, cad("-113.00")))))
        books.transactions.setSalesTaxes(txn.id, mapOf(TaxName.HST to cad("13.00")))
        assertEquals(mapOf(TaxName.HST to cad("13.00")), books.transactions.salesTaxes(txn.id))

        books.transactions.update(txn.id, TransactionDraft(visa.id, LocalDate(2026, 9, 4), cad("-113.00"), "Staples", listOf(SplitDraft(supplies, cad("-113.00")))))
        assertEquals(mapOf(TaxName.HST to cad("13.00")), books.transactions.salesTaxes(txn.id), "an edit of the payee and date keeps them")

        books.transactions.setSalesTaxes(txn.id, mapOf(TaxName.GST to cad("5.38"), TaxName.QST to cad("10.74"), TaxName.HST to cad("0")))
        assertEquals(setOf(TaxName.GST, TaxName.QST), books.transactions.salesTaxes(txn.id).keys, "replaced, a zero removes one")
        assertFailsWith<ValidationException> { books.transactions.setSalesTaxes(txn.id, mapOf(TaxName.HST to cad("200.00"))) }
        assertFailsWith<ValidationException> { books.transactions.setSalesTaxes(txn.id, mapOf(TaxName.HST to cad("-1.00"))) }

        books.transactions.delete(txn.id)
        assertEquals(0, books.ledger(books.groups().single()).salesTaxQueries.salesTaxesBetween("2026-01-01", "2026-12-31").executeAsList().size)
    }

    @Test
    fun `a receipt's taxes come with the transaction filed from it`() {
        val group = books.groups().single().id
        val doc = books.documents.import(group, "r".encodeToByteArray(), "r.jpg", "image/jpeg").document
        val text = "Canadian Tire #412\nSUBTOTAL 30.98\nGST 1.55\nQST 3.09\nTOTAL 35.62\n2026-10-03"
        books.documents.recordText(doc.id, 1, OcrResult(text.lines().map { OcrLine(it, 0.97f) }, 1), "t", LocalDate(2026, 10, 4))
        val txn = books.documents.fileAsTransaction(doc.id, TransactionDraft(visa.id, LocalDate(2026, 10, 3), cad("-35.62"), "Canadian Tire"))
        assertEquals(mapOf(TaxName.GST to cad("1.55"), TaxName.QST to cad("3.09")), books.transactions.salesTaxes(txn.id))
    }

    @Test
    fun `a refund goes back to the purchase's categories, in proportion, never more than was paid`() {
        val electronics = cat("personal.electronics").id
        val clothing = cat("personal.clothing").id
        val purchase = books.transactions.create(
            TransactionDraft(visa.id, LocalDate(2026, 9, 10), cad("-300.00"), "Best Buy", listOf(SplitDraft(electronics, cad("-200.00")), SplitDraft(clothing, cad("-100.00")))),
        )
        val refund = books.transactions.recordRefund(purchase.id, LocalDate(2026, 9, 20), cad("90.00"), "Returned headphones")
        assertEquals(purchase.id, refund.refundOf)
        assertEquals(cad("90.00"), refund.amount)
        assertEquals(listOf(cad("60.00"), cad("30.00")), refund.splits.map { it.amount })
        assertEquals(listOf(electronics, clothing), refund.splits.map { it.categoryId })
        assertEquals("Best Buy", refund.payeeText)
        assertEquals(listOf(refund.id), books.transactions.refundsOf(purchase.id).map { it.id })

        assertFailsWith<ValidationException> { books.transactions.recordRefund(purchase.id, LocalDate(2026, 9, 21), cad("211.00")) }
        books.transactions.recordRefund(purchase.id, LocalDate(2026, 9, 21), cad("210.00"))
        assertFailsWith<ValidationException> { books.transactions.recordRefund(refund.id, LocalDate(2026, 9, 22), cad("1.00")) }

        books.transactions.delete(purchase.id)
        assertEquals(null, books.transactions.get(refund.id).refundOf, "the refund stays, unlinked")
        assertTrue(books.transactions.get(refund.id).amount.minorUnits > 0)
    }
}
