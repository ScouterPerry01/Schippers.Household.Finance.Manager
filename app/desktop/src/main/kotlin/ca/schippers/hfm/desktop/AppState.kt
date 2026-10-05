package ca.schippers.hfm.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import ca.schippers.hfm.data.HouseholdSession
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.i18n.Manual
import ca.schippers.hfm.i18n.Messages
import ca.schippers.hfm.security.RecoveryKey
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.prefs.Preferences

sealed interface Screen {
    data object Welcome : Screen
    data object Create : Screen

    /** About and privacy, before a household is open. */
    data object About : Screen
    data class Unlock(val dir: Path) : Screen
    data class Reset(val dir: Path) : Screen
    data class ShowRecoveryKey(val session: HouseholdSession, val key: RecoveryKey) : Screen
    data class Main(val model: BooksModel) : Screen
}

/** UI state for the desktop app. Holds at most one unlocked household session. */
class AppState(
    val store: HouseholdStore = HouseholdStore(SqlCipherJdbcDriverFactory()),
    private val prefs: Preferences = Preferences.userRoot().node("ca/schippers/hfm"),
) {
    /** DIST-05, SEC-08: update checks for Linux packages from GitHub Releases (per computer). */
    val updater: Updater = Updater.create(prefs, System.getProperty("hfm.demo") == "true")

    /** BILL-04: the reminders already shown as a notification today, per household (per computer). */
    val notified: NotifiedReminders = NotifiedReminders(prefs)

    var language: Language by mutableStateOf(
        prefs.get(PREF_LANGUAGE, null)?.let { tag -> Language.entries.firstOrNull { it.tag == tag } }
            ?: Language.of(Locale.getDefault()),
    )
        private set

    var screen: Screen by mutableStateOf(Screen.Welcome)

    /** NFR-08: light, dark or as the system is; a per-computer setting. */
    var theme: ThemeChoice by mutableStateOf(runCatching { ThemeChoice.valueOf(prefs.get(PREF_THEME, ThemeChoice.SYSTEM.name)) }.getOrDefault(ThemeChoice.SYSTEM))
        private set

    /** NFR-08: text size, as a share of the normal size; a per-computer setting. */
    var textScale: Float by mutableStateOf(prefs.getFloat(PREF_TEXT_SCALE, 1f).coerceIn(TEXT_SCALES.first(), TEXT_SCALES.last()))
        private set

    fun chooseTheme(choice: ThemeChoice) {
        theme = choice
        prefs.put(PREF_THEME, choice.name)
    }

    fun chooseTextScale(scale: Float) {
        textScale = scale
        prefs.putFloat(PREF_TEXT_SCALE, scale)
    }

    /** NFR-12: the help topic shown, or null when the help is closed. */
    var helpTopic by mutableStateOf<String?>(null)

    /** NFR-12: opens the help on the topic for where the user is. */
    fun openHelp() {
        helpTopic = (screen as? Screen.Main)?.model?.section?.helpId ?: ca.schippers.hfm.i18n.HelpGuide.GENERAL.first()
    }

    /** NFR-12: the manual's page to show (a chapter, or chapter#section); null while its window is closed. */
    var manualPage by mutableStateOf<String?>(null)

    /** Increases each time the manual is asked for, so an open manual window comes to the front. */
    var manualRequests by mutableIntStateOf(0)

    /** NFR-12: opens the manual in its own window, on [page] or else on the chapter for the screen shown. */
    fun openManual(page: String? = null) {
        val section = (screen as? Screen.Main)?.model?.section?.helpId
        manualPage = page ?: section?.takeIf { Manual.book(language).chapter(it) != null } ?: MANUAL_START
        manualRequests++
    }

    /** Focus target for Ctrl+F (OTH-03). */
    val searchFocus = FocusRequester()

    /** SEC-02: minutes without keyboard or mouse activity before the household locks; 0 = never. A per-computer setting. */
    var autoLockMinutes: Int by mutableStateOf(prefs.getInt(PREF_AUTO_LOCK, DEFAULT_AUTO_LOCK))
        private set

    @Volatile
    private var lastActivity = System.currentTimeMillis()

    fun touch() {
        lastActivity = System.currentTimeMillis()
    }

    fun setAutoLock(minutes: Int) {
        autoLockMinutes = minutes
        prefs.putInt(PREF_AUTO_LOCK, minutes)
    }

    /** Locks the household if it has been idle longer than the auto-lock time. */
    fun lockIfIdle(now: Long = System.currentTimeMillis()) {
        val unlocked = screen is Screen.Main || screen is Screen.ShowRecoveryKey
        if (unlocked && autoLockMinutes > 0 && now - lastActivity > autoLockMinutes * 60_000L) lock()
    }

    fun t(key: String, vararg args: Any): String = Messages.get(language, key, *args)

    /** NAV-02: whether this user wants the menu as a bar at the top; remembered on this computer. */
    private var menuTop by mutableStateOf<Map<String, Boolean>>(emptyMap())

    fun menuOnTop(userId: String): Boolean = menuTop[userId] ?: prefs.getBoolean("$PREF_MENU_TOP.$userId", false)

    fun setMenuOnTop(userId: String, top: Boolean) {
        menuTop = menuTop + (userId to top)
        prefs.putBoolean("$PREF_MENU_TOP.$userId", top)
    }

    /** NAV-01: the menu groups this user left closed in the list on the left; Settings starts closed. */
    fun closedMenuGroups(userId: String): Set<NavGroup> =
        prefs.get("$PREF_MENU_CLOSED.$userId", NavGroup.SETTINGS.name).split(',').mapNotNull { n -> NavGroup.entries.firstOrNull { it.name == n } }.toSet()

    fun setClosedMenuGroups(userId: String, closed: Set<NavGroup>) = prefs.put("$PREF_MENU_CLOSED.$userId", closed.joinToString(",") { it.name })

    fun switchLanguage(to: Language, remember: Boolean = true) {
        language = to
        if (remember) prefs.put(PREF_LANGUAGE, to.tag)
    }

    val recentHouseholds: List<Path>
        get() = prefs.get(PREF_RECENT, "").split('\n').filter { it.isNotBlank() }.map { Path.of(it) }.filter { Files.isDirectory(it) }

    fun remember(dir: Path) {
        val list = (listOf(dir.toAbsolutePath()) + recentHouseholds).distinct().take(MAX_RECENT)
        prefs.put(PREF_RECENT, list.joinToString("\n"))
    }

    fun opened(session: HouseholdSession) {
        remember(session.dir)
        screen = try {
            Screen.Main(BooksModel(session, this))
        } catch (e: Exception) {
            session.close()
            throw e
        }
    }

    /** Locks the household: closes the session, wiping its keys from memory (SEC-02). */
    fun lock() {
        when (val s = screen) {
            is Screen.Main -> {
                s.model.session.close()
                screen = Screen.Unlock(s.model.session.dir)
            }
            is Screen.ShowRecoveryKey -> {
                s.session.close()
                screen = Screen.Unlock(s.session.dir)
            }
            else -> Unit
        }
    }

    companion object {
        /** M-72: the same minimum as for users added later and changed passwords. */
        const val MIN_PASSWORD_LENGTH = ca.schippers.hfm.books.UserService.MIN_PASSWORD
        private const val PREF_MENU_TOP = "nav.menuTop"
        private const val PREF_MENU_CLOSED = "nav.menuClosed"
        private const val PREF_THEME = "theme"
        private const val PREF_TEXT_SCALE = "textScale"
        private const val PREF_LANGUAGE = "language"

        /** The manual's first chapter, shown when no chapter covers the screen. */
        const val MANUAL_START = "welcome"
        private const val PREF_AUTO_LOCK = "autoLockMinutes"
        const val DEFAULT_AUTO_LOCK = 10
        private const val PREF_RECENT = "recentHouseholds"
        private const val MAX_RECENT = 5
    }
}

