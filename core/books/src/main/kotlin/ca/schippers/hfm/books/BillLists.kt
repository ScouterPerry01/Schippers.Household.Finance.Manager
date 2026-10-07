package ca.schippers.hfm.books

import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.ocr.FieldExtractor
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** BILL-13: what a bill is for: the household's home or a person's business. */
enum class BillType { HOME, BUSINESS }

/** BILL-13: a bill category of the owner's lists, such as "Essential Housing & Utilities". */
data class BillListCategory(
    val key: String,
    val type: BillType,
    val nameEn: String,
    val nameFr: String,
    val builtIn: Boolean,
    val hidden: Boolean = false,
    /** A built-in category renamed by the household. */
    val changed: Boolean = false,
) {
    fun name(language: Language): String = if (language == Language.FRENCH) nameFr else nameEn
}

/** The heading subcategories are listed under in a category, such as "Utilities". */
data class BillListGroup(val key: String, val categoryKey: String, val nameEn: String, val nameFr: String) {
    fun name(language: Language): String = if (language == Language.FRENCH) nameFr else nameEn
}

/**
 * BILL-13: a bill subcategory, such as "Electricity", with the spending category its payments go to
 * by default and, for a metered utility, the kind of meter (BILL-17).
 */
data class BillSubcategory(
    val key: String,
    val type: BillType,
    val categoryKey: String,
    val groupKey: String?,
    val nameEn: String,
    val nameFr: String,
    val spendingCategoryId: String?,
    val meterKind: MeterKind?,
    val builtIn: Boolean,
    val hidden: Boolean = false,
    /** A built-in subcategory renamed, or given another spending category, by the household. */
    val changed: Boolean = false,
) {
    fun name(language: Language): String = if (language == Language.FRENCH) nameFr else nameEn
}

/** BILL-13, BILL-14: the household's bill lists: the built-in ones with its changes and additions. */
data class BillLists(val categories: List<BillListCategory>, val groups: List<BillListGroup>, val subcategories: List<BillSubcategory>) {

    fun category(key: String?): BillListCategory? = categories.firstOrNull { it.key == key }

    fun group(key: String?): BillListGroup? = groups.firstOrNull { it.key == key }

    fun subcategory(key: String?): BillSubcategory? = subcategories.firstOrNull { it.key == key }

    fun categories(type: BillType, includeHidden: Boolean = false): List<BillListCategory> = categories.filter { it.type == type && (includeHidden || !it.hidden) }

    fun subcategories(categoryKey: String, includeHidden: Boolean = false): List<BillSubcategory> =
        subcategories.filter { it.categoryKey == categoryKey && (includeHidden || !it.hidden) }

    /** "Utilities: Electricity", as in the owner's lists; the name alone when it has no heading or the same one ("Other"). */
    fun label(sub: BillSubcategory, language: Language): String {
        val heading = group(sub.groupKey)?.name(language)
        return if (heading == null || heading == sub.name(language)) sub.name(language) else if (language == Language.FRENCH) "$heading : ${sub.name(language)}" else "$heading: ${sub.name(language)}"
    }

    /** BILL-17: a utility bill, whose statements may carry meter readings. */
    fun isUtility(subcategoryKey: String?): Boolean = subcategory(subcategoryKey)?.let { it.meterKind != null || it.groupKey?.endsWith(".utilities") == true } == true
}

/**
 * BILL-13, BILL-14: the bill classification lists. The built-in lists (English and French, from the
 * owner's lists) ship with the app; the household's changes, renamed, hidden, added and another
 * default spending category, are kept beside the spending categories in the core database, as one
 * setting, since they are names shared by the whole household, not amounts. A built-in entry can
 * always be restored.
 */
