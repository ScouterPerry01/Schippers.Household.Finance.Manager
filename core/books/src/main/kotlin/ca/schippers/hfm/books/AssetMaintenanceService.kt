package ca.schippers.hfm.books

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
import ca.schippers.hfm.data.ledger.Asset_service as ServiceRow
import ca.schippers.hfm.data.ledger.Asset_task as TaskRow

/** MNT-03: what an asset's meter counts. */
enum class MeterUnit { HOURS, KM }

/** MNT-01: repeats every [intervalMonths], every [intervalUsage] on the asset's meter, or whichever comes first. */
data class AssetTask(
    val id: String,
    val assetId: String,
    val name: String,
    val templateKey: String? = null,
    val intervalMonths: Int? = null,
    val intervalUsage: Int? = null,
    val startDate: LocalDate? = null,
    val startUsage: Int? = null,
    val remindDays: Int = 14,
    val remindUsage: Int = 10,
    val active: Boolean = true,
    val notes: String? = null,
)

/** MNT-03: a meter reading; [id] is set for readings entered directly. */
data class MeterReading(val id: String?, val date: LocalDate, val usage: Int)

/** When a task was last done and when it is next due. */
data class AssetTaskStatus(val task: AssetTask, val lastDate: LocalDate?, val lastUsage: Int?, val due: DueStatus)

/** MNT-04: one service, for one or more tasks. */
data class AssetServiceRecord(
    val id: String,
    val assetId: String,
    val date: LocalDate,
    val usage: Int? = null,
    val provider: String? = null,
    val diy: Boolean = false,
    val parts: String? = null,
    val cost: Money? = null,
    val transactionId: String? = null,
    val notes: String? = null,
    val taskIds: Set<String> = emptySet(),
)

/**
 * MNT-06: running costs (linked transactions, and services with no transaction of their own), the
 * purchase, and an estimate of the premiums of the policies that name the asset, split evenly
 * between the things each one covers.
 */
data class AssetOwnershipCost(
    val costs: CostSummary,
    val purchase: Money?,
    val insurance: Money?,
    val usage: Int?,
    val unit: MeterUnit?,
    val costPerUnit: Money?,
)

/** MNT-05: a task due soon or overdue, on a vehicle or another asset. */
data class UpkeepDue(
    val subjectId: String,
    val subjectName: String,
    val vehicle: Boolean,
    val taskId: String,
    val taskName: String,
    val status: DueStatus,
    val unit: MeterUnit?,
)

/**
 * Maintenance for the home and other assets (MNT-01 to MNT-06): tasks by time, use or season,
 * meter readings, the service log, and cost of ownership. Vehicles keep their own
 * ([VehicleService]); [Books.upkeepDue] lists both together.
 */
class AssetMaintenanceService internal constructor(private val books: Books) {

    // --- Tasks (MNT-01, MNT-02) -------------------------------------------------------------------

    fun tasks(assetId: String): List<AssetTask> {
        val (group, _) = locate(assetId)
        return books.ledger(group).assetMaintenanceQueries.tasks(assetId).executeAsList().map { it.toTask() }
    }

    fun saveTask(t: AssetTask): AssetTask {
        validate(t.name.isNotBlank(), "error.nameRequired")
        validate(t.intervalMonths != null || t.intervalUsage != null, "error.taskInterval")
        validate((t.intervalMonths ?: 1) in 1..240 && (t.intervalUsage ?: 1) in 1..1_000_000, "error.invalidNumber")
        val (group, a) = locate(t.assetId)
        validate(t.intervalUsage == null || a.meter != null, "error.noMeter")
        books.require(group, PermissionLevel.EDIT)
        val id = t.id.ifBlank { Ids.newId() }
        with(t) {
            books.ledger(group).assetMaintenanceQueries.upsertTask(
                id, assetId, name.trim(), templateKey, intervalMonths?.toLong(), intervalUsage?.toLong(), startDate?.toString(), startUsage?.toLong(),
                remindDays.toLong(), remindUsage.toLong(), if (active) 1 else 0, notes.blankToNull(),
            )
        }
        return t.copy(id = id)
    }

    fun deleteTask(assetId: String, taskId: String) {
        val (group, _) = locate(assetId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).assetMaintenanceQueries.deleteTask(taskId)
    }

