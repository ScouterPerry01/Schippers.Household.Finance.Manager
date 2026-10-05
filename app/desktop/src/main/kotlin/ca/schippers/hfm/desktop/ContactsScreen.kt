package ca.schippers.hfm.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Contact
import ca.schippers.hfm.books.ContactDetail
import ca.schippers.hfm.books.ContactFilter
import ca.schippers.hfm.books.ContactKind
import ca.schippers.hfm.books.DetailType
import ca.schippers.hfm.books.GatherDecision
import ca.schippers.hfm.books.GatherProposal
import ca.schippers.hfm.books.LinkRole
import ca.schippers.hfm.books.LinkTarget
import ca.schippers.hfm.data.AccessDeniedException

/**
 * CON-01 to CON-06: the household's contacts, standing on their own under the dashboard. A list
 * with filters (kind, person served, kind of linked record, search) beside the chosen contact's
 * page: what it is for, whom it serves, how to reach it, and every record it is linked to.
 */
@Composable
fun ContactsScreen(model: BooksModel) {
    val books = model.books
    var text by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<ContactKind?>(null) }
    var memberId by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<LinkTarget?>(null) }
    var archived by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Contact?>(null) }
    var gathering by remember { mutableStateOf(false) }
    var fromPhone by remember { mutableStateOf(false) }
    val phoneCount = remember(model.revision) { runCatching { books.phoneContacts.count() }.getOrDefault(0) }
    val people = remember(model.revision) { model.peopleAndPets(includeArchived = true) }
    val filter = ContactFilter(text, kind, memberId, target, archived)
    val contacts = remember(model.revision, filter) { books.contacts.list(filter) }
    val all = remember(model.revision) { books.contacts.list(ContactFilter(includeArchived = true)) }
    val canAdd = remember(model.revision) { model.editableGroups().isNotEmpty() }

    // Coming from search or from a record's screen: show that contact.
    LaunchedEffect(model.focusContactId) {
        model.focusContactId?.let { selectedId = it; model.focusContactId = null }
    }
    // CON-06: on the first visit, offer to make contacts from what the app already holds.
    LaunchedEffect(Unit) {
        val key = gatherOfferKey(model)
        if (all.isEmpty() && canAdd && books.setting(key) == null && runCatching { books.contacts.proposals() }.getOrDefault(emptyList()).isNotEmpty()) gathering = true
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(model.t("nav.contacts"), style = MaterialTheme.typography.titleLarge)
            TextInput(model.t("contacts.search"), text, Modifier.width(280.dp)) { text = it }
            Box(Modifier.weight(1f))
            // CON-07: contacts made on a phone, waiting for review.
            if (phoneCount > 0) Button(onClick = { fromPhone = true }) { Text(model.t("contacts.fromPhone", phoneCount)) }
            if (canAdd) {
                OutlinedButton(onClick = { gathering = true }) { Text(model.t("contacts.gather")) }
                Button(onClick = { editing = newContact(model) }) { Text(model.t("contacts.add")) }
            }
        }
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker(model.t("contacts.kind"), listOf(null) + ContactKind.entries, kind, { it?.let { k -> model.t("contactKind.$k") } ?: model.t("contacts.allKinds") }, Modifier.width(220.dp)) { kind = it }
            Picker(model.t("contacts.forWhom"), listOf(null) + people, people.firstOrNull { it.id == memberId }, { it?.name ?: model.t("contacts.everyone") }, Modifier.width(220.dp)) { memberId = it?.id }
            Picker(model.t("contacts.linkedTo"), listOf(null) + LinkTarget.entries, target, { it?.let { t -> model.t("linkTarget.$t") } ?: model.t("contacts.anyRecord") }, Modifier.width(220.dp)) { target = it }
            LabeledCheckbox(model.t("contacts.showArchived"), archived) { archived = it }
            if (kind != null || memberId != null || target != null || text.isNotBlank()) {
                TextButton(onClick = { kind = null; memberId = null; target = null; text = "" }) { Text(model.t("contacts.clearFilters")) }
            }
        }
        Row(Modifier.weight(1f)) {
            ContactList(model, contacts, all, selectedId, Modifier.width(380.dp).fillMaxHeight()) { selectedId = it }
            VerticalDivider(Modifier.padding(horizontal = 8.dp))
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val selected = all.firstOrNull { it.id == selectedId }
                if (selected == null) {
                    Text(model.t(if (all.isEmpty()) "contacts.none" else "contacts.pick"), Modifier.padding(8.dp))
                } else {
                    ContactPage(model, selected, all, people, onSelect = { selectedId = it }, onEdit = { editing = it })
                }
            }
        }
    }

    editing?.let { c -> ContactDialog(model, c) { saved -> editing = null; saved?.let { selectedId = it.id } } }
    if (gathering) GatherDialog(model) { gathering = false; books.putSetting(gatherOfferKey(model), "1") }
    if (fromPhone) PhoneContactsDialog(model, onShow = { selectedId = it }) { fromPhone = false }
}

