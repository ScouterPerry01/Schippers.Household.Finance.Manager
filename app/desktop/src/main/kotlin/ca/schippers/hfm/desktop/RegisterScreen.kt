package ca.schippers.hfm.desktop

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Account
import ca.schippers.hfm.books.AccountSummary
import ca.schippers.hfm.books.Category
import ca.schippers.hfm.books.CreditCardTerms
import ca.schippers.hfm.books.ReconciledChangeException
import ca.schippers.hfm.books.SplitDraft
import ca.schippers.hfm.books.StatementStatus
import ca.schippers.hfm.books.Transaction
import ca.schippers.hfm.books.TransactionDraft
import ca.schippers.hfm.books.TransferDraft
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.domain.AccountStatus
import ca.schippers.hfm.domain.ClearedStatus
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat
import ca.schippers.hfm.money.sum
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/** What the category column of an entry holds. */
sealed interface CategoryChoice {
    data object Uncategorized : CategoryChoice
    data class Of(val category: Category, val depth: Int) : CategoryChoice
    data class TransferWith(val account: Account) : CategoryChoice
}

/** The entry form's fields (TX-01, MAN-04). Amounts are typed as a payment or a deposit, as on a statement. */
class EntryState {
    var editing by mutableStateOf<Transaction?>(null)
    var date by mutableStateOf(today().toString())
    var payee by mutableStateOf("")
    var choice by mutableStateOf<CategoryChoice?>(null)
    var splits by mutableStateOf<List<SplitDraft>?>(null)
    var memo by mutableStateOf("")
    var payment by mutableStateOf("")
    var deposit by mutableStateOf("")
    /** For a transfer to an account in another currency: the amount on the other side (FX-04). */
    var otherAmount by mutableStateOf("")
    /** PET-05, VEH-09: the person or pet, and the vehicle, the transaction was for. */
    var forId by mutableStateOf<String?>(null)
    var assetId by mutableStateOf<String?>(null)

    fun clear(keepDate: Boolean = true) {
        editing = null
        if (!keepDate) date = today().toString()
        payee = ""
        choice = null
        splits = null
        memo = ""
        payment = ""
        deposit = ""
        otherAmount = ""
        forId = null
        assetId = null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegisterScreen(model: BooksModel, summary: AccountSummary) {
    val books = model.books
    val account = summary.account
    val locale = model.language.locale
    // NFR-02: open with the most recent transactions; earlier ones load on request.
    var limit by remember(account.id) { mutableStateOf(PAGE) }
    val rows = remember(model.revision, account.id, limit) { books.transactions.register(account.id, limit) }
    val total = remember(model.revision, account.id) { books.transactions.count(account.id) }
    val categoryTree = remember(model.revision) { books.categories.tree() }
    val categories = remember(categoryTree) { categoryTree.associate { it.first.id to it.first } }
    val otherAccounts = remember(model.revision, account.id) { books.accounts.list().map { it.account }.filter { it.id != account.id } }
    val allAccounts = remember(model.revision) { books.accounts.list(includeClosed = true).associate { it.account.id to it.account } }
    val payees = remember(model.revision) { books.payees.list() }
    val whoList = remember(model.revision) { model.peopleAndPets() }
    val vehicleList = remember(model.revision) { model.vehicleChoices() }
    val payeeNames = remember(payees) { payees.associate { it.id to it.name } }
    val entry = remember(account.id) { EntryState() }
    var editingAccount by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf(false) }
    var revealing by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var splitting by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }
    var showStatements by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Jump to the newest entry when the account opens or a transaction is added, not after "show earlier".
    LaunchedEffect(account.id, total) {
        val items = rows.size + if (rows.size < total) 1 else 0
        if (items > 0) listState.scrollToItem(items - 1)
    }

    val choices: List<CategoryChoice> = remember(categoryTree, otherAccounts) {
        listOf(CategoryChoice.Uncategorized) +
            categoryTree.map { (c, depth) -> CategoryChoice.Of(c, depth) } +
            otherAccounts.map { CategoryChoice.TransferWith(it) }
    }
    fun choiceLabel(choice: CategoryChoice): String = when (choice) {
        CategoryChoice.Uncategorized -> model.t("register.uncategorized")
        is CategoryChoice.Of -> choice.category.name(model.language)
        is CategoryChoice.TransferWith -> model.t("register.transfer", choice.account.name)
    }

