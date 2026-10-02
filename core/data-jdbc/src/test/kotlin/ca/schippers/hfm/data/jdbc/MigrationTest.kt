package ca.schippers.hfm.data.jdbc

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ca.schippers.hfm.data.SchemaManager
import ca.schippers.hfm.data.core.CoreDatabase
import ca.schippers.hfm.data.ledger.LedgerDatabase
import ca.schippers.hfm.security.Random
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * NFR-11: households created by an earlier version open in this one. Each test builds an
 * encrypted database at schema version 1 from the saved snapshot, fills it, and lets the
 * application upgrade it.
 */
class MigrationTest {

    @TempDir
    lateinit var temp: Path

    private val factory = SqlCipherJdbcDriverFactory()
    private val key = Random.key()

    /** Creates an encrypted database with exactly the schema of an earlier version, from its snapshot. */
    private fun older(snapshot: String, file: Path, version: Int): SqlDriver {
        val statements = JdbcSqliteDriver("jdbc:sqlite:${Path.of(snapshot).toAbsolutePath()}", Properties()).use { plain ->
            plain.executeQuery(null, "SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' ORDER BY rowid", { c ->
                val list = ArrayList<String>()
                while (c.next().value) list += c.getString(0)!!
                QueryResult.Value(list)
            }, 0).value
        }
        val driver = factory.open(file, key)
        statements.forEach { driver.execute(null, it, 0) }
        driver.execute(null, "PRAGMA user_version = $version", 0)
        return driver
    }

    private fun count(driver: SqlDriver, sql: String): Long =
        driver.executeQuery(null, sql, { c -> c.next(); QueryResult.Value(c.getLong(0)!!) }, 0).value

    @Test
    fun `core database keeps its exchange rates and accepts the second source`() {
        val file = temp.resolve("core.db")
        older("../data/src/main/sqldelight/core/schemas/1.db", file, 1).use { driver ->
            driver.execute(null, "INSERT INTO fx_rate(currency, date, cad_per_unit, source) VALUES ('USD', '2026-09-30', '1.39', 'BOC')", 0)
            driver.execute(null, "INSERT INTO fx_rate(currency, date, cad_per_unit, source) VALUES ('EUR', '2026-09-30', '1.60', 'MANUAL')", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, CoreDatabase.Schema, file)
            assertEquals(CoreDatabase.Schema.version, SchemaManager.userVersion(driver))
            val q = CoreDatabase(driver).coreQueries
            assertEquals("1.39", q.rateOnOrBefore("USD", "2026-10-01").executeAsOne().cad_per_unit)
            q.insertOpenRate("XOF", "2026-10-01", "0.0023")
            q.insertOpenRate("EUR", "2026-09-30", "9.99") // never replaces a manual rate
            assertEquals("1.60", q.rateOnOrBefore("EUR", "2026-09-30").executeAsOne().cad_per_unit)
            assertEquals("OPEN", q.rateOnOrBefore("XOF", "2026-10-01").executeAsOne().source)
        }
        assertTrue(Files.list(temp.resolve("backups/pre-upgrade")).use { it.count() } == 1L, "a copy was saved before upgrading")
    }

    @Test
    fun `ledgers keep their transactions and gain calendar and health tables`() {
        val file = temp.resolve("ledger.db")
        older("../data/src/main/sqldelight/ledger/schemas/1.db", file, 1).use { driver ->
            driver.execute(null, "INSERT INTO account(id, name, type, currency, opening_date, created_at, updated_at) VALUES ('a', 'Chequing', 'CHEQUING', 'CAD', '2026-01-01', 0, 0)", 0)
            driver.execute(null, "INSERT INTO txn(id, account_id, date, amount_minor, created_at, updated_at) VALUES ('t', 'a', '2026-01-02', -500, 0, 0)", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            assertEquals(1L, count(driver, "SELECT count(*) FROM txn"))
            val q = LedgerDatabase(driver)
            q.calendarQueries.insertEvent("e", "Garage", "VEHICLE", "2026-10-05", "09:30", 60, null, null, null, null, null, null, null, "1440,60", 0, 0)
            q.healthQueries.upsertProvider("p", "Pharmacie", "PHARMACY", null, null, null, 0)
            assertEquals(1L, count(driver, "SELECT count(*) FROM event"))
            assertEquals(1L, count(driver, "SELECT count(*) FROM health_provider"))
        }
    }

    @Test
    fun `version 2 ledgers keep calendar marks and providers, and gain vehicles and goals`() {
        val file = temp.resolve("ledger2.db")
        older("../data/src/main/sqldelight/ledger/schemas/2.db", file, 2).use { driver ->
            driver.execute(null, "INSERT INTO event(id, title, category, start_date, reminder_minutes, created_at, updated_at) VALUES ('e', 'Garage', 'VEHICLE', '2026-10-05', '1440', 0, 0)", 0)
            driver.execute(null, "INSERT INTO event_occurrence(event_id, date, status) VALUES ('e', '2026-10-05', 'DONE')", 0)
            driver.execute(null, "INSERT INTO health_provider(id, name, kind) VALUES ('p', 'Pharmacie', 'PHARMACY')", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            assertEquals(LedgerDatabase.Schema.version, SchemaManager.userVersion(driver))
            assertEquals(1L, count(driver, "SELECT count(*) FROM event_occurrence"), "marks survive the table rebuild")
            assertEquals(1L, count(driver, "SELECT count(*) FROM health_provider"))
            val q = LedgerDatabase(driver)
            q.healthQueries.upsertProvider("v", "Clinique vétérinaire", "VET", null, null, null, 0)
            q.calendarQueries.insertEvent("g", "Toilettage", "PET", "2026-10-09", null, null, null, null, null, null, null, null, null, "1440", 0, 0)
            q.calendarQueries.deleteEvent("e")
            assertEquals(0L, count(driver, "SELECT count(*) FROM event_occurrence"), "the foreign key still cascades after the rename")
            assertEquals(0L, count(driver, "SELECT count(*) FROM vehicle") + count(driver, "SELECT count(*) FROM savings_goal"))
            assertEquals(0L, count(driver, "SELECT count(*) FROM txn WHERE asset_id IS NOT NULL"))
        }
    }

    @Test
    fun `version 2 core databases gain pets`() {
        val file = temp.resolve("core2.db")
        older("../data/src/main/sqldelight/core/schemas/2.db", file, 2).close()
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, CoreDatabase.Schema, file)
            driver.execute(null, "INSERT INTO pet(id, name, species, created_at, updated_at) VALUES ('rex', 'Rex', 'DOG', 0, 0)", 0)
            assertEquals(1L, count(driver, "SELECT count(*) FROM pet"))
        }
    }
}
