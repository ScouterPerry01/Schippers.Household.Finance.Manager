package ca.schippers.hfm.desktop

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
        val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(30)).header("Accept", "application/json").GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "HTTP ${response.statusCode()}" }
        return response.body()
    }
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

/** Six significant digits are plenty to read a rate; the full value is kept for conversions. */
private fun shortRate(value: java.math.BigDecimal): String = value.round(java.math.MathContext(6)).stripTrailingZeros().toPlainString()
