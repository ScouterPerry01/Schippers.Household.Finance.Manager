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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
import ca.schippers.hfm.books.AccountSummary
import ca.schippers.hfm.books.LoanChangeKind
import ca.schippers.hfm.books.LoanDetails
import ca.schippers.hfm.books.LoanService
import ca.schippers.hfm.books.LoanStatus
import ca.schippers.hfm.books.RateType
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.calc.loan.Amortization
import ca.schippers.hfm.calc.loan.Comparison
import ca.schippers.hfm.calc.loan.Compounding
import ca.schippers.hfm.calc.loan.DatedRow
import ca.schippers.hfm.calc.loan.LoanTerms
import ca.schippers.hfm.calc.loan.PaymentFrequency
import ca.schippers.hfm.calc.rules.LeadTimes
import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat
import ca.schippers.hfm.money.sum
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

private sealed interface LoanAction {
    data object Terms : LoanAction
    data object Payment : LoanAction
    data object Prepayment : LoanAction
    data class Rate(val renewal: Boolean) : LoanAction
    data object PaymentChange : LoanAction
    data object WhatIf : LoanAction
}

/** LN-01 to LN-06: loans and mortgages, their schedules, payments, renewals and what-ifs. */
@Composable
fun LoansScreen(model: BooksModel) {
    val loans = remember(model.revision) { runCatching { model.books.loans.accounts() }.getOrDefault(emptyList()) }
    // Opened from an account's register, the loan shown is that account.
    var selectedId by remember { mutableStateOf(model.selectedAccountId) }
    val selected = loans.firstOrNull { it.first.account.id == selectedId } ?: loans.firstOrNull()

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(300.dp).fillMaxHeight().padding(12.dp)) {
            Text(model.t("nav.loans"), style = MaterialTheme.typography.titleLarge)
            Text(model.t("loans.explain"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(Modifier.weight(1f)) {
                if (loans.isEmpty()) item { Text(model.t("loans.none"), Modifier.padding(8.dp)) }
                items(loans, key = { it.first.account.id }) { (summary, details) ->
                    val isSelected = summary.account.id == selected?.first?.account?.id
                    Row(Modifier.fillMaxWidth().clickable { selectedId = summary.account.id }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(summary.account.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            Text(
                                if (details == null) model.t("loans.noTerms") else model.t("accountType.${summary.account.type}"),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (details == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                            )
                        }
                        MoneyText(model, -summary.balance, bold = isSelected)
                    }
                }
            }
        }
        VerticalDivider()
        if (selected == null) {
            Text(model.t("loans.select"), Modifier.padding(24.dp))
        } else {
            LoanDetail(model, selected.first, selected.second)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoanDetail(model: BooksModel, summary: AccountSummary, details: LoanDetails?) {
    val books = model.books
    val account = summary.account
    var action by remember(account.id) { mutableStateOf<LoanAction?>(null) }
    var tab by remember(account.id) { mutableStateOf(0) }
    val status = remember(model.revision, account.id) { details?.let { runCatching { books.loans.status(account.id, today()) }.getOrNull() } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(account.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    listOfNotNull(
                        model.t("accountType.${account.type}"),
                        details?.let { model.t("rateType.${it.rateType}") },
                        details?.let { model.t("frequency.${it.frequency}") },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = { action = LoanAction.Terms }) { Text(model.t(if (details == null) "loans.enterTerms" else "loans.editTerms")) }
        }
        if (details == null || status == null) {
            Text(model.t("loans.termsNeeded"), Modifier.padding(vertical = 16.dp))
        } else {
            FlowRow(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(model.t("loans.owed"), model.money(status.owed))
                Stat(model.t("loans.scheduled"), model.money(status.scheduledBalance))
                Stat(model.t("loans.rate"), percent(status.annualRate, model.language.locale))
                Stat(model.t("loans.payment"), model.money(status.payment))
                if (status.totalPayment != status.payment) Stat(model.t("loans.totalPayment"), model.money(status.totalPayment))
                Stat(model.t("loans.nextPayment"), status.nextPayment?.let(model::date) ?: "–")
                Stat(model.t("loans.payoff"), status.payoffDate?.let(model::date) ?: "–")
                Stat(model.t("loans.interestLeft"), model.money(status.interestRemaining))
                if (status.interestSaved.isPositive) Stat(model.t("loans.interestSaved"), model.t("loans.savedValue", model.money(status.interestSaved), status.monthsSooner))
                status.termEnd?.let { end -> Stat(model.t("loans.renewal"), "${model.date(end)} (${daysText(model, status.daysToRenewal ?: 0)})") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { action = LoanAction.Payment }, enabled = status.owed.isPositive) { Text(model.t("loans.recordPayment")) }
                OutlinedButton(onClick = { action = LoanAction.Prepayment }) { Text(model.t("loans.prepayment")) }
                if (details.termEnd != null) OutlinedButton(onClick = { action = LoanAction.Rate(renewal = true) }) { Text(model.t("loans.renew")) }
                OutlinedButton(onClick = { action = LoanAction.Rate(renewal = false) }) { Text(model.t("loans.rateChange")) }
                OutlinedButton(onClick = { action = LoanAction.PaymentChange }) { Text(model.t("loans.paymentChange")) }
                OutlinedButton(onClick = { action = LoanAction.WhatIf }, enabled = status.owed.isPositive) { Text(model.t("loans.whatIf")) }
            }
            PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 12.dp)) {
                Tab(tab == 0, { tab = 0 }, text = { Text(model.t("loans.byYear")) })
                Tab(tab == 1, { tab = 1 }, text = { Text(model.t("loans.everyPayment")) })
                Tab(tab == 2, { tab = 2 }, text = { Text(model.t("loans.changes")) })
            }
            when (tab) {
                0, 1 -> ScheduleView(model, account, status, byYear = tab == 0)
                else -> ChangesView(model, account)
            }
        }
    }

    when (val a = action) {
        LoanAction.Terms -> LoanTermsDialog(model, account, details) { action = null }
        LoanAction.Payment -> PaymentDialog(model, account, details!!) { action = null }
        LoanAction.Prepayment -> PrepaymentDialog(model, account, details!!) { action = null }
        is LoanAction.Rate -> RateDialog(model, account, details!!, status!!, a.renewal) { action = null }
        LoanAction.PaymentChange -> PaymentChangeDialog(model, account, status!!) { action = null }
        LoanAction.WhatIf -> WhatIfDialog(model, account, status!!) { action = null }
        null -> Unit
    }
}

private fun percent(rate: BigDecimal, locale: Locale): String =
    java.text.NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 2; maximumFractionDigits = 3 }.format(rate.movePointRight(2)) + " %"

private fun daysText(model: BooksModel, days: Int) = if (days < 0) model.t("renewal.overdue", -days) else model.t("renewal.inDays", days)

// --- Schedule (LN-02) -----------------------------------------------------------------------------

@Composable
private fun ScheduleView(model: BooksModel, account: Account, status: LoanStatus, byYear: Boolean) {
    val projection = remember(model.revision, account.id) { model.books.loans.projection(account.id) }
    val c = account.currency
    val columns = if (byYear) {
        listOf(model.t("loans.year"), model.t("loans.payments"), model.t("loans.paid"), model.t("loans.interest"), model.t("loans.principal"), model.t("loans.prepaid"), model.t("loans.balance"))
    } else {
        listOf("#", model.t("report.date"), model.t("loans.paid"), model.t("loans.interest"), model.t("loans.principal"), model.t("loans.prepaid"), model.t("loans.balance"))
    }
    val rows: List<List<Any?>> = remember(projection, byYear) {
        if (byYear) {
            projection.rows.groupBy { it.date.year }.map { (year, list) ->
                listOf(year.toString(), list.size.toString(), list.map { it.payment }.sum(c), list.map { it.interest }.sum(c), list.map { it.principal }.sum(c), list.map { it.prepayment }.sum(c), list.last().balance)
            }
        } else {
            projection.rows.map { r: DatedRow -> listOf(r.number.toString(), r.date, r.payment, r.interest, r.principal, r.prepayment, r.balance) }
        }
    }
    val table = ReportTable(model.t("loans.scheduleOf", account.name), model.t("loans.scheduleSubtitle", model.date(today())), columns, rows)
    val locale = model.language.locale
    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(model.t("loans.totals", projection.rows.size, model.money(projection.totalInterest)), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        for (format in ExportFormat.entries) {
            OutlinedButton(onClick = { model.act { ReportExport.save(table, format, locale, model.t("report.export")) } }) { Text(model.t("report.export.$format")) }
        }
        OutlinedButton(onClick = { model.act { ReportExport.print(table, locale) } }) { Text(model.t("report.print")) }
    }
    Row(Modifier.fillMaxWidth()) {
        columns.forEachIndexed { i, name ->
            Text(name, Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = if (i >= 2) TextAlign.End else TextAlign.Start, style = MaterialTheme.typography.bodySmall)
        }
    }
    HorizontalDivider()
    val next = status.nextPayment
    // Opens at the current year or the next payment, not at the loan's first payment.
    val currentIndex = if (byYear) rows.indexOfFirst { it[0] == today().year.toString() } else rows.indexOfFirst { it[1] == next }
    val listState = remember(projection, byYear) { LazyListState((currentIndex - 2).coerceAtLeast(0)) }
    LazyColumn(state = listState) {
        items(rows) { row ->
            // The next payment (or the current year) stands out; past payments are dimmed.
            val date = row[1] as? LocalDate
            val current = if (byYear) row[0] == today().year.toString() else date == next
            val past = !byYear && date != null && date < today()
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                row.forEachIndexed { i, cell ->
                    Text(
                        ReportExport.text(cell, locale), Modifier.weight(1f),
                        textAlign = if (i >= 2) TextAlign.End else TextAlign.Start,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        color = if (past) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

// --- Changes (LN-03) ------------------------------------------------------------------------------

@Composable
private fun ChangesView(model: BooksModel, account: Account) {
    val changes = remember(model.revision, account.id) { model.books.loans.changes(account.id) }
    Column(Modifier.padding(top = 8.dp)) {
        if (changes.isEmpty()) Text(model.t("loans.noChanges"), Modifier.padding(8.dp))
        for (ch in changes.asReversed()) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(model.date(ch.date), Modifier.width(110.dp))
                Text(model.t(if (ch.renewal) "loanChange.RENEWAL" else "loanChange.${ch.kind}"), Modifier.width(200.dp))
                Text(
                    when (ch.kind) {
                        LoanChangeKind.RATE_CHANGE -> percent(ch.annualRate!!, model.language.locale) +
                            if (ch.recalculate) " · " + model.t("loans.recalculated") else " · " + model.t("loans.paymentKept")
                        else -> ch.amount?.let(model::money).orEmpty()
                    },
                    Modifier.weight(1f),
                )
                Text(ch.notes.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { model.act { model.books.loans.deleteChange(account.id, ch.id) } }) { Text(model.t("common.delete")) }
            }
            HorizontalDivider()
        }
        if (changes.any { it.kind == LoanChangeKind.PREPAYMENT && it.transactionId != null }) {
            Text(model.t("loans.deleteKeepsMoney"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// --- Dialogs --------------------------------------------------------------------------------------

private fun rate(text: String, locale: Locale): BigDecimal? =
    text.trim().removeSuffix("%").trim().ifEmpty { null }?.let { MoneyFormat.parseDecimal(it, locale).movePointLeft(2) }

private fun pct(v: BigDecimal?): String = v?.movePointRight(2)?.stripTrailingZeros()?.toPlainString().orEmpty()

private fun date(text: String): LocalDate? = text.trim().ifEmpty { null }?.let { runCatching { LocalDate.parse(it) }.getOrElse { throw ValidationException("error.invalidDate") } }

/** Accounts a loan can be paid from: open bank and credit accounts in the same currency. */
private fun payingAccounts(model: BooksModel, currency: Currency): List<Account> =
    model.books.accounts.list().map { it.account }.filter { it.currency == currency && it.type.kind in setOf(AccountKind.BANK, AccountKind.CREDIT) }

/** LN-01, LN-04, LN-05: the loan's terms. */
@Composable
private fun LoanTermsDialog(model: BooksModel, account: Account, existing: LoanDetails?, onClose: () -> Unit) {
    val locale = model.language.locale
    val c = account.currency
    fun amt(m: Money?) = m?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()
    val owed = remember { -model.books.accounts.list().first { it.account.id == account.id }.balance }
    val payers = remember { payingAccounts(model, c) }
    var principal by remember { mutableStateOf(amt(existing?.principal ?: owed.takeIf { it.isPositive })) }
    var rateText by remember { mutableStateOf(pct(existing?.annualRate)) }
    var rateType by remember { mutableStateOf(existing?.rateType ?: RateType.FIXED) }
    var compounding by remember { mutableStateOf(existing?.compounding ?: if (account.type.name == "MORTGAGE") Compounding.SEMI_ANNUAL else Compounding.MONTHLY) }
    var years by remember { mutableStateOf(existing?.let { (it.amortizationMonths / 12).toString() } ?: "25") }
    var months by remember { mutableStateOf(existing?.let { (it.amortizationMonths % 12).toString() } ?: "0") }
    var frequency by remember { mutableStateOf(existing?.frequency ?: PaymentFrequency.MONTHLY) }
    var first by remember { mutableStateOf(existing?.firstPaymentDate?.toString().orEmpty()) }
    var payment by remember { mutableStateOf(amt(existing?.payment)) }
    var extra by remember { mutableStateOf(amt(existing?.extraPerPayment)) }
    var termEnd by remember { mutableStateOf(existing?.termEnd?.toString().orEmpty()) }
    var remind by remember { mutableStateOf((existing?.renewalRemindDays ?: LeadTimes.loanRenewal()).toString()) }
    var tax by remember { mutableStateOf(amt(existing?.propertyTax)) }
    var insurance by remember { mutableStateOf(amt(existing?.insurance)) }
    var payer by remember { mutableStateOf(payers.firstOrNull { it.id == existing?.paymentAccountId }) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }

    // The payment these terms give, shown while typing.
    val calculated = runCatching {
        val p = parseAmount(principal, c, locale)!!
        Amortization.payment(LoanTerms(p, rate(rateText, locale)!!, compounding, years.trim().toInt() * 12 + (months.trim().toIntOrNull() ?: 0), frequency))
    }.getOrNull()

    FormDialog(model.t("loans.termsOf", account.name), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            val months12 = runCatching { years.trim().toInt() * 12 + (months.trim().ifEmpty { "0" }.toInt()) }.getOrElse { throw ValidationException("error.loanAmortization") }
            model.books.loans.save(
                LoanDetails(
                    account.id,
                    parseAmount(principal, c, locale) ?: throw ValidationException("error.loanPrincipal"),
                    runCatching { rate(rateText, locale) }.getOrNull() ?: throw ValidationException("error.rateRange"),
                    rateType, compounding, months12, frequency,
                    date(first) ?: throw ValidationException("error.invalidDate"),
                    parseAmount(payment, c, locale), parseAmount(extra, c, locale)?.takeIf { it.isPositive }, date(termEnd),
                    remind.trim().toIntOrNull() ?: throw ValidationException("error.reminderDays"),
                    parseAmount(tax, c, locale)?.takeIf { it.isPositive }, parseAmount(insurance, c, locale)?.takeIf { it.isPositive },
                    payer?.id, existing?.lastPaidDate, notes,
                ),
            )
        }
        if (ok != null) onClose()
    }) {
        Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(model.t("loans.termsHint"), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountInput(model.t("loans.principalField"), principal, c, locale, Modifier.weight(1f), model::money) { principal = it }
                TextInput(model.t("loans.rateField"), rateText, Modifier.weight(1f)) { rateText = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("loans.rateType"), RateType.entries, rateType, { model.t("rateType.$it") }, Modifier.weight(1f)) { rateType = it }
                Picker(model.t("loans.compounding"), Compounding.entries, compounding, { model.t("compounding.$it") }, Modifier.weight(1f)) { compounding = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("loans.years"), years, Modifier.weight(1f)) { years = it }
                TextInput(model.t("loans.months"), months, Modifier.weight(1f)) { months = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("loans.frequency"), PaymentFrequency.entries, frequency, { model.t("frequency.$it") }, Modifier.weight(1f)) { frequency = it }
                DateInput(model.t("loans.firstPayment"), first, Modifier.weight(1f)) { first = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    AmountInput(model.t("loans.lenderPayment"), payment, c, locale, Modifier.fillMaxWidth(), model::money) { payment = it }
                    Text(calculated?.let { model.t("loans.calculated", model.money(it)) }.orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
                AmountInput(model.t("loans.extra"), extra, c, locale, Modifier.weight(1f), model::money) { extra = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(model.t("loans.termEnd"), termEnd, Modifier.weight(1f)) { termEnd = it }
                TextInput(model.t("loans.remindDays"), remind, Modifier.weight(1f)) { remind = it }
            }
            Text(model.t("loans.escrowHint"), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountInput(model.t("loans.propertyTax"), tax, c, locale, Modifier.weight(1f), model::money) { tax = it }
                AmountInput(model.t("loans.insurance"), insurance, c, locale, Modifier.weight(1f), model::money) { insurance = it }
            }
            Picker(model.t("loans.payFrom"), listOf<Account?>(null) + payers, payer, { it?.name ?: model.t("common.none") }) { payer = it }
            TextInput(model.t("account.notes"), notes, singleLine = false) { notes = it }
        }
    }
}

/** Records the next payment, split from the balance actually owed. */
@Composable
private fun PaymentDialog(model: BooksModel, account: Account, details: LoanDetails, onClose: () -> Unit) {
    val locale = model.language.locale
    val c = account.currency
    val suggested = remember { model.books.loans.nextPayment(account.id, today()) }
    fun amt(m: Money?) = m?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()
    val payers = remember { payingAccounts(model, c) }
    var payer by remember { mutableStateOf(payers.firstOrNull { it.id == details.paymentAccountId } ?: payers.firstOrNull()) }
    var date by remember { mutableStateOf(suggested?.date?.toString() ?: today().toString()) }
    var principal by remember { mutableStateOf(amt(suggested?.principal)) }
    var interest by remember { mutableStateOf(amt(suggested?.interest)) }
    var tax by remember { mutableStateOf(amt(suggested?.propertyTax?.takeIf { it.isPositive })) }
    var insurance by remember { mutableStateOf(amt(suggested?.insurance?.takeIf { it.isPositive })) }
    val total = runCatching { listOf(principal, interest, tax, insurance).mapNotNull { parseAmount(it, c, locale) }.sum(c) }.getOrNull()

    FormDialog(model.t("loans.recordPayment") + " · " + account.name, model.t("common.save"), model.t("common.cancel"), canSave = payer != null, onDismiss = onClose, onSave = {
        val ok = model.act {
            val zero = Money.zero(c)
            model.books.loans.recordPayment(
                account.id, payer!!.id,
                LoanService.PaymentSplit(
                    date(date) ?: throw ValidationException("error.invalidDate"),
                    parseAmount(principal, c, locale) ?: zero, parseAmount(interest, c, locale) ?: zero,
                    parseAmount(tax, c, locale) ?: zero, parseAmount(insurance, c, locale) ?: zero,
                ),
            )
        }
        if (ok != null) onClose()
    }) {
        Text(model.t("loans.paymentHint"), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker(model.t("loans.payFrom"), payers, payer, { it.name }, Modifier.weight(1f)) { payer = it }
            DateInput(model.t("report.date"), date, Modifier.weight(1f)) { date = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountInput(model.t("loans.principal"), principal, c, locale, Modifier.weight(1f), model::money) { principal = it }
            AmountInput(model.t("loans.interest"), interest, c, locale, Modifier.weight(1f), model::money) { interest = it }
        }
        if (details.propertyTax != null || details.insurance != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountInput(model.t("loans.propertyTax"), tax, c, locale, Modifier.weight(1f), model::money) { tax = it }
                AmountInput(model.t("loans.insurance"), insurance, c, locale, Modifier.weight(1f), model::money) { insurance = it }
            }
        }
        Text(total?.let { model.t("loans.paymentTotal", model.money(it)) }.orEmpty(), fontWeight = FontWeight.Bold)
    }
}

/** LN-03: a lump sum off the principal. */
@Composable
private fun PrepaymentDialog(model: BooksModel, account: Account, details: LoanDetails, onClose: () -> Unit) {
    val locale = model.language.locale
    val c = account.currency
    val payers = remember { payingAccounts(model, c) }
    var payer by remember { mutableStateOf(payers.firstOrNull { it.id == details.paymentAccountId }) }
    var date by remember { mutableStateOf(today().toString()) }
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    FormDialog(model.t("loans.prepayment") + " · " + account.name, model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            model.books.loans.addPrepayment(
                account.id, date(date) ?: throw ValidationException("error.invalidDate"),
                parseAmount(amount, c, locale) ?: throw ValidationException("error.amountPositive"), payer?.id, notes,
            )
        }
        if (ok != null) onClose()
    }) {
        Text(model.t("loans.prepaymentHint"), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t("report.date"), date, Modifier.weight(1f)) { date = it }
            AmountInput(model.t("register.amount"), amount, c, locale, Modifier.weight(1f), model::money) { amount = it }
        }
        Picker(model.t("loans.payFrom"), listOf<Account?>(null) + payers, payer, { it?.name ?: model.t("loans.alreadyRecorded") }) { payer = it }
        TextInput(model.t("register.memo"), notes) { notes = it }
    }
}

/** LN-03 rate changes and LN-04 renewals. */
@Composable
private fun RateDialog(model: BooksModel, account: Account, details: LoanDetails, status: LoanStatus, renewal: Boolean, onClose: () -> Unit) {
    val locale = model.language.locale
    var date by remember { mutableStateOf(((if (renewal) details.termEnd else null) ?: today()).toString()) }
    var rateText by remember { mutableStateOf(pct(status.annualRate)) }
    var recalculate by remember { mutableStateOf(details.rateType == RateType.FIXED) }
    var newEnd by remember { mutableStateOf(details.termEnd?.let { LocalDate(it.year + 5, it.month, it.day) }?.toString().orEmpty()) }
    var notes by remember { mutableStateOf("") }
    FormDialog(model.t(if (renewal) "loans.renew" else "loans.rateChange") + " · " + account.name, model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            val d = date(date) ?: throw ValidationException("error.invalidDate")
            val r = runCatching { rate(rateText, locale) }.getOrNull() ?: throw ValidationException("error.rateRange")
            if (renewal) model.books.loans.renew(account.id, d, r, date(newEnd), notes) else model.books.loans.changeRate(account.id, d, r, recalculate, notes)
        }
        if (ok != null) onClose()
    }) {
        Text(model.t(if (renewal) "loans.renewHint" else "loans.rateHint"), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t(if (renewal) "loans.renewalDate" else "loans.rateFrom"), date, Modifier.weight(1f)) { date = it }
            TextInput(model.t("loans.rateField"), rateText, Modifier.weight(1f)) { rateText = it }
        }
        if (renewal) DateInput(model.t("loans.newTermEnd"), newEnd, Modifier.fillMaxWidth()) { newEnd = it }
        else LabeledCheckbox(model.t("loans.recalculate"), recalculate) { recalculate = it }
        TextInput(model.t("register.memo"), notes) { notes = it }
    }
}

@Composable
private fun PaymentChangeDialog(model: BooksModel, account: Account, status: LoanStatus, onClose: () -> Unit) {
    val locale = model.language.locale
    var date by remember { mutableStateOf((status.nextPayment ?: today()).toString()) }
    var amount by remember { mutableStateOf(MoneyFormat.formatAmount(status.payment, locale)) }
    var notes by remember { mutableStateOf("") }
    FormDialog(model.t("loans.paymentChange") + " · " + account.name, model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            model.books.loans.changePayment(
                account.id, date(date) ?: throw ValidationException("error.invalidDate"),
                parseAmount(amount, account.currency, locale) ?: throw ValidationException("error.amountPositive"), notes,
            )
        }
        if (ok != null) onClose()
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t("loans.rateFrom"), date, Modifier.weight(1f)) { date = it }
            AmountInput(model.t("loans.newPayment"), amount, account.currency, locale, Modifier.weight(1f), model::money) { amount = it }
        }
        TextInput(model.t("register.memo"), notes) { notes = it }
    }
}

