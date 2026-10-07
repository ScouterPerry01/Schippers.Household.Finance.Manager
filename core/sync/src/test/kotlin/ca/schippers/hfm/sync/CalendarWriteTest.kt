package ca.schippers.hfm.sync

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CSY-06: what RANN's Roost writes into the phone's calendar, and how it keeps it current. */
class CalendarWriteTest {

    private val today = LocalDate.of(2026, 10, 7)
    private val zone = ZoneId.of("America/Toronto")

    private object Texts : WriteTexts {
        override val health = "Health appointment"
        override fun bill(name: String) = "Bill due: $name"
        override fun schedule(person: String, kind: String) = "$person: ${kind.lowercase()}"
        override fun forWhom(names: String) = "For: $names"
        override fun driverThere(name: String) = "There: $name"
        override fun driverBack(name: String) = "Back: $name"
        override val footer = "Written by RANN's Roost"
    }

    /** A calendar in memory, as CalendarContract would keep it. */
    private class FakeWriter : CalendarWriter {
        val events = LinkedHashMap<Long, Pair<Long, WriteItem>>()
        var next = 100L
        var deleted = ArrayList<Long>()
        override fun insert(calendarId: Long, item: WriteItem): Long = (next++).also { events[it] = calendarId to item }
        override fun update(eventId: Long, item: WriteItem): Boolean {
            val old = events[eventId] ?: return false
            events[eventId] = old.first to item
            return true
        }
        override fun delete(eventId: Long) {
            deleted += eventId
            events.remove(eventId)
        }
    }

    private fun reference(
        events: List<RefEvent> = emptyList(),
        schedules: List<RefSchedule> = emptyList(),
        bills: List<RefBill> = emptyList(),
    ) = ReferenceData("Home", "en", "CAD", bills = bills, events = events, schedules = schedules)

    private val dentist = RefEvent("ev1|2026-10-09", "Root canal, Dr. Lee", "2026-10-09", "14:30", "MEDICAL", "12 Main St", "Sam", driverThere = "Alex")
    private val soccer = RefEvent("ev2|2026-10-10", "Soccer", "2026-10-10", "09:00", "OTHER", "Park", "Sam", driverThere = "Alex", driverBack = "Jo")
    private val hydro = RefBill("Hydro", "2026-10-15", "142.17", "CAD", false, listOf(3))

    @Test
    fun `a medical appointment is written without its details and a bill without its amount`() {
        val items = CalendarWritePlanner.desired(reference(listOf(dentist, soccer), bills = listOf(hydro)), Texts, today).associateBy { it.key }
        val health = items.getValue("e:ev1|2026-10-09")
        assertEquals("Health appointment", health.title)
        assertNull(health.location, "no place for a health appointment")
        assertEquals("For: Sam\nWritten by RANN's Roost", health.description, "no driver either, nor anything else of it")
        assertEquals(LocalTime.of(14, 30) to LocalTime.of(15, 30), health.startTime to health.endTime, "an hour long")
        val game = items.getValue("e:ev2|2026-10-10")
        assertEquals("Soccer", game.title)
        assertEquals("Park", game.location)
        assertEquals("For: Sam\nThere: Alex\nBack: Jo\nWritten by RANN's Roost", game.description)
        val bill = items.getValue("b:Hydro|2026-10-15")
        assertEquals("Bill due: Hydro", bill.title)
        assertTrue(bill.allDay)
        assertFalse(listOfNotNull(bill.title, bill.location, bill.description).any { "142" in it }, "never the amount")
    }

    @Test
    fun `hours are written per person, a night shift ending the next day`() {
        val items = CalendarWritePlanner.desired(
            reference(schedules = listOf(
                RefSchedule("Alex", "WORK", "2026-10-08", "22:00", "06:00", "Plant"),
                RefSchedule("Sam", "SCHOOL", "2026-10-08", "08:30", "15:00"),
                RefSchedule("Alex", "WORK", "2026-10-08", "07:00", "11:00"),
            )),
            Texts, today,
        )
        assertEquals(listOf("s:Alex|WORK|2026-10-08", "s:Alex|WORK|2026-10-08#2", "s:Sam|SCHOOL|2026-10-08"), items.map { it.key }, "two shifts in a day kept apart")
        val night = items[1]
        assertEquals("Alex: work", night.title)
        assertEquals(LocalDate.of(2026, 10, 9), night.endDate)
        assertEquals("Plant", night.location)
    }

