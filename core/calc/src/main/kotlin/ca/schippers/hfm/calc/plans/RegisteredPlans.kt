package ca.schippers.hfm.calc.plans

import ca.schippers.hfm.calc.CALC
import ca.schippers.hfm.money.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Canadian registered plan rules (INV-09, INV-10). Limits and factors are the published federal
 * and Quebec figures; each table names its source so it can be updated each year.
 */
object RegisteredPlans {

    // --- RRIF and LIF minimum withdrawals (INV-10) -----------------------------------------------

    /**
     * Prescribed RRIF minimum factors from age 71 (Income Tax Regulations, s. 7308(4), as amended in
     * 2015). Below 71 the factor is 1 / (90 - age).
     */
    private val RRIF_FACTORS = mapOf(
        71 to "0.0528", 72 to "0.0540", 73 to "0.0553", 74 to "0.0567", 75 to "0.0582", 76 to "0.0598", 77 to "0.0617",
        78 to "0.0636", 79 to "0.0658", 80 to "0.0682", 81 to "0.0708", 82 to "0.0738", 83 to "0.0771", 84 to "0.0808",
        85 to "0.0851", 86 to "0.0899", 87 to "0.0955", 88 to "0.1021", 89 to "0.1099", 90 to "0.1192", 91 to "0.1306",
        92 to "0.1449", 93 to "0.1634", 94 to "0.1879",
    ).mapValues { BigDecimal(it.value) }

    /**
     * The minimum withdrawal factor for someone [age] years old on January 1 (the holder's age, or
     * the younger spouse's when that was elected).
     */
    fun rrifFactor(age: Int): BigDecimal = when {
        age >= 95 -> BigDecimal("0.20")
        age >= 71 -> RRIF_FACTORS.getValue(age)
        else -> BigDecimal.ONE.divide(BigDecimal(90 - age), CALC)
    }

    /** The year's minimum: the plan's value on January 1 times the factor. LIFs use the same minimum. */
    fun rrifMinimum(valueJanuary1: Money, age: Int): Money = valueJanuary1.times(rrifFactor(age))

    /**
     * Quebec LIF maximum withdrawal (Regulation respecting supplemental pension plans, s. 20 of
     * schedule 0.7): the value on January 1 divided by an annuity of 1 payable at the start of each
     * year until the end of the year the holder turns 90, at the reference rate (6% unless the
     * long-term rate for the first 15 years is higher). Never less than the year's minimum.
     */
    fun lifMaximumFactor(age: Int, referenceRate: BigDecimal = BigDecimal("0.06")): BigDecimal {
        val years = 90 - age
        if (years <= 1) return BigDecimal.ONE
        val v = BigDecimal.ONE.divide(BigDecimal.ONE + referenceRate, CALC)
        var annuity = BigDecimal.ZERO
        var term = BigDecimal.ONE
        repeat(years) {
            annuity += term
            term = term.multiply(v, CALC)
        }
        return BigDecimal.ONE.divide(annuity, CALC).max(rrifFactor(age))
    }

    /**
     * The year's LIF maximum: the formula, or last year's investment earnings in the LIF when
     * they are higher, which Quebec also allows.
     */
    fun lifMaximum(valueJanuary1: Money, age: Int, referenceRate: BigDecimal = BigDecimal("0.06"), lastYearEarnings: Money? = null): Money {
        val byFormula = valueJanuary1.times(lifMaximumFactor(age, referenceRate))
        return if (lastYearEarnings != null && lastYearEarnings > byFormula) lastYearEarnings else byFormula
    }

    /** The RRSP must become a RRIF or annuity by December 31 of the year its holder turns 71. */
    const val RRSP_LAST_AGE = 71

    // --- TFSA (INV-09) ---------------------------------------------------------------------------------

    /** Annual TFSA dollar limits published by the CRA. */
    private val TFSA_LIMITS = mapOf(
        2009 to 5000, 2010 to 5000, 2011 to 5000, 2012 to 5000, 2013 to 5500, 2014 to 5500, 2015 to 10000, 2016 to 5500,
        2017 to 5500, 2018 to 5500, 2019 to 6000, 2020 to 6000, 2021 to 6000, 2022 to 6000, 2023 to 6500, 2024 to 7000,
        2025 to 7000, 2026 to 7000,
    )

