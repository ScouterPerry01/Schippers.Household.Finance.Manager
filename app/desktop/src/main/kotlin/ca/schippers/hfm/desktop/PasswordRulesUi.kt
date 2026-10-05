package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Passwords
import ca.schippers.hfm.calc.rules.PasswordRules
import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.i18n.Messages

/** The household's password rules in a sentence or two, for the hint under a password field. */
internal fun passwordRulesText(language: Language, rules: PasswordRules): String {
    fun t(key: String, vararg args: Any) = Messages.get(language, key, *args)
    val needs = listOfNotNull(
        "capitals".takeIf { rules.capitals },
        "smallLetters".takeIf { rules.smallLetters },
        "digits".takeIf { rules.digits },
        "symbols".takeIf { rules.symbols },
    ).map { t("security.passwordRules.need.$it") }
    return listOfNotNull(
        t("security.passwordRules.summary.length", rules.minLength),
        needs.takeIf { it.isNotEmpty() }?.let { t("security.passwordRules.summary.needs", it.joinToString(", ")) },
        t("security.passwordRules.summary.notLoginName").takeIf { rules.notLoginName },
        t("security.passwordRules.passphrase"),
    ).joinToString(" ")
}

/** What [password] lacks under [rules], as a message; null when it is allowed. */
internal fun passwordProblem(language: Language, rules: PasswordRules, password: String, loginName: String?): String? =
    rules.problems(password.toCharArray(), loginName).firstOrNull()?.let { p ->
        if (p == PasswordRules.Problem.TOO_SHORT) Messages.get(language, p.key, rules.minLength) else Messages.get(language, p.key)
    }

/**
 * The owner's decision 2026-10-05: an administrator sets the household's password rules; they take
 * effect today, kept in Rates and rules with their date. Others see them read only.
 */
@Composable
internal fun PasswordRulesCard(model: BooksModel) {
    val books = model.books
    val current = remember(model.revision) { books.users.passwordRules() }
    val admin = books.users.isAdministrator
    var minLength by remember(current) { mutableStateOf(current.minLength.toString()) }
    var capitals by remember(current) { mutableStateOf(current.capitals) }
    var smallLetters by remember(current) { mutableStateOf(current.smallLetters) }
    var digits by remember(current) { mutableStateOf(current.digits) }
    var symbols by remember(current) { mutableStateOf(current.symbols) }
    var notLoginName by remember(current) { mutableStateOf(current.notLoginName) }
    var saved by remember { mutableStateOf(false) }
    val cost = remember(model.revision) { Passwords.kdfForNewPasswords(today()) }

    Card(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(model.t("security.passwordRules"), style = MaterialTheme.typography.titleMedium)
            Text(model.t("security.passwordRules.explain"), style = MaterialTheme.typography.bodySmall)
            if (!admin) Text(model.t("security.passwordRules.readOnly"), style = MaterialTheme.typography.bodySmall)
            TextInput(
                model.t("security.passwordRules.minLength", PasswordRules.MIN, PasswordRules.MAX), minLength, Modifier.width(360.dp), enabled = admin,
            ) { minLength = it.filter(Char::isDigit).take(2); saved = false }
            for ((key, value, set) in listOf<Triple<String, Boolean, (Boolean) -> Unit>>(
                Triple("capitals", capitals) { capitals = it },
                Triple("smallLetters", smallLetters) { smallLetters = it },
                Triple("digits", digits) { digits = it },
                Triple("symbols", symbols) { symbols = it },
                Triple("notLoginName", notLoginName) { notLoginName = it },
            )) {
                LabeledCheckbox(model.t("security.passwordRules.$key"), value, enabled = admin) { set(it); saved = false }
            }
            if (admin) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        val rules = PasswordRules(minLength.toIntOrNull() ?: 0, capitals, smallLetters, digits, symbols, notLoginName)
                        if (model.act { books.users.setPasswordRules(rules) } != null) saved = true
                    }) { Text(model.t("security.passwordRules.save")) }
                    if (saved) Text(model.t("security.passwordRules.saved"), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(model.t("security.passwordRules.cost", cost.memoryKiB / 1024, cost.iterations), style = MaterialTheme.typography.bodySmall)
        }
    }
}
