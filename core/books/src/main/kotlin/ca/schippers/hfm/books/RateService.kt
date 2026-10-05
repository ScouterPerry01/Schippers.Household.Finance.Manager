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

enum class RateSource { BOC, OPEN, MANUAL, MARKET }

data class FxRate(val currency: Currency, val date: LocalDate, val cadPerUnit: BigDecimal, val source: RateSource) {
    val manual: Boolean get() = source == RateSource.MANUAL
}

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
            .map { FxRate(currency, LocalDate.parse(it.date), BigDecimal(it.cad_per_unit), RateSource.valueOf(it.source)) }

    /** FX-02 manual override, e.g. for a currency the Bank of Canada does not publish. */
    fun setManual(currency: Currency, date: LocalDate, cadPerUnit: BigDecimal) {
        requireEditor(books)
        validate(cadPerUnit.signum() > 0, "error.ratePositive")
        books.core.upsertManualRate(currency.code, date.toString(), cadPerUnit.toPlainString())
        books.session.audit("SET_RATE", "fx_rate", currency.code, "$date=$cadPerUnit")
    }

    /** M-77: like every change to the household's rates, not for viewers. */
    fun deleteRate(currency: Currency, date: LocalDate) {
        requireEditor(books)
        books.core.deleteRate(currency.code, date.toString())
    }

    /** Currencies used by the household's accounts that need rates and that the Bank of Canada publishes. */
    fun neededCurrencies(): Set<Currency> = allNeeded().filter { it.code in BANK_OF_CANADA }.toSet()

    /** Every fiat currency that needs rates: account and security currencies, the base currency and followed ones (FX-07). */
    fun allNeeded(): Set<Currency> = (
        books.accounts.list(includeClosed = true).map { it.account.currency } + baseCurrency + followed() +
            runCatching { books.investments.securities(includeArchived = true).map { it.currency } }.getOrDefault(emptyList())
        ).filter { it != Currency.CAD && !it.isCrypto }.toSet()

    /** FX-07: currencies the user follows although no account uses them. */
    fun followed(): List<Currency> = books.setting(FOLLOWED).orEmpty().split(',').filter { it.isNotBlank() }
        .mapNotNull { runCatching { Currency.of(it) }.getOrNull() }

    fun follow(currency: Currency) {
        requireEditor(books)
        validate(!currency.isCrypto, "error.unknownCurrency")
        books.putSetting(FOLLOWED, (followed() + currency).distinct().joinToString(",") { it.code })
    }

    fun unfollow(currency: Currency) {
        requireEditor(books)
        books.putSetting(FOLLOWED, (followed() - currency).joinToString(",") { it.code })
    }

    /**
     * FX-08: whether the optional second source is used; off by default. M-77: like the price feeds,
     * only an administrator turns it on or off, since it sends currency codes to an outside service.
     */
    var openSourceEnabled: Boolean
        get() = books.setting(OPEN_SOURCE) == "true"
        set(value) {
            requireAdmin(books)
            books.putSetting(OPEN_SOURCE, value.toString())
        }

    /** Currencies the Bank of Canada does not publish; they need the second source or manual rates. */
    fun notOnBankOfCanada(): Set<Currency> = allNeeded().filter { it.code !in BANK_OF_CANADA }.toSet()

    /**
     * Every update that applies: the Bank of Canada always, then the second source for the other
     * currencies when it is enabled (FX-08). Returns the number of rates stored.
     */
    fun updateAll(today: LocalDate, fetch: (String) -> String): Int {
        var count = updateFromBankOfCanada(today, fetch)
        if (openSourceEnabled && notOnBankOfCanada().isNotEmpty()) count += updateFromOpenSource(fetch)
        return count
    }

    /**
     * FX-08: today's rates from ExchangeRate-API's open access service (about 160 currencies, no key,
     * attribution required), stored for the currencies the Bank of Canada does not publish. They never
     * replace Bank of Canada or manual rates.
     */
    fun updateFromOpenSource(fetch: (String) -> String): Int {
        val wanted = notOnBankOfCanada().map { it.code }.toSet()
        if (wanted.isEmpty()) return 0
        return applyOpenSource(fetch(OPEN_SOURCE_URL), wanted)
    }

    /** Stores rates from an open.er-api.com response (units of each currency per CAD). */
    fun applyOpenSource(json: String, wanted: Set<String>): Int {
        val root = Json.parseToJsonElement(json).jsonObject
        if (root["result"]?.jsonPrimitive?.content != "success") return 0
        val date = root["time_last_update_utc"]?.jsonPrimitive?.content?.let(::rfc1123Date) ?: return 0
        val rates = root["rates"]?.jsonObject ?: return 0
        var count = 0
        books.session.core.transaction {
            for (code in wanted) {
                val perCad = rates[code]?.jsonPrimitive?.content?.toBigDecimalOrNull() ?: continue
                if (perCad.signum() <= 0) continue
                books.core.insertOpenRate(code, date.toString(), BigDecimal.ONE.divide(perCad, MathContext.DECIMAL64).toPlainString())
                count++
            }
        }
        return count
    }

    private fun rfc1123Date(text: String): LocalDate? = runCatching {
        val d = java.time.ZonedDateTime.parse(text, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDate()
        LocalDate(d.year, d.monthValue, d.dayOfMonth)
    }.getOrNull()

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
        private const val FOLLOWED = "fx.followed"
        private const val OPEN_SOURCE = "fx.openSource"
        const val OPEN_SOURCE_URL = "https://open.er-api.com/v6/latest/CAD"
        const val OPEN_SOURCE_ATTRIBUTION = "Rates By Exchange Rate API (https://www.exchangerate-api.com)"

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
