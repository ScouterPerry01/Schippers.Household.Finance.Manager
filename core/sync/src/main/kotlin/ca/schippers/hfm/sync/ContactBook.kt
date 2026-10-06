package ca.schippers.hfm.sync

import java.net.URLEncoder
import java.text.Normalizer

/**
 * CON-07 on the phone: the contacts from the computer as a list (people under their organization),
 * the search and kind filter, and the links that dial, write or open a map. Plain Kotlin, so it is
 * tested here and used as is by the Android app.
 */
object ContactBook {

    /** Lower case without accents, so "medecin" finds "Médecin". */
    fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "").lowercase()

    /**
     * The contacts matching [text] (name, what for, organization, job title, for whom, phones, emails,
     * address and notes; accents and case ignored; digits also match a phone written otherwise) and
     * having [kind] when one is chosen.
     */
    fun filter(contacts: List<RefContact>, text: String, kind: String?): List<RefContact> {
        val needle = fold(text.trim())
        val digits = needle.filter(Char::isDigit)
        val phoneLike = digits.length >= 3 && needle.all { it.isDigit() || it in " -.()+" }
        fun matches(c: RefContact) =
            (listOfNotNull(c.name, c.purpose, c.organizationName, c.jobTitle, c.address, c.notes) + c.forWhom + c.phones.map { it.value } + c.emails.map { it.value })
                .any { fold(it).contains(needle) } ||
                (phoneLike && c.phones.any { it.value.filter(Char::isDigit).contains(digits) })
        return contacts.filter { c -> (kind == null || kind in c.kinds) && (needle.isEmpty() || matches(c)) }
    }

    /**
     * The list's rows, by name: each organization followed by its people (indented by 1), then the
     * people whose organization is not in [contacts].
     */
    fun rows(contacts: List<RefContact>): List<Pair<RefContact, Int>> {
        val ids = contacts.map { it.id }.toSet()
        val sorted = contacts.sortedWith(compareBy({ fold(it.name) }, { it.id }))
        val under = sorted.filter { it.person && it.organizationId in ids }.groupBy { it.organizationId }
        return sorted.filter { !(it.person && it.organizationId in ids) }.flatMap { c -> listOf(c to 0) + under[c.id].orEmpty().map { it to 1 } }
    }

    /** The kinds the contacts have, for the filter, in the desktop's order of [order]. */
    fun kinds(contacts: List<RefContact>, order: List<String>): List<String> {
        val present = contacts.flatMap { it.kinds }.toSet()
        return order.filter { it in present } + present.filter { it !in order }.sorted()
    }

    /** The number for the dialer: digits, with a leading + and the extension marks kept. */
    fun telUri(phone: String): String = "tel:" + phone.filter { it.isDigit() || it == '+' || it == ',' || it == ';' || it == '*' || it == '#' }

    fun mailtoUri(email: String): String = "mailto:" + email.trim()

    /** The address for a map app: a search, so any address the user wrote is found. */
    fun geoUri(address: String): String = "geo:0,0?q=" + URLEncoder.encode(address.replace('\n', ' ').trim(), "UTF-8").replace("+", "%20")

    /**
     * A website with its https:// when the user wrote only the name. Only web addresses are opened:
     * another scheme (content://, file://, intent:) is read as a site's name, so a contact cannot
     * make the phone open something else (Phase 5 security review).
     */
    fun webUri(website: String): String = website.trim().let { w ->
        if (w.startsWith("https://", ignoreCase = true) || w.startsWith("http://", ignoreCase = true)) w else "https://" + w.substringAfter("://")
    }

    private val MARKS = Regex("\\p{M}+")
}
