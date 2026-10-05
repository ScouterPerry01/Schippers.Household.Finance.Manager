package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.BudgetLine
import ca.schippers.hfm.books.BudgetPeriod
import ca.schippers.hfm.books.BudgetReport
import ca.schippers.hfm.books.Category
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.domain.CategoryKind
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import java.time.format.DateTimeFormatter

/** BUD-01: budgets per category, compared with what was actually spent. */
@Composable
fun BudgetsScreen(model: BooksModel) {
    var month by remember { mutableStateOf(today().let { LocalDate(it.year, it.month, 1) }) }
    var yearView by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<Category, BudgetLine?>?>(null) }
    var adding by remember { mutableStateOf(false) }
    var suggesting by remember { mutableStateOf(false) }
    val locale = model.language.locale

    Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.t("nav.budgets"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { suggesting = true }) { Text(model.t("budget.suggest")) }
            Button(onClick = { adding = true }, modifier = Modifier.padding(start = 8.dp)) { Text(model.t("budget.add")) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            TextButton(onClick = { month = month.minus(DatePeriod(months = if (yearView) 12 else 1)) }) { Text("◀") }
            Text(
                if (yearView) month.year.toString()
                else java.time.YearMonth.of(month.year, month.month.ordinal + 1).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)).replaceFirstChar { it.titlecase(locale) },
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(200.dp),
            )
            TextButton(onClick = { month = month.plus(DatePeriod(months = if (yearView) 12 else 1)) }) { Text("▶") }
            LabeledCheckbox(model.t("budget.yearView"), yearView) { yearView = it }
        }
        val report = remember(model.revision, month, yearView) { if (yearView) model.books.budgets.year(month.year) else model.books.budgets.month(month) }
        BudgetReportView(model, report, yearView) { line -> editing = line.category to line }
    }

    if (adding) {
        CategoryPickDialog(model, { adding = false }) { category -> adding = false; editing = category to null }
    }
    editing?.let { (category, line) -> BudgetDialog(model, category, line, month) { editing = null } }
    if (suggesting) SuggestDialog(model, month) { suggesting = false }
}

/** Budget vs actual as bars with the budget as a target marker; also used by the Reports screen. */
@Composable
fun BudgetReportView(model: BooksModel, report: BudgetReport, yearView: Boolean, onEdit: ((BudgetLine) -> Unit)? = null) {
    val books = model.books
    val base = books.reports.base
    val title = model.t("report.BUDGET")
    if (report.lines.isEmpty()) {
        Text(model.t("budget.none"), Modifier.padding(8.dp))
        return
    }
    if (report.missingRates.isNotEmpty()) {
        Text(model.t("report.missingRates", report.missingRates.joinToString { it.code }), color = MaterialTheme.colorScheme.error)
    }
    for (kind in listOf(CategoryKind.EXPENSE, CategoryKind.INCOME)) {
        val lines = report.lines.filter { it.category.kind == kind }
        if (lines.isEmpty()) continue
        val (budgeted, actual) = report.total(kind, base)
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
            Stat(model.t(if (kind == CategoryKind.EXPENSE) "budget.spent" else "budget.received"), model.money(actual))
            Stat(model.t("budget.budgeted"), model.money(budgeted))
            Stat(model.t("budget.remaining"), model.money(budgeted - actual))
        }
        RankedBars(
            lines.map { l ->
                RankedBar(
                    l.category.name(model.language) + if (l.budget.period == BudgetPeriod.ANNUAL && !yearView) " (${model.t("budget.annualShort")})" else "",
                    l.actual.toBigDecimal().toDouble(),
                    model.t("budget.of", model.money(l.actual), model.money(l.budgeted)),
                    target = l.budgeted.toBigDecimal().toDouble(),
                    note = when {
                        kind == CategoryKind.EXPENSE && l.isOver -> model.t("budget.over", model.money(l.actual - l.budgeted))
                        else -> model.t("budget.left", model.money(l.remaining)) + if (!l.carriedOver.isZero) " · " + model.t("budget.carried", model.money(l.carriedOver)) else ""
                    },
                    alert = kind == CategoryKind.EXPENSE && l.isOver,
                )
            },
            slot = if (kind == CategoryKind.EXPENSE) 1 else 0,
            onClick = onEdit?.let { edit -> { i: Int -> edit(lines[i]) } },
        )
    }
    TableView(
        model,
        ReportTable(
            title, "${model.date(report.from)} – ${model.date(report.to)} · ${model.t("report.inCurrency", base.code)}",
            listOf(model.t("register.category"), model.t("budget.budgeted"), model.t("budget.actual"), model.t("budget.remaining")),
            report.lines.map { listOf(it.category.name(model.language), it.budgeted, it.actual, it.remaining) },
        ),
    )
}

