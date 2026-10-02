package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen(model: BooksModel) {
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
                Section.ACCOUNTS -> AccountsScreen(model)
                Section.CATEGORIES -> CategoriesScreen(model)
                Section.PAYEES -> PayeesScreen(model)
                Section.RULES -> RulesScreen(model)
                Section.INSTITUTIONS -> InstitutionsScreen(model)
                Section.MEMBERS -> MembersScreen(model)
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
