package ca.schippers.hfm.i18n

/**
 * HLP-03: Walk-Me guides, step-by-step walks through a common task, beside the app. Each language
 * has `hfm/walkme/<lang>/contents.txt` (one line per group, `= group-id Group title`, followed by
 * the ids of its guides in order) and one `hfm/walkme/<lang>/<guide-id>.md` per guide:
 *
 * ```
 * # Guide title
 * @about: One line saying what the guide does.
 *
 * ## Step title {#step-id}
 * @screen: BILLS BillsTab.ALL
 * @target: bills.add
 * @done: shown bill.form
 * @manual: bills#adding-a-bill
 *
 * What to do, in the manual's Markdown subset (paragraphs, bullets, numbered lines, **bold**).
 * ```
 *
 * The `@` lines are optional; the format is described for authors in docs/walkme-format.md.
 */
object WalkMe {

    /** When a step counts as done: [kind] is one of [CONDITIONS], [argument] what it needs. */
    data class Condition(val kind: String, val argument: String?) {
        override fun toString(): String = listOfNotNull(kind, argument).joinToString(" ")
    }

    data class Step(
        val id: String,
        val title: String,
        val blocks: List<Manual.Block>,
        /** The screen to open (a menu section's name, or WELCOME before a household is open). */
        val screen: String?,
        /** The tab to select on [screen], as `TabEnum.NAME`. */
        val tab: String?,
        /** The control to highlight. */
        val target: String?,
        val done: Condition?,
        /** A manual page, `chapter` or `chapter#section`. */
        val manual: String?,
    ) {
        /** Every text of the step, for checks: the title and the body's words with their marks. */
        val text: String get() = (listOf(title) + blocks.map { blockText(it) }).joinToString("\n")
    }

    data class Guide(val id: String, val title: String, val about: String, val steps: List<Step>)

    data class Group(val id: String, val title: String, val guides: List<Guide>)

    /** The conditions a step may wait for. */
    val CONDITIONS = setOf("screen", "shown", "added", "open")

    private val cache = HashMap<Language, List<Group>>()

    /** The guides in [language], by group. */
    fun groups(language: Language): List<Group> = synchronized(cache) { cache.getOrPut(language) { load(language) } }

    fun guide(language: Language, id: String): Guide? = groups(language).firstNotNullOfOrNull { g -> g.guides.firstOrNull { it.id == id } }

    private fun load(language: Language): List<Group> {
        val contents = resource(language, "contents.txt") ?: return emptyList()
        val groups = ArrayList<Group>()
        var id: String? = null
        var title = ""
        val guides = ArrayList<Guide>()
        fun flush() {
            id?.let { groups += Group(it, title, guides.toList()) }
            guides.clear()
        }
        for (raw in contents.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("//")) continue
            if (line.startsWith("= ")) {
                flush()
                val rest = line.removePrefix("= ").trim()
                id = rest.substringBefore(' ')
                title = rest.substringAfter(' ', rest).trim()
            } else {
                resource(language, "$line.md")?.let { guides += parse(line, it) }
            }
        }
        flush()
        return groups
    }

    private fun resource(language: Language, name: String): String? =
        WalkMe::class.java.getResourceAsStream("/hfm/walkme/${language.tag}/$name")?.use { it.readBytes().decodeToString() }

    private val STEP = Regex("""^##\s+(.+?)\s*\{#([a-z0-9-]+)}\s*$""")
    private val DIRECTIVE = Regex("""^@([a-z]+):\s*(.*)$""")

    /** Parses one guide's file; [id] is the guide's id. */
    fun parse(id: String, text: String): Guide {
        var title = id
        var about = ""
        val steps = ArrayList<Step>()
        var stepTitle: String? = null
        var stepId = ""
        val body = StringBuilder()
        val directives = HashMap<String, String>()
        fun flush() {
            val t = stepTitle ?: return
            val blocks = Manual.parse(id, body.toString()).blocks
            val screen = directives["screen"]?.split(Regex("\\s+"))
            val done = directives["done"]?.trim()?.takeIf { it.isNotEmpty() }?.let { d ->
                Condition(d.substringBefore(' '), d.substringAfter(' ', "").trim().takeIf { it.isNotEmpty() })
            }
            steps += Step(stepId, t, blocks, screen?.getOrNull(0), screen?.getOrNull(1), directives["target"], done, directives["manual"])
            body.clear()
            directives.clear()
        }
        for (raw in text.lines()) {
            val line = raw.trimEnd()
            when {
                line.startsWith("# ") -> title = line.removePrefix("# ").trim()
                line.startsWith("## ") -> {
                    flush()
                    val m = STEP.find(line) ?: error("Walk-Me $id: a step needs a title and an {#id}: $line")
                    stepTitle = m.groupValues[1]
                    stepId = m.groupValues[2]
                }
                DIRECTIVE.matches(line) -> {
                    val m = DIRECTIVE.find(line)!!
                    if (stepTitle == null && m.groupValues[1] == "about") about = m.groupValues[2].trim()
                    else directives[m.groupValues[1]] = m.groupValues[2].trim()
                }
                stepTitle != null -> body.append(raw).append('\n')
            }
        }
        flush()
        return Guide(id, title, about, steps)
    }

    /** The words of [text] between `**` marks: the labels a step quotes. */
    fun boldLabels(text: String): List<String> = Regex("""\*\*(.+?)\*\*""").findAll(text).map { it.groupValues[1] }.toList()

    private fun blockText(b: Manual.Block): String = when (b) {
        is Manual.Block.Paragraph -> b.text
        is Manual.Block.Bullet -> b.text
        is Manual.Block.Step -> b.text
        is Manual.Block.Field -> "**${b.name}** ${b.text}"
        is Manual.Block.Callout -> b.text
        is Manual.Block.Image -> b.caption
    }
}
