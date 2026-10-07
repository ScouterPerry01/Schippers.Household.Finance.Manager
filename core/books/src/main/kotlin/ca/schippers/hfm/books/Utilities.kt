package ca.schippers.hfm.books

import ca.schippers.hfm.calc.rules.Thresholds
import ca.schippers.hfm.calc.trackers.MonthComparison
import ca.schippers.hfm.calc.trackers.MonthUse
import ca.schippers.hfm.calc.trackers.TankProjection
import ca.schippers.hfm.calc.trackers.Tanks
import ca.schippers.hfm.calc.trackers.Usage
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import java.math.BigDecimal
import java.math.MathContext

/** UTL-01: what a meter measures, and in what unit: kilowatt-hours or cubic metres. */
enum class MeterKind(val unit: String) { ELECTRICITY("KWH"), GAS("M3"), WATER("M3") }

data class UtilityReading(
    val id: String,
    val date: LocalDate,
    val value: BigDecimal,
    val onPeak: BigDecimal? = null,
    val midPeak: BigDecimal? = null,
    val offPeak: BigDecimal? = null,
    val notes: String? = null,
    val fromPhone: Boolean = false,
)

/** UTL-01: a meter of a home or cottage ([assetId]), or of the household (null). */
data class UtilityMeter(
    val id: String,
    val groupId: String,
    val name: String,
    val kind: MeterKind,
    val assetId: String? = null,
    val timeOfUse: Boolean = false,
    /** The bill whose amounts give the cost per unit. */
    val billId: String? = null,
    val archived: Boolean = false,
    val notes: String? = null,
    val readings: List<UtilityReading> = emptyList(),
)

/** A price per unit (kWh, cubic metre or litre), in [currency]. */
data class UnitCost(val perUnit: BigDecimal, val currency: Currency)

/** UTL-01: a meter's use by month (newest first, each compared), and its cost per unit when it has a bill. */
data class MeterSummary(val meter: UtilityMeter, val months: List<MonthComparison>, val cost: UnitCost?) {
    val unusual: List<MonthComparison> get() = months.filter { it.unusual }
}

/**
 * UTL-01: a meter whose latest complete month (last month or the one before) used more than usual:
 * shown in the reminders, on the dashboard and on the phone, not only on the Utilities screen.
 */
data class UnusualUse(val meter: UtilityMeter, val month: MonthComparison)

/** UTL-02: what a tank holds. */
enum class FuelKind { PROPANE, HEATING_OIL }

data class TankReading(val id: String, val date: LocalDate, val percent: BigDecimal, val notes: String? = null, val fromPhone: Boolean = false)

data class TankDelivery(val id: String, val date: LocalDate, val litres: BigDecimal, val cost: Money? = null, val transactionId: String? = null, val notes: String? = null)

/** UTL-02: a propane or heating oil tank, its level readings and its deliveries. */
data class FuelTank(
    val id: String,
    val groupId: String,
    val name: String,
    val fuel: FuelKind,
    val capacityLitres: BigDecimal,
    val assetId: String? = null,
    /** Order when it is expected to fall to this percentage; null: Rates and rules. */
    val orderPercent: Int? = null,
    val supplier: String? = null,
    val archived: Boolean = false,
    val notes: String? = null,
    val readings: List<TankReading> = emptyList(),
    val deliveries: List<TankDelivery> = emptyList(),
)

/** UTL-02: where a tank stands today, its use by month (newest first) and what a litre cost lately. */
data class TankStatus(
    val tank: FuelTank,
    val orderPercent: Int,
    val projection: TankProjection?,
    val months: List<MonthUse>,
    val pricePerLitre: UnitCost?,
) {
    val levelPercent: BigDecimal? get() = projection?.let { Tanks.percent(it.levelToday, tank.capacityLitres) }
}

/**
 * UTL-01, UTL-02: utility meters and fuel tanks, per home or cottage or for the household, with
 * their readings (from the computer or the phone), use per month and what it costs. Readings and
 * deliveries can be added by anyone who may capture in the group; meters and tanks need an editor.
 */
class UtilityService internal constructor(private val books: Books) {

    private val mc = MathContext.DECIMAL64

    private fun group(groupId: String, level: PermissionLevel = PermissionLevel.EDIT) = books.group(groupId).also { books.require(it, level) }

    private fun number(text: String?): BigDecimal? = text?.toBigDecimalOrNull()

    // --- Meters ---------------------------------------------------------------------------------------

