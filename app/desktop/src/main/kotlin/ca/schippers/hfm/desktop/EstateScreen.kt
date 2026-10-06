package ca.schippers.hfm.desktop

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
import ca.schippers.hfm.books.ConditionStatus
import ca.schippers.hfm.books.ContactRole
import ca.schippers.hfm.books.EmergencySummary
import ca.schippers.hfm.books.EstateContact
import ca.schippers.hfm.books.EstatePlan
import ca.schippers.hfm.books.LinkTarget
import java.io.File
import javax.swing.JFileChooser

private enum class EstateTab { SUMMARY, PAPERS }

/** EST-01 to EST-03: what a spouse or an executor would need, and where each person's papers are. */
@Composable
fun EstateScreen(model: BooksModel) {
    var tab by remember { mutableStateOf(EstateTab.SUMMARY) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(model.t("nav.estate"), style = MaterialTheme.typography.titleLarge)
        Text(model.t("estate.hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        PrimaryTabRow(selectedTabIndex = tab.ordinal, modifier = Modifier.padding(vertical = 8.dp)) {
            for (t in EstateTab.entries) Tab(selected = tab == t, onClick = { tab = t }, text = { Text(model.t("estate.tab.$t")) })
        }
        when (tab) {
            EstateTab.SUMMARY -> SummaryTab(model)
            EstateTab.PAPERS -> PapersTab(model)
        }
    }
}

// --- Summary (EST-01, EST-03) --------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryTab(model: BooksModel) {
    val summary = remember(model.revision) { model.books.estate.summary() }
    val sections = remember(summary, model.language) { emergencySections(model, summary) }
    var exporting by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { exporting = true }) { Text(model.t("estate.export")) }
        OutlinedButton(onClick = { model.act { printSections(model, model.t("estate.title"), sections) } }) { Text(model.t("report.print")) }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
        for (s in sections) {
            Text(s.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            if (s.lines.isEmpty()) Text(model.t("estate.nothing"), style = MaterialTheme.typography.bodySmall)
            for ((label, value) in s.lines) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(label, Modifier.width(260.dp), fontWeight = FontWeight.Medium)
                    Text(value, Modifier.weight(1f))
                }
            }
            s.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            HorizontalDivider(Modifier.padding(top = 8.dp))
        }
    }
    if (exporting) ExportPdfDialog(model, model.t("estate.title"), sections) { exporting = false }
}

