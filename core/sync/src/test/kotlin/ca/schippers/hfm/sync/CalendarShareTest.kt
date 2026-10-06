package ca.schippers.hfm.sync

import ca.schippers.hfm.security.KeyPair
import ca.schippers.hfm.security.PairKey
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** CSY-01 to CSY-03 on the phone: what is read from the phone's calendars and when a calendar is sent again. */
class CalendarShareTest {

    private val zone = ZoneId.of("America/Toronto")
    private val now = LocalDateTime.of(2026, 10, 5, 12, 0).atZone(zone).toInstant().toEpochMilli()
    private var ids = 0
    private val newId = { "s${++ids}" }

    private fun at(day: Int, hour: Int, minute: Int = 0, month: Int = 10) = LocalDateTime.of(2026, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
    private fun utcMidnight(day: Int, month: Int = 10) = LocalDateTime.of(2026, month, day, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    /** A phone with two calendars; [rows] is what CalendarContract.Instances would answer. */
    private class FakeSource(var rows: List<DeviceInstance>) : CalendarSource {
        val asked = ArrayList<Triple<Set<Long>, Long, Long>>()
        override fun calendars() = listOf(DeviceCalendar(1, "Work", "alex@work.ca", 0x3366cc), DeviceCalendar(2, "Family", "alex@gmail.com"))
        override fun instances(calendarIds: Set<Long>, fromMillis: Long, toMillis: Long): List<DeviceInstance> {
            asked += Triple(calendarIds, fromMillis, toMillis)
            return rows.filter { it.calendarId in calendarIds && it.endMillis > fromMillis && it.beginMillis < toMillis }
        }
    }

    private fun rows() = listOf(
        DeviceInstance(1, 10, "Budget meeting", "Room 4", at(7, 10), at(7, 11, 30), false),
        DeviceInstance(1, 11, "Stand-up", null, at(6, 9), at(6, 9, 15), false),
        DeviceInstance(1, 11, "Stand-up", null, at(8, 9), at(8, 9, 15), false),
        DeviceInstance(2, 20, "Cottage", "Lake", utcMidnight(9), utcMidnight(12), true),
        DeviceInstance(1, 12, "Night shift", null, at(10, 22), at(11, 6), false),
    )

    private fun settings(vararg chosen: ChosenCalendar) = CalendarShareSettings(enabled = true, chosen = chosen.toList())

    @Test
    fun `an occurrence is read in the phone's time zone, an all-day one by its days`() {
        val timed = CalendarShare.instance(rows()[0], zone)!!
        assertEquals(CalendarInstance("10", "2026-10-07", "2026-10-07", "10:00", "11:30", "Budget meeting", "Room 4"), timed)
        val allDay = CalendarShare.instance(rows()[3], zone)!!
        assertEquals(CalendarInstance("20", "2026-10-09", "2026-10-11", title = "Cottage", location = "Lake"), allDay, "the end is the midnight after the last day")
        val overnight = CalendarShare.instance(rows()[4], zone)!!
        assertEquals("2026-10-10" to "2026-10-11", overnight.startDate to overnight.endDate)
        val oneDay = CalendarShare.instance(DeviceInstance(2, 21, "Holiday", null, utcMidnight(12), utcMidnight(13), true), zone)!!
        assertEquals("2026-10-12" to "2026-10-12", oneDay.startDate to oneDay.endDate)
        val marked = CalendarShare.instance(rows()[1], zone, mapOf("11" to CalendarVisibility.SHARED))!!
        assertEquals(CalendarVisibility.SHARED, marked.visibility, "a single item's choice goes with each of its dates")
    }

    @Test
    fun `each chosen calendar is sent whole once, then only when it changes`() {
        val source = FakeSource(rows())
        val chosen = settings(ChosenCalendar(1, "Work", "alex@work.ca", visibility = CalendarVisibility.BUSY), ChosenCalendar(2, "Family"))
        val first = CalendarShare.refresh(chosen, source, now, zone, newId)
        assertEquals(listOf("1", "2"), first.pending.map { it.calendarKey })
        val work = first.pending.first()
        assertEquals(CalendarVisibility.BUSY, work.visibility)
        assertEquals("2026-10-05" to "2026-12-04", work.fromDate to work.toDate, "60 days ahead by default")
        assertEquals(listOf("Stand-up", "Budget meeting", "Stand-up", "Night shift"), work.items.map { it.title }, "in date order, repeats expanded")
        assertEquals(setOf(1L, 2L), source.asked.single().first)

        val again = CalendarShare.refresh(first.acknowledged(first.pending.map { it.id }), source, now + 3_600_000, zone, newId)
        assertTrue(again.pending.isEmpty(), "nothing changed, nothing sent")

        source.rows = rows().filterNot { it.title == "Budget meeting" }
        val changed = CalendarShare.refresh(again, source, now + 7_200_000, zone, newId)
        assertEquals(listOf("1"), changed.pending.map { it.calendarKey }, "only the calendar that changed")
        assertTrue(changed.pending.single().items.none { it.title == "Budget meeting" }, "a deleted item is gone from the new copy")
    }

    @Test
    fun `a newer copy replaces one still waiting, and a calendar no longer chosen is removed`() {
        val source = FakeSource(rows())
        val first = CalendarShare.refresh(settings(ChosenCalendar(1, "Work"), ChosenCalendar(2, "Family")), source, now, zone, newId)
        source.rows = rows().dropLast(1)
        val second = CalendarShare.refresh(first, source, now + 1000, zone, newId)
        assertEquals(2, second.pending.size, "one copy per calendar waits")
        assertTrue(second.pending.single { it.calendarKey == "1" }.items.none { it.title == "Night shift" })

        val dropped = CalendarShare.refresh(second.copy(chosen = second.chosen.filter { it.id == 1L }), source, now + 2000, zone, newId)
        val removal = dropped.pending.single { it.calendarKey == "2" }
        assertTrue(removal.removed && removal.items.isEmpty())
        assertEquals(setOf("1"), dropped.sent.keys)

        val off = CalendarShare.refresh(dropped.copy(enabled = false), source, now + 3000, zone, newId)
        assertTrue(off.pending.all { it.removed } && off.pending.map { it.calendarKey }.toSet() == setOf("1", "2"), "turned off: every calendar is removed")
        assertTrue(off.sent.isEmpty())
    }

    @Test
    fun `a refused copy is made again at the next transfer`() {
        val source = FakeSource(rows())
        val first = CalendarShare.refresh(settings(ChosenCalendar(2, "Family")), source, now, zone, newId)
        val failed = first.failed(first.pending.single().id, "No group")
        assertTrue(failed.pending.isEmpty())
        assertEquals(mapOf("2" to "No group"), failed.failures)
        val retried = CalendarShare.refresh(failed, source, now + 1000, zone, newId)
        assertEquals(1, retried.pending.size)
        assertTrue(retried.acknowledged(retried.pending.map { it.id }).failures.isEmpty(), "the reason goes once it is stored")
    }

    @Test
    fun `the list of coming items shows every chosen calendar's occurrences in order`() {
        val list = CalendarShare.upcoming(settings(ChosenCalendar(1, "Work"), ChosenCalendar(2, "Family")).copy(daysAhead = 5), FakeSource(rows()), now, zone)
        assertEquals(listOf("Stand-up", "Budget meeting", "Stand-up", "Cottage"), list.map { it.second.title }, "within 5 days")
        assertEquals("Family", list.last().first.name)
    }

    @Test
    fun `calendars travel sealed in the request and an older desktop ignores them`() {
        val phone = KeyPair.generate()
        val desktop = KeyPair.generate()
        val key = PairKey.derive(phone, desktop.publicKey, desktop.publicKey, phone.publicKey)
        val snapshot = CalendarShare.refresh(settings(ChosenCalendar(2, "Family", visibility = CalendarVisibility.SHARED)), FakeSource(rows()), now, zone, newId).pending.single()
        val request = SyncRequest(now, emptyList(), calendars = listOf(snapshot))
        val sealed = SyncCrypto.seal(SyncRequest.serializer(), request, key, "desk", "phone", Direction.TO_DESKTOP)
        assertEquals(request, SyncCrypto.open(SyncRequest.serializer(), sealed, key, "desk", "phone", Direction.TO_DESKTOP))
        val text = SyncJson.encodeToString(SyncRequest.serializer(), request)
        val old = SyncJson.decodeFromString(OldRequest.serializer(), text)
        assertEquals(now, old.sentAtMillis)
    }

    @kotlinx.serialization.Serializable
    private data class OldRequest(val sentAtMillis: Long, val items: List<CaptureItem>)
}
