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
import ca.schippers.hfm.books.BillHistoryEntry
import ca.schippers.hfm.books.BillKind
import ca.schippers.hfm.books.Category
import ca.schippers.hfm.books.LinkTarget
import ca.schippers.hfm.books.Occurrence
import ca.schippers.hfm.books.OccurrenceStatus
import ca.schippers.hfm.books.PaymentMethod
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.calc.rules.LeadTimes
import ca.schippers.hfm.calc.rules.Thresholds
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
    val agenda = remember(model.revision) { books.bills.agenda(today) }
    // BILL-07: payments that the forecast says would overdraw the paying account.
    val shortfalls = remember(model.revision) {
        books.bills.forecast(today).flatMap { f -> f.shortfalls.mapNotNull { it.occurrence?.let { o -> o.bill.id to o.dueDate } } }.toSet()
    }
    val accounts = remember(model.revision) { books.accounts.list(includeClosed = true).associate { it.account.id to it.account.name } }
    // BILL-09: each amount beside the usual one and the same month last year.
    val comparisons = remember(model.revision) {
        (agenda.overdue + agenda.dueToday + agenda.upcoming + agenda.paid).associate { o -> (o.bill.id to o.dueDate) to runCatching { books.bills.compare(o) }.getOrNull() }
    }

    LazyColumn {
        if (agenda.overdue.isEmpty() && agenda.dueToday.isEmpty() && agenda.upcoming.isEmpty()) {
            item { Text(model.t("bills.nothingDue", LeadTimes.billsAgenda(today)), Modifier.padding(8.dp)) }
        }
        listOf(
            "bills.overdue" to agenda.overdue,
            "bills.dueToday" to agenda.dueToday,
            "bills.upcoming" to agenda.upcoming,
        ).filter { it.second.isNotEmpty() }.forEach { (title, list) ->
            item { GroupTitle(model.t(title, list.size, LeadTimes.billsAgenda(today))) }
            items(list, key = { "${it.bill.id}-${it.dueDate}" }) { o ->
                OccurrenceRow(model, o, accounts, (o.bill.id to o.dueDate) in shortfalls, comparisons[o.bill.id to o.dueDate]) {
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
                OccurrenceRow(model, o, accounts, false, comparisons[o.bill.id to o.dueDate]) {
                    // A payment already reconciled is deleted only after the user confirms it.
                    TextButton(onClick = {
                        model.act(retryConfirmed = { books.bills.unmarkPaid(o.bill.id, o.dueDate, deleteTransaction = true, confirmReconciled = true) }) {
                            books.bills.unmarkPaid(o.bill.id, o.dueDate, deleteTransaction = true)
                        }
                    }) {
                        Text(model.t("bills.undoPaid"))
                    }
                }
            }
        }
        if (agenda.skipped.isNotEmpty()) {
            item { GroupTitle(model.t("bills.skipped", agenda.skipped.size)) }
            items(agenda.skipped, key = { "s-${it.bill.id}-${it.dueDate}" }) { o ->
                OccurrenceRow(model, o, accounts, false, null) {
                    TextButton(onClick = { model.act { books.bills.unskip(o.bill.id, o.dueDate) } }) { Text(model.t("bills.unskip")) }
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
private fun OccurrenceRow(model: BooksModel, o: Occurrence, accounts: Map<String, String>, shortfall: Boolean, comparison: BillHistoryEntry?, actions: @Composable () -> Unit) {
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
                comparison?.let { c -> comparisonText(model, c)?.let { Text(it, style = MaterialTheme.typography.bodySmall) } }
                if (comparison?.unusual == true) Text(model.t("bills.unusual", Thresholds.unusualBill(today()).movePointRight(2).stripTrailingZeros().toPlainString()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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

/** BILL-09: "usually 131,00 $ · same month last year 128,40 $", or null when there is no history yet. */
private fun comparisonText(model: BooksModel, c: BillHistoryEntry): String? = listOfNotNull(
    c.average?.let { model.t("bills.usually", model.money(it)) },
    c.sameMonthLastYear?.let { model.t("bills.lastYear", model.money(it)) },
).takeIf { it.isNotEmpty() }?.joinToString(" · ")

/** BILL-09: the amounts paid for a bill, newest first, each beside the usual and last year's. */
@Composable
internal fun BillHistory(model: BooksModel, bill: Bill) {
    val history = remember(model.revision, bill.id) { runCatching { model.books.bills.history(bill.id) }.getOrDefault(emptyList()) }
    Text(model.t("bills.history"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    if (history.isEmpty()) Text(model.t("bills.history.none"), style = MaterialTheme.typography.bodySmall)
    for (h in history.take(HISTORY_SHOWN)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.date(h.occurrence.dueDate), Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall)
            Text(model.money(h.occurrence.amount), Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(comparisonText(model, h).orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            if (h.unusual) Text(model.t("bills.unusualShort"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Paid amounts shown in the bill form; a year of monthly bills. */
private const val HISTORY_SHOWN = 12

// --- All bills ------------------------------------------------------------------------------------

@Composable
private fun AllBillsTab(model: BooksModel, onEdit: (Bill) -> Unit) {
    val bills = remember(model.revision) { model.books.bills.list(includeInactive = true) }
    val lists = remember(model.revision) { model.books.billLists.lists() }
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
                    // BILL-13, BILL-15: what the bill is for, and the account number, masked.
                    val about = listOfNotNull(classificationText(model, lists, bill), bill.payeeAccountMasked?.let { model.t("bills.accountNo", it) })
                    if (about.isNotEmpty()) Text(about.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
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
            SymbolButton(model.t("common.previousMonth"), "◀") { month = month.minus(DatePeriod(months = 1)) }
            Text(
                java.time.YearMonth.of(month.year, month.month.ordinal + 1)
                    .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", locale)).replaceFirstChar { it.titlecase(locale) },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(220.dp),
            )
            SymbolButton(model.t("common.nextMonth"), "▶") { month = month.plus(DatePeriod(months = 1)) }
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
    var days by remember { mutableStateOf(LeadTimes.billsForecast(today())) }
    val forecast = remember(model.revision, days) { model.books.bills.forecast(today(), days) }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (d in (listOf(30, 60, 90) + LeadTimes.billsForecast(today())).distinct().sorted()) {
                if (d == days) Button(onClick = {}) { Text(model.t("bills.days", d)) } else OutlinedButton(onClick = { days = d }) { Text(model.t("bills.days", d)) }
            }
        }
        LazyColumn(Modifier.padding(top = 8.dp)) {
            // BILL-08: the bank accounts' balances day by day, with a line at zero.
            item(key = "chart") {
                val flow = remember(model.revision, days) { model.books.bills.cashFlow(today(), days) }
                if (flow.accounts.any { it.points.size > 1 }) CashFlowChart(model, flow)
            }
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
internal fun PayDialog(model: BooksModel, o: Occurrence, onClose: () -> Unit) {
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
