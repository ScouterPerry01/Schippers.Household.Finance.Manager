package ca.schippers.hfm.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ca.schippers.hfm.books.StatementStatus
import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.i18n.Messages
import kotlinx.coroutines.delay
import java.util.prefs.Preferences

fun main() {
    PackagedSelfCheck.runIfRequested()
    desktopApp()
}

private fun desktopApp() = application {
    val state = remember {
        // The demo keeps its own per-computer settings, so trying it never changes the real app's.
        val demo = System.getProperty("hfm.demo") == "true"
        val prefs = Preferences.userRoot().node(if (demo) "ca/schippers/hfm-demo" else "ca/schippers/hfm")
        AppState(prefs = prefs).also { app ->
            // Demo mode (./gradlew :app:desktop:runDemo): a throw-away sample household.
            if (demo) {
                System.getProperty("hfm.demo.lang")?.let { tag ->
                    Language.entries.firstOrNull { it.tag == tag }?.let { app.switchLanguage(it, remember = false) }
                }
                val model = BooksModel(DemoHousehold.create(app.store, app.language), app)
                model.selectedAccountId = model.books.accounts.list().firstOrNull { it.account.name == System.getProperty("hfm.demo.account") }?.account?.id
                System.getProperty("hfm.demo.section")?.let { name -> Section.entries.firstOrNull { it.name == name }?.let { model.section = it } }
                System.getProperty("hfm.demo.report")?.let { name -> ReportKind.entries.firstOrNull { it.name == name }?.let { model.reportState.kind = it } }
                if (System.getProperty("hfm.demo.reconcile") == "true") {
                    model.selectedAccountId?.let { id ->
                        model.reconcilingStatementId = model.books.statements.statements(id).firstOrNull { it.status == StatementStatus.OPEN }?.id
                    }
                }
                System.getProperty("hfm.demo.search")?.let { q -> model.search = q to model.books.search.search(q, model.language.locale) }
                app.screen = Screen.Main(model)
            }
        }
    }
    // BILL-04: a system notification with today's bill reminders, once per household and day.
    val trayState = rememberTrayState()
    Tray(icon = AppIcon, state = trayState, tooltip = Messages.get(state.language, "app.name"))
    val main = state.screen as? Screen.Main
    LaunchedEffect(main?.model) {
        val model = main?.model ?: return@LaunchedEffect
        // Checked every few minutes so timed appointments are announced on time; each reminder once.
        val notified = HashSet<String>()
        while (true) {
            val fresh = model.reminderLines().filter { notified.add(it.key) }
            if (fresh.isNotEmpty()) {
                trayState.sendNotification(Notification(model.t("reminder.banner", fresh.size), fresh.take(4).joinToString("\n") { it.text }))
            }
            delay(5 * 60_000L)
        }
    }
    // SEC-02: lock after the chosen time without keyboard or mouse activity.
    LaunchedEffect(state) {
        while (true) {
            delay(15_000)
            state.lockIfIdle()
        }
    }
    Window(
        onCloseRequest = {
            state.lock()
            exitApplication()
        },
        onPreviewKeyEvent = { event ->
            state.touch()
            // Ctrl+F: go to the search box (OTH-03).
            if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.F && state.screen is Screen.Main) {
                runCatching { state.searchFocus.requestFocus() }
                true
            } else if (event.type == KeyEventType.KeyDown && event.key == Key.F1 && state.helpTopic == null) {
                // NFR-12: F1 opens the help on the topic for the screen shown.
                state.openHelp()
                true
            } else {
                false
            }
        },
        title = Messages.get(state.language, "app.name"),
        icon = AppIcon,
        state = rememberWindowState(width = 1440.dp, height = 900.dp),
    ) {
        App(state)
    }
}

/**
 * The RANN's Roost head, for the window, taskbar and tray (branding/, not covered by the GPL).
 * Loaded on first use, so the packaged self-check runs without the graphics libraries.
 */
private val AppIcon by lazy {
    BitmapPainter(
        org.jetbrains.skia.Image.makeFromEncoded(
            checkNotNull(object {}.javaClass.getResourceAsStream("/hfm/branding/icon.png")).use { it.readBytes() },
        ).toComposeImageBitmap(),
    )
}
