package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.calc.invest.ForeignExchange
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/**
 * FX-05 and section 12, currency exposure: foreign currency held with its cost and value,
 * exchange gains and losses realized in the year, and each person's result after the $200
 * exemption.
 */
@Composable
internal fun FxReport(model: BooksModel, year: Int) {
    val books = model.books
    val base = books.reports.base
    val report = remember(model.revision, year) { books.fxGains.report(year, today()) }
    val members = remember(model.revision) { books.members.list(includeArchived = true).associate { it.id to it.displayName } }
    fun owners(ids: Set<String>) = ids.mapNotNull(members::get).sorted().joinToString(", ").ifEmpty { model.t("investments.household") }
    val asOf = minOf(LocalDate(year, 12, 31), today())

    Text(model.t("report.FX"), style = MaterialTheme.typography.titleLarge)
    Text(model.t("fx.subtitle", year.toString(), base.code), style = MaterialTheme.typography.bodySmall)
    MissingRates(model, report.missingRates)
    report.problems.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    Text(model.t("fx.hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 4.dp))
    if (report.holdings.isEmpty()) {
        Text(model.t("fx.none"), Modifier.padding(vertical = 8.dp))
        return
    }

    // Currency exposure: what each holding is worth in the base currency.
    Text(model.t("fx.holdings", model.date(asOf)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
    RankedBars(report.holdings.filter { it.value != null }.map { h -> RankedBar("${h.currency.code} · ${owners(h.ownerMemberIds)}", h.value!!.d(), model.money(h.value!!), note = model.money(h.balance)) })
    TableView(
        model,
        ReportTable(
            model.t("fx.holdings", model.date(asOf)), model.t("report.inCurrency", base.code),
            listOf(model.t("report.currency"), model.t("investments.owners"), model.t("fx.balance"), model.t("investments.acb"), model.t("investments.marketValue"), model.t("investments.unrealized")),
            report.holdings.map { h -> listOf(h.currency.code, owners(h.ownerMemberIds), h.balance, h.cost, h.value, h.unrealized) },
        ),
        startOpen = true,
    )

    Text(model.t("fx.realized", year.toString()), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
    for (p in report.people) {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            Stat(p.member?.displayName ?: model.t("investments.household"), model.money(p.net))
            Stat(model.t("fx.reportable"), model.money(p.reportable))
        }
    }
    if (report.disposals.isEmpty()) Text(model.t("fx.noneRealized", year.toString()), style = MaterialTheme.typography.bodySmall)
    TableView(
        model,
        ReportTable(
            model.t("fx.realized", year.toString()), model.t("report.inCurrency", base.code),
            listOf(model.t("report.date"), model.t("report.currency"), model.t("investments.owners"), model.t("fx.amount"), model.t("investments.proceeds"), model.t("investments.acb"), model.t("investments.gain")),
            report.disposals.map { x ->
                val d = x.disposition
                listOf(d.date, x.currency.code, owners(x.ownerMemberIds), quantityText(d.quantity, x.currency.minorUnits, model), d.proceeds, d.cost, d.gain)
            },
        ),
    )
    // The owner's decision: the exemption applied, with a note.
    Text(model.t("fx.exemptionNote", model.money(Money.of(ForeignExchange.exemption(year), base))), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
}

private fun quantityText(q: BigDecimal, scale: Int, model: BooksModel): String =
    java.text.NumberFormat.getNumberInstance(model.language.locale).apply { minimumFractionDigits = scale; maximumFractionDigits = scale }.format(q)
