package ca.schippers.hfm.books

import ca.schippers.hfm.calc.plans.RegisteredPlans
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanServiceTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var group: String
    private lateinit var chequing: Account
    private lateinit var alex: Member
    private lateinit var sam: Member
    private fun d(s: String) = LocalDate.parse(s)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private val plans get() = books.plans
    private val today = LocalDate(2026, 10, 2)

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("P.hfm"), "P", "perry", "Perry", "pw".toCharArray()).session, clock = { 1_790_000_000_000 })
        group = books.groups().single().id
        alex = books.members.create("Alex", MemberKind.ADULT, d("1980-04-12"))
        sam = books.members.create("Sam", MemberKind.ADULT, d("1995-09-30"))
        chequing = account("Chequing", AccountType.CHEQUING, "500000", emptySet())
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun account(name: String, type: AccountType, opening: String, owners: Set<String>, opened: String = "2020-01-01") =
        books.accounts.create(AccountDraft(group, name, type, Currency.CAD, cad(opening), d(opened), ownerMemberIds = owners))

    private fun move(from: Account, to: Account, date: String, amount: String, member: String? = null) =
        books.transactions.transfer(TransferDraft(from.id, to.id, d(date), cad(amount), memberId = member))

    @Test
    fun `RRSP room from the notice of assessment`() {
        val rrsp = account("REER Alex", AccountType.RRSP, "0", setOf(alex.id))
        val other = account("REER collectif", AccountType.RRSP, "5000", setOf(alex.id))
        val spousal = account("REER conjoint", AccountType.SPOUSAL_RRSP, "0", setOf(sam.id))
        plans.saveDetails(PlanDetails(spousal.id, contributorMemberId = alex.id))
        plans.saveRoom(RoomEntry("", alex.id, RoomPlan.RRSP, 2026, cad("20000"), cad("1000")), group)
        move(chequing, rrsp, "2026-02-15", "3000") // first 60 days: belongs to the previous year's notice
        move(chequing, rrsp, "2026-04-10", "5000")
        move(chequing, rrsp, "2027-02-20", "4000") // first 60 days of the next year: counts for 2026
        move(chequing, spousal, "2026-06-01", "2000") // uses Alex's room, not Sam's
        move(other, rrsp, "2026-07-01", "1000") // RRSP to RRSP: not a contribution

        val r = plans.room(alex.id, RoomPlan.RRSP, 2026, today)
        assertEquals(RoomSource.ENTERED, r.source)
        assertEquals(cad("19000"), r.available)
        assertEquals(cad("11000"), r.contributions)
        assertEquals(cad("8000"), r.left)
        assertNull(r.penaltyPerMonth)
        assertEquals(listOf(alex), plans.peopleWith(RoomPlan.RRSP), "the spousal plan is Alex's room, Sam has no RRSP of their own")

        move(chequing, rrsp, "2026-08-01", "11000")
        val over = plans.room(alex.id, RoomPlan.RRSP, 2026, today)
        assertEquals(cad("-3000"), over.left)
        assertEquals(cad("10.00"), over.penaltyPerMonth, "1% a month on what exceeds the $2,000 allowance")
        assertTrue(plans.warnings(today).any { it.key == "planWarning.over.RRSP" })
    }

    @Test
    fun `TFSA room estimated from the birth year or carried from the CRA figure`() {
        val tfsa = account("CELI Sam", AccountType.TFSA, "0", setOf(sam.id), "2024-01-01")
        move(chequing, tfsa, "2026-03-01", "10000")
        val estimated = plans.room(sam.id, RoomPlan.TFSA, 2026, today)
        assertEquals(RoomSource.ESTIMATED, estimated.source)
        assertEquals(cad("89000"), estimated.available, "every limit since 2013, the year Sam turned 18")
        assertEquals(cad("79000"), estimated.left)

        plans.saveRoom(RoomEntry("", sam.id, RoomPlan.TFSA, 2025, cad("20000")), group)
        move(chequing, tfsa, "2025-05-01", "5000")
        move(tfsa, chequing, "2025-09-01", "2000")
        val carried = plans.room(sam.id, RoomPlan.TFSA, 2026, today)
        assertEquals(RoomSource.ENTERED, carried.source)
        assertEquals(cad("24000"), carried.available, "20,000 - 5,000 + 2,000 withdrawn back in January + 7,000")
        assertEquals(cad("14000"), carried.left)

        move(chequing, tfsa, "2026-09-01", "15000")
        val over = plans.room(sam.id, RoomPlan.TFSA, 2026, today)
        assertEquals(cad("-1000"), over.left)
        assertEquals(cad("10.00"), over.penaltyPerMonth)
        assertTrue(plans.warnings(today).any { it.key == "planWarning.over.TFSA" })
    }

    @Test
    fun `FHSA room with carry-forward`() {
        val fhsa = account("CELIAPP Sam", AccountType.FHSA, "0", setOf(sam.id), "2024-02-01")
        move(chequing, fhsa, "2024-03-01", "3000")
        val r = plans.room(sam.id, RoomPlan.FHSA, 2026, today)
        assertEquals(cad("16000"), r.available, "8,000 plus 8,000 carried forward")
        assertEquals(cad("37000"), r.lifetimeLeft)
        assertEquals(cad("13000"), plans.room(sam.id, RoomPlan.FHSA, 2025, today).available)
    }

    @Test
    fun `RRIF minimum and LIF maximum`() {
        val pat = books.members.create("Pat", MemberKind.ADULT, d("1953-05-10"))
        val rrif = account("FERR Pat", AccountType.RRIF, "100000", setOf(pat.id))
        move(rrif, chequing, "2026-03-01", "2000")
        val w = plans.withdrawals(2026).single()
        assertEquals(72, w.age)
        assertEquals(cad("100000"), w.valueJanuary1)
        assertEquals(cad("5400.00"), w.minimum)
        assertEquals(cad("2000"), w.withdrawn)
        assertEquals(cad("3400.00"), w.leftToWithdraw)
        assertTrue(plans.warnings(LocalDate(2026, 11, 15)).any { it.key == "planWarning.minimum" })
        assertTrue(plans.warnings(LocalDate(2026, 6, 15)).none { it.key == "planWarning.minimum" }, "only from November")

        val lee = books.members.create("Lee", MemberKind.ADULT, d("1960-06-01"))
        val lif = account("FRV Lee", AccountType.LIF, "0", setOf(lee.id))
        plans.setValueJanuary1(lif.id, 2026, cad("50000"))
        val l = plans.withdrawalStatus(lif, 2026)
        assertEquals(65, l.age)
        assertEquals(cad("2000.00"), l.minimum)
        assertEquals(cad("3689.94"), l.maximum)

        plans.saveDetails(PlanDetails(rrif.id, minimumAgeMemberId = lee.id))
        val younger = plans.withdrawalStatus(rrif, 2026)
        assertEquals(cad("4000.00"), younger.minimum, "the younger spouse's age, 65")
        val opened = account("FERR neuf", AccountType.RRIF, "10000", setOf(pat.id), "2026-01-15")
        assertEquals(cad("0"), plans.withdrawalStatus(opened, 2026).minimum, "no minimum in the year the RRIF is opened")
    }

    @Test
    fun `RESP contributions and grants`() {
        val lea = books.members.create("Léa", MemberKind.CHILD, d("2015-06-12"))
        val noah = books.members.create("Noah", MemberKind.CHILD, d("2018-02-01"))
        val resp = account("REEE familial", AccountType.RESP, "0", setOf(alex.id))
        plans.saveBeneficiary(Beneficiary("", resp.id, BeneficiaryKind.RESP_BENEFICIARY, "Léa", lea.id))
        plans.saveBeneficiary(Beneficiary("", resp.id, BeneficiaryKind.RESP_BENEFICIARY, "Noah", noah.id))
        move(chequing, resp, "2026-01-15", "2500", lea.id)
        move(chequing, resp, "2026-02-15", "5000") // shared between both
        plans.recordGrant(resp.id, lea.id, d("2026-03-31"), GrantKind.CESG, cad("500"))

        val status = plans.respBeneficiaries(2026).associateBy { it.member.displayName }
        val l = status.getValue("Léa")
        assertEquals(cad("5000"), l.contributionsTotal)
        assertEquals(cad("1000.00"), l.cesgExpected, "20% of 5,000, at most 1,000 in a year")
        assertEquals(cad("500"), l.cesgReceived)
        assertEquals(RegisteredPlans.ProvincialGrant.QESI, l.provincialGrant, "the household is in Quebec")
        assertEquals(cad("500.00"), l.provincialExpected, "10% of 5,000, at most 500 in a year")
        assertEquals(cad("45000"), l.lifetimeLeft)
        assertEquals(cad("2500"), status.getValue("Noah").contributionsTotal)
        assertEquals(cad("500"), books.accounts.list().first { it.account.id == resp.id }.balance - cad("7500"), "the grant is in the register")
        assertFailsWith<ValidationException> { plans.saveBeneficiary(Beneficiary("", resp.id, BeneficiaryKind.RESP_BENEFICIARY, "Someone")) }
    }

    @Test
    fun `beneficiaries, pensions and the age-71 conversion`() {
        val pat = books.members.create("Pat", MemberKind.ADULT, d("1955-03-01"))
        val rrsp = account("REER Pat", AccountType.RRSP, "80000", setOf(pat.id))
        plans.saveBeneficiary(Beneficiary("", rrsp.id, BeneficiaryKind.BENEFICIARY, "Succession", relationship = "Estate", sharePercent = BigDecimal(100)))
        assertEquals(1, plans.beneficiaries(rrsp.id).size)
        assertTrue(plans.warnings(today).any { it.key == "planWarning.convert" }, "Pat turns 71 in 2026")

        val qpp = plans.savePension(Pension("", pat.id, PensionKind.QPP, "Rente du RRQ", payer = "Retraite Québec"), group)
        plans.saveStatement(qpp, PensionStatement("", qpp.id, 2025, projectedAnnual = cad("12400")))
        val category = books.categories.list().first { it.systemKey == "income.pension.qpp_cpp" }.id
        for (m in 1..9) books.transactions.create(TransactionDraft(chequing.id, LocalDate(2026, m, 25), cad("1033.33"), "Retraite Québec", listOf(SplitDraft(category, cad("1033.33")))))
        val summary = plans.pensionSummaries(2026).single()
        assertEquals(cad("9299.97"), summary.paymentsThisYear)
        assertEquals(cad("12400"), summary.latest?.projectedAnnual)
    }

    @Test
    fun `the January 1 value and last year's earnings stay when left empty (M-27)`() {
        val lee = books.members.create("Lee", MemberKind.ADULT, d("1960-06-01"))
        val lif = account("FRV Lee", AccountType.LIF, "0", setOf(lee.id))
        plans.setValueJanuary1(lif.id, 2026, cad("50000"), cad("2500"))
        assertEquals(cad("2500"), plans.withdrawalStatus(lif, 2026).lastYearEarnings)
        plans.setValueJanuary1(lif.id, 2026, null, null)
        val kept = plans.withdrawalStatus(lif, 2026)
        assertTrue(kept.valueEntered)
        assertEquals(cad("50000"), kept.valueJanuary1)
        assertEquals(cad("2500"), kept.lastYearEarnings, "an empty field no longer erases the earnings")
        plans.setValueJanuary1(lif.id, 2026, cad("51000"))
        assertEquals(cad("2500"), plans.withdrawalStatus(lif, 2026).lastYearEarnings)
        plans.clearValueJanuary1(lif.id, 2026)
        assertTrue(!plans.withdrawalStatus(lif, 2026).valueEntered)
        assertFailsWith<ValidationException>("earnings need a value to go with") { plans.setValueJanuary1(lif.id, 2026, null, cad("100")) }
    }

    @Test
    fun `a spousal RRSP needs its contributor (M-28)`() {
        val spousal = account("REER conjoint", AccountType.SPOUSAL_RRSP, "0", setOf(sam.id))
        assertEquals(listOf(spousal.id), plans.spousalWithoutContributor().map { it.id })
        assertTrue(plans.warnings(today).any { it.key == "planWarning.noContributor" && it.subjectId == spousal.id })
        assertFailsWith<ValidationException> { plans.saveDetails(PlanDetails(spousal.id)) }
        plans.saveDetails(PlanDetails(spousal.id, contributorMemberId = alex.id))
        assertTrue(plans.spousalWithoutContributor().isEmpty())
        assertTrue(plans.warnings(today).none { it.key == "planWarning.noContributor" })
    }

    @Test
    fun `a grant is deleted with its deposit (M-29)`() {
        val lea = books.members.create("Léa", MemberKind.CHILD, d("2015-06-12"))
        val resp = account("REEE", AccountType.RESP, "0", setOf(alex.id))
        plans.saveBeneficiary(Beneficiary("", resp.id, BeneficiaryKind.RESP_BENEFICIARY, "Léa", lea.id))
        plans.recordGrant(resp.id, lea.id, d("2026-03-31"), GrantKind.CESG, cad("500"))
        val grant = plans.grants(resp.id).single()
        val deposit = books.transactions.register(resp.id).single().transaction
        assertEquals(grant.id, plans.grantForTransaction(resp.id, deposit.id)?.id)
        assertNull(plans.grantForTransaction(chequing.id, deposit.id), "only RESP deposits are grants")

        books.transactions.setCleared(deposit.id, ClearedStatus.RECONCILED)
        assertFailsWith<ReconciledChangeException> { plans.deleteGrant(resp.id, grant.id) }
        assertEquals(1, plans.grants(resp.id).size, "nothing changes until confirmed")
        plans.deleteGrant(resp.id, grant.id, confirmReconciled = true)
        assertTrue(plans.grants(resp.id).isEmpty())
        assertTrue(books.transactions.register(resp.id).isEmpty())

        // The deposit already deleted from the register: the grant record still goes.
        plans.recordGrant(resp.id, lea.id, d("2026-06-30"), GrantKind.CESG, cad("200"))
        books.transactions.delete(books.transactions.register(resp.id).single().transaction.id)
        plans.deleteGrant(resp.id, plans.grants(resp.id).single().id)
        assertTrue(plans.grants(resp.id).isEmpty())
    }

    @Test
    fun `cash imported into a plan counts as a contribution (M-32)`() {
        val tfsa = account("CELI Alex", AccountType.TFSA, "0", setOf(alex.id))
        books.transactions.create(TransactionDraft(tfsa.id, d("2026-03-01"), cad("2000"), "Contribution"))
        books.transactions.create(TransactionDraft(tfsa.id, d("2026-05-01"), cad("-500"), "Withdrawal"))
        val interest = books.categories.list().first { it.systemKey == "income.investment.interest" }.id
        books.transactions.create(TransactionDraft(tfsa.id, d("2026-06-30"), cad("12"), "Interest", listOf(SplitDraft(interest, cad("12")))))
        val r = plans.room(alex.id, RoomPlan.TFSA, 2026, today)
        assertEquals(cad("2000"), r.contributions, "the categorized interest is not a contribution")
        assertEquals(cad("500"), r.withdrawals)
    }
}
