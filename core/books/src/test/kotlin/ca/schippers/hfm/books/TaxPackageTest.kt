package ca.schippers.hfm.books

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.TaxName
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TAX-02: the year-end package gathers each person's figures from the books, with the documents behind them. */
class TaxPackageTest {

    @TempDir
    lateinit var temp: Path

    private lateinit var books: Books
    private lateinit var chequing: Account
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id
    private val group get() = books.groups().single().id

    @BeforeEach
    fun setUp() {
        books = Books(HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING).create(temp.resolve("T.hfm"), "T", "perry", "Perry", "password1".toCharArray()).session)
        chequing = books.accounts.create(AccountDraft(group, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("0"), LocalDate(2025, 1, 1)))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    @Test
    fun `each person's package gathers pay, deductions, credits and payments, with line numbers and documents`() {
        val sam = books.members.create("Sam", MemberKind.ADULT)
        val maya = books.members.create("Maya", MemberKind.CHILD)
        books.members.update(sam.copy(province = Province.ON))
        val stub = PayStub(
            "Employer Inc.", LocalDate(2026, 3, 15), listOf(PayEarning("Regular", cad("4000.00"))),
            listOf(
                PayDeduction(DeductionKind.INCOME_TAX, "Federal tax", cad("500.00")), PayDeduction(DeductionKind.INCOME_TAX, "Ontario tax", cad("250.00")),
                PayDeduction(DeductionKind.CPP_QPP, "CPP", cad("230.00")), PayDeduction(DeductionKind.EI_QPIP, "EI", cad("65.00")),
                PayDeduction(DeductionKind.UNION_DUES, "Union", cad("40.00")),
            ),
        )
        books.payStubs.record(chequing.id, stub, sam.id)
        books.payStubs.record(chequing.id, stub.copy(payDate = LocalDate(2026, 3, 31)), sam.id)
        // Tax paid when filing last year's return is not tax deducted at source.
        books.transactions.create(TransactionDraft(chequing.id, LocalDate(2026, 4, 30), cad("-900.00"), "CRA", listOf(SplitDraft(cat("taxes.income_tax"), cad("-900.00"))), memberId = sam.id))
        books.transactions.create(TransactionDraft(chequing.id, LocalDate(2026, 5, 1), cad("-600.00"), "Little Stars Daycare", listOf(SplitDraft(cat("children.childcare"), cad("-600.00"))), memberId = sam.id))
        books.transactions.create(TransactionDraft(chequing.id, LocalDate(2026, 6, 1), cad("-100.00"), "Food Bank", listOf(SplitDraft(cat("gifts.charity"), cad("-100.00"))), memberId = sam.id))
        val supplies = books.transactions.create(TransactionDraft(chequing.id, LocalDate(2026, 7, 1), cad("-113.00"), "Staples", listOf(SplitDraft(cat("business.supplies"), cad("-113.00"))), memberId = sam.id))
        books.transactions.setSalesTaxes(supplies.id, mapOf(TaxName.HST to cad("13.00")))
        val rrsp = books.accounts.create(AccountDraft(group, "RRSP", AccountType.RRSP, Currency.CAD, cad("0"), LocalDate(2025, 1, 1), ownerMemberIds = setOf(sam.id)))
        books.transactions.transfer(TransferDraft(chequing.id, rrsp.id, LocalDate(2026, 2, 20), cad("500.00")))
        books.transactions.transfer(TransferDraft(chequing.id, rrsp.id, LocalDate(2026, 5, 20), cad("2000.00")))
        books.transactions.transfer(TransferDraft(chequing.id, rrsp.id, LocalDate(2027, 2, 10), cad("1000.00")))
        books.instalments.save(chequing.id, sam.id, 2026, TaxAuthority.CRA, List(4) { cad("300.00") })
        books.transactions.create(TransactionDraft(chequing.id, LocalDate(2026, 3, 10), cad("-300.00"), "Receiver General", listOf(SplitDraft(cat("taxes.instalments"), cad("-300.00"))), memberId = sam.id))
        val t4 = books.slipChecklist.checklist(2026).first { it.memberId == sam.id && it.type == SlipType.T4 }
        val doc = books.documents.import(group, "t4".encodeToByteArray(), "t4.pdf", "application/pdf").document
        books.documents.link(doc.id, SlipChecklistService.ENTITY, t4.id)

        val pkg = books.taxPackage.build(2026).people.single { it.memberId == sam.id }
        assertEquals(cad("8000.00"), pkg.total(PackageItem.EMPLOYMENT_INCOME))
        assertEquals(cad("1500.00"), pkg.total(PackageItem.INCOME_TAX_DEDUCTED), "only what was taken off pay")
        assertEquals(cad("460.00"), pkg.total(PackageItem.CPP_QPP_CONTRIBUTIONS))
        assertEquals(cad("130.00"), pkg.total(PackageItem.EI_PREMIUMS))
        assertEquals(cad("80.00"), pkg.total(PackageItem.UNION_DUES))
        assertEquals(cad("600.00"), pkg.total(PackageItem.CHILD_CARE))
        assertEquals(cad("100.00"), pkg.total(PackageItem.DONATIONS))
        assertEquals(cad("113.00"), pkg.total(PackageItem.BUSINESS_EXPENSES))
        assertEquals(cad("13.00"), pkg.total(PackageItem.SALES_TAX_PAID))
        assertEquals(cad("3000.00"), pkg.total(PackageItem.RRSP_CONTRIBUTIONS), "March 2 to the first 60 days of the next year")
        assertEquals(cad("300.00"), pkg.total(PackageItem.INSTALMENTS))
        assertEquals("10100", pkg.lines.first { it.item == PackageItem.EMPLOYMENT_INCOME }.item.line)
        assertEquals(listOf("Employer Inc."), pkg.lines.filter { it.item == PackageItem.EMPLOYMENT_INCOME }.map { it.detail }, "one line per payer")
        assertTrue(pkg.missingSlips.any { it.type == SlipType.RRSP_RECEIPT }, "the RRSP receipt is still expected")
        assertEquals(listOf("T4 - Employer Inc."), pkg.documents.map { it.name })
        assertTrue(books.taxPackage.build(2026).people.none { it.memberId == maya.id })
    }

