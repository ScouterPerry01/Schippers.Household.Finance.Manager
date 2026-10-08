package ca.schippers.hfm.desktop

import ca.schippers.hfm.i18n.HelpGuide
import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.i18n.Manual
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** NFR-12: every screen has its help topic, in both languages, and the guide can be searched. */
class HelpTopicsTest {

    @Test
    fun `every screen and general topic is written in English and French`() {
        val ids = helpTopicIds()
        assertEquals(Section.entries.size + HelpGuide.GENERAL.size, ids.size, "every section is in the menu, once")
        for (language in Language.entries) {
            val missing = ids.filter { id -> javaClass.getResource("/hfm/help/${language.tag}/$id.md") == null }
            assertEquals(emptyList(), missing, "topics missing in $language")
            for (t in HelpGuide.topics(language, ids)) assertTrue(t.blocks.isNotEmpty() && t.title != t.id, "${t.id} in $language has a title and a body")
        }
    }

    @Test
    fun `no help topic is left out of the list, in either language`() {
        val ids = helpTopicIds().toSet()
        for (language in Language.entries) {
            val folder = java.io.File("../../core/i18n/src/main/resources/hfm/help/${language.tag}")
            val files = folder.listFiles { f -> f.extension == "md" }!!.map { it.nameWithoutExtension }.toSet()
            assertTrue(files.isNotEmpty(), "the help folder of $language is found")
            assertEquals(emptySet(), files - ids, "help topics in $language that no screen or general topic opens")
        }
    }

    @Test
    fun `the guide is searched in the user's language, accents ignored`() {
        assertTrue(HelpGuide.search(Language.ENGLISH, helpTopicIds(), "reconcile statement").isNotEmpty())
        assertTrue(HelpGuide.search(Language.FRENCH, helpTopicIds(), "releve").isNotEmpty(), "relevé found without its accent")
        assertTrue(HelpGuide.search(Language.ENGLISH, helpTopicIds(), "zzzqqq").isEmpty())
    }

    @Test
    fun `every screen has its chapter in the manual, which Shift+F1 opens`() {
        for (language in Language.entries) {
            val missing = Section.entries.map { it.helpId }.filter { Manual.book(language).chapter(it) == null }
            assertEquals(emptyList(), missing, "manual chapters missing in $language")
            assertTrue(Manual.book(language).chapter(AppState.MANUAL_START) != null)
        }
    }
}
