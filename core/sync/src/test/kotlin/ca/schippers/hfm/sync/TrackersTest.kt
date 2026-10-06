package ca.schippers.hfm.sync

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** UTL-01, UTL-02, HRS-01, CHO-01, VOL-01 on the phone: what it sends, the timer, and compatibility. */
class TrackersTest {

    private val zone = ZoneId.of("America/Toronto")

    @Test
    fun `a running timer gives the hours from its start, kept across a restart`() {
        // 2026-10-06 18:20 in Toronto (EDT, UTC-4).
        val start = java.time.ZonedDateTime.of(2026, 10, 6, 18, 20, 0, 0, zone).toInstant().toEpochMilli()
        val timer = HoursTimer("lee", start, "tutoring", "Grade 10 math")
        // Kept as text in the phone's encrypted store, then read back after the app starts again.
        val kept = SyncJson.decodeFromString(HoursTimer.serializer(), SyncJson.encodeToString(HoursTimer.serializer(), timer))
        assertEquals(timer, kept)
        val hours = kept.stop(start + 95 * 60_000 + 29_000, zone)
        assertEquals(PhoneHours("lee", "2026-10-06", 95, "tutoring", "18:20", "Grade 10 math"), hours)
        assertEquals(96, kept.minutes(start + 95 * 60_000 + 30_000), "rounded to the nearest minute")
        assertEquals(1, kept.minutes(start + 5_000), "at least a minute")
    }

    @Test
    fun `hours are typed as hours and minutes or as decimal hours`() {
        assertEquals(90, HoursTimer.parse("1:30"))
        assertEquals(90, HoursTimer.parse("1.5"))
        assertEquals(90, HoursTimer.parse("1,5 h"))
        assertEquals(45, HoursTimer.parse("0:45"))
        assertNull(HoursTimer.parse("1:75"))
        assertNull(HoursTimer.parse("0"))
        assertNull(HoursTimer.parse("abc"))
        assertEquals("1:05", HoursTimer.format(65))
    }

    @Test
    fun `log entries travel apart from captures, and older phones and desktops still read each other`() {
        val trackers = listOf(
            PhoneTracker("t1", 1, meter = PhoneMeterReading("hydro", "2026-10-06", "10450.5", onPeak = "2100", midPeak = "2300", offPeak = "6050.5")),
            PhoneTracker("t2", 2, tank = PhoneTankReading("propane", "2026-10-06", percent = "55")),
            PhoneTracker("t3", 3, hours = PhoneHours("lee", "2026-10-06", 90, startTime = "18:20")),
            PhoneTracker("t4", 4, chore = PhoneChoreTick("dishes", "2026-10-06")),
            PhoneTracker("t5", 5, volunteer = PhoneVolunteer("sam", "Fire department", "2026-10-05", 240, "FIREFIGHTER")),
        )
        val request = SyncRequest(1, emptyList(), "v1", trackers = trackers)
        val text = SyncJson.encodeToString(SyncRequest.serializer(), request)
        assertEquals(request, SyncJson.decodeFromString(SyncRequest.serializer(), text))
        // A request with none is written exactly as before.
        assertEquals("""{"sentAtMillis":1,"items":[],"referenceVersion":"v1"}""", SyncJson.encodeToString(SyncRequest.serializer(), SyncRequest(1, emptyList(), "v1")))
        // An older desktop skips the field it does not know and reads the rest.
        val older = SyncJson.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject
        assertTrue("trackers" in older.keys)
        assertEquals("1", older["sentAtMillis"].toString())

        // Reference data: an older desktop's has no trackers; a newer one's reads back whole.
        val old = """{"householdName":"H","language":"en","baseCurrency":"CAD","generatedAtMillis":5}"""
        val ref = SyncJson.decodeFromString(ReferenceData.serializer(), old)
        assertEquals(RefTrackers(), ref.trackers)
        val withTrackers = ref.copy(
            trackers = RefTrackers(
                meters = listOf(RefMeter("hydro", "Hydro", "ELECTRICITY", "Cottage", true, "10400", "2026-09-30")),
                tanks = listOf(RefTank("propane", "Propane", "PROPANE", "945", "Cottage", "60")),
                clients = listOf(RefWorkClient("lee", "Lee family", listOf(RefWorkTask("tutoring", "Tutoring")))),
                chores = listOf(RefChore("dishes", "Dishes", "kid", "Emma", "1.00", "CAD", doneDates = listOf("2026-10-05"))),
                organizations = listOf(RefVolunteerOrg("Fire department", "FIREFIGHTER", memberId = "sam")),
            ),
        )
        assertEquals(withTrackers, SyncJson.decodeFromString(ReferenceData.serializer(), SyncJson.encodeToString(ReferenceData.serializer(), withTrackers)))
        assertEquals(5, ReferenceData.FORMAT)
        assertNull(ReferenceData.knownVersion("abc", storedFormat = 3), "a phone that kept format 3 asks for everything again")
    }
}
