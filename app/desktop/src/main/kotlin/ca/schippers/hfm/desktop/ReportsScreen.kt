package ca.schippers.hfm.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Category
import ca.schippers.hfm.books.DrillRow
import ca.schippers.hfm.books.Granularity
import ca.schippers.hfm.books.ReportFilter
import ca.schippers.hfm.books.StatementStatus
import ca.schippers.hfm.domain.CategoryKind
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.sum
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import java.time.format.DateTimeFormatter

enum class ReportKind { INCOME_EXPENSE, SPENDING_BY_CATEGORY, INCOME_BY_CATEGORY, SPENDING_BY_PAYEE, CUSTOM, YEAR_IN_REVIEW, NET_WORTH, PORTFOLIO, INVESTMENT_INCOME, PLANS, FX, MEDICAL, ASSETS, MAINTENANCE, DEBT, BUDGET, RECONCILIATION }
/** FX-06: reports that can show one currency's accounts in their own amounts. */
private val BY_CURRENCY = setOf(ReportKind.INCOME_EXPENSE, ReportKind.SPENDING_BY_CATEGORY, ReportKind.INCOME_BY_CATEGORY, ReportKind.SPENDING_BY_PAYEE, ReportKind.NET_WORTH)
/** RPT-07: reports that do not use the account group and the chosen accounts, so those choices are not shown. */
private val NO_ACCOUNT_CHOICE = setOf(
    ReportKind.INVESTMENT_INCOME, ReportKind.FX, ReportKind.PLANS, ReportKind.MEDICAL, ReportKind.ASSETS, ReportKind.MAINTENANCE,
    ReportKind.YEAR_IN_REVIEW, ReportKind.BUDGET, ReportKind.RECONCILIATION,
)

enum class RangePreset { THIS_MONTH, LAST_MONTH, THIS_YEAR, LAST_YEAR, LAST_12_MONTHS, CUSTOM }
enum class Compare { NONE, PREVIOUS, LAST_YEAR }

/** The filter bar's choices, kept while the user moves between reports. */
class ReportState {
    var kind by mutableStateOf(ReportKind.INCOME_EXPENSE)
    var preset by mutableStateOf(RangePreset.THIS_YEAR)
    var customFrom by mutableStateOf(LocalDate(today().year, 1, 1).toString())
    var customTo by mutableStateOf(today().toString())
    var groupId by mutableStateOf<String?>(null)
    var memberId by mutableStateOf<String?>(null)
    var tagId by mutableStateOf<String?>(null)
    var compare by mutableStateOf(Compare.NONE)
    /** The tax year of the investment income report: last year by default, as for filing. */
    var taxYear by mutableStateOf(today().year - 1)
    /** The year in review's year: this one so far by default. */
    var reviewYear by mutableStateOf(today().year)
    /** The year of the registered plans report: this year by default, for the room left. */
    var planYear by mutableStateOf(today().year)
    /** FX-06: show only the accounts in this currency, in their own amounts; null for everything in the base currency. */
    var currency by mutableStateOf<Currency?>(null)
    /** Drill path in the category reports: the category whose subcategories are shown. */
    var parent by mutableStateOf<Category?>(null)
    var drill by mutableStateOf<Pair<String, List<DrillRow>>?>(null)
    /** RPT-03: the custom report's rows, columns, measure and chart. */
    var layout by mutableStateOf(ca.schippers.hfm.books.CustomLayout())
    /** RPT-07: a chosen set of accounts (such as the cottage's); null for every account. */
    var accountSet by mutableStateOf<Set<String>?>(null)
    /** The saved report on the screen, if one was opened or saved, so saving again updates it. */
    var savedId by mutableStateOf<String?>(null)

    fun range(): Pair<LocalDate, LocalDate> {
        val t = today()
        val monthStart = LocalDate(t.year, t.month, 1)
        return when (preset) {
            RangePreset.THIS_MONTH -> monthStart to t
            RangePreset.LAST_MONTH -> monthStart.minus(DatePeriod(months = 1)) to monthStart.minus(DatePeriod(days = 1))
            RangePreset.THIS_YEAR -> LocalDate(t.year, 1, 1) to t
            RangePreset.LAST_YEAR -> LocalDate(t.year - 1, 1, 1) to LocalDate(t.year - 1, 12, 31)
            RangePreset.LAST_12_MONTHS -> monthStart.minus(DatePeriod(months = 11)) to t
            RangePreset.CUSTOM -> (runCatching { LocalDate.parse(customFrom.trim()) }.getOrNull() ?: monthStart) to
                (runCatching { LocalDate.parse(customTo.trim()) }.getOrNull() ?: t)
        }
    }
}