    /** MNT-02: the keys of the starter tasks for this kind of asset. */
    fun templates(kind: AssetKind): List<String> = TEMPLATES.filter { kind in it.kinds }.map { it.key }

    /** MNT-02: adds the starter tasks the asset does not have yet; [names] gives each task's name in the user's language. */
    fun addStarterTasks(assetId: String, today: LocalDate, names: (String) -> String): List<AssetTask> {
        val (_, a) = locate(assetId)
        val existing = tasks(assetId).mapNotNull { it.templateKey }.toSet()
        val usage = latestUsage(assetId)
        return TEMPLATES.filter { a.kind in it.kinds && it.key !in existing }.map { tpl ->
            // A use-based interval only applies when the asset's meter counts the same thing.
            val byUsage = tpl.usage?.takeIf { a.meter == tpl.unit }
            val months = tpl.months ?: if (byUsage == null) 12 else null
            val start = tpl.season?.let { (m, d) -> MaintenanceSchedule.nextSeason(m, d, today) }
            saveTask(
                AssetTask(
                    "", assetId, names(tpl.key), tpl.key, months, byUsage,
                    // Seasonal tasks fall due on their date; others count from today.
                    startDate = start?.minus(DatePeriod(months = months ?: 12)) ?: today,
                    startUsage = usage.takeIf { byUsage != null },
                    remindUsage = if (a.meter == MeterUnit.KM) 500 else 10,
                ),
            )
        }
    }

    // --- Meter (MNT-03) ---------------------------------------------------------------------------

    /** Every reading, oldest first: those entered and those of services. */
    fun readings(assetId: String): List<MeterReading> {
        val (group, _) = locate(assetId)
        val q = books.ledger(group).assetMaintenanceQueries
        val direct = q.readings(assetId).executeAsList().map { MeterReading(it.id, LocalDate.parse(it.date), it.usage.toInt()) }
        val service = q.services(assetId).executeAsList().mapNotNull { r -> r.usage?.let { MeterReading(null, LocalDate.parse(r.date), it.toInt()) } }
        return (direct + service).sortedWith(compareBy({ it.date }, { it.usage }))
    }

    fun addReading(assetId: String, date: LocalDate, usage: Int, notes: String? = null) {
        val (group, a) = locate(assetId)
        books.require(group, PermissionLevel.CAPTURE_ONLY)
        validate(a.meter != null, "error.noMeter")
        validate(usage >= 0, "error.invalidNumber")
        books.ledger(group).assetMaintenanceQueries.insertReading(Ids.newId(), assetId, date.toString(), usage.toLong(), notes.blankToNull())
    }

    fun deleteReading(assetId: String, readingId: String) {
        val (group, _) = locate(assetId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).assetMaintenanceQueries.deleteReading(readingId)
    }

    fun latestUsage(assetId: String): Int? = readings(assetId).maxOfOrNull { it.usage }

    // --- Due (MNT-01, MNT-03, MNT-05) -------------------------------------------------------------

    fun taskStatuses(assetId: String, today: LocalDate): List<AssetTaskStatus> {
        val (group, a) = locate(assetId)
        val q = books.ledger(group).assetMaintenanceQueries
        val readings = readings(assetId)
        val current = readings.maxOfOrNull { it.usage }
        val rate = MaintenanceSchedule.usagePerDay(readings.map { it.date to it.usage })
        val services = q.services(assetId).executeAsList().associateBy { it.id }
        val done = q.serviceTasks(assetId).executeAsList().groupBy({ it.task_id }, { it.service_id })
        return q.tasks(assetId).executeAsList().map { it.toTask() }.filter { it.active }.map { task ->
            val last = done[task.id].orEmpty().mapNotNull(services::get).maxByOrNull { it.date }
            val lastDate = last?.let { LocalDate.parse(it.date) } ?: task.startDate ?: a.purchaseDate
            // A reading only matters for tasks that repeat by use.
            val lastUsage = (last?.usage?.toInt() ?: task.startUsage).takeIf { task.intervalUsage != null }
            AssetTaskStatus(
                task, lastDate, lastUsage,
                MaintenanceSchedule.status(lastDate, lastUsage, task.intervalMonths, task.intervalUsage, current, rate, task.remindDays, task.remindUsage, today),
            )
        }.sortedWith(compareBy(nullsLast()) { it.due.nextDate })
    }

