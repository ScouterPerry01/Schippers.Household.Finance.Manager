package ca.schippers.hfm.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** NFR-12: the manual reads its Markdown subset, and both languages have the same chapters, sections and working links. */
class ManualTest {

    private val sample = """
        # Bills

        Intro line one
        and its second line.

        ## Add a bill {#add-bill}
        @index: subscription; recurring payment

        1. Choose **Add a bill**.
        2. Fill in the form,
          then save.

        - **Amount**: what you pay, see [budgets](budgets#limits).
        - **Remind me (days before)** : days, such as 7, 1.
        - a plain point
          - a second-level point

        > Tip: pay early.
        > It is free.

        ![The Bills screen](images/bills.png)

        ### The form {#form}

        Text.

        ## Pay
        Text.
    """.trimIndent()

    @Test
    fun `chapters, sections, fields, steps, callouts and index terms are read`() {
        val c = Manual.parse("bills", sample)
        assertEquals("Bills", c.title)
        assertEquals(listOf(Manual.Block.Paragraph("Intro line one and its second line.")), c.blocks)
        assertEquals(listOf("add-bill", "pay"), c.children.map { it.anchor })
        val add = c.children[0]
        assertEquals(listOf("subscription", "recurring payment"), add.indexTerms)
        assertEquals(Manual.Block.Step(2, "Fill in the form, then save."), add.blocks[1])
        assertEquals(Manual.Block.Field("Amount", "what you pay, see [budgets](budgets#limits)."), add.blocks[2])
        assertEquals(Manual.Block.Field("Remind me (days before)", "days, such as 7, 1."), add.blocks[3])
        assertEquals(Manual.Block.Bullet("a second-level point", 2), add.blocks[5])
        assertEquals(Manual.Block.Callout(Manual.CalloutKind.TIP, "Pay early. It is free."), add.blocks[6])
        assertEquals(Manual.Block.Image("images/bills.png", "The Bills screen"), add.blocks[7])
        assertEquals("bills#form", add.children.single().key)
        assertEquals(3, add.children.single().level)
        assertEquals("pay", c.children[1].anchor, "a heading without an id gets one from its title")
        assertEquals("Amount: what you pay, see budgets.", c.children[0].text.lines().first { it.startsWith("Amount") })
    }

