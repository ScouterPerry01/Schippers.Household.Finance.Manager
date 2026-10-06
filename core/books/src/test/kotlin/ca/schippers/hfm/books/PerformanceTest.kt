package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.CategoryKind
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/**
 * NFR-02: 30 years of history, about 250,000 transactions and 50,000 documents, with screens opening in under 1 second
 * and reports in under 3 seconds. Run with `./gradlew :core:books:performanceTest`.
 * HFM_PERF_FACTOR relaxes the limits on slow machines (CI uses 3).
 */
@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PerformanceTest {

    private lateinit var dir: Path
    private lateinit var books: Books
    private lateinit var accounts: List<Account>
    private val today = LocalDate(2026, 10, 1)
    private val start = today.minus(DatePeriod(years = 30))
    private val factor = System.getenv("HFM_PERF_FACTOR")?.toDoubleOrNull() ?: 1.0
    private val timings = linkedMapOf<String, Long>()

    @BeforeAll
    fun generate() {
        dir = Files.createTempDirectory("hfm-perf")
        val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
        books = Books(store.create(dir.resolve("Perf.hfm"), "Perf", "perry", "Perry", "pw".toCharArray()).session)
        val group = books.groups().single()
        accounts = listOf(
            AccountType.CHEQUING, AccountType.CHEQUING, AccountType.SAVINGS, AccountType.SAVINGS, AccountType.CREDIT_CARD,
            AccountType.CREDIT_CARD, AccountType.CREDIT_CARD, AccountType.LINE_OF_CREDIT, AccountType.CASH, AccountType.CHEQUING,
            AccountType.SAVINGS, AccountType.CREDIT_CARD,
        ).mapIndexed { i, type ->
            books.accounts.create(AccountDraft(group.id, "Account $i", type, Currency.CAD, Money.parse("1000", Currency.CAD), start))
        }
        val expense = books.categories.list().filter { it.kind == CategoryKind.EXPENSE && it.parentId != null }
        val income = books.categories.list().filter { it.kind == CategoryKind.INCOME }
        val payees = (1..800).map { books.payees.create("Merchant $it ${listOf("Épicerie", "Garage", "Pharmacie", "Café", "Quincaillerie")[it % 5]}") }
        val ledger = books.ledger(group).ledgerQueries
        val random = Random(42)
        val days = (today.toEpochDays() - start.toEpochDays()).toInt()
        val (_, generation) = measureTimedValue {
            books.ledger(group).transaction {
                repeat(TRANSACTIONS) { i ->
                    val account = accounts[if (i % 10 < 6) 0 else random.nextInt(accounts.size)]
                    val date = LocalDate.fromEpochDays(start.toEpochDays() + random.nextInt(days)).toString()
                    val isIncome = random.nextInt(12) == 0
                    val amount = if (isIncome) random.nextLong(50_000, 400_000) else -random.nextLong(100, 40_000)
                    val id = Ids.newId()
                    val payee = payees[random.nextInt(payees.size)]
                    ledger.insertTxn(
                        id, account.id, date, payee.id, payee.name, amount, null, null, null,
                        if (i % 7 == 0) "note $i" else null, null, if (random.nextBoolean()) "CLEARED" else "UNCLEARED",
                        null, null, "perry", "desktop", i.toLong(), i.toLong(),
                    )
                    if (!isIncome && i % 5 == 0) {
                        val first = amount / 2
                        ledger.insertSplit(Ids.newId(), id, expense[random.nextInt(expense.size)].id, first, null, null, null)
                        ledger.insertSplit(Ids.newId(), id, expense[random.nextInt(expense.size)].id, amount - first, null, null, null)
                    } else {
                        val category = if (isIncome) income[random.nextInt(income.size)] else expense[random.nextInt(expense.size)]
                        ledger.insertSplit(Ids.newId(), id, category.id, amount, null, null, null)
                    }
                }
            }
        }
        println("Generated $TRANSACTIONS transactions in $generation")
        val (_, investing) = measureTimedValue { investments(group.id) }
        println("Generated 30 years of investments in $investing")
        val (_, filing) = measureTimedValue { documents(group.id) }
        println("Generated $DOCUMENTS documents in $filing")
        val (_, people) = measureTimedValue { contacts(group.id) }
        println("Generated $CONTACTS contacts in $people")
    }

    /** CON-01: a large address book, each contact with a phone, an email and a client number, a few linked to payees. */
    private fun contacts(groupId: String) {
        val trades = listOf("Plomberie", "Pharmacie", "Clinique", "Garage", "Notaire", "Assurances", "Banque", "École")
        val kinds = ContactKind.entries
        val payees = books.payees.list()
        repeat(CONTACTS) { i ->
            val c = books.contacts.save(
                Contact(
                    "", groupId, "${trades[i % trades.size]} Roy $i", purpose = "For the house $i", kinds = setOf(kinds[i % kinds.size]),
                    details = listOf(
                        ContactDetail(type = DetailType.PHONE, label = "Office", value = "418 555-${(1000 + i).toString().takeLast(4)}"),
                        ContactDetail(type = DetailType.EMAIL, value = "contact$i@example.ca"),
                        ContactDetail(type = DetailType.NUMBER, label = "Client", value = "${100000 + i}"),
                    ),
                    notes = if (i % 3 == 0) "Call before ten" else null,
                ),
            )
            if (i % 4 == 0) books.contacts.link(c.id, LinkRole.SAME_AS, LinkTarget.PAYEE, payees[i % payees.size].id)
        }
    }

    private val realDocuments = mutableListOf<String>()

    /**
     * NFR-02's 50,000 documents: a few hundred real files in the encrypted vault, the rest as rows
     * with the text read from them, as receipts and bills over 30 years. Screens and searches read
     * only the rows; a file is decrypted when it is opened.
     */
    private fun documents(groupId: String) {
        val group = books.groups().single { it.id == groupId }
        repeat(REAL_DOCUMENTS) { i ->
            realDocuments += books.documents.import(groupId, ByteArray(40_000) { (it * 31 + i).toByte() }, "scan-$i.jpg", "image/jpeg").document.id
        }
        val ledger = books.ledger(group).ledgerQueries
        val random = Random(7)
        val days = (today.toEpochDays() - start.toEpochDays()).toInt()
        val words = listOf("ÉPICERIE", "PHARMACIE", "QUINCAILLERIE", "GARAGE", "RESTAURANT", "HYDRO", "ASSURANCE", "TPS", "TVQ", "SOUS-TOTAL", "TOTAL", "MERCI")
        books.ledger(group).transaction {
            repeat(DOCUMENTS - REAL_DOCUMENTS) { i ->
                val id = Ids.newId()
                val date = LocalDate.fromEpochDays(start.toEpochDays() + random.nextInt(days))
                val amount = random.nextLong(100, 40_000)
                val text = (1..12).joinToString("\n") { "${words[random.nextInt(words.size)]} ${random.nextInt(1, 999)},${random.nextInt(10, 99)}" }
                ledger.insertDocument(
                    id, "$id.hfmdoc", "image/jpeg", Ids.newId(), 1, text, date.toEpochDays() * 86_400_000L, "perry", "phone",
                    "IMG_$i.jpg", null, if (i % 2000 == 0) "INBOX" else "FILED", "RECEIPT", date.toString(), "Merchant ${i % 800}",
                    amount, "CAD", null, "test", 200_000, 0, null, null,
                )
            }
        }
    }

    private lateinit var portfolio: List<Account>
    private var investmentLines = 0L

    /** A brokerage account and an RRSP fed every month for 30 years, three funds priced monthly, dividends quarterly. */
    private fun investments(groupId: String) {
        val before = books.ledger(books.groups().single()).ledgerQueries.txnCount().executeAsOne()
        val inv = books.investments
        portfolio = listOf(AccountType.BROKERAGE, AccountType.RRSP).map {
            books.accounts.create(AccountDraft(groupId, "Invest $it", it, Currency.CAD, Money.parse("0", Currency.CAD), start))
        }
        val funds = listOf("XIC", "XUU", "XEF").map { inv.saveSecurity(Security("", it, "TSX", "Fund $it", SecurityKind.ETF, Currency.CAD)) }
        val random = Random(7)
        val prices = funds.associate { it.id to BigDecimal("20") }.toMutableMap()
        for (m in 0 until 360) {
            val date = start.plus(DatePeriod(months = m))
            for (f in funds) {
                prices[f.id] = prices.getValue(f.id).multiply(BigDecimal(1 + (random.nextDouble() - 0.45) * 0.06)).setScale(2, RoundingMode.HALF_UP)
                inv.setPrice(f.id, date, prices.getValue(f.id))
            }
            portfolio.forEachIndexed { i, a ->
                books.transactions.transfer(TransferDraft(accounts[0].id, a.id, date, Money.parse("500", Currency.CAD)))
                val f = funds[(m + i) % funds.size]
                val qty = BigDecimal("500").divide(prices.getValue(f.id), 0, RoundingMode.DOWN)
                inv.save(InvestmentTxn("", a.id, date, InvestmentKind.BUY, f.id, qty, prices.getValue(f.id), Money.of(qty.multiply(prices.getValue(f.id)), Currency.CAD)))
                if (m % 3 == 2) inv.save(InvestmentTxn("", a.id, date, InvestmentKind.INCOME, f.id, amount = Money.parse("40", Currency.CAD), incomeType = IncomeType.DIVIDEND))
            }
        }
        investmentLines = books.ledger(books.groups().single()).ledgerQueries.txnCount().executeAsOne() - before
    }

    @AfterAll
    fun cleanUp() {
        println("NFR-02 timings (ms): " + timings.entries.joinToString { "${it.key}=${it.value}" })
        books.session.close()
        dir.toFile().deleteRecursively()
    }

    private fun <T> timed(name: String, limitMillis: Long, block: () -> T): T {
        block() // warm up (first query compiles statements and fills the page cache)
        val (value, duration) = measureTimedValue(block)
        timings[name] = duration.inWholeMilliseconds
        assertTrue(duration.inWholeMilliseconds <= limitMillis * factor, "$name took $duration, limit ${limitMillis * factor} ms")
        return value
    }

    @Test
    fun `screens open in under a second`() {
        val list = timed("account list", 1000) { books.accounts.list() }
        assertEquals(TRANSACTIONS + investmentLines, books.ledger(books.groups().single()).ledgerQueries.txnCount().executeAsOne())
        assertEquals(accounts.size + portfolio.size, list.size)
        val register = timed("largest register, latest 1000", 1000) { books.transactions.register(accounts[0].id, limit = 1000) }
        assertEquals(1000, register.size)
        assertTrue(books.transactions.count(accounts[0].id) > 140_000, "the main account holds most transactions")
        val full = books.transactions.register(accounts[0].id)
        assertEquals(full.last().runningBalance, register.last().runningBalance, "the running balance counts the whole history")
        timed("search", 1000) { books.search.search("pharmacie") }
        val documents = timed("documents, latest 500", 1000) { books.documents.search(DocumentQuery()) }
        assertEquals(500, documents.size)
        val found = timed("text inside 50,000 documents", 1000) { books.documents.search(DocumentQuery(text = "quincaillerie", limit = 200)) }
        assertEquals(200, found.size)
        timed("review inbox count", 1000) { books.documents.inboxCount() }
        timed("open a document", 1000) { books.documents.content(realDocuments.last()) }
        timed("dashboard data", 1000) {
            books.reports.netWorth(books.reports.monthEnds(today.minus(DatePeriod(months = 11)), today))
            books.reports.byCategory(ReportFilter(LocalDate(today.year, today.month, 1), today))
            books.bills.agenda(today)
        }
    }

    @Test
    fun `reports run in under three seconds`() {
        val all = ReportFilter(start, today)
        val periods = timed("income and expense, 30 years by month", 3000) { books.reports.incomeExpense(all, Granularity.MONTH) }
        assertEquals(361, periods.value.size)
        timed("spending by category, 30 years", 3000) { books.reports.byCategory(all) }
        timed("spending by payee, 30 years", 3000) { books.reports.byPayee(all) }
        timed("net worth, 30 years monthly", 3000) { books.reports.netWorth(books.reports.monthEnds(start, today)) }
        timed("spending by category, one year", 1000) { books.reports.byCategory(ReportFilter(today.minus(DatePeriod(years = 1)), today)) }
        timed("drill-down, one year", 1000) { books.reports.drillDown(ReportFilter(today.minus(DatePeriod(years = 1)), today)) }
        val returns = timed("portfolio returns, 30 years", 3000) { books.portfolio.performance(start, today) }
        assertEquals(Money.parse("360000", Currency.CAD), returns.total.contributions)
        timed("portfolio returns, one year", 1000) { books.portfolio.performance(today.minus(DatePeriod(years = 1)), today) }
        timed("asset allocation", 1000) { books.allocation.allocation(AllocationBy.CLASS, today) }
        timed("investment income and gains, one tax year", 3000) { books.taxSlips.report(today.year - 1) }
    }

    /** Screens and reports added in Phase 5: contacts, the tax package and estimate, custom reports and the year in review. */
    @Test
    fun `phase 5 screens and reports`() {
        val contacts = timed("contacts list, 2,000", 1000) { books.contacts.list() }
        assertEquals(CONTACTS, contacts.size)
        val found = timed("contacts, text search", 1000) { books.contacts.list(ContactFilter(text = "plomberie")) }
        assertEquals(CONTACTS / 8, found.size)
        timed("contact links of a payee", 1000) { books.contacts.linkedTo(LinkTarget.PAYEE, books.payees.list().first().id) }
        val search = timed("search with contacts", 1000) { books.search.search("roy 12") }
        assertTrue(search.contacts.isNotEmpty())
        val year = today.year - 1
        val pkg = timed("tax package, one year", 3000) { books.taxPackage.build(year) }
        val member = books.members.list().firstOrNull()?.id ?: books.members.create("Perry", ca.schippers.hfm.domain.MemberKind.ADULT).id
        timed("income tax estimate", 3000) { books.incomeTax.estimate(year, member, entered = emptyMap()) }
        timed("income tax estimate, package already built", 1000) { books.incomeTax.estimate(year, member, entered = emptyMap(), pkg = pkg) }
        val all = ReportFilter(start, today)
        val pivot = timed("custom report, 30 years by category and year", 3000) {
            books.customReports.run(all, CustomLayout(ReportDimension.TOP_CATEGORY, ReportDimension.YEAR), { it })
        }
        assertEquals(31, pivot.columns.size)
        timed("custom report, 30 years by payee and month", 3000) {
            books.customReports.run(all, CustomLayout(ReportDimension.PAYEE, ReportDimension.MONTH, maxRows = 25), { it })
        }
        timed("custom report, one year by person and category", 1000) {
            books.customReports.run(ReportFilter(today.minus(DatePeriod(years = 1)), today), CustomLayout(ReportDimension.PERSON, ReportDimension.CATEGORY), { it })
        }
        val review = timed("year in review", 3000) { books.yearReview.review(year, { it }) }
        assertTrue(review.spending.isPositive)
    }

    private companion object {
        const val CONTACTS = 2_000
        const val TRANSACTIONS = 250_000
        const val DOCUMENTS = 50_000
        const val REAL_DOCUMENTS = 300
    }
}
