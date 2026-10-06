package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.BudgetPeriod
import ca.schippers.hfm.books.Energy
import ca.schippers.hfm.books.Vehicle
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.datetime.LocalDate

/**
 * TRP-08: the vehicle's next 3, 6 and 12 months: distance at the recent pace, fuel or energy by kind
 * of driving at recent prices, and the maintenance falling due; offered to the Transport budget.
 */
@Composable
internal fun ForecastTab(model: BooksModel, v: Vehicle) {
    val books = model.books
    val locale = model.language.locale
    val f = remember(model.revision, v.id) { books.vehicles.forecast(v.id, today()) }
    var budget by remember { mutableStateOf(false) }
    fun kmText(x: Int) = model.t("vehicles.km", String.format(locale, "%,d", x))
    val unit = model.t(if (f.energy == Energy.ELECTRICITY) "vehicles.kwh" else "vehicles.litres")
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(model.t("forecast.hint"), style = MaterialTheme.typography.bodySmall)
        if (f.kmPerDay == null) {
            Text(model.t("forecast.noPace"), fontWeight = FontWeight.Medium)
        } else {
            Text(model.t("forecast.pace", kmText(Math.round(f.kmPerDay!! * 30.44).toInt())), fontWeight = FontWeight.Medium)
        }
        val shares = f.shares.entries.joinToString(" · ") { (load, pct) -> model.t("forecast.share", model.t("vehicles.driving.$load"), pct) }
        if (shares.isNotEmpty()) Text(shares, style = MaterialTheme.typography.bodySmall)
        val consumption = f.consumption.entries.joinToString(" · ") { (load, x) ->
            model.t("trips.purposeKm", model.t("vehicles.driving.$load"), model.t(if (f.energy == Energy.ELECTRICITY) "vehicles.per100Kwh" else "vehicles.per100L", MoneyFormat.formatDecimal(x, locale)))
        }
        Text(consumption.ifEmpty { model.t("vehicles.consumptionUnknown") }, style = MaterialTheme.typography.bodySmall)
        Text(
            f.unitPrice?.let { model.t(if (f.energy == Energy.ELECTRICITY) "forecast.priceKwh" else "forecast.priceLitre", MoneyFormat.formatDecimal(it, locale)) } ?: model.t("forecast.noPrice"),
            style = MaterialTheme.typography.bodySmall,
        )
        // A plug-in hybrid's charging, beside its fuel.
        f.charging?.let { c ->
            val parts = listOfNotNull(
                c.per100km?.let { model.t("trips.purposeKm", model.t("energy.ELECTRICITY"), model.t("vehicles.per100Kwh", MoneyFormat.formatDecimal(it, locale))) },
                c.unitPrice?.let { model.t("forecast.priceKwh", MoneyFormat.formatDecimal(it, locale)) },
            )
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        TableView(
            model,
            ReportTable(
                model.t("forecast.title", v.name), model.t("report.inCurrency", f.currency.code),
                listOf(model.t("forecast.months"), model.t("forecast.distance"), model.t("forecast.quantity", unit), model.t("energy.${f.energy}")) +
                    (if (f.charging != null) listOf(model.t("forecast.quantity", model.t("vehicles.kwh")), model.t("energy.ELECTRICITY")) else emptyList()) +
                    listOf(model.t("forecast.maintenance"), model.t("report.total")),
                f.periods.map { p ->
                    listOf<Any?>(
                        model.t("forecast.nextMonths", p.months), kmText(p.distanceKm), p.quantity?.let { MoneyFormat.formatDecimal(it, locale) } ?: "—",
                        p.energyCost ?: "—",
                    ) + (if (f.charging != null) listOf(p.electricityKwh?.let { MoneyFormat.formatDecimal(it, locale) } ?: "—", p.electricityCost ?: "—") else emptyList()) +
                        listOf(p.maintenance, p.total)
                },
            ),
            startOpen = true,
        )
        val year = f.periods.maxByOrNull { it.months }
        if (year != null && year.tasks.isNotEmpty()) {
            Text(model.t("forecast.tasks", year.months), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            for ((task, times) in year.tasks) Text(model.t("forecast.taskTimes", task, times))
            if (year.unpriced.isNotEmpty()) Text(model.t("forecast.unpriced", year.unpriced.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
        }
        Text(model.t("forecast.how"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        if (books.role != Role.VIEWER) {
            OutlinedButton(onClick = { budget = true }, modifier = Modifier.padding(top = 8.dp)) { Text(model.t("forecast.toBudget")) }
        }
    }
    if (budget) ForecastBudgetDialog(model) { budget = false }
}

/**
 * TRP-08: the Transport budget suggested from every vehicle's next 12 months (vehicles of shared
 * groups only, as a budget is the whole household's); accepted as monthly budgets from this month.
 */
@Composable
private fun ForecastBudgetDialog(model: BooksModel, onClose: () -> Unit) {
    val books = model.books
    val lines = remember { books.vehicles.budgetLines(today()) }
    val names = remember { books.categories.list(includeArchived = true).associate { it.id to it.name(model.language) } }
    FormDialog(model.t("forecast.toBudget"), model.t("forecast.accept"), model.t("common.cancel"), canSave = lines.isNotEmpty(), onDismiss = onClose, onSave = {
        val now = today()
        val ok = model.act {
            for (l in lines) {
                val rollover = books.budgets.list().firstOrNull { it.categoryId == l.categoryId }?.rollover ?: false
                books.budgets.set(l.categoryId, BudgetPeriod.MONTHLY, l.monthly, rollover, LocalDate(now.year, now.month, 1))
            }
        }
        if (ok != null) onClose()
    }) {
        Text(model.t("forecast.budgetHint"), style = MaterialTheme.typography.bodySmall)
        if (lines.isEmpty()) Text(model.t("forecast.budgetNone"))
        for (l in lines) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(names[l.categoryId].orEmpty(), Modifier.width(260.dp))
                Text(model.t("forecast.perMonth", model.money(l.monthly)), Modifier.width(160.dp), fontWeight = FontWeight.Medium)
                Text(l.current?.let { model.t("forecast.currentBudget", model.money(it)) } ?: model.t("forecast.noBudget"), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
