package ca.schippers.hfm.calc.invest

import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReturnsTest {

    private fun d(s: String) = LocalDate.parse(s)
    private fun n(s: String) = BigDecimal(s)
    private fun p(date: String, value: String, flow: String = "0") = ValuePoint(d(date), n(value), n(flow))
    private fun BigDecimal?.round(places: Int) = this!!.setScale(places, RoundingMode.HALF_UP)

    /**
     * The CFA Institute's textbook example: one share bought at $200; a year later a $5 dividend
     * is paid out and a second share bought at $225; a year after that $10 of dividends and both
     * shares sold at $235. Published answers: money-weighted 9.39 %, time-weighted 10.76 % a year
     * (10.755 % unrounded: the published figure rounds the second year to 6.67 % first).
     */
    @Test
    fun `CFA two-year example`() {
        val points = listOf(
            p("2021-01-01", "200"),
            p("2022-01-01", "450", "220"), // 225 put in, 5 taken out
            p("2023-01-01", "480"), // 470 for the shares and the 10 of dividends
        )
        val twr = Returns.timeWeighted(points)
        assertEquals(n("0.2267"), twr.round(4), "1.15 × 1.0667 − 1")
        assertEquals(n("0.10755"), Returns.annualized(twr!!, 730).round(5))
        assertEquals(n("0.0939"), Returns.moneyWeighted(points).round(4))
    }

    /** Microsoft's published XIRR example: the answer is 0.373362535 (37.34 %). */
    @Test
    fun `spreadsheet XIRR example`() {
        val flows = listOf(
            d("2008-01-01") to n("-10000"),
            d("2008-03-01") to n("2750"),
            d("2008-10-30") to n("4250"),
            d("2009-02-15") to n("3250"),
            d("2009-04-01") to n("2750"),
        )
        assertEquals(n("0.3733625"), Returns.xirr(flows).round(7))
    }

    @Test
    fun `time-weighted ignores when money was added, money-weighted does not`() {
        // The market falls 20 % then rises 50 %; a large deposit made just before the rise.
        val early = listOf(p("2025-01-01", "1000"), p("2025-07-01", "800"), p("2026-01-01", "1200"))
        val late = listOf(p("2025-01-01", "1000"), p("2025-07-01", "10800", "10000"), p("2026-01-01", "16200"))
        assertEquals(n("0.20000000"), Returns.timeWeighted(early))
        assertEquals(n("0.20000000"), Returns.timeWeighted(late))
        assertTrue(Returns.moneyWeighted(late)!! > Returns.moneyWeighted(early)!!, "most of the money caught the rise")
    }

    @Test
    fun `an account opened during the period starts with its first deposit`() {
        val points = listOf(p("2026-01-01", "0"), p("2026-02-01", "5000", "5000"), p("2026-12-31", "5250"))
        assertEquals(n("0.05000000"), Returns.timeWeighted(points))
        assertNull(Returns.timeWeighted(listOf(p("2026-01-01", "0"), p("2026-12-31", "0"))))
    }

    @Test
    fun `rates are not annualized under a year`() {
        assertNull(Returns.annualized(n("0.05"), 200))
        assertEquals(n("0.05"), Returns.annualized(n("0.05"), 365).round(2))
        assertEquals(n("0.0246"), Returns.overDays(n("0.05"), 182).round(4), "half a year at 5 % a year")
    }

    @Test
    fun `no rate without money on both sides`() {
        assertNull(Returns.xirr(listOf(d("2026-01-01") to n("-100"))))
        assertNull(Returns.moneyWeighted(listOf(p("2026-01-01", "0"), p("2026-06-01", "0"))))
    }
}