/** EST-01: the summary in sections: each person's papers and contacts, then the household's institutions, accounts, policies, pensions and kept documents. */
fun emergencySections(model: BooksModel, s: EmergencySummary): List<DocSection> {
    val members = model.books.members.list(includeArchived = true).associate { it.id to it.displayName }
    fun yes(b: Boolean) = model.t(if (b) "common.yes" else "common.no")
    fun fold(name: String) = name.trim().lowercase()
    val people = s.records.sortedBy { members[it.memberId].orEmpty() }.map { r ->
        val p = r.plan
        // A person typed on the papers who is also linked from Contacts is listed once, from Contacts (kept up to date).
        val linked = runCatching { model.books.contacts.linkedTo(LinkTarget.ESTATE, r.memberId) }.getOrDefault(emptyList())
        val linkedNames = linked.map { fold(it.contact.name) }.toSet()
        DocSection(
            model.t("estate.papersOf", members[r.memberId].orEmpty()),
            listOfNotNull(
                p.willLocation?.let { model.t("estate.will") to (it + (p.willDate?.let { d -> " (${model.t("estate.dated", d)})" } ?: "")) },
                p.powerOfAttorneyLocation?.let { model.t("estate.powerOfAttorney") to it },
                p.mandateLocation?.let { model.t("estate.mandate") to it },
                p.safeDepositBox?.let { model.t("estate.safeDepositBox") to it },
                p.safeDepositKeys?.let { model.t("estate.keys") to it },
                p.digitalAccounts?.let { model.t("estate.digital") to it },
                p.otherDocuments?.let { model.t("estate.otherDocuments") to it },
                // EST-01: the donor line is always there; "Not stated" until the box is ticked or unticked.
                model.t("estate.organDonor") to (p.organDonor?.let(::yes) ?: model.t("estate.notStated")),
                p.funeralWishes?.let { model.t("estate.funeral") to it },
                p.notes?.let { model.t("estate.notes") to it },
            ) + p.contacts.filter { c -> fold(c.name) !in linkedNames }.map { c ->
                model.t("contactRole.${c.role}") to listOfNotNull(c.name, c.organization, c.phone, c.email, c.notes).joinToString(" · ")
            } + linked.map { l ->
                model.t("linkRoleShort.${l.link.role}") to listOfNotNull(l.contact.name, l.contact.purpose, l.contact.phones.firstOrNull()?.value, l.contact.emails.firstOrNull()?.value).joinToString(" · ")
            },
        )
    }
    val institutions = DocSection(model.t("estate.institutions"), s.institutions.map { i ->
        i.name to listOfNotNull(i.branch, i.phone, i.website).joinToString(" · ")
    })
    val accounts = DocSection(model.t("estate.accounts"), s.accounts.sortedBy { it.institution?.name ?: "~" }.map { a ->
        val acc = a.account
        acc.name to listOfNotNull(
            model.t("accountType.${acc.type}"), a.institution?.name, acc.numberMasked, a.ownerNames.takeIf { it.isNotEmpty() }?.joinToString(", "),
            model.money(a.balance),
            a.beneficiaries.takeIf { it.isNotEmpty() }?.let { b -> model.t("estate.beneficiaries", b.joinToString(", ") { x -> x.name + (x.sharePercent?.let { p -> " ${p.stripTrailingZeros().toPlainString()} %" } ?: "") }) },
        ).joinToString(" · ")
    }, note = model.t("estate.balancesOn", model.date(today())))
    val policies = DocSection(model.t("estate.policies"), s.policies.map { p ->
        "${model.t("policyKind.${p.policy.kind}")} · ${p.policy.insurer}" to listOfNotNull(
            p.policy.policyNumber, p.insuredName, p.policy.coverage?.let(model::money), p.policy.broker,
            p.beneficiaries.takeIf { it.isNotEmpty() }?.let { b -> model.t("estate.beneficiaries", b.joinToString(", ") { it.name }) },
        ).joinToString(" · ")
    })
    // EST-01: medical, dental and other health plans, with the people they cover.
    val medical = DocSection(model.t("estate.medicalPlans"), runCatching { model.books.medical.plans(includeInactive = false) }.getOrDefault(emptyList()).map { p ->
        "${model.t("medPlanKind.${p.kind}")} · ${p.name}" to listOfNotNull(
            p.insurer, p.policyNumber, p.certificateNumber, p.memberId?.let(members::get),
            p.people.mapNotNull { members[it.memberId] }.takeIf { it.isNotEmpty() }?.let { model.t("estate.covers", it.joinToString(", ")) },
        ).joinToString(" · ")
    })
    val pensions = DocSection(model.t("estate.pensions"), s.pensions.map { p ->
        p.name to listOfNotNull(model.t("pensionKind.${p.kind}"), members[p.memberId], p.administrator, p.memberNumber).joinToString(" · ")
    })
    val kept = DocSection(model.t("estate.keptDocuments"), s.keptDocuments.map { d ->
        (d.title ?: d.merchant ?: d.fileName ?: "?") to listOfNotNull(d.date?.let(model::date), d.notes).joinToString(" · ")
    }, note = model.t("estate.keptNote"))
    return people + listOf(institutions, accounts, policies, medical, pensions, kept)
}

