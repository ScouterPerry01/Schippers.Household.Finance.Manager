package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Box
import ca.schippers.hfm.calc.Province
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import ca.schippers.hfm.books.CategoryRule
import ca.schippers.hfm.books.Institution
import ca.schippers.hfm.books.LinkTarget
import ca.schippers.hfm.books.Member
import ca.schippers.hfm.books.Payee
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.domain.CategoryKind
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.domain.TaxFlag
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.MoneyFormat
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

/** CAT-01 and BILL-14: the spending categories, and the bill lists beside them. */
@Composable
fun CategoriesScreen(model: BooksModel) {
    var bills by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        androidx.compose.material3.PrimaryTabRow(selectedTabIndex = if (bills) 1 else 0, modifier = Modifier.padding(horizontal = 12.dp)) {
            androidx.compose.material3.Tab(selected = !bills, onClick = { bills = false }, text = { Text(model.t("category.tab.spending")) })
            androidx.compose.material3.Tab(selected = bills, onClick = { bills = true }, text = { Text(model.t("category.tab.bills")) })
        }
        Box(Modifier.weight(1f)) { if (bills) BillListsEditor(model) else SpendingCategories(model) }
    }
}

@Composable
private fun SpendingCategories(model: BooksModel) {
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
        // M-77: the list is read only for a viewer.
        if (books.canEdit) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { creating = null to CategoryKind.EXPENSE; selectedId = null }) { Text(model.t("category.addExpense")) }
                OutlinedButton(onClick = { creating = null to CategoryKind.INCOME; selectedId = null }) { Text(model.t("category.addIncome")) }
                if (selected != null) OutlinedButton(onClick = { creating = selected to selected.kind; selectedId = null }) { Text(model.t("category.addChild")) }
            }
        }
        val target = creating
        when {
            target != null -> CategoryForm(model, null, target.first, target.second, tree) { id -> creating = null; selectedId = id }
            selected != null -> CategoryForm(model, selected, null, selected.kind, tree) { selectedId = it }
            else -> Text(model.t("category.select"))
        }
    }
}

@Composable
private fun CategoryForm(model: BooksModel, existing: Category?, parent: Category?, kind: CategoryKind, tree: List<Pair<Category, Int>>, onSaved: (String) -> Unit) {
    val editable = model.books.canEdit
    var nameEn by remember(existing, parent) { mutableStateOf(existing?.nameEn.orEmpty()) }
    var nameFr by remember(existing, parent) { mutableStateOf(existing?.nameFr.orEmpty()) }
    var tax by remember(existing, parent) { mutableStateOf(existing?.taxFlag ?: parent?.taxFlag) }
    var archived by remember(existing) { mutableStateOf(existing?.archived ?: false) }
    var parentId by remember(existing) { mutableStateOf(existing?.parentId) }

    Text(
        when {
            existing != null -> existing.name(model.language)
            parent != null -> model.t("category.newUnder", parent.name(model.language))
            else -> model.t("category.new.$kind")
        },
        style = MaterialTheme.typography.titleMedium,
    )
    TextInput(model.t("category.nameEn"), nameEn, enabled = editable) { nameEn = it }
    TextInput(model.t("category.nameFr"), nameFr, enabled = editable) { nameFr = it }
    if (existing != null) {
        // M-75: another parent of the same kind, never the category itself or one inside it.
        val inside = remember(tree, existing) {
            val ids = mutableSetOf(existing.id)
            tree.forEach { (c, _) -> if (c.parentId in ids) ids += c.id }
            ids
        }
        val parents = listOf<Pair<Category, Int>?>(null) + tree.filter { (c, _) -> c.kind == existing.kind && c.id !in inside }
        Picker(
            model.t("category.parent"), parents, tree.firstOrNull { it.first.id == parentId },
            { it?.first?.name(model.language) ?: model.t("category.topLevel") }, indent = { it?.second ?: 0 }, enabled = editable,
        ) { parentId = it?.first?.id }
    }
    Picker(model.t("category.tax"), listOf<TaxFlag?>(null) + TaxFlag.entries, tax, { it?.let { f -> model.t("tax.$f") } ?: model.t("common.none") }, enabled = editable) { tax = it }
    if (existing != null) LabeledCheckbox(model.t("category.archived"), archived, enabled = editable) { archived = it }
    // CAL-09: the person's work and school schedules, also reached from the Calendar.
    if (existing != null) {
        var schedules by remember(existing) { mutableStateOf(false) }
        OutlinedButton(onClick = { schedules = true }) { Text(model.t("schedule.title")) }
        if (schedules) SchedulesDialog(model, existing.id) { schedules = false }
    }
    if (editable) {
        Button(enabled = nameEn.isNotBlank() || nameFr.isNotBlank(), onClick = {
            val id = model.act {
                if (existing == null) {
                    model.books.categories.create(parent?.id, nameEn, nameFr, kind, tax).id
                } else {
                    model.books.categories.update(existing.copy(parentId = parentId, nameEn = nameEn, nameFr = nameFr, taxFlag = tax, archived = archived))
                    existing.id
                }
            }
            if (id != null) onSaved(id)
        }) { Text(model.t("common.save")) }
    } else {
        Text(model.t("common.readOnlyViewer"), style = MaterialTheme.typography.bodySmall)
    }
}

