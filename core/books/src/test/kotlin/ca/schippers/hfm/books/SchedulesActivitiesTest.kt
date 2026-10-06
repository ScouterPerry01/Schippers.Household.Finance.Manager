package ca.schippers.hfm.books

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.schedule.Recurrence
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.OcrResult
import ca.schippers.hfm.security.KdfParams
import ca.schippers.hfm.security.KeyPair
import ca.schippers.hfm.security.PairKey
import ca.schippers.hfm.sync.Direction
import ca.schippers.hfm.sync.PairRequest
import ca.schippers.hfm.sync.RefSchedule
import ca.schippers.hfm.sync.SyncCrypto
import ca.schippers.hfm.sync.SyncRequest
import ca.schippers.hfm.sync.SyncResponse
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CAL-09 to CAL-11: work and school schedules, children's activities with their carpool and cost. */
class SchedulesActivitiesTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var shared: String
    private lateinit var alex: Member
    private lateinit var lea: Member
    private val dir get() = temp.resolve("S.hfm")
    private fun d(m: Int, day: Int) = LocalDate(2026, m, day)
    private fun t(h: Int, m: Int = 0) = LocalTime(h, m)

    @BeforeEach
    fun setUp() {
        books = Books(store.create(dir, "S", "perry", "Perry", "password1".toCharArray()).session)
        books.setProvince(Province.ON)
        shared = books.groups().single().id
        alex = books.members.create("Alex", MemberKind.ADULT)
        lea = books.members.create("Léa", MemberKind.CHILD)
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun weekdays(start: LocalTime, end: LocalTime, week: Int = 0) = (1..5).map { ScheduleShift(week, DayOfWeek(it), start, end) }

    private fun schedule(
        shifts: List<ScheduleShift>, rotation: Int = 1, holidaysOff: Boolean = false, exceptions: List<ScheduleException> = emptyList(),
        start: LocalDate = d(9, 1), end: LocalDate? = null, member: Member = alex, group: String = shared,
    ) = PersonSchedule("", group, member.id, ScheduleKind.WORK, null, start, end, rotation, holidaysOff, null, shifts, exceptions)

    // --- Schedules (CAL-09, CAL-10) --------------------------------------------------------------

    @Test
    fun `a weekly schedule gives each working day its hours, and the bank holidays off when chosen`() {
        val id = books.schedules.save(schedule(weekdays(t(8), t(16, 30)), holidaysOff = true))
        // The week of Thanksgiving (Monday 2026-10-12, a bank holiday in Ontario).
        val days = books.schedules.days(d(10, 10), d(10, 18))
        assertEquals(listOf(d(10, 13), d(10, 14), d(10, 15), d(10, 16)), days.map { it.date }, "no weekend, no Thanksgiving")
        assertTrue(days.all { it.start == t(8) && it.end == t(16, 30) && !it.overnight && it.schedule.id == id })

        val worksHolidays = books.schedules.get(id).copy(holidaysOff = false)
        books.schedules.save(worksHolidays)
        assertEquals(d(10, 12), books.schedules.days(d(10, 12), d(10, 12)).single().date, "a schedule that works holidays")
    }

    @Test
    fun `a rotation counts whole weeks from the week it starts, and a night shift ends the next day`() {
        // Started on a Wednesday: week 1 is that week (from its Monday), week 2 the next, then week 1 again.
        val shifts = listOf(ScheduleShift(0, DayOfWeek.MONDAY, t(7), t(19)), ScheduleShift(1, DayOfWeek.MONDAY, t(19), t(7)))
        books.schedules.save(schedule(shifts, rotation = 2, start = d(9, 2)))
        val mondays = books.schedules.days(d(9, 1), d(9, 30))
        assertEquals(listOf(d(9, 7), d(9, 14), d(9, 21), d(9, 28)), mondays.map { it.date }, "not before it starts")
        assertEquals(listOf(true, false, true, false), mondays.map { it.overnight }, "nights in week 2 (Sept 7), days in week 1 (Sept 14)")

        val pure = ScheduleService.days(schedule(shifts, rotation = 2, start = d(9, 2)), d(8, 1), d(9, 13)) { emptySet() }
        assertEquals(listOf(d(9, 7)), pure.map { it.date })
    }

    @Test
    fun `an exception gives a day off or other hours, and wins over a holiday`() {
        val exceptions = listOf(
            ScheduleException(d(10, 14), off = true, reason = "PD day"),
            ScheduleException(d(10, 15), off = false, start = t(12), end = t(20)),
            ScheduleException(d(10, 12), off = false, start = t(9), end = t(13), reason = "Inventory"),
            ScheduleException(d(10, 17), off = false, start = t(10), end = t(14)),
        )
        val id = books.schedules.save(schedule(weekdays(t(8), t(16)), holidaysOff = true, exceptions = exceptions, end = d(10, 31)))
        val days = books.schedules.days(d(10, 12), d(10, 18)).associateBy { it.date }
        assertEquals(t(9), days.getValue(d(10, 12)).start, "worked on the holiday by exception")
        assertFalse(d(10, 14) in days)
        assertEquals(t(12) to t(20), days.getValue(d(10, 15)).let { it.start to it.end })
        assertTrue(days.getValue(d(10, 15)).changed && !days.getValue(d(10, 16)).changed)
        assertTrue(d(10, 17) in days, "a Saturday added by exception")

        books.schedules.removeException(id, d(10, 14))
        books.schedules.setException(id, ScheduleException(d(10, 16), off = true))
        val after = books.schedules.days(d(10, 14), d(10, 16)).map { it.date }
        assertEquals(listOf(d(10, 14), d(10, 15)), after)
        assertTrue(books.schedules.days(d(11, 2), d(11, 6)).isEmpty(), "ended")
    }

    @Test
    fun `schedules are checked before they are saved`() {
        assertFailsWith<ValidationException> { books.schedules.save(schedule(emptyList())) }
        assertFailsWith<ValidationException> { books.schedules.save(schedule(weekdays(t(8), t(8)))) }
        assertFailsWith<ValidationException> { books.schedules.save(schedule(weekdays(t(8), t(16), week = 1))) }
        assertFailsWith<ValidationException> { books.schedules.save(schedule(weekdays(t(8), t(16)), rotation = 9)) }
        assertFailsWith<ValidationException> { books.schedules.save(schedule(weekdays(t(8), t(16)), end = d(8, 1))) }
        assertFailsWith<ValidationException> { books.schedules.save(schedule(weekdays(t(8), t(16))).copy(memberId = "nobody")) }
        assertFailsWith<ValidationException> { books.schedules.save(schedule(weekdays(t(8), t(16)), exceptions = listOf(ScheduleException(d(10, 1), off = false)))) }
    }

    @Test
    fun `saving again replaces the hours and exceptions, and deleting removes everything`() {
        val id = books.schedules.save(schedule(weekdays(t(8), t(16)), exceptions = listOf(ScheduleException(d(10, 1), true))))
        books.schedules.save(books.schedules.get(id).copy(shifts = listOf(ScheduleShift(0, DayOfWeek.SATURDAY, t(9), t(13))), exceptions = emptyList(), label = " Library "))
        val s = books.schedules.get(id)
        assertEquals(1, s.shifts.size)
        assertTrue(s.exceptions.isEmpty())
        assertEquals("Library", s.label)
        books.schedules.delete(id)
        assertTrue(books.schedules.list().isEmpty())
    }

    @Test
    fun `a schedule in a private group stays private`() {
        books.session.addUser("marie", "Marie", Role.MEMBER, "password2-long".toCharArray())
        val private = books.session.createGroup("Perry - private", private = true)
        books.schedules.save(schedule(weekdays(t(8), t(16)), group = private))
        assertEquals(1, books.schedules.list().size)
        books.session.close()
        Books(store.unlock(dir, "marie", "password2-long".toCharArray())).let { marie ->
            assertTrue(marie.schedules.list().isEmpty())
            assertTrue(marie.calendar.items(d(10, 1), d(10, 31)).none { it.kind == CalendarKind.SCHEDULES })
            marie.session.close()
        }
        books = Books(store.unlock(dir, "perry", "password1".toCharArray()))
    }

    @Test
    fun `the calendar shows schedules as items of their own kind`() {
        books.schedules.save(schedule(weekdays(t(8, 15), t(15, 5)), member = lea))
        val items = books.calendar.items(d(10, 5), d(10, 11))
        val schedules = items.filterIsInstance<CalendarItem.Schedule>()
        assertEquals(5, schedules.size)
        assertTrue(schedules.all { it.kind == CalendarKind.SCHEDULES && it.day.memberId == lea.id })
    }

    // --- Activities (CAL-11) ---------------------------------------------------------------------

    @Test
    fun `an activity keeps its drivers and cost, and a date can have its own drivers`() {
        val jen = Driver(name = "Jen (Noah's mom)")
        val e = books.calendar.create(
            EventDraft(
                shared, "Swimming", EventCategory.ACTIVITY, d(10, 7), t(18), 60, "Brewer Pool", memberId = lea.id, recurrence = Recurrence.WEEKLY,
                driverThere = Driver(memberId = alex.id), driverBack = jen, cost = Money.parse("15.00", Currency.CAD),
            ),
        )
        val read = books.calendar.get(e.id)
        assertEquals(EventCategory.ACTIVITY, read.category)
        assertEquals(Driver(memberId = alex.id), read.driverThere)
        assertEquals(jen, read.driverBack)
        assertEquals(Money.parse("15.00", Currency.CAD), read.cost)

        books.calendar.setDrivers(e.id, d(10, 14), jen, null)
        val (first, second, third) = books.calendar.occurrences(d(10, 7), d(10, 21))
        assertEquals(Driver(memberId = alex.id), first.driverThere)
        assertEquals(jen to null, second.driverThere to second.driverBack, "nobody drives back that day")
        assertTrue(second.driversChanged && !third.driversChanged)
        books.calendar.clearDrivers(e.id, d(10, 14))
        assertEquals(Driver(memberId = alex.id), books.calendar.occurrences(d(10, 14), d(10, 14)).single().driverThere)

        // Changing it to another kind keeps it in the same table's categories.
        books.calendar.update(read.copy(category = EventCategory.PERSONAL, driverThere = null, driverBack = null, cost = null))
        assertEquals(EventCategory.PERSONAL, books.calendar.get(e.id).category)
        assertNull(books.calendar.get(e.id).cost)
    }

    @Test
    fun `an activity's cost becomes a transaction for the child, once per date`() {
        val account = books.accounts.create(AccountDraft(shared, "Chequing", AccountType.CHEQUING, Currency.CAD, Money.parse("0", Currency.CAD), d(1, 1)))
        val category = books.categories.list().first { it.systemKey == "children.activities" }.id
        val e = books.calendar.create(
            EventDraft(shared, "Soccer", EventCategory.ACTIVITY, d(10, 10), t(9, 30), 90, memberId = lea.id, recurrence = Recurrence.WEEKLY, cost = Money.parse("20.00", Currency.CAD)),
        )
        val id = books.calendar.recordCost(e.id, d(10, 17), account.id, category)
        val txn = books.transactions.get(id)
        assertEquals(Money.parse("-20.00", Currency.CAD), txn.amount)
        assertEquals(d(10, 17), txn.date)
        assertEquals(lea.id, txn.memberId)
        assertEquals(category, txn.splits.single().categoryId)
        val occurrences = books.calendar.occurrences(d(10, 10), d(10, 17))
        assertEquals(listOf(null, id), occurrences.map { it.costTransactionId })
        assertFailsWith<ValidationException>("recorded once") { books.calendar.recordCost(e.id, d(10, 17), account.id, category) }

        // Its transaction deleted, the date's cost can be recorded again.
        books.transactions.delete(id)
        assertNull(books.calendar.occurrences(d(10, 17), d(10, 17)).single().costTransactionId)
        val again = books.calendar.recordCost(e.id, d(10, 17), account.id, category)
        assertEquals(again, books.calendar.occurrences(d(10, 17), d(10, 17)).single().costTransactionId)

        val free = books.calendar.create(EventDraft(shared, "Practice", EventCategory.ACTIVITY, d(10, 11), memberId = lea.id))
        assertFailsWith<ValidationException> { books.calendar.recordCost(free.id, d(10, 11), account.id, category) }
        assertFailsWith<ValidationException> {
            books.calendar.create(EventDraft(shared, "Bad", EventCategory.ACTIVITY, d(10, 11), cost = Money.parse("-1", Currency.CAD)))
        }
    }

    // --- The phone (CAL-10) ----------------------------------------------------------------------

    @Test
    fun `the phone gets today's and tomorrow's schedules through a transfer`() {
        books.schedules.save(schedule(weekdays(t(8), t(16, 30))).copy(label = "Office"))
        books.schedules.save(schedule(listOf(ScheduleShift(0, DayOfWeek.WEDNESDAY, t(19), t(7))), member = lea).copy(kind = ScheduleKind.OTHER))
        val now = 1_790_000_000_000L
        val today = d(10, 6)
        val phone = KeyPair.generate()
        val invitation = books.sync.invitation("Desk", "127.0.0.1", 47311, now)
        val desktopKey = SyncCrypto.unb64(invitation.desktopPublicKey)
        books.sync.pair(PairRequest("phone-1", "Pixel", SyncCrypto.b64(phone.publicKey), SyncCrypto.phoneProof(invitation.oneTimeCode, phone.publicKey, desktopKey)), now)
        val key = PairKey.derive(phone, desktopKey, desktopKey, phone.publicKey)
        val sealed = SyncCrypto.seal(SyncRequest.serializer(), SyncRequest(now, emptyList()), key, books.sync.desktopId, "phone-1", Direction.TO_DESKTOP)
        val none = object : CaptureConverter {
            override fun pagesToPdf(pages: List<ByteArray>) = ByteArray(0)
            override fun recognize(content: ByteArray): OcrResult? = null
        }
        val response = SyncCrypto.open(SyncResponse.serializer(), books.sync.handle("phone-1", sealed, none, now, today), key, books.sync.desktopId, "phone-1", Direction.TO_PHONE)
        assertEquals(
            listOf(
                RefSchedule("Alex", "WORK", "2026-10-06", "08:00", "16:30", "Office"),
                RefSchedule("Alex", "WORK", "2026-10-07", "08:00", "16:30", "Office"),
                RefSchedule("Léa", "OTHER", "2026-10-07", "19:00", "07:00"),
            ),
            response.reference!!.schedules,
        )
    }
}
