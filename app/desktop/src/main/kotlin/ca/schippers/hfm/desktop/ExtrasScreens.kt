package ca.schippers.hfm.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Account
import ca.schippers.hfm.books.AssetKind
import ca.schippers.hfm.books.CardReward
import ca.schippers.hfm.books.Contractor
import ca.schippers.hfm.books.ContractorJob
import ca.schippers.hfm.books.HomeProject
import ca.schippers.hfm.books.Invoice
import ca.schippers.hfm.books.InvoiceLine
import ca.schippers.hfm.books.InvoiceStatus
import ca.schippers.hfm.books.InvoiceTax
import ca.schippers.hfm.books.ProjectStatus
import ca.schippers.hfm.books.RentalProperty
import ca.schippers.hfm.books.RewardKind
import ca.schippers.hfm.books.RewardUnit
import ca.schippers.hfm.books.Trip
import ca.schippers.hfm.books.TripPurpose
import ca.schippers.hfm.books.TripService
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.books.VehicleStatus
import ca.schippers.hfm.calc.salestax.SalesTaxes
import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.datetime.LocalDate
import java.io.File
import java.math.BigDecimal
import javax.swing.JFileChooser

private fun date(text: String): LocalDate = runCatching { LocalDate.parse(text.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }

private fun optionalDate(text: String): LocalDate? = text.trim().ifEmpty { null }?.let(::date)

private fun decimal(text: String, error: String): BigDecimal = text.trim().replace(',', '.').toBigDecimalOrNull() ?: throw ValidationException(error)

private fun BooksModel.workGroup(): String = defaultDocumentGroup() ?: books.groups().first().id

private fun BooksModel.memberName(id: String?): String? = books.members.list(includeArchived = true).firstOrNull { it.id == id }?.displayName

// --- Trip log (OTH-02, MED-11) -----------------------------------------------------------------------

/** OTH-02, MED-11: trips by car for work or medical care, each vehicle's work share, and medical travel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripsScreen(model: BooksModel) {
    val books = model.books
    val thisYear = today().year
    var year by remember { mutableStateOf(thisYear) }
    var editing by remember { mutableStateOf<Trip?>(null) }
    var toMedical by remember { mutableStateOf<Trip?>(null) }
    val trips = remember(model.revision, year) { books.trips.list(year) }
    val use = remember(model.revision, year) { books.trips.vehicleUse(year) }
    val vehicles = remember(model.revision) { books.vehicles.list(includeInactive = true).associate { it.id to it.name } }
    val totals = remember(trips) { books.trips.totals(year) }
    // MED-11: the medical trips already added as a medical expense.
    val inMedical = remember(model.revision, trips) { trips.filter { books.trips.qualifiesForMedical(it) && books.trips.medicalExpense(it) != null }.map { it.id }.toSet() }
    fun km(v: BigDecimal) = model.t("trips.km", java.text.NumberFormat.getNumberInstance(model.language.locale).apply { maximumFractionDigits = 1 }.format(v))
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(model.t("nav.trips"), style = MaterialTheme.typography.titleLarge)
        Text(model.t("trips.hint"), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Picker(model.t("taxes.year"), (thisYear downTo thisYear - 6).toList(), year, { it.toString() }, Modifier.width(190.dp)) { year = it }
            Button(onClick = { editing = Trip("", model.workGroup(), today(), "", BigDecimal.ZERO, true, TripPurpose.BUSINESS) }, modifier = Modifier.padding(top = 8.dp)) {
                Text(model.t("trips.add"))
            }
        }
        if (totals.isNotEmpty() || use.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((member, byPurpose) in totals.entries.groupBy { it.key.first }) {
                    Card {
                        Column(Modifier.padding(12.dp).width(220.dp)) {
                            Text(model.memberName(member) ?: model.t("taxes.household"), fontWeight = FontWeight.Medium)
                            for ((key, v) in byPurpose.sortedBy { it.key.second.ordinal }) Text(model.t("trips.purposeKm", model.t("tripPurpose.${key.second}"), km(v)))
                        }
                    }
                }
                for (u in use) {
                    Card {
                        Column(Modifier.padding(12.dp).width(220.dp)) {
                            Text(vehicles[u.vehicleId].orEmpty(), fontWeight = FontWeight.Medium)
                            Text(model.t("trips.workKm", km(u.workKm)))
                            Text(u.totalKm?.let { model.t("trips.totalKm", km(BigDecimal(it))) } ?: model.t("trips.noOdometer"), style = MaterialTheme.typography.bodySmall)
                            u.workPercent?.let { Text(model.t("trips.workShare", it), fontWeight = FontWeight.Medium) }
                        }
                    }
                }
            }
        }
        if (trips.isEmpty()) Text(model.t("trips.none"))
        for (t in trips) {
            Row(Modifier.fillMaxWidth().clickable { editing = t }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(model.date(t.date), Modifier.width(100.dp))
                Column(Modifier.weight(1f)) {
                    Text(listOfNotNull(t.origin, t.destination).joinToString(" → ") + if (t.roundTrip) " ↺" else "")
                    Text(
                        listOfNotNull(model.t("tripPurpose.${t.purpose}"), model.memberName(t.memberId), t.vehicleId?.let(vehicles::get), t.notes).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (t.id in inMedical) {
                    TextButton(onClick = {}, enabled = false) { Text(model.t("trips.addedToMedical")) }
                } else if (books.trips.qualifiesForMedical(t)) {
                    TextButton(onClick = { toMedical = t }) { Text(model.t("trips.toMedical")) }
                }
                Text(km(t.km), Modifier.width(110.dp))
            }
            HorizontalDivider()
        }
    }
    editing?.let { t -> TripDialog(model, t) { editing = null } }
    toMedical?.let { t -> TripMedicalDialog(model, t) { toMedical = null } }
}

@Composable
private fun TripDialog(model: BooksModel, t: Trip, onClose: () -> Unit) {
    val members = remember { model.books.members.list() }
    // The vehicles in use, and the trip's own vehicle even if sold or retired since.
    val vehicles = remember { model.books.vehicles.list(includeInactive = true).filter { it.status == VehicleStatus.ACTIVE || it.id == t.vehicleId } }
    var day by remember { mutableStateOf(t.date.toString()) }
    var origin by remember { mutableStateOf(t.origin.orEmpty()) }
    var destination by remember { mutableStateOf(t.destination) }
    var km by remember { mutableStateOf(if (t.kmOneWay.signum() == 0) "" else t.kmOneWay.stripTrailingZeros().toPlainString()) }
    var round by remember { mutableStateOf(t.roundTrip) }
    var purpose by remember { mutableStateOf(t.purpose) }
    var member by remember { mutableStateOf(members.firstOrNull { it.id == t.memberId }) }
    // A new trip proposes the first vehicle in use; a saved one keeps its own, or none.
    var vehicle by remember { mutableStateOf(if (t.id.isBlank()) vehicles.firstOrNull { it.status == VehicleStatus.ACTIVE } else vehicles.firstOrNull { it.id == t.vehicleId }) }
    var notes by remember { mutableStateOf(t.notes.orEmpty()) }
    var asking by remember { mutableStateOf(false) }
    FormDialog(model.t(if (t.id.isBlank()) "trips.add" else "trips.edit"), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            model.books.trips.save(t.copy(date = date(day), origin = origin, destination = destination, kmOneWay = decimal(km, "error.tripDistance"), roundTrip = round, purpose = purpose, memberId = member?.id, vehicleId = vehicle?.id, notes = notes))
        }
        if (ok != null) onClose()
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t("report.date"), day, Modifier.width(170.dp)) { day = it }
            Picker(model.t("trips.purpose"), TripPurpose.entries, purpose, { model.t("tripPurpose.$it") }, Modifier.weight(1f)) { purpose = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("trips.from"), origin, Modifier.weight(1f)) { origin = it }
            TextInput(model.t("trips.to"), destination, Modifier.weight(1f)) { destination = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextInput(model.t("trips.kmOneWay"), km, Modifier.width(170.dp)) { km = it }
            LabeledCheckbox(model.t("trips.roundTrip"), round) { round = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker(model.t("report.person"), listOf(null) + members, member, { it?.displayName ?: model.t("taxes.household") }, Modifier.weight(1f)) { member = it }
            Picker(model.t("trips.vehicle"), listOf(null) + vehicles, vehicle, { it?.name ?: model.t("trips.noVehicle") }, Modifier.weight(1f)) { vehicle = it }
        }
        TextInput(model.t("calendar.notes"), notes) { notes = it }
        if (purpose == TripPurpose.MEDICAL) Text(model.t("trips.medicalHint", TripService.MEDICAL_MIN_KM), style = MaterialTheme.typography.bodySmall)
        if (t.id.isNotBlank()) TextButton(onClick = { asking = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
    }
    if (asking) {
        AskBeforeDeleting(model, model.t("trips.delete.body", model.date(t.date), t.destination), onDismiss = { asking = false }) {
            (model.act { model.books.trips.delete(t) } != null).also { if (it) onClose() }
        }
    }
}

/** MED-11: a qualifying medical trip as a medical expense, at the rate per kilometre the user enters. */
@Composable
private fun TripMedicalDialog(model: BooksModel, t: Trip, onClose: () -> Unit) {
    val locale = model.language.locale
    val members = remember { model.books.members.list() }
    var member by remember { mutableStateOf(members.firstOrNull { it.id == t.memberId } ?: members.firstOrNull()) }
    var rate by remember { mutableStateOf(model.books.trips.medicalRate(t.date.year)?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    FormDialog(model.t("trips.toMedical"), model.t("common.save"), model.t("common.cancel"), canSave = member != null && rate.isNotBlank(), onDismiss = onClose, onSave = {
        val ok = model.act {
            val r = parseAmount(rate, model.books.reports.base, locale)?.abs() ?: throw ValidationException("error.tripRate")
            model.books.trips.setMedicalRate(t.date.year, r)
            model.books.trips.addToMedical(t, member!!.id, r, model.workGroup())
        }
        if (ok != null) onClose()
    }) {
        Text("${model.date(t.date)} · ${t.destination}", fontWeight = FontWeight.Medium)
        Text(model.t("trips.medicalRateHint", t.date.year), style = MaterialTheme.typography.bodySmall)
        Picker(model.t("report.person"), members, member, { it.displayName }) { member = it }
        AmountInput(model.t("trips.ratePerKm"), rate, model.books.reports.base, locale, Modifier.fillMaxWidth(), model::money) { rate = it }
    }
}

// --- Contractors (MNT-08) ----------------------------------------------------------------------------

@Composable
internal fun ContractorsTab(model: BooksModel) {
    val books = model.books
    var showArchived by remember { mutableStateOf(false) }
    val list = remember(model.revision, showArchived) { books.contractors.list(showArchived) }
    var editing by remember { mutableStateOf<Contractor?>(null) }
    var jobsOf by remember { mutableStateOf<String?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { editing = Contractor("", model.workGroup(), "") }) { Text(model.t("contractor.add")) }
        LabeledCheckbox(model.t("contractor.showArchived"), showArchived) { showArchived = it }
    }
    if (list.isEmpty()) Text(model.t("contractor.none"), Modifier.padding(vertical = 8.dp))
    Column(Modifier.verticalScroll(rememberScrollState())) {
        for (c in list) {
            Row(Modifier.fillMaxWidth().clickable { jobsOf = c.id }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name + (c.trade?.let { " · $it" } ?: ""), fontWeight = FontWeight.Medium)
                    Text(listOfNotNull(c.phone, c.email, c.website).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
                Text(c.rating?.let { "★ ${it.toPlainString()}" } ?: "", Modifier.width(70.dp))
                Text(model.t("contractor.jobs", c.jobs.size), Modifier.width(110.dp))
                TextButton(onClick = { editing = c }) { Text(model.t("contractor.edit")) }
            }
            HorizontalDivider()
        }
    }
    editing?.let { c -> ContractorDialog(model, c) { editing = null } }
    jobsOf?.let { id -> list.firstOrNull { it.id == id }?.let { c -> ContractorJobsDialog(model, c) { jobsOf = null } } }
}

@Composable
private fun ContractorDialog(model: BooksModel, c: Contractor, onClose: () -> Unit) {
    var name by remember { mutableStateOf(c.name) }
    var trade by remember { mutableStateOf(c.trade.orEmpty()) }
    var phone by remember { mutableStateOf(c.phone.orEmpty()) }
    var email by remember { mutableStateOf(c.email.orEmpty()) }
    var website by remember { mutableStateOf(c.website.orEmpty()) }
    var notes by remember { mutableStateOf(c.notes.orEmpty()) }
    var archived by remember { mutableStateOf(c.archived) }
    FormDialog(model.t(if (c.id.isBlank()) "contractor.add" else "contractor.edit"), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        if (model.act { model.books.contractors.save(c.copy(name = name, trade = trade, phone = phone, email = email, website = website, notes = notes, archived = archived)) } != null) onClose()
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("contractor.name"), name, Modifier.weight(1f)) { name = it }
            TextInput(model.t("contractor.trade"), trade, Modifier.weight(1f)) { trade = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("estate.phone"), phone, Modifier.weight(1f)) { phone = it }
            TextInput(model.t("estate.email"), email, Modifier.weight(1f)) { email = it }
        }
        TextInput(model.t("contractor.website"), website) { website = it }
        TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
        if (c.id.isNotBlank()) LabeledCheckbox(model.t("contractor.archive"), archived) { archived = it }
    }
}

