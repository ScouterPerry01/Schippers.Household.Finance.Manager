package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import ca.schippers.hfm.books.AccountGoals
import ca.schippers.hfm.books.GoalEntry
import ca.schippers.hfm.books.GoalProgress
import ca.schippers.hfm.books.GoalStatus
import ca.schippers.hfm.books.SavingsGoal
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.calc.schedule.Frequency
import ca.schippers.hfm.calc.schedule.Recurrence
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.DatePeriod

/** What the Goals screen is doing with a goal. */
private sealed interface GoalAction {
    data class Edit(val goal: SavingsGoal?) : GoalAction
    data class Amount(val goal: SavingsGoal, val kind: AmountKind) : GoalAction
    data class Move(val goal: SavingsGoal) : GoalAction
    data class History(val goal: SavingsGoal) : GoalAction
}

private enum class AmountKind { SET_ASIDE, SPEND, RELEASE }

/** GOAL-01 to GOAL-06: savings goals earmarking parts of an account's balance. */
@Composable
fun GoalsScreen(model: BooksModel) {
    val books = model.books
    val accounts = remember(model.revision) { runCatching { books.goals.accounts(today()) }.getOrDefault(emptyList()) }
    var action by remember { mutableStateOf<GoalAction?>(null) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.t("nav.goals"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = { action = GoalAction.Edit(null) }) { Text(model.t("goals.add")) }
        }
        Text(model.t("goals.explain"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn {
            if (accounts.isEmpty()) item { Text(model.t("goals.none"), Modifier.padding(8.dp)) }
            items(accounts, key = { it.account.id }) { a -> AccountCard(model, a) { action = it } }
        }
    }

    when (val a = action) {
        is GoalAction.Edit -> GoalDialog(model, a.goal) { action = null }
        is GoalAction.Amount -> AmountDialog(model, a.goal, a.kind) { action = null }
        is GoalAction.Move -> MoveDialog(model, a.goal) { action = null }
        is GoalAction.History -> HistoryDialog(model, a.goal) { action = null }
        null -> Unit
    }
}

/** GOAL-03: the account's balance, what is earmarked, and what is left. */
@Composable
private fun AccountCard(model: BooksModel, a: AccountGoals, onAction: (GoalAction) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.account.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(model.t("goals.balance", model.money(a.balance)), fontWeight = FontWeight.Bold)
            }
            Text(
                model.t("goals.split", model.money(a.earmarked), model.money(a.unassigned)),
                style = MaterialTheme.typography.bodyMedium,
                color = if (a.overCommitted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (a.overCommitted) Text(model.t("goals.overCommitted", model.money(-a.unassigned)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            for (p in a.goals) GoalRow(model, p, onAction)
        }
    }
}

@Composable
private fun GoalRow(model: BooksModel, p: GoalProgress, onAction: (GoalAction) -> Unit) {
    val g = p.goal
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(g.name + if (g.status == GoalStatus.REACHED) " (${model.t("goalStatus.REACHED")})" else "", fontWeight = FontWeight.Medium)
                Text(
                    model.t("goals.savedOf", model.money(p.saved), model.money(g.target), p.percent) +
                        (if (!p.reached) " · " + model.t("goals.stillNeeded", model.money(p.remaining)) else ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onAction(GoalAction.Amount(g, AmountKind.SET_ASIDE)) }) { Text(model.t("goals.setAside")) }
                TextButton(onClick = { onAction(GoalAction.Amount(g, AmountKind.SPEND)) }) { Text(model.t("goals.spend")) }
                TextButton(onClick = { onAction(GoalAction.Move(g)) }) { Text(model.t("goals.move")) }
                TextButton(onClick = { onAction(GoalAction.History(g)) }) { Text(model.t("goals.history")) }
                TextButton(onClick = { onAction(GoalAction.Edit(g)) }) { Text(model.t("common.edit")) }
            }
        }
        LinearProgressIndicator(progress = { p.percent / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp).padding(vertical = 2.dp))
        Text(planText(model, p), style = MaterialTheme.typography.bodySmall)
        when (p.onTrack) {
            true -> if (!p.reached) Text(model.t("goals.onTrack"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            false -> Text(
                model.t("goals.behind", p.neededPerPeriod?.let(model::money) ?: "", p.goal.recurrence?.let { inSentence(model, it) } ?: model.t("goals.perMonth")),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            )
            null -> Unit
        }
    }
}

/** "250,00 $ monthly · target 2026-12-31 · reached around 2026-12-15", or what is needed without a plan. */
private fun planText(model: BooksModel, p: GoalProgress): String {
    val g = p.goal
    if (p.reached) return model.t("goals.reached")
    return listOfNotNull(
        g.contribution?.let { per -> g.recurrence?.let { rule -> model.t("goals.plan", model.money(per), inSentence(model, rule)) + if (!g.autoPost) " (${model.t("goals.byHand")})" else "" } }
            ?: model.t("goals.noPlan"),
        g.targetDate?.let { model.t("goals.targetDate", model.date(it)) },
        p.projectedDate?.let { model.t("goals.projected", model.date(it)) },
        p.neededPerPeriod?.takeIf { g.contribution == null }?.let { model.t("goals.neededMonthly", model.money(it)) },
    ).joinToString(" · ")
}

/** "monthly" rather than "Monthly", inside a sentence. */
private fun inSentence(model: BooksModel, r: Recurrence): String = describeRecurrence(model, r).replaceFirstChar { it.lowercase(model.language.locale) }

// --- Dialogs --------------------------------------------------------------------------------------

/** Schedules offered for set-asides: the usual pay and budget periods (GOAL-02). */
private val GOAL_REPEATS = listOf(Repeat.WEEKLY, Repeat.BI_WEEKLY, Repeat.SEMI_MONTHLY, Repeat.MONTHLY, Repeat.QUARTERLY, Repeat.SEMI_ANNUAL, Repeat.ANNUAL)

@Composable
private fun GoalDialog(model: BooksModel, existing: SavingsGoal?, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val accounts = remember {
        books.accounts.list().map { it.account }.filter { it.type !in setOf(AccountType.CREDIT_CARD, AccountType.LINE_OF_CREDIT, AccountType.HELOC, AccountType.LOAN, AccountType.MORTGAGE) }
    }
    var accountId by remember { mutableStateOf(existing?.accountId ?: accounts.firstOrNull { it.type == AccountType.HIGH_INTEREST_SAVINGS || it.type == AccountType.SAVINGS }?.id ?: accounts.firstOrNull()?.id) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var target by remember { mutableStateOf(existing?.let { MoneyFormat.formatAmount(it.target, locale) }.orEmpty()) }
    var targetDate by remember { mutableStateOf(existing?.targetDate?.toString().orEmpty()) }
    var planned by remember { mutableStateOf(existing?.recurrence != null || existing == null) }
    var amount by remember { mutableStateOf(existing?.contribution?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var repeat by remember { mutableStateOf(existing?.recurrence?.let { Repeat.of(it) }?.takeIf { it in GOAL_REPEATS } ?: Repeat.MONTHLY) }
    var secondDay by remember { mutableStateOf(existing?.recurrence?.secondDay?.toString() ?: "0") }
    var start by remember { mutableStateOf((existing?.scheduleStart ?: today()).toString()) }
    var autoPost by remember { mutableStateOf(existing?.autoPost ?: true) }
    var status by remember { mutableStateOf(existing?.status ?: GoalStatus.ACTIVE) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val account = accounts.firstOrNull { it.id == accountId } ?: existing?.let { books.accounts.get(it.accountId) }

    FormDialog(
        model.t(if (existing == null) "goals.add" else "goals.edit"), model.t("common.save"), model.t("common.cancel"),
        canSave = name.isNotBlank() && account != null, onDismiss = onClose,
        onSave = {
            val ok = model.act {
                fun date(text: String) = text.trim().ifEmpty { null }?.let { runCatching { LocalDate.parse(it) }.getOrElse { throw ValidationException("error.invalidDate") } }
                val currency = account!!.currency
                val recurrence = if (!planned) null else when (repeat) {
                    Repeat.SEMI_MONTHLY -> Recurrence(Frequency.SEMI_MONTHLY, secondDay = secondDay.trim().toIntOrNull()?.takeIf { it in 0..31 } ?: throw ValidationException("error.dayOfMonth"))
                    else -> repeat.recurrence
                }
                val goal = (existing ?: SavingsGoal("", account.id, "", parseAmount("1", currency, locale)!!)).copy(
                    name = name,
                    target = parseAmount(target, currency, locale) ?: throw ValidationException("error.goalTarget"),
                    targetDate = date(targetDate),
                    contribution = if (planned) parseAmount(amount, currency, locale) ?: throw ValidationException("error.goalContribution") else null,
                    recurrence = recurrence,
                    scheduleStart = if (planned) date(start) ?: throw ValidationException("error.invalidDate") else null,
                    autoPost = autoPost, status = status, notes = notes,
                )
                books.goals.save(goal)
            }
            if (ok != null) onClose()
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextInput(model.t("goals.name"), name) { name = it }
            Picker(model.t("goals.account"), accounts, account, { it.name }, enabled = existing == null) { accountId = it.id }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (account != null) AmountInput(model.t("goals.target"), target, account.currency, locale, Modifier.weight(1f), model::money) { target = it }
                DateInput(model.t("goals.targetDateField"), targetDate, Modifier.weight(1f)) { targetDate = it }
            }
            LabeledCheckbox(model.t("goals.planned"), planned) { planned = it }
            if (planned) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (account != null) AmountInput(model.t("goals.amountEach"), amount, account.currency, locale, Modifier.weight(1f), model::money) { amount = it }
                    Picker(model.t("bills.repeat"), GOAL_REPEATS, repeat, { model.t("repeat.$it") }, Modifier.weight(1f)) { repeat = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateInput(model.t("goals.firstDate"), start, Modifier.weight(1f)) { start = it }
                    if (repeat == Repeat.SEMI_MONTHLY) TextInput(model.t("bills.secondDay"), secondDay, Modifier.weight(1f), supporting = model.t("bills.secondDay.hint")) { secondDay = it }
                }
                LabeledCheckbox(model.t("goals.autoPost"), autoPost) { autoPost = it }
            }
            if (existing != null) Picker(model.t("goals.status"), GoalStatus.entries, status, { model.t("goalStatus.$it") }) { status = it }
            TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
            if (existing != null) TextButton(onClick = { confirmDelete = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmDelete && existing != null) {
        FormDialog(model.t("goals.delete.title"), model.t("common.delete"), model.t("common.cancel"), onDismiss = { confirmDelete = false }, onSave = {
            if (model.act { books.goals.delete(existing.id) } != null) {
                confirmDelete = false
                onClose()
            }
        }) { Text(model.t("goals.delete.body", existing.name)) }
    }
}

/** Set aside, spend from or take back from a goal (GOAL-02, GOAL-05). */
@Composable
private fun AmountDialog(model: BooksModel, goal: SavingsGoal, kind: AmountKind, onClose: () -> Unit) {
    val locale = model.language.locale
    var date by remember { mutableStateOf(today().toString()) }
    var amount by remember { mutableStateOf(goal.contribution?.takeIf { kind == AmountKind.SET_ASIDE }?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var memo by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(kind) }
    // GOAL-05: spending can be linked to the purchase, chosen among the account's recent payments.
    var purchase by remember { mutableStateOf<ca.schippers.hfm.books.Transaction?>(null) }
    val payments = remember(model.revision) {
        if (kind == AmountKind.SET_ASIDE) emptyList()
        else runCatching { model.books.transactions.register(goal.accountId, limit = 200) }.getOrDefault(emptyList())
            .map { it.transaction }.filter { it.amount.isNegative && it.date >= today().minus(DatePeriod(days = 120)) }.take(40)
    }
    val payees = remember(model.revision) { model.books.payees.list(true).associate { it.id to it.name } }
    fun purchaseLabel(t: ca.schippers.hfm.books.Transaction?) = t?.let {
        listOfNotNull(model.date(it.date), it.payeeId?.let(payees::get) ?: it.payeeText, it.memo, model.money(-it.amount)).joinToString(" · ")
    } ?: model.t("goals.noPurchase")
    FormDialog(model.t("goals.amount.${mode.name}") + " · " + goal.name, model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            val d = runCatching { LocalDate.parse(date.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }
            val value = parseAmount(amount, goal.target.currency, locale) ?: throw ValidationException("error.amountPositive")
            when (mode) {
                AmountKind.SET_ASIDE -> model.books.goals.setAside(goal.id, d, value, memo)
                AmountKind.SPEND -> model.books.goals.spend(goal.id, d, value, purchase?.id, memo)
                AmountKind.RELEASE -> model.books.goals.release(goal.id, d, value, memo)
            }
        }
        if (ok != null) onClose()
    }) {
        if (kind != AmountKind.SET_ASIDE) {
            Picker(model.t("goals.why"), listOf(AmountKind.SPEND, AmountKind.RELEASE), mode, { model.t("goals.amount.${it.name}.why") }) { mode = it }
        }
        Text(model.t("goals.amount.${mode.name}.explain"), style = MaterialTheme.typography.bodySmall)
        if (mode == AmountKind.SPEND && payments.isNotEmpty()) {
            Picker(model.t("goals.purchase"), listOf(null) + payments, purchase, ::purchaseLabel, Modifier.fillMaxWidth()) { t ->
                purchase = t
                if (t != null) {
                    date = t.date.toString()
                    amount = MoneyFormat.formatAmount(-t.amount, locale)
                    if (memo.isBlank()) memo = (t.payeeId?.let(payees::get) ?: t.payeeText).orEmpty()
                }
            }
        }
        DateInput(model.t("report.date"), date, Modifier.fillMaxWidth()) { date = it }
        AmountInput(model.t("register.amount"), amount, goal.target.currency, locale, Modifier.fillMaxWidth(), model::money) { amount = it }
        TextInput(model.t("register.memo"), memo) { memo = it }
    }
}

@Composable
private fun MoveDialog(model: BooksModel, goal: SavingsGoal, onClose: () -> Unit) {
    val locale = model.language.locale
    val others = remember { model.books.goals.list().filter { it.accountId == goal.accountId && it.id != goal.id } }
    var to by remember { mutableStateOf(others.firstOrNull()) }
    var amount by remember { mutableStateOf("") }
    FormDialog(model.t("goals.move") + " · " + goal.name, model.t("common.save"), model.t("common.cancel"), canSave = to != null, onDismiss = onClose, onSave = {
        val ok = model.act {
            val value = parseAmount(amount, goal.target.currency, locale) ?: throw ValidationException("error.amountPositive")
            model.books.goals.move(goal.id, to!!.id, today(), value)
        }
        if (ok != null) onClose()
    }) {
        if (others.isEmpty()) Text(model.t("goals.moveNone"))
        Picker(model.t("goals.moveTo"), others, to, { it.name }) { to = it }
        AmountInput(model.t("register.amount"), amount, goal.target.currency, locale, Modifier.fillMaxWidth(), model::money) { amount = it }
    }
}

/** GOAL-06. */
@Composable
private fun HistoryDialog(model: BooksModel, goal: SavingsGoal, onClose: () -> Unit) {
    val entries = remember(model.revision) { model.books.goals.entries(goal.id) }
    var deleting by remember { mutableStateOf<GoalEntry?>(null) }
    WideDialog(model.t("goals.historyOf", goal.name), model.t("common.close"), onClose) {
        if (entries.isEmpty()) Text(model.t("goals.noHistory"))
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            items(entries, key = { it.id }) { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(model.date(e.date), Modifier.width(110.dp))
                    Text(model.t("goalEntry.${e.kind}"), Modifier.width(180.dp))
                    Text(e.memo.orEmpty(), Modifier.weight(1f))
                    Text(
                        model.money(e.amount), Modifier.width(130.dp),
                        color = if (e.amount.isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(onClick = { deleting = e }) { Text(model.t("common.delete")) }
                }
                HorizontalDivider()
            }
        }
    }
    deleting?.let { e ->
        FormDialog(model.t("goals.deleteEntry.title"), model.t("common.delete"), model.t("common.cancel"), onDismiss = { deleting = null }, onSave = {
            if (model.act { model.books.goals.deleteEntry(goal.id, e.id) } != null) deleting = null
        }) { Text(model.t("goals.deleteEntry.body", model.t("goalEntry.${e.kind}"), model.money(e.amount), model.date(e.date))) }
    }
}
