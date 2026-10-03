package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.ClearedStatus
import ca.schippers.hfm.importers.CsvImporter
import ca.schippers.hfm.importers.CsvMapping
import ca.schippers.hfm.importers.ImportOptions
import ca.schippers.hfm.importers.ImportedLine
import ca.schippers.hfm.importers.ImportedStatement
import ca.schippers.hfm.importers.OfxImporter
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatementServiceTest {

    @TempDir
    lateinit var temp: Path

    private lateinit var books: Books
    private lateinit var account: Account
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun march(d: Int) = LocalDate(2026, 3, d)
    private fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id

    private val ofx = """
        OFXHEADER:100
        CHARSET:1252

        <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><CURDEF>CAD
        <BANKACCTFROM><ACCTID>0045678</BANKACCTFROM>
        <BANKTRANLIST><DTSTART>20260301<DTEND>20260331
        <STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20260301<TRNAMT>3150.00<FITID>F1<NAME>PAIE EMPLOYEUR INC</STMTTRN>
        <STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20260312<TRNAMT>-132.48<FITID>F2<NAME>HYDRO-QUEBEC</STMTTRN>
        <STMTTRN><TRNTYPE>CHECK<DTPOSTED>20260315<TRNAMT>-1450.00<FITID>F3<CHECKNUM>107<NAME>CHEQUE 107</STMTTRN>
        <STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20260318<TRNAMT>-95.00<FITID>F4<NAME>VIDEOTRON LTEE</STMTTRN>
        </BANKTRANLIST><LEDGERBAL><BALAMT>3922.52<DTASOF>20260331</LEDGERBAL></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
        books = Books(store.create(temp.resolve("T.hfm"), "T", "perry", "Perry", "admin-pass".toCharArray()).session)
        account = books.accounts.create(AccountDraft(books.groups().single().id, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("2450.00"), march(1)))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun parsed() = OfxImporter().read(ofx.byteInputStream()).single()

    @Test
    fun `import matches captures, proposes near matches and creates the rest`() {
        books.rules.create("PAIE", cat("income.employment.salary"))
        // Recorded earlier, e.g. captured on the phone:
        val hydro = books.transactions.create(TransactionDraft(account.id, march(11), cad("-132.48"), "Hydro-Québec"))
        val videotron = books.transactions.create(TransactionDraft(account.id, march(14), cad("-95.00"), "Vidéotron"))

        val result = books.statements.import(account.id, parsed(), "march.ofx", ofx.toByteArray())
        assertEquals(ImportResult(result.statementId, created = 2, matched = 1, proposed = 1, duplicates = 0), result)
        assertEquals(ClearedStatus.CLEARED, books.transactions.get(hydro.id).cleared)
        assertEquals(ClearedStatus.UNCLEARED, books.transactions.get(videotron.id).cleared, "proposed matches wait for confirmation")

        val salary = books.transactions.register(account.id).map { it.transaction }.first { it.amount == cad("3150.00") }
        assertEquals(cat("income.employment.salary"), salary.splits.single().categoryId, "categorized by rule")
        assertEquals("Paie Employeur Inc", salary.payeeText, "capitals softened for a new payee")
        assertEquals(ClearedStatus.CLEARED, salary.cleared)

        val view = books.statements.view(result.statementId)
        assertEquals(cad("4017.52"), view.clearedBalance)
        assertEquals(cad("-95.00"), view.difference)
        assertEquals(1, view.unresolved.size)
        assertFalse(view.canFinish)

        books.statements.confirm(view.unresolved.single().id)
        val after = books.statements.view(result.statementId)
        assertEquals(cad("0.00"), after.difference)
        assertTrue(after.canFinish)
    }

    @Test
    fun `finishing locks the period and undo reopens it`() {
        val result = books.statements.import(account.id, parsed(), "march.ofx", ofx.toByteArray())
        val report = books.statements.finish(result.statementId)
        assertEquals(4, report.cleared.size)
        assertTrue(books.transactions.register(account.id).all { it.transaction.cleared == ClearedStatus.RECONCILED })
        assertEquals(march(31), books.statements.lastReconciled()[account.id])
        assertEquals(report, books.statements.report(result.statementId))

        val locked = runCatching { books.statements.updateBalances(result.statementId, null, march(31), null, cad("1")) }
        assertEquals("error.statementLocked", (locked.exceptionOrNull() as ValidationException).key)

        val noReason = runCatching { books.statements.undo(result.statementId, " ") }
        assertEquals("error.reasonRequired", (noReason.exceptionOrNull() as ValidationException).key)
        books.statements.undo(result.statementId, "Wrong statement month")
        assertTrue(books.transactions.register(account.id).all { it.transaction.cleared == ClearedStatus.CLEARED })
        assertEquals(StatementStatus.UNDONE, books.statements.statement(result.statementId).status)
        assertNull(books.statements.lastReconciled()[account.id])
    }

    @Test
    fun `the same file or overlapping lines are never imported twice`() {
        books.statements.import(account.id, parsed(), "march.ofx", ofx.toByteArray())
        val again = runCatching { books.statements.import(account.id, parsed(), "march.ofx", ofx.toByteArray()) }
        assertEquals("error.statementAlreadyImported", (again.exceptionOrNull() as ValidationException).key)

        // A different download covering the same transactions.
        val overlap = books.statements.import(account.id, parsed(), "march-again.ofx", (ofx + " ").toByteArray())
        assertEquals(4, overlap.duplicates)
        assertEquals(4, books.transactions.register(account.id).size)
    }

    @Test
    fun `identical CSV lines on the same day both import, once`() {
        val csv = "Date,Description,Amount\n2026-03-05,TIM HORTONS #123,-2.15\n2026-03-05,TIM HORTONS #123,-2.15\n"
        val mapping = CsvMapping(dateColumn = 0, payeeColumn = 1, amountColumn = 2).toMap()
        fun read() = CsvImporter().read(csv.byteInputStream(), ImportOptions(mapping = mapping)).single()
        assertEquals(2, books.statements.import(account.id, read()).created)
        assertEquals(2, books.statements.import(account.id, read()).duplicates)
        assertEquals("Tim Hortons", books.transactions.register(account.id).first().transaction.payeeText)
    }

    @Test
    fun `recorded transactions missing from the statement are outstanding`() {
        val cheque = books.transactions.create(TransactionDraft(account.id, march(28), cad("-60.00"), "Plombier"))
        val view = books.statements.view(books.statements.import(account.id, parsed()).statementId)
        assertEquals(listOf(cheque.id), view.outstanding.map { it.id })
    }

    @Test
    fun `lines can be unlinked, recreated, linked by hand or ignored`() {
        val misc = books.transactions.create(TransactionDraft(account.id, march(20), cad("-95.00"), "Autre"))
        val result = books.statements.import(account.id, parsed(), settings = ImportSettings(dateToleranceDays = 0))
        val lines = books.statements.view(result.statementId).lines
        val videotron = lines.first { it.amount == cad("-95.00") }
        assertEquals(LineStatus.CREATED, videotron.status, "outside the tolerance, so a new transaction")

        books.statements.unlink(videotron.id)
        assertEquals(LineStatus.UNMATCHED, books.statements.view(result.statementId).lines.first { it.id == videotron.id }.status)
        val wrongAmount = runCatching { books.statements.link(videotron.id, books.transactions.register(account.id).first().transaction.id) }
        assertEquals("error.linkAmount", (wrongAmount.exceptionOrNull() as ValidationException).key)
        books.statements.link(videotron.id, misc.id)
        assertEquals(ClearedStatus.CLEARED, books.transactions.get(misc.id).cleared)

        val hydro = lines.first { it.amount == cad("-132.48") }
        books.statements.unlink(hydro.id)
        books.statements.ignore(hydro.id)
        assertEquals(LineStatus.IGNORED, books.statements.view(result.statementId).lines.first { it.id == hydro.id }.status)
        books.statements.createTransaction(hydro.id)
        assertEquals(LineStatus.CREATED, books.statements.view(result.statementId).lines.first { it.id == hydro.id }.status)
    }

    @Test
    fun `manual reconciliation against a paper statement`() {
        val a = books.transactions.create(TransactionDraft(account.id, march(3), cad("-50.00"), "IGA"))
        books.transactions.create(TransactionDraft(account.id, march(30), cad("-20.00"), "Café"))
        val statement = books.statements.createManual(account.id, march(31), cad("2400.00"))
        assertEquals(cad("-50.00"), books.statements.view(statement.id).difference)
        books.transactions.setCleared(a.id, ClearedStatus.CLEARED)
        val view = books.statements.view(statement.id)
        assertTrue(view.canFinish)
        assertEquals(1, view.outstanding.size)
        val report = books.statements.finish(statement.id)
        assertEquals(1, report.cleared.size)
        assertEquals(1, report.outstanding.size)
    }

    @Test
    fun `statements in another currency are refused`() {
        val usd = ImportedStatement("OFX", null, Currency.USD, null, march(31), null, null, listOf(ImportedLine("X", march(2), Money.parse("1", Currency.USD), null, null, null)))
        val e = runCatching { books.statements.import(account.id, usd) }.exceptionOrNull() as ValidationException
        assertEquals("error.importCurrency", e.key)
    }

    @Test
    fun `rules with amount ranges`() {
        val rule = books.rules.create("HYDRO", cat("utilities.electricity"), amountMin = cad("50"), amountMax = cad("500"))
        assertTrue(rule.matches("HYDRO-QUEBEC", cad("-132.48")))
        assertFalse(rule.matches("HYDRO-QUEBEC", cad("-12.00")))
        assertFalse(rule.matches("BELL", cad("-132.48")))
        books.rules.delete(rule.id)
        assertTrue(books.rules.list().isEmpty())
    }

    @Test
    fun `payee names and similarity`() {
        assertEquals("Iga Extra", PayeeService.cleanName("IGA EXTRA #8123"))
        assertEquals("Costco Wholesale", PayeeService.cleanName("COSTCO WHOLESALE W512"))
        assertEquals("Café Olimpico", PayeeService.cleanName("Café Olimpico"))
        assertTrue(StatementService.similarPayee("IGA EXTRA #8123", "IGA"))
        assertFalse(StatementService.similarPayee("IGA", "Metro"))
    }

    /** REC-04: a purchase entered in US dollars matches the statement's amount, and the fee is posted. */
    @Test
    fun `foreign purchases match within the fee, which is posted`() {
        val usd = { v: String -> Money.parse(v, Currency.USD) }
        val amazon = books.transactions.create(
            TransactionDraft(
                account.id, march(20), cad("-137.25"), "Amazon.com", listOf(SplitDraft(cat("food.groceries"), cad("-137.25"))),
                originalAmount = usd("-100.00"), fxRate = BigDecimal("1.3725"),
            ),
        )
        fun statement(vararg lines: Pair<String, String>) = ImportedStatement(
            "OFX", null, Currency.CAD, march(1), march(31), null, null,
            lines.mapIndexed { i, (id, amount) -> ImportedLine(id, march(21 + i), cad(amount), "AMAZON.COM", null, null) },
        )
        // 2.5 % more than recorded: proposed, never linked without asking.
        val result = books.statements.import(account.id, statement("X1" to "-140.68"))
        assertEquals(1, result.proposed)
        val line = books.statements.view(result.statementId).unresolved.single()
        assertEquals(amazon.id, line.transactionId)
        books.statements.confirm(line.id)
        val t = books.transactions.get(amazon.id)
        assertEquals(cad("-140.68"), t.amount)
        assertEquals(usd("-100.00"), t.originalAmount, "the foreign amount and the rate stay as entered")
        assertEquals(mapOf<String?, Money>(cat("food.groceries") to cad("-137.25"), cat("financial.fx_fees") to cad("-3.43")), t.splits.associate { it.categoryId to it.amount })
        assertEquals(ClearedStatus.CLEARED, t.cleared)

        // Too far from any foreign purchase: a new transaction.
        val other = books.transactions.create(
            TransactionDraft(account.id, march(25), cad("-50.00"), "Etsy", originalAmount = usd("-36.50"), fxRate = BigDecimal("1.37")),
        )
        val far = books.statements.import(account.id, statement("X2" to "-60.00"))
        assertEquals(1, far.created)
        assertEquals(cad("-50.00"), books.transactions.get(other.id).amount)
    }
}
