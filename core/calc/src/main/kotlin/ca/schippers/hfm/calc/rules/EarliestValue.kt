package ca.schippers.hfm.calc.rules

import ca.schippers.hfm.calc.Province
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/**
 * The value of [key] in effect on [on] for [province] or, for a date before its first value, the
 * earliest one: for figures the app applies to any year, such as a rate in force long before the
 * years it holds values for.
 */
internal fun valueOrEarliest(key: String, on: LocalDate, province: Province? = null): RuleValue =
    Rules.valueOn(key, on, province)
        ?: Rules.values(key).let { all ->
            (province?.let { p -> all.filter { it.province == p } }.orEmpty().ifEmpty { all.filter { it.province == null } })
                .minWithOrNull(compareBy<RuleValue>({ it.from }, { !it.builtIn }))
        }
        ?: throw RuleException("No value for $key")

internal fun decimalOrEarliest(key: String, on: LocalDate, province: Province? = null): BigDecimal = BigDecimal(valueOrEarliest(key, on, province).value)

internal fun intOrEarliest(key: String, on: LocalDate, province: Province? = null): Int = decimalOrEarliest(key, on, province).toInt()
