package ca.schippers.hfm.calc.schedule

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** MNT-01, MNT-03: whether a maintenance task is due, soon due, or fine. */
enum class DueState { OK, SOON, DUE }

/**
 * When a task is next due: by date, by usage (kilometres or engine hours), and the date the usage
 * should be reached at the usual rate.
 */
data class DueStatus(val dueDate: LocalDate?, val dueUsage: Int?, val forecastDate: LocalDate?, val state: DueState) {
    /** The earlier of the date and the forecast. */
    val nextDate: LocalDate? get() = listOfNotNull(dueDate, forecastDate).minOrNull()
}

/** MNT-01, MNT-03: schedules that repeat by time, by usage, or whichever comes first. */
object MaintenanceSchedule {

    /**
     * The status of a task last done on [lastDate] at [lastUsage], repeating every
     * [intervalMonths] and/or every [intervalUsage]. [currentUsage] is the latest reading and
     * [usagePerDay] the usual rate, used to forecast when the usage will be reached. A task is
     * SOON within [remindDays] days or [remindUsage] units of being due.
     */
    fun status(
        lastDate: LocalDate?,
        lastUsage: Int?,
        intervalMonths: Int?,
        intervalUsage: Int?,
        currentUsage: Int?,
        usagePerDay: Double?,
        remindDays: Int,
        remindUsage: Int,
        today: LocalDate,
    ): DueStatus {
        val dueDate = intervalMonths?.let { m -> lastDate?.plus(DatePeriod(months = m)) }
        val dueUsage = intervalUsage?.let { u -> lastUsage?.plus(u) }
        val forecast = if (dueUsage != null && currentUsage != null && usagePerDay != null && usagePerDay > 0) {
            today.plus(DatePeriod(days = ((dueUsage - currentUsage) / usagePerDay).toInt().coerceAtLeast(0)))
        } else {
            null
        }
        val overdue = (dueDate != null && dueDate <= today) || (dueUsage != null && currentUsage != null && currentUsage >= dueUsage)
        val soon = (dueDate != null && today.daysUntil(dueDate) <= remindDays) ||
            (dueUsage != null && currentUsage != null && dueUsage - currentUsage <= remindUsage) ||
            (forecast != null && today.daysUntil(forecast) <= remindDays)
        return DueStatus(dueDate, dueUsage, forecast, if (overdue) DueState.DUE else if (soon) DueState.SOON else DueState.OK)
    }

    /**
     * Average usage per day over the last year of readings (date, value), at least two weeks
     * apart; null without enough readings.
     */
    fun usagePerDay(readings: List<Pair<LocalDate, Int>>): Double? {
        val last = readings.maxByOrNull { it.second } ?: return null
        val since = last.first.minus(DatePeriod(years = 1))
        val first = readings.filter { it.first >= since }.minByOrNull { it.first } ?: return null
        val days = first.first.daysUntil(last.first)
        if (days < 14 || last.second <= first.second) return null
        return (last.second - first.second).toDouble() / days
    }

    /** MNT-01: the first time a seasonal task falls due: its month and day this year, or next year if past. */
    fun nextSeason(month: Int, day: Int, today: LocalDate): LocalDate =
        LocalDate(today.year, month, day).let { if (it < today) it.plus(DatePeriod(years = 1)) else it }
}
