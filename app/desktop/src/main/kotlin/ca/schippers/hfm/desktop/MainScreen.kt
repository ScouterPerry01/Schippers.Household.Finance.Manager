package ca.schippers.hfm.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MainScreen(model: BooksModel) {
    val reminders = remember(model.revision) { model.reminders() }
    // FX-02: fetch missing Bank of Canada rates in the background; offline is fine (NFR-10).
    LaunchedEffect(model) {
        val added = withContext(Dispatchers.IO) { runCatching { model.books.rates.updateFromBankOfCanada(today(), Http::get) }.getOrDefault(0) }
        if (added > 0) model.changed()
    }
    LaunchedEffect(model) {
        ensureBackupDefaults(model)
        backupScheduler(model)
    }
    Column(Modifier.fillMaxSize()) {
        // BILL-04: bills due soon, shown on every screen except Bills itself.
        if (reminders.isNotEmpty() && model.section != Section.BILLS) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth().clickable { model.section = Section.BILLS },
            ) {
                Text(
                    model.t("reminder.banner", reminders.size) + "  " + reminders.take(3).joinToString(" · ") { model.describe(it) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(200.dp).fillMaxHeight().padding(8.dp)) {
                for (section in Section.entries) {
                    NavigationDrawerItem(
                        label = { Text(model.t("nav.${section.name.lowercase()}")) },
                        selected = model.section == section,
                        onClick = { model.section = section },
                    )
                }
            }
            VerticalDivider()
            Box(Modifier.fillMaxSize()) {
                when (model.section) {
                    Section.DASHBOARD -> DashboardScreen(model)
                    Section.ACCOUNTS -> AccountsScreen(model)
                    Section.BILLS -> BillsScreen(model)
                    Section.BUDGETS -> BudgetsScreen(model)
                    Section.REPORTS -> ReportsScreen(model, model.reportState)
                    Section.RATES -> RatesScreen(model)
                    Section.BACKUPS -> BackupsScreen(model)
                    Section.CATEGORIES -> CategoriesScreen(model)
                    Section.PAYEES -> PayeesScreen(model)
                    Section.RULES -> RulesScreen(model)
                    Section.INSTITUTIONS -> InstitutionsScreen(model)
                    Section.MEMBERS -> MembersScreen(model)
                }
            }
        }
    }

    model.error?.let { message ->
        AlertDialog(
            onDismissRequest = { model.error = null },
            title = { Text(model.t("error.title")) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { model.error = null }) { Text("OK") } },
        )
    }
    model.pendingReconciledChange?.let { retry ->
        AlertDialog(
            onDismissRequest = { model.pendingReconciledChange = null },
            title = { Text(model.t("reconciled.title")) },
            text = { Text(model.t("reconciled.body"), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    model.pendingReconciledChange = null
                    retry()
                }) { Text(model.t("reconciled.confirm")) }
            },
            dismissButton = { TextButton(onClick = { model.pendingReconciledChange = null }) { Text(model.t("common.cancel")) } },
        )
    }
}
