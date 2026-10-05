package ca.schippers.hfm.books

import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.sync.PhoneContact
import ca.schippers.hfm.sync.RefContact
import ca.schippers.hfm.sync.RefContactDetail
import kotlinx.serialization.json.Json

/** CON-07: a contact made on a phone, waiting in [groupId] for the user to review it. */
data class ReceivedContact(
    val id: String,
    val groupId: String,
    val deviceName: String?,
    val receivedAt: Long,
    val contact: PhoneContact,
)

/**
 * CON-07: contacts and the phone. The contacts the phone's user can see go to the phone, without
 * account or client numbers ([forPhone]); contacts made on the phone wait for review, in the
 * ledger of the group the phone's captures go to, until the user adds them as a new contact, adds
 * their details to an existing one, or discards them. Nothing becomes a contact without review.
 */
class PhoneContactService internal constructor(private val books: Books) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

    // --- To the phone ---------------------------------------------------------------------------

    /** The contacts sent to the phone: those the user can see, not archived, never their numbers. */
    fun forPhone(): List<RefContact> {
        val all = books.contacts.list(ContactFilter(includeArchived = true))
        val names = all.associate { it.id to it.name }
        val sent = all.filter { !it.archived }
        val sentIds = sent.map { it.id }.toSet()
        val who = runCatching { books.members.list(includeArchived = true).associate { it.id to it.displayName } }.getOrDefault(emptyMap()) +
            runCatching { books.pets.list(includeArchived = true).associate { it.id to it.name } }.getOrDefault(emptyMap())
        fun details(list: List<ContactDetail>) = list.map { RefContactDetail(it.value, it.label) }
        return sent.map { c ->
            RefContact(
                id = c.id, name = c.name, person = c.person,
                organizationId = c.organizationId?.takeIf { c.person && it in sentIds },
                organizationName = c.organizationId?.takeIf { c.person }?.let(names::get),
                jobTitle = c.jobTitle.takeIf { c.person },
                kinds = c.kinds.sorted().map { it.name }, purpose = c.purpose,
                forWhom = c.memberIds.mapNotNull(who::get).sortedBy(SearchService::fold),
                phones = details(c.phones), emails = details(c.emails),
                address = c.address, website = c.website, hours = c.hours, notes = c.notes,
            )
        }
    }

    // --- From the phone -------------------------------------------------------------------------

    /** Stores a contact made on the phone for review; called by the transfer, once per contact. */
    internal fun receive(groupId: String, deviceId: String, contact: PhoneContact, now: Long) {
        validate(contact.name.isNotBlank(), "error.nameRequired")
        val group = books.group(groupId)
        books.require(group, PermissionLevel.CAPTURE_ONLY)
        val clean = contact.copy(
            name = contact.name.trim().take(MAX_TEXT),
            phones = contact.phones.filter { it.value.isNotBlank() }.take(MAX_DETAILS),
            emails = contact.emails.filter { it.value.isNotBlank() }.take(MAX_DETAILS),
        )
        books.ledger(group).phoneContactsQueries.insertPhoneContact(contact.id, deviceId, now, json.encodeToString(PhoneContact.serializer(), clean))
        books.session.audit("RECEIVE", "phoneContact", contact.id)
    }

    /** The contacts waiting for review, oldest first, in the groups the user may change. */
    fun waiting(): List<ReceivedContact> {
        val devices = runCatching { books.sync.devices().associate { it.id to it.name } }.getOrDefault(emptyMap())
        return editableGroups().flatMap { g ->
            books.ledger(g).phoneContactsQueries.phoneContacts().executeAsList().mapNotNull { row ->
                runCatching { json.decodeFromString(PhoneContact.serializer(), row.content) }.getOrNull()
                    ?.let { ReceivedContact(row.id, g.id, devices[row.device_id], row.received_at, it) }
            }
        }.sortedBy { it.receivedAt }
    }

    /** How many wait for review, for the menu. */
    fun count(): Int = editableGroups().sumOf { books.ledger(it).phoneContactsQueries.countPhoneContacts().executeAsOne().toInt() }

    /**
     * The existing contacts it may duplicate: the same name (accents and case ignored) or a phone
     * number in common. Only contacts the user may change are offered, since the details are added there.
     */
    fun duplicates(received: ReceivedContact): List<Contact> {
        val c = received.contact
        val name = SearchService.fold(c.name.trim())
        val phones = c.phones.mapNotNull { ContactService.phoneKey(it.value) }.toSet()
        val editable = editableGroups().map { it.id }.toSet()
        return books.contacts.list(ContactFilter(includeArchived = true)).filter { e ->
            e.groupId in editable && (SearchService.fold(e.name.trim()) == name || e.phones.any { ContactService.phoneKey(it.value) in phones })
        }
    }

    /**
     * The new contact it would become in [groupId]: its kinds, what for, phones and emails with
     * their labels, address and notes; a person's organization when the user can see one of that
     * name, otherwise its name in the notes.
     */
    fun draft(received: ReceivedContact, groupId: String): Contact {
        val c = received.contact
        val organization = organization(c)
        val orgNote = c.organizationName?.trim()?.ifEmpty { null }?.takeIf { c.person && organization == null }?.let { books.text("contacts.fromPhone.organizationNote", it) }
        return Contact(
            id = "", groupId = groupId, name = c.name.trim(), person = c.person, organizationId = organization?.id,
            purpose = c.purpose?.trim()?.ifEmpty { null }, kinds = kinds(c),
            details = c.phones.map { ContactDetail(type = DetailType.PHONE, label = it.label, value = it.value.trim()) } +
                c.emails.map { ContactDetail(type = DetailType.EMAIL, label = it.label, value = it.value.trim()) },
            address = c.address?.trim()?.ifEmpty { null },
            notes = listOfNotNull(orgNote, c.notes?.trim()?.ifEmpty { null }).joinToString("\n").ifEmpty { null },
        )
    }

    /**
     * Adds it as a new contact ([contact], as the user completed it from [draft]) and removes it
     * from the review list.
     */
    fun add(id: String, contact: Contact): Contact {
        val (group, _) = locate(id)
        val saved = books.contacts.save(contact)
        books.ledger(group).phoneContactsQueries.deletePhoneContact(id)
        return saved
    }

    /**
     * Adds its details to the existing contact [contactId] instead: phones and emails not already
     * there, its kinds, and what for, address and organization where the contact has none; its
     * notes after the contact's own. Then removes it from the review list.
     */
    fun merge(id: String, contactId: String): Contact {
        val (group, received) = locate(id)
        val existing = books.contacts.get(contactId)
        val draft = draft(received, existing.groupId)
        val phones = existing.phones.map { ContactService.phoneKey(it.value) ?: it.value }.toSet()
        val emails = existing.emails.map { it.value.lowercase() }.toSet()
        val added = draft.details.filter { d ->
            when (d.type) {
                DetailType.PHONE -> (ContactService.phoneKey(d.value) ?: d.value) !in phones
                else -> d.value.lowercase() !in emails
            }
        }
        val notes = listOfNotNull(existing.notes, draft.notes?.takeIf { n -> existing.notes?.contains(n) != true }).joinToString("\n").ifEmpty { null }
        val saved = books.contacts.save(
            existing.copy(
                kinds = existing.kinds + draft.kinds,
                purpose = existing.purpose ?: draft.purpose,
                organizationId = existing.organizationId ?: draft.organizationId.takeIf { existing.person },
                details = existing.details + added,
                address = existing.address ?: draft.address,
                notes = notes,
            ),
        )
        books.ledger(group).phoneContactsQueries.deletePhoneContact(id)
        return saved
    }

    /** Removes it from the review list without making a contact. */
    fun discard(id: String) {
        val (group, _) = locate(id)
        books.ledger(group).phoneContactsQueries.deletePhoneContact(id)
        books.session.audit("DISCARD", "phoneContact", id)
    }

    private fun kinds(c: PhoneContact): Set<ContactKind> = c.kinds.mapNotNull { k -> runCatching { ContactKind.valueOf(k) }.getOrNull() }.toSet()

    /** The organization chosen on the phone, or one of the same name, among those the user can see. */
    private fun organization(c: PhoneContact): Contact? {
        if (!c.person) return null
        val organizations = books.contacts.list(ContactFilter(includeArchived = true)).filter { !it.person }
        return organizations.firstOrNull { it.id == c.organizationId }
            ?: c.organizationName?.trim()?.ifEmpty { null }?.let { n -> organizations.firstOrNull { SearchService.fold(it.name.trim()) == SearchService.fold(n) } }
    }

    private fun editableGroups(): List<GroupInfo> = books.groups().filter { it.level == PermissionLevel.EDIT }

    private fun locate(id: String): Pair<GroupInfo, ReceivedContact> {
        val received = waiting().firstOrNull { it.id == id } ?: throw AccessDeniedException("Contact from the phone not found or not accessible")
        return books.group(received.groupId) to received
    }

    private companion object {
        const val MAX_TEXT = 200
        const val MAX_DETAILS = 20
    }
}