    fun load(txn: Transaction) {
        entry.editing = txn
        entry.date = txn.date.toString()
        entry.payee = txn.payeeId?.let(payeeNames::get) ?: txn.payeeText.orEmpty()
        entry.memo = txn.memo.orEmpty()
        entry.forId = txn.memberId
        entry.assetId = txn.assetId
        val magnitude = MoneyFormat.formatAmount(txn.amount.abs(), locale)
        entry.payment = if (txn.amount.isNegative) magnitude else ""
        entry.deposit = if (txn.amount.isNegative) "" else magnitude
        entry.splits = null
        entry.otherAmount = ""
        val transfer = txn.transfer
        entry.choice = when {
            transfer != null -> allAccounts[transfer.otherAccountId]?.let { CategoryChoice.TransferWith(it) }.also {
                txn.originalAmount?.let { other -> entry.otherAmount = MoneyFormat.formatAmount(other.abs(), locale) }
            }
            txn.isSplit -> {
                entry.splits = txn.splits.map { SplitDraft(it.categoryId, it.amount, it.memo, it.memberId, it.taxFlag) }
                null
            }
            else -> txn.splits.firstOrNull()?.categoryId?.let { id ->
                categoryTree.firstOrNull { it.first.id == id }?.let { CategoryChoice.Of(it.first, it.second) }
            } ?: CategoryChoice.Uncategorized
        }
    }

    fun save() {
        val result = runCatching { buildSave(model, account, entry) }
        result.exceptionOrNull()?.let { model.error = model.describe(it); return }
        val perform = result.getOrThrow()
        if (model.act(retryConfirmed = { perform(true); entry.clear() }) { perform(false) } != null) entry.clear()
    }

    // A transaction chosen in search: load enough history to include it, then open it in the form.
    LaunchedEffect(model.focusTransactionId, rows) {
        val id = model.focusTransactionId ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it.transaction.id == id }
        when {
            index >= 0 -> {
                load(rows[index].transaction)
                listState.scrollToItem(index + if (rows.size < total) 1 else 0)
                model.focusTransactionId = null
            }
            rows.size < total -> limit = total.toInt()
            else -> model.focusTransactionId = null
        }
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        // Header: account, balances and actions.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(account.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    listOfNotNull(model.t("accountType.${account.type}"), account.currency.code, account.numberMasked).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row { Text(model.t("account.balance") + "  "); MoneyText(model, summary.balance, bold = true) }
                Row { Text(model.t("account.cleared") + "  ", style = MaterialTheme.typography.bodySmall); MoneyText(model, summary.clearedBalance) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Button(onClick = { pendingImport = startImport(model, account) }) { Text(model.t("import.button")) }
            OutlinedButton(onClick = {
                val open = books.statements.statements(account.id).firstOrNull { it.status == StatementStatus.OPEN }
                if (open != null) model.reconcilingStatementId = open.id else showStatements = true
            }) { Text(model.t("reconcile.button")) }
            OutlinedButton(onClick = { showStatements = true }) { Text(model.t("statements.button")) }
            OutlinedButton(onClick = { editingAccount = true }) { Text(model.t("account.edit")) }
            if (account.numberMasked != null) OutlinedButton(onClick = { revealing = true }) { Text(model.t("account.show")) }
            if (account.type.kind == AccountKind.CREDIT) OutlinedButton(onClick = { editingCard = true }) { Text(model.t("account.cardDetails")) }
            if (account.status != AccountStatus.CLOSED) {
                OutlinedButton(onClick = { confirmClose = true }) { Text(model.t("account.close")) }
            }
        }
        HorizontalDivider()

