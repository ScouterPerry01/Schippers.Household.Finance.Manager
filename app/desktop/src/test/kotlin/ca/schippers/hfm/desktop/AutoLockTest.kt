package ca.schippers.hfm.desktop

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.security.KdfParams
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** SEC-02: the household locks after the chosen idle time, and its session is closed. */
class AutoLockTest {

    @TempDir
    lateinit var temp: Path

    private val prefs = Preferences.userRoot().node("ca/schippers/hfm-test-${System.nanoTime()}")

    @AfterTest
    fun cleanUp() = prefs.removeNode()

    @Test
    fun `idle households lock and activity keeps them open`() {
        val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
        val state = AppState(store, prefs)
        val session = store.create(temp.resolve("H.hfm"), "H", "perry", "Perry", "pw".toCharArray()).session
        state.opened(session)
        state.setAutoLock(10)
        val now = System.currentTimeMillis()

        state.touch()
        state.lockIfIdle(now + 5 * 60_000)
        assertIs<Screen.Main>(state.screen)

        state.lockIfIdle(now + 11 * 60_000)
        assertIs<Screen.Unlock>(state.screen)
        assertFailsWith<IllegalStateException>("the session is closed and its keys wiped") { session.groupLedger("x") }
    }

    @Test
    fun `never means never`() {
        val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
        val state = AppState(store, prefs)
        state.opened(store.create(temp.resolve("H.hfm"), "H", "perry", "Perry", "pw".toCharArray()).session)
        state.setAutoLock(0)
        state.lockIfIdle(System.currentTimeMillis() + 24 * 3_600_000L)
        assertIs<Screen.Main>(state.screen)
        state.lock()
    }
}
