package ca.schippers.hfm.calc.medical

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * MED-02: what a plan pays for one kind of service. [percent] is out of 100; amounts are in
 * dollars; [frequencyMonths] allows one such service every so many months (an eye exam every 24
 * months, a dental recall every 9).
 */
data class CoverageRule(
    val percent: BigDecimal,
    val deductible: BigDecimal? = null,
    val annualMax: BigDecimal? = null,
    val perVisitMax: BigDecimal? = null,
    val frequencyMonths: Int? = null,
)

/** What has already been used of a rule in the plan year: paid out, and deductible met. */
data class CoverageUse(val paid: BigDecimal = BigDecimal.ZERO, val deductibleMet: BigDecimal = BigDecimal.ZERO)

/** A 12-month period and the eligible expenses paid in it. */
data class Window(val start: LocalDate, val end: LocalDate, val total: BigDecimal)

/** Calculations for medical and dental plans (MED-02, MED-04, MED-07, MED-09) and the tax credit (MED-12). */
object Medical {

    private val HUNDRED = BigDecimal(100)

    /**
     * What a plan should pay on [amount]: the part above what is left of the deductible, at the
     * plan's percentage, no more than the per-visit maximum or what is left of the annual maximum.
     * A secondary plan is given what the primary did not pay (coordination of benefits).
     */
    fun expected(amount: BigDecimal, rule: CoverageRule, use: CoverageUse = CoverageUse()): BigDecimal {
        if (amount.signum() <= 0) return BigDecimal.ZERO.setScale(2)
        val deductibleLeft = ((rule.deductible ?: BigDecimal.ZERO) - use.deductibleMet).max(BigDecimal.ZERO)
        var pay = (amount - deductibleLeft).max(BigDecimal.ZERO).multiply(rule.percent).divide(HUNDRED, 2, RoundingMode.HALF_UP)
        rule.perVisitMax?.let { pay = pay.min(it) }
        rule.annualMax?.let { pay = pay.min((it - use.paid).max(BigDecimal.ZERO)) }
        return pay.min(amount).setScale(2, RoundingMode.HALF_UP)
    }

    /** The part of [amount] that goes towards the deductible. */
    fun deductibleUsed(amount: BigDecimal, rule: CoverageRule, use: CoverageUse): BigDecimal =
        ((rule.deductible ?: BigDecimal.ZERO) - use.deductibleMet).max(BigDecimal.ZERO).min(amount.max(BigDecimal.ZERO))

    /** MED-04: when the next service of this kind is covered again; null when there is no limit or no earlier service. */
    fun nextEligible(lastService: LocalDate?, frequencyMonths: Int?): LocalDate? =
        if (lastService == null || frequencyMonths == null) null else lastService.plus(DatePeriod(months = frequencyMonths))

    /** The first day of the plan year containing [date], for a plan year starting on [startMonth]/[startDay]. */
    fun planYearStart(date: LocalDate, startMonth: Int = 1, startDay: Int = 1): LocalDate {
        val thisYear = LocalDate(date.year, startMonth, startDay.coerceIn(1, 28))
        return if (thisYear <= date) thisYear else thisYear.minus(DatePeriod(years = 1))
    }

    /** MED-09: the last day a claim can be sent: so many days after the service. */
    fun claimDeadline(serviceDate: LocalDate, days: Int): LocalDate = serviceDate.plus(DatePeriod(days = days))

    /**
     * MED-12: the 12-month period ending in [year] with the most eligible expenses (the federal
     * credit allows any such period). The best period always ends on the date of an expense, so
     * only those are tried. Expenses are (date paid, amount). Null when there are none in reach.
     */
    fun bestWindow(expenses: List<Pair<LocalDate, BigDecimal>>, year: Int): Window? {
        val sorted = expenses.sortedBy { it.first }
        val ends = sorted.map { it.first }.filter { it.year == year }.distinct()
        if (ends.isEmpty()) return null
        return ends.map { end ->
            val start = end.minus(DatePeriod(years = 1)).plus(DatePeriod(days = 1))
            Window(start, end, sorted.filter { it.first in start..end }.fold(BigDecimal.ZERO) { a, e -> a + e.second })
        // On a tie, the earlier period: later expenses are then left for next year's claim.
        }.maxWith(compareBy<Window> { it.total }.thenByDescending { it.end })
    }

    /** The calendar year as a period, for comparison and for returns that use it. */
    fun calendarYear(expenses: List<Pair<LocalDate, BigDecimal>>, year: Int): Window =
        Window(LocalDate(year, 1, 1), LocalDate(year, 12, 31), expenses.filter { it.first.year == year }.fold(BigDecimal.ZERO) { a, e -> a + e.second })

    /**
     * MED-13: the part of [total] that counts for the federal credit when claimed by someone with
     * [netIncome]: the expenses above 3 % of net income, or above [maxReduction] (the CRA's fixed
     * amount for the year) when that is less.
     */
    fun claimable(total: BigDecimal, netIncome: BigDecimal, maxReduction: BigDecimal?): BigDecimal {
        val threePercent = netIncome.max(BigDecimal.ZERO).multiply(BigDecimal("0.03")).setScale(2, RoundingMode.HALF_UP)
        val reduction = maxReduction?.let { threePercent.min(it) } ?: threePercent
        return (total - reduction).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * MED-13: who of the spouses should claim the household's expenses: each with what would count
     * for the credit, the most first. Usually the spouse with the lower net income, as long as they
     * have tax to pay: the credit is not refundable.
     */
    fun <K> whoClaims(total: BigDecimal, netIncomes: Map<K, BigDecimal>, maxReduction: BigDecimal?): List<Pair<K, BigDecimal>> =
        netIncomes.map { (k, income) -> k to claimable(total, income, maxReduction) }.sortedByDescending { it.second }

    /** The federal fixed amount for [year] as published by the CRA, when known; later years are entered by the user. */
    fun federalMaxReduction(year: Int): BigDecimal? = FEDERAL_MAX_REDUCTION[year]?.let { BigDecimal(it) }

    /**
     * The "3 % of net income ceiling" from the CRA's table of indexed amounts, "Adjustment of the
     * personal income tax and benefit amounts" (canada.ca/en/revenue-agency/services/tax/individuals/
     * frequently-asked-questions-individuals/adjustment-personal-income-tax-benefit-amounts.html):
     * 2023 to 2026 as shown there in October 2026.
     */
    private val FEDERAL_MAX_REDUCTION = mapOf(2023 to 2635, 2024 to 2759, 2025 to 2834, 2026 to 2890)
}
