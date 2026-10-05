package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
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
import ca.schippers.hfm.books.Allergy
import ca.schippers.hfm.books.CalendarEvent
import ca.schippers.hfm.books.ConditionStatus
import ca.schippers.hfm.books.EventCategory
import ca.schippers.hfm.books.EventDraft
import ca.schippers.hfm.books.HealthCondition
import ca.schippers.hfm.books.HealthProvider
import ca.schippers.hfm.books.HealthRecord
import ca.schippers.hfm.books.HealthTest
import ca.schippers.hfm.books.Immunization
import ca.schippers.hfm.books.Medication
import ca.schippers.hfm.books.ProviderKind
import ca.schippers.hfm.books.Severity
import ca.schippers.hfm.books.ValidationException
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private enum class HealthTab { MEDICATIONS, APPOINTMENTS, CONDITIONS, ALLERGIES, TESTS, IMMUNIZATIONS, PROVIDERS }

/** What the Health screen is editing; an empty id means a new record. */
private sealed interface HealthEdit {
    data class Med(val value: Medication) : HealthEdit
    data class Refill(val value: Medication) : HealthEdit
    data class Record(val value: HealthRecord) : HealthEdit
    data class Provider(val value: HealthProvider) : HealthEdit
    data class NewEvent(val value: EventDraft) : HealthEdit
    data class Event(val value: CalendarEvent) : HealthEdit
}

/**
 * HLT-01 to HLT-07: each person's medications and refills, appointments, conditions, allergies,
 * tests and immunizations, and the household's providers. An organizational aid, not medical advice.
 */
@Composable
fun HealthScreen(model: BooksModel) {
    val books = model.books
    val people = remember(model.revision) { model.peopleAndPets() }
    var personId by remember { mutableStateOf(model.healthSubjectId) }
    var tab by remember { mutableStateOf(HealthTab.MEDICATIONS) }
    var edit by remember { mutableStateOf<HealthEdit?>(null) }
    var summaryFor by remember { mutableStateOf<String?>(null) }
    val person = people.firstOrNull { it.id == personId } ?: people.firstOrNull()
    summaryFor?.let { id ->
        val name = people.firstOrNull { it.id == id }?.name.orEmpty()
        ExportPdfDialog(model, model.t("health.summaryTitle", name), healthSections(model, id)) { summaryFor = null }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(model.t("nav.health"), style = MaterialTheme.typography.titleLarge)
            if (person != null) Picker(model.t("health.person"), people, person, { it.name }, Modifier.width(260.dp)) { personId = it.id }
            Box(Modifier.weight(1f))
            // HLT-09: a printable summary for appointments and emergencies.
            if (person != null && !person.isPet) {
                OutlinedButton(onClick = { summaryFor = person.id }) { Text(model.t("health.summary")) }
            }
            if (person != null && tab != HealthTab.PROVIDERS) {
                Button(onClick = { edit = newRecord(model, tab, person) }) { Text(model.t("health.add.${tab.name}")) }
            }
            if (tab == HealthTab.PROVIDERS) {
                Button(onClick = { edit = newProvider(model) }) { Text(model.t("health.add.PROVIDERS")) }
            }
        }
        Text(model.t("health.disclaimer"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 0.dp, modifier = Modifier.padding(vertical = 8.dp)) {
            for (t in HealthTab.entries) {
                Tab(selected = tab == t, onClick = { tab = t }, text = { Text(model.t("health.tab.${t.name}")) })
            }
        }
        Box(Modifier.weight(1f)) {
            if (person == null && tab != HealthTab.PROVIDERS) {
                Column {
                    Text(model.t("health.noPeople"), Modifier.padding(8.dp))
                    OutlinedButton(onClick = { model.section = Section.MEMBERS }) { Text(model.t("nav.members")) }
                }
            } else {
                when (tab) {
                    HealthTab.MEDICATIONS -> MedicationsTab(model, person!!) { edit = it }
                    HealthTab.APPOINTMENTS -> AgendaList(
                        model, today().minus(DatePeriod(days = 365)), today().plus(DatePeriod(days = 365)), memberId = person!!.id,
                    ) { edit = HealthEdit.Event(it) }
                    HealthTab.CONDITIONS -> RecordsTab(model, remember(model.revision, person) { books.health.conditions(person!!.id) }, "health.noConditions", { edit = it }) { c ->
                        c.name + " · " + model.t("conditionStatus.${c.status}") to listOfNotNull(c.diagnosed?.let { model.t("health.diagnosed", model.date(it)) }, c.notes).joinToString(" · ")
                    }
                    HealthTab.ALLERGIES -> RecordsTab(model, remember(model.revision, person) { books.health.allergies(person!!.id) }, "health.noAllergies", { edit = it }) { a ->
                        a.substance + (a.severity?.let { " · " + model.t("severity.$it") }.orEmpty()) to listOfNotNull(a.reaction, a.notes).joinToString(" · ")
                    }
                    HealthTab.TESTS -> RecordsTab(model, remember(model.revision, person) { books.health.tests(person!!.id).sortedByDescending { it.date } }, "health.noTests", { edit = it }) { t ->
                        "${model.date(t.date)} · ${t.name}" + (t.result?.let { " · $it ${t.units.orEmpty()}".trimEnd() }.orEmpty()) to listOfNotNull(
                            t.referenceRange?.let { model.t("health.range", it) },
                            t.followUp?.let { model.t("health.followUpOn", model.date(it)) },
                            t.notes,
                        ).joinToString(" · ")
                    }
                    HealthTab.IMMUNIZATIONS -> RecordsTab(model, remember(model.revision, person) { books.health.immunizations(person!!.id).sortedByDescending { it.date } }, "health.noImmunizations", { edit = it }) { i ->
                        "${model.date(i.date)} · ${i.vaccine}" to listOfNotNull(i.nextDue?.let { model.t("health.nextDue", model.date(it)) }, i.notes).joinToString(" · ")
                    }
                    HealthTab.PROVIDERS -> ProvidersTab(model) { edit = HealthEdit.Provider(it) }
                }
            }
        }
    }

    when (val e = edit) {
        is HealthEdit.Med -> MedicationDialog(model, e.value) { edit = null }
        is HealthEdit.Refill -> RefillDialog(model, e.value) { edit = null }
        is HealthEdit.Record -> RecordDialog(model, e.value) { edit = null }
        is HealthEdit.Provider -> ProviderDialog(model, e.value) { edit = null }
        is HealthEdit.NewEvent -> EventDialog(model, null, e.value) { edit = null }
        is HealthEdit.Event -> EventDialog(model, e.value, null) { edit = null }
        null -> Unit
    }
}

