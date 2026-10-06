package ca.schippers.hfm.books

import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.ClearedStatus
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.importers.ExportCleared
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
import kotlin.test.assertTrue

/** TX-07 bulk edit and EXP-02 export of chosen transactions. */
class BulkEditTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private val dir get() = temp.resolve("B.hfm")
    private lateinit var books: Books
    private lateinit var shared: String
    private lateinit var chequing: Account
    private lateinit var visa: Account
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun d(day: Int) = LocalDate(2026, 3, day)
    private fun cat(key: String) = books.categories.list().first { it.systemKey == key }

    @BeforeEach
    fun setUp() {
        books = Books(store.create(dir, "B", "perry", "Perry", "password1".toCharArray()).session)
        shared = books.groups().single().id
        chequing = books.accounts.create(AccountDraft(shared, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("1000"), LocalDate(2026, 1, 1)))
        visa = books.accounts.create(AccountDraft(shared, "Visa", AccountType.CREDIT_CARD, Currency.CAD, cad("0"), LocalDate(2026, 1, 1)))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    @Test
    fun `re-categorize keeps split amounts, skips transfers and logs each change`() {
        val groceries = cat("food.groceries").id
        val a = books.transactions.create(TransactionDraft(chequing.id, d(2), cad("-40.00"), "IGA", memo = "milk"))
        val b = books.transactions.create(
            TransactionDraft(chequing.id, d(3), cad("-90.00"), "Costco", listOf(SplitDraft(null, cad("-60.00"), "food"), SplitDraft(null, cad("-30.00"), "soap"))),
        )
        val (out, _) = books.transactions.transfer(TransferDraft(chequing.id, visa.id, d(4), cad("100.00")))

        val result = books.transactions.bulkCategorize(listOf(a.id, b.id, out.id), groceries)
        assertEquals(BulkResult(changed = 2, skipped = 1), result)
        assertEquals(listOf(groceries), books.transactions.get(a.id).splits.map { it.categoryId })
        assertEquals("milk", books.transactions.get(a.id).memo, "the rest of the transaction is unchanged")
        assertEquals(listOf(cad("-60.00") to groceries, cad("-30.00") to groceries), books.transactions.get(b.id).splits.map { it.amount to it.categoryId })
        assertEquals(listOf("food", "soap"), books.transactions.get(b.id).splits.map { it.memo })
        assertEquals(listOf("CREATE", "UPDATE"), books.transactions.versions(a.id).map { it.action })
    }

    @Test
    fun `a tag is added to each, and reconciled transactions need confirmation`() {
        val a = books.transactions.create(TransactionDraft(chequing.id, d(2), cad("-40.00"), "IGA", tags = setOf("Groceries")))
        val b = books.transactions.create(TransactionDraft(chequing.id, d(3), cad("-12.00"), "Café", cleared = ClearedStatus.RECONCILED))
        assertFailsWith<ReconciledChangeException> { books.transactions.bulkAddTag(listOf(a.id, b.id), "Cottage") }
        val tags = { id: String -> books.transactions.get(id).tagIds.mapNotNull { t -> books.tags().firstOrNull { it.id == t }?.name }.toSet() }
        assertEquals(setOf("Groceries"), tags(a.id), "nothing changed before the confirmation")
        books.transactions.bulkAddTag(listOf(a.id, b.id), " Cottage ", confirmReconciled = true)
        assertEquals(setOf("Groceries", "Cottage"), tags(a.id))
        assertEquals(setOf("Cottage"), tags(b.id))
    }

    @Test
    fun `transactions move within a group, never off a statement or into another currency`() {
        val a = books.transactions.create(TransactionDraft(chequing.id, d(2), cad("-40.00"), "IGA"))
        val usd = books.accounts.create(AccountDraft(shared, "US", AccountType.CHEQUING, Currency.USD, Money.parse("0", Currency.USD), LocalDate(2026, 1, 1)))
        val result = books.transactions.bulkMove(listOf(a.id), visa.id)
        assertEquals(BulkResult(1, 0), result)
        assertEquals(visa.id, books.transactions.get(a.id).accountId)
        val moved = books.transactions.versions(a.id).last()
        assertEquals("UPDATE", moved.action)
        assertEquals(chequing.id to visa.id, moved.before?.accountId to moved.after?.accountId, "the history shows the move")
        assertEquals(BulkResult(0, 1), books.transactions.bulkMove(listOf(a.id), usd.id), "another currency")

        val private = books.session.createGroup("Perry - private", private = true)
        val savings = books.accounts.create(AccountDraft(private, "Savings", AccountType.SAVINGS, Currency.CAD, cad("0"), LocalDate(2026, 1, 1)))
        assertFailsWith<ValidationException> { books.transactions.bulkMove(listOf(a.id), savings.id) }
    }

    @Test
    fun `a viewer cannot change transactions in bulk`() {
        val a = books.transactions.create(TransactionDraft(chequing.id, d(2), cad("-40.00"), "IGA"))
        val viewer = books.session.addUser("marie", "Marie", Role.MEMBER, "marie-password".toCharArray())
        books.session.setPermission(shared, viewer.userId, PermissionLevel.VIEW)
        books.session.close()
        store.unlock(dir, "marie", "marie-password".toCharArray()).use { session ->
            val hers = Books(session)
            assertFailsWith<AccessDeniedException> { hers.transactions.bulkCategorize(listOf(a.id), null) }
            assertFailsWith<AccessDeniedException> { hers.transactions.bulkMove(listOf(a.id), visa.id) }
        }
        books = Books(store.unlock(dir, "perry", "password1".toCharArray()))
    }

    @Test
    fun `export lines carry category paths, transfers and splits`() {
        val groceries = cat("food.groceries")
        val a = books.transactions.create(TransactionDraft(chequing.id, d(5), cad("-40.00"), "IGA", listOf(SplitDraft(groceries.id, cad("-40.00"))), cleared = ClearedStatus.CLEARED))
        val (out, _) = books.transactions.transfer(TransferDraft(chequing.id, visa.id, d(4), cad("100.00")))
        val lines = books.transactions.exportLines(listOf(a.id, out.id), french = false)
        assertEquals(listOf(out.id, a.id), lines.map { it.id }, "oldest first")
        assertEquals("Visa", lines[0].transferAccount)
        val parent = books.categories.list().first { it.id == groceries.parentId }
        assertEquals(listOf(parent.nameEn, groceries.nameEn), lines[1].category)
        assertEquals(BigDecimal("-40.00"), lines[1].amount)
        assertEquals(ExportCleared.CLEARED, lines[1].cleared)
        assertEquals(listOf(parent.nameFr, groceries.nameFr), books.transactions.exportLines(listOf(a.id), french = true).single().category)
        assertTrue(lines.all { it.splits.isEmpty() })
    }
}
