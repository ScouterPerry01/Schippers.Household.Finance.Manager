package ca.schippers.hfm.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** NAV-01: the menu's groups, in order; the dashboard stands on its own above them. */
enum class NavGroup(val sections: List<Section>) {
    MONEY(listOf(Section.ACCOUNTS, Section.DOCUMENTS, Section.BILLS, Section.BUDGETS, Section.GOALS, Section.FAMILY, Section.CALENDAR)),
    INVESTING(listOf(Section.INVESTMENTS, Section.PLANS, Section.LOANS)),
    REPORTS(listOf(Section.REPORTS, Section.TAXES)),
    HOME(listOf(Section.HEALTH, Section.MEDICAL, Section.ESTATE, Section.PETS, Section.VEHICLES, Section.ASSETS)),
    SETTINGS(
        listOf(
            Section.MEMBERS, Section.USERS, Section.CATEGORIES, Section.PAYEES, Section.RULES, Section.INSTITUTIONS,
            Section.RATES, Section.PHONES, Section.AI, Section.BACKUPS, Section.SECURITY, Section.DISPLAY, Section.ABOUT,
        ),
    ),
    ;

    companion object {
        fun of(section: Section): NavGroup? = entries.firstOrNull { section in it.sections }
    }
}

/** NAV-03: a section's name with its count, such as documents waiting for review. */
private fun BooksModel.navLabel(section: Section, counts: Map<Section, Int>): String =
    t("nav.${section.name.lowercase()}") + (counts[section]?.takeIf { it > 0 }?.let { " ($it)" } ?: "")

/**
 * NAV-01, NAV-02: the menu as a list on the left. Each group folds open or closed; the group of the
 * screen shown is always open. The groups left closed are remembered for this user on this computer.
 */
@Composable
fun SideMenu(model: BooksModel, app: AppState, counts: Map<Section, Int>) {
    val user = model.books.userId
    var closed by remember(user) { mutableStateOf(app.closedMenuGroups(user)) }
    // NFR-08: wider with larger text, so names stay on one line.
    Column(Modifier.width(210.dp * app.textScale.coerceAtLeast(1f)).fillMaxHeight().verticalScroll(rememberScrollState()).padding(8.dp)) {
        NavigationDrawerItem(label = { Text(model.navLabel(Section.DASHBOARD, counts)) }, selected = model.section == Section.DASHBOARD, onClick = { model.section = Section.DASHBOARD })
        for (group in NavGroup.entries) {
            val open = group !in closed || NavGroup.of(model.section) == group
            val total = group.sections.sumOf { counts[it] ?: 0 }
            Row(
                Modifier.fillMaxWidth()
                    .clickable(role = Role.Button) {
                        closed = if (group in closed) closed - group else closed + group
                        app.setClosedMenuGroups(user, closed)
                    }
                    .semantics { stateDescription = model.t(if (open) "nav.expanded" else "nav.collapsed") }
                    .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(model.t("navGroup.$group") + if (!open && total > 0) " ($total)" else "", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(if (open) "▾" else "▸", color = MaterialTheme.colorScheme.primary)
            }
            if (open) {
                for (section in group.sections) {
                    NavigationDrawerItem(
                        label = { Text(model.navLabel(section, counts)) },
                        selected = model.section == section,
                        onClick = { model.section = section },
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        TextButton(onClick = { app.setMenuOnTop(user, true) }) { Text(model.t("nav.menuOnTop"), style = MaterialTheme.typography.bodySmall) }
    }
}

/** NAV-02: the menu as a bar across the top, each group a drop-down menu. */
@Composable
fun TopMenu(model: BooksModel, app: AppState, counts: Map<Section, Int>) {
    var open by remember { mutableStateOf<NavGroup?>(null) }
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { model.section = Section.DASHBOARD }) {
                Text(model.navLabel(Section.DASHBOARD, counts), fontWeight = if (model.section == Section.DASHBOARD) FontWeight.Bold else FontWeight.Normal)
            }
            for (group in NavGroup.entries) {
                val total = group.sections.sumOf { counts[it] ?: 0 }
                val current = NavGroup.of(model.section) == group
                Box {
                    TextButton(
                        onClick = { open = group },
                        modifier = Modifier.semantics { contentDescription = model.t("navGroup.$group") + ", " + model.t("nav.menu") },
                    ) {
                        Text(
                            model.t("navGroup.$group") + (if (total > 0) " ($total)" else "") + " ▾",
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    DropdownMenu(expanded = open == group, onDismissRequest = { open = null }) {
                        for (section in group.sections) {
                            DropdownMenuItem(
                                text = { Text(model.navLabel(section, counts), fontWeight = if (model.section == section) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { model.section = section; open = null },
                            )
                        }
                    }
                }
            }
            Box(Modifier.weight(1f))
            TextButton(onClick = { app.setMenuOnTop(model.books.userId, false) }) { Text(model.t("nav.menuOnLeft"), style = MaterialTheme.typography.bodySmall) }
        }
    }
}
