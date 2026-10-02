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

/** FX-02: the exchange rates in use, the Bank of Canada download, and manual rates. */
@Composable
fun RatesScreen(model: BooksModel) {
    val books = model.books
    val scope = rememberCoroutineScope()
    val today = today()
    val currencies = remember(model.revision) { books.rates.neededCurrencies().sortedBy { it.code } }
    var selected by remember { mutableStateOf<Currency?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today.toString()) }
    var rate by remember { mutableStateOf("") }
    val locale = model.language.locale

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.t("nav.rates"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { books.rates.updateFromBankOfCanada(today, Http::get) } }
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
            val cad = remember(model.revision, c) { books.rates.cadPerUnit(c, today) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(c.code, Modifier.width(80.dp))
                Text(cad?.let { model.t("rates.value", it.stripTrailingZeros().toPlainString(), c.code) } ?: model.t("rates.missing"), Modifier.weight(1f))
                TextButton(onClick = { selected = c }) { Text(model.t("rates.history")) }
            }
        }
        HorizontalDivider()
        Text(model.t("rates.manual"), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextInput(model.t("rates.currency"), code, Modifier.width(140.dp)) { code = it.uppercase() }
            DateInput(model.t("report.date"), date, Modifier.width(170.dp)) { date = it }
            TextInput(model.t("rates.cadPerUnit"), rate, Modifier.width(200.dp)) { rate = it }
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
                    Text(r.cadPerUnit.toPlainString(), Modifier.width(120.dp))
                    Text(model.t(if (r.manual) "rates.sourceManual" else "rates.sourceBoc"), Modifier.width(180.dp), style = MaterialTheme.typography.bodySmall)
                    if (r.manual) TextButton(onClick = { model.act { books.rates.deleteRate(c, r.date) } }) { Text(model.t("common.delete")) }
                }
            }
        }
    }
}
