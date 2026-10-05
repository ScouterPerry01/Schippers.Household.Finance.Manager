package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** NFR-08: the theme and the text size, for this computer; OTH-04: the Getting started guide, shown again. */
@Composable
fun DisplayScreen(app: AppState, model: BooksModel) {
    var guideHidden by remember(model.revision) { mutableStateOf(model.gettingStartedHidden()) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(app.t("nav.display"), style = MaterialTheme.typography.titleLarge)
        Text(app.t("display.hint"), style = MaterialTheme.typography.bodySmall)
        Picker(app.t("display.theme"), ThemeChoice.entries, app.theme, { app.t("display.theme.$it") }, Modifier.width(320.dp)) { app.chooseTheme(it) }
        Picker(app.t("display.textSize"), TEXT_SCALES, app.textScale, { "${(it * 100).toInt()} %" }, Modifier.width(320.dp)) { app.chooseTextScale(it) }
        Text(app.t("display.sample"), style = MaterialTheme.typography.bodyLarge)
        Text(app.t("display.keyboard"), style = MaterialTheme.typography.bodySmall)
        if (guideHidden) {
            HorizontalDivider()
            Text(app.t("display.guide"), style = MaterialTheme.typography.titleSmall)
            Text(app.t("display.guideHint"), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                if (model.act { model.showGettingStarted() } != null) {
                    guideHidden = false
                    model.section = Section.DASHBOARD
                }
            }) { Text(app.t("display.guideShow")) }
        }
    }
}
