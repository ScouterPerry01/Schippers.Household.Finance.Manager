package ca.schippers.hfm.books

import ca.schippers.hfm.calc.loan.Amortization
import ca.schippers.hfm.calc.loan.Comparison
import ca.schippers.hfm.calc.loan.Compounding
import ca.schippers.hfm.calc.loan.LoanChange
import ca.schippers.hfm.calc.loan.LoanNeverRepaysException
import ca.schippers.hfm.calc.loan.LoanPlan
import ca.schippers.hfm.calc.loan.LoanProjection
import ca.schippers.hfm.calc.loan.LoanTerms
import ca.schippers.hfm.calc.loan.PaymentFrequency
import ca.schippers.hfm.calc.loan.Projection
import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.domain.AccountStatus
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import java.math.BigDecimal
import ca.schippers.hfm.data.ledger.Loan as LoanRow

enum class RateType { FIXED, VARIABLE }

/**
 * LN-01 loan terms, as agreed or as they stood at the start of the current term (for a renewed
 * mortgage, the balance and remaining amortization at the renewal). Rates are annual fractions.
 * [payment] is the lender's payment when known; [propertyTax] and [insurance] are collected with
 * each payment (LN-05).
 */
data class LoanDetails(
    val accountId: String,
    val principal: Money,
    val annualRate: BigDecimal,
    val rateType: RateType = RateType.FIXED,
    val compounding: Compounding = Compounding.SEMI_ANNUAL,
    val amortizationMonths: Int,
    val frequency: PaymentFrequency = PaymentFrequency.MONTHLY,
    val firstPaymentDate: LocalDate,
    val payment: Money? = null,
    val extraPerPayment: Money? = null,
    val termEnd: LocalDate? = null,
    val renewalRemindDays: Int = 120,
    val propertyTax: Money? = null,
    val insurance: Money? = null,
    val paymentAccountId: String? = null,
    val lastPaidDate: LocalDate? = null,
    val notes: String? = null,
) {
    val terms: LoanTerms get() = LoanTerms(principal, annualRate, compounding, amortizationMonths, frequency)
}

enum class LoanChangeKind { PREPAYMENT, RATE_CHANGE, PAYMENT_CHANGE }

data class LoanChangeRecord(
    val id: String,
    val date: LocalDate,
    val kind: LoanChangeKind,
    val amount: Money?,
    val annualRate: BigDecimal?,
    val recalculate: Boolean,
    val transactionId: String?,
    val notes: String?,
)

/** Where a loan stands today, against its schedule. Balances are amounts owed, as positive numbers. */
data class LoanStatus(
    val account: Account,
    val details: LoanDetails,
    val owed: Money,
    val scheduledBalance: Money,
    val annualRate: BigDecimal,
    /** The regular payment in force, without property tax and insurance. */
    val payment: Money,
    /** What leaves the bank account each time: payment, extra principal, property tax and insurance. */
    val totalPayment: Money,
    val nextPayment: LocalDate?,
    val payoffDate: LocalDate?,
    val interestRemaining: Money,
    /** LN-03: what the prepayments and extra payments save, against the loan without them. */
    val interestSaved: Money,
    val monthsSooner: Int,
    val termEnd: LocalDate?,
    val daysToRenewal: Int?,
)

/** One line of the debt summary report: any liability, with what is known about it. */
data class DebtLine(
    val account: Account,
    val owed: Money,
    val annualRate: BigDecimal?,
    val payment: Money?,
    val payoffDate: LocalDate?,
    val interestRemaining: Money?,
    val termEnd: LocalDate?,
)

/** LN-01 to LN-06: loan and mortgage terms, schedules, payments, prepayments, renewals and what-ifs. */
class LoanService internal constructor(private val books: Books) {

    /** Every open loan and mortgage account the user can see, with its terms when they are set. */
    fun accounts(): List<Pair<AccountSummary, LoanDetails?>> =
        books.accounts.list().filter { it.account.type.kind == AccountKind.LOAN }.map { it to details(it.account.id) }

