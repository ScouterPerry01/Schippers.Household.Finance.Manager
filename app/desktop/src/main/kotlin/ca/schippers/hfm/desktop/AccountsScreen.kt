package ca.schippers.hfm.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Account
import ca.schippers.hfm.books.AccountDraft
import ca.schippers.hfm.books.AccountSummary
import ca.schippers.hfm.books.Institution
import ca.schippers.hfm.books.LinkTarget
import ca.schippers.hfm.calc.rules.Thresholds
import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.domain.AccountStatus
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat
import ca.schippers.hfm.money.sum
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

@Composable
fun AccountsScreen(model: BooksModel) {
    var showClosed by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var quicken by remember { mutableStateOf<java.io.File?>(null) }
    val summaries = remember(model.revision, showClosed) { model.books.accounts.list(includeClosed = showClosed) }
    val lastReconciled = remember(model.revision) { model.books.statements.lastReconciled() }
    // Investment accounts are shown at their full value: cash plus securities (INV-04).
    val invested = remember(model.revision) {
        runCatching { model.books.investments.allHoldings(today()).associate { it.account.id to it.totalValue } }.getOrDefault(emptyMap())
    }
    val shown = summaries.map { s -> invested[s.account.id]?.let { s.copy(balance = it, balanceToday = it) } ?: s }
    if (model.selectedAccountId != null && summaries.none { it.account.id == model.selectedAccountId }) model.selectedAccountId = null

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(320.dp).fillMaxHeight().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.t("nav.accounts"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = { adding = true }) { Text(model.t("accounts.add")) }
            }
            LabeledCheckbox(model.t("accounts.showClosed"), showClosed) { showClosed = it }
            // OTH-05: bring a Quicken history across.
            TextButton(onClick = { chooseQif(model)?.let { quicken = it } }) { Text(model.t("quicken.import")) }
            // NAV-04: the two columns of the list.
            HeadingRow(Modifier.padding(horizontal = 8.dp)) {
                ColumnHeading(model.t("templates.account"), Modifier.weight(1f))
                ColumnHeading(model.t("account.balance"), align = TextAlign.End)
            }
            LazyColumn(Modifier.weight(1f)) {
                if (summaries.isEmpty()) item { Text(model.t("accounts.none"), Modifier.padding(8.dp)) }
                for ((kind, list) in shown.groupBy { it.account.type.kind }.toSortedMap()) {
                    item { Text(model.t("accountKind.$kind"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                    items(list, key = { it.account.id }) { summary -> AccountRow(model, summary, lastReconciled[summary.account.id]) }
                }
            }
            HorizontalDivider()
            Totals(model, shown)
        }
        VerticalDivider()
        Box(Modifier.fillMaxSize()) {
            val selected = summaries.firstOrNull { it.account.id == model.selectedAccountId }
            val reconciling = model.reconcilingStatementId
            if (selected != null && reconciling != null) {
                ReconcileScreen(model, selected.account, reconciling)
            } else if (selected != null) {
                RegisterScreen(model, selected)
            } else {
                Text(model.t("accounts.select"), Modifier.padding(24.dp))
            }
        }
    }
    if (adding) AccountDialog(model, existing = null) { adding = false }
    quicken?.let { file -> QuickenImportDialog(model, file) { quicken = null } }
}