    fun meters(includeArchived: Boolean = false): List<UtilityMeter> = books.groups().flatMap { g ->
        val q = books.ledger(g).trackersQueries
        q.utilityMeters().executeAsList().map { m ->
            UtilityMeter(
                m.id, g.id, m.name, MeterKind.valueOf(m.kind), m.asset_id, m.time_of_use == 1L, m.bill_id, m.archived == 1L, m.notes,
                q.utilityReadings(m.id).executeAsList().map { r ->
                    UtilityReading(r.id, LocalDate.parse(r.date), BigDecimal(r.value_), number(r.on_peak), number(r.mid_peak), number(r.off_peak), r.notes, r.device_id != null)
                },
            )
        }
    }.filter { includeArchived || !it.archived }

    fun meter(id: String): UtilityMeter = meters(true).firstOrNull { it.id == id } ?: throw ValidationException("error.notFound")

    fun saveMeter(m: UtilityMeter): UtilityMeter {
        validate(m.name.isNotBlank(), "error.nameRequired")
        val group = group(meters(true).firstOrNull { it.id == m.id }?.groupId ?: m.groupId)
        val id = m.id.ifBlank { Ids.newId() }
        books.ledger(group).trackersQueries.upsertUtilityMeter(
            id, m.name.trim(), m.kind.name, m.assetId, if (m.timeOfUse) 1 else 0, m.billId, if (m.archived) 1 else 0, m.notes?.trim()?.ifEmpty { null }, books.now(),
        )
        return meter(id)
    }

    /** Deletes the meter and its readings. */
    fun deleteMeter(m: UtilityMeter) = books.ledger(group(m.groupId)).trackersQueries.deleteUtilityMeter(m.id)

