package ca.schippers.hfm.books

import ca.schippers.hfm.calc.rules.Thresholds
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import kotlinx.datetime.LocalDate

/**
 * VOL-01: what the volunteering is: volunteer firefighting or search and rescue (the two count
 * together towards the 200 hours of the tax amounts), a student's community hours, or other.
 */
enum class VolunteerKind { FIREFIGHTER, SEARCH_RESCUE, SCHOOL, OTHER }

data class VolunteerEntry(
    val id: String,
    val groupId: String,
    val memberId: String,
    val organization: String,
    val kind: VolunteerKind,
    val date: LocalDate,
    val minutes: Int,
    /** The organization as a contact, when linked. */
    val contactId: String? = null,
    val activity: String? = null,
    val notes: String? = null,
    val fromPhone: Boolean = false,
)

/** VOL-01: one person's volunteer year: the total, per organization, and the hours that count for the tax amounts. */
data class VolunteerYear(
    val memberId: String,
    val year: Int,
    val minutes: Int,
    val byOrganization: List<Pair<String, Int>>,
    /** Volunteer firefighting and search and rescue together. */
    val emergencyMinutes: Int,
    val schoolMinutes: Int,
    /** The hours the volunteer firefighters and search and rescue volunteers amounts need (Rates and rules). */
    val thresholdHours: Int,
) {
    val meetsThreshold: Boolean get() = emergencyMinutes >= thresholdHours * 60
}

/**
 * VOL-01: volunteer hours per person and organization, with each year's total; the hours of
 * volunteer firefighting and search and rescue are checked against the 200 hours the two tax
 * amounts need, and shown as information on the year-end tax package.
 */
class VolunteerService internal constructor(private val books: Books) {

    fun list(): List<VolunteerEntry> = books.groups().flatMap { g ->
        books.ledger(g).trackersQueries.volunteerHours().executeAsList().map { v ->
            VolunteerEntry(
                v.id, g.id, v.member_id, v.organization, VolunteerKind.valueOf(v.kind), LocalDate.parse(v.date), v.minutes.toInt(), v.contact_id, v.activity, v.notes, v.device_id != null,
            )
        }
    }.sortedWith(compareByDescending<VolunteerEntry> { it.date }.thenBy { it.organization })

    /** Adds or changes an entry; anyone who may capture in the group can add one, an editor can change it. */
    fun save(e: VolunteerEntry, deviceId: String? = null): VolunteerEntry {
        validate(e.organization.isNotBlank(), "error.volunteerOrganization")
        validate(e.minutes in 1..WorkHoursService.MAX_MINUTES, "error.workMinutes")
        validate(e.memberId.isNotBlank(), "error.memberRequired")
        val existing = list().firstOrNull { it.id == e.id }
        val group = books.group(existing?.groupId ?: e.groupId).also { books.require(it, if (existing == null) PermissionLevel.CAPTURE_ONLY else PermissionLevel.EDIT) }
        val id = e.id.ifBlank { Ids.newId() }
        books.ledger(group).trackersQueries.upsertVolunteerHours(
            id, e.memberId, e.organization.trim(), e.contactId, e.kind.name, e.date.toString(), e.minutes.toLong(), e.activity?.trim()?.ifEmpty { null },
            e.notes?.trim()?.ifEmpty { null }, deviceId, books.now(),
        )
        return list().first { it.id == id }
    }

    fun delete(e: VolunteerEntry) = books.ledger(books.group(e.groupId).also { books.require(it, PermissionLevel.EDIT) }).trackersQueries.deleteVolunteerHours(e.id)

    /** [memberId]'s volunteer [year]. */
    fun year(memberId: String, year: Int): VolunteerYear {
        val mine = list().filter { it.memberId == memberId && it.date.year == year }
        return VolunteerYear(
            memberId, year, mine.sumOf { it.minutes },
            mine.groupBy { it.organization }.map { (o, l) -> o to l.sumOf { it.minutes } }.sortedByDescending { it.second },
            mine.filter { it.kind == VolunteerKind.FIREFIGHTER || it.kind == VolunteerKind.SEARCH_RESCUE }.sumOf { it.minutes },
            mine.filter { it.kind == VolunteerKind.SCHOOL }.sumOf { it.minutes },
            Thresholds.volunteerHours(year),
        )
    }

    /** Each person's [year], for those with hours in it. */
    fun years(year: Int): List<VolunteerYear> = list().filter { it.date.year == year }.map { it.memberId }.distinct().map { year(it, year) }

    /** The organizations given time before, the latest first, to pick again (on the phone too). */
    fun organizations(): List<VolunteerEntry> = list().distinctBy { it.organization.lowercase() to it.memberId }
}
