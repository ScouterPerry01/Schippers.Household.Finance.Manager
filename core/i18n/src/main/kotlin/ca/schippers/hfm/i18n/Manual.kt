package ca.schippers.hfm.i18n

import java.text.Normalizer

/**
 * NFR-12: the user manual, a book about every screen, field and option, opened in its own window.
 * Unlike the short [HelpGuide], it has parts, chapters, sections and subsections, an index and a
 * search across all of it, in both languages.
 *
 * Each language has `hfm/manual/<lang>/contents.txt`: one line per part (`= part-id Part title`)
 * followed by the ids of its chapters, one per line, in reading order. Each chapter is
 * `hfm/manual/<lang>/<chapter-id>.md`, in a small Markdown subset:
 *
 * - `# Chapter title`; `## Section` and `### Subsection`, each with an optional `{#id}` at the end
 *   so other chapters can link to it. Sections and subsections are the contents' branches.
 * - Plain paragraphs; `- bullets` (indented two spaces for a second level); `1. numbered steps`.
 * - `- **Field name**: what it does`: a field or option. Its name goes into the index on its own.
 * - `> Tip:`, `> Note:` or `> Important:` (`> Conseil :`, `> Remarque :`, `> Important :`) callouts.
 * - `@index: term; another term` under a heading: index entries for that heading.
 * - Inline `**bold**` and links `[text](chapter-id)` or `[text](chapter-id#section-id)`.
 * - `![Caption](images/name.png)` on a line of its own: a picture of the app, from
 *   `hfm/manual/<lang>/images/`, shown with its caption below it.
 *
 * A chapter whose id is a screen's help id (such as `bills`) is the one opened from that screen.
 */
object Manual {

    sealed interface Block {
        data class Paragraph(val text: String) : Block
        data class Bullet(val text: String, val level: Int) : Block
        data class Step(val number: Int, val text: String) : Block
        data class Field(val name: String, val text: String) : Block
        data class Callout(val kind: CalloutKind, val text: String) : Block

        /** A picture: [path] is relative to the language's folder (`images/name.png`); [caption] is shown below it and read by screen readers. */
        data class Image(val path: String, val caption: String) : Block
    }

    enum class CalloutKind { TIP, NOTE, IMPORTANT }

    /**
     * A chapter (level 1), section (2) or subsection (3). [key] is unique in the manual:
     * `chapter` or `chapter#anchor`. [anchor] is the `{#id}` given, or one made from the title.
     */
    data class Node(
        val chapterId: String,
        val anchor: String?,
        val title: String,
        val level: Int,
        val blocks: List<Block>,
        val children: List<Node>,
        val indexTerms: List<String>,
    ) {
        val key: String get() = if (anchor == null) chapterId else "$chapterId#$anchor"

        /** This node and every node under it, in reading order. */
        fun flatten(): List<Node> = listOf(this) + children.flatMap { it.flatten() }

        /** The node's own words (not its children's), for searching. */
        val text: String by lazy {
            (listOf(title) + blocks.map { b ->
                when (b) {
                    is Block.Paragraph -> b.text
                    is Block.Bullet -> b.text
                    is Block.Step -> b.text
                    is Block.Field -> b.name + ": " + b.text
                    is Block.Callout -> b.text
                    is Block.Image -> b.caption
                }
            }).joinToString("\n") { plain(it) }
        }
    }

    data class Part(val id: String, val title: String, val chapters: List<Node>)

    /** One index term and every place it is found, each place a node's [Node.key] and its path for display. */
    data class IndexEntry(val term: String, val places: List<Place>)

    data class Place(val key: String, val path: String)

    data class Hit(val node: Node, val path: String, val snippet: String)

    class Book(val language: Language, val parts: List<Part>) {
        val chapters: List<Node> = parts.flatMap { it.chapters }
        private val byKey: Map<String, Node> = chapters.flatMap { it.flatten() }.associateBy { it.key }
        private val chapterOf: Map<String, Node> = chapters.associateBy { it.chapterId }

        /** The node for [key] (`chapter` or `chapter#anchor`), or null. */
        fun node(key: String): Node? = byKey[key]

        fun chapter(id: String): Node? = chapterOf[id]

        /** Every node in reading order: chapters, then their sections, part by part. */
        val nodes: List<Node> get() = chapters.flatMap { it.flatten() }

        /** "Chapter › Section › Subsection" for [node]. */
        fun path(node: Node): String {
            val chapter = chapterOf[node.chapterId] ?: return node.title
            if (node.level == 1) return chapter.title
            val trail = ArrayList<String>()
            fun walk(n: Node): Boolean {
                trail += n.title
                if (n === node) return true
                for (c in n.children) if (walk(c)) return true
                trail.removeAt(trail.size - 1)
                return false
            }
            walk(chapter)
            return trail.joinToString(" › ")
        }

