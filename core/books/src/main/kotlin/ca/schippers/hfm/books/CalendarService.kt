package ca.schippers.hfm.books

import ca.schippers.hfm.calc.rules.LeadTimes
import ca.schippers.hfm.calc.schedule.Frequency
import ca.schippers.hfm.calc.schedule.Recurrence
import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import ca.schippers.hfm.data.ledger.Event as EventRow

/**
 * [ACTIVITY] (CAL-11): a child's practice, game or lesson, with who drives each way and its cost. It is
 * stored as PERSONAL with the event's activity flag, since the table's list of categories is fixed.
 */
enum class EventCategory { MEDICAL, FINANCIAL, VEHICLE, HOME, PET, PERSONAL, ACTIVITY, OTHER }
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
    /** CAL-11: who drives there and back, for an activity. */
    val driverThere: Driver? = null,
    val driverBack: Driver? = null,
    /** CAL-11: what each occurrence costs; it can become a transaction. */
    val cost: Money? = null,
)

/** CAL-11: who drives: a household member ([memberId]) or a name typed in ([name]), such as another parent. */
data class Driver(val memberId: String? = null, val name: String? = null) {
    init {
        require((memberId == null) != (name.isNullOrBlank())) { "A driver is a member or a name" }
    }
}

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
    val reminderMinutes: List<Int> = listOf(LeadTimes.newEvent()),
    val driverThere: Driver? = null,
    val driverBack: Driver? = null,
    val cost: Money? = null,
)

/**
 * One date of an event. For an activity (CAL-11), [driverThere] and [driverBack] are that date's
 * drivers (the series' unless changed for the date, [driversChanged]), and [costTransactionId] the
 * transaction its cost became.
 */
data class EventOccurrence(
    val event: CalendarEvent,
    val date: LocalDate,
    val mark: OccurrenceMark?,
    val driverThere: Driver? = event.driverThere,
    val driverBack: Driver? = event.driverBack,
    val driversChanged: Boolean = false,
    val costTransactionId: String? = null,
) {
    /** When it starts; all-day events count from 08:00 for reminders. */
    val start: LocalDateTime get() = LocalDateTime(date, event.startTime ?: ALL_DAY_REMINDER_TIME)

    companion object {
        val ALL_DAY_REMINDER_TIME = LocalTime(8, 0)
    }
}

/** An event coming up within one of its reminder lead times. */
data class EventReminder(val occurrence: EventOccurrence, val minutesBefore: Long)

/**
 * CAL-08: the kinds of item the calendar can show or hide. Every [CalendarItem] has one; work and school
 * schedules (CAL-09) and calendars brought in from the phone (CSY-01) have theirs ready.
 */
enum class CalendarKind { EVENTS, BILLS, HEALTH, MAINTENANCE, RENEWALS, SCHEDULES, IMPORTED }

/** Everything the unified calendar shows on a day (CAL-04). */
sealed interface CalendarItem {
    val date: LocalDate
    val kind: CalendarKind

    data class Event(val occurrence: EventOccurrence) : CalendarItem {
        override val date get() = occurrence.date
        override val kind get() = CalendarKind.EVENTS
    }

    data class Bill(val occurrence: Occurrence) : CalendarItem {
        override val date get() = occurrence.dueDate
        override val kind get() = CalendarKind.BILLS
    }

    data class Health(val due: HealthDue) : CalendarItem {
        override val date get() = due.date
        override val kind get() = CalendarKind.HEALTH
    }

    /** VEH-11, MNT-05: a maintenance task's next due date, on a vehicle or another asset. */
    data class Maintenance(val due: UpkeepDue) : CalendarItem {
        override val date get() = due.status.nextDate!!
        override val kind get() = CalendarKind.MAINTENANCE
    }

    /** PET-02, VEH-02, VEH-03: a licence, policy, registration or warranty expiring. */
    data class Renewal(val renewal: ca.schippers.hfm.books.Renewal) : CalendarItem {
        override val date get() = renewal.date
        override val kind get() = CalendarKind.RENEWALS
    }

