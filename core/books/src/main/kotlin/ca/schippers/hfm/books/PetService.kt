package ca.schippers.hfm.books

import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import ca.schippers.hfm.data.core.Pet as PetRow

enum class Species { DOG, CAT, BIRD, FISH, RABBIT, RODENT, REPTILE, HORSE, OTHER }
enum class Sex { MALE, FEMALE }

/** PET-01, PET-02. An empty [id] means a new pet. */
data class Pet(
    val id: String,
    val name: String,
    val species: Species,
    val breed: String? = null,
    val sex: Sex? = null,
    val birthDate: LocalDate? = null,
    val birthDateEstimated: Boolean = false,
    val colour: String? = null,
    val microchip: String? = null,
    val neutered: Boolean = false,
    val licenceNumber: String? = null,
    val licenceMunicipality: String? = null,
    val licenceExpiry: LocalDate? = null,
    val insurer: String? = null,
    val policyNumber: String? = null,
    val insuranceRenewal: LocalDate? = null,
    val ownerMemberId: String? = null,
    val notes: String? = null,
    val archived: Boolean = false,
)

enum class RenewalKind { PET_LICENCE, PET_INSURANCE, REGISTRATION, VEHICLE_INSURANCE, WARRANTY, LOAN_RENEWAL, CARD_ANNUAL_FEE, CARD_PAYMENT_DUE, MEDICAL_CLAIM, ASSET_WARRANTY, INSURANCE_RENEWAL, TAX_INSTALMENT, SECURITY_MATURITY }

/** A licence, policy, registration or warranty that expires soon or has expired. */
data class Renewal(val kind: RenewalKind, val subjectId: String, val subjectName: String, val date: LocalDate, val daysLeft: Int, val detail: String? = null)

/** What something cost over a period, in the base currency, as positive amounts (PET-05, VEH-10). */
data class CostSummary(
    val total: Money,
    val byCategory: List<Pair<String?, Money>>,
    val byYear: Map<Int, Money>,
    /** Lines in another currency with no exchange rate, left out of the totals. */
    val unconverted: Int,
    /** VEH-10: each year's costs by category, largest first. */
    val byYearCategory: Map<Int, List<Pair<String?, Money>>> = emptyMap(),
)

/**
 * Pets in the household (PET-01 to PET-05). Health records and appointments use the pet's id.
 * Pets belong to the whole household: anyone but a viewer can add, change or delete them (HH-06).
 */
class PetService internal constructor(private val books: Books) {

    fun list(includeArchived: Boolean = false): List<Pet> =
        books.core.pets().executeAsList().map { it.toPet() }.filter { includeArchived || !it.archived }

    fun get(petId: String): Pet = list(includeArchived = true).firstOrNull { it.id == petId } ?: throw ValidationException("error.notFound")

    fun save(pet: Pet): Pet {
        requireChanger()
        validate(pet.name.isNotBlank(), "error.nameRequired")
        val id = pet.id.ifBlank { Ids.newId() }
        val existing = books.core.pets().executeAsList().firstOrNull { it.id == id }
        val now = books.now()
        with(pet) {
            books.core.upsertPet(
                id, name.trim(), species.name, breed.blankToNull(), sex?.name, birthDate?.toString(), if (birthDateEstimated) 1 else 0,
                colour.blankToNull(), microchip.blankToNull(), if (neutered) 1 else 0, licenceNumber.blankToNull(), licenceMunicipality.blankToNull(),
                licenceExpiry?.toString(), insurer.blankToNull(), policyNumber.blankToNull(), insuranceRenewal?.toString(), ownerMemberId,
                notes.blankToNull(), if (archived) 1 else 0, existing?.created_at ?: now, now,
            )
        }
        books.session.audit(if (existing == null) "CREATE" else "UPDATE", "pet", id)
        return get(id)
    }

    fun delete(petId: String) {
        requireChanger()
        books.core.deletePet(petId)
        books.session.audit("DELETE", "pet", petId)
    }

