package ca.schippers.hfm.calc.loan

import ca.schippers.hfm.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.datetime.plus
import java.math.BigDecimal

/** Something that changes a loan after it starts (LN-03). */
sealed interface LoanChange {
    val date: LocalDate

    /** A lump sum paid off the principal with the first payment on or after [date]. */
    data class Prepayment(override val date: LocalDate, val amount: Money) : LoanChange

    /**
     * A new rate for payments after [date]. With [recalculatePayment], as at a mortgage renewal, the
     * payment is recalculated to repay the balance over the remaining amortization; otherwise the
     * payment stays the same, as for most variable-rate mortgages.
     */
    data class RateChange(override val date: LocalDate, val annualRate: BigDecimal, val recalculatePayment: Boolean) : LoanChange

    /** A new regular payment from [date] on, for example after asking the lender to increase it. */
    data class PaymentChange(override val date: LocalDate, val payment: Money) : LoanChange
}

/**
 * A loan as agreed, with what happened since. [payment] is the lender's actual payment when it is
 * known; otherwise it is calculated from [terms]. [extraPerPayment] is added to every payment.
 */
data class LoanPlan(
    val terms: LoanTerms,
    val firstPaymentDate: LocalDate,
    val payment: Money? = null,
    val extraPerPayment: Money? = null,
    val changes: List<LoanChange> = emptyList(),
)

data class DatedRow(
    val number: Int,
    val date: LocalDate,
    /** The regular payment, including any extra principal. */
    val payment: Money,
    val interest: Money,
    val principal: Money,
    /** Lump sums applied with this payment. */
    val prepayment: Money,
    val balance: Money,
    val annualRate: BigDecimal,
    /** The regular payment in force, without extra principal and before the last payment is shortened. */
    val regularPayment: Money,
)

data class Projection(val rows: List<DatedRow>, val startBalance: Money) {
    val totalInterest: Money get() = rows.fold(Money.zero(startBalance.currency)) { acc, r -> acc + r.interest }
    val payoffDate: LocalDate? get() = rows.lastOrNull()?.date

    /** The balance after the last payment on or before [date]. */
    fun balanceOn(date: LocalDate): Money = rows.lastOrNull { it.date <= date }?.balance ?: startBalance

    /** The first payment after [date], if the loan is not paid off by then. */
    fun nextAfter(date: LocalDate): DatedRow? = rows.firstOrNull { it.date > date }

    /** Interest still to pay after [date]. */
    fun interestAfter(date: LocalDate): Money =
        rows.filter { it.date > date }.fold(Money.zero(startBalance.currency)) { acc, r -> acc + r.interest }
}

/** The effect of a change compared with the loan as it stands (LN-03 interest saved, LN-06 what-if). */
data class Comparison(val base: Projection, val alternative: Projection) {
    val interestSaved: Money get() = base.totalInterest - alternative.totalInterest
    val paymentsSaved: Int get() = base.rows.size - alternative.rows.size

    /** Months sooner (positive) or later (negative) the loan is paid off. */
    val monthsSooner: Int
        get() {
            val a = base.payoffDate ?: return 0
            val b = alternative.payoffDate ?: return 0
            return monthsBetween(b, a)
        }
}

class LoanNeverRepaysException : IllegalArgumentException("The payment does not cover the interest; the loan never repays")

/** Dated amortization schedules with prepayments, rate changes and payment changes (LN-01 to LN-03, LN-06). */
object LoanProjection {

    /**
     * Payment dates from [first] on. Monthly dates keep the first date's day, shortened in short
     * months; semi-monthly payments fall 15 days apart in each month (the 1st and 16th, the 15th
     * and the last day, and so on).
     */
    fun paymentDates(first: LocalDate, frequency: PaymentFrequency): Sequence<LocalDate> = when (frequency) {
        PaymentFrequency.MONTHLY -> generateSequence(0) { it + 1 }.map { first.plus(DatePeriod(months = it)) }
        PaymentFrequency.SEMI_MONTHLY -> {
            val early = if (first.day <= 15) first.day else first.day - 15
            val start = LocalDate(first.year, first.month.number, 1)
            generateSequence(0) { it + 1 }.flatMap { m ->
                val month = start.plus(DatePeriod(months = m))
                val last = month.plus(DatePeriod(months = 1)).plus(DatePeriod(days = -1)).day
                sequenceOf(early, early + 15).map { day -> LocalDate(month.year, month.month.number, minOf(day, last)) }
            }.dropWhile { it < first }
        }
        PaymentFrequency.BI_WEEKLY, PaymentFrequency.ACCELERATED_BI_WEEKLY ->
            generateSequence(first) { it.plus(DatePeriod(days = 14)) }
        PaymentFrequency.WEEKLY, PaymentFrequency.ACCELERATED_WEEKLY ->
            generateSequence(first) { it.plus(DatePeriod(days = 7)) }
    }

