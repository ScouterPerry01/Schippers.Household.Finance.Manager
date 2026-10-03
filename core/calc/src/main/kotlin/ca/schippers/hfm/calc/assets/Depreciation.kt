package ca.schippers.hfm.calc.assets

import ca.schippers.hfm.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.plus
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** AST-03: an estimate of what something is worth as it ages. */
object Depreciation {

    private val HUNDRED = BigDecimal(100)

    /**
     * Straight-line value on [date]: the [price] falls evenly, month by month, to [residualPercent]
     * of it over [years], and stays there. Before the purchase it is worth nothing in the books.
     */
    fun straightLine(price: Money, purchased: LocalDate, years: Int, residualPercent: BigDecimal, date: LocalDate): Money {
        require(years > 0) { "years must be positive" }
        require(residualPercent.signum() >= 0 && residualPercent <= HUNDRED) { "residual must be 0 to 100 %" }
        if (date < purchased) return Money.zero(price.currency)
        val residual = price.toBigDecimal().multiply(residualPercent).divide(HUNDRED, MathContext.DECIMAL64)
        val months = purchased.monthsUntil(date).coerceAtMost(years * 12)
        val lost = (price.toBigDecimal() - residual).multiply(BigDecimal(months)).divide(BigDecimal(years * 12), MathContext.DECIMAL64)
        return Money.of(price.toBigDecimal() - lost, price.currency, RoundingMode.HALF_UP)
    }

    /** When a warranty extended by [months] ends, if the original ([originalEnd]) is short enough to be extended. */
    fun extendedEnd(start: LocalDate, originalEnd: LocalDate, months: Int, maxYears: Int?): LocalDate? {
        if (maxYears != null && start.plus(DatePeriod(years = maxYears)) < originalEnd) return null
        return originalEnd.plus(DatePeriod(months = months))
    }
}
