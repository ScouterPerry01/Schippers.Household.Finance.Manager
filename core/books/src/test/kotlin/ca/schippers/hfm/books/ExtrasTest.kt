package ca.schippers.hfm.books

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
import java.math.BigDecimal
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Phase 5h: trips, contractors, home projects, invoices, rental properties and card rewards. */
class ExtrasTest {

    @TempDir
    lateinit var temp: Path

    private lateinit var books: Books
    private lateinit var chequing: Account
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id
    private val group get() = books.groups().single().id

    @BeforeEach
    fun setUp() {
        books = Books(HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING).create(temp.resolve("X.hfm"), "X", "perry", "Perry", "password1".toCharArray()).session)
        chequing = books.accounts.create(AccountDraft(group, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("0"), LocalDate(2026, 1, 1)))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    @Test
    fun `trips give a vehicle's work share, and long medical trips become medical expenses`() {
        val sam = books.members.create("Sam", MemberKind.ADULT).id
        val car = books.vehicles.save(Vehicle("", group, "Civic"))
        books.vehicles.addReading(car.id, LocalDate(2026, 1, 2), 40_000)
        books.vehicles.addReading(car.id, LocalDate(2026, 12, 30), 50_000)
        books.trips.save(Trip("", group, LocalDate(2026, 3, 3), "Client in Kingston", BigDecimal("200"), true, TripPurpose.BUSINESS, car.id, sam))
        books.trips.save(Trip("", group, LocalDate(2026, 4, 4), "Supplier", BigDecimal("100.5"), true, TripPurpose.EMPLOYMENT, car.id, sam))
        books.trips.save(Trip("", group, LocalDate(2026, 5, 5), "Groceries", BigDecimal("8"), true, TripPurpose.PERSONAL, car.id, sam))
        val use = books.trips.vehicleUse(2026).single()
        assertEquals(10_000, use.totalKm)
        assertEquals(BigDecimal("601.0"), use.workKm)
        assertEquals(6, use.workPercent)
        assertEquals(BigDecimal("400.0"), books.trips.totals(2026)[sam to TripPurpose.BUSINESS])

        val near = Trip("", group, LocalDate(2026, 6, 6), "Clinic", BigDecimal("12"), true, TripPurpose.MEDICAL)
        val far = books.trips.save(Trip("", group, LocalDate(2026, 6, 7), "Ottawa Heart Institute", BigDecimal("95"), true, TripPurpose.MEDICAL, memberId = sam))
        assertFalse(books.trips.qualifiesForMedical(near))
        assertTrue(books.trips.qualifiesForMedical(far))
        val expense = books.trips.addToMedical(far, sam, cad("0.59"), group)
        assertEquals(cad("112.10"), expense.amount, "190 km at 59 cents")
        assertEquals(MedService.MEDICAL_TRAVEL, expense.service)
        assertFailsWith<ValidationException> { books.trips.addToMedical(near, sam, cad("0.59"), group) }
        books.trips.setMedicalRate(2026, cad("0.59"))
        assertEquals(cad("0.59"), books.trips.medicalRate(2026))
    }

    @Test
    fun `contractors are rated by their jobs, and capital projects add to the home's cost base`() {
        val plumber = books.contractors.save(Contractor("", group, "Plomberie Roy", "Plumbing"))
        books.contractors.saveJob(plumber, ContractorJob("", LocalDate(2026, 2, 1), "Water heater", cad("1800.00"), 5))
        books.contractors.saveJob(plumber, ContractorJob("", LocalDate(2026, 6, 1), "Leak", cad("240.00"), 4))
        assertEquals(BigDecimal("4.5"), books.contractors.list().single().rating)
        assertFailsWith<ValidationException> { books.contractors.saveJob(plumber, ContractorJob("", LocalDate(2026, 6, 2), "Bad", rating = 6)) }

        val home = books.assets.save(Asset("", group, AssetKind.HOME, "House", purchasePrice = cad("400000.00")))
        val roof = books.homeProjects.save(HomeProject("", group, "New roof", ProjectStatus.DONE, Currency.CAD, home.id, budget = cad("15000.00")))
        books.homeProjects.addCost(roof, LocalDate(2026, 7, 1), "Deposit", cad("5000.00"), plumber.id)
        books.homeProjects.addCost(roof, LocalDate(2026, 7, 20), "Balance", cad("9500.00"))
        val paint = books.homeProjects.save(HomeProject("", group, "Repaint", ProjectStatus.DONE, Currency.CAD, home.id, capital = false))
        books.homeProjects.addCost(paint, LocalDate(2026, 8, 1), "Paint", cad("900.00"))
        books.homeProjects.save(HomeProject("", group, "Deck", ProjectStatus.PLANNED, Currency.CAD, home.id))
        val base = books.homeProjects.costBase(home.id)
        assertEquals(cad("14500.00"), base.improvements, "the roof only: a repair and a planned project add nothing")
        assertEquals(cad("414500.00"), base.total)
    }

