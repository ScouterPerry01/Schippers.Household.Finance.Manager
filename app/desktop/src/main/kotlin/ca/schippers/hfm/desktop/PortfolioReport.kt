package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
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
internal fun PortfolioReport(model: BooksModel, filter: ReportFilter) {
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
