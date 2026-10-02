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
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AllocationServiceTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var group: String
    private lateinit var alex: Member
    private lateinit var brokerage: Account
    private lateinit var xglo: Security
    private fun d(s: String) = LocalDate.parse(s)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun n(s: String) = BigDecimal(s)
    private val inv get() = books.investments
    private val june = LocalDate(2026, 6, 30)

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("A.hfm"), "A", "perry", "Perry", "pw".toCharArray()).session)
        group = books.groups().single().id
        alex = books.members.create("Alex", MemberKind.ADULT)
        brokerage = books.accounts.create(AccountDraft(group, "Courtage", AccountType.BROKERAGE, Currency.CAD, cad("10000"), d("2026-01-01"), ownerMemberIds = setOf(alex.id)))
        books.rates.setManual(Currency.USD, d("2026-01-02"), n("1.35"))
        val xic = inv.saveSecurity(Security("", "XIC", "TSX", "Canadian equity", SecurityKind.ETF, Currency.CAD, AssetClass.EQUITY, Region.CANADA))
        val xbal = inv.saveSecurity(
            Security(
                "", "XBAL", "TSX", "Balanced", SecurityKind.ETF, Currency.CAD, AssetClass.BALANCED, Region.GLOBAL,
                classMix = mapOf(AssetClass.EQUITY to n("60"), AssetClass.FIXED_INCOME to n("40")),
                regionMix = mapOf(Region.CANADA to n("30"), Region.US to n("50"), Region.INTERNATIONAL to n("20")),
            ),
        )
        val vti = inv.saveSecurity(Security("", "VTI", "NYSE", "US equity", SecurityKind.ETF, Currency.USD, AssetClass.EQUITY, Region.US))
        xglo = inv.saveSecurity(Security("", "XGLO", "TSX", "Global equity", SecurityKind.ETF, Currency.CAD, AssetClass.EQUITY, Region.GLOBAL))
        buy(xic, "100", "40", "4000")
        buy(xbal, "100", "20", "2000")
        buy(vti, "10", "200", "2700") // US$2,000 at 1.35
        buy(xglo, "10", "10", "100")
        inv.setPrice(xic.id, june, n("50"))
        inv.setPrice(xbal.id, june, n("20"))
        inv.setPrice(vti.id, june, n("200"))
        inv.setPrice(xglo.id, june, n("10"))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun buy(s: Security, qty: String, price: String, cost: String) =
        inv.save(InvestmentTxn("", brokerage.id, d("2026-01-05"), InvestmentKind.BUY, s.id, n(qty), n(price), cad(cost)))

    private fun AllocationReport.values() = slices.associate { it.key to it.value }

    @Test
    fun `divided by class, region and currency, with fund mixes`() {
        val byClass = books.allocation.allocation(AllocationBy.CLASS, june)
        assertEquals(cad("11000"), byClass.total, "1,200 cash, 5,000 XIC, 2,000 XBAL, 2,700 VTI, 100 XGLO")
        assertEquals(mapOf("EQUITY" to cad("9000"), "FIXED_INCOME" to cad("800"), "CASH" to cad("1200")), byClass.values(), "XBAL is 60 % equity")
        val byRegion = books.allocation.allocation(AllocationBy.REGION, june)
        assertEquals(mapOf("CANADA" to cad("6800"), "US" to cad("3700"), "INTERNATIONAL" to cad("400"), "GLOBAL" to cad("100")), byRegion.values(), "cash in dollars counts as Canada")
        assertEquals(setOf(xglo), byRegion.unsplit, "a global fund with no regions entered")
        assertEquals(mapOf("CAD" to cad("8300"), "USD" to cad("2700")), books.allocation.allocation(AllocationBy.CURRENCY, june).values())
        assertEquals(mapOf(brokerage.id to cad("11000")), books.allocation.allocation(AllocationBy.ACCOUNT, june).values())
    }

    @Test
    fun `against a target, with rebalancing`() {
        val household = TargetScope.Household
        books.allocation.setTarget(household, AllocationBy.CLASS, AllocationTarget(mapOf("EQUITY" to n("60"), "FIXED_INCOME" to n("30"), "CASH" to n("10"))))
        val r = books.allocation.allocation(AllocationBy.CLASS, june, scope = household)
        assertEquals(n("81.82"), r.slices.first { it.key == "EQUITY" }.percent.setScale(2, RoundingMode.HALF_UP))
        assertEquals(listOf("EQUITY", "FIXED_INCOME"), r.offTarget.map { it.key }, "cash is within 5 points of 10 %")
        // New money goes where it is short; a full rebalance also sells.
        assertEquals(mapOf("FIXED_INCOME" to cad("1000")), books.allocation.rebalance(r, cad("1000")))
        assertEquals(mapOf("EQUITY" to cad("-2400"), "FIXED_INCOME" to cad("2500"), "CASH" to cad("-100")), books.allocation.rebalance(r, sell = true))

        // Each person or group can have their own; removing one leaves the others.
        val mine = TargetScope.Person(alex.id)
        books.allocation.setTarget(mine, AllocationBy.CLASS, AllocationTarget(mapOf("EQUITY" to n("100")), n("2")))
        assertEquals(n("2"), books.allocation.target(mine)!!.tolerance)
        books.allocation.setTarget(mine, AllocationBy.CLASS, null)
        assertNull(books.allocation.target(mine))
        assertEquals(n("60"), books.allocation.target(household)!!.weights["EQUITY"])
        assertFailsWith<ValidationException> { books.allocation.setTarget(household, AllocationBy.CLASS, AllocationTarget(mapOf("EQUITY" to n("60")))) }
        assertFailsWith<ValidationException> { books.allocation.rebalance(books.allocation.allocation(AllocationBy.REGION, june, scope = household)) }
    }

    @Test
    fun `wallets are crypto-assets, a mix must add up to 100`() {
        books.rates.setManual(Currency.BTC, june, n("80000"))
        books.accounts.create(AccountDraft(group, "Bitcoin", AccountType.CRYPTO_WALLET, Currency.BTC, Money.parse("0.01", Currency.BTC), d("2026-01-01")))
        assertEquals(cad("800"), books.allocation.allocation(AllocationBy.CLASS, june).values()[AllocationService.CRYPTO])
        val bad = Security("", "BAD", null, "Bad", SecurityKind.ETF, Currency.CAD, AssetClass.BALANCED, classMix = mapOf(AssetClass.EQUITY to n("60"), AssetClass.FIXED_INCOME to n("30")))
        assertEquals("error.mixNot100", assertFailsWith<ValidationException> { inv.saveSecurity(bad) }.key)
    }
}
