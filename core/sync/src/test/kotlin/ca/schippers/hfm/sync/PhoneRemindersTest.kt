package ca.schippers.hfm.sync

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CAL-03, HLT-03 on the phone: event reminders at their lead times, refill reminders, and the format they travel in. */
class PhoneRemindersTest {

    private val dentist = RefEvent("ev-1|2026-10-06", "Dentist", "2026-10-06", "14:30", "MEDICAL", "12 Main St", "Léa", listOf(1440, 60))
    private val market = RefEvent("ev-2|2026-10-07", "Farmers market", "2026-10-07", null, "PERSONAL", reminderMinutes = listOf(0))
    private val events = listOf(dentist, market)

    @Test
    fun `each lead time is a reminder, all-day events counting from 8 o'clock`() {
        val all = EventReminders.all(events)
        assertEquals(
            listOf(LocalDateTime.of(2026, 10, 5, 14, 30), LocalDateTime.of(2026, 10, 6, 13, 30), LocalDateTime.of(2026, 10, 7, 8, 0)),
            all.map { it.at },
        )
        assertEquals(listOf("e|ev-1|2026-10-06|1440", "e|ev-1|2026-10-06|60", "e|ev-2|2026-10-07|0"), all.map { it.key })
        assertEquals(2, EventReminders.upcoming(events, LocalDateTime.of(2026, 10, 5, 15, 0)).size, "the day-before reminder has passed")
    }

    @Test
    fun `a reminder is due once, and a phone told late shows only the latest`() {
        val dayBefore = LocalDateTime.of(2026, 10, 5, 14, 31)
        val first = EventReminders.due(events, dayBefore, emptySet()).single()
        assertEquals(1440, first.reminder.minutesBefore)
        assertEquals(1439, EventReminders.minutesUntil(first.reminder, dayBefore))
        assertEquals(60, EventReminders.minutesUntil(first.reminder, LocalDateTime.of(2026, 10, 6, 13, 30, 2)), "an alarm a moment late still says an hour")
        assertTrue(EventReminders.due(events, dayBefore, first.keys.toSet()).isEmpty(), "shown once")

        // Off all day: only the hour-before reminder, and both are remembered.
        val late = EventReminders.due(events, LocalDateTime.of(2026, 10, 6, 14, 0), emptySet()).single()
        assertEquals(60, late.reminder.minutesBefore)
        assertEquals(listOf("e|ev-1|2026-10-06|1440", "e|ev-1|2026-10-06|60"), late.keys)

        assertTrue(EventReminders.due(events, LocalDateTime.of(2026, 10, 6, 15, 0), emptySet()).isEmpty(), "not after the event started")
        assertEquals("Farmers market", EventReminders.due(events, LocalDateTime.of(2026, 10, 7, 8, 5), emptySet()).single().reminder.event.title, "a delayed alarm still shows")
    }

    @Test
    fun `an event with a broken date or no reminder is skipped`() {
        assertTrue(EventReminders.all(listOf(dentist.copy(date = "soon"), market.copy(reminderMinutes = emptyList()))).isEmpty())
        assertNull(EventReminders.start(dentist.copy(time = "25:99")))
    }

    @Test
    fun `refills are reminded from their reminder days, once, overdue ones too`() {
        val today = LocalDate.of(2026, 10, 5)
        val refills = listOf(
            RefRefill("m-1", "Metformin", "2026-10-09", 5, "Perry"),
            RefRefill("m-2", "Ventolin", "2026-10-30", 7),
            RefRefill("m-3", "Ramipril", "2026-10-01", 3, renewal = true),
        )
        val due = RefillReminders.due(refills, today, emptySet())
        assertEquals(listOf("Ramipril" to -4, "Metformin" to 4), due.map { it.refill.medication to it.daysLeft })
        assertTrue(RefillReminders.due(refills, today, due.map { it.key }.toSet()).isEmpty())
        assertEquals("r|m-1|2026-10-08", RefillReminders.due(listOf(refills[0].copy(dueDate = "2026-10-08")), today, due.map { it.key }.toSet()).single().key, "a new fill, a new reminder")
    }

    /** The reference data as a phone from before events kept it. */
    @Serializable
    private data class OlderReference(val householdName: String, val language: String, val baseCurrency: String, val bills: List<RefBill> = emptyList())

    @Test
    fun `events and refills are optional both ways, sealed or not`() {
        val reference = ReferenceData("H", "fr", "CAD", events = events, refills = listOf(RefRefill("m-1", "Metformin", "2026-10-09")))
        val key = ByteArray(32) { it.toByte() }
        val sealed = SyncCrypto.seal(ReferenceData.serializer(), reference, key, "desk", "phone", Direction.TO_PHONE)
        assertEquals(reference, SyncCrypto.open(ReferenceData.serializer(), sealed, key, "desk", "phone", Direction.TO_PHONE))

        val text = SyncJson.encodeToString(ReferenceData.serializer(), reference)
        assertEquals("H", SyncJson.decodeFromString(OlderReference.serializer(), text).householdName, "an older phone ignores them")
        val fromOlderDesktop = SyncJson.decodeFromString(ReferenceData.serializer(), """{"householdName":"H","language":"en","baseCurrency":"CAD"}""")
        assertEquals(emptyList(), fromOlderDesktop.events)
        assertEquals(emptyList(), fromOlderDesktop.refills)
        assertNull(ReferenceData.knownVersion("v1", storedFormat = 2), "a phone that kept an older copy asks for all of it again")
    }
}
