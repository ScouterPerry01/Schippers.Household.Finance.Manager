package ca.schippers.hfm.calc.rules

import ca.schippers.hfm.calc.Province
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/** How a rule's values are written, checked and shown. */
enum class RuleType {
    /** A fraction: 0.05 is 5 %. */
    RATE,

    /** Dollars, as 7000 or 2834.50. */
    AMOUNT,

    /** A plain number, which may have decimals: an age, a factor, kilometres. */
    NUMBER,

    /** A whole number of days. */
    DAYS,

    /** Income brackets: "0:0.15; 58523:0.205", the rate on income above each threshold in dollars. */
    BRACKETS,

    /** A day of the year, as 11-15 for November 15. */
    MONTH_DAY,

    /** Yes or no, as true or false. */
    YES_NO,

    /** Several numbers: "0.0528; 0.0540", read in the order the rule describes. */
    LIST,
}

/**
 * A rate, limit or threshold the app applies (the owner's decision 2026-10-05: every figure can
 * be changed). [key] names it, such as "tfsa.limit"; its values are kept by date and, when
 * [perProvince], by province or territory. [area] groups it on the Rates and rules screen.
 */
data class Rule(val key: String, val type: RuleType, val area: String, val perProvince: Boolean)

/**
 * A value of a rule in effect from [from] for [province] (null: federal, or everywhere). [builtIn]
 * values ship with the app, with their [source]; the household's own values are kept in its core
 * database and take their place from their date on.
 */
data class RuleValue(
    val key: String,
    val province: Province?,
    val from: LocalDate,
    val value: String,
    val source: String? = null,
    val builtIn: Boolean = true,
    val id: String? = null,
)

/** One income bracket: [rate] applies to income above [from] dollars, up to the next bracket. */
data class Bracket(val from: BigDecimal, val rate: BigDecimal)

/** Thrown for a rule key the catalogue does not know, or a value that does not fit its type. */
class RuleException(message: String) : IllegalArgumentException(message)

/**
 * Every rule and its values: the built-in ones, read from `hfm/rules/<area>.rules` files listed in
 * `hfm/rules/index.txt`, and the open household's own ([userValues], set when a household opens
 * and after each change, as [ca.schippers.hfm.calc.schedule.BusinessDays.province] is).
 *
 * A rules file has two kinds of lines (blank lines and lines starting with # are ignored):
 *
 *     rule tfsa.limit AMOUNT
 *     rule sales.pst RATE province
 *     tfsa.limit | - | 2026-01-01 | 7000 | CRA, TFSA contribution room
 *     sales.pst | BC | 2013-04-01 | 0.07 | Government of British Columbia
 *
 * A value line is the key, the province or territory (- for none), the date it takes effect, the
 * value and its source.
 */
object Rules {

    val catalogue: List<Rule>
    val builtIn: List<RuleValue>