    fun details(accountId: String): LoanDetails? {
        val (group, account) = books.accounts.locate(accountId)
        return books.ledger(group).loansQueries.loan(accountId).executeAsOneOrNull()?.toDetails(account.currency)
    }

    fun save(details: LoanDetails) {
        val (group, account) = books.accounts.locate(details.accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(account.type.kind == AccountKind.LOAN, "error.notLoanAccount")
        listOfNotNull(details.principal, details.payment, details.extraPerPayment, details.propertyTax, details.insurance)
            .forEach { validate(it.currency == account.currency, "error.currencyMismatch", account.currency.code) }
        validate(details.principal.isPositive, "error.loanPrincipal")
        validate(details.annualRate.signum() >= 0 && details.annualRate < BigDecimal.ONE, "error.rateRange")
        validate(details.amortizationMonths in 1..(50 * 12), "error.loanAmortization")
        validate(details.renewalRemindDays in 0..365, "error.reminderDays")
        listOfNotNull(details.payment, details.extraPerPayment, details.propertyTax, details.insurance)
            .forEach { validate(!it.isNegative, "error.invalidNumber") }
        validate(details.paymentAccountId != details.accountId, "error.transferSameAccount")
        books.ledger(group).loansQueries.upsertLoan(
            details.accountId, details.principal.minorUnits, details.annualRate.toPlainString(), details.rateType.name,
            details.compounding.name, details.amortizationMonths.toLong(), details.frequency.name, details.firstPaymentDate.toString(),
            details.payment?.minorUnits, details.extraPerPayment?.minorUnits, details.termEnd?.toString(), details.renewalRemindDays.toLong(),
            details.propertyTax?.minorUnits, details.insurance?.minorUnits, details.paymentAccountId, details.lastPaidDate?.toString(),
            details.notes.blankToNull(),
        )
        // The terms must still give a loan that repays.
        runCatching { projection(details.accountId) }.onFailure {
            if (it is LoanNeverRepaysException) {
                books.ledger(group).loansQueries.deleteLoan(details.accountId)
                throw ValidationException("error.loanNeverRepays")
            }
        }
        books.session.audit("UPDATE", "loan", details.accountId)
    }

    fun changes(accountId: String): List<LoanChangeRecord> {
        val (group, account) = books.accounts.locate(accountId)
        return books.ledger(group).loansQueries.loanChanges(accountId).executeAsList().map {
            LoanChangeRecord(
                it.id, LocalDate.parse(it.date), LoanChangeKind.valueOf(it.kind), it.amount_minor?.let { m -> Money.ofMinor(m, account.currency) },
                it.annual_rate?.let(::BigDecimal), it.recalculate == 1L, it.txn_id, it.notes,
            )
        }
    }

    /** The loan as planned: its terms with every change recorded so far. */
    fun plan(accountId: String): LoanPlan {
        val details = requireDetails(accountId)
        return LoanPlan(details.terms, details.firstPaymentDate, details.payment, details.extraPerPayment, changes(accountId).map { it.toChange() })
    }

    /** LN-02: the full dated schedule, splitting each payment into interest and principal. */
    fun projection(accountId: String): Projection = LoanProjection.project(plan(accountId))

    fun status(accountId: String, today: LocalDate): LoanStatus {
        val (group, account) = books.accounts.locate(accountId)
        val details = requireDetails(accountId)
        val plan = plan(accountId)
        val projection = LoanProjection.project(plan)
        val owed = owed(group, account)
        val next = projection.nextAfter(details.lastPaidDate ?: today.minusDays(1))
        val inForce = next ?: projection.rows.lastOrNull()
        val payment = inForce?.regularPayment ?: plan.payment ?: Amortization.payment(details.terms)
        // What is left is projected from the actual balance, so payments missed or made early show.
        val ahead = if (owed.isPositive) LoanProjection.whatIf(plan, details.lastPaidDate ?: today, owed).base else null
        val saved = LoanProjection.interestSaved(plan)
        val zero = Money.zero(account.currency)
        return LoanStatus(
            account, details, owed, projection.balanceOn(today), inForce?.annualRate ?: details.annualRate, payment,
            payment + (details.extraPerPayment ?: zero) + (details.propertyTax ?: zero) + (details.insurance ?: zero),
            next?.date, ahead?.payoffDate ?: projection.payoffDate, ahead?.totalInterest ?: zero,
            saved.interestSaved, saved.monthsSooner, details.termEnd, details.termEnd?.let { today.daysUntil(it) },
        )
    }

    /** LN-06: the loan from today's balance compared with the changes given. */
    fun whatIf(
        accountId: String,
        today: LocalDate,
        extraPerPayment: Money? = null,
        lumpSum: Money? = null,
        annualRate: BigDecimal? = null,
        remainingAmortizationMonths: Int? = null,
    ): Comparison {
        val (group, account) = books.accounts.locate(accountId)
        val owed = owed(group, account)
        validate(owed.isPositive, "error.loanPaidOff")
        validate(annualRate == null || (annualRate.signum() >= 0 && annualRate < BigDecimal.ONE), "error.rateRange")
        validate(remainingAmortizationMonths == null || remainingAmortizationMonths in 1..(50 * 12), "error.loanAmortization")
        return try {
            LoanProjection.whatIf(plan(accountId), requireDetails(accountId).lastPaidDate ?: today, owed, extraPerPayment, lumpSum, annualRate, remainingAmortizationMonths)
        } catch (_: LoanNeverRepaysException) {
            throw ValidationException("error.loanNeverRepays")
        }
    }

    /** What one payment on [date] would be, from the actual balance owed. */
    data class PaymentSplit(val date: LocalDate, val principal: Money, val interest: Money, val propertyTax: Money, val insurance: Money) {
        val total: Money get() = principal + interest + propertyTax + insurance
    }

    /** The next payment due after the last one recorded, split from the balance actually owed. */
    fun nextPayment(accountId: String, today: LocalDate): PaymentSplit? {
        val (group, account) = books.accounts.locate(accountId)
        val details = requireDetails(accountId)
        val projection = projection(accountId)
        val row = projection.nextAfter(details.lastPaidDate ?: details.firstPaymentDate.minusDays(1))
            ?: projection.nextAfter(today) ?: return null
        val owed = owed(group, account)
        if (!owed.isPositive) return null
        val periodic = Amortization.periodicRate(row.annualRate, details.compounding, details.frequency.paymentsPerYear)
        val interest = owed.times(periodic)
        val zero = Money.zero(account.currency)
        val regular = row.regularPayment + (details.extraPerPayment ?: zero)
        val principal = if (regular - interest > owed) owed else regular - interest
        return PaymentSplit(row.date, principal, interest, details.propertyTax ?: zero, details.insurance ?: zero)
    }

    /**
     * Records the next payment. The whole amount moves from the paying account to the loan, as one
     * line that matches the bank statement; the interest, property tax and insurance are then
     * charged to the loan under their categories, so only the principal reduces what is owed.
     */
    fun recordPayment(accountId: String, fromAccountId: String, split: PaymentSplit): Transaction {
        val (group, account) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(split.principal.currency == account.currency, "error.currencyMismatch", account.currency.code)
        val (_, from) = books.accounts.locate(fromAccountId)
        validate(from.currency == account.currency, "error.loanPaymentCurrency", account.currency.code)
        val lender = account.institutionId?.let { id -> books.institutions.list().firstOrNull { it.id == id }?.name } ?: account.name
        val (_, received) = books.transactions.transfer(TransferDraft(fromAccountId, accountId, split.date, split.total))
        val charges = listOfNotNull(
            split.interest.takeIf { it.isPositive }?.let { SplitDraft(category(interestKey(account.type)), -it) },
            split.propertyTax.takeIf { it.isPositive }?.let { SplitDraft(category("housing.municipal_tax"), -it) },
            split.insurance.takeIf { it.isPositive }?.let { SplitDraft(category(insuranceKey(account.type)), -it) },
        )
        val charge = if (charges.isEmpty()) {
            null
        } else {
            books.transactions.create(
                TransactionDraft(accountId, split.date, charges.fold(Money.zero(account.currency)) { a, s -> a + s.amount }, lender, charges),
            )
        }
        books.ledger(group).loansQueries.setLastPaid(split.date.toString(), accountId)
        return charge ?: received
    }

    /**
     * LN-03: a lump sum paid off the principal. When [fromAccountId] is given, the money is moved
     * from that account too.
     */
    fun addPrepayment(accountId: String, date: LocalDate, amount: Money, fromAccountId: String? = null, notes: String? = null): LoanChangeRecord {
        val (group, account) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(amount.currency == account.currency && amount.isPositive, "error.invalidNumber")
        val txnId = fromAccountId?.let { books.transactions.transfer(TransferDraft(it, accountId, date, amount, memo = notes)).second.id }
        return insertChange(group, accountId, date, LoanChangeKind.PREPAYMENT, amount, null, false, txnId, notes)
    }

    /** LN-03: a new rate from [date]. A fixed-rate loan recalculates its payment; a variable one usually keeps it. */
    fun changeRate(accountId: String, date: LocalDate, annualRate: BigDecimal, recalculatePayment: Boolean, notes: String? = null): LoanChangeRecord {
        val (group, _) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(annualRate.signum() >= 0 && annualRate < BigDecimal.ONE, "error.rateRange")
        return checked(group, accountId) { insertChange(group, accountId, date, LoanChangeKind.RATE_CHANGE, null, annualRate, recalculatePayment, null, notes) }
    }

    fun changePayment(accountId: String, date: LocalDate, payment: Money, notes: String? = null): LoanChangeRecord {
        val (group, account) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(payment.currency == account.currency && payment.isPositive, "error.invalidNumber")
        return checked(group, accountId) { insertChange(group, accountId, date, LoanChangeKind.PAYMENT_CHANGE, payment, null, false, null, notes) }
    }

    /**
     * LN-04: renews the loan at the end of its term: the new rate applies from [date] with the
     * payment recalculated over the remaining amortization, and the next term ends on [newTermEnd].
     */
    fun renew(accountId: String, date: LocalDate, annualRate: BigDecimal, newTermEnd: LocalDate?, notes: String? = null): LoanChangeRecord {
        validate(newTermEnd == null || newTermEnd > date, "error.endBeforeStart")
        val change = changeRate(accountId, date, annualRate, recalculatePayment = true, notes = notes)
        val (group, _) = books.accounts.locate(accountId)
        books.ledger(group).loansQueries.setTermEnd(newTermEnd?.toString(), accountId)
        return change
    }

    /** Removes a recorded change. The money moved for a prepayment stays; delete it from the register if needed. */
    fun deleteChange(accountId: String, changeId: String) {
        val (group, _) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).loansQueries.deleteLoanChange(changeId)
    }

