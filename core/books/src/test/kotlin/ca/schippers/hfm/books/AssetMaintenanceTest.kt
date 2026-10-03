package ca.schippers.hfm.books

import ca.schippers.hfm.calc.schedule.DueState
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
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AssetMaintenanceTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var group: String
    private lateinit var boat: Asset
    private lateinit var house: Asset
    private fun d(s: String) = LocalDate.parse(s)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private val upkeep get() = books.assetMaintenance

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("M.hfm"), "M", "perry", "Perry", "pw".toCharArray()).session)
        group = books.groups().single().id
        books.accounts.create(AccountDraft(group, "Chèques", AccountType.CHEQUING, Currency.CAD, cad("1000"), d("2015-01-01")))
        house = books.assets.save(Asset("", group, AssetKind.HOME, "Maison", purchaseDate = d("2015-05-01")))
        boat = books.assets.save(Asset("", group, AssetKind.BOAT, "Chaloupe", purchaseDate = d("2025-06-01"), purchasePrice = cad("14000"), meter = MeterUnit.HOURS))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    @Test
    fun `starter tasks follow the kind of asset and its meter`() {
        upkeep.addReading(boat.id, d("2026-05-01"), 100)
        val tasks = upkeep.addStarterTasks(boat.id, d("2026-05-01")) { it }
        assertEquals(upkeep.templates(AssetKind.BOAT).toSet(), tasks.mapNotNull { it.templateKey }.toSet())
        val oil = tasks.first { it.templateKey == "engine_oil" }
        assertEquals(100, oil.intervalUsage)
        assertEquals(100, oil.startUsage)
        // Adding them again adds nothing.
        assertTrue(upkeep.addStarterTasks(boat.id, d("2026-05-01")) { it }.isEmpty())

        // A home has no meter: its tasks repeat by time or season only.
        val home = upkeep.addStarterTasks(house.id, d("2026-05-01")) { it }
        assertTrue(home.isNotEmpty() && home.all { it.intervalUsage == null && it.intervalMonths != null })
        assertEquals(d("2026-09-15"), upkeep.taskStatuses(house.id, d("2026-05-01")).first { it.task.templateKey == "hvac_service" }.due.dueDate)
        assertFailsWith<ValidationException> { upkeep.saveTask(AssetTask("", house.id, "Filtre", intervalUsage = 100)) }
        assertFailsWith<ValidationException> { upkeep.addReading(house.id, d("2026-05-01"), 3) }

        // Saving the asset again keeps its tasks.
        books.assets.save(books.assets.get(boat.id).copy(location = "Chalet"))
        assertEquals(tasks.size, upkeep.tasks(boat.id).size)
    }

    @Test
    fun `hours forecast when a task falls due, and a service restarts it`() {
        upkeep.addReading(boat.id, d("2026-05-01"), 100)
        val oil = upkeep.addStarterTasks(boat.id, d("2026-05-01")) { it }.first { it.templateKey == "engine_oil" }
        upkeep.addReading(boat.id, d("2026-07-01"), 190)
        val before = upkeep.taskStatuses(boat.id, d("2026-07-01")).first { it.task.id == oil.id }
        assertEquals(200, before.due.dueUsage)
        assertEquals(DueState.SOON, before.due.state)
        // 90 hours over 61 days: the last 10 hours take about 6 days.
        assertEquals(d("2026-07-07"), before.due.forecastDate)

        upkeep.saveService(AssetServiceRecord("", boat.id, d("2026-07-05"), 198, "Marina du Lac", parts = "Huile 10W-30, filtre", cost = cad("180"), taskIds = setOf(oil.id)))
        val after = upkeep.taskStatuses(boat.id, d("2026-07-05")).first { it.task.id == oil.id }
        assertEquals(298, after.due.dueUsage)
        assertEquals(d("2027-07-05"), after.due.dueDate)
        assertEquals(DueState.OK, after.due.state)
        assertEquals("Huile 10W-30, filtre", upkeep.services(boat.id).single().parts)

        // The fall winterization is in the combined list once it comes near.
        val due = books.upkeepDue(d("2026-09-20"))
        assertTrue(due.any { it.subjectId == boat.id && it.taskName == "boat_winterize" && !it.vehicle }, "$due")
        assertTrue(books.upkeepBetween(d("2026-10-01"), d("2026-10-31"), d("2026-09-20")).any { it.taskName == "boat_winterize" })
    }

    @Test
    fun `cost of ownership counts services, linked payments and a share of the insurance`() {
        upkeep.addReading(boat.id, d("2026-05-01"), 100)
        upkeep.addReading(boat.id, d("2026-09-30"), 198)
        upkeep.saveService(AssetServiceRecord("", boat.id, d("2026-07-05"), provider = "Marina du Lac", cost = cad("180")))
        val chequing = books.accounts.list().first { it.account.name == "Chèques" }.account
        upkeep.saveService(AssetServiceRecord("", boat.id, d("2026-08-10"), diy = true, cost = cad("45.50")), PaymentDraft(chequing.id, null, "Canadian Tire"))
        books.insurance.save(InsurancePolicy("", group, PolicyKind.BOAT, "Assureur", premium = cad("600"), frequency = PremiumFrequency.ANNUAL, assetIds = setOf(boat.id)))

        val c = upkeep.costs(boat.id, d("2026-01-01"), d("2026-12-31"))
        assertEquals(cad("225.50"), c.costs.total)
        assertEquals(cad("600.00"), c.insurance)
        assertEquals(98, c.usage)
        assertEquals(cad("2.30"), c.costPerUnit)
        assertEquals(null, c.purchase, "bought the year before")
        assertEquals(cad("14000"), upkeep.costs(boat.id, d("2025-01-01"), d("2025-12-31")).purchase)
    }
}
