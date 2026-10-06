package ca.schippers.hfm.calc.schedule

import ca.schippers.hfm.calc.rules.RuleValue
import ca.schippers.hfm.calc.rules.Rules
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** SEA-01, SEA-03: the seasons from Rates and rules, parts of the year, and weekly tasks in season. */
class SeasonsTest {

    private fun d(s: String) = LocalDate.parse(s)

    @AfterEach
    fun reset() {
        Rules.userValues = emptyList()
    }

    @Test
    fun `the seasons are the astronomical ones, winter running over the new year`() {
        assertEquals(SeasonWindow(Season.FALL, d("2026-09-22"), d("2026-12-21")), Seasons.windowOf(d("2026-10-06")))
        assertEquals(SeasonWindow(Season.WINTER, d("2025-12-21"), d("2026-03-20")), Seasons.windowOf(d("2026-01-15")))
        assertEquals(Season.SPRING, Seasons.windowOf(d("2026-03-20")).season, "a season starts on its first day")
        assertEquals(Season.WINTER, Seasons.windowOf(d("2026-03-19")).season)
        // The current season first, then the others as they next come.
        assertEquals(
            listOf(Season.FALL, Season.WINTER, Season.SPRING, Season.SUMMER),
            Seasons.coming(d("2026-10-06")).map { it.season },
        )
        assertEquals(SeasonWindow(Season.SPRING, d("2027-03-20"), d("2027-06-21")), Seasons.window(Season.SPRING, d("2026-10-06")))
        assertEquals(d("2026-09-22"), Seasons.window(Season.FALL, d("2026-10-06")).start)
    }

    @Test
    fun `a household may move the start of a season`() {
        Rules.userValues = listOf(RuleValue("season.springStart", null, d("2000-01-01"), "04-01", builtIn = false))
        assertEquals(Season.WINTER, Seasons.windowOf(d("2027-03-25")).season)
        assertEquals(SeasonWindow(Season.SPRING, d("2027-04-01"), d("2027-06-21")), Seasons.window(Season.SPRING, d("2026-10-06")))
    }

    @Test
    fun `a part of the year may run over the new year`() {
        val pool = (5 to 20) to (9 to 15)
        assertTrue(Seasons.inPart(d("2026-07-01"), pool.first, pool.second))
        assertFalse(Seasons.inPart(d("2026-10-01"), pool.first, pool.second))
        assertEquals(d("2027-05-20"), Seasons.intoPart(d("2026-10-01"), pool.first, pool.second))
        assertEquals(d("2026-05-20"), Seasons.intoPart(d("2026-02-01"), pool.first, pool.second))
        val cover = (11 to 1) to (4 to 30)
        assertTrue(Seasons.inPart(d("2027-01-10"), cover.first, cover.second))
        assertTrue(Seasons.inPart(d("2026-11-01"), cover.first, cover.second))
        assertEquals(d("2026-11-01"), Seasons.intoPart(d("2026-06-01"), cover.first, cover.second))
        assertEquals(6 to 1, Seasons.parseMonthDay("06-01"))
        assertNull(Seasons.parseMonthDay("13-01"))
        assertNull(Seasons.parseMonthDay(" "))
        assertEquals("06-01", Seasons.formatMonthDay(6 to 1))
    }

    @Test
    fun `a weekly task in season waits for the next season once it ends`() {
        val pool = (5 to 20) to (9 to 15)
        val inSeason = MaintenanceSchedule.status(d("2026-07-01"), null, null, null, null, null, 2, 0, d("2026-07-07"), intervalWeeks = 1, part = pool)
        assertEquals(d("2026-07-08"), inSeason.dueDate)
        assertEquals(DueState.SOON, inSeason.state)
        val closed = MaintenanceSchedule.status(d("2026-09-12"), null, null, null, null, null, 2, 0, d("2026-09-20"), intervalWeeks = 1, part = pool)
        assertEquals(d("2027-05-20"), closed.dueDate, "after closing, the next test is at the next opening")
        assertEquals(DueState.OK, closed.state)
        // Months and weeks together: whichever comes first.
        val both = MaintenanceSchedule.status(d("2026-07-01"), null, 1, null, null, null, 2, 0, d("2026-07-02"), intervalWeeks = 6)
        assertEquals(d("2026-08-01"), both.dueDate)
    }
}
