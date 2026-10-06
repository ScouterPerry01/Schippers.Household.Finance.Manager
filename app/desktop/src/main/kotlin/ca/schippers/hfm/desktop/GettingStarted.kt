package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** OTH-04: the steps of setting up a household, each done when the books show it. */
/** NFR-09: a first receipt captured, and a first statement reconciled, without the manual. */
enum class SetupStep { PEOPLE, ACCOUNTS, BILLS, RECEIPT, STATEMENT, PHONE }

/** Which setup steps the books show as done. */
fun BooksModel.setupDone(): Set<SetupStep> {
    val accounts = books.accounts.list()
    return buildSet {
        if (books.members.list().isNotEmpty()) add(SetupStep.PEOPLE)
        if (accounts.isNotEmpty()) add(SetupStep.ACCOUNTS)
        if (books.bills.list().isNotEmpty()) add(SetupStep.BILLS)
        if (books.documents.search(ca.schippers.hfm.books.DocumentQuery(limit = 1)).isNotEmpty()) add(SetupStep.RECEIPT)
        if (accounts.any { a -> books.statements.statements(a.account.id).any { it.status == ca.schippers.hfm.books.StatementStatus.RECONCILED } }) add(SetupStep.STATEMENT)
        if (books.sync.devices().any { !it.revoked }) add(SetupStep.PHONE)
    }
}

/** OTH-04: the guide is hidden per user, in the household's settings. */
private fun BooksModel.gettingStartedKey() = "onboarding.hidden.${books.userId}"

/** Whether this user hid the Getting started guide. */
fun BooksModel.gettingStartedHidden(): Boolean = books.setting(gettingStartedKey()) == "1"

/** Shows the Getting started guide again on the Dashboard, after it was hidden (Display and accessibility). */
fun BooksModel.showGettingStarted() = books.putSetting(gettingStartedKey(), "0")

/**
 * OTH-04: a guide on the dashboard through the first steps (the people, the accounts, the bills, a
 * first receipt, a first statement reconciled, the phone), each opening the screen that does it, until all are done or the
 * user hides it (Display and accessibility shows it again). The next step to do stands out.
 */
@Composable
fun GettingStarted(model: BooksModel) {
    var hidden by remember { mutableStateOf(model.gettingStartedHidden()) }
    val done = remember(model.revision) { model.setupDone() }
    var adding by remember { mutableStateOf(false) }
    // The phone is optional: once the other steps are done, the guide has done its work.
    if (hidden || done.containsAll(SetupStep.entries - SetupStep.PHONE)) return
    val next = SetupStep.entries.firstOrNull { it !in done }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.t("setup.title", done.size, SetupStep.entries.size), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { model.act { model.books.putSetting(model.gettingStartedKey(), "1") }; hidden = true }) { Text(model.t("setup.hide")) }
            }
            Text(model.t("setup.intro"), style = MaterialTheme.typography.bodySmall)
            for (step in SetupStep.entries) {
                val isDone = step in done
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.semantics { stateDescription = model.t(if (isDone) "setup.done" else "setup.todo") },
                ) {
                    Text(if (isDone) "✓" else "○", Modifier.width(20.dp), color = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer)
                    Column(Modifier.weight(1f)) {
                        Text(model.t("setup.$step"), fontWeight = if (step == next) FontWeight.Bold else FontWeight.Normal)
                        if (!isDone) Text(model.t("setup.$step.hint"), style = MaterialTheme.typography.bodySmall)
                    }
                    if (!isDone) {
                        val go = {
                            when (step) {
                                SetupStep.PEOPLE -> model.section = Section.MEMBERS
                                SetupStep.ACCOUNTS -> adding = true
                                SetupStep.BILLS -> model.section = Section.BILLS
                                SetupStep.RECEIPT -> model.section = Section.DOCUMENTS
                                SetupStep.STATEMENT -> model.section = Section.ACCOUNTS
                                SetupStep.PHONE -> model.section = Section.PHONES
                            }
                        }
                        if (step == next) Button(onClick = go) { Text(model.t("setup.$step.action")) } else OutlinedButton(onClick = go) { Text(model.t("setup.$step.action")) }
                    }
                }
            }
        }
    }
    if (adding) AccountDialog(model, null) { adding = false }
}