private fun newRecord(model: BooksModel, tab: HealthTab, person: BooksModel.Who): HealthEdit? {
    val category = if (person.isPet) EventCategory.PET else EventCategory.MEDICAL
    if (tab == HealthTab.APPOINTMENTS) return model.newEventDraft(today(), category, person.id)?.let { HealthEdit.NewEvent(it) }
    // Pets belong to the whole household; people's records default to their private group.
    val group = if (person.isPet) model.editableGroups().let { g -> g.firstOrNull { !it.isPrivate } ?: g.firstOrNull() } else model.defaultGroupForPersonalRecords()
    if (group == null) {
        model.error = model.t("error.noEditableGroup")
        return null
    }
    val g = group.id
    val p = person.id
    return when (tab) {
        HealthTab.MEDICATIONS -> HealthEdit.Med(Medication("", g, p, "", null, null, null, null, null, today(), null, 30, null, today(), 5, true, null))
        HealthTab.CONDITIONS -> HealthEdit.Record(HealthCondition("", g, p, "", null, ConditionStatus.ACTIVE, null, null))
        HealthTab.ALLERGIES -> HealthEdit.Record(Allergy("", g, p, "", null, null, null))
        HealthTab.TESTS -> HealthEdit.Record(HealthTest("", g, p, "", today(), null, null, null, null, null, null))
        HealthTab.IMMUNIZATIONS -> HealthEdit.Record(Immunization("", g, p, "", today(), null, null, null))
        HealthTab.APPOINTMENTS, HealthTab.PROVIDERS -> null
    }
}

private fun newProvider(model: BooksModel): HealthEdit? {
    val group = model.editableGroups().firstOrNull { !it.isPrivate } ?: model.editableGroups().firstOrNull()
    if (group == null) {
        model.error = model.t("error.noEditableGroup")
        return null
    }
    return HealthEdit.Provider(HealthProvider("", group.id, "", ProviderKind.DOCTOR, null, null, null, false))
}

// --- Medications (HLT-01 to HLT-03) ---------------------------------------------------------------

