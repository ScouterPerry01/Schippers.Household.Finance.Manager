package ca.schippers.hfm.calc.invest

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class ForeignExchangeTest {

    private fun n(s: String) = BigDecimal(s)

    /** The first $200 of a year's net gain or loss is left out, whichever way it goes. */
    @Test
    fun `the 200 dollar exemption`() {
        assertEquals(n("0.00"), ForeignExchange.reportable(n("150.00")))
        assertEquals(n("0.00"), ForeignExchange.reportable(n("-200.00")))
        assertEquals(n("150.00"), ForeignExchange.reportable(n("350.00")))
        assertEquals(n("-0.01"), ForeignExchange.reportable(n("-200.01")))
    }
}
