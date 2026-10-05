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

    /** Adds a rule at the bottom of the list, tried after every other (M-77: not for viewers). */
    fun create(payeeContains: String, categoryId: String, amountMin: Money? = null, amountMax: Money? = null, payeeId: String? = null): CategoryRule {
        requireEditor(books)
        check(payeeContains, categoryId, amountMin, amountMax, payeeId)
        val id = Ids.newId()
        books.session.core.transaction {
            renumber(list().map { it.id })
            insert(id, payeeContains, categoryId, amountMin, amountMax, payeeId, list().size)
        }
        books.session.audit("CREATE", "category_rule", id)
        return list().first { it.id == id }
    }

    /** M-73: changes a rule's text, limits, category or payee; it keeps its place in the list. */
    fun update(ruleId: String, payeeContains: String, categoryId: String, amountMin: Money? = null, amountMax: Money? = null, payeeId: String? = null): CategoryRule {
        requireEditor(books)
        check(payeeContains, categoryId, amountMin, amountMax, payeeId)
        val existing = list().firstOrNull { it.id == ruleId } ?: throw ValidationException("error.unknownRule")
        books.session.core.transaction {
            books.core.deleteRule(ruleId)
            insert(ruleId, payeeContains, categoryId, amountMin, amountMax, payeeId, existing.sortOrder)
        }
        books.session.audit("UPDATE", "category_rule", ruleId)
        return list().first { it.id == ruleId }
    }

    /** M-73: moves a rule one place up (tried sooner) or down; positions stay 0, 1, 2... */
    fun move(ruleId: String, up: Boolean) {
        requireEditor(books)
        val ids = list().map { it.id }.toMutableList()
        val from = ids.indexOf(ruleId)
        validate(from >= 0, "error.unknownRule")
        val to = if (up) from - 1 else from + 1
        if (to !in ids.indices) return
        ids[from] = ids[to].also { ids[to] = ids[from] }
        books.session.core.transaction { renumber(ids) }
        books.session.audit("UPDATE", "category_rule", ruleId, if (up) "up" else "down")
    }

    fun delete(ruleId: String) {
        requireEditor(books)
        books.session.core.transaction {
            books.core.deleteRule(ruleId)
            renumber(list().map { it.id })
        }
        books.session.audit("DELETE", "category_rule", ruleId)
    }

    private fun check(payeeContains: String, categoryId: String, amountMin: Money?, amountMax: Money?, payeeId: String?) {
        validate(payeeContains.isNotBlank(), "error.nameRequired")
        validate(books.categories.list(includeArchived = true).any { it.id == categoryId }, "error.unknownCategory")
        validate(payeeId == null || books.payees.list(includeArchived = true).any { it.id == payeeId }, "error.unknownPayee")
        validate(amountMin == null || amountMax == null || amountMin.currency == amountMax.currency, "error.currencyMismatch", amountMin?.currency?.code ?: "")
        validate(amountMin == null || amountMax == null || amountMin <= amountMax, "error.ruleAmountRange")
    }

    private fun insert(id: String, payeeContains: String, categoryId: String, amountMin: Money?, amountMax: Money?, payeeId: String?, order: Int) {
        val currency = (amountMin ?: amountMax)?.currency?.code
        books.core.insertRule(id, payeeContains.trim(), amountMin?.abs()?.minorUnits, amountMax?.abs()?.minorUnits, currency, categoryId, payeeId, order.toLong())
    }

    /**
     * M-73: gives the rules in [ids] the positions 0, 1, 2... in that order, so no two rules share
     * a place. A rule whose place changes is removed and added again with the same id and contents.
     */
    private fun renumber(ids: List<String>) {
        val byId = list().associateBy { it.id }
        ids.forEachIndexed { i, id ->
            val r = byId[id]
            if (r != null && r.sortOrder != i) {
                books.core.deleteRule(id)
                insert(id, r.payeeContains, r.categoryId, r.amountMin, r.amountMax, r.payeeId, i)
            }
        }
    }

    /** The first rule, in order, that applies to this payee text and amount. */
    fun find(text: String, amount: Money): CategoryRule? = list().firstOrNull { it.matches(text, amount) }
}
