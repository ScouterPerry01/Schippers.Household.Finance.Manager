package ca.schippers.hfm.desktop

import ca.schippers.hfm.i18n.Language
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HLP-02: what's new comes from the bundled release notes, and the support details name nothing
 * personal. HLP-03: each guide's progress is kept on this computer.
 */
class AboutDetailsTest {
    private val prefs = Preferences.userRoot().node("ca/schippers/hfm-test-about-${System.nanoTime()}")

    @AfterTest
    fun clean() = prefs.removeNode()

    @Test
    fun `versions compare part by part`() {
        assertTrue(compareVersions("1.10.0", "1.9.3") > 0)
        assertTrue(compareVersions("0.9.0", "1.0.0") < 0)
        assertEquals(0, compareVersions("1.0", "1.0.0"))
    }

    @Test
    fun `release notes are this version's, else the nearest earlier, else the latest`() {
        for (language in Language.entries) {
            val (version, text) = assertNotNull(releaseNotes(language, "1.0.0"))
            assertEquals("1.0.0", version)
            assertTrue(text.startsWith("# "))
            assertEquals("1.0.0", releaseNotes(language, "1.0.5")!!.first, "the nearest earlier version")
            assertEquals("1.0.0", releaseNotes(language, "0.1.0")!!.first, "the latest when none is earlier")
        }
        assertTrue(releaseNotes(Language.FRENCH, "1.0.0")!!.second != releaseNotes(Language.ENGLISH, "1.0.0")!!.second, "each language its own notes")
    }

    @Test
    fun `support details give versions and settings, never a path or a name`() {
        val text = supportDetails(AppState(prefs = prefs))
        assertTrue(text.startsWith("RANN's Roost "))
        assertEquals(7, text.lines().size, text)
        assertFalse(System.getProperty("user.home") in text, "no home folder")
        assertFalse('\\' in text, "no paths")
    }

    @Test
    fun `a guide resumes where it was left, and starts over once finished`() {
        val walk = WalkMeState(prefs)
        walk.start("bills")
        assertEquals(0, walk.step)
        walk.go(3)
        walk.close()
        assertNull(walk.guideId)
        assertEquals(3, WalkMeState(prefs).savedStep("bills"), "kept on this computer")
        walk.start("bills")
        assertEquals(3, walk.step)
        walk.finish()
        assertEquals(0, walk.savedStep("bills"))
        assertTrue(walk.finished("bills"))
        walk.resize(10_000f)
        assertEquals(WalkMeState.MAX_WIDTH, WalkMeState(prefs).width)
    }
}
