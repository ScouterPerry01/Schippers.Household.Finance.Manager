package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Account
import ca.schippers.hfm.books.LineStatus
import ca.schippers.hfm.books.ReconciliationReport
import ca.schippers.hfm.books.Statement
import ca.schippers.hfm.books.StatementLine
import ca.schippers.hfm.books.StatementStatus
import ca.schippers.hfm.books.Transaction
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.domain.ClearedStatus
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.datetime.LocalDate

private val Positive = Color(0xFF2E7D32)

/** Section 8, steps 2 to 5: review the statement against the books until the difference is zero. */
@Composable
fun ReconcileScreen(model: BooksModel, account: Account, statementId: String) {
    val books = model.books
    val view = remember(model.revision, statementId) { books.statements.view(statementId) }
    val statement = view.statement
    val payeeNames = remember(model.revision) { books.payees.list(true).associate { it.id to it.name } }
    val open = statement.status == StatementStatus.OPEN
    var report by remember { mutableStateOf<ReconciliationReport?>(null) }
    fun payeeOf(t: Transaction) = t.payeeId?.let(payeeNames::get) ?: t.payeeText.orEmpty()

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(model.t("reconcile.title", account.name), style = MaterialTheme.typography.headlineSmall)
                Text(
                    listOfNotNull(statement.sourceName, statement.periodStart?.let { "${model.date(it)} – ${model.date(statement.periodEnd)}" } ?: model.date(statement.periodEnd))
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = { model.reconcilingStatementId = null; model.lastImport = null }) { Text(model.t("reconcile.back")) }
        }
        model.lastImport?.takeIf { it.statementId == statementId }?.let { r ->
            Text(model.t("reconcile.imported", r.created, r.matched, r.proposed, r.duplicates), style = MaterialTheme.typography.bodyMedium)
        }

        // Step 1 and 4: statement balance and the running difference (REC-05).
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (open) BalanceEditor(model, account, statement) else Text(model.t("reconcile.locked"), Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Row { Text(model.t("reconcile.cleared") + "  "); MoneyText(model, view.clearedBalance) }
                    val difference = view.difference
                    Text(
                        if (difference == null) model.t("reconcile.enterClosing") else model.t("reconcile.difference", model.money(difference)),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (difference?.isZero == true) Positive else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        LazyColumn(Modifier.weight(1f)) {
            // Step 3: lines that need a decision.
            if (view.unresolved.isNotEmpty()) {
                item { SectionTitle(model.t("reconcile.attention", view.unresolved.size)) }
                items(view.unresolved, key = { it.id }) { line -> UnresolvedLine(model, line, view.outstanding, ::payeeOf, open) }
            }
            item { SectionTitle(model.t("reconcile.lines", view.lines.size)) }
            items(view.lines.filter { it !in view.unresolved }, key = { it.id }) { line ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    LineCells(model, line)
                    Text(model.t("lineStatus.${line.status}"), Modifier.width(150.dp), style = MaterialTheme.typography.bodySmall)
                    if (open && (line.status == LineStatus.MATCHED || line.status == LineStatus.CREATED)) {
                        TextButton(onClick = { model.act { books.statements.unlink(line.id) } }) { Text(model.t("reconcile.unlink")) }
                    }
                }
            }
            // Recorded transactions not on the statement: outstanding, or ticked by hand.
            val notOnStatement = view.clearedByHand + view.outstanding
            if (notOnStatement.isNotEmpty()) {
                item { SectionTitle(model.t("reconcile.notOnStatement", view.outstanding.size)) }
                items(notOnStatement.sortedBy { it.date }, key = { "t" + it.id }) { txn ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = txn.cleared == ClearedStatus.CLEARED,
                            enabled = open,
                            onCheckedChange = { checked ->
                                model.act { books.transactions.setCleared(txn.id, if (checked) ClearedStatus.CLEARED else ClearedStatus.UNCLEARED) }
                            },
                        )
                        Text(model.date(txn.date), Modifier.width(100.dp))
                        Text(payeeOf(txn), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MoneyText(model, txn.amount, modifier = Modifier.width(130.dp))
                    }
                }
            }
        }

        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (open && !view.canFinish) {
                Text(
                    when {
                        view.difference == null -> model.t("reconcile.enterClosing")
                        view.unresolved.isNotEmpty() -> model.t("error.unresolvedLines")
                        else -> model.t("reconcile.notZero")
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (open) {
                Button(enabled = view.canFinish, onClick = { report = model.act { books.statements.finish(statementId) } }) {
                    Text(model.t("reconcile.finish"))
                }
            }
        }
    }

    report?.let { r ->
        AlertDialog(
            onDismissRequest = { report = null; model.reconcilingStatementId = null },
            title = { Text(model.t("reconcile.done.title")) },
            text = { Text(model.t("reconcile.done.body", r.cleared.size, r.outstanding.size, model.date(LocalDate.parse(r.periodEnd)))) },
            confirmButton = { TextButton(onClick = { report = null; model.reconcilingStatementId = null; model.lastImport = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun RowScope.LineCells(model: BooksModel, line: StatementLine) {
    Text(model.date(line.date), Modifier.width(100.dp))
    Text(listOfNotNull(line.payee, line.checkNumber?.let { "#$it" }).joinToString(" "), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    MoneyText(model, line.amount, modifier = Modifier.width(130.dp))
}

/** A proposed or unmatched line, with what can be done about it. */
@Composable
private fun UnresolvedLine(model: BooksModel, line: StatementLine, outstanding: List<Transaction>, payeeOf: (Transaction) -> String, open: Boolean) {
    val books = model.books
    val proposed = line.transactionId?.let { id -> runCatching { books.transactions.get(id) }.getOrNull() }
    val candidates = outstanding.filter { it.amount == line.amount || books.statements.isFxMatch(it, line.amount) }
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { LineCells(model, line) }
            if (proposed != null) {
                Text(model.t("reconcile.proposed", model.date(proposed.date), payeeOf(proposed)), style = MaterialTheme.typography.bodySmall)
                // REC-04: a purchase in a foreign currency costs what the statement says; the difference is the fee.
                val original = proposed.originalAmount
                if (original != null && proposed.amount != line.amount) {
                    Text(
                        model.t("reconcile.fxFee", model.money(original), model.money(proposed.amount), model.money(line.amount - proposed.amount)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            if (open) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (line.status == LineStatus.PROPOSED) {
                        Button(onClick = { model.act { books.statements.confirm(line.id) } }) { Text(model.t("reconcile.confirm")) }
                    }
                    OutlinedButton(onClick = { model.act { books.statements.createTransaction(line.id) } }) { Text(model.t("reconcile.create")) }
                    if (line.status == LineStatus.UNMATCHED && candidates.isNotEmpty()) {
                        Picker(
                            model.t("reconcile.linkTo"), candidates, null, { "${model.date(it.date)} · ${payeeOf(it)}" }, Modifier.width(320.dp),
                        ) { txn -> model.act { books.statements.link(line.id, txn.id) } }
                    }
                    TextButton(onClick = { model.act { books.statements.ignore(line.id) } }) { Text(model.t("reconcile.ignore")) }
                }
            }
        }
    }
}

/** Period end and closing balance as printed on the statement (step 1). */
@Composable
private fun RowScope.BalanceEditor(model: BooksModel, account: Account, statement: Statement) {
    val locale = model.language.locale
    var end by remember(statement) { mutableStateOf(statement.periodEnd.toString()) }
    var closing by remember(statement) { mutableStateOf(statement.closingBalance?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        DateInput(model.t("reconcile.periodEnd"), end, Modifier.width(170.dp)) { end = it }
        AmountInput(model.t("reconcile.closing"), closing, account.currency, locale, Modifier.width(200.dp), model::money) { closing = it }
        OutlinedButton(onClick = {
            model.act {
                val date = runCatching { LocalDate.parse(end.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }
                val amount: Money? = parseAmount(closing, account.currency, locale)
                model.books.statements.updateBalances(statement.id, statement.periodStart, date, statement.openingBalance, amount)
            }
        }) { Text(model.t("reconcile.apply")) }
    }
}

/** Statement history for an account, with reports and undo of the latest reconciliation (REC-06, REC-07). */
@Composable
fun StatementsDialog(model: BooksModel, account: Account, onClose: () -> Unit) {
    val books = model.books
    val statements = remember(model.revision) { books.statements.statements(account.id) }
    var undoing by remember { mutableStateOf<Statement?>(null) }
    var reason by remember { mutableStateOf("") }
    var newStatement by remember { mutableStateOf(false) }
    val latestReconciled = statements.filter { it.status == StatementStatus.RECONCILED }.maxByOrNull { it.periodEnd }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(model.t("statements.title", account.name)) },
        text = {
            Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (statements.isEmpty()) Text(model.t("statements.none"))
                for (s in statements) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${model.date(s.periodEnd)} · ${s.closingBalance?.let(model::money) ?: "?"}")
                            Text(
                                listOfNotNull(model.t("statementStatus.${s.status}"), s.sourceName, s.undoReason).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (s.status == StatementStatus.OPEN) {
                            TextButton(onClick = { model.reconcilingStatementId = s.id; onClose() }) { Text(model.t("statements.open")) }
                        }
                        if (s.id == latestReconciled?.id) {
                            TextButton(onClick = { undoing = s }) { Text(model.t("statements.undo")) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { newStatement = true }) { Text(model.t("statements.manual")) } },
        dismissButton = { TextButton(onClick = onClose) { Text(model.t("common.close")) } },
    )

    undoing?.let { s ->
        FormDialog(
            model.t("statements.undo.title"), model.t("statements.undo"), model.t("common.cancel"),
            canSave = reason.isNotBlank(),
            onDismiss = { undoing = null },
            onSave = {
                if (model.act { books.statements.undo(s.id, reason) } != null) {
                    undoing = null
                    reason = ""
                }
            },
        ) {
            Text(model.t("statements.undo.body"))
            TextInput(model.t("statements.undo.reason"), reason) { reason = it }
        }
    }
    if (newStatement) ManualStatementDialog(model, account, { newStatement = false }) { id -> model.reconcilingStatementId = id; onClose() }
}

/** A statement typed in from paper, for reconciling without a file. */
@Composable
private fun ManualStatementDialog(model: BooksModel, account: Account, onClose: () -> Unit, onCreated: (String) -> Unit) {
    val locale = model.language.locale
    var end by remember { mutableStateOf(today().toString()) }
    var closing by remember { mutableStateOf("") }
    FormDialog(model.t("statements.manual"), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val created = model.act {
            val date = runCatching { LocalDate.parse(end.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }
            val amount = parseAmount(closing, account.currency, locale) ?: throw ValidationException("error.closingRequired")
            model.books.statements.createManual(account.id, date, amount)
        }
        if (created != null) {
            onClose()
            onCreated(created.id)
        }
    }) {
        DateInput(model.t("reconcile.periodEnd"), end, Modifier.fillMaxWidth()) { end = it }
        AmountInput(model.t("reconcile.closing"), closing, account.currency, locale, Modifier.fillMaxWidth(), model::money) { closing = it }
    }
}
