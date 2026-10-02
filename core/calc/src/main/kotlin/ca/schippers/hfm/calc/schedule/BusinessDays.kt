package ca.schippers.hfm.calc.schedule

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Days when Canadian banks do not process payments, as observed in Quebec: weekends, the
 * federal bank holidays and Quebec's Fête nationale. Used for "last business day" bills and to
 * predict when a pre-authorized debit actually leaves the account (BILL-02, BILL-07).
 *
 * A holiday falling on a weekend is observed on the following Monday (Christmas and Boxing Day
 * on a weekend move to the next two weekdays).
 */
object BusinessDays {

    fun isBusinessDay(date: LocalDate): Boolean =
        date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY && date !in holidays(date.year)

    fun previousOrSame(date: LocalDate): LocalDate {
        var d = date
        while (!isBusinessDay(d)) d = d.minus(DatePeriod(days = 1))
        return d
    }

    fun nextOrSame(date: LocalDate): LocalDate {
        var d = date
        while (!isBusinessDay(d)) d = d.plus(DatePeriod(days = 1))
        return d
    }

    fun lastBusinessDayOfMonth(year: Int, month: Month): LocalDate = previousOrSame(lastDayOfMonth(year, month))

    private val cache = java.util.concurrent.ConcurrentHashMap<Int, Set<LocalDate>>()

    fun holidays(year: Int): Set<LocalDate> = cache.getOrPut(year) {
        val easter = easterSunday(year)
        buildSet {
            add(observed(LocalDate(year, Month.JANUARY, 1)))
            add(easter.minus(DatePeriod(days = 2))) // Good Friday
            add(mondayBefore(LocalDate(year, Month.MAY, 25))) // Victoria Day / Journée nationale des patriotes
            add(observed(LocalDate(year, Month.JUNE, 24))) // Fête nationale du Québec
            add(observed(LocalDate(year, Month.JULY, 1))) // Canada Day
            add(nthMonday(year, Month.SEPTEMBER, 1)) // Labour Day
            if (year >= 2021) add(observed(LocalDate(year, Month.SEPTEMBER, 30))) // Truth and Reconciliation
            add(nthMonday(year, Month.OCTOBER, 2)) // Thanksgiving
            add(observed(LocalDate(year, Month.NOVEMBER, 11))) // Remembrance Day
            val christmas = observed(LocalDate(year, Month.DECEMBER, 25))
            add(christmas)
            var boxing = observed(LocalDate(year, Month.DECEMBER, 26))
            if (boxing <= christmas) boxing = christmas.plus(DatePeriod(days = 1))
            add(boxing)
        }
    }

    /** Easter Sunday by the anonymous Gregorian algorithm. */
    fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate(year, month, day)
    }

    fun lastDayOfMonth(year: Int, month: Month): LocalDate {
        val first = LocalDate(year, month, 1)
        return first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
    }

    private fun observed(date: LocalDate): LocalDate = when (date.dayOfWeek) {
        DayOfWeek.SATURDAY -> date.plus(DatePeriod(days = 2))
        DayOfWeek.SUNDAY -> date.plus(DatePeriod(days = 1))
        else -> date
    }

    private fun mondayBefore(date: LocalDate): LocalDate {
        var d = date.minus(DatePeriod(days = 1))
        while (d.dayOfWeek != DayOfWeek.MONDAY) d = d.minus(DatePeriod(days = 1))
        return d
    }

    private fun nthMonday(year: Int, month: Month, n: Int): LocalDate {
        var d = LocalDate(year, month, 1)
        while (d.dayOfWeek != DayOfWeek.MONDAY) d = d.plus(DatePeriod(days = 1))
        return d.plus(DatePeriod(days = 7 * (n - 1)))
    }
}
