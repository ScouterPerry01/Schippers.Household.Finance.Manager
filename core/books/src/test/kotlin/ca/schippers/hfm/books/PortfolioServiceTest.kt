package ca.schippers.hfm.books

import ca.schippers.hfm.calc.invest.Returns
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
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortfolioServiceTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var group: String
    private lateinit var alex: Member
    private lateinit var chequing: Account
    private lateinit var brokerage: Account
    private lateinit var xic: Security
    private fun d(s: String) = LocalDate.parse(s)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun n(s: String) = BigDecimal(s)
    private fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id
    private val inv get() = books.investments
    private val year get() = books.portfolio.performance(d("2026-01-01"), d("2026-12-31"))

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("P.hfm"), "P", "perry", "Perry", "pw".toCharArray()).session)
        group = books.groups().single().id
        alex = books.members.create("Alex", MemberKind.ADULT)
        chequing = account("Chèques", AccountType.CHEQUING, "50000")
        brokerage = account("Courtage", AccountType.BROKERAGE, "0")
        xic = inv.saveSecurity(Security("", "xic", "tsx", "iShares Core S&P/TSX Capped Composite", SecurityKind.ETF, Currency.CAD))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun account(name: String, type: AccountType, opening: String) =
        books.accounts.create(AccountDraft(group, name, type, Currency.CAD, cad(opening), d("2026-01-01"), ownerMemberIds = setOf(alex.id)))

    private fun deposit(to: Account, date: String, amount: String, from: Account = chequing) =
        books.transactions.transfer(TransferDraft(from.id, to.id, d(date), cad(amount)))

    private fun buy(date: String, qty: String, price: String) =
        inv.save(InvestmentTxn("", brokerage.id, d(date), InvestmentKind.BUY, xic.id, n(qty), n(price), Money.of(n(qty).multiply(n(price)), Currency.CAD)))

    /** Deposits, two purchases, a dividend and a rise: the returns worked out by hand. */
    private fun history() {
        deposit(brokerage, "2026-01-02", "10000")
        buy("2026-01-05", "100", "100")
        inv.setPrice(xic.id, d("2026-06-30"), n("90"))
        deposit(brokerage, "2026-07-01", "9000")
        buy("2026-07-02", "100", "90")
        inv.save(InvestmentTxn("", brokerage.id, d("2026-12-15"), InvestmentKind.INCOME, xic.id, amount = cad("200"), incomeType = IncomeType.DIVIDEND))
        inv.setPrice(xic.id, d("2026-12-31"), n("110"))
    }

    @Test
    fun `returns over a year with two deposits`() {
        history()
        val total = year.total
        assertEquals(cad("0"), total.startValue, "the brokerage account opened empty")
        assertEquals(cad("22200"), total.endValue, "200 units at 110, and the dividend in cash")
        assertEquals(cad("19000"), total.contributions)
        assertEquals(cad("0"), total.withdrawals)
        assertEquals(cad("200"), total.income)
        assertEquals(cad("3200"), total.gain)
        assertEquals(cad("3000"), total.marketChange)
        // 10,000 became 9,000 by July (−10 %), then 18,000 became 22,200 (+23.3 %): 0.9 × 1.2333 − 1.
        assertEquals(n("0.110000"), total.timeWeighted!!.setScale(6, RoundingMode.HALF_UP))
        assertNull(total.timeWeightedAnnual, "invested from January 2: just under a year, so not annualized")
        assertNull(total.moneyWeightedAnnual)
        // The July deposit bought at the low and caught the rise: the personal rate is higher.
        val annual = Returns.xirr(listOf(d("2026-01-02") to n("-10000"), d("2026-07-01") to n("-9000"), d("2026-12-31") to n("22200")))!!
        assertEquals(Returns.overDays(annual, 363).setScale(4, RoundingMode.HALF_UP), total.moneyWeighted!!.setScale(4, RoundingMode.HALF_UP))
        assertTrue(total.moneyWeighted > total.timeWeighted)

        // Over two years with the account already invested at the start, rates are per year.
        val two = books.portfolio.performance(d("2026-01-03"), d("2027-12-31")).total
        assertEquals(cad("10000"), two.startValue, "the January 2 deposit, not yet invested")
        assertEquals(n("0.110000"), two.timeWeighted!!.setScale(6, RoundingMode.HALF_UP), "nothing changed after 2026")
        assertEquals(Returns.annualized(two.timeWeighted, 728), two.timeWeightedAnnual, "January 2, 2026 to December 31, 2027")
        assertTrue(two.moneyWeightedAnnual != null)
        assertEquals(listOf(brokerage.id), year.accounts.map { it.account!!.id })
    }

    @Test
    fun `under a year nothing is annualized`() {
        history()
        val half = books.portfolio.performance(d("2026-07-01"), d("2026-12-31")).total
        assertEquals(cad("9000"), half.startValue, "100 units at the June 30 price of 90")
        assertNull(half.timeWeightedAnnual)
        assertNull(half.moneyWeightedAnnual)
    }

    @Test
    fun `moves between chosen accounts stay inside the portfolio`() {
        history()
        val tfsa = account("CELI", AccountType.TFSA, "0")
        deposit(tfsa, "2026-08-01", "1000", from = brokerage)
        val all = year
        assertEquals(cad("19000"), all.total.contributions, "the move to the TFSA is not new money")
        assertEquals(cad("0"), all.total.withdrawals)
        assertEquals(cad("1000"), all.accounts.first { it.account!!.id == brokerage.id }.withdrawals, "but for the account alone it is")
        assertEquals(cad("1000"), all.accounts.first { it.account!!.id == tfsa.id }.contributions)
        val brokerageOnly = books.portfolio.performance(d("2026-01-01"), d("2026-12-31"), setOf(brokerage.id)).total
        assertEquals(cad("1000"), brokerageOnly.withdrawals)
    }

    @Test
    fun `fees and foreign tax are costs, units moved in are money put in`() {
        inv.setPrice(xic.id, d("2026-02-27"), n("100"))
        inv.save(InvestmentTxn("", brokerage.id, d("2026-03-01"), InvestmentKind.TRANSFER_IN, xic.id, n("50"), amount = cad("4000")))
        deposit(brokerage, "2026-03-02", "500")
        inv.save(InvestmentTxn("", brokerage.id, d("2026-06-30"), InvestmentKind.INCOME, xic.id, amount = cad("100"), withheld = cad("15"), incomeType = IncomeType.DIVIDEND))
        inv.save(InvestmentTxn("", brokerage.id, d("2026-09-30"), InvestmentKind.FEE, amount = cad("25")))
        // A line typed in the register: interest is income, a withdrawal for groceries is money taken out.
        books.transactions.create(TransactionDraft(brokerage.id, d("2026-10-01"), cad("5"), splits = listOf(SplitDraft(cat("income.investment.interest"), cad("5")))))
        books.transactions.create(TransactionDraft(brokerage.id, d("2026-10-02"), cad("-60"), splits = listOf(SplitDraft(cat("food.groceries"), cad("-60")))))
        inv.setPrice(xic.id, d("2026-12-31"), n("104"))

        val t = year.total
        assertEquals(cad("5500"), t.contributions, "50 units at 100, valued that day, and the deposit")
        assertEquals(cad("60"), t.withdrawals)
        assertEquals(cad("105"), t.income)
        assertEquals(cad("40"), t.costs, "15 withheld and the 25 fee")
        assertEquals(cad("5200") + cad("505"), t.endValue, "50 at 104, and 500 + 85 − 25 + 5 − 60 in cash")
        assertEquals(cad("200"), t.marketChange)
    }
}
