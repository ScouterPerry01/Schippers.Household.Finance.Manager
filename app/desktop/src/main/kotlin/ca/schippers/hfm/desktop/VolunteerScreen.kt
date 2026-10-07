package ca.schippers.hfm.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Contact
import ca.schippers.hfm.books.ContactFilter
import ca.schippers.hfm.books.VolunteerEntry
import ca.schippers.hfm.books.VolunteerKind
import ca.schippers.hfm.books.VolunteerYear

/**
 * VOL-01: volunteer hours per person and organization, each person's yearly total, the hours of
 * volunteer firefighting and search and rescue against the 200 hours of the tax amounts, and
 * students' community hours.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VolunteerScreen(model: BooksModel) {
    val books = model.books
    val thisYear = today().year
    var year by remember { mutableStateOf(thisYear) }
    val entries = remember(model.revision, year) { books.volunteer.list().filter { it.date.year == year } }
    val years = remember(model.revision, year) { books.volunteer.years(year) }
    val names = remember(model.revision) { books.members.list(includeArchived = true).associate { it.id to it.displayName } }
    var editing by remember { mutableStateOf<VolunteerEntry?>(null) }
    val access = rememberAccess(model)
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(model.t("nav.volunteer"), style = MaterialTheme.typography.titleLarge)
        Text(model.t("volunteer.hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Picker(model.t("taxes.year"), (thisYear downTo thisYear - 6).toList(), year, { it.toString() }, Modifier.width(190.dp)) { year = it }
            val first = books.members.list().firstOrNull()
            if (first != null && access.canAdd) {
                // The same rule as hours from the phone: where the person's hours are, else the user's private group, else the shared one.
                val group = books.volunteer.defaultGroup(first.id) ?: model.trackerGroup()
                Button(onClick = { editing = VolunteerEntry("", group, first.id, "", VolunteerKind.OTHER, today(), 60) }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(model.t("volunteer.add"))
                }
            }
        }
        if (years.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (y in years) VolunteerCard(model, names[y.memberId].orEmpty(), y)
            }
        }
        if (entries.isEmpty()) Text(model.t("volunteer.none"))
        else HeadingRow {
            ColumnHeading(model.t("register.date"), Modifier.width(100.dp))
            ColumnHeading(model.t("volunteer.organization"), Modifier.weight(1f))
            ColumnHeading(model.t("column.duration"), Modifier.width(90.dp))
        }
        for (e in entries) {
            Row(Modifier.fillMaxWidth().clickable(enabled = access.mayEdit(e.groupId)) { editing = e }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(model.date(e.date), Modifier.width(100.dp))
                Column(Modifier.weight(1f)) {
                    Text("${names[e.memberId].orEmpty()} · ${e.organization}")
                    Text(
                        listOfNotNull(model.t("volunteerKind.${e.kind}"), e.activity, model.t("tracker.fromPhone").takeIf { e.fromPhone }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(model.duration(e.minutes), Modifier.width(90.dp))
            }
            HorizontalDivider()
        }
    }
    editing?.let { e -> VolunteerDialog(model, e) { editing = null } }
}

@Composable
private fun VolunteerCard(model: BooksModel, name: String, y: VolunteerYear) {
    Card {
        Column(Modifier.padding(12.dp).width(300.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, fontWeight = FontWeight.Medium)
            Text(model.t("volunteer.total", model.duration(y.minutes)))
            for ((org, minutes) in y.byOrganization) Text("   " + model.t("common.labelValue", org, model.duration(minutes)), style = MaterialTheme.typography.bodySmall)
            if (y.emergencyMinutes > 0) {
                Text(model.t("volunteer.emergency", model.duration(y.emergencyMinutes), y.thresholdHours), fontWeight = FontWeight.Medium)
                Text(
                    if (y.meetsThreshold) model.t("volunteer.meets", y.thresholdHours) else model.t("volunteer.toGo", model.duration(y.thresholdHours * 60 - y.emergencyMinutes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (y.meetsThreshold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (y.schoolMinutes > 0) Text(model.t("volunteer.school", model.duration(y.schoolMinutes)))
        }
    }
}

@Composable
private fun VolunteerDialog(model: BooksModel, e: VolunteerEntry, onClose: () -> Unit) {
    val books = model.books
    val members = remember { books.members.list() }
    val past = remember { books.volunteer.organizations() }
    // Organizations among the contacts, to link the hours to one (CON-04).
    val contacts = remember { runCatching { books.contacts.list(ContactFilter()) }.getOrDefault(emptyList()).filter { !it.person } }
    var member by remember { mutableStateOf(members.firstOrNull { it.id == e.memberId }) }
    var organization by remember { mutableStateOf(e.organization) }
    var contact by remember { mutableStateOf<Contact?>(contacts.firstOrNull { it.id == e.contactId }) }
    var kind by remember { mutableStateOf(e.kind) }
    var day by remember { mutableStateOf(e.date.toString()) }
    var time by remember { mutableStateOf(if (e.id.isBlank()) "" else ca.schippers.hfm.sync.HoursTimer.format(e.minutes)) }
    var activity by remember { mutableStateOf(e.activity.orEmpty()) }
    var notes by remember { mutableStateOf(e.notes.orEmpty()) }
    var groupId by remember { mutableStateOf(e.groupId) }
    var asking by remember { mutableStateOf(false) }
    FormDialog(model.t(if (e.id.isBlank()) "volunteer.add" else "volunteer.edit"), model.t("common.save"), model.t("common.cancel"), canSave = member != null && organization.isNotBlank() && time.isNotBlank(), onDismiss = onClose, onSave = {
        val ok = model.act {
            books.volunteer.save(
                e.copy(
                    groupId = groupId, memberId = member!!.id, organization = organization, contactId = contact?.id, kind = kind, date = trackerDate(day), minutes = parseDuration(time),
                    activity = activity, notes = notes,
                ),
            )
        }
        if (ok != null) onClose()
    }) {
        Picker(model.t("report.person"), members, member, { it.displayName }) { m ->
            member = m
            // New hours follow the person: where their hours are kept.
            if (e.id.isBlank()) books.volunteer.defaultGroup(m.id)?.let { groupId = it }
        }
        SuggestInput(model.t("volunteer.organization"), organization, past.map { it.organization }.distinct(), onChange = { organization = it }, onPick = { name ->
            organization = name
            past.firstOrNull { it.organization == name }?.let { p -> kind = p.kind; contact = contacts.firstOrNull { it.id == p.contactId } }
        })
        if (contacts.isNotEmpty()) {
            Picker(model.t("volunteer.contact"), listOf(null) + contacts, contact, { it?.name ?: model.t("volunteer.noContact") }) { c ->
                contact = c
                if (c != null && organization.isBlank()) organization = c.name
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker(model.t("volunteer.kind"), VolunteerKind.entries, kind, { model.t("volunteerKind.$it") }, Modifier.weight(1f)) { kind = it }
            DateInput(model.t("report.date"), day, Modifier.width(170.dp)) { day = it }
            TextInput(model.t("tracker.time"), time, Modifier.width(120.dp)) { time = it }
        }
        Text(model.t("tracker.timeHint"), style = MaterialTheme.typography.bodySmall)
        if (kind == VolunteerKind.FIREFIGHTER || kind == VolunteerKind.SEARCH_RESCUE) Text(model.t("volunteer.emergencyHint"), style = MaterialTheme.typography.bodySmall)
        TextInput(model.t("volunteer.activity"), activity) { activity = it }
        TextInput(model.t("calendar.notes"), notes) { notes = it }
        if (e.id.isBlank()) StoreInPicker(model, groupId) { groupId = it }
        if (e.id.isNotBlank()) TextButton(onClick = { asking = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
    }
    if (asking) {
        AskBeforeDeleting(model, model.t("volunteer.delete.body", e.organization, model.date(e.date)), onDismiss = { asking = false }) {
            (model.act { books.volunteer.delete(e) } != null).also { if (it) onClose() }
        }
    }
}
