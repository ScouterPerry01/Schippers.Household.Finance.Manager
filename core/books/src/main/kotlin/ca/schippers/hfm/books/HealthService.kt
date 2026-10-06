package ca.schippers.hfm.books

import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import ca.schippers.hfm.data.ledger.Allergy as AllergyRow
import ca.schippers.hfm.data.ledger.Health_condition as ConditionRow
import ca.schippers.hfm.data.ledger.Health_provider as ProviderRow
import ca.schippers.hfm.data.ledger.Health_test as TestRow
import ca.schippers.hfm.data.ledger.Immunization as ImmunizationRow
import ca.schippers.hfm.data.ledger.Medication as MedicationRow

enum class ProviderKind { DOCTOR, DENTIST, PHARMACY, CLINIC, HOSPITAL, SPECIALIST, LAB, VET, GROOMER, KENNEL, OTHER }
enum class ConditionStatus { ACTIVE, MANAGED, RESOLVED }
enum class Severity { MILD, MODERATE, SEVERE }

/** Every health record is stored in an account group's ledger, chosen by the user (CAL-06). */
data class HealthProvider(val id: String, val groupId: String, val name: String, val kind: ProviderKind, val phone: String?, val address: String?, val notes: String?, val archived: Boolean)

/** HLT-01, HLT-02. */
data class Medication(
    val id: String,
    val groupId: String,
    val memberId: String,
    val name: String,
    val dose: String?,
    val instructions: String?,
    val prescriberId: String?,
    val pharmacyId: String?,
    val rxNumber: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val daysSupply: Int?,
    val refillsRemaining: Int?,
    val lastFillDate: LocalDate?,
    val refillReminderDays: Int,
    val active: Boolean,
    val notes: String?,
) {
    /** HLT-02: when the current supply runs out; null without a last fill and days of supply. */
    val nextRefill: LocalDate? get() = if (lastFillDate != null && daysSupply != null) lastFillDate.plus(DatePeriod(days = daysSupply)) else null

    /** HLT-03: no refills left, so the prescription must be renewed. */
    val needsRenewal: Boolean get() = refillsRemaining == 0
}

data class MedicationFill(val id: String, val date: LocalDate, val daysSupply: Int?, val quantity: String?, val notes: String?)

/** A condition, allergy, test or immunization of one household member. */
sealed interface HealthRecord {
    val id: String
    val groupId: String
    val memberId: String
}

data class HealthCondition(override val id: String, override val groupId: String, override val memberId: String, val name: String, val diagnosed: LocalDate?, val status: ConditionStatus, val providerId: String?, val notes: String?) : HealthRecord
data class Allergy(override val id: String, override val groupId: String, override val memberId: String, val substance: String, val reaction: String?, val severity: Severity?, val notes: String?) : HealthRecord
data class HealthTest(override val id: String, override val groupId: String, override val memberId: String, val name: String, val date: LocalDate, val result: String?, val units: String?, val referenceRange: String?, val providerId: String?, val followUp: LocalDate?, val notes: String?) : HealthRecord
data class Immunization(override val id: String, override val groupId: String, override val memberId: String, val vaccine: String, val date: LocalDate, val providerId: String?, val nextDue: LocalDate?, val notes: String?) : HealthRecord

/** Something health-related falling due on a date, for the calendar and reminders. */
sealed interface HealthDue {
    val date: LocalDate
    val memberId: String

    data class Refill(val medication: Medication, override val date: LocalDate) : HealthDue {
        override val memberId get() = medication.memberId
    }

    data class TestFollowUp(val test: HealthTest, override val date: LocalDate) : HealthDue {
        override val memberId get() = test.memberId
    }

    data class ImmunizationDue(val immunization: Immunization, override val date: LocalDate) : HealthDue {
        override val memberId get() = immunization.memberId
    }
}

/** HLT-03: a refill coming up within its reminder days, or overdue. */
data class RefillReminder(val medication: Medication, val due: LocalDate, val daysLeft: Int)

/**
 * Health records per household member (HLT-01 to HLT-07): medications and refills, conditions,
 * allergies, tests and immunizations, and a directory of providers. An organizational aid, not
 * medical advice.
 */
class HealthService internal constructor(private val books: Books) {

    // --- Providers (HLT-07) ---------------------------------------------------------------------

    fun providers(): List<HealthProvider> = books.groups().flatMap { g ->
        books.ledger(g).healthQueries.providers().executeAsList().map { it.toProvider(g.id) }
    }

    fun saveProvider(p: HealthProvider): HealthProvider {
        validate(p.name.isNotBlank(), "error.nameRequired")
        val group = editable(p.groupId)
        val id = p.id.ifBlank { Ids.newId() }
        books.ledger(group).healthQueries.upsertProvider(id, p.name.trim(), p.kind.name, p.phone?.ifBlank { null }, p.address?.ifBlank { null }, p.notes?.ifBlank { null }, if (p.archived) 1 else 0)
        return p.copy(id = id)
    }

