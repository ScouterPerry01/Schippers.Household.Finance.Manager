package ca.schippers.hfm.calc.tax

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.rules.Bracket
import ca.schippers.hfm.calc.rules.Rules
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Where a figure of the estimate belongs on the return; [CARRY_FORWARD] holds the balances from
 * earlier years that the notice of assessment gives.
 */
enum class TaxInputGroup { INCOME, DEDUCTIONS, CREDITS, CARRY_FORWARD, PAYMENTS }

/**
 * A figure the income tax estimate starts from, in dollars for the tax year. Dividends are the
 * taxable (grossed-up) amounts, as on the slips; [SPOUSE_NET_INCOME] is left out when there is no
 * spouse or common-law partner to claim, and [RRSP_LIMIT] when the deduction limit is not known.
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

    /** Eligible tuition fees of the year (T2202, RL-8). */
    TUITION(TaxInputGroup.CREDITS),

    /** Of this year's tuition, what the student wants to transfer to a spouse, parent or grandparent. */
    TUITION_TO_TRANSFER(TaxInputGroup.CREDITS),

    /** Tuition transferred to this person by a student (their spouse, child or grandchild). */
    TUITION_RECEIVED(TaxInputGroup.CREDITS),
    SPOUSE_NET_INCOME(TaxInputGroup.CREDITS),

    /** Unused federal tuition amounts of earlier years. */
    TUITION_CARRIED(TaxInputGroup.CARRY_FORWARD),

    /** Unused provincial or territorial (or Quebec) tuition amounts of earlier years. */
    TUITION_CARRIED_PROVINCIAL(TaxInputGroup.CARRY_FORWARD),

    /** Donations of the five previous years not claimed yet. */
    DONATIONS_CARRIED(TaxInputGroup.CARRY_FORWARD),

    /** Net capital losses of other years, at their taxable (allowable) amount. */
    CAPITAL_LOSSES_CARRIED(TaxInputGroup.CARRY_FORWARD),

    /** RRSP contributions of earlier years not deducted yet. */
    RRSP_UNUSED(TaxInputGroup.CARRY_FORWARD),

    /** The RRSP deduction limit of the notice of assessment. */
    RRSP_LIMIT(TaxInputGroup.CARRY_FORWARD),
    TAX_DEDUCTED(TaxInputGroup.PAYMENTS),
    INSTALMENTS(TaxInputGroup.PAYMENTS),
}

/** The part of the estimate a line belongs to. */
enum class TaxPart { INCOME, FEDERAL, PROVINCIAL }

/** What a line of the estimate is; the apps name it and explain it in the user's language. */
enum class TaxLineKind {
    TOTAL_INCOME, CPP_ENHANCED, RRSP_DEDUCTION, DEDUCTIONS, NET_INCOME, WORKER_DEDUCTION, CAPITAL_LOSSES, TAXABLE_INCOME,
    BRACKET, TAX_ON_INCOME,
    BASIC_PERSONAL, AGE, SENIOR_SUPPLEMENT, SPOUSE, EMPLOYMENT_AMOUNT, CPP, EI, PENSION, MEDICAL, AGE_PENSION_REDUCTION,
    TUITION, TUITION_RECEIVED,
    CREDIT_AMOUNTS, CREDITS, SUPPLEMENTAL_CREDIT, DONATIONS, DIVIDENDS, BASIC_TAX, ABATEMENT, SURTAX, TAX_REDUCTION, HEALTH_PREMIUM, TAX,
}

/** A balance carried from year to year. */
enum class CarryKind { TUITION_FEDERAL, TUITION_PROVINCIAL, DONATIONS, CAPITAL_LOSSES, RRSP }

/**
 * What became of a balance carried forward this year: [available] (with this year's own amounts,
 * such as the year's tuition or donations), what is [used] on this return, what is [transferred]
 * to someone else (tuition), and what is [left] for the years after.
 */
