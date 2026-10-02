package ca.schippers.hfm.money

import java.util.concurrent.ConcurrentHashMap

/**
 * A currency or crypto-asset with a fixed number of minor-unit digits.
 *
 * Fiat currencies take their minor units from ISO 4217 (CAD = 2, JPY = 0, BHD = 3).
 * Crypto-assets are held to 8 decimal places (CR-01, NFR-04).
 */
class Currency private constructor(val code: String, val minorUnits: Int, val isCrypto: Boolean) {

    override fun equals(other: Any?): Boolean = other is Currency && other.code == code
    override fun hashCode(): Int = code.hashCode()
    override fun toString(): String = code

    companion object {
        const val CRYPTO_MINOR_UNITS = 8

        private val cryptoCodes = setOf(
            "BTC", "ETH", "LTC", "BCH", "XRP", "ADA", "SOL", "DOT", "DOGE", "XLM", "USDC", "USDT",
            "AVAX", "LINK", "MATIC", "POL", "ATOM", "UNI", "ETC", "XMR", "ALGO", "TRX", "SHIB",
        )
        private val cache = ConcurrentHashMap<String, Currency>()

        val CAD: Currency = of("CAD")
        val USD: Currency = of("USD")
        val EUR: Currency = of("EUR")
        val BTC: Currency = of("BTC")

        /** Looks up a currency by its code, e.g. "CAD" or "BTC". */
        fun of(code: String): Currency {
            val upper = code.trim().uppercase()
            return cache.getOrPut(upper) { create(upper) }
        }

        /** Registers an extra crypto-asset code (e.g. one found in an exchange import). */
        fun registerCrypto(code: String): Currency {
            val upper = code.trim().uppercase()
            require(upper.matches(Regex("[A-Z0-9]{2,10}"))) { "Invalid crypto code: $code" }
            return cache.getOrPut(upper) { Currency(upper, CRYPTO_MINOR_UNITS, isCrypto = true) }
        }

        private fun create(code: String): Currency {
            if (code in cryptoCodes) return Currency(code, CRYPTO_MINOR_UNITS, isCrypto = true)
            val iso = try {
                java.util.Currency.getInstance(code)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Unknown currency code: $code", e)
            }
            val digits = iso.defaultFractionDigits
            require(digits >= 0) { "Currency $code has no minor unit and cannot hold money amounts" }
            return Currency(code, digits, isCrypto = false)
        }
    }
}
