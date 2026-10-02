package ca.schippers.hfm.books

import ca.schippers.hfm.calc.schedule.Frequency
import ca.schippers.hfm.calc.schedule.Recurrence
import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import ca.schippers.hfm.data.ledger.Event as EventRow

enum class EventCategory { MEDICAL, FINANCIAL, VEHICLE, HOME, PET, PERSONAL, OTHER }
enum class OccurrenceMark { DONE, CANCELLED }

/** CAL-01, CAL-02: an appointment or event, one-time or repeating. */
data class CalendarEvent(
    val id: String,
    val groupId: String,
    val title: String,
    val category: EventCategory,
    val startDate: LocalDate,
    /** Null for an all-day event. */
    val startTime: LocalTime?,
    val durationMinutes: Int?,
    val location: String?,
    val notes: String?,
    val memberId: String?,
    val providerId: String?,
    val accountId: String?,
    /** Null for a one-time event. */
    val recurrence: Recurrence?,
    val endDate: LocalDate?,
    /** CAL-03: reminder lead times in minutes, e.g. 1440 and 60. */
    val reminderMinutes: List<Int>,
)

data class EventDraft(
    val groupId: String,
    val title: String,
    val category: EventCategory,
    val startDate: LocalDate,
    val startTime: LocalTime? = null,
    val durationMinutes: Int? = null,
    val location: String? = null,
    val notes: String? = null,
    val memberId: String? = null,
    val providerId: String? = null,
    val accountId: String? = null,
    val recurrence: Recurrence? = null,
    val endDate: LocalDate? = null,
    val reminderMinutes: List<Int> = listOf(1440),
)

data class EventOccurrence(val event: CalendarEvent, val date: LocalDate, val mark: OccurrenceMark?) {
    /** When it starts; all-day events count from 08:00 for reminders. */
    val start: LocalDateTime get() = LocalDateTime(date, event.startTime ?: ALL_DAY_REMINDER_TIME)

    companion object {
        val ALL_DAY_REMINDER_TIME = LocalTime(8, 0)
    }
}

/** An event coming up within one of its reminder lead times. */
data class EventReminder(val occurrence: EventOccurrence, val minutesBefore: Long)

/** Everything the unified calendar shows on a day (CAL-04). */
sealed interface CalendarItem {
    val date: LocalDate

    data class Event(val occurrence: EventOccurrence) : CalendarItem {
        override val date get() = occurrence.date
    }

    data class Bill(val occurrence: Occurrence) : CalendarItem {
        override val date get() = occurrence.dueDate
    }

    data class Health(val due: HealthDue) : CalendarItem {
        override val date get() = due.date
    }

    /** VEH-11: a maintenance task's next due date. */
    data class Maintenance(val due: MaintenanceDue) : CalendarItem {
        override val date get() = due.status.nextDate!!
    }

    /** PET-02, VEH-02, VEH-03: a licence, policy, registration or warranty expiring. */
    data class Renewal(val renewal: ca.schippers.hfm.books.Renewal) : CalendarItem {
        override val date get() = renewal.date
    }
}

/**
 * Appointments and events of any kind (CAL-01 to CAL-06), with reminders, shown in one calendar
 * with bills and health due dates. Events live in the ledger of the account group the user
 * chooses, so private ones are encrypted from other household users.
 */
class CalendarService internal constructor(private val books: Books) {

    fun list(): List<CalendarEvent> = books.groups().flatMap { group ->
        books.ledger(group).calendarQueries.events().executeAsList().map { it.toEvent(group.id) }
    }

    fun get(eventId: String): CalendarEvent = locate(eventId).second

    fun create(draft: EventDraft): CalendarEvent {
        val group = books.group(draft.groupId)
        books.require(group, PermissionLevel.CAPTURE_ONLY)
        validate(draft)
        val id = Ids.newId()
        val now = books.now()
        with(draft) {
            books.ledger(group).calendarQueries.insertEvent(
                id, title.trim(), category.name, startDate.toString(), startTime?.let(::time), durationMinutes?.toLong(), location?.ifBlank { null },
                notes?.ifBlank { null }, memberId, providerId, accountId, recurrence?.encode(), endDate?.toString(),
                reminderMinutes.sorted().reversed().joinToString(","), now, now,
            )
        }
        return get(id)
    }

    fun update(event: CalendarEvent) {
        val (group, existing) = locate(event.id)
        books.require(group, PermissionLevel.EDIT)
        validate(event.groupId == existing.groupId, "error.cannotChangeAccount")
        with(event) {
            validate(EventDraft(groupId, title, category, startDate, startTime, durationMinutes, location, notes, memberId, providerId, accountId, recurrence, endDate, reminderMinutes))
            books.ledger(group).calendarQueries.updateEvent(
                title.trim(), category.name, startDate.toString(), startTime?.let(::time), durationMinutes?.toLong(), location?.ifBlank { null },
                notes?.ifBlank { null }, memberId, providerId, accountId, recurrence?.encode(), endDate?.toString(),
                reminderMinutes.sorted().reversed().joinToString(","), books.now(), id,
            )
        }
    }

