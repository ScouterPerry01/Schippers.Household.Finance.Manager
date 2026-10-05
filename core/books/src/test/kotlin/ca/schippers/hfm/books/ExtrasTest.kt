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
                taxes = listOf(InvoiceTax.ofPercent("GST", BigDecimal("5")), InvoiceTax.ofPercent("QST", BigDecimal("9.975")))),
        )
        assertEquals("2026-001", first.number)
        assertEquals("2026-002", books.invoices.nextNumber(2026))
        assertEquals(cad("289.99"), first.subtotal)
        assertEquals(BigDecimal("9.975"), first.taxes[1].percent, "the QST rate is kept exactly, not cut to 9.97 %")
        assertEquals(cad("14.50"), first.tax(first.taxes[0]))
        assertEquals(cad("28.93"), first.tax(first.taxes[1]))
        assertEquals(cad("333.42"), first.total)
        assertTrue(first.overdue(LocalDate(2026, 10, 5)))
        assertFailsWith<ValidationException> { books.invoices.save(first.copy(id = "", customer = "Other")) }

        val paid = books.invoices.markPaid(first, LocalDate(2026, 10, 6), chequing.id)
        assertEquals(InvoiceStatus.PAID, paid.status)
        val deposit = books.transactions.register(chequing.id).map { it.transaction }.single()
        assertEquals(cad("333.42"), deposit.amount)
        assertEquals(mapOf(TaxName.GST to cad("14.50"), TaxName.QST to cad("28.93")), books.transactions.salesTaxes(deposit.id))
    }

    @Test
    fun `an invoice's tax rates are kept as exact decimals, and invoices saved with basis points still read`() {
        val saved = books.invoices.save(
            Invoice("", group, "2012-001", "M. Roy", LocalDate(2012, 6, 1), Currency.CAD, listOf(InvoiceLine("Tutoring", "1", "100.00")),
                taxes = listOf(InvoiceTax.ofPercent("GST", BigDecimal("5")), InvoiceTax.ofPercent("QST", BigDecimal("9.5"), onGst = true))),
        )
        assertEquals(BigDecimal("0.095"), saved.taxes[1].rate)
        assertEquals(cad("9.98"), saved.tax(saved.taxes[1]), "before 2013 the QST was charged on the price plus the GST")
        assertEquals(cad("114.98"), saved.total)

        // A row written before rates were kept as decimals: {"name":"GST","rateBp":500}.
        books.ledger(books.group(group)).extrasQueries.upsertInvoice(
            "old", "2025-009", "Old", null, "2025-03-01", null, "CAD", "SENT", null, null, """[{"description":"A","quantity":"1","unitPrice":"200.00"}]""",
            """[{"name":"HST","rateBp":1300}]""", null,
        )
        val old = books.invoices.list().single { it.id == "old" }
        assertEquals(listOf(InvoiceTax("HST", BigDecimal("0.1300"))), old.taxes)
        assertEquals(cad("226.00"), old.total)
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

    @Test
    fun `a medical trip becomes a medical expense once, and again only once that expense is deleted`() {
        val sam = books.members.create("Sam", MemberKind.ADULT).id
        val trip = books.trips.save(Trip("", group, LocalDate(2026, 6, 7), "Ottawa Heart Institute", BigDecimal("95"), true, TripPurpose.MEDICAL, memberId = sam))
        assertEquals(null, books.trips.medicalExpense(trip))
        val expense = books.trips.addToMedical(trip, sam, cad("0.59"), group)
        assertEquals(expense.id, books.trips.medicalExpense(trip)?.id)
        assertFailsWith<ValidationException> { books.trips.addToMedical(trip, sam, cad("0.59"), group) }
        assertEquals(1, books.medical.expenses().size)
        books.medical.deleteExpense(expense.id)
        assertEquals(null, books.trips.medicalExpense(trip))
        books.trips.addToMedical(trip, sam, cad("0.61"), group)
        assertEquals(cad("115.90"), books.medical.expenses().single().amount)
    }

    @Test
    fun `deleting an invoice can take its deposit with it, and what is waiting is totalled per currency`() {
        val usd = books.accounts.create(AccountDraft(group, "US", AccountType.CHEQUING, Currency.USD, Money.parse("0", Currency.USD), LocalDate(2026, 1, 1)))
        fun invoice(number: String, currency: Currency, price: String) = books.invoices.save(
            Invoice("", group, number, "Client", LocalDate(2026, 9, 1), currency, listOf(InvoiceLine("Work", "1", price)), InvoiceStatus.SENT),
        )
        val a = invoice("2026-001", Currency.CAD, "100.00")
        invoice("2026-002", Currency.CAD, "50.00")
        invoice("2026-003", Currency.USD, "80.00")
        assertEquals(listOf(cad("150.00"), Money.parse("80.00", Currency.USD)), books.invoices.outstanding())

        val paid = books.invoices.markPaid(a, LocalDate(2026, 9, 15), chequing.id)
        val deposit = books.invoices.deposit(paid)!!
        assertEquals(cad("100.00"), deposit.amount)
        books.invoices.delete(paid)
        assertEquals(1, books.transactions.register(chequing.id).size, "without asking, the deposit stays")

        val b = books.invoices.markPaid(books.invoices.list().single { it.number == "2026-002" }, LocalDate(2026, 9, 20), chequing.id)
        books.invoices.delete(b, withDeposit = true)
        assertEquals(listOf(cad("100.00")), books.transactions.register(chequing.id).map { it.transaction.amount })
        assertEquals(0, books.transactions.register(usd.id).size)
    }

    @Test
    fun `a rental property's tag follows its name, and can go with it`() {
        val duplex = books.rentals.save(RentalProperty("", group, "Duplex on Elm", "", "12 Elm St"))
        val renamed = books.rentals.save(duplex.copy(name = "Duplex on Oak"))
        assertEquals(duplex.tagId, renamed.tagId)
        assertEquals("Duplex on Oak", books.tags().single { it.id == duplex.tagId }.name)
        books.transactions.create(TransactionDraft(chequing.id, LocalDate(2026, 3, 1), cad("1500.00"), "Tenant", tags = setOf("Duplex on Oak")))
        books.rentals.save(RentalProperty("", group, "Cottage", "", null))
        assertFailsWith<ValidationException>("another tag already has the name") { books.rentals.save(renamed.copy(name = "cottage")) }

        books.rentals.delete(renamed, removeTag = true)
        assertTrue(books.tags().none { it.id == duplex.tagId })
        assertTrue(books.transactions.register(chequing.id).single().transaction.tagIds.isEmpty(), "the transaction stays, without the tag")
    }
}