class BillListService internal constructor(private val books: Books) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val builtIn: List<DefaultBillCategory> by lazy {
        val text = javaClass.getResourceAsStream("/hfm/books/bill-classification.json")!!.reader(Charsets.UTF_8).use { it.readText() }
        json.decodeFromString<List<DefaultBillCategory>>(text)
    }

    private fun overrides(): BillListChanges =
        books.setting(SETTING)?.let { runCatching { json.decodeFromString(BillListChanges.serializer(), it) }.getOrNull() } ?: BillListChanges()

    private fun save(changes: BillListChanges) {
        requireEditor(books)
        books.putSetting(SETTING, json.encodeToString(BillListChanges.serializer(), changes))
        books.session.audit("UPDATE", "billLists", SETTING)
    }

    /** Every category and subcategory, hidden ones included (marked). */
    fun lists(): BillLists {
        val c = overrides()
        val spending = books.categories.list(includeArchived = true)
        val byKey = spending.mapNotNull { s -> s.systemKey?.let { it to s.id } }.toMap()
        val ids = spending.map { it.id }.toSet()
        val categories = ArrayList<BillListCategory>()
        val groups = ArrayList<BillListGroup>()
        val subs = ArrayList<BillSubcategory>()
        for (cat in builtIn) {
            val type = BillType.valueOf(cat.type)
            val name = c.names[cat.key]
            categories += BillListCategory(cat.key, type, name?.en ?: cat.en, name?.fr ?: cat.fr, true, cat.key in c.hidden, name != null)
            for (g in cat.groups) {
                groups += BillListGroup(g.key, cat.key, g.en, g.fr)
                for (i in g.items) {
                    val n = c.names[i.key]
                    val chosen = c.spending[i.key]
                    val spendingId = if (chosen != null) chosen.ifEmpty { null }?.takeIf { it in ids } else byKey[i.category]
                    subs += BillSubcategory(
                        i.key, type, cat.key, g.key, n?.en ?: i.en, n?.fr ?: i.fr, spendingId, i.meter?.let(MeterKind::valueOf), true,
                        i.key in c.hidden, n != null || chosen != null,
                    )
                }
            }
        }
        for (a in c.addedCategories) {
            val name = c.names[a.key]
            categories += BillListCategory(a.key, BillType.valueOf(a.type), name?.en ?: a.en, name?.fr ?: a.fr, false, a.key in c.hidden)
        }
        for (a in c.added) {
            val cat = categories.firstOrNull { it.key == a.category } ?: continue
            val name = c.names[a.key]
            subs += BillSubcategory(
                a.key, cat.type, cat.key, a.group, name?.en ?: a.en, name?.fr ?: a.fr, c.spending[a.key]?.ifEmpty { null }?.takeIf { it in ids },
                a.meter?.let { runCatching { MeterKind.valueOf(it) }.getOrNull() }, false, a.key in c.hidden,
            )
        }
        return BillLists(categories, groups, subs)
    }

    /** Renames a category or subcategory (BILL-14); a blank name takes the other language's. */
    fun rename(key: String, nameEn: String, nameFr: String) {
        validate(nameEn.isNotBlank() || nameFr.isNotBlank(), "error.nameRequired")
        exists(key)
        val c = overrides()
        save(c.copy(names = c.names + (key to BillListName(nameEn.ifBlank { nameFr }.trim(), nameFr.ifBlank { nameEn }.trim()))))
    }

    /** The spending category a subcategory's bills are paid into by default; null for none. */
    fun setSpendingCategory(subcategoryKey: String, categoryId: String?) {
        validate(lists().subcategory(subcategoryKey) != null, "error.notFound")
        validate(categoryId == null || books.categories.list(includeArchived = true).any { it.id == categoryId }, "error.unknownCategory")
        val c = overrides()
        save(c.copy(spending = c.spending + (subcategoryKey to (categoryId ?: ""))))
    }

    /** Hides a category or subcategory from the choices, or shows it again; bills already classified keep it. */
    fun setHidden(key: String, hidden: Boolean) {
        exists(key)
        val c = overrides()
        save(c.copy(hidden = if (hidden) c.hidden + key else c.hidden - key))
    }

    /** Adds a subcategory to [categoryKey], under one of its headings ([groupKey]) or none. */
    fun addSubcategory(categoryKey: String, groupKey: String?, nameEn: String, nameFr: String, spendingCategoryId: String? = null, meter: MeterKind? = null): BillSubcategory {
        validate(nameEn.isNotBlank() || nameFr.isNotBlank(), "error.nameRequired")
        val lists = lists()
        validate(lists.category(categoryKey) != null, "error.notFound")
        validate(groupKey == null || lists.group(groupKey)?.categoryKey == categoryKey, "error.notFound")
        val key = "user.${Ids.newId()}"
        val c = overrides()
        save(
            c.copy(
                added = c.added + AddedBillSubcategory(key, categoryKey, groupKey, nameEn.ifBlank { nameFr }.trim(), nameFr.ifBlank { nameEn }.trim(), meter?.name),
                spending = if (spendingCategoryId != null) c.spending + (key to spendingCategoryId) else c.spending,
            ),
        )
        return lists().subcategory(key)!!
    }

    /** Adds a category of [type]. */
    fun addCategory(type: BillType, nameEn: String, nameFr: String): BillListCategory {
        validate(nameEn.isNotBlank() || nameFr.isNotBlank(), "error.nameRequired")
        val key = "user.${Ids.newId()}"
        val c = overrides()
        save(c.copy(addedCategories = c.addedCategories + AddedBillCategory(key, type.name, nameEn.ifBlank { nameFr }.trim(), nameFr.ifBlank { nameEn }.trim())))
        return lists().category(key)!!
    }

    /** Puts a built-in entry back as shipped: its names, its spending category, shown. */
    fun restore(key: String) {
        val c = overrides()
        save(c.copy(names = c.names - key, spending = c.spending - key, hidden = c.hidden - key))
    }

    /** Puts every built-in entry back as shipped; what the household added stays. */
    fun restoreAll() {
        val c = overrides()
        val added = (c.added.map { it.key } + c.addedCategories.map { it.key }).toSet()
        save(BillListChanges(c.names.filterKeys { it in added }, c.hidden.filter { it in added }.toSet(), c.spending.filterKeys { it in added }, c.added, c.addedCategories))
    }

    /** The spending category a bill of [subcategoryKey] is paid into by default. */
    fun spendingCategory(subcategoryKey: String?): String? = subcategoryKey?.let { lists().subcategory(it)?.spendingCategoryId }

    /**
     * BILL-18: the subcategory a new bill from [payee] probably has: that of an earlier bill from the
     * same company, else the one whose spending category is the payee's (or [spendingCategoryId],
     * learned from earlier documents), else a guess from common Canadian company names and words.
     */
    fun guess(payee: String?, spendingCategoryId: String? = null): BillSubcategory? {
        val lists = lists()
        val visible = lists.subcategories.filter { !it.hidden && lists.category(it.categoryKey)?.hidden == false }
        if (!payee.isNullOrBlank()) {
            books.bills.list(includeInactive = true).firstOrNull { b ->
                b.subcategoryKey != null && (DocumentService.similarNames(payee, b.payeeName) || DocumentService.similarNames(payee, b.name))
            }?.let { b -> lists.subcategory(b.subcategoryKey)?.let { return it } }
        }
        val category = spendingCategoryId ?: payee?.takeIf { it.isNotBlank() }?.let { p ->
            books.payees.list().firstOrNull { DocumentService.similarNames(it.name, p) }?.defaultCategoryId
        }
        category?.let { id -> visible.sortedBy { it.type }.firstOrNull { it.spendingCategoryId == id }?.let { return it } }
        val folded = " " + FieldExtractor.fold(payee ?: return null) + " "
        val key = KEYWORDS.entries.firstOrNull { (_, words) -> words.any { folded.contains(it) } }?.key ?: return null
        return visible.firstOrNull { it.key == key }
    }

    private fun exists(key: String) {
        val l = lists()
        validate(l.category(key) != null || l.subcategory(key) != null, "error.notFound")
    }

    private companion object {
        const val SETTING = "bills.classification"

        /** BILL-18: words in a company's name that say what its bills are for (folded: lower case, no accents). */
        val KEYWORDS = linkedMapOf(
            // Before electricity: "Énergie Cardio" is a gym.
            "home.lifestyle.gym" to listOf("goodlife", "energie cardio", "gym", "fitness"),
            "home.essential.electricity" to listOf("hydro", "electri", "energie", " power", "toronto hydro", "alectra", "epcor"),
            "home.essential.natural_gas" to listOf("enbridge", "energir", "gaz metro", "fortisbc", "atco gas", " gaz ", " gas ", "mazout", "heating oil"),
            "home.essential.water" to listOf(" water", " eau ", "aqueduc"),
            "home.essential.cell_phone" to listOf("fido", "koodo", "virgin", "freedom", "public mobile", "chatr", "lucky", "mobilite", "mobility", "cellulaire", "wireless", "sans fil"),
            "home.essential.home_internet" to listOf("videotron", "cogeco", "eastlink", "bell", "rogers", "telus", "shaw", "teksavvy", "ebox", "internet"),
            "home.protection.home_insurance" to listOf("assurance habitation", "home insurance", "intact", "desjardins assurances", "belairdirect", "td assurance", "td insurance"),
            "home.lifestyle.streaming" to listOf("netflix", "spotify", "disney", "crave", "prime video", "apple tv", "youtube", "streamco"),
            "home.lifestyle.childcare" to listOf("garderie", "daycare", " cpe ", "childcare"),
        )
    }
}

@Serializable
private data class DefaultBillItem(val key: String, val en: String, val fr: String, val category: String? = null, val meter: String? = null)

@Serializable
private data class DefaultBillGroup(val key: String, val en: String, val fr: String, val items: List<DefaultBillItem>)

@Serializable
private data class DefaultBillCategory(val key: String, val type: String, val en: String, val fr: String, val groups: List<DefaultBillGroup>)

@Serializable
internal data class BillListName(val en: String, val fr: String)

@Serializable
internal data class AddedBillSubcategory(val key: String, val category: String, val group: String? = null, val en: String, val fr: String, val meter: String? = null)

@Serializable
internal data class AddedBillCategory(val key: String, val type: String, val en: String, val fr: String)

/** BILL-14: the household's changes to the bill lists, stored as one core setting. [spending] "" means no category. */
@Serializable
internal data class BillListChanges(
    val names: Map<String, BillListName> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    val spending: Map<String, String> = emptyMap(),
    val added: List<AddedBillSubcategory> = emptyList(),
    val addedCategories: List<AddedBillCategory> = emptyList(),
)
