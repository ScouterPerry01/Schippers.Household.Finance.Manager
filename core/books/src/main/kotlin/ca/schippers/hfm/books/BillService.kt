package ca.schippers.hfm.books

import ca.schippers.hfm.calc.rules.LeadTimes
import ca.schippers.hfm.calc.rules.Thresholds
import ca.schippers.hfm.calc.schedule.BusinessDayAdjust
import ca.schippers.hfm.calc.schedule.BusinessDays
import ca.schippers.hfm.calc.schedule.Frequency
import ca.schippers.hfm.calc.schedule.Recurrence
import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.TaxFlag
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.MeterReadings
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import java.math.BigDecimal
import java.math.RoundingMode
import ca.schippers.hfm.data.ledger.Bill as BillRow
import ca.schippers.hfm.data.ledger.Bill_occurrence as OccurrenceRow
import ca.schippers.hfm.data.ledger.Bill_payment as PaymentRow
import ca.schippers.hfm.data.ledger.Bill_statement as StatementRow

enum class BillKind { BILL, INCOME, TRANSFER }
enum class AmountKind { FIXED, VARIABLE, ESTIMATED }
enum class PaymentMethod { PAD, ONLINE, CARD, CHEQUE, CASH, OTHER }
enum class OccurrenceStatus { DUE, PAID, SKIPPED }

/** A bill, a regular income or a scheduled transfer (BILL-01, BILL-02, BILL-10, BILL-11). Amounts are positive. */
data class Bill(
    val id: String,
    val kind: BillKind,
    val name: String,
    val payeeName: String?,
    /** BILL-15: the account number with the company, masked ("•••• 6789"); the full one through [BillService.revealAccountNumber]. */
    val payeeAccountMasked: String?,
    val amount: Money,
    val amountKind: AmountKind,
    val accountId: String,
    val transferAccountId: String?,
    val paymentMethod: PaymentMethod,
    val categoryId: String?,
    val recurrence: Recurrence,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val reminderDays: List<Int>,
    val isSubscription: Boolean,
    val renewalDate: LocalDate?,
    val cancelBy: LocalDate?,
    val notes: String?,
    val active: Boolean,
    /** BILL-13: home or business, and the category and subcategory keys of the bill lists. */
    val type: BillType? = null,
    val categoryKey: String? = null,
    val subcategoryKey: String? = null,
    /** BILL-20: the person a Business bill belongs to. */
    val memberId: String? = null,
)

data class BillDraft(
    val kind: BillKind,
    val name: String,
    val amount: Money,
    val accountId: String,
    val recurrence: Recurrence,
    val startDate: LocalDate,
    val payeeName: String? = null,
    val payeeAccountNumber: String? = null,
    val amountKind: AmountKind = AmountKind.FIXED,
    val transferAccountId: String? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.ONLINE,
    val categoryId: String? = null,
    val endDate: LocalDate? = null,
    val reminderDays: List<Int> = LeadTimes.newBill(),
    val isSubscription: Boolean = false,
    val renewalDate: LocalDate? = null,
    val cancelBy: LocalDate? = null,
    val notes: String? = null,
    val type: BillType? = null,
    val categoryKey: String? = null,
    val subcategoryKey: String? = null,
    val memberId: String? = null,
)

/** BILL-16, BILL-17: a statement received for a bill, with a utility's meter readings. */
data class BillStatement(
    val id: String,
    val billId: String,
    val statementNumber: String?,
    val issuedDate: LocalDate?,
    val dueDate: LocalDate,
    val amount: Money?,
    val documentId: String?,
    val readings: MeterReadings,
    /** The meter its readings were added to. */
    val meterId: String?,
    val notes: String?,
    /** BILL-23: the instalments it lists, in date order; empty for a bill paid at once. */
    val instalments: List<BillInstalment> = emptyList(),
)

/** BILL-23: one instalment a statement lists: a due date of the bill, with its amount. */
data class BillInstalment(val dueDate: LocalDate, val amount: Money)

/** What is recorded for a statement (BILL-16, BILL-17). */
data class StatementDraft(
    val dueDate: LocalDate,
    val amount: Money? = null,
    val statementNumber: String? = null,
    val issuedDate: LocalDate? = null,
    val documentId: String? = null,
    val readings: MeterReadings = MeterReadings(),
    /** The meter to add the readings to; by default the meter linked to the bill. */
    val meterId: String? = null,
    val notes: String? = null,
    /** BILL-23: the instalments it lists; each becomes a due date of the bill (the first is its due date). */
    val instalments: List<BillInstalment> = emptyList(),
)

/** BILL-22: one payment made toward a due date, with the transaction recorded for it. */
data class BillPayment(val id: String, val billId: String, val dueDate: LocalDate, val amount: Money, val paidDate: LocalDate, val transactionId: String?)

/** One due date of a bill, with the amount expected or actually billed. */
data class Occurrence(
    val bill: Bill,
    val dueDate: LocalDate,
    /** The amount due: the bill's, the statement's or the one set for this due date. */
    val amount: Money,
    /** True when the amount comes from the actual bill or payment, not an estimate. */
    val amountKnown: Boolean,
    val status: OccurrenceStatus,
    val transactionId: String?,
    val paidDate: LocalDate?,
    /** BILL-22: the payments made toward it, oldest first. */
    val payments: List<BillPayment> = emptyList(),
    /** BILL-23: its number among the instalments of its statement ([instalment] of [instalments]). */
    val instalment: Int? = null,
    val instalments: Int? = null,
    /** BILL-24: an instalment proposed from last year's, until the new statement is entered. */
    val estimated: Boolean = false,
) {
    /** BILL-22: paid so far. A due date paid before payments were recorded one by one counts as paid in full. */
    val paidSoFar: Money
        get() = when {
            payments.isNotEmpty() -> payments.fold(Money.zero(amount.currency)) { a, p -> a + p.amount }
            status == OccurrenceStatus.PAID -> amount
            else -> Money.zero(amount.currency)
        }

    /** BILL-22: what is still due: the amount due less what was paid; nothing once paid or skipped. */
    val outstanding: Money
        get() = if (status != OccurrenceStatus.DUE) Money.zero(amount.currency) else (amount - paidSoFar).let { if (it.isNegative) Money.zero(amount.currency) else it }

    /** What reminders, totals and calendars show: the outstanding amount while due, else the amount. */
    val shownAmount: Money get() = if (status == OccurrenceStatus.DUE) outstanding else amount

    /** Effect on the paying account still to come (the outstanding amount while due): money out for bills and transfers, in for income. */
    val signedAmount: Money get() = if (bill.kind == BillKind.INCOME) shownAmount else -shownAmount
}

