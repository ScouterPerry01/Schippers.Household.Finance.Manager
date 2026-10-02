package ca.schippers.hfm.books

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.MathContext

data class FxRate(val currency: Currency, val date: LocalDate, val cadPerUnit: BigDecimal, val manual: Boolean)

/**
 * Exchange rates (FX-01, FX-02): the Bank of Canada's daily average rates, stored in CAD per unit
 * and downloaded when online, with manual rates that are never overwritten. Conversions use the
 * rate of the day, or the latest earlier one (weekends and holidays have no rate).
 */
class RateService internal constructor(private val books: Books) {

    /** The household's base currency for totals and reports (FX-01), CAD by default. */
    val baseCurrency: Currency by lazy { Currency.of(books.core.household().executeAsOne().base_currency) }

    fun cadPerUnit(currency: Currency, date: LocalDate): BigDecimal? {
        if (currency == Currency.CAD) return BigDecimal.ONE
        val q = books.core
        val row = q.rateOnOrBefore(currency.code, date.toString()).executeAsOneOrNull()
            ?: q.rateAfter(currency.code, date.toString()).executeAsOneOrNull()
        return row?.cad_per_unit?.let(::BigDecimal)
    }

    /** Units of [to] per unit of [from] on [date], or null when a rate is missing. */
    fun rate(from: Currency, to: Currency, date: LocalDate): BigDecimal? {
        if (from == to) return BigDecimal.ONE
        val fromCad = cadPerUnit(from, date) ?: return null
        val toCad = cadPerUnit(to, date) ?: return null
        return fromCad.divide(toCad, MathContext.DECIMAL64)
    }

    fun convert(money: Money, to: Currency, date: LocalDate): Money? =
        if (money.currency == to) money else rate(money.currency, to, date)?.let { money.convert(to, it) }

    fun list(currency: Currency, from: LocalDate, to: LocalDate): List<FxRate> =
        books.core.ratesFor(currency.code, from.toString(), to.toString()).executeAsList()
            .map { FxRate(currency, LocalDate.parse(it.date), BigDecimal(it.cad_per_unit), it.source == "MANUAL") }

    /** FX-02 manual override, e.g. for a currency the Bank of Canada does not publish. */
    fun setManual(currency: Currency, date: LocalDate, cadPerUnit: BigDecimal) {
        validate(cadPerUnit.signum() > 0, "error.ratePositive")
        books.core.upsertManualRate(currency.code, date.toString(), cadPerUnit.toPlainString())
        books.session.audit("SET_RATE", "fx_rate", currency.code, "$date=$cadPerUnit")
    }

    fun deleteRate(currency: Currency, date: LocalDate) = books.core.deleteRate(currency.code, date.toString())

    /** Currencies used by the household's accounts that need rates and that the Bank of Canada publishes. */
    fun neededCurrencies(): Set<Currency> = books.accounts.list(includeClosed = true)
        .map { it.account.currency }
        .filter { it != Currency.CAD && !it.isCrypto && it.code in BANK_OF_CANADA }
        .toSet() + (if (baseCurrency != Currency.CAD && baseCurrency.code in BANK_OF_CANADA) setOf(baseCurrency) else emptySet())

    /**
     * Downloads missing daily rates from the Bank of Canada up to [today]. [fetch] performs the
     * HTTPS request (TLS, SEC-06); it is passed in so the books stay usable offline (NFR-10).
     * Returns the number of rates stored.
     */
    fun updateFromBankOfCanada(today: LocalDate, fetch: (String) -> String): Int {
        val currencies = neededCurrencies()
        if (currencies.isEmpty()) return 0
        val earliestAccount = books.accounts.list(includeClosed = true).minOfOrNull { it.account.openingDate } ?: today
        val start = currencies.minOf { c ->
            books.core.latestRateDate(c.code).executeAsOneOrNull()?.latest?.let { LocalDate.parse(it).plus(DatePeriod(days = 1)) }
                ?: maxOf(earliestAccount, today.minus(DatePeriod(years = 5)))
        }
        if (start > today) return 0
        return applyBankOfCanada(fetch(bankOfCanadaUrl(currencies, start, today)))
    }

    /** Stores the observations of a Bank of Canada Valet response. */
    fun applyBankOfCanada(json: String): Int {
        val observations = Json.parseToJsonElement(json).jsonObject["observations"]?.jsonArray ?: return 0
        var count = 0
        books.session.core.transaction {
            for (o in observations) {
                val obj = o.jsonObject
                val date = obj["d"]?.jsonPrimitive?.content ?: continue
                for ((series, value) in obj) {
                    if (!series.startsWith("FX") || !series.endsWith("CAD")) continue
                    val rate = value.jsonObject["v"]?.jsonPrimitive?.content?.toBigDecimalOrNull() ?: continue
                    books.core.insertBocRate(series.removePrefix("FX").removeSuffix("CAD"), date, rate.toPlainString())
                    count++
                }
            }
        }
        return count
    }

    companion object {
        /** Currencies in the Bank of Canada's daily exchange rate group. */
        val BANK_OF_CANADA = setOf(
            "AUD", "BRL", "CHF", "CNY", "EUR", "GBP", "HKD", "IDR", "INR", "JPY", "KRW", "MXN", "MYR", "NOK",
            "NZD", "PEN", "PLN", "RUB", "SAR", "SEK", "SGD", "THB", "TRY", "TWD", "USD", "VND", "ZAR",
        )

        fun bankOfCanadaUrl(currencies: Set<Currency>, start: LocalDate, end: LocalDate): String =
            "https://www.bankofcanada.ca/valet/observations/" +
                currencies.sortedBy { it.code }.joinToString(",") { "FX${it.code}CAD" } +
                "/json?start_date=$start&end_date=$end"
    }
}
