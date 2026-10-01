package ca.schippers.hfm.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ca.schippers.hfm.i18n.Messages

fun main() = application {
    val state = remember { AppState() }
    Window(
        onCloseRequest = {
            state.lock()
            exitApplication()
        },
        title = Messages.get(state.language, "app.name"),
        state = rememberWindowState(width = 1100.dp, height = 760.dp),
    ) {
        App(state)
    }
}
