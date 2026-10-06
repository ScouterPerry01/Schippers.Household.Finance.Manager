package ca.schippers.hfm.books

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.medical.Medical
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.sync.PhoneTrip
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.math.BigDecimal
import java.math.RoundingMode
import ca.schippers.hfm.data.ledger.Trip as TripRow

/** OTH-02, MED-11: why a trip was made, for the claims it supports. */
enum class TripPurpose { BUSINESS, EMPLOYMENT, MEDICAL, PERSONAL }

/** TRP-04: what the vehicle pulled or carried: nothing special, a trailer, or a heavy load. */
enum class TripLoad { NONE, TOWING, HEAVY }

/**
 * One trip by car; [kmOneWay] is doubled for a round trip. A trip from the phone (TRP-01) has its
 * times, odometers and places at both ends; its distance is the odometers' difference (TRP-03).
 */
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
    val startAt: LocalDateTime? = null,
    val endAt: LocalDateTime? = null,
    val startOdometer: Int? = null,
    val endOdometer: Int? = null,
    val startPlaceId: String? = null,
    val endPlaceId: String? = null,
    val load: TripLoad = TripLoad.NONE,
    /** TRP-04: the trailer towed, an asset of the kind trailer. */
    val trailerId: String? = null,
    val passengers: String? = null,
    /** TRP-09: the province or state the trip was driven in, two letters. */
    val province: String? = null,
    /** The phone the trip came from. */
    val deviceId: String? = null,
) {
    val km: BigDecimal get() = if (roundTrip) kmOneWay * BigDecimal(2) else kmOneWay

    /** TRP-03: the time travelled, in minutes, when both times are known. */
    val minutes: Long? get() = if (startAt != null && endAt != null) {
        (endAt.toInstant(TimeZone.UTC) - startAt.toInstant(TimeZone.UTC)).inWholeMinutes
    } else {
        null
    }
}

/**
 * TRP-09: one line of a vehicle's logbook as the CRA asks for it: the date, the places at each end,
 * the purpose, the odometer at each end and the distance, the driver and the province or state.
 */
data class LogbookLine(
    val date: LocalDate,
    val from: String?,
    val to: String,
    val purpose: TripPurpose,
    val startOdometer: Int?,
    val endOdometer: Int?,
    val km: BigDecimal,
    val driverId: String?,
    val province: String?,
)