/** BILL-05: the bill list grouped as overdue, due today, upcoming, recently paid and skipped. */
data class Agenda(
    val overdue: List<Occurrence>,
    val dueToday: List<Occurrence>,
    val upcoming: List<Occurrence>,
    val paid: List<Occurrence>,
    /** Due dates skipped in the same period, newest first, so a skip can be undone. */
    val skipped: List<Occurrence> = emptyList(),
)

data class Reminder(val occurrence: Occurrence, val daysBefore: Int)

/** BILL-09: a paid amount compared with the usual and with the same month last year. */
data class BillHistoryEntry(val occurrence: Occurrence, val average: Money?, val sameMonthLastYear: Money?, val unusual: Boolean)

/** BILL-10. */
data class Subscription(val bill: Bill, val annualCost: Money, val nextDue: LocalDate?)

data class ForecastPoint(val date: LocalDate, val balance: Money, val occurrence: Occurrence?)

/** BILL-07 / BILL-08: projected balance of one account and the payments that would overdraw it. */
data class AccountForecast(val account: Account, val start: Money, val points: List<ForecastPoint>, val lowest: Money, val shortfalls: List<ForecastPoint>) {
    /** The projected balance at the end of [date]. */
    fun balanceOn(date: LocalDate): Money = points.lastOrNull { it.date <= date }?.balance ?: start
}

/**
 * BILL-08, section 12 cash flow forecast: the balance of every bank account day by day, and their
 * total in the base currency (at today's rate). Bank accounts in a currency with no rate are left
 * out of [total] and listed in [missingRates].
 */
data class CashFlowForecast(
    val dates: List<LocalDate>,
    val accounts: List<AccountForecast>,
    val total: List<Money>,
    val missingRates: Set<Currency>,
) {
    /** The first day the bank accounts together would be below zero, if any. */
    val firstShortfall: LocalDate? get() = dates.indices.firstOrNull { total[it].isNegative }?.let { dates[it] }
}

/**
 * Bills and payment scheduling (section 9). The application records, reminds and forecasts;
 * payments are made through the bank (section 1.3).
 */
class BillService internal constructor(private val books: Books) {

    // --- Bills ----------------------------------------------------------------------------------

    fun list(includeInactive: Boolean = false): List<Bill> = books.groups().flatMap { group ->
        val accounts = books.ledger(group).ledgerQueries.accounts().executeAsList().associate { it.id to Currency.of(it.currency) }
        books.ledger(group).ledgerQueries.bills().executeAsList().map { it.toBill(accounts.getValue(it.account_id)) }
    }.filter { includeInactive || it.active }

    fun get(billId: String): Bill = locate(billId).second