@Composable
private fun AccountRow(model: BooksModel, summary: AccountSummary, lastReconciled: LocalDate?) {
    val selected = model.selectedAccountId == summary.account.id
    // REC-09: accounts more than the Rates and rules days behind (45 built in) are highlighted.
    val behind = lastReconciled != null && lastReconciled.daysUntil(today()) > Thresholds.reconcileBehind(today())
    Row(
        Modifier.fillMaxWidth().clickable {
            if (model.selectedAccountId != summary.account.id) model.reconcilingStatementId = null
            model.selectedAccountId = summary.account.id
        }.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                summary.account.name + if (summary.account.status == AccountStatus.CLOSED) " (${model.t("status.CLOSED")})" else "",
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                lastReconciled?.let { model.t("accounts.reconciled", model.date(it)) } ?: model.t("accounts.neverReconciled"),
                style = MaterialTheme.typography.bodySmall,
                color = if (behind) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
            )
        }
        // ACC-01: the balance today, and beside it the balance after post-dated transactions when it differs.
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(model, summary.balanceToday, bold = selected)
            if (summary.hasPostDated) {
                Text(model.t("accounts.postDated", model.money(summary.balance)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/** Net total of the listed accounts today, per currency (liabilities are negative balances). */
@Composable
private fun Totals(model: BooksModel, summaries: List<AccountSummary>) {
    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for ((currency, list) in summaries.groupBy { it.account.currency }.toSortedMap(compareBy { it.code })) {
            Row {
                Text(model.t("accounts.total", currency.code), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                MoneyText(model, list.map { it.balanceToday }.sum(currency), bold = true)
            }
        }
    }
}

@Composable
fun MoneyText(model: BooksModel, money: Money, bold: Boolean = false, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(
        model.money(money),
        textAlign = textAlign,
        color = if (money.isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        modifier = modifier,
    )
}

/** Add or edit an account (ACC-01 to ACC-04). Type and currency are fixed once created. */
@Composable
fun AccountDialog(model: BooksModel, existing: Account?, onClose: () -> Unit) {
    val books = model.books
    val groups = remember { books.groups().filter { it.level == PermissionLevel.EDIT } }
    val institutions = remember { books.institutions.list() }
    val members = remember { books.members.list() }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var type by remember { mutableStateOf(existing?.type ?: AccountType.CHEQUING) }
    var currencyCode by remember { mutableStateOf(existing?.currency?.code ?: "CAD") }
    // HH-09: a member's own accounts are private unless they choose to share them.
    val own = groups.firstOrNull { it.ownerUserId == model.session.userId }
    var groupId by remember { mutableStateOf(existing?.groupId ?: (if (model.books.users.isAdministrator) null else own)?.id ?: groups.firstOrNull()?.id) }
    var institutionId by remember { mutableStateOf(existing?.institutionId) }
    var opening by remember { mutableStateOf(existing?.let { MoneyFormat.formatAmount(it.openingBalance, model.language.locale) } ?: "0") }
    var openingDate by remember { mutableStateOf(existing?.openingDate?.toString() ?: today().toString()) }
    var number by remember { mutableStateOf("") }
    var owners by remember { mutableStateOf(existing?.ownerMemberIds ?: emptySet()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }

    val currency = runCatching { Currency.of(currencyCode) }.getOrNull()
    val openingMoney = currency?.let { c -> runCatching { parseAmount(opening, c, model.language.locale) ?: Money.zero(c) }.getOrNull() }
    val date = runCatching { LocalDate.parse(openingDate.trim()) }.getOrNull()
    val canSave = name.isNotBlank() && currency != null && openingMoney != null && date != null && groupId != null

    FormDialog(
        title = model.t(if (existing == null) "accounts.add" else "account.edit"),
        saveLabel = model.t("common.save"),
        cancelLabel = model.t("common.cancel"),
        canSave = canSave,
        onDismiss = onClose,
        onSave = {
            val result = if (existing == null) {
                model.act {
                    val account = books.accounts.create(
                        AccountDraft(groupId!!, name, type, currency!!, openingMoney!!, date!!, institutionId, number.ifBlank { null }, owners, notes.ifBlank { null }),
                    )
                    model.selectedAccountId = account.id
                }
            } else {
                model.act {
                    books.accounts.update(
                        existing.copy(name = name, institutionId = institutionId, openingBalance = openingMoney!!, openingDate = date!!, ownerMemberIds = owners, notes = notes.ifBlank { null }),
                        newNumber = number.ifBlank { null },
                    )
                }
            }
            if (result != null) onClose()
        },
    ) {
        Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("account.name"), name) { name = it }
            Picker(model.t("account.type"), AccountType.entries, type, { model.t("accountType.$it") }, enabled = existing == null) { type = it }
            TextInput(model.t("account.currency"), currencyCode, enabled = existing == null, error = if (currency == null) model.t("error.unknownCurrency") else null) { currencyCode = it.uppercase() }
            if (groups.size > 1 && existing == null) {
                Picker(model.t("account.group"), groups, groups.firstOrNull { it.id == groupId }, { it.name }) { groupId = it.id }
            }
            Picker(
                model.t("account.institution"), listOf<Institution?>(null) + institutions,
                institutions.firstOrNull { it.id == institutionId }, { it?.name ?: model.t("common.none") },
            ) { institutionId = it?.id }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currency != null) {
                    AmountInput(model.t("account.openingBalance"), opening, currency, model.language.locale, Modifier.weight(1f), model::money) { opening = it }
                }
                DateInput(model.t("account.openingDate"), openingDate, Modifier.weight(1f)) { openingDate = it }
            }
            TextInput(
                model.t("account.number"), number,
                supporting = existing?.numberMasked?.let { model.t("account.number.current", it) },
            ) { number = it }
            if (members.isNotEmpty()) {
                Text(model.t("account.owners"), style = MaterialTheme.typography.labelLarge)
                for (m in members) {
                    LabeledCheckbox(m.displayName, m.id in owners) { checked -> owners = if (checked) owners + m.id else owners - m.id }
                }
            }
            TextInput(model.t("account.notes"), notes, singleLine = false) { notes = it }
            if (type.kind == AccountKind.CREDIT && existing == null) Text(model.t("account.cardHint"), style = MaterialTheme.typography.bodySmall)
            // CON-04: the bank, lender, investment firm or advisor for this account.
            if (existing != null) {
                LinkedContacts(
                    model, LinkTarget.ACCOUNT, existing.id, accountRoles(existing.type), institutions.firstOrNull { it.id == existing.institutionId }?.name.orEmpty(),
                    existing.ownerMemberIds, existing.groupId,
                )
            }
        }
    }
}

fun today(): LocalDate = java.time.LocalDate.now().let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }
