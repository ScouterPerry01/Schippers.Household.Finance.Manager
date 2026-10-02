package ca.schippers.hfm.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
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

fun main() = application {
    val state = remember {
        AppState().also { app ->
            // Demo mode (./gradlew :app:desktop:runDemo): a throw-away sample household.
            if (System.getProperty("hfm.demo") == "true") {
                System.getProperty("hfm.demo.lang")?.let { tag ->
                    Language.entries.firstOrNull { it.tag == tag }?.let { app.switchLanguage(it, remember = false) }
                }
                val model = BooksModel(DemoHousehold.create(app.store), app)
                model.selectedAccountId = model.books.accounts.list().firstOrNull { it.account.name == System.getProperty("hfm.demo.account") }?.account?.id
                System.getProperty("hfm.demo.section")?.let { name -> Section.entries.firstOrNull { it.name == name }?.let { model.section = it } }
                System.getProperty("hfm.demo.report")?.let { name -> ReportKind.entries.firstOrNull { it.name == name }?.let { model.reportState.kind = it } }
                if (System.getProperty("hfm.demo.reconcile") == "true") {
                    model.selectedAccountId?.let { id ->
                        model.reconcilingStatementId = model.books.statements.statements(id).firstOrNull { it.status == StatementStatus.OPEN }?.id
                    }
                }
                app.screen = Screen.Main(model)
            }
        }
    }
    // BILL-04: a system notification with today's bill reminders, once per household and day.
    val trayState = rememberTrayState()
    Tray(icon = TrayIcon, state = trayState, tooltip = Messages.get(state.language, "app.name"))
    val main = state.screen as? Screen.Main
    LaunchedEffect(main?.model, today()) {
        val model = main?.model ?: return@LaunchedEffect
        val reminders = model.reminders()
        if (reminders.isNotEmpty()) {
            trayState.sendNotification(
                Notification(model.t("reminder.banner", reminders.size), reminders.take(4).joinToString("\n") { model.describe(it) }),
            )
        }
    }
    Window(
        onCloseRequest = {
            state.lock()
            exitApplication()
        },
        title = Messages.get(state.language, "app.name"),
        state = rememberWindowState(width = 1440.dp, height = 900.dp),
    ) {
        App(state)
    }
}

/** Placeholder tray icon until the application icon is designed. */
private val TrayIcon = ColorPainter(Color(0xFF6750A4))
