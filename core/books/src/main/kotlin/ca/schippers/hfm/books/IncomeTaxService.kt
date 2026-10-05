package ca.schippers.hfm.books

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.invest.SlipKind
import ca.schippers.hfm.calc.rules.RuleException
import ca.schippers.hfm.calc.tax.IncomeTax
import ca.schippers.hfm.calc.tax.TaxEstimate
import ca.schippers.hfm.calc.tax.TaxInput
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import java.math.BigDecimal

/**
 * One figure the estimate uses: the amount from the books ([fromBooks]) and the year-end package
 * items it comes from ([from], empty when the books have no source for it), or the amount entered
 * in its place ([entered]).
 */
data class EstimateFigure(val input: TaxInput, val fromBooks: BigDecimal?, val from: List<PackageItem>, val entered: BigDecimal?) {
    val amount: BigDecimal? get() = entered ?: fromBooks
}

/**
 * One person's income tax estimate for a tax year: the figures it starts from, whether they are 65
 * or older at the end of the year, the province whose rules apply, and the [estimate] (null when
 * the rates for the year are missing). [householdMedical] is the medical expense claim of the
 * household's package, which one spouse claims.
 */
data class PersonTaxEstimate(
    val year: Int,
    val memberId: String,
    val province: Province,
    val age65: Boolean,
    val figures: List<EstimateFigure>,
    val householdMedical: BigDecimal,
    val estimate: TaxEstimate?,
)

/**
 * The income tax estimate per person and tax year, from the year-end package (pay, pensions,
 * investment income, deductions, credits, tax deducted and instalments), with the rates of
 * Rates and rules for the person's province (their own, otherwise the household's). Figures the
 * books cannot know are entered in their place; nothing entered here is saved.
 */
class IncomeTaxService internal constructor(private val books: Books) {

    /** The people an estimate can be made for: adults and adult dependants, then anyone else with a package. */
    fun people(year: Int, pkg: TaxPackage = books.taxPackage.build(year)): List<Member> {
        val members = books.members.list(includeArchived = true)
        val withLines = pkg.people.mapNotNull { it.memberId }.toSet()
        return members.filter { (!it.archived && it.kind != MemberKind.CHILD) || it.id in withLines }
    }

    /**
     * The estimate for [memberId] in [year]. [entered] replaces figures from the books; a spouse's
     * net income, entered, claims the spouse amount. [age65] replaces what the birth date says.
     */
    fun estimate(
        year: Int,
        memberId: String,
        entered: Map<TaxInput, BigDecimal> = emptyMap(),
        age65: Boolean? = null,
        pkg: TaxPackage = books.taxPackage.build(year),
    ): PersonTaxEstimate {
        val member = books.members.list(includeArchived = true).first { it.id == memberId }
        val senior = age65 ?: member.birthDate?.let { year - it.year >= 65 } ?: false
        val province = books.provinceOf(memberId)
        val person = pkg.people.firstOrNull { it.memberId == memberId }
        val figures = figures(year, memberId, person, senior).map { f -> f.copy(entered = entered[f.input]) }
        val inputs = figures.mapNotNull { f -> f.amount?.let { f.input to it } }.toMap()
        val estimate = try {
            IncomeTax.estimate(year, province, inputs, senior)
        } catch (_: RuleException) {
            null
        }
        val medical = pkg.people.firstOrNull { it.memberId == null }?.total(PackageItem.MEDICAL)?.toBigDecimal() ?: BigDecimal.ZERO
        return PersonTaxEstimate(year, memberId, province, senior, figures, medical, estimate)
    }

