package ca.schippers.hfm.books

import ca.schippers.hfm.data.ledger.CustomSplits
import ca.schippers.hfm.domain.CategoryKind
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.serialization.Serializable

/** RPT-03: what a custom report's rows or columns are. */
enum class ReportDimension { CATEGORY, TOP_CATEGORY, PAYEE, ACCOUNT, PERSON, TAG, MONTH, QUARTER, YEAR }

/** RPT-03: what is added up: spending (shown positive), income, or income less spending. */
enum class ReportMeasure { SPENDING, INCOME, NET }

enum class ReportChart { BARS, LINES, RANKED, TABLE }

/** RPT-03: a custom report's layout; the period and filters come with the report filter. */
@Serializable
data class CustomLayout(
    val rows: ReportDimension = ReportDimension.TOP_CATEGORY,
    /** Null for a single column of totals. */
    val columns: ReportDimension? = ReportDimension.MONTH,
    val measure: ReportMeasure = ReportMeasure.SPENDING,
    val chart: ReportChart = ReportChart.BARS,
    /** Rows beyond this many, by size, are added together as "Other". */
    val maxRows: Int = 12,
    /** Section 12 filter: only this category and its subcategories; null for every category. */
    val categoryId: String? = null,
)

/** A row or column of a pivot: [id] identifies it (a category id, "2026-03"...), [label] is shown. */
data class PivotKey(val id: String?, val label: String)

/**
 * RPT-03: the result of a custom report: [cells] by row and column key ids, in the report currency.
 * Rows are sorted largest first; time columns in order. [other] is the row the smallest rows went into.
 */
data class PivotTable(
    val rows: List<PivotKey>,
    val columns: List<PivotKey>,
    val cells: Map<Pair<String?, String?>, Money>,
    val currency: Currency,
    val missingRates: Set<Currency>,
) {
    fun cell(row: PivotKey, column: PivotKey): Money = cells[row.id to column.id] ?: Money.zero(currency)
    fun rowTotal(row: PivotKey): Money = columns.fold(Money.zero(currency)) { a, c -> a + cell(row, c) }
    fun columnTotal(column: PivotKey): Money = rows.fold(Money.zero(currency)) { a, r -> a + cell(r, column) }
    val total: Money get() = rows.fold(Money.zero(currency)) { a, r -> a + rowTotal(r) }

    companion object {
        /** The id of the "Other" row and of the single totals column. */
        const val OTHER = "\u0000other"
        const val TOTAL = "\u0000total"
    }
}

/** RPT-03, RPT-07: custom reports over the books' transactions, by any two of the report dimensions. */
class CustomReportService internal constructor(private val books: Books) {

