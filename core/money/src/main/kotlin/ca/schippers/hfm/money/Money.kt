package ca.schippers.hfm.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * An exact amount of money: a whole number of minor units (cents, satoshis) in one currency.
 *
 * Money is never held as floating point (NFR-04). Arithmetic overflow throws rather than wraps.
 * Amounts in different currencies cannot be combined; convert them first with [convert].
 */
class Money private constructor(val minorUnits: Long, val currency: Currency) : Comparable<Money> {

    val isZero: Boolean get() = minorUnits == 0L
    val isNegative: Boolean get() = minorUnits < 0L
    val isPositive: Boolean get() = minorUnits > 0L
    val signum: Int get() = java.lang.Long.signum(minorUnits)

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.addExact(minorUnits, other.minorUnits), currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.subtractExact(minorUnits, other.minorUnits), currency)
    }

    operator fun unaryMinus(): Money = Money(Math.negateExact(minorUnits), currency)

    fun abs(): Money = if (isNegative) -this else this

    operator fun times(factor: Long): Money = Money(Math.multiplyExact(minorUnits, factor), currency)

    /** Multiplies by an exact factor (a rate, a percentage) and rounds to the currency's minor unit. */
    fun times(factor: BigDecimal, rounding: RoundingMode = DEFAULT_ROUNDING): Money =
        of(toBigDecimal().multiply(factor), currency, rounding)

    /**
     * Splits this amount into parts proportional to [ratios]. The parts always add up exactly to
     * this amount: leftover minor units go one at a time to the parts with the largest remainders.
     */
    fun allocate(ratios: List<BigDecimal>): List<Money> {
        require(ratios.isNotEmpty()) { "At least one ratio is required" }
        require(ratios.all { it.signum() >= 0 }) { "Ratios cannot be negative" }
        val total = ratios.fold(BigDecimal.ZERO, BigDecimal::add)
        require(total.signum() > 0) { "Ratios must not all be zero" }

        val amount = BigDecimal.valueOf(minorUnits)
        val exact = ratios.map { amount.multiply(it).divide(total, 20, RoundingMode.HALF_EVEN) }
        val parts = exact.map { it.setScale(0, RoundingMode.FLOOR).longValueExact() }.toLongArray()
        var leftover = minorUnits - parts.sum()
        val byRemainder = exact.indices.sortedByDescending { exact[it].subtract(BigDecimal.valueOf(parts[it])) }
        var i = 0
        while (leftover > 0) {
            parts[byRemainder[i % byRemainder.size]]++
            leftover--
            i++
        }
        return parts.map { Money(it, currency) }
    }

    /** Splits this amount into [count] near-equal parts that add up exactly to this amount. */
    fun split(count: Int): List<Money> {
        require(count > 0) { "Count must be positive" }
        return allocate(List(count) { BigDecimal.ONE })
    }

    /**
     * Converts to [target] at [rate] (units of target per one unit of this currency),
     * rounding to the target's minor unit.
     */
    fun convert(target: Currency, rate: BigDecimal, rounding: RoundingMode = DEFAULT_ROUNDING): Money {
        require(rate.signum() > 0) { "Exchange rate must be positive" }
        return of(toBigDecimal().multiply(rate), target, rounding)
    }

    fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(minorUnits, currency.minorUnits)

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minorUnits.compareTo(other.minorUnits)
    }

    override fun equals(other: Any?): Boolean =
        other is Money && other.minorUnits == minorUnits && other.currency == currency

    override fun hashCode(): Int = 31 * minorUnits.hashCode() + currency.hashCode()

    /** Plain, locale-independent form such as "1234.56 CAD". Use [MoneyFormat] for display. */
    override fun toString(): String = "${toBigDecimal().toPlainString()} ${currency.code}"

    private fun requireSameCurrency(other: Money) {
        if (other.currency != currency) throw CurrencyMismatchException(currency, other.currency)
    }

    companion object {
        /**
         * Rounding used when a calculation produces fractions of a cent: half up, as used for
         * GST/HST and QST on receipts and by Canadian financial institutions.
         */
        val DEFAULT_ROUNDING: RoundingMode = RoundingMode.HALF_UP

        fun ofMinor(minorUnits: Long, currency: Currency): Money = Money(minorUnits, currency)

        fun zero(currency: Currency): Money = Money(0, currency)

        /** Rounds [amount] to the currency's minor unit. */
        fun of(amount: BigDecimal, currency: Currency, rounding: RoundingMode = DEFAULT_ROUNDING): Money {
            val scaled = amount.setScale(currency.minorUnits, rounding)
            return Money(scaled.unscaledValue().longValueExact(), currency)
        }

        /** Parses a plain decimal such as "-12.34"; rejects more decimals than the currency allows. */
        fun parse(amount: String, currency: Currency): Money = exact(BigDecimal(amount.trim()), currency)

        /** Converts [amount] without rounding; throws if it has more decimals than the currency allows. */
        fun exact(amount: BigDecimal, currency: Currency): Money {
            require(amount.stripTrailingZeros().scale() <= currency.minorUnits) {
                "$amount has more than ${currency.minorUnits} decimals for ${currency.code}"
            }
            return of(amount, currency, RoundingMode.UNNECESSARY)
        }
    }
}

class CurrencyMismatchException(val expected: Currency, val actual: Currency) :
    IllegalArgumentException("Cannot combine $expected with $actual; convert first")

fun Iterable<Money>.sum(currency: Currency): Money = fold(Money.zero(currency)) { acc, m -> acc + m }
