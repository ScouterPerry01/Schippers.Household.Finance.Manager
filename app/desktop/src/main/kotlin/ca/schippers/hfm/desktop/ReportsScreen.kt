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
import androidx.compose.material3.AlertDialog
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
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import java.time.format.DateTimeFormatter

enum class ReportKind { INCOME_EXPENSE, SPENDING_BY_CATEGORY, INCOME_BY_CATEGORY, SPENDING_BY_PAYEE, NET_WORTH, BUDGET, RECONCILIATION }
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
    /** Drill path in the category reports: the category whose subcategories are shown. */
    var parent by mutableStateOf<Category?>(null)
    var drill by mutableStateOf<Pair<String, List<DrillRow>>?>(null)

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
    val accountIds = remember(model.revision, state.groupId) {
        state.groupId?.let { g -> books.accounts.list(includeClosed = true).filter { it.account.groupId == g }.map { it.account.id }.toSet() }
    }
    val filter = ReportFilter(from, to, accountIds, state.memberId, state.tagId)

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(230.dp).fillMaxHeight().padding(8.dp)) {
            for (kind in ReportKind.entries) {
                NavigationDrawerItem(
                    label = { Text(model.t("report.${kind.name}")) },
                    selected = state.kind == kind,
                    onClick = { state.kind = kind; state.parent = null },
                )
            }
        }
        VerticalDivider()
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            // Filters in one row above the chart.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.kind != ReportKind.RECONCILIATION) {
                    Picker(model.t("report.period"), RangePreset.entries, state.preset, { model.t("range.$it") }, Modifier.width(200.dp)) { state.preset = it }
                    if (state.preset == RangePreset.CUSTOM) {
                        DateInput(model.t("report.from"), state.customFrom, Modifier.width(150.dp)) { state.customFrom = it }
                        DateInput(model.t("report.to"), state.customTo, Modifier.width(150.dp)) { state.customTo = it }
                    }
                }
                if (groups.size > 1) {
                    Picker(model.t("report.accounts"), listOf(null) + groups, groups.firstOrNull { it.id == state.groupId }, { it?.name ?: model.t("report.allAccounts") }, Modifier.width(200.dp)) {
                        state.groupId = it?.id
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
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                when (state.kind) {
                    ReportKind.INCOME_EXPENSE -> IncomeExpenseReport(model, state, filter)
                    ReportKind.SPENDING_BY_CATEGORY -> CategoryReport(model, state, filter, CategoryKind.EXPENSE)
                    ReportKind.INCOME_BY_CATEGORY -> CategoryReport(model, state, filter, CategoryKind.INCOME)
                    ReportKind.SPENDING_BY_PAYEE -> PayeeReport(model, state, filter)
                    ReportKind.NET_WORTH -> NetWorthReport(model, filter)
                    ReportKind.BUDGET -> BudgetReportView(model, LocalDate(to.year, to.month, 1), yearView = state.preset in setOf(RangePreset.THIS_YEAR, RangePreset.LAST_YEAR))
                    ReportKind.RECONCILIATION -> ReconciliationReport(model)
                }
            }
        }
    }
    state.drill?.let { (title, rows) -> DrillDialog(model, title, rows) { state.drill = null } }
}

private fun subtitle(model: BooksModel, filter: ReportFilter, extra: String? = null): String =
    listOfNotNull("${model.date(filter.from)} – ${model.date(filter.to)}", model.t("report.inCurrency", model.books.reports.base.code), extra).joinToString(" · ")

private fun BooksModel.axis(): (Double) -> String = { compactNumber(it, language.locale) }

private fun Money.d(): Double = toBigDecimal().toDouble()

