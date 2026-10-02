package ca.schippers.hfm.desktop

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Account
import ca.schippers.hfm.books.QifAccountPlan
import ca.schippers.hfm.books.QifImportResult
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.importers.DateOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

fun chooseQif(model: BooksModel): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = model.t("quicken.import")
        fileFilter = FileNameExtensionFilter(model.t("quicken.fileType"), "qif")
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

/**
 * OTH-05: shows what a QIF file contains, lets the user say where each account goes (a new
 * account, an existing one, or nowhere) and which date order the file uses, then imports it.
 */
@Composable
fun QuickenImportDialog(model: BooksModel, file: File, onClose: () -> Unit) {
    val books = model.books
    val scope = rememberCoroutineScope()
    val bytes = remember(file) { runCatching { file.readBytes() }.getOrNull() }
    val preview = remember(file) { bytes?.let { runCatching { books.quicken.preview(it) }.getOrNull() } }
    val existing = remember { books.accounts.list().map { it.account } }
    var plans by remember { mutableStateOf(preview?.plans.orEmpty()) }
    var order by remember { mutableStateOf(preview?.dateOrder) }
    var groupId by remember { mutableStateOf(model.defaultDocumentGroup()) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<QifImportResult?>(null) }

    WideDialog(model.t("quicken.title", file.name), model.t("common.close"), onClose) {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val done = result
            when {
                preview == null -> Text(model.t("quicken.unreadable"), color = MaterialTheme.colorScheme.error)
                done != null -> ResultView(model, done)
                else -> {
                    Text(
                        model.t("quicken.summary", preview.transactions, preview.from?.let(model::date) ?: "—", preview.to?.let(model::date) ?: "—") +
                            if (preview.investmentActions > 0) " " + model.t("quicken.investments", preview.investmentActions) else "",
                    )
                    Text(model.t("quicken.explain"), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Picker(
                            model.t("quicken.dateOrder"), DateOrder.entries, order, { model.t("dateOrder.$it") }, Modifier.width(320.dp),
                        ) { order = it }
                        if (preview.dateOrder == null) Text(model.t("quicken.dateOrderAsk"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    GroupPicker(model, groupId, enabled = true, modifier = Modifier.width(420.dp)) { groupId = it.id }
                    HorizontalDivider()
                    for ((i, plan) in plans.withIndex()) {
                        PlanRow(model, plan, existing) { changed -> plans = plans.toMutableList().also { it[i] = changed } }
                    }
                    preview.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Button(enabled = !running && order != null && groupId != null && plans.any { it.include }, onClick = {
                        running = true
                        scope.launch {
                            val outcome = withContext(Dispatchers.IO) {
                                runCatching { books.quicken.import(bytes!!, file.name, groupId!!, plans, order!!, today()) }
                            }
                            running = false
                            outcome.onSuccess { result = it; model.changed() }.onFailure { model.error = model.describe(it) }
                        }
                    }) { Text(model.t(if (running) "quicken.importing" else "quicken.go")) }
                }
            }
        }
    }
}

@Composable
private fun PlanRow(model: BooksModel, plan: QifAccountPlan, existing: List<Account>, onChange: (QifAccountPlan) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(plan.include, onCheckedChange = { onChange(plan.copy(include = it)) })
        Column(Modifier.width(230.dp)) {
            Text(plan.qifName, fontWeight = FontWeight.Medium)
            Text(model.t("quicken.lines", plan.transactions), style = MaterialTheme.typography.bodySmall)
        }
        val choices = listOf<Account?>(null) + existing.filter { it.currency == plan.currency }
        Picker(model.t("quicken.into"), choices, existing.firstOrNull { it.id == plan.targetAccountId }, { it?.name ?: model.t("quicken.newAccount") }, Modifier.weight(1f), enabled = plan.include) {
            onChange(plan.copy(targetAccountId = it?.id))
        }
        if (plan.targetAccountId == null) {
            Picker(model.t("account.type"), AccountType.entries, plan.newType, { model.t("accountType.$it") }, Modifier.weight(1f), enabled = plan.include) {
                onChange(plan.copy(newType = it))
            }
        }
    }
}

@Composable
private fun ResultView(model: BooksModel, r: QifImportResult) {
    Text(model.t("quicken.done"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Text(model.t("quicken.result", r.accountsCreated, r.transactions, r.transfers, r.categoriesCreated))
    if (r.investmentActions > 0) Text(model.t("quicken.investmentsDone", r.investmentActions))
    if (r.alreadyThere > 0) Text(model.t("quicken.alreadyThere", r.alreadyThere))
    if (r.warnings.isNotEmpty()) {
        Text(model.t("quicken.notes"), style = MaterialTheme.typography.labelLarge)
        r.warnings.take(30).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
