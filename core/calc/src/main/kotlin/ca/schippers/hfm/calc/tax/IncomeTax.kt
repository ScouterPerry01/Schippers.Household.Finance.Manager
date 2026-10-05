package ca.schippers.hfm.calc.tax

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.rules.Bracket
import ca.schippers.hfm.calc.rules.Rules
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode

/** Where a figure of the estimate belongs on the return. */
enum class TaxInputGroup { INCOME, DEDUCTIONS, CREDITS, PAYMENTS }

/**
 * A figure the income tax estimate starts from, in dollars for the tax year. Dividends are the
 * taxable (grossed-up) amounts, as on the slips; [SPOUSE_NET_INCOME] is left out when there is no
 * spouse or common-law partner to claim.
 */
enum class TaxInput(val group: TaxInputGroup) {
    EMPLOYMENT(TaxInputGroup.INCOME),

    /** Pension income that qualifies for the pension income amount (an employer pension; a RRIF or annuity from 65). */
    PENSION(TaxInputGroup.INCOME),

    /** Old Age Security, CPP or QPP benefits, EI benefits, plan withdrawals and other taxable income. */
    OTHER_INCOME(TaxInputGroup.INCOME),
    INTEREST(TaxInputGroup.INCOME),
    ELIGIBLE_DIVIDENDS(TaxInputGroup.INCOME),
    OTHER_DIVIDENDS(TaxInputGroup.INCOME),
    TAXABLE_CAPITAL_GAINS(TaxInputGroup.INCOME),

    /** Self-employment income less its expenses; may be negative. */
    BUSINESS(TaxInputGroup.INCOME),
    RRSP(TaxInputGroup.DEDUCTIONS),
    FHSA(TaxInputGroup.DEDUCTIONS),
    PENSION_PLAN(TaxInputGroup.DEDUCTIONS),
    UNION_DUES(TaxInputGroup.DEDUCTIONS),
    CHILD_CARE(TaxInputGroup.DEDUCTIONS),
    OTHER_DEDUCTIONS(TaxInputGroup.DEDUCTIONS),

    /** CPP or QPP contributions paid on employment income, both tiers together. */
    CPP_QPP(TaxInputGroup.CREDITS),

    /** EI premiums, with QPIP premiums in Quebec. */
    EI_QPIP(TaxInputGroup.CREDITS),
    DONATIONS(TaxInputGroup.CREDITS),
    MEDICAL(TaxInputGroup.CREDITS),
    SPOUSE_NET_INCOME(TaxInputGroup.CREDITS),
    TAX_DEDUCTED(TaxInputGroup.PAYMENTS),
    INSTALMENTS(TaxInputGroup.PAYMENTS),
}

/** The part of the estimate a line belongs to. */
enum class TaxPart { INCOME, FEDERAL, PROVINCIAL }

/** What a line of the estimate is; the apps name it and explain it in the user's language. */
enum class TaxLineKind {
    TOTAL_INCOME, CPP_ENHANCED, DEDUCTIONS, NET_INCOME, WORKER_DEDUCTION, TAXABLE_INCOME,
    BRACKET, TAX_ON_INCOME,
    BASIC_PERSONAL, AGE, SENIOR_SUPPLEMENT, SPOUSE, EMPLOYMENT_AMOUNT, CPP, EI, PENSION, MEDICAL, AGE_PENSION_REDUCTION,
    CREDIT_AMOUNTS, CREDITS, SUPPLEMENTAL_CREDIT, DONATIONS, DIVIDENDS, BASIC_TAX, ABATEMENT, SURTAX, TAX_REDUCTION, HEALTH_PREMIUM, TAX,
}

/**
 * One line of the estimate: [amount] in dollars. A bracket line has its [rate], the income taxed
 * at it ([base]) and where the bracket starts ([from]); a credit line its [base] and [rate].
 */
data class TaxLine(
    val part: TaxPart,
    val kind: TaxLineKind,
    val amount: BigDecimal,
    val base: BigDecimal? = null,
    val rate: BigDecimal? = null,
    val from: BigDecimal? = null,
)

/**
 * An estimate of one person's income tax for [year] where they live on December 31 ([province]):
 * federal and provincial or territorial tax after credits, with each step in [lines]. [balance] is
 * what would be owing (positive) or refunded (negative) once the tax deducted at source and the
 * instalments are counted. [ratesFrom] is the year of the rates used, earlier than [year] when its
 * own are not known yet.
 */
