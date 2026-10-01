package ca.schippers.hfm.i18n

import java.text.MessageFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessagesTest {

    @Test
    fun `English and French have exactly the same keys`() {
        val en = Messages.keys(Language.ENGLISH)
        val fr = Messages.keys(Language.FRENCH)
        assertEquals(emptySet(), en - fr, "missing in French")
        assertEquals(emptySet(), fr - en, "missing in English")
    }

    @Test
    fun `every message is a valid pattern in both languages`() {
        for (language in Language.entries) {
            for (key in Messages.keys(language)) {
                MessageFormat(Messages.get(language, key), language.locale)
            }
        }
    }

    @Test
    fun `apostrophes are written twice, so the formatter does not swallow them`() {
        for (language in Language.entries) {
            val stream = Messages::class.java.getResourceAsStream("/hfm/i18n/messages_${language.tag}.properties")!!
            val lone = stream.reader(Charsets.UTF_8).readLines()
                .filter { Regex("(?<!')'(?!')").containsMatchIn(it) }
            assertEquals(emptyList(), lone, "single apostrophes in $language")
        }
        assertEquals("Your household's books, on your own computer.", Messages.get(Language.ENGLISH, "app.tagline"))
    }

    @Test
    fun `French accents and apostrophes survive`() {
        assertEquals("Gestionnaire des finances du ménage", Messages.get(Language.FRENCH, "app.name"))
        assertEquals("Nom d'utilisateur", Messages.get(Language.FRENCH, "unlock.login"))
        assertEquals("Utilisez au moins 12 caractères.", Messages.get(Language.FRENCH, "create.password.tooShort", 12))
    }

    @Test
    fun `missing keys are visible`() {
        assertTrue(Messages.get(Language.ENGLISH, "no.such.key").contains("no.such.key"))
    }
}