    @Test
    fun `only the coming 60 days are written`() {
        val past = RefEvent("old", "Past", "2026-10-06")
        val last = RefEvent("last", "Last day", "2026-12-05")
        val beyond = RefEvent("far", "Too far", "2026-12-06")
        val items = CalendarWritePlanner.desired(reference(listOf(past, last, beyond)), Texts, today)
        assertEquals(listOf("e:last"), items.map { it.key })
        assertTrue(items.single().allDay)
        assertEquals(emptyList(), CalendarWritePlanner.desired(null, Texts, today))
    }

    @Test
    fun `changes update in place, removals delete, and nothing is done twice`() {
        val writer = FakeWriter()
        var written = emptyMap<String, WrittenEvent>()
        fun run(ref: ReferenceData): WritePlan {
            val plan = CalendarWritePlanner.plan(CalendarWritePlanner.desired(ref, Texts, today), written, today)
            written = CalendarWritePlanner.apply(plan, 7, writer, written)
            return plan
        }
        val first = run(reference(listOf(soccer), bills = listOf(hydro)))
        assertEquals(2, first.inserts.size)
        assertEquals(setOf(7L), writer.events.values.map { it.first }.toSet(), "into the chosen calendar")
        val soccerId = written.getValue("e:ev2|2026-10-10").eventId

        assertTrue(run(reference(listOf(soccer), bills = listOf(hydro))).isEmpty, "unchanged: nothing to do")

        val moved = run(reference(listOf(soccer.copy(time = "10:00")), bills = listOf(hydro)))
        assertEquals(1, moved.updates.size)
        assertEquals(soccerId, written.getValue("e:ev2|2026-10-10").eventId, "the same event, updated")
        assertEquals(LocalTime.of(10, 0), writer.events.getValue(soccerId).second.startTime)

        val paid = run(reference(listOf(soccer.copy(time = "10:00"))))
        assertEquals(setOf("b:Hydro|2026-10-15"), paid.deletes.keys)
        assertEquals(listOf(soccerId), writer.events.keys.toList())
        assertEquals(setOf("e:ev2|2026-10-10"), written.keys)
    }

    @Test
    fun `an event the user deleted is written again when it changes`() {
        val writer = FakeWriter()
        val plan = CalendarWritePlanner.plan(CalendarWritePlanner.desired(reference(listOf(soccer)), Texts, today), emptyMap(), today)
        var written = CalendarWritePlanner.apply(plan, 7, writer, emptyMap())
        writer.events.clear()
        val again = CalendarWritePlanner.plan(CalendarWritePlanner.desired(reference(listOf(soccer.copy(title = "Soccer final"))), Texts, today), written, today)
        written = CalendarWritePlanner.apply(again, 7, writer, written)
        assertEquals("Soccer final", writer.events.getValue(written.getValue("e:ev2|2026-10-10").eventId).second.title)
    }

    @Test
    fun `past items are left alone, then no longer followed`() {
        val written = mapOf(
            "e:yesterday" to WrittenEvent(1, "x", "2026-10-06"),
            "e:long-ago" to WrittenEvent(2, "x", "2026-09-01"),
            "e:cancelled" to WrittenEvent(3, "x", "2026-10-20"),
        )
        val plan = CalendarWritePlanner.plan(emptyList(), written, today)
        assertEquals(setOf("e:cancelled"), plan.deletes.keys)
        assertEquals(setOf("e:long-ago"), plan.forget)
        val writer = FakeWriter()
        assertEquals(setOf("e:yesterday"), CalendarWritePlanner.apply(plan, 7, writer, written).keys)
        assertEquals(listOf(3L), writer.deleted)
    }

