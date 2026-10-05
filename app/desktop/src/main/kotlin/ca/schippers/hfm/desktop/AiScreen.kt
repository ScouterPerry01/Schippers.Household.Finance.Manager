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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.ai.AiFailure
import ca.schippers.hfm.ai.AiModel
import ca.schippers.hfm.ai.SecretStore
import ca.schippers.hfm.ai.SecretStoreException
import ca.schippers.hfm.books.AiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.math.BigDecimal
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Section 4.5: cloud AI reading, off until the user turns it on with their own key (AI-01 to AI-06). */
@Composable
fun AiScreen(model: BooksModel) {
    val books = model.books
    val settings = remember(model.revision) { books.ai.settings() }
    var keyText by remember { mutableStateOf("") }
    var keyState by remember(model.revision) { mutableStateOf(DesktopAi.key(model) != null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun save(s: AiSettings) {
        model.act { books.ai.saveSettings(s) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(model.t("nav.ai"), style = MaterialTheme.typography.titleLarge)
        Text(model.t("ai.intro"), style = MaterialTheme.typography.bodyMedium)

        // M-77: a viewer cannot turn AI reading on, since its readings are saved with documents.
        val canEdit = books.canEdit
        if (!canEdit) Text(model.t("ai.viewerNote"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        LabeledCheckbox(model.t("ai.enabled"), settings.enabled, enabled = canEdit) { save(settings.copy(enabled = it)) }
        LabeledCheckbox(model.t("ai.confirmEach"), settings.confirmEach, enabled = canEdit) { save(settings.copy(confirmEach = it)) }
        Picker(
            model.t("ai.model"), AiModel.CLAUDE, AiModel.byId(settings.model),
            { m -> model.t("ai.modelPrice", m.label, model.usd(m.inputPerMillion), model.usd(m.outputPerMillion)) },
            Modifier.width(760.dp), enabled = canEdit,
        ) { save(settings.copy(model = it.id)) }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(model.t("ai.key"), style = MaterialTheme.typography.titleMedium)
        val store = DesktopAi.secrets.kind
        Text(
            if (keyState) model.t("ai.keySaved", model.t("ai.store.$store")) else model.t("ai.noKey"),
            fontWeight = if (keyState) FontWeight.Medium else FontWeight.Normal,
        )
        if (store == SecretStore.Kind.SESSION) {
            val note = if (System.getProperty("hfm.demo") == "true") "ai.demoSession" else "ai.sessionOnly"
            Text(model.t(note), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("ai.keyField"), keyText, Modifier.width(420.dp), secret = true, supporting = model.t("ai.keyWhere")) { keyText = it }
            Button(enabled = keyText.isNotBlank(), onClick = {
                try {
                    DesktopAi.saveKey(model, keyText)
                    keyText = ""
                    keyState = true
                    message = model.t("ai.keyStored")
                } catch (e: SecretStoreException) {
                    message = model.t("ai.keyRefused", e.message.orEmpty())
                }
            }) { Text(model.t("ai.saveKey")) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = keyState, onClick = {
                message = model.t("ai.checking")
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { DesktopAi.provider(model)?.checkKey() } }
                    message = result.fold({ model.t("ai.keyWorks") }, { e -> model.aiFailure(e) })
                }
            }) { Text(model.t("ai.checkKey")) }
            TextButton(enabled = keyState, onClick = {
                DesktopAi.removeKey(model)
                keyState = false
                message = model.t("ai.keyRemoved")
            }) { Text(model.t("ai.removeKey"), color = if (keyState) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified) }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        DocumentTypesPart(model)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        UsagePart(model)
    }
}

/** AI-03: the document types, shipped and added, and files that could not be used. */
@Composable
private fun DocumentTypesPart(model: BooksModel) {
    val loaded = remember(model.revision) { DesktopAi.types() }
    Text(model.t("ai.types"), style = MaterialTheme.typography.titleMedium)
    for (t in loaded.types) {
        val name = t.kind?.let { model.t("documentKind.$it") } ?: t.id
        Text("$name · ${t.version}" + if (t.builtIn) "" else " · " + model.t("ai.typeAdded"), style = MaterialTheme.typography.bodySmall)
    }
    for (r in loaded.rejected) Text(model.t("ai.typeRejected", r.file, r.reason), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(model.t("ai.typesFolder", DesktopAi.typesFolder.toString()), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, fill = false))
        TextButton(onClick = {
            runCatching {
                Files.createDirectories(DesktopAi.typesFolder)
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(DesktopAi.typesFolder.toFile())
            }
        }) { Text(model.t("ai.openFolder")) }
    }
}

/** AI-06: the signed-in user's requests, with what each is estimated to have cost. */
@Composable
private fun UsagePart(model: BooksModel) {
    val zone = ZoneId.systemDefault()
    val periods = listOf("month", "year", "all")
    var period by remember { mutableStateOf("month") }
    val now = LocalDate.now(zone)
    val from = when (period) {
        "month" -> now.withDayOfMonth(1)
        "year" -> now.withDayOfYear(1)
        else -> LocalDate.of(2000, 1, 1)
    }.atStartOfDay(zone).toInstant().toEpochMilli()
    val entries = remember(model.revision, period) { model.books.ai.usage(from, System.currentTimeMillis() + 60_000) }
    val total = entries.fold(BigDecimal.ZERO) { a, e -> a + e.costUsd }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(model.t("ai.usage"), style = MaterialTheme.typography.titleMedium)
        Picker(model.t("ai.period"), periods, period, { model.t("ai.period.$it") }, Modifier.width(200.dp)) { period = it }
        Text(model.t("ai.usageTotal", entries.size, model.usd(total)), fontWeight = FontWeight.Medium)
    }
    if (entries.isEmpty()) Text(model.t("ai.noUsage"), style = MaterialTheme.typography.bodySmall)
    val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    for (e in entries.take(200)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(format.format(Instant.ofEpochMilli(e.usedAt).atZone(zone)), Modifier.width(130.dp), style = MaterialTheme.typography.bodySmall)
            Text(e.documentLabel ?: model.t("ai.deletedDocument"), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text(model.t("documentKind.${typeKind(e.typeId)}"), Modifier.width(170.dp), style = MaterialTheme.typography.bodySmall)
            Text(AiModel.priceOf(e.model, AiModel.DEFAULT).label, Modifier.width(130.dp), style = MaterialTheme.typography.bodySmall)
            Text(model.t("ai.tokens", e.inputTokens, e.outputTokens), Modifier.width(150.dp), style = MaterialTheme.typography.bodySmall)
            Text(model.usd(e.costUsd), Modifier.width(80.dp), style = MaterialTheme.typography.bodySmall)
            Text(model.t(if (e.succeeded) "ai.accepted" else "ai.rejected"), Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun typeKind(typeId: String) = ca.schippers.hfm.ai.DocumentType.kindFor(typeId) ?: ca.schippers.hfm.ocr.DocumentKind.OTHER

/** A US dollar estimate, to the cent, or to a tenth of a cent below one cent: "US$0.02", or "0,02 $ US" in French. */
internal fun BooksModel.usd(amount: BigDecimal): String {
    val scaled = amount.setScale(if (amount > BigDecimal.ZERO && amount < BigDecimal("0.01")) 3 else 2, java.math.RoundingMode.HALF_UP).toPlainString()
    return if (language == ca.schippers.hfm.i18n.Language.FRENCH) scaled.replace('.', ',') + " $ US" else "US$$scaled"
}

/** What a failed reading means, in the user's language. */
internal fun BooksModel.aiFailure(e: Throwable): String = when (e) {
    is AiFailure -> t("ai.failure.${e.reason}") + if (e.reason == AiFailure.Reason.INVALID || e.reason == AiFailure.Reason.SERVICE) " (${e.message.orEmpty().take(200)})" else ""
    else -> t("ai.failure.SERVICE") + " (${e.message.orEmpty().take(200)})"
}
