package ca.schippers.hfm.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import ca.schippers.hfm.books.LinkedContact
import ca.schippers.hfm.books.LinkRole
import ca.schippers.hfm.books.LinkTarget
import ca.schippers.hfm.books.SearchService
import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.domain.AccountType

/**
 * CON-04: the contacts of one record, on the record's own screen: "Bank: RBC Royal Bank · RRSP and
 * TFSA", each opening the contact's page, with a way to link a contact or create one. [roles] are
 * the roles offered, the most likely first; [suggestedName] and [memberIds] fill a new contact;
 * a record kept in a private group ([groupId]) gets its new contacts in that group too. [through]
 * are contacts linked to the record's parent (a job's contractor), shown with [throughNote] but not
 * linked again; one also linked to the record itself is shown once, as its own link.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinkedContacts(
    model: BooksModel,
    target: LinkTarget,
    targetId: String?,
    roles: List<LinkRole> = LinkRole.forTarget(target),
    suggestedName: String = "",
    memberIds: Set<String> = emptySet(),
    groupId: String? = null,
    compact: Boolean = false,
    canLink: Boolean = true,
    through: List<LinkedContact> = emptyList(),
    throughNote: String = "",
) {
    if (targetId.isNullOrBlank()) return
    val linked = remember(model.revision, target, targetId) { runCatching { model.books.contacts.linkedTo(target, targetId) }.getOrDefault(emptyList()) }
    val inherited = through.filter { t -> linked.none { it.contact.id == t.contact.id } }.distinctBy { it.contact.id }
    val editable = remember(model.revision) { model.editableGroups().map { it.id }.toSet() }
    var picking by remember { mutableStateOf(false) }
    if (picking) PickContactDialog(model, target, targetId, roles, suggestedName, memberIds, groupId) { picking = false }
    if (compact) {
        // One line under a record's title: "Lender · RBC Royal Bank · Mortgage", each opening the contact.
        if (linked.isEmpty() && inherited.isEmpty() && !(canLink && editable.isNotEmpty())) return
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (l in linked) {
                TextButton(onClick = { openContact(model, l.contact.id) }) {
                    Text(model.t("linkRoleShort.${l.link.role}") + " · " + l.contact.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            for (l in inherited) {
                TextButton(onClick = { openContact(model, l.contact.id) }) {
                    Text(l.contact.label + " (" + throughNote + ")", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (canLink && editable.isNotEmpty()) TextButton(onClick = { picking = true }) { Text(model.t("contacts.linkContact"), style = MaterialTheme.typography.bodySmall) }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.t("contacts.linked"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (editable.isNotEmpty()) TextButton(onClick = { picking = true }) { Text(model.t("contacts.linkContact")) }
        }
        if (linked.isEmpty() && inherited.isEmpty()) Text(model.t("contacts.noneLinked"), style = MaterialTheme.typography.bodySmall)
        for (l in linked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { openContact(model, l.contact.id) }.padding(vertical = 2.dp)) {
                    Text(model.t("linkRoleShort.${l.link.role}") + " · " + l.contact.name, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    val line = listOfNotNull(l.contact.purpose, l.contact.phones.firstOrNull()?.value, l.contact.emails.firstOrNull()?.value).joinToString(" · ")
                    if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (l.link.groupId in editable) TextButton(onClick = { model.act { model.books.contacts.unlink(l.link) } }) { Text(model.t("contacts.unlink")) }
            }
        }
        // Contacts of the parent record: "Contact · Toitures Laval (through the contractor)", not linked again.
        for (l in inherited) {
            Column(Modifier.fillMaxWidth().clickable { openContact(model, l.contact.id) }.padding(vertical = 2.dp)) {
                Text(model.t("linkRoleShort.${l.link.role}") + " · " + l.contact.name, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                val line = listOfNotNull(throughNote.ifEmpty { null }, l.contact.purpose, l.contact.phones.firstOrNull()?.value).joinToString(" · ")
                if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** CON-04: picks the contact for a record and its role, or creates a new contact and links it. */
