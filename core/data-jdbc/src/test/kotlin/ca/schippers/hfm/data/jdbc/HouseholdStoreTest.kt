package ca.schippers.hfm.data.jdbc

import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.WrongKeyException
import ca.schippers.hfm.data.WrongPasswordException
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.security.KdfParams
import ca.schippers.hfm.security.Random
import ca.schippers.hfm.security.RecoveryKey
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HouseholdStoreTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private val dir get() = temp.resolve("Schippers.hfm")

    private fun createHousehold() =
        store.create(dir, "Schippers", "perry", "Perry", "admin-pass".toCharArray())

    @Test
    fun `create, lock and unlock with the password`() {
        val created = createHousehold()
        val sharedGroup = created.session.core.coreQueries.groups().executeAsList().single()
        addAccountWithTransaction(created.session.groupLedger(sharedGroup.id))
        created.session.close()

        store.unlock(dir, "PERRY", "admin-pass".toCharArray()).use { session ->
            assertEquals("Schippers", session.core.coreQueries.household().executeAsOne().name)
            assertEquals(Role.ADMINISTRATOR, session.role)
            val ledger = session.groupLedger(sharedGroup.id)
            assertEquals(1L, ledger.ledgerQueries.txnCount().executeAsOne())
        }
    }

    @Test
    fun `wrong password and unknown user are rejected the same way`() {
        createHousehold().session.close()
        assertFailsWith<WrongPasswordException> { store.unlock(dir, "perry", "nope".toCharArray()) }
        assertFailsWith<WrongPasswordException> { store.unlock(dir, "nobody", "admin-pass".toCharArray()) }
    }

    @Test
    fun `database files are encrypted on disk`() {
        createHousehold().session.close()
        val dbFiles = Files.list(dir).use { s -> s.filter { it.toString().endsWith(".db") }.toList() }
        assertEquals(2, dbFiles.size, "core + shared ledger")
        for (file in dbFiles) {
            val head = Files.readAllBytes(file).copyOfRange(0, 16).decodeToString()
            assertFalse(head.startsWith("SQLite format 3"), "$file must not be a plain SQLite file")
        }
        val header = Files.readString(dir.resolve("household.json"))
        assertFalse(header.contains("Schippers"), "the household name belongs in the encrypted core database")
    }

    @Test
    fun `a database cannot be opened with the wrong key`() {
        createHousehold().session.close()
        assertFailsWith<WrongKeyException> { SqlCipherJdbcDriverFactory().open(dir.resolve("core.db"), Random.key()) }
    }

    @Test
    fun `private groups are unreadable by other users, even administrators`() {
        val admin = createHousehold().session
        val member = admin.addUser("marie", "Marie", Role.MEMBER, "marie-pass".toCharArray())
        admin.close()

        val marie = store.unlock(dir, "marie", "marie-pass".toCharArray())
        val privateGroup = marie.createGroup("Marie - personal", private = true)
        addAccountWithTransaction(marie.groupLedger(privateGroup))
        val sharedGroupId = marie.core.coreQueries.groups().executeAsList().first { it.owner_user_id == null }.id
        assertFailsWith<AccessDeniedException>("members get no shared group until granted") { marie.groupLedger(sharedGroupId) }
        marie.close()

        val perry = store.unlock(dir, "perry", "admin-pass".toCharArray())
        val privatePartition = perry.core.coreQueries.groups().executeAsList().first { it.id == privateGroup }.partition_id
        assertFalse(perry.canOpen(privatePartition))
        assertFailsWith<AccessDeniedException> { perry.groupLedger(privateGroup) }
        // Perry grants Marie access to the shared group.
        perry.setPermission(sharedGroupId, member.userId, PermissionLevel.EDIT)
        perry.close()

        store.unlock(dir, "marie", "marie-pass".toCharArray()).use { again ->
            again.groupLedger(sharedGroupId)
            // Marie shares her private group with Perry, read-only.
            again.setPermission(privateGroup, again.core.coreQueries.users().executeAsList().first { it.login_name == "perry" }.id, PermissionLevel.VIEW)
        }
        store.unlock(dir, "perry", "admin-pass".toCharArray()).use { perryAgain ->
            assertEquals(1L, perryAgain.groupLedger(privateGroup).ledgerQueries.txnCount().executeAsOne())
        }
    }

    @Test
    fun `members cannot add users or create shared groups`() {
        val admin = createHousehold().session
        admin.addUser("teen", "Teen", Role.MEMBER, "teen-pass".toCharArray())
        admin.close()
        store.unlock(dir, "teen", "teen-pass".toCharArray()).use { teen ->
            assertFailsWith<AccessDeniedException> { teen.addUser("x", "X", Role.MEMBER, "x".toCharArray()) }
            assertFailsWith<AccessDeniedException> { teen.createGroup("Shared 2", private = false) }
        }
    }

    @Test
    fun `recovery key resets a forgotten password`() {
        val created = createHousehold()
        val printed = created.recoveryKey.display()
        created.session.close()

        store.resetPassword(dir, "perry", RecoveryKey.parse(printed), "new-pass".toCharArray()).close()
        assertFailsWith<WrongPasswordException> { store.unlock(dir, "perry", "admin-pass".toCharArray()) }
        store.unlock(dir, "perry", "new-pass".toCharArray()).use { session ->
            assertTrue(session.core.coreQueries.recentAudit(10).executeAsList().any { it.action == "RESET_PASSWORD" })
        }
        assertFailsWith<WrongPasswordException> {
            store.resetPassword(dir, "perry", RecoveryKey.generate(), "hacked".toCharArray())
        }
    }

    @Test
    fun `a tampered header cannot redirect grants`() {
        val created = createHousehold()
        val marie = created.session.addUser("marie", "Marie", Role.MEMBER, "marie-pass".toCharArray())
        created.session.close()
        // An attacker with write access swaps Marie's public key in the unencrypted header for Perry's,
        // hoping future grants to Marie will be readable with Perry's key instead.
        val headerFile = dir.resolve("household.json")
        val text = Files.readString(headerFile)
        val keys = Regex("\"publicKey\": \"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toList()
        Files.writeString(headerFile, text.replace(keys[1], keys[0]))

        val sharedGroupId = store.unlock(dir, "perry", "admin-pass".toCharArray()).use { perry ->
            val id = perry.core.coreQueries.groups().executeAsList().single().id
            perry.setPermission(id, marie.userId, PermissionLevel.VIEW)
            id
        }
        // The grant was sealed to Marie's real key, taken from the authenticated core database.
        store.unlock(dir, "marie", "marie-pass".toCharArray()).use { it.groupLedger(sharedGroupId) }
    }

    private fun addAccountWithTransaction(ledger: ca.schippers.hfm.data.ledger.LedgerDatabase) {
        val now = System.currentTimeMillis()
        val accountId = Ids.newId()
        ledger.ledgerQueries.insertAccount(accountId, null, "Chequing", "CHEQUING", "CAD", "****1234", null, 100_000, "2026-01-01", "OPEN", null, now, now)
        ledger.ledgerQueries.insertTxn(Ids.newId(), accountId, "2026-01-02", null, "IGA", -4_567, null, null, null, null, null, "UNCLEARED", null, null, null, null, now, now)
        assertEquals(95_433L, ledger.ledgerQueries.accountBalance(accountId).executeAsOne())
    }
}
