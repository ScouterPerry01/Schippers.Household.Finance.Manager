package ca.schippers.hfm.books

import ca.schippers.hfm.calc.Province
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

/** TAX-01 and TAX-03: the slips expected from the books, and tax instalments with their reminders. */
class TaxYearTest {

    @TempDir
    lateinit var temp: Path

    private lateinit var books: Books
    private lateinit var chequing: Account
    private lateinit var alex: Member
    private lateinit var sam: Member
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id
    private val group get() = books.groups().single().id

    @BeforeEach
    fun setUp() {
        books = Books(HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING).create(temp.resolve("T.hfm"), "T", "perry", "Perry", "password1".toCharArray()).session)
        alex = books.members.create("Alex", MemberKind.ADULT)
        sam = books.members.create("Sam", MemberKind.ADULT)
        // Sam files in Ontario; Alex where the household is (Quebec).
        books.members.update(sam.copy(province = Province.ON))
        chequing = books.accounts.create(AccountDraft(group, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("0"), LocalDate(2025, 1, 1)))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun deposit(date: LocalDate, payee: String, category: String, amount: String, member: Member) =
        books.transactions.create(TransactionDraft(chequing.id, date, cad(amount), payee, listOf(SplitDraft(cat(category), cad(amount))), memberId = member.id))

    @Test
    fun `the books expect each person's slips, with the Quebec ones for people filing there`() {
        deposit(LocalDate(2026, 3, 1), "Hydro-Québec", "income.employment.salary", "3000.00", alex)
        deposit(LocalDate(2026, 3, 15), "Hydro-Québec", "income.employment.salary", "3000.00", alex)
        deposit(LocalDate(2026, 4, 1), "Service Canada", "income.benefits.ei_qpip", "800.00", sam)
        deposit(LocalDate(2026, 6, 30), "Tangerine", "income.investment.interest", "12.00", sam)
        deposit(LocalDate(2026, 12, 31), "EQ Bank", "income.investment.interest", "61.00", sam)
        deposit(LocalDate(2025, 12, 15), "Old Employer", "income.employment.salary", "100.00", alex)
        books.transactions.create(TransactionDraft(chequing.id, LocalDate(2026, 9, 1), cad("-900.00"), "Université Laval", listOf(SplitDraft(cat("education.tuition"), cad("-900.00"))), memberId = alex.id))
        val rrsp = books.accounts.create(AccountDraft(group, "RRSP", AccountType.RRSP, Currency.CAD, cad("0"), LocalDate(2025, 1, 1), ownerMemberIds = setOf(sam.id)))
        books.transactions.transfer(TransferDraft(chequing.id, rrsp.id, LocalDate(2026, 2, 20), cad("2000.00")))

        val slips = books.slipChecklist.checklist(2026)
        fun of(m: Member) = slips.filter { it.memberId == m.id }.map { "${it.type} ${it.issuer}" }
        assertEquals(listOf("T4 Hydro-Québec", "T2202 Université Laval", "RL1 Hydro-Québec", "RL8 Université Laval"), of(alex))
        assertEquals(listOf("T4E Service Canada", "T5 EQ Bank", "RRSP_RECEIPT RRSP"), of(sam), "no T5 for interest under 50 dollars, no RL slips in Ontario")
    }

    @Test
    fun `slips are marked received or not expected, added by hand, and keep their documents`() {
        deposit(LocalDate(2026, 3, 1), "Employer Inc.", "income.employment.salary", "3000.00", sam)
        val t4 = books.slipChecklist.checklist(2026).single()
        books.slipChecklist.setStatus(t4, SlipStatus.RECEIVED)
        val doc = books.documents.import(group, "t4".encodeToByteArray(), "t4.pdf", "application/pdf").document
        books.documents.link(doc.id, SlipChecklistService.ENTITY, t4.id)
        assertEquals(SlipStatus.RECEIVED to 1, books.slipChecklist.checklist(2026).single().let { it.status to it.documents })

        val contract = books.slipChecklist.add(2026, sam.id, SlipType.T4A, "Summer Contract Ltd", group)
        assertEquals(2, books.slipChecklist.checklist(2026).size)
        books.slipChecklist.setStatus(books.slipChecklist.checklist(2026).first { it.manual }, SlipStatus.RECEIVED)
        assertEquals(SlipStatus.RECEIVED, books.slipChecklist.checklist(2026).first { it.manual }.status)
        assertFailsWith<ValidationException> { books.slipChecklist.remove(t4) }
        books.slipChecklist.remove(contract)
        books.slipChecklist.setStatus(t4, SlipStatus.EXPECTED)
        assertEquals(listOf(SlipStatus.EXPECTED), books.slipChecklist.checklist(2026).map { it.status })
        assertFailsWith<ValidationException> { books.slipChecklist.add(2026, sam.id, SlipType.OTHER, " ", group) }
    }

    @Test
    fun `instalments are covered by payments in order and remind until paid`() {
        val q = cad("1200.00")
        books.instalments.save(chequing.id, sam.id, 2026, TaxAuthority.CRA, listOf(q, q, q, q))
        books.instalments.save(chequing.id, alex.id, 2026, TaxAuthority.REVENU_QUEBEC, listOf(cad("500.00"), cad("500.00"), cad("0"), cad("500.00")))
        fun pay(date: LocalDate, payee: String, amount: String, member: Member) =
            books.transactions.create(TransactionDraft(chequing.id, date, cad("-$amount"), payee, listOf(SplitDraft(cat("taxes.instalments"), cad("-$amount"))), memberId = member.id))
        pay(LocalDate(2026, 3, 10), "Receiver General", "1200.00", sam)
        pay(LocalDate(2026, 6, 20), "Receiver General", "600.00", sam)
        pay(LocalDate(2026, 3, 14), "Revenu Québec", "500.00", alex)

        val today = LocalDate(2026, 9, 5)
        val cra = books.instalments.schedule(2026, today).filter { it.authority == TaxAuthority.CRA }
        assertEquals(listOf(InstalmentState.PAID, InstalmentState.LATE, InstalmentState.DUE, InstalmentState.DUE), cra.map { it.state })
        assertEquals(cad("600.00"), cra[1].covered)
        val rq = books.instalments.schedule(2026, today).filter { it.authority == TaxAuthority.REVENU_QUEBEC }
        assertEquals(listOf(LocalDate(2026, 3, 15), LocalDate(2026, 6, 15), LocalDate(2026, 12, 15)), rq.map { it.dueDate }, "a zero leaves a date out")
        assertEquals(listOf(InstalmentState.PAID, InstalmentState.LATE, InstalmentState.DUE), rq.map { it.state }, "Revenu Québec payments count only for Revenu Québec")

        val reminders = books.instalments.renewals(today, 30).map { it.subjectName to it.date }
        assertTrue((sam.displayName to LocalDate(2026, 9, 15)) in reminders)
        assertEquals("CRA", books.instalments.renewals(today, 30).first { it.subjectName == sam.displayName }.detail)
        assertTrue(reminders.none { it.second == LocalDate(2026, 12, 15) }, "December is too far off")
        assertTrue(books.renewals(today).any { it.kind == RenewalKind.TAX_INSTALMENT })
        assertFailsWith<ValidationException> { books.instalments.save(chequing.id, sam.id, 2026, TaxAuthority.CRA, listOf(q)) }
    }
}
