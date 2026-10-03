package ca.schippers.hfm.desktop

import ca.schippers.hfm.books.Metal
import ca.schippers.hfm.books.PriceService
import ca.schippers.hfm.books.PriceFeed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.RateService
import ca.schippers.hfm.books.RateSource
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** HTTPS GET for public data such as exchange rates (SEC-06: TLS only). */
object Http {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build()

    fun get(url: String): String {
        require(url.startsWith("https://")) { "Only HTTPS is allowed" }
        val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(30)).header("Accept", "application/json")
            .header("User-Agent", "HouseholdFinanceManager/1.0").GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { body ->
            check(response.statusCode() == 200) { "HTTP ${response.statusCode()}" }
            // A public service's answer is not trusted: anything larger than any real answer is refused.
            val bytes = body.readNBytes(MAX_BYTES + 1)
            check(bytes.size <= MAX_BYTES) { "Answer too large" }
            return String(bytes, Charsets.UTF_8)
        }
    }

    private const val MAX_BYTES = 20 * 1024 * 1024
}

/** FX-02, FX-07, FX-08: the exchange rates in use, followed currencies, the downloads and manual rates. */
@Composable
fun RatesScreen(model: BooksModel) {
    val books = model.books
    val scope = rememberCoroutineScope()
    val today = today()
    val currencies = remember(model.revision) { books.rates.allNeeded().sortedBy { it.code } }
    val followed = remember(model.revision) { books.rates.followed().toSet() }
    val notOnBoc = remember(model.revision) { books.rates.notOnBankOfCanada() }
    val openEnabled = remember(model.revision) { books.rates.openSourceEnabled }
    var selected by remember { mutableStateOf<Currency?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today.toString()) }
    var rate by remember { mutableStateOf("") }
    var toFollow by remember { mutableStateOf<java.util.Currency?>(null) }
    val locale = model.language.locale
    val allCurrencies = remember {
        java.util.Currency.getAvailableCurrencies().filter { it.defaultFractionDigits >= 0 && it.currencyCode != "CAD" }.sortedBy { it.currencyCode }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.t("nav.rates"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { books.rates.updateAll(today, Http::get) } }
                    status = result.fold({ model.t("rates.updated", it) }, { model.t("rates.failed", it.message.orEmpty()) })
                    busy = false
                    model.changed()
                }
            }) { Text(model.t("rates.update")) }
        }
        Text(model.t("rates.explain", books.reports.base.code), style = MaterialTheme.typography.bodySmall)
        status?.let { Text(it) }
        if (currencies.isEmpty()) Text(model.t("rates.none"))
        for (c in currencies) {
            val latest = remember(model.revision, c) { books.rates.list(c, today.minus(DatePeriod(days = 30)), today).lastOrNull() }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(c.code, Modifier.width(80.dp))
                Text(
                    latest?.let { model.t("rates.value", shortRate(it.cadPerUnit), c.code) }
                        ?: model.t(if (c in notOnBoc && !openEnabled) "rates.needsSecondSource" else "rates.missing"),
                    Modifier.weight(1f),
                )
                Text(latest?.let { model.date(it.date) + " · " + sourceName(model, it.source) }.orEmpty(), Modifier.width(260.dp), style = MaterialTheme.typography.bodySmall)
                if (c in followed) TextButton(onClick = { model.act { books.rates.unfollow(c) } }) { Text(model.t("rates.unfollow")) }
                TextButton(onClick = { selected = c }) { Text(model.t("rates.history")) }
            }
        }

        // FX-07: currencies to follow although no account uses them (travel, family abroad).
        HorizontalDivider()
        Text(model.t("rates.follow.title"), style = MaterialTheme.typography.titleMedium)
        Text(model.t("rates.follow.explain"), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val shown = currencies.map { it.code }.toSet()
            Picker(
                model.t("rates.currency"), allCurrencies.filter { it.currencyCode !in shown }, toFollow,
                { "${it.currencyCode} · ${it.getDisplayName(locale)}" }, Modifier.width(420.dp),
            ) { toFollow = it }
            OutlinedButton(enabled = toFollow != null, onClick = {
                toFollow?.let { c -> model.act { books.rates.follow(Currency.of(c.currencyCode)) } }
                toFollow = null
            }) { Text(model.t("rates.follow")) }
        }

        // FX-08: the optional second source for currencies the Bank of Canada does not publish.
        HorizontalDivider()
        Text(model.t("rates.second.title"), style = MaterialTheme.typography.titleMedium)
        LabeledCheckbox(model.t("rates.second.enable"), openEnabled) { on -> model.act { books.rates.openSourceEnabled = on } }
        Text(model.t("rates.second.explain"), style = MaterialTheme.typography.bodySmall)
        if (openEnabled) Text(RateService.OPEN_SOURCE_ATTRIBUTION, style = MaterialTheme.typography.bodySmall)
        if (notOnBoc.isNotEmpty()) {
            Text(model.t("rates.second.currencies", notOnBoc.sortedBy { it.code }.joinToString(", ") { it.code }), style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider()
        Text(model.t("rates.manual"), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextInput(model.t("rates.currency"), code, Modifier.width(140.dp)) { code = it.uppercase() }
            DateInput(model.t("report.date"), date, Modifier.width(170.dp)) { date = it }
            TextInput(model.t("rates.cadPerUnit"), rate, Modifier.width(280.dp)) { rate = it }
            OutlinedButton(onClick = {
                model.act {
                    val currency = runCatching { Currency.of(code) }.getOrElse { throw ValidationException("error.unknownCurrency") }
                    val d = runCatching { LocalDate.parse(date.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }
                    val value = runCatching { MoneyFormat.parseDecimal(rate, locale) }.getOrElse { throw ValidationException("error.invalidNumber") }
                    books.rates.setManual(currency, d, value)
                    rate = ""
                }
            }) { Text(model.t("common.save")) }
        }
        MarketPrices(model)
        selected?.let { c ->
            HorizontalDivider()
            Text(model.t("rates.historyOf", c.code), style = MaterialTheme.typography.titleMedium)
            val rows = remember(model.revision, c) { books.rates.list(c, today.minus(DatePeriod(days = 60)), today).reversed() }
            for (r in rows) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(model.date(r.date), Modifier.width(120.dp))
                    Text(shortRate(r.cadPerUnit), Modifier.width(120.dp))
                    Text(sourceName(model, r.source), Modifier.width(220.dp), style = MaterialTheme.typography.bodySmall)
                    if (r.manual) TextButton(onClick = { model.act { books.rates.deleteRate(c, r.date) } }) { Text(model.t("common.delete")) }
                }
            }
        }
    }
}