// --- Bill lists (BILL-13, BILL-14) -------------------------------------------------------------

/** One line of the bill lists: a category or a subcategory. */
private data class BillListLine(val key: String, val category: ca.schippers.hfm.books.BillListCategory?, val sub: ca.schippers.hfm.books.BillSubcategory?)

@Composable
private fun BillListsEditor(model: BooksModel) {
    val books = model.books
    val lists = remember(model.revision) { books.billLists.lists() }
    val tree = remember(model.revision) { books.categories.tree() }
    val lines = remember(lists) {
        ca.schippers.hfm.books.BillType.entries.flatMap { type ->
            lists.categories(type, includeHidden = true).flatMap { c ->
                listOf(BillListLine(c.key, c, null)) + lists.subcategories(c.key, includeHidden = true).map { BillListLine(it.key, null, it) }
            }
        }
    }
    var selected by remember { mutableStateOf<String?>(null) }
    /** A new category of this type, or a new subcategory of this category. */
    var newCategory by remember { mutableStateOf<ca.schippers.hfm.books.BillType?>(null) }
    var newSubOf by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    val line = lines.firstOrNull { it.key == selected }
    val editable = books.canEdit

    ListEditor(
        model.t("billLists.title"), lines, key = { it.key },
        label = { l ->
            l.category?.let { "${model.t("billType.${it.type}")} · ${it.name(model.language)}" } ?: l.sub?.let { lists.label(it, model.language) }.orEmpty()
        },
        indent = { if (it.sub != null) 1 else 0 },
        dimmed = { it.category?.hidden == true || it.sub?.hidden == true },
        addLabel = null, onAdd = {}, selectedKey = selected,
        onSelect = { selected = it.key; newCategory = null; newSubOf = null },
    ) {
        Text(model.t("billLists.explain"), style = MaterialTheme.typography.bodySmall)
        if (editable) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in ca.schippers.hfm.books.BillType.entries) {
                    OutlinedButton(onClick = { newCategory = t; newSubOf = null; selected = null }) { Text(model.t("billLists.addCategory.$t")) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val categoryKey = line?.category?.key ?: line?.sub?.categoryKey
                if (categoryKey != null) OutlinedButton(onClick = { newSubOf = categoryKey; newCategory = null; selected = null }) { Text(model.t("billLists.addSubcategory")) }
                TextButton(onClick = { confirmRestore = true }) { Text(model.t("billLists.restoreAll")) }
            }
        }
        HorizontalDivider()
        val forType = newCategory
        val forCategory = newSubOf
        when {
            forType != null -> BillListNameForm(model, model.t("billLists.newCategory.$forType"), "", "", null, null, false, null, tree) { en, fr, _, _ ->
                model.act { books.billLists.addCategory(forType, en, fr) }?.let { selected = it.key; newCategory = null }
            }
            forCategory != null -> {
                val groups = lists.groups.filter { it.categoryKey == forCategory }
                BillListNameForm(model, model.t("billLists.newSubcategory", lists.category(forCategory)?.name(model.language).orEmpty()), "", "", null, groups, false, null, tree) { en, fr, group, category ->
                    model.act { books.billLists.addSubcategory(forCategory, group, en, fr, category) }?.let { selected = it.key; newSubOf = null }
                }
            }
            line?.category != null -> {
                val c = line.category
                BillListNameForm(model, c.name(model.language), c.nameEn, c.nameFr, null, null, c.hidden, null, tree, builtIn = c.builtIn && (c.changed || c.hidden), onRestore = {
                    model.act { books.billLists.restore(c.key) }
                }, onHidden = { model.act { books.billLists.setHidden(c.key, it) } }) { en, fr, _, _ ->
                    model.act { books.billLists.rename(c.key, en, fr) }
                }
            }
            line?.sub != null -> {
                val sub = line.sub
                BillListNameForm(
                    model, lists.label(sub, model.language), sub.nameEn, sub.nameFr, null, null, sub.hidden, sub.spendingCategoryId, tree, spending = true,
                    builtIn = sub.builtIn && (sub.changed || sub.hidden), onRestore = { model.act { books.billLists.restore(sub.key) } },
                    onHidden = { model.act { books.billLists.setHidden(sub.key, it) } },
                ) { en, fr, _, category ->
                    model.act {
                        books.billLists.rename(sub.key, en, fr)
                        if (category != sub.spendingCategoryId) books.billLists.setSpendingCategory(sub.key, category)
                    }
                }
            }
            else -> Text(model.t("billLists.select"))
        }
        if (!editable) Text(model.t("common.readOnlyViewer"), style = MaterialTheme.typography.bodySmall)
    }
    if (confirmRestore) {
        FormDialog(model.t("billLists.restoreAll"), model.t("billLists.restore"), model.t("common.cancel"), onDismiss = { confirmRestore = false }, onSave = {
            model.act { books.billLists.restoreAll() }
            confirmRestore = false
        }) { Text(model.t("billLists.restoreAll.body")) }
    }
}