        // Column headings.
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp)) {
            Heading(model.t("register.date"), Modifier.width(100.dp))
            Heading(model.t("register.payee"), Modifier.weight(2f))
            Heading(model.t("register.category"), Modifier.weight(2f))
            Heading(model.t("register.memo"), Modifier.weight(2f))
            Heading(model.t("register.amount"), Modifier.width(120.dp), TextAlign.End)
            Heading("✓", Modifier.width(36.dp), TextAlign.Center)
            Heading(model.t("register.balance"), Modifier.width(130.dp), TextAlign.End)
        }
        HorizontalDivider()

        LazyColumn(Modifier.weight(1f), state = listState) {
            if (rows.isEmpty()) item { Text(model.t("register.empty"), Modifier.padding(16.dp)) }
            if (rows.size < total) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                        Text(model.t("register.showing", rows.size, total), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { limit += 2 * PAGE }) { Text(model.t("register.showEarlier")) }
                    }
                }
            }
            items(rows, key = { it.transaction.id }) { row ->
                val txn = row.transaction
                val selected = entry.editing?.id == txn.id
                val category = when {
                    txn.transfer != null -> model.t("register.transfer", allAccounts[txn.transfer!!.otherAccountId]?.name ?: "?")
                    txn.isSplit -> model.t("register.split")
                    else -> txn.splits.firstOrNull()?.categoryId?.let { categories[it]?.name(model.language) } ?: model.t("register.uncategorized")
                }
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                        .clickable { load(txn) }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cell(model.date(txn.date), Modifier.width(100.dp))
                    Cell(txn.payeeId?.let(payeeNames::get) ?: txn.payeeText.orEmpty(), Modifier.weight(2f))
                    Cell(category, Modifier.weight(2f))
                    Cell(txn.memo.orEmpty(), Modifier.weight(2f))
                    MoneyText(model, txn.amount, modifier = Modifier.width(120.dp), textAlign = TextAlign.End)
                    Text(
                        when (txn.cleared) { ClearedStatus.UNCLEARED -> "·"; ClearedStatus.CLEARED -> "c"; ClearedStatus.RECONCILED -> "R" },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(36.dp).clickable {
                            val next = if (txn.cleared == ClearedStatus.UNCLEARED) ClearedStatus.CLEARED else ClearedStatus.UNCLEARED
                            model.act(retryConfirmed = { books.transactions.setCleared(txn.id, next, confirmReconciled = true) }) {
                                books.transactions.setCleared(txn.id, next)
                            }
                        },
                    )
                    MoneyText(model, row.runningBalance, modifier = Modifier.width(130.dp).padding(start = 8.dp), textAlign = TextAlign.End)
                }
            }
        }
        HorizontalDivider()

        // Entry form: Enter saves, Escape clears (MAN-04).
        Card(
            Modifier.fillMaxWidth().padding(top = 8.dp).onPreviewKeyEvent { e ->
                when {
                    e.type != KeyEventType.KeyDown -> false
                    e.key == Key.Enter || e.key == Key.NumPadEnter -> { save(); true }
                    e.key == Key.Escape -> { entry.clear(); true }
                    else -> false
                }
            },
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    model.t(if (entry.editing == null) "register.new" else "register.editing"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    DateInput(model.t("register.date"), entry.date, Modifier.width(160.dp)) { entry.date = it }
                    SuggestInput(
                        model.t("register.payee"), entry.payee, payees.map { it.name }, Modifier.weight(1f),
                        onChange = { entry.payee = it },
                        onPick = { name ->
                            entry.payee = name
                            if (entry.editing == null && entry.payment.isBlank() && entry.deposit.isBlank() && entry.choice == null) {
                                applySuggestion(model, account, entry, name, categoryTree)
                            }
                        },
                    )
                    // PET-05, VEH-09: who or what it was for.
                    if (entry.choice !is CategoryChoice.TransferWith) {
                        Picker(model.t("register.for"), listOf(null) + whoList, whoList.firstOrNull { it.id == entry.forId }, { it?.name ?: model.t("register.forNobody") }, Modifier.width(200.dp)) {
                            entry.forId = it?.id
                        }
                        if (vehicleList.isNotEmpty() || entry.assetId != null) {
                            Picker(model.t("register.vehicle"), listOf(null) + vehicleList, vehicleList.firstOrNull { it.first == entry.assetId }, { it?.second ?: model.t("common.none") }, Modifier.width(170.dp)) {
                                entry.assetId = it?.first
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    if (entry.splits != null) {
                        OutlinedButton(onClick = { splitting = true }, modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                            Text(model.t("register.splitLines", entry.splits!!.size))
                        }
                    } else {
                        Picker(model.t("register.category"), choices, entry.choice, ::choiceLabel, Modifier.weight(1f), indent = {
                            (it as? CategoryChoice.Of)?.depth ?: 0
                        }) { entry.choice = it }
                    }
                    TextInput(model.t("register.memo"), entry.memo, Modifier.weight(1f)) { entry.memo = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    AmountInput(model.t("register.payment"), entry.payment, account.currency, locale, Modifier.width(170.dp), model::money) {
                        entry.payment = it
                        if (it.isNotBlank()) entry.deposit = ""
                    }
                    AmountInput(model.t("register.deposit"), entry.deposit, account.currency, locale, Modifier.width(170.dp), model::money) {
                        entry.deposit = it
                        if (it.isNotBlank()) entry.payment = ""
                    }
                    val transferTo = (entry.choice as? CategoryChoice.TransferWith)?.account
                    if (transferTo != null && transferTo.currency != account.currency) {
                        AmountInput(model.t("register.otherAmount", transferTo.currency.code), entry.otherAmount, transferTo.currency, locale, Modifier.width(200.dp), model::money) {
                            entry.otherAmount = it
                        }
                    }
                    FlowRow(Modifier.weight(1f).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        if (entry.choice !is CategoryChoice.TransferWith) {
                            OutlinedButton(onClick = { splitting = true }) { Text(model.t("register.splitButton")) }
                        }
                        entry.editing?.let { editing ->
                            OutlinedButton(onClick = {
                                if (model.act(retryConfirmed = { books.transactions.delete(editing.id, confirmReconciled = true); entry.clear() }) {
                                        books.transactions.delete(editing.id)
                                    } != null
                                ) entry.clear()
                            }) { Text(model.t("common.delete")) }
                        }
                        TextButton(onClick = { entry.clear() }) { Text(model.t("common.cancel")) }
                        Button(onClick = { save() }) { Text(model.t("common.save")) }
                    }
                }
            }
        }
    }

    pendingImport?.let { PendingImportDialog(model, it) { pendingImport = null } }
    if (showStatements) StatementsDialog(model, account) { showStatements = false }
    if (splitting) {
        SplitDialog(model, account, entry, categoryTree) { splitting = false }
    }
    if (editingAccount) AccountDialog(model, account) { editingAccount = false }
    if (editingCard) CardTermsDialog(model, account) { editingCard = false }
    if (revealing) RevealNumberDialog(model, account) { revealing = false }
    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text(model.t("account.close")) },
            text = { Text(model.t("account.close.confirm", account.name)) },
            confirmButton = { TextButton(onClick = { model.act { books.accounts.close(account.id) }; confirmClose = false }) { Text(model.t("account.close")) } },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text(model.t("common.cancel")) } },
        )
    }
}

