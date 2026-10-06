package ca.schippers.hfm.books

import ca.schippers.hfm.calc.schedule.BusinessDays
import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import ca.schippers.hfm.data.ledger.Person_schedule as ScheduleRow

/** CAL-09: what a schedule is for. */
enum class ScheduleKind { WORK, SCHOOL, OTHER }

/**
 * The hours of one day of the week, in week [week] of the rotation (0 for the week the schedule
 * starts, then 1, 2... up to the rotation's length less one). An [end] at or before [start] ends the
 * next day (a night shift).
 */
data class ScheduleShift(val week: Int, val dayOfWeek: DayOfWeek, val start: LocalTime, val end: LocalTime)

/** A date that differs from the usual hours: a day off ([off]), or other hours ([start] to [end]). */
data class ScheduleException(val date: LocalDate, val off: Boolean, val start: LocalTime? = null, val end: LocalTime? = null, val reason: String? = null)

/**
 * CAL-09: a person's work or school schedule: the same hours every week, or a rotation of several
 * weeks (shift work), from [startDate] to [endDate] (none: no end), with its [exceptions] and, when
 * [holidaysOff], the bank holidays of the person's province off.
 */
data class PersonSchedule(
    val id: String,
    val groupId: String,
    val memberId: String,
    val kind: ScheduleKind,
    val label: String?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val rotationWeeks: Int,
    val holidaysOff: Boolean,
    val notes: String?,
    val shifts: List<ScheduleShift>,
    val exceptions: List<ScheduleException> = emptyList(),
)

/** One day a person is scheduled (CAL-10): their hours that day, [changed] when an exception set them. */
data class ScheduleDay(val schedule: PersonSchedule, val date: LocalDate, val start: LocalTime, val end: LocalTime, val changed: Boolean = false) {
    val memberId: String get() = schedule.memberId

    /** Ends the next day. */
    val overnight: Boolean get() = end <= start
}

/**
 * Work and school schedules per person (CAL-09). A schedule lives in the ledger of the account group
 * the user chooses (shared by default), so a private one is encrypted from other household users.
 */
class ScheduleService internal constructor(private val books: Books) {

    fun list(): List<PersonSchedule> = books.groups().flatMap { group ->
        val q = books.ledger(group).personSchedulesQueries
        val shifts = q.shifts().executeAsList().groupBy { it.schedule_id }
        val exceptions = q.exceptions().executeAsList().groupBy { it.schedule_id }
        q.schedules().executeAsList().map { row ->
            row.toSchedule(
                group.id,
                shifts[row.id].orEmpty().map { ScheduleShift(it.week.toInt(), DayOfWeek(it.day_of_week.toInt()), LocalTime.parse(it.start_time), LocalTime.parse(it.end_time)) },
                exceptions[row.id].orEmpty().map { ScheduleException(LocalDate.parse(it.date), it.off == 1L, it.start_time?.let(LocalTime::parse), it.end_time?.let(LocalTime::parse), it.reason) },
            )
        }
    }

    fun get(id: String): PersonSchedule = list().firstOrNull { it.id == id } ?: throw AccessDeniedException("Schedule not found or not accessible")

    /** Saves a new schedule ([PersonSchedule.id] empty) or changes one, with its hours and exceptions. Returns its id. */
    fun save(schedule: PersonSchedule): String {
        validate(schedule)
        val now = books.now()
        val s = schedule.copy(label = schedule.label?.trim()?.ifEmpty { null }, notes = schedule.notes?.trim()?.ifEmpty { null })
        val id: String
        val group: GroupInfo
        if (s.id.isEmpty()) {
            group = books.group(s.groupId)
            books.require(group, PermissionLevel.EDIT)
            id = Ids.newId()
        } else {
            group = locate(s.id)
            books.require(group, PermissionLevel.EDIT)
            validate(group.id == s.groupId, "error.cannotChangeAccount")
            id = s.id
        }
        val ledger = books.ledger(group)
        ledger.transaction {
            val q = ledger.personSchedulesQueries
            if (s.id.isEmpty()) {
                q.insertSchedule(id, s.memberId, s.kind.name, s.label, s.startDate.toString(), s.endDate?.toString(), s.rotationWeeks.toLong(), if (s.holidaysOff) 1 else 0, s.notes, now, now)
            } else {
                q.updateSchedule(s.memberId, s.kind.name, s.label, s.startDate.toString(), s.endDate?.toString(), s.rotationWeeks.toLong(), if (s.holidaysOff) 1 else 0, s.notes, now, id)
            }
            q.deleteShifts(id)
            for (shift in s.shifts) q.insertShift(id, shift.week.toLong(), shift.dayOfWeek.isoDayNumber.toLong(), time(shift.start), time(shift.end))
            q.deleteExceptions(id)
            for (e in s.exceptions) writeException(q, id, e)
        }
        return id
    }

