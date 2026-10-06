package ca.schippers.hfm.calc.tax

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.rules.Bracket
import ca.schippers.hfm.calc.rules.Rules
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Where a figure of the estimate belongs on the return; [FAMILY] is the household situation the
 * credits and benefits depend on, [CARRY_FORWARD] the balances from earlier years that the notice
 * of assessment gives.
 */
enum class TaxInputGroup { INCOME, DEDUCTIONS, CREDITS, FAMILY, CARRY_FORWARD, PAYMENTS }

/**
 * A figure the income tax estimate starts from, in dollars for the tax year. Dividends are the
 * taxable (grossed-up) amounts, as on the slips; [SPOUSE_NET_INCOME] is left out when there is no
 * spouse or common-law partner (and then the person counts as single), and [RRSP_LIMIT] when the
 * deduction limit is not known. A [count] is a number rather than dollars, a [flag] a yes or no (1 or
 * 0); a [quebec] figure only matters to a Quebec resident. A figure that is not [summed] is not added
 * into total income or the deductions of net income: it is a deduction of taxable income, or a part of
 * other figures that some calculation treats apart.
 */
enum class TaxInput(
    val group: TaxInputGroup,
    val count: Boolean = false,
    val quebec: Boolean = false,
    val flag: Boolean = false,
    val summed: Boolean = true,
) {
    EMPLOYMENT(TaxInputGroup.INCOME),

    /** Pension income that qualifies for the pension income amount (an employer pension; a RRIF or annuity from 65). */
    PENSION(TaxInputGroup.INCOME),

    /** The Old Age Security pension received (before any amount withheld for the recovery tax). */
    OAS(TaxInputGroup.INCOME),

    /** CPP or QPP benefits, EI benefits, plan withdrawals and other taxable income. */
    OTHER_INCOME(TaxInputGroup.INCOME),
    INTEREST(TaxInputGroup.INCOME),
    ELIGIBLE_DIVIDENDS(TaxInputGroup.INCOME),
    OTHER_DIVIDENDS(TaxInputGroup.INCOME),
    TAXABLE_CAPITAL_GAINS(TaxInputGroup.INCOME),

    /** Self-employment income less its expenses; may be negative. */
    BUSINESS(TaxInputGroup.INCOME),

    /**
     * Capital gains on gifts of publicly listed securities (Form T1170): not in income (their
     * inclusion rate is zero), but 30 % of them count for the minimum tax.
     */
    DONATED_SECURITIES_GAINS(TaxInputGroup.INCOME, summed = false),

    /**
     * Of total income, what Quebec's health services fund leaves out besides employment income, the
     * OAS pension and the dividend gross-up (Schedule F, lines 20 to 33): support received, social
     * assistance, income replacement indemnities and net federal supplements, scholarships,
     * profit-sharing allocations, a spousal RRSP recovery.
     */
    FSS_EXEMPT_INCOME(TaxInputGroup.INCOME, quebec = true, summed = false),
    RRSP(TaxInputGroup.DEDUCTIONS),
    FHSA(TaxInputGroup.DEDUCTIONS),
    PENSION_PLAN(TaxInputGroup.DEDUCTIONS),
    UNION_DUES(TaxInputGroup.DEDUCTIONS),
    CHILD_CARE(TaxInputGroup.DEDUCTIONS),
    OTHER_DEDUCTIONS(TaxInputGroup.DEDUCTIONS),

    /** Security options deductions (line 24900; T4 boxes 39, 41, 91 and 92), from net income to taxable income. */
    SECURITY_OPTIONS_DEDUCTION(TaxInputGroup.DEDUCTIONS, summed = false),

    /** Of [SECURITY_OPTIONS_DEDUCTION], the deduction for option shares given to a charity (paragraph 110(1)(d.01)). */
    SECURITY_OPTIONS_GIFTS(TaxInputGroup.DEDUCTIONS, summed = false),

    /** Quebec's security option deduction (line 297, point 02), when it differs from the federal one; left out, the federal one applies. */
    SECURITY_OPTIONS_DEDUCTION_QC(TaxInputGroup.DEDUCTIONS, quebec = true, summed = false),

    /** The capital gains deduction claimed (lifetime capital gains exemption; line 25400, Quebec line 292), at most the taxable capital gains. */
    CAPITAL_GAINS_DEDUCTION(TaxInputGroup.DEDUCTIONS, summed = false),

    /**
     * Of the deductions, those that also reduce the income subject to Quebec's health services fund
     * (Schedule F, lines 41 to 62): support paid, carrying charges, a business investment loss,
     * retirement income transferred to a spouse, repayments of amounts received.
     */
    FSS_DEDUCTIONS(TaxInputGroup.DEDUCTIONS, quebec = true, summed = false),

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

    /** 1 when the person is eligible for the disability tax credit (Form T2201 approved): the disability amount and the workers benefit's disability supplement. */
    DISABILITY(TaxInputGroup.CREDITS, flag = true),
    SPOUSE_NET_INCOME(TaxInputGroup.FAMILY),

    /** The spouse's or partner's working income (employment and self-employment), for the Canada workers benefit. */
    SPOUSE_WORKING_INCOME(TaxInputGroup.FAMILY),

    /** 1 when the spouse or partner is also eligible for the disability tax credit: each spouse's disability supplement is then reduced at half the rate. */
    SPOUSE_DISABILITY(TaxInputGroup.FAMILY, flag = true),

    /** Children under 18 living with the person on December 31. */
    CHILDREN(TaxInputGroup.FAMILY, count = true),

    /** Of [CHILDREN], those under 6. */
    CHILDREN_UNDER_6(TaxInputGroup.FAMILY, count = true),

    /** Months of the year covered by Quebec's public prescription drug insurance plan (RAMQ); 12 when left out. */
    DRUG_PLAN_MONTHS(TaxInputGroup.FAMILY, count = true, quebec = true),

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

    /** Federal minimum tax of the seven previous years that can still be recovered. */
    AMT_CARRIED(TaxInputGroup.CARRY_FORWARD),

    /** Quebec minimum tax (additional income tax) of the seven previous years that can still be recovered (TP-776.42-V, line 49). */
    AMT_CARRIED_QC(TaxInputGroup.CARRY_FORWARD, quebec = true),
    TAX_DEDUCTED(TaxInputGroup.PAYMENTS),
    INSTALMENTS(TaxInputGroup.PAYMENTS),
}

