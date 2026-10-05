package ca.schippers.hfm.books

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.tax.TaxInput
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The income tax estimate per person, from the year-end package and the person's province. */
class IncomeTaxServiceTest {

    @TempDir
    lateinit var temp: Path

    private lateinit var books: Books
    private lateinit var chequing: Account
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private val group get() = books.groups().single().id

    @BeforeEach
    fun setUp() {
        books = Books(HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING).create(temp.resolve("T.hfm"), "T", "perry", "Perry", "password1".toCharArray()).session)
        chequing = books.accounts.create(AccountDraft(group, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("0"), LocalDate(2025, 1, 1)))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    @Test
    fun `the estimate uses the person's pay stubs, province and age, and entered figures replace them`() {
        val sam = books.members.create("Sam", MemberKind.ADULT)
        books.members.update(sam.copy(province = Province.ON))
        val stub = PayStub(
            "Employer Inc.", LocalDate(2025, 3, 15), listOf(PayEarning("Regular", cad("4000.00"))),
            listOf(
                PayDeduction(DeductionKind.INCOME_TAX, "Income tax", cad("700.00")),
                PayDeduction(DeductionKind.CPP_QPP, "CPP", cad("230.00")), PayDeduction(DeductionKind.EI_QPIP, "EI", cad("65.00")),
                PayDeduction(DeductionKind.UNION_DUES, "Union", cad("40.00")),
            ),
        )
        books.payStubs.record(chequing.id, stub, sam.id)
        books.payStubs.record(chequing.id, stub.copy(payDate = LocalDate(2025, 3, 31)), sam.id)

        val r = books.incomeTax.estimate(2025, sam.id)
        fun figure(i: TaxInput) = r.figures.first { it.input == i }
        assertEquals(Province.ON, r.province)
        assertEquals(0, BigDecimal("8000").compareTo(figure(TaxInput.EMPLOYMENT).fromBooks))
        assertEquals(listOf(PackageItem.EMPLOYMENT_INCOME), figure(TaxInput.EMPLOYMENT).from)
        assertEquals(0, BigDecimal("460").compareTo(figure(TaxInput.CPP_QPP).fromBooks))
        assertEquals(0, BigDecimal("80").compareTo(figure(TaxInput.UNION_DUES).fromBooks))
        assertEquals(0, BigDecimal("1400").compareTo(figure(TaxInput.TAX_DEDUCTED).fromBooks))
        assertNull(figure(TaxInput.SPOUSE_NET_INCOME).amount)
        val e = assertNotNull(r.estimate)
        assertEquals(Province.ON, e.province)
        assertTrue(e.balance.signum() < 0, "on 8,000 of pay, the tax deducted is refunded")

        // Entered figures replace the books' for this estimate only.
        val more = books.incomeTax.estimate(2025, sam.id, mapOf(TaxInput.EMPLOYMENT to BigDecimal("90000")), age65 = true)
        assertTrue(more.age65)
        assertEquals(0, BigDecimal("90000").compareTo(more.figures.first { it.input == TaxInput.EMPLOYMENT }.amount))
        assertTrue(more.estimate!!.balance.signum() > 0)
        assertEquals(0, BigDecimal("8000").compareTo(books.incomeTax.estimate(2025, sam.id).figures.first { it.input == TaxInput.EMPLOYMENT }.amount))

        assertTrue(sam.id in books.incomeTax.people(2025).map { it.id })
        assertNull(books.incomeTax.estimate(2023, sam.id).estimate, "no rates before 2024")
    }

    @Test
    fun `carry-forwards and entered figures are kept per person and year`() {
        val alex = books.members.create("Alex", MemberKind.ADULT)
        books.incomeTax.save(2025, alex.id, TaxInput.EMPLOYMENT, BigDecimal("30000"), group)
        books.incomeTax.save(2025, alex.id, TaxInput.TUITION_CARRIED, BigDecimal("4200.50"), group)
        books.incomeTax.save(2025, alex.id, TaxInput.TUITION_CARRIED, BigDecimal("3800"), group)
        books.incomeTax.saveAge65(2025, alex.id, true, group)

        val kept = books.incomeTax.saved(2025, alex.id)
        assertEquals(0, BigDecimal("3800").compareTo(kept.figures[TaxInput.TUITION_CARRIED]))
        assertEquals(true, kept.age65)
        assertTrue(books.incomeTax.saved(2026, alex.id).figures.isEmpty(), "each year has its own")

        // The estimate starts from what was kept: the carried tuition is used against the tax.
        val r = books.incomeTax.estimate(2025, alex.id)
        assertTrue(r.age65)
        val used = r.estimate!!.carryForwards.first { it.kind == ca.schippers.hfm.calc.tax.CarryKind.TUITION_FEDERAL }
        assertEquals(0, BigDecimal("3800").compareTo(used.available))
        assertTrue(used.used.signum() > 0)

        books.incomeTax.save(2025, alex.id, TaxInput.EMPLOYMENT, null, group)
        assertNull(books.incomeTax.saved(2025, alex.id).figures[TaxInput.EMPLOYMENT], "forgotten: the books' figure applies again")
        books.incomeTax.clear(2025, alex.id)
        assertEquals(SavedEstimate(emptyMap(), null), books.incomeTax.saved(2025, alex.id))
    }

    @Test
    fun `children are counted from the household members' birth dates`() {
        val alex = books.members.create("Alex", MemberKind.ADULT)
        books.members.update(books.members.create("Robin", MemberKind.CHILD).copy(birthDate = LocalDate(2021, 6, 1)))
        books.members.update(books.members.create("Sky", MemberKind.CHILD).copy(birthDate = LocalDate(2012, 2, 1)))
        books.members.update(books.members.create("Lee", MemberKind.CHILD).copy(birthDate = LocalDate(2005, 2, 1)))
        val r = books.incomeTax.estimate(2025, alex.id)
        fun figure(i: TaxInput) = r.figures.first { it.input == i }
        assertEquals(0, BigDecimal(2).compareTo(figure(TaxInput.CHILDREN).fromBooks), "Lee is 20")
        assertEquals(0, BigDecimal.ONE.compareTo(figure(TaxInput.CHILDREN_UNDER_6).fromBooks))
        assertTrue(figure(TaxInput.CHILDREN).fromMembers)
        assertTrue(r.estimate!!.lines.any { it.kind == ca.schippers.hfm.calc.tax.TaxLineKind.CHILD_BENEFIT })
    }
}
