package ca.schippers.hfm.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Category
import ca.schippers.hfm.books.Institution
import ca.schippers.hfm.books.Member
import ca.schippers.hfm.books.Payee
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.domain.CategoryKind
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.domain.TaxFlag
import kotlinx.datetime.LocalDate

/** A list on the left and an editor for the selected (or new) item on the right. */
@Composable
private fun <T> ListEditor(
    title: String,
    items: List<T>,
    key: (T) -> String,
    label: (T) -> String,
    indent: (T) -> Int = { 0 },
    dimmed: (T) -> Boolean = { false },
    addLabel: String?,
    onAdd: () -> Unit,
    selectedKey: String?,
    onSelect: (T) -> Unit,
    editor: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(380.dp).fillMaxHeight().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (addLabel != null) Button(onClick = onAdd) { Text(addLabel) }
            }
            LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                items(items, key = key) { item ->
                    Text(
                        label(item),
                        color = if (dimmed(item)) MaterialTheme.colorScheme.outline else Color.Unspecified,
                        modifier = Modifier.fillMaxWidth()
                            .background(if (key(item) == selectedKey) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .clickable { onSelect(item) }
                            .padding(start = (8 + indent(item) * 20).dp, top = 4.dp, bottom = 4.dp, end = 8.dp),
                    )
                }
            }
        }
        VerticalDivider()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).width(520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            editor()
        }
    }
}

// --- Categories (CAT-01, CAT-05) ---------------------------------------------------------------

@Composable
fun CategoriesScreen(model: BooksModel) {
    val books = model.books
    val tree = remember(model.revision) { books.categories.tree(includeArchived = true) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    /** A new category being created: its parent (null for top level) and kind. */
    var creating by remember { mutableStateOf<Pair<Category?, CategoryKind>?>(null) }
    val selected = tree.firstOrNull { it.first.id == selectedId }?.first

    ListEditor(
        model.t("nav.categories"), tree, key = { it.first.id },
        label = { (c, _) -> c.name(model.language) + (c.taxFlag?.let { " · ${model.t("tax.$it")}" } ?: "") },
        indent = { it.second }, dimmed = { it.first.archived },
        addLabel = null, onAdd = {}, selectedKey = selectedId,
        onSelect = { selectedId = it.first.id; creating = null },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { creating = null to CategoryKind.EXPENSE; selectedId = null }) { Text(model.t("category.addExpense")) }
            OutlinedButton(onClick = { creating = null to CategoryKind.INCOME; selectedId = null }) { Text(model.t("category.addIncome")) }
            if (selected != null) OutlinedButton(onClick = { creating = selected to selected.kind; selectedId = null }) { Text(model.t("category.addChild")) }
        }
        val target = creating
        when {
            target != null -> CategoryForm(model, null, target.first, target.second) { id -> creating = null; selectedId = id }
            selected != null -> CategoryForm(model, selected, null, selected.kind) { selectedId = it }
            else -> Text(model.t("category.select"))
        }
    }
}

@Composable
private fun CategoryForm(model: BooksModel, existing: Category?, parent: Category?, kind: CategoryKind, onSaved: (String) -> Unit) {
    var nameEn by remember(existing, parent) { mutableStateOf(existing?.nameEn.orEmpty()) }
    var nameFr by remember(existing, parent) { mutableStateOf(existing?.nameFr.orEmpty()) }
    var tax by remember(existing, parent) { mutableStateOf(existing?.taxFlag ?: parent?.taxFlag) }
    var archived by remember(existing) { mutableStateOf(existing?.archived ?: false) }

    Text(
        when {
            existing != null -> existing.name(model.language)
            parent != null -> model.t("category.newUnder", parent.name(model.language))
            else -> model.t("category.new.$kind")
        },
        style = MaterialTheme.typography.titleMedium,
    )
    TextInput(model.t("category.nameEn"), nameEn) { nameEn = it }
    TextInput(model.t("category.nameFr"), nameFr) { nameFr = it }
    Picker(model.t("category.tax"), listOf<TaxFlag?>(null) + TaxFlag.entries, tax, { it?.let { f -> model.t("tax.$f") } ?: model.t("common.none") }) { tax = it }
    if (existing != null) LabeledCheckbox(model.t("category.archived"), archived) { archived = it }
    Button(onClick = {
        val id = model.act {
            if (existing == null) {
                model.books.categories.create(parent?.id, nameEn, nameFr, kind, tax).id
            } else {
                model.books.categories.update(existing.copy(nameEn = nameEn, nameFr = nameFr, taxFlag = tax, archived = archived))
                existing.id
            }
        }
        if (id != null) onSaved(id)
    }) { Text(model.t("common.save")) }
}

