package ca.schippers.hfm.books

import ca.schippers.hfm.calc.medical.Window
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

class MedicalServiceTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var group: String
    private lateinit var alex: Member
    private lateinit var sam: Member
    private lateinit var lea: Member
    private lateinit var gilles: Member
    private lateinit var alexPlan: MedPlan
    private lateinit var samPlan: MedPlan
    private fun d(s: String) = LocalDate.parse(s)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun n(s: String) = BigDecimal(s)
    private val med get() = books.medical

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("M.hfm"), "M", "perry", "Perry", "pw".toCharArray()).session)
        group = books.groups().single().id
        alex = books.members.create("Alex", MemberKind.ADULT)
        sam = books.members.create("Sam", MemberKind.ADULT)
        lea = books.members.create("Léa", MemberKind.CHILD)
        gilles = books.members.create("Gilles", MemberKind.DEPENDANT)
        // Each spouse's employer plan pays first for its own member and second for the other.
        alexPlan = med.savePlan(
            MedPlan("", group, MedPlanKind.GROUP_HEALTH, "Alex's plan", "Sun Life", memberId = alex.id, people = listOf(PlanPerson(alex.id, 1), PlanPerson(sam.id, 2), PlanPerson(lea.id, 1))),
        )
        samPlan = med.savePlan(
            MedPlan("", group, MedPlanKind.GROUP_HEALTH, "Sam's plan", "Manulife", memberId = sam.id, people = listOf(PlanPerson(sam.id, 1), PlanPerson(alex.id, 2), PlanPerson(lea.id, 2))),
        )
        med.saveCoverage(MedCoverage("", alexPlan.id, MedService.MASSAGE, n("80"), annualMax = cad("500"), perVisitMax = cad("60")))
        med.saveCoverage(MedCoverage("", alexPlan.id, MedService.EYE_EXAM, n("100"), frequencyMonths = 24))
        med.saveCoverage(MedCoverage("", samPlan.id, MedService.MASSAGE, n("100"), annualMax = cad("300")))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun expense(who: Member, service: MedService, date: String, amount: String, what: String? = null) =
        med.saveExpense(MedExpense("", group, who.id, service, d(date), cad(amount), description = what))

    @Test
    fun `claims go to each plan in turn until nothing is left`() {
        val massage = expense(alex, MedService.MASSAGE, "2026-03-01", "120")
        assertEquals(listOf(alexPlan.id, samPlan.id), med.plansFor(alex.id, MedService.MASSAGE).map { it.id })
        var status = med.status(massage)
        assertEquals(ExpenseStage.TO_SUBMIT, status.stage)
        assertEquals(alexPlan.id, status.nextPlan!!.id)
        assertEquals(cad("60"), status.expected, "80 % of 120 is 96, capped at 60 a visit")
        assertEquals(d("2027-03-01"), status.deadline)

        val first = med.submit(massage.id, alexPlan.id, d("2026-03-02"))
        assertEquals(cad("120"), first.claimed)
        assertEquals(ExpenseStage.WAITING, med.status(med.expense(massage.id)).stage)
        med.recordPayment(first.id, d("2026-03-15"), cad("60"))

        // The second plan sees the 60 the first did not pay.
        status = med.status(med.expense(massage.id))
        assertEquals(samPlan.id, status.nextPlan!!.id)
        assertEquals(cad("60"), status.expected)
        val second = med.submit(massage.id, samPlan.id, d("2026-03-16"), cad("60"))
        med.recordPayment(second.id, d("2026-03-30"), cad("60"))
        val done = med.expense(massage.id)
        assertEquals(cad("0"), done.outOfPocket)
        assertEquals(ExpenseStage.CLOSED, med.status(done).stage)
        assertFailsWith<ValidationException>("each plan once") { med.submit(massage.id, samPlan.id, d("2026-04-01")) }

        // What is left this year, and when the next eye exam is covered.
        expense(alex, MedService.EYE_EXAM, "2025-06-01", "90")
        val left = med.coverageLeft(alex.id, d("2026-04-01"))
        assertEquals(cad("440"), left.single { it.plan.id == alexPlan.id && it.coverage.service == MedService.MASSAGE }.annualLeft)
        assertEquals(d("2027-06-01"), left.single { it.coverage.service == MedService.EYE_EXAM }.nextEligible)
        assertEquals(cad("240"), left.single { it.plan.id == samPlan.id }.annualLeft, "Sam's plan paid 60 of its 300 for Alex")
    }

    @Test
    fun `a health spending account pays what the plans leave, up to its credit`() {
        val hsa = med.savePlan(MedPlan("", group, MedPlanKind.HSA, "HSA", hsaAmount = cad("1000"), people = listOf(PlanPerson(lea.id, 1))))
        val glasses = expense(lea, MedService.EYEWEAR, "2026-05-01", "350")
        assertEquals(listOf(hsa.id), med.plansFor(lea.id, MedService.EYEWEAR).map { it.id }, "no plan covers glasses for Léa, the HSA does")
        assertEquals(cad("350"), med.status(glasses).expected)
        val claim = med.submit(glasses.id, hsa.id, d("2026-05-02"))
        med.recordPayment(claim.id, d("2026-05-10"), cad("350"))
        assertEquals(cad("650"), med.coverageLeft(lea.id, d("2026-06-01")).single { it.plan.id == hsa.id }.annualLeft)
        // Denied, or no plan at all: the expense is then out of pocket.
        val denied = expense(lea, MedService.EYE_EXAM, "2026-06-01", "90")
        val c = med.submit(denied.id, alexPlan.id, d("2026-06-02"))
        med.deny(c.id, d("2026-06-20"))
        assertEquals(cad("90"), med.expense(denied.id).outOfPocket)
    }

    @Test
    fun `claims about to expire are reminders`() {
        val exam = expense(lea, MedService.EYE_EXAM, "2025-11-01", "90", "Eye exam")
        val reminder = books.renewals(d("2026-10-15")).single { it.kind == RenewalKind.MEDICAL_CLAIM }
        assertEquals(exam.id, reminder.subjectId)
        assertEquals(d("2026-11-01"), reminder.date)
        assertEquals(17, reminder.daysLeft)
        assertEquals("Alex's plan", reminder.detail)
        med.close(exam.id)
        assertTrue(books.renewals(d("2026-10-15")).none { it.kind == RenewalKind.MEDICAL_CLAIM }, "closed: nothing more to claim")
    }

    @Test
    fun `the tax credit uses the best 12-month period, adult dependants apart`() {
        expense(lea, MedService.EYE_EXAM, "2025-11-01", "90")
        expense(alex, MedService.EYEWEAR, "2026-05-01", "300")
        expense(gilles, MedService.PHYSIOTHERAPY, "2026-04-01", "200")
        val paidBack = expense(sam, MedService.MASSAGE, "2026-02-10", "100")
        med.recordPayment(med.submit(paidBack.id, samPlan.id, d("2026-02-11")).id, d("2026-02-20"), cad("100"))

        val r = med.taxReport(2026)
        assertEquals(Window(d("2025-05-02"), d("2026-05-01"), n("390.00")), r.family, "Léa's exam and Alex's glasses; Sam's massage was paid back")
        assertEquals(listOf(lea.id, alex.id, gilles.id).sorted(), r.people.map { it.member.id }.sorted())
        val g = r.people.single { it.member.id == gilles.id }
        assertTrue(g.otherDependant)
        assertEquals(n("200.00"), g.calendar.total)
        assertEquals(n("300.00"), r.people.single { it.member.id == alex.id }.calendar.total)
        assertEquals(2, med.expensesIn(r.family!!, setOf(lea.id, alex.id, sam.id)).size, "the receipts for the family's claim")
    }

    @Test
    fun `payments in the books become expenses in one click, and explanations of benefits find their claim`() {
        val chequing = books.accounts.create(AccountDraft(group, "Chèques", AccountType.CHEQUING, Currency.CAD, cad("1000"), d("2026-01-01")))
        val pharmacy = books.categories.list().first { it.systemKey == "health.pharmacy" }.id
        val t = books.transactions.create(TransactionDraft(chequing.id, d("2026-07-03"), cad("-85.40"), "Pharmaprix", listOf(SplitDraft(pharmacy, cad("-85.40")))))
        assertEquals(listOf(t.id), med.unrecorded(d("2026-01-01"), d("2026-12-31")).map { it.transactionId })
        val e = med.fromTransaction(t.id, sam.id, MedService.PRESCRIPTION, group)
        assertEquals(cad("85.40"), e.amount)
        assertEquals("Pharmaprix", e.description)
        assertTrue(med.unrecorded(d("2026-01-01"), d("2026-12-31")).isEmpty())

        val claim = med.submit(e.id, samPlan.id, d("2026-07-04"))
        assertEquals(listOf(claim.id), med.eobCandidates(cad("68.32"), d("2026-07-20")).map { it.second.id })
        assertTrue(med.eobCandidates(cad("68.32"), d("2026-07-01")).isEmpty(), "an explanation cannot come before the claim")
        assertNull(med.status(med.expense(e.id)).nextPlan)
    }
}
