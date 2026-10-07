package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.ocr.OcrResult
import ca.schippers.hfm.security.KdfParams
import ca.schippers.hfm.security.KeyPair
import ca.schippers.hfm.security.PairKey
import ca.schippers.hfm.sync.CaptureFields
import ca.schippers.hfm.sync.CaptureItem
import ca.schippers.hfm.sync.CaptureKind
import ca.schippers.hfm.sync.Direction
import ca.schippers.hfm.sync.PairRequest
import ca.schippers.hfm.sync.PhonePlace
import ca.schippers.hfm.sync.PhoneTrip
import ca.schippers.hfm.sync.PhoneTripStop
import ca.schippers.hfm.sync.SyncCrypto
import ca.schippers.hfm.sync.SyncRequest
import ca.schippers.hfm.sync.SyncResponse
import ca.schippers.hfm.sync.VoiceWav
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** TRP-11 to TRP-19: positions and addresses, stops and breaks, photos and notes, and stations saved as places. */
class TripStopsOnPhoneTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private val today = LocalDate(2026, 10, 7)
    private val now = 1_790_000_000_000L
    private val noConversion = object : CaptureConverter {
        override fun pagesToPdf(pages: List<ByteArray>) = ByteArray(0)
        override fun recognize(content: ByteArray): OcrResult? = null
    }

    private fun household(): Books = Books(store.create(temp.resolve("T.hfm"), "T", "perry", "Perry", "password1".toCharArray()).session)

    private fun pairPhone(books: Books, deviceId: String): ByteArray {
        val phone = KeyPair.generate()
        val invitation = books.sync.invitation("Bureau", "127.0.0.1", 47311, now)
        val desktopKey = SyncCrypto.unb64(invitation.desktopPublicKey)
        books.sync.pair(PairRequest(deviceId, "Pixel", SyncCrypto.b64(phone.publicKey), SyncCrypto.phoneProof(invitation.oneTimeCode, phone.publicKey, desktopKey)), now)
        return PairKey.derive(phone, desktopKey, desktopKey, phone.publicKey)
    }

    private fun send(books: Books, deviceId: String, key: ByteArray, request: SyncRequest): SyncResponse {
        val sealed = SyncCrypto.seal(SyncRequest.serializer(), request, key, books.sync.desktopId, deviceId, Direction.TO_DESKTOP)
        return SyncCrypto.open(SyncResponse.serializer(), books.sync.handle(deviceId, sealed, noConversion, now, today), key, books.sync.desktopId, deviceId, Direction.TO_PHONE)
    }

    /** A second of silence as the phone records it (mono, 16 kHz). */
    private fun wav(): ByteArray = VoiceWav.wrap(ByteArray(32_000))

    @Test
    fun `a trip with a stop, a break, positions, addresses, a photo and a voice note reaches the trip log`() {
        val books = household()
        try {
            val group = books.groups().first().id
            val van = books.vehicles.save(Vehicle("", group, "Van", purchaseDate = LocalDate(2024, 1, 1), purchaseOdometer = 10_000, usage = VehicleUsage.MIXED))
            val home = books.places.save(Place("", group, "Home", PlaceCategory.HOME, "12 Elm St, Ottawa", 45.401, -75.703, province = "ON"))
            val key = pairPhone(books, "phone-1")

            // TRP-19: a station added by hand on the phone, with its address.
            val station = PhonePlace("st-1", "ch-1", now, "Corner Esso", "FUEL", 45.41, -75.70, 150, "350 Bank St, Ottawa")
            // TRP-16: a photo and a spoken note taken at the stop, sent before the trip (on arrival the trip follows).
            val photo = CaptureItem(
                "cap-1", CaptureKind.DOCUMENT, now, pages = listOf(SyncCrypto.b64(byteArrayOf(-1, -40, -1, -32, 1, 2, 3))), fileName = "trip-photo.jpg",
                fields = CaptureFields(note = "Loading dock", tripId = "trip-1", stopId = "stop-1"),
            )
            val voice = CaptureItem("cap-2", CaptureKind.DOCUMENT, now, fields = CaptureFields(note = "Client wants a quote", tripId = "trip-1"), voice = SyncCrypto.b64(wav()))
            val early = send(books, "phone-1", key, SyncRequest(now, listOf(photo, voice), places = listOf(station)))
            assertEquals(setOf("cap-1", "cap-2", "ch-1"), early.imported.toSet(), early.failed.toString())
            assertEquals("350 Bank St, Ottawa", books.places.find("st-1")?.address)
            assertEquals(2, books.documents.inbox().count { d -> d.links.any { it.entity == DocumentEntity.TRIP } }, "they wait in the inbox until the trip comes")

            val trip = PhoneTrip(
                "trip-1", now, van.id, "2026-10-07T08:00", "2026-10-07T11:30", 20_000, 20_090, "PERSONAL",
                startPlaceId = home.id, endPlaceId = home.id, startLatitude = 45.401, startLongitude = -75.703, endLatitude = 45.4011, endLongitude = -75.7031,
                stops = listOf(
                    PhoneTripStop("stop-1", "STOP", "2026-10-07T08:40", odometer = 20_040, place = "Client warehouse", address = "5 Industrial Rd", latitude = 45.35, longitude = -75.80, purpose = "BUSINESS"),
                    PhoneTripStop("brk-1", "BREAK", "2026-10-07T09:30", "2026-10-07T10:00", latitude = 45.36, longitude = -75.78),
                    PhoneTripStop("stop-2", "STOP", "2026-10-07T10:30", odometer = 20_070, placeId = "st-1"),
                ),
            )
            val answer = send(books, "phone-1", key, SyncRequest(now, emptyList(), trips = listOf(trip)))
            assertEquals(listOf("trip-1"), answer.imported, answer.failed.toString())

            val logged = books.trips.list(2026).single()
            assertEquals(90, logged.km.toInt())
            assertEquals("12 Elm St, Ottawa", logged.startAddress, "the saved place's address")
            assertEquals(45.401, logged.startLatitude)
            assertEquals(3, logged.stops.size)
            assertEquals("Corner Esso", logged.stops[2].place, "a saved place gives its name")
            assertEquals("350 Bank St, Ottawa", logged.stops[2].address)
            assertEquals(210L, logged.minutes)
            assertEquals(30L, logged.breakMinutes)
            assertEquals(180L, logged.drivingMinutes)

            // TRP-12: three legs, each with its distance and purpose; the business leg counts as work.
            val legs = books.trips.legs(logged)
            assertEquals(listOf("Client warehouse", "Corner Esso", "Home"), legs.map { it.to })
            assertEquals(listOf(40, 30, 20), legs.map { it.km.toInt() })
            assertEquals(listOf(TripPurpose.BUSINESS, TripPurpose.PERSONAL, TripPurpose.PERSONAL), legs.map { it.purpose })
            assertEquals(40, books.trips.vehicleUse(2026).single { it.vehicleId == van.id }.workKm.toInt())
            assertEquals(40, books.trips.totals(2026)[null to TripPurpose.BUSINESS]?.toInt())
            val logbook = books.trips.logbook(van.id, 2026)
            assertEquals(3, logbook.lines.size, "the CRA logbook lists each leg")
            assertEquals(Triple("Home", "Client warehouse", TripPurpose.BUSINESS), logbook.lines[0].let { Triple(it.from, it.to, it.purpose) })
            assertEquals(20_000 to 20_040, logbook.lines[0].startOdometer to logbook.lines[0].endOdometer)

            // TRP-16: the photo and the note are filed with the trip now, the photo with its stop; the voice beside its note.
            val docs = books.trips.attachments(logged)
            assertEquals(2, docs.size)
            assertTrue(docs.none { it.status == DocumentStatus.INBOX })
            assertEquals(mapOf(docs.single { it.fileName == "trip-photo.jpg" }.id to "stop-1"), books.trips.attachmentStops(logged))
            val note = docs.single { it.fileName != "trip-photo.jpg" }
            assertEquals(1, books.documents.voiceNotes(note.id).size)

            // A photo taken after the trip arrived is filed at once.
            send(books, "phone-1", key, SyncRequest(now, listOf(photo.copy(id = "cap-3", pages = listOf(SyncCrypto.b64(byteArrayOf(-1, -40, -1, -32, 9))), fields = photo.fields.copy(stopId = null)))))
            assertEquals(3, books.trips.attachments(logged).size)
            assertTrue(books.trips.attachments(logged).none { it.status == DocumentStatus.INBOX })

            // Saving the trip again on the computer keeps its stops; a stop reading out of order is refused.
            books.trips.save(logged.copy(notes = "Quote"))
            assertEquals(3, books.trips.list(2026).single().stops.size)
            assertFailsWith<ValidationException> { books.trips.save(logged.copy(endOdometer = 20_060)) }
            books.trips.delete(logged)
            assertTrue(books.trips.list(2026).isEmpty())
        } finally {
            books.session.close()
        }
    }

    @Test
    fun `a stop sent out of order or with a bad time is refused`() {
        val books = household()
        try {
            val group = books.groups().first().id
            val van = books.vehicles.save(Vehicle("", group, "Van", purchaseDate = LocalDate(2024, 1, 1), purchaseOdometer = 10_000))
            val key = pairPhone(books, "phone-1")
            val base = PhoneTrip("t", now, van.id, "2026-10-07T08:00", "2026-10-07T09:00", 20_000, 20_050)
            val behind = send(books, "phone-1", key, SyncRequest(now, emptyList(), trips = listOf(base.copy(stops = listOf(PhoneTripStop("s", "STOP", "2026-10-07T08:30", odometer = 20_060))))))
            assertEquals(listOf("t"), behind.failed.map { it.id })
            val badTime = send(books, "phone-1", key, SyncRequest(now, emptyList(), trips = listOf(base.copy(stops = listOf(PhoneTripStop("s", "BREAK", "half past eight"))))))
            assertEquals(listOf("t"), badTime.failed.map { it.id })
            // A trip with no stops is one leg, as before.
            send(books, "phone-1", key, SyncRequest(now, emptyList(), trips = listOf(base)))
            val trip = books.trips.list(2026).single()
            assertEquals(1, books.trips.legs(trip).size)
            assertEquals(60L, trip.drivingMinutes)
            assertEquals(LocalDateTime(2026, 10, 7, 8, 0), trip.startAt)
        } finally {
            books.session.close()
        }
    }
}
