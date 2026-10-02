package ca.schippers.hfm.books

import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.domain.CategoryKind
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.domain.TaxFlag
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Household members (HH-01): people who own accounts, receive care, hold plans. */
class MemberService internal constructor(private val books: Books) {

    fun list(includeArchived: Boolean = false): List<Member> = books.core.members().executeAsList()
        .map { Member(it.id, it.display_name, MemberKind.valueOf(it.kind), it.birth_date?.let(LocalDate::parse), it.archived == 1L) }
        .filter { includeArchived || !it.archived }

    fun create(displayName: String, kind: MemberKind, birthDate: LocalDate? = null): Member {
        requireAdmin(books)
        validate(displayName.isNotBlank(), "error.nameRequired")
        val id = Ids.newId()
        books.core.insertMember(id, displayName.trim(), kind.name, birthDate?.toString(), books.now())
        books.session.audit("CREATE", "member", id)
        return Member(id, displayName.trim(), kind, birthDate, archived = false)
    }

    fun update(member: Member) {
        requireAdmin(books)
        validate(member.displayName.isNotBlank(), "error.nameRequired")
        books.core.updateMember(member.displayName.trim(), member.kind.name, member.birthDate?.toString(), if (member.archived) 1 else 0, member.id)
        books.session.audit("UPDATE", "member", member.id)
    }

    /** Links a sign-in account to the person it belongs to (HH-09). */
    fun linkUser(userId: String, memberId: String?) {
        requireAdmin(books)
        books.core.linkUserToMember(memberId, userId)
        books.session.audit("LINK", "app_user", userId, memberId)
    }
}

/** Financial institutions (ACC-01). */
class InstitutionService internal constructor(private val books: Books) {

    fun list(): List<Institution> = books.core.institutions().executeAsList().map {
        Institution(it.id, it.name, it.branch, it.institution_number, it.transit_number, it.website, it.phone, it.notes)
    }

    fun create(institution: Institution): Institution {
        validate(institution.name.isNotBlank(), "error.nameRequired")
        validateNumbers(institution)
        val id = Ids.newId()
        with(institution) {
            books.core.insertInstitution(id, name.trim(), branch, institutionNumber, transitNumber, website, phone, notes, books.now())
        }
        books.session.audit("CREATE", "institution", id)
        return institution.copy(id = id, name = institution.name.trim())
    }

    fun update(institution: Institution) {
        validate(institution.name.isNotBlank(), "error.nameRequired")
        validateNumbers(institution)
        with(institution) {
            books.core.updateInstitution(name.trim(), branch, institutionNumber, transitNumber, website, phone, notes, id)
        }
        books.session.audit("UPDATE", "institution", institution.id)
    }

    /** Canadian institution numbers have 3 digits and transit (branch) numbers 5. */
    private fun validateNumbers(institution: Institution) {
        institution.institutionNumber?.takeIf { it.isNotBlank() }?.let {
            validate(it.matches(Regex("\\d{3}")), "error.institutionNumber")
        }
        institution.transitNumber?.takeIf { it.isNotBlank() }?.let {
            validate(it.matches(Regex("\\d{5}")), "error.transitNumber")
        }
    }
}

/** The bilingual category tree (CAT-01) with tax flags (CAT-05). */
class CategoryService internal constructor(private val books: Books) {

    fun list(includeArchived: Boolean = false): List<Category> = books.core.categories().executeAsList()
        .map {
            Category(
                it.id, it.parent_id, it.system_key, it.name_en, it.name_fr, CategoryKind.valueOf(it.kind),
                it.tax_flag?.let(TaxFlag::valueOf), it.sort_order.toInt(), it.archived == 1L,
            )
        }
        .filter { includeArchived || !it.archived }

