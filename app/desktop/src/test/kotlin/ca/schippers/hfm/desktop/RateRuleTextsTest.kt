package ca.schippers.hfm.desktop

import ca.schippers.hfm.calc.rules.Rules
import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.i18n.Messages
import kotlin.test.Test
import kotlin.test.assertEquals

/** Rates and rules: every rule and area is named and explained in English and French (NFR-06). */
class RateRuleTextsTest {

    @Test
    fun `every rule has its name and hint, and every area its name, in both languages`() {
        val keys = Rules.catalogue.flatMap { listOf("rateRule.${it.key}", "rateRule.${it.key}.hint") } +
            Rules.catalogue.map { "rateArea.${it.area}" }.distinct()
        for (language in Language.entries) {
            assertEquals(emptyList(), keys.filter { it !in Messages.keys(language) }, "missing in $language")
        }
    }
}
