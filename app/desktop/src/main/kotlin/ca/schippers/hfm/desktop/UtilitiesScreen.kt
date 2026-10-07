package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Asset
import ca.schippers.hfm.books.AssetKind
import ca.schippers.hfm.books.Bill
import ca.schippers.hfm.books.FuelKind
import ca.schippers.hfm.books.FuelTank
import ca.schippers.hfm.books.MeterKind
import ca.schippers.hfm.books.TankDelivery
import ca.schippers.hfm.books.UnitCost
import ca.schippers.hfm.books.UtilityMeter
import ca.schippers.hfm.books.UtilityReading
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.calc.trackers.MonthUse
import ca.schippers.hfm.calc.trackers.Tanks
import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.money.Money
import kotlinx.datetime.daysUntil
import java.math.BigDecimal
import java.math.RoundingMode

private enum class UtilitiesTab { METERS, TANKS }

/** The homes and cottages meters and tanks can belong to. */
private fun BooksModel.homes(): List<Asset> = runCatching { books.assets.list() }.getOrDefault(emptyList()).filter { it.kind == AssetKind.HOME || it.kind == AssetKind.COTTAGE }

private fun BooksModel.placeName(assetId: String?): String =
    assetId?.let { id -> runCatching { books.assets.get(id).name }.getOrNull() } ?: t("utilities.household")

/** "January 2026" in the user's language; [midSentence] keeps French months in lower case ("en septembre 2026"). */
internal fun BooksModel.monthName(m: MonthUse, midSentence: Boolean = false): String =
    java.time.YearMonth.of(m.year, m.month).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", language.locale))
        .let { if (midSentence) it else it.replaceFirstChar { c -> c.uppercase(language.locale) } }

private fun BooksModel.unit(kind: MeterKind): String = t("meterUnit.${kind.unit}")

/** "0.0968 $/kWh" as the user's language writes a price per unit, to four decimals. */
private fun BooksModel.unitCost(c: UnitCost, unit: String): String =
    t("utilities.perUnit", MoneyFormat4.format(c.perUnit, c.currency, language.locale), unit)

/** Prices per unit keep four decimals (0.0968 $), unlike amounts of money. */
private object MoneyFormat4 {
    fun format(v: BigDecimal, currency: ca.schippers.hfm.money.Currency, locale: java.util.Locale): String =
        java.text.NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = java.util.Currency.getInstance(currency.code)
            minimumFractionDigits = 2
            maximumFractionDigits = 4
        }.format(v.setScale(4, RoundingMode.HALF_UP))
}

