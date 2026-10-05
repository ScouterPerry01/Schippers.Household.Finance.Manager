package ca.schippers.hfm.books

import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.data.ledger.Contact as ContactRow

/** CON-01: what a contact is; one contact can be several (a bank that is also the insurer). */
enum class ContactKind {
    BANK, CREDIT_UNION, INVESTMENT_FIRM, FINANCIAL_ADVISOR, INSURER, INSURANCE_BROKER, PHARMACY, FAMILY_DOCTOR, SPECIALIST, DENTIST,
    OPTOMETRIST, CLINIC, HOSPITAL, LABORATORY, VETERINARIAN, LAWYER, NOTARY, ACCOUNTANT, CONTRACTOR, EMPLOYER, SCHOOL, UTILITY,
    GOVERNMENT, OTHER,
}

/** CON-02: a phone number, an email, or an account or client number. */
enum class DetailType { PHONE, EMAIL, NUMBER }

/**
 * A phone, email or number with its label ("Office", "Cell", "Client number"). A number read back
 * holds its masked form ("•••• 1234"); saving it unchanged keeps the full number stored.
 */
data class ContactDetail(val id: String = "", val type: DetailType, val label: String? = null, val value: String)

/** CON-04: the kinds of records a contact can be linked to. */
enum class LinkTarget { INSTITUTION, PAYEE, ACCOUNT, POLICY, HEALTH_PROVIDER, MEDICATION, EVENT, CONTRACTOR, BILL, PET, VEHICLE, ASSET, ESTATE }

/**
 * CON-04: what a contact is to a linked record: "Bank for" an account, "Pharmacy for" a medication,
 * "Executor for" someone's estate papers. [kind] is the kind a new contact gets when created from
 * the record's screen.
 */
enum class LinkRole(val targets: Set<LinkTarget>, val kind: ContactKind? = null) {
    SAME_AS(setOf(LinkTarget.INSTITUTION, LinkTarget.PAYEE, LinkTarget.HEALTH_PROVIDER, LinkTarget.CONTRACTOR)),
    BANK(setOf(LinkTarget.ACCOUNT), ContactKind.BANK),
    LENDER(setOf(LinkTarget.ACCOUNT), ContactKind.BANK),
    INVESTMENT_FIRM(setOf(LinkTarget.ACCOUNT), ContactKind.INVESTMENT_FIRM),
    ADVISOR(setOf(LinkTarget.ACCOUNT, LinkTarget.POLICY), ContactKind.FINANCIAL_ADVISOR),
    INSURER(setOf(LinkTarget.POLICY, LinkTarget.PET, LinkTarget.VEHICLE, LinkTarget.ASSET), ContactKind.INSURER),
    BROKER(setOf(LinkTarget.POLICY), ContactKind.INSURANCE_BROKER),
    PHARMACY(setOf(LinkTarget.MEDICATION), ContactKind.PHARMACY),
    PRESCRIBER(setOf(LinkTarget.MEDICATION), ContactKind.FAMILY_DOCTOR),
    APPOINTMENT(setOf(LinkTarget.EVENT)),
    BILLER(setOf(LinkTarget.BILL)),
    VETERINARIAN(setOf(LinkTarget.PET), ContactKind.VETERINARIAN),
    GARAGE(setOf(LinkTarget.VEHICLE), ContactKind.CONTRACTOR),
    SERVICE(setOf(LinkTarget.ASSET, LinkTarget.VEHICLE, LinkTarget.PET), ContactKind.CONTRACTOR),
    ESTATE_EXECUTOR(setOf(LinkTarget.ESTATE)),
    ESTATE_LIQUIDATOR(setOf(LinkTarget.ESTATE)),
    ESTATE_POWER_OF_ATTORNEY(setOf(LinkTarget.ESTATE)),
    ESTATE_MANDATARY(setOf(LinkTarget.ESTATE)),
    ESTATE_LAWYER(setOf(LinkTarget.ESTATE), ContactKind.LAWYER),
    ESTATE_NOTARY(setOf(LinkTarget.ESTATE), ContactKind.NOTARY),
    ESTATE_FINANCIAL_ADVISOR(setOf(LinkTarget.ESTATE), ContactKind.FINANCIAL_ADVISOR),
    ESTATE_ACCOUNTANT(setOf(LinkTarget.ESTATE), ContactKind.ACCOUNTANT),
    ESTATE_INSURANCE_ADVISOR(setOf(LinkTarget.ESTATE), ContactKind.INSURANCE_BROKER),
    ESTATE_EMPLOYER(setOf(LinkTarget.ESTATE), ContactKind.EMPLOYER),
    ESTATE_OTHER(setOf(LinkTarget.ESTATE)),
    OTHER(LinkTarget.entries.toSet() - LinkTarget.ESTATE),
    ;

