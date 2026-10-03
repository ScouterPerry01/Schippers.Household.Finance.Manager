package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.RoomPlan
import ca.schippers.hfm.books.RoomSource
import ca.schippers.hfm.money.Money

/**
 * Section 12, registered plans (Must): contribution room and contributions per person (INV-09),
 * RRIF and LIF minimums (INV-10), RESP grants, the plans' values, pensions received, and the
 * warnings, for one year.
 */
@Composable
internal fun RegisteredPlansReport(model: BooksModel, year: Int, memberId: String?) {
    val books = model.books
    val today = today()
    fun wanted(id: String?) = memberId == null || id == memberId
    val room = remember(model.revision, year) {
        RoomPlan.entries.flatMap { plan -> books.plans.peopleWith(plan).mapNotNull { m -> runCatching { books.plans.room(m.id, plan, year, today) }.getOrNull() } }
    }.filter { wanted(it.member.id) }
    val withdrawals = remember(model.revision, year) { books.plans.withdrawals(year) }.filter { w -> memberId == null || memberId in w.account.ownerMemberIds }
    val resp = remember(model.revision, year) { books.plans.respBeneficiaries(year, today) }.filter { wanted(it.member.id) }
    val pensions = remember(model.revision, year) { books.plans.pensionSummaries(year) }.filter { wanted(it.pension.memberId) }
    val accounts = remember(model.revision) {
        books.plans.registeredAccounts().map { a -> a to runCatching { books.investments.holdings(a.id, today).totalValue }.getOrNull() }
    }.filter { (a, _) -> memberId == null || memberId in a.ownerMemberIds }
    val members = remember(model.revision) { books.members.list(includeArchived = true).associate { it.id to it.displayName } }
    val warnings = remember(model.revision) { books.plans.warnings(today) }

    Text(model.t("report.PLANS"), style = MaterialTheme.typography.titleLarge)
    Text(model.t("plansReport.subtitle", year.toString()), style = MaterialTheme.typography.bodySmall)
    for (w in warnings) Text(model.t(w.key, *w.args.map { if (it is Money) model.money(it) else it }.toTypedArray()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

    Section(model.t("plans.room"))
    TableView(
        model,
        ReportTable(
            model.t("plans.room") + " · $year", model.t("report.inCurrency", "CAD"),
            listOf(model.t("plans.person"), model.t("plans.plan"), model.t("plans.available"), model.t("plans.contributed"), model.t("plans.withdrawn"), model.t("plans.left"), model.t("plans.lifetimeLeft"), model.t("plansReport.source")),
            room.map { r ->
                listOf(
                    r.member.displayName, model.t("roomPlan.${r.plan}"), r.available, r.contributions, r.withdrawals.takeIf { r.plan == RoomPlan.TFSA }, r.left, r.lifetimeLeft,
                    if (r.over) model.t("plans.overBy") + " " + model.money(-r.left!!) else model.t(if (r.source == RoomSource.ENTERED) "plansReport.entered" else if (r.source == RoomSource.ESTIMATED) "plansReport.estimated" else "plansReport.unknown"),
                )
            },
        ),
        startOpen = true,
    )

    Section(model.t("plansReport.values", model.date(today)))
    TableView(
        model,
        ReportTable(
            model.t("plansReport.values", model.date(today)), "",
            listOf(model.t("nav.accounts"), model.t("account.type"), model.t("investments.owners"), model.t("plans.pensionValue")),
            accounts.map { (a, value) -> listOf(a.name, model.t("accountType.${a.type}"), a.ownerMemberIds.mapNotNull(members::get).joinToString(", "), value) },
        ),
        startOpen = true,
    )

    if (withdrawals.isNotEmpty()) {
        Section(model.t("plans.withdrawals"))
        TableView(
            model,
            ReportTable(
                model.t("plans.withdrawals") + " · $year", "",
                listOf(model.t("nav.accounts"), model.t("plansReport.january1"), model.t("plans.age"), model.t("plans.minimum"), model.t("plans.maximum"), model.t("plans.withdrawnYear"), model.t("plansReport.leftToWithdraw")),
                withdrawals.map { w -> listOf(w.account.name, w.valueJanuary1, w.age?.toString().orEmpty(), w.minimum, w.maximum, w.withdrawn, w.leftToWithdraw) },
            ),
            startOpen = true,
        )
    }

    if (resp.isNotEmpty()) {
        Section(model.t("plans.resp"))
        TableView(
            model,
            ReportTable(
                model.t("plans.resp") + " · $year", model.t("report.inCurrency", "CAD"),
                listOf(
                    model.t("plans.beneficiary"), model.t("plans.contributedTotal"), model.t("plans.contributedYear", year.toString()), model.t("plans.cesgExpected"), model.t("plans.cesgReceived"),
                    model.t("plans.provincialExpected"), model.t("plans.provincialReceived"), model.t("plans.respLifetimeLeft"),
                ),
                resp.map { r -> listOf(r.member.displayName, r.contributionsTotal, r.contributionsThisYear, r.cesgExpected, r.cesgReceived, r.provincialExpected, r.provincialReceived, r.lifetimeLeft) },
            ),
            startOpen = true,
        )
    }

    if (pensions.isNotEmpty()) {
        Section(model.t("plans.pensions"))
        TableView(
            model,
            ReportTable(
                model.t("plans.pensions") + " · $year", "",
                listOf(model.t("plans.person"), model.t("plans.plan"), model.t("plans.pensionKind"), model.t("plans.paymentsYear", year.toString())),
                pensions.map { s -> listOf(members[s.pension.memberId].orEmpty(), s.pension.name, model.t("pensionKind.${s.pension.kind}"), s.paymentsThisYear) },
            ),
            startOpen = true,
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
}
