package ca.schippers.hfm.books

import ca.schippers.hfm.calc.rules.Thresholds
import ca.schippers.hfm.domain.CategoryKind
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import java.math.BigDecimal
import java.math.RoundingMode

enum class BudgetPeriod { MONTHLY, ANNUAL }

/** BUD-01: a monthly or annual budget for a category and its subcategories. */
data class Budget(
    val categoryId: String,
    val period: BudgetPeriod,
    val amount: Money,
    val rollover: Boolean,
    /** First day of the first month the budget applies to. */
    val startMonth: LocalDate,
)

/**
 * One budget line for a month or a year. For expenses, [actual] is spending as a positive amount;
 * for income, money received. [budgeted] includes any amount rolled over from earlier months.
 */
data class BudgetLine(
    val category: Category,
    val budget: Budget,
    val budgeted: Money,
    val carriedOver: Money,
    val actual: Money,
) {
    val remaining: Money get() = budgeted - actual
    val ratio: Double get() = if (budgeted.isZero) (if (actual.isZero) 0.0 else Double.POSITIVE_INFINITY) else actual.toBigDecimal().toDouble() / budgeted.toBigDecimal().toDouble()
    /** Spending is above the budget alert share of Rates and rules (100 % built in). */
    val isOver: Boolean get() = actual.toBigDecimal() > budgeted.toBigDecimal().multiply(Thresholds.budgetAlert())
}

data class BudgetReport(val from: LocalDate, val to: LocalDate, val lines: List<BudgetLine>, val missingRates: Set<Currency>) {
    fun total(kind: CategoryKind, base: Currency): Pair<Money, Money> {
        val lines = lines.filter { it.category.kind == kind }
        return lines.fold(Money.zero(base)) { a, l -> a + l.budgeted } to lines.fold(Money.zero(base)) { a, l -> a + l.actual }
    }
}

/**
 * Budgets per category (BUD-01), compared with actual amounts. A budget covers its category's
 * subcategories, except those that have a budget of their own. With rollover, what was not spent
 * in earlier months (or was overspent) carries into the next month.
 */
class BudgetService internal constructor(private val books: Books) {

    private val base: Currency get() = books.rates.baseCurrency

    fun list(): List<Budget> = books.core.budgets().executeAsList().map {
        Budget(it.category_id, BudgetPeriod.valueOf(it.period), Money.ofMinor(it.amount_minor, Currency.of(it.currency)), it.rollover == 1L, LocalDate.parse(it.start_month + "-01"))
    }

    /** Budgets are the household's (core.db): a viewer cannot set them (M-77). */
    fun set(categoryId: String, period: BudgetPeriod, amount: Money, rollover: Boolean = false, startMonth: LocalDate) {
        requireEditor(books)
        validate(books.categories.list(includeArchived = true).any { it.id == categoryId }, "error.unknownCategory")
        validate(amount.currency == base, "error.currencyMismatch", base.code)
        validate(!amount.isNegative, "error.billAmountPositive")
        val month = "%04d-%02d".format(startMonth.year, startMonth.month.ordinal + 1)
        books.core.upsertBudget(Ids.newId(), categoryId, period.name, amount.minorUnits, amount.currency.code, if (rollover) 1 else 0, month)
        // The amount stays out of the shared audit log (core.db), which every user can read.
        books.session.audit("SET_BUDGET", "category", categoryId, period.toString())
    }

    fun remove(categoryId: String) {
        requireEditor(books)
        books.core.deleteBudget(categoryId)
        books.session.audit("DELETE_BUDGET", "category", categoryId)
    }

    /**
     * Budget vs actual for the month starting [month]. Monthly budgets compare that month (plus any
     * rollover); annual budgets compare the year to date with the whole year's amount.
     */
    fun month(month: LocalDate): BudgetReport {
        val start = LocalDate(month.year, month.month, 1)
        val end = start.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
        val yearStart = LocalDate(start.year, 1, 1)
        return report(start, end, monthlyFrom = start, annualFrom = yearStart, monthsInPeriod = 1)
    }

    /** Budget vs actual for a whole year: monthly budgets count twelve times. */
    fun year(year: Int): BudgetReport {
        val start = LocalDate(year, 1, 1)
        val end = LocalDate(year, 12, 31)
        return report(start, end, monthlyFrom = start, annualFrom = start, monthsInPeriod = 12)
    }

    /**
     * Budget vs actual for any period, as the reports screen chooses it: what came in or went out
     * from [from] to [to]. Monthly budgets count once for each calendar month the period touches
     * (with rollover when it is a single month). Annual budgets are for the calendar year: within
     * one year they compare the year's amount with the year to [to], as the month view does; over
     * a period spanning years, a twelfth of the amount counts for each month.
     */
    fun period(from: LocalDate, to: LocalDate): BudgetReport {
        val months = (to.year - from.year) * 12 + (to.month.ordinal - from.month.ordinal) + 1
        val oneYear = from.year == to.year
        return report(from, to, monthlyFrom = from, annualFrom = if (oneYear) LocalDate(from.year, 1, 1) else from, monthsInPeriod = months, annualProrated = !oneYear)
    }