@Composable
private fun ContractorJobsDialog(model: BooksModel, c: Contractor, onClose: () -> Unit) {
    val locale = model.language.locale
    val homes = remember { model.books.assets.list() }
    var day by remember { mutableStateOf(today().toString()) }
    var description by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf<Int?>(null) }
    var asset by remember { mutableStateOf<ca.schippers.hfm.books.Asset?>(null) }
    var deleting by remember { mutableStateOf<ContractorJob?>(null) }
    FormDialog(c.name, model.t("contractor.addJob"), model.t("common.close"), canSave = description.isNotBlank(), onDismiss = onClose, onSave = {
        val ok = model.act {
            model.books.contractors.saveJob(c, ContractorJob("", date(day), description, parseAmount(cost, model.books.reports.base, locale)?.abs(), rating, asset?.id))
        }
        if (ok != null) onClose()
    }) {
        for (j in c.jobs) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.date(j.date), Modifier.width(100.dp))
                Text(j.description + (j.rating?.let { " · " + "★".repeat(it) } ?: ""), Modifier.weight(1f))
                j.cost?.let { MoneyText(model, it) }
                TextButton(onClick = { deleting = j }) { Text("✕") }
            }
        }
        Text(model.t("contractor.newJob"), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t("report.date"), day, Modifier.width(170.dp)) { day = it }
            TextInput(model.t("contractor.job"), description, Modifier.weight(1f)) { description = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountInput(model.t("contractor.cost"), cost, model.books.reports.base, locale, Modifier.weight(1f), model::money) { cost = it }
            Picker(model.t("contractor.rating"), listOf(null, 1, 2, 3, 4, 5), rating, { it?.let { n -> "★".repeat(n) } ?: model.t("contractor.notRated") }, Modifier.weight(1f)) { rating = it }
        }
        if (homes.isNotEmpty()) Picker(model.t("contractor.asset"), listOf(null) + homes, asset, { it?.name ?: model.t("contractor.noAsset") }) { asset = it }
    }
    deleting?.let { j ->
        AskBeforeDeleting(model, model.t("contractor.deleteJob.body", j.description, model.date(j.date)), onDismiss = { deleting = null }) {
            (model.act { model.books.contractors.deleteJob(c, j.id) } != null).also { if (it) onClose() }
        }
    }
}

