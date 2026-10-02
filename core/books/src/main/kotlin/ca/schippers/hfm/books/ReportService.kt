package ca.schippers.hfm.books

import ca.schippers.hfm.domain.CategoryKind
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Which transactions a report covers (section 12: date range, accounts, person, tag; RPT-07). */
data class ReportFilter(
    val from: LocalDate,
    val to: LocalDate,
    val accountIds: Set<String>? = null,
    val memberId: String? = null,
    val tagId: String? = null,
) {
    /** RPT-02: the period of the same length just before this one. */
    fun previousPeriod(): ReportFilter {
        val days = from.daysUntil(to) + 1
        return copy(from = from.minus(DatePeriod(days = days)), to = from.minus(DatePeriod(days = 1)))
    }

    /** RPT-02: the same dates one year earlier. */
    fun sameLastYear(): ReportFilter = copy(from = from.minus(DatePeriod(years = 1)), to = to.minus(DatePeriod(years = 1)))
}

enum class Granularity { MONTH, QUARTER, YEAR }

data class PeriodTotals(val start: LocalDate, val end: LocalDate, val income: Money, val expense: Money) {
    val net: Money get() = income - expense
}

/** Spending (or income) of a category including its subcategories; [category] null is "uncategorized". */
data class CategoryAmount(val category: Category?, val amount: Money, val hasChildren: Boolean)

data class PayeeAmount(val payeeId: String?, val name: String, val amount: Money)

data class NetWorthPoint(val date: LocalDate, val assets: Money, val liabilities: Money) {
    val net: Money get() = assets - liabilities
}

/** One split behind a figure, for drill-down (RPT-01). */
data class DrillRow(
    val transactionId: String,
    val accountId: String,
    val date: LocalDate,
    val payee: String?,
    val memo: String?,
    val categoryId: String?,
    val amount: Money,
    val baseAmount: Money?,
)

/** A report and the currencies that had no exchange rate, so totals may be incomplete. */
data class Report<T>(val value: T, val missingRates: Set<Currency>)

/**
 * The calculations behind the reports and the dashboard (section 12). Figures are in the
 * household's base currency, converted at each transaction's date (FX-01). Only accounts the
 * signed-in user may see are included (HH-10). Transfers between own accounts are left out.
 */
class ReportService internal constructor(private val books: Books) {

    val base: Currency get() = books.rates.baseCurrency

    private class Converter(private val rates: RateService, private val base: Currency) {
        private val cache = HashMap<Pair<Currency, LocalDate>, java.math.BigDecimal?>()
        val missing = HashSet<Currency>()

        fun toBase(amount: Money, date: LocalDate): Money? {
            if (amount.currency == base) return amount
            val rate = cache.getOrPut(amount.currency to date) { rates.rate(amount.currency, base, date) }
            if (rate == null) {
                missing += amount.currency
                return null
            }
            return amount.convert(base, rate)
        }
    }

    private class Row(val categoryId: String?, val date: LocalDate, val amount: Money)

    /** Category totals per day, in the base currency, for the accounts in the filter. */
    private fun categoryRows(filter: ReportFilter, converter: Converter): List<Row> {
        val accounts = books.accounts.list(includeClosed = true).associate { it.account.id to it.account }
        return books.groups().flatMap { group ->
            books.ledger(group).ledgerQueries.categoryDaily(filter.from.toString(), filter.to.toString(), filter.memberId, filter.tagId)
                .executeAsList()
                .filter { filter.accountIds == null || it.account_id in filter.accountIds }
                .mapNotNull { r ->
                    val account = accounts[r.account_id] ?: return@mapNotNull null
                    val date = LocalDate.parse(r.date)
                    converter.toBase(Money.ofMinor(r.total ?: 0, account.currency), date)?.let { Row(r.category_id, date, it) }
                }
        }
    }

    private fun categories() = books.categories.list(includeArchived = true).associateBy { it.id }

    /** Is this signed amount, in this category, income? Uncategorized amounts follow their sign. */
    private fun isIncome(categoryId: String?, amount: Money, categories: Map<String, Category>): Boolean =
        categoryId?.let { categories[it]?.kind == CategoryKind.INCOME } ?: amount.isPositive

    // --- Income and expense (cash flow) ---------------------------------------------------------