data class TaxEstimate(
    val year: Int,
    val province: Province,
    val totalIncome: BigDecimal,
    val netIncome: BigDecimal,
    val taxableIncome: BigDecimal,
    val federalTax: BigDecimal,
    val provincialTax: BigDecimal,
    val paid: BigDecimal,
    val averageRate: BigDecimal,
    val marginalRate: BigDecimal,
    val lines: List<TaxLine>,
    val ratesFrom: Int,
) {
    val totalTax: BigDecimal get() = federalTax + provincialTax
    val balance: BigDecimal get() = totalTax - paid
}

/**
 * Estimates a person's federal and provincial or territorial income tax from their year's figures,
 * with the rates in effect on January 1 of the year (Rates and rules, area incometax): brackets,
 * the basic personal, age, spouse, Canada employment, pension income, CPP or QPP, EI and medical
 * expense amounts, the donation and dividend tax credits, Ontario's surtax and health premium, and
 * for Quebec residents the federal abatement and Quebec's own tax. It leaves out what the books
 * cannot know or that is rarely needed (tuition and transfers, carry-forwards, low-income
 * reductions, refundable credits, the OAS recovery, alternative minimum tax, political
 * contributions); it is an estimate, not a return.
 */
object IncomeTax {

    private val HUNDRED = BigDecimal(100)

    /** Throws [ca.schippers.hfm.calc.rules.RuleException] when a rate the estimate needs has no value for [year]. */
    fun estimate(year: Int, province: Province, inputs: Map<TaxInput, BigDecimal>, age65: Boolean): TaxEstimate {
        val result = compute(year, province, inputs, age65)
        // The marginal rate: the tax on $100 more of ordinary income.
        val more = compute(year, province, inputs + (TaxInput.OTHER_INCOME to (inputs[TaxInput.OTHER_INCOME] ?: BigDecimal.ZERO) + HUNDRED), age65)
        val marginal = (more.totalTax - result.totalTax).divide(HUNDRED, 4, RoundingMode.HALF_UP)
        val average = if (result.totalIncome.signum() > 0) result.totalTax.divide(result.totalIncome, 4, RoundingMode.HALF_UP) else BigDecimal.ZERO
        return result.copy(averageRate = average.max(BigDecimal.ZERO), marginalRate = marginal)
    }