/** UTL-01, UTL-02: utility meters and fuel tanks, per home or cottage or for the household. */
@Composable
fun UtilitiesScreen(model: BooksModel) {
    var tab by remember { mutableStateOf(UtilitiesTab.METERS) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(model.t("nav.utilities"), style = MaterialTheme.typography.titleLarge)
        Text(model.t("utilities.hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        PrimaryTabRow(selectedTabIndex = tab.ordinal, modifier = Modifier.padding(vertical = 8.dp)) {
            for (t in UtilitiesTab.entries) Tab(selected = tab == t, onClick = { tab = t }, modifier = Modifier.walkTab(t, tab == t) { tab = t }, text = { Text(model.t("utilities.tab.$t")) })
        }
        when (tab) {
            UtilitiesTab.METERS -> MetersTab(model)
            UtilitiesTab.TANKS -> TanksTab(model)
        }
    }
}

// --- Meters (UTL-01) -----------------------------------------------------------------------------------

@Composable
private fun MetersTab(model: BooksModel) {
    val books = model.books
    var showArchived by remember { mutableStateOf(false) }
    val meters = remember(model.revision, showArchived) { books.utilities.meters(showArchived) }
    var editing by remember { mutableStateOf<UtilityMeter?>(null) }
    var reading by remember { mutableStateOf<UtilityMeter?>(null) }
    val access = rememberAccess(model)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (access.canCreate) Button(onClick = { editing = UtilityMeter("", model.trackerGroup(), "", MeterKind.ELECTRICITY) }, modifier = Modifier.walkTarget("meters.add")) { Text(model.t("meter.add")) }
        LabeledCheckbox(model.t("utilities.showArchived"), showArchived) { showArchived = it }
    }
    if (meters.isEmpty()) Text(model.t("meter.none"), Modifier.padding(vertical = 8.dp))
    Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
        for (m in meters) {
            val s = remember(model.revision, m) { books.utilities.summary(m, today()) }
            val unit = model.unit(m.kind)
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontWeight = FontWeight.Medium)
                            Text(
                                listOfNotNull(
                                    model.t("meterKind.${m.kind}"), model.placeName(m.assetId), model.t("meter.timeOfUse").takeIf { m.timeOfUse },
                                    m.readings.lastOrNull()?.let { model.t("meter.last", model.quantity(it.value), unit, model.date(it.date)) },
                                    s.cost?.let { model.unitCost(it, unit) },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        OutlinedButton(onClick = { reading = m }, modifier = Modifier.walkTarget("meters.readings")) { Text(model.t("meter.readings")) }
                        TextButton(onClick = { editing = m }, enabled = access.mayEdit(m.groupId)) { Text(model.t("meter.edit")) }
                    }
                    if (s.months.isEmpty()) Text(model.t("meter.noUse"), style = MaterialTheme.typography.bodySmall)
                    else HeadingRow {
                        ColumnHeading(model.t("custom.dimension.MONTH"), Modifier.width(270.dp))
                        ColumnHeading(model.t("column.used"), Modifier.width(130.dp))
                        ColumnHeading(model.t("column.lastYear"), Modifier.width(220.dp))
                        ColumnHeading(model.t("column.variation"), Modifier.width(80.dp))
                        ColumnHeading(model.t("vehicles.cost"), Modifier.width(120.dp))
                    }
                    for (c in s.months.take(MONTHS_SHOWN)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(model.monthName(c.use) + if (!c.use.complete) " " + model.t("utilities.partial") else "", Modifier.width(270.dp))
                            Text("${model.quantity(c.use.amount, 0)} $unit", Modifier.width(130.dp))
                            Text(c.lastYear?.takeIf { c.use.complete }?.let { model.t("utilities.lastYear", model.quantity(it, 0), unit) } ?: "", Modifier.width(220.dp), style = MaterialTheme.typography.bodySmall)
                            Text(c.changePercent?.takeIf { c.use.complete }?.let { model.t("utilities.change", (if (it.signum() > 0) "+" else "") + ca.schippers.hfm.money.MoneyFormat.formatDecimal(it, model.language.locale)) } ?: "", Modifier.width(80.dp), style = MaterialTheme.typography.bodySmall)
                            Text(
                                s.cost?.let { cost -> model.t("utilities.about", model.money(Money.of((c.use.amount * cost.perUnit).setScale(2, RoundingMode.HALF_UP), cost.currency))) } ?: "",
                                Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall,
                            )
                            if (c.unusual) Text(model.t("utilities.unusual"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
    editing?.let { m -> MeterDialog(model, m) { editing = null } }
    reading?.let { m -> MeterReadingsDialog(model, books.utilities.meters(true).firstOrNull { it.id == m.id } ?: m, access) { reading = null } }
}

@Composable
private fun MeterDialog(model: BooksModel, m: UtilityMeter, onClose: () -> Unit) {
    val homes = remember { model.homes() }
    val bills = remember { runCatching { model.books.bills.list() }.getOrDefault(emptyList()) }
    var name by remember { mutableStateOf(m.name) }
    var kind by remember { mutableStateOf(m.kind) }
    var home by remember { mutableStateOf(homes.firstOrNull { it.id == m.assetId }) }
    var timeOfUse by remember { mutableStateOf(m.timeOfUse) }
    var bill by remember { mutableStateOf<Bill?>(bills.firstOrNull { it.id == m.billId }) }
    var notes by remember { mutableStateOf(m.notes.orEmpty()) }
    var archived by remember { mutableStateOf(m.archived) }
    var groupId by remember { mutableStateOf(m.groupId) }
    var asking by remember { mutableStateOf(false) }
    FormDialog(model.t(if (m.id.isBlank()) "meter.add" else "meter.edit"), model.t("common.save"), model.t("common.cancel"), canSave = name.isNotBlank(), onDismiss = onClose, walkId = "meter.dialog", onSave = {
        val ok = model.act {
            model.books.utilities.saveMeter(
                m.copy(groupId = groupId, name = name, kind = kind, assetId = home?.id, timeOfUse = timeOfUse && kind == MeterKind.ELECTRICITY, billId = bill?.id, notes = notes, archived = archived),
            )
        }
        if (ok != null) onClose()
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("meter.name"), name, Modifier.weight(1f)) { name = it }
            Picker(model.t("meter.kind"), MeterKind.entries, kind, { model.t("meterKind.$it") }, Modifier.width(190.dp)) { kind = it }
        }
        Picker(model.t("utilities.place"), listOf(null) + homes, home, { it?.name ?: model.t("utilities.household") }) { home = it }
        if (kind == MeterKind.ELECTRICITY) {
            LabeledCheckbox(model.t("meter.timeOfUse"), timeOfUse) { timeOfUse = it }
            Text(model.t("meter.timeOfUseHint"), style = MaterialTheme.typography.bodySmall)
        }
        Picker(model.t("meter.bill"), listOf(null) + bills, bill, { it?.name ?: model.t("meter.noBill") }) { bill = it }
        Text(model.t("meter.billHint"), style = MaterialTheme.typography.bodySmall)
        TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
        if (m.id.isBlank()) StoreInPicker(model, groupId) { groupId = it }
        if (m.id.isNotBlank()) {
            LabeledCheckbox(model.t("utilities.archived"), archived) { archived = it }
            TextButton(onClick = { asking = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (asking) {
        AskBeforeDeleting(model, model.t("meter.delete.body", m.name, m.readings.size), onDismiss = { asking = false }) {
            (model.act { model.books.utilities.deleteMeter(m) } != null).also { if (it) onClose() }
        }
    }
}

/** A meter's readings, newest first, and a new one: the total, or each register with time of use. */
@Composable
private fun MeterReadingsDialog(model: BooksModel, m: UtilityMeter, access: GroupAccess, onClose: () -> Unit) {
    val unit = model.unit(m.kind)
    var day by remember { mutableStateOf(today().toString()) }
    var value by remember { mutableStateOf("") }
    var on by remember { mutableStateOf("") }
    var mid by remember { mutableStateOf("") }
    var off by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<UtilityReading?>(null) }
    val filled = value.isNotBlank() || (m.timeOfUse && listOf(on, mid, off).any { it.isNotBlank() })
    FormDialog(m.name, model.t("meter.addReading"), model.t("common.close"), canSave = filled && access.mayAdd(m.groupId), onDismiss = onClose, walkId = "meter.reading", onSave = {
        val ok = model.act {
            model.books.utilities.addReading(m.id, trackerDate(day), trackerNumber(value, "error.meterReading"), trackerNumber(on, "error.meterReading"), trackerNumber(mid, "error.meterReading"), trackerNumber(off, "error.meterReading"), notes)
        }
        if (ok != null) onClose()
    }) {
        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
            if (m.readings.isEmpty()) Text(model.t("meter.noReadings"), style = MaterialTheme.typography.bodySmall)
            else DatedHeadings(model, "column.reading")
            for (r in m.readings.reversed().take(READINGS_SHOWN)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(model.date(r.date), Modifier.width(100.dp))
                    Text(
                        "${model.quantity(r.value)} $unit" +
                            (if (r.onPeak != null || r.midPeak != null || r.offPeak != null) " (" + model.t("meter.registers", r.onPeak?.let { model.quantity(it) } ?: "—", r.midPeak?.let { model.quantity(it) } ?: "—", r.offPeak?.let { model.quantity(it) } ?: "—") + ")" else "") +
                            (if (r.fromPhone) " · " + model.t("tracker.fromPhone") else "") + (r.notes?.let { " · $it" } ?: ""),
                        Modifier.weight(1f),
                    )
                    RemoveButton(model.t("common.delete"), Modifier.width(ICON_ACTIONS_WIDTH), enabled = access.mayEdit(m.groupId)) { deleting = r }
                }
            }
        }
        Text(model.t("meter.newReading"), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t("report.date"), day, Modifier.width(170.dp)) { day = it }
            TextInput(model.t("meter.reading", unit), value, Modifier.weight(1f)) { value = it }
        }
        if (m.timeOfUse) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("meter.onPeak"), on, Modifier.weight(1f)) { on = it }
                TextInput(model.t("meter.midPeak"), mid, Modifier.weight(1f)) { mid = it }
                TextInput(model.t("meter.offPeak"), off, Modifier.weight(1f)) { off = it }
            }
            Text(model.t("meter.registersHint"), style = MaterialTheme.typography.bodySmall)
        }
        TextInput(model.t("calendar.notes"), notes) { notes = it }
    }
    deleting?.let { r ->
        AskBeforeDeleting(model, model.t("meter.deleteReading.body", model.quantity(r.value), unit, model.date(r.date)), onDismiss = { deleting = null }) {
            (model.act { model.books.utilities.deleteReading(m, r.id) } != null).also { if (it) onClose() }
        }
    }
}

