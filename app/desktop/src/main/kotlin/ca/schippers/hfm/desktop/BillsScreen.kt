package ca.schippers.hfm.desktop

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.AmountKind
import ca.schippers.hfm.books.Bill
import ca.schippers.hfm.books.BillDraft
import ca.schippers.hfm.books.BillKind
import ca.schippers.hfm.books.Category
import ca.schippers.hfm.books.Occurrence
import ca.schippers.hfm.books.OccurrenceStatus
import ca.schippers.hfm.books.PaymentMethod
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.calc.schedule.BusinessDayAdjust
import ca.schippers.hfm.calc.schedule.Frequency
import ca.schippers.hfm.calc.schedule.MonthDay
import ca.schippers.hfm.calc.schedule.Recurrence
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat
import ca.schippers.hfm.money.sum
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import java.io.File
import javax.swing.JFileChooser

private enum class BillsTab { AGENDA, ALL, CALENDAR, SUBSCRIPTIONS, FORECAST }

/** Section 9: bills, reminders, calendar, subscriptions and cash flow forecast. */
@Composable
fun BillsScreen(model: BooksModel) {
    var tab by remember { mutableStateOf(BillsTab.AGENDA) }
    var editing by remember { mutableStateOf<Bill?>(null) }
    var creating by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf<Occurrence?>(null) }
    var settingAmount by remember { mutableStateOf<Occurrence?>(null) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.t("nav.bills"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { exportCalendar(model) }) { Text(model.t("bills.exportCalendar")) }
            Button(onClick = { creating = true }, modifier = Modifier.padding(start = 8.dp)) { Text(model.t("bills.add")) }
        }
        PrimaryTabRow(selectedTabIndex = tab.ordinal, modifier = Modifier.padding(vertical = 8.dp)) {
            for (t in BillsTab.entries) {
                Tab(selected = tab == t, onClick = { tab = t }, text = { Text(model.t("bills.tab.${t.name}")) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                BillsTab.AGENDA -> AgendaTab(model, onPay = { paying = it }, onAmount = { settingAmount = it }, onEdit = { editing = it })
                BillsTab.ALL -> AllBillsTab(model) { editing = it }
                BillsTab.CALENDAR -> CalendarTab(model)
                BillsTab.SUBSCRIPTIONS -> SubscriptionsTab(model) { editing = it }
                BillsTab.FORECAST -> ForecastTab(model)
            }
        }
    }

    if (creating) BillDialog(model, null) { creating = false }
    editing?.let { bill -> BillDialog(model, bill) { editing = null } }
    paying?.let { o -> PayDialog(model, o) { paying = null } }
    settingAmount?.let { o -> AmountDialog(model, o) { settingAmount = null } }
}

// --- Agenda (BILL-05) -----------------------------------------------------------------------------

