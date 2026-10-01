package ca.schippers.hfm.i18n

import java.text.MessageFormat
import java.util.Locale
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/** The two interface languages (NFR-06). */
enum class Language(val tag: String) {
    ENGLISH("en"),
    FRENCH("fr"),
    ;

    /** Canadian locale for this language, used for dates, numbers and money (NFR-07). */
    val locale: Locale get() = Locale.forLanguageTag("$tag-CA")

    companion object {
        fun of(locale: Locale): Language = if (locale.language == "fr") FRENCH else ENGLISH
    }
}

/**
 * User-facing text shared by both apps, read from UTF-8 `messages_<lang>.properties` files.
 * Every user-facing string goes through here (or Android's strings.xml) from the first commit.
 * A missing translation falls back to English; a missing key shows the key itself so it is noticed.
 */
object Messages {
    private val bundles = ConcurrentHashMap<Language, Properties>()

    fun get(language: Language, key: String, vararg args: Any): String {
        val pattern = bundle(language).getProperty(key)
            ?: bundle(Language.ENGLISH).getProperty(key)
            ?: return "⟦$key⟧"
        // Always format, so an apostrophe is written '' in every message, with or without arguments.
        return MessageFormat(pattern, language.locale).format(args)
    }

    fun keys(language: Language): Set<String> = bundle(language).stringPropertyNames()

    private fun bundle(language: Language): Properties = bundles.getOrPut(language) {
        val stream = Messages::class.java.getResourceAsStream("/hfm/i18n/messages_${language.tag}.properties")
            ?: error("Missing message bundle for $language")
        Properties().apply { stream.reader(Charsets.UTF_8).use { load(it) } }
    }
}