    fun delete(id: String) {
        val group = locate(id)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).personSchedulesQueries.deleteSchedule(id)
    }

    /** Sets one date's exception (a day off, or other hours), replacing any the date had. */
    fun setException(scheduleId: String, exception: ScheduleException) {
        validateException(exception)
        val group = locate(scheduleId)
        books.require(group, PermissionLevel.EDIT)
        writeException(books.ledger(group).personSchedulesQueries, scheduleId, exception)
    }

    /** Takes a date's exception away: the usual hours apply again. */
    fun removeException(scheduleId: String, date: LocalDate) {
        val group = locate(scheduleId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).personSchedulesQueries.deleteException(scheduleId, date.toString())
    }

    /** CAL-10: every scheduled day between [from] and [to], by date, then start time. */
    fun days(from: LocalDate, to: LocalDate): List<ScheduleDay> {
        val holidays = HashMap<Pair<String, Int>, Set<LocalDate>>()
        return list().flatMap { s ->
            val province = books.provinceOf(s.memberId)
            days(s, from, to) { year -> holidays.getOrPut(province.name to year) { BusinessDays.holidays(year, province) } }
        }.sortedWith(compareBy({ it.date }, { it.start }))
    }

    private fun writeException(q: ca.schippers.hfm.data.ledger.PersonSchedulesQueries, scheduleId: String, e: ScheduleException) {
        q.setException(scheduleId, e.date.toString(), if (e.off) 1 else 0, e.start?.takeIf { !e.off }?.let(::time), e.end?.takeIf { !e.off }?.let(::time), e.reason?.trim()?.ifEmpty { null })
    }

    private fun validate(s: PersonSchedule) {
        validate(books.members.list(includeArchived = true).any { it.id == s.memberId }, "error.scheduleMember")
        validate(s.rotationWeeks in 1..MAX_ROTATION_WEEKS, "error.scheduleRotation", MAX_ROTATION_WEEKS)
        validate(s.endDate == null || s.endDate >= s.startDate, "error.endBeforeStart")
        validate(s.shifts.isNotEmpty(), "error.scheduleNoDays")
        validate(s.shifts.all { it.week in 0 until s.rotationWeeks && it.start != it.end }, "error.scheduleHours")
        validate(s.shifts.distinctBy { it.week to it.dayOfWeek }.size == s.shifts.size, "error.scheduleHours")
        s.exceptions.forEach(::validateException)
    }

    private fun validateException(e: ScheduleException) {
        validate(e.off || (e.start != null && e.end != null && e.start != e.end), "error.scheduleHours")
    }

    private fun locate(id: String): GroupInfo {
        for (group in books.groups()) {
            if (books.ledger(group).personSchedulesQueries.scheduleById(id).executeAsOneOrNull() != null) return group
        }
        throw AccessDeniedException("Schedule not found or not accessible")
    }

    private fun ScheduleRow.toSchedule(groupId: String, shifts: List<ScheduleShift>, exceptions: List<ScheduleException>) = PersonSchedule(
        id, groupId, member_id, ScheduleKind.valueOf(kind), label, LocalDate.parse(start_date), end_date?.let(LocalDate::parse),
        rotation_weeks.toInt(), holidays_off == 1L, notes, shifts, exceptions,
    )

    private fun time(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

    companion object {
        const val MAX_ROTATION_WEEKS = 8

        /**
         * The days [s] is scheduled between [from] and [to]. The rotation counts whole weeks from the
         * Monday of the week the schedule starts. An exception wins over the usual hours and over a
         * bank holiday ([holidays] gives a year's, used when the schedule has them off).
         */
        fun days(s: PersonSchedule, from: LocalDate, to: LocalDate, holidays: (Int) -> Set<LocalDate>): List<ScheduleDay> {
            val first = maxOf(from, s.startDate)
            val last = s.endDate?.let { minOf(it, to) } ?: to
            if (first > last) return emptyList()
            val exceptions = s.exceptions.associateBy { it.date }
            val shifts = s.shifts.associateBy { it.week to it.dayOfWeek }
            val startMonday = s.startDate.minus(DatePeriod(days = s.startDate.dayOfWeek.isoDayNumber - 1))
            val out = ArrayList<ScheduleDay>()
            var date = first
            while (date <= last) {
                val exception = exceptions[date]
                if (exception != null) {
                    if (!exception.off) out += ScheduleDay(s, date, exception.start!!, exception.end!!, changed = true)
                } else if (!(s.holidaysOff && date in holidays(date.year))) {
                    val week = Math.floorMod(startMonday.daysUntil(date) / 7, s.rotationWeeks)
                    shifts[week to date.dayOfWeek]?.let { out += ScheduleDay(s, date, it.start, it.end) }
                }
                date = date.plus(DatePeriod(days = 1))
            }
            return out
        }
    }
}
