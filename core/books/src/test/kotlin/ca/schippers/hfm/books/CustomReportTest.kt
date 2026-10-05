package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** RPT-03, RPT-05, RPT-07: custom reports by any two dimensions, saved and made on a schedule. */
class CustomReportTest {

    @TempDir
    lateinit var temp: Path

    private lateinit var books: Books
    private lateinit var chequing: Account
    private lateinit var cottage: Account
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id
    private val labels: (String) -> String = { if (it == "quarter") "Q%d %d" else "<$it>" }

    @BeforeEach
    fun setUp() {
        books = Books(HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING).create(temp.resolve("T.hfm"), "T", "perry", "Perry", "password1".toCharArray()).session)
        val group = books.groups().single().id
        chequing = books.accounts.create(AccountDraft(group, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("0"), LocalDate(2025, 1, 1)))
        cottage = books.accounts.create(AccountDraft(group, "Cottage account", AccountType.CHEQUING, Currency.CAD, cad("0"), LocalDate(2025, 1, 1)))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun spend(account: Account, date: LocalDate, amount: String, category: String, member: String? = null, payee: String = "Store") =
        books.transactions.create(TransactionDraft(account.id, date, cad("-$amount"), payee, listOf(SplitDraft(cat(category), cad("-$amount"))), memberId = member))

    @Test
    fun `spending by top category and month, by person, and for a chosen set of accounts`() {
        val sam = books.members.create("Sam", MemberKind.ADULT).id
        spend(chequing, LocalDate(2026, 1, 5), "100.00", "food.groceries", sam)
        spend(chequing, LocalDate(2026, 1, 20), "40.00", "food.restaurants")
        spend(chequing, LocalDate(2026, 2, 3), "60.00", "food.groceries", sam)
        spend(cottage, LocalDate(2026, 2, 10), "500.00", "housing.maintenance", payee = "Hydro Cottage")
        books.transactions.create(TransactionDraft(chequing.id, LocalDate(2026, 2, 15), cad("2000.00"), "Employer", listOf(SplitDraft(cat("income.employment.salary"), cad("2000.00")))))
        books.transactions.transfer(TransferDraft(chequing.id, cottage.id, LocalDate(2026, 2, 1), cad("300.00")))
        val filter = ReportFilter(LocalDate(2026, 1, 1), LocalDate(2026, 2, 28))

        val byMonth = books.customReports.run(filter, CustomLayout(ReportDimension.TOP_CATEGORY, ReportDimension.MONTH), labels)
        assertEquals(listOf("2026-01", "2026-02"), byMonth.columns.map { it.label })
        val food = byMonth.rows.first { it.id == books.categories.list().first { c -> c.systemKey == "food" }.id }
        assertEquals(cad("140.00"), byMonth.cell(food, byMonth.columns[0]), "groceries and restaurants roll up to food")
        assertEquals(cad("700.00"), byMonth.total, "spending only: no income, no transfers")
        assertEquals(food.id, byMonth.rows[1].id, "largest first: housing, then food")

        val byPerson = books.customReports.run(filter, CustomLayout(ReportDimension.PERSON, null), labels)
        assertEquals(listOf("<household>" to cad("540.00"), "Sam" to cad("160.00")), byPerson.rows.map { it.label to byPerson.rowTotal(it) })

        // RPT-07: a chosen set of accounts, such as the cottage's.
        val cottageOnly = books.customReports.run(filter.copy(accountIds = setOf(cottage.id)), CustomLayout(ReportDimension.PAYEE, null), labels)
        assertEquals(listOf("Hydro Cottage"), cottageOnly.rows.map { it.label })

        val net = books.customReports.run(filter, CustomLayout(ReportDimension.QUARTER, null, ReportMeasure.NET), labels)
        assertEquals(listOf("Q1 2026" to cad("1300.00")), net.rows.map { it.label to net.rowTotal(it) })
    }

    @Test
    fun `the year in review compares the year with the one before`() {
        books.transactions.create(TransactionDraft(chequing.id, LocalDate(2026, 1, 15), cad("4000.00"), "Employer", listOf(SplitDraft(cat("income.employment.salary"), cad("4000.00")))))
        spend(chequing, LocalDate(2026, 2, 3), "120.00", "food.groceries", payee = "Metro")
        spend(chequing, LocalDate(2026, 3, 3), "80.00", "food.groceries", payee = "Metro")
        spend(chequing, LocalDate(2026, 3, 9), "900.00", "housing.maintenance", payee = "Roofer")
        spend(chequing, LocalDate(2025, 5, 1), "300.00", "food.groceries", payee = "Metro")
        val r = books.yearReview.review(2026, labels)
        assertEquals(cad("4000.00") to cad("1100.00"), r.income to r.spending)
        assertEquals(72, r.savingsRate)
        assertEquals(cad("300.00"), r.lastYearSpending)
        assertEquals(listOf(cad("900.00")), r.largestPurchases.take(1).map { it.amount })
        assertEquals("Metro" to 2, r.frequentPayees.first().let { it.first to it.second })
        assertEquals("2026-03" to cad("980.00"), r.busiestMonth)
        val food = r.topCategories.first { it.thisYear == cad("200.00") }
        assertEquals(cad("-100.00"), food.change, "food went down by 100")
    }

    @Test
    fun `the smallest rows are added together as other`() {
        val keys = listOf("food.groceries", "food.restaurants", "housing.maintenance", "utilities.electricity", "utilities.internet")
        keys.forEachIndexed { i, k -> spend(chequing, LocalDate(2026, 3, 1), "${(i + 1) * 10}.00", k) }
        val t = books.customReports.run(ReportFilter(LocalDate(2026, 3, 1), LocalDate(2026, 3, 31)), CustomLayout(ReportDimension.CATEGORY, null, maxRows = 3), labels)
        assertEquals(3, t.rows.size)
        assertEquals("<other>" to cad("60.00"), t.rows.last().let { it.label to t.rowTotal(it) })
        assertEquals(cad("150.00"), t.total)
    }

    @Test
    fun `saved reports are the user's own, and a schedule makes each finished period once`() {
        assertFailsWith<ValidationException> { books.savedReports.save(SavedReport("", "Monthly", "{}", ReportSchedule.MONTHLY, folder = null)) }
        val saved = books.savedReports.save(SavedReport("", " Cottage ", """{"kind":"CUSTOM"}""", ReportSchedule.MONTHLY, temp.toString()))
        assertEquals("Cottage", saved.name)
        val oct5 = LocalDate(2026, 10, 5)
        val (report, period) = books.savedReports.due(oct5).single()
        assertEquals(ReportPeriod("2026-09", LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)), period)
        books.savedReports.markMade(report.id, period)
        assertTrue(books.savedReports.due(oct5).isEmpty())
        assertEquals("2026-10", books.savedReports.due(LocalDate(2026, 11, 1)).single().second.id)
        assertEquals(ReportPeriod("2026-Q3", LocalDate(2026, 7, 1), LocalDate(2026, 9, 30)), SavedReportService.lastFinished(ReportSchedule.QUARTERLY, oct5))
        assertEquals("2025", SavedReportService.lastFinished(ReportSchedule.YEARLY, oct5).id)
        books.savedReports.delete(report.id)
        assertTrue(books.savedReports.list().isEmpty())
    }
}
