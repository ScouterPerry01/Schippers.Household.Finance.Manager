package ca.schippers.hfm.calc.schedule

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** BILL-02 recurrence patterns. Quarterly, semi-annual and annual are monthly with an interval of 3, 6 and 12. */
enum class Frequency { ONCE, DAILY, WEEKLY, SEMI_MONTHLY, MONTHLY }

/** What happens when a due date falls on a weekend or bank holiday. */
enum class BusinessDayAdjust { NONE, PREVIOUS, NEXT }

/** Which day of the month a monthly schedule uses. */
enum class MonthDay { SAME_DAY, LAST_DAY, LAST_BUSINESS_DAY }

/**
 * A repeating schedule, anchored on a start date.
 *
 * - DAILY / WEEKLY: every [interval] days or weeks from the start (bi-weekly is WEEKLY with interval 2).
 * - MONTHLY: every [interval] months, on the start date's day ([MonthDay.SAME_DAY], shortened in
 *   short months), the last day, or the last business day of the month.
 * - SEMI_MONTHLY: on the start date's day and on [secondDay] each month (0 means the last day),
 *   e.g. the 1st and 15th, or the 15th and the last day.
 */
data class Recurrence(
    val frequency: Frequency,
    val interval: Int = 1,
    val monthDay: MonthDay = MonthDay.SAME_DAY,
    val secondDay: Int? = null,
    val adjust: BusinessDayAdjust = BusinessDayAdjust.NONE,
) {
    init {
        require(interval >= 1) { "Interval must be at least 1" }
        require(frequency != Frequency.SEMI_MONTHLY || secondDay != null) { "Semi-monthly needs a second day" }
        require(secondDay == null || secondDay in 0..31) { "Second day must be 0 (last) to 31" }
    }

    /** Due dates from [start] on, that fall within [from]..[to], at most [limit] of them. */
    fun occurrences(start: LocalDate, from: LocalDate, to: LocalDate, end: LocalDate? = null, limit: Int = 1000): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        val last = if (end != null && end < to) end else to
        for (raw in rawDates(start)) {
            val date = adjusted(raw)
            if (raw > last && date > last) break
            if (date in from..last) out += date
            if (out.size >= limit) break
        }
        return out.distinct()
    }

    /** The first due date on or after [date]. */
    fun next(start: LocalDate, date: LocalDate, end: LocalDate? = null): LocalDate? =
        occurrences(start, date, LocalDate.fromEpochDays(date.toEpochDays() + 400 * interval), end, limit = 1).firstOrNull()

    /** Average number of occurrences per year, for annual cost of subscriptions (BILL-10). */
    val perYear: Double
        get() = when (frequency) {
            Frequency.ONCE -> 0.0
            Frequency.DAILY -> 365.25 / interval
            Frequency.WEEKLY -> 365.25 / 7 / interval
            Frequency.SEMI_MONTHLY -> 24.0
            Frequency.MONTHLY -> 12.0 / interval
        }

    private fun adjusted(date: LocalDate): LocalDate = when (adjust) {
        BusinessDayAdjust.NONE -> date
        BusinessDayAdjust.PREVIOUS -> BusinessDays.previousOrSame(date)
        BusinessDayAdjust.NEXT -> BusinessDays.nextOrSame(date)
    }

    private fun rawDates(start: LocalDate): Sequence<LocalDate> = when (frequency) {
        Frequency.ONCE -> sequenceOf(start)
        Frequency.DAILY -> generateSequence(start) { it.plus(DatePeriod(days = interval)) }
        Frequency.WEEKLY -> generateSequence(start) { it.plus(DatePeriod(days = 7 * interval)) }
        Frequency.MONTHLY -> generateSequence(0) { it + interval }.map { months -> monthDate(start, months) }
            .filter { it >= start }
        Frequency.SEMI_MONTHLY -> generateSequence(0) { it + 1 }.flatMap { months ->
            val first = monthDate(start, months, MonthDay.SAME_DAY)
            val month = start.plus(DatePeriod(months = months))
            val second = if (secondDay == 0) {
                BusinessDays.lastDayOfMonth(month.year, month.month)
            } else {
                clampDay(month.year, month.month, secondDay!!)
            }
            listOf(first, second).sorted().asSequence()
        }.filter { it >= start }
    }

    private fun monthDate(start: LocalDate, months: Int, day: MonthDay = monthDay): LocalDate {
        val firstOfMonth = LocalDate(start.year, start.month, 1).plus(DatePeriod(months = months))
        return when (day) {
            MonthDay.SAME_DAY -> clampDay(firstOfMonth.year, firstOfMonth.month, start.day)
            MonthDay.LAST_DAY -> BusinessDays.lastDayOfMonth(firstOfMonth.year, firstOfMonth.month)
            MonthDay.LAST_BUSINESS_DAY -> BusinessDays.lastBusinessDayOfMonth(firstOfMonth.year, firstOfMonth.month)
        }
    }

    private fun clampDay(year: Int, month: kotlinx.datetime.Month, day: Int): LocalDate {
        val last = BusinessDays.lastDayOfMonth(year, month).day
        return LocalDate(year, month, minOf(day, last))
    }

    /** Compact text form for storage, e.g. "MONTHLY;INTERVAL=3;DAY=LAST_BUSINESS_DAY;ADJUST=NEXT". */
    fun encode(): String = buildList {
        add(frequency.name)
        if (interval != 1) add("INTERVAL=$interval")
        if (monthDay != MonthDay.SAME_DAY) add("DAY=${monthDay.name}")
        secondDay?.let { add("SECOND=$it") }
        if (adjust != BusinessDayAdjust.NONE) add("ADJUST=${adjust.name}")
    }.joinToString(";")

    companion object {
        fun decode(text: String): Recurrence {
            val parts = text.split(';')
            val values = parts.drop(1).associate { it.substringBefore('=') to it.substringAfter('=') }
            return Recurrence(
                frequency = Frequency.valueOf(parts.first()),
                interval = values["INTERVAL"]?.toInt() ?: 1,
                monthDay = values["DAY"]?.let(MonthDay::valueOf) ?: MonthDay.SAME_DAY,
                secondDay = values["SECOND"]?.toInt(),
                adjust = values["ADJUST"]?.let(BusinessDayAdjust::valueOf) ?: BusinessDayAdjust.NONE,
            )
        }

        val WEEKLY = Recurrence(Frequency.WEEKLY)
        val BI_WEEKLY = Recurrence(Frequency.WEEKLY, interval = 2)
        val MONTHLY = Recurrence(Frequency.MONTHLY)
        val QUARTERLY = Recurrence(Frequency.MONTHLY, interval = 3)
        val SEMI_ANNUAL = Recurrence(Frequency.MONTHLY, interval = 6)
        val ANNUAL = Recurrence(Frequency.MONTHLY, interval = 12)
    }
}