data class CarryForward(val kind: CarryKind, val available: BigDecimal, val used: BigDecimal, val transferred: BigDecimal, val left: BigDecimal)

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
    val carryForwards: List<CarryForward> = emptyList(),
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

        val carry = ArrayList<CarryForward>()
        fun p(i: TaxInput) = v(i).max(BigDecimal.ZERO)
        val total = TaxInput.entries.filter { it.group == TaxInputGroup.INCOME }.fold(BigDecimal.ZERO) { a, i -> a + v(i) }

        // RRSP: this year's contributions and the unused ones of earlier years, within the deduction limit when it is known.
        val rrspClaim = p(TaxInput.RRSP) + p(TaxInput.RRSP_UNUSED)
        val rrspLimit = inputs[TaxInput.RRSP_LIMIT]?.max(BigDecimal.ZERO)
        val rrsp = rrspLimit?.let { rrspClaim.min(it) } ?: rrspClaim
        val deductions = TaxInput.entries.filter { it.group == TaxInputGroup.DEDUCTIONS && it != TaxInput.RRSP }.fold(BigDecimal.ZERO) { a, i -> a + p(i) } + rrsp + cppDeduction
        val net = (total - deductions).max(BigDecimal.ZERO)
        lines += TaxLine(TaxPart.INCOME, TaxLineKind.TOTAL_INCOME, money(total))
        if (cppDeduction.signum() > 0) lines += TaxLine(TaxPart.INCOME, TaxLineKind.CPP_ENHANCED, money(cppDeduction))
        if (p(TaxInput.RRSP_UNUSED).signum() > 0 || rrspLimit != null) {
            lines += TaxLine(TaxPart.INCOME, TaxLineKind.RRSP_DEDUCTION, money(rrsp), money(rrspClaim))
            carry += CarryForward(CarryKind.RRSP, money(rrspClaim), money(rrsp), BigDecimal.ZERO.setScale(2), money(rrspClaim - rrsp))
        }
        lines += TaxLine(TaxPart.INCOME, TaxLineKind.DEDUCTIONS, money(deductions))
        lines += TaxLine(TaxPart.INCOME, TaxLineKind.NET_INCOME, money(net))

        // Net capital losses of other years, against this year's taxable capital gains (taxable income, not net income).
        val lossesCarried = p(TaxInput.CAPITAL_LOSSES_CARRIED)
        val losses = lossesCarried.min(p(TaxInput.TAXABLE_CAPITAL_GAINS)).min(net)
        if (lossesCarried.signum() > 0) {
            lines += TaxLine(TaxPart.INCOME, TaxLineKind.CAPITAL_LOSSES, money(losses).negate(), money(p(TaxInput.TAXABLE_CAPITAL_GAINS)))
            carry += CarryForward(CarryKind.CAPITAL_LOSSES, money(lossesCarried), money(losses), BigDecimal.ZERO.setScale(2), money(lossesCarried - losses))
        }
        val taxable = net - losses
        lines += TaxLine(TaxPart.INCOME, TaxLineKind.TAXABLE_INCOME, money(taxable))
        val spouse = inputs[TaxInput.SPOUSE_NET_INCOME]?.max(BigDecimal.ZERO)

        // Donations: this year's and those carried from the five years before, up to a share of net income; the oldest first.
        val giftsCarried = p(TaxInput.DONATIONS_CARRIED)
        val gifts = p(TaxInput.DONATIONS) + giftsCarried
        val claimedGifts = gifts.min(money(net.multiply(Rules.decimal("tax.donationLimit", on))))
        if (giftsCarried.signum() > 0 || claimedGifts < gifts) {
            carry += CarryForward(CarryKind.DONATIONS, money(gifts), money(claimedGifts), BigDecimal.ZERO.setScale(2), money(gifts - claimedGifts))
        }

        // Federal.
        val fedBrackets = Rules.brackets("tax.fed.brackets", on)
        val fedOnIncome = bracketTax(TaxPart.FEDERAL, taxable, fedBrackets, lines)
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
        val fedTuition = Tuition(
            p(TaxInput.TUITION_CARRIED), p(TaxInput.TUITION), p(TaxInput.TUITION_RECEIVED).min(Rules.decimal("tax.fed.tuitionTransfer", on)),
            p(TaxInput.TUITION_TO_TRANSFER), Rules.decimal("tax.fed.tuitionTransfer", on),
        )
        val fedBasic = credits(
            TaxPart.FEDERAL, fedOnIncome, fedAmounts, Rules.decimal("tax.fed.creditRate", on),
            Rules.list("tax.fed.donation", on), federalDividendCredits(on), claimedGifts, taxable, fedBrackets.last().from,
            v(TaxInput.ELIGIBLE_DIVIDENDS), v(TaxInput.OTHER_DIVIDENDS), lines,
            supplemental = Rules.valueOn("tax.fed.topUpCredit", on)?.let { Rules.list("tax.fed.topUpCredit", on) },
            tuition = fedTuition,
        )
        fedTuition.carry(CarryKind.TUITION_FEDERAL)?.let { carry += it }
        var federal = fedBasic
        if (quebec) {
            val rate = Rules.decimal("tax.fed.quebecAbatement", on)
            val abatement = money(fedBasic.multiply(rate))
            lines += TaxLine(TaxPart.FEDERAL, TaxLineKind.ABATEMENT, abatement.negate(), fedBasic, rate)
            federal = fedBasic - abatement
        }
        lines += TaxLine(TaxPart.FEDERAL, TaxLineKind.TAX, federal)

        // Provincial or territorial.
        val provTuition = provincialTuition(on, province, inputs)
        val provincial = if (quebec) {
            quebec(on, inputs, net, losses, gifts, spouse, age65, lines, provTuition)
        } else {
            provincial(on, province, inputs, net, taxable, claimedGifts, cppCredit, spouse, age65, lines, provTuition)
        }
        provTuition.carry(CarryKind.TUITION_PROVINCIAL)?.let { carry += it }
        lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.TAX, provincial)

        val ratesFrom = Rules.valueOn("tax.fed.brackets", on)!!.from.year
        val paid = v(TaxInput.TAX_DEDUCTED) + v(TaxInput.INSTALMENTS)
        return TaxEstimate(
            year, province, money(total), money(net), money(taxable), federal, provincial, money(paid), BigDecimal.ZERO, BigDecimal.ZERO, lines, ratesFrom,
            carry.sortedBy { it.kind },
        )
    }

    /**
     * The provincial or territorial tuition claim: the unused amounts carried forward always count;
     * this year's fees and a transfer only where the province or territory still gives the credit.
     * Quebec's credit is at its own rate (tax.qc.tuition) on the fees and transfers only to a parent
     * or grandparent, without a dollar maximum.
     */
    private fun provincialTuition(on: LocalDate, p: Province, inputs: Map<TaxInput, BigDecimal>): Tuition {
        fun v(i: TaxInput) = (inputs[i] ?: BigDecimal.ZERO).max(BigDecimal.ZERO)
        val carried = v(TaxInput.TUITION_CARRIED_PROVINCIAL)
        if (p == Province.QC) {
            // No dollar maximum: what the student cannot use of this year's fees can go to a parent or grandparent.
            val current = v(TaxInput.TUITION)
            return Tuition(carried, current, v(TaxInput.TUITION_RECEIVED), v(TaxInput.TUITION_TO_TRANSFER), current, Rules.decimal("tax.qc.tuition", on))
        }
        if (!Rules.yesNo("tax.prov.tuition", on, p)) return Tuition(carried, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
        val max = Rules.decimal("tax.prov.tuitionTransfer", on, p)
        return Tuition(carried, v(TaxInput.TUITION), v(TaxInput.TUITION_RECEIVED).min(max), v(TaxInput.TUITION_TO_TRANSFER), max)
    }

    private fun provincial(
        on: LocalDate, p: Province, inputs: Map<TaxInput, BigDecimal>, net: BigDecimal, taxable: BigDecimal, gifts: BigDecimal, cppCredit: BigDecimal,
        spouse: BigDecimal?, age65: Boolean, lines: MutableList<TaxLine>, tuition: Tuition,
    ): BigDecimal {
        fun v(i: TaxInput) = inputs[i] ?: BigDecimal.ZERO
        val brackets = Rules.brackets("tax.prov.brackets", on, p)
        val onIncome = bracketTax(TaxPart.PROVINCIAL, taxable, brackets, lines)
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
            Rules.list("tax.prov.donation", on, p), Rules.list("tax.prov.dividends", on, p), gifts, taxable, brackets.last().from,
            v(TaxInput.ELIGIBLE_DIVIDENDS), v(TaxInput.OTHER_DIVIDENDS), lines,
            supplemental = Rules.valueOn("tax.prov.supplementalCredit", on, p)?.let { Rules.list("tax.prov.supplementalCredit", on, p) },
            tuition = tuition,
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
            val premium = healthPremium(tiers, taxable)
            if (premium.signum() > 0) {
                lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.HEALTH_PREMIUM, premium, money(taxable))
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
    private fun quebec(
        on: LocalDate, inputs: Map<TaxInput, BigDecimal>, net: BigDecimal, losses: BigDecimal, gifts: BigDecimal, spouse: BigDecimal?, age65: Boolean,
        lines: MutableList<TaxLine>, tuition: Tuition,
    ): BigDecimal {
        fun v(i: TaxInput) = inputs[i] ?: BigDecimal.ZERO
        val p = Province.QC
        val (workerRate, workerMax) = Rules.list("tax.qc.workerDeduction", on)
        val worker = money(v(TaxInput.EMPLOYMENT).max(BigDecimal.ZERO).multiply(workerRate).min(workerMax))
        val qcNet = (net - worker).max(BigDecimal.ZERO)
        if (worker.signum() > 0) lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.WORKER_DEDUCTION, worker, v(TaxInput.EMPLOYMENT), workerRate)
        val qcLosses = losses.min(qcNet)
        if (qcLosses.signum() > 0) lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.CAPITAL_LOSSES, money(qcLosses).negate())
        val qcTaxable = qcNet - qcLosses
        lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.TAXABLE_INCOME, money(qcTaxable))
        val qcGifts = gifts.min(money(qcNet.multiply(Rules.decimal("tax.donationLimit", on))))
        val brackets = Rules.brackets("tax.prov.brackets", on, p)
        val onIncome = bracketTax(TaxPart.PROVINCIAL, qcTaxable, brackets, lines)
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
            Rules.list("tax.prov.donation", on, p), Rules.list("tax.prov.dividends", on, p), qcGifts, qcTaxable, brackets.last().from,
            v(TaxInput.ELIGIBLE_DIVIDENDS), v(TaxInput.OTHER_DIVIDENDS), lines, extra = medicalCredit, tuition = tuition,
        )
    }

    /**
     * A tuition claim: unused amounts [carried] from earlier years and this year's fees
     * ([current]), what was [received] from a student, what the student wants transferred
     * ([toTransfer]) up to [transferMax] less what they use of this year's fees. With a [rate], the
     * credit is at that rate on its own (Quebec), otherwise one of the credit amounts. The claim
     * fills in [used] and [transferred] once the tax it must bring to zero is known.
     */
    private class Tuition(
        val carried: BigDecimal, val current: BigDecimal, val received: BigDecimal,
        val toTransfer: BigDecimal, val transferMax: BigDecimal, val rate: BigDecimal? = null,
    ) {
        var used: BigDecimal = BigDecimal.ZERO
        var transferred: BigDecimal = BigDecimal.ZERO

        /**
         * The student claims what is needed to bring the tax to zero ([needed]), the amounts
         * carried forward first; of this year's fees left over, up to the maximum less what they
         * used of them can be transferred. Returns the amount claimed.
         */
        fun claim(needed: BigDecimal): BigDecimal {
            used = (carried + current).min(needed.max(BigDecimal.ZERO)).setScale(2, RoundingMode.HALF_UP)
            val usedCurrent = (used - carried).max(BigDecimal.ZERO)
            transferred = toTransfer.min(current - usedCurrent).min((transferMax - usedCurrent).max(BigDecimal.ZERO)).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP)
            return used
        }

        fun carry(kind: CarryKind): CarryForward? {
            if (carried.signum() == 0 && current.signum() == 0) return null
            val available = (carried + current).setScale(2, RoundingMode.HALF_UP)
            return CarryForward(kind, available, used, transferred, available - used - transferred)
        }
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
     * credits; the tax after them, never below zero. A student's [tuition] is claimed only as far
     * as it brings the tax to zero after the other credit amounts; the rest is carried forward or
     * transferred.
     */
    private fun credits(
        part: TaxPart, onIncome: BigDecimal, amounts: List<Pair<TaxLineKind, BigDecimal>>, rate: BigDecimal,
        donationRates: List<BigDecimal>, dividendRates: List<BigDecimal>, gifts: BigDecimal, taxable: BigDecimal, topFrom: BigDecimal,
        eligible: BigDecimal, other: BigDecimal, lines: MutableList<TaxLine>,
        supplemental: List<BigDecimal>? = null, extra: List<TaxLine> = emptyList(), tuition: Tuition? = null,
    ): BigDecimal {
        var sum = BigDecimal.ZERO
        val all = amounts + listOfNotNull(tuition?.takeIf { it.received.signum() > 0 }?.let { TaxLineKind.TUITION_RECEIVED to it.received })
        for ((kind, amount) in all) {
            val a = money(amount)
            if (a.signum() == 0 && kind != TaxLineKind.BASIC_PERSONAL) continue
            lines += TaxLine(part, kind, a)
            sum += a
        }
        sum = sum.max(BigDecimal.ZERO)
        val extraCredits = extra.fold(BigDecimal.ZERO) { a, l -> a - l.amount }
        // Schedule 11: the tax on taxable income over the lowest rate, less the amounts of lines 30000 to 31800
        // (not medical expenses, transfers, donations or dividends), is what the student must claim first.
        val ownTuition = if (tuition != null && tuition.rate == null && (tuition.carried + tuition.current).signum() > 0) {
            val before = all.filter { (kind, _) -> kind !in AFTER_TUITION }.fold(BigDecimal.ZERO) { a, (_, x) -> a + money(x) }
            tuition.claim(onIncome.divide(rate, 2, RoundingMode.UP) - before)
        } else {
            BigDecimal.ZERO
        }
        if (tuition != null && tuition.rate == null && ownTuition.signum() > 0) {
            lines += TaxLine(part, TaxLineKind.TUITION, money(ownTuition), money(tuition.carried + tuition.current))
            sum += ownTuition
        }
        val credit = money(sum.multiply(rate))
        lines += TaxLine(part, TaxLineKind.CREDIT_AMOUNTS, sum)
        lines += TaxLine(part, TaxLineKind.CREDITS, credit.negate(), sum, rate)
        // The federal top-up credit and Alberta's supplemental credit: a further rate on the credit amounts above a threshold.
        val more = supplemental?.let { (threshold, extraRate) -> money((sum - threshold).max(BigDecimal.ZERO).multiply(extraRate)) } ?: BigDecimal.ZERO
        if (more.signum() > 0) lines += TaxLine(part, TaxLineKind.SUPPLEMENTAL_CREDIT, more.negate(), sum - supplemental!![0], supplemental[1])
        lines += extra
        var others = more + extraCredits
        val donation = donationCredit(donationRates, gifts, taxable, topFrom)
        if (donation.signum() > 0) lines += TaxLine(part, TaxLineKind.DONATIONS, donation.negate(), money(gifts))
        // Quebec's tuition credit (Schedule T): its own rate on the fees, as far as it brings the tax left after the other
        // credits, donations included, to zero.
        if (tuition?.rate != null && (tuition.carried + tuition.current + tuition.received).signum() > 0) {
            val left = (onIncome - credit - others - donation).max(BigDecimal.ZERO)
            val claimed = tuition.claim(left.divide(tuition.rate, 2, RoundingMode.UP)) + tuition.received
            val c = money(claimed.multiply(tuition.rate))
            if (c.signum() > 0) {
                lines += TaxLine(part, TaxLineKind.TUITION, c.negate(), money(claimed), tuition.rate)
                others += c
            }
        }
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

    /** The federal medical threshold: the rate of net income, then the CRA's fixed amount (the Medical rules, shared with the medical report). */
    internal fun federalMedical(on: LocalDate): List<BigDecimal> =
        listOf(Rules.decimal("medical.threshold.rate", on), Rules.decimal("medical.threshold.max", on))

    /** The federal dividend tax credit rates, eligible then other (the Investments rules, shared with the slips). */
    private fun federalDividendCredits(on: LocalDate): List<BigDecimal> =
        listOf(Rules.decimal("dividends.eligible.credit", on), Rules.decimal("dividends.other.credit", on))

    /** Medical expenses above the lesser of a rate of net income and a fixed amount (no fixed amount when only the rate is given). */
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

    /** Credit amounts that come after the tuition amount on the return, so the student claims tuition before them. */
    private val AFTER_TUITION = setOf(TaxLineKind.MEDICAL, TaxLineKind.TUITION_RECEIVED)
}