/** LN-06: what extra payments, a lump sum, another rate or another amortization would do. */
@Composable
private fun WhatIfDialog(model: BooksModel, account: Account, status: LoanStatus, onClose: () -> Unit) {
    val locale = model.language.locale
    val c = account.currency
    var extra by remember { mutableStateOf("") }
    var lump by remember { mutableStateOf("") }
    var rateText by remember { mutableStateOf("") }
    var years by remember { mutableStateOf("") }
    val result: Result<Comparison> = remember(extra, lump, rateText, years) {
        runCatching {
            model.books.loans.whatIf(
                account.id, today(),
                extraPerPayment = parseAmount(extra, c, locale)?.takeIf { it.isPositive },
                lumpSum = parseAmount(lump, c, locale)?.takeIf { it.isPositive },
                annualRate = rate(rateText, locale),
                remainingAmortizationMonths = years.trim().ifEmpty { null }?.let { BigDecimal(it.replace(',', '.')).multiply(BigDecimal(12)).setScale(0, RoundingMode.HALF_UP).toInt() },
            )
        }
    }
    WideDialog(model.t("loans.whatIfOf", account.name), model.t("common.close"), onClose) {
        Text(model.t("loans.whatIfHint", model.money(status.owed)), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            AmountInput(model.t("loans.extraEach"), extra, c, locale, Modifier.weight(1f), model::money) { extra = it }
            AmountInput(model.t("loans.lumpSum"), lump, c, locale, Modifier.weight(1f), model::money) { lump = it }
            TextInput(model.t("loans.otherRate"), rateText, Modifier.weight(1f)) { rateText = it }
            TextInput(model.t("loans.otherYears"), years, Modifier.weight(1f)) { years = it }
        }
        if (anyChangeTyped(extra, lump, rateText, years)) result.onFailure { e -> ErrorText(if (e is ValidationException) e.message(model.language) else model.t("error.invalidNumber")) }
        val anyChange = anyChangeTyped(extra, lump, rateText, years)
        if (!anyChange) Text(model.t("loans.whatIfEmpty"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
        if (anyChange) result.onSuccess { cmp ->
            val columns = listOf("", model.t("loans.now"), model.t("loans.withChanges"))
            val rows = listOf(
                listOf(model.t("loans.payment"), model.money(cmp.base.rows.first().payment), model.money(cmp.alternative.rows.first().payment)),
                listOf(model.t("loans.payoff"), cmp.base.payoffDate?.let(model::date).orEmpty(), cmp.alternative.payoffDate?.let(model::date).orEmpty()),
                listOf(model.t("loans.payments"), cmp.base.rows.size.toString(), cmp.alternative.rows.size.toString()),
                listOf(model.t("loans.interestLeft"), model.money(cmp.base.totalInterest), model.money(cmp.alternative.totalInterest)),
            )
            Column(Modifier.padding(vertical = 8.dp)) {
                Row { columns.forEach { Text(it, Modifier.weight(1f), fontWeight = FontWeight.Bold) } }
                HorizontalDivider()
                rows.forEach { r -> Row(Modifier.padding(vertical = 3.dp)) { r.forEach { Text(it, Modifier.weight(1f)) } } }
            }
            val saved = cmp.interestSaved
            Text(
                if (saved.isNegative) model.t("loans.costsMore", model.money(-saved)) else model.t("loans.saves", model.money(saved), cmp.monthsSooner),
                fontWeight = FontWeight.Bold,
                color = if (saved.isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun anyChangeTyped(vararg fields: String) = fields.any { it.isNotBlank() }
