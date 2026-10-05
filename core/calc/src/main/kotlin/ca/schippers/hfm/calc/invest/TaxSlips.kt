package ca.schippers.hfm.calc.invest

import ca.schippers.hfm.calc.rules.decimalOrEarliest
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode

/** INV-08: the investment tax slips: T5 and T3 everywhere, RL-3 and RL-16 in Quebec (PROV-07). */
enum class SlipKind { T5, T3, RL3, RL16 }

/**
 * Investment income in Canadian dollars, by kind, as it is reported on slips. Dividends are the
 * actual amounts paid, before gross-up.
 */
data class InvestmentIncome(
    val eligibleDividends: BigDecimal = BigDecimal.ZERO,
    val ordinaryDividends: BigDecimal = BigDecimal.ZERO,
    val interest: BigDecimal = BigDecimal.ZERO,
    val otherIncome: BigDecimal = BigDecimal.ZERO,
    val foreignIncome: BigDecimal = BigDecimal.ZERO,
    val foreignTax: BigDecimal = BigDecimal.ZERO,
    /** Capital gains dividends (T5, RL-3) or capital gains allocated by a trust (T3, RL-16). */
    val capitalGains: BigDecimal = BigDecimal.ZERO,
    /** A trust's return of capital (T3 box 42, RL-16 box M), which lowers the adjusted cost base. */
    val returnOfCapital: BigDecimal = BigDecimal.ZERO,
) {
    operator fun plus(o: InvestmentIncome) = InvestmentIncome(
        eligibleDividends + o.eligibleDividends, ordinaryDividends + o.ordinaryDividends, interest + o.interest, otherIncome + o.otherIncome,
        foreignIncome + o.foreignIncome, foreignTax + o.foreignTax, capitalGains + o.capitalGains, returnOfCapital + o.returnOfCapital,
    )

    /** This income shared by [parts] owners, each part rounded to the cent. */
    fun share(parts: Int): InvestmentIncome {
        if (parts <= 1) return this
        val n = BigDecimal(parts)
        fun d(v: BigDecimal) = v.divide(n, 2, RoundingMode.HALF_UP)
        return InvestmentIncome(d(eligibleDividends), d(ordinaryDividends), d(interest), d(otherIncome), d(foreignIncome), d(foreignTax), d(capitalGains), d(returnOfCapital))
    }

    val isZero: Boolean get() = listOf(eligibleDividends, ordinaryDividends, interest, otherIncome, foreignIncome, foreignTax, capitalGains, returnOfCapital).all { it.signum() == 0 }
}

/**
 * The boxes of each slip, from the published slip layouts (CRA T5 and T3; Revenu Québec RL-3 and
 * RL-16), with the dividend gross-up and the federal dividend tax credit for the year (rules of
 * the Rates and rules screen, area "investing"). Quebec's
 * dividend credit (RL-3 box C, RL-16 box J) is left to the return.
 */
object TaxSlips {

    /** Share of a capital gain disposed of on [on] that is taxable (rule capitalgains.inclusion). */
    fun inclusionRate(on: LocalDate): BigDecimal = decimalOrEarliest("capitalgains.inclusion", on)

    /** Eligible dividends: the gross-up factor (1.38 since 2012) and the federal credit on the taxable amount (15.0198 %). */
    fun eligibleGrossUp(year: Int): BigDecimal = decimalOrEarliest("dividends.eligible.grossup", LocalDate(year, 12, 31))

    fun eligibleCredit(year: Int): BigDecimal = decimalOrEarliest("dividends.eligible.credit", LocalDate(year, 12, 31))

    /** Other than eligible (ordinary) dividends: the gross-up factor and federal credit, which changed in 2014 and 2016 to 2019. */
    fun ordinaryGrossUp(year: Int): BigDecimal = decimalOrEarliest("dividends.other.grossup", LocalDate(year, 12, 31))

    fun ordinaryCredit(year: Int): BigDecimal = decimalOrEarliest("dividends.other.credit", LocalDate(year, 12, 31))

    /** The slip's boxes (codes as printed) with their amounts in dollars; empty boxes are left out. */
    fun boxes(kind: SlipKind, income: InvestmentIncome, year: Int): Map<String, BigDecimal> {
        val i = income
        val eligibleTaxable = cents(i.eligibleDividends * eligibleGrossUp(year))
        val ordinaryTaxable = cents(i.ordinaryDividends * ordinaryGrossUp(year))
        val eligibleCredit = cents(eligibleTaxable * eligibleCredit(year))
        val ordinaryCredit = cents(ordinaryTaxable * ordinaryCredit(year))
        val boxes = when (kind) {
            SlipKind.T5 -> listOf(
                "10" to i.ordinaryDividends, "11" to ordinaryTaxable, "12" to ordinaryCredit, "13" to i.interest, "14" to i.otherIncome,
                "15" to i.foreignIncome, "16" to i.foreignTax, "18" to i.capitalGains, "24" to i.eligibleDividends, "25" to eligibleTaxable, "26" to eligibleCredit,
            )
            SlipKind.T3 -> listOf(
                "21" to i.capitalGains, "23" to i.ordinaryDividends, "25" to i.foreignIncome, "26" to i.otherIncome + i.interest, "32" to ordinaryTaxable,
                "34" to i.foreignTax, "39" to ordinaryCredit, "42" to i.returnOfCapital, "49" to i.eligibleDividends, "50" to eligibleTaxable, "51" to eligibleCredit,
            )
            SlipKind.RL3 -> listOf(
                "A1" to i.eligibleDividends, "A2" to i.ordinaryDividends, "B" to eligibleTaxable + ordinaryTaxable, "D" to i.interest, "E" to i.otherIncome,
                "F" to i.foreignIncome, "G" to i.foreignTax, "I" to i.capitalGains,
            )
            SlipKind.RL16 -> listOf(
                "A" to i.capitalGains, "C1" to i.eligibleDividends, "C2" to i.ordinaryDividends, "F" to i.foreignIncome, "G" to i.otherIncome + i.interest,
                "I" to eligibleTaxable + ordinaryTaxable, "L" to i.foreignTax, "M" to i.returnOfCapital,
            )
        }
        return boxes.filter { it.second.signum() != 0 }.associate { it.first to cents(it.second) }
    }

    /** The boxes each slip has, in the order printed, for entering a slip by hand. */
    fun boxCodes(kind: SlipKind): List<String> = when (kind) {
        SlipKind.T5 -> listOf("10", "11", "12", "13", "14", "15", "16", "18", "24", "25", "26")
        SlipKind.T3 -> listOf("21", "23", "25", "26", "32", "34", "39", "42", "49", "50", "51")
        SlipKind.RL3 -> listOf("A1", "A2", "B", "C", "D", "E", "F", "G", "I")
        SlipKind.RL16 -> listOf("A", "C1", "C2", "F", "G", "I", "J", "L", "M")
    }

    /** The Quebec slip that goes with a federal one, and the other way round. */
    fun quebecOf(kind: SlipKind): SlipKind = when (kind) {
        SlipKind.T5, SlipKind.RL3 -> SlipKind.RL3
        SlipKind.T3, SlipKind.RL16 -> SlipKind.RL16
    }

    fun federalOf(kind: SlipKind): SlipKind = when (kind) {
        SlipKind.T5, SlipKind.RL3 -> SlipKind.T5
        SlipKind.T3, SlipKind.RL16 -> SlipKind.T3
    }

    private fun cents(v: BigDecimal) = v.setScale(2, RoundingMode.HALF_UP)
}
