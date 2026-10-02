package ca.schippers.hfm.calc.metals

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.Test
import kotlin.test.assertEquals

/** PM-02. A troy ounce is 31.1034768 g; expected values were worked out separately in Python. */
class MetalsTest {

    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun n(s: String) = BigDecimal(s)

    @Test
    fun `weights in troy ounces`() {
        assertEquals(n("1"), Metals.troyOunces(n("1"), WeightUnit.OZT))
        assertEquals(n("32.1507"), Metals.troyOunces(n("1"), WeightUnit.KG).setScale(4, RoundingMode.HALF_UP), "a kilo bar")
        assertEquals(n("0.3215"), Metals.troyOunces(n("10"), WeightUnit.G).setScale(4, RoundingMode.HALF_UP))
    }

    @Test
    fun `values from the spot price`() {
        val spot = cad("3700")
        assertEquals(cad("3699.63"), Metals.value(Metals.fineOunces(1, n("1"), WeightUnit.OZT, n("0.9999")), spot), "a 1 oz Maple Leaf")
        assertEquals(cad("1189.46"), Metals.value(Metals.fineOunces(1, n("10"), WeightUnit.G, n("0.9999")), spot), "a 10 g bar")
        assertEquals(cad("949.45"), Metals.value(Metals.fineOunces(20, n("1"), WeightUnit.OZT, n("0.999")), cad("44"), n("8")), "20 silver rounds at an 8% premium")
        assertEquals(cad("3588.64"), Metals.value(Metals.fineOunces(1, n("1"), WeightUnit.OZT, n("0.9999")), spot, n("-3")), "a 3% buy-back discount")
    }
}