    fun incomeExpense(filter: ReportFilter, granularity: Granularity = Granularity.MONTH): Report<List<PeriodTotals>> {
        val converter = Converter(books.rates, base)
        val categories = categories()
        val rows = categoryRows(filter, converter)
        val periods = periods(filter.from, filter.to, granularity)
        val zero = Money.zero(base)
        val totals = periods.map { (start, end) ->
            var income = zero
            var expense = zero
            for (r in rows) {
                if (r.date < start || r.date > end) continue
                if (isIncome(r.categoryId, r.amount, categories)) income += r.amount else expense -= r.amount
            }
            PeriodTotals(start, end, income, expense)
        }
        return Report(totals, converter.missing)
    }

    // --- Spending by category -------------------------------------------------------------------

    /**
     * Spending (as positive amounts) or income per category directly under [parentId] (top level
     * when null), each including its subcategories, largest first. At the top level, uncategorized
     * amounts of the same kind are added as a row with no category.
     */
    fun byCategory(filter: ReportFilter, kind: CategoryKind = CategoryKind.EXPENSE, parentId: String? = null): Report<List<CategoryAmount>> {
        val converter = Converter(books.rates, base)
        val categories = categories()
        val rows = categoryRows(filter, converter)
        val zero = Money.zero(base)
        val sign = if (kind == CategoryKind.EXPENSE) -1L else 1L
        val children = categories.values.groupBy { it.parentId }

        /** The child of [parentId] that [categoryId] belongs to, or the parent itself for its direct amounts. */
        fun bucketOf(categoryId: String): String? {
            var current = categories[categoryId] ?: return null
            if (current.id == parentId) return current.id
            while (current.parentId != parentId) {
                current = categories[current.parentId ?: return null] ?: return null
            }
            return current.id
        }

        val totals = LinkedHashMap<String?, Money>()
        for (r in rows) {
            val categoryId = r.categoryId
            if (categoryId == null) {
                if (parentId == null && isIncome(null, r.amount, categories) == (kind == CategoryKind.INCOME)) {
                    totals[null] = (totals[null] ?: zero) + r.amount * sign
                }
                continue
            }
            if (categories[categoryId]?.kind != kind) continue
            val bucket = bucketOf(categoryId) ?: continue
            totals[bucket] = (totals[bucket] ?: zero) + r.amount * sign
        }
        val result = totals.map { (id, amount) ->
            CategoryAmount(id?.let(categories::get), amount, id != null && id != parentId && children[id].orEmpty().isNotEmpty())
        }.filter { !it.amount.isZero }.sortedByDescending { it.amount }
        return Report(result, converter.missing)
    }

    /** Every category's total including its subcategories (signed: spending negative), for budgets. */
    internal fun subtreeTotals(filter: ReportFilter): Report<Map<String, Money>> {
        val converter = Converter(books.rates, base)
        val categories = categories()
        val totals = HashMap<String, Money>()
        for (r in categoryRows(filter, converter)) {
            var id = r.categoryId
            while (id != null) {
                totals[id] = (totals[id] ?: Money.zero(base)) + r.amount
                id = categories[id]?.parentId
            }
        }
        return Report(totals, converter.missing)
    }

    /** Signed totals per month and category (not rolled up), for rollover budgets. */
    internal fun monthlyCategoryTotals(filter: ReportFilter): Map<LocalDate, Map<String?, Money>> {
        val converter = Converter(books.rates, base)
        return categoryRows(filter, converter)
            .groupBy { LocalDate(it.date.year, it.date.month, 1) }
            .mapValues { (_, rows) -> rows.groupBy { it.categoryId }.mapValues { (_, r) -> r.fold(Money.zero(base)) { a, x -> a + x.amount } } }
    }

    // --- Spending by payee ----------------------------------------------------------------------

    /** Net amount per payee (spending positive), largest spending first. */
    fun byPayee(filter: ReportFilter): Report<List<PayeeAmount>> {
        val converter = Converter(books.rates, base)
        val accounts = books.accounts.list(includeClosed = true).associate { it.account.id to it.account }
        val names = books.payees.list(includeArchived = true).associate { it.id to it.name }
        val totals = HashMap<Pair<String?, String>, Money>()
        for (group in books.groups()) {
            for (r in books.ledger(group).ledgerQueries.payeeDaily(filter.from.toString(), filter.to.toString(), filter.memberId, filter.tagId).executeAsList()) {
                if (filter.accountIds != null && r.account_id !in filter.accountIds) continue
                val account = accounts[r.account_id] ?: continue
                val amount = converter.toBase(Money.ofMinor(r.total ?: 0, account.currency), LocalDate.parse(r.date)) ?: continue
                val key = r.payee_id to (r.payee_id?.let(names::get) ?: r.payee_text)
                totals[key] = (totals[key] ?: Money.zero(base)) - amount
            }
        }
        val rows = totals.map { (key, amount) -> PayeeAmount(key.first, key.second, amount) }
            .filter { !it.amount.isZero }
            .sortedByDescending { it.amount }
        return Report(rows, converter.missing)
    }