/** HLT-09: one person's health summary for appointments and emergencies. */
fun healthSections(model: BooksModel, memberId: String): List<DocSection> {
    val h = model.books.health
    val providers = h.providers().associateBy { it.id }
    val meds = h.medications(memberId).filter { it.active }
    return listOf(
        DocSection(model.t("health.tab.ALLERGIES"), h.allergies(memberId).map { a ->
            a.substance to listOfNotNull(a.severity?.let { model.t("severity.$it") }, a.reaction, a.notes).joinToString(" · ")
        }),
        DocSection(model.t("health.tab.CONDITIONS"), h.conditions(memberId).filter { it.status != ConditionStatus.RESOLVED }.map { c ->
            c.name to listOfNotNull(model.t("conditionStatus.${c.status}"), c.diagnosed?.let(model::date), c.notes).joinToString(" · ")
        }),
        DocSection(model.t("health.tab.MEDICATIONS"), meds.map { m ->
            m.name to listOfNotNull(m.dose, m.instructions, m.prescriberId?.let(providers::get)?.name).joinToString(" · ")
        }),
        DocSection(model.t("health.tab.IMMUNIZATIONS"), h.immunizations(memberId).sortedByDescending { it.date }.distinctBy { it.vaccine.lowercase() }.map { i ->
            i.vaccine to model.date(i.date)
        }),
        DocSection(model.t("health.tab.PROVIDERS"), (meds.mapNotNull { it.prescriberId } + meds.mapNotNull { it.pharmacyId }).distinct().mapNotNull(providers::get).map { p ->
            "${model.t("providerKind.${p.kind}")} · ${p.name}" to listOfNotNull(p.phone, p.address).joinToString(" · ")
        }),
    )
}

/** Prints the sections (or opens them when the system has no print action); [subtitleKey] is the line under the title. */
internal fun printSections(model: BooksModel, title: String, sections: List<DocSection>, subtitleKey: String = "estate.prepared") {
    // Not encrypted: removed shortly after the printer or viewer has it (PrintFiles).
    val file = PrintFiles.create("hfm-summary-")
    SectionsPdf.write(title, model.t(subtitleKey, model.date(today())), sections, file, emptyText = model.t("estate.nothing"))
    PrintFiles.printOrOpen(file)
}

