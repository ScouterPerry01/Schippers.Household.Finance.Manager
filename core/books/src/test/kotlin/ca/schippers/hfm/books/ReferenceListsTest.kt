package ca.schippers.hfm.books

import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.CategoryKind
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.Role
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
import kotlin.test.assertTrue

/** Problems found while writing the manual (M-72 to M-79): the household's lists, who may change them, and passwords. */
class ReferenceListsTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private val dir get() = temp.resolve("R.hfm")

    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun category(key: String) = books.categories.list().first { it.systemKey == key }

    @BeforeEach
    fun setUp() {
        books = Books(store.create(dir, "R", "perry", "Perry", "admin-password".toCharArray()).session)
    }

    @AfterEach
    fun tearDown() = books.session.close()

    @Test
    fun `rules can be edited, moved, and keep their places in order (M-73)`() {
        val electricity = category("utilities.electricity").id
        val groceries = category("food.groceries").id
        val hydro = books.payees.create("Hydro-Québec")
        val a = books.rules.create("HYDRO", electricity)
        val b = books.rules.create("COSTCO", groceries)
        val c = books.rules.create("COSTCO GAS", groceries)
        assertEquals(listOf(0, 1, 2), books.rules.list().map { it.sortOrder })

        // The specific rule moves above the general one, so it is tried first.
        books.rules.move(c.id, up = true)
        assertEquals(listOf(a.id, c.id, b.id), books.rules.list().map { it.id })
        assertEquals(listOf(0, 1, 2), books.rules.list().map { it.sortOrder })
        assertEquals(c.id, books.rules.find("COSTCO GAS #123", cad("-40"))!!.id)
        books.rules.move(a.id, up = true) // already first: nothing changes
        assertEquals(a.id, books.rules.list().first().id)

        // Editing keeps the rule's id and place, and can set the payee too.
        books.rules.update(a.id, "HYDRO-QUEBEC", electricity, cad("50"), cad("500"), hydro.id)
        val edited = books.rules.list().first()
        assertEquals(a.id, edited.id)
        assertEquals("HYDRO-QUEBEC", edited.payeeContains)
        assertEquals(hydro.id, edited.payeeId)
        assertEquals(cad("50"), edited.amountMin)
        assertFailsWith<ValidationException> { books.rules.update(a.id, "HYDRO", electricity, payeeId = "no-such-payee") }
        assertFailsWith<ValidationException> { books.rules.update(a.id, "HYDRO", electricity, cad("500"), cad("50")) }

        // Deleting closes the gap.
        books.rules.delete(c.id)
        assertEquals(listOf(a.id, b.id), books.rules.list().map { it.id })
        assertEquals(listOf(0, 1), books.rules.list().map { it.sortOrder })
    }

    @Test
    fun `a payee's aliases are listed (M-74)`() {
        val amazon = books.payees.create("Amazon")
        books.payees.addAlias(amazon.id, "AMZN MKTP")
        books.payees.addAlias(amazon.id, "amazon.ca")
        books.payees.addAlias(books.payees.create("Costco").id, "COSTCO WHOLESALE")
        assertEquals(listOf("amazon.ca", "AMZN MKTP"), books.payees.aliases(amazon.id).map { it.pattern })
    }

    @Test
    fun `a category needs a name and can move under another parent of its kind (M-75)`() {
        val pharmacy = category("health.pharmacy")
        assertFailsWith<ValidationException> { books.categories.update(pharmacy.copy(nameEn = " ", nameFr = "")) }

        // One name is enough; the other language takes it.
        books.categories.update(pharmacy.copy(nameEn = "", nameFr = "Pharmacie"))
        assertEquals("Pharmacie", category("health.pharmacy").name(Language.ENGLISH))

        val food = category("food")
        books.categories.update(category("health.pharmacy").copy(parentId = food.id))
        val moved = category("health.pharmacy")
        assertEquals(food.id, moved.parentId)
        assertEquals(books.categories.list(includeArchived = true).count { it.parentId == food.id } - 1, moved.sortOrder)

        books.categories.update(moved.copy(parentId = null))
        assertEquals(null, category("health.pharmacy").parentId)

        val salary = category("income.employment.salary")
        assertFailsWith<ValidationException> { books.categories.update(moved.copy(parentId = salary.id)) }
        assertEquals(CategoryKind.EXPENSE, category("health.pharmacy").kind)
    }

    @Test
    fun `viewers cannot change the lists and only administrators the settings (M-77)`() {
        val electricity = category("utilities.electricity")
        val rule = books.rules.create("HYDRO", electricity.id)
        val payee = books.payees.create("Hydro-Québec")
        books.users.add("viewer", "Viewer", Role.VIEWER, "watcher-passcode".toCharArray())
        books.users.add("marie", "Marie", Role.MEMBER, "her-own-passcode".toCharArray())
        books.session.close()

        Books(store.unlock(dir, "viewer", "watcher-passcode".toCharArray())).let { viewer ->
            try {
                assertTrue(!viewer.canEdit)
                assertFailsWith<AccessDeniedException> { viewer.categories.create(null, "Hobbies", "Loisirs", CategoryKind.EXPENSE) }
                assertFailsWith<AccessDeniedException> { viewer.categories.update(electricity.copy(nameEn = "Power")) }
                assertFailsWith<AccessDeniedException> { viewer.payees.create("Costco") }
                assertFailsWith<AccessDeniedException> { viewer.payees.update(payee.copy(name = "HQ")) }
                assertFailsWith<AccessDeniedException> { viewer.payees.addAlias(payee.id, "HYDRO") }
                assertFailsWith<AccessDeniedException> { viewer.institutions.create(Institution("", "Desjardins")) }
                assertFailsWith<AccessDeniedException> { viewer.rules.create("COSTCO", electricity.id) }
                assertFailsWith<AccessDeniedException> { viewer.rules.move(rule.id, up = false) }
                assertFailsWith<AccessDeniedException> { viewer.rules.delete(rule.id) }
                assertFailsWith<AccessDeniedException> { viewer.rates.setManual(Currency.USD, LocalDate(2026, 3, 2), BigDecimal("1.40")) }
                assertFailsWith<AccessDeniedException> { viewer.rates.follow(Currency.EUR) }
                assertFailsWith<AccessDeniedException> { viewer.ai.saveSettings(AiSettings(enabled = true, confirmEach = true, model = null)) }
                assertEquals(1, viewer.rules.list().size)
            } finally {
                viewer.session.close()
            }
        }

        Books(store.unlock(dir, "marie", "her-own-passcode".toCharArray())).let { member ->
            try {
                member.payees.addAlias(payee.id, "HYDRO-QUE")
                member.rules.update(rule.id, "HYDRO-QUEBEC", electricity.id, payeeId = payee.id)
                member.rates.follow(Currency.EUR)
                member.ai.saveSettings(AiSettings(enabled = true, confirmEach = true, model = null))
                assertFailsWith<AccessDeniedException> { member.prices.setEnabled(PriceFeed.SECURITIES, true) }
                assertFailsWith<AccessDeniedException> { member.rates.openSourceEnabled = true }
                assertFailsWith<AccessDeniedException> { member.backups.saveSettings(BackupSettings(temp.resolve("b"), BackupFrequency.DAILY, 5)) }
            } finally {
                member.session.close()
            }
        }
        books = Books(store.unlock(dir, "perry", "admin-password".toCharArray()))
        books.prices.setEnabled(PriceFeed.SECURITIES, true)
        books.backups.saveSettings(BackupSettings(temp.resolve("b"), BackupFrequency.DAILY, 5))
    }

    @Test
    fun `a viewer can be given View at most (M-78)`() {
        val shared = books.groups().single().id
        val viewer = books.users.add("viewer", "Viewer", Role.VIEWER, "watcher-passcode".toCharArray()).userId
        assertFailsWith<ValidationException> { books.users.setAccess(shared, viewer, PermissionLevel.EDIT) }
        assertFailsWith<ValidationException> { books.users.setAccess(shared, viewer, PermissionLevel.CAPTURE_ONLY) }
        books.users.setAccess(shared, viewer, PermissionLevel.VIEW)
        assertEquals(PermissionLevel.VIEW, books.users.access().single().levels[viewer])

        // A member given Edit, then made a viewer, shows View: what they can actually do.
        val marie = books.users.add("marie", "Marie", Role.MEMBER, "her-own-passcode".toCharArray()).userId
        books.users.setAccess(shared, marie, PermissionLevel.EDIT)
        books.users.setRole(marie, Role.VIEWER)
        assertEquals(PermissionLevel.VIEW, books.users.access().single().levels[marie])
    }

    @Test
    fun `every password needs 12 characters (M-72)`() {
        assertFailsWith<ValidationException> { books.users.add("sam", "Sam", Role.MEMBER, "short-pass".toCharArray()) }
        books.users.add("sam", "Sam", Role.MEMBER, "twelve-chars".toCharArray())
        assertFailsWith<ValidationException> { books.users.changePassword("admin-password".toCharArray(), "eleven-char".toCharArray()) }
        books.users.changePassword("admin-password".toCharArray(), "a-new-password".toCharArray())
    }

    @Test
    fun `the phone receives the desktop's language (M-79)`() {
        val today = LocalDate(2026, 3, 2)
        assertEquals("en", books.sync.reference(today, 0).language)
        books.language = Language.FRENCH
        assertEquals("fr", books.sync.reference(today, 0).language)
    }
}