/**
 * BILL-04: remembers on this computer which reminders were shown as a system notification today,
 * per household, so each comes once a day however often the app is opened. Reminders are kept
 * as short hashes of their keys, and forgotten the next day.
 */
class NotifiedReminders(private val prefs: Preferences) {

    /** The [keys] not yet notified today for [householdId]; they are remembered as notified from now on. */
    @Synchronized
    fun fresh(householdId: String, today: kotlinx.datetime.LocalDate, keys: List<String>): Set<String> {
        val name = PREFIX + householdId.take(MAX_ID)
        val stored = prefs.get(name, "").split(SEPARATOR)
        val seen = if (stored.firstOrNull() == today.toString()) stored.drop(1).toMutableSet() else mutableSetOf()
        val fresh = keys.filter { seen.add(hash(it)) }.toSet()
        if (fresh.isNotEmpty() || stored.firstOrNull() != today.toString()) {
            // A preference value holds 8 192 characters: about 900 reminders a day, the latest kept.
            prefs.put(name, (listOf(today.toString()) + seen.toList().takeLast(MAX_KEPT)).joinToString(SEPARATOR))
            runCatching { prefs.flush() }
        }
        return fresh
    }

    private fun hash(key: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(key.encodeToByteArray()).take(4).joinToString("") { "%02x".format(it) }

    private companion object {
        const val PREFIX = "notified."
        const val SEPARATOR = ","
        const val MAX_ID = 64
        const val MAX_KEPT = 900
    }
}

/** NFR-08: the colours: as the system is, or always light or dark. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** NFR-08: the text sizes offered. */
val TEXT_SCALES = listOf(0.9f, 1f, 1.15f, 1.3f, 1.5f)

/** Whether the dark colours are in use, for drawings (charts) that pick their own. */
val LocalDarkTheme = androidx.compose.runtime.staticCompositionLocalOf { false }