    @Test
    fun `invoices are numbered per year, add their sales tax, and when paid the deposit is recorded`() {
        val first = books.invoices.save(
            Invoice("", group, books.invoices.nextNumber(2026), "Mme Roy", LocalDate(2026, 9, 1), Currency.CAD,
                listOf(InvoiceLine("Tutoring", "6", "45.00"), InvoiceLine("Workbook", "1", "19.99")), InvoiceStatus.SENT, LocalDate(2026, 9, 30),
                taxes = listOf(InvoiceTax("GST", 500), InvoiceTax("QST", 998))),
        )
        assertEquals("2026-001", first.number)
        assertEquals("2026-002", books.invoices.nextNumber(2026))
        assertEquals(cad("289.99"), first.subtotal)
        assertEquals(cad("14.50"), first.tax(first.taxes[0]))
        assertEquals(cad("28.94"), first.tax(first.taxes[1]))
        assertEquals(cad("333.43"), first.total)
        assertTrue(first.overdue(LocalDate(2026, 10, 5)))
        assertFailsWith<ValidationException> { books.invoices.save(first.copy(id = "", customer = "Other")) }

        val paid = books.invoices.markPaid(first, LocalDate(2026, 10, 6), chequing.id)
        assertEquals(InvoiceStatus.PAID, paid.status)
        val deposit = books.transactions.register(chequing.id).map { it.transaction }.single()
        assertEquals(cad("333.43"), deposit.amount)
        assertEquals(mapOf(TaxName.GST to cad("14.50"), TaxName.QST to cad("28.94")), books.transactions.salesTaxes(deposit.id))
    }

    @Test
    fun `a rental property's year comes from its tagged transactions, at the household's share`() {
        val duplex = books.rentals.save(RentalProperty("", group, "Duplex on Elm", "", "12 Elm St", shareBp = 5_000))
        val tag = books.tags().single { it.id == duplex.tagId }.name
        assertEquals("Duplex on Elm", tag)
        books.transactions.create(TransactionDraft(chequing.id, LocalDate(2026, 3, 1), cad("1500.00"), "Tenant", listOf(SplitDraft(cat("income.rental"), cad("1500.00"))), tags = setOf(tag)))
        books.transactions.create(TransactionDraft(chequing.id, LocalDate(2026, 3, 5), cad("-400.00"), "City", listOf(SplitDraft(cat("housing.municipal_tax"), cad("-400.00"))), tags = setOf(tag)))
        books.transactions.create(TransactionDraft(chequing.id, LocalDate(2026, 3, 6), cad("-99.00"), "Not the duplex", listOf(SplitDraft(cat("housing.maintenance"), cad("-99.00")))))
        val y = books.rentals.year(duplex, 2026, { it })
        assertEquals(cad("1500.00") to cad("400.00"), y.income.total to y.expenses.total)
        assertEquals(cad("1100.00"), y.net)
        assertEquals(cad("550.00"), y.share)
    }

    @Test
    fun `a card's rewards are earned less redeemed, with their worth and this year's estimate`() {
        val visa = books.accounts.create(AccountDraft(group, "Visa", AccountType.CREDIT_CARD, Currency.CAD, cad("0"), LocalDate(2026, 1, 1)))
        books.rewards.save(CardReward(visa.id, group, "Aeroplan", RewardUnit.POINTS, BigDecimal("1.5"), BigDecimal("0.02")))
        books.rewards.addEntry(visa.id, LocalDate(2026, 6, 30), BigDecimal("12000"), RewardKind.EARNED)
        books.rewards.addEntry(visa.id, LocalDate(2026, 8, 1), BigDecimal("5000"), RewardKind.REDEEMED, cad("100.00"), "Flight")
        books.transactions.create(TransactionDraft(visa.id, LocalDate(2026, 9, 1), cad("-1000.00"), "Store", listOf(SplitDraft(cat("food.groceries"), cad("-1000.00")))))
        val s = books.rewards.status(visa.id, LocalDate(2026, 10, 5))
        assertEquals(BigDecimal("7000"), s.balance)
        assertEquals(cad("140.00"), s.worth)
        assertEquals(BigDecimal("1500"), s.estimatedThisYear)
        assertFailsWith<ValidationException> { books.rewards.addEntry(visa.id, LocalDate(2026, 9, 2), BigDecimal("-5"), RewardKind.EARNED) }
    }
}
