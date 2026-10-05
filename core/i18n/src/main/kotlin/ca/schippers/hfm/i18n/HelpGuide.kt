package ca.schippers.hfm.i18n

import java.text.Normalizer

/**
 * NFR-12: the built-in user guide, one short topic per screen and a few general ones, in both
 * languages. Topics are bundled as `hfm/help/<lang>/<topic>.md` in a small Markdown subset:
 * "# Title", "## Subheading", "- bullet" and plain paragraphs.
 */
object HelpGuide {

    /** The general topics, shown first, before one topic per screen. */
    val GENERAL = listOf("getting-started", "privacy", "phone-transfer", "shortcuts")

    sealed interface Block {
        data class Heading(val text: String) : Block
        data class Paragraph(val text: String) : Block
        data class Bullet(val text: String) : Block
    }

    data class Topic(val id: String, val title: String, val blocks: List<Block>) {
        /** The topic's words, for searching. */
        val text: String get() = (listOf(title) + blocks.map { b -> when (b) { is Block.Heading -> b.text; is Block.Paragraph -> b.text; is Block.Bullet -> b.text } }).joinToString("\n")
    }

    data class Hit(val topic: Topic, val snippet: String)

    private val cache = HashMap<Pair<Language, String>, Topic?>()

    /** The topic [id] in [language], falling back to English; null when there is none. */
    fun topic(language: Language, id: String): Topic? = synchronized(cache) {
        cache.getOrPut(language to id) { load(language, id) ?: if (language != Language.ENGLISH) load(Language.ENGLISH, id) else null }
    }

    /** Every topic among [ids] that exists, in that order. */
    fun topics(language: Language, ids: List<String>): List<Topic> = ids.mapNotNull { topic(language, it) }

    /**
     * Topics whose words contain every word of [query], accents and case ignored, best first (title
     * matches first), each with the line that matched.
     */
    fun search(language: Language, ids: List<String>, query: String): List<Hit> {
        val words = fold(query).split(Regex("\\s+")).filter { it.length >= 2 }
        if (words.isEmpty()) return emptyList()
        return topics(language, ids).mapNotNull { t ->
            val text = fold(t.text)
            if (!words.all { it in text }) return@mapNotNull null
            val line = t.text.lines().firstOrNull { l -> words.any { it in fold(l) } } ?: t.title
            Triple(t, line, words.count { it in fold(t.title) })
        }.sortedByDescending { it.third }.map { (t, line, _) -> Hit(t, line.removePrefix("- ").removePrefix("## ").removePrefix("# ").take(160)) }
    }

    internal fun parse(id: String, text: String): Topic {
        var title = id
        val blocks = ArrayList<Block>()
        val paragraph = StringBuilder()
        fun flush() {
            if (paragraph.isNotBlank()) blocks += Block.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }
        for (raw in text.lines()) {
            val line = raw.trimEnd()
            when {
                line.startsWith("# ") -> { flush(); title = line.removePrefix("# ").trim() }
                line.startsWith("## ") -> { flush(); blocks += Block.Heading(line.removePrefix("## ").trim()) }
                line.startsWith("- ") -> { flush(); blocks += Block.Bullet(line.removePrefix("- ").trim()) }
                line.isBlank() -> flush()
                else -> paragraph.append(if (paragraph.isEmpty()) "" else " ").append(line.trim())
            }
        }
        flush()
        return Topic(id, title, blocks)
    }

    private fun load(language: Language, id: String): Topic? =
        HelpGuide::class.java.getResourceAsStream("/hfm/help/${language.tag}/$id.md")?.use { parse(id, it.readBytes().decodeToString()) }

    private fun fold(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