/**
 * Validates the form and returns the change to perform, given whether reconciled changes are
 * confirmed. Validation errors are thrown before anything is written.
 */
private fun buildSave(model: BooksModel, account: Account, entry: EntryState): (Boolean) -> Unit {
    val books = model.books
    val locale = model.language.locale
    val date = runCatching { LocalDate.parse(entry.date.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }
    val payment = parseAmount(entry.payment, account.currency, locale)
    val deposit = parseAmount(entry.deposit, account.currency, locale)
    val splits = entry.splits
    val amount = when {
        deposit != null -> deposit
        payment != null -> -payment
        splits != null -> splits.map { it.amount }.sum(account.currency)
        else -> throw ValidationException("error.amountRequired")
    }
    val editing = entry.editing
    val memo = entry.memo.ifBlank { null }

    fun guard(confirm: Boolean) {
        if (editing?.cleared == ClearedStatus.RECONCILED && !confirm) throw ReconciledChangeException()
    }

    val choice = entry.choice
    if (choice is CategoryChoice.TransferWith) {
        val other = choice.account
        val otherAmount = if (other.currency == account.currency) {
            null
        } else {
            parseAmount(entry.otherAmount, other.currency, locale)?.abs()
                ?: throw ValidationException("error.transferToAmount", other.currency.code)
        }
        val draft = if (amount.isNegative) {
            TransferDraft(account.id, other.id, date, -amount, otherAmount, memo)
        } else {
            TransferDraft(other.id, account.id, date, otherAmount ?: amount, otherAmount?.let { amount }, memo)
        }
        return { confirm ->
            guard(confirm)
            val sameTransfer = editing?.transfer?.takeIf { it.otherAccountId == other.id }
            when {
                sameTransfer != null -> books.transactions.updateTransfer(sameTransfer.transferId, draft, confirm)
                else -> {
                    books.transactions.transfer(draft)
                    editing?.let { books.transactions.delete(it.id, confirm) }
                }
            }
        }
    }

    val splitDrafts = splits ?: when (choice) {
        is CategoryChoice.Of -> listOf(SplitDraft(choice.category.id, amount))
        else -> emptyList()
    }
    val draft = TransactionDraft(
        account.id, date, amount, entry.payee.ifBlank { null }, splitDrafts, memo,
        memberId = entry.forId,
        cleared = editing?.cleared ?: ClearedStatus.UNCLEARED,
        // The draft takes tag names; keep the tags the transaction already has.
        tags = editing?.tagIds?.let { ids -> model.books.tags().filter { it.id in ids }.map { it.name }.toSet() }.orEmpty(),
        assetId = entry.assetId,
    )
    return { confirm ->
        guard(confirm)
        when {
            editing == null -> books.transactions.create(draft)
            editing.transfer != null -> {
                books.transactions.create(draft)
                books.transactions.delete(editing.id, confirm)
            }
            else -> books.transactions.update(editing.id, draft, confirm)
        }
    }
}

/** Fills amount and category from the payee's last transaction (MAN-02). */
private fun applySuggestion(model: BooksModel, account: Account, entry: EntryState, payee: String, tree: List<Pair<Category, Int>>) {
    val suggestion = model.books.transactions.suggest(account.id, payee) ?: return
    suggestion.amount?.let { amount ->
        val text = MoneyFormat.formatAmount(amount.abs(), model.language.locale)
        if (amount.isNegative) entry.payment = text else entry.deposit = text
    }
    when {
        suggestion.splits.size > 1 -> entry.splits = suggestion.splits
        else -> suggestion.splits.firstOrNull()?.categoryId?.let { id ->
            tree.firstOrNull { it.first.id == id }?.let { entry.choice = CategoryChoice.Of(it.first, it.second) }
        }
    }
}

private class SplitLine(categoryId: String?, memo: String, amount: String) {
    var categoryId by mutableStateOf(categoryId)
    var memo by mutableStateOf(memo)
    var amount by mutableStateOf(amount)
}

/**
 * Splits one transaction across categories (TX-02). Amounts are typed as positive numbers and take
 * the sign of the transaction; the dialog shows what remains to be assigned.
 */
@Composable
private fun SplitDialog(model: BooksModel, account: Account, entry: EntryState, tree: List<Pair<Category, Int>>, onClose: () -> Unit) {
    val locale = model.language.locale
    val currency = account.currency
    val total: Money? = runCatching {
        parseAmount(entry.deposit, currency, locale) ?: parseAmount(entry.payment, currency, locale)?.let { -it }
    }.getOrNull()
    val negative = total?.isNegative ?: entry.deposit.isBlank()
    val lines = remember {
        mutableStateListOf<SplitLine>().apply {
            val existing = entry.splits ?: (entry.choice as? CategoryChoice.Of)?.let { c -> total?.let { listOf(SplitDraft(c.category.id, it)) } }
            existing?.forEach { add(SplitLine(it.categoryId, it.memo.orEmpty(), MoneyFormat.formatAmount(it.amount.abs(), locale))) }
            if (isEmpty()) add(SplitLine(null, "", total?.abs()?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()))
            add(SplitLine(null, "", ""))
        }
    }
    val parsed = lines.map { line -> runCatching { parseAmount(line.amount, currency, locale) } }
    val valid = parsed.all { it.isSuccess }
    val assigned = parsed.mapNotNull { it.getOrNull() }.sum(currency)
    val remaining = total?.abs()?.minus(assigned)
    val options = tree.map { it.first.id to it }

    FormDialog(
        title = model.t("split.title"),
        saveLabel = model.t("common.save"),
        cancelLabel = model.t("common.cancel"),
        canSave = valid && assigned.isPositive && (remaining == null || remaining.isZero),
        onDismiss = onClose,
        onSave = {
            val drafts = lines.zip(parsed).mapNotNull { (line, amount) ->
                amount.getOrNull()?.takeIf { !it.isZero }?.let { SplitDraft(line.categoryId, if (negative) -it else it, line.memo.ifBlank { null }) }
            }
            if (total == null) {
                val text = MoneyFormat.formatAmount(assigned, locale)
                if (negative) entry.payment = text else entry.deposit = text
            }
            entry.splits = drafts
            entry.choice = null
            onClose()
        },
    ) {
        for (line in lines) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(
                    model.t("register.category"), listOf<Pair<String, Pair<Category, Int>>?>(null) + options,
                    options.firstOrNull { it.first == line.categoryId },
                    { it?.second?.first?.name(model.language) ?: model.t("register.uncategorized") },
                    Modifier.weight(2f), indent = { it?.second?.second ?: 0 },
                ) { line.categoryId = it?.first }
                TextInput(model.t("register.memo"), line.memo, Modifier.weight(1.5f)) { line.memo = it }
                AmountInput(model.t("register.amount"), line.amount, currency, locale, Modifier.weight(1f), model::money) { line.amount = it }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { lines.add(SplitLine(null, "", remaining?.takeIf { it.isPositive }?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty())) }) {
                Text(model.t("split.add"))
            }
            Text(
                if (remaining != null) model.t("split.remaining", model.money(remaining)) else model.t("split.total", model.money(assigned)),
                color = if (remaining != null && !remaining.isZero) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** CC-01 card terms. Rates are typed as percentages (19.99) and stored as fractions. */
@Composable
private fun CardTermsDialog(model: BooksModel, account: Account, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val currency = account.currency
    val existing = remember { books.creditCards.terms(account.id) ?: CreditCardTerms() }
    fun pct(v: BigDecimal?) = v?.movePointRight(2)?.stripTrailingZeros()?.toPlainString().orEmpty()
    fun amt(m: Money?) = m?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()
    var limit by remember { mutableStateOf(amt(existing.creditLimit)) }
    var purchase by remember { mutableStateOf(pct(existing.purchaseRate)) }
    var cash by remember { mutableStateOf(pct(existing.cashAdvanceRate)) }
    var statementDay by remember { mutableStateOf(existing.statementDay?.toString().orEmpty()) }
    var dueDay by remember { mutableStateOf(existing.dueDay?.toString().orEmpty()) }
    var minPercent by remember { mutableStateOf(pct(existing.minPaymentPercent)) }
    var minFloor by remember { mutableStateOf(amt(existing.minPaymentFloor)) }
    var fee by remember { mutableStateOf(amt(existing.annualFee)) }

    fun rate(text: String): BigDecimal? = text.trim().ifEmpty { null }?.let { MoneyFormat.parseDecimal(it, locale).movePointLeft(2) }

    FormDialog(model.t("card.title"), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            val terms = runCatching {
                existing.copy(
                    creditLimit = parseAmount(limit, currency, locale),
                    purchaseRate = rate(purchase),
                    cashAdvanceRate = rate(cash),
                    statementDay = statementDay.trim().ifEmpty { null }?.toInt(),
                    dueDay = dueDay.trim().ifEmpty { null }?.toInt(),
                    minPaymentPercent = rate(minPercent),
                    minPaymentFloor = parseAmount(minFloor, currency, locale),
                    annualFee = parseAmount(fee, currency, locale),
                )
            }.getOrElse { throw ValidationException("error.invalidNumber") }
            books.creditCards.saveTerms(account.id, terms)
        }
        if (ok != null) onClose()
    }) {
        AmountInput(model.t("card.limit"), limit, currency, locale, Modifier.fillMaxWidth(), model::money) { limit = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("card.purchaseRate"), purchase, Modifier.weight(1f)) { purchase = it }
            TextInput(model.t("card.cashRate"), cash, Modifier.weight(1f)) { cash = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("card.statementDay"), statementDay, Modifier.weight(1f)) { statementDay = it }
            TextInput(model.t("card.dueDay"), dueDay, Modifier.weight(1f)) { dueDay = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("card.minPercent"), minPercent, Modifier.weight(1f)) { minPercent = it }
            AmountInput(model.t("card.minFloor"), minFloor, currency, locale, Modifier.weight(1f), model::money) { minFloor = it }
        }
        AmountInput(model.t("card.annualFee"), fee, currency, locale, Modifier.fillMaxWidth(), model::money) { fee = it }
    }
}

/** Shows the full account number after the password is entered again (SEC-04). */
@Composable
private fun RevealNumberDialog(model: BooksModel, account: Account, onClose: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf<String?>(null) }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(model.t("account.reveal.title")) },
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
                        revealed = model.books.accounts.revealNumber(account.id, password.toCharArray()).orEmpty()
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

@Composable
private fun Heading(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.labelLarge, textAlign = align, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Cell(text: String, modifier: Modifier) {
    Text(text, modifier = modifier.padding(end = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Transactions shown when a register opens (NFR-02). */
private const val PAGE = 1000
