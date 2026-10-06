package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
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

class PetVehicleTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var group: String
    private lateinit var visa: Account
    private fun d(m: Int, day: Int) = LocalDate(2026, m, day)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("P.hfm"), "P", "perry", "Perry", "pw".toCharArray()).session)
        group = books.groups().single().id
        visa = books.accounts.create(AccountDraft(group, "Visa", AccountType.CREDIT_CARD, Currency.CAD, cad("0"), d(1, 1)))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    // --- Pets ------------------------------------------------------------------------------------

    @Test
    fun `pets with licence and insurance renewals`() {
        val rex = books.pets.save(Pet("", "Rex", Species.DOG, "Labrador", Sex.MALE, d(3, 1), neutered = true, licenceNumber = "2026-0412", licenceMunicipality = "Québec", licenceExpiry = d(12, 31)))
        books.pets.save(Pet("", "Mimi", Species.CAT, insurer = "Trupanion", insuranceRenewal = d(10, 20)))
        assertEquals(listOf("Mimi", "Rex"), books.pets.list().map { it.name })
        assertEquals(listOf(RenewalKind.PET_INSURANCE), books.renewals(d(10, 1)).map { it.kind }, "the licence is three months away")
        assertEquals(listOf(RenewalKind.PET_INSURANCE, RenewalKind.PET_LICENCE), books.renewals(d(12, 15)).map { it.kind })
        assertEquals(-56, books.renewals(d(12, 15)).first().daysLeft, "insurance overdue")

        books.pets.save(rex.copy(archived = true))
        assertEquals(listOf("Mimi"), books.pets.list().map { it.name })
        assertFailsWith<ValidationException> { books.pets.save(Pet("", " ", Species.DOG)) }
    }

    @Test
    fun `what a pet costs, and its health records`() {
        val rex = books.pets.save(Pet("", "Rex", Species.DOG))
        books.transactions.create(TransactionDraft(visa.id, d(2, 3), cad("-310.40"), "Clinique vétérinaire", listOf(SplitDraft(cat("pets.vet"), cad("-310.40"))), memberId = rex.id))
        books.transactions.create(
            TransactionDraft(
                visa.id, d(3, 9), cad("-120"), "Costco",
                listOf(SplitDraft(cat("food.groceries"), cad("-60")), SplitDraft(cat("pets.food"), cad("-60"), memberId = rex.id)),
            ),
        )
        books.transactions.create(TransactionDraft(visa.id, d(3, 10), cad("-40"), "Épicerie"))
        val costs = books.pets.costs(rex.id, d(1, 1), d(12, 31))
        assertEquals(cad("370.40"), costs.total, "the vet bill and the pet's line of the Costco receipt")
        assertEquals(cat("pets.vet"), costs.byCategory.first().first)
        assertEquals(mapOf(2026 to cad("370.40")), costs.byYear)

        val vet = books.health.saveProvider(HealthProvider("", group, "Clinique vétérinaire", ProviderKind.VET, null, null, null, false))
        books.health.saveImmunization(Immunization("", group, rex.id, "Rage", d(4, 1), vet.id, LocalDate(2029, 4, 1), null))
        assertEquals(1, books.health.immunizations(rex.id).size)
        books.calendar.create(EventDraft(group, "Toilettage", EventCategory.PET, d(10, 9), memberId = rex.id))
        assertEquals(EventCategory.PET, books.calendar.list().single().category)
    }

    // --- Categories (CAT-06) ---------------------------------------------------------------------

    @Test
    fun `transit is split, and older households receive the new categories once`() {
        val transit = books.categories.list().first { it.systemKey == "transport.transit" }
        assertEquals(
            setOf("transport.transit.pass", "transport.transit.fares"),
            books.categories.list().filter { it.parentId == transit.id }.mapNotNull { it.systemKey }.toSet(),
        )
        // A household from before: the new categories are missing and the defaults are at version 1.
        val added = setOf("transport.transit.pass", "transport.transit.fares", "pets.licence", "pets.insurance", "pets.boarding")
        books.categories.list().filter { it.systemKey in added }.forEach { books.core.deleteCategory(it.id) }
        books.putSetting("categories.defaultsVersion", "1")
        books.categories.ensureDefaults()
        assertEquals(added, books.categories.list().mapNotNull { it.systemKey }.filter { it in added }.toSet())
        assertEquals(cat("pets"), books.categories.list().first { it.systemKey == "pets.boarding" }.parentId)

        // Once done, a category the user removes is not brought back.
        books.core.deleteCategory(cat("pets.boarding"))
        books.categories.ensureDefaults()
        assertTrue(books.categories.list().none { it.systemKey == "pets.boarding" })
    }

    // --- Vehicles --------------------------------------------------------------------------------

    private fun civic() = books.vehicles.save(
        Vehicle(
            "", group, "Civic", "Honda", "Civic", 2021, plate = "abc 123", purchaseDate = d(1, 10), purchasePrice = cad("24000"), purchaseOdometer = 40_000,
            registrationRenewal = d(10, 31), insurer = "Desjardins Assurances", insuranceRenewal = LocalDate(2027, 4, 1),
        ),
    )

    @Test
    fun `vehicle details and renewals`() {
        val v = civic()
        assertEquals("ABC 123", v.plate)
        books.vehicles.saveWarranty(Warranty("", v.id, WarrantyKind.POWERTRAIN, "Honda", d(1, 10), d(11, 20), 100_000))
        val kinds = books.renewals(d(10, 15)).map { it.kind }
        assertEquals(listOf(RenewalKind.REGISTRATION, RenewalKind.WARRANTY), kinds, "warranties are announced 60 days ahead")
        assertTrue(books.renewals(d(12, 1)).none { it.kind == RenewalKind.WARRANTY }, "an expired warranty is no longer a reminder")
        assertTrue(books.vehicles.warranties(v.id).single().covers(d(11, 1), 90_000))
        assertFailsWith<ValidationException> { books.vehicles.saveWarranty(Warranty("", v.id, WarrantyKind.OTHER)) }
    }

    @Test
    fun `a vehicle sale is linked to its deposit`() {
        val v = civic()
        val deposit = books.transactions.create(TransactionDraft(visa.id, d(9, 1), cad("18500"), "J. Tremblay"))
        val sold = books.vehicles.save(v.copy(status = VehicleStatus.SOLD, disposalDate = d(9, 1), disposalPrice = cad("18500"), disposalTransactionId = deposit.id))
        assertEquals(deposit.id, sold.disposalTransactionId, "SAL-03: the sale deposit is kept; its payee is the buyer")
        assertEquals("J. Tremblay", books.transactions.get(sold.disposalTransactionId!!).payeeText)
        assertEquals(deposit.id, books.vehicles.get(v.id).disposalTransactionId)
        assertNull(books.vehicles.save(sold.copy(status = VehicleStatus.RETIRED)).disposalTransactionId, "only a sale keeps a sale deposit")
    }

    @Test
    fun `vehicle warranty claims are logged`() {
        val v = civic()
        val w = books.vehicles.saveWarranty(Warranty("", v.id, WarrantyKind.POWERTRAIN, "Honda", d(1, 10), LocalDate(2029, 1, 10), 100_000))
        books.vehicles.saveClaim(v.id, WarrantyClaim("", w.id, d(3, 2), "Transmission", "Repaired", cad("2400"), cad("100")))
        val later = books.vehicles.saveClaim(v.id, WarrantyClaim("", w.id, d(8, 15), "Noise", "Refused: wear", paid = cad("350")))
        val claims = books.vehicles.claims(v.id, w.id)
        assertEquals(listOf("Noise", "Transmission"), claims.map { it.problem }, "newest first")
        assertEquals(cad("2400"), claims.last().covered)
        assertEquals(cad("100"), claims.last().paid)
        assertFailsWith<ValidationException> { books.vehicles.saveClaim(v.id, WarrantyClaim("", w.id, d(9, 1), " ")) }
        assertFailsWith<ValidationException>("not this vehicle's warranty") { books.vehicles.saveClaim(v.id, WarrantyClaim("", "other", d(9, 1), "Brakes")) }
        books.vehicles.saveClaim(v.id, later.copy(outcome = "Repaired after all", covered = cad("350"), paid = null))
        assertEquals("Repaired after all", books.vehicles.claims(v.id, w.id).first().outcome, "edited in place")
        books.vehicles.deleteClaim(v.id, w.id, later.id)
        assertEquals(1, books.vehicles.claims(v.id, w.id).size)
        books.vehicles.deleteWarranty(v.id, w.id)
        assertTrue(books.vehicles.claims(v.id, w.id).isEmpty(), "the claims go with the warranty")
    }

    @Test
    fun `maintenance falls due by date, by distance, or by the forecast`() {
        val v = civic()
        val oil = books.vehicles.saveTask(MaintenanceTask("", v.id, "Vidange", "oil", 6, 8_000, startDate = d(1, 10), startOdometer = 40_000))
        // 3,000 km in 60 days: 50 km a day.
        books.vehicles.addReading(v.id, d(3, 11), 43_000)
        var status = books.vehicles.taskStatuses(v.id, d(3, 11)).single()
        assertEquals(d(7, 10), status.dueDate)
        assertEquals(48_000, status.dueOdometer)
        assertEquals(d(6, 19), status.forecastDate, "5,000 km at 50 km a day: 100 days")
        assertEquals(d(6, 19), status.nextDate, "the forecast comes before the date")
        assertEquals(TaskState.OK, status.state)

        books.vehicles.addReading(v.id, d(5, 1), 47_600)
        status = books.vehicles.taskStatuses(v.id, d(5, 1)).single()
        assertEquals(TaskState.SOON, status.state, "400 km left, within the 500 km notice")
        assertEquals(1, books.vehicles.due(d(5, 1)).size)

        val service = books.vehicles.saveService(ServiceRecord("", v.id, d(5, 3), 47_700, "Garage Tremblay", cost = cad("89.95"), taskIds = setOf(oil.id)))
        status = books.vehicles.taskStatuses(v.id, d(5, 3)).single()
        assertEquals(d(11, 3), status.dueDate, "the schedule restarts from the service")
        assertEquals(55_700, status.dueOdometer)
        assertEquals(TaskState.OK, status.state)
        assertEquals(setOf(oil.id), books.vehicles.services(v.id).single().taskIds)
        assertEquals(47_700, books.vehicles.latestOdometer(v.id)!!.odometer)
        books.vehicles.deleteService(v.id, service.id)
        assertEquals(TaskState.SOON, books.vehicles.taskStatuses(v.id, d(5, 3)).single().state)
    }

    @Test
    fun `starter tasks suit the vehicle`() {
        val v = civic()
        val names = books.vehicles.addStarterTasks(v.id, d(10, 2)) { "task:$it" }.map { it.templateKey }
        assertTrue("oil" in names && "winter_tires_on" in names)
        assertTrue(books.vehicles.addStarterTasks(v.id, d(10, 2)) { it }.isEmpty(), "not added twice")
        val winter = books.vehicles.taskStatuses(v.id, d(10, 2)).first { it.task.templateKey == "winter_tires_on" }
        assertEquals(d(12, 1), winter.dueDate, "a Quebec household: winter tires are required from December 1 (Rates and rules)")

        val ev = books.vehicles.save(Vehicle("", group, "Ioniq", fuelType = FuelType.ELECTRIC))
        assertTrue(books.vehicles.addStarterTasks(ev.id, d(10, 2)) { it }.none { it.templateKey == "oil" })
    }

    @Test
    fun `fuel consumption from full tank to full tank`() {
        val v = civic()
        books.vehicles.saveFuel(FuelEntry("", v.id, d(6, 1), 50_000, BigDecimal("40"), cad("64")))
        books.vehicles.saveFuel(FuelEntry("", v.id, d(6, 10), 50_300, BigDecimal("15"), cad("24"), fullTank = false))
        books.vehicles.saveFuel(FuelEntry("", v.id, d(6, 20), 50_600, BigDecimal("27"), cad("43.20")))
        val stats = books.vehicles.fuelStats(v.id, d(1, 1), d(12, 31))
        assertEquals(600, stats.distanceKm)
        assertEquals(BigDecimal("7.0"), stats.per100km, "42 L over 600 km")
        assertEquals(Money.ofMinor(11, Currency.CAD), stats.costPerKm, "67.20 $ over 600 km")
        assertNull(books.vehicles.fuelStats(v.id, d(6, 1), d(6, 5)).per100km)
    }

    @Test
    fun `payments linked to the vehicle and the cost of ownership`() {
        val v = civic()
        // Entered from the fuel log, with the payment: one transaction, linked.
        val fill = books.vehicles.saveFuel(FuelEntry("", v.id, d(6, 1), 50_000, BigDecimal("40"), cad("64")), PaymentDraft(visa.id, cat("transport.fuel"), "Petro-Canada"))
        val txn = books.transactions.get(fill.transactionId!!)
        assertEquals(v.id, txn.assetId)
        assertEquals(cad("-64"), txn.amount)
        // Entered in the register and linked there.
        books.transactions.create(TransactionDraft(visa.id, d(4, 1), cad("-1150"), "Desjardins Assurances", listOf(SplitDraft(cat("transport.insurance"), cad("-1150"))), assetId = v.id))
        // A service with a cost but no transaction still counts.
        books.vehicles.saveService(ServiceRecord("", v.id, d(7, 1), 51_000, "Garage", cost = cad("120")))
        books.vehicles.addReading(v.id, d(1, 15), 40_100)

        val cost = books.vehicles.costs(v.id, d(1, 1), d(12, 31))
        assertEquals(cad("1334"), cost.costs.total, "64 + 1,150 + 120, nothing counted twice")
        assertEquals(11_000, cost.distanceKm, "from the purchase reading to the service")
        assertEquals(Money.ofMinor(12, Currency.CAD), cost.costPerKm)
        assertEquals(cat("transport.insurance"), cost.costs.byCategory.first().first)
        assertNull(cost.insurance, "no policy names the vehicle")
    }

    @Test
    fun `costs by year and category, and the vehicle's share of the policies that name it`() {
        val v = civic()
        val other = books.vehicles.save(Vehicle("", group, "Van", "Toyota", "Sienna", 2019))
        books.transactions.create(TransactionDraft(visa.id, LocalDate(2025, 12, 15), cad("-200"), "Garage", listOf(SplitDraft(cat("transport.maintenance"), cad("-200"))), assetId = v.id))
        books.transactions.create(TransactionDraft(visa.id, d(4, 1), cad("-1150"), "Desjardins", listOf(SplitDraft(cat("transport.insurance"), cad("-1150"))), assetId = v.id))
        books.vehicles.saveFuel(FuelEntry("", v.id, d(6, 1), 50_000, BigDecimal("40"), cad("64")), PaymentDraft(visa.id, cat("transport.fuel"), "Petro-Canada"))
        books.vehicles.saveService(ServiceRecord("", v.id, d(7, 1), 51_000, "Garage", cost = cad("120")))
        // One auto policy names both vehicles: $100 a month, half each.
        books.insurance.save(InsurancePolicy("", group, PolicyKind.AUTO, "Desjardins", premium = cad("100"), frequency = PremiumFrequency.MONTHLY, assetIds = setOf(v.id, other.id)))

        val cost = books.vehicles.costs(v.id, LocalDate(2025, 1, 1), d(12, 31))
        // VEH-10: each year by category.
        assertEquals(listOf(2025, 2026), cost.costs.byYearCategory.keys.toList())
        assertEquals(listOf(cat("transport.maintenance") to cad("200")), cost.costs.byYearCategory.getValue(2025))
        assertEquals(
            listOf(cat("transport.insurance") to cad("1150"), cat("transport.maintenance") to cad("120"), cat("transport.fuel") to cad("64")),
            cost.costs.byYearCategory.getValue(2026),
        )
        // MNT-13: $600 a year for this vehicle, from its purchase on January 10: 356 days of 365.
        assertEquals(cad("585.21"), cost.insurance)
        assertEquals(mapOf(2026 to cad("585.21")), cost.insuranceByYear, "nothing before the purchase")
        assertEquals(cad("1534"), cost.costs.total, "the estimate is shown beside the running costs, not added to them")
        // The other vehicle has no purchase date or readings: its share covers the period asked for.
        assertEquals(cad("600.00"), books.vehicles.costs(other.id, d(1, 1), d(12, 31)).insurance)
    }

    @Test
    fun `a warranty limited by kilometres reminds when the odometer should reach them`() {
        val v = civic()
        books.vehicles.saveWarranty(Warranty("", v.id, WarrantyKind.POWERTRAIN, "Honda", endKm = 50_000))
        // 9,000 km in the 180 days since the purchase is 50 km a day: the last 1,000 km take 20 days.
        books.vehicles.addReading(v.id, d(7, 9), 49_000)
        val r = books.renewals(d(7, 9)).single { it.kind == RenewalKind.WARRANTY }
        assertEquals(d(7, 29), r.date)
        assertEquals("Honda · 50000 km", r.detail)
        books.vehicles.addReading(v.id, d(7, 30), 50_100)
        assertTrue(books.renewals(d(7, 31)).none { it.kind == RenewalKind.WARRANTY }, "past its kilometres, the warranty is over")
    }
}
