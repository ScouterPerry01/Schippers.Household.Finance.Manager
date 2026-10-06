package ca.schippers.hfm.importers

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CSY-05: reading .ics files, and the dates their repeats fall on. */
class ICalendarTest {

    private val zone = ZoneId.of("America/Toronto")

    private fun ics(vararg events: String) =
        (listOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Test//EN") + events.flatMap { ("BEGIN:VEVENT\n$it\nEND:VEVENT").lines() } + "END:VCALENDAR")
            .joinToString("\r\n").encodeToByteArray()

    private fun d(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    private fun one(vararg lines: String) = ICalendar.parse(ics(lines.joinToString("\n")), zone).events.single()

    @Test
    fun `a timed event is read in the computer's time zone, with folded and escaped text`() {
        val e = one(
            "UID:abc@example.com",
            "DTSTART:20261007T140000Z",
            "DTEND:20261007T153000Z",
            "SUMMARY:Budget review\\, Q4",
            "LOCATION:Room 4\\; 2nd floor",
            "DESCRIPTION:Bring the\\nfigures and a long",
            "  description folded",
        )
        assertEquals("Budget review, Q4", e.title)
        assertEquals("Room 4; 2nd floor", e.location)
        assertEquals("Bring the\nfigures and a long description folded", e.description)
        assertEquals(d(10, 7) to LocalTime.of(10, 0), e.startDate to e.startTime, "14:00 UTC is 10:00 in Toronto")
        assertEquals(90L, e.durationMinutes)
        assertEquals("abc@example.com", e.uid)
    }

    @Test
    fun `time zones by name, floating times, durations and all-day events`() {
        val vancouver = one("DTSTART;TZID=America/Vancouver:20261007T090000", "DURATION:PT45M", "SUMMARY:Call")
        assertEquals(LocalTime.of(12, 0), vancouver.startTime)
        assertEquals(45L, vancouver.durationMinutes)
        val floating = one("DTSTART;TZID=Eastern Standard Time:20261007T090000", "SUMMARY:Unknown zone")
        assertEquals(LocalTime.of(9, 0), floating.startTime, "a zone Java does not know is taken as the computer's")
        val cottage = one("DTSTART;VALUE=DATE:20261009", "DTEND;VALUE=DATE:20261012", "SUMMARY:Cottage")
        assertTrue(cottage.allDay)
        assertEquals(d(10, 9) to d(10, 11), cottage.startDate to cottage.endDate, "DTEND is the day after the last")
        val single = one("DTSTART;VALUE=DATE:20261012", "SUMMARY:Thanksgiving")
        assertEquals(d(10, 12), single.endDate)
    }

    @Test
    fun `cancelled events and events without a date are left out, with a note for the latter`() {
        val cal = ICalendar.parse(ics("SUMMARY:No date", "DTSTART:20261007T090000\nSUMMARY:Gone\nSTATUS:CANCELLED", "DTSTART:20261008T090000\nSUMMARY:Kept"), zone)
        assertEquals(listOf("Kept"), cal.events.map { it.title })
        assertEquals(listOf("importNote.icsNoDate"), cal.notes.map { it.key })
    }

    @Test
    fun `alarms inside an event do not change it`() {
        val e = one("DTSTART:20261007T090000", "SUMMARY:Dentist", "BEGIN:VALARM", "TRIGGER:-PT15M", "DESCRIPTION:Alarm text", "END:VALARM")
        assertEquals("Dentist", e.title)
        assertNull(e.description)
    }

    @Test
    fun `weekly on two days with a count`() {
        val e = one("DTSTART:20261005T180000", "SUMMARY:Practice", "RRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=5")
        assertEquals(listOf(d(10, 5), d(10, 7), d(10, 12), d(10, 14), d(10, 19)), ICalendar.occurrences(e, d(1, 1), d(12, 31)))
    }

    @Test
    fun `every other week until a date, with a date left out`() {
        val e = one("DTSTART:20261001T090000", "SUMMARY:Pay day", "RRULE:FREQ=WEEKLY;INTERVAL=2;UNTIL=20261126T235959Z", "EXDATE:20261029T090000")
        assertEquals(listOf(d(10, 1), d(10, 15), d(11, 12), d(11, 26)), ICalendar.occurrences(e, d(1, 1), d(12, 31)))
        assertEquals(setOf(d(10, 29)), e.exDates)
    }

    @Test
    fun `monthly by rank, by last day and on the 31st`() {
        val second = one("DTSTART:20261013T190000", "SUMMARY:Council", "RRULE:FREQ=MONTHLY;BYDAY=2TU;COUNT=3")
        assertEquals(listOf(d(10, 13), d(11, 10), d(12, 8)), ICalendar.occurrences(second, d(1, 1), d(12, 31, 2027)))
        val lastFriday = one("DTSTART:20261030T120000", "SUMMARY:Lunch", "RRULE:FREQ=MONTHLY;BYDAY=-1FR")
        assertEquals(listOf(d(10, 30), d(11, 27), d(12, 25)), ICalendar.occurrences(lastFriday, d(1, 1), d(12, 31)))
        val lastDay = one("DTSTART:20261031", "SUMMARY:Rent", "RRULE:FREQ=MONTHLY;BYMONTHDAY=-1")
        assertEquals(listOf(d(10, 31), d(11, 30), d(12, 31), d(1, 31, 2027), d(2, 28, 2027)), ICalendar.occurrences(lastDay, d(1, 1), d(3, 1, 2027)))
        val the31st = one("DTSTART:20261031", "SUMMARY:Thirty-first", "RRULE:FREQ=MONTHLY")
        assertEquals(listOf(d(10, 31), d(12, 31), d(1, 31, 2027)), ICalendar.occurrences(the31st, d(1, 1), d(2, 28, 2027)), "months without the day are skipped")
    }

    @Test
    fun `yearly and daily repeats, read from a window`() {
        val birthday = one("DTSTART;VALUE=DATE:19800314", "SUMMARY:Birthday", "RRULE:FREQ=YEARLY")
        assertEquals(listOf(d(3, 14, 2026), d(3, 14, 2027)), ICalendar.occurrences(birthday, d(1, 1, 2026), d(12, 31, 2027)))
        val thanksgiving = one("DTSTART;VALUE=DATE:20261012", "SUMMARY:Thanksgiving", "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=2MO")
        assertEquals(listOf(d(10, 12), d(10, 11, 2027)), ICalendar.occurrences(thanksgiving, d(1, 1), d(12, 31, 2027)))
        val weekdays = one("DTSTART:20261009T080000", "SUMMARY:Bus", "RRULE:FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR;COUNT=3")
        assertEquals(listOf(d(10, 9), d(10, 12), d(10, 13)), ICalendar.occurrences(weekdays, d(1, 1), d(12, 31)))
        val everyThird = one("DTSTART:20261001T080000", "SUMMARY:Water plants", "RRULE:FREQ=DAILY;INTERVAL=3")
        assertEquals(listOf(d(10, 1), d(10, 4), d(10, 7)), ICalendar.occurrences(everyThird, d(1, 1), d(10, 8)))
        assertEquals(DayOfWeek.MONDAY, thanksgiving.rule!!.byDay.single().day)
    }

    @Test
    fun `repeats not understood are skipped and named`() {
        val cal = ICalendar.parse(
            ics(
                "DTSTART:20261001T080000\nSUMMARY:Hourly\nRRULE:FREQ=HOURLY",
                "DTSTART:20261001T080000\nSUMMARY:Set position\nRRULE:FREQ=MONTHLY;BYDAY=MO,TU;BYSETPOS=-1",
                "DTSTART:20261001T080000\nSUMMARY:Rank weekly\nRRULE:FREQ=WEEKLY;BYDAY=2MO",
                "DTSTART:20261001T080000\nSUMMARY:Fine\nRRULE:FREQ=WEEKLY",
            ),
            zone,
        )
        assertEquals(listOf("Fine"), cal.events.map { it.title })
        assertEquals(listOf("Hourly", "Set position", "Rank weekly"), cal.notes.map { it.args.single() })
        assertTrue(cal.notes.all { it.key == "importNote.icsRule" })
    }

    @Test
    fun `a changed occurrence keeps its series id and date`() {
        val cal = ICalendar.parse(
            ics(
                "UID:s1\nDTSTART:20261005T180000\nSUMMARY:Practice\nRRULE:FREQ=WEEKLY",
                "UID:s1\nRECURRENCE-ID:20261012T180000\nDTSTART:20261013T190000\nSUMMARY:Practice (moved)",
            ),
            zone,
        )
        assertEquals(d(10, 12), cal.events[1].recurrenceId)
        assertEquals(d(10, 13), cal.events[1].startDate)
    }

    @Test
    fun `a file from outside is limited`() {
        assertFailsWith<IllegalArgumentException> { ICalendar.parse(ByteArray(ICalendar.MAX_BYTES + 1) { 'A'.code.toByte() }, zone) }
        assertFailsWith<IllegalArgumentException> { ICalendar.parse("hello".encodeToByteArray(), zone) }
        val many = ICalendar.parse(ics(*Array(ICalendar.MAX_EVENTS + 3) { "DTSTART:20261001T080000\nSUMMARY:E$it" }), zone)
        assertEquals(ICalendar.MAX_EVENTS, many.events.size)
        assertEquals("importNote.icsTooMany", many.notes.single().key)
        val long = one("DTSTART:20261001T080000", "SUMMARY:" + "x".repeat(10_000))
        assertEquals(ICalendar.MAX_TEXT, long.title.length)
        val endless = one("DTSTART:20261001T080000", "SUMMARY:Forever", "RRULE:FREQ=DAILY")
        assertEquals(1000, ICalendar.occurrences(endless, d(1, 1), d(1, 1, 2100)).size, "at most the limit")
    }
}
