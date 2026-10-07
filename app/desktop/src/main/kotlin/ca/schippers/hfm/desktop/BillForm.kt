package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.AmountKind
import ca.schippers.hfm.books.Bill
import ca.schippers.hfm.books.BillDraft
import ca.schippers.hfm.books.BillKind
import ca.schippers.hfm.books.BillLists
import ca.schippers.hfm.books.BillProposal
import ca.schippers.hfm.books.BillStatement
import ca.schippers.hfm.books.BillType
import ca.schippers.hfm.books.Category
import ca.schippers.hfm.books.LinkTarget
import ca.schippers.hfm.books.Occurrence
import ca.schippers.hfm.books.OccurrenceStatus
import ca.schippers.hfm.books.PaymentMethod
import ca.schippers.hfm.books.StatementDraft
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.calc.rules.LeadTimes
import ca.schippers.hfm.calc.schedule.BusinessDayAdjust
import ca.schippers.hfm.calc.schedule.Frequency
import ca.schippers.hfm.calc.schedule.MonthDay
import ca.schippers.hfm.calc.schedule.Recurrence
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat
import ca.schippers.hfm.ocr.MeterReadings
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/** A date typed in a form: empty is none, anything else must be a date. */
private fun dateOrNull(text: String): LocalDate? =
    text.trim().ifEmpty { null }?.let { runCatching { LocalDate.parse(it) }.getOrElse { throw ValidationException("error.invalidDate") } }

/** BILL-13: "Home · Essential Housing & Utilities · Utilities: Electricity", or null when the bill is not classified. */
internal fun classificationText(model: BooksModel, lists: BillLists, bill: Bill): String? {
    val type = bill.type ?: return null
    return listOfNotNull(
        model.t("billType.$type"),
        lists.category(bill.categoryKey)?.name(model.language),
        lists.subcategory(bill.subcategoryKey)?.let { lists.label(it, model.language) },
    ).joinToString(" · ")
}

/** BILL-13: the type, category and subcategory pickers; [onChange] gets the new three. */
@Composable
private fun ClassificationFields(model: BooksModel, lists: BillLists, type: BillType?, categoryKey: String?, subcategoryKey: String?, onChange: (BillType?, String?, String?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Picker(model.t("bills.billType"), listOf<BillType?>(null) + BillType.entries, type, { it?.let { t -> model.t("billType.$t") } ?: model.t("bills.notClassified") }, Modifier.weight(1f)) {
            if (it != type) onChange(it, null, null)
        }
        if (type != null) {
            val categories = lists.categories(type).let { visible -> visible + listOfNotNull(lists.category(categoryKey)?.takeIf { it !in visible }) }
            Picker(model.t("bills.billCategory"), categories, lists.category(categoryKey), { it.name(model.language) }, Modifier.weight(2f)) {
                if (it.key != categoryKey) onChange(type, it.key, null)
            }
        }
    }
    if (type != null && categoryKey != null) {
        val subs = lists.subcategories(categoryKey).let { visible -> visible + listOfNotNull(lists.subcategory(subcategoryKey)?.takeIf { it !in visible }) }
        Picker(model.t("bills.billSubcategory"), listOf(null) + subs, lists.subcategory(subcategoryKey), { it?.let { s -> lists.label(s, model.language) } ?: model.t("common.none") }) {
            onChange(type, categoryKey, it?.key)
        }
    }
}

/** BILL-17: the meter readings of a statement, as typed. */
private class ReadingFields(r: MeterReadings) {
    var previous by mutableStateOf(r.previous?.toPlainString().orEmpty())
    var previousDate by mutableStateOf(r.previousDate?.toString().orEmpty())
    var current by mutableStateOf(r.current?.toPlainString().orEmpty())
    var currentDate by mutableStateOf(r.currentDate?.toString().orEmpty())
    var used by mutableStateOf(r.used?.toPlainString().orEmpty())