    private fun report(from: LocalDate, to: LocalDate, monthlyFrom: LocalDate, annualFrom: LocalDate, monthsInPeriod: Int, annualProrated: Boolean = false): BudgetReport {
        val budgets = list().associateBy { it.categoryId }
        if (budgets.isEmpty()) return BudgetReport(from, to, emptyList(), emptySet())
        val categories = books.categories.list(includeArchived = true).associateBy { it.id }
        val missing = HashSet<Currency>()

        val monthlyActual = books.reports.subtreeTotals(ReportFilter(monthlyFrom, to)).also { missing += it.missingRates }.value
        val annualActual = books.reports.subtreeTotals(ReportFilter(annualFrom, to)).also { missing += it.missingRates }.value

        /** A budget's total minus the totals of descendants that have their own budget. */
        fun own(categoryId: String, totals: Map<String, Money>): Money {
            var total = totals[categoryId] ?: Money.zero(base)
            for ((id, _) in budgets) {
                if (id == categoryId) continue
                if (isDescendant(id, categoryId, categories) && !hasBudgetedAncestorBelow(id, categoryId, budgets.keys, categories)) {
                    total -= totals[id] ?: Money.zero(base)
                }
            }
            return total
        }

        val lines = budgets.values.mapNotNull { budget ->
            val category = categories[budget.categoryId] ?: return@mapNotNull null
            if (budget.startMonth > to) return@mapNotNull null
            val sign = if (category.kind == CategoryKind.EXPENSE) -1L else 1L
            val annual = budget.period == BudgetPeriod.ANNUAL
            val actual = own(budget.categoryId, if (annual) annualActual else monthlyActual) * sign
            val carried = if (budget.rollover && !annual && monthsInPeriod == 1) {
                carryOver(budget, LocalDate(from.year, from.month, 1), sign, categories, budgets)
            } else Money.zero(base)
            val budgeted = when {
                !annual -> budget.amount * monthsInPeriod.toLong()
                annualProrated -> budget.amount.times(BigDecimal(monthsInPeriod).divide(BigDecimal(12), 10, RoundingMode.HALF_UP))
                else -> budget.amount
            }
            BudgetLine(category, budget, budgeted + carried, carried, actual)
        }.sortedWith(compareBy({ it.category.kind }, { -it.ratio }))
        return BudgetReport(from, to, lines, missing)
    }

    /** Unspent (or overspent) amounts of every month from the budget's start to before [month]. */
    private fun carryOver(budget: Budget, month: LocalDate, sign: Long, categories: Map<String, Category>, budgets: Map<String, Budget>): Money {
        if (budget.startMonth >= month) return Money.zero(base)
        val byMonth = books.reports.monthlyCategoryTotals(ReportFilter(budget.startMonth, month.minus(DatePeriod(days = 1))))
        var carry = Money.zero(base)
        var m = budget.startMonth
        while (m < month) {
            val totals = byMonth[m].orEmpty()
            var actual = Money.zero(base)
            for ((categoryId, amount) in totals) {
                val id = categoryId ?: continue
                if (id == budget.categoryId || isDescendant(id, budget.categoryId, categories) && ownedBy(id, categories, budgets) == budget.categoryId) {
                    actual += amount
                }
            }
            carry += budget.amount - actual * sign
            m = m.plus(DatePeriod(months = 1))
        }
        return carry
    }

    /** The closest budgeted category at or above [categoryId]. */
    private fun ownedBy(categoryId: String, categories: Map<String, Category>, budgets: Map<String, Budget>): String? {
        var current: String? = categoryId
        while (current != null) {
            if (current in budgets) return current
            current = categories[current]?.parentId
        }
        return null
    }

    private fun isDescendant(id: String, ancestorId: String, categories: Map<String, Category>): Boolean {
        var current = categories[id]?.parentId
        while (current != null) {
            if (current == ancestorId) return true
            current = categories[current]?.parentId
        }
        return false
    }

    /** True when a budgeted category sits between [id] and [ancestorId], so [id] is already excluded through it. */
    private fun hasBudgetedAncestorBelow(id: String, ancestorId: String, budgeted: Set<String>, categories: Map<String, Category>): Boolean {
        var current = categories[id]?.parentId
        while (current != null && current != ancestorId) {
            if (current in budgeted) return true
            current = categories[current]?.parentId
        }
        return false
    }

    /**
     * BUD-05: a starting budget from the last 12 full months: the monthly average of every
     * top-level expense category that had spending. Only the accounts of shared groups count:
     * a budget is kept for the whole household (core.db), so one made from a private group's
     * spending would show that spending to every user (HH-11).
     */
    fun suggestions(today: LocalDate): Map<String, Money> {
        val end = LocalDate(today.year, today.month, 1).minus(DatePeriod(days = 1))
        val start = LocalDate(end.year, end.month, 1).minus(DatePeriod(months = 11))
        val shared = books.groups().filter { !it.isPrivate }.map { it.id }.toSet()
        val accounts = books.accounts.list(includeClosed = true).map { it.account }.filter { it.groupId in shared }.map { it.id }.toSet()
        val totals = books.reports.byCategory(ReportFilter(start, end, accountIds = accounts), CategoryKind.EXPENSE).value
        return totals.mapNotNull { row ->
            val category = row.category ?: return@mapNotNull null
            val monthly = row.amount.toBigDecimal().divide(BigDecimal(12), 0, RoundingMode.UP)
            category.id to Money.of(monthly, base)
        }.toMap()
    }
}
