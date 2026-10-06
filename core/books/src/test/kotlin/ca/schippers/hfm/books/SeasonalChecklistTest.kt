package ca.schippers.hfm.books

import ca.schippers.hfm.calc.schedule.Season
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.OcrResult
import ca.schippers.hfm.security.KdfParams
import ca.schippers.hfm.security.KeyPair
import ca.schippers.hfm.security.PairKey
import ca.schippers.hfm.sync.BundleFile
import ca.schippers.hfm.sync.Direction
import ca.schippers.hfm.sync.PairRequest
import ca.schippers.hfm.sync.PairedDesktop
import ca.schippers.hfm.sync.PhoneTaskDone
import ca.schippers.hfm.sync.ReferenceData
import ca.schippers.hfm.sync.SyncCrypto
import ca.schippers.hfm.sync.SyncRequest
import ca.schippers.hfm.sync.SyncResponse
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** SEA-01 to SEA-05: starter tasks for pools and yards, the seasonal checklist, its phone round trip, and rebates. */
class SeasonalChecklistTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var group: String
    private lateinit var house: Asset
    private lateinit var pool: Asset
    private lateinit var yard: Asset
    private lateinit var car: Vehicle
    private val today = LocalDate(2026, 10, 6)
    private val now = 1_790_000_000_000L
    private fun d(s: String) = LocalDate.parse(s)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private val noConversion = object : CaptureConverter {
        override fun pagesToPdf(pages: List<ByteArray>) = ByteArray(0)
        override fun recognize(content: ByteArray): OcrResult? = null
    }

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("S.hfm"), "S", "perry", "Perry", "password1".toCharArray()).session)
        group = books.groups().single().id
        house = books.assets.save(Asset("", group, AssetKind.HOME, "House", purchaseDate = d("2015-05-01"), purchasePrice = cad("400000")))
        pool = books.assets.save(Asset("", group, AssetKind.POOL, "Pool", parentId = house.id))
        yard = books.assets.save(Asset("", group, AssetKind.YARD, "Yard", parentId = house.id))
        car = books.vehicles.save(Vehicle("", group, "Civic", purchaseDate = d("2022-04-01"), purchaseOdometer = 10))
        for (a in listOf(house, pool, yard)) books.assetMaintenance.addStarterTasks(a.id, today) { it }
        books.vehicles.addStarterTasks(car.id, today) { it }
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun task(a: Asset, key: String) = books.assetMaintenance.tasks(a.id).first { it.templateKey == key }

    @Test
    fun `pools and yards get their own starter tasks, and a home and its yard share the outside taps`() {
        val keys = books.assetMaintenance.tasks(pool.id).mapNotNull { it.templateKey }.toSet()
        assertEquals(setOf("pool_open", "pool_chemistry", "pool_filter", "pool_close", "pool_cover"), keys)
        val chemistry = task(pool, "pool_chemistry")
        assertEquals(1, chemistry.intervalWeeks)
        assertEquals((5 to 20) to (9 to 15), chemistry.part)
        // Weekly in season: in October the next test is at next spring's opening.
        assertEquals(d("2027-05-20"), books.assetMaintenance.taskStatuses(pool.id, today).first { it.task.id == chemistry.id }.due.dueDate)

        val yardKeys = books.assetMaintenance.tasks(yard.id).mapNotNull { it.templateKey }.toSet()
        assertTrue(yardKeys.containsAll(listOf("mower_service", "irrigation_start", "irrigation_blowout", "snow_blower", "fall_leaves", "yard_spring_cleanup")))
        assertFalse("outside_taps_off" in yardKeys, "the house has them already")
        assertTrue(books.assetMaintenance.tasks(house.id).any { it.templateKey == "outside_taps_off" })
        val carKeys = books.vehicles.tasks(car.id).mapNotNull { it.templateKey }.toSet()
        assertTrue(carKeys.containsAll(listOf("wiper_blades", "block_heater", "winter_kit", "summer_check")))

        // A task kept to part of the year needs both ends.
        assertFailsWith<ValidationException> { books.assetMaintenance.saveTask(chemistry.copy(id = "", seasonTo = null)) }
        // Saving keeps the weeks and the part of the year.
        val saved = books.assetMaintenance.saveTask(chemistry.copy(intervalWeeks = 2))
        assertEquals(2, books.assetMaintenance.tasks(pool.id).first { it.id == saved.id }.intervalWeeks)
    }

    @Test
    fun `the fall checklist lists the season's tasks across vehicles and assets, and a tick records the service`() {
        val fall = books.seasonal.checklist(Season.FALL, today)
        assertEquals(d("2026-09-22"), fall.window.start)
        val names = fall.items.map { it.taskName }.toSet()
        assertTrue(names.containsAll(listOf("irrigation_blowout", "outside_taps_off", "fall_leaves", "snow_blower", "pool_cover", "wiper_blades", "winter_kit", "winter_tires_on", "gutters")), names.toString())
        assertFalse("pool_open" in names)
        assertFalse("pool_chemistry" in names, "the pool is closed")
        assertEquals(0, fall.done)

        val blowout = fall.items.first { it.taskName == "irrigation_blowout" }
        assertEquals(d("2026-10-10"), blowout.dueDate)
        assertEquals(ChecklistState.SOON, blowout.state)
        books.seasonal.tick(blowout, d("2026-10-08"), "Done by the irrigation company", BigDecimal("95.00"))
        val service = books.assetMaintenance.services(yard.id).single()
        assertEquals(setOf(blowout.taskId), service.taskIds)
        assertEquals(cad("95.00"), service.cost)

        val wipers = fall.items.first { it.taskName == "wiper_blades" }
        assertTrue(wipers.vehicle)
        books.seasonal.tick(wipers, d("2026-10-08"), null, null, 61200)
        assertEquals(61200, books.vehicles.services(car.id).single().odometer)
        // Ticked again the same day (on a phone too): recorded once.
        books.seasonal.record(true, car.id, wipers.taskId, d("2026-10-08"), "From the phone", null, null)
        assertEquals(1, books.vehicles.services(car.id).size)

        val after = books.seasonal.checklist(Season.FALL, d("2026-10-08"))
        assertEquals(fall.total, after.total, "a ticked task stays on the list, done")
        assertEquals(2, after.done)
        assertEquals(d("2026-10-08"), after.items.first { it.taskId == blowout.taskId }.doneOn)

        // Next spring's list has the openings, and the weekly pool test from opening day.
        val spring = books.seasonal.checklist(Season.SPRING, today)
        val springNames = spring.items.map { it.taskName }.toSet()
        assertTrue(springNames.containsAll(listOf("pool_open", "pool_chemistry", "mower_service", "outside_taps_on", "winter_tires_off")), springNames.toString())
        assertTrue(spring.items.all { it.state == ChecklistState.TO_DO })
        assertEquals(d("2027-05-20"), spring.items.first { it.taskName == "pool_chemistry" }.dueDate)
        assertEquals(listOf(Season.FALL, Season.WINTER, Season.SPRING, Season.SUMMER), books.seasonal.seasons(today).map { it.season })
    }

    @Test
    fun `the checklist goes to the phone, and a tick made there comes back as a service`() {
        val phone = KeyPair.generate()
        val invitation = books.sync.invitation("Office", "127.0.0.1", 47311, now)
        val desktopKey = SyncCrypto.unb64(invitation.desktopPublicKey)
        books.sync.pair(PairRequest("pixel", "Pixel", SyncCrypto.b64(phone.publicKey), SyncCrypto.phoneProof(invitation.oneTimeCode, phone.publicKey, desktopKey)), now)
        val key = PairKey.derive(phone, desktopKey, desktopKey, phone.publicKey)
        fun send(request: SyncRequest): SyncResponse {
            val sealed = SyncCrypto.seal(SyncRequest.serializer(), request, key, books.sync.desktopId, "pixel", Direction.TO_DESKTOP)
            return SyncCrypto.open(SyncResponse.serializer(), books.sync.handle("pixel", sealed, noConversion, now, today), key, books.sync.desktopId, "pixel", Direction.TO_PHONE)
        }

        val first = send(SyncRequest(now, emptyList()))
        val seasonal = assertNotNull(first.reference?.seasonal)
        assertEquals("FALL", seasonal.season)
        assertEquals("2026-12-21", seasonal.end)
        val leaves = seasonal.tasks.first { it.task == "fall_leaves" }
        assertEquals("Yard", leaves.subject)
        assertEquals("TO_DO", leaves.state)
        val tires = seasonal.tasks.first { it.task == "winter_tires_on" }
        assertTrue(tires.vehicle)
        assertEquals("KM", tires.unit)

        // Ticked on the phone with a cost and a reading; sent twice, recorded once.
        val tick = PhoneTaskDone("t-1", now, tires.taskId, tires.subjectId, true, "2026-10-06", "Canadian Tire", "120,50", 61500)
        val answer = send(SyncRequest(now, emptyList(), first.referenceVersion, tasksDone = listOf(tick)))
        assertEquals(listOf("t-1"), answer.imported)
        assertEquals(listOf("t-1"), send(SyncRequest(now, emptyList(), answer.referenceVersion, tasksDone = listOf(tick))).imported)
        val service = books.vehicles.services(car.id).single()
        assertEquals(cad("120.50"), service.cost)
        assertEquals(61500, service.odometer)
        assertEquals("Canadian Tire", service.notes)
        // The phone's list now shows it done.
        assertEquals("DONE", assertNotNull(answer.reference?.seasonal).tasks.first { it.taskId == tires.taskId }.state)

        // By file too; a tick for a task removed since is refused with its reason.
        val desktop = PairedDesktop(books.sync.desktopId, "S", "127.0.0.1", 47311, "pixel", SyncCrypto.b64(key))
        val leavesTick = PhoneTaskDone("t-2", now, leaves.taskId, leaves.subjectId, false, "2026-10-06")
        val gone = PhoneTaskDone("t-3", now, "no-such-task", yard.id, false, "2026-10-06")
        val (_, file) = BundleFile.request(desktop, SyncRequest(now, emptyList(), tasksDone = listOf(leavesTick, gone)), now)
        val reply = BundleFile.reply(desktop, books.sync.handleFile(file, noConversion, now, today).bytes)
        assertTrue("t-2" in reply.imported)
        assertEquals(listOf("t-3"), reply.failed.map { it.id })
        assertEquals(1, books.assetMaintenance.services(yard.id).size)
        assertEquals(5, ReferenceData.FORMAT)
    }

    @Test
    fun `an energy upgrade's rebates lower its net cost and what it adds to the cost base`() {
        val heatPump = books.homeProjects.save(
            HomeProject("", group, "Cold-climate heat pump", ProjectStatus.DONE, Currency.CAD, house.id, d("2026-05-01"), d("2026-06-15"), energyKind = EnergyUpgrade.HEAT_PUMP),
        )
        assertEquals(EnergyUpgrade.HEAT_PUMP, books.homeProjects.list().single().energyKind)
        books.homeProjects.addCost(heatPump, d("2026-06-15"), "Heat pump installed", cad("14000"))
        val p = books.homeProjects.list().single()
        val rebates = books.projectRebates
        val utility = rebates.save(p, ProjectRebate("", p.id, "Home Renovation Savings", RebateStatus.RECEIVED, amount = cad("7500"), applied = d("2026-06-20"), receivedOn = d("2026-08-30"), received = cad("7500")))
        rebates.save(p, ProjectRebate("", p.id, "Municipal loan", RebateStatus.APPLIED, amount = cad("2000"), applied = d("2026-07-01")))
        assertFailsWith<ValidationException> { rebates.save(p, ProjectRebate("", p.id, "No amount", RebateStatus.RECEIVED)) }
        assertEquals(cad("6500"), rebates.netCost(p))
        assertEquals(cad("2000"), rebates.pending(p))
        val base = books.homeProjects.costBase(house.id)
        assertEquals(cad("6500"), base.improvements)
        assertEquals(cad("7500"), base.rebates)
        assertEquals(cad("406500"), base.total)

        // Papers kept with the rebate and the project.
        val doc = books.documents.import(group, "approval".encodeToByteArray(), "approval.txt", "text/plain").document
        rebates.attach(utility.id, doc.id)
        rebates.attachToProject(p, doc.id)
        assertEquals(listOf(doc.id), rebates.documents(utility.id).map { it.id })
        assertEquals(listOf(doc.id), rebates.projectDocuments(p).map { it.id })
        rebates.detach(utility.id, doc.id)
        assertTrue(rebates.documents(utility.id).isEmpty())

        // Deleting the project deletes its rebates.
        books.homeProjects.delete(p)
        assertEquals(cad("0"), books.homeProjects.costBase(house.id).improvements)
    }
}
