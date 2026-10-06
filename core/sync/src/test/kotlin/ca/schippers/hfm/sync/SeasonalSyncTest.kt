package ca.schippers.hfm.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** SEA-04: the seasonal checklist and its ticks travel between phone and desktop, old and new. */
class SeasonalSyncTest {

    @Test
    fun `ticks made on the phone survive the trip, and older requests still read`() {
        val tick = PhoneTaskDone("t-1", 1L, "task", "car", true, "2026-10-06", "Canadian Tire", "120.50", 61500)
        val request = SyncRequest(2L, emptyList(), "v1", tasksDone = listOf(tick))
        val text = SyncJson.encodeToString(SyncRequest.serializer(), request)
        assertEquals(request, SyncJson.decodeFromString(SyncRequest.serializer(), text))
        // A request from a phone before the checklist has no ticks; a desktop before it ignores them.
        assertTrue(SyncJson.decodeFromString(SyncRequest.serializer(), """{"sentAtMillis":1,"items":[]}""").tasksDone.isEmpty())
        val ignoring = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        assertEquals("v1", ignoring.decodeFromString(OldRequest.serializer(), text).referenceVersion)
    }

    @Test
    fun `the checklist reaches the phone, and reference data without it still reads`() {
        val ref = ReferenceData(
            "H", "en", "CAD",
            seasonal = RefSeasonal("FALL", "2026-09-22", "2026-12-21", listOf(RefSeasonalTask("t", "y", "Yard", "Rake the leaves", false, "TO_DO", "2026-11-01"))),
        )
        val text = SyncJson.encodeToString(ReferenceData.serializer(), ref)
        assertEquals(ref, SyncJson.decodeFromString(ReferenceData.serializer(), text))
        assertNull(SyncJson.decodeFromString(ReferenceData.serializer(), """{"householdName":"H","language":"en","baseCurrency":"CAD"}""").seasonal)
        // A copy kept by an app before the checklist is fetched again whole.
        assertNull(ReferenceData.knownVersion("v1", 3))
        assertEquals("v1", ReferenceData.knownVersion("v1", ReferenceData.FORMAT))
    }

    /** What a desktop before the checklist reads of a request. */
    @kotlinx.serialization.Serializable
    private data class OldRequest(val sentAtMillis: Long, val items: List<CaptureItem>, val referenceVersion: String? = null)
}
