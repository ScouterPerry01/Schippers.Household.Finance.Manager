package ca.schippers.hfm.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import ca.schippers.hfm.books.Allowance
import ca.schippers.hfm.books.AllowanceFrequency
import ca.schippers.hfm.books.AllowanceKind
import ca.schippers.hfm.books.AllowanceEntry
import ca.schippers.hfm.books.FamilyLoan
import ca.schippers.hfm.books.FamilyLoanService
import ca.schippers.hfm.books.LoanPayment
import ca.schippers.hfm.books.ShareEntry
import ca.schippers.hfm.books.ShareGroup
import ca.schippers.hfm.books.SharePerson
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.datetime.LocalDate

private enum class FamilyTab { SHARED, LOANS, ALLOWANCES }

/** HH-03, HH-04, LN-07: money between people: shared expenses, family loans and allowances. */
@Composable
fun FamilyScreen(model: BooksModel) {
    var tab by remember { mutableStateOf(FamilyTab.SHARED) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(model.t("nav.family"), style = MaterialTheme.typography.titleLarge)
        Text(model.t("family.hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        PrimaryTabRow(selectedTabIndex = tab.ordinal, modifier = Modifier.padding(vertical = 8.dp)) {
            for (t in FamilyTab.entries) Tab(selected = tab == t, onClick = { tab = t }, text = { Text(model.t("family.tab.$t")) })
        }
        when (tab) {
            FamilyTab.SHARED -> SharedTab(model)
            FamilyTab.LOANS -> LoansTab(model)
            FamilyTab.ALLOWANCES -> AllowancesTab(model)
        }
    }
}

private fun parseDate(text: String): LocalDate = runCatching { LocalDate.parse(text.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }

private fun BooksModel.editableGroup(): String = defaultDocumentGroup() ?: books.groups().first().id

// --- Shared expenses (HH-04) -------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SharedTab(model: BooksModel) {
    val books = model.books
    val groups = remember(model.revision) { books.sharedExpenses.list() }
    var selected by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ShareGroup?>(null) }
    var creating by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ShareEntry?>(null) }
    val g = groups.firstOrNull { it.id == selected } ?: groups.firstOrNull()
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(240.dp).fillMaxHeight()) {
            Button(onClick = { creating = true }) { Text(model.t("share.newGroup")) }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                if (groups.isEmpty()) Text(model.t("share.none"), style = MaterialTheme.typography.bodySmall)
                for (x in groups) NavigationDrawerItem(label = { Text(x.name + if (x.archived) " (${model.t("share.archived")})" else "") }, selected = x.id == g?.id, onClick = { selected = x.id })
            }
        }
        VerticalDivider(Modifier.padding(horizontal = 12.dp))
        if (g == null) return@Row
        val balances = books.sharedExpenses.balances(g)
        val plan = books.sharedExpenses.settleUp(g)
        val name = g.people.associate { it.id to it.name }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(g.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Button(onClick = { adding = true }) { Text(model.t("share.addExpense")) }
                OutlinedButton(onClick = { editing = g }) { Text(model.t("share.editGroup")) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in g.people) {
                    val b = balances[p.id] ?: Money.zero(g.currency)
                    Card {
                        Column(Modifier.padding(12.dp).width(170.dp)) {
                            Text(p.name, fontWeight = FontWeight.Medium)
                            Text(
                                when {
                                    b.isPositive -> model.t("share.isOwed", model.money(b))
                                    b.isNegative -> model.t("share.owes", model.money(-b))
                                    else -> model.t("share.even")
                                },
                                color = if (b.isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            if (plan.isNotEmpty()) {
                Text(model.t("share.settleUp"), style = MaterialTheme.typography.titleSmall)
                for (s in plan) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(model.t("share.pays", name[s.from].orEmpty(), name[s.to].orEmpty(), model.money(s.amount)), Modifier.weight(1f))
                        TextButton(onClick = {
                            model.act { books.sharedExpenses.saveEntry(g.id, ShareEntry("", today(), model.t("share.settlement"), s.amount, s.from, settlesTo = s.to)) }
                        }) { Text(model.t("share.markPaid")) }
                    }
                }
            }
            HorizontalDivider()
            for (e in g.entries.reversed()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(model.date(e.date), Modifier.width(100.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.description)
                        Text(
                            if (e.settlesTo != null) model.t("share.settled", name[e.paidBy].orEmpty(), name[e.settlesTo].orEmpty())
                            else model.t("share.paidBy", name[e.paidBy].orEmpty(), e.weights.filterValues { it > 0 }.keys.mapNotNull(name::get).joinToString(", ")),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    MoneyText(model, e.amount)
                    TextButton(onClick = { deleting = e }) { Text("✕") }
                }
            }
        }
    }
    if (creating) ShareGroupDialog(model, null) { id -> creating = false; if (id != null) selected = id }
    editing?.let { e -> ShareGroupDialog(model, e) { editing = null } }
    if (adding && g != null) ShareEntryDialog(model, g) { adding = false }
    if (g != null) {
        deleting?.let { e ->
            AskBeforeDeleting(model, model.t("share.deleteEntry.body", e.description, model.money(e.amount), model.date(e.date)), onDismiss = { deleting = null }) {
                model.act { books.sharedExpenses.deleteEntry(g.id, e.id) } != null
            }
        }
    }
}

@Composable
private fun ShareGroupDialog(model: BooksModel, existing: ShareGroup?, onClose: (String?) -> Unit) {
    val members = remember { model.books.members.list() }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    val people = remember { mutableStateListOf<SharePerson>().apply { addAll(existing?.people ?: listOf(SharePerson("", ""), SharePerson("", ""))) } }
    var archived by remember { mutableStateOf(existing?.archived ?: false) }
    var asking by remember { mutableStateOf(false) }
    FormDialog(model.t(if (existing == null) "share.newGroup" else "share.editGroup"), model.t("common.save"), model.t("common.cancel"), onDismiss = { onClose(null) }, onSave = {
        val saved = model.act { model.books.sharedExpenses.save(existing?.id, model.editableGroup(), name, existing?.currency ?: model.books.reports.base, people.toList(), archived) }
        if (saved != null) onClose(saved.id)
    }) {
        TextInput(model.t("share.groupName"), name) { name = it }
        Text(model.t("share.people"), style = MaterialTheme.typography.titleSmall)
        people.forEachIndexed { i, p ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextInput(model.t("share.personName"), p.name, Modifier.weight(1f)) { people[i] = p.copy(name = it) }
                Picker(model.t("share.member"), listOf(null) + members, members.firstOrNull { it.id == p.memberId }, { it?.displayName ?: model.t("share.notMember") }, Modifier.width(170.dp)) { m ->
                    people[i] = p.copy(memberId = m?.id, name = p.name.ifBlank { m?.displayName.orEmpty() })
                }
                TextButton(onClick = { people.removeAt(i) }) { Text("✕") }
            }
        }
        TextButton(onClick = { people.add(SharePerson("", "")) }) { Text(model.t("share.addPerson")) }
        if (existing != null) {
            LabeledCheckbox(model.t("share.archive"), archived) { archived = it }
            TextButton(onClick = { asking = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (asking && existing != null) {
        AskBeforeDeleting(model, model.t("share.delete.body", existing.name), onDismiss = { asking = false }) {
            (model.act { model.books.sharedExpenses.delete(existing.id) } != null).also { if (it) onClose(null) }
        }
    }
}

@Composable
private fun ShareEntryDialog(model: BooksModel, g: ShareGroup, onClose: () -> Unit) {
    val locale = model.language.locale
    var date by remember { mutableStateOf(today().toString()) }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var paidBy by remember { mutableStateOf(g.people.first()) }
    val weights = remember { mutableStateListOf<String>().apply { repeat(g.people.size) { add("1") } } }
    FormDialog(model.t("share.addExpense"), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            val value = parseAmount(amount, g.currency, locale)?.abs() ?: throw ValidationException("error.shareAmount")
            val w = g.people.mapIndexed { i, p -> p.id to (weights[i].trim().toIntOrNull() ?: 0) }.toMap()
            model.books.sharedExpenses.saveEntry(g.id, ShareEntry("", parseDate(date), description, value, paidBy.id, w))
        }
        if (ok != null) onClose()
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t("report.date"), date, Modifier.width(170.dp)) { date = it }
            TextInput(model.t("share.description"), description, Modifier.weight(1f)) { description = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountInput(model.t("share.amount"), amount, g.currency, locale, Modifier.weight(1f), model::money) { amount = it }
            Picker(model.t("share.paidByLabel"), g.people, paidBy, { it.name }, Modifier.weight(1f)) { paidBy = it }
        }
        Text(model.t("share.splitHint"), style = MaterialTheme.typography.bodySmall)
        g.people.forEachIndexed { i, p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p.name, Modifier.weight(1f))
                TextInput(model.t("share.shares"), weights[i], Modifier.width(110.dp)) { weights[i] = it.filter(Char::isDigit) }
            }
        }
    }
}

// --- Family loans (LN-07) ----------------------------------------------------------------------------

@Composable
private fun LoansTab(model: BooksModel) {
    val books = model.books
    val loans = remember(model.revision) { books.familyLoans.list() }
    var editing by remember { mutableStateOf<FamilyLoan?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    Button(onClick = { editing = FamilyLoan("", model.editableGroup(), "", "", Money.zero(books.reports.base), today(), 0, null, false, emptyList()) }) { Text(model.t("loan.add")) }
    Text(model.t("loan.hint"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
    if (loans.isEmpty()) Text(model.t("loan.none"))
    Column(Modifier.verticalScroll(rememberScrollState())) {
        for (l in loans) {
            val s = books.familyLoans.status(l, today())
            Row(Modifier.fillMaxWidth().clickable { open = l.id }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(model.t("loan.between", l.lender, l.borrower) + if (l.closed) " (${model.t("loan.closed")})" else "", fontWeight = FontWeight.Medium)
                    Text(
                        listOfNotNull(
                            model.t("loan.lent", model.money(l.principal), model.date(l.start)),
                            l.rateBp.takeIf { it > 0 }?.let { model.t("loan.rate", String.format(model.language.locale, "%.2f", it / 100.0)) },
                            model.t("loan.repaid", model.money(s.repaid)),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    MoneyText(model, s.balance, bold = true)
                    if (s.interestLeft.isPositive) Text(model.t("loan.interestLeft", model.money(s.interestLeft)), style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
        }
    }
    editing?.let { l -> FamilyLoanDialog(model, l) { editing = null } }
    open?.let { id -> loans.firstOrNull { it.id == id }?.let { l -> LoanPaymentsDialog(model, l, onEdit = { open = null; editing = l }) { open = null } } }
}

@Composable
private fun FamilyLoanDialog(model: BooksModel, loan: FamilyLoan, onClose: () -> Unit) {
    val locale = model.language.locale
    var lender by remember { mutableStateOf(loan.lender) }
    var borrower by remember { mutableStateOf(loan.borrower) }
    var amount by remember { mutableStateOf(if (loan.principal.isZero) "" else MoneyFormat.formatAmount(loan.principal, locale)) }
    var start by remember { mutableStateOf(loan.start.toString()) }
    var rate by remember { mutableStateOf(if (loan.rateBp == 0) "" else (loan.rateBp / 100.0).toString()) }
    var notes by remember { mutableStateOf(loan.notes.orEmpty()) }
    var closed by remember { mutableStateOf(loan.closed) }
    var asking by remember { mutableStateOf(false) }
    val cur = loan.principal.currency
    FormDialog(model.t(if (loan.id.isBlank()) "loan.add" else "loan.edit"), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            val principal = parseAmount(amount, cur, locale)?.abs() ?: throw ValidationException("error.loanPrincipal")
            val bp = rate.trim().replace(',', '.').ifEmpty { "0" }.toBigDecimalOrNull()?.let(FamilyLoanService::rateBp) ?: throw ValidationException("error.loanRate")
            model.books.familyLoans.save(loan.copy(lender = lender, borrower = borrower, principal = principal, start = parseDate(start), rateBp = bp, notes = notes, closed = closed))
        }
        if (ok != null) onClose()
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("loan.lender"), lender, Modifier.weight(1f)) { lender = it }
            TextInput(model.t("loan.borrower"), borrower, Modifier.weight(1f)) { borrower = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountInput(model.t("loan.amount"), amount, cur, locale, Modifier.weight(1f), model::money) { amount = it }
            DateInput(model.t("loan.start"), start, Modifier.weight(1f)) { start = it }
        }
        TextInput(model.t("loan.ratePercent"), rate, supporting = model.t("loan.rateHint")) { rate = it }
        TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
        if (loan.id.isNotBlank()) {
            LabeledCheckbox(model.t("loan.markClosed"), closed) { closed = it }
            TextButton(onClick = { asking = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (asking) {
        AskBeforeDeleting(model, model.t("loan.delete.body", loan.lender, loan.borrower), onDismiss = { asking = false }) {
            (model.act { model.books.familyLoans.delete(loan) } != null).also { if (it) onClose() }
        }
    }
}

@Composable
private fun LoanPaymentsDialog(model: BooksModel, loan: FamilyLoan, onEdit: () -> Unit, onClose: () -> Unit) {
    val locale = model.language.locale
    var date by remember { mutableStateOf(today().toString()) }
    var amount by remember { mutableStateOf("") }
    val s = model.books.familyLoans.status(loan, today())
    var deleting by remember { mutableStateOf<LoanPayment?>(null) }
    FormDialog(model.t("loan.between", loan.lender, loan.borrower), model.t("loan.addPayment"), model.t("common.close"), canSave = amount.isNotBlank(), onDismiss = onClose, onSave = {
        val ok = model.act {
            model.books.familyLoans.addPayment(loan, parseDate(date), parseAmount(amount, loan.principal.currency, locale)?.abs() ?: throw ValidationException("error.loanPayment"))
        }
        if (ok != null) onClose()
    }) {
        Text(model.t("loan.status", model.money(s.balance), model.money(s.interest), model.money(s.repaid)), fontWeight = FontWeight.Medium)
        for (p in loan.payments.reversed()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.date(p.date), Modifier.width(110.dp))
                MoneyText(model, p.amount, modifier = Modifier.weight(1f))
                TextButton(onClick = { deleting = p }) { Text("✕") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t("report.date"), date, Modifier.weight(1f)) { date = it }
            AmountInput(model.t("loan.payment"), amount, loan.principal.currency, locale, Modifier.weight(1f), model::money) { amount = it }
        }
        TextButton(onClick = onEdit) { Text(model.t("loan.edit")) }
    }
    deleting?.let { p ->
        AskBeforeDeleting(model, model.t("loan.deletePayment.body", model.money(p.amount), model.date(p.date)), onDismiss = { deleting = null }) {
            (model.act { model.books.familyLoans.deletePayment(loan, p.id) } != null).also { if (it) onClose() }
        }
    }
}

// --- Allowances (HH-03) ------------------------------------------------------------------------------

@Composable
private fun AllowancesTab(model: BooksModel) {
    val books = model.books
    val list = remember(model.revision) { books.allowances.list() }
    val members = remember(model.revision) { books.members.list() }
    var editing by remember { mutableStateOf<Allowance?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    val child = members.firstOrNull { it.kind == MemberKind.CHILD } ?: members.firstOrNull()
    if (child != null) {
        Button(onClick = { editing = Allowance("", model.editableGroup(), child.id, Money.zero(books.reports.base), AllowanceFrequency.WEEKLY, today(), null, null, emptyList()) }) {
            Text(model.t("allowance.add"))
        }
    }
    Text(model.t("allowance.hint"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
    if (list.isEmpty()) Text(model.t("allowance.none"))
    Column(Modifier.verticalScroll(rememberScrollState())) {
        for (a in list) {
            val s = books.allowances.status(a, today())
            val who = members.firstOrNull { it.id == a.memberId }?.displayName.orEmpty()
            Row(Modifier.fillMaxWidth().clickable { open = a.id }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$who · ${model.money(a.amount)} ${model.t("allowanceFrequency.${a.frequency}")}", fontWeight = FontWeight.Medium)
                    Text(
                        listOfNotNull(
                            model.t("allowance.balance", model.money(s.balance)),
                            s.nextDate?.let { model.t("allowance.next", model.date(it)) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (s.owed.isPositive) {
                    Text(model.t("allowance.owed", model.money(s.owed)), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 8.dp))
                    OutlinedButton(onClick = { model.act { books.allowances.addEntry(a, today(), s.owed, AllowanceKind.PAID) } }) { Text(model.t("allowance.payOwed")) }
                }
            }
            HorizontalDivider()
        }
    }
    editing?.let { a -> AllowanceDialog(model, a) { editing = null } }
    open?.let { id -> list.firstOrNull { it.id == id }?.let { a -> AllowanceEntriesDialog(model, a, onEdit = { open = null; editing = a }) { open = null } } }
}

@Composable
private fun AllowanceDialog(model: BooksModel, a: Allowance, onClose: () -> Unit) {
    val locale = model.language.locale
    val members = remember { model.books.members.list() }
    var member by remember { mutableStateOf(members.firstOrNull { it.id == a.memberId }) }
    var amount by remember { mutableStateOf(if (a.amount.isZero) "" else MoneyFormat.formatAmount(a.amount, locale)) }
    var frequency by remember { mutableStateOf(a.frequency) }
    var start by remember { mutableStateOf(a.start.toString()) }
    var end by remember { mutableStateOf(a.end?.toString().orEmpty()) }
    var notes by remember { mutableStateOf(a.notes.orEmpty()) }
    var asking by remember { mutableStateOf(false) }
    FormDialog(model.t(if (a.id.isBlank()) "allowance.add" else "allowance.edit"), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            val value = parseAmount(amount, a.amount.currency, locale)?.abs() ?: throw ValidationException("error.allowanceAmount")
            model.books.allowances.save(
                a.copy(memberId = member?.id ?: a.memberId, amount = value, frequency = frequency, start = parseDate(start), end = end.trim().ifEmpty { null }?.let(::parseDate), notes = notes),
            )
        }
        if (ok != null) onClose()
    }) {
        Picker(model.t("report.person"), members, member, { it.displayName }) { member = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountInput(model.t("allowance.amount"), amount, a.amount.currency, locale, Modifier.weight(1f), model::money) { amount = it }
            Picker(model.t("allowance.frequency"), AllowanceFrequency.entries, frequency, { model.t("allowanceFrequency.$it") }, Modifier.weight(1f)) { frequency = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t("allowance.start"), start, Modifier.weight(1f)) { start = it }
            DateInput(model.t("allowance.end"), end, Modifier.weight(1f)) { end = it }
        }
        TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
        if (a.id.isNotBlank()) TextButton(onClick = { asking = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
    }
    if (asking) {
        AskBeforeDeleting(model, model.t("allowance.delete.body", member?.displayName.orEmpty()), onDismiss = { asking = false }) {
            (model.act { model.books.allowances.delete(a) } != null).also { if (it) onClose() }
        }
    }
}

@Composable
private fun AllowanceEntriesDialog(model: BooksModel, a: Allowance, onEdit: () -> Unit, onClose: () -> Unit) {
    val locale = model.language.locale
    var date by remember { mutableStateOf(today().toString()) }
    var amount by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(AllowanceKind.PAID) }
    var notes by remember { mutableStateOf("") }
    val s = model.books.allowances.status(a, today())
    var deleting by remember { mutableStateOf<AllowanceEntry?>(null) }
    FormDialog(model.t("allowance.entriesTitle"), model.t("allowance.addEntry"), model.t("common.close"), canSave = amount.isNotBlank(), onDismiss = onClose, onSave = {
        val ok = model.act { model.books.allowances.addEntry(a, parseDate(date), parseAmount(amount, a.amount.currency, locale)?.abs() ?: throw ValidationException("error.allowanceAmount"), kind, notes) }
        if (ok != null) onClose()
    }) {
        Text(model.t("allowance.summary", model.money(s.due), model.money(s.owed), model.money(s.balance)), fontWeight = FontWeight.Medium)
        for (e in a.entries.reversed().take(30)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.date(e.date), Modifier.width(100.dp))
                Text(model.t("allowanceKind.${e.kind}") + (e.notes?.let { " · $it" } ?: ""), Modifier.weight(1f))
                MoneyText(model, if (e.kind == AllowanceKind.SPENT) -e.amount else e.amount)
                TextButton(onClick = { deleting = e }) { Text("✕") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t("report.date"), date, Modifier.weight(1f)) { date = it }
            Picker(model.t("allowance.kind"), AllowanceKind.entries, kind, { model.t("allowanceKind.$it") }, Modifier.weight(1f)) { kind = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountInput(model.t("allowance.amount"), amount, a.amount.currency, locale, Modifier.weight(1f), model::money) { amount = it }
            TextInput(model.t("calendar.notes"), notes, Modifier.weight(1f)) { notes = it }
        }
        TextButton(onClick = onEdit) { Text(model.t("allowance.edit")) }
    }
    deleting?.let { e ->
        AskBeforeDeleting(model, model.t("allowance.deleteEntry.body", model.t("allowanceKind.${e.kind}"), model.money(e.amount), model.date(e.date)), onDismiss = { deleting = null }) {
            (model.act { model.books.allowances.deleteEntry(a, e.id) } != null).also { if (it) onClose() }
        }
    }
}
