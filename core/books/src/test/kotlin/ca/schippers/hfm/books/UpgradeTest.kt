package ca.schippers.hfm.books

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import ca.schippers.hfm.data.EncryptedDriverFactory
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.core.CoreDatabase
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.data.ledger.LedgerDatabase
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.CategoryKind
import ca.schippers.hfm.domain.ClearedStatus
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * NFR-11, Phase 5 exit check: a household saved by any earlier version opens in this one with its
 * data intact, read back through the books (the services the screens use), and its upgraded
 * database has the same tables, columns and indexes as a new one.
 *
 * Each case creates a household with today's schema and fills it through the books, then rebuilds
 * one database (the ledger, or the core database) at an earlier schema version from its saved
 * snapshot, copying in every row and column that version had, and opens the household again.
 */
class UpgradeTest {

    @TempDir
    lateinit var temp: Path

    /** Remembers the key of each database file, so a test can rebuild one at an older version. */
    private class KeyKeeper(private val inner: EncryptedDriverFactory) : EncryptedDriverFactory {
        val keys = mutableMapOf<String, ByteArray>()
        override fun open(file: Path, key: ByteArray): SqlDriver {
            keys[file.fileName.toString()] = key.copyOf()
            return inner.open(file, key)
        }
    }

    private val drivers = KeyKeeper(SqlCipherJdbcDriverFactory())
    private val store = HouseholdStore(drivers, KdfParams.TESTING)
    private val password = "upgrade-pass"
    private val dir get() = temp.resolve("Upgrade.hfm")

    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun day(m: Int, d: Int) = LocalDate(2026, m, d)

    /** A household with accounts, payees, categorized and split transactions and a transfer. */
    private fun household(): String {
        val books = Books(store.create(dir, "Upgrade", "perry", "Perry", password.toCharArray()).session)
        books.session.use {
            val group = books.groups().single().id
            val chequing = books.accounts.create(AccountDraft(group, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("1000.00"), day(1, 1)))
            val savings = books.accounts.create(AccountDraft(group, "Savings", AccountType.SAVINGS, Currency.CAD, cad("250.00"), day(1, 1)))
            val expense = books.categories.list().filter { it.kind == CategoryKind.EXPENSE && it.parentId != null }.sortedBy { it.nameEn }
            val income = books.categories.list().first { it.kind == CategoryKind.INCOME }
            books.transactions.create(TransactionDraft(chequing.id, day(1, 5), cad("-45.67"), payeeName = "IGA", splits = listOf(SplitDraft(expense[0].id, cad("-45.67")))))
            books.transactions.create(TransactionDraft(chequing.id, day(1, 15), cad("2500.00"), payeeName = "Employer", splits = listOf(SplitDraft(income.id, cad("2500.00"))), cleared = ClearedStatus.CLEARED))
            books.transactions.create(
                TransactionDraft(
                    chequing.id, day(2, 3), cad("-120.00"), payeeName = "Costco", memo = "Groceries and a lamp",
                    splits = listOf(SplitDraft(expense[0].id, cad("-80.00"), memo = "Food"), SplitDraft(expense[1].id, cad("-40.00"))),
                ),
            )
            books.transactions.transfer(TransferDraft(chequing.id, savings.id, day(2, 10), cad("300.00"), memo = "Rainy day"))
            return picture(books)
        }
    }

