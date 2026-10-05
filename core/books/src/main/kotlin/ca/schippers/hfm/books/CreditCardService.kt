package ca.schippers.hfm.books

import ca.schippers.hfm.data.ledger.CardsQueries
import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import java.math.BigDecimal
import java.math.RoundingMode

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
    /** CC-04: a date the annual fee is charged; it comes back every year. */
    val annualFeeDate: LocalDate? = null,
) {
    /** The next annual fee date on or after [today]. */
    fun nextAnnualFee(today: LocalDate): LocalDate? = annualFeeDate?.let { d ->
        var next = d
        while (next < today) next = next.plus(DatePeriod(years = 1))
        next
    }

    /** CC-01: what can still be spent when [owed] is owed (negative once over the limit); null without a limit. */
    fun availableCredit(owed: Money): Money? = creditLimit?.let { it - owed }

    /** CC-01: the share of the limit in use when [owed] is owed, in whole percent; null without a limit. */
    fun limitUsedPercent(owed: Money): Int? = creditLimit?.let { CreditCardService.limitUsedPercent(owed, it) }

    /**
     * CC-01: the payment due dates from [from] to [to], from the due day; in a shorter month the
     * payment is due on its last day.
     */
    fun dueDates(from: LocalDate, to: LocalDate): List<LocalDate> {
        val day = dueDay ?: return emptyList()
        val result = ArrayList<LocalDate>()
        var month = LocalDate(from.year, from.month, 1)
        while (month <= to) {
            val last = month.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).day
            val due = LocalDate(month.year, month.month, minOf(day, last))
            if (due in from..to) result += due
            month = month.plus(DatePeriod(months = 1))
        }
        return result
    }
}

/** CC-05: a person holding a card on the account: the main cardholder or a supplementary card. */
data class CardHolder(
    val id: String,
    val accountId: String,
    val name: String,
    val memberId: String? = null,
    val lastDigits: String? = null,
    val isPrimary: Boolean = false,
    val archived: Boolean = false,
)

/** CC-04: the card's insurance and protections. */
enum class BenefitKind { PURCHASE_PROTECTION, EXTENDED_WARRANTY, PRICE_PROTECTION, TRAVEL_MEDICAL, TRIP_CANCELLATION, RENTAL_CAR, MOBILE_DEVICE, OTHER }

/**
 * One benefit. [days]: how long a purchase stays protected (purchase or price protection, mobile
 * device) or the length of trip covered; [months]: what an extended warranty adds to the
 * manufacturer's; [maxYears]: the longest manufacturer warranty it extends; [limit]: the most paid
 * per claim.
 */
data class CardBenefit(
    val id: String,
    val accountId: String,
    val kind: BenefitKind,
    val description: String? = null,
    val days: Int? = null,
    val months: Int? = null,
    val maxYears: Int? = null,
    val limit: Money? = null,
    val notes: String? = null,
)

/** CC-04: a benefit that covers a purchase, and until when ([until] null when it depends on the manufacturer's warranty). */
data class Coverage(val benefit: CardBenefit, val until: LocalDate?)

