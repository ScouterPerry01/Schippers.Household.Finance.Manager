package ca.schippers.hfm.books

import ca.schippers.hfm.calc.schedule.Frequency
import ca.schippers.hfm.calc.schedule.MonthDay
import ca.schippers.hfm.calc.schedule.Recurrence
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.importers.ICalendar
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** What an .ics import did: events made (repeating ones count once), single dates copied from repeats it could not keep as such, and notes. */
data class IcsImportResult(val created: Int, val expanded: Int, val notes: List<String>)

/**
 * CSY-05: an iCalendar (.ics) file copied once into the calendar, as ordinary events of the chosen
 * account group (they can then be changed like any other; nothing links them to the file). A repeat
 * the calendar can keep as such (every day, week, month or year, on the start's day, until a date)
 * stays one repeating event, with its left-out dates cancelled; a week repeating on several days
 * becomes one event per day; other repeats (the second Tuesday of the month) are copied as single
 * events for the next two years. Reminders are not copied: each event gets none, as the file's own
 * alarms are left to the calendar it came from.
 */
class IcsImportService internal constructor(private val books: Books) {

    fun import(groupId: String, bytes: ByteArray, zone: ZoneId = ZoneId.systemDefault()): IcsImportResult {
        val group = books.group(groupId)
        books.require(group, PermissionLevel.EDIT)
        validate(bytes.size <= ICalendar.MAX_BYTES, "error.icsTooLarge", ICalendar.MAX_BYTES / 1024 / 1024)
        val calendar = runCatching { ICalendar.parse(bytes, zone) }.getOrElse { throw ValidationException("error.icsNotCalendar") }
        val notes = calendar.notes.map(books::note).toMutableList()
        val today = books.today().toJava()
        val horizon = today.plusYears(EXPAND_YEARS)
        // A changed occurrence replaces its date in the series it belongs to.
        val changed = calendar.events.filter { it.recurrenceId != null && it.uid != null }.groupBy { it.uid!! }
            .mapValues { (_, list) -> list.mapNotNull { it.recurrenceId }.toSet() }
        var created = 0
        var expanded = 0
        // All or nothing: a file that fails halfway leaves no events behind.
        books.ledger(group).transaction {
            for (e in calendar.events) {
                val title = e.title.ifBlank { books.text("ics.untitled") }
                val skip = (if (e.recurrenceId == null) changed[e.uid].orEmpty() else emptySet()) + e.exDates
                val rule = e.rule
                if (rule == null) {
                    create(groupId, e, title, e.startDate, null, null)
                    created++
                    continue
                }
                val kept = recurrence(e)
                if (kept != null) {
                    for ((start, recurrence) in kept) {
                        // A count of dates becomes the date of the last one.
                        val end = rule.until ?: rule.count?.let {
                            ICalendar.occurrences(e.copy(exDates = emptySet()), start, start.plusYears(MAX_COUNT_YEARS), limit = MAX_COUNT_EXPANSION)
                                .lastOrNull { it.dayOfWeek == start.dayOfWeek || recurrence.frequency != Frequency.WEEKLY } ?: start
                        }
                        val event = create(groupId, e, title, start, recurrence, end)
                        created++
                        for (d in skip) if (d >= start && (end == null || d <= end)) books.calendar.mark(event.id, d.toKotlin(), OccurrenceMark.CANCELLED)
                    }
                } else {
                    val dates = ICalendar.occurrences(e, maxOf(e.startDate, today.minusYears(1)), horizon, limit = MAX_EXPANDED).filter { it !in skip }
                    if (dates.isEmpty()) continue
                    dates.forEach { d -> create(groupId, e, title, d, null, null) }
                    expanded += dates.size
                    notes += books.text("ics.expanded", title, dates.size)
                }
            }
        }
        books.session.audit("IMPORT", "calendar_ics", groupId, "events $created, single dates $expanded")
        return IcsImportResult(created, expanded, notes)
    }