    fun delete(eventId: String) {
        val (group, _) = locate(eventId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).calendarQueries.deleteEvent(eventId)
    }

    /** CAL-05: marks one occurrence done or cancelled (null clears the mark). */
    fun mark(eventId: String, date: LocalDate, mark: OccurrenceMark?) {
        val (group, _) = locate(eventId)
        books.require(group, PermissionLevel.EDIT)
        val q = books.ledger(group).calendarQueries
        if (mark == null) q.clearOccurrenceStatus(eventId, date.toString()) else q.setOccurrenceStatus(eventId, date.toString(), mark.name)
    }

    fun occurrences(from: LocalDate, to: LocalDate): List<EventOccurrence> = books.groups().flatMap { group ->
        val q = books.ledger(group).calendarQueries
        val marks = q.occurrenceStatuses().executeAsList().associate { (it.event_id to it.date) to OccurrenceMark.valueOf(it.status) }
        q.events().executeAsList().map { it.toEvent(group.id) }.flatMap { event ->
            val rule = event.recurrence ?: Recurrence(Frequency.ONCE)
            rule.occurrences(event.startDate, from, to, event.endDate).map { date ->
                EventOccurrence(event, date, marks[event.id to date.toString()])
            }
        }
    }.sortedWith(compareBy({ it.date }, { it.event.startTime ?: LocalTime(0, 0) }))

    /**
     * CAL-04: events, bills and health due dates between [from] and [to], in date order. Paid bills
     * and cancelled events are included so the calendar shows the full picture.
     */
    fun items(from: LocalDate, to: LocalDate): List<CalendarItem> =
        (occurrences(from, to).map { CalendarItem.Event(it) } +
            books.bills.occurrences(from, to).map { CalendarItem.Bill(it) } +
            books.health.due(from, to).map { CalendarItem.Health(it) } +
            renewals(from, to).map { CalendarItem.Renewal(it) } +
            books.vehicles.dueBetween(from, to, books.today()).map { CalendarItem.Maintenance(it) })
            .sortedBy { it.date }

    /** Renewal dates between [from] and [to], for the calendar. */
    private fun renewals(from: LocalDate, to: LocalDate): List<Renewal> =
        books.renewals(from, from.daysUntil(to)).filter { it.date in from..to }

    /** CAL-03: events starting within one of their reminder lead times from [now]. */
    fun reminders(now: LocalDateTime): List<EventReminder> {
        val today = now.date
        val maxLead = list().flatMap { it.reminderMinutes }.maxOrNull() ?: return emptyList()
        val horizon = today.plus(DatePeriod(days = (maxLead / 1440) + 1))
        return occurrences(today.minus(DatePeriod(days = 1)), horizon)
            .filter { it.mark == null }
            .mapNotNull { o ->
                val minutes = minutesBetween(now, o.start)
                if (minutes < 0) return@mapNotNull null
                if (o.event.reminderMinutes.any { lead -> minutes <= lead }) EventReminder(o, minutes) else null
            }
    }

    private fun validate(draft: EventDraft) {
        validate(draft.title.isNotBlank(), "error.nameRequired")
        validate(draft.endDate == null || draft.endDate >= draft.startDate, "error.endBeforeStart")
        validate(draft.durationMinutes == null || draft.durationMinutes in 1..(60 * 24 * 31), "error.invalidNumber")
        validate(draft.reminderMinutes.all { it in 0..(60 * 24 * 60) }, "error.reminderDays")
    }

    private fun locate(eventId: String): Pair<GroupInfo, CalendarEvent> {
        for (group in books.groups()) {
            val row = books.ledger(group).calendarQueries.eventById(eventId).executeAsOneOrNull() ?: continue
            return group to row.toEvent(group.id)
        }
        throw AccessDeniedException("Event not found or not accessible")
    }

    private fun EventRow.toEvent(groupId: String) = CalendarEvent(
        id, groupId, title, EventCategory.valueOf(category), LocalDate.parse(start_date), start_time?.let(LocalTime::parse),
        duration_minutes?.toInt(), location, notes, member_id, provider_id, account_id, recurrence?.let(Recurrence::decode),
        end_date?.let(LocalDate::parse), reminder_minutes.split(',').mapNotNull { it.trim().toIntOrNull() },
    )

    private fun time(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

    companion object {
        fun minutesBetween(from: LocalDateTime, to: LocalDateTime): Long {
            val days = to.date.toEpochDays() - from.date.toEpochDays()
            return days * 1440L + (to.time.hour * 60 + to.time.minute) - (from.time.hour * 60 + from.time.minute)
        }
    }
}