    // --- Medications (HLT-01 to HLT-03) ---------------------------------------------------------

    fun medications(memberId: String? = null): List<Medication> = books.groups().flatMap { g ->
        books.ledger(g).healthQueries.medications().executeAsList().map { it.toMedication(g.id) }
    }.filter { memberId == null || it.memberId == memberId }

    fun saveMedication(m: Medication): Medication {
        validate(m.name.isNotBlank(), "error.nameRequired")
        validate(m.daysSupply == null || m.daysSupply in 1..400, "error.invalidNumber")
        validate(m.refillsRemaining == null || m.refillsRemaining in 0..99, "error.invalidNumber")
        validate(m.refillReminderDays in 0..60, "error.reminderDays")
        val group = editable(m.groupId)
        val q = books.ledger(group).healthQueries
        val now = books.now()
        val existing = m.id.isNotBlank() && q.medicationById(m.id).executeAsOneOrNull() != null
        val id = m.id.ifBlank { Ids.newId() }
        with(m) {
            if (existing) {
                q.updateMedication(memberId, name.trim(), dose, instructions, prescriberId, pharmacyId, rxNumber, startDate?.toString(), endDate?.toString(),
                    daysSupply?.toLong(), refillsRemaining?.toLong(), lastFillDate?.toString(), refillReminderDays.toLong(), if (active) 1 else 0, notes, now, id)
            } else {
                q.insertMedication(id, memberId, name.trim(), dose, instructions, prescriberId, pharmacyId, rxNumber, startDate?.toString(), endDate?.toString(),
                    daysSupply?.toLong(), refillsRemaining?.toLong(), lastFillDate?.toString(), refillReminderDays.toLong(), if (active) 1 else 0, notes, now, now)
            }
        }
        return locateMedication(id).second
    }

