package ca.schippers.hfm.domain

/**
 * A message for the user carried as a plain String through layers that cannot know the user's
 * language, such as import warnings: a message key from core/i18n and its values, marked and
 * joined by invisible control characters. The app shows it with [decode] in the user's language;
 * plain text (not made by [of]) passes through unchanged. A value may itself be a [UserText], one
 * level deep, such as the reason a line was refused.
 */
object UserText {
    private const val START = '\u001E'
    private const val SEP = '\u001F'
    private const val INNER = '\u001D'

    fun of(key: String, vararg args: Any?): String =
        START + (listOf(key) + args.map { a -> a.toString().let { if (it.startsWith(START)) it.replace(SEP, INNER) else it.replace(SEP, ' ') } }).joinToString(SEP.toString())

    /** The key and values of [text], each value already shown with [render]; null for plain text. */
    fun decode(text: String, render: (key: String, args: List<String>) -> String): String? {
        if (!text.startsWith(START)) return null
        val parts = text.drop(1).split(SEP)
        val args = parts.drop(1).map { a -> if (a.startsWith(START)) decode(a.replace(INNER, SEP), render) ?: a else a }
        return render(parts.first(), args)
    }
}