/** Section 12: the standard reports, each with a chart, a table view, drill-down and export. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportsScreen(model: BooksModel, state: ReportState) {
    val books = model.books
    val groups = remember(model.revision) { books.groups() }
    val members = remember(model.revision) { books.members.list() }
    val tags = remember(model.revision) { books.tags() }
    val (from, to) = state.range()
    val owned = state.kind in setOf(ReportKind.NET_WORTH, ReportKind.DEBT)
    val accountIds = remember(model.revision, state.groupId, state.accountSet, state.memberId, owned) {
        val chosen = model.reportAccounts(state.groupId, state.accountSet)
        // RPT-07: net worth and debt by person are those of the accounts the person owns.
        val member = state.memberId.takeIf { owned }
        if (member == null) chosen else books.accounts.list(includeClosed = true).filter { member in it.account.ownerMemberIds }.map { it.account.id }.toSet()
            .let { mine -> chosen?.let { mine intersect it } ?: mine }
    }
    val saved = remember(model.revision) { books.savedReports.list() }
    var saving by remember { mutableStateOf(false) }
    var choosingAccounts by remember { mutableStateOf(false) }
    val currencies = remember(model.revision) { books.accounts.list(includeClosed = true).map { it.account.currency }.filter { !it.isCrypto && it != books.reports.base }.distinct().sortedBy { it.code } }
    val currency = state.currency?.takeIf { state.kind in BY_CURRENCY }
    val filter = ReportFilter(from, to, accountIds, state.memberId, state.tagId, currency)

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(230.dp).fillMaxHeight().padding(8.dp).verticalScroll(rememberScrollState())) {
            for (kind in ReportKind.entries) {
                NavigationDrawerItem(
                    label = { Text(model.t("report.${kind.name}")) },
                    selected = state.kind == kind && state.savedId == null,
                    onClick = { state.kind = kind; state.parent = null; state.savedId = null },
                )
            }
            // RPT-03: the reports this user saved.
            if (saved.isNotEmpty()) {
                Text(model.t("report.saved"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                for (r in saved) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NavigationDrawerItem(
                            label = { Text(r.name + if (r.schedule != null) " ⏱" else "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            selected = state.savedId == r.id,
                            onClick = { ca.schippers.hfm.books.ReportDefinition.fromJson(r.definition)?.let { state.load(it, r) } },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { model.act { books.savedReports.delete(r.id) }; if (state.savedId == r.id) state.savedId = null }) { Text("✕") }
                    }
                }
            }
        }
        VerticalDivider()
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            // Filters in one row above the chart.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.kind == ReportKind.PLANS || state.kind == ReportKind.MAINTENANCE) {
                    Picker(model.t("loans.year"), (today().year downTo today().year - 10).toList(), state.planYear, { it.toString() }, Modifier.width(140.dp)) { state.planYear = it }
                }
                if (state.kind == ReportKind.YEAR_IN_REVIEW) {
                    Picker(model.t("review.year"), (today().year downTo today().year - 10).toList(), state.reviewYear, { it.toString() }, Modifier.width(140.dp)) { state.reviewYear = it }
                }
                if (state.kind in setOf(ReportKind.INVESTMENT_INCOME, ReportKind.FX, ReportKind.MEDICAL)) {
                    Picker(model.t("income.year"), (today().year downTo today().year - 10).toList(), state.taxYear, { it.toString() }, Modifier.width(190.dp)) { state.taxYear = it }
                }
                if (state.kind !in setOf(ReportKind.RECONCILIATION, ReportKind.DEBT, ReportKind.INVESTMENT_INCOME, ReportKind.FX, ReportKind.PLANS, ReportKind.MEDICAL, ReportKind.ASSETS, ReportKind.MAINTENANCE, ReportKind.YEAR_IN_REVIEW)) {
                    Picker(model.t("report.period"), RangePreset.entries, state.preset, { model.t("range.$it") }, Modifier.width(200.dp)) { state.preset = it }
                    if (state.preset == RangePreset.CUSTOM) {
                        DateInput(model.t("report.from"), state.customFrom, Modifier.width(150.dp)) { state.customFrom = it }
                        DateInput(model.t("report.to"), state.customTo, Modifier.width(150.dp)) { state.customTo = it }
                    }
                }
                // Budget vs actual and reconciliation status always cover every account, so they have no account choice.
                if (groups.size > 1 && state.kind !in NO_ACCOUNT_CHOICE) {
                    Picker(model.t("report.accountGroup"), listOf(null) + groups, groups.firstOrNull { it.id == state.groupId }, { it?.name ?: model.t("report.allAccounts") }, Modifier.width(200.dp)) {
                        state.groupId = it?.id
                    }
                }
                if (state.kind !in NO_ACCOUNT_CHOICE) {
                    OutlinedButton(onClick = { choosingAccounts = true }, modifier = Modifier.padding(top = 8.dp)) {
                        Text(state.accountSet?.let { model.t("report.someAccounts", it.size) } ?: model.t("report.chooseAccounts"))
                    }
                }
                if (state.kind in setOf(ReportKind.NET_WORTH, ReportKind.DEBT, ReportKind.CUSTOM) && members.isNotEmpty()) {
                    Picker(model.t("report.person"), listOf(null) + members, members.firstOrNull { it.id == state.memberId }, { it?.displayName ?: model.t("report.everyone") }, Modifier.width(180.dp)) {
                        state.memberId = it?.id
                    }
                }
                if (state.kind == ReportKind.CUSTOM && tags.isNotEmpty()) {
                    Picker(model.t("report.tag"), listOf(null) + tags, tags.firstOrNull { it.id == state.tagId }, { it?.name ?: model.t("report.anyTag") }, Modifier.width(180.dp)) {
                        state.tagId = it?.id
                    }
                }
                if (state.kind in BY_CURRENCY && currencies.isNotEmpty()) {
                    Picker(model.t("report.currency"), listOf(null) + currencies, state.currency, { it?.let { c -> model.t("report.onlyCurrency", c.code) } ?: model.t("report.allInBase", books.reports.base.code) }, Modifier.width(220.dp)) {
                        state.currency = it
                    }
                }
                if (state.kind in setOf(ReportKind.PORTFOLIO, ReportKind.INVESTMENT_INCOME, ReportKind.PLANS, ReportKind.MEDICAL) && members.isNotEmpty()) {
                    Picker(model.t("report.person"), listOf(null) + members, members.firstOrNull { it.id == state.memberId }, { it?.displayName ?: model.t("report.everyone") }, Modifier.width(180.dp)) {
                        state.memberId = it?.id
                    }
                }
                if (state.kind in setOf(ReportKind.INCOME_EXPENSE, ReportKind.SPENDING_BY_CATEGORY, ReportKind.INCOME_BY_CATEGORY, ReportKind.SPENDING_BY_PAYEE)) {
                    if (members.isNotEmpty()) {
                        Picker(model.t("report.person"), listOf(null) + members, members.firstOrNull { it.id == state.memberId }, { it?.displayName ?: model.t("report.everyone") }, Modifier.width(180.dp)) {
                            state.memberId = it?.id
                        }
                    }
                    if (tags.isNotEmpty()) {
                        Picker(model.t("report.tag"), listOf(null) + tags, tags.firstOrNull { it.id == state.tagId }, { it?.name ?: model.t("report.anyTag") }, Modifier.width(180.dp)) {
                            state.tagId = it?.id
                        }
                    }
                    Picker(model.t("report.compare"), Compare.entries, state.compare, { model.t("compare.$it") }, Modifier.width(260.dp)) { state.compare = it }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                TextButton(onClick = { saving = true }) { Text(model.t("report.save")) }
                model.reportMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                when (state.kind) {
                    ReportKind.CUSTOM -> CustomReportView(model, state, filter)
                    ReportKind.YEAR_IN_REVIEW -> YearReviewReport(model, state.reviewYear)
                    ReportKind.INCOME_EXPENSE -> IncomeExpenseReport(model, state, filter)
                    ReportKind.SPENDING_BY_CATEGORY -> CategoryReport(model, state, filter, CategoryKind.EXPENSE)
                    ReportKind.INCOME_BY_CATEGORY -> CategoryReport(model, state, filter, CategoryKind.INCOME)
                    ReportKind.SPENDING_BY_PAYEE -> PayeeReport(model, state, filter)
                    ReportKind.NET_WORTH -> NetWorthReport(model, filter)
                    ReportKind.PORTFOLIO -> PortfolioReport(model, filter, state.groupId)
                    ReportKind.INVESTMENT_INCOME -> InvestmentIncomeReport(model, state.taxYear, state.memberId)
                    ReportKind.FX -> FxReport(model, state.taxYear)
                    ReportKind.PLANS -> RegisteredPlansReport(model, state.planYear, state.memberId)
                    ReportKind.MEDICAL -> MedicalReport(model, state.taxYear, state.memberId)
                    ReportKind.ASSETS -> AssetsReport(model)
                    ReportKind.MAINTENANCE -> MaintenanceReport(model, state.planYear)
                    ReportKind.DEBT -> DebtReport(model, filter.accountIds)
                    ReportKind.BUDGET -> {
                        // The chosen period, whatever it is (a month, a year, the last 12 months, custom dates).
                        val budget = remember(model.revision, from, to) { books.budgets.period(from, to) }
                        BudgetReportView(model, budget, yearView = from == LocalDate(from.year, 1, 1) && to == LocalDate(from.year, 12, 31))
                    }
                    ReportKind.RECONCILIATION -> ReconciliationReport(model)
                }
            }
        }
    }
    state.drill?.let { (title, rows) -> DrillDialog(model, title, rows) { state.drill = null } }
    if (saving) SaveReportDialog(model, state) { saving = false }
    if (choosingAccounts) AccountSetDialog(model, state.accountSet) { state.accountSet = it; choosingAccounts = false }
}

/** FX-06: the currency the report is in: the one chosen, or the base currency. */
internal fun ReportFilter.cur(model: BooksModel): Currency = currency ?: model.books.reports.base