    /** PET-02: licences and pet insurance expiring within [withinDays], or already expired. */
    fun renewals(today: LocalDate, withinDays: Int = 30): List<Renewal> = list().flatMap { p ->
        listOfNotNull(
            p.licenceExpiry?.let { Renewal(RenewalKind.PET_LICENCE, p.id, p.name, it, today.daysUntil(it), p.licenceMunicipality) },
            p.insuranceRenewal?.let { Renewal(RenewalKind.PET_INSURANCE, p.id, p.name, it, today.daysUntil(it), p.insurer) },
        )
    }.filter { it.daysLeft <= withinDays }.sortedBy { it.date }

    /** PET-05: what the pet cost between [from] and [to]. */
    fun costs(petId: String, from: LocalDate, to: LocalDate): CostSummary = books.costs(from, to) { q ->
        q.memberLines(petId, from.toString(), to.toString()).executeAsList().map { CostLine(it.date, it.account_id, it.category_id, it.amount_minor) }
    }

    /** Whether the signed-in user may add, change or delete pets: everyone but a viewer. */
    val canChange: Boolean get() = books.role != Role.VIEWER

    private fun requireChanger() {
        if (!canChange) throw AccessDeniedException("A viewer cannot change pets")
    }

    private fun PetRow.toPet() = Pet(
        id, name, Species.valueOf(species), breed, sex?.let(Sex::valueOf), birth_date?.let(LocalDate::parse), birth_date_estimated == 1L, colour,
        microchip, neutered == 1L, licence_number, licence_municipality, licence_expiry?.let(LocalDate::parse), insurer, policy_number,
        insurance_renewal?.let(LocalDate::parse), owner_member_id, notes, archived == 1L,
    )
}

internal fun String?.blankToNull(): String? = this?.trim()?.ifEmpty { null }

internal class CostLine(val date: String, val accountId: String, val categoryId: String?, val amountMinor: Long)

/**
 * Adds up transaction lines from every ledger the user can see, converted to the base currency at
 * the rate of their date. Spending becomes a positive cost; refunds reduce it.
 */
internal fun Books.costs(
    from: LocalDate,
    to: LocalDate,
    extra: List<Pair<LocalDate, Pair<String?, Money>>> = emptyList(),
    lines: (ca.schippers.hfm.data.ledger.LedgerQueries) -> List<CostLine>,
): CostSummary {
    val base = rates.baseCurrency
    val currencies = accounts.all(includeClosed = true).associate { it.id to it.currency }
    var unconverted = 0
    val items = ArrayList<Pair<LocalDate, Pair<String?, Money>>>()
    for (group in groups()) {
        for (line in lines(ledger(group).ledgerQueries)) {
            val date = LocalDate.parse(line.date)
            val money = Money.ofMinor(-line.amountMinor, currencies[line.accountId] ?: base)
            val converted = if (money.currency == base) money else rates.convert(money, base, date)
            if (converted == null) unconverted++ else items += date to (line.categoryId to converted)
        }
    }
    for ((date, entry) in extra) {
        if (date < from || date > to) continue
        val converted = if (entry.second.currency == base) entry.second else rates.convert(entry.second, base, date)
        if (converted == null) unconverted++ else items += date to (entry.first to converted)
    }
    val zero = Money.zero(base)
    val byCategory = items.groupBy({ it.second.first }, { it.second.second })
        .map { (category, list) -> category to list.fold(zero) { a, b -> a + b } }
        .sortedByDescending { it.second.minorUnits }
    val byYear = items.groupBy({ it.first.year }, { it.second.second }).mapValues { (_, list) -> list.fold(zero) { a, b -> a + b } }.toSortedMap()
    val byYearCategory = items.groupBy { it.first.year }.mapValues { (_, list) ->
        list.groupBy({ it.second.first }, { it.second.second }).map { (category, amounts) -> category to amounts.fold(zero) { a, b -> a + b } }.sortedByDescending { it.second.minorUnits }
    }.toSortedMap()
    return CostSummary(items.fold(zero) { a, b -> a + b.second.second }, byCategory, byYear, unconverted, byYearCategory)
}