@Composable
private fun MedicationsTab(model: BooksModel, person: BooksModel.Who, onEdit: (HealthEdit) -> Unit) {
    val books = model.books
    val meds = remember(model.revision, person.id) { books.health.medications(person.id).sortedWith(compareBy({ !it.active }, { it.name.lowercase() })) }
    val providers = remember(model.revision) { books.health.providers().associate { it.id to it.name } }
    LazyColumn {
        if (meds.isEmpty()) item { Text(model.t("health.noMedications"), Modifier.padding(8.dp)) }
        items(meds, key = { it.id }) { m ->
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            listOfNotNull(m.name, m.dose).joinToString(" ") + if (!m.active) " (${model.t("health.stopped")})" else "",
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                m.instructions,
                                m.pharmacyId?.let { providers[it] },
                                m.rxNumber?.let { model.t("health.rx", it) },
                                m.refillsRemaining?.let { model.t("health.refillsLeft", it) },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (m.active) RefillStatus(model, m)
                    Row(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (m.active) OutlinedButton(onClick = { onEdit(HealthEdit.Refill(m)) }) { Text(model.t("health.recordRefill")) }
                        TextButton(onClick = { onEdit(HealthEdit.Med(m)) }) { Text(model.t("common.edit")) }
                    }
                }
            }
        }
    }
}