/**
 * The names of a bill list entry in both languages and, for a subcategory, its spending category
 * and (when new) its heading. [onSave] gets the names, the heading and the spending category.
 */
@Composable
private fun BillListNameForm(
    model: BooksModel, title: String, en0: String, fr0: String, group0: String?, groups: List<ca.schippers.hfm.books.BillListGroup>?, hidden0: Boolean,
    spending0: String?, tree: List<Pair<Category, Int>>, spending: Boolean = groups != null, builtIn: Boolean = false, onRestore: (() -> Unit)? = null,
    onHidden: ((Boolean) -> Unit)? = null, onSave: (String, String, String?, String?) -> Unit,
) {
    val editable = model.books.canEdit
    var en by remember(title, en0) { mutableStateOf(en0) }
    var fr by remember(title, fr0) { mutableStateOf(fr0) }
    var group by remember(title) { mutableStateOf(group0 ?: groups?.firstOrNull()?.key) }
    var category by remember(title, spending0) { mutableStateOf(spending0) }
    Text(title, style = MaterialTheme.typography.titleMedium)
    TextInput(model.t("category.nameEn"), en, enabled = editable) { en = it }
    TextInput(model.t("category.nameFr"), fr, enabled = editable) { fr = it }
    if (!groups.isNullOrEmpty()) {
        Picker(model.t("billLists.heading"), listOf(null) + groups, groups.firstOrNull { it.key == group }, { it?.name(model.language) ?: model.t("common.none") }, enabled = editable) { group = it?.key }
    }
    if (spending) {
        Picker(
            model.t("billLists.spendingCategory"), listOf<Pair<Category, Int>?>(null) + tree, tree.firstOrNull { it.first.id == category },
            { it?.first?.name(model.language) ?: model.t("common.none") }, indent = { it?.second ?: 0 }, enabled = editable,
        ) { category = it?.first?.id }
        Text(model.t("billLists.spendingCategory.hint"), style = MaterialTheme.typography.bodySmall)
    }
    if (onHidden != null) LabeledCheckbox(model.t("billLists.hidden"), hidden0, enabled = editable) { onHidden(it) }
    if (editable) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = en.isNotBlank() || fr.isNotBlank(), onClick = { onSave(en, fr, group, category) }) { Text(model.t("common.save")) }
            if (builtIn && onRestore != null) OutlinedButton(onClick = onRestore) { Text(model.t("billLists.restore")) }
        }
    }
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
        addLabel = model.t("common.add").takeIf { books.canEdit }, onAdd = { creating = true; selectedId = null }, selectedKey = selectedId,
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
    val editable = model.books.canEdit
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var categoryId by remember(existing) { mutableStateOf(existing?.defaultCategoryId) }
    var archived by remember(existing) { mutableStateOf(existing?.archived ?: false) }
    var alias by remember(existing) { mutableStateOf("") }

    TextInput(model.t("payee.name"), name, enabled = editable) { name = it }
    Picker(
        model.t("payee.defaultCategory"), listOf<Pair<Category, Int>?>(null) + tree, tree.firstOrNull { it.first.id == categoryId },
        { it?.first?.name(model.language) ?: model.t("common.none") }, indent = { it?.second ?: 0 }, enabled = editable,
    ) { categoryId = it?.first?.id }
    if (existing != null) {
        LabeledCheckbox(model.t("category.archived"), archived, enabled = editable) { archived = it }
        // M-74: the payee's aliases, listed.
        val aliases = remember(model.revision, existing) { model.books.payees.aliases(existing.id) }
        Text(model.t("payee.aliases"), style = MaterialTheme.typography.titleSmall)
        if (aliases.isEmpty()) Text(model.t("payee.noAliases"), style = MaterialTheme.typography.bodySmall)
        for (a in aliases) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.pattern, Modifier.weight(1f))
                if (editable) TextButton(onClick = { model.act { model.books.payees.removeAlias(a.id) } }) { Text(model.t("payee.removeAlias")) }
            }
        }
        if (editable) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextInput(model.t("payee.alias"), alias, Modifier.weight(1f), supporting = model.t("payee.alias.hint")) { alias = it }
                OutlinedButton(onClick = { if (model.act { model.books.payees.addAlias(existing.id, alias) } != null) alias = "" }) {
                    Text(model.t("payee.addAlias"))
                }
            }
        }
        LinkedContacts(model, LinkTarget.PAYEE, existing.id, suggestedName = existing.name)
    }
    if (editable) {
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
    } else {
        Text(model.t("common.readOnlyViewer"), style = MaterialTheme.typography.bodySmall)
    }
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
        addLabel = model.t("common.add").takeIf { model.books.canEdit }, onAdd = { creating = true; selectedId = null }, selectedKey = selectedId,
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
    val editable = model.books.canEdit
    var value by remember(existing) { mutableStateOf(existing ?: Institution("", "")) }
    TextInput(model.t("institution.name"), value.name, enabled = editable) { value = value.copy(name = it) }
    TextInput(model.t("institution.branch"), value.branch.orEmpty(), enabled = editable) { value = value.copy(branch = it) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextInput(model.t("institution.number"), value.institutionNumber.orEmpty(), Modifier.weight(1f), enabled = editable) { value = value.copy(institutionNumber = it) }
        TextInput(model.t("institution.transit"), value.transitNumber.orEmpty(), Modifier.weight(1f), enabled = editable) { value = value.copy(transitNumber = it) }
    }
    TextInput(model.t("institution.website"), value.website.orEmpty(), enabled = editable) { value = value.copy(website = it) }
    TextInput(model.t("institution.phone"), value.phone.orEmpty(), enabled = editable) { value = value.copy(phone = it) }
    TextInput(model.t("account.notes"), value.notes.orEmpty(), singleLine = false, enabled = editable) { value = value.copy(notes = it) }
    // CON-04: the contact kept for this institution, with its people and what each is for.
    if (existing != null) LinkedContacts(model, LinkTarget.INSTITUTION, existing.id, suggestedName = existing.name)
    if (!editable) {
        Text(model.t("common.readOnlyViewer"), style = MaterialTheme.typography.bodySmall)
        return
    }
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

    val province = remember(model.revision) { model.books.province }
    Column(Modifier.fillMaxSize()) {
        // PROV-01: the household's province or territory, whose rules apply unless a person has their own.
        Row(Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Picker(model.t("household.province"), Province.entries.sortedBy { model.t("province.$it") }, province, { model.t("province.$it") }, Modifier.width(360.dp), enabled = model.books.users.isAdministrator) {
                if (it != province) model.act { model.books.setProvince(it) }
            }
            Text(model.t("household.provinceHint"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
        // M-71: the household's name, which an administrator can change.
        if (model.books.users.isAdministrator) {
            var householdName by remember(model.revision) { mutableStateOf(model.books.householdName) }
            Row(Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextInput(model.t("create.name"), householdName, Modifier.width(360.dp)) { householdName = it }
                OutlinedButton(onClick = { model.act { model.books.renameHousehold(householdName) } }, enabled = householdName.isNotBlank() && householdName.trim() != model.books.householdName) {
                    Text(model.t("household.rename"))
                }
            }
        }
        Box(Modifier.weight(1f)) {
            ListEditor(
                model.t("nav.members"), members, key = { it.id },
                label = { listOfNotNull(it.displayName, model.t("memberKind.${it.kind}"), it.province?.let { p -> model.t("province.$p") }).joinToString(" · ") }, dimmed = { it.archived },
                addLabel = model.t("common.add").takeIf { model.books.users.isAdministrator }, onAdd = { creating = true; selectedId = null }, selectedKey = selectedId,
                onSelect = { selectedId = it.id; creating = false },
            ) {
                if (creating || selected != null) {
                    MemberForm(model, if (creating) null else selected) { id -> creating = false; selectedId = id }
                } else {
                    Text(model.t("member.select"))
                }
            }
        }
    }
}

@Composable
private fun MemberForm(model: BooksModel, existing: Member?, onSaved: (String) -> Unit) {
    var name by remember(existing) { mutableStateOf(existing?.displayName.orEmpty()) }
    var kind by remember(existing) { mutableStateOf(existing?.kind ?: MemberKind.ADULT) }
    var birth by remember(existing) { mutableStateOf(existing?.birthDate?.toString().orEmpty()) }
    var archived by remember(existing) { mutableStateOf(existing?.archived ?: false) }
    var province by remember(existing) { mutableStateOf(existing?.province) }
    // M-76: only an administrator changes household members; others see the form read only.
    val editable = model.books.users.isAdministrator

    TextInput(model.t("member.name"), name, enabled = editable) { name = it }
    Picker(model.t("member.kind"), MemberKind.entries, kind, { model.t("memberKind.$it") }, enabled = editable) { kind = it }
    DateInput(model.t("member.birthDate"), birth, Modifier.fillMaxWidth(), enabled = editable) { birth = it }
    Picker(
        model.t("member.province"), listOf<Province?>(null) + Province.entries.sortedBy { model.t("province.$it") }, province,
        { it?.let { p -> model.t("province.$p") } ?: model.t("member.provinceHousehold") }, enabled = editable,
    ) { province = it }
    if (existing != null) LabeledCheckbox(model.t("category.archived"), archived, enabled = editable) { archived = it }
    if (!editable) {
        Text(model.t("member.readOnly"), style = MaterialTheme.typography.bodySmall)
        return
    }
    Button(onClick = {
        val id = model.act {
            val birthDate = birth.trim().ifEmpty { null }?.let {
                runCatching { LocalDate.parse(it) }.getOrElse { throw ValidationException("error.invalidDate") }
            }
            if (existing == null) {
                model.books.members.create(name, kind, birthDate).also { m -> if (province != null) model.books.members.update(m.copy(province = province)) }.id
            } else {
                model.books.members.update(existing.copy(displayName = name, kind = kind, birthDate = birthDate, archived = archived, province = province))
                existing.id
            }
        }
        if (id != null) onSaved(id)
    }) { Text(model.t("common.save")) }
}

// --- Category rules (CAT-02) -------------------------------------------------------------------

@Composable
fun RulesScreen(model: BooksModel) {
    val books = model.books
    val rules = remember(model.revision) { books.rules.list() }
    val tree = remember(model.revision) { books.categories.tree() }
    val names = remember(tree) { tree.associate { it.first.id to it.first.name(model.language) } }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val selected = rules.firstOrNull { it.id == selectedId }

    val payees = remember(model.revision) { books.payees.list() }
    val payeeNames = remember(model.revision) { books.payees.list(includeArchived = true).associate { it.id to it.name } }

    ListEditor(
        model.t("nav.rules"), rules, key = { it.id },
        label = { r -> model.t("rule.summary", r.payeeContains, names[r.categoryId] ?: "?") },
        addLabel = model.t("common.add").takeIf { books.canEdit }, onAdd = { creating = true; selectedId = null }, selectedKey = selectedId,
        onSelect = { selectedId = it.id; creating = false },
    ) {
        Text(model.t("rule.explain"), style = MaterialTheme.typography.bodySmall)
        when {
            creating -> RuleForm(model, null, tree, payees) { creating = false; selectedId = it }
            selected != null -> {
                Text(model.t("rule.summary", selected.payeeContains, names[selected.categoryId] ?: "?"), style = MaterialTheme.typography.titleMedium)
                listOfNotNull(
                    selected.amountMin?.let { model.t("rule.min") + " " + model.money(it) }, selected.amountMax?.let { model.t("rule.max") + " " + model.money(it) },
                    selected.payeeId?.let { payeeNames[it] }?.let { model.t("rule.payeeIs", it) },
                ).forEach { Text(it) }
                if (books.canEdit) {
                    // M-73: the order the rules are tried in.
                    val index = rules.indexOfFirst { it.id == selected.id }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = index > 0, onClick = { model.act { books.rules.move(selected.id, up = true) } }) { Text(model.t("rule.moveUp")) }
                        OutlinedButton(enabled = index < rules.size - 1, onClick = { model.act { books.rules.move(selected.id, up = false) } }) { Text(model.t("rule.moveDown")) }
                        OutlinedButton(onClick = { if (model.act { books.rules.delete(selected.id) } != null) selectedId = null }) { Text(model.t("common.delete")) }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(model.t("common.edit"), style = MaterialTheme.typography.titleSmall)
                    RuleForm(model, selected, tree, payees) { selectedId = it }
                }
            }
            else -> Text(model.t("rule.select"))
        }
    }
}

/** A new rule, or the changes to [existing] (M-73), with the payee it may also set. */
@Composable
private fun RuleForm(model: BooksModel, existing: CategoryRule?, tree: List<Pair<Category, Int>>, payees: List<Payee>, onSaved: (String) -> Unit) {
    val locale = model.language.locale
    val currency = remember { Currency.of(model.session.core.coreQueries.household().executeAsOne().base_currency) }
    val ruleCurrency = existing?.let { it.amountMin?.currency ?: it.amountMax?.currency } ?: currency
    var contains by remember(existing) { mutableStateOf(existing?.payeeContains.orEmpty()) }
    var category by remember(existing) { mutableStateOf(tree.firstOrNull { it.first.id == existing?.categoryId }) }
    var payeeId by remember(existing) { mutableStateOf(existing?.payeeId) }
    var min by remember(existing) { mutableStateOf(existing?.amountMin?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var max by remember(existing) { mutableStateOf(existing?.amountMax?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }

    TextInput(model.t("rule.contains"), contains, supporting = model.t("rule.contains.hint")) { contains = it }
    Picker(model.t("register.category"), tree, category, { it.first.name(model.language) }, indent = { it.second }) { category = it }
    Picker(model.t("rule.payee"), listOf<Payee?>(null) + payees, payees.firstOrNull { it.id == payeeId }, { it?.name ?: model.t("rule.payeeKeep") }) { payeeId = it?.id }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AmountInput(model.t("rule.min"), min, ruleCurrency, locale, Modifier.weight(1f), model::money) { min = it }
        AmountInput(model.t("rule.max"), max, ruleCurrency, locale, Modifier.weight(1f), model::money) { max = it }
    }
    Button(enabled = contains.isNotBlank() && category != null, onClick = {
        val rule = model.act {
            val low = parseAmount(min, ruleCurrency, locale)
            val high = parseAmount(max, ruleCurrency, locale)
            if (existing == null) {
                model.books.rules.create(contains, category!!.first.id, low, high, payeeId)
            } else {
                model.books.rules.update(existing.id, contains, category!!.first.id, low, high, payeeId)
            }
        }
        if (rule != null) onSaved(rule.id)
    }) { Text(model.t("common.save")) }
}