internal fun subtitle(model: BooksModel, filter: ReportFilter, extra: String? = null): String =
    listOfNotNull("${model.date(filter.from)} – ${model.date(filter.to)}", model.t("report.inCurrency", filter.cur(model).code), extra).joinToString(" · ")

internal fun BooksModel.axis(): (Double) -> String = { compactNumber(it, language.locale) }

internal fun Money.d(): Double = toBigDecimal().toDouble()

@Composable
internal fun MissingRates(model: BooksModel, missing: Set<Currency>) {
    if (missing.isNotEmpty()) {
        Text(model.t("report.missingRates", missing.joinToString { it.code }), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

// --- Income and expense ---------------------------------------------------------------------------

@Composable
private fun IncomeExpenseReport(model: BooksModel, state: ReportState, filter: ReportFilter) {
    val books = model.books
    val months = filter.from.daysUntil(filter.to) / 30
    val granularity = when {
        months > 36 -> Granularity.YEAR
        months > 18 -> Granularity.QUARTER
        else -> Granularity.MONTH
    }
    val report = remember(model.revision, filter, granularity) { books.reports.incomeExpense(filter, granularity) }
    val comparison = remember(model.revision, filter, state.compare) {
        comparisonFilter(filter, state.compare)?.let { books.reports.incomeExpense(it, Granularity.YEAR).value }
    }
    val periods = report.value
    val locale = model.language.locale
    val labelFormat = DateTimeFormatter.ofPattern(if (granularity == Granularity.MONTH) "MMM yy" else "yyyy", locale)
    fun label(start: LocalDate) = when (granularity) {
        Granularity.QUARTER -> "T${start.month.ordinal / 3 + 1} ${start.year}".let { if (model.language.tag == "en") it.replace("T", "Q") else it }
        else -> java.time.LocalDate.of(start.year, start.month.ordinal + 1, 1).format(labelFormat)
    }

    Text(model.t("report.INCOME_EXPENSE"), style = MaterialTheme.typography.titleLarge)
    Text(subtitle(model, filter), style = MaterialTheme.typography.bodySmall)
    MissingRates(model, report.missingRates)
    val totalIncome = periods.fold(Money.zero(filter.cur(model))) { a, p -> a + p.income }
    val totalExpense = periods.fold(Money.zero(filter.cur(model))) { a, p -> a + p.expense }
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Stat(model.t("report.income"), model.money(totalIncome))
        Stat(model.t("report.expense"), model.money(totalExpense))
        Stat(model.t("report.net"), model.money(totalIncome - totalExpense))
        comparison?.let { c ->
            val net = c.fold(Money.zero(filter.cur(model))) { a, p -> a + p.net }
            Stat(model.t("compare.${state.compare}"), model.money(net))
        }
    }
    GroupedBarChart(
        periods.map { label(it.start) },
        listOf(
            Series(model.t("report.income"), periods.map { it.income.d() }, periods.map { model.money(it.income) }),
            Series(model.t("report.expense"), periods.map { it.expense.d() }, periods.map { model.money(it.expense) }),
        ),
        model.axis(),
        onClick = { p, s ->
            val period = periods[p]
            val rows = books.reports.drillDown(filter.copy(from = period.start, to = period.end))
            val categories = books.categories.list(true).associateBy { it.id }
            val wanted = rows.filter { r ->
                val income = r.categoryId?.let { categories[it]?.kind == CategoryKind.INCOME } ?: r.amount.isPositive
                income == (s == 0)
            }
            state.drill = "${label(period.start)} · ${if (s == 0) model.t("report.income") else model.t("report.expense")}" to wanted
        },
    )
    val table = ReportTable(
        model.t("report.INCOME_EXPENSE"), subtitle(model, filter),
        listOf(model.t("report.periodColumn"), model.t("report.income"), model.t("report.expense"), model.t("report.net")),
        periods.map { listOf(label(it.start), it.income, it.expense, it.net) } + listOf(listOf(model.t("report.total"), totalIncome, totalExpense, totalIncome - totalExpense)),
    )
    TableView(model, table)
}

private fun comparisonFilter(filter: ReportFilter, compare: Compare): ReportFilter? = when (compare) {
    Compare.NONE -> null
    Compare.PREVIOUS -> filter.previousPeriod()
    Compare.LAST_YEAR -> filter.sameLastYear()
}

// --- By category ----------------------------------------------------------------------------------

@Composable
private fun CategoryReport(model: BooksModel, state: ReportState, filter: ReportFilter, kind: CategoryKind) {
    val books = model.books
    val parent = state.parent?.takeIf { it.kind == kind }
    val report = remember(model.revision, filter, kind, parent) { books.reports.byCategory(filter, kind, parent?.id) }
    val comparison = remember(model.revision, filter, kind, parent, state.compare) {
        comparisonFilter(filter, state.compare)?.let { f -> books.reports.byCategory(f, kind, parent?.id).value.associate { it.category?.id to it.amount } }
    }
    val rows = report.value
    val total = rows.fold(Money.zero(filter.cur(model))) { a, r -> a + r.amount }
    val title = model.t(if (kind == CategoryKind.EXPENSE) "report.SPENDING_BY_CATEGORY" else "report.INCOME_BY_CATEGORY")
    fun name(c: Category?) = when {
        c == null -> model.t("register.uncategorized")
        c.id == parent?.id -> model.t("report.directly", c.name(model.language))
        else -> c.name(model.language)
    }

    Text(title, style = MaterialTheme.typography.titleLarge)
    Text(subtitle(model, filter), style = MaterialTheme.typography.bodySmall)
    MissingRates(model, report.missingRates)
    // Breadcrumb back up the category tree.
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        TextButton(onClick = { state.parent = null }) { Text(model.t("report.allCategories")) }
        val path = generateSequence(parent) { c -> c.parentId?.let { id -> books.categories.list(true).firstOrNull { it.id == id } } }.toList().reversed()
        for (c in path) {
            Text("›")
            TextButton(onClick = { state.parent = c }) { Text(c.name(model.language)) }
        }
        Text(model.t("report.total") + " " + model.money(total), Modifier.padding(start = 16.dp), fontWeight = FontWeight.Bold)
    }
    RankedBars(
        rows.map { r ->
            val share = if (total.isZero) 0 else (r.amount.d() / total.d() * 100).toInt()
            val before = comparison?.get(r.category?.id)
            RankedBar(
                name(r.category) + if (r.hasChildren) " ›" else "",
                r.amount.d(), model.money(r.amount),
                note = listOfNotNull("$share %", before?.let { model.t("report.versus", model.money(it)) }).joinToString(" · "),
            )
        },
        slot = if (kind == CategoryKind.EXPENSE) 1 else 0,
        onClick = { i ->
            val row = rows[i]
            when {
                row.hasChildren -> state.parent = row.category
                else -> state.drill = name(row.category) to books.reports.drillDown(filter, categoryId = row.category?.id, uncategorized = row.category == null)
            }
        },
    )
    TableView(
        model,
        ReportTable(
            title + (parent?.let { " · " + it.name(model.language) } ?: ""), subtitle(model, filter),
            listOfNotNull(model.t("register.category"), model.t("register.amount"), comparison?.let { model.t("compare.${state.compare}") }),
            rows.map { r -> listOfNotNull(name(r.category), r.amount, comparison?.let { it[r.category?.id] ?: Money.zero(filter.cur(model)) }) } +
                listOf(listOfNotNull(model.t("report.total"), total, comparison?.let { c -> c.values.fold(Money.zero(filter.cur(model))) { a, m -> a + m } })),
        ),
    )
}

// --- By payee -------------------------------------------------------------------------------------

@Composable
private fun PayeeReport(model: BooksModel, state: ReportState, filter: ReportFilter) {
    val books = model.books
    val report = remember(model.revision, filter) { books.reports.byPayee(filter) }
    // RPT-02: the same payees in the period compared with.
    val comparison = remember(model.revision, filter, state.compare) {
        comparisonFilter(filter, state.compare)?.let { f -> books.reports.byPayee(f).value.associate { it.key to it.amount } }
    }
    val spending = report.value.filter { it.amount.isPositive }
    val zero = Money.zero(filter.cur(model))
    val total = spending.fold(zero) { a, p -> a + p.amount }
    Text(model.t("report.SPENDING_BY_PAYEE"), style = MaterialTheme.typography.titleLarge)
    Text(subtitle(model, filter), style = MaterialTheme.typography.bodySmall)
    MissingRates(model, report.missingRates)
    RankedBars(
        spending.take(30).map { p ->
            RankedBar(p.name.ifBlank { "?" }, p.amount.d(), model.money(p.amount), note = comparison?.let { c -> model.t("report.versus", model.money(c[p.key] ?: zero)) })
        },
        slot = 1,
        onClick = { i ->
            val p = spending[i]
            state.drill = p.name to books.reports.drillDown(filter, payeeId = p.payeeId, payeeText = p.name.takeIf { p.payeeId == null })
        },
    )
    TableView(
        model,
        ReportTable(
            model.t("report.SPENDING_BY_PAYEE"), subtitle(model, filter),
            listOfNotNull(model.t("register.payee"), model.t("register.amount"), comparison?.let { model.t("compare.${state.compare}") }),
            spending.map { p -> listOfNotNull(p.name, p.amount, comparison?.let { it[p.key] ?: zero }) } +
                // The comparison's total is its spending, as the rows above are.
                listOf(listOfNotNull(model.t("report.total"), total, comparison?.let { c -> c.values.filter { it.isPositive }.fold(zero) { a, m -> a + m } })),
        ),
    )
}

// --- Net worth ------------------------------------------------------------------------------------

@Composable
private fun NetWorthReport(model: BooksModel, filter: ReportFilter) {
    val books = model.books
    val dates = remember(filter) { books.reports.monthEnds(filter.from, filter.to) }
    val report = remember(model.revision, dates, filter.accountIds, filter.currency) { books.reports.netWorth(dates, filter.accountIds, filter.currency) }
    val points = report.value
    val locale = model.language.locale
    val labels = dates.map { java.time.LocalDate.of(it.year, it.month.ordinal + 1, 1).format(DateTimeFormatter.ofPattern("MMM yy", locale)) }

    Text(model.t("report.NET_WORTH"), style = MaterialTheme.typography.titleLarge)
    Text(subtitle(model, filter), style = MaterialTheme.typography.bodySmall)
    MissingRates(model, report.missingRates)
    points.lastOrNull()?.let { last ->
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Stat(model.t("report.netWorth"), model.money(last.net))
            Stat(model.t("report.assets"), model.money(last.assets))
            Stat(model.t("report.liabilities"), model.money(last.liabilities))
            points.firstOrNull()?.let { first -> Stat(model.t("report.change"), model.money(last.net - first.net)) }
        }
    }
    LineChart(
        labels,
        listOf(
            Series(model.t("report.netWorth"), points.map { it.net.d() }, points.map { model.money(it.net) }),
            Series(model.t("report.assets"), points.map { it.assets.d() }, points.map { model.money(it.assets) }),
            Series(model.t("report.liabilities"), points.map { it.liabilities.d() }, points.map { model.money(it.liabilities) }),
        ),
        model.axis(),
    )
    TableView(
        model,
        ReportTable(
            model.t("report.NET_WORTH"), subtitle(model, filter),
            listOf(model.t("report.date"), model.t("report.assets"), model.t("report.liabilities"), model.t("report.netWorth")),
            points.map { listOf(it.date, it.assets, it.liabilities, it.net) },
        ),
    )
}

