package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** NFR-08: the theme and the text size, for this computer. */
@Composable
fun DisplayScreen(app: AppState) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(app.t("nav.display"), style = MaterialTheme.typography.titleLarge)
        Text(app.t("display.hint"), style = MaterialTheme.typography.bodySmall)
        Picker(app.t("display.theme"), ThemeChoice.entries, app.theme, { app.t("display.theme.$it") }, Modifier.width(320.dp)) { app.chooseTheme(it) }
        Picker(app.t("display.textSize"), TEXT_SCALES, app.textScale, { "${(it * 100).toInt()} %" }, Modifier.width(320.dp)) { app.chooseTextScale(it) }
        Text(app.t("display.sample"), style = MaterialTheme.typography.bodyLarge)
        Text(app.t("display.keyboard"), style = MaterialTheme.typography.bodySmall)
    }
}
