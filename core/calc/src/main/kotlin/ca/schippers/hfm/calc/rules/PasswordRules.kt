package ca.schippers.hfm.calc.rules

import kotlinx.datetime.LocalDate

/**
 * The household's password rules (the owner's decision 2026-10-05): an administrator sets them for
 * everyone, as Rates and rules values dated like any other (security.password.*). They apply to
 * every password set from their date on: a new household, a new user, a changed password and a
 * reset with the recovery key. Passwords already set keep working.
 */
data class PasswordRules(
    val minLength: Int,
    val capitals: Boolean,
    val smallLetters: Boolean,
    val digits: Boolean,
    val symbols: Boolean,
    val notLoginName: Boolean,
) {
    /** What a password lacks, as message keys, each with its argument when it has one. */
    enum class Problem(val key: String) {
        TOO_SHORT("error.passwordShort"),
        NO_CAPITAL("error.passwordCapital"),
        NO_SMALL_LETTER("error.passwordSmallLetter"),
        NO_DIGIT("error.passwordDigit"),
        NO_SYMBOL("error.passwordSymbol"),
        HAS_LOGIN_NAME("error.passwordLoginName"),
    }

    /** Everything [password] lacks under these rules, for the user [loginName]; empty when it is allowed. */
    fun problems(password: CharArray, loginName: String?): List<Problem> = buildList {
        if (password.size < minLength) add(Problem.TOO_SHORT)
        if (capitals && password.none(Char::isUpperCase)) add(Problem.NO_CAPITAL)
        if (smallLetters && password.none(Char::isLowerCase)) add(Problem.NO_SMALL_LETTER)
        if (digits && password.none(Char::isDigit)) add(Problem.NO_DIGIT)
        if (symbols && password.none(::isSymbol)) add(Problem.NO_SYMBOL)
        if (notLoginName && containsLogin(password, loginName)) add(Problem.HAS_LOGIN_NAME)
    }

    companion object {
        const val MIN = 8
        const val MAX = 64

        /** A login name shorter than this is not looked for in passwords: one or two letters are everywhere. */
        const val LOGIN_NAME_CHECKED_FROM = 3

        /** The rules in effect on [on] for the open household (its own values over the built-in ones). */
        fun on(on: LocalDate): PasswordRules = read { key -> Rules.text(key, on) }

        /**
         * The built-in rules on [on], for a household being created: none of its own values exist
         * yet, whatever household was open before.
         */
        fun builtIn(on: LocalDate): PasswordRules = read { key ->
            Rules.builtIn.filter { it.key == key && it.province == null && it.from <= on }.maxBy { it.from }.value
        }

        private fun read(value: (String) -> String) = PasswordRules(
            minLength = RuleLimits.clamp("security.password.minLength", value("security.password.minLength").toBigDecimal()).toInt(),
            capitals = value("security.password.capitals").toBooleanStrict(),
            smallLetters = value("security.password.smallLetters").toBooleanStrict(),
            digits = value("security.password.digits").toBooleanStrict(),
            symbols = value("security.password.symbols").toBooleanStrict(),
            notLoginName = value("security.password.notLoginName").toBooleanStrict(),
        )

        /** A symbol is anything printed that is neither a letter nor a digit. */
        fun isSymbol(c: Char): Boolean = !c.isLetterOrDigit() && !c.isWhitespace() && !c.isISOControl()

        private fun containsLogin(password: CharArray, loginName: String?): Boolean {
            val login = loginName?.trim()?.lowercase().orEmpty()
            if (login.length < LOGIN_NAME_CHECKED_FROM) return false
            // Searched in place, so the password is never copied into a String that cannot be wiped.
            return (0..password.size - login.length).any { start -> login.indices.all { i -> password[start + i].lowercaseChar() == login[i] } }
        }
    }
}