    init {
        val rules = ArrayList<Rule>()
        val values = ArrayList<RuleValue>()
        for (area in resource("index.txt")!!.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }) {
            val text = resource("$area.rules") ?: throw RuleException("Missing rules file $area.rules")
            text.lines().forEachIndexed { i, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                if (line.startsWith("rule ")) {
                    val parts = line.removePrefix("rule ").trim().split(Regex("\\s+"))
                    rules += Rule(parts[0], RuleType.valueOf(parts[1]), area, parts.getOrNull(2) == "province")
                } else {
                    val f = line.split('|').map { it.trim() }
                    if (f.size < 4) throw RuleException("$area.rules line ${i + 1}: key | province | date | value | source")
                    values += RuleValue(f[0], f[1].takeUnless { it == "-" }?.let(Province::valueOf), LocalDate.parse(f[2]), f[3], f.getOrNull(4)?.ifEmpty { null })
                }
            }
        }
        catalogue = rules
        builtIn = values
        val known = rules.associateBy { it.key }
        require(known.size == rules.size) { "A rule is defined twice" }
        for (v in values) {
            val rule = known[v.key] ?: throw RuleException("Value for unknown rule ${v.key}")
            check(rule, v.value)
        }
    }

    private val byKey: Map<String, Rule> = catalogue.associateBy { it.key }

    @Volatile
    private var index: Map<String, List<RuleValue>> = build(emptyList())

    /** The open household's own values; setting them makes them apply at once. */
    @Volatile
    var userValues: List<RuleValue> = emptyList()
        set(value) {
            field = value
            index = build(value)
        }

    fun rule(key: String): Rule = byKey[key] ?: throw RuleException("Unknown rule $key")

    /** Every value of [key], built-in and the household's, oldest first. */
    fun values(key: String): List<RuleValue> = index[key].orEmpty()

    /**
     * The value of [key] in effect on [on] for [province]: the province's own when the rule has
     * one there, otherwise the value for everywhere. Of the values in effect, the latest date
     * wins, and on the same date the household's value wins over the built-in one. Null when no
     * value is in effect yet.
     */
    fun valueOn(key: String, on: LocalDate, province: Province? = null): RuleValue? {
        val all = index[key] ?: run { rule(key); return null }
        fun latest(p: Province?) = all.filter { it.province == p && it.from <= on }
            .maxWithOrNull(compareBy<RuleValue>({ it.from }, { !it.builtIn }))
        return (if (province != null) latest(province) else null) ?: latest(null)
    }

    fun text(key: String, on: LocalDate, province: Province? = null): String =
        valueOn(key, on, province)?.value ?: throw RuleException("No value for $key on $on${province?.let { " in $it" } ?: ""}")

    fun decimal(key: String, on: LocalDate, province: Province? = null): BigDecimal = BigDecimal(text(key, on, province))

    fun decimalOrNull(key: String, on: LocalDate, province: Province? = null): BigDecimal? = valueOn(key, on, province)?.value?.let(::BigDecimal)

    fun int(key: String, on: LocalDate, province: Province? = null): Int = decimal(key, on, province).toInt()

    fun yesNo(key: String, on: LocalDate, province: Province? = null): Boolean = text(key, on, province).toBooleanStrict()

    /** A [RuleType.MONTH_DAY] value as month and day. */
    fun monthDay(key: String, on: LocalDate, province: Province? = null): Pair<Int, Int> =
        text(key, on, province).split('-').let { it[0].toInt() to it[1].toInt() }

    fun list(key: String, on: LocalDate, province: Province? = null): List<BigDecimal> = parseList(text(key, on, province))

    fun brackets(key: String, on: LocalDate, province: Province? = null): List<Bracket> = parseBrackets(text(key, on, province))

    /** Checks that [value] fits [rule]'s type; throws [RuleException] when it does not. */
    fun check(rule: Rule, value: String) {
        val ok = runCatching {
            when (rule.type) {
                RuleType.RATE -> BigDecimal(value).let { it.signum() >= 0 && it <= BigDecimal.ONE }
                RuleType.AMOUNT, RuleType.NUMBER -> BigDecimal(value).signum() >= 0
                RuleType.DAYS -> value.toInt() >= 0
                RuleType.BRACKETS -> parseBrackets(value).let { b -> b.isNotEmpty() && b.zipWithNext().all { (a, c) -> a.from < c.from } && b.all { it.rate.signum() >= 0 && it.rate <= BigDecimal.ONE } }
                RuleType.MONTH_DAY -> value.split('-').let { it.size == 2 && LocalDate(2024, it[0].toInt(), it[1].toInt()).year == 2024 }
                RuleType.YES_NO -> value == "true" || value == "false"
                RuleType.LIST -> parseList(value).isNotEmpty()
            }
        }.getOrDefault(false)
        if (!ok) throw RuleException("The value $value does not fit ${rule.key} (${rule.type})")
    }

    private fun parseList(value: String): List<BigDecimal> = value.split(';').map { BigDecimal(it.trim()) }

    private fun parseBrackets(value: String): List<Bracket> =
        value.split(';').map { it.trim().split(':').let { (from, rate) -> Bracket(BigDecimal(from.trim()), BigDecimal(rate.trim())) } }

    private fun build(user: List<RuleValue>): Map<String, List<RuleValue>> =
        (builtIn + user).groupBy { it.key }.mapValues { (_, v) -> v.sortedWith(compareBy({ it.from }, { !it.builtIn })) }

    private fun resource(name: String): String? =
        Rules::class.java.getResourceAsStream("/hfm/rules/$name")?.use { it.readBytes().decodeToString() }
}