/**
 * The part of the estimate a line belongs to: [OTHER] amounts on the return are added to the
 * balance (the OAS recovery tax, Quebec's contributions); [REFUNDABLE] credits come off it even
 * below zero; [BENEFITS] are paid outside the return, from the July after the year, and are not in
 * the balance.
 */
enum class TaxPart { INCOME, FEDERAL, PROVINCIAL, OTHER, REFUNDABLE, BENEFITS }

/** What a line of the estimate is; the apps name it and explain it in the user's language. */
enum class TaxLineKind {
    TOTAL_INCOME, CPP_ENHANCED, RRSP_DEDUCTION, DEDUCTIONS, OAS_DEDUCTION, NET_INCOME, WORKER_DEDUCTION, CAPITAL_LOSSES,
    SECURITY_OPTIONS, CAPITAL_GAINS_DEDUCTION, TAXABLE_INCOME,
    BRACKET, TAX_ON_INCOME,
    BASIC_PERSONAL, AGE, SENIOR_SUPPLEMENT, SPOUSE, EMPLOYMENT_AMOUNT, CPP, EI, PENSION, MEDICAL, AGE_PENSION_REDUCTION,
    TUITION, TUITION_RECEIVED, DISABILITY,
    BEFORE_REDUCTION, INCOME_REDUCTION, CWB, CWB_DISABILITY, MEDICAL_SUPPLEMENT, WORK_PREMIUM, QC_MEDICAL_CREDIT, REFUNDABLE_TOTAL,
    GST_CREDIT, CHILD_BENEFIT,
    CREDIT_AMOUNTS, CREDITS, SUPPLEMENTAL_CREDIT, DONATIONS, DIVIDENDS, BASIC_TAX,
    ADJUSTED_TAXABLE_INCOME, MINIMUM_TAX, AMT_ADDITIONAL, AMT_CARRYOVER,
    ABATEMENT, SURTAX, TAX_REDUCTION, HEALTH_PREMIUM, TAX, OAS_RECOVERY, HEALTH_FUND, DRUG_PREMIUM, OTHER_TOTAL,
}

/** A balance carried from year to year. */
enum class CarryKind { TUITION_FEDERAL, TUITION_PROVINCIAL, DONATIONS, CAPITAL_LOSSES, RRSP, MINIMUM_TAX, MINIMUM_TAX_QC }

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
    val refundable: BigDecimal = BigDecimal.ZERO,
    val other: BigDecimal = BigDecimal.ZERO,
) {
    val totalTax: BigDecimal get() = federalTax + provincialTax
    val balance: BigDecimal get() = totalTax + other - refundable - paid
}

/**
 * Estimates a person's federal and provincial or territorial income tax from their year's figures,
 * with the rates in effect on January 1 of the year (Rates and rules, area incometax): brackets,
 * the basic personal, age, spouse, Canada employment, pension income, CPP or QPP, EI and medical
 * expense amounts, the disability amount, tuition and its transfer, the donation and dividend tax
 * credits, the security options and capital gains deductions, the balances carried forward
 * (tuition, donations, net capital losses, RRSP contributions, minimum tax), Ontario's surtax and
 * health premium, the alternative minimum tax (with donated securities, security options and the
 * capital gains deduction), the OAS recovery tax, the refundable credits (workers benefit and its
 * disability supplement, medical expense supplement), and for Quebec residents the federal
 * abatement, Quebec's own tax, minimum tax and its carryover, work premium and refundable medical credit,
 * health services fund contribution and drug insurance premium. The GST/HST credit and the Canada
 * child benefit are shown apart. It leaves out what the books cannot know or that is rarely needed
 * (other low-income reductions and refundable credits, political contributions, foreign tax
 * credits); it is an estimate, not a return.
 */
object IncomeTax {

    private val HUNDRED = BigDecimal(100)