    @Test
    fun `both languages list the same parts and chapters, and every chapter is written`() {
        val en = Manual.book(Language.ENGLISH)
        val fr = Manual.book(Language.FRENCH)
        assertTrue(en.chapters.size > 10, "the manual has its chapters")
        assertEquals(en.parts.map { it.id }, fr.parts.map { it.id })
        assertEquals(en.chapters.map { it.chapterId }, fr.chapters.map { it.chapterId })
        for (language in Language.entries) {
            val listed = resource(language, "contents.txt")!!.lines().map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("//") && !it.startsWith("= ") }
            val missing = listed.filter { resource(language, "$it.md") == null }
            assertEquals(emptyList(), missing, "chapters missing in $language")
            for (c in Manual.book(language).chapters) {
                assertTrue(c.title != c.chapterId && (c.blocks.isNotEmpty() || c.children.isNotEmpty()), "${c.chapterId} in $language has a title and a body")
            }
        }
    }

    @Test
    fun `every heading has an explicit id, the same in English and French`() {
        val heading = Regex("""^#{2,3} (.*)$""")
        for (language in Language.entries) {
            for (c in Manual.book(language).chapters) {
                val bare = resource(language, "${c.chapterId}.md")!!.lines().filter { l -> heading.matches(l) && !l.trimEnd().endsWith("}") }
                assertEquals(emptyList(), bare, "headings without {#id} in ${c.chapterId} ($language)")
            }
        }
        val en = Manual.book(Language.ENGLISH)
        val fr = Manual.book(Language.FRENCH)
        for (c in en.chapters) {
            val other = assertNotNull(fr.chapter(c.chapterId))
            assertEquals(c.flatten().map { it.key }, other.flatten().map { it.key }, "sections of ${c.chapterId} in English and French")
        }
    }

    @Test
    fun `every link leads to a chapter or section that exists`() {
        for (language in Language.entries) {
            val book = Manual.book(language)
            val broken = book.nodes.flatMap { n ->
                n.blocks.flatMap { b -> Manual.LINK.findAll(blockText(b)).map { it.groupValues[2] }.toList() }
                    .filter { book.node(it) == null }.map { "${n.key} -> $it" }
            }
            assertEquals(emptyList(), broken, "broken links in $language")
        }
    }

    @Test
    fun `the index and the search find pages in the reader's language, accents ignored`() {
        val en = Manual.book(Language.ENGLISH)
        val fr = Manual.book(Language.FRENCH)
        assertTrue(en.index.size > 100, "fields and index terms make the index")
        assertTrue(en.index.zipWithNext().all { (a, b) -> Manual.fold(a.term) <= Manual.fold(b.term) }, "the index is alphabetical")
        assertTrue(en.search("reconcile statement").isNotEmpty())
        assertTrue(fr.search("releve").isNotEmpty(), "relevé found without its accent")
        assertTrue(en.search("zzzqqq").isEmpty())
        assertTrue(fr.matches("Créer le ménage", "creer") && !fr.matches("Créer le ménage", "reer"), "words are matched from their start")
        assertTrue(en.matches("RRSP contributions", "rrsp contrib"))
        val hit = en.search("recovery key").first()
        assertTrue(en.path(hit.node).isNotBlank())
    }

    @Test
    fun `every picture exists in both languages, and every picture file is used`() {
        val image = Regex("""^!\[.*""")
        val used = HashMap<Language, Set<String>>()
        for (language in Language.entries) {
            val book = Manual.book(language)
            // A line that starts like a picture but is not read as one (a typo in the path or the caption).
            for (c in book.chapters) {
                val lines = resource(language, "${c.chapterId}.md")!!.lines().count { image.matches(it.trim()) }
                assertEquals(lines, c.flatten().sumOf { n -> n.blocks.count { it is Manual.Block.Image } }, "every picture line in ${c.chapterId} ($language) is read as a picture")
            }
            val pictures = book.nodes.flatMap { n -> n.blocks.filterIsInstance<Manual.Block.Image>().map { n.key to it } }
            for ((key, picture) in pictures) {
                assertTrue(picture.caption.isNotBlank(), "$key has a caption")
                val bytes = assertNotNull(Manual.image(language, picture.path), "${picture.path} in $key ($language) exists")
                assertTrue(bytes.size > 8 && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte(), "${picture.path} ($language) is a PNG")
            }
            used[language] = pictures.map { it.second.path }.toSet()
            val folder = java.io.File(assertNotNull(javaClass.getResource("/hfm/manual/${language.tag}/images"), "the $language images folder").toURI())
            val files = folder.listFiles().orEmpty().filter { it.isFile }.map { "images/${it.name}" }.toSet()
            assertEquals(emptySet(), files - used.getValue(language), "picture files no chapter shows ($language)")
        }
        // Pictures the other language shows must be there too, so both manuals stay alike.
        for (language in Language.entries) for (other in Language.entries) {
            val missing = used.getValue(other).filter { Manual.image(language, it) == null }
            assertEquals(emptyList(), missing, "pictures shown in $other but missing in $language")
        }
        // Light and dark pictures together: they all ship inside the app.
        val total = Language.entries.associateWith { l ->
            used.getValue(l).sumOf { (Manual.image(l, it)?.size ?: 0) + (dark(l, it)?.size ?: 0).toLong() }
        }
        for ((language, bytes) in total) assertTrue(bytes < MAX_BYTES, "the $language pictures stay under ${MAX_BYTES / 1024 / 1024} MB: $bytes bytes")
    }

    @Test
    fun `every desktop picture has its dark twin, and every dark picture a light one`() {
        for (language in Language.entries) {
            val folder = java.io.File(assertNotNull(javaClass.getResource("/hfm/manual/${language.tag}/images"), "the $language images folder").toURI())
            val light = folder.listFiles().orEmpty().filter { it.isFile }.map { it.name }.toSet()
            val dark = java.io.File(folder, "dark").listFiles().orEmpty().map { it.name }.toSet()
            assertEquals(emptySet(), dark - light, "dark pictures with no light one of the same name ($language)")
            // The phone's pictures come from the emulator in light colours only; the one of the dark colours is dark already.
            val desktop = light.filter { !it.startsWith("phone-") && it !in LIGHT_ONLY }.toSet()
            assertEquals(emptySet(), desktop - dark, "desktop pictures with no dark twin ($language)")
            for (name in dark) {
                val bytes = assertNotNull(dark(language, "images/$name"))
                assertTrue(bytes.size > 8 && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte(), "dark/$name ($language) is a PNG")
                assertTrue(Manual.image(language, "images/$name", dark = true).contentEquals(bytes), "dark/$name ($language) is shown in dark mode")
            }
        }
        assertTrue(Manual.image(Language.ENGLISH, "images/phone-capture.png", dark = true).contentEquals(Manual.image(Language.ENGLISH, "images/phone-capture.png")), "a picture with no dark twin shows the light one")
    }

    private fun dark(language: Language, path: String): ByteArray? =
        javaClass.getResourceAsStream("/hfm/manual/${language.tag}/${Manual.darkPath(path)}")?.use { it.readBytes() }

    private companion object {
        /** The pictures with no dark twin besides the phone's: the one of the dark colours, dark already. */
        val LIGHT_ONLY = setOf("display-dark.png")
        const val MAX_BYTES = 8L * 1024 * 1024
    }

    private fun blockText(b: Manual.Block): String = when (b) {
        is Manual.Block.Paragraph -> b.text
        is Manual.Block.Bullet -> b.text
        is Manual.Block.Step -> b.text
        is Manual.Block.Field -> b.text
        is Manual.Block.Callout -> b.text
        is Manual.Block.Image -> b.caption
    }

    private fun resource(language: Language, name: String): String? =
        javaClass.getResourceAsStream("/hfm/manual/${language.tag}/$name")?.use { it.readBytes().decodeToString() }
}
