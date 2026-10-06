package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.CostSummary
import ca.schippers.hfm.books.DocumentEntity
import ca.schippers.hfm.books.EventCategory
import ca.schippers.hfm.books.LinkRole
import ca.schippers.hfm.books.LinkTarget
import ca.schippers.hfm.books.Pet
import ca.schippers.hfm.books.Sex
import ca.schippers.hfm.books.Species
import ca.schippers.hfm.books.ValidationException
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus

/** PET-01 to PET-05: the household's pets, their licences and insurance, and what they cost. */
@Composable
fun PetsScreen(model: BooksModel) {
    val books = model.books
    var showArchived by remember { mutableStateOf(false) }
    val pets = remember(model.revision, showArchived) { books.pets.list(showArchived) }
    var editing by remember { mutableStateOf<Pet?>(null) }
    var costsOf by remember { mutableStateOf<Pet?>(null) }
    var appointmentFor by remember { mutableStateOf<Pet?>(null) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.t("nav.pets"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            LabeledCheckbox(model.t("pets.showArchived"), showArchived) { showArchived = it }
            // HH-06: a viewer sees the pets but cannot change them.
            if (books.pets.canChange) Button(onClick = { editing = Pet("", "", Species.DOG) }, modifier = Modifier.padding(start = 8.dp)) { Text(model.t("pets.add")) }
        }
        LazyColumn(Modifier.padding(top = 8.dp)) {
            if (pets.isEmpty()) item { Text(model.t("pets.none"), Modifier.padding(8.dp)) }
            items(pets, key = { it.id }) { pet ->
                PetCard(
                    model, pet,
                    onEdit = { editing = pet },
                    onCosts = { costsOf = pet },
                    onHealth = {
                        model.healthSubjectId = pet.id
                        model.section = Section.HEALTH
                    },
                    onAppointment = { appointmentFor = pet },
                )
            }
        }
    }

    editing?.let { PetDialog(model, it) { editing = null } }
    costsOf?.let { pet ->
        // PET-05: cost per pet per year by category: the last five years together, or one year.
        var year by remember(pet.id) { mutableStateOf<Int?>(null) }
        val thisYear = today().year
        val costs = remember(pet.id, model.revision, year) {
            year?.let { y -> books.pets.costs(pet.id, LocalDate(y, 1, 1), minOf(LocalDate(y, 12, 31), today())) }
                ?: books.pets.costs(pet.id, LocalDate(thisYear - 4, 1, 1), today())
        }
        CostsDialog(model, pet.name, costs, extra = {
            Picker(model.t("costs.period"), listOf<Int?>(null) + (thisYear downTo thisYear - 4).toList(), year, { it?.toString() ?: model.t("costs.fiveYears") }, Modifier.width(240.dp)) { year = it }
        }) { costsOf = null }
    }
    appointmentFor?.let { pet ->
        val draft = remember(pet.id) { model.newEventDraft(today(), EventCategory.PET, pet.id) }
        if (draft == null) appointmentFor = null else EventDialog(model, null, draft) { appointmentFor = null }
    }
}

