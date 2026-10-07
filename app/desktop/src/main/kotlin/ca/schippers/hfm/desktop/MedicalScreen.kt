package ca.schippers.hfm.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.ClaimDeadlineRule
import ca.schippers.hfm.books.ClaimStatus
import ca.schippers.hfm.books.DocumentStatus
import ca.schippers.hfm.books.ExpenseStage
import ca.schippers.hfm.books.MedClaim
import ca.schippers.hfm.books.MedCoverage
import ca.schippers.hfm.books.MedExpense
import ca.schippers.hfm.books.MedPlan
import ca.schippers.hfm.books.MedPlanKind
import ca.schippers.hfm.books.MedService
import ca.schippers.hfm.books.LinkRole
import ca.schippers.hfm.books.LinkTarget
import ca.schippers.hfm.books.MedicalService
import ca.schippers.hfm.books.PlanPerson
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.calc.rules.LeadTimes
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat
import ca.schippers.hfm.ocr.desktop.FileKind
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import java.math.BigDecimal
import java.nio.file.Files
import javax.swing.JFileChooser

private enum class MedicalTab { EXPENSES, PLANS, COVERAGE }

/** MED-01 to MED-10: medical and dental expenses and their claims, the plans, and what is left of them. */
@Composable
fun MedicalScreen(model: BooksModel) {
    var tab by remember { mutableStateOf(MedicalTab.EXPENSES) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(model.t("medical.title"), style = MaterialTheme.typography.titleLarge)
        Text(model.t("medical.hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        PrimaryTabRow(selectedTabIndex = tab.ordinal, modifier = Modifier.padding(vertical = 8.dp)) {
            for (t in MedicalTab.entries) Tab(selected = tab == t, onClick = { tab = t }, modifier = Modifier.walkTab(t, tab == t) { tab = t }, text = { Text(model.t("medical.tab.$t")) })
        }
        when (tab) {
            MedicalTab.EXPENSES -> ExpensesTab(model)
            MedicalTab.PLANS -> PlansTab(model)
            MedicalTab.COVERAGE -> CoverageTab(model)
        }
    }
}

private fun dateOrNull(text: String): LocalDate? = text.trim().ifEmpty { null }?.let { runCatching { LocalDate.parse(it) }.getOrElse { throw ValidationException("error.invalidDate") } }

private fun BooksModel.memberName(id: String?): String = books.members.list(includeArchived = true).firstOrNull { it.id == id }?.displayName.orEmpty()

/** "To submit to Sun Life by 2027-03-01 (about $60.00)", "Waiting for payment", "Closed". */
private fun stageText(model: BooksModel, e: MedExpense): String {
    val s = model.books.medical.status(e)
    return when (s.stage) {
        ExpenseStage.TO_SUBMIT -> model.t("medical.stage.toSubmit", s.nextPlan!!.name, model.date(s.deadline!!), model.money(s.expected!!))
        ExpenseStage.WAITING -> model.t("medical.stage.waiting")
        ExpenseStage.CLOSED -> model.t("medical.stage.closed")
    }
}

// --- Expenses and claims (MED-06 to MED-10) -------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpensesTab(model: BooksModel) {
    val books = model.books
    val members = remember(model.revision) { books.members.list() }
    var who by remember { mutableStateOf<String?>(null) }
    var openOnly by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<MedExpense?>(null) }
    var fromBooks by remember { mutableStateOf(false) }
    val expenses = remember(model.revision, who) { books.medical.expenses(who) }
    val shown = expenses.filter { !openOnly || books.medical.status(it).stage != ExpenseStage.CLOSED }
    val unrecorded = remember(model.revision) { books.medical.unrecorded(today().minus(DatePeriod(years = 1)), today()) }
    val group = remember(model.revision) { model.defaultDocumentGroup() ?: books.groups().first().id }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Picker(model.t("report.person"), listOf(null) + members, members.firstOrNull { it.id == who }, { it?.displayName ?: model.t("report.everyone") }, Modifier.width(200.dp)) { who = it?.id }
        Button(onClick = { editing = MedExpense("", group, who ?: members.firstOrNull()?.id.orEmpty(), MedService.OTHER, today(), Money.zero(Currency.CAD)) }, modifier = Modifier.walkTarget("medical.addExpense")) {
            Text(model.t("medical.addExpense"))
        }
        if (unrecorded.isNotEmpty()) OutlinedButton(onClick = { fromBooks = true }) { Text(model.t("medical.fromBooks", unrecorded.size)) }
        LabeledCheckbox(model.t("medical.openOnly"), openOnly) { openOnly = it }
    }
    if (shown.isEmpty()) Text(model.t("medical.noExpenses"), Modifier.padding(vertical = 12.dp))
    else HeadingRow(Modifier.padding(top = 8.dp)) {
        ColumnHeading(model.t("register.date"), Modifier.width(100.dp))
        ColumnHeading(model.t("report.person"), Modifier.width(110.dp))
        ColumnHeading(model.t("medical.service"), Modifier.weight(1f))
        ColumnHeading(model.t("register.amount"), align = TextAlign.End)
    }
    LazyColumn {
        items(shown, key = { it.id }) { e ->
            Row(Modifier.fillMaxWidth().clickable { editing = e }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(model.date(e.serviceDate), Modifier.width(100.dp))
                Text(model.memberName(e.memberId), Modifier.width(110.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Column(Modifier.weight(1f)) {
                    Text(listOfNotNull(model.t("medService.${e.service}"), e.description).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stageText(model, e), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                Column(horizontalAlignment = Alignment.End) {
                    MoneyText(model, e.amount)
                    if (e.reimbursed.isPositive) Text(model.t("medical.outOfPocketShort", model.money(e.outOfPocket)), style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
        }
    }
    editing?.let { e -> ExpenseDialog(model, e) { editing = null } }
    if (fromBooks) FromBooksDialog(model, group) { fromBooks = false }
}

/** An expense with its claims: each plan in turn, the payments, and the receipts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseDialog(model: BooksModel, existing: MedExpense, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val members = remember { books.members.list() }
    val providers = remember { books.health.providers().filter { !it.archived } }
    var memberId by remember { mutableStateOf(existing.memberId) }
    var service by remember { mutableStateOf(existing.service) }
    var serviceDate by remember { mutableStateOf(existing.serviceDate.toString()) }
    var paidDate by remember { mutableStateOf(existing.paidDate?.toString().orEmpty()) }
    var amount by remember { mutableStateOf(if (existing.amount.isZero) "" else MoneyFormat.formatAmount(existing.amount, locale)) }
    var description by remember { mutableStateOf(existing.description.orEmpty()) }
    var providerId by remember { mutableStateOf(existing.providerId) }
    var medicationId by remember { mutableStateOf(existing.medicationId) }
    val medications = remember(memberId) { books.health.medications(memberId) }
    var taxEligible by remember { mutableStateOf(existing.taxEligible) }
    var notes by remember { mutableStateOf(existing.notes.orEmpty()) }
    // MED-06: the account group it is kept in, chosen when it is added, as for health records (HH-11).
    var groupId by remember { mutableStateOf(existing.groupId) }
    var asking by remember { mutableStateOf(false) }
    var deletingClaim by remember { mutableStateOf<MedClaim?>(null) }
    // The expense as saved, so claims can be added once it exists.
    var saved by remember { mutableStateOf(existing.takeIf { it.id.isNotBlank() }) }
    val current = saved?.let { s -> remember(model.revision, s.id) { runCatching { books.medical.expense(s.id) }.getOrNull() } }
    var paying by remember { mutableStateOf<MedClaim?>(null) }
    var submitting by remember { mutableStateOf(false) }

    fun draft() = existing.copy(
        id = saved?.id.orEmpty(), groupId = saved?.groupId ?: groupId, memberId = memberId, service = service, serviceDate = dateOrNull(serviceDate) ?: throw ValidationException("error.invalidDate"),
        paidDate = dateOrNull(paidDate), amount = parseAmount(amount, Currency.CAD, locale) ?: throw ValidationException("error.amountPositive"),
        description = description, providerId = providerId, taxEligible = taxEligible, notes = notes, closed = current?.closed ?: existing.closed,
        medicationId = medicationId.takeIf { service == MedService.PRESCRIPTION },
    )

    WideDialog(model.t(if (existing.id.isBlank()) "medical.addExpense" else "medical.expense"), model.t("common.close"), onClose) {
        Column(Modifier.width(760.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("medical.patient"), members, members.firstOrNull { it.id == memberId }, { it.displayName }, Modifier.weight(1f)) { memberId = it.id }
                Picker(model.t("medical.service"), MedService.entries, service, { model.t("medService.$it") }, Modifier.weight(1f)) { service = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(model.t("medical.serviceDate"), serviceDate, Modifier.weight(1f)) { serviceDate = it }
                DateInput(model.t("medical.paidDate"), paidDate, Modifier.weight(1f), hint = model.t("medical.paidDateHint")) { paidDate = it }
                AmountInput(model.t("medical.cost"), amount, Currency.CAD, locale, Modifier.weight(1f), model::money) { amount = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("medical.description"), description, Modifier.weight(1f)) { description = it }
                Picker(model.t("medical.provider"), listOf(null) + providers, providers.firstOrNull { it.id == providerId }, { it?.name ?: model.t("common.none") }, Modifier.weight(1f)) { providerId = it?.id }
            }
            // HLT-08: a prescription names the medication in the person's health records.
            if (service == MedService.PRESCRIPTION && medications.isNotEmpty()) {
                Picker(model.t("medical.medication"), listOf(null) + medications, medications.firstOrNull { it.id == medicationId }, { it?.let { m -> listOfNotNull(m.name, m.dose).joinToString(" ") } ?: model.t("common.none") }) { medicationId = it?.id }
            }
            LabeledCheckbox(model.t("medical.taxEligible"), taxEligible) { taxEligible = it }
            TextInput(model.t("account.notes"), notes, singleLine = false) { notes = it }
            GroupPicker(model, saved?.groupId ?: groupId, enabled = saved == null) { groupId = it.id }
            if (saved == null) PrivateGroupHint(model) { groupId = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { model.act { books.medical.saveExpense(draft()) }?.let { saved = it } }, modifier = Modifier.walkTarget("medical.saveExpense")) { Text(model.t("common.save")) }
                if (current != null) {
                    OutlinedButton(onClick = { model.act { books.medical.close(current.id, !current.closed) } }) { Text(model.t(if (current.closed) "medical.reopen" else "medical.close")) }
                    TextButton(onClick = { asking = true }) { Text(model.t("common.delete")) }
                }
            }

            if (current != null) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(model.t("medical.claims"), style = MaterialTheme.typography.titleSmall)
                val plans = remember(model.revision) { books.medical.plans().associateBy { it.id } }
                for (c in current.claims) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${plans[c.planId]?.name.orEmpty()} · ${model.t("claimStatus.${c.status}")}")
                            Text(
                                listOfNotNull(
                                    model.t("medical.submittedOn", model.date(c.submitted), model.money(c.claimed)),
                                    c.paid?.takeIf { c.status == ClaimStatus.PAID }?.let { model.t("medical.paidOn", model.date(c.paidDate!!), model.money(it)) },
                                    c.reference,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        if (c.status == ClaimStatus.SUBMITTED) {
                            TextButton(onClick = { paying = c }, modifier = Modifier.walkTarget("medical.recordPayment")) { Text(model.t("medical.recordPayment")) }
                            TextButton(onClick = { model.act { books.medical.deny(c.id, today()) } }) { Text(model.t("medical.deny")) }
                        }
                        TextButton(onClick = { deletingClaim = c }) { Text(model.t("common.delete")) }
                    }
                }
                Text(model.t("medical.outOfPocket", model.money(current.outOfPocket), model.money(current.reimbursed)), fontWeight = FontWeight.Bold)
                val status = books.medical.status(current)
                Text(stageText(model, current), style = MaterialTheme.typography.bodySmall)
                if (status.stage == ExpenseStage.TO_SUBMIT) {
                    OutlinedButton(onClick = { submitting = true }, modifier = Modifier.walkTarget("medical.submit")) { Text(model.t("medical.submitTo", status.nextPlan!!.name)) }
                }

                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                DocumentsBlock(model, MedicalService.EXPENSE, current.id, current.groupId, "medical.receipts")
                for (c in current.claims) DocumentsBlock(model, MedicalService.CLAIM, c.id, current.groupId, "medical.eob", plans[c.planId]?.name.orEmpty())
            }
        }
    }
    if (submitting && current != null) {
        val status = books.medical.status(current)
        status.nextPlan?.let { plan -> SubmitDialog(model, current, plan, status.expected) { submitting = false } }
    }
    paying?.let { c -> PaymentDialog(model, c) { paying = null } }
    if (asking && current != null) {
        AskBeforeDeleting(model, model.t("medical.delete.expense", model.t("medService.${current.service}"), model.date(current.serviceDate)), onDismiss = { asking = false }) {
            (model.act { books.medical.deleteExpense(current.id) } != null).also { if (it) onClose() }
        }
    }
    deletingClaim?.let { c ->
        val plan = remember(c.planId) { runCatching { books.medical.plan(c.planId).name }.getOrDefault("") }
        AskBeforeDeleting(model, model.t("medical.delete.claim", plan, model.date(c.submitted)), onDismiss = { deletingClaim = null }) {
            model.act { books.medical.deleteClaim(c.id) } != null
        }
    }
}

@Composable
private fun SubmitDialog(model: BooksModel, expense: MedExpense, plan: MedPlan, expected: Money?, onClose: () -> Unit) {
    val locale = model.language.locale
    var date by remember { mutableStateOf(today().toString()) }
    var claimed by remember { mutableStateOf(MoneyFormat.formatAmount(expense.outOfPocket, locale)) }
    var reference by remember { mutableStateOf("") }
    FormDialog(model.t("medical.submitTo", plan.name), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            model.books.medical.submit(expense.id, plan.id, dateOrNull(date) ?: today(), parseAmount(claimed, Currency.CAD, locale), reference)
        }
        if (ok != null) onClose()
    }) {
        expected?.let { Text(model.t("medical.expected", model.money(it)), style = MaterialTheme.typography.bodySmall) }
        DateInput(model.t("medical.submittedDate"), date, Modifier.fillMaxWidth()) { date = it }
        AmountInput(model.t("medical.claimed"), claimed, Currency.CAD, locale, Modifier.fillMaxWidth(), model::money) { claimed = it }
        TextInput(model.t("medical.reference"), reference) { reference = it }
    }
}

@Composable
private fun PaymentDialog(model: BooksModel, claim: MedClaim, onClose: () -> Unit) {
    val locale = model.language.locale
    var date by remember { mutableStateOf(today().toString()) }
    var paid by remember { mutableStateOf(MoneyFormat.formatAmount(claim.claimed, locale)) }
    FormDialog(model.t("medical.recordPayment"), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act { model.books.medical.recordPayment(claim.id, dateOrNull(date) ?: today(), parseAmount(paid, Currency.CAD, locale) ?: Money.zero(Currency.CAD)) }
        if (ok != null) onClose()
    }) {
        Text(model.t("medical.paymentHint"), style = MaterialTheme.typography.bodySmall)
        DateInput(model.t("medical.paidOnDate"), date, Modifier.fillMaxWidth()) { date = it }
        AmountInput(model.t("medical.paid"), paid, Currency.CAD, locale, Modifier.fillMaxWidth(), model::money) { paid = it }
    }
}

/** Receipts or an explanation of benefits: those attached, a file to add, or a capture from the review inbox (MED-05, MED-08). */
@Composable
internal fun DocumentsBlock(model: BooksModel, entity: String, id: String, groupId: String, titleKey: String, vararg args: Any) {
    val books = model.books
    val attached = remember(model.revision, id) { books.medical.documents(entity, id) }
    val inbox = remember(model.revision) { books.documents.inbox() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(model.t(titleKey, *args) + " (${attached.size})", Modifier.weight(1f))
        TextButton(onClick = {
            val chooser = JFileChooser().apply { dialogTitle = model.t(titleKey, *args) }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                model.act {
                    val bytes = Files.readAllBytes(chooser.selectedFile.toPath())
                    val kind = FileKind.of(bytes).takeIf { it != FileKind.UNSUPPORTED } ?: throw ValidationException("error.unsupportedFile")
                    val doc = books.documents.import(groupId, bytes, chooser.selectedFile.name, kind.mimeType).document
                    books.medical.attach(entity, id, doc.id)
                    books.documents.setStatus(doc.id, DocumentStatus.FILED)
                }
            }
        }) { Text(model.t("medical.attachFile")) }
        if (inbox.isNotEmpty()) {
            Picker(model.t("medical.fromInbox"), inbox, null, { d -> listOfNotNull(d.date?.let(model::date), d.merchant ?: d.title ?: d.fileName, d.amount?.let(model::money)).joinToString(" · ") }, Modifier.width(260.dp)) { d ->
                model.act {
                    books.medical.attach(entity, id, d.id)
                    books.documents.setStatus(d.id, DocumentStatus.FILED)
                }
            }
        }
    }
    for (d in attached) Text("· " + listOfNotNull(d.title ?: d.fileName ?: d.merchant, d.date?.let(model::date)).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
}

/** Health payments in the books not yet recorded as medical expenses: each added in one click. */
@Composable
private fun FromBooksDialog(model: BooksModel, groupId: String, onClose: () -> Unit) {
    val books = model.books
    val rows = remember(model.revision) { books.medical.unrecorded(today().minus(DatePeriod(years = 1)), today()) }
    val members = remember { books.members.list() }
    val accounts = remember { books.accounts.list(includeClosed = true).associate { it.account.id to it.account.name } }
    val picks = remember { mutableStateListOf<Pair<String, MedService>>().apply { rows.forEach { add((members.firstOrNull()?.id.orEmpty()) to MedService.OTHER) } } }
    WideDialog(model.t("medical.fromBooksTitle"), model.t("common.close"), onClose) {
        Column(Modifier.width(820.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            Text(model.t("medical.fromBooksHint"), style = MaterialTheme.typography.bodySmall)
            if (rows.isEmpty()) Text(model.t("medical.fromBooksNone"), Modifier.padding(vertical = 8.dp))
            else HeadingRow(spacing = 8.dp) {
                ColumnHeading(model.t("column.transaction"), Modifier.weight(1f))
                ColumnHeading(model.t("register.amount"), Modifier.width(100.dp))
                ColumnHeading(model.t("medical.patient"), Modifier.width(150.dp))
                ColumnHeading(model.t("medical.service"), Modifier.width(200.dp))
                ColumnHeading(model.t("table.actions"), Modifier.width(ACTIONS_WIDTH), TextAlign.Center)
            }
            rows.forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("${model.date(r.date)} · ${r.payee.orEmpty()}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(accounts[r.accountId].orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    MoneyText(model, -r.amount, modifier = Modifier.width(100.dp))
                    Picker(model.t("medical.patient"), members, members.firstOrNull { it.id == picks[i].first }, { it.displayName }, Modifier.width(150.dp)) { picks[i] = it.id to picks[i].second }
                    Picker(model.t("medical.service"), MedService.entries, picks[i].second, { model.t("medService.$it") }, Modifier.width(200.dp)) { picks[i] = picks[i].first to it }
                    Button(onClick = { model.act { books.medical.fromTransaction(r.transactionId, picks[i].first, picks[i].second, groupId) } }, Modifier.width(ACTIONS_WIDTH)) { Text(model.t("medical.add")) }
                }
            }
        }
    }
}

// --- Plans (MED-01 to MED-03) ------------------------------------------------------------------

@Composable
private fun PlansTab(model: BooksModel) {
    val books = model.books
    val plans = remember(model.revision) { books.medical.plans() }
    var editing by remember { mutableStateOf<MedPlan?>(null) }
    val group = remember(model.revision) { model.defaultDocumentGroup() ?: books.groups().first().id }
    Button(onClick = { editing = MedPlan("", group, MedPlanKind.GROUP_HEALTH, "") }, modifier = Modifier.walkTarget("medical.addPlan")) { Text(model.t("medical.addPlan")) }
    if (plans.isEmpty()) Text(model.t("medical.noPlans"), Modifier.padding(vertical = 12.dp))
    LazyColumn(Modifier.padding(top = 8.dp)) {
        items(plans, key = { it.id }) { p ->
            Column(Modifier.fillMaxWidth().clickable { editing = p }.padding(vertical = 6.dp)) {
                Text(p.name + if (!p.active) " · " + model.t("medical.inactive") else "", fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(model.t("medPlanKind.${p.kind}"), p.insurer, p.people.joinToString { "${model.memberName(it.memberId)} (${model.t("medical.order${it.priority.coerceAtMost(3)}")})" }.ifBlank { null })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                )
            }
            HorizontalDivider()
        }
    }
    editing?.let { p -> PlanDialog(model, p) { editing = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanDialog(model: BooksModel, existing: MedPlan, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val members = remember { books.members.list() }
    var kind by remember { mutableStateOf(existing.kind) }
    var name by remember { mutableStateOf(existing.name) }
    var insurer by remember { mutableStateOf(existing.insurer.orEmpty()) }
    var policy by remember { mutableStateOf(existing.policyNumber.orEmpty()) }
    var certificate by remember { mutableStateOf(existing.certificateNumber.orEmpty()) }
    var memberId by remember { mutableStateOf(existing.memberId) }
    var startMonth by remember { mutableStateOf(existing.yearStartMonth.toString()) }
    var startDay by remember { mutableStateOf(existing.yearStartDay.toString()) }
    var claimDays by remember { mutableStateOf(existing.claimDays.toString()) }
    var claimRule by remember { mutableStateOf(existing.claimRule) }
    var hsa by remember { mutableStateOf(existing.hsaAmount?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var active by remember { mutableStateOf(existing.active) }
    var notes by remember { mutableStateOf(existing.notes.orEmpty()) }
    // Who is covered, and in what order this plan pays for them (0 = not covered).
    val order = remember { mutableStateListOf<Int>().apply { members.forEach { m -> add(existing.people.firstOrNull { it.memberId == m.id }?.priority ?: 0) } } }
    var saved by remember { mutableStateOf(existing.takeIf { it.id.isNotBlank() }) }
    val coverages = saved?.let { s -> remember(model.revision, s.id) { books.medical.coverages(s.id) } }.orEmpty()
    var coverage by remember { mutableStateOf<MedCoverage?>(null) }
    // MED-01: the account group it is kept in, chosen when it is added (HH-11).
    var groupId by remember { mutableStateOf(existing.groupId) }
    var asking by remember { mutableStateOf(false) }

    WideDialog(model.t(if (existing.id.isBlank()) "medical.addPlan" else "medical.plan"), model.t("common.close"), onClose) {
        Column(Modifier.width(760.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("medical.planKind"), MedPlanKind.entries, kind, { model.t("medPlanKind.$it") }, Modifier.weight(1f)) { kind = it; if (name.isBlank()) name = model.t("medPlanKind.$it") }
                TextInput(model.t("medical.planName"), name, Modifier.weight(1f)) { name = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("medical.insurer"), insurer, Modifier.weight(1f)) { insurer = it }
                TextInput(model.t("medical.policy"), policy, Modifier.weight(1f)) { policy = it }
                TextInput(model.t("medical.certificate"), certificate, Modifier.weight(1f)) { certificate = it }
            }
            Picker(model.t("medical.planMember"), listOf(null) + members, members.firstOrNull { it.id == memberId }, { it?.displayName ?: model.t("common.none") }) { memberId = it?.id }
            Text(model.t("medical.coveredHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                members.forEachIndexed { i, m ->
                    Picker(m.displayName, listOf(0, 1, 2, 3), order[i], { if (it == 0) model.t("medical.notCovered") else model.t("medical.order$it") }, Modifier.width(170.dp)) { order[i] = it }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("medical.yearStartMonth"), startMonth, Modifier.weight(1f)) { startMonth = it }
                TextInput(model.t("medical.yearStartDay"), startDay, Modifier.weight(1f)) { startDay = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("medical.claimRule"), ClaimDeadlineRule.entries, claimRule, { model.t("medClaimRule.$it") }, Modifier.weight(1f)) {
                    claimRule = it
                    if (it == ClaimDeadlineRule.AFTER_PLAN_YEAR && claimDays.trim() == LeadTimes.medicalPlanDeadline().toString()) claimDays = "0"
                }
                TextInput(
                    model.t("medical.claimDays"), claimDays, Modifier.weight(1f),
                    supporting = model.t(if (claimRule == ClaimDeadlineRule.AFTER_PLAN_YEAR) "medical.claimDaysPlanYearHint" else "medical.claimDaysHint"),
                ) { claimDays = it }
            }
            if (kind == MedPlanKind.HSA) AmountInput(model.t("medical.hsaAmount"), hsa, Currency.CAD, locale, Modifier.fillMaxWidth(), model::money) { hsa = it }
            LabeledCheckbox(model.t("medical.active"), active) { active = it }
            TextInput(model.t("account.notes"), notes, singleLine = false) { notes = it }
            GroupPicker(model, saved?.groupId ?: groupId, enabled = saved == null) { groupId = it.id }
            if (saved == null) PrivateGroupHint(model) { groupId = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    model.act {
                        books.medical.savePlan(
                            existing.copy(
                                id = saved?.id.orEmpty(), groupId = saved?.groupId ?: groupId, kind = kind, name = name, insurer = insurer, policyNumber = policy, certificateNumber = certificate, memberId = memberId,
                                yearStartMonth = startMonth.trim().toIntOrNull() ?: 1, yearStartDay = startDay.trim().toIntOrNull() ?: 1, claimDays = claimDays.trim().toIntOrNull() ?: LeadTimes.medicalPlanDeadline(), claimRule = claimRule,
                                hsaAmount = if (kind == MedPlanKind.HSA) parseAmount(hsa, Currency.CAD, locale) else null, active = active, notes = notes,
                                people = members.indices.filter { order[it] > 0 }.map { PlanPerson(members[it].id, order[it]) },
                            ),
                        )
                    }?.let { saved = it }
                }) { Text(model.t("common.save")) }
                if (saved != null) TextButton(onClick = { asking = true }) { Text(model.t("common.delete")) }
            }
            if (saved != null && kind != MedPlanKind.HSA) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(model.t("medical.coverage"), style = MaterialTheme.typography.titleSmall)
                Text(model.t("medical.coverageHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                if (coverages.isNotEmpty()) HeadingRow {
                    ColumnHeading(model.t("medical.service"), Modifier.weight(1f))
                    ColumnHeading(model.t("medical.coverage"))
                }
                for (c in coverages) {
                    Row(Modifier.fillMaxWidth().clickable { coverage = c }.padding(vertical = 3.dp)) {
                        Text(model.t("medService.${c.service}"), Modifier.weight(1f))
                        Text(coverageText(model, c), style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(onClick = { coverage = MedCoverage("", saved!!.id, MedService.PRESCRIPTION, BigDecimal(80)) }, modifier = Modifier.walkTarget("medical.addCoverage")) { Text(model.t("medical.addCoverage")) }
            }
            saved?.let { s -> DocumentsBlock(model, MedicalService.PLAN, s.id, s.groupId, "medical.booklets") }
            // CON-04, CON-06: the insurer or the firm that runs the plan, as contacts.
            LinkedContacts(
                model, LinkTarget.MEDICAL_PLAN, saved?.id, listOf(LinkRole.INSURER, LinkRole.PLAN_ADMINISTRATOR, LinkRole.OTHER),
                suggestedName = insurer, memberIds = members.indices.filter { order[it] > 0 }.map { members[it].id }.toSet(), groupId = saved?.groupId ?: groupId,
            )
        }
    }
    coverage?.let { c -> CoverageDialog(model, c) { coverage = null } }
    saved?.let { s ->
        if (asking) {
            AskBeforeDeleting(model, model.t("medical.delete.plan", s.name), onDismiss = { asking = false }) {
                (model.act { books.medical.deletePlan(s.id) } != null).also { if (it) onClose() }
            }
        }
    }
}

private fun coverageText(model: BooksModel, c: MedCoverage): String = listOfNotNull(
    c.percent.stripTrailingZeros().toPlainString() + " %",
    c.deductible?.let { model.t("medical.deductibleOf", model.money(it)) },
    c.perVisitMax?.let { model.t("medical.perVisitOf", model.money(it)) },
    c.annualMax?.let { model.t("medical.annualOf", model.money(it)) },
    c.frequencyMonths?.let { model.t("medical.everyMonths", it) },
).joinToString(" · ")

@Composable
private fun CoverageDialog(model: BooksModel, existing: MedCoverage, onClose: () -> Unit) {
    val locale = model.language.locale
    fun amt(m: Money?) = m?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()
    var service by remember { mutableStateOf(existing.service) }
    var percent by remember { mutableStateOf(existing.percent.stripTrailingZeros().toPlainString()) }
    var deductible by remember { mutableStateOf(amt(existing.deductible)) }
    var perVisit by remember { mutableStateOf(amt(existing.perVisitMax)) }
    var annual by remember { mutableStateOf(amt(existing.annualMax)) }
    var months by remember { mutableStateOf(existing.frequencyMonths?.toString().orEmpty()) }
    var asking by remember { mutableStateOf(false) }
    FormDialog(model.t("medical.coverage"), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            model.books.medical.saveCoverage(
                existing.copy(
                    service = service, percent = runCatching { MoneyFormat.parseDecimal(percent.trim().removeSuffix("%").trim(), locale) }.getOrElse { throw ValidationException("error.invalidNumber") },
                    deductible = parseAmount(deductible, Currency.CAD, locale), perVisitMax = parseAmount(perVisit, Currency.CAD, locale),
                    annualMax = parseAmount(annual, Currency.CAD, locale), frequencyMonths = months.trim().ifEmpty { null }?.toIntOrNull(),
                ),
            )
        }
        if (ok != null) onClose()
    }) {
        Picker(model.t("medical.service"), MedService.entries, service, { model.t("medService.$it") }) { service = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("medical.percent"), percent, Modifier.weight(1f)) { percent = it }
            AmountInput(model.t("medical.deductible"), deductible, Currency.CAD, locale, Modifier.weight(1f), model::money) { deductible = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountInput(model.t("medical.perVisitMax"), perVisit, Currency.CAD, locale, Modifier.weight(1f), model::money) { perVisit = it }
            AmountInput(model.t("medical.annualMax"), annual, Currency.CAD, locale, Modifier.weight(1f), model::money) { annual = it }
        }
        TextInput(model.t("medical.frequency"), months, supporting = model.t("medical.frequencyHint")) { months = it }
        if (existing.id.isNotBlank()) TextButton(onClick = { asking = true }) { Text(model.t("common.delete")) }
    }
    if (asking) {
        AskBeforeDeleting(model, model.t("medical.delete.coverage", model.t("medService.${existing.service}")), onDismiss = { asking = false }) {
            (model.act { model.books.medical.deleteCoverage(existing.planId, existing.id) } != null).also { if (it) onClose() }
        }
    }
}

// --- Coverage left (MED-04) ----------------------------------------------------------------------

@Composable
private fun CoverageTab(model: BooksModel) {
    val books = model.books
    val members = remember(model.revision) { books.members.list() }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(model.t("medical.coverageLeftHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        for (m in members) {
            val left = remember(model.revision, m.id) { books.medical.coverageLeft(m.id, today()) }
            if (left.isEmpty()) continue
            Text(m.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            HeadingRow {
                ColumnHeading(model.t("medical.plan"), Modifier.width(260.dp))
                ColumnHeading(model.t("medical.service"), Modifier.weight(1f))
                ColumnHeading(model.t("column.left"))
            }
            for (l in left) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(l.plan.name, Modifier.width(260.dp).padding(end = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (l.plan.kind == MedPlanKind.HSA) model.t("medical.hsaCredit") else model.t("medService.${l.coverage.service}"), Modifier.weight(1f))
                    Text(
                        listOfNotNull(
                            l.annualLeft?.let { model.t("medical.leftThisYear", model.money(it)) },
                            l.nextEligible?.let { model.t("medical.nextEligible", model.date(it)) },
                        ).joinToString(" · ").ifEmpty { model.t("medical.noLimit") },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
