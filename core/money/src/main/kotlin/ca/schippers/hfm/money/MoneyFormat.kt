package ca.schippers.hfm.money

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

/**
 * Locale-aware display and lenient input parsing (NFR-07).
 *
 * In French (Canada) 1234.56 CAD displays as "1 234,56 $"; in English (Canada) as "$1,234.56".
 */
object MoneyFormat {

    fun format(money: Money, locale: Locale): String {
        val currency = money.currency
        if (currency.isCrypto) return "${formatAmount(money, locale)} ${currency.code}"
        val nf = NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = java.util.Currency.getInstance(currency.code)
            minimumFractionDigits = currency.minorUnits
            maximumFractionDigits = currency.minorUnits
        }
        return nf.format(money.toBigDecimal())
    }

    /** Formats only the number, with grouping and the currency's decimals (for tables and inputs). */
    fun formatAmount(money: Money, locale: Locale): String {
        val nf = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = money.currency.minorUnits
            maximumFractionDigits = money.currency.minorUnits
        }
        return nf.format(money.toBigDecimal())
    }

    /**
     * Parses what a person types into an amount field. Accepts the locale's grouping and decimal
     * separators, any kind of space, a currency symbol or code, a leading minus sign or accounting
     * parentheses. In locales whose decimal separator is a comma, a single "." with no comma is
     * also read as the decimal separator, since many keypads only offer a dot.
     *
     * @throws IllegalArgumentException if the text is not a valid amount for the currency.
     */
    fun parse(text: String, currency: Currency, locale: Locale): Money =
        Money.exact(parseDecimal(text.replace(currency.code, "", ignoreCase = true), locale), currency)

    /** The same lenient rules as [parse], without a currency or a limit on decimals. */
    fun parseDecimal(text: String, locale: Locale): BigDecimal {
        var s = text.trim()
        require(s.isNotEmpty()) { "Amount is empty" }
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true
            s = s.substring(1, s.length - 1)
        }
        s = s.filterNot { it.isWhitespace() || it.isSpaceSeparator() || it in CURRENCY_SYMBOLS }
        if (s.startsWith("-")) {
            negative = !negative
            s = s.substring(1)
        }
        val symbols = DecimalFormatSymbols.getInstance(locale)
        val decimal = symbols.decimalSeparator
        val grouping = symbols.groupingSeparator

        val normalized = if (decimal == ',' && ',' !in s && s.count { it == '.' } == 1) {
            s
        } else {
            val withoutGrouping = if (grouping.isSpaceSeparator()) s else s.replace(grouping.toString(), "")
            withoutGrouping.replace(decimal, '.')
        }
        require(normalized.matches(AMOUNT)) { "Not a valid amount: $text" }
        val value = BigDecimal(normalized)
        return if (negative) value.negate() else value
    }

    private val AMOUNT = Regex("""\d+(\.\d*)?|\.\d+""")
    private const val CURRENCY_SYMBOLS = "$€£¥₿"

    private fun Char.isSpaceSeparator(): Boolean =
        this == ' ' || this == ' ' || Character.getType(this) == Character.SPACE_SEPARATOR.toInt()
}
