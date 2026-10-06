package ca.schippers.hfm.calc.trackers

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** A quantity used from [from] (included) to [to] (excluded): between two readings. */
data class Span(val from: LocalDate, val to: LocalDate, val amount: BigDecimal) {
    val days: Int get() = from.daysUntil(to)
}

/** A month's use: the total spread into it, and how many of its [days] the readings cover. */
data class MonthUse(val year: Int, val month: Int, val amount: BigDecimal, val coveredDays: Int, val days: Int) {
    /** Readings before and after the whole month: its total is complete. */
    val complete: Boolean get() = coveredDays >= days
    internal val index: Int get() = year * 12 + month - 1
}

/**
 * A month compared with the same month a year before and with the average of the three months
 * before it (the complete ones among them); [unusual] when above the threshold share of the first,
 * or of the second when the first is missing.
 */
data class MonthComparison(val use: MonthUse, val lastYear: BigDecimal?, val recentAverage: BigDecimal?, val unusual: Boolean) {
    /** The change from last year's month in percent (+25 for a quarter more), or null. */
    val changePercent: BigDecimal?
        get() = lastYear?.takeIf { it.signum() > 0 }?.let { (use.amount - it).divide(it, MathContext.DECIMAL64).movePointRight(2).setScale(0, RoundingMode.HALF_UP) }
}

/**
 * UTL-01, UTL-02: use per month from meter readings or tank levels. The use between two readings is
 * spread evenly over the days between them, so a reading on the 10th and another on the 12th of the
 * next month give each month its share by days.
 */
object Usage {

    private val MC = MathContext.DECIMAL64

    /**
     * The spans between a meter's readings (its running total, in date order). Of several readings
     * on one date the last counts. A lower reading than the one before starts again from it (a new
     * or reset meter): that span counts nothing.
     */
    fun meterSpans(readings: List<Pair<LocalDate, BigDecimal>>): List<Span> {
        val byDate = readings.groupBy { it.first }.map { (d, l) -> d to l.last().second }.sortedBy { it.first }
        return byDate.zipWithNext { a, b -> Span(a.first, b.first, (b.second - a.second).max(BigDecimal.ZERO)) }
    }

    /**
     * The spans between a tank's level readings (litres): the level at the start, plus what was
     * delivered after that reading up to the next one, less the level then. A reading on the day of
     * a delivery is taken after it. A gauge that reads more than that gives no use, never less.
     */
    fun tankSpans(levels: List<Pair<LocalDate, BigDecimal>>, deliveries: List<Pair<LocalDate, BigDecimal>>): List<Span> {
        val byDate = levels.groupBy { it.first }.map { (d, l) -> d to l.last().second }.sortedBy { it.first }
        return byDate.zipWithNext { a, b ->
            val delivered = deliveries.filter { it.first > a.first && it.first <= b.first }.fold(BigDecimal.ZERO) { s, d -> s + d.second }
            Span(a.first, b.first, (a.second + delivered - b.second).max(BigDecimal.ZERO))
        }
    }

    /** Each span's use spread evenly over its days and added up by month, oldest month first. */
    fun monthly(spans: List<Span>): List<MonthUse> {
        val amounts = HashMap<Int, BigDecimal>()
        val covered = HashMap<Int, Int>()
        for (s in spans) {
            val days = s.days
            if (days <= 0) continue
            val perDay = s.amount.divide(BigDecimal(days), MC)
            var d = s.from
            while (d < s.to) {
                // The days of this span in d's month, at once.
                val next = minOf(LocalDate(d.year, d.month, 1).plus(DatePeriod(months = 1)), s.to)
                val n = d.daysUntil(next)
                val key = d.year * 12 + d.month.ordinal
                amounts.merge(key, perDay.multiply(BigDecimal(n), MC), BigDecimal::add)
                covered.merge(key, n, Int::plus)
                d = next
            }
        }
        return amounts.keys.sorted().map { k ->
            val year = k / 12
            val month = k % 12 + 1
            MonthUse(year, month, amounts.getValue(k), covered.getValue(k), daysIn(year, month))
        }
    }