    /** Categories in tree order, each with its depth, for pickers and the category editor. */
    fun tree(includeArchived: Boolean = false): List<Pair<Category, Int>> {
        val all = list(includeArchived)
        val children = all.groupBy { it.parentId }
        val out = ArrayList<Pair<Category, Int>>()
        fun walk(parentId: String?, depth: Int) {
            for (c in children[parentId].orEmpty().sortedWith(compareBy({ it.kind }, { it.sortOrder }, { it.nameEn }))) {
                out += c to depth
                walk(c.id, depth + 1)
            }
        }
        walk(null, 0)
        return out
    }

    fun create(parentId: String?, nameEn: String, nameFr: String, kind: CategoryKind, taxFlag: TaxFlag? = null): Category {
        validate(nameEn.isNotBlank() || nameFr.isNotBlank(), "error.nameRequired")
        val parent = parentId?.let { id -> list(includeArchived = true).firstOrNull { it.id == id } ?: throw ValidationException("error.unknownCategory") }
        validate(parent == null || parent.kind == kind, "error.categoryKind")
        val en = nameEn.ifBlank { nameFr }.trim()
        val fr = nameFr.ifBlank { nameEn }.trim()
        val order = list(includeArchived = true).count { it.parentId == parentId }
        val id = Ids.newId()
        books.core.insertCategory(id, parentId, null, en, fr, kind.name, taxFlag?.name, order.toLong())
        books.session.audit("CREATE", "category", id)
        return Category(id, parentId, null, en, fr, kind, taxFlag, order, archived = false)
    }

    fun update(category: Category) {
        validate(category.parentId != category.id, "error.categoryCycle")
        val all = list(includeArchived = true).associateBy { it.id }
        var ancestor = category.parentId
        while (ancestor != null) {
            validate(ancestor != category.id, "error.categoryCycle")
            ancestor = all[ancestor]?.parentId
        }
        books.core.updateCategory(
            category.parentId, category.nameEn.trim(), category.nameFr.trim(), category.taxFlag?.name,
            category.sortOrder.toLong(), if (category.archived) 1 else 0, category.id,
        )
        books.session.audit("UPDATE", "category", category.id)
    }

    /**
     * Seeds the default tree the first time a household is opened. Households created before a
     * default category was added receive it once (CAT-06); one the user deleted is not brought back.
     */
    internal fun ensureDefaults() {
        val text = javaClass.getResourceAsStream("/hfm/books/default-categories.json")!!.reader(Charsets.UTF_8).use { it.readText() }
        val roots = Json.decodeFromString<List<DefaultCategory>>(text)
        val version = books.setting(DEFAULTS_VERSION)?.toIntOrNull() ?: 1
        if (books.core.categoryCount().executeAsOne() > 0) {
            if (version < 2) addMissing(roots, ADDED_IN_2)
            return
        }
        books.session.core.transaction {
            fun insert(node: DefaultCategory, parentId: String?, kind: String, index: Int) {
                val id = Ids.newId()
                val nodeKind = node.kind ?: kind
                books.core.insertCategory(id, parentId, node.key, node.en, node.fr, nodeKind, node.tax, index.toLong())
                node.children.forEachIndexed { i, child -> insert(child, id, nodeKind, i) }
            }
            roots.forEachIndexed { i, root -> insert(root, null, root.kind ?: CategoryKind.EXPENSE.name, i) }
            books.putSetting(DEFAULTS_VERSION, CURRENT_DEFAULTS.toString())
        }
    }

    /** Inserts the default categories in [keys] that are missing, under their default parent if it still exists. */
    private fun addMissing(roots: List<DefaultCategory>, keys: Set<String>) {
        val existing = books.core.categories().executeAsList()
        val byKey = existing.mapNotNull { row -> row.system_key?.let { it to row } }.toMap().toMutableMap()
        books.session.core.transaction {
            fun visit(node: DefaultCategory, parentKey: String?, kind: String, index: Int) {
                val nodeKind = node.kind ?: kind
                if (node.key in keys && node.key !in byKey) {
                    val parent = parentKey?.let(byKey::get)
                    if (parentKey == null || parent != null) {
                        val id = Ids.newId()
                        books.core.insertCategory(id, parent?.id, node.key, node.en, node.fr, parent?.kind ?: nodeKind, node.tax, index.toLong())
                        byKey[node.key] = books.core.categories().executeAsList().first { it.id == id }
                    }
                }
                node.children.forEachIndexed { i, child -> visit(child, node.key, nodeKind, i) }
            }
            roots.forEachIndexed { i, root -> visit(root, null, root.kind ?: CategoryKind.EXPENSE.name, i) }
            books.putSetting(DEFAULTS_VERSION, CURRENT_DEFAULTS.toString())
        }
    }