    /** What the screens show of the household: accounts, registers, payees and reports. */
    private fun picture(books: Books): String = buildString {
        for (s in books.accounts.list(includeClosed = true).sortedBy { it.account.name }) {
            val a = s.account
            appendLine("${a.name} ${a.type} ${a.currency} ${a.openingBalance} ${a.openingDate} ${a.status} balance ${s.balance} cleared ${s.clearedBalance}")
            for (r in books.transactions.register(a.id)) {
                val t = r.transaction
                append("  ${t.date} ${t.amount} ${t.payeeText} ${t.memo} ${t.cleared} transfer=${t.transfer != null} running ${r.runningBalance}")
                appendLine(t.splits.joinToString(prefix = " [", postfix = "]") { "${it.categoryId}:${it.amount}:${it.memo}" })
            }
        }
        appendLine("payees " + books.payees.list().map { it.name }.sorted())
        appendLine("categories " + books.categories.list().size)
        val year = ReportFilter(day(1, 1), day(12, 31))
        appendLine("by category " + books.reports.byCategory(year).value.map { "${it.category?.nameEn} ${it.amount}" })
        appendLine("income and expense " + books.reports.incomeExpense(year, Granularity.MONTH).value.take(3))
        appendLine("net worth " + books.reports.netWorth(listOf(day(1, 31), day(2, 28))).value)
        appendLine("search " + books.search.search("Costco").transactions.size)
    }

    private fun reopen(): String {
        val books = Books(store.unlock(dir, "perry", password.toCharArray()))
        val picture = books.session.use { picture(books) }
        assertEquals(1L, Files.list(dir.resolve("backups/pre-upgrade")).use { it.count() }, "the upgrade ran, after a copy was saved")
        return picture
    }

    @ParameterizedTest(name = "ledger version {0}")
    @MethodSource("ledgerVersions")
    fun `a ledger from an earlier version keeps its data and gets today's schema`(version: Int) {
        val before = household()
        val file = Files.list(dir).use { s -> s.filter { it.fileName.toString().startsWith("ledger-") && it.fileName.toString().endsWith(".db") }.findFirst().get() }
        val skipped = downgrade(file, "../data/src/main/sqldelight/ledger/schemas/$version.db", version)
        assertTrue(skipped.isEmpty(), "rows of version $version that could not be copied: $skipped")
        assertEquals(before, reopen(), "ledger version $version")
        assertSameSchema(file, LedgerDatabase.Schema.version) { LedgerDatabase.Schema.create(it) }
    }

    @ParameterizedTest(name = "core version {0}")
    @MethodSource("coreVersions")
    fun `a core database from an earlier version keeps its data and gets today's schema`(version: Int) {
        val before = household()
        val file = dir.resolve("core.db")
        val skipped = downgrade(file, "../data/src/main/sqldelight/core/schemas/$version.db", version)
        assertTrue(skipped.isEmpty(), "rows of version $version that could not be copied: $skipped")
        assertEquals(before, reopen(), "core version $version")
        assertSameSchema(file, CoreDatabase.Schema.version) { CoreDatabase.Schema.create(it) }
    }

    private fun <T> SqlDriver.list(sql: String, row: (app.cash.sqldelight.db.SqlCursor) -> T): List<T> =
        executeQuery(null, sql, { c ->
            val out = ArrayList<T>()
            while (c.next().value) out += row(c)
            QueryResult.Value(out)
        }, 0).value

    private fun columns(driver: SqlDriver, table: String): List<String> = driver.list("PRAGMA table_info(\"$table\")") { it.getString(1)!! }

