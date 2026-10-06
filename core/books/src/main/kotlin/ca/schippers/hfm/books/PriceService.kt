package ca.schippers.hfm.books

import ca.schippers.hfm.calc.invest.normalized
import ca.schippers.hfm.money.Currency
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** The optional price downloads (INV-04, CR-05, PM-02), each off until the user turns it on. */
enum class PriceFeed { SECURITIES, CRYPTO, METALS }

enum class Metal(val yahooSymbol: String) { GOLD("GC=F"), SILVER("SI=F"), PLATINUM("PL=F"), PALLADIUM("PA=F") }

data class SpotPrice(val metal: Metal, val date: LocalDate, val cadPerOz: BigDecimal, val manual: Boolean)

/** What one update did: prices stored per feed, and what could not be fetched, in English for the log. */
data class PriceUpdate(val securities: Int, val crypto: Int, val metals: Int, val problems: List<String>) {
    val total: Int get() = securities + crypto + metals
}

/**
 * Market prices downloaded on request (INV-04, CR-05, PM-02). Every feed is off by default: turning
 * one on sends the symbols the household holds to that service, which the settings screen says.
 * Downloaded prices never replace manual ones.
 *
 * - Securities: Yahoo Finance's chart service (unofficial, no key), daily closes.
 * - Crypto-assets: CoinGecko's public API, daily prices in CAD, kept as each coin's CAD rate so
 *   every report converts wallets like any foreign currency.
 * - Precious metals: Yahoo Finance's near-month futures prices in USD per troy ounce, converted at
 *   the Bank of Canada rate, as an estimate of the spot price.
 */
class PriceService internal constructor(private val books: Books) {

    fun enabled(feed: PriceFeed): Boolean = books.setting(KEY + feed.name) == "true"

    /** M-77: only an administrator turns a feed on or off, since it sends what the household holds to that service. */
    fun setEnabled(feed: PriceFeed, on: Boolean) {
        requireAdmin(books)
        books.putSetting(KEY + feed.name, on.toString())
        books.session.audit("UPDATE", "setting", KEY + feed.name, on.toString())
    }

    /** Runs every enabled feed. Network problems are reported, never thrown, so the app works offline (NFR-10). */
    fun updateAll(today: LocalDate, fetch: (String) -> String): PriceUpdate {
        val problems = ArrayList<String>()
        val securities = if (enabled(PriceFeed.SECURITIES)) updateSecurities(fetch, problems) else 0
        val crypto = if (enabled(PriceFeed.CRYPTO)) updateCrypto(today, fetch, problems) else 0
        val metals = if (enabled(PriceFeed.METALS)) updateMetals(today, fetch, problems) else 0
        if (securities + crypto + metals > 0) books.putSetting(LAST_UPDATE, today.toString())
        return PriceUpdate(securities, crypto, metals, problems)
    }

