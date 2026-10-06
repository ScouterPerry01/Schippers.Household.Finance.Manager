package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Charging
import ca.schippers.hfm.books.DriveKind
import ca.schippers.hfm.books.Energy
import ca.schippers.hfm.books.FuelEntry
import ca.schippers.hfm.books.FuelType
import ca.schippers.hfm.books.Transmission
import ca.schippers.hfm.books.TripLoad
import ca.schippers.hfm.books.VehicleDetails
import ca.schippers.hfm.books.VehicleUsage
import ca.schippers.hfm.books.LinkRole
import ca.schippers.hfm.books.LinkTarget
import ca.schippers.hfm.books.MaintenanceTask
import ca.schippers.hfm.books.OdometerReading
import ca.schippers.hfm.books.PaymentDraft
import ca.schippers.hfm.books.ServiceRecord
import ca.schippers.hfm.books.TaskState
import ca.schippers.hfm.books.TaskStatus
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.books.Vehicle
import ca.schippers.hfm.books.VehicleStatus
import ca.schippers.hfm.books.Transaction
import ca.schippers.hfm.books.Warranty
import ca.schippers.hfm.books.WarrantyClaim
import ca.schippers.hfm.books.WarrantyKind
import ca.schippers.hfm.calc.rules.LeadTimes
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

private enum class VehicleTab { OVERVIEW, MAINTENANCE, SERVICE, FUEL, FORECAST, WARRANTIES, COSTS }

/** What the Vehicles screen is editing; an empty id means a new record. */
private sealed interface VehicleEdit {
    data class Details(val value: Vehicle) : VehicleEdit
    data class Reading(val vehicle: Vehicle) : VehicleEdit
    data class Task(val value: MaintenanceTask) : VehicleEdit
    data class Service(val vehicle: Vehicle, val value: ServiceRecord) : VehicleEdit
    data class Fuel(val vehicle: Vehicle, val value: FuelEntry) : VehicleEdit
    data class WarrantyEdit(val value: Warranty) : VehicleEdit
}

/** VEH-01 to VEH-11: each vehicle's papers, maintenance, service and fuel logs, warranties and costs. */
@Composable
fun VehiclesScreen(model: BooksModel) {
    val books = model.books
    var showInactive by remember { mutableStateOf(false) }
    val vehicles = remember(model.revision, showInactive) { books.vehicles.list(showInactive) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(VehicleTab.OVERVIEW) }
    var edit by remember { mutableStateOf<VehicleEdit?>(null) }
    val vehicle = vehicles.firstOrNull { it.id == selectedId } ?: vehicles.firstOrNull()

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(model.t("nav.vehicles"), style = MaterialTheme.typography.titleLarge)
            if (vehicle != null) Picker(model.t("vehicles.vehicle"), vehicles, vehicle, { vehicleLabel(model, it) }, Modifier.width(320.dp)) { selectedId = it.id }
            Box(Modifier.weight(1f))
            LabeledCheckbox(model.t("vehicles.showInactive"), showInactive) { showInactive = it }
            Button(onClick = {
                val group = model.editableGroups().let { g -> g.firstOrNull { !it.isPrivate } ?: g.firstOrNull() }
                if (group == null) model.error = model.t("error.noEditableGroup") else edit = VehicleEdit.Details(Vehicle("", group.id, "", currency = books.rates.baseCurrency))
            }) { Text(model.t("vehicles.add")) }
        }
        if (vehicle == null) {
            Text(model.t("vehicles.none"), Modifier.padding(8.dp))
        } else {
            PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 0.dp, modifier = Modifier.padding(vertical = 8.dp)) {
                for (t in VehicleTab.entries) Tab(selected = tab == t, onClick = { tab = t }, text = { Text(model.t("vehicles.tab.${t.name}")) })
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    VehicleTab.OVERVIEW -> OverviewTab(model, vehicle) { edit = it }
                    VehicleTab.MAINTENANCE -> MaintenanceTab(model, vehicle) { edit = it }
                    VehicleTab.SERVICE -> ServiceTab(model, vehicle) { edit = it }
                    VehicleTab.FUEL -> FuelTab(model, vehicle) { edit = it }
                    VehicleTab.FORECAST -> ForecastTab(model, vehicle)
                    VehicleTab.WARRANTIES -> WarrantiesTab(model, vehicle) { edit = it }
                    VehicleTab.COSTS -> CostsTab(model, vehicle)
                }
            }
        }
    }

    when (val e = edit) {
        is VehicleEdit.Details -> VehicleDialog(model, e.value) { saved -> saved?.let { selectedId = it }; edit = null }
        is VehicleEdit.Reading -> ReadingDialog(model, e.vehicle) { edit = null }
        is VehicleEdit.Task -> TaskDialog(model, e.value) { edit = null }
        is VehicleEdit.Service -> ServiceDialog(model, e.vehicle, e.value) { edit = null }
        is VehicleEdit.Fuel -> FuelDialog(model, e.vehicle, e.value) { edit = null }
        is VehicleEdit.WarrantyEdit -> WarrantyDialog(model, e.value) { edit = null }
        null -> Unit
    }
}

private fun vehicleLabel(model: BooksModel, v: Vehicle): String =
    v.name + (if (v.status != VehicleStatus.ACTIVE) " (${model.t("vehicleStatus.${v.status}")})" else "")

private fun km(model: BooksModel, value: Int): String = model.t("vehicles.km", String.format(model.language.locale, "%,d", value))

/** TRP-07: "3.5 L V6 · Automatic · Rear-wheel drive · Tank 95 L · ..." from the details entered, or null. */
private fun detailsLine(model: BooksModel, v: Vehicle): String? {
    val d = v.details
    val locale = model.language.locale
    fun dec(x: BigDecimal) = MoneyFormat.formatDecimal(x, locale)
    fun kg(x: Int) = String.format(locale, "%,d", x)
    return listOfNotNull(
        d.engine, d.transmission?.let { model.t("transmission.$it") }, d.drive?.let { model.t("drive.$it") },
        d.tankLitres?.let { model.t("vehicles.tankIs", dec(it)) }, d.batteryKwh?.let { model.t("vehicles.batteryIs", dec(it)) },
        d.oilType?.let { t -> model.t("vehicles.oilIs", t + (d.oilLitres?.let { " · ${dec(it)} L" }.orEmpty())) } ?: d.oilLitres?.let { model.t("vehicles.oilIs", "${dec(it)} L") },
        d.tiresSummer?.let { model.t("vehicles.tiresSummerIs", it) }, d.tiresWinter?.let { model.t("vehicles.tiresWinterIs", it) },
        d.towingKg?.let { model.t("vehicles.towsIs", kg(it)) }, d.gvwrKg?.let { model.t("vehicles.gvwrIs", kg(it)) },
        v.usage.takeIf { it != VehicleUsage.PERSONAL }?.let { model.t("vehicleUsage.$it") },
    ).joinToString(" · ").ifEmpty { null }
}

// --- Overview (VEH-01, VEH-02, VEH-04) -------------------------------------------------------------