@Composable
private fun PickContactDialog(
    model: BooksModel,
    target: LinkTarget,
    targetId: String,
    roles: List<LinkRole>,
    suggestedName: String,
    memberIds: Set<String>,
    groupId: String?,
    onClose: () -> Unit,
) {
    val books = model.books
    var role by remember { mutableStateOf(roles.firstOrNull() ?: LinkRole.OTHER) }
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<Contact?>(null) }
    var creating by remember { mutableStateOf<Contact?>(null) }
    val editable = remember(model.revision) { model.editableGroups().map { it.id }.toSet() }
    val contacts = remember(model.revision, query) { books.contacts.list(ContactFilter(text = query)).filter { it.groupId in editable } }
    // Contacts of the role's kind first: the banks for "Bank", the pharmacies for "Pharmacy".
    val sorted = contacts.sortedWith(compareBy({ role.kind == null || role.kind !in it.kinds }, { SearchService.fold(it.name) }))
    FormDialog(model.t("contacts.pickTitle"), model.t("contacts.link"), model.t("common.cancel"), canSave = chosen != null, onDismiss = onClose, onSave = {
        if (model.act { books.contacts.link(chosen!!.id, role, target, targetId) } != null) onClose()
    }) {
        if (roles.size > 1) Picker(model.t("contacts.role"), roles, role, { model.t("linkRoleShort.$it") }) { role = it }
        TextInput(model.t("contacts.search"), query) { query = it }
        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
            if (sorted.isEmpty()) Text(model.t("contacts.noMatch"), style = MaterialTheme.typography.bodySmall)
            for (c in sorted) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(if (chosen?.id == c.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                        .clickable { chosen = c }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(c.label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val line = listOfNotNull(c.kinds.joinToString(", ") { model.t("contactKind.$it") }.ifEmpty { null }, c.phones.firstOrNull()?.value).joinToString(" · ")
                    if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        TextButton(onClick = {
            creating = newContact(model, query.ifBlank { suggestedName }, setOfNotNull(role.kind), memberIds, groupId?.takeIf { id -> model.editableGroups().any { it.id == id && it.isPrivate } })
        }) { Text(model.t("contacts.newAndLink")) }
    }
    creating?.let { draft ->
        ContactDialog(model, draft) { saved ->
            creating = null
            if (saved != null && model.act { books.contacts.link(saved.id, role, target, targetId) } != null) onClose()
        }
    }
}

/** CON-04: the roles a contact can have for an account, the most likely first. */
fun accountRoles(type: AccountType): List<LinkRole> = when (type.kind) {
    AccountKind.LOAN -> listOf(LinkRole.LENDER, LinkRole.ADVISOR, LinkRole.BANK, LinkRole.OTHER)
    AccountKind.INVESTMENT -> listOf(LinkRole.INVESTMENT_FIRM, LinkRole.ADVISOR, LinkRole.BANK, LinkRole.OTHER)
    AccountKind.CREDIT -> listOf(LinkRole.BANK, LinkRole.LENDER, LinkRole.ADVISOR, LinkRole.OTHER)
    else -> listOf(LinkRole.BANK, LinkRole.ADVISOR, LinkRole.LENDER, LinkRole.OTHER)
}

/** Opens the Contacts screen on one contact. */
fun openContact(model: BooksModel, contactId: String) {
    model.focusContactId = contactId
    model.section = Section.CONTACTS
}

/** CON-04: opens the screen where a linked record lives. */
fun openLinked(model: BooksModel, target: LinkTarget, targetId: String) {
    model.reconcilingStatementId = null
    model.section = when (target) {
        LinkTarget.INSTITUTION -> Section.INSTITUTIONS
        LinkTarget.PAYEE -> Section.PAYEES
        LinkTarget.ACCOUNT -> {
            model.selectedAccountId = targetId
            when (runCatching { model.books.accounts.get(targetId).type.kind }.getOrNull()) {
                AccountKind.LOAN -> Section.LOANS
                AccountKind.INVESTMENT -> Section.INVESTMENTS
                else -> Section.ACCOUNTS
            }
        }
        LinkTarget.POLICY, LinkTarget.ASSET -> Section.ASSETS
        LinkTarget.CONTRACTOR -> {
            model.focusContractorId = targetId
            model.focusContractorJobs = false
            Section.ASSETS
        }
        LinkTarget.CONTRACTOR_JOB -> {
            // The job's contractor, with its jobs open.
            model.focusContractorId = runCatching { model.books.contractors.list(includeArchived = true).firstOrNull { c -> c.jobs.any { it.id == targetId } }?.id }.getOrNull()
            model.focusContractorJobs = true
            Section.ASSETS
        }
        LinkTarget.HEALTH_PROVIDER -> Section.HEALTH
        LinkTarget.MEDICATION -> {
            model.healthSubjectId = runCatching { model.books.health.medications().firstOrNull { it.id == targetId }?.memberId }.getOrNull()
            Section.HEALTH
        }
        LinkTarget.EVENT -> Section.CALENDAR
        LinkTarget.BILL -> Section.BILLS
        LinkTarget.PET -> Section.PETS
        LinkTarget.VEHICLE -> Section.VEHICLES
        LinkTarget.ESTATE -> Section.ESTATE
    }
}
