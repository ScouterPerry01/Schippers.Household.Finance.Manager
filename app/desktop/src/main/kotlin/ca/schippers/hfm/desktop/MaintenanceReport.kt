package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.MeterUnit
import ca.schippers.hfm.calc.schedule.DueState
import ca.schippers.hfm.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Maintenance and cost of ownership (MNT-04 to MNT-06), for vehicles and other assets together:
 * the year's service log, what each one cost to own, and what falls due in the next 12 months.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MaintenanceReport(model: BooksModel, year: Int) {
    val books = model.books
    val today = today()
    val from = LocalDate(year, 1, 1)
    val to = minOf(LocalDate(year, 12, 31), today)
    val base = books.rates.baseCurrency
    val zero = Money.zero(base)
    fun inBase(m: Money?, date: LocalDate) = m?.let { if (it.currency == base) it else books.rates.convert(it, base, date) }

    class Owned(val name: String, val kind: String, val running: Money, val insurance: Money?, val usage: Int?, val unit: MeterUnit?, val perUnit: Money?)
    class Done(val date: LocalDate, val item: String, val tasks: String, val by: String, val parts: String?, val cost: Money?)

    val data = remember(model.revision, year) {
        val vehicles = books.vehicles.list(includeInactive = true).filter { it.disposalDate == null || it.disposalDate!! >= from }
        val assets = books.assets.list(includeDisposed = true).filter { it.disposalDate == null || it.disposalDate!! >= from }
        val owned = ArrayList<Owned>()
        val log = ArrayList<Done>()
        for (v in vehicles) {
            val c = books.vehicles.costs(v.id, from, to)
            val names = books.vehicles.tasks(v.id).associate { it.id to it.name }
            owned += Owned(v.name, model.t("assets.vehicle"), c.costs.total, null, c.distanceKm, MeterUnit.KM, c.costPerKm)
            for (s in books.vehicles.services(v.id).filter { it.date in from..to }) {
                log += Done(s.date, v.name, s.taskIds.mapNotNull(names::get).joinToString(", ").ifBlank { s.notes.orEmpty() }, if (s.diy) model.t("vehicles.diy") else s.provider.orEmpty(), null, inBase(s.cost, s.date))
            }
        }
        for (a in assets) {
            val tasks = books.assetMaintenance.tasks(a.id)
            val services = books.assetMaintenance.services(a.id)
            val c = books.assetMaintenance.costs(a.id, from, to)
            // Only what is looked after: tasks, services or costs of its own.
            if (tasks.isEmpty() && services.isEmpty() && !c.costs.total.isPositive && c.insurance == null) continue
            val names = tasks.associate { it.id to it.name }
            owned += Owned(a.name, model.t("assetKind.${a.kind}"), c.costs.total, c.insurance, c.usage, c.unit, c.costPerUnit)
            for (s in services.filter { it.date in from..to }) {
                log += Done(s.date, a.name, s.taskIds.mapNotNull(names::get).joinToString(", ").ifBlank { s.notes.orEmpty() }, if (s.diy) model.t("vehicles.diy") else s.provider.orEmpty(), s.parts, inBase(s.cost, s.date))
            }
        }
        Triple(owned.sortedByDescending { it.running.minorUnits }, log.sortedByDescending { it.date }, books.upkeepBetween(today, today.plus(DatePeriod(years = 1)), today) + books.upkeepDue(today))
    }
    val (owned, log, upcoming) = data
    val ahead = upcoming.distinctBy { it.taskId }.sortedWith(compareBy(nullsLast()) { it.status.nextDate })
    val spent = log.mapNotNull { it.cost }.fold(zero, Money::plus)

    Text(model.t("report.MAINTENANCE"), style = MaterialTheme.typography.titleLarge)
    Text(model.t("maintenanceReport.subtitle", year, base.code), style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(32.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Stat(model.t("maintenanceReport.spent"), model.money(spent))
        Stat(model.t("maintenanceReport.services"), log.size.toString())
        Stat(model.t("maintenanceReport.overdue"), ahead.count { it.status.state == DueState.DUE }.toString())
    }

    Heading(model.t("maintenanceReport.ownership"))
    TableView(
        model,
        ReportTable(
            model.t("maintenanceReport.ownership"), year.toString(),
            listOf(
                model.t("maintenanceReport.item"), model.t("assets.kind"), model.t("maintenanceReport.running"), model.t("maintenanceReport.insurance"),
                model.t("maintenanceReport.used"), model.t("maintenanceReport.perUnit"),
            ),
            owned.map { o ->
                listOf(
                    o.name, o.kind, o.running, o.insurance, o.usage?.let { model.usage(it, o.unit) }.orEmpty(),
                    o.perUnit?.let { model.t("upkeep.perUnit.${o.unit ?: MeterUnit.KM}", model.money(it)) }.orEmpty(),
                )
            },
        ),
        startOpen = true,
    )
    Text(model.t("upkeep.costsHow"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

    Heading(model.t("maintenanceReport.log"))
    TableView(
        model,
        ReportTable(
            model.t("maintenanceReport.log"), year.toString(),
            listOf(model.t("report.date"), model.t("maintenanceReport.item"), model.t("maintenanceReport.tasks"), model.t("maintenanceReport.by"), model.t("upkeep.parts"), model.t("vehicles.cost")),
            log.map { listOf(it.date, it.item, it.tasks, it.by, it.parts.orEmpty(), it.cost) },
        ),
        startOpen = true,
    )

    Heading(model.t("maintenanceReport.ahead"))
    TableView(
        model,
        ReportTable(
            model.t("maintenanceReport.ahead"), model.date(today),
            listOf(model.t("maintenanceReport.item"), model.t("maintenanceReport.tasks"), model.t("maintenanceReport.due"), model.t("assets.status")),
            ahead.map { u ->
                val due = listOfNotNull(u.status.dueDate?.let(model::date), u.status.dueUsage?.let { model.usage(it, u.unit) }).joinToString(" ${model.t("vehicles.or")} ") +
                    u.status.forecastDate?.takeIf { u.status.dueUsage != null }?.let { " (" + model.t("upkeep.around", model.date(it)) + ")" }.orEmpty()
                listOf(u.subjectName, u.taskName, due, model.t("taskState.${u.status.state}"))
            },
        ),
        startOpen = true,
    )
}

@Composable
private fun Heading(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