@Composable
private fun PetCard(model: BooksModel, pet: Pet, onEdit: () -> Unit, onCosts: () -> Unit, onHealth: () -> Unit, onAppointment: () -> Unit) {
    val books = model.books
    val today = today()
    val year = remember(model.revision, pet.id) { books.pets.costs(pet.id, LocalDate(today.year, 1, 1), today) }
    val last12 = remember(model.revision, pet.id) { books.pets.costs(pet.id, today.minus(DatePeriod(years = 1)), today) }
    val categories = remember(model.revision) { books.categories.list(includeArchived = true).associate { it.id to it.name(model.language) } }
    val owner = remember(model.revision, pet.ownerMemberId) { pet.ownerMemberId?.let { id -> books.members.list(true).firstOrNull { it.id == id }?.displayName } }

    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(pet.name + if (pet.archived) " (${model.t("pets.archived")})" else "", style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(
                            model.t("species.${pet.species}") + (pet.breed?.let { " · $it" }.orEmpty()),
                            pet.sex?.let { model.t("sex.$it") + if (pet.neutered) " (${model.t("pets.neutered.$it")})" else "" },
                            pet.birthDate?.let { ageText(model, it, pet.birthDateEstimated) },
                            pet.colour,
                            owner?.let { model.t("pets.ownerIs", it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onHealth) { Text(model.t("pets.health")) }
                    TextButton(onClick = onAppointment) { Text(model.t("pets.appointment")) }
                    TextButton(onClick = onCosts) { Text(model.t("pets.costs")) }
                    TextButton(onClick = onEdit) { Text(model.t("common.edit")) }
                }
            }
            pet.microchip?.let { Text(model.t("pets.microchipIs", it), style = MaterialTheme.typography.bodySmall) }
            RenewalLine(model, pet.licenceExpiry, model.t("pets.licenceLine", pet.licenceNumber ?: "—", pet.licenceMunicipality ?: "—"))
            RenewalLine(model, pet.insuranceRenewal, model.t("pets.insuranceLine", pet.insurer ?: "—", pet.policyNumber ?: "—"))
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text(model.t("pets.costLine", model.money(year.total), model.money(last12.total)), fontWeight = FontWeight.Medium)
            if (last12.byCategory.isNotEmpty()) {
                Text(
                    last12.byCategory.take(4).joinToString(" · ") { (id, m) -> "${id?.let(categories::get) ?: model.t("register.uncategorized")} ${model.money(m)}" },
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(model.t("pets.noCosts"), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** "Licence 2026-0412 (Québec): renew by 2027-01-31", highlighted when due within 30 days. */
@Composable
internal fun RenewalLine(model: BooksModel, date: LocalDate?, what: String) {
    if (date == null) return
    val days = today().daysUntil(date)
    val color: Color = when {
        days < 0 -> MaterialTheme.colorScheme.error
        days <= 30 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val whenText = if (days < 0) model.t("renewal.expired", model.date(date)) else model.t("renewal.renewBy", model.date(date))
    Text("$what: $whenText", style = MaterialTheme.typography.bodySmall, color = color, fontWeight = if (days <= 30) FontWeight.Bold else FontWeight.Normal)
}

private fun ageText(model: BooksModel, birth: LocalDate, estimated: Boolean): String {
    val months = birth.daysUntil(today()) / 30.44
    val text = if (months < 24) model.t("pets.ageMonths", months.toInt()) else model.t("pets.ageYears", (months / 12).toInt())
    return if (estimated) model.t("pets.about", text) else text
}

/** PET-05, VEH-10: costs by year and by category, in the base currency. */
@Composable
internal fun CostsDialog(model: BooksModel, title: String, costs: CostSummary, extra: (@Composable () -> Unit)? = null, onClose: () -> Unit) {
    val categories = remember(model.revision) { model.books.categories.list(includeArchived = true).associate { it.id to it.name(model.language) } }
    WideDialog(model.t("costs.title", title), model.t("common.close"), onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            extra?.invoke()
            Text(model.t("costs.byYear"), style = MaterialTheme.typography.titleSmall)
            if (costs.byYear.isEmpty()) Text(model.t("costs.none"))
            for ((year, total) in costs.byYear) {
                Row { Text(year.toString(), Modifier.width(120.dp)); Text(model.money(total)) }
            }
            Text(model.t("costs.byCategory"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            for ((id, total) in costs.byCategory) {
                Row { Text(id?.let(categories::get) ?: model.t("register.uncategorized"), Modifier.width(320.dp)); Text(model.money(total)) }
            }
            Text(model.t("costs.total", model.money(costs.total)), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            if (costs.unconverted > 0) Text(model.t("costs.unconverted", costs.unconverted), style = MaterialTheme.typography.bodySmall)
            Text(model.t("costs.how"), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PetDialog(model: BooksModel, existing: Pet, onClose: () -> Unit) {
    val books = model.books
    val people = remember { books.members.list() }
    var name by remember { mutableStateOf(existing.name) }
    var species by remember { mutableStateOf(existing.species) }
    var breed by remember { mutableStateOf(existing.breed.orEmpty()) }
    var sex by remember { mutableStateOf(existing.sex) }
    var neutered by remember { mutableStateOf(existing.neutered) }
    var birth by remember { mutableStateOf(existing.birthDate?.toString().orEmpty()) }
    var estimated by remember { mutableStateOf(existing.birthDateEstimated) }
    var colour by remember { mutableStateOf(existing.colour.orEmpty()) }
    var microchip by remember { mutableStateOf(existing.microchip.orEmpty()) }
    var licence by remember { mutableStateOf(existing.licenceNumber.orEmpty()) }
    var municipality by remember { mutableStateOf(existing.licenceMunicipality.orEmpty()) }
    var licenceExpiry by remember { mutableStateOf(existing.licenceExpiry?.toString().orEmpty()) }
    var insurer by remember { mutableStateOf(existing.insurer.orEmpty()) }
    var policy by remember { mutableStateOf(existing.policyNumber.orEmpty()) }
    var renewal by remember { mutableStateOf(existing.insuranceRenewal?.toString().orEmpty()) }
    var ownerId by remember { mutableStateOf(existing.ownerMemberId) }
    var notes by remember { mutableStateOf(existing.notes.orEmpty()) }
    var archived by remember { mutableStateOf(existing.archived) }
    var confirmDelete by remember { mutableStateOf(false) }

    FormDialog(
        model.t(if (existing.id.isBlank()) "pets.add" else "pets.edit"), model.t("common.save"), model.t("common.cancel"),
        canSave = name.isNotBlank(), onDismiss = onClose,
        onSave = {
            val ok = model.act {
                books.pets.save(
                    existing.copy(
                        name = name, species = species, breed = breed, sex = sex, neutered = neutered, birthDate = optionalDate(birth),
                        birthDateEstimated = estimated, colour = colour, microchip = microchip, licenceNumber = licence,
                        licenceMunicipality = municipality, licenceExpiry = optionalDate(licenceExpiry), insurer = insurer, policyNumber = policy,
                        insuranceRenewal = optionalDate(renewal), ownerMemberId = ownerId, notes = notes, archived = archived,
                    ),
                )
            }
            if (ok != null) onClose()
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("pets.name"), name, Modifier.weight(1f)) { name = it }
                Picker(model.t("pets.species"), Species.entries, species, { model.t("species.$it") }, Modifier.weight(1f)) { species = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("pets.breed"), breed, Modifier.weight(1f)) { breed = it }
                TextInput(model.t("pets.colour"), colour, Modifier.weight(1f)) { colour = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Picker(model.t("pets.sex"), listOf(null) + Sex.entries, sex, { it?.let { s -> model.t("sex.$s") } ?: model.t("common.none") }, Modifier.weight(1f)) { sex = it }
                Column(Modifier.weight(1f)) { LabeledCheckbox(model.t("pets.neuteredField"), neutered) { neutered = it } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DateInput(model.t("pets.birthDate"), birth, Modifier.weight(1f)) { birth = it }
                Column(Modifier.weight(1f)) { LabeledCheckbox(model.t("pets.estimated"), estimated) { estimated = it } }
            }
            TextInput(model.t("pets.microchip"), microchip) { microchip = it }
            Text(model.t("pets.licence"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("pets.licenceNumber"), licence, Modifier.weight(1f)) { licence = it }
                TextInput(model.t("pets.municipality"), municipality, Modifier.weight(1f)) { municipality = it }
                DateInput(model.t("pets.expires"), licenceExpiry, Modifier.weight(1f)) { licenceExpiry = it }
            }
            Text(model.t("pets.insurance"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("pets.insurer"), insurer, Modifier.weight(1f)) { insurer = it }
                TextInput(model.t("pets.policy"), policy, Modifier.weight(1f)) { policy = it }
                DateInput(model.t("pets.renewal"), renewal, Modifier.weight(1f)) { renewal = it }
            }
            Picker(model.t("pets.owner"), listOf(null) + people, people.firstOrNull { it.id == ownerId }, { it?.displayName ?: model.t("pets.wholeHousehold") }) { ownerId = it?.id }
            TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
            if (existing.id.isNotBlank()) {
                LabeledCheckbox(model.t("pets.archivedField"), archived) { archived = it }
                // CON-04: the vet, the insurer, the groomer or kennel, as contacts.
                LinkedContacts(model, LinkTarget.PET, existing.id, listOf(LinkRole.VETERINARIAN, LinkRole.SERVICE, LinkRole.INSURER, LinkRole.OTHER), suggestedName = existing.insurer.orEmpty(), memberIds = setOf(existing.id))
                // PET-01: the pet's photo, and papers such as the adoption or microchip certificate, in the vault.
                val photoGroup = remember(model.revision) { model.defaultDocumentGroup() }
                if (photoGroup != null) DocumentsBlock(model, DocumentEntity.PET, existing.id, photoGroup, "pets.photos")
                if (books.pets.canChange) TextButton(onClick = { confirmDelete = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmDelete) {
        FormDialog(model.t("pets.delete.title"), model.t("common.delete"), model.t("common.cancel"), onDismiss = { confirmDelete = false }, onSave = {
            if (model.act { books.pets.delete(existing.id) } != null) {
                confirmDelete = false
                onClose()
            }
        }) { Text(model.t("pets.delete.body", existing.name)) }
    }
}

private fun optionalDate(text: String): LocalDate? =
    text.trim().ifEmpty { null }?.let { runCatching { LocalDate.parse(it) }.getOrElse { throw ValidationException("error.invalidDate") } }
