package ca.schippers.hfm.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ca.schippers.hfm.i18n.HelpGuide

/** NFR-12: the guide's topics in menu order: the general ones, the dashboard, then each group's screens. */
fun helpTopicIds(): List<String> =
    HelpGuide.GENERAL + Section.DASHBOARD.helpId + NavGroup.entries.flatMap { g -> g.sections.map { it.helpId } }

val Section.helpId: String get() = name.lowercase()

/**
 * NFR-12: the user guide beside the screen: the topic for where the user is, every other topic, and
 * a search across all of them, in the user's language. Opened with F1 or the Help button.
 */
@Composable
fun HelpPanel(state: AppState, start: String, onClose: () -> Unit) {
    val ids = remember { helpTopicIds() }
    var current by remember(start) { mutableStateOf(start) }
    var query by remember { mutableStateOf("") }
    val topic = remember(current, state.language) { HelpGuide.topic(state.language, current) ?: HelpGuide.topic(state.language, HelpGuide.GENERAL.first()) }
    val listed = remember(query, state.language) {
        if (query.isBlank()) HelpGuide.topics(state.language, ids).map { it to null }
        else HelpGuide.search(state.language, ids, query).map { it.topic to it.snippet }
    }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.width(980.dp).height(680.dp).padding(16.dp)
                .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && (e.key == Key.Escape || e.key == Key.F1)) { onClose(); true } else false },
            shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp,
        ) {
            Row(Modifier.padding(20.dp)) {
                Column(Modifier.width(300.dp).fillMaxHeight()) {
                    Text(state.t("help.title"), style = MaterialTheme.typography.titleLarge)
                    TextInput(state.t("help.search"), query, Modifier.fillMaxWidth().padding(vertical = 8.dp).focusRequester(searchFocus)) { query = it }
                    if (query.isNotBlank() && listed.isEmpty()) Text(state.t("help.noResults"), style = MaterialTheme.typography.bodySmall)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        for ((t, snippet) in listed) {
                            Column(
                                Modifier.fillMaxWidth().clickable { current = t.id }.padding(vertical = 6.dp, horizontal = 4.dp),
                            ) {
                                Text(t.title, fontWeight = if (t.id == topic?.id) FontWeight.Bold else FontWeight.Normal)
                                snippet?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                            }
                        }
                    }
                }
                VerticalDivider(Modifier.padding(horizontal = 16.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (topic == null) {
                            Text(state.t("help.missing"))
                        } else {
                            Text(topic.title, style = MaterialTheme.typography.headlineSmall)
                            for (b in topic.blocks) {
                                when (b) {
                                    is HelpGuide.Block.Heading -> Text(b.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                                    is HelpGuide.Block.Paragraph -> Text(b.text, style = MaterialTheme.typography.bodyLarge)
                                    is HelpGuide.Block.Bullet -> Row { Text("•  "); Text(b.text, style = MaterialTheme.typography.bodyLarge) }
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { state.openManual(); onClose() }) { Text(state.t("manual.open")) }
                        TextButton(onClick = onClose) { Text(state.t("common.close")) }
                    }
                }
            }
        }
    }
}
