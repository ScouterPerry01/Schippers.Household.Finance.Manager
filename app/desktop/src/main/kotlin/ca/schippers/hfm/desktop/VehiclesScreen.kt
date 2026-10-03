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
import ca.schippers.hfm.books.FuelEntry
import ca.schippers.hfm.books.FuelType
import ca.schippers.hfm.books.MaintenanceTask
import ca.schippers.hfm.books.PaymentDraft
import ca.schippers.hfm.books.ServiceRecord
import ca.schippers.hfm.books.TaskState
import ca.schippers.hfm.books.TaskStatus
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.books.Vehicle
import ca.schippers.hfm.books.VehicleStatus
import ca.schippers.hfm.books.Warranty
import ca.schippers.hfm.books.WarrantyKind
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

private enum class VehicleTab { OVERVIEW, MAINTENANCE, SERVICE, FUEL, WARRANTIES, COSTS }

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

// --- Overview (VEH-01, VEH-02, VEH-04) -------------------------------------------------------------

@Composable
private fun OverviewTab(model: BooksModel, v: Vehicle, onEdit: (VehicleEdit) -> Unit) {
    val books = model.books
    val readings = remember(model.revision, v.id) { books.vehicles.readings(v.id) }
    val rate = remember(model.revision, v.id) { books.vehicles.kmPerDay(v.id) }
    val driver = remember(model.revision, v.driverMemberId) { v.driverMemberId?.let { id -> books.members.list(true).firstOrNull { it.id == id }?.displayName } }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(listOfNotNull(v.modelYear?.toString(), v.make, v.model, v.trimLevel).joinToString(" ").ifBlank { v.name }, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(model.t("fuelType.${v.fuelType}"), v.colour, v.plate?.let { model.t("vehicles.plateIs", it) }, driver?.let { model.t("vehicles.driverIs", it) })
                        .joinToString(" · "),
                )
                v.vin?.let { Text(model.t("vehicles.vinIs", it), style = MaterialTheme.typography.bodySmall) }
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
        if (v.purchaseDate != null || v.purchasePrice != null) {
            Text(
                model.t("vehicles.purchaseLine", v.purchaseDate?.let(model::date) ?: "—", v.purchasePrice?.let(model::money) ?: "—", v.seller ?: "—",
                    v.purchaseOdometer?.let { km(model, it) } ?: "—"),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (v.status != VehicleStatus.ACTIVE) {
            Text(model.t("vehicles.disposalLine", model.t("vehicleStatus.${v.status}"), v.disposalDate?.let(model::date) ?: "—", v.disposalPrice?.let(model::money) ?: "—"))
        }
        v.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text(model.t("vehicles.readings"), style = MaterialTheme.typography.titleSmall)
        for (r in readings.reversed().take(12)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.date(r.date), Modifier.width(110.dp))
                Text(km(model, r.odometer), Modifier.width(140.dp))
                Text(model.t("readingSource.${r.source}"), Modifier.width(160.dp), style = MaterialTheme.typography.bodySmall)
                r.id?.let { id -> TextButton(onClick = { model.act { books.vehicles.deleteReading(v.id, id) } }) { Text(model.t("common.delete")) } }
            }
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
    val year = remember(model.revision, v.id) { books.vehicles.fuelStats(v.id, LocalDate(today.year - 1, today.month, 1), today) }
    val unit = model.t(if (v.electric) "vehicles.kwh" else "vehicles.litres")
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    year.per100km?.let { model.t(if (v.electric) "vehicles.consumptionKwh" else "vehicles.consumption", MoneyFormat.formatDecimal(it, model.language.locale)) }
                        ?: model.t("vehicles.consumptionUnknown"),
                    fontWeight = FontWeight.Medium,
                )
                year.costPerKm?.let { Text(model.t("vehicles.fuelPerKm", model.money(it)), style = MaterialTheme.typography.bodySmall) }
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
                    Text("${MoneyFormat.formatDecimal(e.quantity, model.language.locale)} $unit" + if (!e.fullTank) " (${model.t("vehicles.partial")})" else "", Modifier.width(180.dp))
                    Text(listOfNotNull(e.station, e.transactionId?.let { model.t("vehicles.paymentLinked") }).joinToString(" · "), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
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
            Text(model.t("vehicles.warrantyExplain"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
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
        if (allYears && cost.costs.byYear.size > 1) {
            Text(model.t("costs.byYear"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            for ((year, total) in cost.costs.byYear) Row { Text(year.toString(), Modifier.width(120.dp)); Text(model.money(total)) }
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
    var notes by remember { mutableStateOf(existing.notes.orEmpty()) }
    var groupId by remember { mutableStateOf(existing.groupId) }
    var confirmDelete by remember { mutableStateOf(false) }

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
            Picker(model.t("vehicles.driver"), listOf(null) + people, people.firstOrNull { it.id == driverId }, { it?.displayName ?: model.t("common.none") }) { driverId = it?.id }
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
            if (existing.id.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Picker(model.t("goals.status"), VehicleStatus.entries, status, { model.t("vehicleStatus.$it") }, Modifier.weight(1f)) { status = it }
                    if (status != VehicleStatus.ACTIVE) {
                        DateInput(model.t("vehicles.disposalDate"), disposalDate, Modifier.weight(1f)) { disposalDate = it }
                        AmountInput(model.t("vehicles.disposalPrice"), disposalPrice, c, locale, Modifier.weight(1f), model::money) { disposalPrice = it }
                    }
                }
            }
            TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
            GroupPicker(model, groupId, enabled = existing.id.isBlank()) { groupId = it.id }
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
    FormDialog(
        model.t(if (existing.id.isBlank()) "vehicles.addTask" else "vehicles.editTask"), model.t("common.save"), model.t("common.cancel"),
        canSave = name.isNotBlank(), onDismiss = onClose,
        onSave = {
            val ok = model.act {
                model.books.vehicles.saveTask(
                    existing.copy(
                        name = name, intervalMonths = optionalInt(months), intervalKm = optionalInt(kms), startDate = optionalDate(start),
                        startOdometer = optionalInt(startOdo), remindDays = optionalInt(remindDays) ?: 14, remindKm = optionalInt(remindKm) ?: 500,
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
                TextButton(onClick = { if (model.act { model.books.vehicles.deleteTask(existing.vehicleId, existing.id) } != null) onClose() }) {
                    Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error)
                }
            }
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
                TextButton(onClick = { if (model.act { books.vehicles.deleteService(v.id, existing.id) } != null) onClose() }) {
                    Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error)
                }
            }
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
    FormDialog(
        model.t(if (v.electric) "vehicles.addCharge" else "vehicles.addFuel") + " · " + v.name, model.t("common.save"), model.t("common.cancel"),
        onDismiss = onClose,
        onSave = {
            val ok = model.act {
                val q = runCatching { MoneyFormat.parseDecimal(quantity, locale) }.getOrElse { throw ValidationException("error.fuelQuantity") }
                books.vehicles.saveFuel(
                    existing.copy(date = requiredDate(date), odometer = optionalInt(odometer), quantity = q, cost = parseAmount(cost, v.currency, locale), fullTank = full, station = station),
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t(if (v.electric) "vehicles.kwhField" else "vehicles.litresField"), quantity, Modifier.weight(1f)) { quantity = it }
                AmountInput(model.t("vehicles.cost"), cost, v.currency, locale, Modifier.weight(1f), model::money) { cost = it }
            }
            LabeledCheckbox(model.t(if (v.electric) "vehicles.fullCharge" else "vehicles.fullTank"), full) { full = it }
            Text(model.t("vehicles.fullTank.hint"), style = MaterialTheme.typography.bodySmall)
            TextInput(model.t("vehicles.station"), station) { station = it }
            if (existing.transactionId == null) {
                PaymentFields(model, v.currency, if (v.electric) "transport.ev_charging" else "transport.fuel", payment)
            } else {
                Text(model.t("vehicles.paymentLinked"), style = MaterialTheme.typography.bodySmall)
            }
            if (existing.id.isNotBlank()) {
                TextButton(onClick = { if (model.act { books.vehicles.deleteFuel(v.id, existing.id) } != null) onClose() }) {
                    Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error)
                }
            }
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
            TextButton(onClick = { if (model.act { model.books.vehicles.deleteWarranty(existing.vehicleId, existing.id) } != null) onClose() }) {
                Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error)
            }
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