    /** The limit for [year]; years not yet published repeat the last one known. */
    fun tfsaLimit(year: Int): Int = when {
        year < 2009 -> 0
        else -> TFSA_LIMITS[year] ?: TFSA_LIMITS.getValue(TFSA_LIMITS.keys.max())
    }

    /** Every limit since 2009 or the year the person turned 18, through [year]: the room of someone who never contributed. */
    fun tfsaCumulativeLimit(birthYear: Int, year: Int): Int = (maxOf(2009, birthYear + 18)..year).sumOf(::tfsaLimit)

    // --- FHSA (INV-09) --------------------------------------------------------------------------------

    const val FHSA_ANNUAL = 8000
    const val FHSA_LIFETIME = 40000

    /**
     * FHSA participation room for each year from [openedYear] through [year]: $8,000 a year, plus up
     * to $8,000 left unused the year before, within the $40,000 lifetime limit. [contributions]
     * gives what was put in each year.
     */
    fun fhsaRoom(openedYear: Int, year: Int, contributions: Map<Int, Long>): List<YearRoom> {
        val out = ArrayList<YearRoom>()
        var carried = 0L
        var lifetimeUsed = 0L
        for (y in openedYear..year) {
            val lifetimeLeft = (FHSA_LIFETIME * 100L - lifetimeUsed).coerceAtLeast(0)
            val available = minOf(FHSA_ANNUAL * 100L + carried, lifetimeLeft)
            val used = contributions[y] ?: 0L
            out += YearRoom(y, available, used)
            lifetimeUsed += used
            carried = (available - used).coerceIn(0, FHSA_ANNUAL * 100L)
        }
        return out
    }

    /** Room in minor units (cents) for one year. */
    data class YearRoom(val year: Int, val available: Long, val used: Long) {
        val left: Long get() = available - used
    }

    // --- RESP grants ----------------------------------------------------------------------------------

    /**
     * A matching grant on RESP contributions: [rate] of the year's contributions, with grant room
     * accruing [annualRoom] a year from the beneficiary's birth (or [firstYear]) to the year they
     * turn 17, at most [yearlyMax] a year and [lifetime] in all. Amounts in cents.
     */
    data class GrantRules(val rate: BigDecimal, val annualRoom: Long, val yearlyMax: Long, val lifetime: Long, val firstYear: Int)

    /** Canada Education Savings Grant: 20% on the first $2,500 a year, up to $1,000 with carry-forward, $7,200 in all. */
    val CESG = GrantRules(BigDecimal("0.20"), 500_00, 1000_00, 7200_00, 2007)

    /** Quebec education savings incentive (QESI): 10% on the first $2,500 a year, up to $500 with carry-forward, $3,600 in all. */
    val QESI = GrantRules(BigDecimal("0.10"), 250_00, 500_00, 3600_00, 2007)

    const val RESP_LIFETIME = 50000_00L

    data class GrantYear(val year: Int, val contributions: Long, val grant: Long, val roomLeft: Long, val eligible: Boolean)

    /**
     * The grant each year for a beneficiary born in [birthYear], through [year]. At 16 and 17 the
     * grant is paid only if $2,000 was contributed before the end of the year they turned 15, or
     * at least $100 in each of four years before then.
     */
    fun grants(rules: GrantRules, birthYear: Int, contributions: Map<Int, Long>, year: Int): List<GrantYear> {
        val out = ArrayList<GrantYear>()
        var room = 0L
        var paid = 0L
        val lastYear = birthYear + 17
        val before16 = contributions.filterKeys { it <= birthYear + 15 }
        val earlyEnough = before16.values.sum() >= 2000_00 || before16.values.count { it >= 100_00 } >= 4
        for (y in maxOf(birthYear, rules.firstYear)..minOf(year, lastYear)) {
            room += rules.annualRoom
            val c = contributions[y] ?: 0L
            val eligible = y - birthYear < 16 || earlyEnough
            val grant = if (!eligible) 0L else minOf(
                BigDecimal(c).multiply(rules.rate).setScale(0, RoundingMode.HALF_UP).toLong(), room, rules.yearlyMax, rules.lifetime - paid,
            ).coerceAtLeast(0)
            room -= grant
            paid += grant
            out += GrantYear(y, c, grant, room, eligible)
        }
        return out
    }
}