    @Test
    fun `in Quebec the combined EI and QPIP premiums have no single federal line`() {
        val alex = books.members.create("Alex", MemberKind.ADULT)
        books.payStubs.record(
            chequing.id,
            PayStub("Hydro-Québec", LocalDate(2026, 3, 15), listOf(PayEarning("Salaire", cad("3000.00"))), listOf(PayDeduction(DeductionKind.EI_QPIP, "RQAP", cad("15.00")))),
            alex.id,
        )
        val pkg = books.taxPackage.build(2026).people.single { it.memberId == alex.id }
        assertEquals(cad("15.00"), pkg.total(PackageItem.EI_QPIP_PREMIUMS))
        assertEquals(null, PackageItem.EI_QPIP_PREMIUMS.line)
    }

    @Test
    fun `medical expenses are claimed as the medical expenses report claims them`() {
        val sam = books.members.create("Sam", MemberKind.ADULT)
        val alex = books.members.create("Alex", MemberKind.ADULT)
        val gilles = books.members.create("Gilles", MemberKind.DEPENDANT)
        fun expense(who: Member, date: String, amount: String) =
            books.medical.saveExpense(MedExpense("", group, who.id, MedService.PHYSIOTHERAPY, LocalDate.parse(date), cad(amount)))
        expense(sam, "2026-03-01", "300.00")
        expense(alex, "2026-06-01", "200.00")
        expense(gilles, "2026-04-01", "400.00")

        val pkg = books.taxPackage.build(2026)
        val household = pkg.people.single { it.memberId == null }
        assertEquals(cad("500.00"), household.total(PackageItem.MEDICAL), "the spouses' expenses together, over one period")
        assertEquals(cad("400.00"), household.total(PackageItem.MEDICAL_DEPENDANT))
        assertEquals("33199", PackageItem.MEDICAL_DEPENDANT.line)
        assertTrue(pkg.people.filter { it.memberId != null }.none { p -> p.lines.any { it.item == PackageItem.MEDICAL } }, "not each person's own best period")
    }
}
