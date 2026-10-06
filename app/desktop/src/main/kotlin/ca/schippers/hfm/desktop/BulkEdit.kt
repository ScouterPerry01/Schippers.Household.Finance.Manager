package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Account
import ca.schippers.hfm.books.BulkResult
import ca.schippers.hfm.books.Category
import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.AccountStatus
import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.importers.ExportAccountKind
import ca.schippers.hfm.importers.TransactionExporter
import java.io.File
import javax.swing.JFileChooser

/** TX-07 and EXP-02: what can be done with the transactions chosen in a register. */
private enum class BulkAction { CATEGORIZE, TAG, MOVE, EXPORT }

/** EXP-02: the file formats chosen transactions can be saved in. */
private enum class TxnExportFormat(val extension: String) { CSV("csv"), QIF("qif"), OFX("ofx") }

/**
 * TX-07, EXP-02: the bar shown while transactions are being chosen in a register: how many are
 * chosen, select all shown or none, and the actions on them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BulkBar(
    model: BooksModel,
    account: Account,
    chosen: List<String>,
    shownIds: List<String>,
    categoryTree: List<Pair<Category, Int>>,
    onChoose: (List<String>) -> Unit,
    onDone: () -> Unit,
) {
    var action by remember { mutableStateOf<BulkAction?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        Text(model.t("bulk.chosen", chosen.size), style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = { onChoose(shownIds) }) { Text(model.t("bulk.all")) }
        TextButton(enabled = chosen.isNotEmpty(), onClick = { onChoose(emptyList()) }) { Text(model.t("bulk.none")) }
        OutlinedButton(enabled = chosen.isNotEmpty(), onClick = { action = BulkAction.CATEGORIZE }) { Text(model.t("bulk.categorize")) }
        OutlinedButton(enabled = chosen.isNotEmpty(), onClick = { action = BulkAction.TAG }) { Text(model.t("bulk.tag")) }
        OutlinedButton(enabled = chosen.isNotEmpty(), onClick = { action = BulkAction.MOVE }) { Text(model.t("bulk.move")) }
        OutlinedButton(enabled = chosen.isNotEmpty(), onClick = { action = BulkAction.EXPORT }) { Text(model.t("bulk.export")) }
        TextButton(onClick = onDone) { Text(model.t("bulk.done")) }
    }
    message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }

    fun report(result: BulkResult?) {
        if (result == null) return
        message = if (result.skipped == 0) model.t("bulk.changed", result.changed) else model.t("bulk.changedSkipped", result.changed, result.skipped)
        action = null
    }
    val books = model.books
    when (action) {
        BulkAction.CATEGORIZE -> {
            var category by remember { mutableStateOf<Category?>(null) }
            FormDialog(model.t("bulk.categorizeTitle", chosen.size), model.t("common.save"), model.t("common.cancel"), onDismiss = { action = null }, onSave = {
                report(model.act(retryConfirmed = { books.transactions.bulkCategorize(chosen, category?.id, confirmReconciled = true) }) { books.transactions.bulkCategorize(chosen, category?.id) })
            }) {
                Text(model.t("bulk.categorizeHint"), style = MaterialTheme.typography.bodySmall)
                val options = listOf<Pair<Category, Int>?>(null) + categoryTree.filter { !it.first.archived }
                Picker(
                    model.t("register.category"), options, options.firstOrNull { it?.first?.id == category?.id },
                    { it?.first?.name(model.language) ?: model.t("register.uncategorized") }, indent = { it?.second ?: 0 },
                ) { category = it?.first }
            }
        }
        BulkAction.TAG -> {
            var tag by remember { mutableStateOf("") }
            val names = remember(model.revision) { books.tags().map { it.name }.sorted() }
            FormDialog(model.t("bulk.tagTitle", chosen.size), model.t("common.save"), model.t("common.cancel"), canSave = tag.isNotBlank(), onDismiss = { action = null }, onSave = {
                report(model.act(retryConfirmed = { books.transactions.bulkAddTag(chosen, tag, confirmReconciled = true) }) { books.transactions.bulkAddTag(chosen, tag) })
            }) {
                SuggestInput(model.t("bulk.tagName"), tag, names, onChange = { tag = it }, onPick = { tag = it })
            }
        }
        BulkAction.MOVE -> {
            val group = account.groupId
            val targets = remember(model.revision, account.id) {
                books.accounts.list().map { it.account }.filter {
                    it.id != account.id && it.groupId == group && it.currency == account.currency && it.status != AccountStatus.CLOSED &&
                        it.type.kind != AccountKind.INVESTMENT
                }
            }
            var target by remember { mutableStateOf<Account?>(null) }
            FormDialog(model.t("bulk.moveTitle", chosen.size), model.t("bulk.moveButton"), model.t("common.cancel"), canSave = target != null, onDismiss = { action = null }, onSave = {
                val to = target!!.id
                report(model.act(retryConfirmed = { books.transactions.bulkMove(chosen, to, confirmReconciled = true) }) { books.transactions.bulkMove(chosen, to) })
            }) {
                Text(model.t("bulk.moveHint"), style = MaterialTheme.typography.bodySmall)
                if (targets.isEmpty()) Text(model.t("bulk.moveNone"), color = MaterialTheme.colorScheme.error)
                else Picker(model.t("bulk.moveTo"), targets, target, { it.name }) { target = it }
            }
        }
        BulkAction.EXPORT -> {
            var format by remember { mutableStateOf(TxnExportFormat.CSV) }
            FormDialog(model.t("bulk.exportTitle", chosen.size), model.t("bulk.exportButton"), model.t("common.cancel"), onDismiss = { action = null }, onSave = {
                val name = account.name.replace(Regex("""[\\/:*?"<>|]"""), "-") + "." + format.extension
                val chooser = JFileChooser().apply { dialogTitle = model.t("bulk.exportTitle", chosen.size); selectedFile = File(name) }
                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val file = chooser.selectedFile.let { if (it.extension.equals(format.extension, true)) it else File(it.path + "." + format.extension) }
                    val written = model.act {
                        writeExport(model, account, chosen, format, file)
                        books.session.audit("EXPORT", "txn", account.id, "${chosen.size} ${format.name}")
                    }
                    if (written != null) {
                        message = model.t("bulk.exported", chosen.size, file.name)
                        action = null
                    }
                }
            }) {
                Picker(model.t("bulk.format"), TxnExportFormat.entries, format, { model.t("bulk.format.${it.name}") }) { format = it }
                Text(model.t("bulk.format.${format.name}.hint"), style = MaterialTheme.typography.bodySmall)
                Text(model.t("bulk.exportPrivacy"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        null -> Unit
    }
}

/** EXP-02: writes the chosen transactions of [account] to [file]; CSV follows the user's language as report CSVs do. */
private fun writeExport(model: BooksModel, account: Account, ids: List<String>, format: TxnExportFormat, file: File) {
    val french = model.language == Language.FRENCH
    val lines = model.books.transactions.exportLines(ids, french)
    val kind = when {
        account.type == AccountType.CREDIT_CARD -> ExportAccountKind.CREDIT_CARD
        account.type == AccountType.CASH -> ExportAccountKind.CASH
        account.type.kind == AccountKind.CREDIT || account.type.kind == AccountKind.LOAN -> ExportAccountKind.LIABILITY
        account.type.kind == AccountKind.ASSET -> ExportAccountKind.ASSET
        else -> ExportAccountKind.BANK
    }
    val text = when (format) {
        TxnExportFormat.CSV -> "﻿" + TransactionExporter.csv(
            lines, account.currency.code,
            listOf("register.date", "register.payee", "register.category", "register.memo", "register.amount", "bulk.csv.currency", "bulk.csv.cleared", "bulk.csv.tags").map { model.t(it) },
            separator = if (french) ';' else ',', decimalComma = french,
        )
        TxnExportFormat.QIF -> TransactionExporter.qif(lines, account.name, kind)
        TxnExportFormat.OFX -> {
            val balance = model.books.accounts.list(includeClosed = true).first { it.account.id == account.id }.balanceToday
            TransactionExporter.ofx(lines, account.id, account.currency.code, kind, balance.toBigDecimal(), today())
        }
    }
    file.writeText(text, Charsets.UTF_8)
}