    /** Throws [ca.schippers.hfm.calc.rules.RuleException] when a rate the estimate needs has no value for [year]. */
    fun estimate(year: Int, province: Province, inputs: Map<TaxInput, BigDecimal>, age65: Boolean): TaxEstimate {
        val result = compute(year, province, inputs, age65)
        // The marginal rate: the tax on $100 more of ordinary income.
        val more = compute(year, province, inputs + (TaxInput.OTHER_INCOME to (inputs[TaxInput.OTHER_INCOME] ?: BigDecimal.ZERO) + HUNDRED), age65)
        val marginal = (more.totalTax + more.other - result.totalTax - result.other).divide(HUNDRED, 4, RoundingMode.HALF_UP)
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
        val total = TaxInput.entries.filter { it.group == TaxInputGroup.INCOME && it.summed }.fold(BigDecimal.ZERO) { a, i -> a + v(i) }

        // RRSP: this year's contributions and the unused ones of earlier years, within the deduction limit when it is known.
        val rrspClaim = p(TaxInput.RRSP) + p(TaxInput.RRSP_UNUSED)
        val rrspLimit = inputs[TaxInput.RRSP_LIMIT]?.max(BigDecimal.ZERO)
        val rrsp = rrspLimit?.let { rrspClaim.min(it) } ?: rrspClaim
        val deductions = TaxInput.entries.filter { it.group == TaxInputGroup.DEDUCTIONS && it != TaxInput.RRSP && it.summed }.fold(BigDecimal.ZERO) { a, i -> a + p(i) } + rrsp + cppDeduction
        val beforeRecovery = (total - deductions).max(BigDecimal.ZERO)
        // The OAS recovery tax: a rate of net income above a threshold, at most the OAS received; deducted from net income.
        val (oasThreshold, oasRate) = Rules.list("tax.oasRecovery", on)
        val recovery = money((beforeRecovery - oasThreshold).max(BigDecimal.ZERO).multiply(oasRate).min(p(TaxInput.OAS)))
        val net = beforeRecovery - recovery
        lines += TaxLine(TaxPart.INCOME, TaxLineKind.TOTAL_INCOME, money(total))
        if (cppDeduction.signum() > 0) lines += TaxLine(TaxPart.INCOME, TaxLineKind.CPP_ENHANCED, money(cppDeduction))
        if (p(TaxInput.RRSP_UNUSED).signum() > 0 || rrspLimit != null) {
            lines += TaxLine(TaxPart.INCOME, TaxLineKind.RRSP_DEDUCTION, money(rrsp), money(rrspClaim))
            carry += CarryForward(CarryKind.RRSP, money(rrspClaim), money(rrsp), BigDecimal.ZERO.setScale(2), money(rrspClaim - rrsp))
        }
        lines += TaxLine(TaxPart.INCOME, TaxLineKind.DEDUCTIONS, money(deductions))
        if (recovery.signum() > 0) lines += TaxLine(TaxPart.INCOME, TaxLineKind.OAS_DEDUCTION, recovery.negate(), money(beforeRecovery - oasThreshold), oasRate, money(oasThreshold))
        lines += TaxLine(TaxPart.INCOME, TaxLineKind.NET_INCOME, money(net))

        // Net capital losses of other years, against this year's taxable capital gains (taxable income, not net income).
        val lossesCarried = p(TaxInput.CAPITAL_LOSSES_CARRIED)
        val losses = lossesCarried.min(p(TaxInput.TAXABLE_CAPITAL_GAINS)).min(net)
        if (lossesCarried.signum() > 0) {
            lines += TaxLine(TaxPart.INCOME, TaxLineKind.CAPITAL_LOSSES, money(losses).negate(), money(p(TaxInput.TAXABLE_CAPITAL_GAINS)))
            carry += CarryForward(CarryKind.CAPITAL_LOSSES, money(lossesCarried), money(losses), BigDecimal.ZERO.setScale(2), money(lossesCarried - losses))
        }
        // Division C deductions: security options (line 24900) and the capital gains deduction (line 25400), at most
        // the taxable capital gains left after the losses.
        val options = p(TaxInput.SECURITY_OPTIONS_DEDUCTION).min(net - losses)
        if (options.signum() > 0) lines += TaxLine(TaxPart.INCOME, TaxLineKind.SECURITY_OPTIONS, money(options).negate())
        val lcge = p(TaxInput.CAPITAL_GAINS_DEDUCTION).min(p(TaxInput.TAXABLE_CAPITAL_GAINS) - losses).min(net - losses - options).max(BigDecimal.ZERO)
        if (lcge.signum() > 0) lines += TaxLine(TaxPart.INCOME, TaxLineKind.CAPITAL_GAINS_DEDUCTION, money(lcge).negate())
        val taxable = net - losses - options - lcge
        lines += TaxLine(TaxPart.INCOME, TaxLineKind.TAXABLE_INCOME, money(taxable))
        val disabled = v(TaxInput.DISABILITY).signum() > 0
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
            if (disabled) add(TaxLineKind.DISABILITY to Rules.decimal("tax.fed.disability", on))
            add(TaxLineKind.MEDICAL to medical(federalMedical(on), v(TaxInput.MEDICAL), net))
        }
        val fedTuition = Tuition(
            p(TaxInput.TUITION_CARRIED), p(TaxInput.TUITION), p(TaxInput.TUITION_RECEIVED).min(Rules.decimal("tax.fed.tuitionTransfer", on)),
            p(TaxInput.TUITION_TO_TRANSFER), Rules.decimal("tax.fed.tuitionTransfer", on),
        )
        val fedCredits = credits(
            TaxPart.FEDERAL, fedOnIncome, fedAmounts, Rules.decimal("tax.fed.creditRate", on),
            Rules.list("tax.fed.donation", on), federalDividendCredits(on), claimedGifts, taxable, fedBrackets.last().from,
            v(TaxInput.ELIGIBLE_DIVIDENDS), v(TaxInput.OTHER_DIVIDENDS), lines,
            supplemental = Rules.valueOn("tax.fed.topUpCredit", on)?.let { Rules.list("tax.fed.topUpCredit", on) },
            tuition = fedTuition,
        )
        fedTuition.carry(CarryKind.TUITION_FEDERAL)?.let { carry += it }