    private fun compute(year: Int, province: Province, inputs: Map<TaxInput, BigDecimal>, age65: Boolean): TaxEstimate {
        val on = LocalDate(year, 1, 1)
        val lines = ArrayList<TaxLine>()
        fun v(i: TaxInput) = inputs[i] ?: BigDecimal.ZERO
        val quebec = province == Province.QC

        // CPP or QPP: the base part of the first contribution is a credit; the enhanced part and the
        // second contribution are a deduction.
        val (maxFirst, baseRate, firstRate) = Rules.list(if (quebec) "tax.qpp" else "tax.cpp", on)
        val paidPlan = v(TaxInput.CPP_QPP).max(BigDecimal.ZERO)
        val cppCredit = money(paidPlan.min(maxFirst).multiply(baseRate).divide(firstRate, 10, RoundingMode.HALF_UP))
        val cppDeduction = paidPlan - cppCredit

        val total = TaxInput.entries.filter { it.group == TaxInputGroup.INCOME }.fold(BigDecimal.ZERO) { a, i -> a + v(i) }
        val deductions = TaxInput.entries.filter { it.group == TaxInputGroup.DEDUCTIONS }.fold(BigDecimal.ZERO) { a, i -> a + v(i).max(BigDecimal.ZERO) } + cppDeduction
        val net = (total - deductions).max(BigDecimal.ZERO)
        lines += TaxLine(TaxPart.INCOME, TaxLineKind.TOTAL_INCOME, money(total))
        if (cppDeduction.signum() > 0) lines += TaxLine(TaxPart.INCOME, TaxLineKind.CPP_ENHANCED, money(cppDeduction))
        lines += TaxLine(TaxPart.INCOME, TaxLineKind.DEDUCTIONS, money(deductions))
        lines += TaxLine(TaxPart.INCOME, TaxLineKind.NET_INCOME, money(net))
        lines += TaxLine(TaxPart.INCOME, TaxLineKind.TAXABLE_INCOME, money(net))
        val spouse = inputs[TaxInput.SPOUSE_NET_INCOME]?.max(BigDecimal.ZERO)

        // Federal.
        val fedBrackets = Rules.brackets("tax.fed.brackets", on)
        val fedOnIncome = bracketTax(TaxPart.FEDERAL, net, fedBrackets, lines)
        val bpa = phased(Rules.list("tax.fed.bpa", on), net)
        val fedAmounts = buildList {
            add(TaxLineKind.BASIC_PERSONAL to bpa)
            if (age65) add(TaxLineKind.AGE to reduced(Rules.list("tax.fed.age", on), net))
            if (spouse != null) add(TaxLineKind.SPOUSE to (bpa - spouse).max(BigDecimal.ZERO))
            add(TaxLineKind.EMPLOYMENT_AMOUNT to Rules.decimal("tax.fed.employmentAmount", on).min(v(TaxInput.EMPLOYMENT).max(BigDecimal.ZERO)))
            add(TaxLineKind.CPP to cppCredit)
            add(TaxLineKind.EI to v(TaxInput.EI_QPIP).max(BigDecimal.ZERO))
            add(TaxLineKind.PENSION to Rules.decimal("tax.fed.pension", on).min(v(TaxInput.PENSION).max(BigDecimal.ZERO)))
            add(TaxLineKind.MEDICAL to medical(federalMedical(on), v(TaxInput.MEDICAL), net))
        }
        val fedBasic = credits(
            TaxPart.FEDERAL, fedOnIncome, fedAmounts, Rules.decimal("tax.fed.creditRate", on),
            Rules.list("tax.fed.donation", on), federalDividendCredits(on), v(TaxInput.DONATIONS), net, fedBrackets.last().from,
            v(TaxInput.ELIGIBLE_DIVIDENDS), v(TaxInput.OTHER_DIVIDENDS), lines,
            supplemental = Rules.valueOn("tax.fed.topUpCredit", on)?.let { Rules.list("tax.fed.topUpCredit", on) },
        )
        var federal = fedBasic
        if (quebec) {
            val rate = Rules.decimal("tax.fed.quebecAbatement", on)
            val abatement = money(fedBasic.multiply(rate))
            lines += TaxLine(TaxPart.FEDERAL, TaxLineKind.ABATEMENT, abatement.negate(), fedBasic, rate)
            federal = fedBasic - abatement
        }
        lines += TaxLine(TaxPart.FEDERAL, TaxLineKind.TAX, federal)

        // Provincial or territorial.
        val provincial = if (quebec) quebec(on, inputs, net, spouse, age65, lines) else provincial(on, province, inputs, net, cppCredit, spouse, age65, lines)
        lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.TAX, provincial)

