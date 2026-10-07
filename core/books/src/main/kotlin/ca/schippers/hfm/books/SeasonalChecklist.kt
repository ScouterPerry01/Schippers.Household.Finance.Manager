package ca.schippers.hfm.books

import ca.schippers.hfm.calc.schedule.DueState
import ca.schippers.hfm.calc.schedule.Season
import ca.schippers.hfm.calc.schedule.SeasonWindow
import ca.schippers.hfm.calc.schedule.Seasons
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import java.math.BigDecimal

/** SEA-02: where a task stands in a season's checklist. TO_DO is not due yet, or in a season still to come. */
enum class ChecklistState { DONE, DUE, SOON, TO_DO }

/**
 * SEA-02: one task in a season's checklist, on a vehicle ([vehicle]) or another asset. [dueDate] is
 * when it falls due in the season (or before it, when overdue); [doneOn] the last time it was done
 * in the season. [unit] is what a reading counts when one may be given with the tick (a vehicle's
 * km, an asset's meter), null when none.
 */
data class ChecklistItem(
    val subjectId: String,
    val subjectName: String,
    val vehicle: Boolean,
    val taskId: String,
    val taskName: String,
    val dueDate: LocalDate?,
    val state: ChecklistState,
    val doneOn: LocalDate?,
    val currency: Currency,
    val unit: MeterUnit?,
    /**
     * SEA-02: for a done task that repeats within the season (weekly pool water, a monthly filter),
     * when it falls due again; it is DONE until then, and DUE from that day. Null when it is not
     * due again this season.
     */
    val again: LocalDate? = null,
)

/** SEA-02: a season's checklist and how far along it is ("7 of 12 done"). */
data class SeasonalChecklist(val window: SeasonWindow, val items: List<ChecklistItem>) {
    val done: Int get() = items.count { it.state == ChecklistState.DONE }
    val total: Int get() = items.size
}

/**
 * SEA-02, SEA-04: the seasonal checklist: every task of a season across vehicles, homes, cottages,
 * pools, yards and other assets, with its due date; ticking one records it in the service log, as
 * "Mark done" does. A task is in a season when it falls due in it, or was done in it; for the
 * current season, overdue tasks are in it too.
 */
class SeasonalChecklistService internal constructor(private val books: Books) {

    /** The four seasons, the current one first. */
    fun seasons(today: LocalDate): List<SeasonWindow> = Seasons.coming(today)

    /** The checklist of [season], as it is now or will next be. */
    fun checklist(season: Season, today: LocalDate): SeasonalChecklist = checklist(Seasons.window(season, today), today)

    fun checklist(window: SeasonWindow, today: LocalDate): SeasonalChecklist {
        val current = today in window
        val items = ArrayList<ChecklistItem>()
        // Each vehicle and asset read once from its own group.
        val groups = books.groups().associateBy { it.id }
        for (v in books.vehicles.list()) {
            val g = groups[v.groupId] ?: continue
            val done = doneDates(books.vehicles.services(g, v).map { it.date to it.taskIds })
            for (s in books.vehicles.taskStatuses(g, v, today)) {
                val t = s.task
                val state = DueState.valueOf(s.state.name)
                item(window, current, s.nextDate, state, t.intervalMonths, null, null, done[t.id].orEmpty())?.let { i ->
                    items += ChecklistItem(v.id, v.name, true, t.id, t.name, i.due, i.state, i.doneOn, v.currency, MeterUnit.KM, i.again)
                }
            }
        }
        for (a in books.assets.list()) {
            val g = groups[a.groupId] ?: continue
            val done = doneDates(books.assetMaintenance.services(g, a).map { it.date to it.taskIds })
            for (s in books.assetMaintenance.taskStatuses(g, a, today)) {
                val t = s.task
                item(window, current, s.due.nextDate, s.due.state, t.intervalMonths, t.intervalWeeks, t.part, done[t.id].orEmpty())?.let { i ->
                    items += ChecklistItem(a.id, a.name, false, t.id, t.name, i.due, i.state, i.doneOn, a.currency, a.meter, i.again)
                }
            }
        }
        return SeasonalChecklist(window, items.sortedWith(compareBy<ChecklistItem, LocalDate?>(nullsLast()) { it.dueDate ?: it.doneOn }.thenBy { it.subjectName }.thenBy { it.taskName }))
    }

    /**
     * SEA-02, SEA-04: records [item] as done on [date]: a service in its log, with the [note], the
     * [cost] in the vehicle's or asset's currency, and the odometer or meter [reading] when given.
     */
    fun tick(item: ChecklistItem, date: LocalDate, note: String? = null, cost: BigDecimal? = null, reading: Int? = null) =
        record(item.vehicle, item.subjectId, item.taskId, date, note, cost, reading)

