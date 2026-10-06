package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.DocumentStatus
import ca.schippers.hfm.books.HomeProject
import ca.schippers.hfm.books.ProjectRebate
import ca.schippers.hfm.books.RebateStatus
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.books.VaultDocument
import ca.schippers.hfm.money.MoneyFormat
import ca.schippers.hfm.ocr.desktop.FileKind
import kotlinx.datetime.LocalDate
import javax.swing.JFileChooser

private fun dayOrNull(text: String): LocalDate? =
    text.trim().ifEmpty { null }?.let { runCatching { LocalDate.parse(it) }.getOrElse { throw ValidationException("error.invalidDate") } }

/**
 * SEA-05: a home project's rebates and grants, its net cost after them, and the papers kept with
 * the project (the contract, the energy advisor's reports). Shown in the project's costs dialog.
 */
@Composable
internal fun ProjectRebatesBlock(model: BooksModel, p: HomeProject) {
    val rebates = model.books.projectRebates
    val list = remember(model.revision, p.id) { rebates.list(p) }
    val papers = remember(model.revision, p.id) { rebates.projectDocuments(p) }
    var editing by remember { mutableStateOf<ProjectRebate?>(null) }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(model.t("rebate.title"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { editing = ProjectRebate("", p.id, "", RebateStatus.APPLIED, applied = today()) }) { Text(model.t("rebate.add")) }
    }
    Text(model.t("rebate.hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    if (list.isEmpty()) Text(model.t("rebate.none"), style = MaterialTheme.typography.bodySmall)
    for (r in list) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.program, fontWeight = FontWeight.Medium)
                Text(
                    listOfNotNull(
                        model.t("rebateStatus.${r.status}"), r.amount?.let { model.t("rebate.askedFor", model.money(it)) },
                        r.received?.let { model.t("rebate.receivedOf", model.money(it)) }, r.reference,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = { editing = r }) { Text(model.t("common.edit")) }
        }
    }
    val received = rebates.received(p)
    val pending = rebates.pending(p)
    Text(
        listOfNotNull(
            model.t("rebate.net", model.money(rebates.netCost(p)), model.money(p.spent), model.money(received)),
            pending.takeIf { !it.isZero }?.let { model.t("rebate.pending", model.money(it)) },
        ).joinToString(" · "),
        fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp),
    )

    Text(model.t("rebate.papers"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
    Papers(model, papers, attach = { rebates.attachToProject(p, it) }, detach = { rebates.detachFromProject(p, it) }, groupId = p.groupId)

    editing?.let { r -> RebateDialog(model, p, r) { editing = null } }
}

/** The documents linked to a project or a rebate, with buttons to add a file or take one off. */
@Composable
private fun Papers(model: BooksModel, papers: List<VaultDocument>, attach: (String) -> Unit, detach: (String) -> Unit, groupId: String) {
    for (doc in papers) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(doc.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { model.act { detach(doc.id) } }) { Text(model.t("metals.detach")) }
        }
    }
    TextButton(onClick = { attachFile(model, groupId, attach) }) { Text(model.t("rebate.attach")) }
}

/** Stores a chosen file in the vault, already filed, and links it with [link]. */
private fun attachFile(model: BooksModel, groupId: String, link: (String) -> Unit) {
    val chooser = JFileChooser().apply { dialogTitle = model.t("rebate.attach") }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
    val file = chooser.selectedFile
    model.act {
        val bytes = file.readBytes()
        val kind = FileKind.of(bytes)
        if (kind == FileKind.UNSUPPORTED) throw ValidationException("error.unsupportedFile")
        val doc = model.books.documents.import(groupId, bytes, file.name, kind.mimeType).document
        model.books.documents.setStatus(doc.id, DocumentStatus.FILED)
        link(doc.id)
    }
}

@Composable
private fun RebateDialog(model: BooksModel, p: HomeProject, existing: ProjectRebate, onClose: () -> Unit) {
    val locale = model.language.locale
    val rebates = model.books.projectRebates
    fun amt(m: ca.schippers.hfm.money.Money?) = m?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()
    var program by remember { mutableStateOf(existing.program) }
    var status by remember { mutableStateOf(existing.status) }
    var reference by remember { mutableStateOf(existing.reference.orEmpty()) }
    var amount by remember { mutableStateOf(amt(existing.amount)) }
    var applied by remember { mutableStateOf(existing.applied?.toString().orEmpty()) }
    var decided by remember { mutableStateOf(existing.decided?.toString().orEmpty()) }
    var receivedOn by remember { mutableStateOf(existing.receivedOn?.toString().orEmpty()) }
    var received by remember { mutableStateOf(amt(existing.received)) }
    var notes by remember { mutableStateOf(existing.notes.orEmpty()) }
    var asking by remember { mutableStateOf(false) }
    val papers = remember(model.revision, existing.id) { if (existing.id.isBlank()) emptyList() else rebates.documents(existing.id) }
    FormDialog(
        model.t(if (existing.id.isBlank()) "rebate.add" else "rebate.edit") + " · " + p.name, model.t("common.save"), model.t("common.cancel"),
        canSave = program.isNotBlank(), onDismiss = onClose,
        onSave = {
            val ok = model.act {
                rebates.save(
                    p,
                    existing.copy(
                        program = program, status = status, reference = reference, amount = parseAmount(amount, p.currency, locale)?.abs(),
                        applied = dayOrNull(applied), decided = dayOrNull(decided), receivedOn = dayOrNull(receivedOn),
                        received = parseAmount(received, p.currency, locale)?.abs(), notes = notes,
                    ),
                )
            }
            if (ok != null) onClose()
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextInput(model.t("rebate.program"), program) { program = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("rebate.status"), RebateStatus.entries, status, { model.t("rebateStatus.$it") }, Modifier.weight(1f)) { status = it }
                TextInput(model.t("rebate.reference"), reference, Modifier.weight(1f)) { reference = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountInput(model.t("rebate.amount"), amount, p.currency, locale, Modifier.weight(1f), model::money) { amount = it }
                DateInput(model.t("rebate.applied"), applied, Modifier.weight(1f)) { applied = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(model.t("rebate.decided"), decided, Modifier.weight(1f)) { decided = it }
                DateInput(model.t("rebate.receivedOn"), receivedOn, Modifier.weight(1f)) { receivedOn = it }
            }
            AmountInput(model.t("rebate.received"), received, p.currency, locale, Modifier.fillMaxWidth(), model::money) { received = it }
            Text(model.t("rebate.receivedHint"), style = MaterialTheme.typography.bodySmall)
            TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
            if (existing.id.isNotBlank()) {
                Text(model.t("rebate.papers"), style = MaterialTheme.typography.labelLarge)
                Papers(model, papers, attach = { rebates.attach(existing.id, it) }, detach = { rebates.detach(existing.id, it) }, groupId = p.groupId)
                TextButton(onClick = { asking = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (asking) {
        AskBeforeDeleting(model, model.t("rebate.delete", existing.program), onDismiss = { asking = false }) {
            (model.act { rebates.delete(p, existing.id) } != null).also { if (it) onClose() }
        }
    }
}
