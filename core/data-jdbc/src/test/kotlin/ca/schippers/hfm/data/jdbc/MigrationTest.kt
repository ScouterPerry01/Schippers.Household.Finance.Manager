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
            driver.execute(null, "INSERT INTO document(id, vault_file, mime_type, sha256, captured_at) VALUES ('d', 'd.hfmdoc', 'image/jpeg', 'abc', 0)", 0)
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
            assertEquals(1L, count(driver, "SELECT count(*) FROM document WHERE status = 'FILED' AND keep_forever = 0"), "version 4: existing documents count as filed")
        }
    }

    @Test
    fun `version 4 ledgers keep their accounts and gain loans`() {
        val file = temp.resolve("ledger4.db")
        older("../data/src/main/sqldelight/ledger/schemas/4.db", file, 4).use { driver ->
            driver.execute(null, "INSERT INTO account(id, name, type, currency, opening_balance_minor, opening_date, created_at, updated_at) VALUES ('m', 'Mortgage', 'MORTGAGE', 'CAD', -50000000, '2026-01-01', 0, 0)", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            assertEquals(LedgerDatabase.Schema.version, SchemaManager.userVersion(driver))
            val q = LedgerDatabase(driver).loansQueries
            q.upsertLoan("m", 50000000, "0.05", "FIXED", "SEMI_ANNUAL", 300, "MONTHLY", "2026-02-01", null, null, "2031-01-01", 120, 25000, null, null, null, null)
            q.insertLoanChange("c", "m", "2027-01-01", "PREPAYMENT", 1000000, null, 0, null, null, 0, 0, null)
            assertEquals("2031-01-01", q.loan("m").executeAsOne().term_end)
            driver.execute(null, "PRAGMA foreign_keys = ON", 0)
            driver.execute(null, "DELETE FROM account WHERE id = 'm'", 0)
            assertEquals(0L, count(driver, "SELECT count(*) FROM loan") + count(driver, "SELECT count(*) FROM loan_change"), "loans go with their account")
        }
    }

    @Test
    fun `version 5 ledgers keep their transactions in reports and gain investments`() {
        val file = temp.resolve("ledger5.db")
        older("../data/src/main/sqldelight/ledger/schemas/5.db", file, 5).use { driver ->
            driver.execute(null, "INSERT INTO account(id, name, type, currency, opening_date, created_at, updated_at) VALUES ('b', 'Courtage', 'BROKERAGE', 'CAD', '2026-01-01', 0, 0)", 0)
            driver.execute(null, "INSERT INTO txn(id, account_id, date, amount_minor, created_at, updated_at) VALUES ('t', 'b', '2026-01-02', 2410, 0, 0)", 0)
            driver.execute(null, "INSERT INTO txn_split(id, txn_id, amount_minor) VALUES ('s', 't', 2410)", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            assertEquals(LedgerDatabase.Schema.version, SchemaManager.userVersion(driver))
            val db = LedgerDatabase(driver)
            assertEquals(1, db.ledgerQueries.categoryMonthly("2026-01-01", "2026-01-31", null, null).executeAsList().size, "existing lines are not trades")
            db.investmentsQueries.upsertSecurity("x", "XIC", "TSX", "iShares XIC", "ETF", "CAD", "EQUITY", "CANADA", "1", null, null, null, 0, 0, 0, null, null)
            db.investmentsQueries.insertInvTxn("i", "b", "2026-01-15", "BUY", null, "x", "10", "38.5", 38500, 995, 0, null, null, "T1", null, 0, 0)
            db.investmentsQueries.putPrice("x", "2026-01-31", "39", "MANUAL")
            db.ledgerQueries.setTxnInvestment("i", 1, "t")
            assertEquals(0, db.ledgerQueries.payeeDaily("2026-01-01", "2026-01-31", null, null).executeAsList().size, "trades stay out of reports")
        }
    }

    @Test
    fun `version 6 ledgers gain registered plans and pensions`() {
        val file = temp.resolve("ledger6.db")
        older("../data/src/main/sqldelight/ledger/schemas/6.db", file, 6).use { driver ->
            driver.execute(null, "INSERT INTO account(id, name, type, currency, opening_date, created_at, updated_at) VALUES ('r', 'FERR', 'RRIF', 'CAD', '2020-01-01', 0, 0)", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            assertEquals(LedgerDatabase.Schema.version, SchemaManager.userVersion(driver))
            val q = LedgerDatabase(driver).plansQueries
            q.upsertPlan("r", null, null, "0.06", null, null)
            q.putPlanValue("r", 2026, 10000000, null)
            q.upsertBeneficiary("b", "r", "SUCCESSOR_HOLDER", null, "Sam", "Spouse", null, null)
            q.upsertRoom("x", "m", "TFSA", 2026, 700000, 0, null)
            q.upsertPension("p", "m", "QPP", "RRQ", null, null, null, 65, 1, null, null, "Retraite Québec", null, 0)
            q.upsertPensionStatement("s", "p", 2025, null, null, 1240000, null, null, null)
            assertEquals(1L, count(driver, "SELECT count(*) FROM pension_statement"))
            assertEquals("Sam", q.beneficiaries("r").executeAsOne().name)
        }
    }

    @Test
    fun `version 4 core databases default to Quebec`() {
        val file = temp.resolve("core4.db")
        older("../data/src/main/sqldelight/core/schemas/4.db", file, 4).use { driver ->
            driver.execute(null, "INSERT INTO household(id, name, base_currency, default_locale, created_at) VALUES ('h', 'Maison', 'CAD', 'fr-CA', 0)", 0)
            driver.execute(null, "INSERT INTO member(id, display_name, kind, created_at) VALUES ('m', 'Léa', 'CHILD', 0)", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, CoreDatabase.Schema, file)
            val q = CoreDatabase(driver).coreQueries
            assertEquals("QC", q.household().executeAsOne().province, "existing households are in Quebec")
            assertEquals(null, q.members().executeAsOne().province)
            q.setMemberProvince("BC", "m")
            assertEquals("BC", q.members().executeAsOne().province)
        }
    }

    @Test
    fun `version 7 ledgers keep their RESP grants and accept the BC grant`() {
        val file = temp.resolve("ledger7.db")
        older("../data/src/main/sqldelight/ledger/schemas/7.db", file, 7).use { driver ->
            driver.execute(null, "INSERT INTO account(id, name, type, currency, opening_date, created_at, updated_at) VALUES ('r', 'REEE', 'RESP', 'CAD', '2020-01-01', 0, 0)", 0)
            driver.execute(null, "INSERT INTO resp_grant(id, account_id, member_id, date, kind, amount_minor) VALUES ('g', 'r', 'm', '2025-03-31', 'CESG', 50000)", 0)
            driver.execute(null, "INSERT INTO registered_plan(account_id) VALUES ('r')", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            val q = LedgerDatabase(driver).plansQueries
            assertEquals(listOf("CESG"), q.grantsFor("r").executeAsList().map { it.kind }, "grants survive the table rebuild")
            q.insertGrant("b", "r", "m", "2026-05-01", "BCTESG", 120000, null, null)
            assertEquals(2, q.grantsFor("r").executeAsList().size)
            assertEquals(null, q.plan("r").executeAsOne().jurisdiction)
            driver.execute(null, "PRAGMA foreign_keys = ON", 0)
            driver.execute(null, "DELETE FROM account WHERE id = 'r'", 0)
            assertEquals(0L, count(driver, "SELECT count(*) FROM resp_grant"), "the foreign key still cascades after the rebuild")
        }
    }

    @Test
    fun `version 5 core databases keep their rates and accept market prices`() {
        val file = temp.resolve("core5.db")
        older("../data/src/main/sqldelight/core/schemas/5.db", file, 5).use { driver ->
            driver.execute(null, "INSERT INTO fx_rate(currency, date, cad_per_unit, source) VALUES ('USD', '2026-09-30', '1.39', 'BOC')", 0)
            driver.execute(null, "INSERT INTO fx_rate(currency, date, cad_per_unit, source) VALUES ('BTC', '2026-09-30', '80000', 'MANUAL')", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, CoreDatabase.Schema, file)
            val q = CoreDatabase(driver).coreQueries
            assertEquals("1.39", q.rateOnOrBefore("USD", "2026-10-01").executeAsOne().cad_per_unit)
            q.insertMarketRate("BTC", "2026-09-30", "86000")
            q.insertMarketRate("BTC", "2026-10-01", "86500")
            assertEquals("80000", q.rateOnOrBefore("BTC", "2026-09-30").executeAsOne().cad_per_unit, "manual stays")
            assertEquals("MARKET", q.rateOnOrBefore("BTC", "2026-10-01").executeAsOne().source)
            q.putSpot("GOLD", "2026-10-01", "3699.07", "MARKET")
            assertEquals("3699.07", q.spotOnOrBefore("GOLD", "2026-10-02").executeAsOne().cad_per_oz)
        }
    }

    @Test
    fun `version 8 ledgers gain wallet details`() {
        val file = temp.resolve("ledger8.db")
        older("../data/src/main/sqldelight/ledger/schemas/8.db", file, 8).use { driver ->
            driver.execute(null, "INSERT INTO account(id, name, type, currency, opening_date, created_at, updated_at) VALUES ('w', 'Cold', 'CRYPTO_WALLET', 'BTC', '2020-01-01', 0, 0)", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            val q = LedgerDatabase(driver).cryptoQueries
            q.upsertWallet("w", 150000, "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu", "ADDRESS", null, null)
            assertEquals("ADDRESS", q.wallet("w").executeAsOne().watch_kind)
        }
    }

    @Test
    fun `version 9 ledgers gain precious metal items`() {
        val file = temp.resolve("ledger9.db")
        older("../data/src/main/sqldelight/ledger/schemas/9.db", file, 9).use { driver ->
            driver.execute(null, "INSERT INTO account(id, name, type, currency, opening_date, created_at, updated_at) VALUES ('m', 'Métaux', 'PRECIOUS_METALS', 'CAD', '2020-01-01', 0, 0)", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            val q = LedgerDatabase(driver).metalsQueries
            q.upsertMetalItem("i", "m", "GOLD", "COIN", "Maple Leaf", "1", "OZT", "0.9999", 2, null, null, null, 620000, null, "BANK_BOX", null, 1, null, null, null, null, 0, 0)
            assertEquals("Maple Leaf", q.metalItems("m").executeAsOne().description)
        }
    }

    @Test
    fun `version 10 ledgers gain fund mixes`() {
        val file = temp.resolve("ledger10.db")
        older("../data/src/main/sqldelight/ledger/schemas/10.db", file, 10).use { driver ->
            driver.execute(null, "INSERT INTO security(id, name, kind, currency, asset_class, region, created_at, updated_at) VALUES ('s', 'XBAL', 'ETF', 'CAD', 'BALANCED', 'GLOBAL', 0, 0)", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            val q = LedgerDatabase(driver).investmentsQueries
            val kept = q.securityById("s").executeAsOne()
            assertEquals("XBAL", kept.name)
            assertEquals(null, kept.class_mix)
            q.upsertSecurity("s", null, null, "XBAL", "ETF", "CAD", "BALANCED", "GLOBAL", "1", null, null, null, 0, 0, 0, "{\"EQUITY\":\"60\",\"FIXED_INCOME\":\"40\"}", null)
            assertEquals("{\"EQUITY\":\"60\",\"FIXED_INCOME\":\"40\"}", q.securityById("s").executeAsOne().class_mix)
        }
    }

    @Test
    fun `version 11 ledgers gain tax slips`() {
        val file = temp.resolve("ledger11.db")
        older("../data/src/main/sqldelight/ledger/schemas/11.db", file, 11).use { driver ->
            driver.execute(null, "INSERT INTO account(id, name, type, currency, opening_date, created_at, updated_at) VALUES ('b', 'Courtage', 'BROKERAGE', 'CAD', '2020-01-01', 0, 0)", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            val q = LedgerDatabase(driver).taxSlipsQueries
            q.upsertSlip("s", "b", 2026, "T5", "", "{\"13\":\"42.00\"}", null, 0, 0)
            assertEquals("{\"13\":\"42.00\"}", q.slipByKey("b", 2026, "T5", "").executeAsOne().boxes)
        }
    }

    @Test
    fun `version 12 ledgers gain cardholders and card benefits`() {
        val file = temp.resolve("ledger12.db")
        older("../data/src/main/sqldelight/ledger/schemas/12.db", file, 12).use { driver ->
            driver.execute(null, "INSERT INTO account(id, name, type, currency, opening_date, created_at, updated_at) VALUES ('v', 'Visa', 'CREDIT_CARD', 'CAD', '2020-01-01', 0, 0)", 0)
            driver.execute(null, "INSERT INTO txn(id, account_id, date, amount_minor, created_at, updated_at) VALUES ('t', 'v', '2026-01-10', -5000, 0, 0)", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            val db = LedgerDatabase(driver)
            db.cardsQueries.upsertCardHolder("h", "v", null, "Sam", "1234", 0, 0, 0, 0)
            db.cardsQueries.setTxnCardHolder("h", "t")
            assertEquals("h", db.ledgerQueries.txnById("t").executeAsOne().card_holder_id)
            db.cardsQueries.upsertCardBenefit("b", "v", "PURCHASE_PROTECTION", null, 90, null, null, null, null, 0, 0)
            assertEquals(1, db.cardsQueries.cardBenefits("v").executeAsList().size)
        }
    }

    @Test
    fun `version 13 ledgers gain medical plans and claims`() {
        val file = temp.resolve("ledger13.db")
        older("../data/src/main/sqldelight/ledger/schemas/13.db", file, 13).close()
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            val q = LedgerDatabase(driver).medicalQueries
            q.upsertPlan("p", "DENTAL", "Dental", null, null, null, null, 1, 1, 365, null, 1, null, 0, 0, "AFTER_SERVICE")
            q.upsertExpense("e", "m", null, "DENTAL_BASIC", "2026-01-10", null, null, 12000, null, null, 1, 0, null, 0, 0)
            q.upsertClaim("c", "e", "p", "SUBMITTED", "2026-01-11", 12000, null, null, null, null, null, 0, 0)
            assertEquals(1, q.claimsForExpense("e").executeAsList().size)
        }
    }

    @Test
    fun `version 14 ledgers gain assets, warranties and insurance`() {
        val file = temp.resolve("ledger14.db")
        older("../data/src/main/sqldelight/ledger/schemas/14.db", file, 14).close()
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            val q = LedgerDatabase(driver).assetsQueries
            q.upsertAsset("a", null, "HOME", "Maison", null, null, null, null, null, null, "CAD", null, null, null, "MANUAL", 65000000, null, null, null, 1, "ACTIVE", null, null, null, null, 0, 0, null)
            q.upsertPolicy("p", "HOME", "Desjardins", null, null, null, 120000, "ANNUAL", null, null, null, null, "2026-11-01", 1, null, 0, 0)
            q.addPolicyAsset("p", "a")
            assertEquals(listOf("a"), q.policyAssets("p").executeAsList())
        }
    }

    @Test
    fun `version 15 ledgers gain asset maintenance, kept when the asset is saved again`() {
        val file = temp.resolve("ledger15.db")
        older("../data/src/main/sqldelight/ledger/schemas/15.db", file, 15).close()
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            val db = LedgerDatabase(driver)
            fun saveBoat() = db.assetsQueries.upsertAsset("b", null, "BOAT", "Bateau", null, null, null, null, null, null, "CAD", null, null, null, "NONE", null, null, null, null, 0, "ACTIVE", null, null, null, null, 0, 0, "HOURS")
            saveBoat()
            val q = db.assetMaintenanceQueries
            q.upsertTask("t", "b", "Vidange", null, 12, 100, "2026-05-01", 0, 14, 10, 1, null)
            q.insertReading("r", "b", "2026-08-01", 42, null)
            saveBoat()
            assertEquals(1, q.tasks("b").executeAsList().size)
            assertEquals(1, q.readings("b").executeAsList().size)
            assertEquals("HOURS", db.assetsQueries.assetById("b").executeAsOne().meter)
        }
    }

    @Test
    fun `version 17 ledgers keep their documents and gain AI readings and the usage log`() {
        val file = temp.resolve("ledger17.db")
        older("../data/src/main/sqldelight/ledger/schemas/17.db", file, 17).use { driver ->
            driver.execute(null, "INSERT INTO document(id, vault_file, mime_type, sha256, captured_at, status) VALUES ('d', 'd', 'image/jpeg', 'x', 0, 'INBOX')", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            assertEquals(LedgerDatabase.Schema.version, SchemaManager.userVersion(driver))
            val q = LedgerDatabase(driver).aiQueries
            q.saveDocumentAi("d", "receipt", "hfm/receipt/v1", "{}", 1, "claude-opus-5-5", 1, "u")
            q.saveDocumentAi("d", "receipt", "hfm/receipt/v1", """{"total":1}""", 1, "claude-opus-5-5", 2, "u")
            q.insertAiUsage("a", 1, "u", "d", "anthropic", "claude-opus-5-5", "receipt", 1500, 200, 10000, 1)
            assertEquals("""{"total":1}""", q.documentAi("d").executeAsOne().answer)
            driver.execute(null, "DELETE FROM document WHERE id = 'd'", 0)
            assertEquals(null, q.documentAi("d").executeAsOneOrNull(), "the reading goes with its document")
            assertEquals(null, q.aiUsageBetween(0, 10).executeAsOne().document_id, "the billed request stays in the log")
        }
    }

    @Test
    fun `version 18 ledgers gain what was learned from corrections`() {
        val file = temp.resolve("ledger18.db")
        older("../data/src/main/sqldelight/ledger/schemas/18.db", file, 18).close()
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            assertEquals(LedgerDatabase.Schema.version, SchemaManager.userVersion(driver))
            val q = LedgerDatabase(driver).learningQueries
            q.rememberMerchant("iga extra famille", "IGA", null, null, 1)
            q.rememberMerchant("iga extra famille", null, "RECEIPT", "cat", 2)
            val m = q.merchantMemory("iga extra famille").executeAsOne()
            assertEquals(listOf("IGA", "RECEIPT", "cat"), listOf(m.merchant, m.kind, m.category_id), "later corrections add to earlier ones")
            assertEquals(2L, m.uses)
        }
    }

    @Test
    fun `version 19 ledgers keep their transactions and gain sales taxes and refund links`() {
        val file = temp.resolve("ledger19.db")
        older("../data/src/main/sqldelight/ledger/schemas/19.db", file, 19).use { driver ->
            driver.execute(null, "INSERT INTO account(id, name, type, currency, opening_balance_minor, opening_date, created_at, updated_at) VALUES ('a', 'Visa', 'CREDIT_CARD', 'CAD', 0, '2026-01-01', 0, 0)", 0)
            driver.execute(null, "INSERT INTO txn(id, account_id, date, amount_minor, created_at, updated_at) VALUES ('p', 'a', '2026-09-10', -30000, 0, 0)", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            assertEquals(LedgerDatabase.Schema.version, SchemaManager.userVersion(driver))
            driver.execute(null, "INSERT INTO txn(id, account_id, date, amount_minor, created_at, updated_at) VALUES ('r', 'a', '2026-09-20', 9000, 0, 0)", 0)
            val q = LedgerDatabase(driver).salesTaxQueries
            q.setRefundOf("p", "r")
            q.insertSalesTax("p", "HST", 3451)
            assertEquals(listOf("r"), q.refundsOf("p").executeAsList())
            assertEquals(1, q.salesTaxesFor("p").executeAsList().size)
            driver.execute(null, "DELETE FROM txn WHERE id = 'p'", 0)
            assertEquals(0, q.salesTaxesFor("p").executeAsList().size, "the taxes go with their transaction")
            assertEquals(1L, count(driver, "SELECT count(*) FROM txn WHERE id = 'r' AND refund_of IS NULL"), "the refund stays, unlinked")
        }
    }

    @Test
    fun `version 20 ledgers keep their donations and gain their receipts`() {
        val file = temp.resolve("ledger20.db")
        older("../data/src/main/sqldelight/ledger/schemas/20.db", file, 20).use { driver ->
            driver.execute(null, "INSERT INTO account(id, name, type, currency, opening_balance_minor, opening_date, created_at, updated_at) VALUES ('a', 'Chequing', 'CHEQUING', 'CAD', 0, '2026-01-01', 0, 0)", 0)
            driver.execute(null, "INSERT INTO txn(id, account_id, date, amount_minor, created_at, updated_at) VALUES ('d', 'a', '2026-11-30', -25000, 0, 0)", 0)
            driver.execute(null, "INSERT INTO txn_split(id, txn_id, category_id, amount_minor, tax_flag) VALUES ('s', 'd', 'charity', -25000, NULL)", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            assertEquals(LedgerDatabase.Schema.version, SchemaManager.userVersion(driver))
            val q = LedgerDatabase(driver).donationQueries
            assertEquals(listOf("d"), q.donationSplits("2026-01-01", "2026-12-31", listOf("charity")).executeAsList().map { it.txn_id })
            q.upsertDonation("d", "Food Bank", "123456789RR0001", "A-1", null, 0)
            q.upsertDonation("d", "Food Bank", "123456789RR0001", "A-1", 20000, 1)
            assertEquals(20000L, q.donationsFor(listOf("d")).executeAsOne().eligible_minor, "updated in place")
            driver.execute(null, "DELETE FROM txn WHERE id = 'd'", 0)
            assertEquals(0, q.donationsFor(listOf("d")).executeAsList().size, "the receipt details go with their transaction")
        }
    }

    @Test
    fun `version 21 ledgers gain the slip checklist and tax instalments`() {
        val file = temp.resolve("ledger21.db")
        older("../data/src/main/sqldelight/ledger/schemas/21.db", file, 21).use { driver ->
            driver.execute(null, "INSERT INTO account(id, name, type, currency, opening_balance_minor, opening_date, created_at, updated_at) VALUES ('a', 'Chequing', 'CHEQUING', 'CAD', 0, '2026-01-01', 0, 0)", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            assertEquals(LedgerDatabase.Schema.version, SchemaManager.userVersion(driver))
            val q = LedgerDatabase(driver).taxYearQueries
            q.upsertSlipCheck(2026, "m", "t4:employer inc", "T4", "Employer Inc.", "EXPECTED", 0)
            q.upsertSlipCheck(2026, "m", "t4:employer inc", "T4", "Employer Inc.", "RECEIVED", 0)
            assertEquals("RECEIVED", q.slipChecks(2026).executeAsOne().status, "updated in place")
            q.insertInstalment("a", "m", 2026, "CRA", "2026-03-15", 120000)
            assertEquals(1, q.instalments(2026).executeAsList().size)
            driver.execute(null, "DELETE FROM account WHERE id = 'a'", 0)
            assertEquals(0, q.instalments(2026).executeAsList().size, "instalments go with their account")
        }
    }

    @Test
    fun `version 22 ledgers gain estate records`() {
        val file = temp.resolve("ledger22.db")
        older("../data/src/main/sqldelight/ledger/schemas/22.db", file, 22).close()
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            assertEquals(LedgerDatabase.Schema.version, SchemaManager.userVersion(driver))
            val q = LedgerDatabase(driver).estateQueries
            q.upsertEstateRecord("e", "m", "{}", 0)
            q.upsertEstateRecord("e", "m", """{"willLocation":"Notary"}""", 1)
            assertEquals("""{"willLocation":"Notary"}""", q.estateRecords().executeAsOne().plan, "updated in place")
        }
    }

    @Test
    fun `version 23 ledgers gain shared expenses, family loans and allowances`() {
        val file = temp.resolve("ledger23.db")
        older("../data/src/main/sqldelight/ledger/schemas/23.db", file, 23).close()
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            assertEquals(LedgerDatabase.Schema.version, SchemaManager.userVersion(driver))
            val q = LedgerDatabase(driver).familyMoneyQueries
            q.upsertShareGroup("g", "Trip", "CAD", 0, 0)
            q.upsertSharePerson("p", "g", "Paul", null)
            q.upsertShareEntry("e", "g", "2026-07-01", "Gas", 6000, "p", """{"p":1}""", null)
            q.deleteShareGroup("g")
            assertEquals(0, q.sharePeople("g").executeAsList().size + q.shareEntries("g").executeAsList().size, "people and entries go with their group")
            q.upsertFamilyLoan("l", "Mom", "Maya", 100000, "CAD", "2026-01-01", 0, null, 0)
            q.insertLoanPayment("lp", "l", "2026-02-01", 10000, null)
            assertEquals(1, q.loanPayments("l").executeAsList().size)
            q.upsertAllowance("a", "m", 1000, "CAD", "WEEKLY", "2026-09-01", null, null)
            q.insertAllowanceEntry("ae", "a", "2026-09-07", 1000, "PAID", null)
            assertEquals(1, q.allowanceEntries("a").executeAsList().size)
        }
    }

    @Test
    fun `version 24 ledgers gain trips, contractors, projects, invoices, rentals and rewards`() {
        val file = temp.resolve("ledger24.db")
        older("../data/src/main/sqldelight/ledger/schemas/24.db", file, 24).use { driver ->
            driver.execute(null, "INSERT INTO account(id, name, type, currency, opening_balance_minor, opening_date, created_at, updated_at) VALUES ('v', 'Visa', 'CREDIT_CARD', 'CAD', 0, '2026-01-01', 0, 0)", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            assertEquals(LedgerDatabase.Schema.version, SchemaManager.userVersion(driver))
            val q = LedgerDatabase(driver).extrasQueries
            q.upsertTrip("t", "2026-10-01", null, null, "Home", "Client", 425, 1, "BUSINESS", null)
            assertEquals(1, q.trips("2026-01-01", "2026-12-31").executeAsList().size)
            q.upsertContractor("c", "Plombier", "Plumbing", null, null, null, null, 0)
            q.upsertContractorJob("j", "c", "2026-05-01", "Water heater", 180000, "CAD", 5, null, null)
            q.deleteContractor("c")
            assertEquals(0, q.contractorJobs("c").executeAsList().size, "jobs go with their contractor")
            q.upsertInvoice("i", "2026-001", "Mme Roy", null, "2026-10-01", null, "CAD", "SENT", null, null, "[]", null, null)
            q.upsertCardReward("v", "Aeroplan", "POINTS", "1.5", "0.015", null)
            q.insertRewardEntry("r", "v", "2026-09-30", "1200", "EARNED", null, null)
            driver.execute(null, "DELETE FROM account WHERE id = 'v'", 0)
            assertEquals(0, q.cardRewards().executeAsList().size + q.rewardEntries("v").executeAsList().size, "rewards go with their card")
        }
    }

    @Test
    fun `version 6 core databases gain saved reports`() {
        val file = temp.resolve("core6.db")
        older("../data/src/main/sqldelight/core/schemas/6.db", file, 6).use { driver ->
            driver.execute(null, "INSERT INTO app_user(id, login_name, display_name, role, public_key, created_at) VALUES ('u', 'perry', 'Perry', 'ADMINISTRATOR', x'00', 0)", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, CoreDatabase.Schema, file)
            assertEquals(CoreDatabase.Schema.version, SchemaManager.userVersion(driver))
            val q = CoreDatabase(driver).savedReportQueries
            q.upsertSavedReport("r", "u", "Cottage", "{}", null, null, null, 0)
            q.upsertSavedReport("r", "u", "Cottage costs", "{}", "MONTHLY", "/tmp", null, 0)
            assertEquals("Cottage costs" to "MONTHLY", q.savedReports("u").executeAsOne().let { it.name to it.schedule })
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

    @Test
    fun `version 25 ledgers keep their donation receipts and gain receipts per gift`() {
        val file = temp.resolve("ledger25.db")
        older("../data/src/main/sqldelight/ledger/schemas/25.db", file, 25).use { driver ->
            driver.execute(null, "INSERT INTO account(id, name, type, currency, opening_date, created_at, updated_at) VALUES ('a', 'Chequing', 'CHEQUING', 'CAD', '2026-01-01', 0, 0)", 0)
            driver.execute(null, "INSERT INTO txn(id, account_id, date, amount_minor, created_at, updated_at) VALUES ('t', 'a', '2026-05-09', -30000, 0, 0)", 0)
            driver.execute(null, "INSERT INTO donation(txn_id, charity, eligible_minor, received) VALUES ('t', 'Hospital Foundation', 15000, 1)", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            assertEquals(LedgerDatabase.Schema.version, SchemaManager.userVersion(driver))
            val q = LedgerDatabase(driver).donationQueries
            assertEquals(15000L, q.donationsFor(listOf("t")).executeAsOne().eligible_minor, "the receipt kept for the whole payment stays")
            q.upsertDonationReceipt("t", "sam", "CHARITABLE", "Hospital Foundation", null, "S-1", 12000, 1)
            assertEquals("S-1", q.donationReceiptsFor(listOf("t")).executeAsOne().receipt_number)
        }
    }

    @Test
    fun `version 26 ledgers keep their slip checklist and gain the estimate's figures`() {
        val file = temp.resolve("ledger26.db")
        older("../data/src/main/sqldelight/ledger/schemas/26.db", file, 26).use { driver ->
            driver.execute(null, "INSERT INTO tax_slip_check(tax_year, member_id, slip_key, slip_type, issuer, status, manual) VALUES (2025, 'sam', 't2202:college', 'T2202', 'College', 'RECEIVED', 0)", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            assertEquals(LedgerDatabase.Schema.version, SchemaManager.userVersion(driver))
            val q = LedgerDatabase(driver).taxYearQueries
            assertEquals("RECEIVED", q.slipChecks(2025).executeAsOne().status, "the checklist stays")
            q.upsertEstimateFigure("sam", 2025, "TUITION_CARRIED", 420000)
            q.upsertEstimateFigure("sam", 2025, "TUITION_CARRIED", 380000)
            q.upsertEstimateFigure("sam", 2025, "EMPLOYMENT", 5200000)
            assertEquals(380000L, q.estimateFigures("sam", 2025).executeAsList().first { it.figure == "TUITION_CARRIED" }.amount_minor, "updated in place")
            q.deleteEstimateFigure("sam", 2025, "EMPLOYMENT")
            assertEquals(1, q.estimateFigures("sam", 2025).executeAsList().size)
        }
    }

    @Test
    fun `version 27 ledgers keep their providers, contractors and policies and gain contacts`() {
        val file = temp.resolve("ledger27.db")
        older("../data/src/main/sqldelight/ledger/schemas/27.db", file, 27).use { driver ->
            driver.execute(null, "INSERT INTO health_provider(id, name, kind, phone) VALUES ('p', 'Pharmacie Roy', 'PHARMACY', '418 555-0199')", 0)
            driver.execute(null, "INSERT INTO contractor(id, name, trade) VALUES ('c', 'Plomberie Roy', 'Plumber')", 0)
            driver.execute(null, "INSERT INTO tax_estimate_figure(member_id, tax_year, figure, amount_minor) VALUES ('sam', 2025, 'EMPLOYMENT', 5200000)", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            assertEquals(LedgerDatabase.Schema.version, SchemaManager.userVersion(driver))
            val db = LedgerDatabase(driver)
            assertEquals("418 555-0199", db.healthQueries.providers().executeAsOne().phone, "providers stay")
            assertEquals("Plumber", db.extrasQueries.contractors().executeAsOne().trade, "contractors stay")
            assertEquals(1L, count(driver, "SELECT count(*) FROM tax_estimate_figure"))
            val q = db.contactsQueries
            q.upsertContact("k", "Pharmacie Roy", 0, null, null, "Sam's pharmacy", "PHARMACY", "sam", null, null, null, null, 0, 0, 0)
            q.upsertDetail("d", "k", "NUMBER", "Client", "778899", "•••• 8899", 0)
            q.insertLink("l", "k", "SAME_AS", "HEALTH_PROVIDER", "p", 0)
            q.upsertContact("k", "Pharmacie Roy", 0, null, null, "Pharmacy", "PHARMACY", "sam", null, null, null, null, 0, 0, 1)
            assertEquals(1L, count(driver, "SELECT count(*) FROM contact_link"), "saving the contact again keeps its links")
            assertEquals(1L, count(driver, "SELECT count(*) FROM contact_detail"), "and its details")
            assertEquals("k", q.linksTo("HEALTH_PROVIDER", "p").executeAsOne().contact_id)
        }
    }

    @Test
    fun `version 28 ledgers keep their contacts and gain contacts waiting from the phone`() {
        val file = temp.resolve("ledger28.db")
        older("../data/src/main/sqldelight/ledger/schemas/28.db", file, 28).use { driver ->
            driver.execute(null, "INSERT INTO contact(id, name, kinds, created_at, updated_at) VALUES ('k', 'Pharmacie Roy', 'PHARMACY', 0, 0)", 0)
            driver.execute(null, "INSERT INTO contact_detail(id, contact_id, kind, content) VALUES ('d', 'k', 'PHONE', '418 555-0199')", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            assertEquals(LedgerDatabase.Schema.version, SchemaManager.userVersion(driver))
            val db = LedgerDatabase(driver)
            assertEquals("418 555-0199", db.contactsQueries.detailsFor("k").executeAsOne().content, "contacts stay")
            val q = db.phoneContactsQueries
            q.insertPhoneContact("p", "pixel-8", 5, "{\"name\":\"Plomberie Roy\"}")
            q.insertPhoneContact("p", "pixel-8", 6, "{}")
            assertEquals(1L, q.countPhoneContacts().executeAsOne(), "received once")
            assertEquals(5L, q.phoneContactById("p").executeAsOne().received_at)
        }
    }

    @Test
    fun `version 29 ledgers keep their medical plans and gain the plan year deadline`() {
        val file = temp.resolve("ledger29.db")
        older("../data/src/main/sqldelight/ledger/schemas/29.db", file, 29).use { driver ->
            driver.execute(null, "INSERT INTO med_plan(id, kind, name, claim_days, created_at, updated_at) VALUES ('m', 'GROUP_HEALTH', 'Sun Life', 365, 0, 0)", 0)
            driver.execute(null, "INSERT INTO med_plan_person(plan_id, member_id) VALUES ('m', 'alex')", 0)
            driver.execute(null, "INSERT INTO vehicle(id, name, status, disposal_date, disposal_price_minor, created_at, updated_at) VALUES ('v', 'Civic', 'SOLD', '2026-09-01', 1850000, 0, 0)", 0)
        }
        factory.open(file, key).use { driver ->
            SchemaManager.prepare(driver, LedgerDatabase.Schema, file)
            assertEquals(30L, LedgerDatabase.Schema.version)
            assertEquals(LedgerDatabase.Schema.version, SchemaManager.userVersion(driver))
            val q = LedgerDatabase(driver).medicalQueries
            assertEquals("AFTER_SERVICE", q.planById("m").executeAsOne().claim_rule, "existing plans keep counting from the service")
            q.upsertPlan("m", "GROUP_HEALTH", "Sun Life", null, null, null, null, 1, 1, 90, null, 1, null, 0, 1, "AFTER_PLAN_YEAR")
            assertEquals("AFTER_PLAN_YEAR", q.planById("m").executeAsOne().claim_rule)
            assertEquals(1L, count(driver, "SELECT count(*) FROM med_plan_person"), "saving the plan again keeps its people")
            val vehicles = LedgerDatabase(driver).vehiclesQueries
            assertEquals(null, vehicles.vehicleById("v").executeAsOne().disposal_txn_id, "a vehicle sold before has no linked deposit")
            assertEquals(1850000L, vehicles.vehicleById("v").executeAsOne().disposal_price_minor)
        }
    }
}