        val ratesFrom = Rules.valueOn("tax.fed.brackets", on)!!.from.year
        val paid = v(TaxInput.TAX_DEDUCTED) + v(TaxInput.INSTALMENTS)
        return TaxEstimate(year, province, money(total), money(net), money(net), federal, provincial, money(paid), BigDecimal.ZERO, BigDecimal.ZERO, lines, ratesFrom)
    }

    private fun provincial(
        on: LocalDate, p: Province, inputs: Map<TaxInput, BigDecimal>, net: BigDecimal, cppCredit: BigDecimal,
        spouse: BigDecimal?, age65: Boolean, lines: MutableList<TaxLine>,
    ): BigDecimal {
        fun v(i: TaxInput) = inputs[i] ?: BigDecimal.ZERO
        val brackets = Rules.brackets("tax.prov.brackets", on, p)
        val onIncome = bracketTax(TaxPart.PROVINCIAL, net, brackets, lines)
        val amounts = buildList {
            add(TaxLineKind.BASIC_PERSONAL to phased(Rules.list("tax.prov.bpa", on, p), net))
            if (age65) add(TaxLineKind.AGE to reduced(Rules.list("tax.prov.age", on, p), net))
            if (spouse != null) {
                val (max, threshold) = Rules.list("tax.prov.spouse", on, p)
                add(TaxLineKind.SPOUSE to (max - (spouse - threshold).max(BigDecimal.ZERO)).max(BigDecimal.ZERO))
            }
            if (age65) Rules.decimalOrNull("tax.prov.seniorSupplement", on, p)?.let { add(TaxLineKind.SENIOR_SUPPLEMENT to it) }
            Rules.decimalOrNull("tax.prov.employmentAmount", on, p)?.let { add(TaxLineKind.EMPLOYMENT_AMOUNT to it.min(v(TaxInput.EMPLOYMENT).max(BigDecimal.ZERO))) }
            add(TaxLineKind.CPP to cppCredit)
            add(TaxLineKind.EI to v(TaxInput.EI_QPIP).max(BigDecimal.ZERO))
            add(TaxLineKind.PENSION to Rules.decimal("tax.prov.pension", on, p).min(v(TaxInput.PENSION).max(BigDecimal.ZERO)))
            add(TaxLineKind.MEDICAL to medical(Rules.list("tax.prov.medical", on, p), v(TaxInput.MEDICAL), net))
        }
        val basic = credits(
            TaxPart.PROVINCIAL, onIncome, amounts, Rules.decimal("tax.prov.creditRate", on, p),
            Rules.list("tax.prov.donation", on, p), Rules.list("tax.prov.dividends", on, p), v(TaxInput.DONATIONS), net, brackets.last().from,
            v(TaxInput.ELIGIBLE_DIVIDENDS), v(TaxInput.OTHER_DIVIDENDS), lines,
            supplemental = Rules.valueOn("tax.prov.supplementalCredit", on, p)?.let { Rules.list("tax.prov.supplementalCredit", on, p) },
        )
        var tax = basic
        // British Columbia's tax reduction: an amount reduced by a rate of net income above a base.
        Rules.valueOn("tax.prov.lowIncomeReduction", on, p)?.let { Rules.list("tax.prov.lowIncomeReduction", on, p) }?.let { r ->
            val reduction = money(reduced(r, net)).min(tax)
            if (reduction.signum() > 0) {
                lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.TAX_REDUCTION, reduction.negate(), net)
                tax -= reduction
            }
        }
        Rules.valueOn("tax.prov.surtax", on, p)?.let { Rules.list("tax.prov.surtax", on, p) }?.let { s ->
            val surtax = s.chunked(2).filter { it.size == 2 }.fold(BigDecimal.ZERO) { a, (threshold, rate) -> a + (basic - threshold).max(BigDecimal.ZERO).multiply(rate) }
            if (surtax.signum() > 0) {
                lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.SURTAX, money(surtax), basic)
                tax += money(surtax)
            }
        }
        // Ontario's tax reduction: twice the basic reduction, less the tax, when that is positive.
        Rules.decimalOrNull("tax.prov.taxReduction", on, p)?.let { amount ->
            val reduction = money((amount.multiply(BigDecimal(2)) - tax).max(BigDecimal.ZERO).min(tax))
            if (reduction.signum() > 0) {
                lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.TAX_REDUCTION, reduction.negate(), tax)
                tax -= reduction
            }
        }
        Rules.valueOn("tax.prov.healthPremium", on, p)?.let { Rules.list("tax.prov.healthPremium", on, p) }?.let { tiers ->
            val premium = healthPremium(tiers, net)
            if (premium.signum() > 0) {
                lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.HEALTH_PREMIUM, premium, net)
                tax += premium
            }
        }
        return tax
    }

    /**
     * Quebec's own tax: the deduction for workers, Quebec's brackets and credits. The age and
     * retirement income amounts are reduced together on family income (the person's and their
     * spouse's), as is the floor of the medical expense credit; QPP, EI and QPIP give no Quebec
     * credit.
     */
    private fun quebec(on: LocalDate, inputs: Map<TaxInput, BigDecimal>, net: BigDecimal, spouse: BigDecimal?, age65: Boolean, lines: MutableList<TaxLine>): BigDecimal {
        fun v(i: TaxInput) = inputs[i] ?: BigDecimal.ZERO
        val p = Province.QC
        val (workerRate, workerMax) = Rules.list("tax.qc.workerDeduction", on)
        val worker = money(v(TaxInput.EMPLOYMENT).max(BigDecimal.ZERO).multiply(workerRate).min(workerMax))
        val qcNet = (net - worker).max(BigDecimal.ZERO)
        if (worker.signum() > 0) lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.WORKER_DEDUCTION, worker, v(TaxInput.EMPLOYMENT), workerRate)
        lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.TAXABLE_INCOME, money(qcNet))
        val brackets = Rules.brackets("tax.prov.brackets", on, p)
        val onIncome = bracketTax(TaxPart.PROVINCIAL, qcNet, brackets, lines)
        val family = qcNet + (spouse ?: BigDecimal.ZERO)
        val age = Rules.list("tax.prov.age", on, p)
        val amounts = buildList {
            add(TaxLineKind.BASIC_PERSONAL to phased(Rules.list("tax.prov.bpa", on, p), qcNet))
            val ageAmount = if (age65) age[0] else BigDecimal.ZERO
            val (retirementMax, retirementFactor) = Rules.list("tax.qc.retirement", on)
            val pension = retirementMax.min(v(TaxInput.PENSION).max(BigDecimal.ZERO).multiply(retirementFactor))
            if (age65) add(TaxLineKind.AGE to ageAmount)
            if (pension.signum() > 0) add(TaxLineKind.PENSION to pension)
            if (ageAmount + pension > BigDecimal.ZERO) {
                val reduction = (family - age[1]).max(BigDecimal.ZERO).multiply(age[2]).min(ageAmount + pension)
                if (reduction.signum() > 0) add(TaxLineKind.AGE_PENSION_REDUCTION to reduction.negate())
            }
        }
        // Quebec's medical expense credit has its own rate, on expenses above a rate of family income.
        val (floorRate, medicalRate) = Rules.list("tax.qc.medical", on)
        val claim = money(medical(listOf(floorRate), v(TaxInput.MEDICAL), family))
        val medicalCredit = if (claim.signum() > 0) listOf(TaxLine(TaxPart.PROVINCIAL, TaxLineKind.MEDICAL, money(claim.multiply(medicalRate)).negate(), claim, medicalRate)) else emptyList()
        return credits(
            TaxPart.PROVINCIAL, onIncome, amounts, Rules.decimal("tax.prov.creditRate", on, p),
            Rules.list("tax.prov.donation", on, p), Rules.list("tax.prov.dividends", on, p), v(TaxInput.DONATIONS), qcNet, brackets.last().from,
            v(TaxInput.ELIGIBLE_DIVIDENDS), v(TaxInput.OTHER_DIVIDENDS), lines, extra = medicalCredit,
        )
    }

    /** The tax on [income] bracket by bracket, a line for each bracket reached. */
    internal fun bracketTax(part: TaxPart, income: BigDecimal, brackets: List<Bracket>, lines: MutableList<TaxLine>?): BigDecimal {
        var tax = BigDecimal.ZERO
        brackets.forEachIndexed { i, b ->
            val to = brackets.getOrNull(i + 1)?.from
            val base = ((to?.let { income.min(it) } ?: income) - b.from).max(BigDecimal.ZERO)
            if (base.signum() > 0 || i == 0) {
                val t = money(base.multiply(b.rate))
                lines?.add(TaxLine(part, TaxLineKind.BRACKET, t, money(base), b.rate, b.from))
                tax += t
            }
        }
        lines?.add(TaxLine(part, TaxLineKind.TAX_ON_INCOME, tax))
        return tax
    }

    /**
     * The non-refundable credits: the amounts at the credit rate, then the donation and dividend
     * credits; the tax after them, never below zero.
     */
    private fun credits(
        part: TaxPart, onIncome: BigDecimal, amounts: List<Pair<TaxLineKind, BigDecimal>>, rate: BigDecimal,
        donationRates: List<BigDecimal>, dividendRates: List<BigDecimal>, gifts: BigDecimal, taxable: BigDecimal, topFrom: BigDecimal,
        eligible: BigDecimal, other: BigDecimal, lines: MutableList<TaxLine>,
        supplemental: List<BigDecimal>? = null, extra: List<TaxLine> = emptyList(),
    ): BigDecimal {
        var sum = BigDecimal.ZERO
        for ((kind, amount) in amounts) {
            val a = money(amount)
            if (a.signum() == 0 && kind != TaxLineKind.BASIC_PERSONAL) continue
            lines += TaxLine(part, kind, a)
            sum += a
        }
        sum = sum.max(BigDecimal.ZERO)
        val credit = money(sum.multiply(rate))
        lines += TaxLine(part, TaxLineKind.CREDIT_AMOUNTS, sum)
        lines += TaxLine(part, TaxLineKind.CREDITS, credit.negate(), sum, rate)
        // The federal top-up credit and Alberta's supplemental credit: a further rate on the credit amounts above a threshold.
        val more = supplemental?.let { (threshold, extraRate) -> money((sum - threshold).max(BigDecimal.ZERO).multiply(extraRate)) } ?: BigDecimal.ZERO
        if (more.signum() > 0) lines += TaxLine(part, TaxLineKind.SUPPLEMENTAL_CREDIT, more.negate(), sum - supplemental!![0], supplemental[1])
        lines += extra
        val others = more - extra.fold(BigDecimal.ZERO) { a, l -> a + l.amount }
        val donation = donationCredit(donationRates, gifts, taxable, topFrom)
        if (donation.signum() > 0) lines += TaxLine(part, TaxLineKind.DONATIONS, donation.negate(), money(gifts))
        val dividends = money(eligible.max(BigDecimal.ZERO).multiply(dividendRates[0]) + other.max(BigDecimal.ZERO).multiply(dividendRates[1]))
        if (dividends.signum() > 0) lines += TaxLine(part, TaxLineKind.DIVIDENDS, dividends.negate(), money(eligible + other))
        val basic = (onIncome - credit - others - donation - dividends).max(BigDecimal.ZERO)
        lines += TaxLine(part, TaxLineKind.BASIC_TAX, basic)
        return basic
    }

    /**
     * The donation credit: rates are the first tier's limit, its rate, the rate above it, and
     * optionally a higher rate on gifts above the limit up to the income taxed in the top bracket
     * (from [topFrom]).
     */
    internal fun donationCredit(rates: List<BigDecimal>, gifts: BigDecimal, taxable: BigDecimal, topFrom: BigDecimal): BigDecimal {
        if (gifts.signum() <= 0) return BigDecimal.ZERO
        val limit = rates[0]
        val first = gifts.min(limit)
        val rest = (gifts - limit).max(BigDecimal.ZERO)
        val top = if (rates.size > 3) rest.min((taxable - topFrom).max(BigDecimal.ZERO)) else BigDecimal.ZERO
        return money(first.multiply(rates[1]) + (rest - top).multiply(rates[2]) + if (rates.size > 3) top.multiply(rates[3]) else BigDecimal.ZERO)
    }

    /** A basic personal amount: one value, or the maximum, minimum and the net income range it is reduced over. */
    internal fun phased(values: List<BigDecimal>, net: BigDecimal): BigDecimal {
        if (values.size < 4) return values[0]
        val (max, min, start, end) = values
        return when {
            net <= start -> max
            net >= end -> min
            else -> max - (max - min).multiply(net - start).divide(end - start, 10, RoundingMode.HALF_UP)
        }
    }

    /** An amount reduced by a rate of net income above a threshold: maximum, threshold, rate. */
    internal fun reduced(values: List<BigDecimal>, net: BigDecimal): BigDecimal =
        (values[0] - (net - values[1]).max(BigDecimal.ZERO).multiply(values[2])).max(BigDecimal.ZERO)

    /** Medical expenses above the lesser of a rate of net income and a fixed amount (no fixed amount when only the rate is given). */
    /** The federal medical threshold: the rate of net income, then the CRA's fixed amount (the Medical rules, shared with the medical report). */
    internal fun federalMedical(on: LocalDate): List<BigDecimal> =
        listOf(Rules.decimal("medical.threshold.rate", on), Rules.decimal("medical.threshold.max", on))

    /** The federal dividend tax credit rates, eligible then other (the Investments rules, shared with the slips). */
    private fun federalDividendCredits(on: LocalDate): List<BigDecimal> =
        listOf(Rules.decimal("dividends.eligible.credit", on), Rules.decimal("dividends.other.credit", on))

    internal fun medical(values: List<BigDecimal>, expenses: BigDecimal, net: BigDecimal): BigDecimal {
        if (expenses.signum() <= 0) return BigDecimal.ZERO
        val floor = net.multiply(values[0]).let { f -> values.getOrNull(1)?.let { f.min(it) } ?: f }
        return (expenses - floor).max(BigDecimal.ZERO)
    }

    /** Ontario Health Premium: tiers of (income from, rate, cap); each tier adds its rate of the income above it to the last cap, up to its own cap. */
    internal fun healthPremium(tiers: List<BigDecimal>, income: BigDecimal): BigDecimal {
        var premium = BigDecimal.ZERO
        var lastCap = BigDecimal.ZERO
        for ((from, rate, cap) in tiers.chunked(3).filter { it.size == 3 }) {
            if (income > from) premium = cap.min(lastCap + (income - from).multiply(rate))
            lastCap = cap
        }
        return money(premium)
    }

    private fun money(x: BigDecimal): BigDecimal = x.setScale(2, RoundingMode.HALF_UP)
}
