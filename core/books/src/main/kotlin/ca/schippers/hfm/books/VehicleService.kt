package ca.schippers.hfm.books

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.rules.LeadTimes
import ca.schippers.hfm.calc.rules.Thresholds
import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.calc.schedule.DueState
import ca.schippers.hfm.calc.schedule.DueStatus
import ca.schippers.hfm.calc.schedule.MaintenanceSchedule
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import java.math.BigDecimal
import java.math.RoundingMode
import ca.schippers.hfm.data.ledger.Fuel_entry as FuelRow
import ca.schippers.hfm.data.ledger.Maintenance_task as TaskRow
import ca.schippers.hfm.data.ledger.Service_record as ServiceRow
import ca.schippers.hfm.data.ledger.Vehicle as VehicleRow
import ca.schippers.hfm.data.ledger.Vehicle_warranty as WarrantyRow

enum class FuelType { GASOLINE, DIESEL, HYBRID, PLUG_IN_HYBRID, ELECTRIC, OTHER }
enum class VehicleStatus { ACTIVE, SOLD, RETIRED }
enum class WarrantyKind { MANUFACTURER, POWERTRAIN, EXTENDED, CORROSION, BATTERY, OTHER }
enum class ReadingSource { PURCHASE, READING, FUEL, SERVICE }
enum class TaskState { OK, SOON, DUE }

/** VEH-01, VEH-02. An empty [id] means a new vehicle, stored in [groupId]. */
data class Vehicle(
    val id: String,
    val groupId: String,
    val name: String,
    val make: String? = null,
    val model: String? = null,
    val modelYear: Int? = null,
    val trimLevel: String? = null,
    val colour: String? = null,
    val vin: String? = null,
    val plate: String? = null,
    val fuelType: FuelType = FuelType.GASOLINE,
    val driverMemberId: String? = null,
    val purchaseDate: LocalDate? = null,
    val purchasePrice: Money? = null,
    val seller: String? = null,
    val purchaseOdometer: Int? = null,
    val currency: Currency = Currency.CAD,
    val registrationRenewal: LocalDate? = null,
    val insurer: String? = null,
    val policyNumber: String? = null,
    val insuranceRenewal: LocalDate? = null,
    val status: VehicleStatus = VehicleStatus.ACTIVE,
    val disposalDate: LocalDate? = null,
    val disposalPrice: Money? = null,
    val notes: String? = null,
    /** SAL-03: the sale deposit in the books, when sold; its payee is the buyer. */
    val disposalTransactionId: String? = null,
) {
    val electric: Boolean get() = fuelType == FuelType.ELECTRIC
}

/** VEH-04: an odometer reading, from wherever it was recorded. [id] is set for direct readings only. */
data class OdometerReading(val id: String?, val date: LocalDate, val odometer: Int, val source: ReadingSource)

/** VEH-05: repeats every [intervalMonths], every [intervalKm], or whichever comes first. */
data class MaintenanceTask(
    val id: String,
    val vehicleId: String,
    val name: String,
    val templateKey: String? = null,
    val intervalMonths: Int? = null,
    val intervalKm: Int? = null,
    val startDate: LocalDate? = null,
    val startOdometer: Int? = null,
    val remindDays: Int = LeadTimes.maintenance(),
    val remindKm: Int = 500,
    val active: Boolean = true,
    val notes: String? = null,
)

/** When a task was last done and when it is next due, by date, by distance, or forecast from the distance. */
data class TaskStatus(
    val task: MaintenanceTask,
    val lastDate: LocalDate?,
    val lastOdometer: Int?,
    val dueDate: LocalDate?,
    val dueOdometer: Int?,
    /** When [dueOdometer] will be reached at the usual distance per day. */
    val forecastDate: LocalDate?,
    val state: TaskState,
) {
    /** The earlier of the date and the forecast. */
    val nextDate: LocalDate? get() = listOfNotNull(dueDate, forecastDate).minOrNull()
}