        // Alternative minimum tax (Form T691): shown only when it is more than the regular tax; otherwise the
        // minimum tax carried forward recovers the difference.
        val amt = Rules.list("tax.amt", on)
        val fedAdjusted = adjustedTaxable(taxable, inputs, cppDeduction, amt[4], amt[5], amt[6], BigDecimal.ZERO, Rules.list("tax.amt.gains", on), options, lcge)
        val fedMinimum = money((fedAdjusted - amt[1]).max(BigDecimal.ZERO).multiply(amt[0]) - fedCredits.amountCredit.multiply(amt[2]) - fedCredits.donation.multiply(amt[3]))
            .max(BigDecimal.ZERO)
        var fedBasic = fedCredits.basic
        val amtAdded = (fedMinimum - fedBasic).max(BigDecimal.ZERO)
        val amtCarried = p(TaxInput.AMT_CARRIED)
        var amtUsed = BigDecimal.ZERO
        if (amtAdded.signum() > 0) {
            lines += TaxLine(TaxPart.FEDERAL, TaxLineKind.ADJUSTED_TAXABLE_INCOME, money(fedAdjusted))
            lines += TaxLine(TaxPart.FEDERAL, TaxLineKind.MINIMUM_TAX, fedMinimum, money(fedAdjusted - amt[1]), amt[0], money(amt[1]))
            lines += TaxLine(TaxPart.FEDERAL, TaxLineKind.AMT_ADDITIONAL, amtAdded)
            fedBasic = fedMinimum
        } else if (amtCarried.signum() > 0) {
            amtUsed = money(amtCarried.min(fedBasic - fedMinimum))
            if (amtUsed.signum() > 0) {
                lines += TaxLine(TaxPart.FEDERAL, TaxLineKind.AMT_CARRYOVER, amtUsed.negate(), fedBasic - fedMinimum)
                fedBasic -= amtUsed
            }
        }
        if (amtCarried.signum() > 0 || amtAdded.signum() > 0) {
            carry += CarryForward(CarryKind.MINIMUM_TAX, money(amtCarried + amtAdded), amtUsed, BigDecimal.ZERO.setScale(2), money(amtCarried + amtAdded - amtUsed))
        }
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
            quebec(on, inputs, net, losses, lcge, gifts, spouse, age65, lines, provTuition, cppDeduction, carry)
        } else {
            provincial(on, province, inputs, net, taxable, claimedGifts, cppCredit, spouse, age65, lines, provTuition, amtAdded, amtUsed)
        }
        provTuition.carry(CarryKind.TUITION_PROVINCIAL)?.let { carry += it }
        lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.TAX, provincial)

        // Other amounts on the return, added to the balance.
        var other = BigDecimal.ZERO
        if (recovery.signum() > 0) {
            lines += TaxLine(TaxPart.OTHER, TaxLineKind.OAS_RECOVERY, recovery, money(beforeRecovery - oasThreshold), oasRate, money(oasThreshold))
            other += recovery
        }
        if (quebec) other += quebecContributions(on, inputs, total, net, spouse, familyOf(inputs, spouse), lines)
        if (other.signum() > 0) lines += TaxLine(TaxPart.OTHER, TaxLineKind.OTHER_TOTAL, other)

        val family = familyOf(inputs, spouse)
        val refundable = refundable(on, province, inputs, net, family, lines)
        benefits(on, net, family, lines)

        val ratesFrom = Rules.valueOn("tax.fed.brackets", on)!!.from.year
        val paid = v(TaxInput.TAX_DEDUCTED) + v(TaxInput.INSTALMENTS)
        return TaxEstimate(
            year, province, money(total), money(net), money(taxable), federal, provincial, money(paid), BigDecimal.ZERO, BigDecimal.ZERO, lines, ratesFrom,
            carry.sortedBy { it.kind }, refundable, other,
        )
    }

    private fun familyOf(inputs: Map<TaxInput, BigDecimal>, spouse: BigDecimal?): Family {
        fun count(i: TaxInput) = (inputs[i] ?: BigDecimal.ZERO).max(BigDecimal.ZERO).toInt()
        return Family(spouse, (inputs[TaxInput.SPOUSE_WORKING_INCOME] ?: BigDecimal.ZERO).max(BigDecimal.ZERO), count(TaxInput.CHILDREN), count(TaxInput.CHILDREN_UNDER_6))
    }

    /**
     * Quebec's contributions on the return: the health services fund contribution (Schedule F, line
     * 446), 1 % of the income subject to it above a threshold, up to a first cap, then 1 % above a
     * second threshold up to the maximum. The income subject to it is total income less employment
     * income, the OAS pension, the dividend gross-up and the other income it leaves out (lines 20 to
     * 33, [TaxInput.FSS_EXEMPT_INCOME]), less the deductions of lines 41 to 62
     * ([TaxInput.FSS_DEDUCTIONS]); and the prescription drug
     * insurance premium (Schedule K), a rate of family income above an exemption that depends on
     * the household, up to the year's maximum, for the months covered by the public plan.
     */
    private fun quebecContributions(
        on: LocalDate, inputs: Map<TaxInput, BigDecimal>, total: BigDecimal, net: BigDecimal, spouse: BigDecimal?, family: Family, lines: MutableList<TaxLine>,
    ): BigDecimal {
        fun p(i: TaxInput) = (inputs[i] ?: BigDecimal.ZERO).max(BigDecimal.ZERO)
        val amt = Rules.list("tax.amt", on)
        val grossUp = p(TaxInput.ELIGIBLE_DIVIDENDS).multiply(amt[5]) + p(TaxInput.OTHER_DIVIDENDS).multiply(amt[6])
        val base = (total - p(TaxInput.EMPLOYMENT) - p(TaxInput.OAS) - grossUp - p(TaxInput.FSS_EXEMPT_INCOME) - p(TaxInput.FSS_DEDUCTIONS)).max(BigDecimal.ZERO)
        val (first, second, rate, cap, max) = Rules.list("tax.qc.fss", on)
        val fss = when {
            base <= first -> BigDecimal.ZERO
            base <= second -> money((base - first).multiply(rate).min(cap))
            else -> money((cap + (base - second).multiply(rate)).min(max))
        }
        if (fss.signum() > 0) {
            val from = if (base <= second) first else second
            lines += TaxLine(TaxPart.OTHER, TaxLineKind.HEALTH_FUND, fss, money(base - from), rate, money(from))
        }

        val d = Rules.list("tax.qc.drugPremium", on)
        val kids = family.children.coerceAtMost(2)
        val exemption = if (family.couple) d[4 + kids] else d[1 + kids]
        val familyIncome = quebecNet(on, inputs, net) + (spouse ?: BigDecimal.ZERO)
        val excess = (familyIncome - exemption).max(BigDecimal.ZERO)
        val (low, high) = if (family.couple) d[10] to d[11] else d[8] to d[9]
        val formula = excess.min(d[7]).multiply(low) + (excess - d[7]).max(BigDecimal.ZERO).multiply(high)
        val months = (inputs[TaxInput.DRUG_PLAN_MONTHS] ?: BigDecimal(12)).max(BigDecimal.ZERO).min(BigDecimal(12))
        val premium = money(formula.min(d[0]).multiply(months).divide(BigDecimal(12), 10, RoundingMode.HALF_UP))
        if (premium.signum() > 0) lines += TaxLine(TaxPart.OTHER, TaxLineKind.DRUG_PREMIUM, premium, money(excess), null, money(exemption))
        return fss + premium
    }

    /** The household: a spouse's net income (null: no spouse) and working income, the children under 18 and under 6. */
    private class Family(val spouseNet: BigDecimal?, val spouseWorking: BigDecimal, val children: Int, val under6: Int) {
        val couple get() = spouseNet != null
        val ruleSuffix get() = when {
            couple && children > 0 -> "coupleChildren"
            couple -> "couple"
            children > 0 -> "singleParent"
            else -> "single"
        }
        fun income(net: BigDecimal) = net + (spouseNet ?: BigDecimal.ZERO)
    }

    /**
     * Adds a benefit's lines: before the reduction ([gross], with its [base] and [rate]), the
     * reduction ([rateOff] of [income] above [threshold]), and what is left as [kind]. Returns it.
     */
    private fun reducedBenefit(
        part: TaxPart, kind: TaxLineKind, gross: BigDecimal, base: BigDecimal?, rate: BigDecimal?,
        income: BigDecimal, threshold: BigDecimal, rateOff: BigDecimal, lines: MutableList<TaxLine>,
        reduction: BigDecimal = money((income - threshold).max(BigDecimal.ZERO).multiply(rateOff)),
    ): BigDecimal {
        val g = money(gross.max(BigDecimal.ZERO))
        if (g.signum() == 0) return g
        val off = reduction.min(g)
        val left = g - off
        if (off.signum() > 0) {
            lines += TaxLine(part, TaxLineKind.BEFORE_REDUCTION, g, base?.let(::money), rate)
            lines += TaxLine(part, TaxLineKind.INCOME_REDUCTION, off.negate(), money((income - threshold).max(BigDecimal.ZERO)), rateOff, money(threshold))
        }
        if (left.signum() > 0 || off.signum() > 0) lines += TaxLine(part, kind, left, if (off.signum() > 0) null else base?.let(::money), if (off.signum() > 0) null else rate)
        return left
    }

    /**
     * Refundable credits that change the balance: the Canada workers benefit and its disability
     * supplement (Schedule 6, with Quebec's, Alberta's and Nunavut's own parameters), the refundable
     * medical expense supplement,
     * and for Quebec residents the work premium (Schedule P) and the refundable credit for medical
     * expenses. A couple's family credits are shown for the person estimated: one spouse claims them.
     */
    private fun refundable(on: LocalDate, province: Province, inputs: Map<TaxInput, BigDecimal>, net: BigDecimal, family: Family, lines: MutableList<TaxLine>): BigDecimal {
        fun p(i: TaxInput) = (inputs[i] ?: BigDecimal.ZERO).max(BigDecimal.ZERO)
        val part = TaxPart.REFUNDABLE
        val working = p(TaxInput.EMPLOYMENT) + p(TaxInput.BUSINESS)
        var total = BigDecimal.ZERO

        // Canada workers benefit: a rate of family working income above a threshold, up to a maximum, less a rate of
        // adjusted family net income above another; a couple's lower earner's working income is partly exempt.
        val (threshold, phaseIn, max, start, rateOff) = Rules.list("tax.cwb.${family.ruleSuffix}", on, province)
        val familyWorking = working + if (family.couple) family.spouseWorking else BigDecimal.ZERO
        val exempt = if (family.couple) Rules.decimal("tax.cwb.secondaryEarner", on).min(working.min(family.spouseWorking)) else BigDecimal.ZERO
        val cwbGross = (familyWorking - threshold).max(BigDecimal.ZERO).multiply(phaseIn).min(max)
        total += reducedBenefit(part, TaxLineKind.CWB, cwbGross, familyWorking - threshold, phaseIn, family.income(net) - exempt, start, rateOff, lines)

        // Its disability supplement (Schedule 6, Step 3), for a person eligible for the disability tax credit: a rate of their own
        // working income above a threshold, up to a maximum, less a rate of the same adjusted family net income above a start that
        // depends on the household; at half the rate when both spouses are eligible, each claiming their own.
        if (p(TaxInput.DISABILITY).signum() > 0) {
            val d = Rules.list("tax.cwb.disability", on, province)
            val rate = if (family.couple) d[2] else d[1]
            val from = when {
                family.couple && family.children > 0 -> d[7]
                family.couple -> d[5]
                family.children > 0 -> d[6]
                else -> d[4]
            }
            val off = if (family.couple && p(TaxInput.SPOUSE_DISABILITY).signum() > 0) d[9] else d[8]
            val gross = (working - d[0]).max(BigDecimal.ZERO).multiply(rate).min(d[3])
            total += reducedBenefit(part, TaxLineKind.CWB_DISABILITY, gross, (working - d[0]).max(BigDecimal.ZERO), rate, family.income(net) - exempt, from, off, lines)
        }

        // Refundable medical expense supplement: a rate of the medical expenses claimed, for a person with enough earned income.
        val (supMax, minEarned, supThreshold, supRate, supOff) = Rules.list("tax.fed.medicalSupplement", on)
        val earned = p(TaxInput.EMPLOYMENT) - p(TaxInput.PENSION_PLAN) - p(TaxInput.UNION_DUES) - p(TaxInput.OTHER_DEDUCTIONS) + p(TaxInput.BUSINESS)
        val fedClaim = medical(federalMedical(on), p(TaxInput.MEDICAL), net)
        if (earned >= minEarned) {
            total += reducedBenefit(part, TaxLineKind.MEDICAL_SUPPLEMENT, fedClaim.multiply(supRate).min(supMax), fedClaim, supRate, family.income(net), supThreshold, supOff, lines)
        }

        if (province == Province.QC) {
            val qcNet = quebecNet(on, inputs, net)
            val qcFamily = family.income(qcNet)
            // The work premium: a rate (by household) of work income above an exclusion, up to a ceiling, less a rate of
            // family income above that ceiling.
            val w = Rules.list("tax.qc.workPremium", on)
            val (exclusion, ceiling) = if (family.couple) w[1] to w[3] else w[0] to w[2]
            val rate = when {
                family.children == 0 -> w[4]
                family.couple -> w[6]
                else -> w[5]
            }
            val work = familyWorking.min(ceiling)
            total += reducedBenefit(part, TaxLineKind.WORK_PREMIUM, (work - exclusion).max(BigDecimal.ZERO).multiply(rate), (work - exclusion).max(BigDecimal.ZERO), rate, qcFamily, ceiling, w[7], lines)
            // The refundable credit for medical expenses: a rate of the expenses above 3 % of family income, for a person with enough work income.
            val (qMax, qMinWork, qThreshold, qRate, qOff) = Rules.list("tax.qc.medicalRefund", on)
            val (floorRate, _) = Rules.list("tax.qc.medical", on)
            val qcClaim = medical(listOf(floorRate), p(TaxInput.MEDICAL), qcFamily)
            if (working >= qMinWork) {
                total += reducedBenefit(part, TaxLineKind.QC_MEDICAL_CREDIT, qcClaim.multiply(qRate).min(qMax), qcClaim, qRate, qcFamily, qThreshold, qOff, lines)
            }
        }
        if (total.signum() > 0) lines += TaxLine(part, TaxLineKind.REFUNDABLE_TOTAL, money(total))
        return money(total)
    }

    /**
     * Benefits paid outside the return from the July after the year, on the year's family net
     * income: the GST/HST credit (the Canada Groceries and Essentials Benefit from July 2026) and
     * the Canada child benefit. Shown apart; they are not in the balance.
     */
    private fun benefits(on: LocalDate, net: BigDecimal, family: Family, lines: MutableList<TaxLine>) {
        val part = TaxPart.BENEFITS
        val income = family.income(net)
        val g = Rules.list("tax.gstCredit", on)
        val (adult, child, single, singleFrom, singleRate) = g
        val (threshold, rateOff) = g[5] to g[6]
        var gross = adult + (if (family.couple) adult else BigDecimal.ZERO) + child.multiply(BigDecimal(family.children))
        // A single parent gets the adult amount for the first child, and every single person the supplement, phased in.
        if (!family.couple && family.children > 0) gross += adult - child
        if (!family.couple) gross += single.min((net - singleFrom).max(BigDecimal.ZERO).multiply(singleRate))
        reducedBenefit(part, TaxLineKind.GST_CREDIT, gross, null, null, income, threshold, rateOff, lines)

        if (family.children > 0) {
            val c = Rules.list("tax.ccb", on)
            val under6 = family.under6.coerceIn(0, family.children)
            val ccbGross = c[0].multiply(BigDecimal(under6)) + c[1].multiply(BigDecimal(family.children - under6))
            val i = family.children.coerceAtMost(4) - 1
            val (t1, t2) = c[2] to c[3]
            val reduction = when {
                income <= t1 -> BigDecimal.ZERO
                income <= t2 -> (income - t1).multiply(c[4 + i])
                else -> (t2 - t1).multiply(c[4 + i]) + (income - t2).multiply(c[8 + i])
            }
            reducedBenefit(part, TaxLineKind.CHILD_BENEFIT, ccbGross, null, null, income, t1, c[4 + i], lines, money(reduction))
        }
    }

    /** Quebec net income: net income less the deduction for workers. */
    private fun quebecNet(on: LocalDate, inputs: Map<TaxInput, BigDecimal>, net: BigDecimal): BigDecimal {
        val (workerRate, workerMax) = Rules.list("tax.qc.workerDeduction", on)
        val worker = money((inputs[TaxInput.EMPLOYMENT] ?: BigDecimal.ZERO).max(BigDecimal.ZERO).multiply(workerRate).min(workerMax))
        return (net - worker).max(BigDecimal.ZERO)
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
        spouse: BigDecimal?, age65: Boolean, lines: MutableList<TaxLine>, tuition: Tuition, amtAdded: BigDecimal, amtUsed: BigDecimal,
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
            if (v(TaxInput.DISABILITY).signum() > 0) add(TaxLineKind.DISABILITY to Rules.decimal("tax.prov.disability", on, p))
            add(TaxLineKind.MEDICAL to medical(Rules.list("tax.prov.medical", on, p), v(TaxInput.MEDICAL), net))
        }
        val beforeAmt = credits(
            TaxPart.PROVINCIAL, onIncome, amounts, Rules.decimal("tax.prov.creditRate", on, p),
            Rules.list("tax.prov.donation", on, p), Rules.list("tax.prov.dividends", on, p), gifts, taxable, brackets.last().from,
            v(TaxInput.ELIGIBLE_DIVIDENDS), v(TaxInput.OTHER_DIVIDENDS), lines,
            supplemental = Rules.valueOn("tax.prov.supplementalCredit", on, p)?.let { Rules.list("tax.prov.supplementalCredit", on, p) },
            tuition = tuition,
        ).basic
        // The provincial or territorial share of the federal minimum tax (added, or recovered from earlier years).
        val (amtRate, amtCarryRate) = Rules.list("tax.prov.amt", on, p)
        val provAmt = money(amtAdded.multiply(amtRate))
        if (provAmt.signum() > 0) lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.AMT_ADDITIONAL, provAmt, amtAdded, amtRate)
        val basic = beforeAmt + provAmt
        var tax = basic
        val provRecovered = money(amtUsed.multiply(amtCarryRate)).min(tax)
        if (provRecovered.signum() > 0) {
            lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.AMT_CARRYOVER, provRecovered.negate(), amtUsed, amtCarryRate)
            tax -= provRecovered
        }
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
     * credit. Quebec's minimum tax is added when it is more than the regular tax; otherwise the
     * minimum tax of the seven previous years ([TaxInput.AMT_CARRIED_QC]) recovers the difference.
     */
    private fun quebec(
        on: LocalDate, inputs: Map<TaxInput, BigDecimal>, net: BigDecimal, losses: BigDecimal, lcgeFederal: BigDecimal, gifts: BigDecimal, spouse: BigDecimal?,
        age65: Boolean, lines: MutableList<TaxLine>, tuition: Tuition, cppDeduction: BigDecimal, carry: MutableList<CarryForward>,
    ): BigDecimal {
        fun v(i: TaxInput) = inputs[i] ?: BigDecimal.ZERO
        val p = Province.QC
        val (workerRate, workerMax) = Rules.list("tax.qc.workerDeduction", on)
        val worker = money(v(TaxInput.EMPLOYMENT).max(BigDecimal.ZERO).multiply(workerRate).min(workerMax))
        val qcNet = (net - worker).max(BigDecimal.ZERO)
        if (worker.signum() > 0) lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.WORKER_DEDUCTION, worker, v(TaxInput.EMPLOYMENT), workerRate)
        val qcLosses = losses.min(qcNet)
        if (qcLosses.signum() > 0) lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.CAPITAL_LOSSES, money(qcLosses).negate())
        // Quebec's security option deduction (line 297, point 02; the federal one unless entered) and the capital gains deduction (line 292).
        val qcOptions = (inputs[TaxInput.SECURITY_OPTIONS_DEDUCTION_QC] ?: v(TaxInput.SECURITY_OPTIONS_DEDUCTION)).max(BigDecimal.ZERO).min(qcNet - qcLosses)
        if (qcOptions.signum() > 0) lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.SECURITY_OPTIONS, money(qcOptions).negate())
        val qcLcge = lcgeFederal.min(qcNet - qcLosses - qcOptions).max(BigDecimal.ZERO)
        if (qcLcge.signum() > 0) lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.CAPITAL_GAINS_DEDUCTION, money(qcLcge).negate())
        val qcTaxable = qcNet - qcLosses - qcOptions - qcLcge
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
            if (v(TaxInput.DISABILITY).signum() > 0) add(TaxLineKind.DISABILITY to Rules.decimal("tax.prov.disability", on, p))
            if (ageAmount + pension > BigDecimal.ZERO) {
                val reduction = (family - age[1]).max(BigDecimal.ZERO).multiply(age[2]).min(ageAmount + pension)
                if (reduction.signum() > 0) add(TaxLineKind.AGE_PENSION_REDUCTION to reduction.negate())
            }
        }
        // Quebec's medical expense credit has its own rate, on expenses above a rate of family income.
        val (floorRate, medicalRate) = Rules.list("tax.qc.medical", on)
        val claim = money(medical(listOf(floorRate), v(TaxInput.MEDICAL), family))
        val medicalCredit = if (claim.signum() > 0) listOf(TaxLine(TaxPart.PROVINCIAL, TaxLineKind.MEDICAL, money(claim.multiply(medicalRate)).negate(), claim, medicalRate)) else emptyList()
        val c = credits(
            TaxPart.PROVINCIAL, onIncome, amounts, Rules.decimal("tax.prov.creditRate", on, p),
            Rules.list("tax.prov.donation", on, p), Rules.list("tax.prov.dividends", on, p), qcGifts, qcTaxable, brackets.last().from,
            v(TaxInput.ELIGIBLE_DIVIDENDS), v(TaxInput.OTHER_DIVIDENDS), lines, extra = medicalCredit, tuition = tuition,
        )
        // Quebec's own minimum tax (TP-776.42): its rate and exemption, the deduction for workers half added back,
        // half of the credits other than donations and 80 % of the donation credit.
        val (qRate, qExemption, qShare, qDonationShare, qDeductionShare) = Rules.list("tax.qc.amt", on)
        val fed = Rules.list("tax.amt", on)
        val adjusted = adjustedTaxable(qcTaxable, inputs, cppDeduction, qDeductionShare, fed[5], fed[6], worker, Rules.list("tax.qc.amtGains", on), qcOptions, qcLcge)
        val minimum = money((adjusted - qExemption).max(BigDecimal.ZERO).multiply(qRate) - (c.amountCredit + c.others).multiply(qShare) - c.donation.multiply(qDonationShare))
            .max(BigDecimal.ZERO)
        val carried = (inputs[TaxInput.AMT_CARRIED_QC] ?: BigDecimal.ZERO).max(BigDecimal.ZERO)
        var added = BigDecimal.ZERO
        var used = BigDecimal.ZERO
        var tax = c.basic
        if (minimum > c.basic) {
            added = minimum - c.basic
            lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.ADJUSTED_TAXABLE_INCOME, money(adjusted))
            lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.MINIMUM_TAX, minimum, money(adjusted - qExemption), qRate, money(qExemption))
            lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.AMT_ADDITIONAL, added)
            tax = minimum
        } else if (carried.signum() > 0) {
            // TP-776.42-V, line 50: the least of the tax, the tax less the minimum tax, and the amount carried.
            used = money(carried.min(c.basic - minimum))
            if (used.signum() > 0) {
                lines += TaxLine(TaxPart.PROVINCIAL, TaxLineKind.AMT_CARRYOVER, used.negate(), c.basic - minimum)
                tax -= used
            }
        }
        if (carried.signum() > 0 || added.signum() > 0) {
            carry += CarryForward(CarryKind.MINIMUM_TAX_QC, money(carried + added), used, BigDecimal.ZERO.setScale(2), money(carried + added - used))
        }
        return tax
    }

    /**
     * Adjusted taxable income for the minimum tax: taxable income, plus the other half of the
     * taxable capital gains (gains count in full), plus the share of the deductions allowed only in
     * part (union dues, child care, other deductions, the enhanced CPP or QPP, and [more], such as
     * Quebec's deduction for workers), less the dividends' gross-up (dividends count at their actual
     * amount). With the 2024 rules ([gains]: the share of the gains on donated listed securities
     * added, then the share taken off): that share of the gains on gifts of publicly listed
     * securities; the security [options] deduction added back, less the share taken off of the part
     * for donated option shares; and that share of the capital gains deduction ([lcge]) taken off,
     * so that 30 % of those gains count (T691 lines 24 to 32 and 87; TP-776.42-V lines 146.1, 159.4
     * and 171.1 to 171.5).
     */
    private fun adjustedTaxable(
        taxable: BigDecimal, inputs: Map<TaxInput, BigDecimal>, cppDeduction: BigDecimal, share: BigDecimal,
        eligibleGrossUp: BigDecimal, otherGrossUp: BigDecimal, more: BigDecimal,
        gains: List<BigDecimal>, options: BigDecimal, lcge: BigDecimal,
    ): BigDecimal {
        fun p(i: TaxInput) = (inputs[i] ?: BigDecimal.ZERO).max(BigDecimal.ZERO)
        val (giftShare, offShare) = gains
        val partly = p(TaxInput.UNION_DUES) + p(TaxInput.CHILD_CARE) + p(TaxInput.OTHER_DEDUCTIONS) + cppDeduction + more
        val optionGifts = p(TaxInput.SECURITY_OPTIONS_GIFTS).min(options)
        val optionsBack = (options - optionGifts - optionGifts.multiply(offShare)).max(BigDecimal.ZERO)
        return taxable + p(TaxInput.TAXABLE_CAPITAL_GAINS) + partly.multiply(share) -
            p(TaxInput.ELIGIBLE_DIVIDENDS).multiply(eligibleGrossUp) - p(TaxInput.OTHER_DIVIDENDS).multiply(otherGrossUp) +
            p(TaxInput.DONATED_SECURITIES_GAINS).multiply(giftShare) + optionsBack - lcge.multiply(offShare)
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
    ): Credits {
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
        return Credits(basic, credit, others, donation)
    }

    /**
     * The tax after the non-refundable credits ([basic]), with the parts the minimum tax needs: the
     * credit on the credit amounts, the [others] (top-up or supplemental credit, Quebec's medical
     * and tuition credits) and the [donation] credit.
     */
    private class Credits(val basic: BigDecimal, val amountCredit: BigDecimal, val others: BigDecimal, val donation: BigDecimal)

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
