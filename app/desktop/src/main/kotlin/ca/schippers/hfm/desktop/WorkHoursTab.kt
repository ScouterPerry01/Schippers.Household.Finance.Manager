package ca.schippers.hfm.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.WorkClient
import ca.schippers.hfm.books.WorkEntry
import ca.schippers.hfm.books.WorkTask
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat

/** HRS-01: side-income clients, the hours worked for them, and the unbilled hours turned into an invoice. */
@Composable
internal fun HoursTab(model: BooksModel) {
    val books = model.books
    var showArchived by remember { mutableStateOf(false) }
    val clients = remember(model.revision, showArchived) { books.workHours.clients(showArchived) }
    val hours = remember(model.revision) { books.workHours.hours() }
    val unbilled = remember(model.revision) { books.workHours.unbilled().map { it.id }.toSet() }
    val invoices = remember(model.revision) { books.invoices.list().associateBy { it.id } }
    val names = remember(model.revision) { books.members.list(includeArchived = true).associate { it.id to it.displayName } }
    var editingClient by remember { mutableStateOf<WorkClient?>(null) }
    var editingHours by remember { mutableStateOf<WorkEntry?>(null) }
    var billing by remember { mutableStateOf<WorkClient?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val access = rememberAccess(model)
    // New hours can go to the clients of the groups the user may add to.
    val open = clients.filter { access.mayAdd(it.groupId) && !it.archived }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (access.canCreate) Button(onClick = { editingClient = WorkClient("", model.trackerGroup(), "", books.reports.base) }) { Text(model.t("hours.addClient")) }
        if (open.isNotEmpty()) OutlinedButton(onClick = { editingHours = WorkEntry("", open.first().id, today(), 60) }) { Text(model.t("hours.add")) }
        LabeledCheckbox(model.t("utilities.showArchived"), showArchived) { showArchived = it }
    }
    Text(model.t("hours.hint"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    if (clients.isEmpty()) Text(model.t("hours.none"))
    Column(Modifier.verticalScroll(rememberScrollState())) {
        for (c in clients) {
            val mine = hours.filter { it.clientId == c.id }
            val unbilledHours = mine.filter { it.id in unbilled }
            val amount = unbilledHours.mapNotNull { books.workHours.amount(it, c) }.fold(Money.zero(c.currency)) { a, m -> a + m }
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.Medium)
                            Text(
                                listOfNotNull(c.rate?.let { model.t("hours.rate", model.money(it)) }, c.memberId?.let(names::get), c.tasks.filter { !it.archived }.joinToString(", ") { it.name }.ifEmpty { null })
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (unbilledHours.isNotEmpty()) OutlinedButton(onClick = { billing = c }, enabled = access.mayEdit(c.groupId)) { Text(model.t("hours.makeInvoice")) }
                        TextButton(onClick = { editingClient = c }, enabled = access.mayEdit(c.groupId)) { Text(model.t("hours.editClient")) }
                    }
                    Text(
                        if (unbilledHours.isEmpty()) model.t("hours.allBilled") else model.t("hours.unbilled", model.duration(unbilledHours.sumOf { it.minutes }), model.money(amount)),
                        fontWeight = FontWeight.Medium,
                    )
                    for (h in mine.reversed().take(HOURS_SHOWN)) {
                        Row(Modifier.fillMaxWidth().clickable(enabled = access.mayEdit(c.groupId)) { editingHours = h }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(model.date(h.date), Modifier.width(100.dp))
                            Text(
                                listOfNotNull(c.tasks.firstOrNull { it.id == h.taskId }?.name, h.description, h.startTime, model.t("tracker.fromPhone").takeIf { h.fromPhone }).joinToString(" · "),
                                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            )
                            Text(model.duration(h.minutes), Modifier.width(80.dp))
                            Text(books.workHours.amount(h, c)?.let(model::money) ?: "—", Modifier.width(110.dp))
                            Text(
                                if (h.id in unbilled) model.t("hours.notBilled") else model.t("hours.billedOn", invoices[h.invoiceId]?.number.orEmpty()),
                                Modifier.width(150.dp), style = MaterialTheme.typography.bodySmall,
                                color = if (h.id in unbilled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
    editingClient?.let { c -> WorkClientDialog(model, c) { editingClient = null } }
    editingHours?.let { h -> WorkHoursDialog(model, h, if (h.id.isBlank()) open else clients) { editingHours = null } }
    billing?.let { c -> WorkInvoiceDialog(model, c, hours.filter { it.clientId == c.id && it.id in unbilled }, onDone = { message = it }) { billing = null } }
}

private data class TaskRow(val id: String, val name: String, val rate: String, val archived: Boolean)

@Composable
private fun WorkClientDialog(model: BooksModel, c: WorkClient, onClose: () -> Unit) {
    val locale = model.language.locale
    val members = remember { model.books.members.list() }
    var name by remember { mutableStateOf(c.name) }
    var details by remember { mutableStateOf(c.details.orEmpty()) }
    var rate by remember { mutableStateOf(c.rate?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var member by remember { mutableStateOf(members.firstOrNull { it.id == c.memberId }) }
    var notes by remember { mutableStateOf(c.notes.orEmpty()) }
    var archived by remember { mutableStateOf(c.archived) }
    var groupId by remember { mutableStateOf(c.groupId) }
    val tasks = remember { mutableStateListOf<TaskRow>().apply { c.tasks.filter { !it.archived }.forEach { add(TaskRow(it.id, it.name, it.rate?.let { r -> MoneyFormat.formatAmount(r, locale) }.orEmpty(), false)) } } }
    var asking by remember { mutableStateOf(false) }
    FormDialog(model.t(if (c.id.isBlank()) "hours.addClient" else "hours.editClient"), model.t("common.save"), model.t("common.cancel"), canSave = name.isNotBlank(), onDismiss = onClose, onSave = {
        val ok = model.act {
            model.books.workHours.saveClient(
                c.copy(
                    groupId = groupId, name = name, details = details, rate = parseAmount(rate, c.currency, locale)?.abs(), memberId = member?.id, notes = notes, archived = archived,
                    tasks = tasks.filter { it.name.isNotBlank() }.map { WorkTask(it.id, it.name, parseAmount(it.rate, c.currency, locale)?.abs(), it.archived) },
                ),
            )
        }
        if (ok != null) onClose()
    }) {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("hours.client"), name) { name = it }
            TextInput(model.t("invoice.customerDetails"), details, singleLine = false) { details = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountInput(model.t("hours.hourlyRate"), rate, c.currency, locale, Modifier.weight(1f), model::money) { rate = it }
                Picker(model.t("hours.who"), listOf(null) + members, member, { it?.displayName ?: model.t("taxes.household") }, Modifier.weight(1f)) { member = it }
            }
            Text(model.t("hours.tasks"), style = MaterialTheme.typography.titleSmall)
            Text(model.t("hours.tasksHint"), style = MaterialTheme.typography.bodySmall)
            tasks.forEachIndexed { n, t ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextInput(model.t("hours.task"), t.name, Modifier.weight(1f)) { tasks[n] = t.copy(name = it) }
                    AmountInput(model.t("hours.taskRate"), t.rate, c.currency, locale, Modifier.width(150.dp), model::money) { tasks[n] = t.copy(rate = it) }
                    RemoveButton(model.t("common.remove")) { tasks.removeAt(n) }
                }
            }
            TextButton(onClick = { tasks.add(TaskRow("", "", "", false)) }) { Text(model.t("hours.addTask")) }
            TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
            if (c.id.isBlank()) StoreInPicker(model, groupId) { groupId = it }
            if (c.id.isNotBlank()) {
                LabeledCheckbox(model.t("utilities.archived"), archived) { archived = it }
                TextButton(onClick = { asking = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (asking) {
        AskBeforeDeleting(model, model.t("hours.deleteClient.body", c.name), onDismiss = { asking = false }) {
            (model.act { model.books.workHours.deleteClient(c) } != null).also { if (it) onClose() }
        }
    }
}

@Composable
private fun WorkHoursDialog(model: BooksModel, h: WorkEntry, clients: List<WorkClient>, onClose: () -> Unit) {
    val locale = model.language.locale
    var client by remember { mutableStateOf(clients.firstOrNull { it.id == h.clientId } ?: clients.first()) }
    val tasks = client.tasks.filter { !it.archived || it.id == h.taskId }
    var task by remember(client) { mutableStateOf(tasks.firstOrNull { it.id == h.taskId }) }
    var day by remember { mutableStateOf(h.date.toString()) }
    var start by remember { mutableStateOf(h.startTime.orEmpty()) }
    var time by remember { mutableStateOf(if (h.id.isBlank()) "" else ca.schippers.hfm.sync.HoursTimer.format(h.minutes)) }
    var description by remember { mutableStateOf(h.description.orEmpty()) }
    var rate by remember { mutableStateOf(h.rate?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var asking by remember { mutableStateOf(false) }
    FormDialog(model.t(if (h.id.isBlank()) "hours.add" else "hours.edit"), model.t("common.save"), model.t("common.cancel"), canSave = time.isNotBlank(), onDismiss = onClose, onSave = {
        val ok = model.act {
            model.books.workHours.save(
                h.copy(
                    clientId = client.id, taskId = task?.id, date = trackerDate(day), startTime = start.trim().ifEmpty { null }, minutes = parseDuration(time), description = description,
                    rate = parseAmount(rate, client.currency, locale)?.abs(),
                ),
            )
        }
        if (ok != null) onClose()
    }) {
        if (h.id.isBlank()) Picker(model.t("hours.client"), clients, client, { it.name }) { client = it } else Text(client.name, fontWeight = FontWeight.Medium)
        if (tasks.isNotEmpty()) Picker(model.t("hours.task"), listOf(null) + tasks, task, { it?.name ?: "—" }) { task = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t("report.date"), day, Modifier.width(170.dp)) { day = it }
            TextInput(model.t("hours.start"), start, Modifier.width(110.dp)) { start = it }
            TextInput(model.t("tracker.time"), time, Modifier.width(120.dp)) { time = it }
        }
        Text(model.t("tracker.timeHint"), style = MaterialTheme.typography.bodySmall)
        TextInput(model.t("share.description"), description) { description = it }
        AmountInput(model.t("hours.ownRate"), rate, client.currency, locale, Modifier.fillMaxWidth(), model::money) { rate = it }
        Text(model.t("hours.ownRateHint"), style = MaterialTheme.typography.bodySmall)
        if (h.invoiceId != null) Text(model.t("hours.billedHint"), style = MaterialTheme.typography.bodySmall)
        if (h.id.isNotBlank()) TextButton(onClick = { asking = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
    }
    if (asking) {
        AskBeforeDeleting(model, model.t("hours.delete.body", model.duration(h.minutes), model.date(h.date)), onDismiss = { asking = false }) {
            (model.act { model.books.workHours.delete(h) } != null).also { if (it) onClose() }
        }
    }
}

/** HRS-01, SAL-04: the client's unbilled hours, all chosen at first, made into a draft invoice in one step. */
@Composable
private fun WorkInvoiceDialog(model: BooksModel, c: WorkClient, open: List<WorkEntry>, onDone: (String) -> Unit, onClose: () -> Unit) {
    val chosen = remember { mutableStateListOf<String>().apply { addAll(open.map { it.id }) } }
    var day by remember { mutableStateOf(today().toString()) }
    val total = open.filter { it.id in chosen }.mapNotNull { model.books.workHours.amount(it, c) }.fold(Money.zero(c.currency)) { a, m -> a + m }
    FormDialog(model.t("hours.makeInvoice"), model.t("hours.makeInvoice"), model.t("common.cancel"), canSave = chosen.isNotEmpty(), onDismiss = onClose, onSave = {
        model.act { model.books.workHours.invoice(c.id, chosen.toList(), trackerDate(day)) }?.let { i ->
            onDone(model.t("hours.invoiceMade", i.number, model.money(i.total)))
            onClose()
        }
    }) {
        Text(c.name, fontWeight = FontWeight.Medium)
        Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
            for (h in open) {
                LabeledCheckbox(
                    listOfNotNull(model.date(h.date), c.tasks.firstOrNull { it.id == h.taskId }?.name, h.description, model.duration(h.minutes), model.books.workHours.amount(h, c)?.let(model::money) ?: model.t("hours.noRate"))
                        .joinToString(" · "),
                    h.id in chosen,
                ) { if (it) chosen.add(h.id) else chosen.remove(h.id) }
            }
        }
        DateInput(model.t("invoice.issued"), day) { day = it }
        Text(model.t("hours.invoiceTotal", model.money(total)), fontWeight = FontWeight.Medium)
        Text(model.t("hours.invoiceHint"), style = MaterialTheme.typography.bodySmall)
    }
}

private const val HOURS_SHOWN = 8