data class ServiceRecord(
    val id: String,
    val vehicleId: String,
    val date: LocalDate,
    val odometer: Int? = null,
    val provider: String? = null,
    val diy: Boolean = false,
    val cost: Money? = null,
    val transactionId: String? = null,
    val notes: String? = null,
    val taskIds: Set<String> = emptySet(),
)

/** VEH-07: litres, or kWh for an electric vehicle. */
data class FuelEntry(
    val id: String,
    val vehicleId: String,
    val date: LocalDate,
    val odometer: Int?,
    val quantity: BigDecimal,
    val cost: Money? = null,
    val fullTank: Boolean = true,
    val station: String? = null,
    val transactionId: String? = null,
    val notes: String? = null,
)

data class Warranty(
    val id: String,
    val vehicleId: String,
    val kind: WarrantyKind,
    val provider: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val endKm: Int? = null,
    val phone: String? = null,
    val notes: String? = null,
) {
    fun covers(date: LocalDate, odometer: Int?): Boolean =
        (endDate == null || date <= endDate) && (endKm == null || odometer == null || odometer <= endKm)
}

/** VEH-07: consumption over full-tank to full-tank intervals. */
data class FuelStats(
    val distanceKm: Int,
    val quantity: BigDecimal,
    /** L/100 km, or kWh/100 km. */
    val per100km: BigDecimal?,
    val costPerKm: Money?,
)

/** VEH-08: also enter the payment in an account, linked to the vehicle. */
data class PaymentDraft(val accountId: String, val categoryId: String?, val payeeName: String? = null)

/** VEH-11: a maintenance task due soon or overdue. */
data class MaintenanceDue(val vehicle: Vehicle, val status: TaskStatus) {
    fun toUpkeep() = UpkeepDue(
        vehicle.id, vehicle.name, true, status.task.id, status.task.name,
        DueStatus(status.dueDate, status.dueOdometer, status.forecastDate, DueState.valueOf(status.state.name)), MeterUnit.KM,
    )
}

/** VEH-10. */
data class OwnershipCost(val costs: CostSummary, val distanceKm: Int?, val costPerKm: Money?)

/**
 * Vehicles and everything about them (VEH-01 to VEH-11): papers, warranties, odometer,
 * maintenance schedule, service and fuel logs, and cost of ownership. Stored in the ledger of the
 * account group chosen for the vehicle.
 */
class VehicleService internal constructor(private val books: Books) {

    // --- Vehicles (VEH-01, VEH-02) --------------------------------------------------------------

    fun list(includeInactive: Boolean = false): List<Vehicle> = books.groups().flatMap { g ->
        books.ledger(g).vehiclesQueries.vehicles().executeAsList().map { it.toVehicle(g.id) }
    }.filter { includeInactive || it.status == VehicleStatus.ACTIVE }

    fun get(vehicleId: String): Vehicle = locate(vehicleId).second

    fun save(v: Vehicle): Vehicle {
        validate(v.name.isNotBlank(), "error.nameRequired")
        validate(v.modelYear == null || v.modelYear in 1900..2100, "error.invalidNumber")
        validate(listOfNotNull(v.purchasePrice, v.disposalPrice).all { it.currency == v.currency }, "error.currencyMismatch", v.currency.code)
        val group = if (v.id.isBlank()) books.group(v.groupId) else locate(v.id).first
        books.require(group, PermissionLevel.EDIT)
        val q = books.ledger(group).vehiclesQueries
        val id = v.id.ifBlank { Ids.newId() }
        val created = q.vehicleById(id).executeAsOneOrNull()?.created_at ?: books.now()
        with(v) {
            q.upsertVehicle(
                id, name.trim(), make.blankToNull(), model.blankToNull(), modelYear?.toLong(), trimLevel.blankToNull(), colour.blankToNull(),
                vin.blankToNull()?.uppercase(), plate.blankToNull()?.uppercase(), fuelType.name, driverMemberId, purchaseDate?.toString(),
                purchasePrice?.minorUnits, seller.blankToNull(), purchaseOdometer?.toLong(), currency.code, registrationRenewal?.toString(),
                insurer.blankToNull(), policyNumber.blankToNull(), insuranceRenewal?.toString(), status.name, disposalDate?.toString(),
                disposalPrice?.minorUnits, notes.blankToNull(), created, books.now(), disposalTransactionId.takeIf { status == VehicleStatus.SOLD },
            )
        }
        return get(id)
    }

