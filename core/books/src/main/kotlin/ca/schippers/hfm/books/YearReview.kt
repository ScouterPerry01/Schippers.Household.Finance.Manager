package ca.schippers.hfm.books

import ca.schippers.hfm.domain.CategoryKind
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate

/** A category's spending this year against last year. */
data class CategoryChange(val categoryId: String?, val name: String, val thisYear: Money, val lastYear: Money) {
    val change: Money get() = thisYear - lastYear
}

/** One of the year's largest purchases. */
data class LargePurchase(val date: LocalDate, val payee: String?, val category: String, val amount: Money)

/**
 * The year in review: income, spending and what was kept, net worth from the start of the year to
 * its end, the categories that took most and those that changed most against the year before, the
 * largest purchases, the payees visited most, and the busiest month. In the base currency.
 */
data class YearInReview(
    val year: Int,
    val income: Money,
    val spending: Money,
    val lastYearIncome: Money,
    val lastYearSpending: Money,
    val netWorthStart: Money?,
    val netWorthEnd: Money?,
    val topCategories: List<CategoryChange>,
    val biggestChanges: List<CategoryChange>,
    val largestPurchases: List<LargePurchase>,
    /** Payee, number of visits, amount spent. */
    val frequentPayees: List<Triple<String, Int, Money>>,
    /** "2026-03" and what was spent that month. */
    val busiestMonth: Pair<String, Money>?,
) {
    val kept: Money get() = income - spending

    /** The share of income kept, in percent, when there was income. */
    val savingsRate: Int? get() = income.takeIf { it.isPositive }?.let { (kept.toBigDecimal() * java.math.BigDecimal(100) / it.toBigDecimal()).toInt() }
}

class YearReviewService internal constructor(private val books: Books) {

    fun review(year: Int, labels: (String) -> String, french: Boolean = false): YearInReview {
        val cur = books.reports.base
        val zero = Money.zero(cur)
        val thisYear = ReportFilter(LocalDate(year, 1, 1), LocalDate(year, 12, 31))
        val lastYear = ReportFilter(LocalDate(year - 1, 1, 1), LocalDate(year - 1, 12, 31))
        fun run(f: ReportFilter, rows: ReportDimension, measure: ReportMeasure, max: Int = 1000) =
            books.customReports.run(f, CustomLayout(rows, null, measure, maxRows = max), labels, french)
        val spendNow = run(thisYear, ReportDimension.TOP_CATEGORY, ReportMeasure.SPENDING)
        val spendBefore = run(lastYear, ReportDimension.TOP_CATEGORY, ReportMeasure.SPENDING)
        val before = spendBefore.rows.associate { it.id to spendBefore.rowTotal(it) }
        val changes = (spendNow.rows.map { it.id to it.label } + spendBefore.rows.map { it.id to it.label }).distinctBy { it.first }.map { (id, label) ->
            CategoryChange(id, label, spendNow.rows.firstOrNull { it.id == id }?.let(spendNow::rowTotal) ?: zero, before[id] ?: zero)
        }
        val months = run(thisYear, ReportDimension.MONTH, ReportMeasure.SPENDING)
        val payees = run(thisYear, ReportDimension.PAYEE, ReportMeasure.SPENDING)

        // Visits and the largest single purchases come from the transactions themselves.
        val categories = books.categories.list(includeArchived = true).associateBy { it.id }
        val accounts = books.accounts.all(includeClosed = true).associateBy { it.id }
        val purchases = ArrayList<LargePurchase>()
        val visits = HashMap<String, Int>()
        for (g in books.groups()) {
            val rows = books.ledger(g).taxYearQueries.packageSplits(thisYear.from.toString(), thisYear.to.toString()).executeAsList()
            // Visits count by payee as the payee rows group them (ReportService.payeeKey).
            val payeeOf = books.ledger(g).ledgerQueries.reportSplits(thisYear.from.toString(), thisYear.to.toString(), null, null).executeAsList().associate { it.txn_id to it.payee_id }
            for ((_, splits) in rows.groupBy { it.txn_id }) {
                val first = splits.first()
                val account = accounts[first.account_id] ?: continue
                val spent = splits.filter { s -> s.category_id?.let(categories::get)?.kind == CategoryKind.EXPENSE }
                if (spent.isEmpty()) continue
                val date = LocalDate.parse(first.date)
                val amount = books.rates.convert(Money.ofMinor(-spent.sumOf { it.amount_minor }, account.currency), cur, date) ?: continue
                if (!amount.isPositive) continue
                val payeeId = payeeOf[first.txn_id]
                if (payeeId != null || !first.payee_text.isNullOrBlank()) visits.merge(ReportService.payeeKey(payeeId, first.payee_text), 1, Int::plus)
                val top = spent.maxBy { -it.amount_minor }.category_id?.let(categories::get)
                purchases += LargePurchase(date, first.payee_text, top?.let { if (french) it.nameFr else it.nameEn } ?: labels("uncategorized"), amount)
            }
        }
        val worth = runCatching { books.reports.netWorth(listOf(LocalDate(year - 1, 12, 31), LocalDate(year, 12, 31).let { minOf(it, books.today()) })).value }.getOrNull()
        return YearInReview(
            year,
            run(thisYear, ReportDimension.YEAR, ReportMeasure.INCOME).total,
            spendNow.total,
            run(lastYear, ReportDimension.YEAR, ReportMeasure.INCOME).total,
            spendBefore.total,
            worth?.getOrNull(0)?.net,
            worth?.getOrNull(1)?.net,
            changes.sortedByDescending { it.thisYear.minorUnits }.take(5),
            changes.filter { !it.lastYear.isZero || !it.thisYear.isZero }.sortedByDescending { kotlin.math.abs(it.change.minorUnits) }.take(5),
            purchases.sortedByDescending { it.amount.minorUnits }.take(5),
            payees.rows.filter { it.id != null && it.id != PivotTable.OTHER && it.id != ReportService.payeeKey(null, null) }
                .map { Triple(it.label, visits[it.id] ?: 0, payees.rowTotal(it)) }
                .sortedWith(compareByDescending<Triple<String, Int, Money>> { it.second }.thenByDescending { it.third.minorUnits }).take(5),
            months.rows.maxByOrNull { months.rowTotal(it).minorUnits }?.let { it.label to months.rowTotal(it) },
        )
    }
}
