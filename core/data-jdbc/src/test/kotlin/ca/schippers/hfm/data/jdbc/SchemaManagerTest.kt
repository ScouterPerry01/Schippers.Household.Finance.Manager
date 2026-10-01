package ca.schippers.hfm.data.jdbc

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import ca.schippers.hfm.data.NewerFormatException
import ca.schippers.hfm.data.SchemaManager
import ca.schippers.hfm.security.Random
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SchemaManagerTest {

    @TempDir
    lateinit var temp: Path

    /** A two-version schema: v1 creates a table, v2 adds a column. */
    private class TestSchema(override val version: Long) : SqlSchema<QueryResult.Value<Unit>> {
        override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
            driver.execute(null, "CREATE TABLE item (id INTEGER PRIMARY KEY, name TEXT)", 0)
            if (version >= 2) driver.execute(null, "ALTER TABLE item ADD COLUMN note TEXT", 0)
            return QueryResult.Unit
        }

        override fun migrate(driver: SqlDriver, oldVersion: Long, newVersion: Long, vararg callbacks: AfterVersion): QueryResult.Value<Unit> {
            if (oldVersion < 2 && newVersion >= 2) driver.execute(null, "ALTER TABLE item ADD COLUMN note TEXT", 0)
            return QueryResult.Unit
        }
    }

    private val factory = SqlCipherJdbcDriverFactory()
    private val key = Random.key()
    private val file get() = temp.resolve("ledger.db")

    @Test
    fun `upgrade backs up first and keeps the data`() {
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, TestSchema(1), file)
            driver.execute(null, "INSERT INTO item(name) VALUES ('kept')", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, TestSchema(2), file)
            assertEquals(2L, SchemaManager.userVersion(driver))
            driver.execute(null, "UPDATE item SET note = 'migrated'", 0)
        }

        val backups = Files.list(temp.resolve("backups/pre-upgrade")).use { it.toList() }
        assertEquals(1, backups.size)
        // The backup is still encrypted, is still version 1, and has the row written just before the upgrade.
        factory.open(backups.single(), key).use { driver ->
            assertEquals(1L, SchemaManager.userVersion(driver))
            val name = driver.executeQuery(null, "SELECT name FROM item", { c -> c.next(); QueryResult.Value(c.getString(0)) }, 0).value
            assertEquals("kept", name)
        }
    }

    @Test
    fun `a database from a newer version is refused`() {
        factory.open(file, key).use { SchemaManager.prepare(it, TestSchema(2), file) }
        factory.open(file, key).use { driver ->
            assertFailsWith<NewerFormatException> { SchemaManager.prepare(driver, TestSchema(1), file) }
        }
    }
}
