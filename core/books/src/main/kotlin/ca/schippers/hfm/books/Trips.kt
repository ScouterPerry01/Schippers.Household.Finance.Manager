package ca.schippers.hfm.books

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.medical.Medical
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode

/** OTH-02, MED-11: why a trip was made, for the claims it supports. */
enum class TripPurpose { BUSINESS, EMPLOYMENT, MEDICAL, PERSONAL }

/** One trip by car; [kmOneWay] is doubled for a round trip. */
data class Trip(
    val id: String,
    val groupId: String,
    val date: LocalDate,
    val destination: String,
    val kmOneWay: BigDecimal,
    val roundTrip: Boolean,
    val purpose: TripPurpose,
    val vehicleId: String? = null,
    val memberId: String? = null,
    val origin: String? = null,
    val notes: String? = null,
) {
    val km: BigDecimal get() = if (roundTrip) kmOneWay * BigDecimal(2) else kmOneWay
}

/** A vehicle's year: the kilometres on its odometer, those logged for work, and the share that was for work. */
data class VehicleUse(val vehicleId: String, val totalKm: Int?, val workKm: BigDecimal) {
    /** Business and employment use, in percent of all the kilometres driven, when the odometer shows them. */
    val workPercent: Int? get() = totalKm?.takeIf { it > 0 }?.let { (workKm * BigDecimal(100) / BigDecimal(it)).setScale(0, RoundingMode.HALF_UP).toInt().coerceAtMost(100) }
}

/**
 * MED-11: a rate per kilometre for medical travel in dollars, which may have a half cent, for [province], in effect from [from]: built in
 * (with its [source]) or the household's own.
 */
data class MedicalTravelRate(val rate: BigDecimal, val province: Province, val from: LocalDate, val builtIn: Boolean, val source: String?)

/** OTH-02, MED-11: the trip log, business use of each vehicle, and medical travel. */
class TripService internal constructor(private val books: Books) {

    fun list(year: Int): List<Trip> = books.groups().flatMap { g ->
        books.ledger(g).extrasQueries.trips(LocalDate(year, 1, 1).toString(), LocalDate(year, 12, 31).toString()).executeAsList().map { r ->
            Trip(
                r.id, g.id, LocalDate.parse(r.date), r.destination, BigDecimal(r.km_tenths).movePointLeft(1), r.round_trip == 1L,
                TripPurpose.valueOf(r.purpose), r.vehicle_id, r.member_id, r.origin, r.notes,
            )
        }
    }.sortedByDescending { it.date }

    fun save(t: Trip): Trip {
        validate(t.destination.isNotBlank(), "error.tripDestination")
        validate(t.kmOneWay.signum() > 0 && t.kmOneWay < BigDecimal(10_000), "error.tripDistance")
        val existing = if (t.id.isBlank()) null else groupOf(t.id)
        val group = books.group(existing ?: t.groupId).also { books.require(it, PermissionLevel.EDIT) }
        val id = t.id.ifBlank { Ids.newId() }
        books.ledger(group).extrasQueries.upsertTrip(
            id, t.date.toString(), t.vehicleId, t.memberId, t.origin?.trim()?.ifEmpty { null }, t.destination.trim(),
            t.kmOneWay.movePointRight(1).setScale(0, RoundingMode.HALF_UP).toLong(), if (t.roundTrip) 1 else 0, t.purpose.name, t.notes?.trim()?.ifEmpty { null },
        )
        return t.copy(id = id, groupId = group.id)
    }

    fun delete(t: Trip) = books.ledger(books.group(t.groupId).also { books.require(it, PermissionLevel.EDIT) }).extrasQueries.deleteTrip(t.id)

    /** The group a trip is kept in, whatever its year. */
    private fun groupOf(id: String): String? = books.groups().firstOrNull { g ->
        books.ledger(g).extrasQueries.trips("0000-01-01", "9999-12-31").executeAsList().any { it.id == id }
    }?.id

    /** Kilometres per person (null for no one in particular) and purpose in [year]. */
    fun totals(year: Int): Map<Pair<String?, TripPurpose>, BigDecimal> =
        list(year).groupBy { it.memberId to it.purpose }.mapValues { (_, l) -> l.fold(BigDecimal.ZERO) { a, t -> a + t.km } }

