package ca.schippers.hfm.calc.plans

import ca.schippers.hfm.calc.CALC
import ca.schippers.hfm.calc.PensionJurisdiction
import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.rules.Rules
import ca.schippers.hfm.calc.rules.decimalOrEarliest
import ca.schippers.hfm.calc.rules.intOrEarliest
import ca.schippers.hfm.calc.rules.valueOrEarliest
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Canadian registered plan rules (INV-09, INV-10). Every limit, rate and factor is a rule of the
 * Rates and rules screen (area "plans", file hfm/rules/plans.rules), read for the year it applies
 * to, so a figure the household adds there takes the built-in one's place from its date.
 */
object RegisteredPlans {

    /** The current year, for callers that do not name one. */
    private fun thisYear(): Int = java.time.LocalDate.now().year

    // --- RRIF and LIF minimum withdrawals (INV-10) -----------------------------------------------

    /**
     * The minimum withdrawal factor for someone [age] years old on January 1 of [year] (the
     * holder's age, or the younger spouse's when that was elected): from the prescribed factors
     * (rule rrif.factors, starting at the age of rule rrif.factors.age, the last one for every
     * older age), and below that 1 / (N - age), N being rule rrif.formula.age.
     */
    fun rrifFactor(age: Int, year: Int = thisYear()): BigDecimal {
        val on = LocalDate(year, 1, 1)
        val first = intOrEarliest("rrif.factors.age", on)
        if (age < first) return BigDecimal.ONE.divide(BigDecimal((intOrEarliest("rrif.formula.age", on) - age).coerceAtLeast(1)), CALC)
        val factors = valueOrEarliest("rrif.factors", on).value.split(';').map { BigDecimal(it.trim()) }
        return factors[minOf(age - first, factors.size - 1)]
    }

    /** The year's minimum: the plan's value on January 1 times the factor. LIFs use the same minimum. */
    fun rrifMinimum(valueJanuary1: Money, age: Int, year: Int = thisYear()): Money = valueJanuary1.times(rrifFactor(age, year))

    /** The LIF maximum's reference rate for a plan that has none of its own (rule lif.reference.rate). */
    fun lifReferenceRate(year: Int = thisYear()): BigDecimal = decimalOrEarliest("lif.reference.rate", LocalDate(year, 1, 1))

    /**
     * LIF maximum withdrawal, as the federal rules and most provinces set it (in Quebec, s. 20 of
     * schedule 0.7 of the Regulation respecting supplemental pension plans; in Ontario, schedule 1.1
     * of Regulation 909; and so on): the value on January 1 divided by an annuity of 1 payable at
     * the start of each year until the end of the year the holder turns 90 (rule lif.last.age), at
     * the reference rate (rule lif.reference.rate: 6 % unless the long-term rate for the first 15
     * years is higher). Never less than the year's minimum. Where a LIF has none: see [lifHasMaximum].
     */
    fun lifMaximumFactor(age: Int, referenceRate: BigDecimal = lifReferenceRate(), year: Int = thisYear()): BigDecimal {
        val years = intOrEarliest("lif.last.age", LocalDate(year, 1, 1)) - age
        if (years <= 1) return BigDecimal.ONE
        val v = BigDecimal.ONE.divide(BigDecimal.ONE + referenceRate, CALC)
        var annuity = BigDecimal.ZERO
        var term = BigDecimal.ONE
        repeat(years) {
            annuity += term
            term = term.multiply(v, CALC)
        }
        return BigDecimal.ONE.divide(annuity, CALC).max(rrifFactor(age, year))
    }

    /**
     * The year's LIF maximum: the formula, or last year's investment earnings in the LIF when
     * they are higher, which Quebec also allows.
     */
    fun lifMaximum(
        valueJanuary1: Money,
        age: Int,
        referenceRate: BigDecimal = lifReferenceRate(),
        lastYearEarnings: Money? = null,
        year: Int = thisYear(),
    ): Money {
        val byFormula = valueJanuary1.times(lifMaximumFactor(age, referenceRate, year))
        return if (lastYearEarnings != null && lastYearEarnings > byFormula) lastYearEarnings else byFormula
    }

    /**
     * Whether a LIF under [jurisdiction] has a yearly maximum (rule lif.maximum, by province; the
     * value for everywhere covers federal plans). Saskatchewan replaced LIFs with prescribed RRIFs
     * without a maximum in 2002, and Prince Edward Island has no locked-in plan rules.
     */
    fun lifHasMaximum(jurisdiction: PensionJurisdiction, year: Int = thisYear()): Boolean =
        valueOrEarliest("lif.maximum", LocalDate(year, 1, 1), (jurisdiction as? PensionJurisdiction.Provincial)?.province).value.toBooleanStrict()