    /** CAL-09, CAL-10: a person's work or school hours that day. */
    data class Schedule(val day: ScheduleDay) : CalendarItem {
        override val date get() = day.date
        override val kind get() = CalendarKind.SCHEDULES
    }

    /** CSY-04: an item brought in from a person's phone, read-only, on one of the days it covers. */
    data class Imported(val item: BroughtInItem, override val date: LocalDate) : CalendarItem {
        override val kind get() = CalendarKind.IMPORTED
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
                id, title.trim(), storedCategory(category), startDate.toString(), startTime?.let(::time), durationMinutes?.toLong(), location?.ifBlank { null },
                notes?.ifBlank { null }, memberId, providerId, accountId, recurrence?.encode(), endDate?.toString(),
                reminderMinutes.sorted().reversed().joinToString(","), now, now, if (category == EventCategory.ACTIVITY) 1 else 0,
                driverThere?.memberId, driverThere?.name?.trim(), driverBack?.memberId, driverBack?.name?.trim(), cost?.minorUnits, cost?.currency?.code,
            )
        }
        return get(id)
    }

    fun update(event: CalendarEvent) {
        val (group, existing) = locate(event.id)
        books.require(group, PermissionLevel.EDIT)
        validate(event.groupId == existing.groupId, "error.cannotChangeAccount")
        with(event) {
            validate(EventDraft(groupId, title, category, startDate, startTime, durationMinutes, location, notes, memberId, providerId, accountId, recurrence, endDate, reminderMinutes, driverThere, driverBack, cost))
            books.ledger(group).calendarQueries.updateEvent(
                title.trim(), storedCategory(category), startDate.toString(), startTime?.let(::time), durationMinutes?.toLong(), location?.ifBlank { null },
                notes?.ifBlank { null }, memberId, providerId, accountId, recurrence?.encode(), endDate?.toString(),
                reminderMinutes.sorted().reversed().joinToString(","), books.now(), if (category == EventCategory.ACTIVITY) 1 else 0,
                driverThere?.memberId, driverThere?.name?.trim(), driverBack?.memberId, driverBack?.name?.trim(), cost?.minorUnits, cost?.currency?.code, id,
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
        val days = books.ledger(group).eventActivitiesQueries.activityDays().executeAsList().associateBy { it.event_id to it.date }
        q.events().executeAsList().map { it.toEvent(group.id) }.flatMap { event ->
            val rule = event.recurrence ?: Recurrence(Frequency.ONCE)
            rule.occurrences(event.startDate, from, to, event.endDate).map { date ->
                val day = days[event.id to date.toString()]
                val changed = day?.drivers_set == 1L
                EventOccurrence(
                    event, date, marks[event.id to date.toString()],
                    if (changed) driver(day.driver_there_member_id, day.driver_there_name) else event.driverThere,
                    if (changed) driver(day.driver_back_member_id, day.driver_back_name) else event.driverBack,
                    changed, day?.cost_txn_id,
                )
            }
        }
    }.sortedWith(compareBy({ it.date }, { it.event.startTime ?: LocalTime(0, 0) }))

    /**
     * CAL-11: who drives on one date of an activity, in place of the series' drivers (a carpool turn).
     * [there] and [back] null mean nobody drives that way that day.
     */
    fun setDrivers(eventId: String, date: LocalDate, there: Driver?, back: Driver?) {
        val (group, _) = locate(eventId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).eventActivitiesQueries.setActivityDrivers(eventId, date.toString(), there?.memberId, there?.name?.trim(), back?.memberId, back?.name?.trim())
    }

    /** CAL-11: the date's drivers are the series' again. */
    fun clearDrivers(eventId: String, date: LocalDate) {
        val (group, _) = locate(eventId)
        books.require(group, PermissionLevel.EDIT)
        val q = books.ledger(group).eventActivitiesQueries
        q.clearActivityDrivers(eventId, date.toString())
        q.deleteEmptyActivityDays()
    }

    /**
     * CAL-11: records the cost of an activity's date as a spending transaction from [accountId] in
     * [categoryId], for the activity's person, and keeps it with the date. Returns the transaction's id.
     */
    fun recordCost(eventId: String, date: LocalDate, accountId: String, categoryId: String?): String {
        val (group, event) = locate(eventId)
        books.require(group, PermissionLevel.EDIT)
        val cost = event.cost
        validate(cost != null && cost.isPositive, "error.amountPositive")
        val account = books.accounts.get(accountId)
        validate(account.currency == cost!!.currency, "error.currencyMismatch", account.currency.code)
        val txn = books.transactions.create(
            TransactionDraft(account.id, date, -cost, event.location ?: event.title, listOf(SplitDraft(categoryId, -cost)), event.title, memberId = event.memberId),
        )
        books.ledger(group).eventActivitiesQueries.setActivityCostTxn(eventId, date.toString(), txn.id)
        return txn.id
    }

    /**
     * CAL-04: events, bills and health due dates between [from] and [to], in date order. Paid bills
     * and cancelled events are included so the calendar shows the full picture.
     */
    fun items(from: LocalDate, to: LocalDate): List<CalendarItem> =
        (occurrences(from, to).map { CalendarItem.Event(it) } +
            books.bills.occurrences(from, to).map { CalendarItem.Bill(it) } +
            books.health.due(from, to).map { CalendarItem.Health(it) } +
            renewals(from, to).map { CalendarItem.Renewal(it) } +
            books.upkeepBetween(from, to, books.today()).map { CalendarItem.Maintenance(it) } +
            books.schedules.days(from, to).map { CalendarItem.Schedule(it) } +
            broughtIn(from, to))
            .sortedBy { it.date }

    /** CSY-04: brought-in items, one per day they cover. */
    private fun broughtIn(from: LocalDate, to: LocalDate): List<CalendarItem> =
        books.broughtIn.items(from, to).flatMap { item -> books.broughtIn.days(item, from, to).map { CalendarItem.Imported(item, it) } }

    /**
     * Renewal dates between [from] and [to], for the calendar. Card payments are announced only a
     * few days ahead among the reminders, so the calendar asks for every due date of the period.
     */
    private fun renewals(from: LocalDate, to: LocalDate): List<Renewal> =
        (books.renewals(from, from.daysUntil(to)).filter { it.kind != RenewalKind.CARD_PAYMENT_DUE } + books.creditCards.paymentsDue(from, to))
            .filter { it.date in from..to }

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
        validate(draft.cost == null || draft.cost.isPositive, "error.amountPositive")
    }

    /** ACTIVITY is kept as PERSONAL with the activity flag (the table's categories are fixed). */
    private fun storedCategory(c: EventCategory) = if (c == EventCategory.ACTIVITY) EventCategory.PERSONAL.name else c.name

    private fun driver(memberId: String?, name: String?): Driver? = when {
        memberId != null -> Driver(memberId = memberId)
        !name.isNullOrBlank() -> Driver(name = name)
        else -> null
    }

    private fun locate(eventId: String): Pair<GroupInfo, CalendarEvent> {
        for (group in books.groups()) {
            val row = books.ledger(group).calendarQueries.eventById(eventId).executeAsOneOrNull() ?: continue
            return group to row.toEvent(group.id)
        }
        throw AccessDeniedException("Event not found or not accessible")
    }

    private fun EventRow.toEvent(groupId: String) = CalendarEvent(
        id, groupId, title, if (activity == 1L) EventCategory.ACTIVITY else EventCategory.valueOf(category), LocalDate.parse(start_date), start_time?.let(LocalTime::parse),
        duration_minutes?.toInt(), location, notes, member_id, provider_id, account_id, recurrence?.let(Recurrence::decode),
        end_date?.let(LocalDate::parse), reminder_minutes.split(',').mapNotNull { it.trim().toIntOrNull() },
        driver(driver_there_member_id, driver_there_name), driver(driver_back_member_id, driver_back_name),
        cost_minor?.let { Money.ofMinor(it, cost_currency?.let(Currency::of) ?: Currency.CAD) },
    )

    private fun time(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

    companion object {
        fun minutesBetween(from: LocalDateTime, to: LocalDateTime): Long {
            val days = to.date.toEpochDays() - from.date.toEpochDays()
            return days * 1440L + (to.time.hour * 60 + to.time.minute) - (from.time.hour * 60 + from.time.minute)
        }
    }
}