    companion object {
        /** The roles a contact can have for a record of [target], the most specific first. */
        fun forTarget(target: LinkTarget): List<LinkRole> = entries.filter { target in it.targets }

        /** The estate screen's role ([ContactRole]) as a link role. */
        fun of(role: ContactRole): LinkRole = valueOf("ESTATE_${role.name}")
    }
}

/**
 * CON-01, CON-02: a person or organization the household deals with. [purpose] is the user's own
 * "what for" line ("RRSP and TFSA", "Sam's dermatologist"), shown wherever the contact is named.
 * No [memberIds] means the contact serves the whole household.
 */
data class Contact(
    val id: String,
    val groupId: String,
    val name: String,
    val person: Boolean = false,
    val organizationId: String? = null,
    val jobTitle: String? = null,
    val purpose: String? = null,
    val kinds: Set<ContactKind> = emptySet(),
    val memberIds: Set<String> = emptySet(),
    val details: List<ContactDetail> = emptyList(),
    val address: String? = null,
    val website: String? = null,
    val hours: String? = null,
    val notes: String? = null,
    val archived: Boolean = false,
) {
    val phones: List<ContactDetail> get() = details.filter { it.type == DetailType.PHONE }
    val emails: List<ContactDetail> get() = details.filter { it.type == DetailType.EMAIL }
    val numbers: List<ContactDetail> get() = details.filter { it.type == DetailType.NUMBER }

    /** "RBC Royal Bank · RRSP and TFSA": the name with its what-for line. */
    val label: String get() = name + (purpose?.let { " · $it" }.orEmpty())
}

data class ContactLink(val id: String, val contactId: String, val groupId: String, val role: LinkRole, val target: LinkTarget, val targetId: String)

/** A link with the name of the record it points to, for the contact's page. */
data class ResolvedLink(val link: ContactLink, val name: String)

/** A contact linked to a record, as the record's screen shows it. */
data class LinkedContact(val contact: Contact, val link: ContactLink)

/** A record a contact can be linked to, as the link picker lists it. */
data class LinkCandidate(val target: LinkTarget, val id: String, val name: String)

/**
 * CON-05: the filters of the Contacts screen. Text matches the name, what-for line, job title,
 * organization, address, notes, phones and emails, accents and case ignored.
 */
data class ContactFilter(
    val text: String = "",
    val kind: ContactKind? = null,
    val memberId: String? = null,
    val target: LinkTarget? = null,
    val includeArchived: Boolean = false,
)

/**
 * CON-06: one record of the app that can become a contact (an institution, a health provider, a
 * contractor, a policy's insurer or broker, an estate contact, a pet's insurer), with the links
 * the new contact gets.
 */
data class GatherSource(
    val target: LinkTarget,
    val targetId: String,
    val name: String,
    val kinds: Set<ContactKind>,
    val links: List<Pair<LinkRole, Pair<LinkTarget, String>>>,
    val phone: String? = null,
    val email: String? = null,
    val website: String? = null,
    val address: String? = null,
    val purpose: String? = null,
    val notes: String? = null,
    val memberIds: Set<String> = emptySet(),
    /** The private group the record is kept in, where its contact stays; null for a shared record. */
    val privateGroupId: String? = null,
)

/**
 * CON-06: records that are probably the same person or organization (same name, accents and case
 * ignored, or same phone number), possibly matching contacts that already exist.
 */
data class GatherProposal(val sources: List<GatherSource>, val existing: List<Contact>) {
    val isDuplicate: Boolean get() = sources.size + existing.size > 1
}

/**
 * CON-06: what the user decided for some sources: one contact made from all of them, or, with
 * [intoContactId], everything added to that existing contact.
 */
data class GatherDecision(val sources: List<GatherSource>, val intoContactId: String? = null)

/**
 * The household's contacts (CON-01 to CON-06): who each is, what for and for whom, how to reach
 * them, and links to the records they concern. Stored in the ledger of the group the user chooses;
 * only contacts in groups the user can see are listed, and only links to records the user can see.
 */