    /** MNT-05: tasks due soon or overdue on every asset still owned. */
    fun due(today: LocalDate): List<UpkeepDue> = upkeep(today).filter { it.status.state != DueState.OK }

    /** Every active task on every asset still owned, with its status. */
    fun upkeep(today: LocalDate): List<UpkeepDue> = books.assets.list().flatMap { a ->
        taskStatuses(a.id, today).map { UpkeepDue(a.id, a.name, false, it.task.id, it.task.name, it.due, a.meter) }
    }

    // --- Service log (MNT-04) ---------------------------------------------------------------------

    fun services(assetId: String): List<AssetServiceRecord> {
        val (group, a) = locate(assetId)
        val q = books.ledger(group).assetMaintenanceQueries
        val tasks = q.serviceTasks(assetId).executeAsList().groupBy({ it.service_id }, { it.task_id })
        return q.services(assetId).executeAsList().map { it.toService(a.currency, tasks[it.id].orEmpty().toSet()) }
    }

    /** Saves a service; with [payment], the matching transaction is entered too and linked to the asset. */
    fun saveService(record: AssetServiceRecord, payment: PaymentDraft? = null): AssetServiceRecord {
        val (group, a) = locate(record.assetId)
        books.require(group, PermissionLevel.CAPTURE_ONLY)
        validate(record.cost == null || record.cost.currency == a.currency, "error.currencyMismatch", a.currency.code)
        validate(record.usage == null || record.usage >= 0, "error.invalidNumber")
        val txnId = payment?.let { pay(a, record, it) } ?: record.transactionId
        val q = books.ledger(group).assetMaintenanceQueries
        val id = record.id.ifBlank { Ids.newId() }
        books.ledger(group).transaction {
            with(record) {
                if (record.id.isBlank()) {
                    q.insertService(id, assetId, date.toString(), usage?.toLong(), provider.blankToNull(), if (diy) 1 else 0, parts.blankToNull(), cost?.minorUnits, txnId, notes.blankToNull(), books.now())
                } else {
                    q.updateService(date.toString(), usage?.toLong(), provider.blankToNull(), if (diy) 1 else 0, parts.blankToNull(), cost?.minorUnits, txnId, notes.blankToNull(), id)
                }
                q.deleteServiceTasks(id)
                taskIds.forEach { q.insertServiceTask(id, it) }
            }
        }
        return services(record.assetId).first { it.id == id }
    }

    fun deleteService(assetId: String, serviceId: String) {
        val (group, _) = locate(assetId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).assetMaintenanceQueries.deleteService(serviceId)
    }

    // --- Cost of ownership (MNT-06) ---------------------------------------------------------------

    fun costs(assetId: String, from: LocalDate, to: LocalDate): AssetOwnershipCost {
        val (_, a) = locate(assetId)
        val keys = books.categories.list(includeArchived = true).associate { it.systemKey to it.id }
        val category = keys[if (a.kind in LEISURE) "leisure.cottage_rv" else "housing.maintenance"]
        val extra = services(assetId).filter { it.transactionId == null && it.cost != null }.map { it.date to (category to it.cost!!) }
        val summary = books.costs(from, to, extra) { q ->
            q.assetLines(assetId, from.toString(), to.toString()).executeAsList().map { CostLine(it.date, it.account_id, it.category_id, it.amount_minor) }
        }
        val base = summary.total.currency
        val days = from.daysUntil(to) + 1
        val insurance = books.insurance.policies(includeInactive = false).filter { assetId in it.assetIds }.mapNotNull { p ->
            val annual = p.annualPremium ?: return@mapNotNull null
            val converted = (if (annual.currency == base) annual else books.rates.convert(annual, base, to)) ?: return@mapNotNull null
            converted.times(BigDecimal(days).divide(BigDecimal(365 * p.assetIds.size), 10, RoundingMode.HALF_UP))
        }.takeIf { it.isNotEmpty() }?.fold(Money.zero(base), Money::plus)
        val inRange = readings(assetId).filter { it.date in from..to }
        val usage = if (a.meter != null && inRange.size >= 2) inRange.maxOf { it.usage } - inRange.minOf { it.usage } else null
        val perUnit = usage?.takeIf { it > 0 }?.let { Money.ofMinor((summary.total.minorUnits + it / 2) / it, base) }
        val purchase = a.purchasePrice?.takeIf { a.purchaseDate == null || a.purchaseDate in from..to }
        return AssetOwnershipCost(summary, purchase, insurance, usage, a.meter, perUnit)
    }

