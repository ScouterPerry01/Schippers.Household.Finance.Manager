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
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Account
import ca.schippers.hfm.books.Category
import ca.schippers.hfm.books.TemplateLine
import ca.schippers.hfm.books.Transaction
import ca.schippers.hfm.books.TxnTemplate
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat

/**
 * MAN-05: fills the entry form from a template: payee, category or split lines, memo, person,
 * tags, and the amount when the template has one in the account's currency.
 */
internal fun applyTemplate(model: BooksModel, account: Account, entry: EntryState, t: TxnTemplate, tree: List<Pair<Category, Int>>) {
    val locale = model.language.locale
    entry.payee = t.payee.orEmpty()
    entry.memo = t.memo.orEmpty()
    entry.forId = t.memberId
    entry.tags = t.tags
    val amount = t.amount?.takeIf { it.currency == account.currency }
    entry.payment = ""
    entry.deposit = ""
    amount?.let { a ->
        val text = MoneyFormat.formatAmount(a.abs(), locale)
        if (a.isNegative) entry.payment = text else entry.deposit = text
    }
    entry.splits = null
    entry.choice = null
    when {
        t.isSplit && amount != null -> entry.splits = t.lines.map { ca.schippers.hfm.books.SplitDraft(it.categoryId, it.amount!!, it.memo) }
        else -> t.categoryId?.let { id -> tree.firstOrNull { it.first.id == id }?.let { entry.choice = CategoryChoice.Of(it.first, it.second) } }
    }
}

/** The templates of an account's group, to add, edit or delete (each delete asks first). */
@Composable
internal fun TemplatesDialog(model: BooksModel, account: Account, tree: List<Pair<Category, Int>>, onClose: () -> Unit) {
    val books = model.books
    val all = remember(model.revision) { books.templates.list().filter { it.groupId == account.groupId } }
    val accounts = remember(model.revision) { books.accounts.list(includeClosed = true).associate { it.account.id to it.account.name } }
    val categories = remember(tree) { tree.associate { it.first.id to it.first } }
    var editing by remember { mutableStateOf<TxnTemplate?>(null) }
    var deleting by remember { mutableStateOf<TxnTemplate?>(null) }
    WideDialog(model.t("templates.title"), model.t("common.close"), onClose) {
        Column(Modifier.width(720.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(model.t("templates.explain"), style = MaterialTheme.typography.bodySmall)
            if (all.isEmpty()) Text(model.t("templates.none"), Modifier.padding(vertical = 8.dp))
            for (t in all) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, fontWeight = FontWeight.Medium)
                        Text(
                            listOfNotNull(
                                t.payee,
                                if (t.isSplit) model.t("register.splitLines", t.lines.size) else t.categoryId?.let { categories[it]?.name(model.language) },
                                t.amount?.let(model::money),
                                t.accountId?.let { model.t("templates.onlyIn", accounts[it] ?: "?") },
                                t.tags.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(", "),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = { editing = t }) { Text(model.t("common.edit")) }
                    TextButton(onClick = { deleting = t }) { Text(model.t("common.delete")) }
                }
                HorizontalDivider()
            }
            Button(onClick = { editing = TxnTemplate("", account.groupId, "") }, Modifier.padding(top = 8.dp)) { Text(model.t("templates.add")) }
        }
    }
    editing?.let { t -> TemplateForm(model, account, t, tree) { editing = null } }
    deleting?.let { t ->
        AskBeforeDeleting(model, model.t("templates.delete", t.name), onDismiss = { deleting = null }) {
            model.act { books.templates.delete(t.id) } != null
        }
    }
}