        /** The chapter before and after [chapterId] in reading order. */
        fun neighbours(chapterId: String): Pair<Node?, Node?> {
            val i = chapters.indexOfFirst { it.chapterId == chapterId }
            if (i < 0) return null to null
            return chapters.getOrNull(i - 1) to chapters.getOrNull(i + 1)
        }

        /**
         * The index: every `@index` term and every field name, alphabetical (accents ignored),
         * each with the places it appears.
         */
        val index: List<IndexEntry> by lazy {
            val terms = LinkedHashMap<String, Pair<String, LinkedHashMap<String, Place>>>()
            for (node in nodes) {
                val found = node.indexTerms + node.blocks.filterIsInstance<Block.Field>().map { it.name }
                for (term in found) {
                    val clean = term.trim().trimEnd(':').trim()
                    if (clean.isEmpty()) continue
                    val (_, places) = terms.getOrPut(fold(clean)) { clean to LinkedHashMap() }
                    places.putIfAbsent(node.key, Place(node.key, path(node)))
                }
            }
            terms.values.map { (term, places) -> IndexEntry(term, places.values.toList()) }
                .sortedWith(compareBy({ fold(it.term) }, { it.term }))
        }

        /**
         * Nodes with a word starting with each word of [query], accents and case ignored; nodes
         * with the words in their title come first. Each hit has the line that matched.
         */
        fun search(query: String): List<Hit> {
            val words = patterns(query)
            if (words.isEmpty()) return emptyList()
            return nodes.mapNotNull { n ->
                val text = fold(n.text)
                if (!words.all { it.containsMatchIn(text) }) return@mapNotNull null
                val line = n.text.lines().drop(1).firstOrNull { l -> words.any { it.containsMatchIn(fold(l)) } } ?: n.title
                Triple(n, line, words.count { it.containsMatchIn(fold(n.title)) })
            }.sortedByDescending { it.third }.map { (n, line, _) -> Hit(n, path(n), line.take(200)) }
        }

        /** True when [text] has a word starting with each word of [query], accents and case ignored: "rrsp" finds "RRSPs", "reer" does not find "créer". */
        fun matches(text: String, query: String): Boolean = patterns(query).let { w -> w.isNotEmpty() && fold(text).let { t -> w.all { it.containsMatchIn(t) } } }
    }

    private val cache = HashMap<Language, Book>()

    /** The manual in [language]. */
    fun book(language: Language): Book = synchronized(cache) { cache.getOrPut(language) { load(language) } }