    /**
     * The repeating events that keep [e]'s repeat exactly, with their first dates; null when the
     * calendar cannot keep it (it is then copied date by date).
     */
    private fun recurrence(e: ICalendar.Event): List<Pair<java.time.LocalDate, Recurrence>>? {
        val r = e.rule ?: return null
        if (r.byMonth.isNotEmpty() && !(r.frequency == ICalendar.Frequency.YEARLY && r.byMonth == listOf(e.startDate.monthValue))) return null
        return when (r.frequency) {
            ICalendar.Frequency.DAILY -> if (r.byDay.isEmpty() && r.byMonthDay.isEmpty()) listOf(e.startDate to Recurrence(Frequency.DAILY, r.interval)) else null
            ICalendar.Frequency.WEEKLY -> {
                if (r.byMonthDay.isNotEmpty()) return null
                val days = r.byDay.map { it.day }.ifEmpty { listOf(e.startDate.dayOfWeek) }.distinct()
                // Each day's first date, within the first weeks of the series.
                val firsts = ICalendar.occurrences(e.copy(exDates = emptySet()), e.startDate, e.startDate.plusWeeks(7L * r.interval + 7), limit = 64)
                days.mapNotNull { d -> firsts.firstOrNull { it.dayOfWeek == d } }.sorted().map { it to Recurrence(Frequency.WEEKLY, r.interval) }
            }
            ICalendar.Frequency.MONTHLY -> when {
                r.byDay.isNotEmpty() -> null
                r.byMonthDay.isEmpty() && e.startDate.dayOfMonth <= 28 -> listOf(e.startDate to Recurrence(Frequency.MONTHLY, r.interval))
                r.byMonthDay == listOf(e.startDate.dayOfMonth) && e.startDate.dayOfMonth <= 28 -> listOf(e.startDate to Recurrence(Frequency.MONTHLY, r.interval))
                r.byMonthDay == listOf(-1) -> listOf(e.startDate to Recurrence(Frequency.MONTHLY, r.interval, MonthDay.LAST_DAY))
                else -> null
            }
            ICalendar.Frequency.YEARLY ->
                if (r.byDay.isEmpty() && r.byMonthDay.isEmpty() && !(e.startDate.monthValue == 2 && e.startDate.dayOfMonth == 29)) {
                    listOf(e.startDate to Recurrence(Frequency.MONTHLY, 12 * r.interval))
                } else {
                    null
                }
        }
    }

    private fun create(groupId: String, e: ICalendar.Event, title: String, date: java.time.LocalDate, recurrence: Recurrence?, end: java.time.LocalDate?): CalendarEvent {
        val time = e.startTime?.let { LocalTime(it.hour, it.minute) }
        val minutes = e.durationMinutes?.takeIf { it > 0 }?.coerceAtMost(MAX_MINUTES)?.toInt()
        // An all-day event over several days repeats each day to its last one.
        val days = ChronoUnit.DAYS.between(e.startDate, e.endDate)
        val (rule, last) = if (recurrence == null && e.allDay && days > 0) Recurrence(Frequency.DAILY) to date.plusDays(days) else recurrence to end
        return books.calendar.create(
            EventDraft(
                groupId, title.take(MAX_TITLE), EventCategory.OTHER, date.toKotlin(), time, minutes, e.location, e.description,
                recurrence = rule, endDate = last?.toKotlin(), reminderMinutes = emptyList(),
            ),
        )
    }

    private fun java.time.LocalDate.toKotlin() = LocalDate(year, monthValue, dayOfMonth)

    private fun LocalDate.toJava() = java.time.LocalDate.of(year, month.ordinal + 1, day)

    companion object {
        /** Repeats the calendar cannot keep are copied this many years ahead. */
        private const val EXPAND_YEARS = 2L
        private const val MAX_EXPANDED = 250
        private const val MAX_COUNT_EXPANSION = 100_000
        private const val MAX_COUNT_YEARS = 200L
        private const val MAX_MINUTES = 60L * 24 * 31
        private const val MAX_TITLE = 300
    }
}