// --- Debt summary ---------------------------------------------------------------------------------

/** Every liability: what is owed, at what rate, the payment, and when it will be paid off. */
@Composable
private fun DebtReport(model: BooksModel, accountIds: Set<String>?) {
    val lines = remember(model.revision, accountIds) { model.books.loans.debtSummary(today()).filter { accountIds == null || it.account.id in accountIds } }
    val locale = model.language.locale
    fun pct(r: java.math.BigDecimal?) = r?.let { java.text.NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 2; maximumFractionDigits = 3 }.format(it.movePointRight(2)) + " %" }.orEmpty()
    Text(model.t("report.DEBT"), style = MaterialTheme.typography.titleLarge)
    Text(model.t("report.debtHint", model.date(today())), style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        for ((currency, list) in lines.groupBy { it.account.currency }.toSortedMap(compareBy { it.code })) {
            Stat(model.t("report.totalOwed", currency.code), model.money(list.map { it.owed }.sum(currency)))
            list.mapNotNull { it.interestRemaining }.takeIf { it.isNotEmpty() }?.let { Stat(model.t("report.interestLeft", currency.code), model.money(it.sum(currency))) }
        }
    }
    if (lines.isEmpty()) Text(model.t("report.noDebt"))
    TableView(
        model,
        ReportTable(
            model.t("report.DEBT"), model.date(today()),
            listOf(model.t("nav.accounts"), model.t("account.type"), model.t("loans.owed"), model.t("loans.rate"), model.t("report.cashAdvanceRate"), model.t("loans.payment"), model.t("loans.payoff"), model.t("loans.interestLeft"), model.t("loans.renewal")),
            lines.map { listOf(it.account.name, model.t("accountType.${it.account.type}"), it.owed, pct(it.annualRate), pct(it.cashAdvanceRate), it.payment, it.payoffDate, it.interestRemaining, it.termEnd) },
        ),
        startOpen = true,
    )
}

