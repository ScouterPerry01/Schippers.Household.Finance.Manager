package ca.schippers.hfm.books

import ca.schippers.hfm.calc.rules.PasswordRules
import ca.schippers.hfm.calc.rules.Rules
import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.i18n.Messages
import ca.schippers.hfm.security.KdfParams
import ca.schippers.hfm.security.RecoveryKey
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The household's password rules, set by an administrator and applied wherever a password is chosen. */
class PasswordRulesTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private val dir get() = temp.resolve("P.hfm")

    // 2026-10-05 at noon UTC.
    private val clock = { 1_791_201_600_000L }

    @AfterTest
    fun reset() {
        Rules.userValues = emptyList()
    }

    private fun Books.use(block: (Books) -> Unit) = try { block(this) } finally { session.close() }

    private fun household(): Pair<Books, RecoveryKey> {
        val created = store.create(dir, "P", "perry", "Perry", "a long enough passphrase".toCharArray())
        return Books(created.session, clock) to created.recoveryKey
    }

    @Test
    fun `adding a user and changing a password follow the rules`() {
        val (books, _) = household()
        books.use {
            val short = assertFailsWith<ValidationException> { it.users.add("marie", "Marie", Role.MEMBER, "too short".toCharArray()) }
            assertEquals("error.passwordShort", short.key)
            assertEquals(12, short.args.single())
            assertEquals("error.passwordLoginName", assertFailsWith<ValidationException> { it.users.add("marie", "Marie", Role.MEMBER, "marie's password".toCharArray()) }.key)
            it.users.add("marie", "Marie", Role.MEMBER, "a long passphrase".toCharArray())
            assertEquals("error.passwordLoginName", assertFailsWith<ValidationException> {
                it.users.changePassword("a long enough passphrase".toCharArray(), "perry the platypus".toCharArray())
            }.key)
        }
    }

    @Test
    fun `an administrator's rules take effect today and others may not change them`() {
        val (books, _) = household()
        books.use {
            it.users.setPasswordRules(PasswordRules(16, capitals = true, smallLetters = false, digits = true, symbols = false, notLoginName = true))
            val stored = Rules.values("security.password.minLength").last()
            assertEquals(LocalDate(2026, 10, 5), stored.from)
            assertEquals("16", stored.value)
            assertEquals(16, it.users.passwordRules().minLength)
            assertEquals("error.passwordCapital", assertFailsWith<ValidationException> { it.users.add("marie", "Marie", Role.MEMBER, "sixteen letters!".toCharArray()) }.key)
            assertEquals("error.passwordDigit", assertFailsWith<ValidationException> { it.users.add("marie", "Marie", Role.MEMBER, "Sixteen letters!".toCharArray()) }.key)
            it.users.add("marie", "Marie", Role.MEMBER, "Sixteen letters 2".toCharArray())
            assertEquals("error.passwordRuleLength", assertFailsWith<ValidationException> { it.users.setPasswordRules(it.users.passwordRules().copy(minLength = 6)) }.key)
            assertFailsWith<ValidationException> { it.rateRules.add("security.password.minLength", null, LocalDate(2026, 10, 5), "100") }
        }
        Books(store.unlock(dir, "marie", "Sixteen letters 2".toCharArray()), clock).use {
            assertFailsWith<AccessDeniedException> { it.users.setPasswordRules(it.users.passwordRules().copy(minLength = 20)) }
        }
    }

    @Test
    fun `a reset with the recovery key follows the household's own rules`() {
        val (books, key) = household()
        books.use { it.users.setPasswordRules(it.users.passwordRules().copy(minLength = 20)) }
        Rules.userValues = emptyList()
        val today = LocalDate(2026, 10, 6)
        val refused = assertFailsWith<ValidationException> {
            store.resetPassword(dir, "perry", key, "only sixteen ok".toCharArray()) { s -> Passwords.checkReset(s, "perry", "only sixteen ok".toCharArray(), today) }
        }
        assertEquals("error.passwordShort", refused.key)
        assertEquals(20, refused.args.single())
        // The old password still opens the household.
        store.unlock(dir, "perry", "a long enough passphrase".toCharArray()).close()
        val password = "twenty characters or more"
        store.resetPassword(dir, "perry", key, password.toCharArray()) { s -> Passwords.checkReset(s, "perry", password.toCharArray(), today) }.close()
        store.unlock(dir, "perry", password.toCharArray()).close()
    }

    @Test
    fun `new passwords take the household's Argon2id cost, never below the safe minimum`() {
        assertEquals(KdfParams(64 * 1024, 3, 1), Passwords.kdfForNewPasswords(LocalDate(2026, 10, 5)))
        Rules.userValues = listOf(ca.schippers.hfm.calc.rules.RuleValue("security.argon2.memory", null, LocalDate(2026, 1, 1), "4", builtIn = false, id = "x"))
        assertEquals(KdfParams.MIN_MEMORY_KIB, Passwords.kdfForNewPasswords(LocalDate(2026, 10, 5)).memoryKiB)
        assertFailsWith<IllegalArgumentException> { KdfParams.forNewPasswords(8, 3) }
    }

    @Test
    fun `every password problem has a message in both languages`() {
        for (language in Language.entries) for (p in PasswordRules.Problem.entries) {
            assertTrue(p.key in Messages.keys(language), "${p.key} in $language")
        }
    }
}