class ContactService internal constructor(private val books: Books) {

    // --- Contacts (CON-01, CON-02) --------------------------------------------------------------

    fun list(filter: ContactFilter = ContactFilter()): List<Contact> {
        val all = books.groups().flatMap { g -> rows(g) }
        val byId = all.associateBy { it.id }
        val needle = filter.text.trim().takeIf { it.isNotEmpty() }?.let(SearchService::fold)
        val targets = if (filter.target != null) allLinks().filter { it.target == filter.target }.map { it.contactId }.toSet() else emptySet()
        return all.filter { c ->
            (filter.includeArchived || !c.archived) &&
                (filter.kind == null || filter.kind in c.kinds) &&
                (filter.memberId == null || filter.memberId in c.memberIds) &&
                (filter.target == null || c.id in targets) &&
                (needle == null || matches(c, byId[c.organizationId ?: ""], needle))
        }.sortedWith(compareBy({ SearchService.fold(it.name) }, { it.id }))
    }

    fun get(id: String): Contact = locate(id).second

    /** Saves a contact in its group; a new one needs edit rights there (CAL-06, HH-11). */
    fun save(contact: Contact): Contact {
        validate(contact.name.isNotBlank(), "error.nameRequired")
        validate(contact.details.all { it.value.isNotBlank() }, "error.contactDetailEmpty")
        validate(contact.organizationId == null || contact.organizationId != contact.id, "error.contactOwnOrganization")
        val existing = contact.id.takeIf { it.isNotBlank() }?.let { id -> runCatching { locate(id) }.getOrNull() }
        val group = books.group(existing?.first?.id ?: contact.groupId).also { books.require(it, PermissionLevel.EDIT) }
        val id = contact.id.ifBlank { Ids.newId() }
        val db = books.ledger(group)
        val q = db.contactsQueries
        val now = books.now()
        fun t(v: String?) = v?.trim()?.ifEmpty { null }
        db.transaction {
            val created = q.contactById(id).executeAsOneOrNull()?.created_at ?: now
            q.upsertContact(
                id, contact.name.trim(), if (contact.person) 1 else 0, contact.organizationId, t(contact.jobTitle), t(contact.purpose),
                contact.kinds.sorted().joinToString(",") { it.name }, contact.memberIds.sorted().joinToString(","),
                t(contact.address), t(contact.website), t(contact.hours), t(contact.notes), if (contact.archived) 1 else 0, created, now,
            )
            val stored = q.detailsFor(id).executeAsList().associateBy { it.id }
            val kept = HashSet<String>()
            contact.details.forEachIndexed { i, d ->
                val detailId = d.id.takeIf { it in stored } ?: Ids.newId()
                kept += detailId
                val value = d.value.trim()
                val old = stored[detailId]
                if (d.type == DetailType.NUMBER) {
                    // A number shown masked and saved unchanged keeps its full value.
                    val full = if (old != null && old.kind == DetailType.NUMBER.name && value == old.masked) old.content else value
                    q.upsertDetail(detailId, id, d.type.name, t(d.label), full, AccountService.mask(full) ?: "••••", i.toLong())
                } else {
                    q.upsertDetail(detailId, id, d.type.name, t(d.label), value, null, i.toLong())
                }
            }
            stored.keys.filter { it !in kept }.forEach(q::deleteDetail)
        }
        books.session.audit(if (existing == null) "CREATE" else "UPDATE", "contact", id)
        return get(id)
    }