    /** LN-04: renewals due within each loan's own reminder lead time, or within [withinDays], whichever is longer. */
    fun renewals(today: LocalDate, withinDays: Int = 30): List<Renewal> = accounts().mapNotNull { (summary, details) ->
        val end = details?.termEnd ?: return@mapNotNull null
        if (summary.account.status == AccountStatus.CLOSED) return@mapNotNull null
        val days = today.daysUntil(end)
        if (days > maxOf(withinDays, details.renewalRemindDays)) null
        else Renewal(RenewalKind.LOAN_RENEWAL, summary.account.id, summary.account.name, end, days)
    }

    /** The debt summary report: every liability, largest first. */
    fun debtSummary(today: LocalDate): List<DebtLine> = books.accounts.list()
        .filter { it.account.type.kind.isLiability }
        .map { summary ->
            val account = summary.account
            val owed = -summary.balance
            when (account.type.kind) {
                AccountKind.LOAN -> {
                    val status = details(account.id)?.let { runCatching { status(account.id, today) }.getOrNull() }
                    DebtLine(account, owed, status?.annualRate, status?.payment, status?.payoffDate, status?.interestRemaining, status?.termEnd)
                }
                else -> {
                    val terms = runCatching { books.creditCards.terms(account.id) }.getOrNull()
                    val minimum = terms?.let { CreditCardService.minimumPayment(owed, it) }
                    DebtLine(account, owed, terms?.purchaseRate, minimum?.takeIf { it.isPositive }, null, null, null)
                }
            }
        }
        .sortedByDescending { it.owed.minorUnits }

