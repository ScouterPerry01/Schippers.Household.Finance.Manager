package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import ca.schippers.hfm.books.CustomLayout
import ca.schippers.hfm.books.PivotTable
import ca.schippers.hfm.books.ReportChart
import ca.schippers.hfm.books.ReportDefinition
import ca.schippers.hfm.books.ReportDimension
import ca.schippers.hfm.books.ReportFilter
import ca.schippers.hfm.books.ReportMeasure
import ca.schippers.hfm.books.ReportSchedule
import ca.schippers.hfm.books.SavedReport
import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.money.Currency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import java.io.File

/** RPT-03: the screen's choices as a definition to save. */
fun ReportState.definition(): ReportDefinition = ReportDefinition(
    kind.name, preset.name, customFrom, customTo, groupId, memberId, tagId, accountSet, currency?.code, compare.name, layout.takeIf { kind == ReportKind.CUSTOM },
)

/** RPT-03: a saved report's choices back on the screen; unknown names (from a newer version) keep the current choice. */
fun ReportState.load(d: ReportDefinition, saved: SavedReport) {
    kind = runCatching { ReportKind.valueOf(d.kind) }.getOrDefault(kind)
    preset = runCatching { RangePreset.valueOf(d.preset) }.getOrDefault(preset)
    d.customFrom?.let { customFrom = it }
    d.customTo?.let { customTo = it }
    groupId = d.groupId
    memberId = d.memberId
    tagId = d.tagId
    accountSet = d.accountIds
    currency = d.currency?.let { runCatching { Currency.of(it) }.getOrNull() }
    compare = d.compare?.let { runCatching { Compare.valueOf(it) }.getOrNull() } ?: Compare.NONE
    d.layout?.let { layout = it }
    parent = null
    savedId = saved.id
}

/** RPT-07: the accounts a report covers: those of the chosen group, narrowed to the chosen set; null for all. */
fun BooksModel.reportAccounts(groupId: String?, set: Set<String>?): Set<String>? {
    val inGroup = groupId?.let { g -> books.accounts.list(includeClosed = true).filter { it.account.groupId == g }.map { it.account.id }.toSet() }
    return when {
        inGroup == null -> set
        set == null -> inGroup
        else -> inGroup intersect set
    }
}

/** The words a custom report needs, in the user's language. */
fun BooksModel.pivotLabels(): (String) -> String = { key -> t("custom.label.$key") }

/** RPT-03, RPT-04: a custom report as a table to show and export: a row per row key, a column per column key, and totals. */
fun pivotReportTable(model: BooksModel, title: String, subtitle: String, layout: CustomLayout, pivot: PivotTable): ReportTable {
    val single = layout.columns == null
    val columns = listOf(model.t("custom.dimension.${layout.rows}")) + (if (single) emptyList() else pivot.columns.map { it.label }) + model.t("custom.total")
    val rows = pivot.rows.map { r -> listOf<Any?>(r.label) + (if (single) emptyList() else pivot.columns.map { pivot.cell(r, it) }) + pivot.rowTotal(r) } +
        listOf(listOf<Any?>(model.t("custom.total")) + (if (single) emptyList() else pivot.columns.map { pivot.columnTotal(it) }) + pivot.total)
    return ReportTable(title, subtitle, columns, rows)
}