/** EST-03: saves the summary as a PDF, protected by a password when one is given, for a spouse or an executor. */
@Composable
fun ExportPdfDialog(model: BooksModel, title: String, sections: List<DocSection>, subtitleKey: String = "estate.prepared", onClose: () -> Unit) {
    var protect by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val ok = !protect || (password.length >= 8 && password == again)
    FormDialog(model.t("estate.exportTitle"), model.t("estate.saveAs"), model.t("common.cancel"), canSave = ok, onDismiss = onClose, onSave = {
        val chooser = JFileChooser().apply { selectedFile = File(title.replace(Regex("""[\\/:*?"<>|]"""), "-") + ".pdf") }
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile.let { if (it.extension.equals("pdf", true)) it else File(it.path + ".pdf") }
            if (model.act { SectionsPdf.write(title, model.t(subtitleKey, model.date(today())), sections, file, password.toCharArray().takeIf { protect }, model.t("estate.nothing")) } != null) onClose()
        }
    }) {
        Text(model.t("estate.exportHint"), style = MaterialTheme.typography.bodySmall)
        LabeledCheckbox(model.t("estate.protect"), protect) { protect = it }
        if (protect) {
            TextInput(model.t("estate.password"), password, secret = true, supporting = model.t("estate.passwordHint")) { password = it }
            TextInput(model.t("estate.passwordAgain"), again, secret = true, error = model.t("estate.passwordsDiffer").takeIf { again.isNotEmpty() && again != password }) { again = it }
        } else {
            Text(model.t("estate.unprotected"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// --- Papers and wishes (EST-02) ------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PapersTab(model: BooksModel) {
    val books = model.books
    val members = remember(model.revision) { books.members.list() }
    var who by remember { mutableStateOf(members.firstOrNull()?.id) }
    if (members.isEmpty()) {
        Text(model.t("estate.noMembers"))
        return
    }
    Picker(model.t("report.person"), members, members.firstOrNull { it.id == who }, { it.displayName }, Modifier.width(240.dp)) { who = it.id }
    val memberId = who ?: return
    val record = remember(model.revision, memberId) { books.estate.record(memberId) }
    val groups = remember(model.revision) { books.groups().filter { it.level.allows(ca.schippers.hfm.domain.PermissionLevel.EDIT) } }
    var group by remember(memberId) { mutableStateOf(record?.groupId ?: groups.firstOrNull { it.isPrivate } ?.id ?: groups.firstOrNull()?.id) }
    var plan by remember(memberId, record) { mutableStateOf(record?.plan ?: EstatePlan()) }
    val contacts = remember(memberId, record) { mutableStateListOf<EstateContact>().apply { addAll(record?.plan?.contacts.orEmpty()) } }
    var saved by remember(memberId) { mutableStateOf(false) }
    fun text(v: String?) = v.orEmpty()
    fun clean(v: String) = v.trim().ifEmpty { null }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(model.t("estate.papersHint"), style = MaterialTheme.typography.bodySmall)
        if (record == null && groups.size > 1) {
            Picker(model.t("estate.keepIn"), groups, groups.firstOrNull { it.id == group }, { it.name }, Modifier.width(320.dp)) { group = it.id }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("estate.will"), text(plan.willLocation), Modifier.weight(1f)) { plan = plan.copy(willLocation = clean(it)) }
            DateInput(model.t("estate.willDate"), text(plan.willDate), Modifier.width(180.dp)) { plan = plan.copy(willDate = clean(it)) }
        }
        TextInput(model.t("estate.powerOfAttorney"), text(plan.powerOfAttorneyLocation)) { plan = plan.copy(powerOfAttorneyLocation = clean(it)) }
        TextInput(model.t("estate.mandate"), text(plan.mandateLocation), supporting = model.t("estate.mandateHint")) { plan = plan.copy(mandateLocation = clean(it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("estate.safeDepositBox"), text(plan.safeDepositBox), Modifier.weight(1f)) { plan = plan.copy(safeDepositBox = clean(it)) }
            TextInput(model.t("estate.keys"), text(plan.safeDepositKeys), Modifier.weight(1f)) { plan = plan.copy(safeDepositKeys = clean(it)) }
        }
        TextInput(model.t("estate.digital"), text(plan.digitalAccounts), supporting = model.t("estate.digitalHint")) { plan = plan.copy(digitalAccounts = clean(it)) }
        TextInput(model.t("estate.otherDocuments"), text(plan.otherDocuments)) { plan = plan.copy(otherDocuments = clean(it)) }
        TextInput(model.t("estate.funeral"), text(plan.funeralWishes), singleLine = false) { plan = plan.copy(funeralWishes = clean(it)) }
        LabeledCheckbox(model.t("estate.organDonor"), plan.organDonor == true) { plan = plan.copy(organDonor = it) }
        TextInput(model.t("estate.notes"), text(plan.notes), singleLine = false) { plan = plan.copy(notes = clean(it)) }

        Text(model.t("estate.contacts"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        contacts.forEachIndexed { i, c ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Picker(model.t("estate.role"), ContactRole.entries, c.role, { model.t("contactRole.$it") }, Modifier.width(220.dp)) { contacts[i] = c.copy(role = it) }
                TextInput(model.t("estate.contactName"), c.name, Modifier.width(220.dp)) { contacts[i] = c.copy(name = it) }
                TextInput(model.t("estate.organization"), text(c.organization), Modifier.width(220.dp)) { contacts[i] = c.copy(organization = clean(it)) }
                TextInput(model.t("estate.phone"), text(c.phone), Modifier.width(170.dp)) { contacts[i] = c.copy(phone = clean(it)) }
                TextInput(model.t("estate.email"), text(c.email), Modifier.width(220.dp)) { contacts[i] = c.copy(email = clean(it)) }
                RemoveButton(model.t("common.remove"), Modifier.align(Alignment.CenterVertically)) { contacts.removeAt(i) }
            }
        }
        TextButton(onClick = { contacts.add(EstateContact(ContactRole.EXECUTOR, "")) }) { Text(model.t("estate.addContact")) }
        // CON-04: contacts from the Contacts screen in an estate role, once the papers are saved.
        record?.let { r -> LinkedContacts(model, LinkTarget.ESTATE, r.memberId, memberIds = setOf(r.memberId), groupId = r.groupId) }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                val g = group ?: return@Button
                saved = model.act { books.estate.save(memberId, g, plan.copy(contacts = contacts.filter { it.name.isNotBlank() }.toList())) } != null
            }) { Text(model.t("common.save")) }
            if (saved) Text(model.t("estate.saved"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        record?.let { r -> DocumentsBlock(model, ESTATE_ENTITY, r.id, r.groupId, "estate.files") }
    }
}

/** Scans of the will, the mandate and the like are vault documents linked to the person's record. */
const val ESTATE_ENTITY = "estate"
