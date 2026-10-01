package ca.schippers.hfm.calc.loan

import ca.schippers.hfm.calc.CALC
import ca.schippers.hfm.calc.rationalPower
import ca.schippers.hfm.money.Money
import java.math.BigDecimal
import java.math.RoundingMode

/** How often interest compounds per year. Canadian fixed-rate mortgages compound semi-annually (LN-01). */
enum class Compounding(val periodsPerYear: Int) {
    ANNUAL(1),
    SEMI_ANNUAL(2),
    MONTHLY(12),
}

/**
 * Payment frequencies (LN-01). Accelerated frequencies pay a fraction of the monthly payment
 * more often, which pays the loan off faster than the amortization period.
 */
enum class PaymentFrequency(val paymentsPerYear: Int, val acceleratedDivisor: Int?) {
    MONTHLY(12, null),
    SEMI_MONTHLY(24, null),
    BI_WEEKLY(26, null),
    ACCELERATED_BI_WEEKLY(26, 2),
    WEEKLY(52, null),
    ACCELERATED_WEEKLY(52, 4),
}

data class LoanTerms(
    val principal: Money,
    /** Nominal annual rate as a fraction, e.g. 0.05 for 5%. */
    val annualRate: BigDecimal,
    val compounding: Compounding,
    val amortizationMonths: Int,
    val frequency: PaymentFrequency,
) {
    init {
        require(principal.isPositive) { "Principal must be positive" }
        require(annualRate.signum() >= 0) { "Rate cannot be negative" }
        require(amortizationMonths > 0) { "Amortization must be positive" }
    }
}

data class ScheduleRow(
    val number: Int,
    val payment: Money,
    val interest: Money,
    val principal: Money,
    val balance: Money,
)

data class Schedule(val payment: Money, val rows: List<ScheduleRow>) {
    val totalInterest: Money get() = rows.fold(Money.zero(payment.currency)) { acc, r -> acc + r.interest }
}

/** Loan and mortgage arithmetic (LN-01, LN-02). All results are exact to the cent. */
object Amortization {

    /**
     * Interest rate per payment period equivalent to [annualRate] compounded [compounding]:
     * (1 + j/m)^(m/p) - 1.
     */
    fun periodicRate(annualRate: BigDecimal, compounding: Compounding, paymentsPerYear: Int): BigDecimal {
        if (annualRate.signum() == 0) return BigDecimal.ZERO
        val m = compounding.periodsPerYear
        val perCompoundingPeriod = BigDecimal.ONE.add(annualRate.divide(BigDecimal(m), CALC))
        return rationalPower(perCompoundingPeriod, m, paymentsPerYear).subtract(BigDecimal.ONE)
    }

    /** Level payment that repays [principal] over [periods] at [rate] per period, rounded to the cent. */
    fun levelPayment(principal: Money, rate: BigDecimal, periods: Int): Money {
        require(periods > 0) { "Periods must be positive" }
        if (rate.signum() == 0) return Money.of(principal.toBigDecimal().divide(BigDecimal(periods), CALC), principal.currency)
        val discount = BigDecimal.ONE.subtract(BigDecimal.ONE.divide(BigDecimal.ONE.add(rate).pow(periods, CALC), CALC))
        return Money.of(principal.toBigDecimal().multiply(rate).divide(discount, CALC), principal.currency)
    }

    /** The regular payment for [terms], including accelerated frequencies. */
    fun payment(terms: LoanTerms): Money {
        val divisor = terms.frequency.acceleratedDivisor
        if (divisor != null) {
            val monthly = levelPayment(
                terms.principal,
                periodicRate(terms.annualRate, terms.compounding, 12),
                terms.amortizationMonths,
            )
            return Money.of(monthly.toBigDecimal().divide(BigDecimal(divisor), CALC), monthly.currency, RoundingMode.HALF_UP)
        }
        val perYear = terms.frequency.paymentsPerYear
        val periods = BigDecimal(terms.amortizationMonths).multiply(BigDecimal(perYear))
            .divide(BigDecimal(12), 0, RoundingMode.CEILING).intValueExact()
        return levelPayment(terms.principal, periodicRate(terms.annualRate, terms.compounding, perYear), periods)
    }

    /**
     * Full schedule splitting each payment into interest and principal (LN-02). Interest is
     * rounded to the cent each period, as lenders do; the final payment clears the remaining balance.
     */
    fun schedule(terms: LoanTerms): Schedule {
        val payment = payment(terms)
        val rate = periodicRate(terms.annualRate, terms.compounding, terms.frequency.paymentsPerYear)
        val zero = Money.zero(terms.principal.currency)
        val rows = ArrayList<ScheduleRow>()
        var balance = terms.principal
        var n = 0
        val maxPayments = terms.amortizationMonths * 5 + 1
        while (balance.isPositive) {
            n++
            check(n <= maxPayments) { "Payment does not cover interest; loan never repays" }
            val interest = balance.times(rate)
            val due = balance + interest
            val paid = if (payment < due) payment else due
            val principalPart = paid - interest
            balance -= principalPart
            rows += ScheduleRow(n, paid, interest, principalPart, if (balance.isNegative) zero else balance)
        }
        return Schedule(payment, rows)
    }
}