    @Test
    fun `turning off, unpairing or another calendar removes what was written`() {
        val target = WriteTarget(phoneOnly = false, calendarId = 7, name = "Family", accountName = "alex@gmail.com")
        val writing = CalendarShareSettings(chosen = emptyList()).withMode(CalendarSyncMode.BOTH_WAYS)
            .copy(writeTarget = target, written = mapOf("e:x" to WrittenEvent(100, "p", "2026-10-09")), writtenCalendarId = 7)
        assertTrue(CalendarWritePlanner.wanted(writing, paired = true))
        assertFalse(CalendarWritePlanner.mustRemove(writing, paired = true, calendarId = 7))
        assertTrue(CalendarWritePlanner.mustRemove(writing.withMode(CalendarSyncMode.BRING_IN), paired = true, calendarId = 7), "both ways turned off")
        assertTrue(CalendarWritePlanner.mustRemove(writing, paired = false, calendarId = 7), "unpaired")
        assertTrue(CalendarWritePlanner.mustRemove(writing.copy(writeTarget = target.copy(calendarId = 8)), paired = true, calendarId = 8), "another calendar")
        assertTrue(CalendarWritePlanner.mustRemove(writing.copy(writeTarget = WriteTarget(phoneOnly = true)), paired = true, calendarId = null), "the phone-only calendar, not made yet")
        val phoneOnlyBefore = writing.copy(localCalendarId = 5, writtenCalendarId = 5)
        assertTrue(CalendarWritePlanner.mustRemove(phoneOnlyBefore, paired = true, calendarId = 7), "the phone-only calendar goes once an account is chosen")
        assertFalse(CalendarWritePlanner.mustRemove(CalendarShareSettings().withMode(CalendarSyncMode.OFF), paired = true, calendarId = null), "nothing written, nothing to remove")
        val cleared = CalendarWritePlanner.cleared(phoneOnlyBefore)
        assertEquals(emptyMap(), cleared.written)
        assertNull(cleared.localCalendarId)
        assertNull(cleared.writtenCalendarId)

        val writer = FakeWriter()
        writer.events[100] = 7L to WriteItem("e:x", "x", today, today)
        CalendarWritePlanner.removeAll(writing.written, writer)
        assertTrue(writer.events.isEmpty())
    }

    @Test
    fun `the mode defaults to bring in only, and a phone that had turned bringing in off stays off`() {
        assertEquals(CalendarSyncMode.BRING_IN, CalendarShareSettings().syncMode)
        assertEquals(CalendarSyncMode.BRING_IN, CalendarShareSettings(enabled = true, chosen = listOf(ChosenCalendar(1, "Work"))).syncMode)
        assertEquals(CalendarSyncMode.OFF, CalendarShareSettings(enabled = false, chosen = listOf(ChosenCalendar(1, "Work"))).syncMode)
        val both = CalendarShareSettings().withMode(CalendarSyncMode.BOTH_WAYS)
        assertTrue(both.bringsIn && both.writes && both.enabled)
        assertFalse(CalendarShareSettings().withMode(CalendarSyncMode.OFF).enabled)
    }

    @Test
    fun `items written by RANN's Roost are never brought back in`() {
        val zoneMillis = { d: Int, h: Int -> LocalDateTime.of(2026, 10, d, h, 0).atZone(zone).toInstant().toEpochMilli() }
        val source = object : CalendarSource {
            override fun calendars() = listOf(DeviceCalendar(7, "Family"), DeviceCalendar(5, "RANN's Roost"))
            override fun instances(calendarIds: Set<Long>, fromMillis: Long, toMillis: Long) = listOf(
                DeviceInstance(7, 100, "Soccer", "Park", zoneMillis(10, 9), zoneMillis(10, 10), false),
                DeviceInstance(7, 101, "Dinner", null, zoneMillis(10, 18), zoneMillis(10, 19), false),
                DeviceInstance(5, 200, "Bill due: Hydro", null, zoneMillis(15, 0), zoneMillis(16, 0), false),
            ).filter { it.calendarId in calendarIds }
        }
        val settings = CalendarShareSettings(chosen = listOf(ChosenCalendar(7, "Family"), ChosenCalendar(5, "RANN's Roost")))
            .withMode(CalendarSyncMode.BOTH_WAYS)
            .copy(written = mapOf("e:ev2" to WrittenEvent(100, "p", "2026-10-10")), localCalendarId = 5)
        val now = LocalDateTime.of(2026, 10, 7, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val sent = CalendarShare.refresh(settings, source, now, zone) { "id" }
        assertEquals(listOf("7"), sent.pending.map { it.calendarKey }, "the phone-only calendar is never brought in")
        assertEquals(listOf("Dinner"), sent.pending.single().items.map { it.title }, "nor what was written into an account calendar")
        assertEquals(listOf("Dinner"), CalendarShare.upcoming(settings, source, now, zone).map { it.second.title })
    }

    @Test
    fun `times are stored as Android expects them`() {
        val allDay = CalendarWritePlanner.times(WriteItem("b", "Bill", LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15)), zone)
        assertEquals(LocalDate.of(2026, 10, 15).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), allDay.startMillis)
        assertEquals(24L * 3600 * 1000, allDay.endMillis - allDay.startMillis)
        assertEquals("UTC", allDay.timeZone)
        val night = CalendarWritePlanner.times(WriteItem("s", "Alex", LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 9), LocalTime.of(22, 0), LocalTime.of(6, 0)), zone)
        assertEquals(8L * 3600 * 1000, night.endMillis - night.startMillis)
        assertEquals("America/Toronto", night.timeZone)
        assertFalse(night.allDay)
    }
}
