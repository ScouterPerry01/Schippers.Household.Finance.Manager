package ca.schippers.hfm.books

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.rules.PasswordRules
import ca.schippers.hfm.calc.rules.RuleException
import ca.schippers.hfm.calc.rules.RuleLimits
import ca.schippers.hfm.calc.rules.RuleValue
import ca.schippers.hfm.calc.rules.Rules
import ca.schippers.hfm.data.HouseholdSession
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate

/**
 * The household's password rules and key derivation cost, applied wherever a password is chosen
 * (the owner's decision 2026-10-05): creating the household (built-in rules, as it has none of its
 * own yet), adding a user, changing a password, and resetting one with the recovery key.
 */
object Passwords {

    /** Refuses [password] for [loginName] when it breaks [rules], naming the first thing it lacks. */
    fun check(rules: PasswordRules, password: CharArray, loginName: String?) {
        val problem = rules.problems(password, loginName).firstOrNull() ?: return
        if (problem == PasswordRules.Problem.TOO_SHORT) throw ValidationException(problem.key, rules.minLength)
        throw ValidationException(problem.key)
    }

    /**
     * For a reset with the recovery key: the household is open but not yet in use, so its own rules
     * are read from [session] first. They stay in effect, as they would once the household opens;
     * when the password is refused, the rules in effect before come back.
     */
    fun checkReset(session: HouseholdSession, loginName: String, password: CharArray, on: LocalDate) {
        val before = Rules.userValues
        Rules.userValues = householdValues(session)
        try {
            check(PasswordRules.on(on), password, loginName)
        } catch (e: ValidationException) {
            Rules.userValues = before
            throw e
        }
    }

    /** The Argon2id settings for a password set on [on], never below the safe minimums. */
    fun kdfForNewPasswords(on: LocalDate): KdfParams = KdfParams.forNewPasswords(
        RuleLimits.clamp("security.argon2.memory", Rules.decimal("security.argon2.memory", on)).toInt(),
        RuleLimits.clamp("security.argon2.iterations", Rules.decimal("security.argon2.iterations", on)).toInt(),
    )

    /** The household's own rule values, as [RateRuleService] loads them when the household opens. */
    private fun householdValues(session: HouseholdSession): List<RuleValue> =
        session.core.rulesQueries.ruleValues().executeAsList().mapNotNull { r ->
            runCatching {
                Rules.rule(r.rule_key)
                RuleValue(r.rule_key, r.province?.let(Province::valueOf), LocalDate.parse(r.effective_from), r.value_, r.note, builtIn = false, id = r.id)
            }.getOrElse { e -> if (e is RuleException) null else throw e }
        }
}