@Composable
private fun MissingRates(model: BooksModel, missing: Set<Currency>) {
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
    val totalIncome = periods.fold(Money.zero(books.reports.base)) { a, p -> a + p.income }
    val totalExpense = periods.fold(Money.zero(books.reports.base)) { a, p -> a + p.expense }
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Stat(model.t("report.income"), model.money(totalIncome))
        Stat(model.t("report.expense"), model.money(totalExpense))
        Stat(model.t("report.net"), model.money(totalIncome - totalExpense))
        comparison?.let { c ->
            val net = c.fold(Money.zero(books.reports.base)) { a, p -> a + p.net }
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
    val total = rows.fold(Money.zero(books.reports.base)) { a, r -> a + r.amount }
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
            rows.map { r -> listOfNotNull(name(r.category), r.amount, comparison?.let { it[r.category?.id] ?: Money.zero(books.reports.base) }) } +
                listOf(listOfNotNull(model.t("report.total"), total, comparison?.let { c -> c.values.fold(Money.zero(books.reports.base)) { a, m -> a + m } })),
        ),
    )
}

// --- By payee -------------------------------------------------------------------------------------

@Composable
private fun PayeeReport(model: BooksModel, state: ReportState, filter: ReportFilter) {
    val books = model.books
    val report = remember(model.revision, filter) { books.reports.byPayee(filter) }
    val spending = report.value.filter { it.amount.isPositive }
    Text(model.t("report.SPENDING_BY_PAYEE"), style = MaterialTheme.typography.titleLarge)
    Text(subtitle(model, filter), style = MaterialTheme.typography.bodySmall)
    MissingRates(model, report.missingRates)
    RankedBars(
        spending.take(30).map { RankedBar(it.name.ifBlank { "?" }, it.amount.d(), model.money(it.amount)) },
        slot = 1,
        onClick = { i ->
            val p = spending[i]
            state.drill = p.name to books.reports.drillDown(filter, payeeId = p.payeeId, payeeText = p.name.takeIf { p.payeeId == null })
        },
    )
    TableView(
        model,
        ReportTable(model.t("report.SPENDING_BY_PAYEE"), subtitle(model, filter), listOf(model.t("register.payee"), model.t("register.amount")), spending.map { listOf(it.name, it.amount) }),
    )
}

// --- Net worth ------------------------------------------------------------------------------------

@Composable
private fun NetWorthReport(model: BooksModel, filter: ReportFilter) {
    val books = model.books
    val dates = remember(filter) { books.reports.monthEnds(filter.from, filter.to) }
    val report = remember(model.revision, dates, filter.accountIds) { books.reports.netWorth(dates, filter.accountIds) }
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

// --- Reconciliation history -----------------------------------------------------------------------

@Composable
private fun ReconciliationReport(model: BooksModel) {
    val books = model.books
    val rows = remember(model.revision) {
        val last = books.statements.lastReconciled()
        books.accounts.list().map { s ->
            val statements = books.statements.statements(s.account.id)
            val open = statements.count { it.status == StatementStatus.OPEN }
            val reconciled = last[s.account.id]
            val days = reconciled?.daysUntil(today())
            listOf<Any?>(s.account.name, reconciled ?: model.t("accounts.neverReconciled"), days?.toString() ?: "", open.toString(), s.balance - s.clearedBalance)
        }
    }
    Text(model.t("report.RECONCILIATION"), style = MaterialTheme.typography.titleLarge)
    Text(model.t("report.reconciliationHint"), style = MaterialTheme.typography.bodySmall)
    TableView(
        model,
        ReportTable(
            model.t("report.RECONCILIATION"), model.date(today()),
            listOf(model.t("nav.accounts"), model.t("report.lastReconciled"), model.t("report.daysSince"), model.t("report.openStatements"), model.t("report.uncleared")),
            rows,
        ),
        startOpen = true,
    )
}

// --- Shared pieces --------------------------------------------------------------------------------

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
fun TableView(model: BooksModel, table: ReportTable, startOpen: Boolean = false) {
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
                    Text(name, Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = if (table.isNumeric(c)) TextAlign.End else TextAlign.Start, style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
            for (row in table.rows) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    table.columns.indices.forEach { c ->
                        Text(
                            ReportExport.text(row.getOrNull(c), locale), Modifier.weight(1f),
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
    val total = rows.mapNotNull { it.baseAmount }.fold(Money.zero(books.reports.base)) { a, m -> a + m }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
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
        },
        confirmButton = { TextButton(onClick = onClose) { Text(model.t("common.close")) } },
    )
}