// --- Payees (section 7.4) ----------------------------------------------------------------------

@Composable
fun PayeesScreen(model: BooksModel) {
    val books = model.books
    val payees = remember(model.revision) { books.payees.list(includeArchived = true) }
    val tree = remember(model.revision) { books.categories.tree() }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val selected = payees.firstOrNull { it.id == selectedId }

    ListEditor(
        model.t("nav.payees"), payees, key = { it.id }, label = { it.name }, dimmed = { it.archived },
        addLabel = model.t("common.add"), onAdd = { creating = true; selectedId = null }, selectedKey = selectedId,
        onSelect = { selectedId = it.id; creating = false },
    ) {
        if (creating || selected != null) {
            PayeeForm(model, if (creating) null else selected, tree) { id -> creating = false; selectedId = id }
        } else {
            Text(model.t("payee.select"))
        }
    }
}

@Composable
private fun PayeeForm(model: BooksModel, existing: Payee?, tree: List<Pair<Category, Int>>, onSaved: (String) -> Unit) {
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var categoryId by remember(existing) { mutableStateOf(existing?.defaultCategoryId) }
    var archived by remember(existing) { mutableStateOf(existing?.archived ?: false) }
    var alias by remember(existing) { mutableStateOf("") }

    TextInput(model.t("payee.name"), name) { name = it }
    Picker(
        model.t("payee.defaultCategory"), listOf<Pair<Category, Int>?>(null) + tree, tree.firstOrNull { it.first.id == categoryId },
        { it?.first?.name(model.language) ?: model.t("common.none") }, indent = { it?.second ?: 0 },
    ) { categoryId = it?.first?.id }
    if (existing != null) {
        LabeledCheckbox(model.t("category.archived"), archived) { archived = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextInput(model.t("payee.alias"), alias, Modifier.weight(1f), supporting = model.t("payee.alias.hint")) { alias = it }
            OutlinedButton(onClick = { if (model.act { model.books.payees.addAlias(existing.id, alias) } != null) alias = "" }) {
                Text(model.t("payee.addAlias"))
            }
        }
    }
    Button(onClick = {
        val id = model.act {
            if (existing == null) {
                model.books.payees.create(name, categoryId).id
            } else {
                model.books.payees.update(existing.copy(name = name, defaultCategoryId = categoryId, archived = archived))
                existing.id
            }
        }
        if (id != null) onSaved(id)
    }) { Text(model.t("common.save")) }
}

// --- Institutions (ACC-01) ---------------------------------------------------------------------

@Composable
fun InstitutionsScreen(model: BooksModel) {
    val institutions = remember(model.revision) { model.books.institutions.list() }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val selected = institutions.firstOrNull { it.id == selectedId }

    ListEditor(
        model.t("nav.institutions"), institutions, key = { it.id }, label = { it.name },
        addLabel = model.t("common.add"), onAdd = { creating = true; selectedId = null }, selectedKey = selectedId,
        onSelect = { selectedId = it.id; creating = false },
    ) {
        if (creating || selected != null) {
            InstitutionForm(model, if (creating) null else selected) { id -> creating = false; selectedId = id }
        } else {
            Text(model.t("institution.select"))
        }
    }
}

