package ca.schippers.hfm.books

import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.Role
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

/** MAN-05: named transaction templates, per account group. */
class TemplateServiceTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var group: String
    private lateinit var chequing: Account
    private lateinit var visa: Account
    private fun d(s: String) = LocalDate.parse(s)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id
    private val templates get() = books.templates

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("T.hfm"), "T", "perry", "Perry", "password1".toCharArray()).session)
        group = books.groups().single().id
        chequing = books.accounts.create(AccountDraft(group, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("1000"), d("2026-01-01")))
        visa = books.accounts.create(AccountDraft(group, "Visa", AccountType.CREDIT_CARD, Currency.CAD, cad("0"), d("2026-01-01")))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    @Test
    fun `a template fills a transaction, with or without its amount`() {
        val rent = templates.save(
            TxnTemplate("", group, "Rent", "Gestion Tremblay", "Monthly rent", cad("-1450"), chequing.id, lines = listOf(TemplateLine(cat("housing.rent"))), tags = setOf("Home")),
        )
        val coffee = templates.save(TxnTemplate("", group, "Coffee", "Café Morgane", lines = listOf(TemplateLine(cat("food.restaurants")))))
        assertEquals(listOf("Coffee", "Rent"), templates.forAccount(chequing.id).map { it.name }, "by name")
        assertEquals(listOf("Coffee"), templates.forAccount(visa.id).map { it.name }, "a template for one account is only offered there")

        val draft = templates.draft(rent.id, chequing.id, d("2026-02-01"))
        assertEquals(cad("-1450"), draft.amount)
        assertEquals(listOf(cat("housing.rent")), draft.splits.map { it.categoryId })
        assertEquals(setOf("Home"), draft.tags)
        val txn = books.transactions.create(draft)
        assertEquals("Monthly rent", txn.memo)
        assertEquals(1, txn.tagIds.size)

        assertFailsWith<ValidationException>("no amount in the template: one must be typed") { templates.draft(coffee.id, visa.id, d("2026-02-02")) }
        assertEquals(cad("-4.75"), templates.draft(coffee.id, visa.id, d("2026-02-02"), cad("-4.75")).splits.single().amount)
    }

    @Test
    fun `save as template from a split transaction, then edit and delete`() {
        val txn = books.transactions.create(
            TransactionDraft(
                visa.id, d("2026-03-05"), cad("-120"), "Costco",
                listOf(SplitDraft(cat("food.groceries"), cad("-90")), SplitDraft(cat("pets.supplies"), cad("-30"), "Paper")), "Weekly", tags = setOf("Costco run"),
            ),
        )
        val t = templates.fromTransaction(txn.id, "Costco", thisAccountOnly = true)
        assertEquals("Costco", t.payee)
        assertEquals(visa.id, t.accountId)
        assertEquals(cad("-120"), t.amount)
        assertEquals(listOf(cad("-90"), cad("-30")), t.lines.map { it.amount })
        assertEquals(setOf("Costco run"), t.tags)
        val draft = templates.draft(t.id, visa.id, d("2026-03-12"))
        assertEquals(2, draft.splits.size, "the split lines come back with their amounts")
        assertEquals("Paper", draft.splits.last().memo)

        // Without its amount, a split transaction keeps no lines (they need amounts).
        val bare = templates.fromTransaction(txn.id, "Costco (any amount)", keepAmount = false)
        assertNull(bare.amount)
        assertTrue(bare.lines.isEmpty())
        assertNull(bare.accountId)

        assertFailsWith<ValidationException>("names are unique in a group") { templates.fromTransaction(txn.id, "costco") }
        val renamed = templates.save(t.copy(name = "Costco weekly", memo = null))
        assertEquals(2, renamed.lines.size, "saving again keeps its lines")
        templates.delete(renamed.id)
        assertEquals(listOf("Costco (any amount)"), templates.list().map { it.name })
    }

    @Test
    fun `rules for a template`() {
        assertFailsWith<ValidationException> { templates.save(TxnTemplate("", group, " ")) }
        assertFailsWith<ValidationException>("a split template needs an amount on each line") {
            templates.save(TxnTemplate("", group, "Split", lines = listOf(TemplateLine(cat("food.groceries"), cad("-5")), TemplateLine(cat("pets.supplies")))))
        }
        val usd = books.accounts.create(AccountDraft(group, "US", AccountType.CHEQUING, Currency.USD, Money.parse("0", Currency.USD), d("2026-01-01")))
        assertFailsWith<ValidationException>("the amount is in the account's currency") { templates.save(TxnTemplate("", group, "US rent", amount = cad("-5"), accountId = usd.id)) }
        val transfer = books.transactions.transfer(TransferDraft(chequing.id, visa.id, d("2026-03-01"), cad("200")))
        assertFailsWith<ValidationException>("a transfer is not a template") { templates.fromTransaction(transfer.first.id, "Pay the card") }
        // A template's amount in another currency is left out in that account.
        val any = templates.save(TxnTemplate("", group, "Gift", amount = cad("-50")))
        assertFailsWith<ValidationException> { templates.draft(any.id, usd.id, d("2026-03-01")) }
    }

    @Test
    fun `a viewer sees templates but cannot change them`() {
        templates.save(TxnTemplate("", group, "Rent", amount = cad("-1450")))
        val vic = books.users.add("vic", "Vic", Role.VIEWER, "password3-long".toCharArray()).userId
        books.session.setPermission(group, vic, PermissionLevel.VIEW)
        books.session.close()
        books = Books(store.unlock(temp.resolve("T.hfm"), "vic", "password3-long".toCharArray()))
        val rent = templates.list().single()
        assertFailsWith<AccessDeniedException> { templates.save(rent.copy(name = "Loyer")) }
        assertFailsWith<AccessDeniedException> { templates.delete(rent.id) }
    }
}
