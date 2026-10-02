package ca.schippers.hfm.calc.schedule

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BusinessDaysTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)

    @Test
    fun `Easter dates`() {
        assertEquals(d(2024, 3, 31), BusinessDays.easterSunday(2024))
        assertEquals(d(2025, 4, 20), BusinessDays.easterSunday(2025))
        assertEquals(d(2026, 4, 5), BusinessDays.easterSunday(2026))
        assertEquals(d(2027, 3, 28), BusinessDays.easterSunday(2027))
    }

    @Test
    fun `2026 bank holidays observed in Quebec`() {
        val expected = setOf(
            d(2026, 1, 1), d(2026, 4, 3), d(2026, 5, 18), d(2026, 6, 24), d(2026, 7, 1),
            d(2026, 9, 7), d(2026, 9, 30), d(2026, 10, 12), d(2026, 11, 11), d(2026, 12, 25), d(2026, 12, 28),
        )
        assertEquals(expected, BusinessDays.holidays(2026))
    }

    @Test
    fun `weekend holidays move to weekdays`() {
        // 2027: Christmas on Saturday, Boxing Day on Sunday -> Monday 27 and Tuesday 28.
        assertTrue(d(2027, 12, 27) in BusinessDays.holidays(2027))
        assertTrue(d(2027, 12, 28) in BusinessDays.holidays(2027))
        // 2029: Canada Day on Sunday -> Monday July 2.
        assertTrue(d(2029, 7, 2) in BusinessDays.holidays(2029))
    }

    @Test
    fun `last business day of the month`() {
        assertEquals(d(2026, 1, 30), BusinessDays.lastBusinessDayOfMonth(2026, kotlinx.datetime.Month.JANUARY)) // 31st is a Saturday
        assertEquals(d(2026, 12, 31), BusinessDays.lastBusinessDayOfMonth(2026, kotlinx.datetime.Month.DECEMBER))
        assertEquals(d(2026, 10, 30), BusinessDays.lastBusinessDayOfMonth(2026, kotlinx.datetime.Month.OCTOBER))
        assertFalse(BusinessDays.isBusinessDay(d(2026, 6, 24)))
    }
}

class RecurrenceTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)
    private val year = d(2026, 1, 1) to d(2026, 12, 31)

    @Test
    fun `monthly on the 31st is shortened in short months`() {
        val dates = Recurrence.MONTHLY.occurrences(d(2026, 1, 31), year.first, d(2026, 4, 30))
        assertEquals(listOf(d(2026, 1, 31), d(2026, 2, 28), d(2026, 3, 31), d(2026, 4, 30)), dates)
    }

    @Test
    fun `bi-weekly pay`() {
        val dates = Recurrence.BI_WEEKLY.occurrences(d(2026, 1, 2), year.first, d(2026, 2, 28))
        assertEquals(listOf(d(2026, 1, 2), d(2026, 1, 16), d(2026, 1, 30), d(2026, 2, 13), d(2026, 2, 27)), dates)
    }

    @Test
    fun `semi-monthly on the 15th and the last day`() {
        val rule = Recurrence(Frequency.SEMI_MONTHLY, secondDay = 0)
        val dates = rule.occurrences(d(2026, 1, 15), year.first, d(2026, 2, 28))
        assertEquals(listOf(d(2026, 1, 15), d(2026, 1, 31), d(2026, 2, 15), d(2026, 2, 28)), dates)
    }

    @Test
    fun `last business day`() {
        val rule = Recurrence(Frequency.MONTHLY, monthDay = MonthDay.LAST_BUSINESS_DAY)
        val dates = rule.occurrences(d(2026, 1, 1), year.first, d(2026, 6, 30))
        assertEquals(listOf(d(2026, 1, 30), d(2026, 2, 27), d(2026, 3, 31), d(2026, 4, 30), d(2026, 5, 29), d(2026, 6, 30)), dates)
    }

    @Test
    fun `pre-authorized debit on a holiday moves to the next business day`() {
        val rule = Recurrence(Frequency.MONTHLY, adjust = BusinessDayAdjust.NEXT)
        // July 1 2026 is Canada Day (Wednesday) -> July 2; Aug 1 is a Saturday -> Monday Aug 3.
        val dates = rule.occurrences(d(2026, 6, 1), d(2026, 6, 1), d(2026, 8, 31))
        assertEquals(listOf(d(2026, 6, 1), d(2026, 7, 2), d(2026, 8, 3)), dates)
    }

    @Test
    fun `quarterly and annual`() {
        assertEquals(listOf(d(2026, 2, 1), d(2026, 5, 1), d(2026, 8, 1), d(2026, 11, 1)), Recurrence.QUARTERLY.occurrences(d(2026, 2, 1), year.first, year.second))
        assertEquals(listOf(d(2026, 7, 1)), Recurrence.ANNUAL.occurrences(d(2025, 7, 1), year.first, year.second))
    }

    @Test
    fun `end date and once`() {
        assertEquals(listOf(d(2026, 1, 5), d(2026, 2, 5)), Recurrence.MONTHLY.occurrences(d(2026, 1, 5), year.first, year.second, end = d(2026, 2, 20)))
        assertEquals(listOf(d(2026, 3, 9)), Recurrence(Frequency.ONCE).occurrences(d(2026, 3, 9), year.first, year.second))
        assertEquals(emptyList(), Recurrence(Frequency.ONCE).occurrences(d(2025, 3, 9), year.first, year.second))
    }

    @Test
    fun `next due date`() {
        assertEquals(d(2026, 10, 15), Recurrence.MONTHLY.next(d(2026, 1, 15), d(2026, 10, 1)))
        assertEquals(d(2026, 10, 1), Recurrence.MONTHLY.next(d(2026, 1, 1), d(2026, 10, 1)))
    }

    @Test
    fun `encode and decode`() {
        val rule = Recurrence(Frequency.MONTHLY, interval = 3, monthDay = MonthDay.LAST_BUSINESS_DAY, adjust = BusinessDayAdjust.PREVIOUS)
        assertEquals("MONTHLY;INTERVAL=3;DAY=LAST_BUSINESS_DAY;ADJUST=PREVIOUS", rule.encode())
        assertEquals(rule, Recurrence.decode(rule.encode()))
        val semi = Recurrence(Frequency.SEMI_MONTHLY, secondDay = 0)
        assertEquals(semi, Recurrence.decode(semi.encode()))
    }

    @Test
    fun `occurrences per year`() {
        assertEquals(12.0, Recurrence.MONTHLY.perYear)
        assertEquals(4.0, Recurrence.QUARTERLY.perYear)
        assertEquals(26.0, Recurrence.BI_WEEKLY.perYear, 0.1)
    }
}