// --- Home projects (MNT-09) --------------------------------------------------------------------------

@Composable
internal fun ProjectsTab(model: BooksModel) {
    val books = model.books
    val projects = remember(model.revision) { books.homeProjects.list() }
    val homes = remember(model.revision) { books.assets.list().filter { it.kind == AssetKind.HOME || it.kind == AssetKind.COTTAGE } }
    var editing by remember { mutableStateOf<HomeProject?>(null) }
    var costsOf by remember { mutableStateOf<String?>(null) }
    Button(onClick = { editing = HomeProject("", model.workGroup(), "", ProjectStatus.PLANNED, books.reports.base, homes.firstOrNull()?.id) }) { Text(model.t("project.add")) }
    Text(model.t("project.hint"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
    Column(Modifier.verticalScroll(rememberScrollState())) {
        for (h in homes) {
            val base = books.homeProjects.costBase(h.id)
            if (base.improvements.isZero && base.purchase == null) continue
            Text(
                model.t("project.costBase", h.name, base.total?.let(model::money) ?: "—", model.money(base.improvements)),
                fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        if (projects.isEmpty()) Text(model.t("project.none"))
        for (p in projects) {
            Row(Modifier.fillMaxWidth().clickable { costsOf = p.id }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.Medium)
                    Text(
                        listOfNotNull(
                            model.t("projectStatus.${p.status}"), homes.firstOrNull { it.id == p.assetId }?.name,
                            model.t(if (p.capital) "project.capital" else "project.repair"),
                            p.budget?.let { model.t("project.budgetOf", model.money(it)) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val budget = p.budget
                val over = budget != null && p.spent.minorUnits > budget.minorUnits
                Text(model.money(p.spent), color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                TextButton(onClick = { editing = p }) { Text(model.t("project.edit")) }
            }
            HorizontalDivider()
        }
    }
    editing?.let { p -> ProjectDialog(model, p, homes) { editing = null } }
    costsOf?.let { id -> projects.firstOrNull { it.id == id }?.let { p -> ProjectCostsDialog(model, p) { costsOf = null } } }
}

@Composable
private fun ProjectDialog(model: BooksModel, p: HomeProject, homes: List<ca.schippers.hfm.books.Asset>, onClose: () -> Unit) {
    val locale = model.language.locale
    var name by remember { mutableStateOf(p.name) }
    var status by remember { mutableStateOf(p.status) }
    var home by remember { mutableStateOf(homes.firstOrNull { it.id == p.assetId }) }
    var start by remember { mutableStateOf(p.start?.toString().orEmpty()) }
    var end by remember { mutableStateOf(p.end?.toString().orEmpty()) }
    var budget by remember { mutableStateOf(p.budget?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var capital by remember { mutableStateOf(p.capital) }
    var notes by remember { mutableStateOf(p.notes.orEmpty()) }
    var asking by remember { mutableStateOf(false) }
    FormDialog(model.t(if (p.id.isBlank()) "project.add" else "project.edit"), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            model.books.homeProjects.save(p.copy(name = name, status = status, assetId = home?.id, start = optionalDate(start), end = optionalDate(end), budget = parseAmount(budget, p.currency, locale)?.abs(), capital = capital, notes = notes))
        }
        if (ok != null) onClose()
    }) {
        TextInput(model.t("project.name"), name) { name = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker(model.t("project.status"), ProjectStatus.entries, status, { model.t("projectStatus.$it") }, Modifier.weight(1f)) { status = it }
            Picker(model.t("project.home"), listOf(null) + homes, home, { it?.name ?: model.t("contractor.noAsset") }, Modifier.weight(1f)) { home = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t("project.start"), start, Modifier.weight(1f)) { start = it }
            DateInput(model.t("project.end"), end, Modifier.weight(1f)) { end = it }
        }
        AmountInput(model.t("project.budget"), budget, p.currency, locale, Modifier.fillMaxWidth(), model::money) { budget = it }
        LabeledCheckbox(model.t("project.capitalCheck"), capital) { capital = it }
        Text(model.t("project.capitalHint"), style = MaterialTheme.typography.bodySmall)
        TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
        if (p.id.isNotBlank()) TextButton(onClick = { asking = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
    }
    if (asking) {
        AskBeforeDeleting(model, model.t("project.delete.body", p.name), onDismiss = { asking = false }) {
            (model.act { model.books.homeProjects.delete(p) } != null).also { if (it) onClose() }
        }
    }
}

@Composable
private fun ProjectCostsDialog(model: BooksModel, p: HomeProject, onClose: () -> Unit) {
    val locale = model.language.locale
    val contractors = remember { model.books.contractors.list() }
    var day by remember { mutableStateOf(today().toString()) }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var contractor by remember { mutableStateOf<Contractor?>(null) }
    var deleting by remember { mutableStateOf<ca.schippers.hfm.books.ProjectCost?>(null) }
    FormDialog(p.name, model.t("project.addCost"), model.t("common.close"), canSave = amount.isNotBlank(), onDismiss = onClose, onSave = {
        val ok = model.act {
            model.books.homeProjects.addCost(p, date(day), description, parseAmount(amount, p.currency, locale) ?: throw ValidationException("error.projectCost"), contractor?.id)
        }
        if (ok != null) onClose()
    }) {
        Text(model.t("project.spent", model.money(p.spent), p.budget?.let(model::money) ?: "—"), fontWeight = FontWeight.Medium)
        for (c in p.costs) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.date(c.date), Modifier.width(100.dp))
                Text(c.description + (contractors.firstOrNull { it.id == c.contractorId }?.let { " · ${it.name}" } ?: ""), Modifier.weight(1f))
                MoneyText(model, c.amount)
                TextButton(onClick = { deleting = c }) { Text("✕") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t("report.date"), day, Modifier.width(170.dp)) { day = it }
            TextInput(model.t("share.description"), description, Modifier.weight(1f)) { description = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountInput(model.t("share.amount"), amount, p.currency, locale, Modifier.weight(1f), model::money) { amount = it }
            if (contractors.isNotEmpty()) Picker(model.t("project.contractor"), listOf(null) + contractors, contractor, { it?.name ?: "—" }, Modifier.weight(1f)) { contractor = it }
        }
    }
    deleting?.let { c ->
        AskBeforeDeleting(model, model.t("project.deleteCost.body", c.description, model.money(c.amount)), onDismiss = { deleting = null }) {
            (model.act { model.books.homeProjects.deleteCost(p, c.id) } != null).also { if (it) onClose() }
        }
    }
}

// --- Side income: invoices (SAL-04) and rental properties (SAL-05) -----------------------------------

private enum class SideTab { INVOICES, RENTALS }

/** SAL-04, SAL-05: invoices for side income, and rental properties' income and expenses. */
@Composable
fun SideIncomeScreen(model: BooksModel) {
    var tab by remember { mutableStateOf(SideTab.INVOICES) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(model.t("nav.side"), style = MaterialTheme.typography.titleLarge)
        Text(model.t("side.hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        PrimaryTabRow(selectedTabIndex = tab.ordinal, modifier = Modifier.padding(vertical = 8.dp)) {
            for (t in SideTab.entries) Tab(selected = tab == t, onClick = { tab = t }, text = { Text(model.t("side.tab.$t")) })
        }
        when (tab) {
            SideTab.INVOICES -> InvoicesTab(model)
            SideTab.RENTALS -> RentalsTab(model)
        }
    }
}

@Composable
private fun InvoicesTab(model: BooksModel) {
    val books = model.books
    val invoices = remember(model.revision) { books.invoices.list() }
    var editing by remember { mutableStateOf<Invoice?>(null) }
    var paying by remember { mutableStateOf<Invoice?>(null) }
    val outstanding = invoices.filter { it.status == InvoiceStatus.SENT }
    // SAL-04: what is waiting to be paid, one total per currency.
    val waiting = remember(invoices) { books.invoices.outstanding() }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { editing = Invoice("", model.workGroup(), books.invoices.nextNumber(today().year), "", today(), books.reports.base, listOf(InvoiceLine("", "1", ""))) }) {
            Text(model.t("invoice.add"))
        }
        if (outstanding.isNotEmpty()) Text(model.t("invoice.outstanding", outstanding.size, waiting.joinToString(" + ") { model.money(it) }))
    }
    if (invoices.isEmpty()) Text(model.t("invoice.none"), Modifier.padding(vertical = 8.dp))
    Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
        for (i in invoices) {
            Row(Modifier.fillMaxWidth().clickable { editing = i }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(i.number, Modifier.width(100.dp), fontWeight = FontWeight.Medium)
                Column(Modifier.weight(1f)) {
                    Text(i.customer)
                    Text(
                        listOfNotNull(model.date(i.issueDate), i.dueDate?.let { model.t("invoice.dueOn", model.date(it)) }, i.paidDate?.let { model.t("invoice.paidOn", model.date(it)) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    model.t(if (i.overdue(today())) "invoice.overdue" else "invoiceStatus.${i.status}"),
                    Modifier.width(110.dp),
                    color = if (i.overdue(today())) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                MoneyText(model, i.total, modifier = Modifier.width(120.dp))
                TextButton(onClick = { model.act { saveInvoicePdf(model, i) } }) { Text(model.t("invoice.pdf")) }
                Box(Modifier.width(140.dp)) {
                    if (i.status == InvoiceStatus.SENT || i.status == InvoiceStatus.DRAFT) TextButton(onClick = { paying = i }) { Text(model.t("invoice.markPaid")) }
                }
            }
            HorizontalDivider()
        }
    }
    editing?.let { i -> InvoiceDialog(model, i) { editing = null } }
    paying?.let { i -> InvoicePaidDialog(model, i) { paying = null } }
}

private data class LineRow(val description: String, val quantity: String, val price: String)

@Composable
private fun InvoiceDialog(model: BooksModel, i: Invoice, onClose: () -> Unit) {
    val locale = model.language.locale
    val members = remember { model.books.members.list() }
    var number by remember { mutableStateOf(i.number) }
    // SAL-04: a new invoice's number follows the year of its issue date, as long as it is the one proposed.
    var proposed by remember { mutableStateOf(i.number.takeIf { i.id.isBlank() }) }
    var customer by remember { mutableStateOf(i.customer) }
    var details by remember { mutableStateOf(i.customerDetails.orEmpty()) }
    var issue by remember { mutableStateOf(i.issueDate.toString()) }
    var due by remember { mutableStateOf(i.dueDate?.toString().orEmpty()) }
    var status by remember { mutableStateOf(i.status) }
    var member by remember { mutableStateOf(members.firstOrNull { it.id == i.memberId }) }
    // The sales taxes: the GST or HST, and the province's own (QST, PST or RST), in percent as typed.
    val federalOf = { t: List<InvoiceTax> -> t.firstOrNull { it.name == "GST" || it.name == "HST" } }
    val provincialOf = { t: List<InvoiceTax> -> t.firstOrNull { it.name == "QST" || it.name == "PST" || it.name == "RST" } }
    var gst by remember { mutableStateOf(federalOf(i.taxes)?.percent?.toPlainString().orEmpty()) }
    var gstName by remember { mutableStateOf(federalOf(i.taxes)?.name ?: "GST") }
    var qst by remember { mutableStateOf(provincialOf(i.taxes)?.percent?.toPlainString().orEmpty()) }
    var qstName by remember { mutableStateOf(provincialOf(i.taxes)?.name ?: SalesTaxes.provincialLabel(model.books.provinceOf(i.memberId))) }
    // The rates in effect on the issue date in the province of the person invoicing (Rates and rules).
    val province = model.books.provinceOf(member?.id)
    val issued = runCatching { LocalDate.parse(issue.trim()) }.getOrNull()
    val inEffect = issued?.let { SalesTaxes.ratesOn(it, province) }.orEmpty()
    fun useRatesInEffect() {
        val proposed = inEffect.map { InvoiceTax(it.label, it.rate, it.onGst) }
        gst = federalOf(proposed)?.percent?.toPlainString().orEmpty()
        gstName = federalOf(proposed)?.name ?: "GST"
        qst = provincialOf(proposed)?.percent?.toPlainString().orEmpty()
        qstName = provincialOf(proposed)?.name ?: SalesTaxes.provincialLabel(province)
    }
    // A new invoice of someone who charged sales tax before follows the rates in effect, until a rate is typed.
    var following by remember { mutableStateOf(i.id.isBlank() && model.books.invoices.list().filter { it.memberId == i.memberId }.maxByOrNull { it.issueDate }?.taxes?.isNotEmpty() == true) }
    LaunchedEffect(following, issued, province) { if (following) useRatesInEffect() }
    var notes by remember { mutableStateOf(i.notes.orEmpty()) }
    val lines = remember { mutableStateListOf<LineRow>().apply { i.lines.forEach { add(LineRow(it.description, it.quantity, it.unitPrice)) } } }
    var asking by remember { mutableStateOf(false) }
    FormDialog(model.t(if (i.id.isBlank()) "invoice.add" else "invoice.edit"), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            // Exact rates: 9.975 % stays 9.975 %.
            fun percent(text: String) = text.trim().ifEmpty { null }?.let { decimal(it, "error.salesTaxRate").also { p -> if (p.signum() < 0 || p > BigDecimal(100)) throw ValidationException("error.salesTaxRate") } }
            val onGst = inEffect.firstOrNull { it.label == qstName }?.onGst ?: false
            val taxes = listOfNotNull(percent(gst)?.let { InvoiceTax.ofPercent(gstName, it) }, percent(qst)?.let { InvoiceTax.ofPercent(qstName, it, onGst) })
            model.books.invoices.save(
                i.copy(
                    number = number, customer = customer, customerDetails = details, issueDate = date(issue), dueDate = optionalDate(due), status = status, memberId = member?.id,
                    lines = lines.filter { it.description.isNotBlank() }.map { InvoiceLine(it.description.trim(), it.quantity.trim().replace(',', '.'), it.price.trim().replace(',', '.').replace(Regex("[^0-9.-]"), "")) },
                    taxes = taxes, notes = notes,
                ),
            )
        }
        if (ok != null) onClose()
    }) {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("invoice.number"), number, Modifier.width(140.dp)) { number = it }
                TextInput(model.t("invoice.customer"), customer, Modifier.weight(1f)) { customer = it }
            }
            TextInput(model.t("invoice.customerDetails"), details, singleLine = false) { details = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(model.t("invoice.issued"), issue, Modifier.weight(1f)) {
                    issue = it
                    val year = runCatching { LocalDate.parse(it.trim()).year }.getOrNull()
                    if (year != null && proposed != null && number == proposed) {
                        proposed = model.books.invoices.nextNumber(year)
                        number = proposed!!
                    }
                }
                DateInput(model.t("invoice.due"), due, Modifier.weight(1f)) { due = it }
                Picker(model.t("invoice.status"), InvoiceStatus.entries, status, { model.t("invoiceStatus.$it") }, Modifier.weight(1f)) { status = it }
            }
            Picker(model.t("invoice.from"), listOf(null) + members, member, { it?.displayName ?: model.t("taxes.household") }) { member = it }
            Text(model.t("invoice.lines"), style = MaterialTheme.typography.titleSmall)
            lines.forEachIndexed { n, l ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextInput(model.t("share.description"), l.description, Modifier.weight(1f)) { lines[n] = l.copy(description = it) }
                    TextInput(model.t("invoice.quantity"), l.quantity, Modifier.width(90.dp)) { lines[n] = l.copy(quantity = it) }
                    TextInput(model.t("invoice.price"), l.price, Modifier.width(110.dp)) { lines[n] = l.copy(price = it) }
                    TextButton(onClick = { lines.removeAt(n) }, enabled = lines.size > 1) { Text("✕") }
                }
            }
            TextButton(onClick = { lines.add(LineRow("", "1", "")) }) { Text(model.t("invoice.addLine")) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Picker(model.t("invoice.salesTax"), listOf("GST", "HST"), gstName, { model.t("taxName.$it") }, Modifier.width(120.dp)) { gstName = it }
                TextInput(model.t("invoice.ratePercent"), gst, Modifier.width(120.dp)) { gst = it; following = false }
                Picker(model.t("invoice.provincialTax"), listOf("QST", "PST", "RST"), qstName, { model.t("taxName.$it") }, Modifier.width(120.dp)) { qstName = it }
                TextInput(model.t("invoice.ratePercent"), qst, Modifier.width(120.dp)) { qst = it; following = false }
            }
            if (issued != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(salesTaxesInEffect(model, issued, province, inEffect), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { useRatesInEffect(); following = true }) { Text(model.t("invoice.useRatesInEffect")) }
                }
            }
            Text(model.t("invoice.taxHint"), style = MaterialTheme.typography.bodySmall)
            TextInput(model.t("invoice.notes"), notes, singleLine = false) { notes = it }
            if (i.id.isNotBlank()) TextButton(onClick = { asking = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (asking) InvoiceDeleteDialog(model, i, onDismiss = { asking = false }, onDeleted = onClose)
}

/** SAL-04: asks before deleting an invoice and, when its deposit is still in the books, whether it goes too. */
@Composable
private fun InvoiceDeleteDialog(model: BooksModel, i: Invoice, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val books = model.books
    val deposit = remember { books.invoices.deposit(i) }
    val accounts = remember { books.accounts.list(includeClosed = true).associate { it.account.id to it.account.name } }
    var withDeposit by remember { mutableStateOf(false) }
    AskBeforeDeleting(
        model, model.t("invoice.delete.body", i.number, i.customer), onDismiss = onDismiss,
        extra = {
            if (deposit != null) {
                LabeledCheckbox(model.t("invoice.delete.deposit", model.money(deposit.amount), model.date(deposit.date), accounts[deposit.accountId].orEmpty()), withDeposit) { withDeposit = it }
                Text(model.t("invoice.delete.depositHint"), style = MaterialTheme.typography.bodySmall)
            }
        },
    ) {
        // A reconciled deposit is deleted only once the user confirms it.
        (model.act(retryConfirmed = { books.invoices.delete(i, withDeposit = true, confirmReconciled = true); onDeleted() }) { books.invoices.delete(i, withDeposit) } != null)
            .also { if (it) onDeleted() }
    }
}

@Composable
private fun InvoicePaidDialog(model: BooksModel, i: Invoice, onClose: () -> Unit) {
    val accounts = remember { model.books.accounts.list().map { it.account }.filter { it.type.kind == AccountKind.BANK && it.currency == i.currency } }
    var day by remember { mutableStateOf(today().toString()) }
    var account by remember { mutableStateOf(accounts.firstOrNull()) }
    var record by remember { mutableStateOf(accounts.isNotEmpty()) }
    FormDialog(model.t("invoice.markPaid"), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        if (model.act { model.books.invoices.markPaid(i, date(day), account?.id?.takeIf { record }) } != null) onClose()
    }) {
        Text("${i.number} · ${i.customer} · ${model.money(i.total)}", fontWeight = FontWeight.Medium)
        DateInput(model.t("invoice.paidDate"), day) { day = it }
        if (accounts.isNotEmpty()) {
            LabeledCheckbox(model.t("invoice.recordDeposit"), record) { record = it }
            if (record) Picker(model.t("payStub.account"), accounts, account, { it.name }) { account = it }
        }
    }
}

/** SAL-04: asks where to save the invoice's PDF, writes it and opens it. */
private fun saveInvoicePdf(model: BooksModel, i: Invoice) {
    val chooser = JFileChooser().apply { selectedFile = File("${model.t("invoice.fileName")} ${i.number}.pdf") }
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return
    val file = chooser.selectedFile.let { if (it.extension.equals("pdf", true)) it else File(it.path + ".pdf") }
    val from = model.memberName(i.memberId) ?: model.session.core.coreQueries.household().executeAsOne().name
    InvoicePdf.write(i, from, file, { key, args -> model.t(key, *args) }, model::money, model::date)
    runCatching { java.awt.Desktop.getDesktop().open(file) }
}

@Composable
private fun RentalsTab(model: BooksModel) {
    val books = model.books
    val thisYear = today().year
    var year by remember { mutableStateOf(thisYear - if (today().month <= kotlinx.datetime.Month.MARCH) 1 else 0) }
    val properties = remember(model.revision) { books.rentals.list() }
    var editing by remember { mutableStateOf<RentalProperty?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Picker(model.t("taxes.year"), (thisYear downTo thisYear - 6).toList(), year, { it.toString() }, Modifier.width(190.dp)) { year = it }
        Button(onClick = { editing = RentalProperty("", model.workGroup(), "", "") }, modifier = Modifier.padding(top = 8.dp)) { Text(model.t("rental.add")) }
    }
    Text(model.t("rental.hint"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
    if (properties.isEmpty()) Text(model.t("rental.none"))
    Column(Modifier.verticalScroll(rememberScrollState())) {
        for (p in properties) {
            val y = remember(model.revision, p, year) { books.rentals.year(p, year, model.pivotLabels(), model.language == Language.FRENCH) }
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Medium)
                            Text(listOfNotNull(p.address, model.t("rental.tagged", books.tags().firstOrNull { it.id == p.tagId }?.name.orEmpty())).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { editing = p }) { Text(model.t("rental.edit")) }
                    }
                    Text(model.t("rental.income", model.money(y.income.total)))
                    for (r in y.expenses.rows) Text("   ${r.label}: ${model.money(y.expenses.rowTotal(r))}", style = MaterialTheme.typography.bodySmall)
                    Text(model.t("rental.expenses", model.money(y.expenses.total)))
                    Text(model.t("rental.net", model.money(y.net)), fontWeight = FontWeight.Medium)
                    if (p.shareBp < 10_000) Text(model.t("rental.share", BigDecimal(p.shareBp).movePointLeft(2).stripTrailingZeros().toPlainString().replace(".", if (model.language == Language.FRENCH) "," else "."), model.money(y.share)))
                }
            }
        }
    }
    editing?.let { p -> RentalDialog(model, p) { editing = null } }
}

@Composable
private fun RentalDialog(model: BooksModel, p: RentalProperty, onClose: () -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var address by remember { mutableStateOf(p.address.orEmpty()) }
    var share by remember { mutableStateOf(BigDecimal(p.shareBp).movePointLeft(2).stripTrailingZeros().toPlainString()) }
    var notes by remember { mutableStateOf(p.notes.orEmpty()) }
    var asking by remember { mutableStateOf(false) }
    var removeTag by remember { mutableStateOf(false) }
    val tag = remember { model.books.tags().firstOrNull { it.id == p.tagId }?.name }
    FormDialog(model.t(if (p.id.isBlank()) "rental.add" else "rental.edit"), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act { model.books.rentals.save(p.copy(name = name, address = address, shareBp = decimal(share, "error.rentalShare").movePointRight(2).toInt(), notes = notes)) }
        if (ok != null) onClose()
    }) {
        TextInput(model.t("rental.name"), name, supporting = model.t(if (p.id.isBlank()) "rental.nameHint" else "rental.renameHint")) { name = it }
        TextInput(model.t("rental.address"), address) { address = it }
        TextInput(model.t("rental.sharePercent"), share) { share = it }
        TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
        if (p.id.isNotBlank()) TextButton(onClick = { asking = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
    }
    if (asking) {
        AskBeforeDeleting(
            model, model.t("rental.delete.body", p.name), onDismiss = { asking = false },
            extra = { if (tag != null) LabeledCheckbox(model.t("rental.delete.tag", tag), removeTag) { removeTag = it } },
        ) {
            (model.act { model.books.rentals.delete(p, removeTag) } != null).also { if (it) onClose() }
        }
    }
}

// --- Card rewards (CC-03) ----------------------------------------------------------------------------

/** CC-03: a card's rewards program, its balance and worth, and points earned or redeemed. */
@Composable
fun RewardsDialog(model: BooksModel, account: Account, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val program = remember(model.revision) { books.rewards.program(account.id) }
    val status = remember(model.revision) { books.rewards.status(account.id, today()) }
    val entries = remember(model.revision) { books.rewards.entries(account.id) }
    var name by remember { mutableStateOf(program?.program.orEmpty()) }
    var unit by remember { mutableStateOf(program?.unit ?: RewardUnit.POINTS) }
    var rate by remember { mutableStateOf(program?.earnRate?.toPlainString().orEmpty()) }
    var value by remember { mutableStateOf(program?.unitValue?.toPlainString().orEmpty()) }
    var day by remember { mutableStateOf(today().toString()) }
    var units by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(RewardKind.EARNED) }
    var worth by remember { mutableStateOf("") }
    fun n(v: BigDecimal) = String.format(locale, "%,.2f", v.toDouble()).removeSuffix(".00").removeSuffix(",00")
    var deleting by remember { mutableStateOf<ca.schippers.hfm.books.RewardEntry?>(null) }
    FormDialog(model.t("rewards.title", account.name), model.t("common.save"), model.t("common.close"), canSave = name.isNotBlank(), onDismiss = onClose, onSave = {
        val ok = model.act {
            books.rewards.save(CardReward(account.id, account.groupId, name, unit, rate.trim().replace(',', '.').ifEmpty { null }?.toBigDecimalOrNull(), value.trim().replace(',', '.').ifEmpty { null }?.toBigDecimalOrNull()))
            if (units.isNotBlank()) books.rewards.addEntry(account.id, date(day), decimal(units, "error.rewardUnits"), kind, parseAmount(worth, account.currency, locale)?.abs())
        }
        if (ok != null) onClose()
    }) {
        Text(model.t("rewards.hint"), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("rewards.program"), name, Modifier.weight(1f)) { name = it }
            Picker(model.t("rewards.unit"), RewardUnit.entries, unit, { model.t("rewardUnit.$it") }, Modifier.width(170.dp)) { unit = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("rewards.earnRate"), rate, Modifier.weight(1f)) { rate = it }
            TextInput(model.t("rewards.unitValue", account.currency.code), value, Modifier.weight(1f)) { value = it }
        }
        if (program != null) {
            Text(
                listOfNotNull(
                    model.t("rewards.balance", n(status.balance)), status.worth?.let { model.t("rewards.worth", model.money(it)) },
                    status.estimatedThisYear?.let { model.t("rewards.estimate", n(it)) },
                ).joinToString(" · "),
                fontWeight = FontWeight.Medium,
            )
            for (e in entries.take(12)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(model.date(e.date), Modifier.width(100.dp))
                    Text(model.t("rewardKind.${e.kind}") + (e.value?.let { " · ${model.money(it)}" } ?: "") + (e.notes?.let { " · $it" } ?: ""), Modifier.weight(1f))
                    Text(n(if (e.kind == RewardKind.REDEEMED) -e.units else e.units))
                    TextButton(onClick = { deleting = e }) { Text("✕") }
                }
            }
        }
        // A first entry can go in with the program: Save keeps the program, then adds the entry.
        Text(model.t("rewards.newEntry"), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t("report.date"), day, Modifier.width(160.dp)) { day = it }
            Picker(model.t("allowance.kind"), RewardKind.entries, kind, { model.t("rewardKind.$it") }, Modifier.weight(1f)) { kind = it }
            TextInput(model.t("rewards.units"), units, Modifier.width(120.dp)) { units = it }
        }
        if (kind == RewardKind.REDEEMED) AmountInput(model.t("rewards.redeemedFor"), worth, account.currency, locale, Modifier.fillMaxWidth(), model::money) { worth = it }
    }
    deleting?.let { e ->
        AskBeforeDeleting(model, model.t("rewards.deleteEntry.body", model.t("rewardKind.${e.kind}"), n(e.units), model.date(e.date)), onDismiss = { deleting = null }) {
            model.act { books.rewards.deleteEntry(account.id, e.id) } != null
        }
    }
}