    /**
     * Adds a reading: the meter's total, or with time of use its registers (the total is then their
     * sum when left out). [deviceId] names the phone it came from.
     */
    fun addReading(
        meterId: String, date: LocalDate, value: BigDecimal?, onPeak: BigDecimal? = null, midPeak: BigDecimal? = null, offPeak: BigDecimal? = null,
        notes: String? = null, deviceId: String? = null,
    ): UtilityReading {
        val m = meter(meterId)
        val registers = listOfNotNull(onPeak, midPeak, offPeak)
        val total = value ?: registers.takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO, BigDecimal::add)
        validate(total != null && total.signum() >= 0 && registers.all { it.signum() >= 0 }, "error.meterReading")
        val id = Ids.newId()
        books.ledger(group(m.groupId, PermissionLevel.CAPTURE_ONLY)).trackersQueries.insertUtilityReading(
            id, meterId, date.toString(), total!!.stripTrailingZeros().toPlainString(), onPeak?.toPlainString(), midPeak?.toPlainString(), offPeak?.toPlainString(),
            notes?.trim()?.ifEmpty { null }, deviceId, books.now(),
        )
        return meter(meterId).readings.first { it.id == id }
    }

    fun deleteReading(m: UtilityMeter, readingId: String) = books.ledger(group(m.groupId)).trackersQueries.deleteUtilityReading(readingId)

    /** The meter's use by month, compared with the same month last year and the months before (threshold of Rates and rules). */
    fun summary(m: UtilityMeter, today: LocalDate = books.today()): MeterSummary {
        val months = Usage.monthly(Usage.meterSpans(m.readings.map { it.date to it.value }))
        return MeterSummary(m, Usage.compare(months, Thresholds.unusualUtility(today)), cost(m, months, today))
    }

    /**
     * UTL-01: the meters whose latest complete month is unusual, when that month is last month or
     * the one before (readings often come a few days after a month ends). An older unusual month
     * stays on the Utilities screen only.
     */
    fun unusual(today: LocalDate = books.today()): List<UnusualUse> {
        val threshold = Thresholds.unusualUtility(today)
        val now = today.year * 12 + today.month.ordinal
        return meters().mapNotNull { m ->
            val latest = Usage.compare(Usage.monthly(Usage.meterSpans(m.readings.map { it.date to it.value })), threshold).firstOrNull { it.use.complete }
                ?: return@mapNotNull null
            val age = now - (latest.use.year * 12 + latest.use.month - 1)
            if (latest.unusual && age in 1..UNUSUAL_MONTHS) UnusualUse(m, latest) else null
        }
    }

    /**
     * The cost per unit from the meter's bill: what the bill came to over the last twelve months
     * (paid amounts, or the estimates of those not paid) divided by the use over the same months.
     */
    private fun cost(m: UtilityMeter, months: List<MonthUse>, today: LocalDate): UnitCost? {
        val billId = m.billId ?: return null
        val from = today.minus(DatePeriod(months = 12))
        val occurrences = runCatching { books.bills.occurrences(from, today, setOf(billId)) }.getOrDefault(emptyList()).filter { it.status != OccurrenceStatus.SKIPPED }
        if (occurrences.isEmpty()) return null
        val currency = occurrences.first().amount.currency
        val spent = occurrences.filter { it.amount.currency == currency }.fold(BigDecimal.ZERO) { a, o -> a + o.amount.toBigDecimal() }
        val fromIndex = from.year * 12 + from.month.ordinal
        val toIndex = today.year * 12 + today.month.ordinal
        val used = months.filter { (it.year * 12 + it.month - 1) in fromIndex until toIndex }.fold(BigDecimal.ZERO) { a, u -> a + u.amount }
        if (used.signum() <= 0 || spent.signum() <= 0) return null
        return UnitCost(spent.divide(used, mc), currency)
    }

    // --- Fuel tanks -----------------------------------------------------------------------------------

    fun tanks(includeArchived: Boolean = false): List<FuelTank> = books.groups().flatMap { g ->
        val q = books.ledger(g).trackersQueries
        q.fuelTanks().executeAsList().map { t ->
            FuelTank(
                t.id, g.id, t.name, FuelKind.valueOf(t.fuel), BigDecimal(t.capacity_litres), t.asset_id, t.order_percent?.toInt(), t.supplier, t.archived == 1L, t.notes,
                q.tankReadings(t.id).executeAsList().map { r -> TankReading(r.id, LocalDate.parse(r.date), BigDecimal(r.percent), r.notes, r.device_id != null) },
                q.tankDeliveries(t.id).executeAsList().map { d ->
                    TankDelivery(d.id, LocalDate.parse(d.date), BigDecimal(d.litres), d.cost_minor?.let { Money.ofMinor(it, Currency.of(d.currency ?: books.reports.base.code)) }, d.txn_id, d.notes)
                },
            )
        }
    }.filter { includeArchived || !it.archived }

    fun tank(id: String): FuelTank = tanks(true).firstOrNull { it.id == id } ?: throw ValidationException("error.notFound")

    fun saveTank(t: FuelTank): FuelTank {
        validate(t.name.isNotBlank(), "error.nameRequired")
        validate(t.capacityLitres.signum() > 0, "error.tankCapacity")
        validate(t.orderPercent == null || t.orderPercent in 1..90, "error.tankOrderPercent")
        val group = group(tanks(true).firstOrNull { it.id == t.id }?.groupId ?: t.groupId)
        val id = t.id.ifBlank { Ids.newId() }
        books.ledger(group).trackersQueries.upsertFuelTank(
            id, t.name.trim(), t.fuel.name, t.assetId, t.capacityLitres.stripTrailingZeros().toPlainString(), t.orderPercent?.toLong(), t.supplier?.trim()?.ifEmpty { null },
            if (t.archived) 1 else 0, t.notes?.trim()?.ifEmpty { null }, books.now(),
        )
        return tank(id)
    }

    /** Deletes the tank, its readings and deliveries; the transactions that paid them stay. */
    fun deleteTank(t: FuelTank) = books.ledger(group(t.groupId)).trackersQueries.deleteFuelTank(t.id)

    /** Adds a gauge reading, in [percent] or in [litres] (kept as its percent of the capacity). */
    fun addTankReading(tankId: String, date: LocalDate, percent: BigDecimal?, litres: BigDecimal? = null, notes: String? = null, deviceId: String? = null): TankReading {
        val t = tank(tankId)
        val p = percent ?: litres?.let { Tanks.percent(it, t.capacityLitres) }
        validate(p != null && p.signum() >= 0 && p <= BigDecimal(100), "error.tankLevel")
        val id = Ids.newId()
        books.ledger(group(t.groupId, PermissionLevel.CAPTURE_ONLY)).trackersQueries
            .insertTankReading(id, tankId, date.toString(), p!!.stripTrailingZeros().toPlainString(), notes?.trim()?.ifEmpty { null }, deviceId, books.now())
        return tank(tankId).readings.first { it.id == id }
    }

    fun deleteTankReading(t: FuelTank, readingId: String) = books.ledger(group(t.groupId)).trackersQueries.deleteTankReading(readingId)

    /**
     * Adds a delivery of [litres]; with [cost] and [accountId], also the payment in that account,
     * to the supplier, in the heating category.
     */
    fun addDelivery(tankId: String, date: LocalDate, litres: BigDecimal, cost: Money? = null, accountId: String? = null, notes: String? = null): TankDelivery {
        val t = tank(tankId)
        validate(litres.signum() > 0, "error.tankDelivery")
        validate(cost == null || cost.isPositive, "error.tankDeliveryCost")
        val group = group(t.groupId)
        val txn = if (cost != null && accountId != null) {
            val category = books.categories.list().firstOrNull { it.systemKey == "utilities.heating" }?.id
            books.transactions.create(TransactionDraft(accountId, date, -cost, t.supplier ?: t.name, listOf(SplitDraft(category, -cost, t.name)), "${litres.stripTrailingZeros().toPlainString()} L"))
        } else {
            null
        }
        val id = Ids.newId()
        books.ledger(group).trackersQueries.insertTankDelivery(
            id, tankId, date.toString(), litres.stripTrailingZeros().toPlainString(), cost?.minorUnits, cost?.currency?.code, txn?.id, notes?.trim()?.ifEmpty { null }, books.now(),
        )
        return tank(tankId).deliveries.first { it.id == id }
    }

    /** Deletes a delivery; with [withTransaction], also the payment recorded with it. */
    fun deleteDelivery(t: FuelTank, delivery: TankDelivery, withTransaction: Boolean = false, confirmReconciled: Boolean = false) {
        val group = group(t.groupId)
        if (withTransaction) delivery.transactionId?.let { id -> runCatching { books.transactions.get(id) }.getOrNull()?.let { books.transactions.delete(it.id, confirmReconciled) } }
        books.ledger(group).trackersQueries.deleteTankDelivery(delivery.id)
    }

    /** The order level in effect for [t] on [today], in percent. */
    fun orderPercent(t: FuelTank, today: LocalDate = books.today()): Int = t.orderPercent ?: Thresholds.tankOrderLevel(today)

    /**
     * Where the tank stands: its level today estimated from the last reading, the deliveries since
     * and the use per day over the readings of the last 90 days; its use by month; and the price of
     * a litre over the deliveries of the last twelve months.
     */
    fun status(t: FuelTank, today: LocalDate = books.today()): TankStatus {
        val levels = t.readings.map { it.date to Tanks.litres(it.percent, t.capacityLitres) }
        val deliveries = t.deliveries.map { it.date to it.litres }
        val spans = Usage.tankSpans(levels, deliveries)
        val order = orderPercent(t, today)
        // Readings come in date order: the last of the latest day counts.
        val last = t.readings.lastOrNull()
        val projection = last?.let { r ->
            val since = t.deliveries.filter { it.date > r.date && it.date <= today }.fold(BigDecimal.ZERO) { a, d -> a + d.litres }
            Tanks.project(r.date, Tanks.litres(r.percent, t.capacityLitres), since, Usage.dailyRate(spans), t.capacityLitres, Tanks.litres(BigDecimal(order), t.capacityLitres), today)
        }
        val priced = t.deliveries.filter { it.cost != null && it.date.daysUntil(today) in 0..365 }
        val currency = priced.firstOrNull()?.cost?.currency
        val price = currency?.let { c ->
            val same = priced.filter { it.cost!!.currency == c }
            val litres = same.fold(BigDecimal.ZERO) { a, d -> a + d.litres }
            litres.takeIf { it.signum() > 0 }?.let { UnitCost(same.fold(BigDecimal.ZERO) { a, d -> a + d.cost!!.toBigDecimal() }.divide(it, mc), c) }
        }
        return TankStatus(t, order, projection, Usage.monthly(spans).reversed(), price)
    }

    /**
     * UTL-02: tanks expected to fall to their order level within [withinDays] of [today] (or already
     * there), as renewals for the reminders and the calendar: dated when the level is reached.
     */
    fun orders(today: LocalDate, withinDays: Int = Thresholds.tankOrderDays(today)): List<Renewal> = tanks().mapNotNull { t -> order(t, today, withinDays) }

    /** [orders] for one tank: its order reminder, or null when the order level is further off than [withinDays]. */
    fun order(t: FuelTank, today: LocalDate, withinDays: Int = Thresholds.tankOrderDays(today)): Renewal? {
        val date = status(t, today).projection?.orderDate ?: return null
        val days = today.daysUntil(date)
        return if (days > withinDays) null else Renewal(RenewalKind.FUEL_ORDER, t.id, t.name, date, days, t.supplier)
    }

    private companion object {
        /** An unusual month is reminded about while it is last month or the one before. */
        const val UNUSUAL_MONTHS = 2
    }
}
