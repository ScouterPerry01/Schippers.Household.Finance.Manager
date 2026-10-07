package ca.schippers.hfm.sync

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TRP-12 to TRP-15: stops and breaks on the way, and what an older phone or computer makes of them. */
class TripStopsTest {

    private val stop = PhoneTripStop("s1", TripRules.STOP, "2026-10-07T09:20", odometer = 1_030, place = "Bank", purpose = "BUSINESS")
    private val pause = PhoneTripStop("b1", TripRules.BREAK, "2026-10-07T09:40", "2026-10-07T10:05", latitude = 45.4, longitude = -75.7)
    private val open = PhoneTripStop("b2", TripRules.BREAK, "2026-10-07T10:50")
    private val lenient = Json { ignoreUnknownKeys = true }

    @Test
    fun `breaks are left out of the driving time`() {
        assertEquals(25, TripRules.breakMinutes(listOf(stop, pause), "2026-10-07T11:00"))
        assertEquals(120 - 25L, TripRules.drivingMinutes("2026-10-07T09:00", "2026-10-07T11:00", listOf(stop, pause)))
        // A break not ended counts until the end.
        assertEquals(25 + 10L, TripRules.breakMinutes(listOf(stop, pause, open), "2026-10-07T11:00"))
        assertNull(TripRules.drivingMinutes("not a time", "2026-10-07T11:00", emptyList()))
        // A break over midnight.
        val night = PhoneTripStop("n", TripRules.BREAK, "2026-10-07T23:50", "2026-10-08T00:20")
        assertEquals(30, TripRules.breakMinutes(listOf(night), "2026-10-08T01:00"))
    }

    @Test
    fun `the break under way is the last point when not ended`() {
        assertEquals("b2", TripRules.openBreak(listOf(stop, pause, open))?.id)
        assertNull(TripRules.openBreak(listOf(stop, pause)))
        assertNull(TripRules.openBreak(emptyList()))
    }

    @Test
    fun `the next leg starts from the last stop and readings must follow`() {
        assertEquals(1_000, TripRules.lastReading(1_000, emptyList()))
        assertEquals(1_030, TripRules.lastReading(1_000, listOf(stop, pause)))
        assertTrue(TripRules.follows(1_030, 1_042))
        assertTrue(!TripRules.follows(1_030, 1_030), "the same reading is no distance")
        assertTrue(!TripRules.follows(1_030, 1_020))
        assertTrue(!TripRules.follows(1_030, null))
        assertTrue(!TripRules.follows(1_030, 12_000), "a typo of ten thousand kilometres")
        assertEquals("s1", TripRules.previousStop(listOf(stop, pause))?.id)
        assertNull(TripRules.previousStop(listOf(pause)))
    }

    @Test
    fun `a trip with stops reads on an older computer as the trip it was, and an older trip reads here`() {
        val trip = PhoneTrip(
            "t", 1, "v", "2026-10-07T09:00", "2026-10-07T11:00", 1_000, 1_060, startLatitude = 45.3, startAddress = "1 Main St",
            stops = listOf(stop, pause),
        )
        val json = SyncJson.encodeToString(PhoneTrip.serializer(), trip)
        val older = lenient.decodeFromString(OldTrip.serializer(), json)
        assertEquals(1_060, older.endOdometer)
        val back = SyncJson.decodeFromString(PhoneTrip.serializer(), """{"id":"t","createdAtMillis":1,"vehicleId":"v","start":"a","end":"b","startOdometer":1,"endOdometer":2}""")
        assertTrue(back.stops.isEmpty())
        assertNull(back.startLatitude)
        assertEquals(trip, SyncJson.decodeFromString(PhoneTrip.serializer(), json))
    }

    @Test
    fun `the charging category is offered after fuel`() {
        assertEquals(TripRules.CATEGORIES.indexOf("FUEL") + 1, TripRules.CATEGORIES.indexOf("CHARGING"))
    }

    /** PhoneTrip as the 2026-10-06 app knew it. */
    @kotlinx.serialization.Serializable
    private data class OldTrip(val id: String, val start: String, val end: String, val startOdometer: Int, val endOdometer: Int)
}
