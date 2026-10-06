package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.ocr.OcrResult
import ca.schippers.hfm.security.KdfParams
import ca.schippers.hfm.security.KeyPair
import ca.schippers.hfm.security.PairKey
import ca.schippers.hfm.sync.CalendarInstance
import ca.schippers.hfm.sync.CalendarSnapshot
import ca.schippers.hfm.sync.CalendarVisibility
import ca.schippers.hfm.sync.Direction
import ca.schippers.hfm.sync.PairRequest
import ca.schippers.hfm.sync.SyncCrypto
import ca.schippers.hfm.sync.SyncRequest
import ca.schippers.hfm.sync.SyncResponse
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CSY-02 to CSY-04: calendars brought in from a phone, who sees what, and their place in the calendar. */
class BroughtInCalendarTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private val dir get() = temp.resolve("C.hfm")
    private val today = LocalDate(2026, 10, 5)
    private val now = java.time.LocalDate.of(2026, 10, 5).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val clock = { now }
    private val noConversion = object : CaptureConverter {
        override fun pagesToPdf(pages: List<ByteArray>) = ByteArray(0)
        override fun recognize(content: ByteArray): OcrResult? = null
    }

    private fun household(): Books = Books(store.create(dir, "C", "alex", "Alex", "password1".toCharArray()).session, clock)
    private fun alex() = Books(store.unlock(dir, "alex", "password1".toCharArray()), clock)
    private fun sam() = Books(store.unlock(dir, "sam", "password2-long".toCharArray()), clock)

    private fun Books.use(block: (Books) -> Unit) = try { block(this) } finally { session.close() }

    private fun d(day: Int) = LocalDate(2026, 10, day)

    /** Pairs a phone with [books] and returns a function that sends a request and opens the answer. */
    private fun pair(books: Books): (SyncRequest) -> SyncResponse {
        val phone = KeyPair.generate()
        val invitation = books.sync.invitation("Desk", "127.0.0.1", 47311, now)
        val desktopKey = SyncCrypto.unb64(invitation.desktopPublicKey)
        books.sync.pair(PairRequest("phone-1", "Pixel", SyncCrypto.b64(phone.publicKey), SyncCrypto.phoneProof(invitation.oneTimeCode, phone.publicKey, desktopKey)), now)
        val key = PairKey.derive(phone, desktopKey, desktopKey, phone.publicKey)
        return { request ->
            val sealed = SyncCrypto.seal(SyncRequest.serializer(), request, key, books.sync.desktopId, "phone-1", Direction.TO_DESKTOP)
            SyncCrypto.open(SyncResponse.serializer(), books.sync.handle("phone-1", sealed, noConversion, now, today), key, books.sync.desktopId, "phone-1", Direction.TO_PHONE)
        }
    }

    private fun work(id: String, takenAt: Long, items: List<CalendarInstance>) =
        CalendarSnapshot(id, "7", takenAt, "Work", "alex@work.ca", 0x3366cc, CalendarVisibility.BUSY, "2026-10-05", "2026-12-04", items)

    private val budget = CalendarInstance("10", "2026-10-07", "2026-10-07", "10:00", "11:30", "Budget meeting", "Room 4")
    private val lunch = CalendarInstance("11", "2026-10-08", "2026-10-08", "12:00", "13:00", "Team lunch", "Bistro", CalendarVisibility.SHARED)
    private val doctor = CalendarInstance("12", "2026-10-09", "2026-10-09", "08:00", "08:30", "Doctor", "Clinic", CalendarVisibility.PRIVATE)
    private val conference = CalendarInstance("13", "2026-10-14", "2026-10-16", title = "Conference")

    private fun setUp(): String {
        var shared = ""
        household().use { books ->
            shared = books.groups().single().id
            val sam = books.users.add("sam", "Sam", Role.MEMBER, "password2-long".toCharArray()).userId
            books.session.setPermission(shared, sam, PermissionLevel.VIEW)
        }
        return shared
    }

    @Test
    fun `a busy-only calendar shows its owner everything and others only the times`() {
        val shared = setUp()
        alex().use { a ->
            val send = pair(a)
            assertTrue(a.groups().none { it.isPrivate }, "no private group yet")
            val response = send(SyncRequest(now, emptyList(), calendars = listOf(work("s1", now, listOf(budget, lunch, doctor, conference)))))
            assertEquals(listOf("s1"), response.imported, "the snapshot is acknowledged like a capture")
            val private = a.groups().single { it.isPrivate && it.ownerUserId == a.userId }

            val mine = a.broughtIn.items(d(5), d(31))
            assertEquals(listOf("Budget meeting", "Team lunch", "Doctor", "Conference"), mine.map { it.title }, "the owner sees each once, in full")
            val b = mine.first()
            assertEquals(LocalTime(10, 0) to LocalTime(11, 30), b.startTime to b.endTime)
            assertEquals("Work" to "Alex", b.sourceName to b.ownerName)

            // What a ledger others can open holds: no title or place of a busy-only item, no private item at all.
            val q = a.ledger(a.group(shared)).broughtInCalendarQueries
            val forOthers = q.broughtInItemsBetween("2026-12-31", "2026-01-01").executeAsList()
            assertEquals(setOf("10", "11", "13"), forOthers.map { it.event_id }.toSet())
            assertTrue(forOthers.filter { it.visibility == "BUSY" }.all { it.title == null && it.location == null })
            assertNull(q.broughtInCalendars().executeAsList().single().source_name, "the calendar's name stays private unless it is shared")
            val inPrivate = a.ledger(private).broughtInCalendarQueries.broughtInItemsBetween("2026-12-31", "2026-01-01").executeAsList()
            assertEquals(setOf("10", "12", "13"), inPrivate.map { it.event_id }.toSet(), "private and busy-only items in full")

            val calendar = a.broughtIn.calendars().single()
            assertEquals(4, calendar.items, "each item once")
            assertEquals(shared, calendar.othersGroupId)
            assertEquals(CalendarVisibility.BUSY, calendar.visibility)
        }
        sam().use { s ->
            val seen = s.broughtIn.items(d(5), d(31))
            assertEquals(listOf(null, "Team lunch", null), seen.map { it.title }, "busy, shared, busy; never the doctor")
            assertTrue(seen.first().busyOnly && seen.first().ownerName == "Alex")
            assertNull(seen.first().location)
            assertTrue(s.broughtIn.calendars().isEmpty(), "only the owner manages a calendar")
            val items = s.calendar.items(d(5), d(31)).filterIsInstance<CalendarItem.Imported>()
            assertEquals(listOf(d(7), d(8), d(14), d(15), d(16)), items.map { it.date }, "a three-day item shows on each day")
            assertTrue(items.all { it.kind == CalendarKind.IMPORTED })
        }
    }

    @Test
    fun `a newer copy replaces the items, an older one is ignored, and a removal deletes the calendar`() {
        setUp()
        alex().use { a ->
            val send = pair(a)
            send(SyncRequest(now, emptyList(), calendars = listOf(work("s1", now, listOf(budget, lunch)))))
            val moved = budget.copy(startTime = "14:00", endTime = "15:00")
            send(SyncRequest(now, emptyList(), calendars = listOf(work("s2", now + 1000, listOf(moved)))))
            assertEquals(listOf(LocalTime(14, 0)), a.broughtIn.items(d(5), d(31)).map { it.startTime }, "the moved meeting, and the deleted lunch gone")
            val late = send(SyncRequest(now, emptyList(), calendars = listOf(work("s0", now - 1000, listOf(budget, lunch)))))
            assertEquals(listOf("s0"), late.imported, "an older copy is acknowledged")
            assertEquals(1, a.broughtIn.items(d(5), d(31)).size, "but changes nothing")
            send(SyncRequest(now, emptyList(), calendars = listOf(CalendarSnapshot("s3", "7", now + 2000, removed = true))))
            assertTrue(a.broughtIn.items(d(5), d(31)).isEmpty())
            assertTrue(a.broughtIn.calendars().isEmpty())
            assertTrue(a.groups().all { g -> a.ledger(g).broughtInCalendarQueries.broughtInCalendars().executeAsList().isEmpty() }, "in every group")
        }
    }

    @Test
    fun `a private calendar leaves no trace where others can read`() {
        val shared = setUp()
        alex().use { a ->
            val private = work("s1", now, listOf(budget, conference)).copy(visibility = CalendarVisibility.PRIVATE)
            pair(a)(SyncRequest(now, emptyList(), calendars = listOf(private)))
            assertTrue(a.ledger(a.group(shared)).broughtInCalendarQueries.broughtInCalendars().executeAsList().isEmpty())
            assertEquals(2, a.broughtIn.items(d(5), d(31)).size)
        }
        sam().use { assertTrue(it.broughtIn.items(d(5), d(31)).isEmpty()) }
    }

    @Test
    fun `a calendar made less visible takes its past items along`() {
        val shared = setUp()
        alex().use { a ->
            val send = pair(a)
            // Sent a week ago, shared: a lunch now past, a meeting still to come.
            val past = CalendarInstance("20", "2026-09-29", "2026-09-29", "12:00", "13:00", "Lunch with Secretname", "Bistro")
            val first = work("s1", now - 7_000, listOf(past, budget)).copy(visibility = CalendarVisibility.SHARED, fromDate = "2026-09-28")
            send(SyncRequest(now, emptyList(), calendars = listOf(first)))
            assertEquals("Lunch with Secretname", a.ledger(a.group(shared)).broughtInCalendarQueries.broughtInItemsOf(a.broughtIn.calendars().single().id).executeAsList().first().title)

            // Now busy only: the phone sends only what is to come, and the past lunch keeps only its times for others.
            send(SyncRequest(now, emptyList(), calendars = listOf(work("s2", now - 5_000, listOf(budget)))))
            val forOthers = a.ledger(a.group(shared)).broughtInCalendarQueries.broughtInItemsBetween("2026-12-31", "2026-01-01").executeAsList()
            assertEquals(setOf("20", "10"), forOthers.map { it.event_id }.toSet())
            assertTrue(forOthers.all { it.title == null && it.location == null && it.visibility == "BUSY" }, "$forOthers")
            assertNull(a.ledger(a.group(shared)).broughtInCalendarQueries.broughtInCalendars().executeAsList().single().source_name)

            // Then private: nothing at all is left where others can read; the owner keeps it all.
            send(SyncRequest(now, emptyList(), calendars = listOf(work("s3", now - 3_000, listOf(budget)).copy(visibility = CalendarVisibility.PRIVATE))))
            assertTrue(a.ledger(a.group(shared)).broughtInCalendarQueries.broughtInCalendars().executeAsList().isEmpty())
            assertEquals(listOf("Lunch with Secretname", "Budget meeting"), a.broughtIn.items(LocalDate(2026, 9, 1), d(31)).map { it.title })
        }
        sam().use { assertTrue(it.broughtIn.items(LocalDate(2026, 9, 1), d(31)).isEmpty()) }
    }

    @Test
    fun `an item with an overlong id is left out`() {
        setUp()
        alex().use { a ->
            pair(a)(SyncRequest(now, emptyList(), calendars = listOf(work("s1", now, listOf(budget, budget.copy(eventId = "9".repeat(5000)))))))
            assertEquals(listOf("Budget meeting"), a.broughtIn.items(d(5), d(31)).map { it.title })
        }
    }

    @Test
    fun `the owner can keep a calendar to themselves or remove it`() {
        val shared = setUp()
        alex().use { a ->
            pair(a)(SyncRequest(now, emptyList(), calendars = listOf(work("s1", now, listOf(budget, lunch, doctor)))))
            val c = a.broughtIn.calendars().single()
            a.broughtIn.setOthersGroup(c.id, c.privateGroupId)
            assertTrue(a.ledger(a.group(shared)).broughtInCalendarQueries.broughtInCalendars().executeAsList().isEmpty(), "nothing left for others")
            assertEquals(listOf("Budget meeting", "Team lunch", "Doctor"), a.broughtIn.items(d(5), d(31)).map { it.title }, "all kept for the owner")
        }
        sam().use { assertTrue(it.broughtIn.items(d(5), d(31)).isEmpty()) }
        alex().use { a ->
            val c = a.broughtIn.calendars().single()
            a.broughtIn.setOthersGroup(c.id, shared)
            assertEquals(2, a.ledger(a.group(shared)).broughtInCalendarQueries.broughtInItemsOf(c.id).executeAsList().size, "the shared and busy ones go back")
            a.broughtIn.remove(c.id)
            assertTrue(a.broughtIn.items(d(5), d(31)).isEmpty())
        }
    }

    @Test
    fun `an ics file becomes ordinary appointments`() {
        val shared = setUp()
        val file = listOf(
            "BEGIN:VCALENDAR", "VERSION:2.0",
            "BEGIN:VEVENT", "UID:p1", "DTSTART:20261005T180000", "DTEND:20261005T193000", "SUMMARY:Practice", "LOCATION:Arena",
            "RRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=6", "EXDATE:20261012T180000", "END:VEVENT",
            "BEGIN:VEVENT", "UID:p1", "RECURRENCE-ID:20261014T180000", "DTSTART:20261015T180000", "DTEND:20261015T193000", "SUMMARY:Practice (moved)", "END:VEVENT",
            "BEGIN:VEVENT", "UID:c1", "DTSTART:20261013T190000", "SUMMARY:Council", "RRULE:FREQ=MONTHLY;BYDAY=2TU;COUNT=3", "END:VEVENT",
            "BEGIN:VEVENT", "DTSTART;VALUE=DATE:20261009", "DTEND;VALUE=DATE:20261012", "SUMMARY:Cottage", "END:VEVENT",
            "BEGIN:VEVENT", "DTSTART:20261001T080000", "SUMMARY:Odd", "RRULE:FREQ=MINUTELY", "END:VEVENT",
            "END:VCALENDAR",
        ).joinToString("\r\n").encodeToByteArray()
        alex().use { a ->
            val result = a.icsImport.import(shared, file, ZoneId.systemDefault())
            assertEquals(4, result.created, "two weekly series (Mondays, Wednesdays), the moved one, the cottage")
            assertEquals(3, result.expanded, "the second Tuesdays, copied date by date")
            assertTrue(result.notes.any { "Odd" in it } && result.notes.any { "Council" in it })
            val events = a.calendar.list()
            assertTrue(events.all { it.reminderMinutes.isEmpty() && it.groupId == shared })
            val dates = a.calendar.occurrences(d(1), LocalDate(2026, 12, 31)).filter { it.event.title.startsWith("Practice") && it.mark == null }.map { it.date }
            assertEquals(listOf(d(5), d(7), d(15), d(19), d(21)), dates, "six dates less the one left out, and one moved from Wednesday to Thursday")
            val practice = events.first { it.title == "Practice" }
            assertEquals(90, practice.durationMinutes)
            assertEquals("Arena", practice.location)
            val cottage = a.calendar.occurrences(d(1), d(31)).filter { it.event.title == "Cottage" }.map { it.date }
            assertEquals(listOf(d(9), d(10), d(11)), cottage)
        }
    }

    @Test
    fun `an ics file of runaway repeats is cut short with a note`() {
        val shared = setUp()
        val events = (1..300).flatMap { listOf("BEGIN:VEVENT", "DTSTART:00010101T080000", "SUMMARY:Runaway $it", "RRULE:FREQ=DAILY;BYDAY=MO;COUNT=100000", "END:VEVENT") }
        val file = (listOf("BEGIN:VCALENDAR", "VERSION:2.0") + events + "END:VCALENDAR").joinToString("\r\n").encodeToByteArray()
        alex().use { a ->
            val started = System.nanoTime()
            val result = a.icsImport.import(shared, file, ZoneId.systemDefault())
            assertTrue(System.nanoTime() - started < 60_000_000_000L, "within a minute")
            assertEquals(0, result.created)
            assertTrue(result.expanded <= 5000)
            assertTrue(result.notes.any { "cut short" in it }, "${result.notes}")
        }
    }

    @Test
    fun `a file that is not a calendar is refused`() {
        val shared = setUp()
        alex().use { a ->
            val e = kotlin.runCatching { a.icsImport.import(shared, "hello".encodeToByteArray()) }.exceptionOrNull()
            assertTrue(e is ValidationException && e.key == "error.icsNotCalendar")
        }
    }
}
