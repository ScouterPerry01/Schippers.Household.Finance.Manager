package ca.schippers.hfm.money

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmountExpressionTest {

    private val enCA = Locale.forLanguageTag("en-CA")
    private val frCA = Locale.forLanguageTag("fr-CA")
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun eval(text: String, locale: Locale = enCA) = AmountExpression.evaluate(text, Currency.CAD, locale)

    @Test
    fun `plain amounts are not expressions`() {
        assertFalse(AmountExpression.isExpression("-12.50"))
        assertFalse(AmountExpression.isExpression("1 234,56"))
        assertTrue(AmountExpression.isExpression("12.50+3"))
        assertTrue(AmountExpression.isExpression("100 - 20"))
        assertEquals(cad("-12.50"), eval("-12.50"))
    }

    @Test
    fun `arithmetic with precedence and parentheses`() {
        assertEquals(cad("15.75"), eval("12.50 + 3.25"))
        assertEquals(cad("14.97"), eval("3 * 4.99"))
        assertEquals(cad("20.00"), eval("(100 - 20) / 4"))
        assertEquals(cad("6.50"), eval("2 + 3 * 1.5"))
        assertEquals(cad("-5.00"), eval("10 - 15"))
    }

    @Test
    fun `French numbers in calculations`() {
        assertEquals(cad("1246.81"), eval("1 234,56 + 12,25", frCA))
        assertEquals(cad("3.33"), eval("10 / 3", frCA))
    }

    @Test
    fun `only the result is rounded`() {
        // 3 x 4.995 = 14.985 -> 14.99; rounding each number first would give 15.00.
        assertEquals(cad("14.99"), eval("3 * 4.995"))
    }

    @Test
    fun `bad input is rejected`() {
        assertFailsWith<IllegalArgumentException> { eval("10 / 0") }
        assertFailsWith<IllegalArgumentException> { eval("(10 + 2") }
        assertFailsWith<IllegalArgumentException> { eval("10 + abc") }
    }
}
