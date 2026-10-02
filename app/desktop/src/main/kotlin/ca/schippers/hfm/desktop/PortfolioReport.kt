package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ca.schippers.hfm.books.AllocationBy
import ca.schippers.hfm.books.AllocationReport
import ca.schippers.hfm.books.AllocationService
import ca.schippers.hfm.books.AllocationTarget
import ca.schippers.hfm.books.AssetClass
import ca.schippers.hfm.books.Region
import ca.schippers.hfm.books.TargetScope
import ca.schippers.hfm.money.MoneyFormat
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.PerformanceLine
import ca.schippers.hfm.books.ReportFilter
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.money.Money
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

/**
 * Section 12, investment portfolio (INV-06): value over time, returns for the period, the
 * breakdown from start to end value, returns per account and the holdings at the end.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PortfolioReport(model: BooksModel, filter: ReportFilter, groupId: String?) {
    val books = model.books
    val base = books.reports.base
    val accounts = remember(model.revision, filter.accountIds, filter.memberId) {
        books.investments.accounts(includeClosed = true)
            .filter { a -> filter.accountIds?.contains(a.id) ?: true }
            .filter { a -> filter.memberId?.let { it in a.ownerMemberIds } ?: true }
    }
    val ids = accounts.map { it.id }.toSet()
    val result = remember(model.revision, filter.from, filter.to, ids) { books.portfolio.performance(filter.from, filter.to, ids) }
    val dates = remember(filter) { books.reports.monthEnds(filter.from, filter.to) }
    val values = remember(model.revision, dates, ids) { books.reports.netWorth(dates, ids) }
    val holdings = remember(model.revision, filter.to, ids) { accounts.map { books.investments.holdings(it.id, filter.to) } }
    val locale = model.language.locale
    val total = result.total

    Text(model.t("report.PORTFOLIO"), style = MaterialTheme.typography.titleLarge)
    Text(subtitle(model, filter), style = MaterialTheme.typography.bodySmall)
    MissingRates(model, result.missingRates + values.missingRates)
    if (result.missingPrices.isNotEmpty()) {
        Text(model.t("portfolio.missingPrices", result.missingPrices.joinToString { it.label }), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    if (accounts.isEmpty()) {
        Text(model.t("portfolio.none"), Modifier.padding(vertical = 8.dp))
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Stat(model.t("portfolio.endValue"), model.money(total.endValue))
        Stat(model.t("portfolio.gain"), model.money(total.gain))
        Stat(model.t("portfolio.twr"), rate(model, total.timeWeighted, total.timeWeightedAnnual))
        Stat(model.t("portfolio.mwr"), rate(model, total.moneyWeighted, total.moneyWeightedAnnual))
    }
    Text(model.t("portfolio.hint", base.code), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

    val labels = dates.map { java.time.LocalDate.of(it.year, it.month.ordinal + 1, 1).format(DateTimeFormatter.ofPattern("MMM yy", locale)) }
    val points = values.value
    LineChart(labels, listOf(Series(model.t("portfolio.value"), points.map { it.net.d() }, points.map { model.money(it.net) })), model.axis())

    // From the start value to the end value, so the gain can be followed line by line.
    TableView(
        model,
        ReportTable(
            model.t("portfolio.breakdown"), subtitle(model, filter),
            listOf("", model.t("register.amount")),
            listOf(
                listOf(model.t("portfolio.startValue"), total.startValue),
                listOf(model.t("portfolio.contributions"), total.contributions),
                listOf(model.t("portfolio.withdrawals"), -total.withdrawals),
                listOf(model.t("portfolio.income"), total.income),
                listOf(model.t("portfolio.costs"), -total.costs),
                listOf(model.t("portfolio.marketChange"), total.marketChange),
                listOf(model.t("portfolio.endValue"), total.endValue),
            ),
        ),
        startOpen = true,
    )

    Text(model.t("portfolio.byAccount"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
    fun row(l: PerformanceLine) = listOf(
        l.account?.name ?: model.t("report.total"), l.startValue, l.contributions - l.withdrawals, l.income - l.costs, l.endValue, l.gain,
        rate(model, l.timeWeighted, l.timeWeightedAnnual), rate(model, l.moneyWeighted, l.moneyWeightedAnnual),
    )
    TableView(
        model,
        ReportTable(
            model.t("portfolio.byAccount"), subtitle(model, filter),
            listOf(
                model.t("nav.accounts"), model.t("portfolio.startValue"), model.t("portfolio.netPutIn"), model.t("portfolio.netIncome"), model.t("portfolio.endValue"),
                model.t("portfolio.gain"), model.t("portfolio.twr"), model.t("portfolio.mwr"),
            ),
            result.accounts.map(::row) + listOf(row(total)),
        ),
        startOpen = true,
    )

    AllocationSection(model, filter, ids, filter.memberId?.let { TargetScope.Person(it) } ?: groupId?.let { TargetScope.Group(it) } ?: TargetScope.Household)

    Text(model.t("portfolio.holdings", model.date(filter.to)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
    val rows = holdings.flatMap { h ->
        val a = h.account
        val positions = h.holdings.map { p ->
            listOf(a.name, p.security.label, p.quantity.stripTrailingZeros().toPlainString(), p.bookCost, p.marketValue, p.gain, percentOf(p.gain, p.bookCost, locale))
        }
        val cash = when {
            // A wallet holds its coins as its balance: shown with their value in the base currency.
            a.currency.isCrypto -> listOf(listOf(a.name, a.currency.code, h.cash.toBigDecimal().stripTrailingZeros().toPlainString(), null, books.rates.convert(h.cash, base, filter.to), null, ""))
            h.cash.isZero -> emptyList()
            else -> listOf(listOf(a.name, model.t("investments.cash"), "", null, h.cash, null, ""))
        }
        val metals = h.metals?.takeIf { a.type == AccountType.PRECIOUS_METALS }?.let { listOf(listOf(a.name, model.t("accountType.PRECIOUS_METALS"), "", null, it, null, "")) }.orEmpty()
        positions + metals + cash
    }
    TableView(
        model,
        ReportTable(
            model.t("portfolio.holdings", model.date(filter.to)), subtitle(model, filter),
            listOf(model.t("nav.accounts"), model.t("investments.security"), model.t("investments.quantity"), model.t("investments.bookCost"), model.t("investments.marketValue"), model.t("investments.gain"), "%"),
            rows,
        ),
        startOpen = true,
    )
}

/** A rate as a percentage: per year when money was invested a year or more, over that time otherwise. */
private fun rate(model: BooksModel, overPeriod: BigDecimal?, perYear: BigDecimal?): String {
    val format = java.text.NumberFormat.getNumberInstance(model.language.locale).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
    return when {
        perYear != null -> model.t("portfolio.perYear", format.format(perYear.movePointRight(2)))
        overPeriod != null -> format.format(overPeriod.movePointRight(2)) + " %"
        else -> "–"
    }
}