private fun sourceName(model: BooksModel, source: RateSource): String = model.t("rates.source.${source.name}")

/**
 * INV-04, CR-05, PM-02: the optional price downloads, each off until turned on, coin prices with
 * their CoinGecko names, and precious metal spot prices with manual entry.
 */
@Composable
private fun MarketPrices(model: BooksModel) {
    val books = model.books
    val scope = rememberCoroutineScope()
    val today = today()
    val locale = model.language.locale
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val coins = remember(model.revision) { books.prices.coinsHeld() }
    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(model.t("prices.title"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Button(enabled = !busy && PriceFeed.entries.any { books.prices.enabled(it) }, onClick = {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { books.prices.updateAll(today, Http::get) } }
                status = result.fold(
                    { r -> model.t("prices.updated", r.total) + if (r.problems.isNotEmpty()) " " + model.t("prices.problems", r.problems.joinToString("; ")) else "" },
                    { model.t("rates.failed", it.message.orEmpty()) },
                )
                busy = false
                model.changed()
            }
        }) { Text(model.t("prices.update")) }
    }
    Text(model.t("prices.explain"), style = MaterialTheme.typography.bodySmall)
    for (feed in PriceFeed.entries) {
        val on = remember(model.revision, feed) { books.prices.enabled(feed) }
        LabeledCheckbox(model.t("prices.feed.$feed"), on) { v -> model.act { books.prices.setEnabled(feed, v) } }
    }
    Text(PriceService.ATTRIBUTION, style = MaterialTheme.typography.bodySmall)
    books.prices.lastUpdate()?.let { Text(model.t("prices.last", model.date(it)), style = MaterialTheme.typography.bodySmall) }
    status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

    if (coins.isNotEmpty()) {
        Text(model.t("prices.coins"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        for (c in coins) {
            val latest = remember(model.revision, c) { books.rates.list(c, today.minus(DatePeriod(days = 30)), today).lastOrNull() }
            var id by remember(model.revision, c) { mutableStateOf(books.prices.coinId(c).orEmpty()) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(c.code, Modifier.width(80.dp))
                Text(latest?.let { model.money(ca.schippers.hfm.money.Money.of(it.cadPerUnit, Currency.CAD)) + " · " + model.date(it.date) + " · " + sourceName(model, it.source) } ?: model.t("rates.missing"), Modifier.weight(1f))
                TextInput(model.t("prices.coinId"), id, Modifier.width(260.dp)) { id = it }
                TextButton(onClick = { model.act { books.prices.setCoinId(c, id) } }) { Text(model.t("common.save")) }
            }
        }
        Text(model.t("prices.coinsHint"), style = MaterialTheme.typography.bodySmall)
    }

    Text(model.t("prices.metals"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    for (m in Metal.entries) {
        val spot = remember(model.revision, m) { books.prices.spot(m, today) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(model.t("metal.$m"), Modifier.width(140.dp))
            Text(spot?.let { model.t("prices.perOz", model.money(ca.schippers.hfm.money.Money.of(it.cadPerOz, Currency.CAD))) + " · " + model.date(it.date) + " · " + model.t(if (it.manual) "rates.source.MANUAL" else "prices.market") } ?: model.t("rates.missing"), Modifier.weight(1f))
        }
    }
    var metal by remember { mutableStateOf(Metal.GOLD) }
    var date by remember { mutableStateOf(today.toString()) }
    var price by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Picker(model.t("prices.metal"), Metal.entries, metal, { model.t("metal.$it") }, Modifier.width(180.dp)) { metal = it }
        DateInput(model.t("report.date"), date, Modifier.width(170.dp)) { date = it }
        TextInput(model.t("prices.cadPerOz"), price, Modifier.width(240.dp)) { price = it }
        OutlinedButton(onClick = {
            model.act {
                val d = runCatching { LocalDate.parse(date.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }
                val value = runCatching { MoneyFormat.parseDecimal(price, locale) }.getOrElse { throw ValidationException("error.invalidNumber") }
                books.prices.setSpot(metal, d, value)
                price = ""
            }
        }) { Text(model.t("common.save")) }
    }
    Text(model.t("prices.metalsHint"), style = MaterialTheme.typography.bodySmall)
}

/** Six significant digits are plenty to read a rate; the full value is kept for conversions. */
private fun shortRate(value: java.math.BigDecimal): String = value.round(java.math.MathContext(6)).stripTrailingZeros().toPlainString()