private fun gatherOfferKey(model: BooksModel) = "contacts.gatherOffered.${model.books.userId}"

/** A new contact in the household's shared group, the one most contacts belong in. */
internal fun newContact(model: BooksModel, name: String = "", kinds: Set<ContactKind> = emptySet(), memberIds: Set<String> = emptySet(), privateGroupId: String? = null): Contact? {
    val group = model.editableGroups().firstOrNull { it.id == privateGroupId } ?: model.defaultGroupForContacts()
    if (group == null) {
        model.error = model.t("error.noEditableGroup")
        return null
    }
    return Contact("", group.id, name, kinds = kinds, memberIds = memberIds)
}

/** Organizations with their people just under them, then everyone else, by name. */
@Composable
private fun ContactList(model: BooksModel, contacts: List<Contact>, all: List<Contact>, selectedId: String?, modifier: Modifier, onSelect: (String) -> Unit) {
    val ids = contacts.map { it.id }.toSet()
    val under = contacts.filter { it.person && it.organizationId in ids }.groupBy { it.organizationId }
    val rows = contacts.filter { !(it.person && it.organizationId in ids) }.flatMap { c -> listOf(c to 0) + under[c.id].orEmpty().map { it to 1 } }
    val names = all.associate { it.id to it.name }
    LazyColumn(modifier) {
        if (rows.isEmpty()) item { Text(model.t(if (all.isEmpty()) "contacts.none" else "contacts.noMatch"), Modifier.padding(8.dp)) }
        items(rows, key = { it.first.id }) { (c, indent) ->
            Column(
                Modifier.fillMaxWidth()
                    .background(if (c.id == selectedId) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                    .clickable { onSelect(c.id) }
                    .padding(start = (8 + indent * 20).dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Text(c.name + if (c.archived) " (${model.t("contacts.archived")})" else "", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                c.purpose?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                val line = listOfNotNull(
                    c.kinds.joinToString(", ") { model.t("contactKind.$it") }.ifEmpty { null },
                    if (indent == 0) c.organizationId?.let(names::get) else null,
                    c.phones.firstOrNull()?.value,
                ).joinToString(" · ")
                if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HorizontalDivider()
        }
    }
}

/** CON-01 to CON-04: one contact's page, with the records it is linked to, grouped by role. */
@Composable
private fun ContactPage(model: BooksModel, c: Contact, all: List<Contact>, people: List<BooksModel.Who>, onSelect: (String) -> Unit, onEdit: (Contact) -> Unit) {
    val books = model.books
    val editable = remember(model.revision, c.groupId) { model.editableGroups().any { it.id == c.groupId } }
    val groupName = remember(model.revision, c.groupId) { books.groups().firstOrNull { it.id == c.groupId }?.let { if (it.isPrivate) model.t("group.privateLabel", it.name) else it.name } }
    val links = remember(model.revision, c.id) { books.contacts.links(c.id) }
    val members = remember(model.revision, c.id) { books.contacts.people(c.id) }
    var linking by remember { mutableStateOf(false) }
    var revealing by remember { mutableStateOf<ContactDetail?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    val organization = c.organizationId?.let { id -> all.firstOrNull { it.id == id } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (editable) {
                TextButton(onClick = { onEdit(c) }) { Text(model.t("common.edit")) }
                TextButton(onClick = { merging = true }) { Text(model.t("contacts.merge")) }
                TextButton(onClick = { deleting = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
            }
        }
        c.purpose?.let { Text(model.t("contacts.whatForIs", it), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
        if (c.kinds.isNotEmpty()) Text(c.kinds.joinToString(", ") { model.t("contactKind.$it") })
        if (c.person && (c.jobTitle != null || organization != null)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                c.jobTitle?.let { Text(it + if (organization != null) " · " else "") }
                organization?.let { o -> Text(o.name, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onSelect(o.id) }) }
            }
        }
        Text(
            if (c.memberIds.isEmpty()) model.t("contacts.forHousehold")
            else model.t("contacts.forPeople", c.memberIds.mapNotNull { id -> people.firstOrNull { it.id == id }?.name }.joinToString(", ")),
            style = MaterialTheme.typography.bodyMedium,
        )
        HorizontalDivider()
        for (d in c.details) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.t("contacts.labelled", d.label ?: model.t("detailType.${d.type}")), fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 6.dp))
                Text(d.value)
                if (d.type == DetailType.NUMBER) TextButton(onClick = { revealing = d }) { Text(model.t("account.show")) }
            }
        }
        c.address?.let { Field(model.t("contacts.address"), it) }
        c.website?.let { Field(model.t("contacts.website"), it) }
        c.hours?.let { Field(model.t("contacts.hours"), it) }
        c.notes?.let { Field(model.t("calendar.notes"), it) }
        groupName?.let { Text(model.t("contacts.keptIn", it), style = MaterialTheme.typography.bodySmall) }
        if (c.archived) Text(model.t("contacts.archived"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)

        if (members.isNotEmpty()) {
            HorizontalDivider()
            Text(model.t("contacts.people"), style = MaterialTheme.typography.titleSmall)
            for (p in members) {
                Column(Modifier.fillMaxWidth().clickable { onSelect(p.id) }.padding(vertical = 2.dp)) {
                    Text(listOfNotNull(p.name, p.jobTitle).joinToString(" · "), color = MaterialTheme.colorScheme.primary)
                    p.purpose?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.t("contacts.links"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (editable) TextButton(onClick = { linking = true }) { Text(model.t("contacts.linkRecord")) }
        }
        if (links.isEmpty()) Text(model.t("contacts.noLinks"), style = MaterialTheme.typography.bodySmall)
        for ((role, inRole) in links.groupBy { it.link.role }.toSortedMap()) {
            Text(model.t("linkRole.$role"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            for (l in inRole) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        model.t("linkTarget.${l.link.target}") + " · " + l.name,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f).clickable { openLinked(model, l.link.target, l.link.targetId) }.padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                    )
                    if (editable) TextButton(onClick = { model.act { books.contacts.unlink(l.link) } }) { Text(model.t("contacts.unlink")) }
                }
            }
        }
    }

    if (linking) LinkRecordDialog(model, c) { linking = false }
    if (merging) MergeContactDialog(model, c) { merging = false }
    revealing?.let { d -> RevealContactNumberDialog(model, c, d) { revealing = null } }
    if (deleting) {
        AskBeforeDeleting(model, model.t("contacts.deleteQuestion", c.name), { deleting = false }) { model.act { books.contacts.delete(c.id) } != null }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value)
    }
}

/** CON-04: links the contact to a record of the app, in the role chosen. */
@Composable
private fun LinkRecordDialog(model: BooksModel, contact: Contact, onClose: () -> Unit) {
    var role by remember { mutableStateOf(contact.kinds.firstNotNullOfOrNull { k -> LinkRole.entries.firstOrNull { it.kind == k } } ?: LinkRole.OTHER) }
    var target by remember(role) { mutableStateOf(role.targets.minBy { it.ordinal }) }
    val candidates = remember(target) { model.books.contacts.candidates(target) }
    var recordId by remember(target) { mutableStateOf<String?>(null) }
    FormDialog(model.t("contacts.linkRecordTitle", contact.name), model.t("contacts.link"), model.t("common.cancel"), canSave = recordId != null, onDismiss = onClose, onSave = {
        if (model.act { model.books.contacts.link(contact.id, role, target, recordId!!) } != null) onClose()
    }) {
        Picker(model.t("contacts.role"), LinkRole.entries, role, { model.t("linkRole.$it") }) { role = it }
        if (role.targets.size > 1) Picker(model.t("contacts.recordKind"), role.targets.sortedBy { it.ordinal }, target, { model.t("linkTarget.$it") }) { target = it }
        Picker(model.t("linkTarget.$target"), candidates, candidates.firstOrNull { it.id == recordId }, { it.name }) { recordId = it.id }
        if (candidates.isEmpty()) Text(model.t("contacts.noRecords"), style = MaterialTheme.typography.bodySmall)
    }
}

/** SEC-04: a contact's account or client number in full, after the password is entered again. */
@Composable
private fun RevealContactNumberDialog(model: BooksModel, contact: Contact, detail: ContactDetail, onClose: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf<String?>(null) }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(detail.label ?: model.t("detailType.NUMBER")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (revealed != null) {
                    Text(revealed!!, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                } else {
                    Text(model.t("account.reveal.prompt"))
                    TextInput(model.t("unlock.password"), password, secret = true, error = if (wrong) model.t("unlock.wrong") else null) { password = it }
                }
            }
        },
        confirmButton = {
            if (revealed == null) {
                TextButton(onClick = {
                    try {
                        revealed = model.books.contacts.revealNumber(contact.id, detail.id, password.toCharArray()).orEmpty()
                    } catch (_: AccessDeniedException) {
                        wrong = true
                    }
                    password = ""
                }) { Text(model.t("account.show")) }
            } else {
                TextButton(onClick = onClose) { Text("OK") }
            }
        },
        dismissButton = { if (revealed == null) TextButton(onClick = onClose) { Text(model.t("common.cancel")) } },
    )
}

/**
 * CON-01, CON-02: adds or changes a contact. [onClose] receives the saved contact, or null when
 * the user cancels. [save] stores it (a contact from the phone also leaves the review list).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContactDialog(model: BooksModel, existing: Contact, save: (Contact) -> Contact = model.books.contacts::save, onClose: (Contact?) -> Unit) {
    val books = model.books
    val isNew = existing.id.isBlank()
    val people = remember { model.peopleAndPets() }
    val organizations = remember(model.revision) { books.contacts.list(ContactFilter(includeArchived = true)).filter { !it.person && it.id != existing.id } }
    var groupId by remember { mutableStateOf(existing.groupId) }
    var name by remember { mutableStateOf(existing.name) }
    var person by remember { mutableStateOf(existing.person) }
    var organizationId by remember { mutableStateOf(existing.organizationId) }
    var jobTitle by remember { mutableStateOf(existing.jobTitle.orEmpty()) }
    var purpose by remember { mutableStateOf(existing.purpose.orEmpty()) }
    var kinds by remember { mutableStateOf(existing.kinds) }
    var memberIds by remember { mutableStateOf(existing.memberIds) }
    var details by remember { mutableStateOf(existing.details) }
    var address by remember { mutableStateOf(existing.address.orEmpty()) }
    var website by remember { mutableStateOf(existing.website.orEmpty()) }
    var hours by remember { mutableStateOf(existing.hours.orEmpty()) }
    var notes by remember { mutableStateOf(existing.notes.orEmpty()) }
    var archived by remember { mutableStateOf(existing.archived) }
    var allKinds by remember { mutableStateOf(false) }

    FormDialog(
        model.t(if (isNew) "contacts.add" else "contacts.edit"), model.t("common.save"), model.t("common.cancel"),
        canSave = name.isNotBlank(), onDismiss = { onClose(null) },
        onSave = {
            val saved = model.act {
                save(
                    existing.copy(
                        groupId = groupId, name = name, person = person, organizationId = if (person) organizationId else null, jobTitle = jobTitle.takeIf { person },
                        purpose = purpose, kinds = kinds, memberIds = memberIds, details = details.filter { it.value.isNotBlank() },
                        address = address, website = website, hours = hours, notes = notes, archived = archived,
                    ),
                )
            }
            if (saved != null) onClose(saved)
        },
    ) {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextInput(model.t("contacts.name"), name) { name = it }
            TextInput(model.t("contacts.whatFor"), purpose, supporting = model.t("contacts.whatFor.hint")) { purpose = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(!person, { person = false }, { Text(model.t("contacts.organization")) })
                FilterChip(person, { person = true }, { Text(model.t("contacts.person")) })
            }
            if (person) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput(model.t("contacts.jobTitle"), jobTitle, Modifier.weight(1f)) { jobTitle = it }
                    Picker(model.t("contacts.worksAt"), listOf(null) + organizations, organizations.firstOrNull { it.id == organizationId }, { it?.label ?: model.t("common.none") }, Modifier.weight(1f)) { organizationId = it?.id }
                }
            }
            Text(model.t("contacts.kinds"), style = MaterialTheme.typography.labelLarge)
            // The common kinds first; the rest on request, and always those already chosen.
            val shown = if (allKinds) ContactKind.entries else ContactKind.entries.filter { it in COMMON_KINDS || it in kinds }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (k in shown) FilterChip(k in kinds, { kinds = if (k in kinds) kinds - k else kinds + k }, { Text(model.t("contactKind.$k")) })
                if (!allKinds) TextButton(onClick = { allKinds = true }) { Text(model.t("contacts.moreKinds")) }
            }
            Text(model.t("contacts.forWhomHint"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (p in people) FilterChip(p.id in memberIds, { memberIds = if (p.id in memberIds) memberIds - p.id else memberIds + p.id }, { Text(p.name) })
            }
            Text(model.t("contacts.reach"), style = MaterialTheme.typography.labelLarge)
            details.forEachIndexed { i, d ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(model.t("detailType.${d.type}"), Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall)
                    TextInput(model.t("contacts.detailLabel"), d.label.orEmpty(), Modifier.weight(1f)) { v -> details = details.toMutableList().also { it[i] = d.copy(label = v) } }
                    TextInput(
                        model.t("contacts.detailValue"), d.value, Modifier.weight(1.6f),
                        supporting = if (d.type == DetailType.NUMBER && d.id.isNotBlank()) model.t("contacts.numberMasked") else null,
                    ) { v -> details = details.toMutableList().also { it[i] = d.copy(value = v) } }
                    TextButton(onClick = { details = details.toMutableList().also { it.removeAt(i) } }) { Text(model.t("contacts.remove")) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { details = details + ContactDetail(type = DetailType.PHONE, value = "") }) { Text(model.t("contacts.addPhone")) }
                TextButton(onClick = { details = details + ContactDetail(type = DetailType.EMAIL, value = "") }) { Text(model.t("contacts.addEmail")) }
                TextButton(onClick = { details = details + ContactDetail(type = DetailType.NUMBER, value = "") }) { Text(model.t("contacts.addNumber")) }
            }
            TextInput(model.t("contacts.address"), address, singleLine = false) { address = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("contacts.website"), website, Modifier.weight(1f)) { website = it }
                TextInput(model.t("contacts.hours"), hours, Modifier.weight(1f)) { hours = it }
            }
            TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
            GroupPicker(model, groupId, enabled = isNew) { groupId = it.id }
            if (isNew) PrivateGroupHint(model) { groupId = it }
            if (!isNew) LabeledCheckbox(model.t("contacts.archived"), archived) { archived = it }
        }
    }
}

private val COMMON_KINDS = setOf(
    ContactKind.BANK, ContactKind.CREDIT_UNION, ContactKind.INVESTMENT_FIRM, ContactKind.FINANCIAL_ADVISOR, ContactKind.INSURER, ContactKind.INSURANCE_BROKER,
    ContactKind.PHARMACY, ContactKind.FAMILY_DOCTOR, ContactKind.SPECIALIST, ContactKind.DENTIST, ContactKind.CONTRACTOR,
)

/**
 * CON-06: offers to make contacts from the institutions, health providers, contractors, policy
 * insurers and brokers, pets' insurers and estate contacts already in the app. Records that look
 * like the same contact are shown together and merged only when the user ticks it; nothing
 * existing is changed.
 */
@Composable
private fun GatherDialog(model: BooksModel, onClose: () -> Unit) {
    val books = model.books
    val proposals = remember { runCatching { books.contacts.proposals() }.getOrDefault(emptyList<GatherProposal>()) }
    val excluded = remember { mutableStateMapOf<Pair<Int, Int>, Boolean>() }
    val merge = remember { mutableStateMapOf<Int, Boolean>() }
    var groupId by remember { mutableStateOf(model.defaultGroupForContacts()?.id) }
    WideDialog(model.t("contacts.gatherTitle"), model.t("contacts.notNow"), onClose) {
        Column(Modifier.width(760.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (proposals.isEmpty()) {
                Text(model.t("contacts.gatherNothing"))
                return@Column
            }
            Text(model.t("contacts.gatherExplain"), style = MaterialTheme.typography.bodyMedium)
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                proposals.forEachIndexed { pi, p ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            if (p.isDuplicate) Text(model.t("contacts.likelySame"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                            p.sources.forEachIndexed { si, s ->
                                LabeledCheckbox(
                                    listOfNotNull(s.name, model.t("linkTarget.${s.target}"), s.phone, s.purpose).joinToString(" · "),
                                    excluded[pi to si] != true,
                                ) { excluded[pi to si] = !it }
                            }
                            for (e in p.existing) Text(model.t("contacts.existing", e.label), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp))
                            if (p.isDuplicate) {
                                LabeledCheckbox(model.t(if (p.existing.isEmpty()) "contacts.mergeInto" else "contacts.addToExisting"), merge[pi] == true) { merge[pi] = it }
                            }
                        }
                    }
                }
            }
            GroupPicker(model, groupId, enabled = true) { groupId = it.id }
            Text(model.t("contacts.gatherPrivate"), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Button(enabled = groupId != null, onClick = {
                    val decisions = proposals.flatMapIndexed { pi, p ->
                        val chosen = p.sources.filterIndexed { si, _ -> excluded[pi to si] != true }
                        when {
                            chosen.isEmpty() -> emptyList()
                            p.isDuplicate && merge[pi] == true -> listOf(GatherDecision(chosen, p.existing.firstOrNull()?.id))
                            else -> chosen.map { GatherDecision(listOf(it)) }
                        }
                    }
                    if (model.act { books.contacts.gather(groupId!!, decisions) } != null) onClose()
                }) { Text(model.t("contacts.gatherCreate")) }
            }
        }
    }
}
