package ca.schippers.hfm.calc.assets

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DepreciationTest {

    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `straight line to the residual`() {
        // A $1,200 fridge over 10 years to 10 %: $1,080 lost, $9 a month.
        val price = cad("1200")
        assertEquals(cad("1200"), Depreciation.straightLine(price, d("2026-01-15"), 10, BigDecimal(10), d("2026-01-31")))
        assertEquals(cad("1092"), Depreciation.straightLine(price, d("2026-01-15"), 10, BigDecimal(10), d("2027-01-15")), "12 months")
        assertEquals(cad("660"), Depreciation.straightLine(price, d("2026-01-15"), 10, BigDecimal(10), d("2031-01-15")), "halfway")
        assertEquals(cad("120"), Depreciation.straightLine(price, d("2026-01-15"), 10, BigDecimal(10), d("2045-06-01")), "stays at the residual")
        assertEquals(cad("0"), Depreciation.straightLine(price, d("2026-01-15"), 10, BigDecimal(10), d("2025-12-31")))
    }

    @Test
    fun `a card's extended warranty`() {
        // One year from the manufacturer, doubled by the card for warranties up to 3 years.
        assertEquals(d("2028-01-15"), Depreciation.extendedEnd(d("2026-01-15"), d("2027-01-15"), 12, 3))
        assertNull(Depreciation.extendedEnd(d("2026-01-15"), d("2031-01-15"), 12, 3), "a 5-year warranty is too long to be extended")
        assertEquals(d("2031-07-15"), Depreciation.extendedEnd(d("2026-01-15"), d("2031-01-15"), 6, null))
    }
}
