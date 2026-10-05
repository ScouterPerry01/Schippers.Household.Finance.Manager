package ca.schippers.hfm.books

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.rules.Rule
import ca.schippers.hfm.calc.rules.RuleException
import ca.schippers.hfm.calc.rules.RuleType
import ca.schippers.hfm.calc.rules.RuleValue
import ca.schippers.hfm.calc.rules.Rules
import ca.schippers.hfm.domain.Ids
import kotlinx.datetime.LocalDate

/**
 * Rates and rules (the owner's decision 2026-10-05): every rate, limit and threshold the app
 * applies has built-in values with their dates; the household can add its own, in effect from a
 * date, for one province or territory or for all. Its values are kept in the core database and
 * take the built-in one's place from their date on. Changing them is for an administrator.
 */
class RateRuleService internal constructor(private val books: Books) {

    private val q get() = books.session.core.rulesQueries

    init {
        load()
    }

    /** Every rule, in catalogue order. */
    val catalogue: List<Rule> get() = Rules.catalogue

    /** Every value of [key], built-in and the household's, oldest first. */
    fun values(key: String): List<RuleValue> = Rules.values(key)

    /** The value of [key] in effect on [on] for [province] (see [Rules.valueOn]). */
    fun valueOn(key: String, on: LocalDate, province: Province? = null): RuleValue? = Rules.valueOn(key, on, province)

    /**
     * Adds the household's own value of [key], in effect from [from] for [province] (null: for all,
     * or federal). It must fit the rule's type; a rule kept for all has no province.
     */
    fun add(key: String, province: Province?, from: LocalDate, value: String, note: String? = null): RuleValue {
        requireAdmin(books)
        val rule = runCatching { Rules.rule(key) }.getOrNull()
        validate(rule != null, "error.unknownRule")
        validate(province == null || rule!!.perProvince, "error.ruleNoProvince")
        // A decimal comma, as typed in French, becomes a point; lists and brackets keep theirs as written.
        val clean = value.trim().let { if (rule!!.type in setOf(RuleType.RATE, RuleType.AMOUNT, RuleType.NUMBER)) it.replace(',', '.') else it }
        validate(runCatching { Rules.check(rule!!, clean) }.isSuccess, "error.ruleValue")
        val id = Ids.newId()
        q.insertRuleValue(id, key, province?.name, from.toString(), clean, note?.trim()?.ifEmpty { null }, books.userId, books.now())
        books.session.audit("CREATE", "rule_value", id, key)
        load()
        return Rules.values(key).first { it.id == id }
    }

    /** Who added each of the household's values ([RuleValue.id]) and when, for the Rates and rules screen. */
    fun origins(): Map<String, RuleValueOrigin> =
        q.ruleValues().executeAsList().associate { it.id to RuleValueOrigin(it.created_by, it.created_at) }

    /** Removes one of the household's values; built-in values cannot be removed, only replaced. */
    fun delete(id: String) {
        requireAdmin(books)
        q.deleteRuleValue(id)
        books.session.audit("DELETE", "rule_value", id)
        load()
    }

    /** Makes the household's values apply: when it opens, and after each change. */
    internal fun load() {
        Rules.userValues = q.ruleValues().executeAsList().mapNotNull { r ->
            runCatching {
                Rules.rule(r.rule_key)
                RuleValue(r.rule_key, r.province?.let(Province::valueOf), LocalDate.parse(r.effective_from), r.value_, r.note, builtIn = false, id = r.id)
            }.getOrElse { e -> if (e is RuleException) null else throw e }
        }
    }
}

/** The user ([createdBy], null when unknown) who added a household value of a rule, and when ([createdAt], epoch milliseconds). */
data class RuleValueOrigin(val createdBy: String?, val createdAt: Long)