    /** The RRSP must become a RRIF or annuity by December 31 of the year its holder turns this age (rule rrsp.last.age). */
    fun rrspLastAge(year: Int = thisYear()): Int = intOrEarliest("rrsp.last.age", LocalDate(year, 1, 1))

    /** Contributions in this many first days of a year count for the year before (rule rrsp.deadline.days). */
    fun rrspDeadlineDays(year: Int): Int = intOrEarliest("rrsp.deadline.days", LocalDate(year, 1, 1))

    /** RRSP contributions over the deduction limit by up to this many dollars are not taxed (rule rrsp.excess.buffer). */
    fun rrspExcessBuffer(year: Int): BigDecimal = decimalOrEarliest("rrsp.excess.buffer", LocalDate(year, 12, 31))

    /** The tax a month on an excess in [plan] (rrsp, tfsa or fhsa): rule <plan>.excess.tax, 1 % a month. */
    fun excessTaxPerMonth(plan: String, year: Int): BigDecimal = decimalOrEarliest("$plan.excess.tax", LocalDate(year, 12, 31))

    // --- TFSA (INV-09) ---------------------------------------------------------------------------------

    /** The TFSA dollar limit for [year] (rule tfsa.limit); years not yet published keep the last one. */
    fun tfsaLimit(year: Int): Int = Rules.valueOn("tfsa.limit", LocalDate(year, 1, 1))?.value?.let { BigDecimal(it).toInt() } ?: 0

    /** The age from which a resident accrues TFSA room (rule tfsa.age, 18). */
    fun tfsaAge(year: Int = thisYear()): Int = intOrEarliest("tfsa.age", LocalDate(year, 1, 1))

    /** Every limit since 2009 or the year the person turned 18, through [year]: the room of someone who never contributed. */
    fun tfsaCumulativeLimit(birthYear: Int, year: Int): Int = (maxOf(2009, birthYear + tfsaAge(year))..year).sumOf(::tfsaLimit)

    // --- FHSA (INV-09) --------------------------------------------------------------------------------

    /** The FHSA's yearly participation room in dollars (rule fhsa.annual, $8,000), for [year]. */
    fun fhsaAnnual(year: Int): Int = intOrEarliest("fhsa.annual", LocalDate(year, 12, 31))

    /** The FHSA lifetime limit in dollars (rule fhsa.lifetime, $40,000), as it stands in [year]. */
    fun fhsaLifetime(year: Int): Int = intOrEarliest("fhsa.lifetime", LocalDate(year, 12, 31))

    /**
     * FHSA participation room for each year from [openedYear] through [year]: the year's limit
     * ($8,000), plus up to that much left unused the year before, within the lifetime limit
     * ($40,000). [contributions] gives what was put in each year.
     */
    fun fhsaRoom(openedYear: Int, year: Int, contributions: Map<Int, Long>): List<YearRoom> {
        val out = ArrayList<YearRoom>()
        var carried = 0L
        var lifetimeUsed = 0L
        for (y in openedYear..year) {
            val annual = fhsaAnnual(y) * 100L
            val lifetimeLeft = (fhsaLifetime(y) * 100L - lifetimeUsed).coerceAtLeast(0)
            val available = minOf(annual + carried, lifetimeLeft)
            val used = contributions[y] ?: 0L
            out += YearRoom(y, available, used)
            lifetimeUsed += used
            carried = (available - used).coerceIn(0, annual)
        }
        return out
    }

    /** Room in minor units (cents) for one year. */
    data class YearRoom(val year: Int, val available: Long, val used: Long) {
        val left: Long get() = available - used
    }

    // --- RESP grants ----------------------------------------------------------------------------------

    /**
     * A matching grant on RESP contributions, from the rules starting with [key]: <key>.rate of the
     * year's contributions, with grant room accruing <key>.room a year from the beneficiary's birth
     * (or <key>.first.year) to the year they turn 17, at most <key>.yearly.max a year and
     * <key>.lifetime in all. Each figure is read for the year; amounts in cents.
     */
    class GrantRules(val key: String) {
        fun rate(year: Int): BigDecimal = decimalOrEarliest("$key.rate", LocalDate(year, 12, 31))
        fun annualRoom(year: Int): Long = cents("$key.room", year)
        fun yearlyMax(year: Int): Long = cents("$key.yearly.max", year)
        fun lifetime(year: Int): Long = cents("$key.lifetime", year)
        fun firstYear(year: Int): Int = intOrEarliest("$key.first.year", LocalDate(year, 12, 31))