    /**
     * OTH-02: each vehicle's kilometres in [year] from its odometer readings (the first and last of the
     * year), against the business and employment kilometres logged for it.
     */
    fun vehicleUse(year: Int): List<VehicleUse> {
        val trips = list(year).filter { it.purpose == TripPurpose.BUSINESS || it.purpose == TripPurpose.EMPLOYMENT }
        return books.vehicles.list(includeInactive = true).mapNotNull { v ->
            val work = trips.filter { it.vehicleId == v.id }.fold(BigDecimal.ZERO) { a, t -> a + t.km }
            val readings = books.vehicles.readings(v.id).filter { it.date.year == year }.sortedBy { it.date }
            val total = if (readings.size >= 2) readings.last().odometer - readings.first().odometer else null
            if (work.signum() == 0 && total == null) null else VehicleUse(v.id, total, work)
        }
    }

    /** MED-11: the one-way distance from which a medical trip counts, on [on] (rule medical.travel.min.km, 40 km). */
    fun medicalMinimumKm(on: LocalDate): BigDecimal = Medical.travelMinimumKm(on)

    /**
     * MED-11: a medical trip of at least [medicalMinimumKm] one way (care not available closer to
     * home) qualifies for vehicle expenses; the CRA's simplified method pays a rate per kilometre,
     * set per province each year (see [medicalRate]).
     */
    fun qualifiesForMedical(t: Trip): Boolean = t.purpose == TripPurpose.MEDICAL && t.kmOneWay >= medicalMinimumKm(t.date)

    /**
     * MED-11: records a qualifying medical trip as a medical expense for [memberId], at [rate]
     * dollars a kilometre (the CRA's rates have half cents). A trip is added once: the expense is remembered, and adding the trip again is
     * refused while that expense exists (delete the expense to add the trip anew).
     */
    fun addToMedical(t: Trip, memberId: String, rate: BigDecimal, groupId: String): MedExpense {
        validate(qualifiesForMedical(t), "error.tripNotMedical")
        validate(rate.signum() > 0, "error.tripRate")
        validate(t.id.isBlank() || medicalExpense(t) == null, "error.tripAlreadyMedical")
        val amount = Money.of((rate * t.km).setScale(2, RoundingMode.HALF_UP), Currency.CAD)
        val expense = books.medical.saveExpense(
            MedExpense(
                "", groupId, memberId, MedService.MEDICAL_TRAVEL, t.date, amount, paidDate = t.date,
                description = "${t.destination} · ${t.km.stripTrailingZeros().toPlainString()} km",
            ),
        )
        if (t.id.isNotBlank()) books.putSetting("$MEDICAL_KEY.${t.id}", expense.id)
        return expense
    }

    /** MED-11: the medical expense the trip was added as, while it still exists. */
    fun medicalExpense(t: Trip): MedExpense? = books.setting("$MEDICAL_KEY.${t.id}")?.ifBlank { null }
        ?.let { id -> books.medical.expenses().firstOrNull { it.id == id } }

    /**
     * MED-11: the rate per kilometre for a medical trip on [date] by [memberId]: the rule
     * medical.travel.rate for the province or territory the person lives in (where the travel
     * begins), or null when none is set. A rate typed for the year before rates and rules existed
     * (setting `trip.medicalRate.<year>`) counts as the household's own value for that year, for
     * every province, unless the household has since added its own rule value for the year.
     */
    fun medicalRate(date: LocalDate, memberId: String?): MedicalTravelRate? {
        val province = books.provinceOf(memberId)
        val rule = Medical.travelRate(date, province)
        val yearStart = LocalDate(date.year, 1, 1)
        val typed = books.setting("$RATE_KEY.${date.year}")?.ifBlank { null }?.let { runCatching { BigDecimal(it.trim()) }.getOrNull() }
        if (typed != null && typed.signum() > 0 && (rule == null || rule.builtIn || rule.from < yearStart)) {
            return MedicalTravelRate(typed, province, yearStart, builtIn = false, source = null)
        }
        return rule?.let { MedicalTravelRate(BigDecimal(it.value), province, it.from, it.builtIn, it.source) }
    }

    /**
     * MED-11: keeps [rate] as the household's medical travel rate for [province] from January 1 of
     * [year], in Rates and rules (an administrator's change, as any rule value).
     */
    fun keepMedicalRate(year: Int, province: Province, rate: BigDecimal) {
        validate(rate.signum() > 0, "error.tripRate")
        books.rateRules.add("medical.travel.rate", province, LocalDate(year, 1, 1), rate.stripTrailingZeros().toPlainString())
    }

    companion object {
        /** The rate typed for a year before rates and rules: `trip.medicalRate.<year>`, still read. */
        private const val RATE_KEY = "trip.medicalRate"

        /** The setting that remembers the medical expense a trip became: `trip.medicalExpense.<trip id>`. */
        private const val MEDICAL_KEY = "trip.medicalExpense"
    }
}