    fun delete(vehicleId: String) {
        val (group, _) = locate(vehicleId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).vehiclesQueries.deleteVehicle(vehicleId)
    }

    // --- Odometer (VEH-04) -----------------------------------------------------------------------

    /** Every reading, oldest first: the purchase, direct readings, fuel and service entries. */
    fun readings(vehicleId: String): List<OdometerReading> {
        val (group, v) = locate(vehicleId)
        val q = books.ledger(group).vehiclesQueries
        val purchase = listOfNotNull(v.purchaseOdometer?.let { odo -> v.purchaseDate?.let { OdometerReading(null, it, odo, ReadingSource.PURCHASE) } })
        val direct = q.readings(vehicleId).executeAsList().map { OdometerReading(it.id, LocalDate.parse(it.date), it.odometer.toInt(), ReadingSource.READING) }
        val fuel = q.fuelEntries(vehicleId).executeAsList().mapNotNull { r -> r.odometer?.let { OdometerReading(null, LocalDate.parse(r.date), it.toInt(), ReadingSource.FUEL) } }
        val service = q.services(vehicleId).executeAsList().mapNotNull { r -> r.odometer?.let { OdometerReading(null, LocalDate.parse(r.date), it.toInt(), ReadingSource.SERVICE) } }
        return (purchase + direct + fuel + service).sortedWith(compareBy({ it.date }, { it.odometer }))
    }

    fun addReading(vehicleId: String, date: LocalDate, odometer: Int, notes: String? = null) {
        val (group, _) = locate(vehicleId)
        books.require(group, PermissionLevel.CAPTURE_ONLY)
        validate(odometer >= 0, "error.invalidNumber")
        books.ledger(group).vehiclesQueries.insertReading(Ids.newId(), vehicleId, date.toString(), odometer.toLong(), notes.blankToNull())
    }

    fun deleteReading(vehicleId: String, readingId: String) {
        val (group, _) = locate(vehicleId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).vehiclesQueries.deleteReading(readingId)
    }

    fun latestOdometer(vehicleId: String): OdometerReading? = readings(vehicleId).maxByOrNull { it.odometer }

    /** Average distance per day over the last year of readings (at least two weeks apart), for forecasts. */
    fun kmPerDay(vehicleId: String): Double? = kmPerDay(readings(vehicleId))

    private fun kmPerDay(readings: List<OdometerReading>): Double? = MaintenanceSchedule.usagePerDay(readings.map { it.date to it.odometer })

    // --- Maintenance (VEH-05, VEH-06, VEH-11) ----------------------------------------------------

    fun tasks(vehicleId: String): List<MaintenanceTask> {
        val (group, _) = locate(vehicleId)
        return books.ledger(group).vehiclesQueries.tasks(vehicleId).executeAsList().map { it.toTask() }
    }

    fun saveTask(t: MaintenanceTask): MaintenanceTask {
        validate(t.name.isNotBlank(), "error.nameRequired")
        validate(t.intervalMonths != null || t.intervalKm != null, "error.taskInterval")
        validate((t.intervalMonths ?: 1) in 1..240 && (t.intervalKm ?: 1) in 1..1_000_000, "error.invalidNumber")
        val (group, _) = locate(t.vehicleId)
        books.require(group, PermissionLevel.EDIT)
        val id = t.id.ifBlank { Ids.newId() }
        with(t) {
            books.ledger(group).vehiclesQueries.upsertTask(
                id, vehicleId, name.trim(), templateKey, intervalMonths?.toLong(), intervalKm?.toLong(), startDate?.toString(), startOdometer?.toLong(),
                remindDays.toLong(), remindKm.toLong(), if (active) 1 else 0, notes.blankToNull(),
            )
        }
        return t.copy(id = id)
    }

