package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.BroughtInCalendar
import ca.schippers.hfm.books.BroughtInItem
import ca.schippers.hfm.books.CalendarItem
import ca.schippers.hfm.books.IcsImportResult
import ca.schippers.hfm.domain.PermissionLevel
import kotlinx.datetime.LocalTime
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/*
 * CSY-01 to CSY-05 on the computer: how items brought in from phones show in the calendar's Agenda
 * and Month views (read-only, marked with their source and owner), the list of the user's own
 * brought-in calendars with the group others see them in, and the .ics import.
 */

internal fun importedKey(item: CalendarItem.Imported): String = "i-${item.item.id}-${item.date}"

/** "Alex: busy" for a busy-only entry, otherwise the item's title. */
internal fun importedTitle(model: BooksModel, item: BroughtInItem): String =
    if (item.busyOnly) model.t("calendar.broughtIn.busy", item.ownerName) else item.title!!.ifBlank { model.t("ics.untitled") }

/** The line in a month's day: its start time and title. */
internal fun importedMonthText(model: BooksModel, item: CalendarItem.Imported): String {
    val i = item.item
    val time = i.startTime?.takeIf { item.date == i.startDate }?.let(::hhmm)
    return listOfNotNull(time, importedTitle(model, i)).joinToString(" ")
}

/** "Work (alex@work.ca)", or "Alex's calendar" when the calendar's name is not shown to the viewer. */
internal fun importedSource(model: BooksModel, item: BroughtInItem): String =
    item.sourceName?.ifBlank { null }?.let { name -> item.accountName?.let { model.t("calendar.broughtIn.sourceAccount", name, it) } ?: name }
        ?: model.t("calendar.broughtIn.ownersCalendar", item.ownerName)

private fun hhmm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

private fun updatedText(model: BooksModel, millis: Long): String =
    java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT).withLocale(model.language.locale)
        .format(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()))

/** The buttons above the calendar: the calendars brought in from phones, and an .ics import. */
@Composable
internal fun BroughtInButtons(model: BooksModel) {
    var managing by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    TextButton(onClick = { managing = true }) { Text(model.t("calendar.broughtIn.manage")) }
    TextButton(onClick = { importing = true }) { Text(model.t("calendar.ics.import")) }
    if (managing) BroughtInDialog(model) { managing = false }
    if (importing) IcsImportDialog(model) { importing = false }
}

/** CSY-03: the user's own brought-in calendars; who sees them is chosen here, the rest on the phone. */
@Composable
internal fun BroughtInDialog(model: BooksModel, onClose: () -> Unit) {
    val books = model.books
    val calendars = remember(model.revision) { runCatching { books.broughtIn.calendars() }.getOrDefault(emptyList()) }
    val groups = remember(model.revision) { books.groups().filter { it.level.allows(PermissionLevel.CAPTURE_ONLY) } }
    var removing by remember { mutableStateOf<BroughtInCalendar?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(model.t("calendar.broughtIn.title")) },
        text = {
            Column(Modifier.width(620.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(model.t("calendar.broughtIn.intro"), style = MaterialTheme.typography.bodySmall)
                if (calendars.isEmpty()) Text(model.t("calendar.broughtIn.none"))
                for (c in calendars) {
                    Card {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.accountName?.let { model.t("calendar.broughtIn.sourceAccount", c.sourceName, it) } ?: c.sourceName, fontWeight = FontWeight.Medium)
                                    Text(
                                        listOf(
                                            model.t("calendar.broughtIn.visibility.${c.visibility}"),
                                            model.t("calendar.broughtIn.items", c.items),
                                            model.t("calendar.broughtIn.updated", updatedText(model, c.updatedAt)),
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                TextButton(onClick = { removing = c }) { Text(model.t("calendar.broughtIn.remove")) }
                            }
                            Picker(
                                model.t("calendar.broughtIn.othersIn"), groups, groups.firstOrNull { it.id == c.othersGroupId },
                                { if (it.id == c.privateGroupId) model.t("calendar.broughtIn.onlyMe", it.name) else it.name },
                            ) { g -> if (g.id != c.othersGroupId) model.act { books.broughtIn.setOthersGroup(c.id, g.id) } }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(model.t("common.close")) } },
    )
    removing?.let { c ->
        AskBeforeDeleting(model, model.t("calendar.broughtIn.removeConfirm", c.sourceName), onDismiss = { removing = null }) {
            model.act { books.broughtIn.remove(c.id) } != null
        }
    }
}

/** CSY-05: an .ics file copied once into a group of the user's choice. */
@Composable
internal fun IcsImportDialog(model: BooksModel, onClose: () -> Unit) {
    var groupId by remember { mutableStateOf(model.editableGroups().firstOrNull { !it.isPrivate }?.id ?: model.editableGroups().firstOrNull()?.id) }
    var result by remember { mutableStateOf<IcsImportResult?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(model.t("calendar.ics.title")) },
        text = {
            Column(Modifier.width(560.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(model.t("calendar.ics.body"))
                GroupPicker(model, groupId, enabled = result == null) { groupId = it.id }
                val r = result
                if (r == null) {
                    OutlinedButton(enabled = groupId != null, onClick = {
                        val file = chooseIcs(model) ?: return@OutlinedButton
                        result = model.act {
                            require(Files.size(file) <= ca.schippers.hfm.importers.ICalendar.MAX_BYTES) { "Too large" }
                            model.books.icsImport.import(groupId!!, Files.readAllBytes(file))
                        }
                    }) { Text(model.t("calendar.ics.choose")) }
                } else {
                    HorizontalDivider()
                    Text(model.t("calendar.ics.done", r.created, r.expanded), fontWeight = FontWeight.Medium)
                    if (r.notes.isNotEmpty()) {
                        Text(model.t("calendar.ics.notes"))
                        r.notes.take(MAX_NOTES).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        if (r.notes.size > MAX_NOTES) Text(model.t("calendar.ics.moreNotes", r.notes.size - MAX_NOTES), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(model.t("common.close")) } },
    )
}

private const val MAX_NOTES = 30

private fun chooseIcs(model: BooksModel): Path? {
    val chooser = JFileChooser().apply {
        dialogTitle = model.t("calendar.ics.title")
        fileFilter = FileNameExtensionFilter(model.t("calendar.ics.fileType"), "ics", "ical", "ifb", "icalendar")
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
}
