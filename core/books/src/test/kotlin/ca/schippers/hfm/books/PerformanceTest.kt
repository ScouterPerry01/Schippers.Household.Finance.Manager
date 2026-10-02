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
 * NFR-02: 30 years of history, about 250,000 transactions, with screens opening in under 1 second
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

    private companion object {
        const val TRANSACTIONS = 250_000
    }
}
