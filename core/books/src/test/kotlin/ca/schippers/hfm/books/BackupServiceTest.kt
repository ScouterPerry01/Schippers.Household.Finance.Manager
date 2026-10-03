package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupServiceTest {

    @TempDir
    lateinit var temp: Path

    private val factory = SqlCipherJdbcDriverFactory()
    private val store = HouseholdStore(factory, KdfParams.TESTING)
    private lateinit var books: Books
    private val home get() = temp.resolve("Home.hfm")

    @BeforeEach
    fun setUp() {
        books = Books(store.create(home, "Home", "perry", "Perry", "pw".toCharArray()).session)
        books.accounts.create(AccountDraft(books.groups().single().id, "Chequing", AccountType.CHEQUING, Currency.CAD, Money.parse("10", Currency.CAD), LocalDate(2026, 1, 1), number = "12345678"))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    @Test
    fun `nothing is due until a folder is chosen, then daily or weekly`() {
        val now = Instant.parse("2026-10-01T12:00:00Z")
        assertFalse(books.backups.isDue(now), "no folder yet")
        assertTrue(books.backups.needsReminder(now), "never backed up")

        books.backups.saveSettings(BackupSettings(temp.resolve("b"), BackupFrequency.DAILY, keep = 2))
        assertTrue(books.backups.isDue(now))
        val run = books.backups.backUpNow(factory, LocalDateTime.ofInstant(now, ZoneId.systemDefault()))
        assertTrue(run.check.ok, run.check.problems.toString())
        assertEquals(now, books.backups.status().lastSuccess)
        assertNull(books.backups.status().lastProblem)
        assertFalse(books.backups.isDue(now.plus(Duration.ofHours(5))))
        assertTrue(books.backups.isDue(now.plus(Duration.ofHours(21))))
        assertFalse(books.backups.needsReminder(now.plus(Duration.ofDays(6))))
        assertTrue(books.backups.needsReminder(now.plus(Duration.ofDays(8))))

        books.backups.saveSettings(BackupSettings(temp.resolve("b"), BackupFrequency.WEEKLY, keep = 2))
        assertFalse(books.backups.isDue(now.plus(Duration.ofDays(3))))
        books.backups.saveSettings(BackupSettings(temp.resolve("b"), BackupFrequency.OFF, keep = 2))
        assertFalse(books.backups.isDue(now.plus(Duration.ofDays(30))))
    }

    @Test
    fun `old versions beyond the number kept are pruned`() {
        books.backups.saveSettings(BackupSettings(temp.resolve("b"), BackupFrequency.DAILY, keep = 2))
        (1..4).forEach { books.backups.backUpNow(factory, LocalDateTime.of(2026, 10, it, 3, 0)) }
        assertEquals(2, books.backups.backups().size)
        assertEquals(2, books.backups.settings().keep)
    }

    @Test
    fun `a missing folder is reported`() {
        val e = runCatching { books.backups.backUpNow(factory) }.exceptionOrNull() as ValidationException
        assertEquals("error.backupFolder", e.key)
    }

    @Test
    fun `full export contains every visible table as CSV and JSON, but not others' private data`() {
        books.session.addUser("marie", "Marie", Role.MEMBER, "mpw".toCharArray())
        books.session.close()
        store.unlock(home, "marie", "mpw".toCharArray()).use { marie ->
            val marieBooks = Books(marie)
            val group = marie.createGroup("Marie private", private = true)
            marieBooks.accounts.create(AccountDraft(group, "Secret savings", AccountType.SAVINGS, Currency.CAD, Money.parse("99", Currency.CAD), LocalDate(2026, 1, 1)))
            marieBooks.documents.import(group, "Marie's letter".toByteArray(), "letter.txt", "text/plain")
        }
        books = Books(store.unlock(home, "perry", "pw".toCharArray()))
        val receipt = books.documents.import(books.groups().single().id, "receipt bytes".toByteArray(), "Costco: receipt.pdf", "application/pdf").document

        val file = temp.resolve("export.zip")
        books.backups.exportAll(file)
        ZipFile(file.toFile()).use { zip ->
            val names = zip.entries().toList().map { it.name }.toSet()
            assertTrue("README.txt" in names)
            assertTrue("core/category.csv" in names && "core.json" in names)
            assertTrue("ledger - Shared/account.csv" in names && "ledger - Shared/txn.csv" in names)
            assertTrue(names.none { it.contains("Marie private") }, "Marie's private group is not exported by Perry")
            val accounts = zip.getInputStream(zip.getEntry("ledger - Shared/account.csv")).readBytes().decodeToString()
            assertTrue(accounts.contains("Chequing") && accounts.contains("1000"))
            val categories = zip.getInputStream(zip.getEntry("core/category.csv")).readBytes().decodeToString()
            assertTrue(categories.contains("Épicerie"))
            val json = zip.getInputStream(zip.getEntry("core.json")).readBytes().decodeToString()
            assertTrue(json.contains("\"household\""))
            // EXP-01: the original document files, decrypted, but only those this user may see.
            val documents = names.filter { it.startsWith("documents/") }
            assertEquals(listOf("documents/${receipt.id}-Costco- receipt.pdf"), documents)
            assertEquals("receipt bytes", zip.getInputStream(zip.getEntry(documents.single())).readBytes().decodeToString())
        }
    }
}
