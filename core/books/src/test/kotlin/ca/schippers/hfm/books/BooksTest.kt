package ca.schippers.hfm.books

import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountStatus
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.CategoryKind
import ca.schippers.hfm.domain.ClearedStatus
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.domain.TaxFlag
import ca.schippers.hfm.i18n.Language
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BooksTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var sharedGroup: String
    private val dir get() = temp.resolve("Test.hfm")

    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun usd(s: String) = Money.parse(s, Currency.USD)
    private fun day(d: Int) = LocalDate(2026, 3, d)

    @BeforeEach
    fun setUp() {
        val created = store.create(dir, "Test", "perry", "Perry", "admin-pass".toCharArray())
        books = Books(created.session)
        sharedGroup = books.groups().single().id
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun chequing(opening: String = "1000.00") =
        books.accounts.create(AccountDraft(sharedGroup, "Chequing", AccountType.CHEQUING, Currency.CAD, cad(opening), day(1), number = "12345-678-9012345"))

    private fun category(key: String) = books.categories.list().first { it.systemKey == key }.id

    // --- Categories ------------------------------------------------------------------------------

    @Test
    fun `default bilingual categories are seeded once`() {
        val all = books.categories.list()
        assertTrue(all.size > 90, "expected the full default tree, got ${all.size}")
        val pharmacy = all.first { it.systemKey == "health.pharmacy" }
        assertEquals("Médicaments sur ordonnance", pharmacy.name(Language.FRENCH))
        assertEquals(TaxFlag.MEDICAL, pharmacy.taxFlag)
        assertEquals(all.first { it.systemKey == "health" }.id, pharmacy.parentId)
        assertTrue(all.filter { it.parentId != null }.all { child -> all.first { it.id == child.parentId }.kind == child.kind })

        Books(books.session) // opening again must not seed twice
        assertEquals(all.size, books.categories.list().size)
    }

    @Test
    fun `category tree lists parents before children`() {
        val tree = books.categories.tree()
        val index = tree.withIndex().associate { it.value.first.id to it.index }
        tree.forEach { (c, depth) ->
            if (c.parentId != null) {
                assertTrue(index.getValue(c.parentId) < index.getValue(c.id))
                assertEquals(1, depth)
            }
        }
    }

    @Test
    fun `a category cannot be moved under its own child`() {
        val food = books.categories.list().first { it.systemKey == "food" }
        val groceries = category("food.groceries")
        assertFailsWith<ValidationException> { books.categories.update(food.copy(parentId = groceries)) }
    }

    @Test
    fun `new categories take the kind of their parent`() {
        val food = category("food")
        val bakery = books.categories.create(food, "Bakery", "Boulangerie", CategoryKind.EXPENSE)
        assertEquals("Boulangerie", bakery.name(Language.FRENCH))
        assertFailsWith<ValidationException> { books.categories.create(food, "Odd", "", CategoryKind.INCOME) }
    }

    // --- Institutions and accounts --------------------------------------------------------------

    @Test
    fun `institution numbers are validated`() {
        assertFailsWith<ValidationException> { books.institutions.create(Institution("", "Desjardins", institutionNumber = "81")) }
        val inst = books.institutions.create(Institution("", "Desjardins", institutionNumber = "815", transitNumber = "30123"))
        assertEquals(listOf("Desjardins"), books.institutions.list().map { it.name })
        assertEquals("815", inst.institutionNumber)
    }

    @Test
    fun `account numbers are masked and revealed only with the password`() {
        val account = chequing()
        assertEquals("•••• 2345", account.numberMasked)
        assertFailsWith<AccessDeniedException> { books.accounts.revealNumber(account.id, "wrong".toCharArray()) }
        assertEquals("12345-678-9012345", books.accounts.revealNumber(account.id, "admin-pass".toCharArray()))
    }

    @Test
    fun `closed accounts keep their history but are hidden by default`() {
        val account = chequing()
        books.transactions.create(TransactionDraft(account.id, day(2), cad("-10.00")))
        books.accounts.close(account.id)
        assertTrue(books.accounts.list().none { it.account.id == account.id })
        val closed = books.accounts.list(includeClosed = true).single { it.account.id == account.id }
        assertEquals(AccountStatus.CLOSED, closed.account.status)
        assertEquals(cad("990.00"), closed.balance)
    }

    @Test
    fun `account owners are recorded`() {
        val marie = books.members.create("Marie", MemberKind.ADULT)
        val perry = books.members.create("Perry", MemberKind.ADULT)
        val joint = books.accounts.create(
            AccountDraft(sharedGroup, "Joint", AccountType.SAVINGS, Currency.CAD, cad("0"), day(1), ownerMemberIds = setOf(marie.id, perry.id)),
        )
        assertEquals(setOf(marie.id, perry.id), joint.ownerMemberIds)
        books.accounts.update(joint.copy(ownerMemberIds = setOf(marie.id)))
        assertEquals(setOf(marie.id), books.accounts.get(joint.id).ownerMemberIds)
    }

    // --- Transactions ---------------------------------------------------------------------------

    @Test
    fun `register shows the running balance`() {
        val account = chequing()
        books.transactions.create(TransactionDraft(account.id, day(5), cad("-45.67"), payeeName = "IGA"))
        books.transactions.create(TransactionDraft(account.id, day(3), cad("2500.00"), payeeName = "Employer"))
        books.transactions.create(TransactionDraft(account.id, day(10), cad("-120.00"), payeeName = "Hydro-Québec"))
        val register = books.transactions.register(account.id)
        assertEquals(listOf(day(3), day(5), day(10)), register.map { it.transaction.date })
        assertEquals(listOf(cad("3500.00"), cad("3454.33"), cad("3334.33")), register.map { it.runningBalance })
        assertEquals(cad("3334.33"), books.accounts.list().single().balance)
    }

    @Test
    fun `splits must add up to the amount`() {
        val account = chequing()
        val groceries = category("food.groceries")
        val pharmacy = category("health.otc")
        val txn = books.transactions.create(
            TransactionDraft(
                account.id, day(4), cad("-100.00"), payeeName = "Costco",
                splits = listOf(SplitDraft(groceries, cad("-70.00")), SplitDraft(pharmacy, cad("-30.00"))),
            ),
        )
        assertTrue(txn.isSplit)
        assertFailsWith<ValidationException> {
            books.transactions.create(
                TransactionDraft(account.id, day(4), cad("-100.00"), splits = listOf(SplitDraft(groceries, cad("-70.00")))),
            )
        }
    }

    @Test
    fun `a transaction without splits is one uncategorized split`() {
        val txn = books.transactions.create(TransactionDraft(chequing().id, day(4), cad("-5.00")))
        assertEquals(1, txn.splits.size)
        assertNull(txn.splits.single().categoryId)
        assertEquals(cad("-5.00"), txn.splits.single().amount)
    }

    @Test
    fun `amounts must be in the account currency`() {
        assertFailsWith<ValidationException> { books.transactions.create(TransactionDraft(chequing().id, day(4), usd("-5.00"))) }
    }

    @Test
    fun `foreign purchases keep the original amount and the rate charged`() {
        val txn = books.transactions.create(
            TransactionDraft(chequing().id, day(4), cad("-137.25"), payeeName = "Amazon.com", originalAmount = usd("-100.00")),
        )
        assertEquals(usd("-100.00"), txn.originalAmount)
        assertEquals(0, BigDecimal("1.3725").compareTo(txn.fxRate))
    }

    @Test
    fun `payees are created once and matched by alias`() {
        val account = chequing()
        books.transactions.create(TransactionDraft(account.id, day(2), cad("-10"), payeeName = "Amazon"))
        val amazon = books.payees.list().single()
        books.payees.addAlias(amazon.id, "AMZN MKTP")
        val imported = books.transactions.create(TransactionDraft(account.id, day(3), cad("-20"), payeeName = "AMZN MKTP CA*2X4"))
        assertEquals(amazon.id, imported.payeeId)
        assertEquals("AMZN MKTP CA*2X4", imported.payeeText)
        assertEquals(1, books.payees.list().size)
    }

    @Test
    fun `typing a known payee suggests the last amount and categories`() {
        val account = chequing()
        books.transactions.create(
            TransactionDraft(account.id, day(2), cad("-89.99"), payeeName = "Vidéotron", splits = listOf(SplitDraft(category("utilities.internet"), cad("-89.99")))),
        )
        val suggestion = assertNotNull(books.transactions.suggest(account.id, "vidéotron"))
        assertEquals(cad("-89.99"), suggestion.amount)
        assertEquals(category("utilities.internet"), suggestion.splits.single().categoryId)
        assertNull(books.transactions.suggest(account.id, "Unknown shop"))
    }

    @Test
    fun `updates and deletes are recorded in the change history`() {
        val txn = books.transactions.create(TransactionDraft(chequing().id, day(2), cad("-10.00"), payeeName = "Metro"))
        books.transactions.update(txn.id, TransactionDraft(txn.accountId, day(2), cad("-12.50"), payeeName = "Metro", tags = setOf("Party")))
        val history = books.transactions.history(txn.id)
        assertEquals(listOf("CREATE", "UPDATE"), history.map { it.action })
        assertTrue(history[1].before!!.contains("-1000") && history[1].after!!.contains("-1250"))
        assertEquals(1, books.transactions.get(txn.id).tagIds.size)

        books.transactions.delete(txn.id)
        assertFailsWith<AccessDeniedException> { books.transactions.get(txn.id) }
    }

    @Test
    fun `reconciled transactions only change with confirmation`() {
        val txn = books.transactions.create(TransactionDraft(chequing().id, day(2), cad("-10.00")))
        books.transactions.setCleared(txn.id, ClearedStatus.RECONCILED)
        val change = TransactionDraft(txn.accountId, day(2), cad("-11.00"), cleared = ClearedStatus.RECONCILED)
        assertFailsWith<ReconciledChangeException> { books.transactions.update(txn.id, change) }
        assertFailsWith<ReconciledChangeException> { books.transactions.delete(txn.id) }
        assertFailsWith<ReconciledChangeException> { books.transactions.setCleared(txn.id, ClearedStatus.CLEARED) }
        books.transactions.update(txn.id, change, confirmReconciled = true)
        assertEquals(cad("-11.00"), books.transactions.get(txn.id).amount)
    }

    // --- Transfers ------------------------------------------------------------------------------

    @Test
    fun `a transfer appears in both accounts and is never income or spending`() {
        val chequing = chequing("1000.00")
        val savings = books.accounts.create(AccountDraft(sharedGroup, "Savings", AccountType.SAVINGS, Currency.CAD, cad("0"), day(1)))
        val (out, into) = books.transactions.transfer(TransferDraft(chequing.id, savings.id, day(6), cad("250.00"), memo = "Monthly saving"))
        assertEquals(cad("-250.00"), out.amount)
        assertEquals(cad("250.00"), into.amount)
        assertEquals(out.transfer!!.transferId, into.transfer!!.transferId)
        assertEquals(savings.id, out.transfer.otherAccountId)
        assertTrue(out.splits.isEmpty() && into.splits.isEmpty(), "transfers carry no category")
        val balances = books.accounts.list().associate { it.account.name to it.balance }
        assertEquals(cad("750.00"), balances["Chequing"])
        assertEquals(cad("250.00"), balances["Savings"])

        books.transactions.updateTransfer(out.transfer.transferId, TransferDraft(chequing.id, savings.id, day(7), cad("300.00")))
        assertEquals(cad("300.00"), books.transactions.get(into.id).amount)

        books.transactions.delete(into.id) // deleting either side removes both
        assertEquals(cad("1000.00"), books.accounts.list().first { it.account.name == "Chequing" }.balance)
    }

    @Test
    fun `transfers between currencies record both amounts and the rate`() {
        val cadAccount = chequing()
        val usdAccount = books.accounts.create(AccountDraft(sharedGroup, "US account", AccountType.CHEQUING, Currency.USD, usd("0"), day(1)))
        assertFailsWith<ValidationException> { books.transactions.transfer(TransferDraft(cadAccount.id, usdAccount.id, day(6), cad("137.25"))) }
        val (out, into) = books.transactions.transfer(TransferDraft(cadAccount.id, usdAccount.id, day(6), cad("137.25"), toAmount = usd("100.00")))
        assertEquals(usd("100.00"), out.originalAmount)
        assertEquals(cad("137.25"), into.originalAmount)
        assertEquals(0, BigDecimal("1.3725").compareTo(into.fxRate))
    }

    @Test
    fun `transfers work between a shared and a private group`() {
        val shared = chequing()
        val privateGroup = books.session.createGroup("Perry - personal", private = true)
        val personal = books.accounts.create(AccountDraft(privateGroup, "Personal", AccountType.SAVINGS, Currency.CAD, cad("0"), day(1)))
        val (_, into) = books.transactions.transfer(TransferDraft(shared.id, personal.id, day(8), cad("40.00")))
        assertEquals(personal.id, into.accountId)
        assertEquals(cad("40.00"), books.accounts.list().first { it.account.id == personal.id }.balance)
        books.transactions.delete(into.id)
        assertEquals(cad("1000.00"), books.accounts.list().first { it.account.id == shared.id }.balance)
    }

    // --- Permissions ----------------------------------------------------------------------------

    @Test
    fun `members need capture permission to add and edit permission to change`() {
        val account = chequing()
        val existing = books.transactions.create(TransactionDraft(account.id, day(2), cad("-10.00")))
        val teen = books.session.addUser("teen", "Teen", Role.MEMBER, "teen-pass".toCharArray())
        books.session.setPermission(sharedGroup, teen.userId, PermissionLevel.VIEW)
        books.session.close()

        store.unlock(dir, "teen", "teen-pass".toCharArray()).use { session ->
            val teenBooks = Books(session)
            assertEquals(1, teenBooks.transactions.register(account.id).size)
            assertFailsWith<AccessDeniedException> { teenBooks.transactions.create(TransactionDraft(account.id, day(3), cad("-1.00"))) }
        }
        store.unlock(dir, "perry", "admin-pass".toCharArray()).use { it.setPermission(sharedGroup, teen.userId, PermissionLevel.CAPTURE_ONLY) }
        store.unlock(dir, "teen", "teen-pass".toCharArray()).use { session ->
            val teenBooks = Books(session)
            teenBooks.transactions.create(TransactionDraft(account.id, day(3), cad("-1.00"), payeeName = "Dépanneur"))
            assertFailsWith<AccessDeniedException> {
                teenBooks.transactions.update(existing.id, TransactionDraft(account.id, day(2), cad("-99.00")))
            }
        }
        books = Books(store.unlock(dir, "perry", "admin-pass".toCharArray()))
    }

    // --- Credit cards ---------------------------------------------------------------------------

    @Test
    fun `credit card terms and statements`() {
        val card = books.accounts.create(AccountDraft(sharedGroup, "Visa", AccountType.CREDIT_CARD, Currency.CAD, cad("0"), day(1)))
        val terms = CreditCardTerms(
            creditLimit = cad("5000"), purchaseRate = BigDecimal("0.1999"), statementDay = 15, dueDay = 6,
            minPaymentPercent = BigDecimal("0.03"), minPaymentFloor = cad("10.00"),
        )
        books.creditCards.saveTerms(card.id, terms)
        assertEquals(terms, books.creditCards.terms(card.id))

        val statement = books.creditCards.recordStatement(card.id, LocalDate(2026, 3, 15), cad("1234.56"), LocalDate(2026, 4, 6))
        assertEquals(cad("37.04"), statement.minimumDue) // 3% of 1234.56 = 37.0368
        assertEquals(cad("10.00"), CreditCardService.minimumPayment(cad("200.00"), terms))
        assertEquals(cad("4.00"), CreditCardService.minimumPayment(cad("4.00"), terms))
        books.creditCards.markPaid(card.id, statement.id)
        assertTrue(books.creditCards.statements(card.id).single().paid)

        assertFailsWith<ValidationException> { books.creditCards.saveTerms(chequing().id, terms) }
    }
}