        private fun cents(rule: String, year: Int): Long = decimalOrEarliest(rule, LocalDate(year, 12, 31)).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()

        override fun toString(): String = key
    }

    /** Canada Education Savings Grant: 20% on the first $2,500 a year, up to $1,000 with carry-forward, $7,200 in all. */
    val CESG = GrantRules("resp.cesg")

    /** Quebec education savings incentive (QESI): 10% on the first $2,500 a year, up to $500 with carry-forward, $3,600 in all. */
    val QESI = GrantRules("resp.qesi")

    /** The RESP lifetime contribution limit per beneficiary, in cents (rule resp.lifetime, $50,000), for [year]. */
    fun respLifetime(year: Int = thisYear()): Long = decimalOrEarliest("resp.lifetime", LocalDate(year, 12, 31)).movePointRight(2).toLong()

    /**
     * The provincial RESP grant for a beneficiary living in [province] in [year], if it has one
     * (rules resp.qesi.offered and resp.bctesg.offered, by province).
     */
    fun provincialGrant(province: Province, year: Int = thisYear()): ProvincialGrant? {
        val on = LocalDate(year, 12, 31)
        return when {
            valueOrEarliest("resp.qesi.offered", on, province).value.toBooleanStrict() -> ProvincialGrant.QESI
            valueOrEarliest("resp.bctesg.offered", on, province).value.toBooleanStrict() -> ProvincialGrant.BCTESG
            else -> null
        }
    }

    enum class ProvincialGrant { QESI, BCTESG }

    /** B.C. Training and Education Savings Grant, in cents: $1,200 once, without a contribution (rule resp.bctesg.amount). */
    fun bctesgAmount(on: LocalDate): Long = decimalOrEarliest("resp.bctesg.amount", on).movePointRight(2).toLong()

    /**
     * Whether a B.C. child born on [birth] can have the BCTESG by [today]: born in 2006 or later
     * (rule resp.bctesg.born.from), applied for from the 6th birthday (rule resp.bctesg.age.from).
     */
    fun bctesgEligible(birth: LocalDate, today: LocalDate): Boolean =
        birth.year >= intOrEarliest("resp.bctesg.born.from", today) && ageOn(birth, today) >= intOrEarliest("resp.bctesg.age.from", today)

    /** The BCTESG can no longer be applied for once the child turns 9 (rule resp.bctesg.age.until). */
    fun bctesgWindowClosed(birth: LocalDate, today: LocalDate): Boolean = ageOn(birth, today) >= intOrEarliest("resp.bctesg.age.until", today)

    private fun ageOn(birth: LocalDate, date: LocalDate): Int =
        date.year - birth.year - if (date.month < birth.month || (date.month == birth.month && date.day < birth.day)) 1 else 0

    data class GrantYear(val year: Int, val contributions: Long, val grant: Long, val roomLeft: Long, val eligible: Boolean)

    /**
     * The grant each year for a beneficiary born in [birthYear], through [year]. At 16 and 17 the
     * grant is paid only if $2,000 (rule resp.grant.early.total) was contributed before the end of
     * the year they turned 15, or at least $100 (rule resp.grant.early.yearly) in each of four
     * years (rule resp.grant.early.years) before then.
     */
    fun grants(rules: GrantRules, birthYear: Int, contributions: Map<Int, Long>, year: Int): List<GrantYear> {
        val out = ArrayList<GrantYear>()
        var room = 0L
        var paid = 0L
        val lastYear = birthYear + 17
        val at15 = LocalDate(birthYear + 15, 12, 31)
        val before16 = contributions.filterKeys { it <= birthYear + 15 }
        val earlyTotal = decimalOrEarliest("resp.grant.early.total", at15).movePointRight(2).toLong()
        val earlyYearly = decimalOrEarliest("resp.grant.early.yearly", at15).movePointRight(2).toLong()
        val earlyYears = intOrEarliest("resp.grant.early.years", at15)
        val earlyEnough = before16.values.sum() >= earlyTotal || before16.values.count { it >= earlyYearly } >= earlyYears
        for (y in maxOf(birthYear, rules.firstYear(year))..minOf(year, lastYear)) {
            room += rules.annualRoom(y)
            val c = contributions[y] ?: 0L
            val eligible = y - birthYear < 16 || earlyEnough
            val grant = if (!eligible) 0L else minOf(
                BigDecimal(c).multiply(rules.rate(y)).setScale(0, RoundingMode.HALF_UP).toLong(), room, rules.yearlyMax(y), rules.lifetime(y) - paid,
            ).coerceAtLeast(0)
            room -= grant
            paid += grant
            out += GrantYear(y, c, grant, room, eligible)
        }
        return out
    }
}
