package ca.schippers.hfm.calc.schedule

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MaintenanceScheduleTest {

    private val today = LocalDate(2026, 10, 2)

    @Test
    fun `a task by months is due after the interval and soon within the reminder`() {
        val ok = MaintenanceSchedule.status(LocalDate(2026, 6, 1), null, 12, null, null, null, 30, 0, today)
        assertEquals(LocalDate(2027, 6, 1), ok.dueDate)
        assertEquals(DueState.OK, ok.state)
        val soon = MaintenanceSchedule.status(LocalDate(2025, 10, 20), null, 12, null, null, null, 30, 0, today)
        assertEquals(DueState.SOON, soon.state)
        val due = MaintenanceSchedule.status(LocalDate(2025, 10, 2), null, 12, null, null, null, 30, 0, today)
        assertEquals(DueState.DUE, due.state)
    }

    @Test
    fun `a task by usage is due at the reading and forecast from the usual rate`() {
        // Furnace filter every 300 hours, last at 1000, now 1200, 2 hours a day: due in 50 days.
        val s = MaintenanceSchedule.status(LocalDate(2026, 6, 1), 1000, null, 300, 1200, 2.0, 30, 20, today)
        assertEquals(1300, s.dueUsage)
        assertEquals(LocalDate(2026, 11, 21), s.forecastDate)
        assertEquals(DueState.OK, s.state)
        assertEquals(s.forecastDate, s.nextDate)
        assertEquals(DueState.SOON, MaintenanceSchedule.status(LocalDate(2026, 6, 1), 1000, null, 300, 1285, null, 30, 20, today).state)
        assertEquals(DueState.DUE, MaintenanceSchedule.status(LocalDate(2026, 6, 1), 1000, null, 300, 1300, null, 30, 20, today).state)
    }

    @Test
    fun `whichever comes first decides`() {
        // The date is far, but the hours are already reached.
        val s = MaintenanceSchedule.status(LocalDate(2026, 6, 1), 100, 24, 50, 160, null, 30, 5, today)
        assertEquals(DueState.DUE, s.state)
        assertEquals(LocalDate(2028, 6, 1), s.dueDate)
    }

    @Test
    fun `never done has no due date`() {
        val s = MaintenanceSchedule.status(null, null, 12, 5000, 20000, 30.0, 30, 500, today)
        assertNull(s.dueDate)
        assertNull(s.dueUsage)
        assertEquals(DueState.OK, s.state)
    }

    @Test
    fun `usage per day covers the last year and needs two weeks`() {
        val readings = listOf(
            LocalDate(2024, 1, 1) to 0,
            LocalDate(2025, 10, 2) to 1000,
            LocalDate(2026, 10, 2) to 1365,
        )
        assertEquals(1.0, MaintenanceSchedule.usagePerDay(readings))
        assertNull(MaintenanceSchedule.usagePerDay(listOf(LocalDate(2026, 9, 25) to 10, LocalDate(2026, 10, 2) to 20)))
        assertNull(MaintenanceSchedule.usagePerDay(emptyList()))
    }

    @Test
    fun `a season falls this year or next`() {
        assertEquals(LocalDate(2026, 10, 15), MaintenanceSchedule.nextSeason(10, 15, today))
        assertEquals(LocalDate(2027, 4, 15), MaintenanceSchedule.nextSeason(4, 15, today))
    }
}
