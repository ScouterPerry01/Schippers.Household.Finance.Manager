package ca.schippers.hfm.books

import ca.schippers.hfm.calc.schedule.Recurrence
import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import java.math.BigDecimal
import java.math.RoundingMode
import ca.schippers.hfm.data.ledger.Bill as BillRow
import ca.schippers.hfm.data.ledger.Bill_occurrence as OccurrenceRow

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
    val payeeAccountNumber: String?,
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
    val reminderDays: List<Int> = listOf(7, 1),
    val isSubscription: Boolean = false,
    val renewalDate: LocalDate? = null,
    val cancelBy: LocalDate? = null,
    val notes: String? = null,
)

/** One due date of a bill, with the amount expected or actually billed. */
data class Occurrence(
    val bill: Bill,
    val dueDate: LocalDate,
    val amount: Money,
    /** True when the amount comes from the actual bill or payment, not an estimate. */
    val amountKnown: Boolean,
    val status: OccurrenceStatus,
    val transactionId: String?,
    val paidDate: LocalDate?,
) {
    /** Effect on the paying account: money out for bills and transfers, in for income. */
    val signedAmount: Money get() = if (bill.kind == BillKind.INCOME) amount else -amount
}

/** BILL-05: the bill list grouped as overdue, due today, upcoming and recently paid. */
data class Agenda(
    val overdue: List<Occurrence>,
    val dueToday: List<Occurrence>,
    val upcoming: List<Occurrence>,
    val paid: List<Occurrence>,
)

data class Reminder(val occurrence: Occurrence, val daysBefore: Int)

/** BILL-09: a paid amount compared with the usual and with the same month last year. */
data class BillHistoryEntry(val occurrence: Occurrence, val average: Money?, val sameMonthLastYear: Money?, val unusual: Boolean)

/** BILL-10. */
data class Subscription(val bill: Bill, val annualCost: Money, val nextDue: LocalDate?)

data class ForecastPoint(val date: LocalDate, val balance: Money, val occurrence: Occurrence?)