    fun deleteTask(vehicleId: String, taskId: String) {
        val (group, _) = locate(vehicleId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).vehiclesQueries.deleteTask(taskId)
    }

    /** VEH-05: adds the starter tasks the vehicle does not have yet; [names] gives each task's name in the user's language. */
    fun addStarterTasks(vehicleId: String, today: LocalDate, names: (String) -> String): List<MaintenanceTask> {
        val v = get(vehicleId)
        val existing = tasks(vehicleId).mapNotNull { it.templateKey }.toSet()
        val odometer = latestOdometer(vehicleId)?.odometer
        return TEMPLATES.filter { it.key !in existing && (it.combustion == null || it.combustion == !v.electric) }.map { tpl ->
            val province = books.provinceOf(v.driverMemberId)
            val start = tpl.seasonStart?.invoke(today, province)?.let { (month, day) ->
                Thresholds.dayIn(today.year, month, day).let { if (it < today) Thresholds.dayIn(today.year + 1, month, day) else it }
            }
            saveTask(
                MaintenanceTask(
                    "", vehicleId, names(tpl.key), tpl.key, tpl.months, tpl.km,
                    // Seasonal tasks fall due on their date; others count from today.
                    startDate = start?.minus(DatePeriod(months = tpl.months ?: 12)) ?: today, startOdometer = odometer.takeIf { tpl.km != null },
                ),
            )
        }
    }

    fun taskStatuses(vehicleId: String, today: LocalDate): List<TaskStatus> {
        val (group, v) = locate(vehicleId)
        val q = books.ledger(group).vehiclesQueries
        val readings = readings(vehicleId)
        val current = readings.maxByOrNull { it.odometer }?.odometer
        val rate = kmPerDay(readings)
        val services = q.services(vehicleId).executeAsList()
        val done = q.serviceTasks(vehicleId).executeAsList().groupBy({ it.task_id }, { it.service_id })
        val byId = services.associateBy { it.id }
        return q.tasks(vehicleId).executeAsList().map { it.toTask() }.filter { it.active }.map { task ->
            val last = done[task.id].orEmpty().mapNotNull(byId::get).maxByOrNull { it.date }
            val lastDate = last?.let { LocalDate.parse(it.date) } ?: task.startDate ?: v.purchaseDate
            // A reading only matters for tasks that repeat by distance.
            val lastOdometer = (last?.odometer?.toInt() ?: task.startOdometer ?: v.purchaseOdometer).takeIf { task.intervalKm != null }
            val s = MaintenanceSchedule.status(lastDate, lastOdometer, task.intervalMonths, task.intervalKm, current, rate, task.remindDays, task.remindKm, today)
            TaskStatus(task, lastDate, lastOdometer, s.dueDate, s.dueUsage, s.forecastDate, TaskState.valueOf(s.state.name))
        }.sortedWith(compareBy(nullsLast()) { it.nextDate })
    }

    /** VEH-11: tasks due soon or overdue, for every active vehicle. */
    fun due(today: LocalDate): List<MaintenanceDue> = list().flatMap { v ->
        taskStatuses(v.id, today).filter { it.state != TaskState.OK }.map { MaintenanceDue(v, it) }
    }

    /** Next due dates between [from] and [to], for the calendar. */
    fun dueBetween(from: LocalDate, to: LocalDate, today: LocalDate): List<MaintenanceDue> = list().flatMap { v ->
        taskStatuses(v.id, today).filter { s -> s.nextDate?.let { it in from..to } == true }.map { MaintenanceDue(v, it) }
    }

    // --- Service log (VEH-06, VEH-08) -----------------------------------------------------------

    fun services(vehicleId: String): List<ServiceRecord> {
        val (group, _) = locate(vehicleId)
        val q = books.ledger(group).vehiclesQueries
        val tasks = q.serviceTasks(vehicleId).executeAsList().groupBy({ it.service_id }, { it.task_id })
        val currency = get(vehicleId).currency
        return q.services(vehicleId).executeAsList().map { it.toService(currency, tasks[it.id].orEmpty().toSet()) }
    }