@Composable
private fun TemplateForm(model: BooksModel, account: Account, existing: TxnTemplate, tree: List<Pair<Category, Int>>, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val payees = remember(model.revision) { books.payees.list().map { it.name } }
    val groupAccounts = remember(model.revision) { books.accounts.list().map { it.account }.filter { it.groupId == existing.groupId } }
    val who = remember(model.revision) { model.peopleAndPets() }
    val currency = existing.accountId?.let { id -> groupAccounts.firstOrNull { it.id == id }?.currency } ?: existing.amount?.currency ?: account.currency
    var name by remember { mutableStateOf(existing.name) }
    var payee by remember { mutableStateOf(existing.payee.orEmpty()) }
    var memo by remember { mutableStateOf(existing.memo.orEmpty()) }
    var categoryId by remember { mutableStateOf(existing.categoryId) }
    var lines by remember { mutableStateOf(existing.lines.takeIf { existing.isSplit }) }
    var payment by remember { mutableStateOf(existing.amount?.takeIf { it.isNegative }?.let { MoneyFormat.formatAmount(it.abs(), locale) }.orEmpty()) }
    var deposit by remember { mutableStateOf(existing.amount?.takeIf { !it.isNegative }?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var accountId by remember { mutableStateOf(existing.accountId) }
    var memberId by remember { mutableStateOf(existing.memberId) }
    var tags by remember { mutableStateOf(existing.tags.sorted().joinToString(", ")) }
    val categoryOptions: List<Pair<Category, Int>?> = listOf(null) + tree
    FormDialog(
        model.t(if (existing.id.isBlank()) "templates.add" else "templates.edit"), model.t("common.save"), model.t("common.cancel"),
        canSave = name.isNotBlank(), onDismiss = onClose,
        onSave = {
            val saved = model.act {
                val pay = parseAmount(payment, currency, locale)
                val dep = parseAmount(deposit, currency, locale)
                val amount: Money? = dep ?: pay?.let { -it }
                books.templates.save(
                    existing.copy(
                        name = name, payee = payee, memo = memo, accountId = accountId, memberId = memberId,
                        amount = amount, lines = lines ?: listOfNotNull(categoryId?.let { TemplateLine(it) }),
                        tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                    ),
                )
            }
            if (saved != null) onClose()
        },
    ) {
        Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextInput(model.t("templates.name"), name, supporting = model.t("templates.nameHint")) { name = it }
            SuggestInput(model.t("register.payee"), payee, payees, onChange = { payee = it }, onPick = { payee = it })
            val split = lines
            if (split != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(model.t("templates.splitKept", split.size, model.money(split.mapNotNull { it.amount }.fold(Money.zero(currency), Money::plus))), Modifier.weight(1f))
                    TextButton(onClick = { lines = null }) { Text(model.t("templates.oneCategory")) }
                }
            } else {
                Picker(
                    model.t("register.category"), categoryOptions, categoryOptions.firstOrNull { it?.first?.id == categoryId },
                    { it?.first?.name(model.language) ?: model.t("register.uncategorized") }, indent = { it?.second ?: 0 },
                ) { categoryId = it?.first?.id }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmountInput(model.t("register.payment"), payment, currency, locale, Modifier.weight(1f), model::money) { payment = it; if (it.isNotBlank()) deposit = "" }
                    AmountInput(model.t("register.deposit"), deposit, currency, locale, Modifier.weight(1f), model::money) { deposit = it; if (it.isNotBlank()) payment = "" }
                }
                Text(model.t("templates.amountHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            TextInput(model.t("register.memo"), memo) { memo = it }
            Picker(model.t("register.for"), listOf(null) + who, who.firstOrNull { it.id == memberId }, { it?.name ?: model.t("register.forNobody") }) { memberId = it?.id }
            Picker(model.t("templates.account"), listOf(null) + groupAccounts, groupAccounts.firstOrNull { it.id == accountId }, { it?.name ?: model.t("templates.anyAccount") }) { accountId = it?.id }
            TextInput(model.t("templates.tags"), tags, supporting = model.t("templates.tagsHint")) { tags = it }
        }
    }
}

/** "Save as template" for the transaction open in the form (MAN-05). */
@Composable
internal fun SaveAsTemplateDialog(model: BooksModel, txn: Transaction, payeeName: String, onClose: () -> Unit) {
    var name by remember { mutableStateOf(payeeName) }
    var keepAmount by remember { mutableStateOf(true) }
    var thisAccount by remember { mutableStateOf(false) }
    FormDialog(
        model.t("templates.saveAs"), model.t("common.save"), model.t("common.cancel"), canSave = name.isNotBlank(), onDismiss = onClose,
        onSave = {
            if (model.act { model.books.templates.fromTransaction(txn.id, name, keepAmount, thisAccount) } != null) onClose()
        },
    ) {
        Column(Modifier.width(460.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextInput(model.t("templates.name"), name, supporting = model.t("templates.nameHint")) { name = it }
            LabeledCheckbox(model.t("templates.keepAmount"), keepAmount) { keepAmount = it }
            if (txn.isSplit && !keepAmount) Text(model.t("templates.splitNeedsAmount"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            LabeledCheckbox(model.t("templates.thisAccountOnly"), thisAccount) { thisAccount = it }
        }
    }
}
