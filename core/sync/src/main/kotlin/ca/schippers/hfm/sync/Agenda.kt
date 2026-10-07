package ca.schippers.hfm.sync

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** The agenda's kinds of item: each has its colour in the month view. */
enum class AgendaKind { EVENT, SCHEDULE, BILL, HEALTH, MAINTENANCE, SEASONAL, RENEWAL, TANK, PHONE }

/**
 * One line of the phone's agenda, on [date]. [time] ("HH:mm") is set for an item at a time of day; the
 * others come first in their day. [late] marks an item due before today, shown on today.
 */
sealed interface AgendaItem {
    val date: LocalDate
    val kind: AgendaKind
    val time: String?
    val title: String

    /** CAL-03, CAL-11: an appointment or event, with its place, who it is for and the drivers. */
    data class Event(override val date: LocalDate, val event: RefEvent) : AgendaItem {
        override val kind get() = AgendaKind.EVENT
        override val time get() = event.time
        override val title get() = event.title
    }

    /** CAL-10: a person's work or school hours. */
    data class Schedule(override val date: LocalDate, val schedule: RefSchedule) : AgendaItem {
        override val kind get() = AgendaKind.SCHEDULE
        override val time get() = schedule.start
        override val title get() = schedule.person
    }

    data class Bill(override val date: LocalDate, val bill: RefBill) : AgendaItem {
        override val kind get() = AgendaKind.BILL
        override val time: String? get() = null
        override val title get() = bill.name
    }

    /** HLT-03: a medication to refill, or to renew. */
    data class Refill(override val date: LocalDate, val refill: RefRefill, val late: Boolean) : AgendaItem {
        override val kind get() = AgendaKind.HEALTH
        override val time: String? get() = null
        override val title get() = refill.medication
    }

    /** MNT-05: a maintenance task's next due date; [late] when overdue or due by use, with no date. */
    data class Maintenance(override val date: LocalDate, val due: RefDue, val late: Boolean) : AgendaItem {
        override val kind get() = AgendaKind.MAINTENANCE
        override val time: String? get() = null
        override val title get() = "${due.subject}: ${due.task}"
    }

    /** SEA-04: a task of the season's checklist. */
    data class Seasonal(override val date: LocalDate, val task: RefSeasonalTask, val late: Boolean) : AgendaItem {
        override val kind get() = AgendaKind.SEASONAL
        override val time: String? get() = null
        override val title get() = "${task.subject}: ${task.task}"
    }

    data class Renewal(override val date: LocalDate, val renewal: RefRenewal) : AgendaItem {
        override val kind get() = AgendaKind.RENEWAL
        override val time: String? get() = null
        override val title get() = renewal.subject
    }

    /** UTL-02: the date to order fuel for a tank ([late]: order now). */
    data class TankOrder(override val date: LocalDate, val tank: RefTank, val late: Boolean) : AgendaItem {
        override val kind get() = AgendaKind.TANK
        override val time: String? get() = null
        override val title get() = tank.name
    }

    /**
     * CSY-01: an item of one of the phone's own calendars the user brings in, read on the phone for the agenda
     * only. [continued] for an item at a time that began on an earlier day.
     */
    data class Phone(override val date: LocalDate, val calendar: ChosenCalendar, val item: CalendarInstance, val continued: Boolean = false) : AgendaItem {
        override val kind get() = AgendaKind.PHONE
        override val time get() = if (continued) null else item.startTime
        override val title get() = item.title
    }
}

/** One day of the agenda that has something on it. */
data class AgendaDay(val date: LocalDate, val items: List<AgendaItem>)

/**
 * The phone's agenda: day by day for [DAYS] days from today, what the computer sent (events, hours,
 * bills, refills, maintenance, the season's tasks, renewals, fuel orders) and the items of the phone's
 * own calendars the user brings in. Plain Kotlin, so it is tested here; read-only.
 */
object Agenda {
    /** Today and the 59 days after it. */
    const val DAYS = 60

    fun lastDay(today: LocalDate): LocalDate = today.plusDays(DAYS - 1L)