/** BILL-07 / BILL-08: projected balance of one account and the payments that would overdraw it. */
data class AccountForecast(val account: Account, val start: Money, val points: List<ForecastPoint>, val lowest: Money, val shortfalls: List<ForecastPoint>)

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
        with(draft) {
            books.ledger(group).ledgerQueries.insertBill(
                id, kind.name, name.trim(), payeeName?.trim()?.ifEmpty { null }, payeeAccountNumber?.trim()?.ifEmpty { null },
                amount.minorUnits, amountKind.name, accountId, transferAccountId, paymentMethod.name, categoryId,
                recurrence.encode(), startDate.toString(), endDate?.toString(), reminderDays.sorted().joinToString(","),
                if (isSubscription) 1 else 0, renewalDate?.toString(), cancelBy?.toString(), notes, 1, now, now,
            )
        }
        books.session.audit("CREATE", "bill", id)
        return get(id)
    }

    fun update(bill: Bill) {
        val (group, existing) = locate(bill.id)
        books.require(group, PermissionLevel.EDIT)
        validate(bill.accountId == existing.accountId, "error.billAccountFixed")
        val draft = BillDraft(
            bill.kind, bill.name, bill.amount, bill.accountId, bill.recurrence, bill.startDate, bill.payeeName, bill.payeeAccountNumber,
            bill.amountKind, bill.transferAccountId, bill.paymentMethod, bill.categoryId, bill.endDate, bill.reminderDays,
            bill.isSubscription, bill.renewalDate, bill.cancelBy, bill.notes,
        )
        validate(draft, books.accounts.get(bill.accountId))
        with(bill) {
            books.ledger(group).ledgerQueries.updateBill(
                kind.name, name.trim(), payeeName?.trim()?.ifEmpty { null }, payeeAccountNumber?.trim()?.ifEmpty { null },
                amount.minorUnits, amountKind.name, accountId, transferAccountId, paymentMethod.name, categoryId,
                recurrence.encode(), startDate.toString(), endDate?.toString(), reminderDays.sorted().joinToString(","),
                if (isSubscription) 1 else 0, renewalDate?.toString(), cancelBy?.toString(), notes, if (active) 1 else 0, books.now(), id,
            )
        }
        books.session.audit("UPDATE", "bill", bill.id)
    }

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
    }

    // --- Occurrences ----------------------------------------------------------------------------

    /** Every due date of every active bill between [from] and [to], with paid and skipped ones included. */
    fun occurrences(from: LocalDate, to: LocalDate, billIds: Set<String>? = null): List<Occurrence> = books.groups().flatMap { group ->
        val q = books.ledger(group).ledgerQueries
        val currencies = q.accounts().executeAsList().associate { it.id to Currency.of(it.currency) }
        q.bills().executeAsList()
            .filter { billIds == null || it.id in billIds }
            .map { it.toBill(currencies.getValue(it.account_id)) }
            .filter { it.active }
            .flatMap { bill -> occurrencesOf(bill, q.occurrencesForBill(bill.id).executeAsList(), from, to) }
    }.sortedWith(compareBy({ it.dueDate }, { it.bill.name }))

    private fun occurrencesOf(bill: Bill, stored: List<OccurrenceRow>, from: LocalDate, to: LocalDate): List<Occurrence> {
        val byDate = stored.associateBy { LocalDate.parse(it.due_date) }
        val expected = expectedAmount(bill, stored)
        val dates = (bill.recurrence.occurrences(bill.startDate, from, to, bill.endDate) + byDate.keys.filter { it in from..to }).toSortedSet()
        return dates.map { date ->
            val row = byDate[date]
            Occurrence(
                bill, date,
                row?.amount_minor?.let { Money.ofMinor(it, bill.amount.currency) } ?: expected,
                amountKnown = row?.amount_minor != null || bill.amountKind == AmountKind.FIXED,
                status = row?.status?.let(OccurrenceStatus::valueOf) ?: OccurrenceStatus.DUE,
                transactionId = row?.txn_id,
                paidDate = row?.paid_date?.let(LocalDate::parse),
            )
        }
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

    /** BILL-05. Overdue looks back a year at most; [days] sets how far ahead "upcoming" reaches. */
    fun agenda(today: LocalDate, days: Int = 30): Agenda {
        val all = occurrences(today.minus(DatePeriod(days = 365)), today.plus(DatePeriod(days = days)))
        val due = all.filter { it.status == OccurrenceStatus.DUE }
        return Agenda(
            overdue = due.filter { it.dueDate < today },
            dueToday = due.filter { it.dueDate == today },
            upcoming = due.filter { it.dueDate > today },
            paid = all.filter { it.status == OccurrenceStatus.PAID && it.dueDate >= today.minus(DatePeriod(days = 31)) }.sortedByDescending { it.dueDate },
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
     * BILL-06: marks a due date as paid and records the payment as a transaction in the paying
     * account (or a transfer). When the bank statement arrives, the import links to this transaction.
     * Pass [existingTransactionId] to link to a transaction already recorded instead.
     */
    fun markPaid(billId: String, dueDate: LocalDate, paidDate: LocalDate, amount: Money? = null, existingTransactionId: String? = null): Occurrence {
        val (group, bill) = locate(billId)
        books.require(group, PermissionLevel.CAPTURE_ONLY)
        val stored = storedOccurrence(group, billId, dueDate)
        validate(stored?.status != OccurrenceStatus.PAID.name, "error.alreadyPaid")
        val paid = amount ?: stored?.amount_minor?.let { Money.ofMinor(it, bill.amount.currency) } ?: expectedAmount(bill, books.ledger(group).ledgerQueries.occurrencesForBill(billId).executeAsList())
        validate(paid.currency == bill.amount.currency && paid.isPositive, "error.billAmountPositive")
        val txnId = existingTransactionId ?: when (bill.kind) {
            BillKind.TRANSFER -> books.transactions.transfer(
                TransferDraft(bill.accountId, bill.transferAccountId!!, paidDate, paid, memo = bill.name),
            ).first.id
            else -> {
                val signed = if (bill.kind == BillKind.INCOME) paid else -paid
                books.transactions.create(
                    TransactionDraft(
                        bill.accountId, paidDate, signed, bill.payeeName ?: bill.name,
                        listOfNotNull(bill.categoryId?.let { SplitDraft(it, signed) }), memo = bill.name,
                    ),
                ).id
            }
        }
        upsert(group, billId, dueDate, paid, OccurrenceStatus.PAID, txnId, paidDate.toString())
        books.session.audit("PAID", "bill", billId, dueDate.toString())
        return occurrencesOf(bill, books.ledger(group).ledgerQueries.occurrencesForBill(billId).executeAsList(), dueDate, dueDate).single()
    }

    /** Reverses "paid"; optionally deletes the transaction that was created for it. */
    fun unmarkPaid(billId: String, dueDate: LocalDate, deleteTransaction: Boolean) {
        val (group, _) = locate(billId)
        books.require(group, PermissionLevel.EDIT)
        val stored = storedOccurrence(group, billId, dueDate) ?: return
        if (deleteTransaction) stored.txn_id?.let { books.transactions.delete(it) }
        upsert(group, billId, dueDate, stored.amount_minor?.let { Money.ofMinor(it, books.bills.get(billId).amount.currency) }, OccurrenceStatus.DUE, null, null)
    }

    fun skip(billId: String, dueDate: LocalDate) {
        val (group, _) = locate(billId)
        books.require(group, PermissionLevel.EDIT)
        upsert(group, billId, dueDate, null, OccurrenceStatus.SKIPPED, null, null)
    }

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
        val paid = books.ledger(group).ledgerQueries.occurrencesForBill(billId).executeAsList()
            .filter { it.status == OccurrenceStatus.PAID.name && it.amount_minor != null }
            .map { Occurrence(bill, LocalDate.parse(it.due_date), Money.ofMinor(it.amount_minor!!, bill.amount.currency), true, OccurrenceStatus.PAID, it.txn_id, it.paid_date?.let(LocalDate::parse)) }
        return paid.mapIndexed { i, o ->
            val previous = paid.subList(maxOf(0, i - 12), i)
            val average = if (previous.isEmpty()) null else Money.ofMinor(previous.sumOf { it.amount.minorUnits } / previous.size, bill.amount.currency)
            val lastYear = paid.firstOrNull { it.dueDate.year == o.dueDate.year - 1 && it.dueDate.month == o.dueDate.month }?.amount
            // Unusual: a quarter above the average of the previous bills (at least three of them).
            val unusual = previous.size >= 3 && average != null && o.amount > average.times(BigDecimal("1.25"))
            BillHistoryEntry(o, average, lastYear, unusual)
        }.reversed()
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
    fun forecast(today: LocalDate, days: Int = 30): List<AccountForecast> {
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
                val change = if (incoming) o.amount else o.signedAmount
                balance += change
                val point = ForecastPoint(date, balance, o)
                points += point
                if (change.isNegative && balance.isNegative && account.type.kind == AccountKind.BANK) shortfalls += point
            }
            AccountForecast(account, summary.balance, points, points.minOf { it.balance }, shortfalls)
        }
    }

    // --- Calendar export ------------------------------------------------------------------------

    /** BILL-12: the bill calendar as an iCalendar file for the user's own calendar. */
    fun iCalendar(from: LocalDate, to: LocalDate, describe: (Occurrence) -> String): String = buildString {
        append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Schippers//Household Finance Manager//EN\r\nCALSCALE:GREGORIAN\r\n")
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

    private fun upsert(group: GroupInfo, billId: String, dueDate: LocalDate, amount: Money?, status: OccurrenceStatus, txnId: String?, paidDate: String?, documentId: String? = null) {
        val existing = storedOccurrence(group, billId, dueDate)
        books.ledger(group).ledgerQueries.upsertOccurrence(
            existing?.id ?: Ids.newId(), billId, dueDate.toString(), amount?.minorUnits, status.name, txnId, paidDate, documentId ?: existing?.document_id,
        )
    }

    private fun BillRow.toBill(currency: Currency) = Bill(
        id, BillKind.valueOf(kind), name, payee_name, payee_account_number, Money.ofMinor(amount_minor, currency), AmountKind.valueOf(amount_kind),
        account_id, transfer_account_id, PaymentMethod.valueOf(payment_method), category_id, Recurrence.decode(recurrence),
        LocalDate.parse(start_date), end_date?.let(LocalDate::parse), reminder_days.split(',').mapNotNull { it.trim().toIntOrNull() },
        is_subscription == 1L, renewal_date?.let(LocalDate::parse), cancel_by?.let(LocalDate::parse), notes, active == 1L,
    )
}
