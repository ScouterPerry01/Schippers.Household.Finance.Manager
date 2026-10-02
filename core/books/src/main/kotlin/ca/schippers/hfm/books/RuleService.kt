package ca.schippers.hfm.books

import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money

/**
 * "If the payee contains X (and the amount is between Y and Z), use category C" (CAT-02).
 * Amount limits apply to the size of the amount, whether money in or out.
 */
data class CategoryRule(
    val id: String,
    val payeeContains: String,
    val amountMin: Money?,
    val amountMax: Money?,
    val categoryId: String,
    /** Optionally also file the transaction under this payee. */
    val payeeId: String?,
    val sortOrder: Int,
) {
    fun matches(text: String, amount: Money): Boolean {
        if (!text.contains(payeeContains, ignoreCase = true)) return false
        val size = amount.abs()
        amountMin?.let { if (it.currency != amount.currency || size < it) return false }
        amountMax?.let { if (it.currency != amount.currency || size > it) return false }
        return true
    }
}

class RuleService internal constructor(private val books: Books) {

    fun list(): List<CategoryRule> = books.core.rules().executeAsList().map {
        val currency = it.currency?.let(Currency::of)
        CategoryRule(
            it.id, it.payee_contains,
            it.amount_min_minor?.let { m -> Money.ofMinor(m, currency!!) },
            it.amount_max_minor?.let { m -> Money.ofMinor(m, currency!!) },
            it.category_id, it.payee_id, it.sort_order.toInt(),
        )
    }

    fun create(payeeContains: String, categoryId: String, amountMin: Money? = null, amountMax: Money? = null, payeeId: String? = null): CategoryRule {
        validate(payeeContains.isNotBlank(), "error.nameRequired")
        validate(books.categories.list(includeArchived = true).any { it.id == categoryId }, "error.unknownCategory")
        validate(amountMin == null || amountMax == null || amountMin.currency == amountMax.currency, "error.currencyMismatch", amountMin?.currency?.code ?: "")
        validate(amountMin == null || amountMax == null || amountMin <= amountMax, "error.ruleAmountRange")
        val id = Ids.newId()
        val order = list().size
        val currency = (amountMin ?: amountMax)?.currency?.code
        books.core.insertRule(id, payeeContains.trim(), amountMin?.abs()?.minorUnits, amountMax?.abs()?.minorUnits, currency, categoryId, payeeId, order.toLong())
        books.session.audit("CREATE", "category_rule", id)
        return list().first { it.id == id }
    }

    fun delete(ruleId: String) {
        books.core.deleteRule(ruleId)
        books.session.audit("DELETE", "category_rule", ruleId)
    }

    /** The first rule, in order, that applies to this payee text and amount. */
    fun find(text: String, amount: Money): CategoryRule? = list().firstOrNull { it.matches(text, amount) }
}
