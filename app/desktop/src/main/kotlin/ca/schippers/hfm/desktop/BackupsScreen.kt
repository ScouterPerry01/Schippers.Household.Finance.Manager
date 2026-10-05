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
import ca.schippers.hfm.books.BackupFrequency
import ca.schippers.hfm.books.BackupSettings
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.swing.JFileChooser

/** BAK-01 to BAK-05 and EXP-01: backup settings, backups, checks, and the full export. */
@Composable
fun BackupsScreen(model: BooksModel) {
    val books = model.books
    val scope = rememberCoroutineScope()
    val settings = remember(model.revision) { books.backups.settings() }
    val status = remember(model.revision) { books.backups.status() }
    val list = remember(model.revision) { runCatching { books.backups.backups() }.getOrDefault(emptyList()) }
    var keep by remember(settings) { mutableStateOf(settings.keep.toString()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmExport by remember { mutableStateOf(false) }

    fun runBackup() {
        busy = true
        message = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { books.backups.backUpNow(SqlCipherJdbcDriverFactory()) } }
            message = result.fold(
                { run -> if (run.check.ok) model.t("backup.done", run.backup.file.fileName) else model.t("backup.checkFailed", run.check.problems.joinToString("; ")) },
                { model.describe(it) },
            )
            busy = false
            model.changed()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(model.t("nav.backups"), style = MaterialTheme.typography.titleLarge)
        Text(model.t("backup.explain"), style = MaterialTheme.typography.bodySmall)

        // M-77: the folder, frequency and number kept are the administrator's choice.
        val admin = books.users.isAdministrator
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(settings.dir?.toString() ?: model.t("backup.noFolder"), Modifier.weight(1f))
            OutlinedButton(enabled = admin, onClick = {
                chooseDirectory(model.t("backup.chooseFolder"))?.let { dir -> model.act { books.backups.saveSettings(settings.copy(dir = dir)) } }
            }) { Text(model.t("backup.chooseFolder")) }
        }
        Text(model.t("backup.folderHint"), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Picker(model.t("backup.frequency"), BackupFrequency.entries, settings.frequency, { model.t("backupFrequency.$it") }, Modifier.width(220.dp), enabled = admin) {
                model.act { books.backups.saveSettings(settings.copy(frequency = it)) }
            }
            TextInput(model.t("backup.keep"), keep, Modifier.width(200.dp), enabled = admin) { keep = it }
            OutlinedButton(enabled = admin, onClick = {
                model.act { books.backups.saveSettings(settings.copy(keep = keep.trim().toIntOrNull() ?: throw ValidationException("error.backupKeep"))) }
            }) { Text(model.t("common.save")) }
        }
        if (!admin) Text(model.t("backup.adminOnly"), style = MaterialTheme.typography.bodySmall)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = !busy && settings.dir != null, onClick = ::runBackup) { Text(model.t(if (busy) "backup.running" else "backup.now")) }
            Text(
                status.lastSuccess?.let { model.t("backup.last", formatInstant(model, it)) } ?: model.t("backup.never"),
                color = if (books.backups.needsReminder(Instant.now())) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        status.lastProblem?.let { Text(model.t("backup.problem", it), color = MaterialTheme.colorScheme.error) }
        message?.let { Text(it) }

        HorizontalDivider()
        Text(model.t("backup.list", list.size), style = MaterialTheme.typography.titleMedium)
        for (b in list) {
            var result by remember(b.file) { mutableStateOf<String?>(null) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatInstant(model, b.createdAt), Modifier.width(220.dp))
                Text(b.file.fileName.toString(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(model.t("backup.size", (b.size + 1023) / 1024), Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    val check = books.backups.verify(b.file, SqlCipherJdbcDriverFactory())
                    result = if (check.ok) model.t("backup.checkOk", check.checkedDatabases) else model.t("backup.checkFailed", check.problems.joinToString("; "))
                }) { Text(model.t("backup.check")) }
            }
            result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Text(model.t("backup.restoreHint"), style = MaterialTheme.typography.bodySmall)

        HorizontalDivider()
        Text(model.t("export.title"), style = MaterialTheme.typography.titleMedium)
        Text(model.t("export.explain"), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { confirmExport = true }) { Text(model.t("export.button")) }
    }

    if (confirmExport) {
        FormDialog(model.t("export.title"), model.t("export.continue"), model.t("common.cancel"), onDismiss = { confirmExport = false }, onSave = {
            confirmExport = false
            val chooser = JFileChooser().apply {
                dialogTitle = model.t("export.title")
                selectedFile = File("household-export.zip")
            }
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                model.act { books.backups.exportAll(chooser.selectedFile.toPath()) }?.let { message = model.t("export.done", chooser.selectedFile) }
            }
        }) {
            Text(model.t("export.warning"), color = MaterialTheme.colorScheme.error)
        }
    }
}

/** BAK-01: runs scheduled backups while the household is open; checks every 30 minutes. */
suspend fun backupScheduler(model: BooksModel) {
    while (true) {
        val ran = withContext(Dispatchers.IO) {
            runCatching {
                if (model.books.backups.isDue(Instant.now())) {
                    model.books.backups.backUpNow(SqlCipherJdbcDriverFactory())
                    true
                } else {
                    false
                }
            }.getOrDefault(false)
        }
        if (ran) model.changed()
        delay(30 * 60 * 1000L)
    }
}

/** Default for a new household: daily backups into a folder beside it (BAK-01), until the user picks another. */
fun ensureBackupDefaults(model: BooksModel) {
    val books = model.books
    if (books.backups.settings().dir != null) return
    val dir = model.session.dir.toAbsolutePath()
    val folder: Path = dir.resolveSibling(dir.fileName.toString().removeSuffix(".hfm") + " - backups")
    runCatching { books.backups.saveSettings(BackupSettings(folder, BackupFrequency.DAILY, 10)) }
}

private fun formatInstant(model: BooksModel, instant: Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(model.language.locale)
        .format(instant.atZone(ZoneId.systemDefault()))

fun chooseDirectory(title: String): Path? {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
}
