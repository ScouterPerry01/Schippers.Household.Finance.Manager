package ca.schippers.hfm.money

import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale

/**
 * The calculator built into amount fields (MAN-03): "12,50 + 3,25", "3 * 4.99", "(100 - 20) / 4".
 * Numbers follow [MoneyFormat] rules for the locale; only the final result is rounded to the
 * currency. Arithmetic is exact decimal, never floating point.
 */
object AmountExpression {

    private val OPERATOR_AFTER_NUMBER = Regex("""[\d)]\s*[-+*/×÷]\s*[\d(.,-]""")

    /** True when the text is a calculation rather than a single amount. */
    fun isExpression(text: String): Boolean = OPERATOR_AFTER_NUMBER.containsMatchIn(text)

    /** @throws IllegalArgumentException if the text is not a valid amount or calculation. */
    fun evaluate(text: String, currency: Currency, locale: Locale): Money {
        if (!isExpression(text)) return MoneyFormat.parse(text, currency, locale)
        return Money.of(Parser(text.replace(currency.code, "", ignoreCase = true), locale).parse(), currency)
    }

    private class Parser(private val text: String, private val locale: Locale) {
        private var pos = 0

        fun parse(): BigDecimal {
            val value = expression()
            skipSpaces()
            require(pos == text.length) { "Unexpected '${text[pos]}' in $text" }
            return value
        }

        private fun expression(): BigDecimal {
            var value = term()
            while (true) {
                skipSpaces()
                value = when (peek()) {
                    '+' -> { pos++; value.add(term()) }
                    '-' -> { pos++; value.subtract(term()) }
                    else -> return value
                }
            }
        }

        private fun term(): BigDecimal {
            var value = factor()
            while (true) {
                skipSpaces()
                value = when (peek()) {
                    '*', '×' -> { pos++; value.multiply(factor()) }
                    '/', '÷' -> {
                        pos++
                        val divisor = factor()
                        require(divisor.signum() != 0) { "Division by zero" }
                        value.divide(divisor, MathContext.DECIMAL128)
                    }
                    else -> return value
                }
            }
        }

        private fun factor(): BigDecimal {
            skipSpaces()
            return when (peek()) {
                '-' -> { pos++; factor().negate() }
                '(' -> {
                    pos++
                    val value = expression()
                    skipSpaces()
                    require(peek() == ')') { "Missing ')' in $text" }
                    pos++
                    value
                }
                else -> number()
            }
        }

        /** Digits, decimal and grouping separators, and spaces that sit between digits ("1 234,56"). */
        private fun number(): BigDecimal {
            val start = pos
            while (pos < text.length) {
                val c = text[pos]
                val partOfNumber = c.isDigit() || c == '.' || c == ',' || c in CURRENCY_SYMBOLS ||
                    (c.isWhitespace() || c == ' ' || c == ' ') && text.getOrNull(pos + 1)?.isDigit() == true &&
                    text.getOrNull(pos - 1)?.isDigit() == true
                if (!partOfNumber) break
                pos++
            }
            val token = text.substring(start, pos)
            require(token.isNotBlank()) { "A number is expected in $text" }
            return MoneyFormat.parseDecimal(token, locale)
        }

        private fun peek(): Char? = text.getOrNull(pos)

        private fun skipSpaces() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        companion object {
            const val CURRENCY_SYMBOLS = "$€£¥₿"
        }
    }
}
