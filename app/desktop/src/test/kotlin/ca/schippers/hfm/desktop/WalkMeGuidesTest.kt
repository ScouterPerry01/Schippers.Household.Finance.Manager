package ca.schippers.hfm.desktop

import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.i18n.Manual
import ca.schippers.hfm.i18n.Messages
import ca.schippers.hfm.i18n.WalkMe
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HLP-03, HLP-04: every Walk-Me guide, in English and French, can be followed: the same guides and
 * steps in both languages, each step's screen and tab exist, each control it points at is marked
 * on some screen, its condition is one the panel knows, its manual link opens a page, and every
 * label it quotes in bold is a label of the app (desktop or phone) in that language.
 *
 * The phone app's labels come from a checkout of its own repository, Rann.Roost.Mobile, beside this
 * one (or at ROOST_MOBILE_DIR). Without it, as on CI, the labels of the phone's steps (those with no
 * screen on the computer) are not checked; the desktop's always are.
 */
class WalkMeGuidesTest {

    private val sources = File("src/main/kotlin").walk().filter { it.extension == "kt" }.toList()

    private val phoneRes: File? = listOfNotNull(System.getenv("ROOST_MOBILE_DIR"), "../../../Rann.Roost.Mobile")
        .map { File(it, "app/src/main/res") }.firstOrNull { it.isDirectory }