@Composable
private fun InstitutionForm(model: BooksModel, existing: Institution?, onSaved: (String) -> Unit) {
    var value by remember(existing) { mutableStateOf(existing ?: Institution("", "")) }
    TextInput(model.t("institution.name"), value.name) { value = value.copy(name = it) }
    TextInput(model.t("institution.branch"), value.branch.orEmpty()) { value = value.copy(branch = it) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextInput(model.t("institution.number"), value.institutionNumber.orEmpty(), Modifier.weight(1f)) { value = value.copy(institutionNumber = it) }
        TextInput(model.t("institution.transit"), value.transitNumber.orEmpty(), Modifier.weight(1f)) { value = value.copy(transitNumber = it) }
    }
    TextInput(model.t("institution.website"), value.website.orEmpty()) { value = value.copy(website = it) }
    TextInput(model.t("institution.phone"), value.phone.orEmpty()) { value = value.copy(phone = it) }
    TextInput(model.t("account.notes"), value.notes.orEmpty(), singleLine = false) { value = value.copy(notes = it) }
    Button(onClick = {
        val cleaned = value.copy(
            branch = value.branch?.ifBlank { null }, institutionNumber = value.institutionNumber?.ifBlank { null },
            transitNumber = value.transitNumber?.ifBlank { null }, website = value.website?.ifBlank { null },
            phone = value.phone?.ifBlank { null }, notes = value.notes?.ifBlank { null },
        )
        val id = model.act {
            if (existing == null) model.books.institutions.create(cleaned).id else model.books.institutions.update(cleaned).let { cleaned.id }
        }
        if (id != null) onSaved(id)
    }) { Text(model.t("common.save")) }
}

// --- Household members (HH-01) -----------------------------------------------------------------

@Composable
fun MembersScreen(model: BooksModel) {
    val members = remember(model.revision) { model.books.members.list(includeArchived = true) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val selected = members.firstOrNull { it.id == selectedId }

    ListEditor(
        model.t("nav.members"), members, key = { it.id },
        label = { "${it.displayName} · ${model.t("memberKind.${it.kind}")}" }, dimmed = { it.archived },
        addLabel = model.t("common.add"), onAdd = { creating = true; selectedId = null }, selectedKey = selectedId,
        onSelect = { selectedId = it.id; creating = false },
    ) {
        if (creating || selected != null) {
            MemberForm(model, if (creating) null else selected) { id -> creating = false; selectedId = id }
        } else {
            Text(model.t("member.select"))
        }
    }
}

@Composable
private fun MemberForm(model: BooksModel, existing: Member?, onSaved: (String) -> Unit) {
    var name by remember(existing) { mutableStateOf(existing?.displayName.orEmpty()) }
    var kind by remember(existing) { mutableStateOf(existing?.kind ?: MemberKind.ADULT) }
    var birth by remember(existing) { mutableStateOf(existing?.birthDate?.toString().orEmpty()) }
    var archived by remember(existing) { mutableStateOf(existing?.archived ?: false) }

    TextInput(model.t("member.name"), name) { name = it }
    Picker(model.t("member.kind"), MemberKind.entries, kind, { model.t("memberKind.$it") }) { kind = it }
    DateInput(model.t("member.birthDate"), birth, Modifier.fillMaxWidth()) { birth = it }
    if (existing != null) LabeledCheckbox(model.t("category.archived"), archived) { archived = it }
    Button(onClick = {
        val id = model.act {
            val birthDate = birth.trim().ifEmpty { null }?.let {
                runCatching { LocalDate.parse(it) }.getOrElse { throw ValidationException("error.invalidDate") }
            }
            if (existing == null) {
                model.books.members.create(name, kind, birthDate).id
            } else {
                model.books.members.update(existing.copy(displayName = name, kind = kind, birthDate = birthDate, archived = archived))
                existing.id
            }
        }
        if (id != null) onSaved(id)
    }) { Text(model.t("common.save")) }
}
