package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.OcrResult
import ca.schippers.hfm.security.KdfParams
import ca.schippers.hfm.security.KeyPair
import ca.schippers.hfm.security.PairKey
import ca.schippers.hfm.sync.Direction
import ca.schippers.hfm.sync.HoursTimer
import ca.schippers.hfm.sync.PairRequest
import ca.schippers.hfm.sync.PhoneChoreTick
import ca.schippers.hfm.sync.PhoneMeterReading
import ca.schippers.hfm.sync.PhoneTankReading
import ca.schippers.hfm.sync.PhoneTracker
import ca.schippers.hfm.sync.PhoneVolunteer
import ca.schippers.hfm.sync.SyncCrypto
import ca.schippers.hfm.sync.SyncRequest
import ca.schippers.hfm.sync.SyncResponse
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Path
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** UTL-01, UTL-02, HRS-01, CHO-01, VOL-01: the phone's log forms, from the reference data to the books and back. */
class TrackerSyncTest {

    @TempDir
    lateinit var temp: Path

    private lateinit var books: Books
    private val today = LocalDate(2026, 10, 6)
    private val now = 1_791_300_000_000L
    private val phone = KeyPair.generate()
    private val group get() = books.groups().single().id

    private val converter = object : CaptureConverter {
        override fun pagesToPdf(pages: List<ByteArray>): ByteArray = ByteArray(0)
        override fun recognize(content: ByteArray): OcrResult? = null
    }

    @BeforeEach
    fun setUp() {
        books = Books(HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING).create(temp.resolve("S.hfm"), "Famille S", "perry", "Perry", "pw".toCharArray()).session)
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun pair(): ByteArray {
        val invitation = books.sync.invitation("Bureau", "127.0.0.1", 47311, now)
        val desktopKey = SyncCrypto.unb64(invitation.desktopPublicKey)
        books.sync.pair(PairRequest("pixel", "Pixel", SyncCrypto.b64(phone.publicKey), SyncCrypto.phoneProof(invitation.oneTimeCode, phone.publicKey, desktopKey)), now)
        return PairKey.derive(phone, desktopKey, desktopKey, phone.publicKey)
    }

    private fun send(key: ByteArray, request: SyncRequest): SyncResponse {
        val sealed = SyncCrypto.seal(SyncRequest.serializer(), request, key, books.sync.desktopId, "pixel", Direction.TO_DESKTOP)
        return SyncCrypto.open(SyncResponse.serializer(), books.sync.handle("pixel", sealed, converter, now, today), key, books.sync.desktopId, "pixel", Direction.TO_PHONE)
    }

