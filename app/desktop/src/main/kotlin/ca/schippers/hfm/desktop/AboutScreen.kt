package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.awt.Desktop
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The app's version, and its update checks on Linux (DIST-05, SEC-08). */
@Composable
fun AboutScreen(state: AppState) {
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(state.t("about.title"), style = MaterialTheme.typography.titleLarge)
        Text(state.t("about.version", AppVersion.current))
        Text(state.t("about.publisher"), style = MaterialTheme.typography.bodySmall)
        UpdatesCard(state)
    }
}

@Composable
private fun UpdatesCard(state: AppState) {
    val updater = state.updater
    Card(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.t("update.title"), style = MaterialTheme.typography.titleMedium)
            if (updater.channel == null) {
                Text(state.t("update.notChecked"), style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(updater.enabled == true, onCheckedChange = { updater.setEnabled(it) })
                Text(state.t("update.enabled"))
            }
            when (val status = updater.status) {
                UpdateStatus.Idle -> if (updater.enabled != true) Text(state.t("update.off"), style = MaterialTheme.typography.bodySmall)
                UpdateStatus.Checking -> Text(state.t("update.checking"))
                is UpdateStatus.UpToDate -> Text(state.t("update.upToDate", dateTime(state, status.checkedAt)))
                is UpdateStatus.Available -> {
                    Text(state.t("update.available", status.offer.version), style = MaterialTheme.typography.titleSmall)
                    Text(status.offer.notes(state.language.tag))
                    Button(onClick = { updater.startDownload(status.offer) }) { Text(state.t("update.download")) }
                }
                is UpdateStatus.Downloading -> {
                    Text(state.t("update.downloading", status.fraction.toDouble()))
                    LinearProgressIndicator(progress = { status.fraction }, modifier = Modifier.fillMaxWidth())
                }
                is UpdateStatus.Ready -> if (status.replacedAppImage) {
                    Text(state.t("update.readyAppImage", status.offer.version))
                } else {
                    Text(state.t("update.readyPackage", status.offer.version, status.file.parent))
                    OutlinedButton(onClick = { runCatching { Desktop.getDesktop().open(status.file.toFile()) } }) {
                        Text(state.t("update.openInstaller"))
                    }
                    updater.installCommand(status.file)?.let { command ->
                        Text(state.t("update.orTerminal"), style = MaterialTheme.typography.bodySmall)
                        SelectionContainer { Text(command, fontFamily = FontFamily.Monospace) }
                    }
                }
                is UpdateStatus.Failed -> Text(state.t(status.messageKey, status.detail), color = MaterialTheme.colorScheme.error)
            }
            val busy = updater.status == UpdateStatus.Checking || updater.status is UpdateStatus.Downloading
            if (updater.enabled == true && !busy) {
                OutlinedButton(onClick = updater::startCheck) { Text(state.t("update.checkNow")) }
            }
        }
    }
}

/** Asked once, on first start, on copies that can check (the owner's decision, 2026-10-03). */
@Composable
fun UpdateQuestion(state: AppState) {
    val updater = state.updater
    if (!updater.shouldAsk) return
    // The first check then runs from the app's update loop, which restarts when the answer is saved.
    AlertDialog(
        onDismissRequest = {},
        title = { Text(state.t("update.ask.title")) },
        text = { Text(state.t("update.ask.body")) },
        confirmButton = {
            Button(onClick = { updater.setEnabled(true) }) { Text(state.t("update.ask.yes")) }
        },
        dismissButton = { TextButton(onClick = { updater.setEnabled(false) }) { Text(state.t("update.ask.no")) } },
    )
}

private fun dateTime(state: AppState, millis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(state.language.locale)
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
