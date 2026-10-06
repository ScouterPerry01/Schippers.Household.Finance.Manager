package ca.schippers.hfm.books

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode

/** VEH-07, TRP-05: the fuel or energy used from one full tank (or charge) to the next. */
data class FillInterval(
    val from: LocalDate,
    val to: LocalDate,
    val fromOdometer: Int,
    val toOdometer: Int,
    val quantity: BigDecimal,
    /** In minor units; null when a fill-up in the interval has no cost. */
    val costMinor: Long?,
) {
    val distanceKm: Int get() = toOdometer - fromOdometer
}

/** TRP-10: what electricity cost at home or in public: kWh bought, what they cost, and the price of a kWh. */
data class ChargingCost(val charging: Charging, val kwh: BigDecimal, val cost: Money, val perKwh: BigDecimal?)

/** TRP-08: one horizon of a vehicle's forecast. */
data class ForecastPeriod(
    val months: Int,
    val distanceKm: Int,
    /** Litres or kWh, when the consumption is known. */
    val quantity: BigDecimal?,
    /** Fuel or energy, when its price is known too. */
    val energyCost: Money?,
    /** Maintenance whose cost is known from earlier services. */
    val maintenance: Money,
    /** Each maintenance task due in the period and how many times. */
    val tasks: List<Pair<String, Int>>,
    /** Tasks due that no earlier service gives a cost for. */
    val unpriced: List<String>,
    /** A plug-in hybrid's charging for the distance: kWh, when its consumption is known. */
    val electricityKwh: BigDecimal? = null,
    /** And what they cost, when the price of a kWh is known too. */
    val electricityCost: Money? = null,
) {
    val total: Money get() = listOfNotNull(energyCost, electricityCost).fold(maintenance) { a, m -> a + m }
}

/** A plug-in hybrid's electricity (TRP-08): kWh/100 km over all the distance driven, and the recent price of a kWh. */
data class ChargingForecast(val per100km: BigDecimal?, val unitPrice: BigDecimal?)

/**
 * TRP-08: the coming months of a vehicle: the distance at the pace of the last 90 days, the fuel or
 * energy for it at the consumption of each kind of driving (normal, towing, heavy load) in the
 * shares of the last 90 days, at recent prices, and the maintenance falling due.
 */
data class VehicleForecast(
    val vehicleId: String,
    val energy: Energy,
    val currency: Currency,
    val kmPerDay: Double?,
    /** Percent of the distance driven towing and with a heavy load, from the trips of the last 90 days. */
    val shares: Map<TripLoad, Int>,
    /** L/100 km or kWh/100 km used for each kind of driving. */
    val consumption: Map<TripLoad, BigDecimal>,
    /** The price of a litre or kWh. */
    val unitPrice: BigDecimal?,
    val periods: List<ForecastPeriod>,
    /** For a plug-in hybrid, its charging beside the fuel of [energy]; null for other vehicles. */
    val charging: ChargingForecast? = null,
)

/** TRP-08: an amount a month for a Transport category, from the vehicles' forecasts, and the budget it has now. */
data class VehicleBudgetLine(val categoryId: String, val monthly: Money, val current: Money?)

/** The arithmetic of the fuel log, apart so it can be checked on its own. */
internal object FuelMath {

    /**
     * VEH-07: full tank to full tank. The fuel bought after one full tank, up to and including the
     * next, was used over the distance between them. [entries] must have odometers, oldest first.
     */
    fun intervals(entries: List<FuelEntry>): List<FillInterval> {
        val out = ArrayList<FillInterval>()
        var lastFull: FuelEntry? = null
        var since = BigDecimal.ZERO
        var sinceCost = 0L
        var costed = true
        for (e in entries) {
            since += e.quantity
            if (e.cost != null) sinceCost += e.cost.minorUnits else costed = false
            if (!e.fullTank) continue
            val start = lastFull
            if (start != null && e.odometer!! > start.odometer!!) {
                out += FillInterval(start.date, e.date, start.odometer, e.odometer, since, sinceCost.takeIf { costed })
            }
            lastFull = e
            since = BigDecimal.ZERO
            sinceCost = 0L
            costed = true
        }
        return out
    }

    /**
     * TRP-05: the kind of driving an interval stands for: towing when at least half its distance
     * was driven towing, a heavy load likewise, otherwise normal driving. Only trips with both
     * odometers count, for the part of their distance inside the interval.
     */
    fun loadOf(interval: FillInterval, trips: List<Trip>): TripLoad {
        val total = interval.distanceKm.takeIf { it > 0 } ?: return TripLoad.NONE
        fun km(load: TripLoad) = trips.filter { it.load == load && it.startOdometer != null && it.endOdometer != null }.sumOf { t ->
            (minOf(t.endOdometer!!, interval.toOdometer) - maxOf(t.startOdometer!!, interval.fromOdometer)).coerceAtLeast(0)
        }
        return when {
            km(TripLoad.TOWING) * 2 >= total -> TripLoad.TOWING
            km(TripLoad.HEAVY) * 2 >= total -> TripLoad.HEAVY
            else -> TripLoad.NONE
        }
    }

    /** Consumption and cost per km over [intervals]; [quantity] is everything bought in the period. */
    fun stats(intervals: List<FillInterval>, quantity: BigDecimal, currency: Currency): FuelStats {
        val distance = intervals.sumOf { it.distanceKm }
        val used = intervals.fold(BigDecimal.ZERO) { a, i -> a + i.quantity }
        val costed = intervals.all { it.costMinor != null }
        val cost = intervals.sumOf { it.costMinor ?: 0L }
        val per100 = if (distance > 0) used.multiply(BigDecimal(100)).divide(BigDecimal(distance), 1, RoundingMode.HALF_UP) else null
        val perKm = if (distance > 0 && costed) Money.ofMinor((cost + distance / 2) / distance, currency) else null
        return FuelStats(distance, quantity, per100, perKm)
    }

    /**
     * TRP-08: how many times a task falls due between [from] and [to]: first on [next] (today when
     * overdue), then every [everyDays].
     */
    fun occurrences(next: LocalDate?, everyDays: Double?, from: LocalDate, to: LocalDate): Int {
        val first = maxOf(next ?: return 0, from)
        if (first > to) return 0
        val step = everyDays?.takeIf { it >= 1.0 } ?: return 1
        val days = to.toEpochDays() - first.toEpochDays()
        return 1 + (days / step).toInt()
    }
}