    /**
     * The full dated schedule. Interest is charged on the balance at the rate in force, rounded to
     * the cent each period; the last payment clears what is left.
     *
     * @throws LoanNeverRepaysException if a payment does not cover the interest.
     */
    fun project(plan: LoanPlan): Projection {
        val terms = plan.terms
        val currency = terms.principal.currency
        val zero = Money.zero(currency)
        val extra = plan.extraPerPayment ?: zero
        val changes = plan.changes.sortedBy { it.date }
        val perYear = terms.frequency.paymentsPerYear
        var rate = terms.annualRate
        var periodic = Amortization.periodicRate(rate, terms.compounding, perYear)
        var payment = plan.payment ?: Amortization.payment(terms)
        var balance = terms.principal
        val rows = ArrayList<DatedRow>()
        val applied = HashSet<LoanChange>()
        // Long enough for the longest amortization at the slowest repayment, with room for payment holidays.
        val maxPayments = (terms.amortizationMonths / 12 + 1) * perYear * 3
        for (date in paymentDates(plan.firstPaymentDate, terms.frequency)) {
            if (!balance.isPositive) break
            if (rows.size >= maxPayments) throw LoanNeverRepaysException()
            for (change in changes) {
                if (change in applied) continue
                when (change) {
                    is LoanChange.RateChange -> if (change.date < date) {
                        rate = change.annualRate
                        periodic = Amortization.periodicRate(rate, terms.compounding, perYear)
                        if (change.recalculatePayment) payment = recalculated(terms, balance, rate, rows.size)
                        applied += change
                    }
                    is LoanChange.PaymentChange -> if (change.date <= date) {
                        payment = change.payment
                        applied += change
                    }
                    is LoanChange.Prepayment -> Unit
                }
            }
            val interest = balance.times(periodic)
            val regular = payment + extra
            if (regular <= interest) throw LoanNeverRepaysException()
            val due = balance + interest
            val paid = if (regular < due) regular else due
            val principal = paid - interest
            balance -= principal
            var lump = zero
            for (change in changes) {
                if (change is LoanChange.Prepayment && change !in applied && change.date <= date) {
                    val amount = if (change.amount > balance) balance else change.amount
                    lump += amount
                    balance -= amount
                    applied += change
                }
            }
            rows += DatedRow(rows.size + 1, date, paid, interest, principal, lump, balance, rate, payment)
        }
        return Projection(rows, terms.principal)
    }

    /** The payment that repays [balance] at [rate] over what is left of the amortization after [paymentsMade]. */
    private fun recalculated(terms: LoanTerms, balance: Money, rate: BigDecimal, paymentsMade: Int): Money {
        val elapsedMonths = paymentsMade * 12 / terms.frequency.paymentsPerYear
        val remaining = (terms.amortizationMonths - elapsedMonths).coerceAtLeast(1)
        return Amortization.payment(terms.copy(principal = balance, annualRate = rate, amortizationMonths = remaining))
    }

    /** The plan without its prepayments and extra payments, to show the interest they save (LN-03). */
    fun interestSaved(plan: LoanPlan): Comparison {
        val without = plan.copy(extraPerPayment = null, changes = plan.changes.filter { it !is LoanChange.Prepayment })
        return Comparison(project(without), project(plan))
    }

    /**
     * LN-06: the loan from [date] on, starting at [balance] with [paymentsMade] payments behind it,
     * compared with the same loan after the changes given. Null values keep what the loan has now.
     */
    fun whatIf(
        plan: LoanPlan,
        date: LocalDate,
        balance: Money,
        extraPerPayment: Money? = null,
        lumpSum: Money? = null,
        annualRate: BigDecimal? = null,
        remainingAmortizationMonths: Int? = null,
    ): Comparison {
        val current = project(plan)
        val next = current.nextAfter(date)
        val made = current.rows.count { it.date <= date }
        val last = next ?: current.rows.lastOrNull()
        val nowRate = last?.annualRate ?: plan.terms.annualRate
        val nowPayment = last?.regularPayment ?: plan.payment ?: Amortization.payment(plan.terms)
        val elapsed = made * 12 / plan.terms.frequency.paymentsPerYear
        val remaining = (plan.terms.amortizationMonths - elapsed).coerceAtLeast(1)
        val first = next?.date ?: date.plus(DatePeriod(days = 1))
        val base = LoanPlan(
            plan.terms.copy(principal = balance, annualRate = nowRate, amortizationMonths = remaining),
            first, nowPayment, plan.extraPerPayment,
        )
        val newRate = annualRate ?: nowRate
        val newMonths = remainingAmortizationMonths ?: remaining
        val altTerms = base.terms.copy(annualRate = newRate, amortizationMonths = newMonths)
        val altPayment = if (annualRate == null && remainingAmortizationMonths == null) nowPayment else null
        val alt = LoanPlan(
            altTerms, first, altPayment,
            if (extraPerPayment != null) extraPerPayment + (plan.extraPerPayment ?: Money.zero(balance.currency)) else plan.extraPerPayment,
            listOfNotNull(lumpSum?.takeIf { it.isPositive }?.let { LoanChange.Prepayment(first, it) }),
        )
        return Comparison(project(base), project(alt))
    }
}

internal fun monthsBetween(from: LocalDate, to: LocalDate): Int {
    val months = (to.year - from.year) * 12 + (to.month.number - from.month.number)
    return if (months > 0 && to.day < from.day) months - 1 else if (months < 0 && to.day > from.day) months + 1 else months
}