// --- Fuel tanks (UTL-02) -------------------------------------------------------------------------------

@Composable
private fun TanksTab(model: BooksModel) {
    val books = model.books
    var showArchived by remember { mutableStateOf(false) }
    val tanks = remember(model.revision, showArchived) { books.utilities.tanks(showArchived) }
    var editing by remember { mutableStateOf<FuelTank?>(null) }
    var reading by remember { mutableStateOf<FuelTank?>(null) }
    var delivering by remember { mutableStateOf<FuelTank?>(null) }
    val access = rememberAccess(model)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (access.canCreate) Button(onClick = { editing = FuelTank("", model.trackerGroup(), "", FuelKind.PROPANE, BigDecimal.ZERO) }, modifier = Modifier.walkTarget("tanks.add")) { Text(model.t("tank.add")) }
        LabeledCheckbox(model.t("utilities.showArchived"), showArchived) { showArchived = it }
    }
    if (tanks.isEmpty()) Text(model.t("tank.none"), Modifier.padding(vertical = 8.dp))
    Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
        for (t in tanks) {
            val s = remember(model.revision, t) { books.utilities.status(t, today()) }
            val litre = model.t("meterUnit.L")
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontWeight = FontWeight.Medium)
                            Text(
                                listOfNotNull(
                                    model.t("fuelKind.${t.fuel}"), model.placeName(t.assetId), model.t("tank.capacity", model.quantity(t.capacityLitres, 0)), t.supplier,
                                    s.pricePerLitre?.let { model.unitCost(it, litre) },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        OutlinedButton(onClick = { reading = t }) { Text(model.t("tank.readings")) }
                        OutlinedButton(onClick = { delivering = t }, modifier = Modifier.padding(start = 8.dp)) { Text(model.t("tank.deliveries")) }
                        TextButton(onClick = { editing = t }, enabled = access.mayEdit(t.groupId)) { Text(model.t("tank.edit")) }
                    }
                    val p = s.projection
                    if (p == null) {
                        Text(model.t("tank.noReadings"), style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(
                            model.t("tank.levelNow", model.quantity(Tanks.percent(p.levelToday, t.capacityLitres), 0), model.quantity(p.levelToday, 0)) +
                                (t.readings.lastOrNull()?.let { " · " + model.t("tank.lastReading", model.quantity(it.percent, 0), model.date(it.date)) } ?: ""),
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            p.dailyUse?.let { model.t("tank.perDay", model.quantity(it)) } ?: model.t("tank.noUse"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        p.orderDate?.let { d ->
                            val days = today().daysUntil(d)
                            Text(
                                model.t("tank.orderBy", s.orderPercent, model.date(d)) + (p.emptyDate?.let { " · " + model.t("tank.emptyBy", model.date(it)) } ?: ""),
                                color = if (days <= ca.schippers.hfm.calc.rules.Thresholds.tankOrderDays(today())) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (s.months.isNotEmpty()) HeadingRow {
                        ColumnHeading(model.t("custom.dimension.MONTH"), Modifier.width(270.dp))
                        ColumnHeading(model.t("column.used"), Modifier.width(130.dp))
                        if (s.pricePerLitre != null) ColumnHeading(model.t("vehicles.cost"))
                    }
                    for (u in s.months.take(MONTHS_SHOWN)) {
                        Row {
                            Text(model.monthName(u) + if (!u.complete) " " + model.t("utilities.partial") else "", Modifier.width(270.dp))
                            Text("${model.quantity(u.amount, 0)} $litre", Modifier.width(130.dp))
                            s.pricePerLitre?.let { c -> Text(model.t("utilities.about", model.money(Money.of((u.amount * c.perUnit).setScale(2, RoundingMode.HALF_UP), c.currency))), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
    editing?.let { t -> TankDialog(model, t) { editing = null } }
    reading?.let { t -> TankReadingsDialog(model, books.utilities.tanks(true).firstOrNull { it.id == t.id } ?: t, access) { reading = null } }
    delivering?.let { t -> TankDeliveriesDialog(model, books.utilities.tanks(true).firstOrNull { it.id == t.id } ?: t, access) { delivering = null } }
}

@Composable
private fun TankDialog(model: BooksModel, t: FuelTank, onClose: () -> Unit) {
    val homes = remember { model.homes() }
    var name by remember { mutableStateOf(t.name) }
    var fuel by remember { mutableStateOf(t.fuel) }
    var home by remember { mutableStateOf(homes.firstOrNull { it.id == t.assetId }) }
    var capacity by remember { mutableStateOf(if (t.capacityLitres.signum() == 0) "" else t.capacityLitres.toPlainString()) }
    var order by remember { mutableStateOf(t.orderPercent?.toString().orEmpty()) }
    var supplier by remember { mutableStateOf(t.supplier.orEmpty()) }
    var notes by remember { mutableStateOf(t.notes.orEmpty()) }
    var archived by remember { mutableStateOf(t.archived) }
    var groupId by remember { mutableStateOf(t.groupId) }
    var asking by remember { mutableStateOf(false) }
    val rule = remember { ca.schippers.hfm.calc.rules.Thresholds.tankOrderLevel(today()) }
    FormDialog(model.t(if (t.id.isBlank()) "tank.add" else "tank.edit"), model.t("common.save"), model.t("common.cancel"), canSave = name.isNotBlank(), onDismiss = onClose, onSave = {
        val ok = model.act {
            model.books.utilities.saveTank(
                t.copy(
                    groupId = groupId, name = name, fuel = fuel, assetId = home?.id, capacityLitres = trackerNumber(capacity, "error.tankCapacity") ?: throw ValidationException("error.tankCapacity"),
                    orderPercent = order.trim().ifEmpty { null }?.let { it.toIntOrNull() ?: throw ValidationException("error.tankOrderPercent") }, supplier = supplier, notes = notes, archived = archived,
                ),
            )
        }
        if (ok != null) onClose()
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("tank.name"), name, Modifier.weight(1f)) { name = it }
            Picker(model.t("tank.fuel"), FuelKind.entries, fuel, { model.t("fuelKind.$it") }, Modifier.width(190.dp)) { fuel = it }
        }
        Picker(model.t("utilities.place"), listOf(null) + homes, home, { it?.name ?: model.t("utilities.household") }) { home = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("tank.capacityLitres"), capacity, Modifier.weight(1f)) { capacity = it }
            TextInput(model.t("tank.orderPercent"), order, Modifier.weight(1f), supporting = model.t("tank.orderPercentHint", rule)) { order = it }
        }
        TextInput(model.t("tank.supplier"), supplier) { supplier = it }
        TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
        if (t.id.isBlank()) StoreInPicker(model, groupId) { groupId = it }
        if (t.id.isNotBlank()) {
            LabeledCheckbox(model.t("utilities.archived"), archived) { archived = it }
            TextButton(onClick = { asking = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (asking) {
        AskBeforeDeleting(model, model.t("tank.delete.body", t.name), onDismiss = { asking = false }) {
            (model.act { model.books.utilities.deleteTank(t) } != null).also { if (it) onClose() }
        }
    }
}

/** A tank's gauge readings, newest first, and a new one in percent or in litres. */
@Composable
private fun TankReadingsDialog(model: BooksModel, t: FuelTank, access: GroupAccess, onClose: () -> Unit) {
    var day by remember { mutableStateOf(today().toString()) }
    var percent by remember { mutableStateOf("") }
    var litres by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<ca.schippers.hfm.books.TankReading?>(null) }
    FormDialog(t.name, model.t("tank.addReading"), model.t("common.close"), canSave = (percent.isNotBlank() || litres.isNotBlank()) && access.mayAdd(t.groupId), onDismiss = onClose, onSave = {
        val ok = model.act { model.books.utilities.addTankReading(t.id, trackerDate(day), trackerNumber(percent, "error.tankLevel"), trackerNumber(litres, "error.tankLevel"), notes) }
        if (ok != null) onClose()
    }) {
        Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
            if (t.readings.isEmpty()) Text(model.t("tank.noReadings"), style = MaterialTheme.typography.bodySmall)
            else DatedHeadings(model, "column.level")
            for (r in t.readings.reversed().take(READINGS_SHOWN)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(model.date(r.date), Modifier.width(100.dp))
                    Text(
                        model.t("tank.level", model.quantity(r.percent), model.quantity(Tanks.litres(r.percent, t.capacityLitres), 0)) +
                            (if (r.fromPhone) " · " + model.t("tracker.fromPhone") else "") + (r.notes?.let { " · $it" } ?: ""),
                        Modifier.weight(1f),
                    )
                    RemoveButton(model.t("common.delete"), Modifier.width(ICON_ACTIONS_WIDTH), enabled = access.mayEdit(t.groupId)) { deleting = r }
                }
            }
        }
        Text(model.t("tank.newReading"), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t("report.date"), day, Modifier.width(170.dp)) { day = it }
            TextInput(model.t("tank.percent"), percent, Modifier.weight(1f), enabled = litres.isBlank()) { percent = it }
            TextInput(model.t("tank.litres"), litres, Modifier.weight(1f), enabled = percent.isBlank()) { litres = it }
        }
        TextInput(model.t("calendar.notes"), notes) { notes = it }
    }
    deleting?.let { r ->
        AskBeforeDeleting(model, model.t("tank.deleteReading.body", model.quantity(r.percent), model.date(r.date)), onDismiss = { deleting = null }) {
            (model.act { model.books.utilities.deleteTankReading(t, r.id) } != null).also { if (it) onClose() }
        }
    }
}

/** A tank's deliveries, newest first, and a new one, which can record its payment. */
@Composable
private fun TankDeliveriesDialog(model: BooksModel, t: FuelTank, access: GroupAccess, onClose: () -> Unit) {
    val locale = model.language.locale
    val base = model.books.reports.base
    val accounts = remember { model.books.accounts.list().map { it.account }.filter { it.type.kind == AccountKind.BANK || it.type.kind == AccountKind.CREDIT } }
    var day by remember { mutableStateOf(today().toString()) }
    var litres by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var record by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf(accounts.firstOrNull()) }
    var notes by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<TankDelivery?>(null) }
    FormDialog(t.name, model.t("tank.addDelivery"), model.t("common.close"), canSave = litres.isNotBlank() && access.mayEdit(t.groupId), onDismiss = onClose, onSave = {
        val ok = model.act {
            val currency = account?.takeIf { record }?.currency ?: base
            val money = parseAmount(cost, currency, locale)?.abs()
            model.books.utilities.addDelivery(t.id, trackerDate(day), trackerNumber(litres, "error.tankDelivery") ?: throw ValidationException("error.tankDelivery"), money, account?.id?.takeIf { record && money != null }, notes)
        }
        if (ok != null) onClose()
    }) {
        Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
            if (t.deliveries.isEmpty()) Text(model.t("tank.noDeliveries"), style = MaterialTheme.typography.bodySmall)
            else DatedHeadings(model, "column.delivery")
            for (d in t.deliveries.reversed().take(READINGS_SHOWN)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(model.date(d.date), Modifier.width(100.dp))
                    Text(
                        "${model.quantity(d.litres)} ${model.t("meterUnit.L")}" + (d.cost?.let { " · ${model.money(it)}" } ?: "") +
                            (if (d.transactionId != null) " · " + model.t("tank.paymentRecorded") else "") + (d.notes?.let { " · $it" } ?: ""),
                        Modifier.weight(1f),
                    )
                    RemoveButton(model.t("common.delete"), Modifier.width(ICON_ACTIONS_WIDTH), enabled = access.mayEdit(t.groupId)) { deleting = d }
                }
            }
        }
        Text(model.t("tank.newDelivery"), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t("report.date"), day, Modifier.width(170.dp)) { day = it }
            TextInput(model.t("tank.litres"), litres, Modifier.weight(1f)) { litres = it }
            AmountInput(model.t("tank.cost"), cost, account?.takeIf { record }?.currency ?: base, locale, Modifier.weight(1f), model::money) { cost = it }
        }
        if (accounts.isNotEmpty()) {
            LabeledCheckbox(model.t("tank.recordPayment"), record) { record = it }
            if (record) Picker(model.t("payStub.account"), accounts, account, { it.name }) { account = it }
        }
        Text(model.t("tank.deliveryHint"), style = MaterialTheme.typography.bodySmall)
        TextInput(model.t("calendar.notes"), notes) { notes = it }
    }
    deleting?.let { d ->
        var withPayment by remember { mutableStateOf(false) }
        AskBeforeDeleting(
            model, model.t("tank.deleteDelivery.body", model.quantity(d.litres), model.date(d.date)), onDismiss = { deleting = null },
            extra = { if (d.transactionId != null) LabeledCheckbox(model.t("tank.deleteDelivery.payment"), withPayment) { withPayment = it } },
        ) {
            (model.act(retryConfirmed = { model.books.utilities.deleteDelivery(t, d, withTransaction = true, confirmReconciled = true); onClose() }) { model.books.utilities.deleteDelivery(t, d, withPayment) } != null)
                .also { if (it) onClose() }
        }
    }
}

private const val MONTHS_SHOWN = 13
private const val READINGS_SHOWN = 36
