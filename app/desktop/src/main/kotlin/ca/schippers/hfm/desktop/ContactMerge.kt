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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Contact
import ca.schippers.hfm.books.ContactFilter
import ca.schippers.hfm.books.MergeCandidate
import ca.schippers.hfm.books.MergeChoices
import ca.schippers.hfm.books.MergeField
import ca.schippers.hfm.books.SearchService

/**
 * "Merge with…" on a contact's page: pick the other contact (likely duplicates first), then choose,
 * field by field, which value to keep; phones, emails, numbers, kinds, people served, links and
 * the people of an organization are combined. The other contact is deleted once the user confirms.
 */
@Composable
internal fun MergeContactDialog(model: BooksModel, contact: Contact, onClose: () -> Unit) {
    val books = model.books
    var otherId by remember { mutableStateOf<String?>(null) }
    var choices by remember { mutableStateOf(MergeChoices()) }
    var confirming by remember { mutableStateOf(false) }
    val candidates = remember(model.revision) { runCatching { books.contacts.mergeCandidates(contact.id) }.getOrDefault(emptyList()) }
    val other = otherId?.let { id -> candidates.firstOrNull { it.contact.id == id }?.contact }

    WideDialog(model.t("contacts.mergeTitle", contact.name), model.t("common.cancel"), onClose) {
        Column(Modifier.width(820.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (other == null) {
                PickOther(model, contact, candidates) { c -> otherId = c.id; choices = MergeChoices.suggested(contact, c) }
            } else {
                MergeChoicesView(model, contact, other, choices, onChange = { choices = it }, onBack = { otherId = null }, onMerge = { confirming = true })
            }
        }
    }

    if (confirming && other != null) {
        val keptName = if (MergeField.NAME in choices.fromOther) other.name else contact.name
        FormDialog(
            model.t("contacts.mergeDo"), model.t("contacts.mergeDo"), model.t("common.cancel"),
            onDismiss = { confirming = false },
            onSave = {
                confirming = false
                if (model.act { books.contacts.merge(contact.id, other.id, choices) } != null) onClose()
            },
        ) { Text(model.t("contacts.mergeQuestion", other.name, keptName)) }
    }
}

/** The first step: the other contacts the user may change, with a search; likely duplicates first. */
@Composable
private fun PickOther(model: BooksModel, contact: Contact, candidates: List<MergeCandidate>, onPick: (Contact) -> Unit) {
    var text by remember { mutableStateOf("") }
    Text(model.t("contacts.mergePick", contact.name), style = MaterialTheme.typography.bodyMedium)
    TextInput(model.t("contacts.search"), text) { text = it }
    val needle = SearchService.fold(text.trim())
    val shown = candidates.filter { m ->
        needle.isEmpty() || (listOfNotNull(m.contact.name, m.contact.purpose) + m.contact.phones.map { it.value } + m.contact.emails.map { it.value }).any { SearchService.fold(it).contains(needle) }
    }
    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
        if (candidates.isEmpty()) Text(model.t("contacts.mergeNone"))
        else if (shown.isEmpty()) Text(model.t("contacts.noMatch"))
        for ((likely, group) in shown.groupBy { it.likelySame }.toSortedMap(compareBy { !it })) {
            Text(
                model.t(if (likely) "contacts.mergeLikely" else "contacts.mergeOthers"), style = MaterialTheme.typography.labelLarge,
                color = if (likely) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp),
            )
            for (m in group) {
                val c = m.contact
                Column(Modifier.fillMaxWidth().clickable { onPick(c) }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(c.name + if (c.archived) " (${model.t("contacts.archived")})" else "", fontWeight = FontWeight.Medium)
                    val line = listOfNotNull(
                        c.purpose,
                        c.kinds.joinToString(", ") { model.t("contactKind.$it") }.ifEmpty { null },
                        c.phones.firstOrNull()?.value,
                        groupLabel(model, c.groupId),
                    ).joinToString(" · ")
                    if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The second step: each field the two contacts hold differently, side by side, and what is combined. */
@Composable
private fun MergeChoicesView(
    model: BooksModel,
    keep: Contact,
    other: Contact,
    choices: MergeChoices,
    onChange: (MergeChoices) -> Unit,
    onBack: () -> Unit,
    onMerge: () -> Unit,
) {
    val books = model.books
    val result = remember(model.revision, keep.id, other.id, choices) { runCatching { books.contacts.mergePreview(keep.id, other.id, choices) } }
    val preview = result.getOrNull()
    val names = remember(model.revision) { books.contacts.list(ContactFilter(includeArchived = true)).associate { it.id to it.name } }
    val people = remember(model.revision) { model.peopleAndPets(includeArchived = true) }
    fun value(c: Contact, f: MergeField): String? = when (f) {
        MergeField.PERSON -> model.t(if (c.person) "contacts.person" else "contacts.organization")
        MergeField.WORKS_AT -> listOfNotNull(c.jobTitle, c.organizationId?.let { names[it] }).joinToString(" · ").ifEmpty { null }
        MergeField.GROUP -> groupLabel(model, c.groupId)
        else -> MergeChoices.text(c, f)
    }
    fun label(f: MergeField): String = when (f) {
        MergeField.NAME -> model.t("contacts.name")
        MergeField.PERSON -> model.t("contacts.mergeKindOf")
        MergeField.WORKS_AT -> model.t("contacts.worksAt")
        MergeField.WHAT_FOR -> model.t("contacts.whatFor")
        MergeField.ADDRESS -> model.t("contacts.address")
        MergeField.WEBSITE -> model.t("contacts.website")
        MergeField.HOURS -> model.t("contacts.hours")
        MergeField.NOTES -> model.t("calendar.notes")
        MergeField.GROUP -> model.t("calendar.storeIn")
    }

    Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(model.t("contacts.mergeChoose"), style = MaterialTheme.typography.bodyMedium)
        HeadingRow(spacing = 8.dp) {
            ColumnHeading(model.t("column.field"), Modifier.width(150.dp))
            ColumnHeading(keep.name, Modifier.weight(1f))
            ColumnHeading(other.name, Modifier.weight(1f))
        }
        // Only the fields the two contacts hold differently need a choice.
        for (f in MergeField.entries.filter { value(keep, it) != value(other, it) || (it == MergeField.GROUP && keep.groupId != other.groupId) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label(f), Modifier.width(150.dp), style = MaterialTheme.typography.labelLarge)
                for ((fromOther, c) in listOf(false to keep, true to other)) {
                    FilterChip(
                        (f in choices.fromOther) == fromOther,
                        { onChange(MergeChoices(if (fromOther) choices.fromOther + f else choices.fromOther - f)) },
                        { Text(value(c, f) ?: model.t("contacts.mergeEmpty"), maxLines = 3, overflow = TextOverflow.Ellipsis) },
                        Modifier.weight(1f),
                    )
                }
            }
        }
        result.exceptionOrNull()?.let { ErrorText(model.describe(it)) }
        preview?.let { p ->
            HorizontalDivider()
            Text(model.t("contacts.mergeCombined"), style = MaterialTheme.typography.titleSmall)
            val m = p.merged
            if (m.kinds.isNotEmpty()) Text(model.t("contacts.labelled", model.t("contacts.kinds")) + " " + m.kinds.joinToString(", ") { model.t("contactKind.$it") })
            Text(
                if (m.memberIds.isEmpty()) model.t("contacts.forHousehold")
                else model.t("contacts.forPeople", m.memberIds.mapNotNull { id -> people.firstOrNull { it.id == id }?.name }.joinToString(", ")),
            )
            for (d in m.details) Text(model.t("contacts.labelled", d.label ?: model.t("detailType.${d.type}")) + " " + d.value)
            if (p.links.isNotEmpty()) {
                Text(model.t("contacts.links"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                for (l in p.links) Text(model.t("linkRole.${l.link.role}") + " · " + model.t("linkTarget.${l.link.target}") + " · " + l.name, style = MaterialTheme.typography.bodySmall)
            }
            if (p.people.isNotEmpty()) Text(model.t("contacts.mergePeople", m.name, p.people.joinToString(", ") { it.name }))
            if (p.exposesPrivate) Text(model.t("contacts.mergePrivateWarning", groupLabel(model, m.groupId).orEmpty()), color = MaterialTheme.colorScheme.error)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        TextButton(onClick = onBack) { Text(model.t("contacts.mergeBack")) }
        Button(enabled = preview != null, onClick = onMerge) { Text(model.t("contacts.mergeDo")) }
    }
}

/** "Marie - privé (private)": the name of the group a contact is kept in. */
private fun groupLabel(model: BooksModel, groupId: String): String? =
    model.books.groups().firstOrNull { it.id == groupId }?.let { if (it.isPrivate) model.t("group.privateLabel", it.name) else it.name }
