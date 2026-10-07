package ca.schippers.hfm.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Logbook
import ca.schippers.hfm.books.Place
import ca.schippers.hfm.books.PlaceCategory
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.sync.TripRules
import java.math.BigDecimal

/**
 * TRP-02: the saved places. The phone matches its one location fix at Start and Arrive to the
 * nearest place within its radius; places stay on the household's computer and phones.
 */
@Composable
internal fun PlacesDialog(model: BooksModel, onClose: () -> Unit) {
    val books = model.books
    var showArchived by remember { mutableStateOf(false) }
    val places = remember(model.revision, showArchived) { books.places.list(includeArchived = showArchived) }
    var editing by remember { mutableStateOf<Place?>(null) }
    val access = rememberAccess(model)
    WideDialog(model.t("places.title"), model.t("common.close"), onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(model.t("places.hint"), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (access.canCreate) {
                    Button(onClick = {
                        val group = model.editableGroups().let { g -> g.firstOrNull { !it.isPrivate } ?: g.firstOrNull() }
                        if (group == null) model.error = model.t("error.noEditableGroup") else editing = Place("", group.id, "", province = books.province.name)
                    }) { Text(model.t("places.add")) }
                }
                LabeledCheckbox(model.t("places.showArchived"), showArchived) { showArchived = it }
            }
            if (places.isEmpty()) Text(model.t("places.none"))
            for (p in places) {
                Row(Modifier.fillMaxWidth().clickable(enabled = access.mayEdit(p.groupId)) { editing = p }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name + if (p.archived) " (${model.t("places.archived")})" else "", fontWeight = FontWeight.Medium)
                        Text(
                            listOfNotNull(
                                model.t("placeCategory.${p.category}"), p.address,
                                p.latitude?.let { TripRules.coordinates(it, p.longitude!!) + " · " + model.t("places.radiusIs", p.radiusM) },
                                p.province?.let { provinceName(model, it) }, p.deviceId?.let { model.t("places.fromPhone") },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = { editing = p }, enabled = access.mayEdit(p.groupId)) { Text(model.t("common.edit")) }
                }
                HorizontalDivider()
            }
        }
    }
    editing?.let { p -> PlaceDialog(model, p) { editing = null } }
}

