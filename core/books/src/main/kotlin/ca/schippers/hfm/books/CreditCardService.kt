package ca.schippers.hfm.books

import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/** CC-01 card terms. Rates are annual fractions (0.1999 = 19.99%). */
data class CreditCardTerms(
    val creditLimit: Money? = null,
    val purchaseRate: BigDecimal? = null,
    val cashAdvanceRate: BigDecimal? = null,
    val promoRate: BigDecimal? = null,
    val promoEnds: LocalDate? = null,
    val statementDay: Int? = null,
    val dueDay: Int? = null,
    /** Minimum payment: the greater of this fraction of the balance and [minPaymentFloor]. */
    val minPaymentPercent: BigDecimal? = null,
    val minPaymentFloor: Money? = null,
    val annualFee: Money? = null,
    val primaryAccountId: String? = null,
)

/** CC-02 one statement cycle. [balance] is the amount owed, as a positive number. */
data class CardStatement(
    val id: String,
    val statementDate: LocalDate,
    val balance: Money,
    val minimumDue: Money,
    val dueDate: LocalDate,
    val paid: Boolean,
)

class CreditCardService internal constructor(private val books: Books) {

    fun terms(accountId: String): CreditCardTerms? {
        val (group, account) = books.accounts.locate(accountId)
        val row = books.ledger(group).ledgerQueries.creditCard(accountId).executeAsOneOrNull() ?: return null
        val c = account.currency
        return CreditCardTerms(
            row.credit_limit_minor?.let { Money.ofMinor(it, c) },
            row.purchase_rate?.let(::BigDecimal),
            row.cash_advance_rate?.let(::BigDecimal),
            row.promo_rate?.let(::BigDecimal),
            row.promo_ends?.let(LocalDate::parse),
            row.statement_day?.toInt(),
            row.due_day?.toInt(),
            row.min_payment_percent?.let(::BigDecimal),
            row.min_payment_floor_minor?.let { Money.ofMinor(it, c) },
            row.annual_fee_minor?.let { Money.ofMinor(it, c) },
            row.primary_account_id,
        )
    }

    fun saveTerms(accountId: String, terms: CreditCardTerms) {
        val (group, account) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(account.type.kind == AccountKind.CREDIT, "error.notCreditAccount")
        listOfNotNull(terms.statementDay, terms.dueDay).forEach { validate(it in 1..31, "error.dayOfMonth") }
        listOfNotNull(terms.purchaseRate, terms.cashAdvanceRate, terms.promoRate, terms.minPaymentPercent)
            .forEach { validate(it.signum() >= 0 && it < BigDecimal.ONE, "error.rateRange") }
        books.ledger(group).ledgerQueries.upsertCreditCard(
            accountId, terms.creditLimit?.minorUnits, terms.purchaseRate?.toPlainString(), terms.cashAdvanceRate?.toPlainString(),
            terms.promoRate?.toPlainString(), terms.promoEnds?.toString(), terms.statementDay?.toLong(), terms.dueDay?.toLong(),
            terms.minPaymentPercent?.toPlainString(), terms.minPaymentFloor?.minorUnits, terms.annualFee?.minorUnits, terms.primaryAccountId,
        )
        books.session.audit("UPDATE", "credit_card", accountId)
    }

    fun statements(accountId: String): List<CardStatement> {
        val (group, account) = books.accounts.locate(accountId)
        return books.ledger(group).ledgerQueries.cardStatements(accountId).executeAsList().map {
            CardStatement(
                it.id, LocalDate.parse(it.statement_date), Money.ofMinor(it.balance_minor, account.currency),
                Money.ofMinor(it.minimum_due_minor, account.currency), LocalDate.parse(it.due_date), it.paid == 1L,
            )
        }
    }

    /** Records a statement; the minimum due defaults to the card's rule when not given. */
    fun recordStatement(accountId: String, statementDate: LocalDate, balance: Money, dueDate: LocalDate, minimumDue: Money? = null): CardStatement {
        val (group, account) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(balance.currency == account.currency, "error.currencyMismatch", account.currency.code)
        validate(dueDate >= statementDate, "error.dueBeforeStatement")
        val minimum = minimumDue ?: terms(accountId)?.let { minimumPayment(balance, it) } ?: Money.zero(account.currency)
        val existing = statements(accountId).firstOrNull { it.statementDate == statementDate }
        val statement = CardStatement(existing?.id ?: Ids.newId(), statementDate, balance, minimum, dueDate, existing?.paid ?: false)
        save(group, accountId, statement)
        return statement
    }

    fun markPaid(accountId: String, statementId: String, paid: Boolean = true) {
        val (group, _) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        val statement = statements(accountId).first { it.id == statementId }
        save(group, accountId, statement.copy(paid = paid))
    }

    private fun save(group: GroupInfo, accountId: String, s: CardStatement) {
        books.ledger(group).ledgerQueries.upsertCardStatement(
            s.id, accountId, s.statementDate.toString(), s.balance.minorUnits, s.minimumDue.minorUnits, s.dueDate.toString(), if (s.paid) 1 else 0,
        )
    }

    companion object {
        /** The greater of the percentage and the floor, but never more than the balance owed. */
        fun minimumPayment(balance: Money, terms: CreditCardTerms): Money {
            if (!balance.isPositive) return Money.zero(balance.currency)
            val byPercent = terms.minPaymentPercent?.let { balance.times(it) } ?: Money.zero(balance.currency)
            val floor = terms.minPaymentFloor ?: Money.zero(balance.currency)
            val minimum = if (byPercent > floor) byPercent else floor
            return if (minimum > balance) balance else minimum
        }
    }
}
