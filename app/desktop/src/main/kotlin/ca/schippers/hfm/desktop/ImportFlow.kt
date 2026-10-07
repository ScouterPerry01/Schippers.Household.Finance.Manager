package ca.schippers.hfm.desktop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Account
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.importers.CsvImporter
import ca.schippers.hfm.importers.CsvMapping
import ca.schippers.hfm.importers.CsvPreview
import ca.schippers.hfm.importers.ImportException
import ca.schippers.hfm.importers.ImportOptions
import ca.schippers.hfm.importers.ImportedStatement
import ca.schippers.hfm.importers.Importers
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** An import waiting for the user: a CSV layout to confirm, or one of several OFX accounts to choose. */
sealed interface PendingImport {
    val account: Account
    val fileName: String
    val bytes: ByteArray

    class Csv(override val account: Account, override val fileName: String, override val bytes: ByteArray, val preview: CsvPreview, val saved: CsvMapping?) : PendingImport
    class ChooseStatement(override val account: Account, override val fileName: String, override val bytes: ByteArray, val statements: List<ImportedStatement>) : PendingImport
}

/** Step 1 of section 8: choose a statement file and read it. Returns a pending step, or null when done. */
fun startImport(model: BooksModel, account: Account): PendingImport? {
    val chooser = JFileChooser().apply {
        dialogTitle = model.t("import.choose")
        fileFilter = FileNameExtensionFilter(model.t("import.fileTypes"), "ofx", "qfx", "qbo", "csv", "txt")
    }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return null
    val file: File = chooser.selectedFile
    val bytes = file.readBytes()
    val importer = Importers.forFile(file.name, bytes.copyOf(minOf(bytes.size, 2048)))
    return try {
        when (importer) {
            is CsvImporter -> {
                val saved = model.books.setting(mappingKey(account))?.let(CsvMapping::decode)
                PendingImport.Csv(account, file.name, bytes, importer.preview(bytes), saved)
            }
            null -> throw ValidationException("error.importFormat")
            else -> {
                val statements = importer.read(bytes.inputStream(), ImportOptions(defaultCurrency = account.currency))
                val match = statements.singleOrNull() ?: statements.singleOrNull { s -> matchesAccount(s, account) }
                if (match != null) {
                    finishImport(model, account, file.name, bytes, match)
                    null
                } else {
                    PendingImport.ChooseStatement(account, file.name, bytes, statements)
                }
            }
        }
    } catch (e: ImportException) {
        model.error = model.t("error.importFailed", e.message.orEmpty())
        null
    } catch (e: Exception) {
        model.error = model.describe(e)
        null
    }
}

/** Saved CSV layouts are kept per institution, or per account when it has none (REC-01). */
private fun mappingKey(account: Account) = "csv.mapping." + (account.institutionId ?: account.id)

/** The OFX account number ends with the same digits as the account's masked number. */
private fun matchesAccount(statement: ImportedStatement, account: Account): Boolean {
    val last4 = account.numberMasked?.filter(Char::isDigit)?.takeLast(4) ?: return false
    return statement.accountNumberHint?.filter(Char::isDigit)?.endsWith(last4) == true
}

private fun finishImport(model: BooksModel, account: Account, fileName: String, bytes: ByteArray, statement: ImportedStatement) {
    val result = model.act { model.books.statements.import(account.id, statement, fileName, bytes) } ?: return
    model.lastImport = result
    model.reconcilingStatementId = result.statementId
}

@Composable
fun PendingImportDialog(model: BooksModel, pending: PendingImport, onClose: () -> Unit) {
    when (pending) {
        is PendingImport.Csv -> CsvMappingDialog(model, pending, onClose)
        is PendingImport.ChooseStatement -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text(model.t("import.chooseAccount")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (s in pending.statements) {
                        TextButton(onClick = {
                            finishImport(model, pending.account, pending.fileName, pending.bytes, s)
                            onClose()
                        }) {
                            Text(model.t("import.statementChoice", s.accountNumberHint ?: "?", s.currency.code, s.lines.size))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onClose) { Text(model.t("common.cancel")) } },
        )
    }
}

