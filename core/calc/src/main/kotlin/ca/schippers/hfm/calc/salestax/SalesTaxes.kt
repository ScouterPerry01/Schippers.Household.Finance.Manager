package ca.schippers.hfm.calc.salestax

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.rules.Rules
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** The kinds of sales tax, named as receipts and the books name them. */
enum class SalesTaxKind { GST, HST, QST, PST }

/**
 * A sales tax in effect: its [kind], the [label] a receipt or invoice gives it (GST, HST, QST, PST,
 * or RST for Manitoba's and Ontario's retail sales tax), its [rate] as a fraction (0.09975 is
 * 9.975 %), and whether it is charged on the price plus the GST ([onGst], Quebec before 2013).
 */
data class SalesTaxRate(val kind: SalesTaxKind, val label: String, val rate: BigDecimal, val onGst: Boolean = false)

/** A sales tax and its amount, as worked out by [SalesTaxes]. */
data class SalesTaxAmount(val tax: SalesTaxRate, val amount: BigDecimal)

/**
 * Sales taxes by date and province or territory, from the rules `sales.gst`, `sales.hst`,
 * `sales.pst`, `sales.qst` and `sales.qstOnGst` (Rates and rules), so the household's own values
 * apply as soon as they are set.
 */
object SalesTaxes {

    private val MC = MathContext.DECIMAL64

    /**
     * The sales taxes charged on [on] in [province]: the HST where it applies, otherwise the GST,
     * then the province's own sales tax (QST, PST or RST) when it has one. Taxes at 0 are left out.
     */
    fun ratesOn(on: LocalDate, province: Province): List<SalesTaxRate> {
        val result = ArrayList<SalesTaxRate>()
        val hst = positive(Rules.decimalOrNull("sales.hst", on, province))
        if (hst != null) {
            result += SalesTaxRate(SalesTaxKind.HST, "HST", hst)
        } else {
            positive(Rules.decimalOrNull("sales.gst", on))?.let { result += SalesTaxRate(SalesTaxKind.GST, "GST", it) }
        }
        if (province == Province.QC) {
            positive(Rules.decimalOrNull("sales.qst", on))?.let {
                result += SalesTaxRate(SalesTaxKind.QST, "QST", it, Rules.valueOn("sales.qstOnGst", on)?.value == "true")
            }
        } else if (hst == null) {
            positive(Rules.decimalOrNull("sales.pst", on, province))?.let { result += SalesTaxRate(SalesTaxKind.PST, provincialLabel(province), it) }
        }
        return result
    }

    /** What [province]'s own sales tax is called: QST in Quebec, RST in Manitoba and Ontario, PST elsewhere. */
    fun provincialLabel(province: Province): String = when (province) {
        Province.QC -> "QST"
        Province.MB, Province.ON -> "RST"
        else -> "PST"
    }

    /** Every rate each kind of sales tax has on [on] somewhere in Canada, to check a receipt's taxes against. */
    fun ratesAnywhere(on: LocalDate): Map<SalesTaxKind, Set<BigDecimal>> =
        Province.entries.flatMap { ratesOn(on, it) }.groupBy({ it.kind }, { it.rate.stripTrailingZeros() }).mapValues { (_, v) -> v.toSet() }

    /**
     * The taxes on [price] (before taxes), each rounded to [scale] decimals; a tax charged on the
     * price plus the GST ([SalesTaxRate.onGst]) is worked out on both.
     */
    fun taxesOn(price: BigDecimal, rates: List<SalesTaxRate>, scale: Int = 2): List<SalesTaxAmount> {
        val federal = rates.filter { !it.onGst && (it.kind == SalesTaxKind.GST || it.kind == SalesTaxKind.HST) }
            .fold(BigDecimal.ZERO) { a, r -> a + price.multiply(r.rate) }
        return rates.map { r ->
            val base = if (r.onGst) price + federal else price
            SalesTaxAmount(r, base.multiply(r.rate).setScale(scale, RoundingMode.HALF_UP))
        }
    }

    /**
     * Splits [total], an amount that includes [rates], into its taxes: the price before taxes is
     * the total divided by one plus the taxes, and each tax is worked out on it, rounded to
     * [scale] decimals. The sign of [total] is ignored; the amounts are positive.
     */
    fun fromTotal(total: BigDecimal, rates: List<SalesTaxRate>, scale: Int = 2): List<SalesTaxAmount> {
        if (rates.isEmpty()) return emptyList()
        val federal = rates.filter { !it.onGst && (it.kind == SalesTaxKind.GST || it.kind == SalesTaxKind.HST) }.fold(BigDecimal.ZERO) { a, r -> a + r.rate }
        val factor = rates.fold(BigDecimal.ONE) { a, r -> a + if (r.onGst) r.rate.multiply(BigDecimal.ONE + federal) else r.rate }
        val price = total.abs().divide(factor, MC)
        return taxesOn(price, rates, scale)
    }

    private fun positive(value: BigDecimal?): BigDecimal? = value?.takeIf { it.signum() > 0 }
}