    private companion object {
        const val DEFAULTS_VERSION = "categories.defaultsVersion"
        const val CURRENT_DEFAULTS = 2

        /** Default categories added in version 2 (CAT-06). */
        val ADDED_IN_2 = setOf("transport.transit.pass", "transport.transit.fares", "pets.licence", "pets.insurance", "pets.boarding")
    }

    @Serializable
    private data class DefaultCategory(
        val key: String,
        val en: String,
        val fr: String,
        val kind: String? = null,
        val tax: String? = null,
        val children: List<DefaultCategory> = emptyList(),
    )
}

/** Payees with aliases that map statement text to a clean name (section 7.4). */
class PayeeService internal constructor(private val books: Books) {

    fun list(includeArchived: Boolean = false): List<Payee> = books.core.payees().executeAsList()
        .map { Payee(it.id, it.name, it.default_category_id, it.archived == 1L) }
        .filter { includeArchived || !it.archived }

    fun create(name: String, defaultCategoryId: String? = null): Payee {
        validate(name.isNotBlank(), "error.nameRequired")
        val id = Ids.newId()
        books.core.insertPayee(id, name.trim(), defaultCategoryId)
        return Payee(id, name.trim(), defaultCategoryId, archived = false)
    }

    fun update(payee: Payee) {
        validate(payee.name.isNotBlank(), "error.nameRequired")
        books.core.updatePayee(payee.name.trim(), payee.defaultCategoryId, if (payee.archived) 1 else 0, payee.id)
    }

    /** "AMZN MKTP CA*2X4" → Amazon: an alias pattern contained in the text, ignoring case. */
    fun addAlias(payeeId: String, pattern: String) {
        validate(pattern.isNotBlank(), "error.nameRequired")
        books.core.insertPayeeAlias(Ids.newId(), payeeId, pattern.trim())
    }

    /** Finds the payee for text typed or imported, by alias, then exact name. */
    fun match(text: String): Payee? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val payees = list(includeArchived = true).associateBy { it.id }
        val byAlias = books.core.payeeAliases().executeAsList()
            .filter { trimmed.contains(it.pattern, ignoreCase = true) }
            .maxByOrNull { it.pattern.length }
        byAlias?.let { alias -> payees[alias.payee_id]?.let { return it } }
        return books.core.payeeByName(trimmed).executeAsOneOrNull()?.let { Payee(it.id, it.name, it.default_category_id, it.archived == 1L) }
    }

    companion object {
        /**
         * A tidier name for a payee first seen on a statement: store numbers removed and capitals
         * softened ("COSTCO WHOLESALE W512" becomes "Costco Wholesale"). The original text is kept
         * on the transaction.
         */
        fun cleanName(text: String): String {
            var s = text.trim().replace(Regex("""\s+"""), " ")
            s = s.replace(Regex("""\s*#\s*\d+.*$"""), "") // "IGA EXTRA #8123"
            s = s.replace(Regex("""\s+[A-Z]?\d{3,}$"""), "") // "COSTCO WHOLESALE W512"
            if (s.isEmpty()) return text.trim()
            if (s.none { it.isLowerCase() }) {
                s = s.lowercase().split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
            }
            return s
        }
    }

    internal fun findOrCreate(text: String): Payee? {
        if (text.isBlank()) return null
        return match(text) ?: create(text)
    }
}

internal fun requireAdmin(books: Books) {
    if (books.role != Role.ADMINISTRATOR) throw AccessDeniedException("Only an administrator can do this")
}