    /** Saves a service; with [payment], the matching transaction is entered too and linked (VEH-08). */
    fun saveService(record: ServiceRecord, payment: PaymentDraft? = null): ServiceRecord {
        val (group, v) = locate(record.vehicleId)
        books.require(group, PermissionLevel.CAPTURE_ONLY)
        validate(record.cost == null || record.cost.currency == v.currency, "error.currencyMismatch", v.currency.code)
        validate(record.odometer == null || record.odometer >= 0, "error.invalidNumber")
        val txnId = payment?.let { pay(v, record.date, record.cost, it, record.provider ?: v.name, record.notes) } ?: record.transactionId
        val q = books.ledger(group).vehiclesQueries
        val id = record.id.ifBlank { Ids.newId() }
        books.ledger(group).transaction {
            with(record) {
                if (record.id.isBlank()) {
                    q.insertService(id, vehicleId, date.toString(), odometer?.toLong(), provider.blankToNull(), if (diy) 1 else 0, cost?.minorUnits, txnId, notes.blankToNull(), books.now())
                } else {
                    q.updateService(date.toString(), odometer?.toLong(), provider.blankToNull(), if (diy) 1 else 0, cost?.minorUnits, txnId, notes.blankToNull(), id)
                }
                q.deleteServiceTasks(id)
                taskIds.forEach { q.insertServiceTask(id, it) }
            }
        }
        return services(record.vehicleId).first { it.id == id }
    }

    fun deleteService(vehicleId: String, serviceId: String) {
        val (group, _) = locate(vehicleId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).vehiclesQueries.deleteService(serviceId)
    }

    // --- Fuel log (VEH-07, VEH-08) ---------------------------------------------------------------

    fun fuel(vehicleId: String): List<FuelEntry> {
        val (group, v) = locate(vehicleId)
        return books.ledger(group).vehiclesQueries.fuelEntries(vehicleId).executeAsList().map { it.toFuel(v.currency) }
    }

    fun saveFuel(entry: FuelEntry, payment: PaymentDraft? = null): FuelEntry {
        val (group, v) = locate(entry.vehicleId)
        books.require(group, PermissionLevel.CAPTURE_ONLY)
        validate(entry.quantity.signum() > 0, "error.fuelQuantity")
        validate(entry.cost == null || entry.cost.currency == v.currency, "error.currencyMismatch", v.currency.code)
        val txnId = payment?.let { pay(v, entry.date, entry.cost, it, entry.station ?: v.name, entry.notes) } ?: entry.transactionId
        val q = books.ledger(group).vehiclesQueries
        val id = entry.id.ifBlank { Ids.newId() }
        with(entry) {
            if (entry.id.isBlank()) {
                q.insertFuel(id, vehicleId, date.toString(), odometer?.toLong(), quantity.toPlainString(), cost?.minorUnits, if (fullTank) 1 else 0, station.blankToNull(), txnId, notes.blankToNull(), books.now())
            } else {
                q.updateFuel(date.toString(), odometer?.toLong(), quantity.toPlainString(), cost?.minorUnits, if (fullTank) 1 else 0, station.blankToNull(), txnId, notes.blankToNull(), id)
            }
        }
        return fuel(entry.vehicleId).first { it.id == id }
    }

    fun deleteFuel(vehicleId: String, fuelId: String) {
        val (group, _) = locate(vehicleId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).vehiclesQueries.deleteFuel(fuelId)
    }