    /**
     * Rebuilds [file] at schema [version] from its [snapshot], with every row of today's database
     * in the tables and columns that version had. Returns the tables whose rows could not be copied.
     */
    private fun downgrade(file: Path, snapshot: String, version: Int): List<String> {
        val statements = DriverManager.getConnection("jdbc:sqlite:${Path.of(snapshot).toAbsolutePath()}").use { c ->
            c.createStatement().executeQuery("SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' ORDER BY rowid").use { r ->
                buildList { while (r.next()) add(r.getString(1)) }
            }
        }
        val key = drivers.keys.getValue(file.fileName.toString())
        val older = file.resolveSibling("older.db")
        val skipped = ArrayList<String>()
        SqlCipherJdbcDriverFactory().open(file, key).use { current ->
            SqlCipherJdbcDriverFactory().open(older, key).use { old ->
                statements.forEach { old.execute(null, it, 0) }
                old.execute(null, "PRAGMA foreign_keys = OFF", 0)
                val tables = old.list("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'") { it.getString(0)!! }
                val currentTables = current.list("SELECT name FROM sqlite_master WHERE type = 'table'") { it.getString(0)!! }.toSet()
                for (table in tables.filter { it in currentTables }) {
                    val have = columns(current, table).toSet()
                    val common = columns(old, table).filter { it in have }
                    if (common.isEmpty()) continue
                    val names = common.joinToString { "\"$it\"" }
                    val rows = current.list("SELECT " + common.joinToString(" || ',' || ") { "quote(\"$it\")" } + " FROM \"$table\"") { it.getString(0)!! }
                    try {
                        rows.forEach { old.execute(null, "INSERT INTO \"$table\"($names) VALUES ($it)", 0) }
                    } catch (e: Exception) {
                        skipped += "$table: ${e.message}"
                    }
                }
                old.execute(null, "PRAGMA user_version = $version", 0)
                old.execute(null, "PRAGMA wal_checkpoint(TRUNCATE)", 0)
            }
        }
        for (suffix in listOf("", "-wal", "-shm")) Files.deleteIfExists(file.resolveSibling(file.fileName.toString() + suffix))
        Files.move(older, file)
        Files.deleteIfExists(older.resolveSibling("older.db-wal"))
        Files.deleteIfExists(older.resolveSibling("older.db-shm"))
        return skipped
    }

    /** Tables with their columns (name, type, not null, default, key), indexes with their columns, and foreign keys. */
    private fun schema(driver: SqlDriver): Map<String, Set<String>> {
        val out = sortedMapOf<String, Set<String>>()
        val tables = driver.list("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'") { it.getString(0)!! }
        for (t in tables) {
            out["table $t"] = driver.list("PRAGMA table_info(\"$t\")") { c ->
                "${c.getString(1)} ${c.getString(2)?.uppercase()} notnull=${c.getLong(3)} default=${c.getString(4)} pk=${c.getLong(5)}"
            }.toSet()
            out["foreign keys $t"] = driver.list("PRAGMA foreign_key_list(\"$t\")") { c ->
                "${c.getString(3)} -> ${c.getString(2)}.${c.getString(4)} on delete ${c.getString(6)}"
            }.toSet()
        }
        val indexes = driver.list("SELECT name, tbl_name FROM sqlite_master WHERE type = 'index' AND sql IS NOT NULL") { it.getString(0)!! to it.getString(1)!! }
        for ((name, table) in indexes) {
            out["index $name on $table"] = setOf(driver.list("PRAGMA index_info(\"$name\")") { it.getString(2) ?: "(expression)" }.joinToString())
        }
        return out
    }

    private fun assertSameSchema(file: Path, version: Long, create: (SqlDriver) -> Unit) {
        val key = drivers.keys.getValue(file.fileName.toString())
        val upgraded = SqlCipherJdbcDriverFactory().open(file, key).use { schema(it) }
        val fresh = SqlCipherJdbcDriverFactory().open(file.resolveSibling("fresh.db"), key).use { d ->
            create(d)
            schema(d)
        }
        assertTrue(fresh.keys.count { it.startsWith("table ") } > 10, "tables are compared")
        val differences = (upgraded.keys + fresh.keys).filter { upgraded[it] != fresh[it] }
            .map { "$it\n    upgraded: ${upgraded[it]?.minus(fresh[it].orEmpty())}\n    new:      ${fresh[it]?.minus(upgraded[it].orEmpty())}" }
        assertTrue(differences.isEmpty(), "upgraded to version $version, the schema differs from a new database:\n" + differences.joinToString("\n"))
    }

    companion object {
        @JvmStatic
        fun ledgerVersions(): List<Int> = (1 until LedgerDatabase.Schema.version.toInt()).toList()

        @JvmStatic
        fun coreVersions(): List<Int> = (1 until CoreDatabase.Schema.version.toInt()).toList()
    }
}
