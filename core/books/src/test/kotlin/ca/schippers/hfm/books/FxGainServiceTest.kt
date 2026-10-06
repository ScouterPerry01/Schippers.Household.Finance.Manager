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
import kotlin.test.assertTrue

class FxGainServiceTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var group: String
    private lateinit var alex: Member
    private lateinit var chequing: Account
    private lateinit var usd: Account
    private lateinit var usdBroker: Account
    private fun d(s: String) = LocalDate.parse(s)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun us(s: String) = Money.parse(s, Currency.USD)
    private fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("F.hfm"), "F", "perry", "Perry", "pw".toCharArray()).session)
        group = books.groups().single().id
        alex = books.members.create("Alex", MemberKind.ADULT)
        fun account(name: String, type: AccountType, currency: Currency, opening: String) =
            books.accounts.create(AccountDraft(group, name, type, currency, Money.parse(opening, currency), d("2026-01-01"), ownerMemberIds = setOf(alex.id)))
        chequing = account("Chèques", AccountType.CHEQUING, Currency.CAD, "100000")
        usd = account("Compte US", AccountType.SAVINGS, Currency.USD, "0")
        usdBroker = account("Courtage US", AccountType.BROKERAGE, Currency.USD, "0")
        for ((date, rate) in listOf("2026-01-02" to "1.30", "2026-06-01" to "1.40", "2026-12-31" to "1.35")) books.rates.setManual(Currency.USD, d(date), BigDecimal(rate))
        // US$10,000 bought for $13,000.
        books.transactions.transfer(TransferDraft(chequing.id, usd.id, d("2026-01-02"), cad("13000"), us("10000")))
        // US$2,000 spent while the dollar was at 1.40: worth $2,800, cost $2,600.
        books.transactions.create(TransactionDraft(usd.id, d("2026-06-01"), us("-2000"), "Hotel", listOf(SplitDraft(cat("travel"), us("-2000")))))
        // US$3,000 brought home as $4,170: cost $3,900.
        books.transactions.transfer(TransferDraft(usd.id, chequing.id, d("2026-07-01"), us("3000"), cad("4170")))
        // A move between two US-dollar accounts of the same person is neither.
        books.transactions.transfer(TransferDraft(usd.id, usdBroker.id, d("2026-08-01"), us("1000")))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    @Test
    fun `realized and unrealized gains, with the 200 dollar exemption`() {
        val r = books.fxGains.report(2026, d("2026-12-31"))
        assertEquals(listOf(cad("200"), cad("270")), r.disposals.map { it.disposition.gain })
        val alexFx = r.people.single()
        assertEquals(cad("470"), alexFx.net)
        assertEquals(cad("270"), alexFx.reportable, "the first $200 is left out")
        val h = r.holdings.single()
        assertEquals(us("5000"), h.balance, "both US-dollar accounts together")
        assertEquals(cad("6500"), h.cost)
        assertEquals(cad("6750"), h.value)
        assertEquals(cad("250"), h.unrealized)
        assertTrue(r.problems.isEmpty())
        // Next year has nothing realized yet.
        assertTrue(books.fxGains.report(2027, d("2027-03-01")).disposals.isEmpty())
    }

    @Test
    fun `exposure counts securities by trading currency, registered plans and debts`() {
        fun account(name: String, type: AccountType, currency: Currency, opening: String) =
            books.accounts.create(AccountDraft(group, name, type, currency, Money.parse(opening, currency), d("2026-01-01"), ownerMemberIds = setOf(alex.id)))
        val inv = books.investments
        val aapl = inv.saveSecurity(Security("", "AAPL", "NASDAQ", "Apple Inc.", SecurityKind.STOCK, Currency.USD))
        val xyz = inv.saveSecurity(Security("", "XYZ", "NYSE", "XYZ Corp", SecurityKind.STOCK, Currency.USD))
        // A US stock held in a Canadian-dollar TFSA is US-dollar exposure, in the registered column.
        val tfsa = account("CELI", AccountType.TFSA, Currency.CAD, "5000")
        inv.save(InvestmentTxn("", tfsa.id, d("2026-03-02"), InvestmentKind.BUY, aapl.id, BigDecimal("10"), BigDecimal("270"), cad("2700")))
        // US$500 of the US brokerage cash buys a US stock.
        inv.save(InvestmentTxn("", usdBroker.id, d("2026-03-02"), InvestmentKind.BUY, xyz.id, BigDecimal("5"), BigDecimal("100"), us("500")))
        inv.setPrice(aapl.id, d("2026-12-31"), BigDecimal("210"))
        inv.setPrice(xyz.id, d("2026-12-31"), BigDecimal("120"))
        // A US-dollar card owing US$300, and a euro account with no rate.
        account("Visa US", AccountType.CREDIT_CARD, Currency.USD, "-300")
        account("Compte EUR", AccountType.SAVINGS, Currency.EUR, "1000")

        val r = books.fxGains.report(2026, d("2026-12-31"))
        val usd = r.exposure.single()
        assertEquals(Currency.USD, usd.currency)
        assertEquals(cad("6075"), usd.cash, "US$4,000 saved and US$500 left in the brokerage, at 1.35")
        assertEquals(cad("810"), usd.securities, "5 XYZ at US$120")
        assertEquals(cad("2835"), usd.registered, "10 AAPL at US$210 in the TFSA")
        assertEquals(cad("405"), usd.debts)
        assertEquals(cad("9315"), usd.net)
        assertTrue(Currency.EUR in r.missingRates, "the euro account has no rate")
        // With a euro rate, the euro account shows as its own line.
        books.rates.setManual(Currency.EUR, d("2026-12-31"), BigDecimal("1.50"))
        val eur = books.fxGains.exposure(d("2026-12-31")).first { it.currency == Currency.EUR }
        assertEquals(cad("1500"), eur.cash)
        // Before anything was bought, there was no exposure.
        assertTrue(books.fxGains.exposure(d("2025-12-31")).isEmpty())
    }

    @Test
    fun `reports in the original currency`() {
        val year = ReportFilter(d("2026-01-01"), d("2026-12-31"))
        val inCad = books.reports.byCategory(year).value.single()
        assertEquals(cad("2800"), inCad.amount, "the hotel at 1.40")
        val inUsd = books.reports.byCategory(year.copy(currency = Currency.USD)).value.single()
        assertEquals(us("2000"), inUsd.amount, "only US-dollar accounts, in dollars")
        assertTrue(books.reports.byCategory(year.copy(currency = Currency.EUR)).value.isEmpty())
        val worth = books.reports.netWorth(listOf(d("2026-12-31")), currency = Currency.USD).value.single()
        assertEquals(us("5000"), worth.assets)
        assertEquals(CategoryKind.EXPENSE, inUsd.category!!.kind)
    }
}
