package ca.schippers.hfm.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ca.schippers.hfm.data.HouseholdSession
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.i18n.Messages
import ca.schippers.hfm.security.RecoveryKey
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.prefs.Preferences

sealed interface Screen {
    data object Welcome : Screen
    data object Create : Screen
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
    var language: Language by mutableStateOf(
        prefs.get(PREF_LANGUAGE, null)?.let { tag -> Language.entries.firstOrNull { it.tag == tag } }
            ?: Language.of(Locale.getDefault()),
    )
        private set

    var screen: Screen by mutableStateOf(Screen.Welcome)

    fun t(key: String, vararg args: Any): String = Messages.get(language, key, *args)

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
        const val MIN_PASSWORD_LENGTH = 12
        private const val PREF_LANGUAGE = "language"
        private const val PREF_RECENT = "recentHouseholds"
        private const val MAX_RECENT = 5
    }
}