/** RPT-03: the builder (rows, columns, what is added up, chart) and the report it makes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomReportView(model: BooksModel, state: ReportState, filter: ReportFilter) {
    val books = model.books
    val layout = state.layout
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Picker(model.t("custom.rows"), ReportDimension.entries, layout.rows, { model.t("custom.dimension.$it") }, Modifier.width(190.dp)) { state.layout = layout.copy(rows = it) }
        Picker(model.t("custom.columns"), listOf(null) + listOf(ReportDimension.MONTH, ReportDimension.QUARTER, ReportDimension.YEAR, ReportDimension.PERSON, ReportDimension.ACCOUNT, ReportDimension.TOP_CATEGORY),
            layout.columns, { it?.let { d -> model.t("custom.dimension.$d") } ?: model.t("custom.noColumns") }, Modifier.width(190.dp)) { state.layout = layout.copy(columns = it) }
        Picker(model.t("custom.measure"), ReportMeasure.entries, layout.measure, { model.t("custom.measure.$it") }, Modifier.width(200.dp)) { state.layout = layout.copy(measure = it) }
        Picker(model.t("custom.chart"), ReportChart.entries, layout.chart, { model.t("custom.chart.$it") }, Modifier.width(180.dp)) { state.layout = layout.copy(chart = it) }
    }
    val pivot = remember(model.revision, filter, layout) { books.customReports.run(filter, layout, model.pivotLabels(), model.language == Language.FRENCH) }
    val title = model.t("report.CUSTOM") + " · " + model.t("custom.measure.${layout.measure}")
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
    Text(subtitle(model, filter), style = MaterialTheme.typography.bodySmall)
    MissingRates(model, pivot.missingRates)
    if (pivot.rows.isEmpty()) {
        Text(model.t("custom.empty"), Modifier.padding(vertical = 12.dp))
        return
    }
    val chart = if (layout.columns == null && layout.chart != ReportChart.TABLE) ReportChart.RANKED else layout.chart
    // Grouped bars and lines stay readable with a few series; the table has every row.
    val shown = pivot.rows.take(6)
    val series = shown.map { r -> Series(r.label, pivot.columns.map { pivot.cell(r, it).d() }, pivot.columns.map { model.money(pivot.cell(r, it)) }) }
    when (chart) {
        ReportChart.BARS -> GroupedBarChart(pivot.columns.map { it.label }, series, model.axis(), Modifier.fillMaxWidth())
        ReportChart.LINES -> LineChart(pivot.columns.map { it.label }, series, model.axis(), Modifier.fillMaxWidth())
        ReportChart.RANKED -> RankedBars(pivot.rows.map { RankedBar(it.label, pivot.rowTotal(it).d(), model.money(pivot.rowTotal(it))) })
        ReportChart.TABLE -> Unit
    }
    if (pivot.rows.size > shown.size && chart != ReportChart.RANKED && chart != ReportChart.TABLE) {
        Text(model.t("custom.moreInTable", pivot.rows.size - shown.size), style = MaterialTheme.typography.bodySmall)
    }
    TableView(model, pivotReportTable(model, title, subtitle(model, filter), layout, pivot), startOpen = chart == ReportChart.TABLE)
}

/** RPT-07: a set of accounts for reports, such as the cottage's; none chosen means every account. */
@Composable
fun AccountSetDialog(model: BooksModel, chosen: Set<String>?, onDone: (Set<String>?) -> Unit) {
    val accounts = remember { model.books.accounts.list(includeClosed = true).map { it.account } }
    var picked by remember { mutableStateOf(chosen ?: emptySet()) }
    FormDialog(model.t("report.chooseAccounts"), model.t("common.save"), model.t("common.cancel"), onDismiss = { onDone(chosen) }, onSave = { onDone(picked.ifEmpty { null }) }) {
        Text(model.t("report.chooseAccountsHint"), style = MaterialTheme.typography.bodySmall)
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            for (a in accounts) LabeledCheckbox(a.name, a.id in picked) { on -> picked = if (on) picked + a.id else picked - a.id }
        }
        TextButton(onClick = { picked = emptySet() }) { Text(model.t("report.allAccounts")) }
    }
}

