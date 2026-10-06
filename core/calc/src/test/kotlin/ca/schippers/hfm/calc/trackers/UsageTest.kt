package ca.schippers.hfm.calc.trackers

import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UsageTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)
    private fun n(s: String) = BigDecimal(s)
    private fun round(v: BigDecimal) = v.setScale(2, java.math.RoundingMode.HALF_UP)

    @Test
    fun `use between readings is spread by days into each month`() {
        // 620 kWh from January 21 to March 2: 11 days of January, 28 of February, 1 of March (40 days).
        val spans = Usage.meterSpans(listOf(d(2026, 1, 21) to n("10000"), d(2026, 3, 2) to n("10620")))
        val months = Usage.monthly(spans)
        assertEquals(listOf(1, 2, 3), months.map { it.month })
        assertEquals(listOf("170.50", "434.00", "15.50"), months.map { round(it.amount).toPlainString() })
        assertEquals(listOf(11, 28, 1), months.map { it.coveredDays })
        assertTrue(months[1].complete)
        assertFalse(months[0].complete)
    }

    @Test
    fun `a lower reading starts again, and the last reading of a day counts`() {
        val spans = Usage.meterSpans(
            listOf(d(2026, 1, 1) to n("500"), d(2026, 1, 11) to n("520"), d(2026, 1, 11) to n("530"), d(2026, 1, 21) to n("12"), d(2026, 1, 31) to n("42")),
        )
        assertEquals(listOf("30", "0", "30"), spans.map { it.amount.toPlainString() })
    }

    @Test
    fun `a month is unusual above the share of the same month last year, else of the recent average`() {
        val months = listOf(
            MonthUse(2025, 1, n("800"), 31, 31),
            MonthUse(2025, 11, n("600"), 30, 30),
            MonthUse(2025, 12, n("700"), 31, 31),
            MonthUse(2026, 1, n("1100"), 31, 31),
            MonthUse(2026, 2, n("1000"), 28, 28),
            MonthUse(2026, 3, n("500"), 10, 31),
        )
        val c = Usage.compare(months, n("1.3")).associateBy { it.use.year * 100 + it.use.month }
        // January: 1100 against 800 last January (137 %).
        assertTrue(c.getValue(202601).unusual)
        assertEquals(n("38"), c.getValue(202601).changePercent)
        // February: no February 2025; the three months before average (600 + 700 + 1100) / 3 = 800: 125 %.
        assertFalse(c.getValue(202602).unusual)
        assertEquals("800", c.getValue(202602).recentAverage!!.stripTrailingZeros().toPlainString())
        // March is not complete: never flagged.
        assertFalse(c.getValue(202603).unusual)
        // November 2025 has nothing to compare with.
        assertNull(c.getValue(202511).lastYear)
        assertNull(c.getValue(202511).recentAverage)
    }

    @Test
    fun `tank use counts deliveries, and a reading on a delivery day is after it`() {
        val levels = listOf(d(2026, 1, 1) to n("300"), d(2026, 1, 11) to n("400"), d(2026, 1, 21) to n("350"))
        // 200 litres on January 11, before that day's reading: 300 + 200 - 400 = 100 used.
        val spans = Usage.tankSpans(levels, listOf(d(2026, 1, 11) to n("200")))
        assertEquals(listOf("100", "50"), spans.map { it.amount.toPlainString() })
        assertEquals("7.5", Usage.dailyRate(spans)!!.toPlainString())
        // Over the last 5 days, only the span ending at the last reading counts: 50 litres in 10 days.
        assertEquals("5", Usage.dailyRate(spans, days = 5)!!.stripTrailingZeros().toPlainString())
    }

    @Test
    fun `a tank's order and empty dates come from its use per day`() {
        // 500 litres, 60 % (300 litres) on October 1, 5 litres a day, order at 25 % (125 litres).
        val p = Tanks.project(d(2026, 10, 1), n("300"), BigDecimal.ZERO, n("5"), n("500"), n("125"), d(2026, 10, 11))
        assertEquals("250", p.levelToday.stripTrailingZeros().toPlainString())
        assertEquals(d(2026, 11, 5), p.orderDate)
        assertEquals(d(2026, 11, 30), p.emptyDate)
        // A delivery since fills it, up to the capacity.
        val filled = Tanks.project(d(2026, 10, 1), n("300"), n("400"), n("5"), n("500"), n("125"), d(2026, 10, 1))
        assertEquals("500", filled.levelToday.toPlainString())
        // Without use per day, no dates.
        assertNull(Tanks.project(d(2026, 10, 1), n("300"), BigDecimal.ZERO, null, n("500"), n("125"), d(2026, 10, 11)).orderDate)
        assertEquals("60.0", Tanks.percent(n("300"), n("500")).toPlainString())
        assertEquals("300", Tanks.litres(n("60"), n("500")).stripTrailingZeros().toPlainString())
    }
}