    fun create(draft: BillDraft): Bill {
        val (group, account) = books.accounts.locate(draft.accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(draft, account)
        val id = Ids.newId()
        val now = books.now()
        // BILL-13: a subcategory's spending category, when none was chosen.
        val category = draft.categoryId ?: draft.subcategoryKey?.takeIf { draft.kind == BillKind.BILL }?.let { books.billLists.spendingCategory(it) }
        with(draft) {
            books.ledger(group).ledgerQueries.insertBill(
                id, kind.name, name.trim(), payeeName?.trim()?.ifEmpty { null }, payeeAccountNumber?.trim()?.ifEmpty { null },
                amount.minorUnits, amountKind.name, accountId, transferAccountId, paymentMethod.name, category,
                recurrence.encode(), startDate.toString(), endDate?.toString(), reminderDays.sorted().joinToString(","),
                if (isSubscription) 1 else 0, renewalDate?.toString(), cancelBy?.toString(), notes, 1, now, now,
                type?.name, categoryKey, subcategoryKey, memberId.takeIf { type == BillType.BUSINESS },
            )
        }
        books.session.audit("CREATE", "bill", id)
        return get(id)
    }

    /**
     * Saves a bill. BILL-15: the account number with the company is kept unless [newAccountNumber]
     * is given (blank removes it), since [Bill] only holds it masked.
     */
    fun update(bill: Bill, newAccountNumber: String? = null) {
        val (group, existing) = locate(bill.id)
        books.require(group, PermissionLevel.EDIT)
        validate(bill.accountId == existing.accountId, "error.billAccountFixed")
        val q = books.ledger(group).ledgerQueries
        val number = if (newAccountNumber != null) newAccountNumber.trim().ifEmpty { null } else q.billById(bill.id).executeAsOne().payee_account_number
        val draft = BillDraft(
            bill.kind, bill.name, bill.amount, bill.accountId, bill.recurrence, bill.startDate, bill.payeeName, number,
            bill.amountKind, bill.transferAccountId, bill.paymentMethod, bill.categoryId, bill.endDate, bill.reminderDays,
            bill.isSubscription, bill.renewalDate, bill.cancelBy, bill.notes, bill.type, bill.categoryKey, bill.subcategoryKey, bill.memberId,
        )
        validate(draft, books.accounts.get(bill.accountId))
        with(bill) {
            q.updateBill(
                kind.name, name.trim(), payeeName?.trim()?.ifEmpty { null }, number,
                amount.minorUnits, amountKind.name, accountId, transferAccountId, paymentMethod.name, categoryId,
                recurrence.encode(), startDate.toString(), endDate?.toString(), reminderDays.sorted().joinToString(","),
                if (isSubscription) 1 else 0, renewalDate?.toString(), cancelBy?.toString(), notes, if (active) 1 else 0, books.now(),
                type?.name, categoryKey, subcategoryKey, memberId.takeIf { type == BillType.BUSINESS }, id,
            )
        }
        books.session.audit("UPDATE", "bill", bill.id)
    }

    /** BILL-15: the full account number with the company, only after the user re-enters their password (SEC-04). */
    fun revealAccountNumber(billId: String, password: CharArray): String? {
        val (group, _) = locate(billId)
        books.revealGuard.check(password, "bill", billId)
        books.session.audit("REVEAL", "bill", billId)
        return books.ledger(group).ledgerQueries.billById(billId).executeAsOne().payee_account_number
    }

    /** BILL-03: the bills' full account numbers, for matching a captured bill; never shown. */
    internal fun accountNumbers(): Map<String, String> = books.groups().flatMap { g ->
        books.ledger(g).ledgerQueries.bills().executeAsList().mapNotNull { r -> r.payee_account_number?.let { r.id to it } }
    }.toMap()

    fun delete(billId: String) {
        val (group, _) = locate(billId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).ledgerQueries.deleteBill(billId)
        books.session.audit("DELETE", "bill", billId)
    }

    private fun validate(draft: BillDraft, account: Account) {
        validate(draft.name.isNotBlank(), "error.nameRequired")
        validate(draft.amount.currency == account.currency, "error.currencyMismatch", account.currency.code)
        validate(!draft.amount.isNegative, "error.billAmountPositive")
        validate(draft.endDate == null || draft.endDate >= draft.startDate, "error.endBeforeStart")
        validate(draft.reminderDays.all { it in 0..365 }, "error.reminderDays")
        if (draft.kind == BillKind.TRANSFER) {
            validate(draft.transferAccountId != null && draft.transferAccountId != draft.accountId, "error.transferSameAccount")
        }
        // BILL-13: a category of the bill's type, and a subcategory of that category.
        if (draft.categoryKey != null || draft.subcategoryKey != null) {
            val lists = books.billLists.lists()
            val category = lists.category(draft.categoryKey)
            validate(category != null && category.type == draft.type, "error.billClassification")
            validate(draft.subcategoryKey == null || lists.subcategory(draft.subcategoryKey)?.categoryKey == draft.categoryKey, "error.billClassification")
        }
        // BILL-20: a Business bill names the person whose business it is.
        if (draft.type == BillType.BUSINESS) {
            validate(draft.memberId != null && books.members.list(includeArchived = true).any { it.id == draft.memberId }, "error.billPerson")
        }
    }

    // --- Occurrences ----------------------------------------------------------------------------

    /** Every due date of every active bill between [from] and [to], with paid and skipped ones included. */
    fun occurrences(from: LocalDate, to: LocalDate, billIds: Set<String>? = null): List<Occurrence> = books.groups().flatMap { group ->
        val q = books.ledger(group).ledgerQueries
        val currencies = q.accounts().executeAsList().associate { it.id to Currency.of(it.currency) }
        // BILL-22, BILL-23: the payments and the statements with instalments, read once per ledger.
        val payments = q.allBillPayments().executeAsList().groupBy { it.bill_id }
        val listing = q.instalmentStatements().executeAsList().groupBy({ it.bill_id }, { it.instalments })
        q.bills().executeAsList()
            .filter { billIds == null || it.id in billIds }
            .map { it.toBill(currencies.getValue(it.account_id)) }
            .filter { it.active }
            .flatMap { bill -> occurrencesOf(bill, q.occurrencesForBill(bill.id).executeAsList(), payments[bill.id].orEmpty(), listing[bill.id].orEmpty(), from, to) }
    }.sortedWith(compareBy({ it.dueDate }, { it.bill.name }))

    /** The due dates of one bill between [from] and [to], read from its ledger. */
    private fun occurrencesOf(group: GroupInfo, bill: Bill, from: LocalDate, to: LocalDate): List<Occurrence> {
        val q = books.ledger(group).ledgerQueries
        return occurrencesOf(
            bill, q.occurrencesForBill(bill.id).executeAsList(), q.billPayments(bill.id).executeAsList(),
            q.billStatements(bill.id).executeAsList().sortedBy { it.due_date }.mapNotNull { it.instalments }, from, to,
        )
    }

    private fun occurrencesOf(bill: Bill, stored: List<OccurrenceRow>, payments: List<PaymentRow>, listings: List<String>, from: LocalDate, to: LocalDate): List<Occurrence> {
        val currency = bill.amount.currency
        val byDate = stored.associateBy { LocalDate.parse(it.due_date) }
        val paidToward = payments.groupBy { LocalDate.parse(it.due_date) }
        val expected = expectedAmount(bill, stored)
        // BILL-16: a scheduled due date a statement moved is due on the statement's date instead.
        val moved = stored.mapNotNull { it.scheduled_date?.let(LocalDate::parse) }.filter { it !in byDate }.toSet()
        // BILL-23: the instalments the statements list, numbered within their statement.
        val listed = listings.map { decodeInstalments(it, currency) }.filter { it.isNotEmpty() }
        val numbers = HashMap<LocalDate, Pair<Int, Int>>()
        for (list in listed) list.forEachIndexed { i, it -> numbers.putIfAbsent(it.dueDate, i + 1 to list.size) }
        // BILL-24: next years' instalments, proposed until their statement is entered.
        val proposed = if (bill.recurrence.frequency == Frequency.INSTALMENTS) rollOver(bill, listed, to).filterKeys { it !in numbers } else emptyMap()
        val dates = (
            bill.recurrence.occurrences(bill.startDate, from, to, bill.endDate).filter { it !in moved } +
                byDate.keys.filter { it in from..to } +
                proposed.keys.filter { it in from..to && (bill.endDate == null || it <= bill.endDate) }
            ).toSortedSet()
        return dates.map { date ->
            val row = byDate[date]
            val estimate = proposed[date]
            val number = numbers[date] ?: estimate?.let { it.number to it.of }
            Occurrence(
                bill, date,
                row?.amount_minor?.let { Money.ofMinor(it, currency) } ?: estimate?.amount ?: expected,
                amountKnown = row?.amount_minor != null || (estimate == null && bill.amountKind == AmountKind.FIXED),
                status = row?.status?.let(OccurrenceStatus::valueOf) ?: OccurrenceStatus.DUE,
                transactionId = row?.txn_id,
                paidDate = row?.paid_date?.let(LocalDate::parse),
                payments = paidToward[date].orEmpty().map { it.toPayment(currency) },
                instalment = number?.first,
                instalments = number?.second,
                estimated = estimate != null,
            )
        }
    }

    /** BILL-24: an instalment proposed from last year's: its amount and its number on that statement. */
    private data class Proposed(val amount: Money, val number: Int, val of: Int)

    /**
     * BILL-24: the instalments of the last year listed (those within a year of the latest), each
     * proposed again a year later, and every year after, on the same date moved to a business day
     * (the bill's choice for weekends and holidays, the next business day by default) with the same
     * amount, up to [to]. Only dates after the latest instalment listed are proposed, and an
     * instalment whose next year's one is already listed is not, so entering next year's statement
     * replaces them.
     */
    private fun rollOver(bill: Bill, listed: List<List<BillInstalment>>, to: LocalDate): Map<LocalDate, Proposed> {
        val all = listed.flatMap { list -> list.mapIndexed { i, it -> Triple(it, i + 1, list.size) } }
        val latest = all.maxOfOrNull { it.first.dueDate } ?: return emptyMap()
        val lastYear = all.filter { it.first.dueDate > latest.minus(DatePeriod(years = 1)) }
        fun adjusted(raw: LocalDate) = if (bill.recurrence.adjust == BusinessDayAdjust.PREVIOUS) BusinessDays.previousOrSame(raw) else BusinessDays.nextOrSame(raw)
        // An instalment listed this close to where one would be proposed is that one, already entered:
        // at most 45 days, and less than half the time between two instalments (monthly plans).
        val gaps = lastYear.map { it.first.dueDate.toEpochDays() }.sorted().zipWithNext { a, b -> b - a }
        val tolerance = minOf(45L, (gaps.minOrNull() ?: 90L) / 2)
        val actual = all.map { it.first.dueDate.toEpochDays() }
        fun listedNear(date: LocalDate) = actual.any { kotlin.math.abs(it - date.toEpochDays()) < tolerance }
        val template = lastYear.filterNot { listedNear(adjusted(it.first.dueDate.plus(DatePeriod(years = 1)))) }
        val out = sortedMapOf<LocalDate, Proposed>()
        for (years in 1..MAX_ROLL_OVER_YEARS) {
            var any = false
            for ((instalment, number, of) in template) {
                val raw = instalment.dueDate.plus(DatePeriod(years = years))
                if (raw > to) continue
                any = true
                val date = adjusted(raw)
                if (date > latest) out.putIfAbsent(date, Proposed(instalment.amount, number, of))
            }
            if (!any) break
        }
        return out
    }

    /** Variable bills are expected to cost the average of their last three actual amounts (BILL-09). */
    private fun expectedAmount(bill: Bill, stored: List<OccurrenceRow>): Money {
        if (bill.amountKind != AmountKind.VARIABLE) return bill.amount
        val recent = stored.filter { it.amount_minor != null && it.status == OccurrenceStatus.PAID.name }.takeLast(3)
        if (recent.isEmpty()) return bill.amount
        return Money.ofMinor(
            BigDecimal(recent.sumOf { it.amount_minor!! }).divide(BigDecimal(recent.size), 0, RoundingMode.HALF_UP).longValueExact(),
            bill.amount.currency,
        )
    }

    /**
     * BILL-05. Overdue looks back a year at most; [days] sets how far ahead "upcoming" reaches, by
     * default the bills agenda lead time of Rates and rules.
     */
    fun agenda(today: LocalDate, days: Int = LeadTimes.billsAgenda(today)): Agenda {
        val all = occurrences(today.minus(DatePeriod(days = 365)), today.plus(DatePeriod(days = days)))
        val due = all.filter { it.status == OccurrenceStatus.DUE }
        return Agenda(
            overdue = due.filter { it.dueDate < today },
            dueToday = due.filter { it.dueDate == today },
            upcoming = due.filter { it.dueDate > today },
            paid = all.filter { it.status == OccurrenceStatus.PAID && it.dueDate >= today.minus(DatePeriod(days = 31)) }.sortedByDescending { it.dueDate },
            skipped = all.filter { it.status == OccurrenceStatus.SKIPPED }.sortedByDescending { it.dueDate },
        )
    }

    /** BILL-03 and variable bills: records the actual amount of one bill (from the paper or e-bill). */
    fun setAmount(billId: String, dueDate: LocalDate, amount: Money, documentId: String? = null) {
        val (group, bill) = locate(billId)
        books.require(group, PermissionLevel.EDIT)
        validate(amount.currency == bill.amount.currency && !amount.isNegative, "error.billAmountPositive")
        val existing = storedOccurrence(group, billId, dueDate)
        upsert(group, billId, dueDate, amount, OccurrenceStatus.valueOf(existing?.status ?: "DUE"), existing?.txn_id, existing?.paid_date, documentId)
    }

    /**
     * BILL-06: records a payment toward a due date as a transaction in the paying account (or a
     * transfer). When the bank statement arrives, the import links to this transaction. Pass
     * [existingTransactionId] to link to a transaction already recorded instead.
     *
     * BILL-22: [amount] is what is paid now, by default what is still due. Paying less leaves the
     * rest due on the same due date; it is paid once its payments reach the amount due (within a
     * cent). Paying more than is still due needs [confirmOverpay]. A due date whose amount is not
     * known yet (a variable bill with no amount set) is paid in full by its first payment, which
     * becomes its amount.
     */
    fun markPaid(
        billId: String, dueDate: LocalDate, paidDate: LocalDate, amount: Money? = null, existingTransactionId: String? = null, confirmOverpay: Boolean = false,
    ): Occurrence {
        val (group, bill) = locate(billId)
        books.require(group, PermissionLevel.CAPTURE_ONLY)
        val q = books.ledger(group).ledgerQueries
        val stored = storedOccurrence(group, billId, dueDate)
        validate(stored?.status != OccurrenceStatus.PAID.name, "error.alreadyPaid")
        val currency = bill.amount.currency
        val current = occurrencesOf(group, bill, dueDate, dueDate).firstOrNull { it.dueDate == dueDate }
        val due = current?.amount ?: stored?.amount_minor?.let { Money.ofMinor(it, currency) } ?: expectedAmount(bill, q.occurrencesForBill(billId).executeAsList())
        val before = current?.payments.orEmpty()
        // An instalment proposed from last year's (BILL-24) is expected at last year's amount.
        val known = current?.let { it.amountKnown || it.estimated } ?: (stored?.amount_minor != null || bill.amountKind == AmountKind.FIXED)
        val paidBefore = before.fold(Money.zero(currency)) { a, p -> a + p.amount }
        val paid = amount ?: if (known) due - paidBefore else due
        validate(paid.currency == currency && paid.isPositive, "error.billAmountPositive")
        val amountDue = if (known || before.isNotEmpty()) due else paid
        val total = paidBefore + paid
        val cent = Money.ofMinor(1, currency)
        validate(confirmOverpay || total <= amountDue + cent, "error.billOverpaid", (amountDue - paidBefore).toBigDecimal().toPlainString())
        val txnId = existingTransactionId ?: when (bill.kind) {
            BillKind.TRANSFER -> books.transactions.transfer(
                TransferDraft(bill.accountId, bill.transferAccountId!!, paidDate, paid, memo = bill.name),
            ).first.id
            else -> {
                val signed = if (bill.kind == BillKind.INCOME) paid else -paid
                // BILL-13: the subcategory's spending category when the bill has none of its own.
                val category = bill.categoryId ?: bill.subcategoryKey?.let { books.billLists.spendingCategory(it) }
                // BILL-20: a Business bill's payment is that person's business expense.
                val business = bill.type == BillType.BUSINESS && bill.kind == BillKind.BILL
                val splits = when {
                    business -> listOf(SplitDraft(category, signed, memberId = bill.memberId, taxFlag = TaxFlag.BUSINESS))
                    else -> listOfNotNull(category?.let { SplitDraft(it, signed) })
                }
                val txn = books.transactions.create(
                    TransactionDraft(bill.accountId, paidDate, signed, bill.payeeName ?: bill.name, splits, memo = bill.name, memberId = bill.memberId),
                )
                if (business) businessSalesTaxes(txn, stored?.document_id)
                txn.id
            }
        }
        q.insertBillPayment(Ids.newId(), billId, dueDate.toString(), paid.minorUnits, paidDate.toString(), txnId, books.now())
        // Paid in full once the payments reach the amount due, within a cent.
        val full = total + cent >= amountDue
        upsert(
            group, billId, dueDate, amountDue, if (full) OccurrenceStatus.PAID else OccurrenceStatus.DUE, txnId.takeIf { full }, paidDate.toString().takeIf { full },
        )
        books.session.audit("PAID", "bill", billId, dueDate.toString())
        return occurrencesOf(group, bill, dueDate, dueDate).first { it.dueDate == dueDate }
    }

    /**
     * Undoes one payment of a due date ([paymentId], by default the latest), which is then due again
     * for what is no longer paid; optionally deletes the transaction recorded for it. A reconciled
     * transaction is deleted only with [confirmReconciled]; otherwise [ReconciledChangeException]
     * is thrown so the user can be asked first, and nothing changes.
     */
    fun unmarkPaid(billId: String, dueDate: LocalDate, deleteTransaction: Boolean, confirmReconciled: Boolean = false, paymentId: String? = null) {
        val (group, bill) = locate(billId)
        books.require(group, PermissionLevel.EDIT)
        val q = books.ledger(group).ledgerQueries
        val stored = storedOccurrence(group, billId, dueDate) ?: return
        val currency = bill.amount.currency
        fun deleteTransaction(id: String?) {
            // The payment may have been deleted in the register since; then there is nothing to delete.
            if (deleteTransaction && id != null && runCatching { books.transactions.get(id) }.isSuccess) books.transactions.delete(id, confirmReconciled)
        }
        val payments = q.billPayments(billId).executeAsList().filter { it.due_date == stored.due_date }
        val payment = paymentId?.let { id -> payments.firstOrNull { it.id == id } } ?: payments.lastOrNull()
        if (payment == null) {
            // Paid before payments were recorded one by one.
            deleteTransaction(stored.txn_id)
            upsert(group, billId, dueDate, stored.amount_minor?.let { Money.ofMinor(it, currency) }, OccurrenceStatus.DUE, null, null)
            return
        }
        deleteTransaction(payment.txn_id)
        q.deleteBillPayment(payment.id)
        val rest = payments.filter { it.id != payment.id }
        val due = stored.amount_minor
        val full = rest.isNotEmpty() && due != null && rest.sumOf { it.amount_minor } + 1 >= due
        val last = rest.lastOrNull()
        upsert(
            group, billId, dueDate, due?.let { Money.ofMinor(it, currency) }, if (full) OccurrenceStatus.PAID else OccurrenceStatus.DUE,
            last?.txn_id?.takeIf { full }, last?.paid_date?.takeIf { full },
        )
        books.session.audit("UPDATE", "bill", billId, dueDate.toString())
    }

    /** BILL-22: the payments made toward the due dates of a bill, oldest first. */
    fun payments(billId: String): List<BillPayment> {
        val (group, bill) = locate(billId)
        return books.ledger(group).ledgerQueries.billPayments(billId).executeAsList().map { it.toPayment(bill.amount.currency) }
    }

    /** Skips one due date; an amount already entered for it is kept, in case the skip is undone. */
    fun skip(billId: String, dueDate: LocalDate) {
        val (group, bill) = locate(billId)
        books.require(group, PermissionLevel.EDIT)
        val existing = storedOccurrence(group, billId, dueDate)
        validate(existing?.status != OccurrenceStatus.PAID.name, "error.alreadyPaid")
        upsert(group, billId, dueDate, existing?.amount_minor?.let { Money.ofMinor(it, bill.amount.currency) }, OccurrenceStatus.SKIPPED, null, null)
    }

    /** Undoes [skip]: the due date is due again, with any amount entered for it. */
    fun unskip(billId: String, dueDate: LocalDate) {
        val (group, bill) = locate(billId)
        books.require(group, PermissionLevel.EDIT)
        val existing = storedOccurrence(group, billId, dueDate)?.takeIf { it.status == OccurrenceStatus.SKIPPED.name } ?: return
        upsert(group, billId, dueDate, existing.amount_minor?.let { Money.ofMinor(it, bill.amount.currency) }, OccurrenceStatus.DUE, null, null)
    }

    // --- Statements (BILL-16, BILL-17) ---------------------------------------------------------------

    /** The statements received for a bill, the latest due date first. */
    fun statements(billId: String): List<BillStatement> {
        val (group, bill) = locate(billId)
        return books.ledger(group).ledgerQueries.billStatements(billId).executeAsList().map { it.toStatement(bill.amount.currency) }
    }

    /**
     * BILL-16: records a statement (or saves [statementId] again). Its due date becomes the due date
     * of the bill's nearest unpaid due date (within the bill match window of Rates and rules), with
     * its amount; when none is near, it is a due date of its own. BILL-17: a utility's readings are
     * added to the bill's meter (or [StatementDraft.meterId]) unless the meter already has a reading
     * on that date.
     *
     * BILL-23: a statement listing instalments puts each of them in the schedule the same way, with
     * its amount (a proposed instalment of BILL-24 near it is replaced); the first one's date is the
     * statement's due date, and their total its amount when none is given.
     */
    fun recordStatement(billId: String, draft: StatementDraft, statementId: String? = null): BillStatement {
        val (group, bill) = locate(billId)
        books.require(group, PermissionLevel.EDIT)
        val currency = bill.amount.currency
        draft.amount?.let { validate(it.currency == currency && !it.isNegative, "error.billAmountPositive") }
        val instalments = draft.instalments.distinctBy { it.dueDate }.sortedBy { it.dueDate }
        validate(instalments.size <= ca.schippers.hfm.ocr.ReadInstalment.MAX, "error.billInstalments", ca.schippers.hfm.ocr.ReadInstalment.MAX.toString())
        instalments.forEach { validate(it.amount.currency == currency && it.amount.isPositive, "error.billAmountPositive") }
        val dueDate = instalments.firstOrNull()?.dueDate ?: draft.dueDate
        val amount = draft.amount ?: instalments.takeIf { it.isNotEmpty() }?.fold(Money.zero(currency)) { a, i -> a + i.amount }
        val q = books.ledger(group).ledgerQueries
        val before = statementId?.let { id -> q.billStatements(billId).executeAsList().firstOrNull { it.id == id } }
        if (instalments.isEmpty()) {
            placeDueDate(group, bill, dueDate, amount, draft.documentId, before?.due_date?.let(LocalDate::parse))
        } else {
            val earlier = decodeInstalments(before?.instalments, currency)
            instalments.forEachIndexed { i, it ->
                val previous = earlier.getOrNull(i)?.dueDate ?: before?.due_date?.takeIf { i == 0 }?.let(LocalDate::parse)
                placeDueDate(group, bill, it.dueDate, it.amount, draft.documentId, previous)
            }
        }
        val meter = draft.meterId?.let { id -> books.utilities.meters(true).firstOrNull { it.id == id } }
            ?: books.utilities.meters().firstOrNull { it.billId == billId }
        val r = draft.readings
        if (meter != null) {
            val label = listOfNotNull(bill.name, draft.statementNumber).joinToString(" ")
            for ((value, date) in listOf(r.previous to r.previousDate, r.current to r.currentDate)) {
                if (value == null || date == null) continue
                if (books.utilities.meter(meter.id).readings.none { it.date == date }) books.utilities.addReading(meter.id, date, value, notes = label)
            }
        }
        val id = statementId ?: Ids.newId()
        q.upsertBillStatement(
            id, billId, draft.statementNumber?.trim()?.ifEmpty { null }, draft.issuedDate?.toString(), dueDate.toString(), amount?.minorUnits,
            draft.documentId ?: before?.document_id, r.previous?.toPlainString(), r.previousDate?.toString(), r.current?.toPlainString(), r.currentDate?.toString(),
            (r.used ?: r.usedOrComputed)?.toPlainString(), meter?.id, draft.notes?.trim()?.ifEmpty { null }, before?.created_at ?: books.now(),
            encodeInstalments(instalments),
        )
        draft.documentId?.let { books.ledger(group).ledgerQueries.linkDocument(it, DocumentEntity.BILL, billId) }
        books.session.audit(if (before == null) "CREATE" else "UPDATE", "billStatement", id)
        return statements(billId).first { it.id == id }
    }

    /** Deletes a statement; the due date and amount it set stay, as do readings added to a meter. */
    fun deleteStatement(billId: String, statementId: String) {
        val (group, _) = locate(billId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).ledgerQueries.deleteBillStatement(statementId)
        books.session.audit("DELETE", "billStatement", statementId)
    }

    /** The due date of [statement], to mark it paid with [markPaid]; null when it no longer exists. */
    fun occurrenceOf(statement: BillStatement): Occurrence? = occurrences(statement.dueDate, statement.dueDate, setOf(statement.billId)).firstOrNull()

    /** BILL-16: puts a statement's due date in the bill's schedule (see [recordStatement]). [previous] is the date it set before. */
    private fun placeDueDate(group: GroupInfo, bill: Bill, due: LocalDate, amount: Money?, documentId: String?, previous: LocalDate?) {
        val q = books.ledger(group).ledgerQueries
        val stored = q.occurrencesForBill(bill.id).executeAsList()
        val at = stored.firstOrNull { it.due_date == due.toString() }
        if (at != null) {
            if (at.status != OccurrenceStatus.PAID.name) {
                upsert(group, bill.id, due, amount ?: at.amount_minor?.let { Money.ofMinor(it, bill.amount.currency) }, OccurrenceStatus.valueOf(at.status), at.txn_id, at.paid_date, documentId)
            }
            return
        }
        // Within the bill match window, and less than half a period away, so another period's due date never moves.
        val period = bill.recurrence.perYear.takeIf { it > 0 }?.let { (365.0 / it / 2).toInt() } ?: Int.MAX_VALUE
        val window = minOf(LeadTimes.billMatch(due), period)
        val near = occurrencesOf(group, bill, due.minus(DatePeriod(days = window)), due.plus(DatePeriod(days = window)))
        val nearest = previous?.let { p -> near.firstOrNull { it.dueDate == p && it.status == OccurrenceStatus.DUE } }
            ?: near.filter { it.status != OccurrenceStatus.SKIPPED }.minByOrNull { kotlin.math.abs(it.dueDate.toEpochDays() - due.toEpochDays()) }
        // The nearest due date was already paid: the statement belongs to it, and nothing more is due.
        if (nearest?.status == OccurrenceStatus.PAID) return
        val moving = nearest ?: run {
            upsert(group, bill.id, due, amount, OccurrenceStatus.DUE, null, null, documentId)
            return
        }
        val row = stored.firstOrNull { it.due_date == moving.dueDate.toString() }
        if (row != null) {
            q.deleteOccurrence(bill.id, row.due_date)
            // BILL-22: payments already made toward it move with it.
            q.moveBillPayments(due.toString(), bill.id, row.due_date)
        }
        val kept = amount ?: row?.amount_minor?.let { Money.ofMinor(it, bill.amount.currency) }
        // A one-off due date (no schedule behind it), or an instalment proposed from last year's,
        // moves without leaving a scheduled date behind.
        val scheduled = if (moving.estimated) {
            null
        } else {
            row?.scheduled_date ?: moving.dueDate.toString().takeIf { row == null || bill.recurrence.occurrences(bill.startDate, moving.dueDate, moving.dueDate, bill.endDate).isNotEmpty() }
        }
        upsert(group, bill.id, due, kept, OccurrenceStatus.DUE, null, null, documentId ?: row?.document_id, scheduled)
    }

    /** BILL-20: the sales taxes the bill's document shows, on its payment, when they fit in it. */
    private fun businessSalesTaxes(txn: Transaction, documentId: String?) {
        val doc = documentId?.let { runCatching { books.documents.get(it) }.getOrNull() } ?: return
        val taxes = doc.draft?.taxes.orEmpty().filter { (_, v) -> v.value.currency == txn.amount.currency && !v.value.isNegative }
            .groupBy({ it.first }, { it.second.value }).mapValues { (_, v) -> v.reduce(Money::plus) }
        if (taxes.isEmpty() || taxes.values.fold(Money.zero(txn.amount.currency), Money::plus).minorUnits > kotlin.math.abs(txn.amount.minorUnits)) return
        books.transactions.setSalesTaxes(txn.id, taxes)
    }

    private fun StatementRow.toStatement(currency: Currency) = BillStatement(
        id, bill_id, statement_number, issued_date?.let(LocalDate::parse), LocalDate.parse(due_date), amount_minor?.let { Money.ofMinor(it, currency) }, document_id,
        MeterReadings(
            previous_reading?.toBigDecimalOrNull(), previous_reading_date?.let(LocalDate::parse), current_reading?.toBigDecimalOrNull(),
            current_reading_date?.let(LocalDate::parse), used?.toBigDecimalOrNull(),
        ),
        meter_id, notes, decodeInstalments(instalments, currency),
    )

    private fun PaymentRow.toPayment(currency: Currency) =
        BillPayment(id, bill_id, LocalDate.parse(due_date), Money.ofMinor(amount_minor, currency), LocalDate.parse(paid_date), txn_id)

    // --- Reminders, history, subscriptions --------------------------------------------------------

    /**
     * BILL-04: what to remind about today: due dates matching a bill's lead times, anything due
     * today or overdue, and subscriptions whose cancellation deadline is within a week.
     */
    fun reminders(today: LocalDate): List<Reminder> {
        val horizon = (list().flatMap { it.reminderDays }.maxOrNull() ?: 7).coerceAtLeast(7)
        val agenda = agenda(today, horizon)
        val byLeadTime = agenda.upcoming.mapNotNull { o ->
            val days = today.daysUntil(o.dueDate)
            if (days in o.bill.reminderDays) Reminder(o, days) else null
        }
        val cancellations = list().filter { it.isSubscription && it.cancelBy != null && today.daysUntil(it.cancelBy) in 0..7 }
            .map { Reminder(Occurrence(it, it.cancelBy!!, it.amount, true, OccurrenceStatus.DUE, null, null), today.daysUntil(it.cancelBy)) }
        return (agenda.overdue + agenda.dueToday).map { Reminder(it, today.daysUntil(it.dueDate)) } + byLeadTime + cancellations
    }

    /** BILL-09: paid amounts of one bill, newest first, compared with the usual and with last year. */
    fun history(billId: String): List<BillHistoryEntry> {
        val (group, bill) = locate(billId)
        val paid = paidOf(group, bill)
        return paid.map { compare(it, paid) }.reversed()
    }

    /**
     * BILL-09: one due date, paid or not, compared with the bills paid before it: their average and
     * the amount paid in the same month last year. It is unusual only when its amount is known.
     */
    fun compare(occurrence: Occurrence): BillHistoryEntry {
        val (group, bill) = locate(occurrence.bill.id)
        return compare(occurrence, paidOf(group, bill))
    }

    /** The paid due dates of a bill with their amounts, oldest first. */
    private fun paidOf(group: GroupInfo, bill: Bill): List<Occurrence> =
        books.ledger(group).ledgerQueries.occurrencesForBill(bill.id).executeAsList()
            .filter { it.status == OccurrenceStatus.PAID.name && it.amount_minor != null }
            .map { Occurrence(bill, LocalDate.parse(it.due_date), Money.ofMinor(it.amount_minor!!, bill.amount.currency), true, OccurrenceStatus.PAID, it.txn_id, it.paid_date?.let(LocalDate::parse)) }
            .sortedBy { it.dueDate }

    private fun compare(o: Occurrence, paid: List<Occurrence>): BillHistoryEntry {
        val currency = o.bill.amount.currency
        val previous = paid.filter { it.dueDate < o.dueDate }.takeLast(12)
        val average = if (previous.isEmpty()) null else Money.ofMinor(previous.sumOf { it.amount.minorUnits } / previous.size, currency)
        val lastYear = paid.firstOrNull { it.dueDate.year == o.dueDate.year - 1 && it.dueDate.month == o.dueDate.month }?.amount
        // Unusual: above the Rates and rules share of the average of the previous bills (at least three of them), 125 % built in.
        val unusual = o.amountKnown && previous.size >= 3 && average != null && o.amount > average.times(Thresholds.unusualBill(o.dueDate))
        return BillHistoryEntry(o, average, lastYear, unusual)
    }

    /** BILL-10: every subscription with its yearly cost. */
    fun subscriptions(today: LocalDate): List<Subscription> = list().filter { it.isSubscription }.map { bill ->
        val annual = bill.amount.times(BigDecimal(bill.recurrence.perYear))
        Subscription(bill, annual, bill.recurrence.next(bill.startDate, today, bill.endDate))
    }.sortedByDescending { it.annualCost.minorUnits }

    // --- Forecast -------------------------------------------------------------------------------

    /**
     * BILL-08: projected balance of every account over the next [days] days, from scheduled bills,
     * income and transfers that are still due (overdue ones are counted today). BILL-07: payments
     * that would take a bank account below zero are reported as shortfalls.
     */
    fun forecast(today: LocalDate, days: Int = LeadTimes.billsForecast(today)): List<AccountForecast> {
        val end = today.plus(DatePeriod(days = days))
        val due = occurrences(today.minus(DatePeriod(days = 365)), end).filter { it.status == OccurrenceStatus.DUE }
        val effects = due.flatMap { o ->
            val date = if (o.dueDate < today) today else o.dueDate
            buildList {
                add(Triple(o.bill.accountId, date, o))
                if (o.bill.kind == BillKind.TRANSFER) add(Triple(o.bill.transferAccountId!!, date, o))
            }
        }.groupBy { it.first }

        return books.accounts.list().map { summary ->
            val account = summary.account
            var balance = summary.balance
            val points = mutableListOf(ForecastPoint(today, balance, null))
            val shortfalls = mutableListOf<ForecastPoint>()
            for ((_, date, o) in effects[account.id].orEmpty().sortedBy { it.second }) {
                val incoming = o.bill.kind == BillKind.TRANSFER && o.bill.transferAccountId == account.id
                // A transfer into an account in another currency arrives in an amount not known yet.
                if (incoming && o.amount.currency != account.currency) continue
                val change = if (incoming) o.shownAmount else o.signedAmount
                balance += change
                val point = ForecastPoint(date, balance, o)
                points += point
                if (change.isNegative && balance.isNegative && account.type.kind == AccountKind.BANK) shortfalls += point
            }
            AccountForecast(account, summary.balance, points, points.minOf { it.balance }, shortfalls)
        }
    }

    /** BILL-08: the bank accounts' forecast day by day from [today] for [days] days, for the chart. */
    fun cashFlow(today: LocalDate, days: Int = LeadTimes.billsForecast(today)): CashFlowForecast {
        val base = books.rates.baseCurrency
        val banks = forecast(today, days).filter { it.account.type.kind == AccountKind.BANK && !it.account.currency.isCrypto }
        val dates = (0..days).map { today.plus(DatePeriod(days = it)) }
        val missing = HashSet<Currency>()
        val rates = banks.map { it.account.currency }.distinct().associateWith { c ->
            if (c == base) java.math.BigDecimal.ONE else books.rates.rate(c, base, today).also { if (it == null) missing += c }
        }
        val total = dates.map { date ->
            banks.fold(Money.zero(base)) { sum, f -> rates[f.account.currency]?.let { sum + f.balanceOn(date).convert(base, it) } ?: sum }
        }
        return CashFlowForecast(dates, banks, total, missing)
    }

    // --- Calendar export ------------------------------------------------------------------------

    /** BILL-12: the bill calendar as an iCalendar file for the user's own calendar. */
    fun iCalendar(from: LocalDate, to: LocalDate, describe: (Occurrence) -> String): String = buildString {
        append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//RANN//RANN's Roost//EN\r\nCALSCALE:GREGORIAN\r\n")
        for (o in occurrences(from, to).filter { it.status == OccurrenceStatus.DUE }) {
            val day = o.dueDate.toString().replace("-", "")
            val next = o.dueDate.plus(DatePeriod(days = 1)).toString().replace("-", "")
            append("BEGIN:VEVENT\r\n")
            append("UID:${o.bill.id}-$day@hfm\r\n")
            append("DTSTAMP:${day}T000000Z\r\n")
            append("DTSTART;VALUE=DATE:$day\r\nDTEND;VALUE=DATE:$next\r\n")
            append("SUMMARY:${escape(describe(o))}\r\n")
            append("END:VEVENT\r\n")
        }
        append("END:VCALENDAR\r\n")
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

    // --- Helpers --------------------------------------------------------------------------------

    private fun locate(billId: String): Pair<GroupInfo, Bill> {
        for (group in books.groups()) {
            val q = books.ledger(group).ledgerQueries
            val row = q.billById(billId).executeAsOneOrNull() ?: continue
            val currency = Currency.of(q.accountById(row.account_id).executeAsOne().currency)
            return group to row.toBill(currency)
        }
        throw AccessDeniedException("Bill not found or not accessible")
    }

    private fun storedOccurrence(group: GroupInfo, billId: String, dueDate: LocalDate): OccurrenceRow? =
        books.ledger(group).ledgerQueries.occurrencesForBill(billId).executeAsList().firstOrNull { it.due_date == dueDate.toString() }

    private fun upsert(
        group: GroupInfo, billId: String, dueDate: LocalDate, amount: Money?, status: OccurrenceStatus, txnId: String?, paidDate: String?, documentId: String? = null,
        scheduledDate: String? = null,
    ) {
        val existing = storedOccurrence(group, billId, dueDate)
        books.ledger(group).ledgerQueries.upsertOccurrence(
            existing?.id ?: Ids.newId(), billId, dueDate.toString(), amount?.minorUnits, status.name, txnId, paidDate, documentId ?: existing?.document_id,
            scheduledDate ?: existing?.scheduled_date,
        )
    }

    private fun BillRow.toBill(currency: Currency) = Bill(
        id, BillKind.valueOf(kind), name, payee_name, AccountService.mask(payee_account_number), Money.ofMinor(amount_minor, currency), AmountKind.valueOf(amount_kind),
        account_id, transfer_account_id, PaymentMethod.valueOf(payment_method), category_id, Recurrence.decode(recurrence),
        LocalDate.parse(start_date), end_date?.let(LocalDate::parse), reminder_days.split(',').mapNotNull { it.trim().toIntOrNull() },
        is_subscription == 1L, renewal_date?.let(LocalDate::parse), cancel_by?.let(LocalDate::parse), notes, active == 1L,
        bill_type?.let { runCatching { BillType.valueOf(it) }.getOrNull() }, bill_category, bill_subcategory, member_id,
    )
}

/** BILL-24: how many years ahead instalments are proposed at most. */
private const val MAX_ROLL_OVER_YEARS = 50

/** BILL-23: instalments as stored with a statement: "2027-02-19=119000;2027-04-16=119000" (amounts in minor units). */
internal fun encodeInstalments(list: List<BillInstalment>): String? =
    list.sortedBy { it.dueDate }.joinToString(";") { "${it.dueDate}=${it.amount.minorUnits}" }.ifEmpty { null }

internal fun decodeInstalments(text: String?, currency: Currency): List<BillInstalment> =
    text?.split(';')?.mapNotNull { part ->
        val date = runCatching { LocalDate.parse(part.substringBefore('=').trim()) }.getOrNull()
        val minor = part.substringAfter('=', "").trim().toLongOrNull()
        if (date == null || minor == null) null else BillInstalment(date, Money.ofMinor(minor, currency))
    }?.sortedBy { it.dueDate }.orEmpty()
