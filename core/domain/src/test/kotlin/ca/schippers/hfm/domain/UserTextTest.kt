package ca.schippers.hfm.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserTextTest {

    private val texts = mapOf("line" to "Ligne {0} : {1}", "why" to "refusé ({0})")

    private fun render(key: String, args: List<String>) =
        args.foldIndexed(texts.getValue(key)) { i, s, a -> s.replace("{$i}", a) }

    @Test
    fun `a key and its values come back in the user's language, one level deep`() {
        val text = UserText.of("line", 5, UserText.of("why", "no units"))
        assertEquals("Ligne 5 : refusé (no units)", UserText.decode(text, ::render))
    }

    @Test
    fun `plain text is not decoded`() {
        assertNull(UserText.decode("Line 5: skipped", ::render))
    }
}