    fun lastUpdate(): LocalDate? = books.setting(LAST_UPDATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    // --- Securities (INV-04) ----------------------------------------------------------------------

    /**
     * The symbol Yahoo Finance quotes a security under: Toronto listings end in .TO (.V for the
     * venture exchange, .NE for Cboe Canada, .CN for the CSE); U.S. listings have no suffix. Funds,
     * bonds and GICs are not quoted there and keep manual prices.
     */
    fun quoteSymbol(s: Security): String? {
        val symbol = s.symbol ?: return null
        if (s.kind in setOf(SecurityKind.MUTUAL_FUND, SecurityKind.BOND, SecurityKind.GIC, SecurityKind.OPTION)) return null
        val suffix = when (s.exchange) {
            "TSX" -> ".TO"
            "TSXV" -> ".V"
            "NEO", "CBOE CANADA" -> ".NE"
            "CSE" -> ".CN"
            null -> if (s.currency == Currency.CAD) ".TO" else ""
            else -> ""
        }
        return symbol.replace('.', '-') + suffix
    }

    private fun updateSecurities(fetch: (String) -> String, problems: MutableList<String>): Int =
        books.investments.securities().sumOf { s ->
            val quote = quoteSymbol(s) ?: return@sumOf 0
            val range = if (books.investments.prices(s.id).isEmpty()) "1y" else "1mo"
            runCatching { applyYahooChart(s, fetch(yahooUrl(quote, range))) }
                .getOrElse { problems += "${s.label}: ${it.message}"; 0 }
        }

    /** Stores the daily closes of a Yahoo Finance chart response for [s]; refuses a response in another currency. */
    fun applyYahooChart(s: Security, json: String): Int {
        val closes = yahooCloses(json)
        if (closes.currency != null && closes.currency != s.currency.code) throw IllegalStateException("quoted in ${closes.currency}, not ${s.currency.code}")
        closes.prices.forEach { (date, price) -> books.investments.setPrice(s.id, date, price, DOWNLOAD) }
        return closes.prices.size
    }

    private data class Closes(val currency: String?, val prices: List<Pair<LocalDate, BigDecimal>>)

    private fun yahooCloses(json: String): Closes {
        val result = Json.parseToJsonElement(json).jsonObject["chart"]?.jsonObject?.get("result")?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw IllegalStateException("no quote")
        val currency = result["meta"]?.jsonObject?.get("currency")?.jsonPrimitive?.content
        val zone = result["meta"]?.jsonObject?.get("exchangeTimezoneName")?.jsonPrimitive?.content?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of("America/Toronto")
        val times = result["timestamp"]?.jsonArray.orEmpty()
        val close = result["indicators"]?.jsonObject?.get("quote")?.jsonArray?.firstOrNull()?.jsonObject?.get("close")?.jsonArray.orEmpty()
        val prices = times.indices.mapNotNull { i ->
            val c = close.getOrNull(i)?.takeIf { it != JsonNull }?.jsonPrimitive?.content?.toBigDecimalOrNull() ?: return@mapNotNull null
            val day = Instant.ofEpochSecond(times[i].jsonPrimitive.content.toLong()).atZone(zone).toLocalDate()
            LocalDate(day.year, day.monthValue, day.dayOfMonth) to c.round(MathContext(10)).normalized()
        }.filter { it.second.signum() > 0 }.toMap().toList()
        return Closes(currency, prices)
    }

    // --- Crypto-assets (CR-05) ----------------------------------------------------------------------

    /** Coins held in the household's wallets. */
    fun coinsHeld(): List<Currency> = books.accounts.all(includeClosed = true).map { it.currency }.filter { it.isCrypto }.distinct().sortedBy { it.code }

    /** CoinGecko's name for a coin: the user's choice when set, else the usual one. */
    fun coinId(c: Currency): String? = books.setting(COIN_KEY + c.code)?.ifBlank { null } ?: COIN_IDS[c.code]

    fun setCoinId(c: Currency, id: String?) {
        requireEditor(books)
        books.putSetting(COIN_KEY + c.code, id?.trim()?.lowercase().orEmpty())
    }

    private fun updateCrypto(today: LocalDate, fetch: (String) -> String, problems: MutableList<String>): Int = coinsHeld().sumOf { c ->
        val id = coinId(c) ?: run { problems += books.text("importNote.noCoinGeckoName", c.code); return@sumOf 0 }
        val latest = books.core.latestMarketDate(c.code).executeAsOneOrNull()?.latest?.let(LocalDate::parse)
        val days = latest?.let { (it.toEpochDays() until today.toEpochDays()).count().coerceIn(2, 365) } ?: 365
        runCatching { applyCoinGecko(c, fetch(coinGeckoUrl(id, days))) }.getOrElse { problems += "${c.code}: ${it.message}"; 0 }
    }

    /** Stores a CoinGecko market chart (daily prices in CAD) as the coin's CAD rate per day; manual rates are kept. */
    fun applyCoinGecko(c: Currency, json: String): Int {
        val prices = Json.parseToJsonElement(json).jsonObject["prices"]?.jsonArray ?: throw IllegalStateException("no prices")
        val byDay = prices.associate { p ->
            val pair = p.jsonArray
            val day = Instant.ofEpochMilli(pair[0].jsonPrimitive.content.toBigDecimal().toLong()).atZone(ZoneOffset.UTC).toLocalDate()
            LocalDate(day.year, day.monthValue, day.dayOfMonth) to pair[1].jsonPrimitive.content.toBigDecimal().round(MathContext(12)).normalized()
        }.filter { it.value.signum() > 0 }
        books.session.core.transaction { byDay.forEach { (d, price) -> books.core.insertMarketRate(c.code, d.toString(), price.toPlainString()) } }
        return byDay.size
    }

    // --- Precious metals (PM-02) ---------------------------------------------------------------------

    fun spot(metal: Metal, date: LocalDate): SpotPrice? = books.core.spotOnOrBefore(metal.name, date.toString()).executeAsOneOrNull()
        ?.let { SpotPrice(metal, LocalDate.parse(it.date), BigDecimal(it.cad_per_oz), it.source == "MANUAL") }

    fun spots(metal: Metal, limit: Int = 30): List<SpotPrice> = books.core.spots(metal.name, limit.toLong()).executeAsList()
        .map { SpotPrice(metal, LocalDate.parse(it.date), BigDecimal(it.cad_per_oz), it.source == "MANUAL") }

    /** A spot price entered by hand, in CAD per troy ounce; downloads never replace it. */
    fun setSpot(metal: Metal, date: LocalDate, cadPerOz: BigDecimal) {
        requireEditor(books)
        validate(cadPerOz.signum() > 0, "error.invalidNumber")
        books.core.putSpot(metal.name, date.toString(), cadPerOz.normalized().toPlainString(), "MANUAL")
    }

    fun deleteSpot(metal: Metal, date: LocalDate) {
        requireEditor(books)
        books.core.deleteSpot(metal.name, date.toString())
    }

    private fun updateMetals(today: LocalDate, fetch: (String) -> String, problems: MutableList<String>): Int = Metal.entries.sumOf { m ->
        val range = if (spot(m, today) == null) "1y" else "1mo"
        runCatching { applyYahooMetal(m, fetch(yahooUrl(m.yahooSymbol, range))) }.getOrElse { problems += "${m.name}: ${it.message}"; 0 }
    }

    /** Stores a metal's daily USD closes as CAD spot prices, at the Bank of Canada rate of each day. */
    fun applyYahooMetal(m: Metal, json: String): Int {
        var count = 0
        for ((date, usd) in yahooCloses(json).prices) {
            val rate = books.rates.rate(Currency.USD, Currency.CAD, date) ?: continue
            books.core.putSpot(m.name, date.toString(), usd.multiply(rate, MathContext.DECIMAL64).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), "MARKET")
            count++
        }
        return count
    }