@Composable
private fun CategoryPickDialog(model: BooksModel, onClose: () -> Unit, onPick: (Category) -> Unit) {
    val tree = remember { model.books.categories.tree() }
    var chosen by remember { mutableStateOf<Pair<Category, Int>?>(null) }
    FormDialog(model.t("budget.add"), model.t("common.continue"), model.t("common.cancel"), canSave = chosen != null, onDismiss = onClose, onSave = { chosen?.let { onPick(it.first) } }) {
        Picker(model.t("register.category"), tree, chosen, { it.first.name(model.language) }, indent = { it.second }) { chosen = it }
    }
}

@Composable
private fun BudgetDialog(model: BooksModel, category: Category, line: BudgetLine?, month: LocalDate, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val base = books.reports.base
    val existing = line?.budget ?: books.budgets.list().firstOrNull { it.categoryId == category.id }
    var period by remember { mutableStateOf(existing?.period ?: BudgetPeriod.MONTHLY) }
    var amount by remember { mutableStateOf(existing?.amount?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var rollover by remember { mutableStateOf(existing?.rollover ?: false) }
    var start by remember { mutableStateOf((existing?.startMonth ?: month).toString()) }
    var confirmDelete by remember { mutableStateOf(false) }

    FormDialog(model.t("budget.edit", category.name(model.language)), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            val value = parseAmount(amount, base, locale) ?: throw ValidationException("error.amountRequired")
            val startDate = runCatching { LocalDate.parse(start.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }
            books.budgets.set(category.id, period, value, rollover && period == BudgetPeriod.MONTHLY, LocalDate(startDate.year, startDate.month, 1))
        }
        if (ok != null) onClose()
    }) {
        Picker(model.t("budget.period"), BudgetPeriod.entries, period, { model.t("budgetPeriod.$it") }) { period = it }
        AmountInput(model.t("register.amount"), amount, base, locale, Modifier, model::money) { amount = it }
        if (period == BudgetPeriod.MONTHLY) LabeledCheckbox(model.t("budget.rollover"), rollover) { rollover = it }
        DateInput(model.t("budget.start"), start) { start = it }
        Text(model.t("budget.coversChildren"), style = MaterialTheme.typography.bodySmall)
        if (existing != null) {
            TextButton(onClick = { confirmDelete = true }) {
                Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmDelete) {
        FormDialog(model.t("budget.delete.title"), model.t("common.delete"), model.t("common.cancel"), onDismiss = { confirmDelete = false }, onSave = {
            if (model.act { books.budgets.remove(category.id) } != null) {
                confirmDelete = false
                onClose()
            }
        }) { Text(model.t("budget.delete.body", category.name(model.language))) }
    }
}

/** BUD-05: budgets proposed from the monthly average of the last 12 months. */
@Composable
private fun SuggestDialog(model: BooksModel, month: LocalDate, onClose: () -> Unit) {
    val books = model.books
    val suggestions = remember { books.budgets.suggestions(today()) }
    val categories = remember { books.categories.list().associateBy { it.id } }
    val existing = remember { books.budgets.list().map { it.categoryId }.toSet() }
    var chosen by remember { mutableStateOf(suggestions.keys - existing) }
    FormDialog(model.t("budget.suggest"), model.t("budget.apply"), model.t("common.cancel"), canSave = chosen.isNotEmpty(), onDismiss = onClose, onSave = {
        val ok = model.act {
            for (id in chosen) books.budgets.set(id, BudgetPeriod.MONTHLY, suggestions.getValue(id), startMonth = month)
        }
        if (ok != null) onClose()
    }) {
        Text(model.t("budget.suggest.explain"), style = MaterialTheme.typography.bodySmall)
        if (suggestions.isEmpty()) Text(model.t("budget.suggest.none"))
        for ((id, amount) in suggestions.entries.sortedByDescending { it.value }) {
            val name = categories[id]?.name(model.language) ?: continue
            LabeledCheckbox("$name · ${model.money(amount)} ${model.t("budget.perMonth")}" + if (id in existing) " (${model.t("budget.alreadySet")})" else "", id in chosen) { checked ->
                chosen = if (checked) chosen + id else chosen - id
            }
        }
    }
}