    /**
     * VEH-07: consumption from full tank to full tank. The fuel bought after one full tank, up to
     * and including the next, was used over the distance between them.
     */
    fun fuelStats(vehicleId: String, from: LocalDate, to: LocalDate): FuelStats {
        val v = get(vehicleId)
        val entries = fuel(vehicleId).filter { it.date in from..to && it.odometer != null }.sortedWith(compareBy({ it.date }, { it.odometer }))
        var distance = 0
        var used = BigDecimal.ZERO
        var cost = 0L
        var costed = true
        var lastFull: FuelEntry? = null
        var since = BigDecimal.ZERO
        var sinceCost = 0L
        var sinceCosted = true
        for (e in entries) {
            since += e.quantity
            if (e.cost != null) sinceCost += e.cost.minorUnits else sinceCosted = false
            if (!e.fullTank) continue
            val start = lastFull
            if (start != null && e.odometer!! > start.odometer!!) {
                distance += e.odometer - start.odometer
                used += since
                cost += sinceCost
                costed = costed && sinceCosted
            }
            lastFull = e
            since = BigDecimal.ZERO
            sinceCost = 0L
            sinceCosted = true
        }
        val per100 = if (distance > 0) used.multiply(BigDecimal(100)).divide(BigDecimal(distance), 1, RoundingMode.HALF_UP) else null
        val perKm = if (distance > 0 && costed) Money.ofMinor((cost + distance / 2) / distance, v.currency) else null
        return FuelStats(distance, entries.fold(BigDecimal.ZERO) { a, e -> a + e.quantity }, per100, perKm)
    }

    // --- Warranties (VEH-03) ----------------------------------------------------------------------

    fun warranties(vehicleId: String): List<Warranty> {
        val (group, _) = locate(vehicleId)
        return books.ledger(group).vehiclesQueries.warranties(vehicleId).executeAsList().map { it.toWarranty() }
    }

    fun saveWarranty(w: Warranty): Warranty {
        validate(w.endDate != null || w.endKm != null, "error.warrantyEnd")
        validate(w.startDate == null || w.endDate == null || w.endDate >= w.startDate, "error.endBeforeStart")
        val (group, _) = locate(w.vehicleId)
        books.require(group, PermissionLevel.EDIT)
        val id = w.id.ifBlank { Ids.newId() }
        with(w) {
            books.ledger(group).vehiclesQueries.upsertWarranty(
                id, vehicleId, kind.name, provider.blankToNull(), startDate?.toString(), endDate?.toString(), endKm?.toLong(), phone.blankToNull(), notes.blankToNull(),
            )
        }
        return w.copy(id = id)
    }

    /** WAR-03: the claims made under a vehicle warranty, newest first, in the vehicle's currency. */
    fun claims(vehicleId: String, warrantyId: String): List<WarrantyClaim> {
        val (group, v) = locate(vehicleId)
        val q = books.ledger(group).vehiclesQueries
        if (q.warranties(vehicleId).executeAsList().none { it.id == warrantyId }) return emptyList()
        return q.warrantyClaims(warrantyId).executeAsList().map {
            WarrantyClaim(it.id, it.warranty_id, LocalDate.parse(it.date), it.problem, it.outcome, it.covered_minor?.let { m -> Money.ofMinor(m, v.currency) }, it.paid_minor?.let { m -> Money.ofMinor(m, v.currency) }, it.notes)
        }
    }

    /** WAR-03: records a claim under one of [vehicleId]'s warranties: date, problem, outcome, cost covered and cost paid. */
    fun saveClaim(vehicleId: String, c: WarrantyClaim): WarrantyClaim {
        validate(c.problem.isNotBlank(), "error.descriptionRequired")
        val (group, v) = locate(vehicleId)
        books.require(group, PermissionLevel.EDIT)
        val q = books.ledger(group).vehiclesQueries
        validate(q.warranties(vehicleId).executeAsList().any { it.id == c.warrantyId }, "error.notFound")
        listOfNotNull(c.covered, c.paid).forEach {
            validate(it.currency == v.currency, "error.currencyMismatch", v.currency.code)
            validate(!it.isNegative, "error.amountPositive")
        }
        val id = c.id.ifBlank { Ids.newId() }
        q.upsertWarrantyClaim(id, c.warrantyId, c.date.toString(), c.problem.trim(), c.outcome.blankToNull(), c.covered?.minorUnits, c.paid?.minorUnits, c.notes.blankToNull())
        books.session.audit("UPDATE", "vehicle_warranty_claim", id)
        return c.copy(id = id)
    }