// --- Reconciliation history -----------------------------------------------------------------------

@Composable
private fun ReconciliationReport(model: BooksModel) {
    val books = model.books
    // Accounts last reconciled more than this many days ago are flagged.
    val behindAfter = 45
    val accounts = remember(model.revision) {
        val last = books.statements.lastReconciled()
        books.accounts.list().map { s -> Triple(s, last[s.account.id], books.statements.statements(s.account.id).count { it.status == StatementStatus.OPEN }) }
    }
    val behind = accounts.mapNotNull { (s, reconciled, _) -> reconciled?.daysUntil(today())?.takeIf { it > behindAfter }?.let { s.account.name to it } }
    val rows = accounts.map { (s, reconciled, open) ->
        val days = reconciled?.daysUntil(today())
        listOf<Any?>(
            s.account.name, reconciled ?: model.t("accounts.neverReconciled"), days?.toString() ?: "", open.toString(), s.balance - s.clearedBalance,
            if (days != null && days > behindAfter) model.t("report.behind") else "",
        )
    }
    Text(model.t("report.RECONCILIATION"), style = MaterialTheme.typography.titleLarge)
    Text(model.t("report.reconciliationHint"), style = MaterialTheme.typography.bodySmall)
    // The accounts behind stand out above the table, in the warning colour.
    for ((name, days) in behind) {
        Text(model.t("report.behindAccount", name, days), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
    }
    TableView(
        model,
        ReportTable(
            model.t("report.RECONCILIATION"), model.date(today()),
            listOf(model.t("nav.accounts"), model.t("report.lastReconciled"), model.t("report.daysSince"), model.t("report.openStatements"), model.t("report.uncleared"), model.t("report.flag")),
            rows,
        ),
        startOpen = true,
    )
}

// --- Shared pieces --------------------------------------------------------------------------------

/** Screens and reports whose tables hold tax figures: slips, capital gains, contribution room, medical credits. */
private fun hasTaxFigures(model: BooksModel): Boolean = when (model.section) {
    Section.INVESTMENTS, Section.PLANS, Section.MEDICAL -> true
    Section.REPORTS -> model.reportState.kind in setOf(ReportKind.INVESTMENT_INCOME, ReportKind.PLANS, ReportKind.FX, ReportKind.MEDICAL)
    else -> false
}

@Composable
fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

/** The table view under every chart (accessibility and exact values), with export buttons (RPT-04). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TableView(model: BooksModel, shown: ReportTable, startOpen: Boolean = false) {
    // TAX-04: exports of tables with tax figures carry the notice too.
    val table = if (hasTaxFigures(model)) shown.copy(notes = shown.notes + model.t("about.notice.tax")) else shown
    var open by remember(table.title) { mutableStateOf(startOpen) }
    val locale = model.language.locale
    FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { open = !open }) { Text(model.t(if (open) "report.hideTable" else "report.showTable")) }
        for (format in ExportFormat.entries) {
            OutlinedButton(onClick = { model.act { ReportExport.save(table, format, locale, model.t("report.export")) } }) {
                Text(model.t("report.export.$format"))
            }
        }
        OutlinedButton(onClick = { model.act { ReportExport.print(table, locale) } }) { Text(model.t("report.print")) }
    }
    if (open) {
        Column(Modifier.padding(top = 8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                table.columns.forEachIndexed { c, name ->
                    Text(name, Modifier.weight(1f).padding(horizontal = 6.dp), fontWeight = FontWeight.Bold, textAlign = if (table.isNumeric(c)) TextAlign.End else TextAlign.Start, style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
            for (row in table.rows) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    table.columns.indices.forEach { c ->
                        Text(
                            ReportExport.text(row.getOrNull(c), locale), Modifier.weight(1f).padding(horizontal = 6.dp),
                            textAlign = if (table.isNumeric(c)) TextAlign.End else TextAlign.Start, style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** RPT-01: the transactions behind a figure; each opens in its account's register. */
@Composable
fun DrillDialog(model: BooksModel, title: String, rows: List<DrillRow>, onClose: () -> Unit) {
    val books = model.books
    val accounts = remember { books.accounts.list(includeClosed = true).associate { it.account.id to it.account.name } }
    val categories = remember { books.categories.list(true).associateBy { it.id } }
    val total = rows.mapNotNull { it.baseAmount }.let { amounts -> amounts.fold(Money.zero(amounts.firstOrNull()?.currency ?: books.reports.base)) { a, m -> a + m } }
    WideDialog(title, model.t("common.close"), onClose) {
            Column(Modifier.width(820.dp)) {
                Text(model.t("report.drillSummary", rows.size, model.money(total)), style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max = 460.dp).padding(top = 8.dp)) {
                    items(rows) { r ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                model.section = Section.ACCOUNTS
                                model.reconcilingStatementId = null
                                model.selectedAccountId = r.accountId
                                onClose()
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(model.date(r.date), Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall)
                            Text(accounts[r.accountId].orEmpty(), Modifier.width(150.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.payee.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.categoryId?.let { categories[it]?.name(model.language) } ?: model.t("register.uncategorized"), Modifier.width(180.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            MoneyText(model, r.amount, modifier = Modifier.width(120.dp), textAlign = TextAlign.End)
                        }
                    }
                }
            }
    }
}