    private fun figures(year: Int, memberId: String, person: PersonPackage?, age65: Boolean): List<EstimateFigure> {
        fun of(vararg items: PackageItem): Pair<BigDecimal?, List<PackageItem>> {
            val used = items.filter { item -> person?.lines?.any { it.item == item } == true }
            return (if (used.isEmpty()) null else used.fold(BigDecimal.ZERO) { a, i -> a + person!!.total(i).toBigDecimal() }) to used
        }
        // Dividends: the eligible and other taxable amounts, from the investment income report's slips.
        val slips = books.taxSlips.report(year).people.firstOrNull { it.member?.id == memberId }?.slips.orEmpty()
        fun box(kind: SlipKind, code: String) = slips.filter { it.kind == kind }.mapNotNull { it.boxes[code] }.fold(Money.zero(Currency.CAD)) { a, m -> a + m }.toBigDecimal()
        val eligible = box(SlipKind.T5, "25") + box(SlipKind.T3, "50")
        val other = box(SlipKind.T5, "11") + box(SlipKind.T3, "32")
        val dividends = listOf(PackageItem.TAXABLE_DIVIDENDS).filter { person?.lines?.any { l -> l.item == it } == true }

        val rrif = if (age65) TaxInput.PENSION else TaxInput.OTHER_INCOME
        val sources = mapOf(
            TaxInput.EMPLOYMENT to of(PackageItem.EMPLOYMENT_INCOME),
            TaxInput.PENSION to if (rrif == TaxInput.PENSION) of(PackageItem.OTHER_PENSIONS, PackageItem.RRIF_INCOME) else of(PackageItem.OTHER_PENSIONS),
            TaxInput.OTHER_INCOME to if (rrif == TaxInput.OTHER_INCOME) {
                of(PackageItem.OAS_PENSION, PackageItem.CPP_QPP_BENEFITS, PackageItem.EI_BENEFITS, PackageItem.RRIF_INCOME)
            } else {
                of(PackageItem.OAS_PENSION, PackageItem.CPP_QPP_BENEFITS, PackageItem.EI_BENEFITS)
            },
            TaxInput.INTEREST to of(PackageItem.INTEREST),
            TaxInput.ELIGIBLE_DIVIDENDS to ((if (dividends.isEmpty()) null else eligible) to dividends),
            TaxInput.OTHER_DIVIDENDS to ((if (dividends.isEmpty()) null else other) to dividends),
            TaxInput.TAXABLE_CAPITAL_GAINS to of(PackageItem.TAXABLE_CAPITAL_GAINS),
            TaxInput.BUSINESS to of(PackageItem.BUSINESS_INCOME, PackageItem.BUSINESS_EXPENSES).let { (_, used) ->
                val net = person?.let { it.total(PackageItem.BUSINESS_INCOME) - it.total(PackageItem.BUSINESS_EXPENSES) }?.toBigDecimal()
                (if (used.isEmpty()) null else net) to used
            },
            TaxInput.RRSP to of(PackageItem.RRSP_CONTRIBUTIONS),
            TaxInput.FHSA to of(PackageItem.FHSA_CONTRIBUTIONS),
            TaxInput.PENSION_PLAN to of(PackageItem.PENSION_PLAN_CONTRIBUTIONS),
            TaxInput.UNION_DUES to of(PackageItem.UNION_DUES),
            TaxInput.CHILD_CARE to of(PackageItem.CHILD_CARE),
            TaxInput.OTHER_DEDUCTIONS to of(PackageItem.MOVING, PackageItem.EMPLOYMENT_EXPENSES),
            TaxInput.CPP_QPP to of(PackageItem.CPP_QPP_CONTRIBUTIONS),
            TaxInput.EI_QPIP to of(PackageItem.EI_PREMIUMS, PackageItem.EI_QPIP_PREMIUMS),
            TaxInput.DONATIONS to of(PackageItem.DONATIONS),
            TaxInput.TAX_DEDUCTED to of(PackageItem.INCOME_TAX_DEDUCTED),
            TaxInput.INSTALMENTS to of(PackageItem.INSTALMENTS),
        )
        return TaxInput.entries.map { i ->
            val (amount, from) = sources[i] ?: (null to emptyList())
            EstimateFigure(i, amount, from, null)
        }
    }
}
