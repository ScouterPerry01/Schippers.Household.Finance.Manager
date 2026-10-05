package ca.schippers.hfm.sync

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * BUD-04: which budget alerts the phone should show: a category whose spending this month reached
 * 80 % of its budget, or 100 %, each once a month. [key] is remembered so an alert is not repeated.
 */
object BudgetAlerts {

    data class Alert(val budget: RefBudget, val categoryName: String, val level: Int, val key: String) {
        /** The keys to remember: reaching 100 % also counts as having passed 80 %. */
        val keys: List<String> get() = if (level == 100) listOf(key, key.removeSuffix("|100") + "|80") else listOf(key)
    }

    fun due(reference: ReferenceData, month: YearMonth, zone: ZoneId, french: Boolean, shown: Set<String>): List<Alert> {
        // Figures from another month (the phone has not synced since) are not this month's spending.
        if (YearMonth.from(Instant.ofEpochMilli(reference.generatedAtMillis).atZone(zone)) != month) return emptyList()
        val names = reference.categories.associate { it.id to if (french) it.nameFr else it.nameEn }
        return reference.budgets.mapNotNull { b ->
            val budget = b.budget.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return@mapNotNull null
            val spent = b.spent.toBigDecimalOrNull()?.abs() ?: return@mapNotNull null
            val ratio = spent.divide(budget, 4, RoundingMode.HALF_UP)
            val level = when {
                ratio >= BigDecimal.ONE -> 100
                ratio >= BigDecimal("0.8") -> 80
                else -> return@mapNotNull null
            }
            val key = "b|$month|${b.categoryId ?: b.categoryName}|$level"
            if (key in shown) null else Alert(b, b.categoryId?.let(names::get) ?: b.categoryName, level, key)
        }
    }
}