    /**
     * Everything on the agenda between [today] and [lastDay]. What is overdue (a refill, maintenance, a
     * seasonal task, a fuel order) is shown on today; bills and events of past days are left out.
     */
    fun items(reference: ReferenceData?, phone: List<Pair<ChosenCalendar, CalendarInstance>>, today: LocalDate): List<AgendaItem> {
        val last = lastDay(today)
        val out = ArrayList<AgendaItem>()
        fun inWindow(d: LocalDate?) = d != null && !d.isBefore(today) && !d.isAfter(last)
        if (reference != null) {
            for (e in reference.events) date(e.date)?.takeIf(::inWindow)?.let { out += AgendaItem.Event(it, e) }
            for (s in reference.schedules) date(s.date)?.takeIf(::inWindow)?.let { out += AgendaItem.Schedule(it, s) }
            for (b in reference.bills) date(b.dueDate)?.takeIf(::inWindow)?.let { out += AgendaItem.Bill(it, b) }
            for (r in reference.refills) {
                val d = date(r.dueDate) ?: continue
                if (!d.isAfter(last)) out += AgendaItem.Refill(maxOf(d, today), r, d.isBefore(today))
            }
            // A task in this season's checklist is shown once, as a seasonal task.
            val seasonalIds = reference.seasonal?.tasks.orEmpty().map { it.taskId }.toSet()
            for (m in (reference.maintenance + reference.maintenanceAhead).distinctBy { it.taskId }.filter { it.taskId !in seasonalIds }) {
                val d = date(m.dueDate)
                when {
                    d == null -> if (m.state == "DUE" || m.state == "SOON") out += AgendaItem.Maintenance(today, m, true)
                    !d.isAfter(last) -> out += AgendaItem.Maintenance(maxOf(d, today), m, d.isBefore(today))
                }
            }
            reference.seasonal?.let { season ->
                for (t in season.tasks) {
                    // A done task that repeats falls due again on its "again" date; a task never done, on its due date.
                    val d = if (t.state == "DONE") date(t.again) else date(t.dueDate) ?: today.takeIf { t.state == "DUE" }
                    if (d != null && !d.isAfter(last)) out += AgendaItem.Seasonal(maxOf(d, today), t, d.isBefore(today))
                }
            }
            for (r in reference.renewals) date(r.date)?.takeIf(::inWindow)?.let { out += AgendaItem.Renewal(it, r) }
            // A tank's order date comes with the renewals from computers that send them; else from the tank itself.
            val ordered = reference.renewals.filter { it.kind == "FUEL_ORDER" }.map { it.subjectId }.toSet()
            for (t in reference.trackers.tanks) {
                if (t.id in ordered) continue
                val d = date(t.orderDate) ?: continue
                if (!d.isAfter(last)) out += AgendaItem.TankOrder(maxOf(d, today), t, d.isBefore(today))
            }
        }
        for ((calendar, item) in phone) {
            val start = date(item.startDate) ?: continue
            val end = date(item.endDate)?.let { maxOf(it, start) } ?: start
            if (item.startTime == null) {
                // All day: on each day it covers.
                var d = maxOf(start, today)
                while (!d.isAfter(minOf(end, last))) {
                    out += AgendaItem.Phone(d, calendar, item)
                    d = d.plusDays(1)
                }
            } else if (inWindow(start)) {
                out += AgendaItem.Phone(start, calendar, item)
            } else if (start.isBefore(today) && !end.isBefore(today)) {
                out += AgendaItem.Phone(today, calendar, item, continued = true)
            }
        }
        return out
    }

    /** The days that have something on them, today first; in each day, items without a time first, then by time. */
    fun days(items: List<AgendaItem>): List<AgendaDay> =
        items.groupBy { it.date }.toSortedMap().map { (date, list) -> AgendaDay(date, list.sortedWith(ORDER)) }

    /** The month view: each day's kinds, one per kind present, in [AgendaKind] order, and how many items it has. */
    fun marks(days: List<AgendaDay>): Map<LocalDate, Pair<List<AgendaKind>, Int>> =
        days.associate { d -> d.date to (d.items.map { it.kind }.distinct().sorted() to d.items.size) }

    /** The months the agenda reaches: the current one, and the next while the window lasts. */
    fun months(today: LocalDate): List<YearMonth> {
        val first = YearMonth.from(today)
        val last = YearMonth.from(lastDay(today))
        return generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }.toList()
    }

    /** The month's grid, week by week from [firstDayOfWeek]: blanks (null) before the 1st and after the last day. */
    fun grid(month: YearMonth, firstDayOfWeek: DayOfWeek): List<LocalDate?> {
        val first = month.atDay(1)
        val lead = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val cells = ArrayList<LocalDate?>()
        repeat(lead) { cells += null }
        for (d in 1..month.lengthOfMonth()) cells += month.atDay(d)
        while (cells.size % 7 != 0) cells += null
        return cells
    }

    /** Where the agenda list goes when a day is tapped: that day, or the next one with something on it; null past the last. */
    fun indexOf(days: List<AgendaDay>, date: LocalDate): Int? = days.indexOfFirst { !it.date.isBefore(date) }.takeIf { it >= 0 }

    private val ORDER = compareBy<AgendaItem>({ it.time != null }, { it.time ?: "" }, { it.kind.ordinal }, { it.title.lowercase() })

    private fun date(text: String?): LocalDate? = text?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
