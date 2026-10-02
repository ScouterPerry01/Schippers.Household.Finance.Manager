package ca.schippers.hfm.data.jdbc

import ca.schippers.hfm.data.BackupException
import ca.schippers.hfm.data.Backups
import ca.schippers.hfm.data.HouseholdSession
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.security.KdfParams
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupsTest {

    @TempDir
    lateinit var temp: Path

    private val factory = SqlCipherJdbcDriverFactory()
    private val store = HouseholdStore(factory, KdfParams.TESTING)
    private val home get() = temp.resolve("Home.hfm")
    private val backups get() = temp.resolve("backups")

    private fun addTransaction(session: HouseholdSession, groupId: String, payee: String) {
        val ledger = session.groupLedger(groupId)
        val now = System.currentTimeMillis()
        val accountId = ledger.ledgerQueries.accounts().executeAsList().firstOrNull()?.id ?: Ids.newId().also {
            ledger.ledgerQueries.insertAccount(it, null, "Chequing", "CHEQUING", "CAD", null, null, 0, "2026-01-01", "OPEN", null, now, now)
        }
        ledger.ledgerQueries.insertTxn(Ids.newId(), accountId, "2026-01-02", null, payee, -1000, null, null, null, null, null, "UNCLEARED", null, null, null, null, now, now)
    }

    private fun payees(session: HouseholdSession, groupId: String) =
        session.groupLedger(groupId).ledgerQueries.register(session.groupLedger(groupId).ledgerQueries.accounts().executeAsList().single().id).executeAsList().map { it.payee_text }

    @Test
    fun `back up, check and restore to a new folder`() {
        val session = store.create(home, "Home", "perry", "Perry", "pw".toCharArray()).session
        val shared = session.core.coreQueries.groups().executeAsList().single().id
        addTransaction(session, shared, "IGA")

        val backup = Backups.create(session, backups, LocalDateTime.of(2026, 10, 1, 9, 30))
        assertEquals("Home-20261001-093000.hfmbak", backup.file.fileName.toString())
        val check = Backups.verify(backup.file, session, factory)
        assertTrue(check.ok, check.problems.toString())
        assertEquals(2, check.checkedDatabases)

        // Changes after the backup are not in it.
        addTransaction(session, shared, "Metro")
        session.close()

        val restored = Backups.restore(backup.file, temp.resolve("Restored.hfm"))
        store.unlock(restored, "perry", "pw".toCharArray()).use { again ->
            assertEquals(listOf("IGA"), payees(again, shared))
        }
    }

    @Test
    fun `the backup holds only encrypted databases and the key ring`() {
        val session = store.create(home, "Home", "perry", "Perry", "pw".toCharArray()).session
        val backup = Backups.create(session, backups)
        session.close()
        ZipFile(backup.file.toFile()).use { zip ->
            val names = zip.entries().toList().map { it.name }.toSet()
            assertTrue("household.json" in names && "core.db" in names && "manifest.json" in names)
            assertTrue(names.none { it.startsWith("backups/") }, "no backups inside backups")
            for (name in names.filter { it.endsWith(".db") }) {
                val head = zip.getInputStream(zip.getEntry(name)).readNBytes(16).decodeToString()
                assertFalse(head.startsWith("SQLite format 3"), "$name must be encrypted")
            }
        }
    }

    @Test
    fun `other users' private ledgers are included and still private`() {
        val admin = store.create(home, "Home", "perry", "Perry", "pw".toCharArray()).session
        admin.addUser("marie", "Marie", Role.MEMBER, "mpw".toCharArray())
        admin.close()
        val marie = store.unlock(home, "marie", "mpw".toCharArray())
        val private = marie.createGroup("Marie", private = true)
        addTransaction(marie, private, "Secret")
        marie.close()

        // Perry makes the backup without being able to read Marie's ledger.
        val perry = store.unlock(home, "perry", "pw".toCharArray())
        val backup = Backups.create(perry, backups)
        assertTrue(Backups.verify(backup.file, perry, factory).ok)
        perry.close()

        val restored = Backups.restore(backup.file, temp.resolve("Restored.hfm"))
        store.unlock(restored, "marie", "mpw".toCharArray()).use { assertEquals(listOf("Secret"), payees(it, private)) }
        store.unlock(restored, "perry", "pw".toCharArray()).use { assertFalse(it.canOpen(it.core.coreQueries.groups().executeAsList().first { g -> g.id == private }.partition_id)) }
    }

    @Test
    fun `damaged backups are detected and not restored`() {
        val session = store.create(home, "Home", "perry", "Perry", "pw".toCharArray()).session
        val backup = Backups.create(session, backups)
        session.close()
        val damaged = temp.resolve("damaged.hfmbak")
        val bytes = Files.readAllBytes(backup.file)
        // Flip a byte inside the stored core database (entries are stored compressed; any change breaks it).
        bytes[bytes.size / 3] = (bytes[bytes.size / 3] + 1).toByte()
        Files.write(damaged, bytes)
        assertFalse(Backups.verify(damaged).ok)
        assertFailsWith<BackupException> { Backups.restore(damaged, temp.resolve("X.hfm")) }
        assertFalse(Backups.verify(temp.resolve("nothing.hfmbak")).ok)
    }

    @Test
    fun `restore never overwrites an existing household`() {
        val session = store.create(home, "Home", "perry", "Perry", "pw".toCharArray()).session
        val backup = Backups.create(session, backups)
        session.close()
        assertFailsWith<BackupException> { Backups.restore(backup.file, home) }
    }

    @Test
    fun `old versions are pruned, newest kept`() {
        val session = store.create(home, "Home", "perry", "Perry", "pw".toCharArray()).session
        val times = (1..5).map { LocalDateTime.of(2026, 10, it, 2, 0) }
        times.forEach { Backups.create(session, backups, it) }
        session.close()
        assertEquals(5, Backups.list(backups, session.householdId).size)
        val deleted = Backups.prune(backups, session.householdId, keep = 3)
        assertEquals(2, deleted.size)
        assertEquals(listOf(5, 4, 3), Backups.list(backups, session.householdId).map { LocalDateTime.ofInstant(it.createdAt, java.time.ZoneId.systemDefault()).dayOfMonth })
    }

    @Test
    fun `writes made during a backup wait and are kept`() {
        val session = store.create(home, "Home", "perry", "Perry", "pw".toCharArray()).session
        val shared = session.core.coreQueries.groups().executeAsList().single().id
        addTransaction(session, shared, "Before")
        val writer = Thread { addTransaction(session, shared, "During") }
        Backups.create(session, backups)
        writer.start()
        writer.join()
        assertEquals(setOf("Before", "During"), payees(session, shared).toSet())
        session.setPermission(shared, session.userId, PermissionLevel.EDIT)
        session.close()
    }
}
