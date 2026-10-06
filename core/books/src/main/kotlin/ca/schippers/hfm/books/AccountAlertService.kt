package ca.schippers.hfm.books

import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * ACC-06: one account's alerts. Each is off while null (or false): [lowBalance] for a bank or cash
 * account; [nearLimitPercent] and [overLimit] for a card with a limit; [largeMultiple] (a
 * transaction more than so many times the account's usual amount) and [newPayeeAbove] (a first
 * transaction with a payee for at least this much) for unusual activity.
 */
data class AlertSettings(
    val accountId: String,
    val lowBalance: Money? = null,
    val nearLimitPercent: Int? = null,
    val overLimit: Boolean = false,
    val largeMultiple: BigDecimal? = null,
    val newPayeeAbove: Money? = null,
) {
    val anyOn: Boolean get() = lowBalance != null || nearLimitPercent != null || overLimit || largeMultiple != null || newPayeeAbove != null
}

enum class AccountAlertKind { LOW_BALANCE, OVER_LIMIT, NEAR_LIMIT, LARGE_TRANSACTION, NEW_PAYEE }

/**
 * ACC-06: an alert raised now. [amount] is the balance (low balance), what is owed (card limit) or
 * the transaction's amount; [threshold] the limit or amount it is compared with; [usual] the
 * account's usual transaction amount (large transaction); [transactionId], [date] and [payee] name
 * the transaction for unusual activity.
 */
data class AccountAlert(
    val kind: AccountAlertKind,
    val account: Account,
    val amount: Money,
    val threshold: Money? = null,
    val percent: Int? = null,
    val usual: Money? = null,
    val transactionId: String? = null,
    val date: LocalDate? = null,
    val payee: String? = null,
) {
    /** Unusual activity can be dismissed; a balance alert lasts while the balance stays so. */
    val dismissible: Boolean get() = transactionId != null

    /** Stable while the alert stands, for reminders shown once. */
    val key: String get() = "alert:$kind:${account.id}:${transactionId.orEmpty()}"
}

class AccountAlertService internal constructor(private val books: Books) {

    fun settings(accountId: String): AlertSettings {
        val (group, account) = books.accounts.locate(accountId)
        return books.ledger(group).accountAlertsQueries.alertFor(accountId).executeAsOneOrNull()?.toSettings(account) ?: AlertSettings(accountId)
    }

    fun save(s: AlertSettings): AlertSettings {
        val (group, account) = books.accounts.locate(s.accountId)
        books.require(group, PermissionLevel.EDIT)
        val kind = account.type.kind
        validate(s.lowBalance == null || kind == AccountKind.BANK, "error.alertKind")
        validate((s.nearLimitPercent == null && !s.overLimit) || kind == AccountKind.CREDIT, "error.alertKind")
        validate(s.nearLimitPercent == null || s.nearLimitPercent in 1..100, "error.percentRange")
        validate(s.largeMultiple == null || s.largeMultiple > BigDecimal.ONE, "error.alertMultiple")
        listOfNotNull(s.lowBalance, s.newPayeeAbove).forEach { validate(it.currency == account.currency, "error.currencyMismatch", account.currency.code) }
        validate(s.newPayeeAbove == null || s.newPayeeAbove.isPositive, "error.amountPositive")
        val q = books.ledger(group).accountAlertsQueries
        if (!s.anyOn) {
            q.deleteAlert(s.accountId)
        } else {
            q.upsertAlert(
                s.accountId, s.lowBalance?.minorUnits, s.nearLimitPercent?.toLong(), if (s.overLimit) 1 else 0,
                s.largeMultiple?.stripTrailingZeros()?.toPlainString(), s.newPayeeAbove?.minorUnits, books.now(),
            )
        }
        books.session.audit("UPDATE", "account_alert", s.accountId)
        return settings(s.accountId)
    }

    /**
     * ACC-06: every alert standing on [today] on the accounts the user can see, balances first.
     * Unusual activity looks at the last [RECENT_DAYS] days: a transaction is large when it is more
     * than the chosen multiple of the median amount over the year before (at least [MIN_HISTORY]
     * transactions are needed to know what is usual); a payee is new when this account never had a
     * transaction with it before. Transfers and investment trade lines are left out, and dismissed
     * transactions are not raised again.
     */
    fun alerts(today: LocalDate): List<AccountAlert> {
        val summaries = books.accounts.list().associateBy { it.account.id }
        return books.groups().flatMap { group ->
            val q = books.ledger(group).accountAlertsQueries
            q.alerts().executeAsList().mapNotNull { row -> summaries[row.account_id]?.let { it to row.toSettings(it.account) } }
                .flatMap { (summary, s) -> balanceAlerts(summary, s) + unusual(q, summary.account, s, today) }
        }.sortedWith(compareBy({ it.kind.ordinal }, { it.account.name }, { it.date }))
    }

