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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun MainScreen(model: BooksModel, app: AppState) {
    // Recomputed when the language changes too, so the banner follows it.
    val reminders = remember(model.revision, model.language) { model.reminderLines() }
    // Lines the books write themselves are written in the language in use.
    LaunchedEffect(model.language) { model.books.language = model.language }
    val inboxCount = remember(model.revision) { runCatching { model.books.documents.inboxCount() }.getOrDefault(0) }
    // FX-02: fetch missing Bank of Canada rates in the background; offline is fine (NFR-10).
    LaunchedEffect(model) {
        val added = withContext(Dispatchers.IO) { runCatching { model.books.rates.updateAll(today(), Http::get) }.getOrDefault(0) }
        // INV-04, CR-05, PM-02: market prices, only for the feeds the user turned on.
        val prices = withContext(Dispatchers.IO) { runCatching { model.books.prices.updateAll(today(), Http::get).total }.getOrDefault(0) }
        if (added + prices > 0) model.changed()
    }
    // GOAL-02: scheduled set-asides are entered on their dates while the app is open.
    LaunchedEffect(model) {
        while (true) {
            if (runCatching { model.books.goals.postScheduled(today()) }.getOrDefault(0) > 0) model.changed()
            delay(60 * 60_000L)
        }
    }
    // Section 3: phones can send captures while the household is unlocked; locking stops it.
    DisposableEffect(model) {
        try {
            model.syncServer.start()
        } catch (e: Exception) {
            model.syncError = e.message ?: e.javaClass.simpleName
        }
        onDispose { model.syncServer.close() }
    }
    // CAP-04: files saved into the watched folder are imported in the background.
    LaunchedEffect(model) { watchFolder(model) }
    LaunchedEffect(model) { watchTransferFolder(model) }
    LaunchedEffect(model) { scheduledReports(model) }
    LaunchedEffect(model) {
        ensureBackupDefaults(model)
        backupScheduler(model)
    }
    Column(Modifier.fillMaxSize()) {
        // DIST-05: a newer version, until the user looks at it under About.
        val update = app.updater.status as? UpdateStatus.Available
        if (update != null && model.section != Section.ABOUT) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().clickable { model.section = Section.ABOUT },
            ) {
                Text(model.t("update.banner", update.offer.version), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        // BILL-04, CAL-03, HLT-03: reminders, shown on every screen except the one they belong to.
        val shown = reminders.filter { it.section != model.section }
        if (shown.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth().clickable { model.section = shown.first().section },
            ) {
                Text(
                    model.t("reminder.banner", shown.size) + "  " + shown.take(3).joinToString(" · ") { it.text },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(200.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(8.dp)) {
                for (section in Section.entries) {
                    NavigationDrawerItem(
                        label = {
                            // SYNC-05: how many documents wait for review.
                            val count = if (section == Section.DOCUMENTS) inboxCount else 0
                            Text(model.t("nav.${section.name.lowercase()}") + if (count > 0) " ($count)" else "")
                        },
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
                    Section.DOCUMENTS -> DocumentsScreen(model)
                    Section.BILLS -> BillsScreen(model)
                    Section.CALENDAR -> CalendarScreen(model)
                    Section.HEALTH -> HealthScreen(model)
                    Section.MEDICAL -> MedicalScreen(model)
                    Section.ASSETS -> AssetsScreen(model)
                    Section.PETS -> PetsScreen(model)
                    Section.VEHICLES -> VehiclesScreen(model)
                    Section.BUDGETS -> BudgetsScreen(model)
                    Section.GOALS -> GoalsScreen(model)
                    Section.LOANS -> LoansScreen(model)
                    Section.INVESTMENTS -> InvestmentsScreen(model)
                    Section.PLANS -> PlansScreen(model)
                    Section.REPORTS -> ReportsScreen(model, model.reportState)
                    Section.RATES -> RatesScreen(model)
                    Section.PHONES -> PhonesScreen(model)
                    Section.AI -> AiScreen(model)
                    Section.TAXES -> TaxesScreen(model)
                    Section.ESTATE -> EstateScreen(model)
                    Section.USERS -> UsersScreen(model)
                    Section.BACKUPS -> BackupsScreen(model)
                    Section.SECURITY -> SecurityScreen(model, app)
                    Section.ABOUT -> AboutScreen(app)
                    Section.CATEGORIES -> CategoriesScreen(model)
                    Section.PAYEES -> PayeesScreen(model)
                    Section.RULES -> RulesScreen(model)
                    Section.INSTITUTIONS -> InstitutionsScreen(model)
                    Section.MEMBERS -> MembersScreen(model)
                }
            }
        }
    }

    SearchResultsDialog(model)
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