    fun deleteClaim(vehicleId: String, warrantyId: String, claimId: String) {
        val (group, _) = locate(vehicleId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).vehiclesQueries.deleteWarrantyClaim(claimId, warrantyId)
        books.session.audit("DELETE", "vehicle_warranty_claim", claimId)
    }

    fun deleteWarranty(vehicleId: String, warrantyId: String) {
        val (group, _) = locate(vehicleId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).vehiclesQueries.deleteWarranty(warrantyId)
    }

    /**
     * VEH-02, VEH-03: registration and insurance within [withinDays], warranties within the
     * warranty lead time of Rates and rules (WAR-02). A warranty limited by kilometres also ends on the day the odometer should reach the
     * limit, at the usual distance driven; once past the limit it is over and no longer reminds.
     */
    fun renewals(today: LocalDate, withinDays: Int = LeadTimes.renewals(today)): List<Renewal> = list().flatMap { v ->
        val readings = readings(v.id)
        val odometer = readings.maxOfOrNull { it.odometer }
        listOfNotNull(
            v.registrationRenewal?.let { Renewal(RenewalKind.REGISTRATION, v.id, v.name, it, today.daysUntil(it), v.plate) }?.takeIf { it.daysLeft <= withinDays },
            v.insuranceRenewal?.let { Renewal(RenewalKind.VEHICLE_INSURANCE, v.id, v.name, it, today.daysUntil(it), v.insurer) }?.takeIf { it.daysLeft <= withinDays },
        ) + warranties(v.id).filter { it.covers(today, odometer) }.mapNotNull { w ->
            val byKm = w.endKm?.let { MaintenanceSchedule.limitReachedOn(it, odometer, kmPerDay(readings), today) }
            val end = listOfNotNull(w.endDate, byKm).minOrNull() ?: return@mapNotNull null
            val detail = listOfNotNull(w.provider ?: w.kind.name, w.endKm?.takeIf { byKm == end }?.let { "$it km" }).joinToString(" · ")
            // An expired warranty is not a reminder; only the run-up to its end is.
            Renewal(RenewalKind.WARRANTY, v.id, v.name, end, today.daysUntil(end), detail).takeIf { it.daysLeft in 0..maxOf(withinDays, LeadTimes.warranty(today)) }
        }
    }

    // --- Cost of ownership (VEH-10) -----------------------------------------------------------------