    // --- Helpers ----------------------------------------------------------------------------------

    private fun requireDetails(accountId: String): LoanDetails =
        details(accountId) ?: throw ValidationException("error.loanNoTerms")

    private fun owed(group: GroupInfo, account: Account): Money =
        -Money.ofMinor(books.ledger(group).ledgerQueries.accountBalance(account.id).executeAsOne(), account.currency)

    private fun category(key: String): String? = books.categories.list().firstOrNull { it.systemKey == key }?.id

    private fun interestKey(type: AccountType) = if (type == AccountType.MORTGAGE) "housing.mortgage_interest" else "financial.interest"

    private fun insuranceKey(type: AccountType) = if (type == AccountType.MORTGAGE) "housing.insurance" else "insurance.life"

    private fun insertChange(
        group: GroupInfo, accountId: String, date: LocalDate, kind: LoanChangeKind, amount: Money?, rate: BigDecimal?,
        recalculate: Boolean, txnId: String?, notes: String?,
    ): LoanChangeRecord {
        val id = Ids.newId()
        books.ledger(group).loansQueries.insertLoanChange(
            id, accountId, date.toString(), kind.name, amount?.minorUnits, rate?.toPlainString(), if (recalculate) 1 else 0, txnId, notes.blankToNull(), books.now(),
        )
        books.session.audit("UPDATE", "loan", accountId)
        return LoanChangeRecord(id, date, kind, amount, rate, recalculate, txnId, notes.blankToNull())
    }

