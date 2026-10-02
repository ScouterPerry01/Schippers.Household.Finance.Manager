package ca.schippers.hfm.books

import ca.schippers.hfm.calc.PensionJurisdiction
import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.plans.RegisteredPlans
import ca.schippers.hfm.calc.schedule.BusinessDays
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PROV-01 to PROV-05: the household's province or territory and what follows from it. */
class ProvinceTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private var books: Books? = null
    private fun d(s: String) = LocalDate.parse(s)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private val today = LocalDate(2026, 10, 2)

    private fun open(province: String): Books =
        Books(store.create(temp.resolve("$province.hfm"), province, "perry", "Perry", "pw".toCharArray(), province = province).session, clock = { 1_790_000_000_000 })
            .also { books = it }

    @AfterEach
    fun tearDown() {
        books?.session?.close()
        BusinessDays.province = Province.QC
    }

    private fun Books.keys() = categories.list().mapNotNull { it.systemKey }.toSet()

    @Test
    fun `an Ontario household gets Ontario's categories and bank holidays`() {
        val b = open("ON")
        assertEquals(Province.ON, b.province)
        assertEquals(Province.ON, BusinessDays.province)
        val keys = b.keys()
        assertTrue("income.benefits.ei" in keys && "income.benefits.provincial" in keys)
        assertFalse("housing.school_tax" in keys || "income.benefits.family_allowance" in keys || "income.benefits.ei_qpip" in keys)
    }

    @Test
    fun `a Quebec household keeps its categories, and moving adds the new province's`() {
        val b = open("QC")
        val before = b.keys()
        assertTrue("housing.school_tax" in before && "income.benefits.ei_qpip" in before)
        assertFalse("income.benefits.ei" in before)
        b.setProvince(Province.NB)
        val after = b.keys()
        assertTrue(before.all { it in after }, "nothing is removed")
        assertTrue("income.benefits.ei" in after && "income.benefits.provincial" in after)
        assertEquals(Province.NB, b.province)
    }

    @Test
    fun `a person can live in another province`() {
        val b = open("QC")
        val student = b.members.create("Léa", MemberKind.ADULT, d("2005-03-01"))
        assertEquals(Province.QC, b.provinceOf(student.id))
        b.members.update(student.copy(province = Province.BC))
        assertEquals(Province.BC, b.members.list().single().province)
        assertEquals(Province.BC, b.provinceOf(student.id))
        assertEquals(Province.QC, b.provinceOf(null))
    }

    @Test
    fun `RESP grants follow the child's province`() {
        val b = open("BC")
        val group = b.groups().single().id
        val chequing = b.accounts.create(AccountDraft(group, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("10000"), d("2020-01-01")))
        val maya = b.members.create("Maya", MemberKind.CHILD, d("2018-04-10"))
        val leo = b.members.create("Leo", MemberKind.CHILD, d("2016-01-20"))
        b.members.update(leo.copy(province = Province.ON))
        val resp = b.accounts.create(AccountDraft(group, "RESP", AccountType.RESP, Currency.CAD, cad("0"), d("2020-01-01")))
        b.plans.saveBeneficiary(Beneficiary("", resp.id, BeneficiaryKind.RESP_BENEFICIARY, "Maya", maya.id))
        b.plans.saveBeneficiary(Beneficiary("", resp.id, BeneficiaryKind.RESP_BENEFICIARY, "Leo", leo.id))
        b.transactions.transfer(TransferDraft(chequing.id, resp.id, d("2026-02-01"), cad("2000")))
        b.plans.recordGrant(resp.id, maya.id, d("2026-05-01"), GrantKind.BCTESG, cad("1200"))

        val status = b.plans.respBeneficiaries(2026, today).associateBy { it.member.displayName }
        val m = status.getValue("Maya")
        assertEquals(RegisteredPlans.ProvincialGrant.BCTESG, m.provincialGrant)
        assertEquals(cad("1200"), m.provincialExpected, "Maya is 8: the B.C. grant is due without a contribution")
        assertEquals(cad("1200"), m.provincialReceived)
        assertEquals(cad("200.00"), m.cesgExpected, "the CESG is federal: 20% of her 1,000 share")
        val l = status.getValue("Leo")
        assertNull(l.provincialGrant, "Ontario has no provincial RESP grant")
        assertEquals(cad("0"), l.provincialExpected)
    }

    @Test
    fun `the LIF maximum follows the plan's jurisdiction`() {
        val b = open("SK")
        val group = b.groups().single().id
        val holder = b.members.create("Pat", MemberKind.ADULT, d("1960-06-01"))
        val lif = b.accounts.create(AccountDraft(group, "LIF", AccountType.LIF, Currency.CAD, cad("0"), d("2020-01-01"), ownerMemberIds = setOf(holder.id)))
        b.plans.setValueJanuary1(lif.id, 2026, cad("50000"))
        val sk = b.plans.withdrawalStatus(lif, 2026)
        assertEquals(PensionJurisdiction.Provincial(Province.SK), sk.jurisdiction)
        assertNull(sk.maximum, "no maximum in Saskatchewan")
        assertEquals(cad("2000.00"), sk.minimum)
        b.plans.saveDetails(PlanDetails(lif.id, jurisdiction = PensionJurisdiction.Federal))
        val federal = b.plans.withdrawalStatus(lif, 2026)
        assertNotNull(federal.maximum)
        assertEquals(cad("3689.94"), federal.maximum)
    }
}