    /**
     * Transactions linked to the vehicle, plus fuel and service entries that have no transaction
     * of their own, so nothing is missed or counted twice. The purchase price is not a running cost.
     */
    fun costs(vehicleId: String, from: LocalDate, to: LocalDate): OwnershipCost {
        val v = get(vehicleId)
        val keys = books.categories.list(includeArchived = true).associate { it.systemKey to it.id }
        val fuelCategory = keys[if (v.electric) "transport.ev_charging" else "transport.fuel"]
        val extra = fuel(vehicleId).filter { it.transactionId == null && it.cost != null }.map { it.date to (fuelCategory to it.cost!!) } +
            services(vehicleId).filter { it.transactionId == null && it.cost != null }.map { it.date to (keys["transport.maintenance"] to it.cost!!) }
        val summary = books.costs(from, to, extra) { q ->
            q.assetLines(vehicleId, from.toString(), to.toString()).executeAsList().map { CostLine(it.date, it.account_id, it.category_id, it.amount_minor) }
        }
        val inRange = readings(vehicleId).filter { it.date in from..to }
        val distance = if (inRange.size >= 2) inRange.maxOf { it.odometer } - inRange.minOf { it.odometer } else null
        val perKm = distance?.takeIf { it > 0 }?.let { Money.ofMinor((summary.total.minorUnits + it / 2) / it, summary.total.currency) }
        return OwnershipCost(summary, distance, perKm)
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private fun pay(v: Vehicle, date: LocalDate, cost: Money?, payment: PaymentDraft, payee: String, memo: String?): String {
        validate(cost != null && cost.isPositive, "error.amountPositive")
        val account = books.accounts.get(payment.accountId)
        validate(account.currency == cost!!.currency, "error.currencyMismatch", account.currency.code)
        val txn = books.transactions.create(
            TransactionDraft(
                account.id, date, -cost, payment.payeeName ?: payee,
                listOf(SplitDraft(payment.categoryId, -cost)), memo, assetId = v.id,
            ),
        )
        return txn.id
    }

    private fun locate(vehicleId: String): Pair<GroupInfo, Vehicle> {
        for (group in books.groups()) {
            val row = books.ledger(group).vehiclesQueries.vehicleById(vehicleId).executeAsOneOrNull() ?: continue
            return group to row.toVehicle(group.id)
        }
        throw AccessDeniedException("Vehicle not found or not accessible")
    }

    private fun VehicleRow.toVehicle(groupId: String): Vehicle {
        val c = Currency.of(currency)
        return Vehicle(
            id, groupId, name, make, model, model_year?.toInt(), trim_level, colour, vin, plate, FuelType.valueOf(fuel_type), driver_member_id,
            purchase_date?.let(LocalDate::parse), purchase_price_minor?.let { Money.ofMinor(it, c) }, seller, purchase_odometer?.toInt(), c,
            registration_renewal?.let(LocalDate::parse), insurer, policy_number, insurance_renewal?.let(LocalDate::parse), VehicleStatus.valueOf(status),
            disposal_date?.let(LocalDate::parse), disposal_price_minor?.let { Money.ofMinor(it, c) }, notes, disposal_txn_id,
        )
    }

    private fun TaskRow.toTask() = MaintenanceTask(
        id, vehicle_id, name, template_key, interval_months?.toInt(), interval_km?.toInt(), start_date?.let(LocalDate::parse), start_odometer?.toInt(),
        remind_days.toInt(), remind_km.toInt(), active == 1L, notes,
    )

    private fun ServiceRow.toService(currency: Currency, tasks: Set<String>) = ServiceRecord(
        id, vehicle_id, LocalDate.parse(date), odometer?.toInt(), provider, diy == 1L, cost_minor?.let { Money.ofMinor(it, currency) }, txn_id, notes, tasks,
    )

    private fun FuelRow.toFuel(currency: Currency) = FuelEntry(
        id, vehicle_id, LocalDate.parse(date), odometer?.toInt(), BigDecimal(quantity), cost_minor?.let { Money.ofMinor(it, currency) }, full_tank == 1L, station, txn_id, notes,
    )

    private fun WarrantyRow.toWarranty() = Warranty(
        id, vehicle_id, WarrantyKind.valueOf(kind), provider, start_date?.let(LocalDate::parse), end_date?.let(LocalDate::parse), end_km?.toInt(), phone, notes,
    )

    /**
     * VEH-05, MNT-02: starter tasks. [combustion] limits a task to engines (true) or electric vehicles
     * (false); a seasonal task falls due on the month and day [seasonStart] gives for the province.
     */
    private class Template(
        val key: String,
        val months: Int?,
        val km: Int?,
        val combustion: Boolean? = null,
        val seasonStart: ((LocalDate, Province) -> Pair<Int, Int>)? = null,
    )

    companion object {
        private val TEMPLATES = listOf(
            Template("oil", 6, 8_000, combustion = true),
            Template("tire_rotation", 12, 10_000),
            // Rates and rules: Quebec requires winter tires from December 1 to March 15; elsewhere the dates are suggestions.
            Template("winter_tires_on", 12, null, seasonStart = Thresholds::winterTiresOn),
            Template("winter_tires_off", 12, null, seasonStart = Thresholds::winterTiresOff),
            Template("brakes", 12, 20_000),
            Template("cabin_filter", 12, 20_000),
            Template("engine_filter", 24, 30_000, combustion = true),
            Template("inspection", 12, null),
        )

        val TEMPLATE_KEYS: List<String> = TEMPLATES.map { it.key }
    }
}