/** HLT-02, HLT-03: next refill date, highlighted when it is near, and a renewal warning. */
@Composable
private fun RefillStatus(model: BooksModel, m: Medication) {
    val next = m.nextRefill
    Column(Modifier.width(220.dp)) {
        if (next != null) {
            val days = today().daysUntil(next)
            Text(
                model.t("health.nextRefill", model.date(next)),
                color = when {
                    days < 0 -> MaterialTheme.colorScheme.error
                    days <= m.refillReminderDays -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (days <= m.refillReminderDays) FontWeight.Bold else FontWeight.Normal,
            )
        } else {
            Text(model.t("health.noRefillDate"), style = MaterialTheme.typography.bodySmall)
        }
        if (m.needsRenewal) Text(model.t("health.renewal"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MedicationDialog(model: BooksModel, existing: Medication, onClose: () -> Unit) {
    val books = model.books
    val providers = remember { books.health.providers().filter { !it.archived } }
    val pharmacies = providers.filter { it.kind == ProviderKind.PHARMACY }
    val prescribers = providers.filter { it.kind != ProviderKind.PHARMACY && it.kind != ProviderKind.LAB }
    val fills = remember(existing.id) { if (existing.id.isBlank()) emptyList() else books.health.fills(existing.id) }
    var groupId by remember { mutableStateOf(existing.groupId) }
    var name by remember { mutableStateOf(existing.name) }
    var dose by remember { mutableStateOf(existing.dose.orEmpty()) }
    var instructions by remember { mutableStateOf(existing.instructions.orEmpty()) }
    var prescriberId by remember { mutableStateOf(existing.prescriberId) }
    var pharmacyId by remember { mutableStateOf(existing.pharmacyId) }
    var rx by remember { mutableStateOf(existing.rxNumber.orEmpty()) }
    var start by remember { mutableStateOf(existing.startDate?.toString().orEmpty()) }
    var end by remember { mutableStateOf(existing.endDate?.toString().orEmpty()) }
    var supply by remember { mutableStateOf(existing.daysSupply?.toString().orEmpty()) }
    var refills by remember { mutableStateOf(existing.refillsRemaining?.toString().orEmpty()) }
    var lastFill by remember { mutableStateOf(existing.lastFillDate?.toString().orEmpty()) }
    var remindDays by remember { mutableStateOf(existing.refillReminderDays.toString()) }
    var active by remember { mutableStateOf(existing.active) }
    var notes by remember { mutableStateOf(existing.notes.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }

    FormDialog(
        model.t(if (existing.id.isBlank()) "health.add.MEDICATIONS" else "health.editMedication"), model.t("common.save"), model.t("common.cancel"),
        canSave = name.isNotBlank(), onDismiss = onClose,
        onSave = {
            val ok = model.act {
                books.health.saveMedication(
                    existing.copy(
                        groupId = groupId, name = name, dose = dose.ifBlank { null }, instructions = instructions.ifBlank { null },
                        prescriberId = prescriberId, pharmacyId = pharmacyId, rxNumber = rx.ifBlank { null },
                        startDate = optionalDate(start), endDate = optionalDate(end), daysSupply = optionalInt(supply), refillsRemaining = optionalInt(refills),
                        lastFillDate = optionalDate(lastFill), refillReminderDays = optionalInt(remindDays) ?: 5, active = active, notes = notes.ifBlank { null },
                    ),
                )
            }
            if (ok != null) onClose()
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("health.medication"), name, Modifier.weight(2f)) { name = it }
                TextInput(model.t("health.dose"), dose, Modifier.weight(1f)) { dose = it }
            }
            TextInput(model.t("health.instructions"), instructions) { instructions = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("health.prescriber"), listOf(null) + prescribers, prescribers.firstOrNull { it.id == prescriberId }, { it?.name ?: model.t("common.none") }, Modifier.weight(1f)) { prescriberId = it?.id }
                Picker(model.t("health.pharmacy"), listOf(null) + pharmacies, pharmacies.firstOrNull { it.id == pharmacyId }, { it?.name ?: model.t("common.none") }, Modifier.weight(1f)) { pharmacyId = it?.id }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("health.rxNumber"), rx, Modifier.weight(1f)) { rx = it }
                DateInput(model.t("health.started"), start, Modifier.weight(1f)) { start = it }
                DateInput(model.t("health.ended"), end, Modifier.weight(1f)) { end = it }
            }
            Text(model.t("health.refillSection"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(model.t("health.lastFill"), lastFill, Modifier.weight(1f)) { lastFill = it }
                TextInput(model.t("health.daysSupply"), supply, Modifier.weight(1f)) { supply = it }
                TextInput(model.t("health.refillsRemaining"), refills, Modifier.weight(1f)) { refills = it }
            }
            TextInput(model.t("health.remindDays"), remindDays, supporting = model.t("health.remindDays.hint")) { remindDays = it }
            TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
            GroupPicker(model, groupId, enabled = existing.id.isBlank()) { groupId = it.id }
            if (existing.id.isBlank()) PrivateGroupHint(model) { groupId = it }
            if (fills.isNotEmpty()) {
                Text(model.t("health.fills"), style = MaterialTheme.typography.labelLarge)
                for (f in fills.take(6)) {
                    Text(
                        listOfNotNull(model.date(f.date), f.daysSupply?.let { model.t("health.daysCount", it) }, f.quantity, f.notes).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (existing.id.isNotBlank()) {
                LabeledCheckbox(model.t("health.active"), active) { active = it }
                TextButton(onClick = { confirmDelete = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmDelete) {
        FormDialog(model.t("health.delete.title"), model.t("common.delete"), model.t("common.cancel"), onDismiss = { confirmDelete = false }, onSave = {
            if (model.act { books.health.deleteMedication(existing.id) } != null) {
                confirmDelete = false
                onClose()
            }
        }) { Text(model.t("health.delete.body", existing.name)) }
    }
}

/** HLT-02: a refill picked up; uses one refill and moves the next refill date. */
@Composable
private fun RefillDialog(model: BooksModel, m: Medication, onClose: () -> Unit) {
    var date by remember { mutableStateOf(today().toString()) }
    var supply by remember { mutableStateOf(m.daysSupply?.toString().orEmpty()) }
    var quantity by remember { mutableStateOf("") }
    FormDialog(model.t("health.recordRefill") + " · " + m.name, model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act { model.books.health.recordFill(m.id, requiredDate(date), optionalInt(supply), quantity.ifBlank { null }) }
        if (ok != null) onClose()
    }) {
        DateInput(model.t("health.fillDate"), date, Modifier.fillMaxWidth()) { date = it }
        TextInput(model.t("health.daysSupply"), supply) { supply = it }
        TextInput(model.t("health.quantity"), quantity) { quantity = it }
        m.refillsRemaining?.let { Text(model.t("health.refillsAfter", (it - 1).coerceAtLeast(0)), style = MaterialTheme.typography.bodySmall) }
    }
}

// --- Conditions, allergies, tests, immunizations (HLT-04, HLT-05) --------------------------------

@Composable
private fun <T : HealthRecord> RecordsTab(model: BooksModel, records: List<T>, emptyKey: String, onEdit: (HealthEdit) -> Unit, describe: (T) -> Pair<String, String>) {
    LazyColumn {
        if (records.isEmpty()) item { Text(model.t(emptyKey), Modifier.padding(8.dp)) }
        items(records, key = { it.id }) { r ->
            val (title, detail) = describe(r)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { onEdit(HealthEdit.Record(r)) }) { Text(model.t("common.edit")) }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun RecordDialog(model: BooksModel, record: HealthRecord, onClose: () -> Unit) {
    val books = model.books
    val providers = remember { books.health.providers().filter { !it.archived } }
    val isNew = record.id.isBlank()
    var groupId by remember { mutableStateOf(record.groupId) }
    // Shared fields; each record type uses the ones it has.
    var name by remember {
        mutableStateOf(
            when (record) {
                is HealthCondition -> record.name
                is Allergy -> record.substance
                is HealthTest -> record.name
                is Immunization -> record.vaccine
            },
        )
    }
    var date by remember {
        mutableStateOf(
            when (record) {
                is HealthCondition -> record.diagnosed?.toString().orEmpty()
                is HealthTest -> record.date.toString()
                is Immunization -> record.date.toString()
                is Allergy -> ""
            },
        )
    }
    var next by remember {
        mutableStateOf(
            when (record) {
                is HealthTest -> record.followUp?.toString().orEmpty()
                is Immunization -> record.nextDue?.toString().orEmpty()
                else -> ""
            },
        )
    }
    var providerId by remember {
        mutableStateOf(
            when (record) {
                is HealthCondition -> record.providerId
                is HealthTest -> record.providerId
                is Immunization -> record.providerId
                is Allergy -> null
            },
        )
    }
    var status by remember { mutableStateOf((record as? HealthCondition)?.status ?: ConditionStatus.ACTIVE) }
    var reaction by remember { mutableStateOf((record as? Allergy)?.reaction.orEmpty()) }
    var severity by remember { mutableStateOf((record as? Allergy)?.severity) }
    var result by remember { mutableStateOf((record as? HealthTest)?.result.orEmpty()) }
    var units by remember { mutableStateOf((record as? HealthTest)?.units.orEmpty()) }
    var range by remember { mutableStateOf((record as? HealthTest)?.referenceRange.orEmpty()) }
    var notes by remember {
        mutableStateOf(
            when (record) {
                is HealthCondition -> record.notes
                is Allergy -> record.notes
                is HealthTest -> record.notes
                is Immunization -> record.notes
            }.orEmpty(),
        )
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val kind = record.javaClass.simpleName

    FormDialog(
        model.t(if (isNew) "health.new.$kind" else "health.edit.$kind"), model.t("common.save"), model.t("common.cancel"),
        canSave = name.isNotBlank(), onDismiss = onClose,
        onSave = {
            val ok = model.act {
                val n = notes.ifBlank { null }
                when (record) {
                    is HealthCondition -> books.health.saveCondition(record.copy(groupId = groupId, name = name, diagnosed = optionalDate(date), status = status, providerId = providerId, notes = n))
                    is Allergy -> books.health.saveAllergy(record.copy(groupId = groupId, substance = name, reaction = reaction.ifBlank { null }, severity = severity, notes = n))
                    is HealthTest -> books.health.saveTest(
                        record.copy(
                            groupId = groupId, name = name, date = requiredDate(date), result = result.ifBlank { null }, units = units.ifBlank { null },
                            referenceRange = range.ifBlank { null }, providerId = providerId, followUp = optionalDate(next), notes = n,
                        ),
                    )
                    is Immunization -> books.health.saveImmunization(record.copy(groupId = groupId, vaccine = name, date = requiredDate(date), providerId = providerId, nextDue = optionalDate(next), notes = n))
                }
            }
            if (ok != null) onClose()
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextInput(model.t("health.field.$kind"), name) { name = it }
            when (record) {
                is HealthCondition -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateInput(model.t("health.diagnosedDate"), date, Modifier.weight(1f)) { date = it }
                    Picker(model.t("health.status"), ConditionStatus.entries, status, { model.t("conditionStatus.$it") }, Modifier.weight(1f)) { status = it }
                }
                is Allergy -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput(model.t("health.reaction"), reaction, Modifier.weight(2f)) { reaction = it }
                    Picker(model.t("health.severity"), listOf(null) + Severity.entries, severity, { it?.let { s -> model.t("severity.$s") } ?: model.t("common.none") }, Modifier.weight(1f)) { severity = it }
                }
                is HealthTest -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateInput(model.t("calendar.date"), date, Modifier.weight(1f)) { date = it }
                        TextInput(model.t("health.result"), result, Modifier.weight(1f)) { result = it }
                        TextInput(model.t("health.units"), units, Modifier.weight(0.7f)) { units = it }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextInput(model.t("health.referenceRange"), range, Modifier.weight(1f)) { range = it }
                        DateInput(model.t("health.followUp"), next, Modifier.weight(1f)) { next = it }
                    }
                }
                is Immunization -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateInput(model.t("calendar.date"), date, Modifier.weight(1f)) { date = it }
                    DateInput(model.t("health.nextDueDate"), next, Modifier.weight(1f)) { next = it }
                }
            }
            if (record !is Allergy) {
                Picker(model.t("calendar.provider"), listOf(null) + providers, providers.firstOrNull { it.id == providerId }, { it?.name ?: model.t("common.none") }) { providerId = it?.id }
            }
            TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
            GroupPicker(model, groupId, enabled = isNew) { groupId = it.id }
            if (isNew) PrivateGroupHint(model) { groupId = it }
            if (!isNew) TextButton(onClick = { confirmDelete = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmDelete) {
        FormDialog(model.t("health.delete.title"), model.t("common.delete"), model.t("common.cancel"), onDismiss = { confirmDelete = false }, onSave = {
            if (model.act { books.health.delete(record) } != null) {
                confirmDelete = false
                onClose()
            }
        }) { Text(model.t("health.delete.body", name)) }
    }
}

// --- Providers (HLT-07) ---------------------------------------------------------------------------

@Composable
private fun ProvidersTab(model: BooksModel, onEdit: (HealthProvider) -> Unit) {
    val providers = remember(model.revision) { model.books.health.providers().sortedWith(compareBy({ it.archived }, { it.name.lowercase() })) }
    LazyColumn {
        if (providers.isEmpty()) item { Text(model.t("health.noProviders"), Modifier.padding(8.dp)) }
        items(providers, key = { it.id }) { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name + if (p.archived) " (${model.t("health.archived")})" else "", fontWeight = FontWeight.Medium)
                    Text(listOfNotNull(model.t("providerKind.${p.kind}"), p.phone, p.address).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { onEdit(p) }) { Text(model.t("common.edit")) }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ProviderDialog(model: BooksModel, existing: HealthProvider, onClose: () -> Unit) {
    var groupId by remember { mutableStateOf(existing.groupId) }
    var name by remember { mutableStateOf(existing.name) }
    var kind by remember { mutableStateOf(existing.kind) }
    var phone by remember { mutableStateOf(existing.phone.orEmpty()) }
    var address by remember { mutableStateOf(existing.address.orEmpty()) }
    var notes by remember { mutableStateOf(existing.notes.orEmpty()) }
    var archived by remember { mutableStateOf(existing.archived) }
    FormDialog(
        model.t(if (existing.id.isBlank()) "health.add.PROVIDERS" else "health.editProvider"), model.t("common.save"), model.t("common.cancel"),
        canSave = name.isNotBlank(), onDismiss = onClose,
        onSave = {
            val ok = model.act {
                model.books.health.saveProvider(existing.copy(groupId = groupId, name = name, kind = kind, phone = phone, address = address, notes = notes, archived = archived))
            }
            if (ok != null) onClose()
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("health.providerName"), name, Modifier.weight(2f)) { name = it }
            Picker(model.t("health.providerKind"), ProviderKind.entries, kind, { model.t("providerKind.$it") }, Modifier.weight(1f)) { kind = it }
        }
        TextInput(model.t("institution.phone"), phone) { phone = it }
        TextInput(model.t("health.address"), address) { address = it }
        TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
        GroupPicker(model, groupId, enabled = existing.id.isBlank()) { groupId = it.id }
        if (existing.id.isNotBlank()) LabeledCheckbox(model.t("health.archived"), archived) { archived = it }
    }
}

// --- Helpers --------------------------------------------------------------------------------------

private fun optionalDate(text: String): LocalDate? =
    text.trim().ifEmpty { null }?.let { runCatching { LocalDate.parse(it) }.getOrElse { throw ValidationException("error.invalidDate") } }

private fun requiredDate(text: String): LocalDate = optionalDate(text) ?: throw ValidationException("error.invalidDate")

private fun optionalInt(text: String): Int? = text.trim().ifEmpty { null }?.let { it.toIntOrNull() ?: throw ValidationException("error.invalidNumber") }