    /** Applies a change and takes it back if the loan would then never be repaid. */
    private fun checked(group: GroupInfo, accountId: String, block: () -> LoanChangeRecord): LoanChangeRecord {
        val change = block()
        try {
            projection(accountId)
        } catch (_: LoanNeverRepaysException) {
            books.ledger(group).loansQueries.deleteLoanChange(change.id)
            throw ValidationException("error.loanNeverRepays")
        }
        return change
    }

    private fun LoanChangeRecord.toChange(): LoanChange = when (kind) {
        LoanChangeKind.PREPAYMENT -> LoanChange.Prepayment(date, amount!!)
        LoanChangeKind.RATE_CHANGE -> LoanChange.RateChange(date, annualRate!!, recalculate)
        LoanChangeKind.PAYMENT_CHANGE -> LoanChange.PaymentChange(date, amount!!)
    }

    private fun LoanRow.toDetails(c: Currency) = LoanDetails(
        account_id, Money.ofMinor(principal_minor, c), BigDecimal(annual_rate), RateType.valueOf(rate_type), Compounding.valueOf(compounding),
        amortization_months.toInt(), PaymentFrequency.valueOf(frequency), LocalDate.parse(first_payment_date),
        payment_minor?.let { Money.ofMinor(it, c) }, extra_payment_minor?.let { Money.ofMinor(it, c) }, term_end?.let(LocalDate::parse),
        renewal_remind_days.toInt(), property_tax_minor?.let { Money.ofMinor(it, c) }, insurance_minor?.let { Money.ofMinor(it, c) },
        payment_account_id, last_paid_date?.let(LocalDate::parse), notes,
    )
}

private fun LocalDate.minusDays(days: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() - days)