    private fun load(language: Language): Book {
        val contents = resource(language, "contents.txt") ?: return Book(language, emptyList())
        val parts = ArrayList<Part>()
        var partId: String? = null
        var partTitle = ""
        val chapters = ArrayList<Node>()
        fun flush() {
            partId?.let { parts += Part(it, partTitle, chapters.toList()) }
            chapters.clear()
        }
        for (raw in contents.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("//")) continue
            if (line.startsWith("= ")) {
                flush()
                val rest = line.removePrefix("= ").trim()
                partId = rest.substringBefore(' ')
                partTitle = rest.substringAfter(' ', rest).trim()
            } else {
                resource(language, "$line.md")?.let { chapters += parse(line, it) }
            }
        }
        flush()
        return Book(language, parts)
    }

    private fun resource(language: Language, name: String): String? =
        Manual::class.java.getResourceAsStream("/hfm/manual/${language.tag}/$name")?.use { it.readBytes().decodeToString() }

    /** The bytes of an [Block.Image]'s picture in [language], or null when it is missing. */
    fun image(language: Language, path: String): ByteArray? =
        if (IMAGE_PATH.matches(path)) Manual::class.java.getResourceAsStream("/hfm/manual/${language.tag}/$path")?.use { it.readBytes() } else null

    private val HEADING_ID = Regex("""\s*\{#([a-z0-9-]+)}\s*$""")
    private val STEP = Regex("""^(\d+)\.\s+(.*)$""")
    private val FIELD = Regex("""^\*\*(.+?)\*\*\s*(?::|—|–)?\s*(.*)$""")
    /** A picture's path: a PNG in the language's images folder. */
    val IMAGE_PATH = Regex("""images/[a-z0-9-]+\.png""")
    private val IMAGE = Regex("""^!\[([^\]]+)]\((${IMAGE_PATH.pattern})\)$""")
    private val CALLOUT = Regex("""^(Tip|Note|Important|Conseil|Remarque|Attention)\s*:\s*(.*)$""", RegexOption.IGNORE_CASE)

    /** Parses one chapter's Markdown; [id] is the chapter's id. */
    fun parse(id: String, text: String): Node {
        class Draft(val anchor: String?, val title: String, val level: Int) {
            val blocks = ArrayList<Block>()
            val children = ArrayList<Draft>()
            val index = ArrayList<String>()
            fun build(): Node = Node(id, anchor, title, level, blocks.toList(), children.map { it.build() }, index.toList())
        }
        var chapter = Draft(null, id, 1)
        val stack = ArrayList<Draft>().apply { add(chapter) }
        val paragraph = StringBuilder()
        var callout: Pair<CalloutKind, StringBuilder>? = null
        val anchors = HashSet<String>()
        fun current() = stack.last()
        fun flush() {
            if (paragraph.isNotBlank()) current().blocks += Block.Paragraph(paragraph.toString().trim())
            paragraph.clear()
            // "> Tip: the buttons..." reads as "Tip" above "The buttons...".
            callout?.let { (kind, sb) -> current().blocks += Block.Callout(kind, sb.toString().trim().replaceFirstChar { it.uppercase() }) }
            callout = null
        }
        fun heading(level: Int, raw: String) {
            flush()
            val explicit = HEADING_ID.find(raw)?.groupValues?.get(1)
            val title = raw.replace(HEADING_ID, "").trim()
            var anchor = explicit ?: slug(title)
            if (explicit == null) { var n = 2; val base = anchor; while (anchor in anchors) anchor = "$base-${n++}" }
            anchors += anchor
            val node = Draft(anchor, title, level)
            while (stack.size > 1 && stack.last().level >= level) stack.removeAt(stack.size - 1)
            stack.last().children += node
            stack += node
        }
        for (raw in text.lines()) {
            val line = raw.trimEnd()
            val trimmed = line.trimStart()
            when {
                line.startsWith("# ") -> { flush(); chapter = Draft(null, line.removePrefix("# ").trim(), 1).also { stack.clear(); stack += it } }
                line.startsWith("### ") -> heading(3, line.removePrefix("### "))
                line.startsWith("## ") -> heading(2, line.removePrefix("## "))
                trimmed.startsWith("@index:") -> {
                    flush()
                    current().index += trimmed.removePrefix("@index:").split(';').map { it.trim() }.filter { it.isNotEmpty() }
                }
                IMAGE.matches(trimmed) && line == trimmed -> {
                    flush()
                    val m = IMAGE.find(trimmed)!!
                    current().blocks += Block.Image(m.groupValues[2], m.groupValues[1].trim())
                }
                trimmed.startsWith("> ") || trimmed == ">" -> {
                    val body = trimmed.removePrefix(">").trim()
                    val open = callout
                    if (open == null) {
                        flush()
                        val m = CALLOUT.find(body)
                        val kind = when (m?.groupValues?.get(1)?.lowercase()) {
                            "tip", "conseil" -> CalloutKind.TIP
                            "important", "attention" -> CalloutKind.IMPORTANT
                            else -> CalloutKind.NOTE
                        }
                        callout = kind to StringBuilder(m?.groupValues?.get(2) ?: body)
                    } else {
                        open.second.append(if (body.isEmpty()) "\n" else " $body")
                    }
                }
                trimmed.startsWith("- ") -> {
                    flush()
                    val level = if (line.length - trimmed.length >= 2) 2 else 1
                    val body = trimmed.removePrefix("- ").trim()
                    val field = if (level == 1) FIELD.find(body) else null
                    current().blocks += if (field != null) Block.Field(field.groupValues[1].trim(), field.groupValues[2].trim())
                    else Block.Bullet(body, level)
                }
                STEP.matches(trimmed) && paragraph.isEmpty() -> {
                    flush()
                    val m = STEP.find(trimmed)!!
                    current().blocks += Block.Step(m.groupValues[1].toInt(), m.groupValues[2].trim())
                }
                line.isBlank() -> flush()
                else -> {
                    // A line under a bullet, step or field continues it.
                    val blocks = current().blocks
                    val continued = if (paragraph.isEmpty() && callout == null && raw.startsWith("  ")) {
                        when (val last = blocks.lastOrNull()) {
                            is Block.Bullet -> last.copy(text = last.text + " " + trimmed)
                            is Block.Step -> last.copy(text = last.text + " " + trimmed)
                            is Block.Field -> last.copy(text = last.text + " " + trimmed)
                            else -> null
                        }
                    } else {
                        null
                    }
                    if (continued != null) blocks[blocks.size - 1] = continued
                    else paragraph.append(if (paragraph.isEmpty()) "" else " ").append(trimmed)
                }
            }
        }
        flush()
        return chapter.build()
    }

    /** A heading's anchor made from its title: lower case, accents dropped, words joined by hyphens. */
    fun slug(title: String): String = fold(title).replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /** Links in a block's text: `[text](target)`. */
    val LINK = Regex("""\[([^\]]+)]\(([a-z0-9-]+(?:#[a-z0-9-]+)?)\)""")

    /** [text] without its Markdown marks: bold and links reduced to their words. */
    fun plain(text: String): String = text.replace(LINK) { it.groupValues[1] }.replace("**", "")

    /** Each word of [query] (two letters or more), to be found at the start of a word. */
    private fun patterns(query: String): List<Regex> =
        fold(query).split(Regex("\\s+")).filter { it.length >= 2 }.map { Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(it)) }

    fun fold(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