/** Shows the first rows of the file and lets the user say which column is which (REC-01). */
@Composable
private fun CsvMappingDialog(model: BooksModel, pending: PendingImport.Csv, onClose: () -> Unit) {
    var mapping by remember { mutableStateOf(pending.saved ?: pending.preview.guess) }
    val rows = pending.preview.rows
    val width = rows.maxOfOrNull { it.size } ?: 0
    val header = if (mapping.hasHeader) rows.firstOrNull().orEmpty() else emptyList()
    val columns: List<Int?> = listOf(null) + (0 until width).toList()
    fun columnName(c: Int?): String = when (c) {
        null -> model.t("common.none")
        else -> model.t("import.column", c + 1) + (header.getOrNull(c)?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
    }
    var amountMode by remember { mutableStateOf(if (mapping.amountColumn != null || mapping.debitColumn == null && mapping.creditColumn == null) "single" else "split") }

    FormDialog(
        title = model.t("import.csvTitle", pending.fileName),
        saveLabel = model.t("import.run"),
        cancelLabel = model.t("common.cancel"),
        canSave = mapping.isValid(),
        onDismiss = onClose,
        walkId = "import.csv",
        onSave = {
            val final = if (amountMode == "single") mapping.copy(debitColumn = null, creditColumn = null) else mapping.copy(amountColumn = null)
            try {
                val statement = CsvImporter().read(pending.bytes.inputStream(), ImportOptions(pending.account.currency, final.toMap())).single()
                model.books.putSetting(mappingKey(pending.account), final.encode())
                finishImport(model, pending.account, pending.fileName, pending.bytes, statement)
                onClose()
            } catch (e: ImportException) {
                model.error = model.t("error.importFailed", e.message.orEmpty())
            }
        },
    ) {
        // Preview of the first rows.
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            for ((i, row) in rows.take(6).withIndex()) {
                Row {
                    for (cell in row) {
                        Text(
                            cell, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.dp).padding(end = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (i == 0 && mapping.hasHeader) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        LabeledCheckbox(model.t("import.hasHeader"), mapping.hasHeader) { mapping = mapping.copy(hasHeader = it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker(model.t("import.dateColumn"), columns.drop(1), mapping.dateColumn, ::columnName, Modifier.weight(1f)) { mapping = mapping.copy(dateColumn = it!!) }
            Picker(model.t("import.dateFormat"), CsvImporter.DATE_FORMATS, mapping.dateFormat, { it }, Modifier.weight(1f)) { mapping = mapping.copy(dateFormat = it) }
        }
        Picker(model.t("import.amountMode"), listOf("single", "split"), amountMode, { model.t("import.amountMode.$it") }) { amountMode = it }
        if (amountMode == "single") {
            Picker(model.t("import.amountColumn"), columns, mapping.amountColumn, ::columnName) { mapping = mapping.copy(amountColumn = it) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("import.debitColumn"), columns, mapping.debitColumn, ::columnName, Modifier.weight(1f)) { mapping = mapping.copy(debitColumn = it, amountColumn = null) }
                Picker(model.t("import.creditColumn"), columns, mapping.creditColumn, ::columnName, Modifier.weight(1f)) { mapping = mapping.copy(creditColumn = it, amountColumn = null) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker(model.t("import.payeeColumn"), columns, mapping.payeeColumn, ::columnName, Modifier.weight(1f)) { mapping = mapping.copy(payeeColumn = it) }
            Picker(model.t("import.memoColumn"), columns, mapping.memoColumn, ::columnName, Modifier.weight(1f)) { mapping = mapping.copy(memoColumn = it) }
        }
        Picker(model.t("import.balanceColumn"), columns, mapping.balanceColumn, ::columnName) { mapping = mapping.copy(balanceColumn = it) }
        LabeledCheckbox(model.t("import.decimalComma"), mapping.decimalComma) { mapping = mapping.copy(decimalComma = it) }
        LabeledCheckbox(model.t("import.negate"), mapping.negate) { mapping = mapping.copy(negate = it) }
    }
}
