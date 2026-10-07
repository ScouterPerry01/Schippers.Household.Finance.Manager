package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.ChecklistItem
import ca.schippers.hfm.books.ChecklistState
import ca.schippers.hfm.books.SeasonalChecklist
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.calc.schedule.SeasonWindow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import java.io.File
import javax.swing.JFileChooser

/** The last day of a season (its window ends the day before the next season starts). */
internal fun SeasonWindow.lastDay(): LocalDate = end.minus(DatePeriod(days = 1))

/** "Fall 2026", or "Winter 2026–2027" when the season runs over the new year. */
internal fun BooksModel.seasonName(w: SeasonWindow): String {
    val last = w.lastDay()
    val years = if (last.year != w.start.year) "${w.start.year}–${last.year}" else "${w.start.year}"
    return t("season.${w.season}", years)
}

/**
 * SEA-02: the seasonal checklist: every task of a season across vehicles and assets, the current
 * season first; ticking one records it in the service log; it can be printed or saved as a PDF.
 */
@Composable
internal fun SeasonalTab(model: BooksModel) {
    val books = model.books
    val today = today()
    val seasons = remember(model.revision) { books.seasonal.seasons(today) }
    var chosen by remember { mutableStateOf(0) }
    val window = seasons[chosen.coerceIn(seasons.indices)]
    val list = remember(model.revision, window) { books.seasonal.checklist(window, today) }
    var ticking by remember { mutableStateOf<ChecklistItem?>(null) }
    // A vehicle or asset whose group the user may only view cannot be ticked: the books would refuse the service.
    val tickable = remember(list) {
        list.items.map { it.vehicle to it.subjectId }.distinct().filter { (v, id) -> runCatching { books.seasonal.mayTick(v, id) }.getOrDefault(false) }.toSet()
    }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            seasons.forEachIndexed { i, w ->
                val label = model.seasonName(w) + if (i == 0) " · " + model.t("seasonal.now") else ""
                if (i == chosen) Button(onClick = {}) { Text(label) } else OutlinedButton(onClick = { chosen = i }) { Text(label) }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    model.t("seasonal.dates", model.date(window.start), model.date(window.lastDay())) + " · " + model.t("seasonal.progress", list.done, list.total),
                    fontWeight = FontWeight.Medium,
                )
                if (list.total > 0) LinearProgressIndicator(progress = { list.done.toFloat() / list.total }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            OutlinedButton(onClick = { model.act { printChecklist(model, list) } }, enabled = list.total > 0, modifier = Modifier.walkTarget("seasonal.print")) { Text(model.t("seasonal.print")) }
            OutlinedButton(onClick = { saveChecklist(model, list) }, enabled = list.total > 0) { Text(model.t("seasonal.savePdf")) }
        }
        Text(model.t("seasonal.hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 4.dp))
        val groups = list.items.groupBy { it.subjectId }
        if (list.items.isNotEmpty()) HeadingRow {
            ColumnHeading("✓", Modifier.width(48.dp), TextAlign.Center, spoken = model.t("column.done"))
            ColumnHeading(model.t("vehicles.taskName"), Modifier.weight(1f))
            ColumnHeading(model.t("column.when"), Modifier.width(280.dp))
            ColumnHeading(model.t("goals.status"), Modifier.width(120.dp))
        }
        LazyColumn {
            if (list.items.isEmpty()) item { Text(model.t("seasonal.none"), Modifier.padding(8.dp)) }
            for ((_, items) in groups) {
                val first = items.first()
                item(key = "h-" + first.subjectId) {
                    Text(
                        first.subjectName + if (first.vehicle) " · " + model.t("assets.vehicle") else "",
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                }
                items(items, key = { it.taskId }) { item -> ChecklistRow(model, item, current = chosen == 0, mayTick = (item.vehicle to item.subjectId) in tickable) { ticking = item } }
            }
        }
    }
    ticking?.let { item -> TickDialog(model, item) { ticking = null } }
}

@Composable
private fun ChecklistRow(model: BooksModel, item: ChecklistItem, current: Boolean, mayTick: Boolean, onTick: () -> Unit) {
    val done = item.state == ChecklistState.DONE
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        // A season still to come can be ticked early too: the task is then done ahead of time.
        Checkbox(checked = done, onCheckedChange = { if (!done) onTick() }, Modifier.width(48.dp).walkTarget("seasonal.tick"), enabled = !done && mayTick)
        Text(item.taskName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (done) FontWeight.Normal else FontWeight.Medium)
        Text(model.checklistWhen(item), Modifier.width(280.dp))
        val color = when (item.state) {
            ChecklistState.DUE -> MaterialTheme.colorScheme.error
            ChecklistState.SOON -> MaterialTheme.colorScheme.tertiary
            ChecklistState.DONE -> MaterialTheme.colorScheme.primary
            ChecklistState.TO_DO -> MaterialTheme.colorScheme.onSurface
        }
        Text(
            if (!current && !done) model.t("checklistState.TO_DO") else model.t("checklistState.${item.state}"),
            Modifier.width(120.dp), color = color, fontWeight = if (item.state == ChecklistState.DUE) FontWeight.Bold else FontWeight.Normal,
        )
    }
    HorizontalDivider()
}

/** SEA-02: ticks a task: a service on the date, with an optional cost, reading and note, as "Mark done" records it. */
@Composable
private fun TickDialog(model: BooksModel, item: ChecklistItem, onClose: () -> Unit) {
    val locale = model.language.locale
    var date by remember { mutableStateOf(today().toString()) }
    var cost by remember { mutableStateOf("") }
    var reading by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    FormDialog(model.t("seasonal.tickTitle", item.taskName, item.subjectName), model.t("seasonal.record"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            val day = runCatching { LocalDate.parse(date.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }
            val amount = parseAmount(cost, item.currency, locale)
            val value = reading.trim().ifEmpty { null }?.let { it.toIntOrNull() ?: throw ValidationException("error.invalidNumber") }
            model.books.seasonal.tick(item, day, note, amount?.toBigDecimal(), value)
        }
        if (ok != null) onClose()
    }) {
        Text(model.t("seasonal.tickHint"), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t("report.date"), date, Modifier.weight(1f)) { date = it }
            AmountInput(model.t("vehicles.cost"), cost, item.currency, locale, Modifier.weight(1f), model::money) { cost = it }
        }
        item.unit?.let { unit -> TextInput(model.t("meterUnit.$unit"), reading) { reading = it } }
        TextInput(model.t("calendar.notes"), note, singleLine = false) { note = it }
    }
}