    fun deleteMedication(id: String) {
        val (group, _) = locateMedication(id)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).healthQueries.deleteMedication(id)
    }

    /**
     * HLT-02: records a refill. The last fill date moves to [date], one refill is used, and the
     * days of supply are updated when given.
     */
    fun recordFill(medicationId: String, date: LocalDate, daysSupply: Int? = null, quantity: String? = null, notes: String? = null): Medication {
        val (group, med) = locateMedication(medicationId)
        books.require(group, PermissionLevel.CAPTURE_ONLY)
        validate(daysSupply == null || daysSupply in 1..400, "error.invalidNumber")
        val q = books.ledger(group).healthQueries
        books.ledger(group).transaction {
            q.insertFill(Ids.newId(), medicationId, date.toString(), daysSupply?.toLong(), quantity, notes)
            val isLatest = med.lastFillDate == null || date >= med.lastFillDate
            // Only the fill fields change, so a capture-only user can record a refill (the rest of
            // the medication stays theirs to read, not to edit).
            q.updateMedicationFill(
                (daysSupply ?: med.daysSupply)?.toLong(),
                med.refillsRemaining?.let { (it - 1).coerceAtLeast(0) }?.toLong(),
                (if (isLatest) date else med.lastFillDate).toString(),
                books.now(),
                medicationId,
            )
        }
        return locateMedication(medicationId).second
    }

    fun fills(medicationId: String): List<MedicationFill> {
        val (group, _) = locateMedication(medicationId)
        return books.ledger(group).healthQueries.fillsFor(medicationId).executeAsList()
            .map { MedicationFill(it.id, LocalDate.parse(it.fill_date), it.days_supply?.toInt(), it.quantity, it.notes) }
    }

    /** HLT-03: active medications whose refill is due within their reminder days, or overdue. */
    fun refillReminders(today: LocalDate): List<RefillReminder> = medications().filter { it.active }.mapNotNull { m ->
        val due = m.nextRefill ?: return@mapNotNull null
        val days = today.daysUntil(due)
        if (days <= m.refillReminderDays) RefillReminder(m, due, days) else null
    }.sortedBy { it.due }

    // --- Conditions, allergies, tests, immunizations (HLT-04, HLT-05) --------------------------

    fun conditions(memberId: String? = null): List<HealthCondition> = books.groups().flatMap { g ->
        books.ledger(g).healthQueries.conditions().executeAsList().map { it.toCondition(g.id) }
    }.filter { memberId == null || it.memberId == memberId }

    fun saveCondition(c: HealthCondition): HealthCondition {
        validate(c.name.isNotBlank(), "error.nameRequired")
        val id = c.id.ifBlank { Ids.newId() }
        books.ledger(editable(c.groupId)).healthQueries.upsertCondition(id, c.memberId, c.name.trim(), c.diagnosed?.toString(), c.status.name, c.providerId, c.notes?.ifBlank { null })
        return c.copy(id = id)
    }

    fun allergies(memberId: String? = null): List<Allergy> = books.groups().flatMap { g ->
        books.ledger(g).healthQueries.allergies().executeAsList().map { it.toAllergy(g.id) }
    }.filter { memberId == null || it.memberId == memberId }

    fun saveAllergy(a: Allergy): Allergy {
        validate(a.substance.isNotBlank(), "error.nameRequired")
        val id = a.id.ifBlank { Ids.newId() }
        books.ledger(editable(a.groupId)).healthQueries.upsertAllergy(id, a.memberId, a.substance.trim(), a.reaction?.ifBlank { null }, a.severity?.name, a.notes?.ifBlank { null })
        return a.copy(id = id)
    }

    fun tests(memberId: String? = null): List<HealthTest> = books.groups().flatMap { g ->
        books.ledger(g).healthQueries.tests().executeAsList().map { it.toTest(g.id) }
    }.filter { memberId == null || it.memberId == memberId }

    fun saveTest(t: HealthTest): HealthTest {
        validate(t.name.isNotBlank(), "error.nameRequired")
        val id = t.id.ifBlank { Ids.newId() }
        books.ledger(editable(t.groupId)).healthQueries.upsertTest(
            id, t.memberId, t.name.trim(), t.date.toString(), t.result?.ifBlank { null }, t.units?.ifBlank { null },
            t.referenceRange?.ifBlank { null }, t.providerId, t.followUp?.toString(), t.notes?.ifBlank { null },
        )
        return t.copy(id = id)
    }

    fun immunizations(memberId: String? = null): List<Immunization> = books.groups().flatMap { g ->
        books.ledger(g).healthQueries.immunizations().executeAsList().map { it.toImmunization(g.id) }
    }.filter { memberId == null || it.memberId == memberId }

    fun saveImmunization(i: Immunization): Immunization {
        validate(i.vaccine.isNotBlank(), "error.nameRequired")
        val id = i.id.ifBlank { Ids.newId() }
        books.ledger(editable(i.groupId)).healthQueries.upsertImmunization(id, i.memberId, i.vaccine.trim(), i.date.toString(), i.providerId, i.nextDue?.toString(), i.notes?.ifBlank { null })
        return i.copy(id = id)
    }

    fun delete(record: HealthRecord) {
        val q = books.ledger(editable(record.groupId)).healthQueries
        when (record) {
            is HealthCondition -> q.deleteCondition(record.id)
            is Allergy -> q.deleteAllergy(record.id)
            is HealthTest -> q.deleteTest(record.id)
            is Immunization -> q.deleteImmunization(record.id)
        }
    }

    /** For the calendar (CAL-04): refills, test follow-ups and immunizations falling due. */
    fun due(from: LocalDate, to: LocalDate): List<HealthDue> {
        val refills = medications().filter { it.active }.mapNotNull { m -> m.nextRefill?.takeIf { it in from..to }?.let { HealthDue.Refill(m, it) } }
        val followUps = tests().mapNotNull { t -> t.followUp?.takeIf { it in from..to }?.let { HealthDue.TestFollowUp(t, it) } }
        val shots = immunizations().mapNotNull { i -> i.nextDue?.takeIf { it in from..to }?.let { HealthDue.ImmunizationDue(i, it) } }
        return (refills + followUps + shots).sortedBy { it.date }
    }

    // --- Helpers --------------------------------------------------------------------------------

    private fun editable(groupId: String): GroupInfo {
        val group = books.group(groupId)
        books.require(group, PermissionLevel.EDIT)
        return group
    }

    private fun locateMedication(id: String): Pair<GroupInfo, Medication> {
        for (group in books.groups()) {
            val row = books.ledger(group).healthQueries.medicationById(id).executeAsOneOrNull() ?: continue
            return group to row.toMedication(group.id)
        }
        throw AccessDeniedException("Medication not found or not accessible")
    }

    private fun ProviderRow.toProvider(groupId: String) =
        HealthProvider(id, groupId, name, ProviderKind.valueOf(kind), phone, address, notes, archived == 1L)

    private fun MedicationRow.toMedication(groupId: String) = Medication(
        id, groupId, member_id, name, dose, instructions, prescriber_id, pharmacy_id, rx_number,
        start_date?.let(LocalDate::parse), end_date?.let(LocalDate::parse), days_supply?.toInt(), refills_remaining?.toInt(),
        last_fill_date?.let(LocalDate::parse), refill_reminder_days.toInt(), active == 1L, notes,
    )

    private fun ConditionRow.toCondition(groupId: String) =
        HealthCondition(id, groupId, member_id, name, diagnosed_date?.let(LocalDate::parse), ConditionStatus.valueOf(status), provider_id, notes)

    private fun AllergyRow.toAllergy(groupId: String) =
        Allergy(id, groupId, member_id, substance, reaction, severity?.let(Severity::valueOf), notes)

    private fun TestRow.toTest(groupId: String) = HealthTest(
        id, groupId, member_id, name, LocalDate.parse(date), result, units, reference_range, provider_id, follow_up_date?.let(LocalDate::parse), notes,
    )

    private fun ImmunizationRow.toImmunization(groupId: String) =
        Immunization(id, groupId, member_id, vaccine, LocalDate.parse(date), provider_id, next_due_date?.let(LocalDate::parse), notes)
}
