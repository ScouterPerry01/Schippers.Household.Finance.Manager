package ca.schippers.hfm.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.AccountAlert
import ca.schippers.hfm.books.BillKind
import ca.schippers.hfm.books.BudgetLine
import ca.schippers.hfm.books.CategoryAmount
import ca.schippers.hfm.books.CreditCardService
import ca.schippers.hfm.books.NetWorthPoint
import ca.schippers.hfm.books.Occurrence
import ca.schippers.hfm.books.OccurrenceStatus
import ca.schippers.hfm.books.LineStatus
import ca.schippers.hfm.books.ReportFilter
import ca.schippers.hfm.books.StatementStatus
import ca.schippers.hfm.calc.rules.Thresholds
import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.domain.CategoryKind
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import java.time.format.DateTimeFormatter

/** Section 12 dashboard: where the household stands today and what needs attention. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(model: BooksModel) {
    val books = model.books
    val today = today()
    val base = books.reports.base
    val monthStart = LocalDate(today.year, today.month, 1)
    val data = remember(model.revision) {
        val summaries = books.accounts.list()
        val missing = HashSet<Currency>()
        // ACC-01: what the accounts hold today; post-dated transactions count once their day comes.
        fun inBase(m: Money) = books.rates.convert(m, base, today) ?: Money.zero(base).also { missing += m.currency }
        fun total(kind: AccountKind) = summaries.filter { it.account.type.kind == kind }.fold(Money.zero(base)) { acc, s -> acc + inBase(s.balanceToday) }
        // CC-01: the credit still available on the cards that have a limit, and how much of those limits is used.
        val limited = summaries.filter { it.account.type.kind == AccountKind.CREDIT }
            .mapNotNull { s -> books.creditCards.terms(s.account.id)?.creditLimit?.takeIf { it.isPositive }?.let { limit -> inBase(limit) to inBase(-s.balanceToday) } }
        val netWorth = books.reports.netWorth(books.reports.monthEnds(monthStart.minus(DatePeriod(months = 11)), today))
        val upcoming = books.bills.occurrences(today.minus(DatePeriod(days = 365)), today.plus(DatePeriod(days = 7)))
            .filter { it.status == OccurrenceStatus.DUE && it.bill.kind != BillKind.INCOME }
        val budget = books.budgets.month(monthStart)
        val spending = books.reports.byCategory(ReportFilter(monthStart, today), CategoryKind.EXPENSE)
        missing += netWorth.missingRates + spending.missingRates
        val openStatements = summaries.flatMap { s -> books.statements.statements(s.account.id).filter { it.status == StatementStatus.OPEN } }
        val unresolved = openStatements.sumOf { st -> books.statements.view(st.id).lines.count { it.status == LineStatus.PROPOSED || it.status == LineStatus.UNMATCHED } }
        val last = books.statements.lastReconciled()
        DashboardData(
            netWorth = netWorth.value,
            cash = total(AccountKind.BANK),
            credit = -(total(AccountKind.CREDIT) + total(AccountKind.LOAN)),
            creditAvailable = limited.takeIf { it.isNotEmpty() }?.let { l -> l.fold(Money.zero(base)) { a, (limit, owed) -> a + limit - owed } },
            limitUsed = limited.takeIf { it.isNotEmpty() }?.let { l -> CreditCardService.limitUsedPercent(l.fold(Money.zero(base)) { a, p -> a + p.second }, l.fold(Money.zero(base)) { a, p -> a + p.first }) },
            bills = upcoming,
            overdue = upcoming.count { it.dueDate < today },
            budgetLines = budget.lines.filter { it.category.kind == CategoryKind.EXPENSE },
            spending = spending.value.take(5),
            unresolvedLines = unresolved,
            uncategorized = books.reports.uncategorizedCount(),
            behind = summaries.filter { s -> last[s.account.id]?.let { it.daysUntil(today) > Thresholds.reconcileBehind(today) } ?: false }.map { it.account.name },
            missingRates = missing.map { it.code }.sorted(),
            backupReminder = books.backups.needsReminder(java.time.Instant.now()),
            alerts = runCatching { books.accountAlerts.alerts(today) }.getOrDefault(emptyList()),
            unusualUse = runCatching { books.utilities.unusual(today) }.getOrDefault(emptyList()),
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(model.t("nav.dashboard"), style = MaterialTheme.typography.titleLarge)
        GettingStarted(model)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val net = data.netWorth.lastOrNull()
            val monthAgo = data.netWorth.getOrNull(data.netWorth.size - 2)
            Tile(model.t("report.netWorth"), net?.let { model.money(it.net) } ?: "—", monthAgo?.let { m -> net?.let { model.t("dashboard.sinceLastMonth", signed(model, it.net - m.net)) } }) { model.section = Section.REPORTS }
            Tile(model.t("dashboard.cash"), model.money(data.cash), null) { model.section = Section.ACCOUNTS }
            Tile(
                model.t("dashboard.credit"), model.money(data.credit),
                data.creditAvailable?.let { model.t("dashboard.creditAvailable", model.money(it), data.limitUsed ?: 0) },
                alert = data.creditAvailable?.isNegative == true,
            ) { model.section = Section.ACCOUNTS }
            val billsTotal = data.bills.fold(Money.zero(base)) { a, o -> a + (books.rates.convert(o.amount, base, today) ?: Money.zero(base)) }
            Tile(model.t("dashboard.bills"), model.money(billsTotal), model.t("dashboard.billsCount", data.bills.size)) { model.section = Section.BILLS }
            if (data.budgetLines.isNotEmpty()) {
                val budgeted = data.budgetLines.fold(Money.zero(base)) { a, l -> a + l.budgeted }
                val spent = data.budgetLines.fold(Money.zero(base)) { a, l -> a + l.actual }
                val over = data.budgetLines.count { it.isOver }
                val detail = listOfNotNull(model.t("dashboard.ofBudget", model.money(budgeted)), if (over > 0) model.t("dashboard.overBudget", over) else null).joinToString(" · ")
                Tile(model.t("dashboard.budget"), model.money(spent), detail, alert = over > 0) {
                    model.section = Section.BUDGETS
                }
            }
        }

        // Items needing review.
        val review = buildList {
            if (data.overdue > 0) add(model.t("dashboard.review.overdue", data.overdue) to Section.BILLS)
            if (data.unresolvedLines > 0) add(model.t("dashboard.review.statementLines", data.unresolvedLines) to Section.ACCOUNTS)
            if (data.uncategorized > 0) add(model.t("dashboard.review.uncategorized", data.uncategorized) to Section.ACCOUNTS)
            data.behind.forEach { add(model.t("dashboard.review.behind", it, Thresholds.reconcileBehind(today())) to Section.ACCOUNTS) }
            if (data.backupReminder) add(model.t("dashboard.review.backup") to Section.BACKUPS)
            if (data.missingRates.isNotEmpty()) add(model.t("report.missingRates", data.missingRates.joinToString()) to Section.RATES)
            // UTL-01: meters that used more than usual last month.
            data.unusualUse.forEach { add(model.describe(it) to Section.UTILITIES) }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(model.t("dashboard.review"), style = MaterialTheme.typography.titleMedium)
                if (review.isEmpty() && data.alerts.isEmpty()) Text(model.t("dashboard.review.none"), Modifier.padding(top = 6.dp))
                // ACC-06: each alert opens its account; unusual activity can be dismissed once looked at.
                for (a in data.alerts) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "• " + model.describe(a),
                            Modifier.clickable { model.selectedAccountId = a.account.id; model.section = Section.ACCOUNTS }.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                        if (a.dismissible) TextButton(onClick = { model.act { books.accountAlerts.dismiss(a.transactionId!!) } }) { Text(model.t("alert.dismiss")) }
                    }
                }
                for ((text, section) in review) {
                    Text("• $text", Modifier.clickable { model.section = section }.padding(vertical = 4.dp))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text(model.t("report.netWorth"), style = MaterialTheme.typography.titleMedium)
                    val fmt = DateTimeFormatter.ofPattern("MMM yy", model.language.locale)
                    LineChart(
                        data.netWorth.map { java.time.LocalDate.of(it.date.year, it.date.month.ordinal + 1, 1).format(fmt) },
                        listOf(Series(model.t("report.netWorth"), data.netWorth.map { it.net.toBigDecimal().toDouble() }, data.netWorth.map { model.money(it.net) })),
                        { compactNumber(it, model.language.locale) },
                        height = 200.dp,
                    )
                }
            }
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text(model.t("dashboard.topSpending"), style = MaterialTheme.typography.titleMedium)
                    if (data.spending.isEmpty()) Text(model.t("dashboard.noSpending"), Modifier.padding(top = 6.dp))
                    RankedBars(
                        data.spending.map { RankedBar(it.category?.name(model.language) ?: model.t("register.uncategorized"), it.amount.toBigDecimal().toDouble(), model.money(it.amount)) },
                        slot = 1,
                        onClick = { model.section = Section.REPORTS },
                    )
                }
            }
        }
    }
}

private class DashboardData(
    val netWorth: List<NetWorthPoint>,
    val cash: Money,
    val credit: Money,
    /** Null when no card has a limit. */
    val creditAvailable: Money?,
    val limitUsed: Int?,
    val bills: List<Occurrence>,
    val overdue: Int,
    val budgetLines: List<BudgetLine>,
    val spending: List<CategoryAmount>,
    val unresolvedLines: Int,
    val uncategorized: Long,
    val behind: List<String>,
    val missingRates: List<String>,
    val backupReminder: Boolean,
    /** ACC-06: the alerts standing on the accounts that ask for them. */
    val alerts: List<AccountAlert>,
    /** UTL-01: meters whose last month was unusual. */
    val unusualUse: List<ca.schippers.hfm.books.UnusualUse>,
)

private fun signed(model: BooksModel, m: Money) = (if (m.isPositive) "+" else "") + model.money(m)

@Composable
private fun Tile(label: String, value: String, detail: String?, alert: Boolean = false, onClick: () -> Unit) {
    Card(Modifier.width(250.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
        }
    }
}