    companion object {
        const val DOWNLOAD = "DOWNLOAD"
        private const val KEY = "prices.feed."
        private const val COIN_KEY = "prices.coin."
        private const val LAST_UPDATE = "prices.lastUpdate"

        /** Who to credit in the settings, as the services ask. */
        const val ATTRIBUTION = "Prices: Yahoo Finance (unofficial), CoinGecko (https://www.coingecko.com)"

        /** CoinGecko's names for common coins; others can be set by hand. */
        val COIN_IDS = mapOf(
            "BTC" to "bitcoin", "ETH" to "ethereum", "LTC" to "litecoin", "BCH" to "bitcoin-cash", "XRP" to "ripple", "ADA" to "cardano",
            "SOL" to "solana", "DOT" to "polkadot", "DOGE" to "dogecoin", "XLM" to "stellar", "USDC" to "usd-coin", "USDT" to "tether",
            "AVAX" to "avalanche-2", "LINK" to "chainlink", "MATIC" to "matic-network", "POL" to "polygon-ecosystem-token", "ATOM" to "cosmos",
            "UNI" to "uniswap", "ETC" to "ethereum-classic", "XMR" to "monero", "ALGO" to "algorand", "TRX" to "tron", "SHIB" to "shiba-inu",
        )

        fun yahooUrl(symbol: String, range: String) =
            "https://query1.finance.yahoo.com/v8/finance/chart/${java.net.URLEncoder.encode(symbol, Charsets.UTF_8)}?range=$range&interval=1d"

        fun coinGeckoUrl(id: String, days: Int) =
            "https://api.coingecko.com/api/v3/coins/${java.net.URLEncoder.encode(id, Charsets.UTF_8)}/market_chart?vs_currency=cad&days=$days&interval=daily"
    }
}
