package ca.schippers.hfm.desktop

import ca.schippers.hfm.calc.rules.RuleType
import ca.schippers.hfm.money.MoneyFormat
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.Normalizer
import java.util.Locale

/**
 * Rates and rules: what a person types in the value editor, and the text a rule's value is kept
 * as (see [ca.schippers.hfm.calc.rules.Rules]). A rate is typed as a percentage, 9.975, and kept as
 * a fraction, 0.09975; numbers are read in the user's language, so a French decimal comma works.
 */
object RateRuleInput {

    private val HUNDRED = BigDecimal(100)

    /**
     * The stored text for what was typed for a rule of [type], or null when it cannot be read.
     * Brackets are typed as rows: see [storedBrackets].
     */
    fun stored(type: RuleType, typed: String, locale: Locale): String? = runCatching {
        val text = typed.trim()
        when (type) {
            RuleType.RATE -> plain(MoneyFormat.parseDecimal(text.removeSuffix("%").trim(), locale).divide(HUNDRED))
            RuleType.AMOUNT, RuleType.NUMBER -> plain(MoneyFormat.parseDecimal(text, locale))
            RuleType.DAYS -> text.toInt().takeIf { it >= 0 }?.toString()
            RuleType.YES_NO -> text.takeIf { it == "true" || it == "false" }
            RuleType.MONTH_DAY -> text.split('-', '/').map { it.trim().toInt() }.let { (m, d) -> "%02d-%02d".format(m, d) }
            RuleType.LIST -> text.split(';').map { plain(MoneyFormat.parseDecimal(it, locale)) }.joinToString("; ")
            RuleType.BRACKETS -> null
        }
    }.getOrNull()

    /**
     * Brackets typed as rows of a threshold in dollars and a rate in per cent, such as
     * ("58 523", "20,5"), kept as "0:0.15; 58523:0.205". Empty rows are skipped; the rows are put
     * in order of threshold. Null when a row cannot be read or there is none.
     */
    fun storedBrackets(rows: List<Pair<String, String>>, locale: Locale): String? = runCatching {
        rows.filter { (from, rate) -> from.isNotBlank() || rate.isNotBlank() }
            .map { (from, rate) -> MoneyFormat.parseDecimal(from, locale) to MoneyFormat.parseDecimal(rate.trim().removeSuffix("%").trim(), locale).divide(HUNDRED) }
            .sortedBy { it.first }
            .joinToString("; ") { (from, rate) -> "${plain(from)}:${plain(rate)}" }
            .ifEmpty { null }
    }.getOrNull()

    /** A stored value as it is typed in the editor, to start from the value in effect. */
    fun typed(type: RuleType, stored: String, locale: Locale): String = runCatching {
        when (type) {
            RuleType.RATE -> number(BigDecimal(stored).multiply(HUNDRED), locale)
            RuleType.AMOUNT, RuleType.NUMBER -> number(BigDecimal(stored), locale)
            RuleType.LIST -> stored.split(';').joinToString("; ") { number(BigDecimal(it.trim()), locale) }
            else -> stored
        }
    }.getOrDefault(stored)

    /** Stored brackets as rows of the editor: threshold in dollars, rate in per cent. */
    fun typedBrackets(stored: String, locale: Locale): List<Pair<String, String>> = runCatching {
        stored.split(';').map { it.trim().split(':').let { (from, rate) -> number(BigDecimal(from.trim()), locale) to number(BigDecimal(rate.trim()).multiply(HUNDRED), locale) } }
    }.getOrDefault(emptyList())

    /** Whether a rule named [name] with [key] is found by the filter [query], accents and case ignored. */
    fun matches(name: String, key: String, query: String): Boolean {
        val q = fold(query.trim())
        return q.isEmpty() || fold(name).contains(q) || fold(key).contains(q)
    }

    private fun fold(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private fun plain(value: BigDecimal): String = value.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }.toPlainString()

    /** A number in the user's language without grouping, as it is typed: 9,975 in French. */
    private fun number(value: BigDecimal, locale: Locale): String =
        DecimalFormat("0.##########", DecimalFormatSymbols.getInstance(locale)).apply { isParseBigDecimal = true }.format(value.stripTrailingZeros())
}