    // --- Helpers ----------------------------------------------------------------------------------

    private fun pay(a: Asset, record: AssetServiceRecord, payment: PaymentDraft): String {
        val cost = record.cost
        validate(cost != null && cost.isPositive, "error.amountPositive")
        val account = books.accounts.get(payment.accountId)
        validate(account.currency == cost!!.currency, "error.currencyMismatch", account.currency.code)
        return books.transactions.create(
            TransactionDraft(
                account.id, record.date, -cost, payment.payeeName ?: record.provider ?: a.name,
                listOf(SplitDraft(payment.categoryId, -cost)), record.notes, assetId = a.id,
            ),
        ).id
    }

    private fun locate(assetId: String): Pair<GroupInfo, Asset> {
        val a = books.assets.get(assetId)
        return books.group(a.groupId) to a
    }

    private fun TaskRow.toTask() = AssetTask(
        id, asset_id, name, template_key, interval_months?.toInt(), interval_usage?.toInt(), start_date?.let(LocalDate::parse), start_usage?.toInt(),
        remind_days.toInt(), remind_usage.toInt(), active == 1L, notes,
    )

    private fun ServiceRow.toService(currency: Currency, tasks: Set<String>) = AssetServiceRecord(
        id, asset_id, LocalDate.parse(date), usage?.toInt(), provider, diy == 1L, parts, cost_minor?.let { Money.ofMinor(it, currency) }, txn_id, notes, tasks,
    )

    /** MNT-02: a starter task for some kinds of asset. [usage] counts in [unit] and only applies to an asset whose meter counts the same. */
    private class Template(
        val key: String,
        val kinds: Set<AssetKind>,
        val months: Int?,
        val usage: Int? = null,
        val unit: MeterUnit? = null,
        val season: Pair<Int, Int>? = null,
    )

    companion object {
        private val HOME = setOf(AssetKind.HOME, AssetKind.COTTAGE)
        private val HVAC = setOf(AssetKind.HOME, AssetKind.COTTAGE, AssetKind.HEATING_COOLING)
        private val LEISURE = setOf(AssetKind.COTTAGE, AssetKind.RV, AssetKind.BOAT, AssetKind.TRAILER)

        private val TEMPLATES = listOf(
            // Home and cottage
            Template("furnace_filter", HVAC, 3),
            Template("hvac_service", HVAC, 12, season = 9 to 15),
            Template("gutters", HOME, 12, season = 10 to 30),
            Template("smoke_co_detectors", HOME, 6),
            Template("water_heater_flush", HOME, 12),
            Template("chimney", HOME, 12, season = 9 to 1),
            Template("sump_pump", HOME, 12, season = 3 to 15),
            Template("septic", setOf(AssetKind.COTTAGE), 36),
            Template("cottage_open", setOf(AssetKind.COTTAGE), 12, season = 5 to 1),
            Template("cottage_close", setOf(AssetKind.COTTAGE), 12, season = 10 to 15),
            // RV
            Template("rv_dewinterize", setOf(AssetKind.RV), 12, season = 4 to 15),
            Template("rv_winterize", setOf(AssetKind.RV), 12, season = 10 to 15),
            Template("roof_sealant", setOf(AssetKind.RV), 12, season = 5 to 1),
            Template("wheel_bearings", setOf(AssetKind.RV, AssetKind.TRAILER), 12, 20_000, MeterUnit.KM),
            Template("propane_inspection", setOf(AssetKind.RV), 12),
            Template("rv_battery", setOf(AssetKind.RV, AssetKind.BOAT), 6),
            Template("generator", setOf(AssetKind.RV), 12, 150, MeterUnit.HOURS),
            // Boat
            Template("boat_launch", setOf(AssetKind.BOAT), 12, season = 5 to 1),
            Template("boat_winterize", setOf(AssetKind.BOAT), 12, season = 10 to 1),
            Template("engine_oil", setOf(AssetKind.BOAT), 12, 100, MeterUnit.HOURS),
            Template("impeller", setOf(AssetKind.BOAT), 24, 300, MeterUnit.HOURS),
            // Trailer
            Template("trailer_lights", setOf(AssetKind.TRAILER), 12, season = 4 to 15),
        )

        val TEMPLATE_KEYS: List<String> = TEMPLATES.map { it.key }
    }
}