/** CC-05: what one card spent in a period (purchases less refunds), as a positive amount. */
data class HolderSpending(val holder: CardHolder?, val spent: Money)

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
            row.annual_fee_date?.let(LocalDate::parse),
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
            terms.minPaymentPercent?.toPlainString(), terms.minPaymentFloor?.minorUnits, terms.annualFee?.minorUnits, terms.annualFeeDate?.toString(),
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

    // --- Cardholders and supplementary cards (CC-05) ---------------------------------------------

    fun holders(accountId: String, includeArchived: Boolean = false): List<CardHolder> {
        val (group, _) = books.accounts.locate(accountId)
        return books.ledger(group).cardsQueries.cardHolders(accountId).executeAsList().map {
            CardHolder(it.id, it.account_id, it.name, it.member_id, it.last_digits, it.is_primary == 1L, it.archived == 1L)
        }.filter { includeArchived || !it.archived }
    }

    /** Saves a cardholder; making one the main cardholder makes the others supplementary. */
    fun saveHolder(holder: CardHolder): CardHolder {
        val (group, account) = books.accounts.locate(holder.accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(account.type.kind == AccountKind.CREDIT, "error.notCreditAccount")
        validate(holder.name.isNotBlank(), "error.nameRequired")
        validate(holder.lastDigits.isNullOrBlank() || holder.lastDigits.trim().matches(Regex("\\d{4}")), "error.lastDigits")
        val q = books.ledger(group).cardsQueries
        val id = holder.id.ifBlank { Ids.newId() }
        val now = books.now()
        val others = holders(holder.accountId, includeArchived = true).filter { it.id != id }
        val primary = holder.isPrimary || others.none { it.isPrimary }
        books.ledger(group).transaction {
            if (primary) others.filter { it.isPrimary }.forEach { o -> write(q, o.copy(isPrimary = false), now) }
            write(q, holder.copy(id = id, name = holder.name.trim(), lastDigits = holder.lastDigits.blankToNull(), isPrimary = primary), now)
        }
        books.session.audit("UPDATE", "card_holder", id)
        return holders(holder.accountId, includeArchived = true).first { it.id == id }
    }

    private fun write(q: CardsQueries, h: CardHolder, now: Long) {
        val created = q.cardHolderById(h.id).executeAsOneOrNull()?.created_at ?: now
        q.upsertCardHolder(h.id, h.accountId, h.memberId, h.name, h.lastDigits, if (h.isPrimary) 1 else 0, if (h.archived) 1 else 0, created, now)
    }

    /** Removes a cardholder no transaction uses; one that was used is archived instead. */
    fun deleteHolder(accountId: String, holderId: String) {
        val (group, _) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        val q = books.ledger(group).cardsQueries
        if (q.cardHolderUseCount(holderId).executeAsOne() > 0) {
            holders(accountId, includeArchived = true).firstOrNull { it.id == holderId }?.let { write(q, it.copy(archived = true, isPrimary = false), books.now()) }
        } else {
            q.deleteCardHolder(holderId)
        }
    }

    /**
     * Spending per card from [from] to [to]: purchases less refunds, payments left out. Lines with
     * no card count for the main cardholder.
     */
    fun spendingByHolder(accountId: String, from: LocalDate, to: LocalDate): List<HolderSpending> {
        val (group, account) = books.accounts.locate(accountId)
        val holders = holders(accountId, includeArchived = true)
        val primary = holders.firstOrNull { it.isPrimary }
        val totals = books.ledger(group).cardsQueries.spendingByHolder(accountId, from.toString(), to.toString()).executeAsList()
            .groupBy({ r -> holders.firstOrNull { it.id == r.card_holder_id } ?: primary }, { -(it.total ?: 0L) })
        return totals.map { (h, list) -> HolderSpending(h, Money.ofMinor(list.sum(), account.currency)) }.sortedByDescending { it.spent }
    }

    // --- Benefits and the annual fee (CC-04) ------------------------------------------------

    fun benefits(accountId: String): List<CardBenefit> {
        val (group, account) = books.accounts.locate(accountId)
        return books.ledger(group).cardsQueries.cardBenefits(accountId).executeAsList().map {
            CardBenefit(
                it.id, it.account_id, BenefitKind.valueOf(it.kind), it.description, it.days?.toInt(), it.months?.toInt(), it.max_years?.toInt(),
                it.limit_minor?.let { m -> Money.ofMinor(m, account.currency) }, it.notes,
            )
        }
    }

    fun saveBenefit(benefit: CardBenefit): CardBenefit {
        val (group, account) = books.accounts.locate(benefit.accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(account.type.kind == AccountKind.CREDIT, "error.notCreditAccount")
        listOfNotNull(benefit.days, benefit.months, benefit.maxYears).forEach { validate(it > 0, "error.invalidNumber") }
        validate(benefit.limit == null || (benefit.limit.currency == account.currency && benefit.limit.isPositive), "error.currencyMismatch", account.currency.code)
        val q = books.ledger(group).cardsQueries
        val id = benefit.id.ifBlank { Ids.newId() }
        val now = books.now()
        val created = q.cardBenefitById(id).executeAsOneOrNull()?.created_at ?: now
        q.upsertCardBenefit(
            id, benefit.accountId, benefit.kind.name, benefit.description.blankToNull(), benefit.days?.toLong(), benefit.months?.toLong(), benefit.maxYears?.toLong(),
            benefit.limit?.minorUnits, benefit.notes.blankToNull(), created, now,
        )
        books.session.audit("UPDATE", "card_benefit", id)
        return benefit.copy(id = id)
    }

    fun deleteBenefit(accountId: String, benefitId: String) {
        val (group, _) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).cardsQueries.deleteCardBenefit(benefitId)
    }

    /**
     * CC-04: the benefits that cover a purchase made with the card: purchase and price protection
     * for their number of days, the extended warranty for the manufacturer's warranty plus its
     * months. Trip and rental benefits depend on the trip, not the purchase, and are not listed.
     */
    fun coverage(transaction: Transaction, today: LocalDate): List<Coverage> {
        if (!transaction.amount.isNegative || transaction.transfer != null) return emptyList()
        val account = books.accounts.get(transaction.accountId)
        if (account.type.kind != AccountKind.CREDIT) return emptyList()
        return benefits(account.id).mapNotNull { b ->
            when (b.kind) {
                BenefitKind.PURCHASE_PROTECTION, BenefitKind.PRICE_PROTECTION, BenefitKind.MOBILE_DEVICE ->
                    b.days?.let { transaction.date.plus(DatePeriod(days = it)) }?.takeIf { it >= today }?.let { Coverage(b, it) }
                BenefitKind.EXTENDED_WARRANTY -> Coverage(b, null)
                else -> null
            }
        }.sortedWith(compareBy({ it.until == null }, { it.until }))
    }

    /**
     * CC-04: purchases of goods on the card still under purchase or price protection on [today],
     * newest first. Food, fuel, services, bills and fees are left out: the protection covers items.
     */
    fun protectedPurchases(accountId: String, today: LocalDate): List<Pair<Transaction, List<Coverage>>> {
        val longest = benefits(accountId).filter { it.kind in DATED }.mapNotNull { it.days }.maxOrNull() ?: return emptyList()
        val (group, _) = books.accounts.locate(accountId)
        val since = today.minus(DatePeriod(days = longest))
        val categories = books.categories.list(includeArchived = true).associateBy { it.id }
        fun notGoods(categoryId: String?): Boolean {
            var c = categoryId?.let(categories::get) ?: return false
            while (true) {
                if (c.systemKey in NOT_GOODS) return true
                c = c.parentId?.let(categories::get) ?: return false
            }
        }
        return books.ledger(group).cardsQueries.purchasesSince(accountId, since.toString()).executeAsList()
            .map { books.transactions.get(it.id) }
            .filter { t -> t.splits.any { !notGoods(it.categoryId) } }
            .map { t -> t to coverage(t, today).filter { it.until != null } }
            .filter { it.second.isNotEmpty() }
    }

    /**
     * CC-04: annual fees coming up within [withinDays], as reminders; CC-01: payments due within
     * [PAYMENT_LEAD_DAYS] (or [withinDays] if shorter) on cards that are owed something.
     */
    fun renewals(today: LocalDate, withinDays: Int = 30): List<Renewal> = books.accounts.list().map { it.account }
        .filter { it.type.kind == AccountKind.CREDIT }
        .mapNotNull { a ->
            val next = terms(a.id)?.takeIf { it.annualFee?.isPositive == true }?.nextAnnualFee(today) ?: return@mapNotNull null
            val days = today.daysUntil(next)
            if (days > withinDays) null else Renewal(RenewalKind.CARD_ANNUAL_FEE, a.id, a.name, next, days)
        } + paymentsDue(today, today.plus(DatePeriod(days = minOf(withinDays, PAYMENT_LEAD_DAYS))), today)

    /**
     * CC-01: the payment due dates between [from] and [to] of every open card with a due day that
     * is owed something on [today], for the calendar and the reminders.
     */
    fun paymentsDue(from: LocalDate, to: LocalDate, today: LocalDate = books.today()): List<Renewal> = books.accounts.list()
        .filter { it.account.type.kind == AccountKind.CREDIT && it.balanceToday.isNegative }
        .flatMap { s ->
            val terms = terms(s.account.id) ?: return@flatMap emptyList()
            terms.dueDates(from, to).map { Renewal(RenewalKind.CARD_PAYMENT_DUE, s.account.id, s.account.name, it, today.daysUntil(it)) }
        }

    companion object {
        /** CC-01: how many days before a card payment is due it appears among the reminders. */
        const val PAYMENT_LEAD_DAYS = 7

        private val DATED = setOf(BenefitKind.PURCHASE_PROTECTION, BenefitKind.PRICE_PROTECTION, BenefitKind.MOBILE_DEVICE)

        /** Categories of things purchase protection does not cover: food, fuel, services, bills, fees. */
        private val NOT_GOODS = setOf(
            "housing", "utilities", "food", "transport", "insurance", "financial", "taxes", "travel", "education",
            "health.pharmacy", "health.dental", "health.medical", "health.paramedical", "health.premiums",
            "pets.food", "pets.vet", "pets.licence", "pets.insurance", "pets.boarding",
        )

        /** CC-01: [owed] as a whole percentage of [limit] (none below zero); null for a limit of zero. */
        fun limitUsedPercent(owed: Money, limit: Money): Int? = limit.takeIf { it.isPositive }?.let {
            maxOf(0, owed.toBigDecimal().movePointRight(2).divide(it.toBigDecimal(), 0, RoundingMode.HALF_UP).toInt())
        }

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