    /** The readings typed; in English a comma separates thousands, in French it is the decimal mark. */
    fun readings(language: Language): MeterReadings {
        fun number(text: String): BigDecimal? {
            val clean = text.filterNot { it.isWhitespace() || it == ' ' || it == ' ' }.let { if (language == Language.FRENCH) it.replace(',', '.') else it.replace(",", "") }
            return clean.ifEmpty { null }?.let { it.toBigDecimalOrNull()?.takeIf { v -> v.signum() >= 0 } ?: throw ValidationException("error.meterReading") }
        }
        return MeterReadings(number(previous), dateOrNull(previousDate), number(current), dateOrNull(currentDate), number(used))
    }
}

@Composable
private fun ReadingInputs(model: BooksModel, f: ReadingFields) {
    Text(model.t("bills.readings"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextInput(model.t("bills.previousReading"), f.previous, Modifier.weight(1f)) { f.previous = it }
        DateInput(model.t("bills.previousReadingDate"), f.previousDate, Modifier.weight(1f)) { f.previousDate = it }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextInput(model.t("bills.currentReading"), f.current, Modifier.weight(1f)) { f.current = it }
        DateInput(model.t("bills.currentReadingDate"), f.currentDate, Modifier.weight(1f)) { f.currentDate = it }
    }
    TextInput(model.t("bills.used"), f.used, supporting = model.t("bills.used.hint")) { f.used = it }
}

/** The fields of a statement other than its readings, as typed. */
private class StatementFields(s: StatementDraft?, locale: java.util.Locale) {
    var number by mutableStateOf(s?.statementNumber.orEmpty())
    var issued by mutableStateOf(s?.issuedDate?.toString().orEmpty())
    var due by mutableStateOf(s?.dueDate?.toString().orEmpty())
    var amount by mutableStateOf(s?.amount?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty())
    val readings = ReadingFields(s?.readings ?: MeterReadings())
}

/**
 * Add or edit a bill, income or scheduled transfer (BILL-01, BILL-02, BILL-04, BILL-10), with its
 * classification (BILL-13), masked account number (BILL-15), meter (BILL-17) and statements
 * (BILL-16). BILL-18: with a [proposal] read from the captured document [documentId], it creates
 * the bill and records the document as its first statement.
 */
@Composable
internal fun BillDialog(model: BooksModel, existing: Bill?, proposal: BillProposal? = null, documentId: String? = null, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val accounts = remember { books.accounts.list().map { it.account } }
    val tree = remember { books.categories.tree() }
    val lists = remember(model.revision) { books.billLists.lists() }
    val people = remember { books.members.list().filter { it.kind == MemberKind.ADULT } }
    val meters = remember(model.revision) { books.utilities.meters() }
    val start0 = proposal?.bill
    var kind by remember { mutableStateOf(existing?.kind ?: start0?.kind ?: BillKind.BILL) }
    var name by remember { mutableStateOf(existing?.name ?: start0?.name.orEmpty()) }
    var payee by remember { mutableStateOf(existing?.payeeName ?: start0?.payeeName.orEmpty()) }
    // BILL-15: an existing number stays hidden; a new one is typed.
    var payeeAccount by remember { mutableStateOf(start0?.payeeAccountNumber.orEmpty()) }
    var changingNumber by remember { mutableStateOf(existing?.payeeAccountMasked == null) }
    var revealing by remember { mutableStateOf(false) }
    var accountId by remember { mutableStateOf(existing?.accountId ?: start0?.accountId ?: accounts.firstOrNull()?.id) }
    var transferId by remember { mutableStateOf(existing?.transferAccountId) }
    var amount by remember { mutableStateOf((existing?.amount ?: start0?.amount)?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var amountKind by remember { mutableStateOf(existing?.amountKind ?: start0?.amountKind ?: AmountKind.FIXED) }
    var method by remember { mutableStateOf(existing?.paymentMethod ?: PaymentMethod.ONLINE) }
    var categoryId by remember { mutableStateOf(existing?.categoryId ?: start0?.categoryId) }
    var type by remember { mutableStateOf(existing?.type ?: start0?.type) }
    var billCategory by remember { mutableStateOf(existing?.categoryKey ?: start0?.categoryKey) }
    var subcategory by remember { mutableStateOf(existing?.subcategoryKey ?: start0?.subcategoryKey) }
    var memberId by remember { mutableStateOf(existing?.memberId ?: runCatching { books.users.list().firstOrNull { it.id == books.userId }?.memberId }.getOrNull()?.takeIf { id -> people.any { it.id == id } }) }
    val meterBefore = remember { existing?.let { books.utilities.meterFor(it.id)?.id } }
    var meterId by remember {
        mutableStateOf(meterBefore ?: lists.subcategory(subcategory)?.meterKind?.let { k -> meters.filter { it.kind == k }.singleOrNull()?.id })
    }
    var repeat by remember { mutableStateOf(existing?.let { Repeat.of(it.recurrence) } ?: Repeat.MONTHLY) }
    var interval by remember { mutableStateOf(existing?.recurrence?.interval?.toString() ?: "1") }
    var monthDay by remember { mutableStateOf(existing?.recurrence?.monthDay ?: MonthDay.SAME_DAY) }
    var secondDay by remember { mutableStateOf(existing?.recurrence?.secondDay?.toString() ?: "0") }
    var adjust by remember { mutableStateOf(existing?.recurrence?.adjust ?: BusinessDayAdjust.NONE) }
    var start by remember { mutableStateOf((existing?.startDate ?: start0?.startDate ?: today()).toString()) }
    var end by remember { mutableStateOf(existing?.endDate?.toString().orEmpty()) }
    var reminders by remember { mutableStateOf((existing?.reminderDays ?: LeadTimes.newBill()).joinToString(", ")) }
    var subscription by remember { mutableStateOf(existing?.isSubscription ?: false) }
    var cancelBy by remember { mutableStateOf(existing?.cancelBy?.toString().orEmpty()) }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    var confirmDelete by remember { mutableStateOf(false) }
    val statement = remember { proposal?.let { StatementFields(it.statement, locale) } }
    val account = accounts.firstOrNull { it.id == accountId }
    val utility = lists.isUtility(subcategory) || meterId != null

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
        model.t(if (existing == null) (if (proposal != null) "bills.createFromDocument" else "bills.add") else "bills.edit"), model.t("common.save"), model.t("common.cancel"),
        canSave = name.isNotBlank() && account != null,
        onDismiss = onClose,
        onSave = {
            val ok = model.act {
                val value = parseAmount(amount, account!!.currency, locale) ?: Money.zero(account.currency)
                val days = reminders.split(',', ' ').mapNotNull { it.trim().ifEmpty { null } }.map { it.toIntOrNull() ?: throw ValidationException("error.reminderDays") }
                val classified = kind == BillKind.BILL && type != null
                val draft = BillDraft(
                    kind, name, value, account.id, recurrence(), dateOrNull(start) ?: throw ValidationException("error.invalidDate"),
                    payee.ifBlank { null }, payeeAccount.ifBlank { null }, amountKind, if (kind == BillKind.TRANSFER) transferId else null,
                    method, if (kind == BillKind.TRANSFER) null else categoryId, dateOrNull(end), days, subscription, null, dateOrNull(cancelBy), null,
                    type.takeIf { classified }, billCategory.takeIf { classified }, subcategory.takeIf { classified }, memberId.takeIf { classified && type == BillType.BUSINESS },
                )
                val linkedMeter = meterId.takeIf { kind == BillKind.BILL }
                val id = when {
                    existing != null -> {
                        books.bills.update(
                            existing.copy(
                                kind = draft.kind, name = draft.name, amount = draft.amount, recurrence = draft.recurrence, startDate = draft.startDate,
                                payeeName = draft.payeeName, amountKind = draft.amountKind,
                                transferAccountId = draft.transferAccountId, paymentMethod = draft.paymentMethod, categoryId = draft.categoryId,
                                endDate = draft.endDate, reminderDays = draft.reminderDays, isSubscription = draft.isSubscription,
                                cancelBy = draft.cancelBy, active = active, type = draft.type, categoryKey = draft.categoryKey, subcategoryKey = draft.subcategoryKey,
                                memberId = draft.memberId,
                            ),
                            newAccountNumber = if (changingNumber) payeeAccount else null,
                        )
                        existing.id
                    }
                    proposal != null && documentId != null -> {
                        val s = statement!!
                        val first = StatementDraft(
                            dateOrNull(s.due) ?: draft.startDate, parseAmount(s.amount, account.currency, locale), s.number.ifBlank { null }, dateOrNull(s.issued),
                            documentId, s.readings.readings(model.language), linkedMeter,
                        )
                        books.documents.createBillFrom(documentId, draft, first).id
                    }
                    else -> books.bills.create(draft).id
                }
                if (linkedMeter != meterBefore) books.utilities.linkBill(id, linkedMeter)
            }
            if (ok != null) onClose()
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (proposal != null) Text(model.t("bills.createFromDocument.explain"), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("bills.kind"), BillKind.entries, kind, { model.t("billKind.$it") }, Modifier.weight(1f)) { kind = it }
                TextInput(model.t("bills.name"), name, Modifier.weight(2f)) { name = it }
            }
            // BILL-13, BILL-20: bills only; income and transfers are not classified.
            if (kind == BillKind.BILL) {
                ClassificationFields(model, lists, type, billCategory, subcategory) { t, c, s ->
                    // The subcategory's spending category replaces the one it gave before, never one chosen by hand.
                    val before = lists.subcategory(subcategory)?.spendingCategoryId
                    val after = lists.subcategory(s)?.spendingCategoryId
                    if (after != null && (categoryId == null || categoryId == before)) categoryId = after
                    if (s != subcategory && meterId == null) lists.subcategory(s)?.meterKind?.let { k -> meters.filter { it.kind == k }.singleOrNull()?.let { meterId = it.id } }
                    type = t
                    billCategory = c
                    subcategory = s
                }
                if (type == BillType.BUSINESS) {
                    Picker(model.t("bills.businessOf"), people, people.firstOrNull { it.id == memberId }, { it.displayName }) { memberId = it.id }
                    Text(model.t("bills.businessOf.hint"), style = MaterialTheme.typography.bodySmall)
                }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextInput(model.t("register.payee"), payee, Modifier.weight(1f)) { payee = it }
                    if (changingNumber) {
                        TextInput(model.t("bills.payeeAccount"), payeeAccount, Modifier.weight(1f)) { payeeAccount = it }
                    } else {
                        Column(Modifier.weight(1f)) {
                            Text(model.t("bills.payeeAccount"), style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(existing?.payeeAccountMasked.orEmpty(), Modifier.weight(1f))
                                TextButton(onClick = { revealing = true }) { Text(model.t("account.show")) }
                                TextButton(onClick = { changingNumber = true }) { Text(model.t("bills.changeNumber")) }
                            }
                        }
                    }
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
            // BILL-17: the meter its statements' readings go to (UTL-01).
            if (kind == BillKind.BILL && (utility || meters.isNotEmpty())) {
                Picker(model.t("bills.meter"), listOf(null) + meters, meters.firstOrNull { it.id == meterId }, { it?.name ?: model.t("common.none") }) { meterId = it?.id }
            }
            // BILL-18: the captured bill becomes the first statement.
            if (statement != null && account != null) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(model.t("bills.firstStatement"), style = MaterialTheme.typography.titleSmall)
                StatementInputs(model, statement, account.currency, utility)
            }
            if (existing != null) {
                LabeledCheckbox(model.t("bills.active"), active) { active = it }
                TextButton(onClick = { confirmDelete = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
                // CON-04: the company that sends the bill, as a contact.
                LinkedContacts(model, LinkTarget.BILL, existing.id, suggestedName = existing.payeeName ?: existing.name)
                if (existing.kind == BillKind.BILL) BillStatements(model, existing, utility)
                BillHistory(model, existing)
            }
        }
    }
    if (revealing && existing != null) RevealNumberDialog(model, { books.bills.revealAccountNumber(existing.id, it) }) { revealing = false }
    if (confirmDelete && existing != null) {
        FormDialog(model.t("bills.delete.title"), model.t("common.delete"), model.t("common.cancel"), onDismiss = { confirmDelete = false }, onSave = {
            if (model.act { books.bills.delete(existing.id) } != null) {
                confirmDelete = false
                onClose()
            }
        }) { Text(model.t("bills.delete.body", existing.name)) }
    }
}

@Composable
private fun StatementInputs(model: BooksModel, f: StatementFields, currency: Currency, utility: Boolean) {
    val locale = model.language.locale
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextInput(model.t("bills.statementNumber"), f.number, Modifier.weight(1f)) { f.number = it }
        AmountInput(model.t("register.amount"), f.amount, currency, locale, Modifier.weight(1f), model::money) { f.amount = it }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateInput(model.t("bills.issuedDate"), f.issued, Modifier.weight(1f)) { f.issued = it }
        DateInput(model.t("bills.dueDate"), f.due, Modifier.weight(1f)) { f.due = it }
    }
    if (utility) ReadingInputs(model, f.readings)
}

/** BILL-16: the statements received for a bill, latest first, each with its document, readings and payment. */
@Composable
private fun BillStatements(model: BooksModel, bill: Bill, utility: Boolean) {
    val books = model.books
    val statements = remember(model.revision, bill.id) { runCatching { books.bills.statements(bill.id) }.getOrDefault(emptyList()) }
    var editing by remember { mutableStateOf<BillStatement?>(null) }
    var adding by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf<Occurrence?>(null) }
    var viewing by remember { mutableStateOf<String?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(model.t("bills.statements"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { adding = true }) { Text(model.t("bills.addStatement")) }
    }
    if (statements.isEmpty()) Text(model.t("bills.statements.none"), style = MaterialTheme.typography.bodySmall)
    for (s in statements.take(STATEMENTS_SHOWN)) {
        val occurrence = remember(model.revision, s.id) { books.bills.occurrenceOf(s) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(model.t("bills.dueOn", model.date(s.dueDate)), s.amount?.let(model::money), s.statementNumber?.let { model.t("bills.statementNo", it) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                )
                val r = s.readings
                val details = listOfNotNull(
                    s.issuedDate?.let { model.t("bills.issuedOn", model.date(it)) },
                    r.current?.let { model.t("bills.readingOn", it.toPlainString(), r.currentDate?.let(model::date).orEmpty()) },
                    r.usedOrComputed?.let { model.t("bills.usedShort", it.toPlainString()) },
                    occurrence?.takeIf { it.status == OccurrenceStatus.PAID }?.paidDate?.let { model.t("bills.paidOn", model.date(it)) },
                )
                if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            if (s.documentId != null) TextButton(onClick = { viewing = s.documentId }) { Text(model.t("bills.viewDocument")) }
            if (occurrence != null && occurrence.status == OccurrenceStatus.DUE) TextButton(onClick = { paying = occurrence }) { Text(model.t("bills.markPaid")) }
            TextButton(onClick = { editing = s }) { Text(model.t("common.edit")) }
        }
    }
    if (adding || editing != null) StatementDialog(model, bill, editing, utility) { adding = false; editing = null }
    paying?.let { o -> PayDialog(model, o) { paying = null } }
    viewing?.let { id ->
        val doc = remember(id) { runCatching { books.documents.get(id) }.getOrNull() }
        if (doc == null) {
            viewing = null
        } else {
            WideDialog(doc.label, model.t("common.close"), { viewing = null }) { DocumentViewer(model, doc, Modifier.width(560.dp).height(620.dp)) }
        }
    }
}

/** Statements listed in the bill form; two years of monthly bills. */
private const val STATEMENTS_SHOWN = 24

/** BILL-16, BILL-17: add or correct a statement. */
@Composable
private fun StatementDialog(model: BooksModel, bill: Bill, existing: BillStatement?, utility: Boolean, onClose: () -> Unit) {
    val locale = model.language.locale
    val fields = remember {
        StatementFields(
            existing?.let { StatementDraft(it.dueDate, it.amount, it.statementNumber, it.issuedDate, it.documentId, it.readings, it.meterId, it.notes) }
                ?: StatementDraft(bill.recurrence.next(bill.startDate, today(), bill.endDate) ?: today()),
            locale,
        )
    }
    var confirmDelete by remember { mutableStateOf(false) }
    FormDialog(model.t(if (existing == null) "bills.addStatement" else "bills.editStatement") + " · " + bill.name, model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            val draft = StatementDraft(
                dateOrNull(fields.due) ?: throw ValidationException("error.invalidDate"), parseAmount(fields.amount, bill.amount.currency, locale), fields.number.ifBlank { null },
                dateOrNull(fields.issued), existing?.documentId, fields.readings.readings(model.language), existing?.meterId, existing?.notes,
            )
            model.books.bills.recordStatement(bill.id, draft, existing?.id)
        }
        if (ok != null) onClose()
    }) {
        Text(model.t("bills.statement.explain"), style = MaterialTheme.typography.bodySmall)
        StatementInputs(model, fields, bill.amount.currency, utility)
        if (utility) Text(model.t("bills.readings.hint"), style = MaterialTheme.typography.bodySmall)
        if (existing != null) TextButton(onClick = { confirmDelete = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
    }
    if (confirmDelete && existing != null) {
        FormDialog(model.t("bills.deleteStatement.title"), model.t("common.delete"), model.t("common.cancel"), onDismiss = { confirmDelete = false }, onSave = {
            if (model.act { model.books.bills.deleteStatement(bill.id, existing.id) } != null) {
                confirmDelete = false
                onClose()
            }
        }) { Text(model.t("bills.deleteStatement.body")) }
    }
}

/**
 * BILL-18: attaches a captured bill to a bill the user picks, recording it as a statement with
 * what was read (corrected here if need be). [onClose] is told whether it was filed.
 */
@Composable
internal fun AttachBillDialog(model: BooksModel, doc: ca.schippers.hfm.books.VaultDocument, onClose: (Boolean) -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val bills = remember(model.revision) { books.bills.list().filter { it.kind == BillKind.BILL } }
    val lists = remember(model.revision) { books.billLists.lists() }
    var billId by remember { mutableStateOf(books.documents.billFor(doc.id)?.id ?: bills.firstOrNull()?.id) }
    val bill = bills.firstOrNull { it.id == billId }
    val read = doc.draft
    val fields = remember {
        StatementFields(
            StatementDraft(read?.dueDate?.value ?: doc.date ?: today(), doc.amount, read?.invoiceNumber?.value, doc.date, readings = read?.meter?.value ?: MeterReadings()),
            locale,
        )
    }
    FormDialog(model.t("documents.attachToBill"), model.t("common.save"), model.t("common.cancel"), canSave = bill != null, onDismiss = { onClose(false) }, onSave = {
        val ok = model.act {
            val b = bill!!
            val draft = StatementDraft(
                dateOrNull(fields.due) ?: throw ValidationException("error.invalidDate"), parseAmount(fields.amount, b.amount.currency, locale), fields.number.ifBlank { null },
                dateOrNull(fields.issued), doc.id, fields.readings.readings(model.language),
            )
            books.documents.fileWithBill(doc.id, b.id, draft)
        }
        if (ok != null) onClose(true)
    }) {
        Text(model.t("documents.attachToBill.explain"), style = MaterialTheme.typography.bodySmall)
        Picker(model.t("documents.whichBill"), bills, bill, { b -> listOfNotNull(b.name, lists.subcategory(b.subcategoryKey)?.let { lists.label(it, model.language) }).joinToString(" · ") }) { billId = it.id }
        if (bill != null) {
            val meter = remember(bill.id) { books.utilities.meterFor(bill.id) }
            StatementInputs(model, fields, bill.amount.currency, lists.isUtility(bill.subcategoryKey) || meter != null || read?.meter != null)
        }
    }
}
