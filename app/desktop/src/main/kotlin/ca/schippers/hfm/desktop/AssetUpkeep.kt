package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Asset
import ca.schippers.hfm.books.AssetKind
import ca.schippers.hfm.books.AssetServiceRecord
import ca.schippers.hfm.books.AssetTask
import ca.schippers.hfm.books.AssetTaskStatus
import ca.schippers.hfm.books.MeterUnit
import ca.schippers.hfm.books.UpkeepDue
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.calc.schedule.DueState
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

private sealed interface UpkeepEdit {
    data class Task(val value: AssetTask) : UpkeepEdit
    data class Service(val value: AssetServiceRecord) : UpkeepEdit
    data object Reading : UpkeepEdit
}

private fun dateOf(text: String): LocalDate? = text.trim().ifEmpty { null }?.let { runCatching { LocalDate.parse(it) }.getOrElse { throw ValidationException("error.invalidDate") } }

private fun intOf(text: String): Int? = text.trim().ifEmpty { null }?.let { it.toIntOrNull() ?: throw ValidationException("error.invalidNumber") }

@Composable
private fun stateColor(state: DueState) = when (state) {
    DueState.DUE -> MaterialTheme.colorScheme.error
    DueState.SOON -> MaterialTheme.colorScheme.tertiary
    DueState.OK -> MaterialTheme.colorScheme.onSurface
}

// --- Due this month, vehicles and assets together (MNT-05) ------------------------------------------

/** MNT-05: what is due this month or overdue, on vehicles and everything else; [onOpen] opens an asset. */
@Composable
internal fun UpkeepTab(model: BooksModel, onOpen: (Asset) -> Unit) {
    val books = model.books
    val today = today()
    var ahead by remember { mutableStateOf(false) }
    val endOfMonth = LocalDate(today.year, today.month, 1).plus(DatePeriod(months = 1)).plus(DatePeriod(days = -1))
    val until = if (ahead) today.plus(DatePeriod(years = 1)) else endOfMonth
    val items = remember(model.revision, ahead) {
        (books.upkeepDue(today) + books.upkeepBetween(today, until, today)).distinctBy { it.taskId }.sortedWith(compareBy(nullsLast()) { it.status.nextDate })
    }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(model.t("upkeep.dueHint"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            for (a in listOf(false, true)) {
                val label = model.t(if (a) "upkeep.nextYear" else "upkeep.thisMonth")
                if (a == ahead) Button(onClick = {}) { Text(label) } else OutlinedButton(onClick = { ahead = a }) { Text(label) }
            }
        }
        LazyColumn(Modifier.padding(top = 8.dp)) {
            if (items.isEmpty()) item { Text(model.t("upkeep.noneDue"), Modifier.padding(8.dp)) }
            items(items, key = { it.taskId }) { u -> UpkeepRow(model, u, onOpen) }
        }
    }
}

@Composable
private fun UpkeepRow(model: BooksModel, u: UpkeepDue, onOpen: (Asset) -> Unit) {
    val s = u.status
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(u.taskName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(u.subjectName + if (u.vehicle) " · " + model.t("assets.vehicle") else "", style = MaterialTheme.typography.bodySmall)
        }
        val due = listOfNotNull(s.dueDate?.let(model::date), s.dueUsage?.let { model.usage(it, u.unit) }).joinToString(" ${model.t("vehicles.or")} ")
        Column(Modifier.width(320.dp)) {
            Text(model.t("taskState.${s.state}") + if (due.isNotEmpty()) " · $due" else "", color = stateColor(s.state), fontWeight = if (s.state == DueState.OK) FontWeight.Normal else FontWeight.Bold)
            s.forecastDate?.takeIf { s.dueUsage != null }?.let { Text(model.t("upkeep.forecast", model.date(it)), style = MaterialTheme.typography.bodySmall) }
        }
        TextButton(onClick = {
            if (u.vehicle) model.section = Section.VEHICLES else model.act { model.books.assets.get(u.subjectId) }?.let(onOpen)
        }) { Text(model.t("upkeep.open")) }
    }
    HorizontalDivider()
}

// --- One asset's maintenance (MNT-01 to MNT-04, MNT-06) ---------------------------------------------