    /** The alerts of one account, for its register. */
    fun alertsFor(accountId: String, today: LocalDate): List<AccountAlert> = alerts(today).filter { it.account.id == accountId }

    /** ACC-06: an unusual transaction was looked at; its alert is not raised again. */
    fun dismiss(transactionId: String) {
        val txn = books.transactions.get(transactionId)
        val (group, _) = books.accounts.locate(txn.accountId)
        books.ledger(group).accountAlertsQueries.dismiss(transactionId, books.now())
    }

    private fun balanceAlerts(summary: AccountSummary, s: AlertSettings): List<AccountAlert> {
        val account = summary.account
        val balance = summary.balanceToday
        val out = ArrayList<AccountAlert>()
        s.lowBalance?.let { threshold -> if (balance < threshold) out += AccountAlert(AccountAlertKind.LOW_BALANCE, account, balance, threshold) }
        if (account.type.kind == AccountKind.CREDIT && (s.overLimit || s.nearLimitPercent != null)) {
            val limit = books.creditCards.terms(account.id)?.creditLimit?.takeIf { it.isPositive && it.currency == account.currency }
            if (limit != null) {
                val owed = -balance
                val percent = CreditCardService.limitUsedPercent(owed, limit) ?: 0
                when {
                    owed > limit && s.overLimit -> out += AccountAlert(AccountAlertKind.OVER_LIMIT, account, owed, limit, percent)
                    owed <= limit && s.nearLimitPercent != null && percent >= s.nearLimitPercent ->
                        out += AccountAlert(AccountAlertKind.NEAR_LIMIT, account, owed, limit, percent)
                }
            }
        }
        return out
    }

    private fun unusual(q: ca.schippers.hfm.data.ledger.AccountAlertsQueries, account: Account, s: AlertSettings, today: LocalDate): List<AccountAlert> {
        if (s.largeMultiple == null && s.newPayeeAbove == null) return emptyList()
        val from = today.minus(DatePeriod(days = RECENT_DAYS - 1))
        val dismissed = q.dismissedIn(account.id, from.toString()).executeAsList().toSet()
        val recent = q.recentTransactions(account.id, from.toString(), today.toString()).executeAsList()
        if (recent.isEmpty()) return emptyList()
        val payees = books.payees.list(true).associate { it.id to it.name }
        val out = ArrayList<AccountAlert>()
        val usual = s.largeMultiple?.let { median(q.amountsBetween(account.id, from.minus(DatePeriod(days = 365)).toString(), from.toString()).executeAsList()) }
        val seen = q.payeesBefore(account.id, from.toString()).executeAsList().mapNotNull { it.payee_id ?: it.payee_key }.toMutableSet()
        for (t in recent) {
            val amount = Money.ofMinor(t.amount_minor, account.currency)
            val name = t.payee_id?.let(payees::get) ?: t.payee_text
            val key = t.payee_id ?: t.payee_text?.lowercase()
            val date = LocalDate.parse(t.date)
            if (t.id !in dismissed) {
                if (usual != null && usual > 0 && BigDecimal(kotlin.math.abs(t.amount_minor)) > s.largeMultiple.multiply(BigDecimal(usual))) {
                    out += AccountAlert(AccountAlertKind.LARGE_TRANSACTION, account, amount, usual = Money.ofMinor(usual, account.currency), transactionId = t.id, date = date, payee = name)
                } else if (s.newPayeeAbove != null && key != null && key !in seen && amount.abs() >= s.newPayeeAbove) {
                    out += AccountAlert(AccountAlertKind.NEW_PAYEE, account, amount, s.newPayeeAbove, transactionId = t.id, date = date, payee = name)
                }
            }
            key?.let { seen += it }
        }
        return out
    }

    /** The median size of [amounts] in minor units; null with fewer than [MIN_HISTORY] of them. */
    private fun median(amounts: List<Long>): Long? {
        if (amounts.size < MIN_HISTORY) return null
        val sorted = amounts.map { kotlin.math.abs(it) }.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else BigDecimal(sorted[mid - 1] + sorted[mid]).divide(BigDecimal(2), 0, RoundingMode.HALF_UP).toLong()
    }

    private fun ca.schippers.hfm.data.ledger.Account_alert.toSettings(account: Account) = AlertSettings(
        account_id, low_balance_minor?.let { Money.ofMinor(it, account.currency) }, near_limit_percent?.toInt(), over_limit == 1L,
        large_multiple?.let(::BigDecimal), new_payee_minor?.let { Money.ofMinor(it, account.currency) },
    )

    companion object {
        /** How far back unusual activity is looked for, in days. */
        const val RECENT_DAYS = 7

        /** Transactions needed in the year before to know an account's usual amount. */
        const val MIN_HISTORY = 10
    }
}