/** RPT-03, RPT-05: saves the report on the screen under a name; a custom report can also be made on a schedule. */
@Composable
fun SaveReportDialog(model: BooksModel, state: ReportState, onClose: () -> Unit) {
    val books = model.books
    val existing = remember { books.savedReports.list().firstOrNull { it.id == state.savedId } }
    var name by remember { mutableStateOf(existing?.name ?: model.t("report.${state.kind}")) }
    var schedule by remember { mutableStateOf(existing?.schedule) }
    var folder by remember { mutableStateOf(existing?.folder.orEmpty()) }
    var asNew by remember { mutableStateOf(existing == null) }
    val custom = state.kind == ReportKind.CUSTOM
    FormDialog(model.t("report.saveTitle"), model.t("common.save"), model.t("common.cancel"), canSave = name.isNotBlank(), onDismiss = onClose, onSave = {
        val saved = model.act {
            books.savedReports.save(
                SavedReport(if (asNew) "" else existing!!.id, name, state.definition().toJson(), schedule.takeIf { custom }, folder.takeIf { custom && schedule != null }, existing?.lastPeriod),
            )
        }
        if (saved != null) {
            state.savedId = saved.id
            onClose()
        }
    }) {
        TextInput(model.t("report.saveName"), name) { name = it }
        if (existing != null) LabeledCheckbox(model.t("report.saveAsNew"), asNew) { asNew = it }
        if (custom) {
            Picker(model.t("report.schedule"), listOf(null) + ReportSchedule.entries, schedule, { it?.let { s -> model.t("report.schedule.$s") } ?: model.t("report.schedule.none") }) { schedule = it }
            if (schedule != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(folder.ifBlank { model.t("report.noFolder") }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { chooseDirectory(model.t("report.chooseFolder"))?.let { folder = it.toString() } }) { Text(model.t("report.chooseFolder")) }
                }
                Text(model.t("report.scheduleHint"), style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Text(model.t("report.scheduleCustomOnly"), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * RPT-05: while the household is open, makes each scheduled report whose period has ended, as a
 * PDF in its folder, such as "Cottage 2026-09.pdf". Missed periods are made the next time it is open.
 */
suspend fun scheduledReports(model: BooksModel) {
    while (true) {
        val made = withContext(Dispatchers.IO) { runCatching { makeDueReports(model) }.getOrDefault(emptyList()) }
        if (made.isNotEmpty()) model.reportMessage = model.t("report.scheduledMade", made.joinToString(", "))
        delay(60 * 60_000L)
    }
}

/** Makes the reports due now; returns the files written. */
fun makeDueReports(model: BooksModel, today: LocalDate = today()): List<String> {
    val books = model.books
    val made = ArrayList<String>()
    for ((report, period) in books.savedReports.due(today)) {
        val d = ReportDefinition.fromJson(report.definition) ?: continue
        val layout = d.layout ?: continue
        val folder = report.folder?.let(::File)?.takeIf { it.isDirectory } ?: continue
        val filter = ReportFilter(period.from, period.to, model.reportAccounts(d.groupId, d.accountIds), d.memberId, d.tagId, d.currency?.let { runCatching { Currency.of(it) }.getOrNull() })
        val pivot = books.customReports.run(filter, layout, model.pivotLabels(), model.language == Language.FRENCH)
        val title = "${report.name} · ${period.id}"
        val file = File(folder, "${report.name.replace(Regex("""[\\/:*?"<>|]"""), "-")} ${period.id}.pdf")
        ReportExport.pdf(pivotReportTable(model, title, subtitle(model, filter), layout, pivot), file, model.language.locale)
        books.savedReports.markMade(report.id, period)
        made += file.name
    }
    return made
}

/** The year in review: the year at a glance against the one before, to read, print or share. */
@Composable
fun YearReviewReport(model: BooksModel, year: Int) {
    val r = remember(model.revision, year) { model.books.yearReview.review(year, model.pivotLabels(), model.language == Language.FRENCH) }
    val title = model.t(if (year == today().year) "review.titleSoFar" else "review.title", year.toString())
    Text(title, style = MaterialTheme.typography.titleLarge)
    val start = r.netWorthStart
    val end = r.netWorthEnd
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Stat(model.t("review.income"), model.money(r.income))
        Stat(model.t("review.spending"), model.money(r.spending))
        Stat(model.t("review.kept"), model.money(r.kept) + (r.savingsRate?.let { " ($it %)" } ?: ""))
        if (start != null && end != null) Stat(model.t("review.netWorth"), model.money(end - start))
    }
    val sections = listOf(
        model.t("review.compared", (year - 1).toString()) to listOf<Pair<String, Any?>>(model.t("review.income") to r.lastYearIncome, model.t("review.spending") to r.lastYearSpending),
        model.t("review.topCategories") to r.topCategories.map { it.name to it.thisYear },
        model.t("review.changes") to r.biggestChanges.map { c ->
            c.name to (if (c.change.isNegative) model.t("review.down", model.money(-c.change)) else model.t("review.up", model.money(c.change)))
        },
        model.t("review.largest") to r.largestPurchases.map { p -> "${model.date(p.date)} · ${p.payee.orEmpty()} · ${p.category}" to p.amount },
        model.t("review.payees") to r.frequentPayees.map { (name, visits, spent) -> name to model.t("review.visits", visits, model.money(spent)) },
        model.t("review.busiest") to listOfNotNull(r.busiestMonth),
    ).filter { it.second.isNotEmpty() }
    for ((name, lines) in sections) {
        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        for ((label, value) in lines) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(label, Modifier.weight(1f))
                Text(if (value is ca.schippers.hfm.money.Money) model.money(value) else value?.toString().orEmpty())
            }
        }
    }
    val rows = sections.flatMap { (name, lines) -> lines.map { (label, value) -> listOf(name, label, value) } }
    TableView(model, ReportTable(title, model.t("review.subtitle", model.books.reports.base.code), listOf(model.t("review.section"), model.t("review.item"), model.t("review.value")), rows))
}