@Composable
private fun PlaceDialog(model: BooksModel, existing: Place, onClose: () -> Unit) {
    val locale = model.language.locale
    fun number(d: Double?) = d?.let { String.format(locale, "%.6f", it).trimEnd('0').trimEnd('.', ',') }.orEmpty()
    var name by remember { mutableStateOf(existing.name) }
    var category by remember { mutableStateOf(existing.category) }
    var address by remember { mutableStateOf(existing.address.orEmpty()) }
    var latitude by remember { mutableStateOf(number(existing.latitude)) }
    var longitude by remember { mutableStateOf(number(existing.longitude)) }
    var radius by remember { mutableStateOf(existing.radiusM.toString()) }
    var province by remember { mutableStateOf(existing.province.orEmpty()) }
    var notes by remember { mutableStateOf(existing.notes.orEmpty()) }
    var archived by remember { mutableStateOf(existing.archived) }
    var groupId by remember { mutableStateOf(existing.groupId) }
    var asking by remember { mutableStateOf(false) }
    fun coordinate(text: String): Double? = text.trim().ifEmpty { null }?.replace(',', '.')?.toDoubleOrNull() ?: text.trim().ifEmpty { null }?.let { throw ValidationException("error.placeCoordinates") }
    FormDialog(
        model.t(if (existing.id.isBlank()) "places.add" else "places.edit"), model.t("common.save"), model.t("common.cancel"), canSave = name.isNotBlank(), onDismiss = onClose,
        onSave = {
            val ok = model.act {
                model.books.places.save(
                    existing.copy(
                        groupId = groupId, name = name, category = category, address = address, latitude = coordinate(latitude), longitude = coordinate(longitude),
                        radiusM = radius.trim().toIntOrNull() ?: throw ValidationException("error.placeRadius"), province = province.trim().ifEmpty { null }, notes = notes, archived = archived,
                    ),
                )
            }
            if (ok != null) onClose()
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("places.name"), name, Modifier.weight(2f)) { name = it }
                Picker(model.t("places.category"), PlaceCategory.entries, category, { model.t("placeCategory.$it") }, Modifier.weight(1f)) { category = it }
            }
            TextInput(model.t("places.address"), address) { address = it }
            Text(model.t("places.coordinatesHint"), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("places.latitude"), latitude, Modifier.weight(1f)) { latitude = it }
                TextInput(model.t("places.longitude"), longitude, Modifier.weight(1f)) { longitude = it }
                TextInput(model.t("places.radius"), radius, Modifier.weight(1f)) { radius = it }
            }
            TextInput(model.t("trips.province"), province, supporting = model.t("places.province.hint")) { province = it.take(2) }
            TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
            GroupPicker(model, groupId, enabled = existing.id.isBlank()) { groupId = it.id }
            if (existing.id.isNotBlank()) {
                LabeledCheckbox(model.t("places.archive"), archived) { archived = it }
                TextButton(onClick = { asking = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (asking) {
        AskBeforeDeleting(model, model.t("places.delete.body", existing.name), onDismiss = { asking = false }) {
            (model.act { model.books.places.delete(existing) } != null).also { if (it) onClose() }
        }
    }
}

/**
 * TRP-09: a vehicle's logbook for a year as the CRA asks for it (date, places, purpose, odometers
 * and distance), exported to CSV or PDF, with the kilometres per province or state.
 */
@Composable
internal fun LogbookDialog(model: BooksModel, startYear: Int, onClose: () -> Unit) {
    val books = model.books
    val vehicles = remember { books.vehicles.list(includeInactive = true) }
    var vehicle by remember { mutableStateOf(vehicles.firstOrNull { it.usage != ca.schippers.hfm.books.VehicleUsage.PERSONAL } ?: vehicles.firstOrNull()) }
    var year by remember { mutableStateOf(startYear) }
    val thisYear = today().year
    val book = remember(model.revision, vehicle, year) { vehicle?.let { books.trips.logbook(it.id, year) } }
    val provinces = remember(model.revision, vehicle, year) { vehicle?.let { books.trips.kmByProvince(year, it.id) }.orEmpty() }
    WideDialog(model.t("trips.logbook"), model.t("common.close"), onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(model.t("trips.logbook.hint"), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Picker(model.t("trips.vehicle"), vehicles, vehicle, { it.name }, Modifier.width(260.dp)) { vehicle = it }
                Picker(model.t("taxes.year"), (thisYear downTo thisYear - 6).toList(), year, { it.toString() }, Modifier.width(160.dp)) { year = it }
            }
            if (book == null) return@Column
            val table = logbookTable(model, book)
            book.use?.let { u ->
                Text(
                    listOfNotNull(
                        u.totalKm?.let { model.t("trips.totalKm", model.t("trips.km", odometer(model, it))) }, model.t("trips.workKm", model.t("trips.km", ca.schippers.hfm.money.MoneyFormat.formatDecimal(u.workKm, model.language.locale))),
                        u.workPercent?.let { model.t("trips.workShare", it) },
                    ).joinToString(" · "),
                    fontWeight = FontWeight.Medium,
                )
            }
            if (provinces.isNotEmpty()) {
                Text(
                    model.t(
                        "trips.purposeKm", model.t("trips.byProvince"),
                        provinces.entries.joinToString(" · ") { (p, km) -> "${provinceName(model, p)} ${model.t("trips.km", ca.schippers.hfm.money.MoneyFormat.formatDecimal(km, model.language.locale))}" },
                    ),
                )
            }
            // The table's own buttons save it as CSV, Excel or PDF, or print it.
            if (book.lines.isEmpty()) Text(model.t("trips.logbook.none")) else TableView(model, table, startOpen = true)
        }
    }
}

/** TRP-09: the logbook as a table, the same on screen and in the CSV and PDF files. */
internal fun logbookTable(model: BooksModel, book: Logbook): ReportTable {
    val names = model.books.members.list(includeArchived = true).associate { it.id to it.displayName }
    fun km(v: BigDecimal) = v.stripTrailingZeros().toPlainString()
    return ReportTable(
        model.t("trips.logbook.title", book.vehicle.name, book.year),
        listOfNotNull(book.vehicle.plate, book.vehicle.let { listOfNotNull(it.modelYear?.toString(), it.make, it.model).joinToString(" ").ifBlank { null } }).joinToString(" · "),
        listOf(
            model.t("report.date"), model.t("trips.from"), model.t("trips.to"), model.t("trips.purpose"), model.t("trips.startOdometer"), model.t("trips.endOdometer"),
            model.t("trips.kmColumn"), model.t("trips.driver"), model.t("trips.province"),
        ),
        book.lines.map { l ->
            listOf<Any?>(l.date, l.from.orEmpty(), l.to, model.t("tripPurpose.${l.purpose}"), l.startOdometer?.toString().orEmpty(), l.endOdometer?.toString().orEmpty(), km(l.km), l.driverId?.let(names::get).orEmpty(), l.province.orEmpty())
        },
        listOfNotNull(
            book.use?.let { u ->
                listOfNotNull(
                    u.totalKm?.let { model.t("trips.totalKm", model.t("trips.km", it.toString())) }, model.t("trips.workKm", model.t("trips.km", km(u.workKm))),
                    u.workPercent?.let { model.t("trips.workShare", it) },
                ).joinToString(" · ")
            },
        ),
    )
}
