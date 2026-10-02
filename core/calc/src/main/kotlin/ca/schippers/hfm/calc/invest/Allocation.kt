package ca.schippers.hfm.calc.invest

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * INV-07: asset allocation against a target, and the trades that bring it back. Percentages are
 * out of 100; amounts are in one currency, rounded to [scale] decimal places (cents by default).
 */
object Allocation {

    private val HUNDRED = BigDecimal(100)
    private val MC = MathContext.DECIMAL64

    /**
     * Divides [value] according to [mix] (percentages adding up to 100). The parts always add up
     * to [value] exactly: the rounding difference goes to the largest part.
     */
    fun <K> split(value: BigDecimal, mix: Map<K, BigDecimal>, scale: Int = 2): Map<K, BigDecimal> {
        require(isComplete(mix)) { "A mix must add up to 100 %" }
        val parts = mix.filterValues { it.signum() > 0 }.mapValues { (_, p) -> value.multiply(p).divide(HUNDRED, scale, RoundingMode.HALF_UP) }.toMutableMap()
        val rest = value.setScale(scale, RoundingMode.HALF_UP) - parts.values.fold(BigDecimal.ZERO, BigDecimal::add)
        if (rest.signum() != 0) parts.maxByOrNull { it.value }?.let { parts[it.key] = it.value + rest }
        return parts
    }

    /** True when the percentages are all zero or more and add up to 100. */
    fun isComplete(percentages: Map<*, BigDecimal>): Boolean =
        percentages.values.all { it.signum() >= 0 } && percentages.values.fold(BigDecimal.ZERO, BigDecimal::add).compareTo(HUNDRED) == 0

    /** Each part's share of the whole, in percent (0 for an empty whole). */
    fun <K> shares(values: Map<K, BigDecimal>): Map<K, BigDecimal> {
        val total = values.values.fold(BigDecimal.ZERO, BigDecimal::add)
        return values.mapValues { (_, v) -> if (total.signum() == 0) BigDecimal.ZERO else v.multiply(HUNDRED).divide(total, MC) }
    }

    /**
     * The amount to buy (positive) or sell (negative) in each class so that [current] plus
     * [newMoney] matches [target] (percentages adding up to 100; a class missing from it has a
     * target of zero).
     *
     * With [sell] false, only [newMoney] is placed: it goes to the classes below their target in
     * proportion to how far below they are. Nothing is sold, so classes above target may stay above.
     */
    fun <K> rebalance(current: Map<K, BigDecimal>, target: Map<K, BigDecimal>, newMoney: BigDecimal = BigDecimal.ZERO, sell: Boolean = true, scale: Int = 2): Map<K, BigDecimal> {
        require(isComplete(target)) { "A target must add up to 100 %" }
        require(newMoney.signum() >= 0 || sell) { "Money can be taken out only by selling" }
        val keys = (current.keys + target.keys).toList()
        val total = current.values.fold(BigDecimal.ZERO, BigDecimal::add) + newMoney
        val goal = keys.associateWith { k -> total.multiply(target[k] ?: BigDecimal.ZERO).divide(HUNDRED, MC) }
        val raw: Map<K, BigDecimal> = if (sell) {
            keys.associateWith { k -> goal.getValue(k) - (current[k] ?: BigDecimal.ZERO) }
        } else {
            val shortfall = keys.associateWith { k -> (goal.getValue(k) - (current[k] ?: BigDecimal.ZERO)).max(BigDecimal.ZERO) }
            // The shortfalls always add up to at least the new money, since the gaps above and below target cancel out.
            val missing = shortfall.values.fold(BigDecimal.ZERO, BigDecimal::add)
            keys.associateWith { k -> if (missing.signum() == 0) BigDecimal.ZERO else newMoney.multiply(shortfall.getValue(k)).divide(missing, MC) }
        }
        // Rounded to cents, with the difference on the largest trade so the trades add up to the new money.
        val rounded = raw.mapValues { it.value.setScale(scale, RoundingMode.HALF_UP) }.toMutableMap()
        val diff = newMoney.setScale(scale, RoundingMode.HALF_UP) - rounded.values.fold(BigDecimal.ZERO, BigDecimal::add)
        if (diff.signum() != 0) rounded.maxByOrNull { it.value.abs() }?.let { rounded[it.key] = it.value + diff }
        return rounded.filterValues { it.signum() != 0 }
    }
}