    /**
     * [tick] by ids, for a tick made on the phone; [vehicle] tells which log the task is in. A task
     * already recorded as done on [date] (ticked on the computer and on a phone) is not recorded twice.
     */
    fun record(vehicle: Boolean, subjectId: String, taskId: String, date: LocalDate, note: String?, cost: BigDecimal?, reading: Int?) {
        validate(cost == null || cost.signum() >= 0, "error.invalidNumber")
        validate(reading == null || reading >= 0, "error.invalidNumber")
        if (!mayTick(vehicle, subjectId)) throw ca.schippers.hfm.data.AccessDeniedException("You may not add to this account group")
        if (vehicle) {
            val v = books.vehicles.get(subjectId)
            validate(books.vehicles.tasks(subjectId).any { it.id == taskId }, "error.taskGone")
            if (books.vehicles.services(subjectId).any { it.date == date && taskId in it.taskIds }) return
            books.vehicles.saveService(
                ServiceRecord("", subjectId, date, reading, notes = note?.trim()?.ifEmpty { null }, cost = cost?.let { Money.exact(it, v.currency) }, taskIds = setOf(taskId)),
            )
        } else {
            val a = books.assets.get(subjectId)
            validate(books.assetMaintenance.tasks(subjectId).any { it.id == taskId }, "error.taskGone")
            if (books.assetMaintenance.services(subjectId).any { it.date == date && taskId in it.taskIds }) return
            books.assetMaintenance.saveService(
                AssetServiceRecord(
                    "", subjectId, date, reading.takeIf { a.meter != null }, notes = note?.trim()?.ifEmpty { null }, cost = cost?.let { Money.exact(it, a.currency) },
                    taskIds = setOf(taskId),
                ),
            )
        }
    }

    /** Whether the signed-in user may record a service of the vehicle or asset [subjectId]: the right to add in its group. */
    fun mayTick(vehicle: Boolean, subjectId: String): Boolean {
        val groupId = if (vehicle) books.vehicles.get(subjectId).groupId else books.assets.get(subjectId).groupId
        return books.group(groupId).level.allows(ca.schippers.hfm.domain.PermissionLevel.CAPTURE_ONLY)
    }

    /** The dates each task was done, from a log of (date, tasks done). */
    private fun doneDates(log: List<Pair<LocalDate, Set<String>>>): Map<String, List<LocalDate>> =
        log.flatMap { (date, tasks) -> tasks.map { it to date } }.groupBy({ it.first }, { it.second })

    /** Where a task stands in a season: see [ChecklistItem]. */
    private data class Placed(val due: LocalDate?, val state: ChecklistState, val doneOn: LocalDate?, val again: LocalDate?)

    /**
     * Whether a task is in the season and how it stands there: its due date (moved on by its
     * interval into a season still to come), its state, and when it was done in the season. A task
     * that repeats within the season is done for its current occurrence only: once its next date
     * comes, it is due again (SEA-02), so a weekly task is not done for the whole season after one tick.
     */
    private fun item(
        window: SeasonWindow,
        current: Boolean,
        next: LocalDate?,
        state: DueState,
        months: Int?,
        weeks: Int?,
        part: Pair<Pair<Int, Int>, Pair<Int, Int>>?,
        done: List<LocalDate>,
    ): Placed? {
        val doneOn = done.filter { it in window }.maxOrNull()
        var due = next
        if (next != null && !current && (months != null || weeks != null)) {
            var d: LocalDate = next
            var steps = 0
            while (d < window.start && steps++ < MAX_STEPS) {
                val moved = listOfNotNull(months?.let { d.plus(DatePeriod(months = it)) }, weeks?.let { d.plus(DatePeriod(days = 7 * it)) }).min()
                d = part?.let { Seasons.intoPart(moved, it.first, it.second) } ?: moved
            }
            due = d
        }
        val dueInSeason = due != null && due < window.end && (current || due >= window.start)
        if (!dueInSeason && doneOn == null) return null
        val st = when {
            doneOn != null && !(current && state == DueState.DUE) -> ChecklistState.DONE
            !current -> ChecklistState.TO_DO
            state == DueState.DUE -> ChecklistState.DUE
            state == DueState.SOON -> ChecklistState.SOON
            else -> ChecklistState.TO_DO
        }
        // Done, and due again before the season ends: the next occurrence's date.
        val again = due.takeIf { st == ChecklistState.DONE && current && dueInSeason && doneOn != null && it != null && it > doneOn }
        return Placed(due.takeIf { dueInSeason }, st, doneOn, again)
    }

    companion object {
        /** A weekly task moved on over a year at most. */
        private const val MAX_STEPS = 60
    }
}
