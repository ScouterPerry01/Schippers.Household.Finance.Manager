package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.ClearedStatus
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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InvestmentServiceTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var group: String
    private lateinit var alex: Member
    private lateinit var brokerage: Account
    private lateinit var xic: Security
    private fun d(s: String) = LocalDate.parse(s)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun usd(s: String) = Money.parse(s, Currency.USD)
    private fun n(s: String) = BigDecimal(s)
    private fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id
    private val inv get() = books.investments

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("I.hfm"), "I", "perry", "Perry", "pw".toCharArray()).session)
        group = books.groups().single().id
        alex = books.members.create("Alex", MemberKind.ADULT)
        brokerage = account("Courtage", AccountType.BROKERAGE, "10000")
        xic = inv.saveSecurity(Security("", "xic", "tsx", "iShares Core S&P/TSX Capped Composite", SecurityKind.ETF, Currency.CAD))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun account(name: String, type: AccountType, opening: String, currency: Currency = Currency.CAD, owners: Set<String> = setOf(alex.id)) =
        books.accounts.create(AccountDraft(group, name, type, currency, Money.parse(opening, currency), d("2026-01-01"), ownerMemberIds = owners))

    private fun buy(a: Account, s: Security, date: String, qty: String, price: String, fees: String = "0") =
        inv.save(InvestmentTxn("", a.id, d(date), InvestmentKind.BUY, s.id, n(qty), n(price), Money.of(n(qty).multiply(n(price)), a.currency), Money.parse(fees, a.currency)))

    private fun sell(a: Account, s: Security, date: String, qty: String, price: String, fees: String = "0") =
        inv.save(InvestmentTxn("", a.id, d(date), InvestmentKind.SELL, s.id, n(qty), n(price), Money.of(n(qty).multiply(n(price)), a.currency), Money.parse(fees, a.currency)))

    private fun cash(a: Account) = books.accounts.list().first { it.account.id == a.id }.balance

    @Test
    fun `trades, income and holdings`() {
        buy(brokerage, xic, "2026-01-15", "100", "38.50", "9.95")
        inv.save(InvestmentTxn("", brokerage.id, d("2026-03-31"), InvestmentKind.INCOME, xic.id, amount = cad("24.10"), incomeType = IncomeType.DIVIDEND))
        inv.save(InvestmentTxn("", brokerage.id, d("2026-06-30"), InvestmentKind.INCOME, xic.id, amount = cad("50"), withheld = cad("7.50"), incomeType = IncomeType.DIVIDEND))
        sell(brokerage, xic, "2026-08-15", "40", "41.00", "9.95")
        inv.setPrice(xic.id, d("2026-09-30"), n("42.00"))

        assertEquals(cad("7836.70"), cash(brokerage), "10,000 - 3,859.95 + 24.10 + 42.50 + 1,630.05")
        val h = inv.holdings(brokerage.id, d("2026-09-30"))
        val pos = h.holdings.single()
        assertEquals(n("60"), pos.quantity)
        assertEquals(cad("2315.97"), pos.bookCost)
        assertEquals(cad("2520.00"), pos.marketValue)
        assertEquals(cad("204.03"), pos.gain)
        assertEquals(cad("10356.70"), h.totalValue)

        // The register shows the cash; reports count the income and the tax, not the trades.
        val lines = books.transactions.register(brokerage.id).map { it.transaction }
        assertEquals(4, lines.size)
        assertTrue(lines.all { it.investmentId != null })
        val withheld = lines.first { it.date == d("2026-06-30") }
        assertEquals(setOf(cat("income.investment.dividends"), cat("taxes.foreign_tax")), withheld.splits.map { it.categoryId }.toSet())
        val year = books.reports.incomeExpense(ReportFilter(d("2026-01-01"), d("2026-12-31")), Granularity.YEAR).value.single()
        assertEquals(cad("74.10"), year.income)
        assertEquals(cad("7.50"), year.expense)
        assertFailsWith<ValidationException>("cash lines change only from Investments") { books.transactions.delete(lines.first().id) }

        // Net worth counts the securities at market value.
        assertEquals(cad("10356.70"), books.reports.netWorth(listOf(d("2026-09-30"))).value.single().assets)
    }

    @Test
    fun `ACB is pooled across non-registered accounts of the same owners`() {
        val second = account("Courtage 2", AccountType.BROKERAGE, "5000")
        val tfsa = account("CELI", AccountType.TFSA, "5000")
        val sams = account("Courtage Sam", AccountType.BROKERAGE, "5000", owners = setOf(books.members.create("Sam", MemberKind.ADULT).id))
        buy(brokerage, xic, "2026-01-15", "100", "38.50", "9.95")
        buy(second, xic, "2026-02-01", "50", "40")
        buy(tfsa, xic, "2026-02-01", "50", "40")
        buy(sams, xic, "2026-02-01", "10", "40")
        sell(brokerage, xic, "2026-08-15", "40", "41.00", "9.95")

        val report = inv.acb(d("2026-12-31"))
        val pool = report.pools.single { it.ownerMemberIds == setOf(alex.id) }
        assertEquals(n("110"), pool.quantity)
        assertEquals(setOf(brokerage.id, second.id), pool.accountIds, "the TFSA has no tax pool")
        val gain = report.gains.single().disposition
        assertEquals(cad("1562.65"), gain.cost, "40 of 150 units pooled at 5,859.95")
        assertEquals(cad("67.40"), gain.gain)
        assertEquals(cad("4297.30"), pool.acb)
        assertEquals(2, report.pools.size, "Sam's units are a separate pool")
        assertEquals(cad("2315.97"), inv.holdings(brokerage.id, d("2026-12-31")).holdings.single().bookCost, "the account keeps its own book cost")
    }

    @Test
    fun `foreign securities convert at each trade date for ACB`() {
        val us = account("Courtage US", AccountType.BROKERAGE, "5000", Currency.USD)
        val aapl = inv.saveSecurity(Security("", "AAPL", "NASDAQ", "Apple Inc.", SecurityKind.STOCK, Currency.USD, region = Region.US))
        books.rates.setManual(Currency.USD, d("2026-03-02"), n("1.35"))
        books.rates.setManual(Currency.USD, d("2026-06-01"), n("1.40"))
        buy(us, aapl, "2026-03-02", "10", "200")
        sell(us, aapl, "2026-06-01", "10", "210")
        val gain = inv.acb(d("2026-12-31")).gains.single().disposition
        assertEquals(cad("2700.00"), gain.cost)
        assertEquals(cad("2940.00"), gain.proceeds)
        assertEquals(cad("240.00"), gain.gain)
        assertEquals(usd("5100.00"), cash(us))
    }

    @Test
    fun `return of capital, notional distribution, split and merger`() {
        val abc = inv.saveSecurity(Security("", "ABC", null, "ABC Corp", SecurityKind.STOCK, Currency.CAD))
        val def = inv.saveSecurity(Security("", "DEF", null, "DEF Holdings", SecurityKind.STOCK, Currency.CAD))
        buy(brokerage, abc, "2026-01-10", "100", "10")
        inv.save(InvestmentTxn("", brokerage.id, d("2026-03-31"), InvestmentKind.RETURN_OF_CAPITAL, abc.id, amount = cad("50")))
        inv.save(InvestmentTxn("", brokerage.id, d("2026-12-31"), InvestmentKind.NOTIONAL_DISTRIBUTION, abc.id, amount = cad("30")))
        inv.save(InvestmentTxn("", brokerage.id, d("2027-01-15"), InvestmentKind.SPLIT, abc.id, amount = cad("0"), ratio = n("3")))
        inv.save(InvestmentTxn("", brokerage.id, d("2027-02-01"), InvestmentKind.MERGER, abc.id, n("300"), amount = cad("0"), ratio = n("0.5"), otherSecurityId = def.id))
        val held = inv.holdings(brokerage.id, d("2027-03-01")).holdings.single()
        assertEquals("DEF", held.security.symbol)
        assertEquals(n("150"), held.quantity)
        assertEquals(cad("980"), held.bookCost)
        assertEquals(cad("9050"), cash(brokerage), "10,000 - 1,000 + 50 returned")
        assertEquals(n("300"), inv.holdings(brokerage.id, d("2027-01-20")).holdings.single().quantity)
    }

    @Test
    fun `impossible sales are refused and edits replace the cash lines`() {
        val first = buy(brokerage, xic, "2026-01-15", "10", "38")
        assertFailsWith<ValidationException> { sell(brokerage, xic, "2026-02-01", "11", "40") }
        sell(brokerage, xic, "2026-02-01", "10", "40")
        assertFailsWith<ValidationException>("the sale needs the purchase") { inv.delete(brokerage.id, first.id) }

        inv.save(first.copy(price = n("37"), amount = cad("370")))
        val lines = books.transactions.register(brokerage.id).map { it.transaction }.filter { it.investmentId == first.id }
        assertEquals(listOf(cad("-370.00")), lines.map { it.amount })
        assertEquals(cad("10030.00"), cash(brokerage))
    }

    @Test
    fun `OFX statement imports once and reconciles`() {
        val acct = account("Disnat", AccountType.BROKERAGE, "0")
        val statement = books.brokerage.read("disnat.ofx", OFX.toByteArray(charset("windows-1252"))).single()
        val result = books.brokerage.import(acct.id, statement)
        assertEquals(6, result.added)
        assertEquals(1, result.securitiesCreated)
        assertTrue(result.statementSaved)
        assertEquals(cad("2794.20"), cash(acct))
        val held = inv.holdings(acct.id, d("2026-09-30")).holdings.single()
        assertEquals(n("122"), held.quantity)
        assertEquals(cad("2574.20"), held.marketValue)

        val st = inv.statements(acct.id).single()
        assertTrue(inv.check(acct.id, st.id).matches)
        inv.reconcile(acct.id, st.id)
        assertTrue(inv.statements(acct.id).single().reconciled)

        val again = books.brokerage.import(acct.id, statement)
        assertEquals(0, again.added)
        assertEquals(6, again.alreadyThere)
        assertEquals(cad("2794.20"), cash(acct))
    }

    @Test
    fun `a statement that differs cannot be reconciled`() {
        buy(brokerage, xic, "2026-01-15", "100", "38.50")
        val st = inv.saveStatement(brokerage.id, d("2026-01-31"), cad("6150.00"), mapOf(xic.id to n("99")))
        val check = inv.check(brokerage.id, st.id)
        assertTrue(check.cashMatches)
        assertEquals(n("100"), check.positions.single().books)
        assertFailsWith<ValidationException> { inv.reconcile(brokerage.id, st.id) }
    }

    @Test
    fun `reconciled cash lines change only once confirmed (M-26)`() {
        val trade = buy(brokerage, xic, "2026-01-15", "10", "38")
        val line = books.transactions.register(brokerage.id).single { it.transaction.investmentId == trade.id }.transaction
        books.transactions.setCleared(line.id, ClearedStatus.RECONCILED)
        assertFailsWith<ReconciledChangeException> { inv.save(trade.copy(price = n("37"), amount = cad("370"))) }
        assertFailsWith<ReconciledChangeException> { inv.delete(brokerage.id, trade.id) }
        assertEquals(cad("9620.00"), cash(brokerage), "nothing changed")
        inv.save(trade.copy(price = n("37"), amount = cad("370")), confirmReconciled = true)
        assertEquals(cad("9630.00"), cash(brokerage))
        inv.delete(brokerage.id, trade.id, confirmReconciled = true)
        assertTrue(inv.transactions(brokerage.id).isEmpty())
    }

    @Test
    fun `a bond held shows its maturity on the calendar (M-35)`() {
        val bond = inv.saveSecurity(
            Security("", "", "", "Canada 3.25% 2027", SecurityKind.BOND, Currency.CAD, AssetClass.FIXED_INCOME, maturity = d("2027-06-01"), couponRate = n("0.0325")),
        )
        assertTrue(books.renewals(d("2027-05-10")).none { it.kind == RenewalKind.SECURITY_MATURITY }, "not held yet")
        buy(brokerage, bond, "2026-02-01", "50", "99")
        assertTrue(books.renewals(d("2027-04-01")).none { it.kind == RenewalKind.SECURITY_MATURITY }, "61 days ahead")
        val due = books.renewals(d("2027-05-10")).single { it.kind == RenewalKind.SECURITY_MATURITY }
        assertEquals(22, due.daysLeft)
        assertEquals("Courtage", due.detail)
        assertTrue(books.calendar.items(d("2027-06-01"), d("2027-06-30")).any { it is CalendarItem.Renewal && it.renewal.subjectId == bond.id })
        sell(brokerage, bond, "2027-06-01", "50", "100")
        assertTrue(books.renewals(d("2027-06-05")).none { it.kind == RenewalKind.SECURITY_MATURITY }, "redeemed: nothing left to remind")
    }

    companion object {
        val OFX = """
            OFXHEADER:100
            DATA:OFXSGML
            VERSION:102
            CHARSET:1252

            <OFX>
            <INVSTMTMSGSRSV1><INVSTMTTRNRS><INVSTMTRS>
            <DTASOF>20260930
            <CURDEF>CAD
            <INVACCTFROM><BROKERID>disnat.com<ACCTID>12345678</INVACCTFROM>
            <INVTRANLIST><DTSTART>20260101<DTEND>20260930
            <INVBANKTRAN><STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20260110<TRNAMT>5000.00<FITID>T0<NAME>Dépôt</STMTTRN><SUBACCTFUND>CASH</INVBANKTRAN>
            <BUYSTOCK><INVBUY><INVTRAN><FITID>T1<DTTRADE>20260115</INVTRAN>
              <SECID><UNIQUEID>464286103<UNIQUEIDTYPE>CUSIP</SECID><UNITS>100<UNITPRICE>38.50<COMMISSION>9.95<TOTAL>-3859.95</INVBUY><BUYTYPE>BUY</BUYSTOCK>
            <INCOME><INVTRAN><FITID>T2<DTTRADE>20260331</INVTRAN><SECID><UNIQUEID>464286103<UNIQUEIDTYPE>CUSIP</SECID>
              <INCOMETYPE>DIV<TOTAL>24.10</INCOME>
            <REINVEST><INVTRAN><FITID>T3<DTTRADE>20260630</INVTRAN><SECID><UNIQUEID>464286103<UNIQUEIDTYPE>CUSIP</SECID>
              <INCOMETYPE>CGLONG<TOTAL>-38.90<UNITS>1<UNITPRICE>38.90</REINVEST>
            <SELLSTOCK><INVSELL><INVTRAN><FITID>T4<DTTRADE>20260815</INVTRAN><SECID><UNIQUEID>464286103<UNIQUEIDTYPE>CUSIP</SECID>
              <UNITS>-40<UNITPRICE>41.00<COMMISSION>9.95<TOTAL>1630.05</INVSELL><SELLTYPE>SELL</SELLSTOCK>
            <SPLIT><INVTRAN><FITID>T5<DTTRADE>20260901</INVTRAN><SECID><UNIQUEID>464286103<UNIQUEIDTYPE>CUSIP</SECID>
              <OLDUNITS>61<NEWUNITS>122<NUMERATOR>2<DENOMINATOR>1</SPLIT>
            </INVTRANLIST>
            <INVPOSLIST><POSSTOCK><INVPOS><SECID><UNIQUEID>464286103<UNIQUEIDTYPE>CUSIP</SECID><HELDINACCT>CASH<POSTYPE>LONG
              <UNITS>122<UNITPRICE>21.10<MKTVAL>2574.20<DTPRICEASOF>20260930</INVPOS></POSSTOCK></INVPOSLIST>
            <INVBAL><AVAILCASH>2794.20<MARGINBALANCE>0<SHORTBALANCE>0</INVBAL>
            </INVSTMTRS></INVSTMTTRNRS></INVSTMTMSGSRSV1>
            <SECLISTMSGSRSV1><SECLIST>
            <STOCKINFO><SECINFO><SECID><UNIQUEID>464286103<UNIQUEIDTYPE>CUSIP</SECID><SECNAME>iShares S&amp;P/TSX 60<TICKER>XIU<UNITPRICE>21.10<DTASOF>20260930</SECINFO></STOCKINFO>
            </SECLIST></SECLISTMSGSRSV1>
            </OFX>
        """.trimIndent()
    }
}