@Composable
private fun AgendaTab(model: BooksModel, onPay: (Occurrence) -> Unit, onAmount: (Occurrence) -> Unit, onEdit: (Bill) -> Unit) {
    val books = model.books
    val today = today()
    val agenda = remember(model.revision) { books.bills.agenda(today, 30) }
    // BILL-07: payments that the forecast says would overdraw the paying account.
    val shortfalls = remember(model.revision) {
        books.bills.forecast(today, 30).flatMap { f -> f.shortfalls.mapNotNull { it.occurrence?.let { o -> o.bill.id to o.dueDate } } }.toSet()
    }
    val accounts = remember(model.revision) { books.accounts.list(includeClosed = true).associate { it.account.id to it.account.name } }

    LazyColumn {
        if (agenda.overdue.isEmpty() && agenda.dueToday.isEmpty() && agenda.upcoming.isEmpty()) {
            item { Text(model.t("bills.nothingDue"), Modifier.padding(8.dp)) }
        }
        listOf(
            "bills.overdue" to agenda.overdue,
            "bills.dueToday" to agenda.dueToday,
            "bills.upcoming" to agenda.upcoming,
        ).filter { it.second.isNotEmpty() }.forEach { (title, list) ->
            item { GroupTitle(model.t(title, list.size)) }
            items(list, key = { "${it.bill.id}-${it.dueDate}" }) { o ->
                OccurrenceRow(model, o, accounts, (o.bill.id to o.dueDate) in shortfalls) {
                    Button(onClick = { onPay(o) }) { Text(model.t(if (o.bill.kind == BillKind.INCOME) "bills.markReceived" else "bills.markPaid")) }
                    if (o.bill.amountKind != AmountKind.FIXED) OutlinedButton(onClick = { onAmount(o) }) { Text(model.t("bills.enterAmount")) }
                    TextButton(onClick = { model.act { books.bills.skip(o.bill.id, o.dueDate) } }) { Text(model.t("bills.skip")) }
                    TextButton(onClick = { onEdit(o.bill) }) { Text(model.t("common.edit")) }
                }
            }
        }
        if (agenda.paid.isNotEmpty()) {
            item { GroupTitle(model.t("bills.paid", agenda.paid.size)) }
            items(agenda.paid, key = { "p-${it.bill.id}-${it.dueDate}" }) { o ->
                OccurrenceRow(model, o, accounts, false) {
                    TextButton(onClick = { model.act { books.bills.unmarkPaid(o.bill.id, o.dueDate, deleteTransaction = true) } }) {
                        Text(model.t("bills.undoPaid"))
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun OccurrenceRow(model: BooksModel, o: Occurrence, accounts: Map<String, String>, shortfall: Boolean, actions: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(model.date(o.dueDate), Modifier.width(100.dp))
            Column(Modifier.weight(1f)) {
                Text(o.bill.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        model.t("paymentMethod.${o.bill.paymentMethod}"),
                        accounts[o.bill.accountId],
                        o.bill.transferAccountId?.let { "→ ${accounts[it]}" },
                        o.paidDate?.let { model.t("bills.paidOn", model.date(it)) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (shortfall) Text(model.t("bills.shortfall"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                (if (o.amountKnown) "" else "≈ ") + model.money(o.amount),
                Modifier.width(130.dp),
                color = if (o.bill.kind == BillKind.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Row(Modifier.width(470.dp), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}

// --- All bills ------------------------------------------------------------------------------------

@Composable
private fun AllBillsTab(model: BooksModel, onEdit: (Bill) -> Unit) {
    val bills = remember(model.revision) { model.books.bills.list(includeInactive = true) }
    LazyColumn {
        if (bills.isEmpty()) item { Text(model.t("bills.none"), Modifier.padding(8.dp)) }
        items(bills, key = { it.id }) { bill ->
            val next = remember(model.revision, bill.id) { bill.recurrence.next(bill.startDate, today(), bill.endDate) }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(bill.name + if (!bill.active) " (${model.t("bills.inactive")})" else "", fontWeight = FontWeight.Medium)
                    Text(
                        listOfNotNull(model.t("billKind.${bill.kind}"), describeRecurrence(model, bill), next?.let { model.t("bills.next", model.date(it)) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text((if (bill.amountKind == AmountKind.FIXED) "" else "≈ ") + model.money(bill.amount), Modifier.width(130.dp))
                TextButton(onClick = { onEdit(bill) }) { Text(model.t("common.edit")) }
            }
            HorizontalDivider()
        }
    }
}

// --- Calendar (BILL-05) ---------------------------------------------------------------------------

@Composable
private fun CalendarTab(model: BooksModel) {
    var month by remember { mutableStateOf(today().let { LocalDate(it.year, it.month, 1) }) }
    val end = month.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
    val occurrences = remember(model.revision, month) { model.books.bills.occurrences(month, end).groupBy { it.dueDate } }
    val locale = model.language.locale

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = month.minus(DatePeriod(months = 1)) }) { Text("◀") }
            Text(
                java.time.YearMonth.of(month.year, month.month.ordinal + 1)
                    .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", locale)).replaceFirstChar { it.titlecase(locale) },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(220.dp),
            )
            TextButton(onClick = { month = month.plus(DatePeriod(months = 1)) }) { Text("▶") }
        }
        Row {
            for (dow in 0 until 7) {
                val name = java.time.DayOfWeek.of(dow + 1).getDisplayName(java.time.format.TextStyle.SHORT, locale)
                Text(name, Modifier.weight(1f).padding(4.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
        val firstOffset = month.dayOfWeek.ordinal // Monday = 0
        val days = month.daysUntil(end) + 1
        val cells = firstOffset + days
        for (week in 0 until (cells + 6) / 7) {
            Row(Modifier.fillMaxWidth()) {
                for (dow in 0 until 7) {
                    val index = week * 7 + dow - firstOffset
                    Box(Modifier.weight(1f).height(96.dp).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(4.dp)) {
                        if (index in 0 until days) {
                            val date = month.plus(DatePeriod(days = index))
                            Column {
                                Text(
                                    date.day.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (date == today()) FontWeight.Bold else FontWeight.Normal,
                                    color = if (date == today()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                for (o in occurrences[date].orEmpty().take(3)) {
                                    Text(
                                        "${o.bill.name} ${MoneyFormat.formatAmount(o.amount, locale)}",
                                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = when (o.status) {
                                            OccurrenceStatus.PAID -> MaterialTheme.colorScheme.outline
                                            OccurrenceStatus.SKIPPED -> MaterialTheme.colorScheme.outlineVariant
                                            OccurrenceStatus.DUE -> if (date < today()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                }
                                if (occurrences[date].orEmpty().size > 3) Text("+${occurrences[date]!!.size - 3}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Subscriptions (BILL-10) ----------------------------------------------------------------------

@Composable
private fun SubscriptionsTab(model: BooksModel, onEdit: (Bill) -> Unit) {
    val subs = remember(model.revision) { model.books.bills.subscriptions(today()) }
    LazyColumn {
        if (subs.isEmpty()) item { Text(model.t("bills.noSubscriptions"), Modifier.padding(8.dp)) }
        items(subs, key = { it.bill.id }) { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.bill.name, fontWeight = FontWeight.Medium)
                    Text(
                        listOfNotNull(
                            describeRecurrence(model, s.bill) + " · " + model.money(s.bill.amount),
                            s.nextDue?.let { model.t("bills.next", model.date(it)) },
                            s.bill.cancelBy?.let { model.t("bills.cancelBy", model.date(it)) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(model.t("bills.perYear", model.money(s.annualCost)), Modifier.width(180.dp), fontWeight = FontWeight.Bold)
                TextButton(onClick = { onEdit(s.bill) }) { Text(model.t("common.edit")) }
            }
            HorizontalDivider()
        }
        if (subs.isNotEmpty()) {
            item {
                subs.groupBy { it.annualCost.currency }.forEach { (currency, list) ->
                    Text(
                        model.t("bills.subscriptionsTotal", model.money(list.map { it.annualCost }.sum(currency))),
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

// --- Forecast (BILL-07, BILL-08) ------------------------------------------------------------------

@Composable
private fun ForecastTab(model: BooksModel) {
    var days by remember { mutableStateOf(30) }
    val forecast = remember(model.revision, days) { model.books.bills.forecast(today(), days) }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (d in listOf(30, 60, 90)) {
                if (d == days) Button(onClick = {}) { Text(model.t("bills.days", d)) } else OutlinedButton(onClick = { days = d }) { Text(model.t("bills.days", d)) }
            }
        }
        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(forecast.filter { it.points.size > 1 || it.start.isNegative }, key = { it.account.id }) { f ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row {
                            Text(f.account.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(model.t("bills.forecastSummary", model.money(f.start), model.money(f.points.last().balance), model.money(f.lowest)))
                        }
                        if (f.shortfalls.isNotEmpty()) {
                            Text(model.t("bills.forecastWarning", f.shortfalls.size), color = MaterialTheme.colorScheme.error)
                        }
                        for (p in f.points.drop(1)) {
                            Row {
                                Text(model.date(p.date), Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall)
                                Text(p.occurrence?.bill?.name.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                Text(
                                    model.money(p.balance), style = MaterialTheme.typography.bodySmall,
                                    color = if (p.balance.isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Dialogs --------------------------------------------------------------------------------------

/** BILL-06: confirm the payment date and amount; the transaction is created in the paying account. */
@Composable
private fun PayDialog(model: BooksModel, o: Occurrence, onClose: () -> Unit) {
    val locale = model.language.locale
    var date by remember { mutableStateOf(today().toString()) }
    var amount by remember { mutableStateOf(MoneyFormat.formatAmount(o.amount, locale)) }
    FormDialog(model.t(if (o.bill.kind == BillKind.INCOME) "bills.markReceived" else "bills.markPaid") + " · " + o.bill.name, model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            val paidDate = runCatching { LocalDate.parse(date.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }
            model.books.bills.markPaid(o.bill.id, o.dueDate, paidDate, parseAmount(amount, o.amount.currency, locale))
        }
        if (ok != null) onClose()
    }) {
        Text(model.t("bills.payExplain", model.date(o.dueDate)), style = MaterialTheme.typography.bodySmall)
        DateInput(model.t("bills.paidDate"), date, Modifier.fillMaxWidth()) { date = it }
        AmountInput(model.t("register.amount"), amount, o.amount.currency, locale, Modifier.fillMaxWidth(), model::money) { amount = it }
    }
}

/** The actual amount of a variable bill, from the paper or e-bill. */
@Composable
private fun AmountDialog(model: BooksModel, o: Occurrence, onClose: () -> Unit) {
    val locale = model.language.locale
    var amount by remember { mutableStateOf(if (o.amountKnown) MoneyFormat.formatAmount(o.amount, locale) else "") }
    FormDialog(model.t("bills.enterAmount") + " · " + o.bill.name, model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            val value = parseAmount(amount, o.amount.currency, locale) ?: throw ValidationException("error.amountRequired")
            model.books.bills.setAmount(o.bill.id, o.dueDate, value)
        }
        if (ok != null) onClose()
    }) {
        AmountInput(model.t("register.amount"), amount, o.amount.currency, locale, Modifier.fillMaxWidth(), model::money) { amount = it }
    }
}

/** Repetition choices offered in the bill and event editors (BILL-02, CAL-02). */
internal enum class Repeat(val recurrence: Recurrence?) {
    ONCE(Recurrence(Frequency.ONCE)),
    WEEKLY(Recurrence.WEEKLY),
    BI_WEEKLY(Recurrence.BI_WEEKLY),
    SEMI_MONTHLY(null),
    MONTHLY(Recurrence.MONTHLY),
    QUARTERLY(Recurrence.QUARTERLY),
    SEMI_ANNUAL(Recurrence.SEMI_ANNUAL),
    ANNUAL(Recurrence.ANNUAL),
    EVERY_N_DAYS(null),
    EVERY_N_WEEKS(null),
    EVERY_N_MONTHS(null),
    ;

    companion object {
        fun of(r: Recurrence): Repeat = when {
            r.frequency == Frequency.ONCE -> ONCE
            r.frequency == Frequency.SEMI_MONTHLY -> SEMI_MONTHLY
            r.frequency == Frequency.DAILY -> EVERY_N_DAYS
            r.frequency == Frequency.WEEKLY && r.interval == 1 -> WEEKLY
            r.frequency == Frequency.WEEKLY && r.interval == 2 -> BI_WEEKLY
            r.frequency == Frequency.WEEKLY -> EVERY_N_WEEKS
            r.interval == 1 -> MONTHLY
            r.interval == 3 -> QUARTERLY
            r.interval == 6 -> SEMI_ANNUAL
            r.interval == 12 -> ANNUAL
            else -> EVERY_N_MONTHS
        }
    }
}

private fun describeRecurrence(model: BooksModel, bill: Bill): String = describeRecurrence(model, bill.recurrence)

internal fun describeRecurrence(model: BooksModel, r: Recurrence): String {
    val repeat = Repeat.of(r)
    val base = when (repeat) {
        Repeat.EVERY_N_DAYS, Repeat.EVERY_N_WEEKS, Repeat.EVERY_N_MONTHS -> model.t("repeat.${repeat.name}.n", r.interval)
        else -> model.t("repeat.${repeat.name}")
    }
    return if (r.monthDay != MonthDay.SAME_DAY) "$base (${model.t("monthDay.${r.monthDay}")})" else base
}

/** Add or edit a bill, income or scheduled transfer (BILL-01, BILL-02, BILL-04, BILL-10). */
@Composable
private fun BillDialog(model: BooksModel, existing: Bill?, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val accounts = remember { books.accounts.list().map { it.account } }
    val tree = remember { books.categories.tree() }
    var kind by remember { mutableStateOf(existing?.kind ?: BillKind.BILL) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var payee by remember { mutableStateOf(existing?.payeeName.orEmpty()) }
    var payeeAccount by remember { mutableStateOf(existing?.payeeAccountNumber.orEmpty()) }
    var accountId by remember { mutableStateOf(existing?.accountId ?: accounts.firstOrNull()?.id) }
    var transferId by remember { mutableStateOf(existing?.transferAccountId) }
    var amount by remember { mutableStateOf(existing?.let { MoneyFormat.formatAmount(it.amount, locale) }.orEmpty()) }
    var amountKind by remember { mutableStateOf(existing?.amountKind ?: AmountKind.FIXED) }
    var method by remember { mutableStateOf(existing?.paymentMethod ?: PaymentMethod.ONLINE) }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var repeat by remember { mutableStateOf(existing?.let { Repeat.of(it.recurrence) } ?: Repeat.MONTHLY) }
    var interval by remember { mutableStateOf(existing?.recurrence?.interval?.toString() ?: "1") }
    var monthDay by remember { mutableStateOf(existing?.recurrence?.monthDay ?: MonthDay.SAME_DAY) }
    var secondDay by remember { mutableStateOf(existing?.recurrence?.secondDay?.toString() ?: "0") }
    var adjust by remember { mutableStateOf(existing?.recurrence?.adjust ?: BusinessDayAdjust.NONE) }
    var start by remember { mutableStateOf(existing?.startDate?.toString() ?: today().toString()) }
    var end by remember { mutableStateOf(existing?.endDate?.toString().orEmpty()) }
    var reminders by remember { mutableStateOf(existing?.reminderDays?.joinToString(", ") ?: "7, 1") }
    var subscription by remember { mutableStateOf(existing?.isSubscription ?: false) }
    var cancelBy by remember { mutableStateOf(existing?.cancelBy?.toString().orEmpty()) }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    var confirmDelete by remember { mutableStateOf(false) }
    val account = accounts.firstOrNull { it.id == accountId }

    fun recurrence(): Recurrence {
        val n = interval.trim().toIntOrNull()?.takeIf { it >= 1 } ?: throw ValidationException("error.invalidNumber")
        val monthly = repeat in setOf(Repeat.MONTHLY, Repeat.QUARTERLY, Repeat.SEMI_ANNUAL, Repeat.ANNUAL, Repeat.EVERY_N_MONTHS)
        return when (repeat) {
            Repeat.SEMI_MONTHLY -> Recurrence(Frequency.SEMI_MONTHLY, secondDay = secondDay.trim().toIntOrNull()?.takeIf { it in 0..31 } ?: throw ValidationException("error.dayOfMonth"), adjust = adjust)
            Repeat.EVERY_N_DAYS -> Recurrence(Frequency.DAILY, n, adjust = adjust)
            Repeat.EVERY_N_WEEKS -> Recurrence(Frequency.WEEKLY, n, adjust = adjust)
            Repeat.EVERY_N_MONTHS -> Recurrence(Frequency.MONTHLY, n, monthDay, adjust = adjust)
            else -> repeat.recurrence!!.copy(monthDay = if (monthly) monthDay else MonthDay.SAME_DAY, adjust = adjust)
        }
    }

    FormDialog(
        model.t(if (existing == null) "bills.add" else "bills.edit"), model.t("common.save"), model.t("common.cancel"),
        canSave = name.isNotBlank() && account != null,
        onDismiss = onClose,
        onSave = {
            val ok = model.act {
                fun date(text: String) = text.trim().ifEmpty { null }?.let { runCatching { LocalDate.parse(it) }.getOrElse { throw ValidationException("error.invalidDate") } }
                val value = parseAmount(amount, account!!.currency, locale) ?: Money.zero(account.currency)
                val days = reminders.split(',', ' ').mapNotNull { it.trim().ifEmpty { null } }.map { it.toIntOrNull() ?: throw ValidationException("error.reminderDays") }
                val draft = BillDraft(
                    kind, name, value, account.id, recurrence(), date(start) ?: throw ValidationException("error.invalidDate"),
                    payee.ifBlank { null }, payeeAccount.ifBlank { null }, amountKind, if (kind == BillKind.TRANSFER) transferId else null,
                    method, if (kind == BillKind.TRANSFER) null else categoryId, date(end), days, subscription, null, date(cancelBy), null,
                )
                if (existing == null) {
                    books.bills.create(draft)
                } else {
                    books.bills.update(
                        existing.copy(
                            kind = draft.kind, name = draft.name, amount = draft.amount, recurrence = draft.recurrence, startDate = draft.startDate,
                            payeeName = draft.payeeName, payeeAccountNumber = draft.payeeAccountNumber, amountKind = draft.amountKind,
                            transferAccountId = draft.transferAccountId, paymentMethod = draft.paymentMethod, categoryId = draft.categoryId,
                            endDate = draft.endDate, reminderDays = draft.reminderDays, isSubscription = draft.isSubscription,
                            cancelBy = draft.cancelBy, active = active,
                        ),
                    )
                }
            }
            if (ok != null) onClose()
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("bills.kind"), BillKind.entries, kind, { model.t("billKind.$it") }, Modifier.weight(1f)) { kind = it }
                TextInput(model.t("bills.name"), name, Modifier.weight(2f)) { name = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t(if (kind == BillKind.INCOME) "bills.depositAccount" else "bills.payingAccount"), accounts, account, { it.name }, Modifier.weight(1f), enabled = existing == null) { accountId = it.id }
                if (kind == BillKind.TRANSFER) {
                    Picker(model.t("bills.toAccount"), accounts.filter { it.id != accountId }, accounts.firstOrNull { it.id == transferId }, { it.name }, Modifier.weight(1f)) { transferId = it.id }
                } else {
                    Picker(
                        model.t("register.category"), listOf<Pair<Category, Int>?>(null) + tree, tree.firstOrNull { it.first.id == categoryId },
                        { it?.first?.name(model.language) ?: model.t("common.none") }, Modifier.weight(1f), indent = { it?.second ?: 0 },
                    ) { categoryId = it?.first?.id }
                }
            }
            if (kind != BillKind.TRANSFER) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput(model.t("register.payee"), payee, Modifier.weight(1f)) { payee = it }
                    TextInput(model.t("bills.payeeAccount"), payeeAccount, Modifier.weight(1f)) { payeeAccount = it }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (account != null) AmountInput(model.t("register.amount"), amount, account.currency, locale, Modifier.weight(1f), model::money) { amount = it }
                Picker(model.t("bills.amountKind"), AmountKind.entries, amountKind, { model.t("amountKind.$it") }, Modifier.weight(1f)) { amountKind = it }
                Picker(model.t("bills.method"), PaymentMethod.entries, method, { model.t("paymentMethod.$it") }, Modifier.weight(1f)) { method = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("bills.repeat"), Repeat.entries, repeat, { model.t("repeat.$it") }, Modifier.weight(1f)) { repeat = it }
                when (repeat) {
                    Repeat.EVERY_N_DAYS, Repeat.EVERY_N_WEEKS, Repeat.EVERY_N_MONTHS -> TextInput(model.t("bills.interval"), interval, Modifier.weight(0.6f)) { interval = it }
                    Repeat.SEMI_MONTHLY -> TextInput(model.t("bills.secondDay"), secondDay, Modifier.weight(0.6f), supporting = model.t("bills.secondDay.hint")) { secondDay = it }
                    else -> Unit
                }
                if (repeat in setOf(Repeat.MONTHLY, Repeat.QUARTERLY, Repeat.SEMI_ANNUAL, Repeat.ANNUAL, Repeat.EVERY_N_MONTHS)) {
                    Picker(model.t("bills.monthDay"), MonthDay.entries, monthDay, { model.t("monthDay.$it") }, Modifier.weight(1f)) { monthDay = it }
                }
            }
            Picker(model.t("bills.adjust"), BusinessDayAdjust.entries, adjust, { model.t("adjust.$it") }) { adjust = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(model.t("bills.start"), start, Modifier.weight(1f)) { start = it }
                DateInput(model.t("bills.end"), end, Modifier.weight(1f)) { end = it }
            }
            TextInput(model.t("bills.reminders"), reminders, supporting = model.t("bills.reminders.hint")) { reminders = it }
            LabeledCheckbox(model.t("bills.subscription"), subscription) { subscription = it }
            if (subscription) DateInput(model.t("bills.cancelByDate"), cancelBy, Modifier.fillMaxWidth()) { cancelBy = it }
            if (existing != null) {
                LabeledCheckbox(model.t("bills.active"), active) { active = it }
                TextButton(onClick = { confirmDelete = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmDelete && existing != null) {
        FormDialog(model.t("bills.delete.title"), model.t("common.delete"), model.t("common.cancel"), onDismiss = { confirmDelete = false }, onSave = {
            if (model.act { books.bills.delete(existing.id) } != null) {
                confirmDelete = false
                onClose()
            }
        }) { Text(model.t("bills.delete.body", existing.name)) }
    }
}

/** BILL-12: saves the next twelve months of due dates as an iCalendar file. */
private fun exportCalendar(model: BooksModel) {
    val chooser = JFileChooser().apply {
        dialogTitle = model.t("bills.exportCalendar")
        selectedFile = File("bills.ics")
    }
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return
    model.act {
        val ics = model.books.bills.iCalendar(today(), today().plus(DatePeriod(months = 12))) { o ->
            "${o.bill.name} · ${(if (o.amountKnown) "" else "≈ ")}${model.money(o.amount)}"
        }
        chooser.selectedFile.writeText(ics, Charsets.UTF_8)
    }
}
