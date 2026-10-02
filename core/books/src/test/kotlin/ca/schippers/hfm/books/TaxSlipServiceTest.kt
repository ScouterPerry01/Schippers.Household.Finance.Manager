package ca.schippers.hfm.books

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.invest.SlipKind
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
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaxSlipServiceTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var group: String
    private lateinit var alex: Member
    private lateinit var sam: Member
    private lateinit var joint: Account
    private lateinit var xic: Security
    private fun d(s: String) = LocalDate.parse(s)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun n(s: String) = BigDecimal(s)
    private val inv get() = books.investments
    private fun boxes(vararg pairs: Pair<String, String>) = pairs.associate { it.first to cad(it.second) }

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("T.hfm"), "T", "perry", "Perry", "pw".toCharArray()).session)
        books.setProvince(Province.ON)
        group = books.groups().single().id
        alex = books.members.create("Alex", MemberKind.ADULT)
        sam = books.members.create("Sam", MemberKind.ADULT).also { books.members.update(it.copy(province = Province.QC)) }
        joint = books.accounts.create(AccountDraft(group, "Joint", AccountType.BROKERAGE, Currency.CAD, cad("50000"), d("2026-01-01"), ownerMemberIds = setOf(alex.id, sam.id)))
        val ry = inv.saveSecurity(Security("", "RY", "TSX", "Royal Bank", SecurityKind.STOCK, Currency.CAD))
        xic = inv.saveSecurity(Security("", "XIC", "TSX", "iShares XIC", SecurityKind.ETF, Currency.CAD))
        val vti = inv.saveSecurity(Security("", "VTI", "NYSE", "Vanguard Total Market", SecurityKind.ETF, Currency.USD, region = Region.US))
        fun txn(date: String, kind: InvestmentKind, s: Security?, amount: String, type: IncomeType? = null, qty: String? = null, withheld: String = "0") =
            inv.save(InvestmentTxn("", joint.id, d(date), kind, s?.id, qty?.let(::n), null, cad(amount), withheld = cad(withheld), incomeType = type))
        txn("2026-01-10", InvestmentKind.BUY, ry, "1000", qty = "10")
        txn("2026-01-10", InvestmentKind.BUY, xic, "4000", qty = "100")
        txn("2026-01-10", InvestmentKind.BUY, vti, "3000", qty = "10")
        txn("2026-03-31", InvestmentKind.INCOME, ry, "200", IncomeType.DIVIDEND)
        txn("2026-06-30", InvestmentKind.INCOME, xic, "120", IncomeType.DISTRIBUTION)
        txn("2026-06-30", InvestmentKind.RETURN_OF_CAPITAL, xic, "10")
        txn("2026-12-31", InvestmentKind.NOTIONAL_DISTRIBUTION, xic, "30")
        txn("2026-09-30", InvestmentKind.INCOME, vti, "100", IncomeType.DIVIDEND, withheld = "15")
        txn("2026-11-30", InvestmentKind.INCOME, null, "50", IncomeType.INTEREST)
        txn("2026-08-01", InvestmentKind.SELL, ry, "1200", qty = "10")
        // Last year's income is not in this year's slips; registered plans give no slips.
        txn("2025-12-31", InvestmentKind.INCOME, null, "999", IncomeType.INTEREST)
        val tfsa = books.accounts.create(AccountDraft(group, "CELI", AccountType.TFSA, Currency.CAD, cad("1000"), d("2026-01-01"), ownerMemberIds = setOf(alex.id)))
        inv.save(InvestmentTxn("", tfsa.id, d("2026-05-01"), InvestmentKind.INCOME, null, amount = cad("40"), incomeType = IncomeType.INTEREST))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun person(m: Member, year: Int = 2026) = books.taxSlips.report(year).people.first { it.member?.id == m.id }

    @Test
    fun `slips estimated from the books, shared by the owners`() {
        val a = person(alex)
        assertEquals(Province.ON, a.province)
        assertEquals(listOf(SlipKind.T5, SlipKind.T3), a.slips.map { it.kind }, "no Quebec slips in Ontario")
        assertEquals(boxes("13" to "25", "15" to "50", "16" to "7.50", "24" to "100", "25" to "138", "26" to "20.73"), a.totals(SlipKind.T5), "half of each, the TFSA left out")
        val t3 = a.slips.single { it.kind == SlipKind.T3 }
        assertEquals(xic, t3.security)
        assertEquals(boxes("21" to "15", "26" to "60", "42" to "5"), t3.boxes)
        assertEquals(2, t3.shareOf)
        assertNull(t3.entered)
        // Capital gains: half of the 200 gain on RY.
        assertEquals(cad("100"), a.netGain)
        assertEquals(cad("50"), a.taxableGain)

        // Sam files in Quebec: the RL-3 and RL-16 come with the federal slips.
        val s = person(sam)
        assertEquals(listOf(SlipKind.T5, SlipKind.T3, SlipKind.RL3, SlipKind.RL16), s.slips.map { it.kind })
        assertEquals(boxes("A1" to "100", "B" to "138", "D" to "25", "F" to "50", "G" to "7.50"), s.totals(SlipKind.RL3))
        assertEquals(boxes("A" to "15", "G" to "60", "M" to "5"), s.totals(SlipKind.RL16))
    }

    @Test
    fun `a slip entered replaces the estimate`() {
        // The whole account's estimate prefills the slip to enter.
        assertEquals(mapOf("21" to n("30.00"), "26" to n("120.00"), "42" to n("10.00")), books.taxSlips.estimate(joint.id, 2026, SlipKind.T3, xic.id))
        assertEquals(mapOf("A" to n("30.00"), "G" to n("120.00"), "M" to n("10.00")), books.taxSlips.estimate(joint.id, 2026, SlipKind.RL16, xic.id))
        books.taxSlips.save(EnteredSlip("", joint.id, 2026, SlipKind.T3, xic.id, mapOf("26" to n("80"), "49" to n("40"), "42" to n("10"))))
        val t3 = person(alex).slips.single { it.kind == SlipKind.T3 }
        assertEquals(boxes("26" to "40", "49" to "20", "42" to "5"), t3.boxes)
        assertEquals(boxes("21" to "15", "26" to "60", "42" to "5"), t3.estimate, "the estimate stays visible for comparison")
        assertTrue(t3.entered != null)
        // Entering it again updates the same slip; the RL-16 is still estimated until entered.
        books.taxSlips.save(EnteredSlip("", joint.id, 2026, SlipKind.T3, xic.id, mapOf("26" to n("90"))))
        assertEquals(1, books.taxSlips.slips(2026).size)
        assertEquals(boxes("A" to "15", "G" to "60", "M" to "5"), person(sam).totals(SlipKind.RL16))
        books.taxSlips.delete(joint.id, books.taxSlips.slips(2026).single().id)
        assertNull(person(alex).slips.single { it.kind == SlipKind.T3 }.entered)

        val tfsa = books.accounts.list().first { it.account.type == AccountType.TFSA }.account
        assertFailsWith<ValidationException> { books.taxSlips.save(EnteredSlip("", tfsa.id, 2026, SlipKind.T5, null, mapOf("13" to n("40")))) }
        assertFailsWith<ValidationException>("a T5 has no box 42") { books.taxSlips.save(EnteredSlip("", joint.id, 2026, SlipKind.T5, null, mapOf("42" to n("1")))) }
    }

    @Test
    fun `other years`() {
        assertEquals(boxes("13" to "499.50"), person(alex, 2025).totals(SlipKind.T5))
        assertTrue(books.taxSlips.report(2024).people.isEmpty())
    }
}
