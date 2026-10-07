package ca.schippers.hfm.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CAL-10 on the phone: schedules in the reference data, readable by older and newer apps. */
class ScheduleReferenceTest {

    private val alex = RefSchedule("Alex", "WORK", "2026-10-06", "08:00", "16:30", "Office")
    private val sam = RefSchedule("Sam", "WORK", "2026-10-07", "19:00", "07:00")

    @Test
    fun `schedules go to the phone and come back the same`() {
        val ref = ReferenceData("H", "en", "CAD", schedules = listOf(alex, sam))
        val text = SyncJson.encodeToString(ReferenceData.serializer(), ref)
        assertTrue("\"schedules\"" in text)
        assertEquals(ref, SyncJson.decodeFromString(ReferenceData.serializer(), text))
        assertNull(SyncJson.decodeFromString(ReferenceData.serializer(), text).schedules[1].label, "no place given")
    }

    @Test
    fun `an older desktop's data has no schedules`() {
        val old = """{"householdName":"H","language":"en","baseCurrency":"CAD","events":[],"generatedAtMillis":5}"""
        assertTrue(SyncJson.decodeFromString(ReferenceData.serializer(), old).schedules.isEmpty())
        // Without schedules, the data is written exactly as before.
        assertTrue("schedules" !in SyncJson.encodeToString(ReferenceData.serializer(), ReferenceData("H", "en", "CAD")))
        // A copy kept before schedules existed is fetched again in full.
        assertNull(ReferenceData.knownVersion("v", storedFormat = 3))
        assertEquals(7, ReferenceData.FORMAT)
    }
}
