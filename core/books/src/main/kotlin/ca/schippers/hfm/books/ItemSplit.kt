package ca.schippers.hfm.books

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** How the taxes of a split receipt were shared out (OCR-03). */
enum class SplitMethod {
    /** Each tax over the lines the receipt marks as subject to it. */
    TAX_CODES,
    /** Every tax over every line, in proportion to its amount. */
    PROPORTIONAL,
}

/** Why a split shares taxes in proportion rather than by the receipt's codes. */
enum class SplitNote {
    /** The taxes followed the receipt's codes. */
    NONE,
    /** The receipt does not show which lines are taxed. */
    NO_CODES,
    /** The marked lines do not account for a printed tax at any Canadian rate, so the codes were not trusted. */
    CODES_DISAGREE,
}

/** OCR-03: an item of a receipt, as printed, its share of the amount paid, and the taxes it was marked with. */
data class ItemShare(val description: String, val printed: Money, val share: Money, val taxes: Set<String> = emptySet())

data class ItemSplit(val shares: List<ItemShare>, val method: SplitMethod, val note: SplitNote)

/** One line of a receipt: what is printed, and the taxes marked on it (null when the receipt does not say). */
data class ReceiptLine(val description: String, val amount: BigDecimal, val taxes: Set<String>?)

/**
 * OCR-03: shares a receipt's amount paid over its items. Where the receipt marks which lines each
 * sales tax applies to, each printed tax is shared over its own lines, in proportion to their
 * amounts, so a zero-rated grocery carries none and a taxable item carries its own; what is left
 * (a tip, another tax, rounding) is shared over every line. The codes are trusted only when each
 * printed tax is what its lines would give at a Canadian rate (GST 5 %, HST 13, 14 or 15 %, QST
 * 9.975 %, PST 6 or 7 %); otherwise, or without codes, every tax is shared over every line.
 * Shares are rounded to the cent so they add up to [total] exactly, the cents left by rounding going
 * to the lines that lost the most.
 */
object ItemSplitter {

    /** Rates in percent each sales tax can have somewhere in Canada. */
    private val RATES = mapOf(
        "GST" to listOf("5"), "HST" to listOf("13", "14", "15"), "QST" to listOf("9.975"), "PST" to listOf("6", "7"),
    ).mapValues { (_, v) -> v.map(::BigDecimal) }

    private val MC = MathContext.DECIMAL64

    fun split(lines: List<ReceiptLine>, printedTaxes: Map<String, BigDecimal>, total: Money): ItemSplit? {
        val sum = lines.fold(BigDecimal.ZERO) { a, l -> a + l.amount }
        if (lines.size < 2 || sum.signum() == 0) return null
        val target = total.toBigDecimal().abs()
        val hasCodes = lines.any { it.taxes != null }
        val marked = lines.map { it.taxes.orEmpty() }
        val trusted = hasCodes && printedTaxes.filterKeys { it in RATES }.filterValues { it.signum() != 0 }.all { (name, amount) ->
            val base = lines.indices.filter { name in marked[it] }.fold(BigDecimal.ZERO) { a, i -> a + lines[i].amount }
            base.signum() > 0 && RATES.getValue(name).any { rate ->
                val expected = base * rate / BigDecimal(100)
                (expected - amount).abs() <= BigDecimal("0.05").max(amount.abs() * BigDecimal("0.03"))
            }
        }
        val exact: List<BigDecimal> = if (trusted) {
            val shares = lines.map { it.amount }.toMutableList()
            for ((name, amount) in printedTaxes.filterKeys { it in RATES }) {
                val idx = lines.indices.filter { name in marked[it] }
                val base = idx.fold(BigDecimal.ZERO) { a, i -> a + lines[i].amount }
                if (base.signum() == 0) continue
                for (i in idx) shares[i] = shares[i] + amount.multiply(lines[i].amount).divide(base, MC)
            }
            // A tip, another tax or the receipt's own rounding: over every line.
            val rest = target - shares.fold(BigDecimal.ZERO, BigDecimal::add)
            shares.mapIndexed { i, s -> s + rest.multiply(lines[i].amount).divide(sum, MC) }
        } else {
            lines.map { target.multiply(it.amount).divide(sum, MC) }
        }
        val rounded = toCents(exact, target, total.currency)
        val shares = lines.mapIndexed { i, l -> ItemShare(l.description, Money.of(l.amount, total.currency), Money.of(rounded[i], total.currency), marked[i]) }
        val note = when {
            trusted -> SplitNote.NONE
            hasCodes -> SplitNote.CODES_DISAGREE
            else -> SplitNote.NO_CODES
        }
        return ItemSplit(shares, if (trusted) SplitMethod.TAX_CODES else SplitMethod.PROPORTIONAL, note)
    }

    /** Rounds each share down to the cent, then gives the cents left to the largest remainders, so the sum is [target]. */
    private fun toCents(exact: List<BigDecimal>, target: BigDecimal, currency: Currency): List<BigDecimal> {
        val scale = currency.minorUnits
        val floors = exact.map { it.setScale(scale, RoundingMode.FLOOR) }
        val cent = BigDecimal.ONE.movePointLeft(scale)
        var left = (target - floors.fold(BigDecimal.ZERO, BigDecimal::add)).divide(cent).toInt()
        val result = floors.toMutableList()
        val order = exact.indices.sortedByDescending { exact[it] - floors[it] }
        var k = 0
        while (left > 0 && order.isNotEmpty()) {
            val i = order[k % order.size]
            result[i] = result[i] + cent
            left--
            k++
        }
        // Arithmetic on very long fractions can leave a cent too many: taken from the smallest remainders.
        while (left < 0 && order.isNotEmpty()) {
            val i = order[order.size - 1 - (k % order.size)]
            result[i] = result[i] - cent
            left++
            k++
        }
        return result
    }
}
