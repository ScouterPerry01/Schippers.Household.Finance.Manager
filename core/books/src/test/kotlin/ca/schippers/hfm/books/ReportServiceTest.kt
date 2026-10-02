package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.CategoryKind
import ca.schippers.hfm.domain.MemberKind
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportServiceTest {

    @TempDir
    lateinit var temp: Path

    private lateinit var books: Books
    private lateinit var chequing: Account
    private lateinit var visa: Account
    private lateinit var savings: Account
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun usd(s: String) = Money.parse(s, Currency.USD)
    private fun d(m: Int, day: Int) = LocalDate(2026, m, day)
    private fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id
    private val q1 get() = ReportFilter(d(1, 1), d(3, 31))

    private fun spend(account: Account, date: LocalDate, amount: String, key: String?, payee: String = "Shop", member: String? = null, tags: Set<String> = emptySet()) =
        books.transactions.create(
            TransactionDraft(
                account.id, date, Money.parse(amount, account.currency), payee,
                listOfNotNull(key?.let { SplitDraft(cat(it), Money.parse(amount, account.currency)) }), memberId = member, tags = tags,
            ),
        )

    @BeforeEach
    fun setUp() {
        val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
        books = Books(store.create(temp.resolve("R.hfm"), "R", "perry", "Perry", "admin-pass".toCharArray()).session)
        val group = books.groups().single().id
        chequing = books.accounts.create(AccountDraft(group, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("1000.00"), d(1, 1)))
        visa = books.accounts.create(AccountDraft(group, "Visa", AccountType.CREDIT_CARD, Currency.CAD, cad("0"), d(1, 1)))
        savings = books.accounts.create(AccountDraft(group, "Savings", AccountType.SAVINGS, Currency.CAD, cad("5000.00"), d(1, 1)))

        spend(chequing, d(1, 1), "3000.00", "income.employment.salary", "Employer")
        spend(chequing, d(2, 1), "3000.00", "income.employment.salary", "Employer")
        spend(chequing, d(1, 3), "-1450.00", "housing.rent", "Landlord")
        spend(chequing, d(2, 3), "-1450.00", "housing.rent", "Landlord")
        spend(visa, d(1, 10), "-200.00", "food.groceries", "IGA")
        spend(visa, d(1, 20), "-60.00", "food.restaurants", "Bistro")
        spend(visa, d(2, 10), "-250.00", "food.groceries", "IGA")
        spend(visa, d(2, 12), "15.00", "food.groceries", "IGA") // a refund
        spend(visa, d(3, 5), "-40.00", null, "Mystery")
        books.transactions.transfer(TransferDraft(chequing.id, visa.id, d(2, 15), cad("495.00")))
        books.transactions.transfer(TransferDraft(chequing.id, savings.id, d(2, 16), cad("500.00")))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    @Test
    fun `income and expense by month leave transfers out`() {
        val months = books.reports.incomeExpense(q1).value
        assertEquals(listOf(d(1, 1), d(2, 1), d(3, 1)), months.map { it.start })
        assertEquals(listOf(cad("3000.00"), cad("3000.00"), cad("0.00")), months.map { it.income })
        assertEquals(listOf(cad("1710.00"), cad("1685.00"), cad("40.00")), months.map { it.expense })
        assertEquals(cad("1290.00"), months[0].net)
        val quarter = books.reports.incomeExpense(q1, Granularity.QUARTER).value.single()
        assertEquals(cad("3435.00"), quarter.expense)
    }

    @Test
    fun `spending by category rolls up and drills down`() {
        val top = books.reports.byCategory(q1).value
        assertEquals(listOf("housing", "food", null), top.map { it.category?.systemKey })
        assertEquals(listOf(cad("2900.00"), cad("495.00"), cad("40.00")), top.map { it.amount })
        assertTrue(top.first { it.category?.systemKey == "food" }.hasChildren)

        val food = books.reports.byCategory(q1, parentId = cat("food")).value
        assertEquals(mapOf("food.groceries" to cad("435.00"), "food.restaurants" to cad("60.00")), food.associate { it.category!!.systemKey!! to it.amount })

        val rows = books.reports.drillDown(q1, categoryId = cat("food"))
        assertEquals(4, rows.size)
        assertEquals(cad("-495.00"), rows.fold(cad("0")) { a, r -> a + r.amount })
        assertEquals(listOf("Mystery"), books.reports.drillDown(q1, uncategorized = true).map { it.payee })

        val income = books.reports.byCategory(q1, CategoryKind.INCOME).value
        assertEquals(cad("6000.00"), income.single().amount)
    }

    @Test
    fun `spending by payee`() {
        val payees = books.reports.byPayee(q1).value.associate { it.name to it.amount }
        assertEquals(cad("2900.00"), payees["Landlord"])
        assertEquals(cad("435.00"), payees["IGA"])
        assertEquals(cad("-6000.00"), payees["Employer"], "income shows as negative spending")
    }

    @Test
    fun `filters by person, tag and account`() {
        val alex = books.members.create("Alex", MemberKind.ADULT)
        spend(chequing, d(3, 9), "-25.00", "leisure.hobbies", "Hobby shop", member = alex.id, tags = setOf("Kitchen 2026"))
        val byMember = books.reports.byCategory(q1.copy(memberId = alex.id)).value
        assertEquals(listOf(cad("25.00")), byMember.map { it.amount })
        val tag = books.core.tagByName("Kitchen 2026").executeAsOne().id
        assertEquals(cad("25.00"), books.reports.byCategory(q1.copy(tagId = tag)).value.single().amount)
        val visaOnly = books.reports.incomeExpense(q1.copy(accountIds = setOf(visa.id)), Granularity.QUARTER).value.single()
        assertEquals(cad("535.00"), visaOnly.expense)
    }

    @Test
    fun `net worth counts what is owed as a liability`() {
        val points = books.reports.netWorth(listOf(d(1, 31), d(2, 28))).value
        // January: chequing 1000+3000-1450 = 2550, savings 5000; Visa owes 260.
        assertEquals(cad("7550.00"), points[0].assets)
        assertEquals(cad("260.00"), points[0].liabilities)
        assertEquals(cad("7290.00"), points[0].net)
        // February: chequing 2550+3000-1450-495-500 = 3105, savings 5500; Visa owes 260+250-15-495 = 0.
        assertEquals(cad("8605.00"), points[1].net)
    }

    @Test
    fun `foreign currency amounts are converted at the date's rate`() {
        val group = books.groups().single().id
        val us = books.accounts.create(AccountDraft(group, "US", AccountType.CHEQUING, Currency.USD, usd("0"), d(1, 1)))
        spend(us, d(1, 15), "-100.00", "travel.lodging", "Hotel")
        spend(us, d(1, 17), "-50.00", "travel.meals", "Diner") // a Saturday: uses Friday's rate

        val missing = books.reports.byCategory(q1, parentId = cat("travel"))
        assertEquals(setOf(Currency.USD), missing.missingRates)
        assertTrue(missing.value.isEmpty())

        val json = """{"observations":[{"d":"2026-01-15","FXUSDCAD":{"v":"1.3700"}},{"d":"2026-01-16","FXUSDCAD":{"v":"1.3800"}}]}"""
        assertEquals(2, books.rates.applyBankOfCanada(json))
        val travel = books.reports.byCategory(q1, parentId = cat("travel")).value.associate { it.category!!.systemKey to it.amount }
        assertEquals(cad("137.00"), travel["travel.lodging"])
        assertEquals(cad("69.00"), travel["travel.meals"])
    }

    @Test
    fun `manual rates win over downloaded ones`() {
        books.rates.setManual(Currency.USD, d(1, 15), BigDecimal("1.40"))
        books.rates.applyBankOfCanada("""{"observations":[{"d":"2026-01-15","FXUSDCAD":{"v":"1.37"}}]}""")
        assertEquals(0, BigDecimal("1.40").compareTo(books.rates.cadPerUnit(Currency.USD, d(1, 15))))
        assertNull(books.rates.cadPerUnit(Currency.EUR, d(1, 15)))
        assertEquals(usd("100.00"), books.rates.convert(cad("140.00"), Currency.USD, d(1, 20)))
    }

    @Test
    fun `Bank of Canada download asks only for missing days`() {
        val group = books.groups().single().id
        books.accounts.create(AccountDraft(group, "US", AccountType.CHEQUING, Currency.USD, usd("0"), d(1, 1)))
        books.accounts.create(AccountDraft(group, "Bitcoin", AccountType.CRYPTO_WALLET, Currency.BTC, Money.parse("0", Currency.BTC), d(1, 1)))
        val urls = mutableListOf<String>()
        val fake = { url: String -> urls += url; """{"observations":[{"d":"2026-03-30","FXUSDCAD":{"v":"1.36"}}]}""" }
        assertEquals(1, books.rates.updateFromBankOfCanada(d(3, 31), fake))
        assertEquals("https://www.bankofcanada.ca/valet/observations/FXUSDCAD/json?start_date=2026-01-01&end_date=2026-03-31", urls.single())
        books.rates.updateFromBankOfCanada(d(3, 31), fake)
        assertTrue(urls.last().contains("start_date=2026-03-31"))
    }

    @Test
    fun `periods and comparisons`() {
        assertEquals(
            listOf(d(1, 15) to d(1, 31), d(2, 1) to d(2, 28), d(3, 1) to d(3, 10)),
            ReportService.periods(d(1, 15), d(3, 10), Granularity.MONTH),
        )
        assertEquals(ReportFilter(d(1, 1), d(3, 31)).previousPeriod(), ReportFilter(LocalDate(2025, 10, 3), LocalDate(2025, 12, 31)))
        assertEquals(ReportFilter(LocalDate(2025, 1, 1), LocalDate(2025, 3, 31)), q1.sameLastYear())
    }
}

class BudgetServiceTest {

    @TempDir
    lateinit var temp: Path

    private lateinit var books: Books
    private lateinit var account: Account
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun d(m: Int, day: Int) = LocalDate(2026, m, day)
    private fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id

    private fun spend(date: LocalDate, amount: String, key: String) = books.transactions.create(
        TransactionDraft(account.id, date, cad(amount), "Shop", listOf(SplitDraft(cat(key), cad(amount)))),
    )

    @BeforeEach
    fun setUp() {
        val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
        books = Books(store.create(temp.resolve("B.hfm"), "B", "perry", "Perry", "admin-pass".toCharArray()).session)
        account = books.accounts.create(AccountDraft(books.groups().single().id, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("0"), d(1, 1)))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    @Test
    fun `a parent budget covers subcategories without their own budget`() {
        books.budgets.set(cat("food"), BudgetPeriod.MONTHLY, cad("600.00"), startMonth = d(1, 1))
        books.budgets.set(cat("food.restaurants"), BudgetPeriod.MONTHLY, cad("100.00"), startMonth = d(1, 1))
        spend(d(3, 2), "-450.00", "food.groceries")
        spend(d(3, 9), "-30.00", "food.coffee_snacks")
        spend(d(3, 15), "-140.00", "food.restaurants")
        val lines = books.budgets.month(d(3, 1)).lines.associateBy { it.category.systemKey }
        assertEquals(cad("480.00"), lines.getValue("food").actual, "groceries + coffee, not restaurants")
        assertEquals(cad("140.00"), lines.getValue("food.restaurants").actual)
        assertTrue(lines.getValue("food.restaurants").isOver)
        assertEquals(cad("120.00"), lines.getValue("food").remaining)
    }

    @Test
    fun `rollover carries unspent and overspent amounts`() {
        books.budgets.set(cat("leisure"), BudgetPeriod.MONTHLY, cad("100.00"), rollover = true, startMonth = d(1, 1))
        spend(d(1, 10), "-40.00", "leisure.hobbies") // 60 unspent
        spend(d(2, 10), "-130.00", "leisure.entertainment") // 30 over
        val march = books.budgets.month(d(3, 1)).lines.single()
        assertEquals(cad("30.00"), march.carriedOver)
        assertEquals(cad("130.00"), march.budgeted)
    }

    @Test
    fun `annual budgets compare the year to date, and the year view`() {
        books.budgets.set(cat("housing.insurance"), BudgetPeriod.ANNUAL, cad("1200.00"), startMonth = d(1, 1))
        books.budgets.set(cat("food"), BudgetPeriod.MONTHLY, cad("500.00"), startMonth = d(1, 1))
        spend(d(2, 1), "-1184.00", "housing.insurance")
        spend(d(4, 5), "-300.00", "food.groceries")
        val april = books.budgets.month(d(4, 1)).lines.associateBy { it.category.systemKey }
        assertEquals(cad("1184.00"), april.getValue("housing.insurance").actual)
        assertEquals(cad("1200.00"), april.getValue("housing.insurance").budgeted)

        val year = books.budgets.year(2026).lines.associateBy { it.category.systemKey }
        assertEquals(cad("6000.00"), year.getValue("food").budgeted)
        assertEquals(cad("300.00"), year.getValue("food").actual)
    }

    @Test
    fun `income budgets and totals`() {
        books.budgets.set(cat("income.employment"), BudgetPeriod.MONTHLY, cad("6000.00"), startMonth = d(1, 1))
        spend(d(5, 1), "3000.00", "income.employment.salary")
        val report = books.budgets.month(d(5, 1))
        assertEquals(cad("3000.00"), report.lines.single().actual)
        assertEquals(cad("6000.00") to cad("3000.00"), report.total(CategoryKind.INCOME, Currency.CAD))
    }

    @Test
    fun `suggested budgets from the last twelve months`() {
        spend(LocalDate(2025, 11, 5), "-1200.00", "food.groceries")
        spend(LocalDate(2026, 2, 5), "-1200.00", "food.groceries")
        spend(LocalDate(2026, 10, 5), "-999.00", "food.groceries") // current month: not counted
        val suggestions = books.budgets.suggestions(today = d(10, 15))
        assertEquals(cad("200.00"), suggestions[cat("food")])
        books.budgets.set(cat("food"), BudgetPeriod.MONTHLY, suggestions.getValue(cat("food")), startMonth = d(10, 1))
        assertEquals(1, books.budgets.list().size)
        books.budgets.remove(cat("food"))
        assertTrue(books.budgets.list().isEmpty())
    }
}