    /**
     * Each month compared, newest first. Only complete months are flagged, against a complete
     * month a year before or, without it, at least two complete months among the three before.
     */
    fun compare(months: List<MonthUse>, threshold: BigDecimal): List<MonthComparison> {
        val complete = months.filter { it.complete }.associateBy { it.index }
        return months.sortedByDescending { it.index }.map { m ->
            val lastYear = complete[m.index - 12]?.amount
            val before = (1..3).mapNotNull { complete[m.index - it]?.amount }
            val average = before.takeIf { it.size >= 2 }?.let { l -> l.fold(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal(l.size), MC) }
            val reference = lastYear ?: average
            val unusual = m.complete && reference != null && reference.signum() > 0 && m.amount > reference.multiply(threshold, MC)
            MonthComparison(m, lastYear, average, unusual)
        }
    }

    /** The average use per day over the spans that end in the [days] days up to the last reading, or null. */
    fun dailyRate(spans: List<Span>, days: Int = 90): BigDecimal? {
        val last = spans.maxOfOrNull { it.to } ?: return null
        val recent = spans.filter { it.to.daysUntil(last) < days && it.days > 0 }
        val total = recent.sumOf { it.days }
        if (total == 0) return null
        return recent.fold(BigDecimal.ZERO) { s, x -> s + x.amount }.divide(BigDecimal(total), MC)
    }

    fun daysIn(year: Int, month: Int): Int = LocalDate(year, month, 1).daysUntil(LocalDate(year, month, 1).plus(DatePeriod(months = 1)))
}

/**
 * UTL-02: where a tank stands [today]: its level estimated from the last reading, the deliveries
 * since and the recent use per day; when it is expected to reach the level to order at, and when empty.
 */
data class TankProjection(val levelToday: BigDecimal, val dailyUse: BigDecimal?, val orderDate: LocalDate?, val emptyDate: LocalDate?)

object Tanks {

    /**
     * [lastLevel] litres on [lastDate], [deliveredSince] litres after it (up to [today]), using
     * [dailyUse] litres a day. The estimate stays between empty and [capacity]. Without a use per
     * day the level stays as it is and no dates are given, except that a level already at [orderAt]
     * or below is to be ordered at once (from [lastDate]), so a first reading of a low tank reminds.
     */
    fun project(lastDate: LocalDate, lastLevel: BigDecimal, deliveredSince: BigDecimal, dailyUse: BigDecimal?, capacity: BigDecimal, orderAt: BigDecimal, today: LocalDate): TankProjection {
        val start = (lastLevel + deliveredSince).min(capacity)
        val elapsed = lastDate.daysUntil(today).coerceAtLeast(0)
        val rate = dailyUse?.takeIf { it.signum() > 0 } ?: return TankProjection(start.max(BigDecimal.ZERO), dailyUse, lastDate.takeIf { start <= orderAt }, null)
        val level = (start - rate.multiply(BigDecimal(elapsed))).max(BigDecimal.ZERO)
        fun reaches(target: BigDecimal): LocalDate {
            val days = (start - target).divide(rate, 0, RoundingMode.FLOOR).toInt().coerceAtLeast(0)
            return lastDate.plus(DatePeriod(days = days))
        }
        return TankProjection(level, rate, reaches(orderAt), reaches(BigDecimal.ZERO))
    }

    /** [percent] of [capacity], in litres. */
    fun litres(percent: BigDecimal, capacity: BigDecimal): BigDecimal = capacity.multiply(percent, MathContext.DECIMAL64).movePointLeft(2)

    /** [litres] as a percentage of [capacity], to one decimal. */
    fun percent(litres: BigDecimal, capacity: BigDecimal): BigDecimal =
        if (capacity.signum() <= 0) BigDecimal.ZERO else litres.movePointRight(2).divide(capacity, 1, RoundingMode.HALF_UP)
}
