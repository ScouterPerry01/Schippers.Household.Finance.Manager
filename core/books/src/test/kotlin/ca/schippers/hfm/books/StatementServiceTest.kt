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
import kotlin.test.assertFailsWith
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
    fun `a category from the payee's habits is marked to review, one from a rule is not`() {
        books.rules.create("PAIE", cat("income.employment.salary"))
        // Last month's bill: Vidéotron is internet.
        books.transactions.create(TransactionDraft(account.id, LocalDate(2026, 2, 18), cad("-95.00"), "Videotron Ltee", listOf(SplitDraft(cat("utilities.internet"), cad("-95.00")))))
        books.statements.import(account.id, parsed(), "march.ofx", ofx.toByteArray())
        val register = books.transactions.register(account.id).map { it.transaction }
        val videotron = register.single { it.date == march(18) }
        val salary = register.single { it.amount == cad("3150.00") }
        assertEquals(cat("utilities.internet"), videotron.splits.single().categoryId, "the payee's last category is still filled in")
        assertEquals(listOf(videotron.id), books.transactions.suggestedCategories(account.id).map { it.id }, "and marked to review; the rule's is not")
        assertEquals(setOf(videotron.id), books.transactions.suggestedCategoryIds(account.id))
        assertEquals(cat("income.employment.salary"), salary.splits.single().categoryId, "a rule's category is not to review")

        // Accepting removes the mark; changing the category does too.
        books.transactions.acceptSuggestedCategory(videotron.id)
        assertTrue(books.transactions.suggestedCategories(account.id).isEmpty())
        val april = ImportedStatement(
            "OFX", null, Currency.CAD, LocalDate(2026, 4, 1), LocalDate(2026, 4, 30), null, null,
            listOf(ImportedLine("A1", LocalDate(2026, 4, 18), cad("-95.00"), "VIDEOTRON LTEE", null, null), ImportedLine("A2", LocalDate(2026, 4, 19), cad("-40.00"), "VIDEOTRON LTEE", null, null)),
        )
        books.statements.import(account.id, april)
        val (first, second) = books.transactions.suggestedCategories(account.id)
        books.transactions.update(first.id, TransactionDraft(account.id, first.date, first.amount, "Videotron Ltee", listOf(SplitDraft(cat("utilities.mobile"), first.amount))))
        assertEquals(listOf(second.id), books.transactions.suggestedCategories(account.id).map { it.id }, "editing the category clears the mark")
        books.transactions.update(second.id, TransactionDraft(account.id, second.date, second.amount, "Videotron Ltee", listOf(SplitDraft(cat("utilities.internet"), second.amount)), memo = "Modem"))
        assertEquals(1, books.transactions.suggestedCategories(account.id).size, "a memo alone leaves it to review")
        books.transactions.acceptSuggestedCategories(account.id)
        assertTrue(books.transactions.suggestedCategoryIds(account.id).isEmpty(), "keep all")
    }

    private fun lines(vararg lines: Triple<String, Int, String>) = ImportedStatement(
        "OFX", null, Currency.CAD, march(1), march(31), null, null,
        lines.map { (id, day, amount) -> ImportedLine(id, march(day), cad(amount), "LINE $id", null, null) },
    )

    @Test
    fun `one deposit for several cheques, and one purchase in two charges, are proposed as groups`() {
        // Two cheques received, deposited together: the bank shows one deposit.
        val c1 = books.transactions.create(TransactionDraft(account.id, march(3), cad("200.00"), "Cheque Marie"))
        val c2 = books.transactions.create(TransactionDraft(account.id, march(4), cad("300.00"), "Cheque Luc"))
        // One receipt of $120, charged as $70 and $50.
        val purchase = books.transactions.create(TransactionDraft(account.id, march(10), cad("-120.00"), "Rona"))
        val result = books.statements.import(account.id, lines(Triple("D1", 5, "500.00"), Triple("R1", 10, "-70.00"), Triple("R2", 11, "-50.00")))
        assertEquals(2, result.groups)
        assertEquals(0, result.created, "nothing is added while a group is proposed")
        val view = books.statements.view(result.statementId)
        assertEquals(2, view.groups.size)
        assertTrue(view.groups.none { it.confirmed }, "proposed, never linked without asking")
        assertEquals(3, view.unresolved.size)
        assertEquals(ClearedStatus.UNCLEARED, books.transactions.get(c1.id).cleared)

        val deposit = view.groups.single { it.lines.size == 1 }
        assertEquals(setOf(c1.id, c2.id), deposit.transactionIds.toSet())
        val charges = view.groups.single { it.lines.size == 2 }
        assertEquals(listOf(purchase.id), charges.transactionIds)
        assertEquals(cad("-120.00"), charges.total)

        books.statements.confirmGroup(deposit.id)
        books.statements.confirmGroup(charges.id)
        val after = books.statements.view(result.statementId)
        assertTrue(after.unresolved.isEmpty())
        assertTrue(after.groups.all { it.confirmed })
        assertEquals(listOf(ClearedStatus.CLEARED, ClearedStatus.CLEARED, ClearedStatus.CLEARED), listOf(c1, c2, purchase).map { books.transactions.get(it.id).cleared })
        assertTrue(after.outstanding.isEmpty(), "the grouped transactions are on the statement")

        // Undo works: the group's transactions are no longer cleared and the lines need a decision.
        books.statements.unlink(after.lines.first { it.matchGroup == charges.id }.id)
        assertEquals(ClearedStatus.UNCLEARED, books.transactions.get(purchase.id).cleared)
        assertEquals(2, books.statements.view(result.statementId).unresolved.size)
        assertEquals(1, books.statements.view(result.statementId).groups.size)
    }

    @Test
    fun `groups chosen by hand must add up, show in the report, survive undo and are not imported twice`() {
        val c1 = books.transactions.create(TransactionDraft(account.id, march(3), cad("200.00"), "Cheque Marie"))
        val c2 = books.transactions.create(TransactionDraft(account.id, march(20), cad("300.00"), "Cheque Luc"))
        val statement = lines(Triple("D1", 22, "500.00"))
        val result = books.statements.import(account.id, statement)
        assertEquals(0, result.groups, "the first cheque is too far in date to propose")
        val created = books.statements.view(result.statementId).lines.single()
        books.statements.unlink(created.id)
        val line = books.statements.view(result.statementId).unresolved.single()
        assertFailsWith<ValidationException>("200 + 300 is not 450") {
            books.statements.matchGroup(result.statementId, listOf(line.id), listOf(c1.id))
        }
        val group = books.statements.matchGroup(result.statementId, listOf(line.id), listOf(c1.id, c2.id))
        assertTrue(group.confirmed, "chosen by hand: linked at once")
        books.statements.updateBalances(result.statementId, march(1), march(31), cad("2450.00"), cad("2950.00"))
        val report = books.statements.finish(result.statementId)
        assertEquals(1, report.groups.size)
        assertEquals(listOf(50000L), report.groups.single().lines.map { it.amountMinor })
        assertEquals(setOf(c1.id, c2.id), report.groups.single().transactions.map { it.transactionId }.toSet())
        assertEquals(ClearedStatus.RECONCILED, books.transactions.get(c2.id).cleared)

        // The same line again is a duplicate, not a new deposit.
        val again = books.statements.import(account.id, statement)
        assertEquals(1, again.duplicates)

        val reopened = books.statements.undo(result.statementId, "Wrong closing balance")
        val copy = books.statements.view(reopened)
        assertEquals(1, copy.groups.size, "the group comes back with the reopened statement")
        assertEquals(setOf(c1.id, c2.id), copy.groups.single().transactionIds.toSet())
        assertTrue(copy.groups.single().confirmed)
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
    fun `an undone reconciliation can be reconciled again`() {
        val result = books.statements.import(account.id, parsed(), "march.ofx", ofx.toByteArray())
        books.statements.finish(result.statementId)
        val reopened = books.statements.undo(result.statementId, "Typed the wrong closing balance")

        // The undone statement stays in the history; an open copy takes its place, lines and all.
        val statuses = books.statements.statements(account.id).associate { it.id to it.status }
        assertEquals(mapOf(result.statementId to StatementStatus.UNDONE, reopened to StatementStatus.OPEN), statuses)
        assertEquals("Typed the wrong closing balance", books.statements.statement(result.statementId).undoReason)
        val view = books.statements.view(reopened)
        assertEquals(books.statements.view(result.statementId).lines.map { it.transactionId to it.status }, view.lines.map { it.transactionId to it.status })
        assertEquals(march(31), view.statement.periodEnd)
        assertTrue(view.canFinish, "nothing changed: the difference is still zero")

        books.statements.finish(reopened)
        assertEquals(StatementStatus.RECONCILED, books.statements.statement(reopened).status)
        assertTrue(books.transactions.register(account.id).all { it.transaction.cleared == ClearedStatus.RECONCILED })
        assertEquals(march(31), books.statements.lastReconciled()[account.id])

        // The file itself still counts as imported.
        val again = runCatching { books.statements.import(account.id, parsed(), "march.ofx", ofx.toByteArray()) }
        assertEquals("error.statementAlreadyImported", (again.exceptionOrNull() as ValidationException).key)
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