@Composable
private fun OverviewTab(model: BooksModel, v: Vehicle, onEdit: (VehicleEdit) -> Unit) {
    val books = model.books
    val readings = remember(model.revision, v.id) { books.vehicles.readings(v.id) }
    val rate = remember(model.revision, v.id) { books.vehicles.kmPerDay(v.id) }
    val driver = remember(model.revision, v.driverMemberId) { v.driverMemberId?.let { id -> books.members.list(true).firstOrNull { it.id == id }?.displayName } }
    var deleting by remember { mutableStateOf<OdometerReading?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(listOfNotNull(v.modelYear?.toString(), v.make, v.model, v.trimLevel).joinToString(" ").ifBlank { v.name }, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(model.t("fuelType.${v.fuelType}"), v.colour, v.plate?.let { model.t("vehicles.plateIs", it) }, driver?.let { model.t("vehicles.driverIs", it) })
                        .joinToString(" · "),
                )
                v.vin?.let { Text(model.t("vehicles.vinIs", it), style = MaterialTheme.typography.bodySmall) }
                detailsLine(model, v)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            OutlinedButton(onClick = { onEdit(VehicleEdit.Reading(v)) }) { Text(model.t("vehicles.addReading")) }
            TextButton(onClick = { onEdit(VehicleEdit.Details(v)) }) { Text(model.t("common.edit")) }
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        val latest = readings.maxByOrNull { it.odometer }
        Text(
            latest?.let { model.t("vehicles.odometerIs", km(model, it.odometer), model.date(it.date)) } ?: model.t("vehicles.noOdometer"),
            fontWeight = FontWeight.Medium,
        )
        rate?.let { Text(model.t("vehicles.perYear", km(model, (it * 365).toInt())), style = MaterialTheme.typography.bodySmall) }
        RenewalLine(model, v.registrationRenewal, model.t("vehicles.registrationLine", v.plate ?: "—"))
        RenewalLine(model, v.insuranceRenewal, model.t("vehicles.insuranceLine", v.insurer ?: "—", v.policyNumber ?: "—"))
        // TRP-09: a commercial vehicle's safety inspection and operator registration.
        v.inspectionDue?.let { RenewalLine(model, it, model.t("renewalKind.SAFETY_INSPECTION")) }
        v.operatorRenewal?.let { RenewalLine(model, it, model.t("renewalKind.OPERATOR_RENEWAL")) }
        if (v.purchaseDate != null || v.purchasePrice != null) {
            Text(
                model.t("vehicles.purchaseLine", v.purchaseDate?.let(model::date) ?: "—", v.purchasePrice?.let(model::money) ?: "—", v.seller ?: "—",
                    v.purchaseOdometer?.let { km(model, it) } ?: "—"),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (v.status != VehicleStatus.ACTIVE) {
            Text(model.t("vehicles.disposalLine", model.t("vehicleStatus.${v.status}"), v.disposalDate?.let(model::date) ?: "—", v.disposalPrice?.let(model::money) ?: "—"))
            // SAL-03: the sale deposit in the books; its payee is the buyer.
            val sale = v.disposalTransactionId?.takeIf { v.status == VehicleStatus.SOLD }?.let { id -> remember(id, model.revision) { runCatching { model.books.transactions.get(id) }.getOrNull() } }
            sale?.let { t -> Text(model.t("assets.sale", model.date(t.date), t.payeeText ?: "", model.money(t.amount)), style = MaterialTheme.typography.bodySmall) }
            // SAL-03: the gain or loss against the purchase price.
            if (v.status == VehicleStatus.SOLD) saleResult(model, v.purchasePrice, v.disposalPrice)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        v.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text(model.t("vehicles.readings"), style = MaterialTheme.typography.titleSmall)
        for (r in readings.reversed().take(12)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.date(r.date), Modifier.width(110.dp))
                Text(km(model, r.odometer), Modifier.width(140.dp))
                Text(model.t("readingSource.${r.source}"), Modifier.width(160.dp), style = MaterialTheme.typography.bodySmall)
                if (r.id != null) TextButton(onClick = { deleting = r }) { Text(model.t("common.delete")) }
            }
        }
    }
    deleting?.let { r ->
        AskBeforeDeleting(model, model.t("vehicles.delete.reading", km(model, r.odometer), model.date(r.date)), onDismiss = { deleting = null }) {
            model.act { books.vehicles.deleteReading(v.id, r.id!!) } != null
        }
    }
}

// --- Maintenance (VEH-05, VEH-11) ---------------------------------------------------------------------

@Composable
private fun MaintenanceTab(model: BooksModel, v: Vehicle, onEdit: (VehicleEdit) -> Unit) {
    val books = model.books
    val statuses = remember(model.revision, v.id) { books.vehicles.taskStatuses(v.id, today()) }
    val inactive = remember(model.revision, v.id) { books.vehicles.tasks(v.id).filter { !it.active } }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(model.t("vehicles.maintenanceExplain"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { model.act { books.vehicles.addStarterTasks(v.id, today()) { model.t("task.$it") } } }) { Text(model.t("vehicles.starterTasks")) }
            Button(onClick = { onEdit(VehicleEdit.Task(MaintenanceTask("", v.id, "", intervalMonths = 12, startDate = today(), startOdometer = books.vehicles.latestOdometer(v.id)?.odometer))) }) {
                Text(model.t("vehicles.addTask"))
            }
        }
        LazyColumn(Modifier.padding(top = 8.dp)) {
            if (statuses.isEmpty() && inactive.isEmpty()) item { Text(model.t("vehicles.noTasks"), Modifier.padding(8.dp)) }
            items(statuses, key = { it.task.id }) { s -> TaskRow(model, v, s, onEdit) }
            items(inactive, key = { "x" + it.id }) { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.name + " (${model.t("vehicles.taskOff")})", Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
                    TextButton(onClick = { onEdit(VehicleEdit.Task(t)) }) { Text(model.t("common.edit")) }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(model: BooksModel, v: Vehicle, s: TaskStatus, onEdit: (VehicleEdit) -> Unit) {
    val t = s.task
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.name, fontWeight = FontWeight.Medium)
                Text(
                    listOfNotNull(
                        intervalText(model, t),
                        s.lastDate?.let { d -> model.t("vehicles.lastDone", model.date(d)) + (s.lastOdometer?.let { " · ${km(model, it)}" }.orEmpty()) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(Modifier.width(360.dp)) {
                val due = listOfNotNull(s.dueDate?.let(model::date), s.dueOdometer?.let { km(model, it) }).joinToString(" ${model.t("vehicles.or")} ")
                Text(
                    model.t("taskState.${s.state}") + if (due.isNotEmpty()) " · $due" else "",
                    fontWeight = if (s.state == TaskState.OK) FontWeight.Normal else FontWeight.Bold,
                    color = when (s.state) {
                        TaskState.DUE -> MaterialTheme.colorScheme.error
                        TaskState.SOON -> MaterialTheme.colorScheme.tertiary
                        TaskState.OK -> MaterialTheme.colorScheme.onSurface
                    },
                )
                s.forecastDate?.takeIf { s.dueOdometer != null }?.let { Text(model.t("vehicles.forecast", model.date(it)), style = MaterialTheme.typography.bodySmall) }
            }
            OutlinedButton(onClick = {
                onEdit(VehicleEdit.Service(v, ServiceRecord("", v.id, today(), model.books.vehicles.latestOdometer(v.id)?.odometer, taskIds = setOf(t.id))))
            }) { Text(model.t("vehicles.markDone")) }
            TextButton(onClick = { onEdit(VehicleEdit.Task(t)) }) { Text(model.t("common.edit")) }
        }
    }
}

private fun intervalText(model: BooksModel, t: MaintenanceTask): String = listOfNotNull(
    t.intervalMonths?.let { model.t("vehicles.everyMonths", it) },
    t.intervalKm?.let { model.t("vehicles.everyKm", km(model, it)) },
).joinToString(" ${model.t("vehicles.or")} ")

// --- Service log (VEH-06) ----------------------------------------------------------------------------

@Composable
private fun ServiceTab(model: BooksModel, v: Vehicle, onEdit: (VehicleEdit) -> Unit) {
    val books = model.books
    val services = remember(model.revision, v.id) { books.vehicles.services(v.id) }
    val taskNames = remember(model.revision, v.id) { books.vehicles.tasks(v.id).associate { it.id to it.name } }
    Column {
        Row {
            Box(Modifier.weight(1f))
            Button(onClick = { onEdit(VehicleEdit.Service(v, ServiceRecord("", v.id, today(), books.vehicles.latestOdometer(v.id)?.odometer))) }) { Text(model.t("vehicles.addService")) }
        }
        LazyColumn(Modifier.padding(top = 8.dp)) {
            if (services.isEmpty()) item { Text(model.t("vehicles.noServices"), Modifier.padding(8.dp)) }
            items(services, key = { it.id }) { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(model.date(s.date), Modifier.width(110.dp))
                    Text(s.odometer?.let { km(model, it) }.orEmpty(), Modifier.width(120.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.taskIds.mapNotNull(taskNames::get).joinToString(", ").ifBlank { s.notes ?: model.t("vehicles.service") },
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(if (s.diy) model.t("vehicles.diy") else s.provider, s.transactionId?.let { model.t("vehicles.paymentLinked") }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(s.cost?.let(model::money).orEmpty(), Modifier.width(120.dp))
                    TextButton(onClick = { onEdit(VehicleEdit.Service(v, s)) }) { Text(model.t("common.edit")) }
                }
                HorizontalDivider()
            }
        }
    }
}

// --- Fuel (VEH-07) -------------------------------------------------------------------------------------

@Composable
private fun FuelTab(model: BooksModel, v: Vehicle, onEdit: (VehicleEdit) -> Unit) {
    val books = model.books
    val today = today()
    val entries = remember(model.revision, v.id) { books.vehicles.fuel(v.id).reversed() }
    val from = LocalDate(today.year - 1, today.month, 1)
    val year = remember(model.revision, v.id) { books.vehicles.fuelStats(v.id, from, today) }
    // TRP-05: consumption by kind of driving; TRP-10: charging and the cost of a km on each energy.
    val byLoad = remember(model.revision, v.id) { books.vehicles.fuelByLoad(v.id, from, today) }
    val other = if (v.defaultEnergy == Energy.FUEL) Energy.ELECTRICITY else Energy.FUEL
    val otherStats = remember(model.revision, v.id) { if (v.takesBoth) books.vehicles.fuelStats(v.id, from, today, other) else null }
    val charging = remember(model.revision, v.id) { books.vehicles.chargingCosts(v.id, from, today) }
    val compare = remember(model.revision, v.id) { books.vehicles.householdCostPerKm(other, from, today, v.id, v.currency) }
    fun unitOf(e: Energy) = model.t(if (e == Energy.ELECTRICITY) "vehicles.kwh" else "vehicles.litres")
    fun per100(e: Energy, x: BigDecimal) = model.t(if (e == Energy.ELECTRICITY) "vehicles.consumptionKwh" else "vehicles.consumption", MoneyFormat.formatDecimal(x, model.language.locale))
    fun short(e: Energy, x: BigDecimal) = model.t(if (e == Energy.ELECTRICITY) "vehicles.per100Kwh" else "vehicles.per100L", MoneyFormat.formatDecimal(x, model.language.locale))
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(year.per100km?.let { per100(v.defaultEnergy, it) } ?: model.t("vehicles.consumptionUnknown"), fontWeight = FontWeight.Medium)
                year.costPerKm?.let { Text(model.t("vehicles.costPerKmOn", model.t("energy.${v.defaultEnergy}"), model.money(it)), style = MaterialTheme.typography.bodySmall) }
                otherStats?.per100km?.let { Text(model.t("trips.purposeKm", model.t("energy.$other"), short(other, it)) + (otherStats.costPerKm?.let { c -> " · " + model.t("vehicles.costPerKmOn", model.t("energy.$other"), model.money(c)) }.orEmpty()), style = MaterialTheme.typography.bodySmall) }
                if (byLoad.keys.any { it != TripLoad.NONE }) {
                    Text(
                        byLoad.entries.mapNotNull { (load, s) -> s.per100km?.let { model.t("trips.purposeKm", model.t("vehicles.driving.$load"), short(v.defaultEnergy, it)) } }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (charging.isNotEmpty()) {
                    Text(
                        charging.joinToString(" · ") { c ->
                            model.t("vehicles.chargingLine", model.t("charging.${c.charging}"), MoneyFormat.formatDecimal(c.kwh, model.language.locale), c.perKwh?.let { MoneyFormat.formatDecimal(it, model.language.locale) } ?: "—")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                compare?.takeIf { !v.takesBoth }?.let { Text(model.t("vehicles.compareOther", model.t("energy.$other"), model.money(it)), style = MaterialTheme.typography.bodySmall) }
            }
            Button(onClick = { onEdit(VehicleEdit.Fuel(v, FuelEntry("", v.id, today, books.vehicles.latestOdometer(v.id)?.odometer, BigDecimal.ZERO))) }) {
                Text(model.t(if (v.electric) "vehicles.addCharge" else "vehicles.addFuel"))
            }
        }
        LazyColumn(Modifier.padding(top = 8.dp)) {
            if (entries.isEmpty()) item { Text(model.t("vehicles.noFuel"), Modifier.padding(8.dp)) }
            items(entries, key = { it.id }) { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(model.date(e.date), Modifier.width(110.dp))
                    Text(e.odometer?.let { km(model, it) }.orEmpty(), Modifier.width(120.dp))
                    val energy = e.energy ?: v.defaultEnergy
                    Text("${MoneyFormat.formatDecimal(e.quantity, model.language.locale)} ${unitOf(energy)}" + if (!e.fullTank) " (${model.t("vehicles.partial")})" else "", Modifier.width(180.dp))
                    Text(
                        listOfNotNull(e.charging?.let { model.t("charging.$it") }, e.station, e.deviceId?.let { model.t("vehicles.fromPhone") }, e.transactionId?.let { model.t("vehicles.paymentLinked") },
                            // A fill-up from the phone comes without its payment: Edit offers to enter it.
                            model.t("vehicles.noPayment").takeIf { e.deviceId != null && e.transactionId == null && e.cost != null },
                        ).joinToString(" · "),
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                    )
                    Text(e.cost?.let(model::money).orEmpty(), Modifier.width(120.dp))
                    TextButton(onClick = { onEdit(VehicleEdit.Fuel(v, e)) }) { Text(model.t("common.edit")) }
                }
                HorizontalDivider()
            }
        }
    }
}

// --- Warranties (VEH-03) ------------------------------------------------------------------------------

@Composable
private fun WarrantiesTab(model: BooksModel, v: Vehicle, onEdit: (VehicleEdit) -> Unit) {
    val books = model.books
    val warranties = remember(model.revision, v.id) { books.vehicles.warranties(v.id) }
    val odometer = remember(model.revision, v.id) { books.vehicles.latestOdometer(v.id)?.odometer }
    Column {
        Row {
            Text(model.t("vehicles.warrantyExplain", LeadTimes.warranty(today())), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Button(onClick = { onEdit(VehicleEdit.WarrantyEdit(Warranty("", v.id, WarrantyKind.MANUFACTURER, v.make, v.purchaseDate))) }) { Text(model.t("vehicles.addWarranty")) }
        }
        LazyColumn(Modifier.padding(top = 8.dp)) {
            if (warranties.isEmpty()) item { Text(model.t("vehicles.noWarranties"), Modifier.padding(8.dp)) }
            items(warranties, key = { it.id }) { w ->
                val covered = w.covers(today(), odometer)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(model.t("warrantyKind.${w.kind}") + (w.provider?.let { " · $it" }.orEmpty()), fontWeight = FontWeight.Medium)
                        Text(
                            listOfNotNull(w.endDate?.let { model.t("vehicles.until", model.date(it)) }, w.endKm?.let { km(model, it) }, w.phone).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        model.t(if (covered) "vehicles.covered" else "vehicles.notCovered"), Modifier.width(160.dp),
                        color = if (covered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Medium,
                    )
                    TextButton(onClick = { onEdit(VehicleEdit.WarrantyEdit(w)) }) { Text(model.t("common.edit")) }
                }
                HorizontalDivider()
            }
        }
    }
}

// --- Costs (VEH-10) ------------------------------------------------------------------------------------

@Composable
private fun CostsTab(model: BooksModel, v: Vehicle) {
    val books = model.books
    val today = today()
    var allYears by remember { mutableStateOf(false) }
    val from = if (allYears) LocalDate(1990, 1, 1) else LocalDate(today.year, 1, 1)
    val cost = remember(model.revision, v.id, allYears) { books.vehicles.costs(v.id, from, today) }
    val categories = remember(model.revision) { books.categories.list(includeArchived = true).associate { it.id to it.name(model.language) } }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (all in listOf(false, true)) {
                val label = model.t(if (all) "vehicles.costsAll" else "vehicles.costsYear", today.year)
                if (all == allYears) Button(onClick = {}) { Text(label) } else OutlinedButton(onClick = { allYears = all }) { Text(label) }
            }
        }
        Text(model.t("costs.total", model.money(cost.costs.total)), style = MaterialTheme.typography.titleMedium)
        Text(
            listOfNotNull(cost.distanceKm?.let { model.t("vehicles.driven", km(model, it)) }, cost.costPerKm?.let { model.t("vehicles.perKm", model.money(it)) }).joinToString(" · "),
        )
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        if (cost.costs.byCategory.isEmpty()) Text(model.t("costs.none"))
        for ((id, total) in cost.costs.byCategory) {
            Row { Text(id?.let(categories::get) ?: model.t("register.uncategorized"), Modifier.width(320.dp)); Text(model.money(total)) }
        }
        // MNT-13: the vehicle's share of the policies that name it, an estimate beside the running costs.
        cost.insurance?.let { Text(model.t("vehicles.insuranceShare", model.money(it)), modifier = Modifier.padding(top = 4.dp)) }
        if (allYears && cost.costs.byYear.size > 1) {
            // VEH-10: each year by category; the largest categories as bars, the rest together.
            Text(model.t("costs.byYear"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            val years = cost.costs.byYear.keys.toList()
            val top = cost.costs.byCategory.take(4).map { it.first }
            val zero = Money.zero(cost.costs.total.currency)
            fun name(category: String?) = category?.let(categories::get) ?: model.t("register.uncategorized")
            fun amount(year: Int, category: String?) = cost.costs.byYearCategory[year].orEmpty().firstOrNull { it.first == category }?.second ?: zero
            fun rest(year: Int) = cost.costs.byYearCategory[year].orEmpty().filter { it.first !in top }.fold(zero) { a, b -> a + b.second }
            val series = top.map { c -> Series(name(c), years.map { amount(it, c).d() }, years.map { model.money(amount(it, c)) }) } +
                listOfNotNull(
                    Series(model.t("costs.otherCategories"), years.map { rest(it).d() }, years.map { model.money(rest(it)) }).takeIf { years.any { y -> rest(y).isPositive } },
                )
            GroupedBarChart(years.map { it.toString() }, series, model.axis())
            TableView(
                model,
                ReportTable(
                    model.t("costs.byYearCategory", v.name), model.t("report.inCurrency", cost.costs.total.currency.code),
                    listOf(model.t("loans.year"), model.t("register.category"), model.t("register.amount")),
                    years.flatMap { y ->
                        cost.costs.byYearCategory[y].orEmpty().map { (c, m) -> listOf<Any?>(y.toString(), name(c), m) } +
                            listOf(listOf<Any?>(y.toString(), model.t("report.total"), cost.costs.byYear[y])) +
                            listOfNotNull(cost.insuranceByYear[y]?.let { listOf<Any?>(y.toString(), model.t("vehicles.insuranceEstimate"), it) })
                    },
                ),
            )
        }
        v.purchasePrice?.let { Text(model.t("vehicles.purchaseNotIncluded", model.money(it)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        if (cost.costs.unconverted > 0) Text(model.t("costs.unconverted", cost.costs.unconverted), style = MaterialTheme.typography.bodySmall)
        Text(model.t("vehicles.costsHow"), style = MaterialTheme.typography.bodySmall)
    }
}

// --- Dialogs ---------------------------------------------------------------------------------------------

@Composable
private fun VehicleDialog(model: BooksModel, existing: Vehicle, onClose: (String?) -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val people = remember { books.members.list() }
    val c = existing.currency
    var name by remember { mutableStateOf(existing.name) }
    var make by remember { mutableStateOf(existing.make.orEmpty()) }
    var vModel by remember { mutableStateOf(existing.model.orEmpty()) }
    var year by remember { mutableStateOf(existing.modelYear?.toString().orEmpty()) }
    var trim by remember { mutableStateOf(existing.trimLevel.orEmpty()) }
    var colour by remember { mutableStateOf(existing.colour.orEmpty()) }
    var vin by remember { mutableStateOf(existing.vin.orEmpty()) }
    var plate by remember { mutableStateOf(existing.plate.orEmpty()) }
    var fuel by remember { mutableStateOf(existing.fuelType) }
    var driverId by remember { mutableStateOf(existing.driverMemberId) }
    var purchaseDate by remember { mutableStateOf(existing.purchaseDate?.toString().orEmpty()) }
    var price by remember { mutableStateOf(existing.purchasePrice?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var seller by remember { mutableStateOf(existing.seller.orEmpty()) }
    var purchaseOdo by remember { mutableStateOf(existing.purchaseOdometer?.toString().orEmpty()) }
    var registration by remember { mutableStateOf(existing.registrationRenewal?.toString().orEmpty()) }
    var insurer by remember { mutableStateOf(existing.insurer.orEmpty()) }
    var policy by remember { mutableStateOf(existing.policyNumber.orEmpty()) }
    var insurance by remember { mutableStateOf(existing.insuranceRenewal?.toString().orEmpty()) }
    var status by remember { mutableStateOf(existing.status) }
    var disposalDate by remember { mutableStateOf(existing.disposalDate?.toString().orEmpty()) }
    var disposalPrice by remember { mutableStateOf(existing.disposalPrice?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    // SAL-03: the sale in the books (its payee is the buyer).
    var saleId by remember { mutableStateOf(existing.disposalTransactionId) }
    var notes by remember { mutableStateOf(existing.notes.orEmpty()) }
    var groupId by remember { mutableStateOf(existing.groupId) }
    var confirmDelete by remember { mutableStateOf(false) }
    // TRP-07, TRP-09.
    val d = existing.details
    fun dec(x: BigDecimal?) = x?.let { MoneyFormat.formatDecimal(it, locale) }.orEmpty()
    var engine by remember { mutableStateOf(d.engine.orEmpty()) }
    var transmission by remember { mutableStateOf(d.transmission) }
    var drive by remember { mutableStateOf(d.drive) }
    var tank by remember { mutableStateOf(dec(d.tankLitres)) }
    var battery by remember { mutableStateOf(dec(d.batteryKwh)) }
    var tiresSummer by remember { mutableStateOf(d.tiresSummer.orEmpty()) }
    var tiresWinter by remember { mutableStateOf(d.tiresWinter.orEmpty()) }
    var oilType by remember { mutableStateOf(d.oilType.orEmpty()) }
    var oilLitres by remember { mutableStateOf(dec(d.oilLitres)) }
    var towing by remember { mutableStateOf(d.towingKg?.toString().orEmpty()) }
    var gvwr by remember { mutableStateOf(d.gvwrKg?.toString().orEmpty()) }
    var usage by remember { mutableStateOf(existing.usage) }
    var inspection by remember { mutableStateOf(existing.inspectionDue?.toString().orEmpty()) }
    var operator by remember { mutableStateOf(existing.operatorRenewal?.toString().orEmpty()) }
    fun decimalOrNull(text: String): BigDecimal? = text.trim().ifEmpty { null }?.let { runCatching { MoneyFormat.parseDecimal(it, locale) }.getOrElse { throw ValidationException("error.invalidNumber") } }

    FormDialog(
        model.t(if (existing.id.isBlank()) "vehicles.add" else "vehicles.edit"), model.t("common.save"), model.t("common.cancel"),
        canSave = name.isNotBlank(), onDismiss = { onClose(null) },
        onSave = {
            val saved = model.act {
                books.vehicles.save(
                    existing.copy(
                        groupId = groupId, name = name, make = make, model = vModel, modelYear = optionalInt(year), trimLevel = trim, colour = colour, vin = vin,
                        plate = plate, fuelType = fuel, driverMemberId = driverId, purchaseDate = optionalDate(purchaseDate),
                        purchasePrice = parseAmount(price, c, locale), seller = seller, purchaseOdometer = optionalInt(purchaseOdo),
                        registrationRenewal = optionalDate(registration), insurer = insurer, policyNumber = policy, insuranceRenewal = optionalDate(insurance),
                        status = status, disposalDate = optionalDate(disposalDate), disposalPrice = parseAmount(disposalPrice, c, locale), notes = notes,
                        disposalTransactionId = saleId.takeIf { status == VehicleStatus.SOLD },
                        details = VehicleDetails(
                            engine, transmission, drive, decimalOrNull(tank), decimalOrNull(battery), tiresSummer, tiresWinter, oilType, decimalOrNull(oilLitres),
                            optionalInt(towing), optionalInt(gvwr),
                        ),
                        usage = usage, inspectionDue = optionalDate(inspection), operatorRenewal = optionalDate(operator),
                    ),
                )
            }
            if (saved != null) onClose(saved.id)
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextInput(model.t("vehicles.name"), name, supporting = model.t("vehicles.name.hint")) { name = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("vehicles.make"), make, Modifier.weight(1f)) { make = it }
                TextInput(model.t("vehicles.model"), vModel, Modifier.weight(1f)) { vModel = it }
                TextInput(model.t("vehicles.year"), year, Modifier.weight(0.6f)) { year = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("vehicles.trim"), trim, Modifier.weight(1f)) { trim = it }
                TextInput(model.t("pets.colour"), colour, Modifier.weight(1f)) { colour = it }
                Picker(model.t("vehicles.fuelType"), FuelType.entries, fuel, { model.t("fuelType.$it") }, Modifier.weight(1f)) { fuel = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("vehicles.plate"), plate, Modifier.weight(1f)) { plate = it }
                TextInput(model.t("vehicles.vin"), vin, Modifier.weight(2f)) { vin = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("vehicles.driver"), listOf(null) + people, people.firstOrNull { it.id == driverId }, { it?.displayName ?: model.t("common.none") }, Modifier.weight(1f)) { driverId = it?.id }
                Picker(model.t("vehicles.usage"), VehicleUsage.entries, usage, { model.t("vehicleUsage.$it") }, Modifier.weight(1f)) { usage = it }
            }
            // TRP-07: technical details.
            Text(model.t("vehicles.technical"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("vehicles.engine"), engine, Modifier.weight(1f), supporting = model.t("vehicles.engine.hint")) { engine = it }
                Picker(model.t("vehicles.transmission"), listOf(null) + Transmission.entries, transmission, { it?.let { t -> model.t("transmission.$t") } ?: model.t("common.none") }, Modifier.weight(1f)) { transmission = it }
                Picker(model.t("vehicles.drive"), listOf(null) + DriveKind.entries, drive, { it?.let { x -> model.t("drive.$x") } ?: model.t("common.none") }, Modifier.weight(1f)) { drive = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (fuel != FuelType.ELECTRIC) TextInput(model.t("vehicles.tankLitres"), tank, Modifier.weight(1f)) { tank = it }
                if (fuel == FuelType.ELECTRIC || fuel == FuelType.PLUG_IN_HYBRID || fuel == FuelType.HYBRID) TextInput(model.t("vehicles.batteryKwh"), battery, Modifier.weight(1f)) { battery = it }
                TextInput(model.t("vehicles.tiresSummer"), tiresSummer, Modifier.weight(1f)) { tiresSummer = it }
                TextInput(model.t("vehicles.tiresWinter"), tiresWinter, Modifier.weight(1f)) { tiresWinter = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (fuel != FuelType.ELECTRIC) {
                    TextInput(model.t("vehicles.oilType"), oilType, Modifier.weight(1f)) { oilType = it }
                    TextInput(model.t("vehicles.oilLitres"), oilLitres, Modifier.weight(1f)) { oilLitres = it }
                }
                TextInput(model.t("vehicles.towingKg"), towing, Modifier.weight(1f)) { towing = it }
                TextInput(model.t("vehicles.gvwrKg"), gvwr, Modifier.weight(1f)) { gvwr = it }
            }
            Text(model.t("vehicles.purchase"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(model.t("report.date"), purchaseDate, Modifier.weight(1f)) { purchaseDate = it }
                AmountInput(model.t("vehicles.price"), price, c, locale, Modifier.weight(1f), model::money) { price = it }
                TextInput(model.t("vehicles.odometer"), purchaseOdo, Modifier.weight(1f)) { purchaseOdo = it }
            }
            TextInput(model.t("vehicles.seller"), seller) { seller = it }
            Text(model.t("vehicles.papers"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(model.t("vehicles.registrationRenewal"), registration, Modifier.weight(1f)) { registration = it }
                DateInput(model.t("vehicles.insuranceRenewal"), insurance, Modifier.weight(1f)) { insurance = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("pets.insurer"), insurer, Modifier.weight(1f)) { insurer = it }
                TextInput(model.t("pets.policy"), policy, Modifier.weight(1f)) { policy = it }
            }
            // TRP-09: reminded like the registration; mostly for commercial vehicles.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(model.t("vehicles.inspectionDue"), inspection, Modifier.weight(1f)) { inspection = it }
                if (usage != VehicleUsage.PERSONAL || operator.isNotBlank()) DateInput(model.t("vehicles.operatorRenewal"), operator, Modifier.weight(1f)) { operator = it }
            }
            if (existing.id.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Picker(model.t("goals.status"), VehicleStatus.entries, status, { model.t("vehicleStatus.$it") }, Modifier.weight(1f)) { status = it }
                    if (status != VehicleStatus.ACTIVE) {
                        DateInput(model.t("vehicles.disposalDate"), disposalDate, Modifier.weight(1f)) { disposalDate = it }
                        AmountInput(model.t("vehicles.disposalPrice"), disposalPrice, c, locale, Modifier.weight(1f), model::money) { disposalPrice = it }
                    }
                }
                if (status == VehicleStatus.SOLD) {
                    SaleLink(model, saleId, onUnlink = { saleId = null }) { t ->
                        saleId = t.id
                        if (disposalDate.isBlank()) disposalDate = t.date.toString()
                        if (disposalPrice.isBlank()) disposalPrice = MoneyFormat.formatAmount(t.amount, locale)
                    }
                    saleResult(model, runCatching { parseAmount(price, c, locale) }.getOrNull(), runCatching { parseAmount(disposalPrice, c, locale) }.getOrNull())
                        ?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
            GroupPicker(model, groupId, enabled = existing.id.isBlank()) { groupId = it.id }
            // CON-04: the garage, the insurer, as contacts.
            LinkedContacts(model, LinkTarget.VEHICLE, existing.id.ifBlank { null }, listOf(LinkRole.GARAGE, LinkRole.SERVICE, LinkRole.INSURER, LinkRole.OTHER), groupId = existing.groupId)
            if (existing.id.isNotBlank()) TextButton(onClick = { confirmDelete = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmDelete) {
        FormDialog(model.t("vehicles.delete.title"), model.t("common.delete"), model.t("common.cancel"), onDismiss = { confirmDelete = false }, onSave = {
            if (model.act { books.vehicles.delete(existing.id) } != null) {
                confirmDelete = false
                onClose(null)
            }
        }) { Text(model.t("vehicles.delete.body", existing.name)) }
    }
}

@Composable
private fun ReadingDialog(model: BooksModel, v: Vehicle, onClose: () -> Unit) {
    var date by remember { mutableStateOf(today().toString()) }
    var odometer by remember { mutableStateOf("") }
    FormDialog(model.t("vehicles.addReading") + " · " + v.name, model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act { model.books.vehicles.addReading(v.id, requiredDate(date), optionalInt(odometer) ?: throw ValidationException("error.invalidNumber")) }
        if (ok != null) onClose()
    }) {
        DateInput(model.t("report.date"), date, Modifier.fillMaxWidth()) { date = it }
        TextInput(model.t("vehicles.odometer"), odometer) { odometer = it }
    }
}

@Composable
private fun TaskDialog(model: BooksModel, existing: MaintenanceTask, onClose: () -> Unit) {
    var name by remember { mutableStateOf(existing.name) }
    var months by remember { mutableStateOf(existing.intervalMonths?.toString().orEmpty()) }
    var kms by remember { mutableStateOf(existing.intervalKm?.toString().orEmpty()) }
    var start by remember { mutableStateOf(existing.startDate?.toString().orEmpty()) }
    var startOdo by remember { mutableStateOf(existing.startOdometer?.toString().orEmpty()) }
    var remindDays by remember { mutableStateOf(existing.remindDays.toString()) }
    var remindKm by remember { mutableStateOf(existing.remindKm.toString()) }
    var active by remember { mutableStateOf(existing.active) }
    var notes by remember { mutableStateOf(existing.notes.orEmpty()) }
    var asking by remember { mutableStateOf(false) }
    FormDialog(
        model.t(if (existing.id.isBlank()) "vehicles.addTask" else "vehicles.editTask"), model.t("common.save"), model.t("common.cancel"),
        canSave = name.isNotBlank(), onDismiss = onClose,
        onSave = {
            val ok = model.act {
                model.books.vehicles.saveTask(
                    existing.copy(
                        name = name, intervalMonths = optionalInt(months), intervalKm = optionalInt(kms), startDate = optionalDate(start),
                        startOdometer = optionalInt(startOdo), remindDays = optionalInt(remindDays) ?: LeadTimes.maintenance(), remindKm = optionalInt(remindKm) ?: 500,
                        active = active, notes = notes,
                    ),
                )
            }
            if (ok != null) onClose()
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextInput(model.t("vehicles.taskName"), name) { name = it }
            Text(model.t("vehicles.intervalExplain"), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("vehicles.intervalMonths"), months, Modifier.weight(1f)) { months = it }
                TextInput(model.t("vehicles.intervalKm"), kms, Modifier.weight(1f)) { kms = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(model.t("vehicles.lastDoneDate"), start, Modifier.weight(1f)) { start = it }
                TextInput(model.t("vehicles.lastDoneKm"), startOdo, Modifier.weight(1f)) { startOdo = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("vehicles.remindDays"), remindDays, Modifier.weight(1f)) { remindDays = it }
                TextInput(model.t("vehicles.remindKm"), remindKm, Modifier.weight(1f)) { remindKm = it }
            }
            TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
            if (existing.id.isNotBlank()) {
                LabeledCheckbox(model.t("vehicles.taskActive"), active) { active = it }
                TextButton(onClick = { asking = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (asking) {
        AskBeforeDeleting(model, model.t("upkeep.delete.task", existing.name), onDismiss = { asking = false }) {
            (model.act { model.books.vehicles.deleteTask(existing.vehicleId, existing.id) } != null).also { if (it) onClose() }
        }
    }
}

/** VEH-08: "also enter the payment" for service and fuel entries. */
@Composable
internal fun PaymentFields(model: BooksModel, currency: Currency, defaultCategoryKey: String, state: PaymentState) {
    val books = model.books
    val accounts = remember { books.accounts.list().map { it.account }.filter { it.currency == currency } }
    val tree = remember { books.categories.tree() }
    if (state.accountId == null) state.accountId = accounts.firstOrNull { it.type == AccountType.CREDIT_CARD }?.id ?: accounts.firstOrNull()?.id
    if (state.categoryId == null) state.categoryId = tree.firstOrNull { it.first.systemKey == defaultCategoryKey }?.first?.id
    LabeledCheckbox(model.t("vehicles.alsoPayment"), state.enabled) { state.enabled = it }
    if (state.enabled) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker(model.t("vehicles.paidFrom"), accounts, accounts.firstOrNull { it.id == state.accountId }, { it.name }, Modifier.weight(1f)) { state.accountId = it.id }
            Picker(model.t("register.category"), tree, tree.firstOrNull { it.first.id == state.categoryId }, { it.first.name(model.language) }, Modifier.weight(1f), indent = { it.second }) {
                state.categoryId = it.first.id
            }
        }
    }
}

internal class PaymentState {
    var enabled by mutableStateOf(false)
    var accountId by mutableStateOf<String?>(null)
    var categoryId by mutableStateOf<String?>(null)

    fun draft(): PaymentDraft? = if (enabled) PaymentDraft(accountId ?: throw ValidationException("error.accountRequired"), categoryId) else null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceDialog(model: BooksModel, v: Vehicle, existing: ServiceRecord, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val tasks = remember { books.vehicles.tasks(v.id).filter { it.active || it.id in existing.taskIds } }
    var date by remember { mutableStateOf(existing.date.toString()) }
    var odometer by remember { mutableStateOf(existing.odometer?.toString().orEmpty()) }
    var provider by remember { mutableStateOf(existing.provider.orEmpty()) }
    var diy by remember { mutableStateOf(existing.diy) }
    var cost by remember { mutableStateOf(existing.cost?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var notes by remember { mutableStateOf(existing.notes.orEmpty()) }
    var done by remember { mutableStateOf(existing.taskIds) }
    val payment = remember { PaymentState() }
    var asking by remember { mutableStateOf(false) }
    FormDialog(
        model.t(if (existing.id.isBlank()) "vehicles.addService" else "vehicles.editService") + " · " + v.name, model.t("common.save"), model.t("common.cancel"),
        onDismiss = onClose,
        onSave = {
            val ok = model.act {
                books.vehicles.saveService(
                    existing.copy(
                        date = requiredDate(date), odometer = optionalInt(odometer), provider = provider, diy = diy,
                        cost = parseAmount(cost, v.currency, locale), notes = notes, taskIds = done,
                    ),
                    payment.draft().takeIf { existing.transactionId == null },
                )
            }
            if (ok != null) onClose()
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(model.t("report.date"), date, Modifier.weight(1f)) { date = it }
                TextInput(model.t("vehicles.odometer"), odometer, Modifier.weight(1f)) { odometer = it }
            }
            if (tasks.isNotEmpty()) {
                Text(model.t("vehicles.tasksDone"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (t in tasks) LabeledCheckbox(t.name, t.id in done) { on -> done = if (on) done + t.id else done - t.id }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextInput(model.t("vehicles.provider"), provider, Modifier.weight(1f), enabled = !diy) { provider = it }
                Column(Modifier.weight(1f)) { LabeledCheckbox(model.t("vehicles.diy"), diy) { diy = it } }
            }
            AmountInput(model.t("vehicles.cost"), cost, v.currency, locale, Modifier.fillMaxWidth(), model::money) { cost = it }
            TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
            if (existing.transactionId == null) PaymentFields(model, v.currency, "transport.maintenance", payment) else Text(model.t("vehicles.paymentLinked"), style = MaterialTheme.typography.bodySmall)
            if (existing.id.isNotBlank()) {
                TextButton(onClick = { asking = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (asking) {
        AskBeforeDeleting(model, model.t("upkeep.delete.service", model.date(existing.date)), onDismiss = { asking = false }) {
            (model.act { books.vehicles.deleteService(v.id, existing.id) } != null).also { if (it) onClose() }
        }
    }
}

@Composable
private fun FuelDialog(model: BooksModel, v: Vehicle, existing: FuelEntry, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    var date by remember { mutableStateOf(existing.date.toString()) }
    var odometer by remember { mutableStateOf(existing.odometer?.toString().orEmpty()) }
    var quantity by remember { mutableStateOf(existing.quantity.takeIf { it.signum() > 0 }?.let { MoneyFormat.formatDecimal(it, locale) }.orEmpty()) }
    var cost by remember { mutableStateOf(existing.cost?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var full by remember { mutableStateOf(existing.fullTank) }
    var station by remember { mutableStateOf(existing.station.orEmpty()) }
    val payment = remember { PaymentState() }
    var asking by remember { mutableStateOf(false) }
    // TRP-05, TRP-10: fuel or electricity (a plug-in hybrid takes both), home or public charging, the station as a saved place.
    var energy by remember { mutableStateOf(existing.energy ?: v.defaultEnergy) }
    var charging by remember { mutableStateOf(existing.charging ?: Charging.HOME) }
    val places = remember { books.places.list(includeArchived = true).filter { !it.archived || it.id == existing.placeId } }
    var place by remember { mutableStateOf(places.firstOrNull { it.id == existing.placeId }) }
    val electricity = energy == Energy.ELECTRICITY
    FormDialog(
        model.t(if (electricity) "vehicles.addCharge" else "vehicles.addFuel") + " · " + v.name, model.t("common.save"), model.t("common.cancel"),
        onDismiss = onClose,
        onSave = {
            val ok = model.act {
                val q = runCatching { MoneyFormat.parseDecimal(quantity, locale) }.getOrElse { throw ValidationException("error.fuelQuantity") }
                books.vehicles.saveFuel(
                    existing.copy(
                        date = requiredDate(date), odometer = optionalInt(odometer), quantity = q, cost = parseAmount(cost, v.currency, locale), fullTank = full, station = station,
                        energy = energy, charging = charging.takeIf { electricity }, placeId = place?.id,
                    ),
                    payment.draft().takeIf { existing.transactionId == null },
                )
            }
            if (ok != null) onClose()
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (v.takesBoth) Picker(model.t("vehicles.energy"), Energy.entries, energy, { model.t("energy.$it") }) { energy = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(model.t("report.date"), date, Modifier.weight(1f)) { date = it }
                TextInput(model.t("vehicles.odometer"), odometer, Modifier.weight(1f)) { odometer = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t(if (electricity) "vehicles.kwhField" else "vehicles.litresField"), quantity, Modifier.weight(1f)) { quantity = it }
                AmountInput(model.t("vehicles.cost"), cost, v.currency, locale, Modifier.weight(1f), model::money) { cost = it }
            }
            LabeledCheckbox(model.t(if (electricity) "vehicles.fullCharge" else "vehicles.fullTank"), full) { full = it }
            Text(model.t("vehicles.fullTank.hint"), style = MaterialTheme.typography.bodySmall)
            if (electricity) Picker(model.t("vehicles.charging"), Charging.entries, charging, { model.t("charging.$it") }) { charging = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("vehicles.stationPlace"), listOf(null) + places, place, { it?.name ?: model.t("trips.noPlace") }, Modifier.weight(1f)) { p ->
                    place = p
                    if (p != null) station = p.name
                }
                TextInput(model.t("vehicles.station"), station, Modifier.weight(1f)) { station = it }
            }
            if (existing.transactionId == null) {
                PaymentFields(model, v.currency, if (electricity) "transport.ev_charging" else "transport.fuel", payment)
            } else {
                Text(model.t("vehicles.paymentLinked"), style = MaterialTheme.typography.bodySmall)
            }
            if (existing.id.isNotBlank()) {
                TextButton(onClick = { asking = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (asking) {
        AskBeforeDeleting(model, model.t("vehicles.delete.fuel", model.date(existing.date)), onDismiss = { asking = false }) {
            (model.act { books.vehicles.deleteFuel(v.id, existing.id) } != null).also { if (it) onClose() }
        }
    }
}

@Composable
private fun WarrantyDialog(model: BooksModel, existing: Warranty, onClose: () -> Unit) {
    var kind by remember { mutableStateOf(existing.kind) }
    var provider by remember { mutableStateOf(existing.provider.orEmpty()) }
    var start by remember { mutableStateOf(existing.startDate?.toString().orEmpty()) }
    var end by remember { mutableStateOf(existing.endDate?.toString().orEmpty()) }
    var endKm by remember { mutableStateOf(existing.endKm?.toString().orEmpty()) }
    var phone by remember { mutableStateOf(existing.phone.orEmpty()) }
    var notes by remember { mutableStateOf(existing.notes.orEmpty()) }
    var asking by remember { mutableStateOf(false) }
    FormDialog(
        model.t(if (existing.id.isBlank()) "vehicles.addWarranty" else "vehicles.editWarranty"), model.t("common.save"), model.t("common.cancel"),
        onDismiss = onClose,
        onSave = {
            val ok = model.act {
                model.books.vehicles.saveWarranty(
                    existing.copy(kind = kind, provider = provider, startDate = optionalDate(start), endDate = optionalDate(end), endKm = optionalInt(endKm), phone = phone, notes = notes),
                )
            }
            if (ok != null) onClose()
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker(model.t("vehicles.warrantyKind"), WarrantyKind.entries, kind, { model.t("warrantyKind.$it") }, Modifier.weight(1f)) { kind = it }
            TextInput(model.t("vehicles.provider"), provider, Modifier.weight(1f)) { provider = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t("vehicles.warrantyStart"), start, Modifier.weight(1f)) { start = it }
            DateInput(model.t("vehicles.warrantyEnd"), end, Modifier.weight(1f)) { end = it }
            TextInput(model.t("vehicles.warrantyKm"), endKm, Modifier.weight(1f)) { endKm = it }
        }
        TextInput(model.t("vehicles.claimPhone"), phone) { phone = it }
        TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
        if (existing.id.isNotBlank()) {
            TextButton(onClick = { asking = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            WarrantyClaims(model, existing)
        } else {
            Text(model.t("vehicles.claimsLater"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (asking) {
        AskBeforeDeleting(model, model.t("vehicles.delete.warranty", model.t("warrantyKind.${existing.kind}")), onDismiss = { asking = false }) {
            (model.act { model.books.vehicles.deleteWarranty(existing.vehicleId, existing.id) } != null).also { if (it) onClose() }
        }
    }
}

/** WAR-03: the claim log of a saved vehicle warranty: date, problem, outcome, cost covered, cost paid. */
@Composable
private fun WarrantyClaims(model: BooksModel, warranty: Warranty) {
    val books = model.books
    val locale = model.language.locale
    val currency = remember(warranty.vehicleId) { books.vehicles.get(warranty.vehicleId).currency }
    val claims = remember(model.revision, warranty.id) { books.vehicles.claims(warranty.vehicleId, warranty.id) }
    var date by remember { mutableStateOf(today().toString()) }
    var problem by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf("") }
    var covered by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<WarrantyClaim?>(null) }
    Text(model.t("assets.claims"), style = MaterialTheme.typography.titleSmall)
    for (c in claims) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${model.date(c.date)} · ${c.problem}" + (c.outcome?.let { " → $it" }.orEmpty()), Modifier.weight(1f))
            Text(
                listOfNotNull(c.covered?.let { model.t("assets.coveredAmount", model.money(it)) }, c.paid?.let { model.t("vehicles.paidAmount", model.money(it)) }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { deleting = c }) { Text(model.t("common.delete")) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        DateInput(model.t("report.date"), date, Modifier.width(150.dp)) { date = it }
        TextInput(model.t("assets.problem"), problem, Modifier.weight(1f)) { problem = it }
        TextInput(model.t("assets.outcome"), outcome, Modifier.weight(1f)) { outcome = it }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AmountInput(model.t("assets.coveredLabel"), covered, currency, locale, Modifier.width(150.dp), model::money) { covered = it }
        AmountInput(model.t("vehicles.claimPaid"), paid, currency, locale, Modifier.width(150.dp), model::money) { paid = it }
        OutlinedButton(onClick = {
            model.act {
                books.vehicles.saveClaim(
                    warranty.vehicleId,
                    WarrantyClaim("", warranty.id, optionalDate(date) ?: today(), problem, outcome, parseAmount(covered, currency, locale), parseAmount(paid, currency, locale)),
                )
            }?.let { problem = ""; outcome = ""; covered = ""; paid = "" }
        }) { Text(model.t("assets.addClaim")) }
    }
    deleting?.let { c ->
        AskBeforeDeleting(model, model.t("assets.delete.claim", c.problem, model.date(c.date)), onDismiss = { deleting = null }) {
            model.act { books.vehicles.deleteClaim(warranty.vehicleId, warranty.id, c.id) } != null
        }
    }
}

// --- Helpers ---------------------------------------------------------------------------------------------

private fun optionalDate(text: String): LocalDate? =
    text.trim().ifEmpty { null }?.let { runCatching { LocalDate.parse(it) }.getOrElse { throw ValidationException("error.invalidDate") } }

private fun requiredDate(text: String): LocalDate = optionalDate(text) ?: throw ValidationException("error.invalidDate")

/** Whole numbers such as odometer readings; spaces and thousands separators are ignored. */
private fun optionalInt(text: String): Int? =
    text.filterNot { it.isWhitespace() || it == ',' || it == ' ' || it == ' ' }.ifEmpty { null }?.let { it.toIntOrNull() ?: throw ValidationException("error.invalidNumber") }