    /**
     * Adds up the splits in [filter]'s period, accounts, person, tag and currency, by [layout]'s rows
     * and columns. Transfers are left out, as in every spending report. [labels] names the
     * dimensions that need words (uncategorized, no payee, no person, untagged, other, total, quarters).
     */
    fun run(filter: ReportFilter, layout: CustomLayout, labels: (String) -> String, french: Boolean = false): PivotTable {
        val cur = filter.currency ?: books.reports.base
        val categories = books.categories.list(includeArchived = true).associateBy { it.id }
        val accounts = books.accounts.all(includeClosed = true).associateBy { it.id }
        val members = books.members.list(includeArchived = true).associate { it.id to it.displayName }
        val tags = books.tags().associate { it.id to it.name }
        val payees = books.payees.list(includeArchived = true).associate { it.id to it.name }
        val missing = HashSet<Currency>()
        val cache = HashMap<Pair<Currency, LocalDate>, java.math.BigDecimal?>()
        fun convert(m: Money, date: LocalDate): Money? {
            if (m.currency == cur) return m
            val rate = cache.getOrPut(m.currency to date) { books.rates.rate(m.currency, cur, date) } ?: run { missing += m.currency; return null }
            return m.convert(cur, rate)
        }
        fun top(id: String?): String? {
            var c = id?.let(categories::get) ?: return null
            while (c.parentId != null) c = categories[c.parentId] ?: break
            return c.id
        }
        fun within(id: String?, ancestor: String): Boolean {
            var c = id?.let(categories::get) ?: return false
            while (true) {
                if (c.id == ancestor) return true
                c = c.parentId?.let(categories::get) ?: return false
            }
        }
        fun categoryName(id: String?) = id?.let(categories::get)?.let { if (french) it.nameFr else it.nameEn } ?: labels("uncategorized")

        val cells = HashMap<Pair<String?, String?>, Money>()
        val rowLabels = HashMap<String?, String>()
        val columnLabels = HashMap<String?, String>()
        // Each day parsed once: a long range has hundreds of thousands of splits on a few thousand days (NFR-02).
        val dates = HashMap<String, LocalDate>()
        for (g in books.groups()) {
            val q = books.ledger(g).ledgerQueries
            val from = filter.from.toString()
            val to = filter.to.toString()
            val rows = if (filter.from.daysUntil(filter.to) > 400) q.customSplitsWide(from, to, ::CustomSplits).executeAsList() else q.customSplits(from, to).executeAsList()
            val tagsOf: Map<String, List<String>> =
                if (layout.rows == ReportDimension.TAG || layout.columns == ReportDimension.TAG || filter.tagId != null) {
                    q.tagsBetween(from, to).executeAsList().groupBy({ it.txn_id }, { it.tag_id })
                } else emptyMap()
            for (r in rows) {
                val account = accounts[r.account_id] ?: continue
                if (filter.accountIds != null && account.id !in filter.accountIds) continue
                if (filter.currency != null && account.currency != filter.currency) continue
                if (filter.memberId != null && r.member_id != filter.memberId) continue
                if (filter.tagId != null && filter.tagId !in tagsOf[r.txn_id].orEmpty()) continue
                if (layout.categoryId != null && !within(r.category_id, layout.categoryId)) continue
                val kind = r.category_id?.let(categories::get)?.kind
                val raw = Money.ofMinor(r.amount_minor, account.currency)
                val value = when (layout.measure) {
                    // Uncategorized money out counts as spending, money in as income.
                    ReportMeasure.SPENDING -> if (kind == CategoryKind.EXPENSE || (kind == null && raw.isNegative)) -raw else continue
                    ReportMeasure.INCOME -> if (kind == CategoryKind.INCOME || (kind == null && raw.isPositive)) raw else continue
                    ReportMeasure.NET -> raw
                }
                val date = dates.getOrPut(r.date) { LocalDate.parse(r.date) }
                val money = convert(value, date) ?: continue
                fun keys(d: ReportDimension?): List<Pair<String?, String>> = when (d) {
                    null -> listOf(PivotTable.TOTAL to labels("total"))
                    ReportDimension.CATEGORY -> listOf(r.category_id to categoryName(r.category_id))
                    ReportDimension.TOP_CATEGORY -> top(r.category_id).let { listOf(it to categoryName(it)) }
                    // Payees are grouped as in the spending by payee report: by payee, or by the text typed.
                    ReportDimension.PAYEE -> r.payee_id.let { id ->
                        listOf(ReportService.payeeKey(id, r.payee_text) to (id?.let(payees::get) ?: r.payee_text?.trim()?.ifEmpty { null } ?: labels("noPayee")))
                    }
                    ReportDimension.ACCOUNT -> listOf(account.id to account.name)
                    ReportDimension.PERSON -> listOf(r.member_id to (r.member_id?.let(members::get) ?: labels("household")))
                    // A transaction with several tags counts under each.
                    ReportDimension.TAG -> tagsOf[r.txn_id].orEmpty().ifEmpty { listOf(null) }.map { it to (it?.let(tags::get) ?: labels("untagged")) }
                    ReportDimension.MONTH -> r.date.substring(0, 7).let { listOf(it to it) }
                    ReportDimension.QUARTER -> ((date.month.ordinal / 3) + 1).let { listOf("${date.year}-Q$it" to labels("quarter").format(it, date.year)) }
                    ReportDimension.YEAR -> r.date.substring(0, 4).let { listOf(it to it) }
                }
                for ((rowId, rowLabel) in keys(layout.rows)) {
                    for ((colId, colLabel) in keys(layout.columns)) {
                        rowLabels[rowId] = rowLabel
                        columnLabels[colId] = colLabel
                        cells.merge(rowId to colId, money, Money::plus)
                    }
                }
            }
        }
        val time = setOf(ReportDimension.MONTH, ReportDimension.QUARTER, ReportDimension.YEAR)
        // Each row's and column's total, added up once (sorting by a total worked out at every comparison took minutes; NFR-02).
        val rowTotals = HashMap<String?, Long>()
        val columnTotals = HashMap<String?, Long>()
        for ((key, money) in cells) {
            rowTotals.merge(key.first, money.minorUnits, Long::plus)
            columnTotals.merge(key.second, money.minorUnits, Long::plus)
        }
        val columns = columnLabels.map { (id, label) -> PivotKey(id, label) }
            .sortedWith(if (layout.columns in time) compareBy { it.id } else compareByDescending { k -> columnTotals[k.id] ?: 0L })
        var rows = rowLabels.map { (id, label) -> PivotKey(id, label) }.let { keys ->
            if (layout.rows in time) keys.sortedBy { it.id } else keys.sortedByDescending { k -> rowTotals[k.id] ?: 0L }
        }
        // The smallest rows are added together, so the chart stays readable.
        if (layout.rows !in time && rows.size > layout.maxRows) {
            val rest = rows.drop(layout.maxRows - 1)
            for (c in columns) {
                val sum = rest.fold(Money.zero(cur)) { a, r -> a + (cells.remove(r.id to c.id) ?: Money.zero(cur)) }
                if (!sum.isZero) cells[PivotTable.OTHER to c.id] = sum
            }
            rows = rows.take(layout.maxRows - 1) + PivotKey(PivotTable.OTHER, labels("other"))
        }
        return PivotTable(rows, columns, cells, cur, missing)
    }
}