private fun percentOf(gain: Money?, cost: Money, locale: java.util.Locale): String {
    if (gain == null || cost.isZero) return ""
    val pct = gain.toBigDecimal().movePointRight(2).divide(cost.toBigDecimal(), 1, java.math.RoundingMode.HALF_UP)
    return java.text.NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }.format(pct) + " %"
}

/**
 * INV-07: the portfolio divided by class, region, currency or account against the target of the
 * household, the person or the group chosen in the filters, with the trades that bring it back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllocationSection(model: BooksModel, filter: ReportFilter, ids: Set<String>, scope: TargetScope) {
    val books = model.books
    val locale = model.language.locale
    var by by remember { mutableStateOf(AllocationBy.CLASS) }
    var editing by remember { mutableStateOf(false) }
    var newMoney by remember { mutableStateOf("") }
    var sell by remember { mutableStateOf(false) }
    val report = remember(model.revision, by, filter.to, ids, scope) { books.allocation.allocation(by, filter.to, ids, scope) }
    val names = remember(model.revision) { books.accounts.list(includeClosed = true).associate { it.account.id to it.account.name } }
    fun label(key: String) = when (by) {
        AllocationBy.CLASS -> if (key == AllocationService.CRYPTO) model.t("allocation.crypto") else model.t("assetClass.$key")
        AllocationBy.REGION -> model.t("region.$key")
        AllocationBy.CURRENCY -> key
        AllocationBy.ACCOUNT -> names[key] ?: key
    }
    val target = report.target
    val scopeName = when (scope) {
        TargetScope.Household -> model.t("allocation.scopeHousehold")
        is TargetScope.Person -> books.members.list(includeArchived = true).firstOrNull { it.id == scope.memberId }?.displayName.orEmpty()
        is TargetScope.Group -> books.groups().firstOrNull { it.id == scope.groupId }?.name.orEmpty()
    }

    Text(model.t("allocation.title", model.date(filter.to)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        Picker(model.t("allocation.by"), AllocationBy.entries, by, { model.t("allocation.by.$it") }, Modifier.width(220.dp)) { by = it }
        if (by != AllocationBy.ACCOUNT) {
            OutlinedButton(onClick = { editing = true }) { Text(model.t(if (target == null) "allocation.setTarget" else "allocation.editTarget", scopeName)) }
        }
    }
    if (report.unsplit.isNotEmpty()) {
        Text(model.t("allocation.unsplit", report.unsplit.joinToString { it.label }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    val pct = java.text.NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }
    fun p(v: BigDecimal) = pct.format(v) + " %"
    RankedBars(
        report.slices.map { s ->
            RankedBar(
                label(s.key), s.value.d(), model.money(s.value),
                note = listOfNotNull(p(s.percent), s.target?.let { model.t("allocation.targetOf", p(it)) }).joinToString(" · "),
            )
        },
    )
    if (target != null) {
        val off = report.offTarget
        Text(
            if (off.isEmpty()) model.t("allocation.onTarget", pct.format(target.tolerance)) else model.t("allocation.offTarget", off.joinToString { label(it.key) }, pct.format(target.tolerance)),
            style = MaterialTheme.typography.bodySmall,
            color = if (off.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error,
        )
    }
    TableView(
        model,
        ReportTable(
            model.t("allocation.title", model.date(filter.to)), subtitle(model, filter, model.t("allocation.by.$by")),
            listOfNotNull(model.t("allocation.part"), model.t("investments.marketValue"), "%", target?.let { model.t("allocation.target") }, target?.let { model.t("allocation.drift") }),
            report.slices.map { s -> listOfNotNull(label(s.key), s.value, p(s.percent), target?.let { s.target?.let(::p) }, target?.let { s.drift?.let(::p) }) } +
                listOf(listOfNotNull(model.t("report.total"), report.total, p(BigDecimal(100)), target?.let { "" }, target?.let { "" })),
        ),
    )

    // Rebalancing: new money first, or a full rebalance that also sells.
    if (target != null) {
        Text(model.t("allocation.rebalance"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextInput(model.t("allocation.newMoney", books.reports.base.code), newMoney, Modifier.width(220.dp)) { newMoney = it }
            LabeledCheckbox(model.t("allocation.allowSales"), sell) { sell = it }
        }
        val amount = runCatching { MoneyFormat.parseDecimal(newMoney.trim().ifEmpty { "0" }, locale) }.getOrNull()
        val trades = amount?.let { a -> runCatching { books.allocation.rebalance(report, Money.of(a, books.reports.base), sell) }.getOrNull() }
        when {
            trades == null -> Text(model.t("error.invalidNumber"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            trades.isEmpty() -> Text(model.t("allocation.nothingToDo"), style = MaterialTheme.typography.bodySmall)
            else -> TableView(
                model,
                ReportTable(
                    model.t("allocation.rebalance"), subtitle(model, filter, model.t("allocation.by.$by")),
                    listOf(model.t("allocation.part"), model.t("allocation.buy"), model.t("allocation.sell")),
                    trades.entries.sortedByDescending { it.value }.map { (k, m) -> listOf(label(k), m.takeIf { it.isPositive }, (-m).takeIf { m.isNegative }) },
                ),
                startOpen = true,
            )
        }
        Text(model.t("allocation.rebalanceHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
    if (editing) {
        TargetDialog(model, by, scope, scopeName, report, ::label) { editing = false }
    }
}

/** The target percentages for one way of dividing the portfolio; all fields empty removes the target. */
@Composable
private fun TargetDialog(model: BooksModel, by: AllocationBy, scope: TargetScope, scopeName: String, report: AllocationReport, label: (String) -> String, onClose: () -> Unit) {
    val locale = model.language.locale
    val existing = report.target
    val keys = when (by) {
        AllocationBy.CLASS -> AssetClass.entries.filter { it != AssetClass.BALANCED }.map { it.name } + AllocationService.CRYPTO
        AllocationBy.REGION -> Region.entries.map { it.name }
        else -> (report.slices.map { it.key } + existing?.weights?.keys.orEmpty()).distinct()
    }
    fun text(v: BigDecimal?) = v?.let { java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }.format(it) }.orEmpty()
    val fields = remember { mutableStateMapOf<String, String>().apply { keys.forEach { put(it, text(existing?.weights?.get(it))) } } }
    var tolerance by remember { mutableStateOf(text(existing?.tolerance ?: BigDecimal(5))) }
    fun parse(t: String) = t.trim().removeSuffix("%").trim().ifEmpty { null }?.let { runCatching { MoneyFormat.parseDecimal(it, locale) }.getOrNull() }
    val total = fields.values.mapNotNull(::parse).fold(BigDecimal.ZERO, BigDecimal::add)
    val empty = fields.values.all { it.isBlank() }
    FormDialog(
        model.t("allocation.targetFor", scopeName, model.t("allocation.by.$by").replaceFirstChar { it.lowercase(locale) }), model.t("common.save"), model.t("common.cancel"),
        canSave = empty || total.compareTo(BigDecimal(100)) == 0,
        onDismiss = onClose,
        onSave = {
            model.act {
                val target = if (empty) null else AllocationTarget(fields.mapNotNull { (k, v) -> parse(v)?.let { k to it } }.toMap(), parse(tolerance) ?: BigDecimal(5))
                model.books.allocation.setTarget(scope, by, target)
            }
            onClose()
        },
    ) {
        Column(Modifier.width(420.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (k in keys) TextInput("${label(k)} %", fields[k].orEmpty()) { fields[k] = it }
            Text(model.t("investments.mixTotal", text(total)), style = MaterialTheme.typography.bodySmall, color = if (empty || total.compareTo(BigDecimal(100)) == 0) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error)
            TextInput(model.t("allocation.tolerance"), tolerance, supporting = model.t("allocation.toleranceHint")) { tolerance = it }
            Text(model.t("allocation.targetHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