    @Test
    fun `readings, hours, chores and volunteer hours logged on the phone reach the books once`() {
        val kid = books.members.create("Emma", MemberKind.CHILD)
        val sam = books.members.create("Sam", MemberKind.ADULT)
        val meter = books.utilities.saveMeter(UtilityMeter("", group, "Hydro", MeterKind.ELECTRICITY, timeOfUse = true))
        val tank = books.utilities.saveTank(FuelTank("", group, "Propane", FuelKind.PROPANE, BigDecimal(500)))
        val client = books.workHours.saveClient(WorkClient("", group, "Lee family", Currency.CAD, Money.parse("40", Currency.CAD), tasks = listOf(WorkTask("", "Tutoring"))))
        val chore = books.chores.save(Chore("", group, kid.id, "Dishes", Currency.CAD, Money.parse("1", Currency.CAD)))
        books.volunteer.save(VolunteerEntry("", group, sam.id, "Fire department", VolunteerKind.FIREFIGHTER, LocalDate(2026, 9, 1), 240))
        val key = pair()

        // What the forms pick from comes with the first answer.
        val ref = send(key, SyncRequest(now, emptyList())).reference!!.trackers
        assertEquals(listOf("Hydro"), ref.meters.map { it.name })
        assertTrue(ref.meters.single().timeOfUse)
        assertEquals(listOf("500"), ref.tanks.map { it.capacityLitres })
        assertEquals(listOf("Tutoring"), ref.clients.single().tasks.map { it.name })
        assertEquals("Emma", ref.chores.single().memberName)
        assertEquals("1.00", ref.chores.single().amount)
        assertEquals(listOf("Fire department"), ref.organizations.map { it.name })

        // The timer started on the phone, then stopped 75 minutes later.
        val started = java.time.ZonedDateTime.of(2026, 10, 5, 16, 0, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
        val hours = HoursTimer(client.id, started, client.tasks.single().id).stop(started + 75 * 60_000, ZoneId.systemDefault())
        val trackers = listOf(
            PhoneTracker("m1", now, meter = PhoneMeterReading(meter.id, "2026-10-05", "", onPeak = "100", midPeak = "200", offPeak = "300,5")),
            PhoneTracker("t1", now, tank = PhoneTankReading(tank.id, "2026-10-05", litres = "250")),
            PhoneTracker("h1", now, hours = hours),
            PhoneTracker("c1", now, chore = PhoneChoreTick(chore.id, "2026-10-05")),
            PhoneTracker("v1", now, volunteer = PhoneVolunteer(sam.id, "Fire department", "2026-10-04", 180, "FIREFIGHTER")),
            PhoneTracker("bad", now, chore = PhoneChoreTick("no-such-chore", "2026-10-05")),
        )
        val answer = send(key, SyncRequest(now, emptyList(), trackers = trackers))
        assertEquals(listOf("m1", "t1", "h1", "c1", "v1"), answer.imported)
        assertEquals(listOf("bad"), answer.failed.map { it.id })

        val reading = books.utilities.meter(meter.id).readings.single()
        assertEquals(0, reading.value.compareTo(BigDecimal("600.5")), "the registers add up")
        assertTrue(reading.fromPhone)
        assertEquals(0, books.utilities.tank(tank.id).readings.single().percent.compareTo(BigDecimal(50)))
        val worked = books.workHours.hours(client.id).single()
        assertEquals(LocalDate(2026, 10, 5) to 75, worked.date to worked.minutes)
        assertEquals("16:00", worked.startTime)
        assertEquals(1, books.chores.get(chore.id).ticks.size)
        assertEquals(7 * 60, books.volunteer.year(sam.id, 2026).minutes)

        // Sent again (the answer was lost): acknowledged, not stored twice.
        val again = send(key, SyncRequest(now, emptyList(), trackers = trackers.dropLast(1)))
        assertEquals(listOf("m1", "t1", "h1", "c1", "v1"), again.imported)
        assertEquals(1, books.chores.get(chore.id).ticks.size)
        assertEquals(1, books.workHours.hours(client.id).size)
        assertEquals(listOf("2026-10-05"), again.reference?.trackers?.chores?.single()?.doneDates ?: books.sync.reference(today, now).trackers.chores.single().doneDates)
    }

    @Test
    fun `numbers and texts from the phone are bounded`() {
        val sam = books.members.create("Sam", MemberKind.ADULT)
        val meter = books.utilities.saveMeter(UtilityMeter("", group, "Hydro", MeterKind.ELECTRICITY))
        val tank = books.utilities.saveTank(FuelTank("", group, "Propane", FuelKind.PROPANE, BigDecimal(500)))
        val key = pair()
        val answer = send(
            key,
            SyncRequest(
                now, emptyList(),
                trackers = listOf(
                    PhoneTracker("huge", now, meter = PhoneMeterReading(meter.id, "2026-10-05", "1E999999999")),
                    PhoneTracker("tiny", now, tank = PhoneTankReading(tank.id, "2026-10-05", litres = "1E-999999999")),
                    PhoneTracker("ok", now, meter = PhoneMeterReading(meter.id, "2026-10-05", "12345.5", note = "n".repeat(100_000))),
                    PhoneTracker("org", now, volunteer = PhoneVolunteer(sam.id, "o".repeat(100_000), "2026-10-04", 60, activity = "a".repeat(100_000))),
                ),
            ),
        )
        assertEquals(listOf("ok", "org"), answer.imported)
        assertEquals(listOf("huge", "tiny"), answer.failed.map { it.id })
        assertEquals(500, books.utilities.meter(meter.id).readings.single().notes?.length)
        val entry = books.volunteer.list().single()
        assertEquals(120 to 500, entry.organization.length to entry.activity?.length)
    }

    @Test
    fun `a once-a-day chore ticked on two phones earns once, and the phone hears of fuel orders and unusual use`() {
        val kid = books.members.create("Emma", MemberKind.CHILD)
        val dishes = books.chores.save(Chore("", group, kid.id, "Dishes", Currency.CAD, Money.parse("1", Currency.CAD)))
        val dog = books.chores.save(Chore("", group, kid.id, "Feed the dog", Currency.CAD, Money.parse("0.50", Currency.CAD), severalADay = true))
        // A tank already below its order level, and a meter whose September used 40 % more than last September.
        val tank = books.utilities.saveTank(FuelTank("", group, "Cottage propane", FuelKind.PROPANE, BigDecimal(500)))
        books.utilities.addTankReading(tank.id, LocalDate(2026, 10, 1), BigDecimal(20))
        val meter = books.utilities.saveMeter(UtilityMeter("", group, "Hydro", MeterKind.ELECTRICITY))
        var total = BigDecimal.ZERO
        var date = LocalDate(2025, 1, 1)
        books.utilities.addReading(meter.id, date, total)
        while (date < LocalDate(2026, 10, 1)) {
            total += BigDecimal(if (date == LocalDate(2026, 9, 1)) 1400 else 1000)
            date = date.plus(kotlinx.datetime.DatePeriod(months = 1))
            books.utilities.addReading(meter.id, date, total)
        }
        val key = pair()
        val ticks = listOf(
            PhoneTracker("p1", now, chore = PhoneChoreTick(dishes.id, "2026-10-05")),
            PhoneTracker("p2", now, chore = PhoneChoreTick(dishes.id, "2026-10-05")),
            PhoneTracker("d1", now, chore = PhoneChoreTick(dog.id, "2026-10-05")),
            PhoneTracker("d2", now, chore = PhoneChoreTick(dog.id, "2026-10-05")),
        )
        val answer = send(key, SyncRequest(now, emptyList(), trackers = ticks))
        assertEquals(listOf("p1", "p2", "d1", "d2"), answer.imported, "the second tick is acknowledged, so the phone stops sending it")
        assertEquals(1, books.chores.get(dishes.id).ticks.size, "once a day: earns once")
        assertEquals(2, books.chores.get(dog.id).ticks.size, "several a day")
        assertEquals(Money.parse("2.00", Currency.CAD), books.chores.earnings(kid.id, Currency.CAD, today).unpaid)

        val ref = answer.reference!!.trackers
        assertEquals(listOf(false, true), ref.chores.sortedBy { it.name }.map { it.severalADay })
        assertEquals("2026-10-01", ref.tanks.single().orderDate, "already at its order level: order now")
        val hydro = ref.meters.single()
        assertEquals("2026-09", hydro.unusualMonth)
        assertEquals("+40", hydro.unusualChange)
    }

    @Test
    fun `volunteer hours follow the same rule on the phone as on the computer`() {
        val shared = group
        val sam = books.members.create("Sam", MemberKind.ADULT)
        val kid = books.members.create("Emma", MemberKind.CHILD)
        // Emma's hours are already in the shared group; Sam has none yet.
        books.volunteer.save(VolunteerEntry("", shared, kid.id, "Food bank", VolunteerKind.SCHOOL, LocalDate(2026, 9, 1), 120))
        val private = books.session.createGroup("Perry - private", private = true)
        assertEquals(private, books.volunteer.defaultGroup(sam.id), "a first entry goes to the user's own private group")
        assertEquals(shared, books.volunteer.defaultGroup(kid.id), "then where the person's hours are")
        val key = pair()
        val answer = send(
            key,
            SyncRequest(
                now, emptyList(),
                trackers = listOf(
                    PhoneTracker("s", now, volunteer = PhoneVolunteer(sam.id, "Fire department", "2026-10-04", 180, "FIREFIGHTER")),
                    PhoneTracker("k", now, volunteer = PhoneVolunteer(kid.id, "Food bank", "2026-10-04", 60, "SCHOOL")),
                ),
            ),
        )
        assertEquals(listOf("s", "k"), answer.imported)
        val entries = books.volunteer.list()
        assertEquals(private, entries.single { it.memberId == sam.id }.groupId)
        assertEquals(setOf(shared), entries.filter { it.memberId == kid.id }.map { it.groupId }.toSet())
    }
}
