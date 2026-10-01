package ca.schippers.hfm.money

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyFormatTest {

    private val frCA = Locale.forLanguageTag("fr-CA")
    private val enCA = Locale.forLanguageTag("en-CA")

    /** The JDK uses no-break and narrow no-break spaces; compare with plain spaces. */
    private fun plain(s: String) = s.replace(' ', ' ').replace(' ', ' ')

    @Test
    fun `French Canada formats as 1 234,56 $`() {
        assertEquals("1 234,56 $", plain(MoneyFormat.format(Money.parse("1234.56", Currency.CAD), frCA)))
    }

    @Test
    fun `English Canada formats as $1,234,56`() {
        assertEquals("$1,234.56", MoneyFormat.format(Money.parse("1234.56", Currency.CAD), enCA))
    }

    @Test
    fun `crypto shows 8 decimals and the code`() {
        assertEquals("0.00145906 BTC", MoneyFormat.format(Money.parse("0.00145906", Currency.BTC), enCA))
    }

    @Test
    fun `parses French input`() {
        val expected = Money.parse("1234.56", Currency.CAD)
        assertEquals(expected, MoneyFormat.parse("1 234,56 $", Currency.CAD, frCA))
        assertEquals(expected, MoneyFormat.parse("1 234,56 $", Currency.CAD, frCA))
        assertEquals(expected, MoneyFormat.parse("1234.56", Currency.CAD, frCA))
    }

    @Test
    fun `parses English input including negatives`() {
        assertEquals(Money.parse("1234.56", Currency.CAD), MoneyFormat.parse("$1,234.56", Currency.CAD, enCA))
        assertEquals(Money.parse("-12.50", Currency.CAD), MoneyFormat.parse("-12.5", Currency.CAD, enCA))
        assertEquals(Money.parse("-12.50", Currency.CAD), MoneyFormat.parse("(12.50)", Currency.CAD, enCA))
        assertEquals(Money.parse("7", Currency.CAD), MoneyFormat.parse("CAD 7", Currency.CAD, enCA))
    }

    @Test
    fun `rejects garbage and too many decimals`() {
        assertFailsWith<IllegalArgumentException> { MoneyFormat.parse("12abc", Currency.CAD, enCA) }
        assertFailsWith<IllegalArgumentException> { MoneyFormat.parse("1.234", Currency.CAD, enCA) }
        assertFailsWith<IllegalArgumentException> { MoneyFormat.parse("", Currency.CAD, enCA) }
    }
}
