package ca.schippers.hfm.calc.invest

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.pow

/**
 * One day of a portfolio's history: the money that came in that day ([flow], negative when it
 * went out) and the value at the end of the day, after the flow. Flows are counted at the end of
 * their day, so money added on a day earns nothing that day.
 */
data class ValuePoint(val date: LocalDate, val value: BigDecimal, val flow: BigDecimal = BigDecimal.ZERO)

/**
 * INV-06: rates of return. Returns are ratios (0.05 is 5 %), never money; the money-weighted rate
 * is found by iteration in double precision and rounded to [SCALE] places.
 */
object Returns {

    /** Decimal places kept on a rate: 0.000001 is 0.0001 %. */
    const val SCALE = 8

    private val MC = MathContext.DECIMAL64

    /**
     * Time-weighted return over [points] (sorted by date; the first is the starting value, its
     * flow ignored): each stretch between flows is measured on its own and the results chained,
     * so the timing of contributions and withdrawals does not count. A stretch that starts with
     * nothing invested is skipped. Null when nothing was invested at all.
     */
    fun timeWeighted(points: List<ValuePoint>): BigDecimal? {
        if (points.size < 2) return null
        var growth = BigDecimal.ONE
        var measured = false
        var previous = points.first().value
        for (p in points.drop(1)) {
            if (previous.signum() > 0) {
                growth = growth.multiply(p.value.subtract(p.flow).divide(previous, MC), MC)
                measured = true
            }
            previous = p.value
        }
        return if (measured) growth.subtract(BigDecimal.ONE).setScale(SCALE, RoundingMode.HALF_UP) else null
    }

    /**
     * Money-weighted (personal) rate of return, a yearly rate: the rate at which the starting
     * value and every flow, grown to the end, equal the ending value (an internal rate of return
     * on actual days, as spreadsheet XIRR). Null when it has no answer, for example when nothing
     * was invested.
     */
    fun moneyWeighted(points: List<ValuePoint>): BigDecimal? {
        if (points.size < 2) return null
        val start = points.first().date
        // The investor's view: money put in is negative, the final value positive.
        val flows = ArrayList<Pair<Double, Double>>()
        flows += 0.0 to -points.first().value.toDouble()
        for (p in points.drop(1)) if (p.flow.signum() != 0) flows += start.daysUntil(p.date) / 365.0 to -p.flow.toDouble()
        flows += start.daysUntil(points.last().date) / 365.0 to points.last().value.toDouble()
        return irr(flows)?.let { BigDecimal(it).setScale(SCALE, RoundingMode.HALF_UP) }
    }

    /** Spreadsheet XIRR: yearly rate for dated cash flows (negative paid out, positive received). */
    fun xirr(flows: List<Pair<LocalDate, BigDecimal>>): BigDecimal? {
        if (flows.isEmpty()) return null
        val first = flows.minOf { it.first }
        return irr(flows.map { first.daysUntil(it.first) / 365.0 to it.second.toDouble() })?.let { BigDecimal(it).setScale(SCALE, RoundingMode.HALF_UP) }
    }

    /** The yearly rate [annual] expressed over [days] days. */
    fun overDays(annual: BigDecimal, days: Int): BigDecimal =
        BigDecimal((1 + annual.toDouble()).pow(days / 365.0) - 1).setScale(SCALE, RoundingMode.HALF_UP)

    /**
     * A return over [days] days as a yearly rate. Null under one year: a rate is not annualized
     * over a shorter period (it would claim a whole year's result from a few months).
     */
    fun annualized(cumulative: BigDecimal, days: Int): BigDecimal? {
        if (days < 365) return null
        val growth = 1 + cumulative.toDouble()
        if (growth <= 0) return BigDecimal.ONE.negate()
        return BigDecimal(growth.pow(365.0 / days) - 1).setScale(SCALE, RoundingMode.HALF_UP)
    }

    /** Net present value is zero at the returned rate; Newton's method, then bisection if needed. */
    private fun irr(flows: List<Pair<Double, Double>>): Double? {
        if (flows.none { it.second > 0 } || flows.none { it.second < 0 }) return null
        fun npv(r: Double) = flows.sumOf { (t, cf) -> cf / (1 + r).pow(t) }
        fun slope(r: Double) = flows.sumOf { (t, cf) -> -t * cf / (1 + r).pow(t + 1) }
        var r = 0.1
        repeat(100) {
            val f = npv(r)
            val d = slope(r)
            if (d == 0.0 || f.isNaN()) return@repeat
            val next = r - f / d
            if (next <= -1 || next.isNaN() || next.isInfinite()) return@repeat
            if (abs(next - r) < 1e-12) return next
            r = next
        }
        // Bisection over a wide bracket when Newton's method wanders off.
        var lo = -0.999999
        var hi = 100.0
        var fLo = npv(lo)
        if (fLo.isNaN() || fLo * npv(hi) > 0) return null
        repeat(300) {
            val mid = (lo + hi) / 2
            val fMid = npv(mid)
            if (abs(fMid) < 1e-9 || hi - lo < 1e-13) return mid
            if (fLo * fMid < 0) hi = mid else { lo = mid; fLo = fMid }
        }
        return (lo + hi) / 2
    }
}