    /** Deletes a contact and its links; the records it was linked to stay as they are. */
    fun delete(id: String) {
        val (group, _) = locate(id)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).contactsQueries.deleteContact(id)
        // People who belonged to it no longer do, wherever the user may change them.
        books.groups().filter { it.level == PermissionLevel.EDIT }.forEach { books.ledger(it).contactsQueries.clearOrganization(id) }
        books.session.audit("DELETE", "contact", id)
    }

    /** CON-01: the people who belong to an organization contact. */
    fun people(organizationId: String): List<Contact> = list(ContactFilter(includeArchived = true)).filter { it.organizationId == organizationId }

    /** CON-02: a full account or client number, only after the user re-enters their password (SEC-04). */
    fun revealNumber(contactId: String, detailId: String, password: CharArray): String? {
        val (group, _) = locate(contactId)
        if (!books.session.verifyPassword(password)) throw AccessDeniedException("Wrong password")
        books.session.audit("REVEAL", "contact", contactId)
        return books.ledger(group).contactsQueries.detailValue(detailId, contactId).executeAsOneOrNull()
    }

    // --- Links (CON-04) -------------------------------------------------------------------------

    /** The links of one contact, each with the name of its record; records the user cannot see are left out. */
    fun links(contactId: String): List<ResolvedLink> {
        val (group, _) = locate(contactId)
        val links = books.ledger(group).contactsQueries.linksFor(contactId).executeAsList().map { it.toLink(group.id) }
        val names = links.map { it.target }.toSet().associateWith { names(it) }
        return links.mapNotNull { l -> names[l.target]?.get(l.targetId)?.let { ResolvedLink(l, it) } }
    }

    /** The contacts linked to one record, for its screen (CON-04). */
    fun linkedTo(target: LinkTarget, targetId: String): List<LinkedContact> = books.groups().flatMap { g ->
        val q = books.ledger(g).contactsQueries
        q.linksTo(target.name, targetId).executeAsList().mapNotNull { row ->
            val link = row.toLink(g.id)
            q.contactById(link.contactId).executeAsOneOrNull()?.let { LinkedContact(it.toContact(g.id, q.detailsFor(it.id).executeAsList()), link) }
        }
    }

    /** Links a contact to a record in [role]; linking twice the same way changes nothing. */
    fun link(contactId: String, role: LinkRole, target: LinkTarget, targetId: String): ContactLink {
        validate(target in role.targets, "error.contactRole")
        val (group, _) = locate(contactId)
        books.require(group, PermissionLevel.EDIT)
        val q = books.ledger(group).contactsQueries
        q.insertLink(Ids.newId(), contactId, role.name, target.name, targetId, books.now())
        return q.linksFor(contactId).executeAsList().map { it.toLink(group.id) }.first { it.role == role && it.target == target && it.targetId == targetId }
    }

    fun unlink(link: ContactLink) {
        val group = books.group(link.groupId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).contactsQueries.deleteLink(link.id)
    }

    /** The records of [target] the user can see, for the link picker. */
    fun candidates(target: LinkTarget): List<LinkCandidate> =
        names(target).map { (id, name) -> LinkCandidate(target, id, name) }.sortedBy { SearchService.fold(it.name) }

    /** Every link in the groups the user can see. */
    fun allLinks(): List<ContactLink> = books.groups().flatMap { g -> books.ledger(g).contactsQueries.links().executeAsList().map { it.toLink(g.id) } }

    /** The names of the records of [target] the user can see, by id. */
    private fun names(target: LinkTarget): Map<String, String> = runCatching {
        when (target) {
            LinkTarget.INSTITUTION -> books.institutions.list().associate { it.id to it.name }
            LinkTarget.PAYEE -> books.payees.list(includeArchived = true).associate { it.id to it.name }
            LinkTarget.ACCOUNT -> books.accounts.list(includeClosed = true).associate { it.account.id to it.account.name }
            LinkTarget.POLICY -> books.insurance.policies(includeInactive = true).associate { p ->
                p.id to listOfNotNull(books.text("policyKind.${p.kind}"), p.insurer, p.policyNumber).joinToString(" · ")
            }
            LinkTarget.HEALTH_PROVIDER -> books.health.providers().associate { it.id to it.name }
            LinkTarget.MEDICATION -> {
                val who = whoNames()
                books.health.medications().associate { m -> m.id to (listOfNotNull(m.name, m.dose).joinToString(" ") + (who[m.memberId]?.let { " ($it)" }.orEmpty())) }
            }
            LinkTarget.EVENT -> books.calendar.list().associate { it.id to "${it.title} · ${it.startDate}" }
            LinkTarget.CONTRACTOR -> books.contractors.list(includeArchived = true).associate { it.id to (it.name + (it.trade?.let { t -> " · $t" }.orEmpty())) }
            LinkTarget.BILL -> books.bills.list(includeInactive = true).associate { it.id to it.name }
            LinkTarget.PET -> books.pets.list(includeArchived = true).associate { it.id to it.name }
            LinkTarget.VEHICLE -> books.vehicles.list(includeInactive = true).associate { it.id to it.name }
            LinkTarget.ASSET -> books.assets.list(includeDisposed = true).associate { it.id to it.name }
            LinkTarget.ESTATE -> {
                val who = whoNames()
                books.estate.records().associate { it.memberId to books.text("contact.estateOf", who[it.memberId] ?: "?") }
            }
        }
    }.getOrDefault(emptyMap())

    private fun whoNames(): Map<String, String> =
        books.members.list(includeArchived = true).associate { it.id to it.displayName } +
            runCatching { books.pets.list(includeArchived = true).associate { it.id to it.name } }.getOrDefault(emptyMap())

    // --- Gathering existing records (CON-06) ----------------------------------------------------

    /**
     * The records of the app not yet linked to a contact, grouped where they look like the same
     * person or organization, each group with the existing contacts it seems to match. Nothing is
     * changed until [gather] is called with the user's decisions.
     */
    fun proposals(): List<GatherProposal> {
        val links = allLinks()
        val linked = links.map { Triple(it.role, it.target, it.targetId) }.toSet()
        val contacts = list(ContactFilter(includeArchived = true))
        val contactNames = contacts.associate { it.id to SearchService.fold(it.name.trim()) }
        val sources = sources().filter { s ->
            val first = s.links.firstOrNull() ?: return@filter false
            if (s.target == LinkTarget.ESTATE) {
                // An estate contact is gathered when a contact of the same name has its role for that person.
                links.none { l -> l.role == first.first && l.target == LinkTarget.ESTATE && l.targetId == s.targetId && contactNames[l.contactId] == SearchService.fold(s.name.trim()) }
            } else {
                Triple(first.first, first.second.first, first.second.second) !in linked
            }
        }
        // Union-find over sources and existing contacts, joined by the same name or phone.
        val nodes: List<Any> = sources + contacts
        val parent = IntArray(nodes.size) { it }
        fun find(i: Int): Int { var x = i; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
        fun union(a: Int, b: Int) { parent[find(a)] = find(b) }
        val byKey = HashMap<String, Int>()
        nodes.forEachIndexed { i, n ->
            val keys = when (n) {
                is GatherSource -> listOfNotNull("n:" + SearchService.fold(n.name.trim()), phoneKey(n.phone)?.let { "p:$it" })
                is Contact -> listOf("n:" + SearchService.fold(n.name.trim())) + n.phones.mapNotNull { phoneKey(it.value)?.let { k -> "p:$k" } }
                else -> emptyList()
            }
            for (k in keys) byKey[k]?.let { union(i, it) } ?: run { byKey[k] = i }
        }
        return sources.indices.groupBy { find(it) }.map { (root, members) ->
            val existing = contacts.indices.filter { find(sources.size + it) == root }.map { contacts[it] }
            GatherProposal(members.map { sources[it] }, existing)
        }.sortedBy { SearchService.fold(it.sources.first().name) }
    }

    /**
     * CON-06: applies the user's decisions: each becomes one new contact in [groupId] (or in the
     * private group its records are kept in), or is added to an existing contact, linked to every
     * record it came from. Existing records are left as they are.
     */
    fun gather(groupId: String, decisions: List<GatherDecision>): List<Contact> = decisions.filter { it.sources.isNotEmpty() }.map { d ->
        val s = d.sources
        val base = d.intoContactId?.let { get(it) }
        val group = base?.groupId ?: s.firstNotNullOfOrNull { it.privateGroupId } ?: groupId
        val kept = base?.details.orEmpty()
        val phones = s.mapNotNull { it.phone?.trim()?.ifEmpty { null } }.distinctBy { phoneKey(it) ?: it }
            .filter { p -> kept.none { it.type == DetailType.PHONE && (phoneKey(it.value) ?: it.value) == (phoneKey(p) ?: p) } }
        val emails = s.mapNotNull { it.email?.trim()?.ifEmpty { null } }.distinctBy { it.lowercase() }
            .filter { e -> kept.none { it.type == DetailType.EMAIL && it.value.equals(e, ignoreCase = true) } }
        fun first(f: (GatherSource) -> String?) = s.firstNotNullOfOrNull { f(it)?.trim()?.ifEmpty { null } }
        val contact = (base ?: Contact("", group, s.first().name.trim())).let { c ->
            c.copy(
                purpose = c.purpose ?: first { it.purpose },
                kinds = c.kinds + s.flatMap { it.kinds },
                memberIds = c.memberIds + s.flatMap { it.memberIds },
                details = kept + phones.map { ContactDetail(type = DetailType.PHONE, value = it) } + emails.map { ContactDetail(type = DetailType.EMAIL, value = it) },
                website = c.website ?: first { it.website },
                address = c.address ?: first { it.address },
                notes = c.notes ?: s.mapNotNull { it.notes?.trim()?.ifEmpty { null } }.distinct().joinToString("\n").ifEmpty { null },
            )
        }
        val saved = save(contact)
        for (source in s) for ((role, target) in source.links) link(saved.id, role, target.first, target.second)
        saved
    }

    /** Every record that can become a contact, with what it brings and the links it gets. */
    private fun sources(): List<GatherSource> {
        val out = ArrayList<GatherSource>()
        val privateGroups = books.groups().filter { it.isPrivate }.map { it.id }.toSet()
        fun private(groupId: String) = groupId.takeIf { it in privateGroups }
        val accounts = runCatching { books.accounts.list(includeClosed = true).map { it.account } }.getOrDefault(emptyList())
        for (i in books.institutions.list()) {
            val folded = SearchService.fold(i.name)
            val kind = if ("caisse" in folded || "credit union" in folded) ContactKind.CREDIT_UNION else ContactKind.BANK
            val accountLinks = accounts.filter { it.institutionId == i.id }.map { a ->
                val role = when (a.type.kind) {
                    AccountKind.LOAN -> LinkRole.LENDER
                    AccountKind.INVESTMENT -> LinkRole.INVESTMENT_FIRM
                    else -> LinkRole.BANK
                }
                role to (LinkTarget.ACCOUNT to a.id)
            }
            val numbers = listOfNotNull(i.institutionNumber?.let { books.text("contact.gather.institutionNumber", it) }, i.transitNumber?.let { books.text("contact.gather.transitNumber", it) })
            out += GatherSource(
                LinkTarget.INSTITUTION, i.id, i.name, setOf(kind), listOf(LinkRole.SAME_AS to (LinkTarget.INSTITUTION to i.id)) + accountLinks,
                phone = i.phone, website = i.website, address = i.branch,
                notes = (numbers + listOfNotNull(i.notes)).joinToString("\n").ifEmpty { null },
            )
        }
        val medications = runCatching { books.health.medications() }.getOrDefault(emptyList())
        for (p in runCatching { books.health.providers() }.getOrDefault(emptyList())) {
            val kind = when (p.kind) {
                ProviderKind.DOCTOR -> ContactKind.FAMILY_DOCTOR
                ProviderKind.DENTIST -> ContactKind.DENTIST
                ProviderKind.PHARMACY -> ContactKind.PHARMACY
                ProviderKind.CLINIC -> ContactKind.CLINIC
                ProviderKind.HOSPITAL -> ContactKind.HOSPITAL
                ProviderKind.SPECIALIST -> ContactKind.SPECIALIST
                ProviderKind.LAB -> ContactKind.LABORATORY
                ProviderKind.VET -> ContactKind.VETERINARIAN
                ProviderKind.GROOMER, ProviderKind.KENNEL, ProviderKind.OTHER -> ContactKind.OTHER
            }
            val medLinks = medications.filter { it.pharmacyId == p.id }.map { LinkRole.PHARMACY to (LinkTarget.MEDICATION to it.id) } +
                medications.filter { it.prescriberId == p.id }.map { LinkRole.PRESCRIBER to (LinkTarget.MEDICATION to it.id) }
            val served = medications.filter { it.pharmacyId == p.id || it.prescriberId == p.id }.map { it.memberId }.toSet()
            out += GatherSource(
                LinkTarget.HEALTH_PROVIDER, p.id, p.name, setOf(kind), listOf(LinkRole.SAME_AS to (LinkTarget.HEALTH_PROVIDER to p.id)) + medLinks,
                phone = p.phone, address = p.address, notes = p.notes, memberIds = if (p.kind == ProviderKind.PHARMACY) emptySet() else served,
                privateGroupId = private(p.groupId),
            )
        }
        for (c in runCatching { books.contractors.list(includeArchived = true) }.getOrDefault(emptyList())) {
            out += GatherSource(
                LinkTarget.CONTRACTOR, c.id, c.name, setOf(ContactKind.CONTRACTOR), listOf(LinkRole.SAME_AS to (LinkTarget.CONTRACTOR to c.id)),
                phone = c.phone, email = c.email, website = c.website, purpose = c.trade, notes = c.notes, privateGroupId = private(c.groupId),
            )
        }
        for (p in runCatching { books.insurance.policies(includeInactive = true) }.getOrDefault(emptyList())) {
            val members = setOfNotNull(p.insuredMemberId)
            if (p.insurer.isNotBlank()) {
                out += GatherSource(LinkTarget.POLICY, p.id, p.insurer, setOf(ContactKind.INSURER), listOf(LinkRole.INSURER to (LinkTarget.POLICY to p.id)), memberIds = members, privateGroupId = private(p.groupId))
            }
            p.broker?.takeIf { it.isNotBlank() }?.let { b ->
                out += GatherSource(LinkTarget.POLICY, p.id, b, setOf(ContactKind.INSURANCE_BROKER), listOf(LinkRole.BROKER to (LinkTarget.POLICY to p.id)), memberIds = members, privateGroupId = private(p.groupId))
            }
        }
        for (pet in runCatching { books.pets.list(includeArchived = true) }.getOrDefault(emptyList())) {
            pet.insurer?.takeIf { it.isNotBlank() }?.let { name ->
                out += GatherSource(LinkTarget.PET, pet.id, name, setOf(ContactKind.INSURER), listOf(LinkRole.INSURER to (LinkTarget.PET to pet.id)), memberIds = setOf(pet.id))
            }
        }
        for (r in runCatching { books.estate.records() }.getOrDefault(emptyList())) {
            for (c in r.plan.contacts.filter { it.name.isNotBlank() }) {
                val role = LinkRole.of(c.role)
                out += GatherSource(
                    LinkTarget.ESTATE, r.memberId, c.name, setOfNotNull(role.kind ?: ContactKind.OTHER), listOf(role to (LinkTarget.ESTATE to r.memberId)),
                    phone = c.phone, email = c.email, notes = listOfNotNull(c.organization, c.notes).joinToString("\n").ifEmpty { null },
                    memberIds = setOf(r.memberId), privateGroupId = private(r.groupId),
                )
            }
        }
        return out
    }

    // --- Helpers --------------------------------------------------------------------------------

    private fun rows(g: GroupInfo): List<Contact> {
        val q = books.ledger(g).contactsQueries
        val details = q.details().executeAsList().groupBy { it.contact_id }
        return q.contacts().executeAsList().map { it.toContact(g.id, details[it.id].orEmpty()) }
    }

    private fun matches(c: Contact, organization: Contact?, needle: String): Boolean =
        (listOfNotNull(c.name, c.purpose, c.jobTitle, c.address, c.website, c.notes, c.hours, organization?.name) + c.phones.map { it.value } + c.emails.map { it.value })
            .any { SearchService.fold(it).contains(needle) } ||
            (needle.any(Char::isDigit) && needle.filter(Char::isDigit).let { digits -> digits.length >= 3 && c.phones.any { it.value.filter(Char::isDigit).contains(digits) } })

    private fun locate(id: String): Pair<GroupInfo, Contact> {
        for (group in books.groups()) {
            val q = books.ledger(group).contactsQueries
            val row = q.contactById(id).executeAsOneOrNull() ?: continue
            return group to row.toContact(group.id, q.detailsFor(id).executeAsList())
        }
        throw AccessDeniedException("Contact not found or not accessible")
    }

    private fun ContactRow.toContact(groupId: String, details: List<ca.schippers.hfm.data.ledger.Contact_detail>) = Contact(
        id = id, groupId = groupId, name = name, person = person == 1L, organizationId = organization_id, jobTitle = job_title, purpose = purpose,
        kinds = kinds.split(',').filter { it.isNotBlank() }.mapNotNull { k -> runCatching { ContactKind.valueOf(k) }.getOrNull() }.toSet(),
        memberIds = member_ids.split(',').filter { it.isNotBlank() }.toSet(),
        details = details.map { d ->
            val type = DetailType.valueOf(d.kind)
            ContactDetail(d.id, type, d.label, if (type == DetailType.NUMBER) d.masked ?: "••••" else d.content)
        },
        address = address, website = website, hours = hours, notes = notes, archived = archived == 1L,
    )

    private fun ca.schippers.hfm.data.ledger.Contact_link.toLink(groupId: String) =
        ContactLink(id, contact_id, groupId, LinkRole.valueOf(role), LinkTarget.valueOf(target_type), target_id)

    companion object {
        /** A phone number's last ten digits, to compare "613 555-0101" with "+1 (613) 555-0101"; null when too short. */
        fun phoneKey(phone: String?): String? = phone?.filter(Char::isDigit)?.takeLast(10)?.takeIf { it.length >= 7 }
    }
}
