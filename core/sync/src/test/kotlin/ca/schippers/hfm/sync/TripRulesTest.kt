package ca.schippers.hfm.sync

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TRP-02, TRP-03: places matched to a location fix, the suggested purpose, and the protocol's compatibility. */
class TripRulesTest {

    private val home = RefPlace("home", "Home", "HOME", 45.40100, -75.70300, 150)
    private val work = RefPlace("work", "Office", "WORK", 45.42150, -75.69720, 200)
    private val clinic = RefPlace("clinic", "Clinic", "MEDICAL", 45.42260, -75.69720, 150)
    private val noFix = RefPlace("nowhere", "Typed address", "STORE")
    private val lenient = Json { ignoreUnknownKeys = true }

    @Test
    fun `distances along the earth`() {
        // One thousandth of a degree of latitude is about 111 m.
        assertEquals(111.2, TripRules.distanceM(45.0, -75.0, 45.001, -75.0), 0.5)
        // Ottawa to Kingston, about 146 km in a straight line.
        assertEquals(146.2, TripRules.distanceM(45.4215, -75.6972, 44.2312, -76.4860) / 1000, 0.5)
    }

    @Test
    fun `a fix is the nearest place whose radius reaches it`() {
        val places = listOf(home, work, clinic, noFix)
        assertEquals("home", TripRules.nearest(45.40150, -75.70300, places)?.id, "55 m from home")
        assertNull(TripRules.nearest(45.40400, -75.70300, places), "330 m from home: outside its 150 m")
        // Between the office (200 m radius) and the clinic, 122 m apart: the nearer one wins.
        assertEquals("clinic", TripRules.nearest(45.42230, -75.69720, places)?.id)
        assertEquals("work", TripRules.nearest(45.42170, -75.69720, places)?.id)
        assertNull(TripRules.nearest(10.0, 10.0, emptyList()))
    }

    @Test
    fun `coordinates are written the same in every language`() {
        assertEquals("45.42153, -75.69719", TripRules.coordinates(45.421534, -75.697193))
    }

    @Test
    fun `the purpose is suggested from the places and the vehicle's use`() {
        assertEquals("BUSINESS", TripRules.suggestPurpose("HOME", "CLIENT", "PERSONAL"))
        assertEquals("BUSINESS", TripRules.suggestPurpose("CLIENT", "HOME", null), "home from a client")
        assertEquals("MEDICAL", TripRules.suggestPurpose("HOME", "MEDICAL", null))
        assertEquals("MEDICAL", TripRules.suggestPurpose("MEDICAL", "HOME", null), "home from the clinic")
        assertEquals("PERSONAL", TripRules.suggestPurpose("HOME", "WORK", "PERSONAL"), "the commute is personal")
        assertEquals("PERSONAL", TripRules.suggestPurpose(null, null, null))
        assertEquals("BUSINESS", TripRules.suggestPurpose("HOME", "STORE", "COMMERCIAL"))
        assertEquals("MEDICAL", TripRules.suggestPurpose("HOME", "MEDICAL", "COMMERCIAL"))
    }

    @Test
    fun `a request with trips, fuel and places is read by a desktop that knows nothing of them`() {
        val request = SyncRequest(
            1L, emptyList(), "v1",
            trips = listOf(PhoneTrip("t1", 1L, "civic", "2026-10-06T08:05", "2026-10-06T08:31", 65_420, 65_442, "PERSONAL", load = "TOWING", trailerId = "trailer")),
            fuel = listOf(PhoneFuel("f1", 1L, "civic", "2026-10-06", "41.8", 65_450, "68.55")),
            places = listOf(PhonePlace("p1", "c1", 1L, "Cottage", "OTHER", 44.9, -76.7)),
        )
        val text = SyncJson.encodeToString(SyncRequest.serializer(), request)
        // A desktop before 1.0 knew only these fields, and ignores the rest.
        val older = lenient.decodeFromString(OldRequest.serializer(), text)
        assertEquals(1L, older.sentAtMillis)
        assertTrue(older.items.isEmpty())
        val back = SyncJson.decodeFromString(SyncRequest.serializer(), text)
        assertEquals(request, back)
        // And a phone before 1.0 reads newer reference data.
        val reference = ReferenceData(
            "Home", "en", "CAD", vehicles = listOf(RefVehicle("civic", "Civic", 65_420, fuelType = "GASOLINE", use = "PERSONAL")),
            places = listOf(home), trailers = listOf(RefTrailer("trailer", "Boat trailer")), userMemberId = "alex",
        )
        val refText = SyncJson.encodeToString(ReferenceData.serializer(), reference)
        assertEquals(reference, SyncJson.decodeFromString(ReferenceData.serializer(), refText))
        assertEquals("Civic", lenient.decodeFromString(OldReference.serializer(), refText).vehicles.single().name)
    }

    @kotlinx.serialization.Serializable
    private data class OldRequest(val sentAtMillis: Long, val items: List<CaptureItem>, val referenceVersion: String? = null)

    @kotlinx.serialization.Serializable
    private data class OldVehicle(val id: String, val name: String, val odometer: Int?, val unit: String = "KM")

    @kotlinx.serialization.Serializable
    private data class OldReference(val householdName: String, val vehicles: List<OldVehicle> = emptyList())
}
