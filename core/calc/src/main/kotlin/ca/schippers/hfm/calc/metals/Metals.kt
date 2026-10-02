package ca.schippers.hfm.calc.metals

import ca.schippers.hfm.calc.CALC
import ca.schippers.hfm.money.Money
import java.math.BigDecimal

/** How a precious metal item's weight is written (PM-01). */
enum class WeightUnit(val gramsPerUnit: BigDecimal) {
    /** The troy ounce, in which metals are priced. */
    OZT(BigDecimal("31.1034768")),
    G(BigDecimal.ONE),
    KG(BigDecimal(1000)),
}

/** PM-02: valuing coins, bars and rounds from the spot price per troy ounce of pure metal. */
object Metals {

    /** [weight] in [unit], as troy ounces. */
    fun troyOunces(weight: BigDecimal, unit: WeightUnit): BigDecimal =
        if (unit == WeightUnit.OZT) weight else weight.multiply(unit.gramsPerUnit).divide(WeightUnit.OZT.gramsPerUnit, CALC)

    /** The pure metal in [quantity] items of [weight] at [purity] (0.9999 for "four nines"). */
    fun fineOunces(quantity: Int, weight: BigDecimal, unit: WeightUnit, purity: BigDecimal): BigDecimal =
        troyOunces(weight, unit).multiply(purity, CALC).multiply(BigDecimal(quantity), CALC)

    /**
     * The value at [spotPerOz] (in the money's currency), adjusted by [premiumPercent]: positive for
     * coins that sell above melt value, negative for a dealer's buy-back discount.
     */
    fun value(fineOunces: BigDecimal, spotPerOz: Money, premiumPercent: BigDecimal? = null): Money {
        val factor = BigDecimal.ONE.add((premiumPercent ?: BigDecimal.ZERO).movePointLeft(2))
        return spotPerOz.times(fineOunces.multiply(factor, CALC))
    }
}