    /** The ids marked with walkTarget("…"), the dialogs' walkId = "…" with their Save buttons, and lists' addTarget = "…". */
    private val targets: Set<String> = sources.flatMapTo(sortedSetOf()) { file ->
        val text = file.readText()
        Regex("""walkTarget\("([a-zA-Z0-9.\-]+)"\)""").findAll(text).map { it.groupValues[1] } +
            Regex("""walkId = "([a-zA-Z0-9.\-]+)"""").findAll(text).flatMap { listOf(it.groupValues[1], it.groupValues[1] + ".save") } +
            Regex("""addTarget = "([a-zA-Z0-9.\-]+)"""").findAll(text).map { it.groupValues[1] }
    }

    @Test
    fun `both languages have the same groups, guides and steps`() {
        val en = WalkMe.groups(Language.ENGLISH)
        val fr = WalkMe.groups(Language.FRENCH)
        assertEquals(6, en.size, "six groups")
        assertEquals(en.map { g -> g.id to g.guides.map { it.id } }, fr.map { g -> g.id to g.guides.map { it.id } })
        for ((ge, gf) in en.zip(fr)) {
            for ((e, f) in ge.guides.zip(gf.guides)) {
                assertTrue(e.steps.isNotEmpty() && e.about.isNotBlank() && e.title != e.id, "${e.id} has a title, a line about it and steps")
                assertTrue(f.about.isNotBlank() && f.title != f.id, "${f.id} in French has a title and a line about it")
                assertEquals(e.steps.map { it.id }, f.steps.map { it.id }, "${e.id}: the same steps")
                for ((se, sf) in e.steps.zip(f.steps)) {
                    assertEquals(listOf(se.screen, se.tab, se.target, se.done, se.manual), listOf(sf.screen, sf.tab, sf.target, sf.done, sf.manual), "${e.id}#${se.id}: the same directions")
                    assertTrue(se.blocks.isNotEmpty() && sf.blocks.isNotEmpty(), "${e.id}#${se.id} says what to do in both languages")
                }
                assertEquals(e.steps.size, e.steps.map { it.id }.distinct().size, "${e.id}: step ids are unique")
            }
        }
        val all = en.flatMap { it.guides }.map { it.id }
        assertEquals(all.size, all.distinct().size, "each guide once")
    }

    @Test
    fun `every step's screen, tab, control and condition exist`() {
        val problems = ArrayList<String>()
        val screens = Section.entries.map { it.name }.toSet() + WELCOME
        for (language in Language.entries) for (guide in WalkMe.groups(language).flatMap { it.guides }) for (step in guide.steps) {
            val where = "${language.tag} ${guide.id}#${step.id}"
            step.screen?.let { if (it !in screens) problems += "$where: no screen $it" }
            step.tab?.let { tab ->
                if (step.screen == null) problems += "$where: a tab without a screen"
                if (!tabExists(tab)) problems += "$where: no tab $tab with walkTab"
            }
            step.target?.let { if (it !in targets) problems += "$where: no control marked $it" }
            step.done?.let { d ->
                when (d.kind) {
                    "screen" -> if (step.screen == null) problems += "$where: done when on screen, but no screen"
                    "shown" -> if (d.argument == null || (d.argument !in targets && !tabExists(d.argument!!))) problems += "$where: no control marked ${d.argument}"
                    "added" -> if (d.argument !in WALK_KINDS) problems += "$where: no kind ${d.argument}"
                    "open" -> Unit
                    else -> problems += "$where: unknown condition ${d.kind}"
                }
            }
            step.manual?.let { page ->
                val book = Manual.book(language)
                if (book.node(page) == null && (page.contains('#') || book.chapter(page) == null)) problems += "$where: no manual page $page"
            }
        }
        assertEquals(emptyList(), problems)
    }

    @Test
    fun `every label quoted in bold is on screen in that language`() {
        val problems = ArrayList<String>()
        for (language in Language.entries) {
            val labels = labels(language)
            for (guide in WalkMe.groups(language).flatMap { it.guides }) for (step in guide.steps) {
                if (phoneRes == null && step.screen == null && step.target == null) continue
                for (label in WalkMe.boldLabels(step.text)) {
                    if (normalize(label) !in labels) problems += "${language.tag} ${guide.id}#${step.id}: **$label**"
                }
            }
        }
        assertEquals(emptyList(), problems)
    }

    @Test
    fun `the guides of HLP-04 are all written`() {
        for (language in Language.entries) {
            val count = WalkMe.groups(language).sumOf { it.guides.size }
            assertTrue(count >= 32, "the 32 guides of HLP-04 in ${language.tag}, found $count")
        }
    }

    /** A tab id `Enum.NAME` names a tab enum of the app whose screen marks its tabs with walkTab. */
    private fun tabExists(id: String): Boolean {
        val enumName = id.substringBefore('.')
        val constant = id.substringAfter('.')
        val cls = runCatching { Class.forName("ca.schippers.hfm.desktop.$enumName") }.getOrNull() ?: return false
        if (cls.enumConstants?.any { (it as Enum<*>).name == constant } != true) return false
        return sources.any { f -> f.readText().let { "enum class $enumName" in it && "walkTab(" in it } }
    }

    /** Every label of the app in [language]: the desktop's messages and the phone's strings, normalized. */
    private fun labels(language: Language): Set<String> {
        val desktop = java.util.Properties().apply {
            Messages::class.java.getResourceAsStream("/hfm/i18n/messages_${language.tag}.properties")!!.reader(Charsets.UTF_8).use { load(it) }
        }.values.map { (it as String).replace("''", "'") }
        val phone = phoneRes?.let { File(it, if (language == Language.FRENCH) "values-fr/strings.xml" else "values/strings.xml").readText() }.orEmpty()
            .let { xml -> Regex("""<string name="[^"]+"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { it.groupValues[1].replace("\\'", "'").replace("\\\"", "\"") } }
        // A label with a value in it (To review ({0}), Paired with %1$s) counts for its words around the value,
        // and each choice of a {0,choice,...} counts as written.
        val choice = Regex("""\{\d+,choice,([^}]*)}""")
        val placeholder = Regex("""\s*(\{\d[^}]*}|%\d\$[sd])\s*""")
        return (desktop + phone).flatMap { text ->
            val options = choice.findAll(text).flatMap { m -> m.groupValues[1].split('|').map { it.substringAfter('#').substringAfter('<') } }.toList()
            (listOf(text) + options).flatMap { listOf(it) + it.split(placeholder) }
        }.map { normalize(it) }.filter { it.isNotEmpty() }.toSet()
    }

    private fun normalize(s: String): String = s.trim().removeSuffix("…").removeSuffix("...").removeSuffix(":").trim()
        .removeSuffix("(").trim().replace('’', '\'')
}