    // --- Net worth ------------------------------------------------------------------------------

    /** What the household owns and owes on each date (section 12, net worth). */
    fun netWorth(dates: List<LocalDate>, accountIds: Set<String>? = null): Report<List<NetWorthPoint>> {
        val converter = Converter(books.rates, base)
        val accounts = books.accounts.list(includeClosed = true).associate { it.account.id to it.account }
        val zero = Money.zero(base)
        val points = dates.map { date ->
            var assets = zero
            var liabilities = zero
            for (group in books.groups()) {
                for (r in books.ledger(group).ledgerQueries.balancesAsOf(date.toString()).executeAsList()) {
                    if (accountIds != null && r.id !in accountIds) continue
                    val account = accounts[r.id] ?: continue
                    val balance = converter.toBase(Money.ofMinor(r.balance, account.currency), date) ?: continue
                    if (account.type.kind.isLiability) liabilities -= balance else assets += balance
                }
            }
            NetWorthPoint(date, assets, liabilities)
        }
        return Report(points, converter.missing)
    }

    /** Month ends from [from]'s month to [to]'s month, ending with [to] itself. */
    fun monthEnds(from: LocalDate, to: LocalDate): List<LocalDate> =
        periods(from, to, Granularity.MONTH).map { it.second }

    // --- Drill-down (RPT-01) --------------------------------------------------------------------

    /**
     * The splits behind a figure: those in [categoryId] and its subcategories (or uncategorized
     * when [uncategorized] is true), or of [payeeId].
     */
    fun drillDown(filter: ReportFilter, categoryId: String? = null, uncategorized: Boolean = false, payeeId: String? = null, payeeText: String? = null): List<DrillRow> {
        val converter = Converter(books.rates, base)
        val categories = categories()
        val accounts = books.accounts.list(includeClosed = true).associate { it.account.id to it.account }
        val names = books.payees.list(includeArchived = true).associate { it.id to it.name }
        fun inSubtree(id: String?): Boolean {
            if (categoryId == null) return true
            var current = id
            while (current != null) {
                if (current == categoryId) return true
                current = categories[current]?.parentId
            }
            return false
        }
        return books.groups().flatMap { group ->
            books.ledger(group).ledgerQueries.reportSplits(filter.from.toString(), filter.to.toString(), filter.memberId, filter.tagId).executeAsList()
                .filter { filter.accountIds == null || it.account_id in filter.accountIds }
                .filter { if (uncategorized) it.category_id == null else inSubtree(it.category_id) }
                .filter { payeeId == null || it.payee_id == payeeId }
                .filter { payeeText == null || payeeId != null || (it.payee_id == null && it.payee_text.orEmpty() == payeeText) }
                .mapNotNull { r ->
                    val account = accounts[r.account_id] ?: return@mapNotNull null
                    val date = LocalDate.parse(r.date)
                    val amount = Money.ofMinor(r.amount_minor, account.currency)
                    DrillRow(r.txn_id, r.account_id, date, r.payee_id?.let(names::get) ?: r.payee_text, r.memo, r.category_id, amount, converter.toBase(amount, date))
                }
        }.sortedBy { it.date }
    }

    /** Transactions with no category, for the dashboard's "needs review" (excluding transfers). */
    fun uncategorizedCount(): Long = books.groups().sumOf { books.ledger(it).ledgerQueries.uncategorizedCount().executeAsOne() }

    companion object {
        /** Calendar periods covering [from]..[to], clipped to the range. */
        fun periods(from: LocalDate, to: LocalDate, granularity: Granularity): List<Pair<LocalDate, LocalDate>> {
            val months = when (granularity) {
                Granularity.MONTH -> 1
                Granularity.QUARTER -> 3
                Granularity.YEAR -> 12
            }
            val startMonth = (from.month.ordinal / months) * months
            var start = LocalDate(from.year, startMonth + 1, 1)
            val out = ArrayList<Pair<LocalDate, LocalDate>>()
            while (start <= to) {
                val next = start.plus(DatePeriod(months = months))
                val end = next.minus(DatePeriod(days = 1))
                out += maxOf(start, from) to minOf(end, to)
                start = next
            }
            return out
        }
    }
}