/**
 * "done 2026-06-01", "done 2026-06-01 · due again 2026-06-08" for a task that repeats in the season
 * (SEA-02: done for this occurrence only), or "due 2026-06-08".
 */
private fun BooksModel.checklistWhen(item: ChecklistItem): String = when {
    item.state == ChecklistState.DONE && item.again != null -> t("seasonal.doneAgain", date(item.doneOn ?: today()), date(item.again!!))
    item.state == ChecklistState.DONE -> t("seasonal.doneOn", date(item.doneOn ?: today()))
    item.dueDate != null -> t("seasonal.due", date(item.dueDate!!))
    else -> ""
}

/** The checklist as printed: a box to tick by hand for each task, by vehicle or asset. */
internal fun checklistPdf(model: BooksModel, list: SeasonalChecklist, file: File) {
    val groups = list.items.groupBy { it.subjectId }.values.map { items ->
        ChecklistPdf.Group(
            items.first().subjectName,
            items.map { i ->
                ChecklistPdf.Line(
                    i.state == ChecklistState.DONE, i.taskName, model.checklistWhen(i),
                )
            },
        )
    }
    ChecklistPdf.write(
        model.t("seasonal.pdfTitle", model.seasonName(list.window)),
        model.t("seasonal.dates", model.date(list.window.start), model.date(list.window.lastDay())) + " · " + model.t("seasonal.progress", list.done, list.total),
        model.t("seasonal.pdfNotes"), groups, file,
    )
}

private fun printChecklist(model: BooksModel, list: SeasonalChecklist) {
    val file = PrintFiles.create("hfm-checklist-")
    checklistPdf(model, list, file)
    PrintFiles.printOrOpen(file)
}

private fun saveChecklist(model: BooksModel, list: SeasonalChecklist) {
    val name = model.t("seasonal.pdfTitle", model.seasonName(list.window)).replace(Regex("""[\\/:*?"<>|]"""), "-")
    val chooser = JFileChooser().apply { selectedFile = File("$name.pdf") }
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return
    val file = chooser.selectedFile.let { if (it.extension.equals("pdf", true)) it else File(it.path + ".pdf") }
    model.act { checklistPdf(model, list, file) }
}