/** The maintenance part of the asset dialog: tasks and when they fall due, the meter, the service log and the costs. */
@Composable
internal fun AssetUpkeepBlock(model: BooksModel, a: Asset) {
    val books = model.books
    val today = today()
    var edit by remember { mutableStateOf<UpkeepEdit?>(null) }
    val statuses = remember(model.revision, a.id) { books.assetMaintenance.taskStatuses(a.id, today) }
    val paused = remember(model.revision, a.id) { books.assetMaintenance.tasks(a.id).filter { !it.active } }
    val services = remember(model.revision, a.id) { books.assetMaintenance.services(a.id) }
    val usage = remember(model.revision, a.id) { books.assetMaintenance.latestUsage(a.id) }
    val cost = remember(model.revision, a.id) { books.assetMaintenance.costs(a.id, LocalDate(today.year, 1, 1), today) }
    val hasTemplates = books.assetMaintenance.templates(a.kind).isNotEmpty()
    val taskNames = statuses.associate { it.task.id to it.task.name } + paused.associate { it.id to it.name }

    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text(model.t("upkeep.title"), style = MaterialTheme.typography.titleSmall)
    Text(model.t("upkeep.explain"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (hasTemplates) OutlinedButton(onClick = { model.act { books.assetMaintenance.addStarterTasks(a.id, today) { model.t("assetTemplate.$it") } } }) { Text(model.t("vehicles.starterTasks")) }
        OutlinedButton(onClick = { edit = UpkeepEdit.Task(AssetTask("", a.id, "", intervalMonths = 12, startDate = today, startUsage = usage, remindUsage = if (a.meter == MeterUnit.KM) 500 else 10)) }) {
            Text(model.t("vehicles.addTask"))
        }
        if (a.meter != null) {
            OutlinedButton(onClick = { edit = UpkeepEdit.Reading }) { Text(model.t("upkeep.addReading")) }
            usage?.let { Text(model.t("upkeep.latest", model.usage(it, a.meter)), style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (statuses.isEmpty() && paused.isEmpty()) Text(model.t("upkeep.noTasks"), style = MaterialTheme.typography.bodySmall)
    val serviced = services.flatMap { it.taskIds }.toSet()
    for (s in statuses) TaskLine(model, a, s, usage, s.task.id in serviced) { edit = it }
    for (t in paused) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(t.name + " (${model.t("vehicles.taskOff")})", Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
            TextButton(onClick = { edit = UpkeepEdit.Task(t) }) { Text(model.t("common.edit")) }
        }
    }

    // MNT-04: the service log.
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(model.t("upkeep.services"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { edit = UpkeepEdit.Service(AssetServiceRecord("", a.id, today, usage)) }) { Text(model.t("upkeep.addService")) }
    }
    if (services.isEmpty()) Text(model.t("upkeep.noServices"), style = MaterialTheme.typography.bodySmall)
    for (s in services) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(model.date(s.date), Modifier.width(100.dp))
            Column(Modifier.weight(1f)) {
                Text(s.taskIds.mapNotNull(taskNames::get).joinToString(", ").ifBlank { s.notes ?: model.t("vehicles.service") }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(if (s.diy) model.t("vehicles.diy") else s.provider, s.usage?.let { model.usage(it, a.meter) }, s.parts, s.transactionId?.let { model.t("vehicles.paymentLinked") }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(s.cost?.let(model::money).orEmpty(), Modifier.width(100.dp))
            TextButton(onClick = { edit = UpkeepEdit.Service(s) }) { Text(model.t("common.edit")) }
        }
    }

    // MNT-06: cost of ownership this year.
    Text(
        listOfNotNull(
            model.t("upkeep.costs", today.year, model.money(cost.costs.total)),
            cost.insurance?.let { model.t("upkeep.insurance", model.money(it)) },
            cost.usage?.let { model.t("upkeep.used", model.usage(it, cost.unit)) },
            cost.costPerUnit?.let { model.t("upkeep.perUnit.${cost.unit}", model.money(it)) },
        ).joinToString(" · "),
        Modifier.padding(top = 6.dp), fontWeight = FontWeight.Medium,
    )
    Text(model.t("upkeep.costsHow"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

    when (val e = edit) {
        is UpkeepEdit.Task -> TaskDialog(model, a, e.value) { edit = null }
        is UpkeepEdit.Service -> ServiceDialog(model, a, e.value) { edit = null }
        UpkeepEdit.Reading -> ReadingDialog(model, a) { edit = null }
        null -> Unit
    }
}

@Composable
private fun TaskLine(model: BooksModel, a: Asset, s: AssetTaskStatus, usage: Int?, serviced: Boolean, onEdit: (UpkeepEdit) -> Unit) {
    val t = s.task
    val d = s.due
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t.name, fontWeight = FontWeight.Medium)
            Text(
                listOfNotNull(
                    listOfNotNull(t.intervalMonths?.let { model.t("vehicles.everyMonths", it) }, t.intervalUsage?.let { model.t("vehicles.everyKm", model.usage(it, a.meter)) })
                        .joinToString(" ${model.t("vehicles.or")} "),
                    // Until it is first done, the schedule counts from its start.
                    s.lastDate?.let { model.t(if (serviced) "vehicles.lastDone" else "upkeep.since", model.date(it)) + (s.lastUsage?.let { u -> " · ${model.usage(u, a.meter)}" }.orEmpty()) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(Modifier.width(250.dp)) {
            val due = listOfNotNull(d.dueDate?.let(model::date), d.dueUsage?.let { model.usage(it, a.meter) }).joinToString(" ${model.t("vehicles.or")} ")
            Text(model.t("taskState.${d.state}") + if (due.isNotEmpty()) " · $due" else "", color = stateColor(d.state), fontWeight = if (d.state == DueState.OK) FontWeight.Normal else FontWeight.Bold)
            d.forecastDate?.takeIf { d.dueUsage != null }?.let { Text(model.t("upkeep.forecast", model.date(it)), style = MaterialTheme.typography.bodySmall) }
        }
        TextButton(onClick = { onEdit(UpkeepEdit.Service(AssetServiceRecord("", a.id, today(), usage, taskIds = setOf(t.id)))) }) { Text(model.t("vehicles.markDone")) }
        TextButton(onClick = { onEdit(UpkeepEdit.Task(t)) }) { Text(model.t("common.edit")) }
    }
}

@Composable
private fun ReadingDialog(model: BooksModel, a: Asset, onClose: () -> Unit) {
    var date by remember { mutableStateOf(today().toString()) }
    var value by remember { mutableStateOf("") }
    FormDialog(model.t("upkeep.addReading") + " · " + a.name, model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        if (model.act { model.books.assetMaintenance.addReading(a.id, dateOf(date) ?: throw ValidationException("error.invalidDate"), intOf(value) ?: throw ValidationException("error.invalidNumber")) } != null) onClose()
    }) {
        DateInput(model.t("report.date"), date, Modifier.fillMaxWidth()) { date = it }
        TextInput(model.t("meterUnit.${a.meter}"), value) { value = it }
    }
}

@Composable
private fun TaskDialog(model: BooksModel, a: Asset, existing: AssetTask, onClose: () -> Unit) {
    var name by remember { mutableStateOf(existing.name) }
    var months by remember { mutableStateOf(existing.intervalMonths?.toString().orEmpty()) }
    var every by remember { mutableStateOf(existing.intervalUsage?.toString().orEmpty()) }
    var start by remember { mutableStateOf(existing.startDate?.toString().orEmpty()) }
    var startUsage by remember { mutableStateOf(existing.startUsage?.toString().orEmpty()) }
    var remindDays by remember { mutableStateOf(existing.remindDays.toString()) }
    var remindUsage by remember { mutableStateOf(existing.remindUsage.toString()) }
    var active by remember { mutableStateOf(existing.active) }
    var notes by remember { mutableStateOf(existing.notes.orEmpty()) }
    val meter = a.meter
    FormDialog(
        model.t(if (existing.id.isBlank()) "vehicles.addTask" else "vehicles.editTask") + " · " + a.name, model.t("common.save"), model.t("common.cancel"),
        canSave = name.isNotBlank(), onDismiss = onClose,
        onSave = {
            val ok = model.act {
                model.books.assetMaintenance.saveTask(
                    existing.copy(
                        name = name, intervalMonths = intOf(months), intervalUsage = intOf(every).takeIf { meter != null }, startDate = dateOf(start),
                        startUsage = intOf(startUsage).takeIf { meter != null }, remindDays = intOf(remindDays) ?: 14, remindUsage = intOf(remindUsage) ?: existing.remindUsage,
                        active = active, notes = notes,
                    ),
                )
            }
            if (ok != null) onClose()
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextInput(model.t("vehicles.taskName"), name) { name = it }
            Text(model.t(if (meter == null) "upkeep.intervalExplainTime" else "vehicles.intervalExplain"), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("vehicles.intervalMonths"), months, Modifier.weight(1f)) { months = it }
                if (meter != null) TextInput(model.t("upkeep.every.$meter"), every, Modifier.weight(1f)) { every = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(model.t("vehicles.lastDoneDate"), start, Modifier.weight(1f)) { start = it }
                if (meter != null) TextInput(model.t("upkeep.lastAt.$meter"), startUsage, Modifier.weight(1f)) { startUsage = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("vehicles.remindDays"), remindDays, Modifier.weight(1f)) { remindDays = it }
                if (meter != null) TextInput(model.t("upkeep.remind.$meter"), remindUsage, Modifier.weight(1f)) { remindUsage = it }
            }
            TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
            if (existing.id.isNotBlank()) {
                LabeledCheckbox(model.t("vehicles.taskActive"), active) { active = it }
                TextButton(onClick = { if (model.act { model.books.assetMaintenance.deleteTask(a.id, existing.id) } != null) onClose() }) {
                    Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceDialog(model: BooksModel, a: Asset, existing: AssetServiceRecord, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val tasks = remember { books.assetMaintenance.tasks(a.id).filter { it.active || it.id in existing.taskIds } }
    var date by remember { mutableStateOf(existing.date.toString()) }
    var usage by remember { mutableStateOf(existing.usage?.toString().orEmpty()) }
    var provider by remember { mutableStateOf(existing.provider.orEmpty()) }
    var diy by remember { mutableStateOf(existing.diy) }
    var parts by remember { mutableStateOf(existing.parts.orEmpty()) }
    var cost by remember { mutableStateOf(existing.cost?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var notes by remember { mutableStateOf(existing.notes.orEmpty()) }
    var done by remember { mutableStateOf(existing.taskIds) }
    val payment = remember { PaymentState() }
    val leisure = a.kind in setOf(AssetKind.COTTAGE, AssetKind.RV, AssetKind.BOAT, AssetKind.TRAILER)
    FormDialog(
        model.t(if (existing.id.isBlank()) "upkeep.addService" else "vehicles.editService") + " · " + a.name, model.t("common.save"), model.t("common.cancel"),
        onDismiss = onClose,
        onSave = {
            val ok = model.act {
                books.assetMaintenance.saveService(
                    existing.copy(
                        date = dateOf(date) ?: throw ValidationException("error.invalidDate"), usage = intOf(usage).takeIf { a.meter != null }, provider = provider, diy = diy,
                        parts = parts, cost = parseAmount(cost, a.currency, locale), notes = notes, taskIds = done,
                    ),
                    payment.draft().takeIf { existing.transactionId == null },
                )
            }
            if (ok != null) onClose()
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(model.t("report.date"), date, Modifier.weight(1f)) { date = it }
                if (a.meter != null) TextInput(model.t("meterUnit.${a.meter}"), usage, Modifier.weight(1f)) { usage = it }
            }
            if (tasks.isNotEmpty()) {
                Text(model.t("vehicles.tasksDone"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (t in tasks) LabeledCheckbox(t.name, t.id in done) { on -> done = if (on) done + t.id else done - t.id }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextInput(model.t("upkeep.provider"), provider, Modifier.weight(1f), enabled = !diy) { provider = it }
                Column(Modifier.weight(1f)) { LabeledCheckbox(model.t("vehicles.diy"), diy) { diy = it } }
            }
            TextInput(model.t("upkeep.parts"), parts) { parts = it }
            AmountInput(model.t("vehicles.cost"), cost, a.currency, locale, Modifier.fillMaxWidth(), model::money) { cost = it }
            TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
            if (existing.transactionId == null) {
                PaymentFields(model, a.currency, if (leisure) "leisure.cottage_rv" else "housing.maintenance", payment)
            } else {
                Text(model.t("vehicles.paymentLinked"), style = MaterialTheme.typography.bodySmall)
            }
            if (existing.id.isNotBlank()) {
                TextButton(onClick = { if (model.act { books.assetMaintenance.deleteService(a.id, existing.id) } != null) onClose() }) {
                    Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