/** TRP-09: a vehicle's logbook for a year, with the year's distance on the odometer and the business share. */
data class Logbook(val vehicle: Vehicle, val year: Int, val lines: List<LogbookLine>, val use: VehicleUse?)

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
        books.ledger(g).extrasQueries.trips(LocalDate(year, 1, 1).toString(), LocalDate(year, 12, 31).toString()).executeAsList().map { it.toTrip(g.id) }
    }.sortedWith(compareByDescending<Trip> { it.date }.thenByDescending { it.startAt })

    /** TRP-06: every trip made with [vehicleId], in every group the user can see, oldest first. */
    fun forVehicle(vehicleId: String): List<Trip> = books.groups().flatMap { g ->
        books.ledger(g).extrasQueries.tripsForVehicle(vehicleId).executeAsList().map { it.toTrip(g.id) }
    }.sortedWith(compareBy<Trip> { it.date }.thenBy { it.startAt }.thenBy { it.startOdometer })

    fun save(t: Trip): Trip = store(t, PermissionLevel.EDIT)

    /**
     * Saves [t]. With both odometers, the distance is their difference and the trip is one way
     * (TRP-03). The places' names fill in the origin and destination left empty; the province is
     * the one chosen, else the start place's, else the driver's (TRP-09).
     */
    private fun store(t: Trip, level: PermissionLevel): Trip {
        val places = (listOfNotNull(t.startPlaceId, t.endPlaceId)).associateWith { books.places.find(it) }
        val destination = t.destination.trim().ifEmpty { t.endPlaceId?.let { places[it]?.name }.orEmpty() }
        val origin = t.origin?.trim()?.ifEmpty { null } ?: t.startPlaceId?.let { places[it]?.name }
        validate(destination.isNotBlank(), "error.tripDestination")
        val byOdometer = t.startOdometer != null && t.endOdometer != null
        if (byOdometer) validate(t.endOdometer > t.startOdometer && t.endOdometer - t.startOdometer < 10_000, "error.tripOdometers")
        validate(t.startOdometer == null || t.startOdometer >= 0, "error.invalidNumber")
        validate(t.startAt == null || t.endAt == null || t.endAt >= t.startAt, "error.tripTimes")
        val km = if (byOdometer) BigDecimal(t.endOdometer - t.startOdometer) else t.kmOneWay
        validate(km.signum() > 0 && km < BigDecimal(10_000), "error.tripDistance")
        val existing = if (t.id.isBlank()) null else groupOf(t.id)
        // A trip in a vehicle is kept with the vehicle, by someone who may add there: its odometers become the
        // vehicle's readings, and a private group's vehicle must not have its trips land where others read.
        val vehicleGroup = t.vehicleId?.let { id -> books.group(books.vehicles.get(id).groupId).also { books.require(it, level) } }
        val group = books.group(existing ?: vehicleGroup?.id ?: t.groupId).also { books.require(it, level) }
        val id = t.id.ifBlank { Ids.newId() }
        val province = t.province?.trim()?.uppercase()?.ifEmpty { null }
            ?: t.startPlaceId?.let { places[it]?.province }
            ?: books.provinceOf(t.memberId).name
        val saved = t.copy(
            id = id, groupId = group.id, destination = destination, origin = origin, kmOneWay = km, roundTrip = t.roundTrip && !byOdometer, province = province,
            trailerId = t.trailerId.takeIf { t.load == TripLoad.TOWING }, date = t.startAt?.date ?: t.date,
        )
        with(saved) {
            books.ledger(group).extrasQueries.upsertTrip(
                id, date.toString(), vehicleId, memberId, origin, destination,
                kmOneWay.movePointRight(1).setScale(0, RoundingMode.HALF_UP).toLong(), if (roundTrip) 1 else 0, purpose.name, notes?.trim()?.ifEmpty { null },
                startAt?.let(::minuteText), endAt?.let(::minuteText), startOdometer?.toLong(), endOdometer?.toLong(), startPlaceId, endPlaceId,
                load.name, trailerId, passengers?.trim()?.ifEmpty { null }, province, deviceId,
            )
        }
        return saved
    }

    fun delete(t: Trip) = books.ledger(books.group(t.groupId).also { books.require(it, PermissionLevel.EDIT) }).extrasQueries.deleteTrip(t.id)

    /** The group a trip is kept in, whatever its year. */
    private fun groupOf(id: String): String? = books.groups().firstOrNull { g ->
        books.ledger(g).extrasQueries.tripById(id).executeAsOneOrNull() != null
    }?.id

    /**
     * TRP-01, TRP-06: a trip driven with the phone, kept with its vehicle (refused unless the user may add there). Its distance is the odometers'
     * difference; places the phone saved arrive before the trips that use them. The trip keeps the
     * phone's own id, so receiving it again changes nothing.
     */
    fun receive(groupId: String, deviceId: String, p: PhoneTrip): Trip {
        val vehicle = books.vehicles.get(p.vehicleId)
        val start = runCatching { LocalDateTime.parse(p.start) }.getOrElse { throw ValidationException("error.invalidDate") }
        val end = runCatching { LocalDateTime.parse(p.end) }.getOrElse { throw ValidationException("error.invalidDate") }
        val startPlace = p.startPlaceId?.let { books.places.find(it) }
        val endPlace = p.endPlaceId?.let { books.places.find(it) }
        if (groupOf(p.id) != null) return list(start.date.year).first { it.id == p.id }
        // What the phone names must be the household's: a driver among the members, a trailer among the assets.
        validate(p.driverId == null || books.members.list(includeArchived = true).any { it.id == p.driverId }, "error.memberRequired")
        validate(p.trailerId == null || books.assets.list().any { it.id == p.trailerId && it.kind == AssetKind.TRAILER }, "error.notFound")
        return store(
            Trip(
                p.id, groupId, start.date, endPlace?.name ?: phoneText(p.endPlace, MAX_PLACE) ?: vehicle.name, BigDecimal.ZERO, false,
                TripPurpose.entries.firstOrNull { it.name == p.purpose } ?: TripPurpose.PERSONAL, vehicle.id, p.driverId,
                startPlace?.name ?: phoneText(p.startPlace, MAX_PLACE), phoneText(p.notes, MAX_TEXT),
                start, end, p.startOdometer, p.endOdometer, startPlace?.id, endPlace?.id,
                TripLoad.entries.firstOrNull { it.name == p.load } ?: TripLoad.NONE, p.trailerId,
                p.passengers.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ").take(MAX_TEXT).ifEmpty { null }, null, deviceId,
            ),
            PermissionLevel.CAPTURE_ONLY,
        )
    }

    /** TRP-09: the logbook of [vehicleId] for [year], oldest first, as the CRA asks for it. */
    fun logbook(vehicleId: String, year: Int): Logbook {
        val vehicle = books.vehicles.get(vehicleId)
        val lines = forVehicle(vehicleId).filter { it.date.year == year }.map { t ->
            LogbookLine(t.date, t.origin, t.destination, t.purpose, t.startOdometer, t.endOdometer, t.km, t.memberId, t.province ?: books.provinceOf(t.memberId).name)
        }
        return Logbook(vehicle, year, lines, vehicleUse(year).firstOrNull { it.vehicleId == vehicleId })
    }

    /**
     * TRP-09: kilometres per province or state in [year], for fuel tax reports such as IFTA: each
     * trip counts where it was driven, as chosen on the trip (by default where it started). With
     * [vehicleId], that vehicle's trips only.
     */
    fun kmByProvince(year: Int, vehicleId: String? = null): Map<String, BigDecimal> =
        list(year).filter { vehicleId == null || it.vehicleId == vehicleId }
            .groupBy { it.province ?: books.provinceOf(it.memberId).name }
            .mapValues { (_, l) -> l.fold(BigDecimal.ZERO) { a, t -> a + t.km } }
            .toSortedMap()

    private fun TripRow.toTrip(groupId: String) = Trip(
        id, groupId, LocalDate.parse(date), destination, BigDecimal(km_tenths).movePointLeft(1), round_trip == 1L,
        TripPurpose.valueOf(purpose), vehicle_id, member_id, origin, notes,
        start_at?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }, end_at?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
        start_odometer?.toInt(), end_odometer?.toInt(), start_place_id, end_place_id,
        TripLoad.entries.firstOrNull { it.name == load_kind } ?: TripLoad.NONE, trailer_id, passengers, province, device_id,
    )

    /** "2026-10-06T08:05": kept to the minute. */
    private fun minuteText(t: LocalDateTime): String = "%04d-%02d-%02dT%02d:%02d".format(t.year, t.month.ordinal + 1, t.day, t.hour, t.minute)

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
            val readings = books.vehicles.readings(v.id).filter { it.date.year == year }
            val total = if (readings.size >= 2) readings.maxOf { it.odometer } - readings.minOf { it.odometer } else null
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

        /** Notes and passengers sent by a phone are cut to this length. */
        private const val MAX_TEXT = 500

        /** A place typed on the phone, as long as a saved place's name. */
        private const val MAX_PLACE = 120
    }
}
