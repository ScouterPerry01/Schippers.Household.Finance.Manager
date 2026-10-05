package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.MedExpense
import ca.schippers.hfm.books.MedicalService
import ca.schippers.hfm.books.Member
import ca.schippers.hfm.calc.medical.Medical
import ca.schippers.hfm.calc.medical.Window
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat
import ca.schippers.hfm.ocr.desktop.PdfPages
import java.io.File
import javax.swing.JFileChooser

/**
 * Section 12, medical expenses (MED-12, MED-14, MED-15): costs, reimbursements and out of pocket
 * per person for the year, the 12-month period ending in the year with the most eligible expenses
 * for the credit, adult dependants apart, and the receipts as one PDF.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MedicalReport(model: BooksModel, year: Int, memberId: String?) {
    val books = model.books
    val cad = Currency.CAD
    val zero = Money.zero(cad)
    val all = remember(model.revision) { books.medical.expenses() }
    val tax = remember(model.revision, year) { books.medical.taxReport(year) }
    val members = remember(model.revision) { books.members.list(includeArchived = true) }
    val inYear = all.filter { it.serviceDate.year == year && (memberId == null || it.memberId == memberId) }
    val byPerson = inYear.groupBy { it.memberId }
    fun name(id: String) = members.firstOrNull { it.id == id }?.displayName.orEmpty()
    fun sum(list: List<MedExpense>, f: (MedExpense) -> Money) = list.map(f).fold(zero, Money::plus)

    Text(model.t("report.MEDICAL"), style = MaterialTheme.typography.titleLarge)
    Text(model.t("medicalReport.subtitle", year.toString()), style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(32.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Stat(model.t("medicalReport.costs"), model.money(sum(inYear) { it.amount }))
        Stat(model.t("medicalReport.reimbursed"), model.money(sum(inYear) { it.reimbursed }))
        Stat(model.t("medicalReport.outOfPocket"), model.money(sum(inYear) { it.outOfPocket }))
    }
    val ids = byPerson.keys.sortedBy(::name)
    if (ids.isNotEmpty()) {
        GroupedBarChart(
            ids.map(::name),
            listOf(
                Series(model.t("medicalReport.reimbursed"), ids.map { sum(byPerson.getValue(it)) { e -> e.reimbursed }.d() }, ids.map { model.money(sum(byPerson.getValue(it)) { e -> e.reimbursed }) }),
                Series(model.t("medicalReport.outOfPocket"), ids.map { sum(byPerson.getValue(it)) { e -> e.outOfPocket }.d() }, ids.map { model.money(sum(byPerson.getValue(it)) { e -> e.outOfPocket }) }),
            ),
            model.axis(),
        )
    }
    TableView(
        model,
        ReportTable(
            model.t("report.MEDICAL") + " · $year", model.t("report.inCurrency", cad.code),
            listOf(model.t("medical.serviceDate"), model.t("medical.patient"), model.t("medical.service"), model.t("medical.description"), model.t("medicalReport.costs"), model.t("medicalReport.reimbursed"), model.t("medicalReport.outOfPocket")),
            inYear.sortedBy { it.serviceDate }.map { e -> listOf(e.serviceDate, name(e.memberId), model.t("medService.${e.service}"), e.description.orEmpty(), e.amount, e.reimbursed, e.outOfPocket) },
        ),
    )

    // The tax credit (MED-12, MED-14).
    Text(model.t("medicalReport.credit"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
    Text(model.t("medicalReport.creditHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    val family = tax.family
    if (family == null && tax.people.none { it.otherDependant }) Text(model.t("medicalReport.noneEligible", year.toString()), Modifier.padding(vertical = 6.dp))
    family?.let { w ->
        val familyIds = tax.people.filter { !it.otherDependant }.map { it.member.id }.toSet()
        Text(model.t("medicalReport.family", model.date(w.start), model.date(w.end), model.money(Money.of(w.total, cad))), modifier = Modifier.padding(vertical = 6.dp))
        BundleButton(model, model.t("medicalReport.bundleFamily"), w, familyIds, year)
        val adults = members.filter { it.kind == MemberKind.ADULT && !it.archived }
        if (memberId == null && adults.size >= 2) WhoClaims(model, year, w, adults)
    }
    TableView(
        model,
        ReportTable(
            model.t("medicalReport.credit") + " · $year", model.t("report.inCurrency", cad.code),
            listOf(model.t("medical.patient"), model.t("medicalReport.province"), model.t("medicalReport.bestPeriod"), model.t("medicalReport.bestTotal"), model.t("medicalReport.calendarTotal"), model.t("medicalReport.line")),
            tax.people.filter { memberId == null || it.member.id == memberId }.map { p ->
                listOf(
                    p.member.displayName, model.t("province.${p.province.name}"),
                    p.best?.let { "${model.date(it.start)} – ${model.date(it.end)}" }.orEmpty(), p.best?.let { Money.of(it.total, cad) }, Money.of(p.calendar.total, cad),
                    model.t(if (p.otherDependant) "medicalReport.lineDependant" else "medicalReport.lineFamily") + if (p.province.isQuebec) " · " + model.t("medicalReport.lineQuebec") else "",
                )
            },
        ),
        startOpen = true,
    )
    for (p in tax.people.filter { it.otherDependant && it.best != null }) {
        BundleButton(model, model.t("medicalReport.bundleFor", p.member.displayName), p.best!!, setOf(p.member.id), year)
    }
    // TAX-04: an organizational aid, not tax advice.
    Text(model.t("medicalReport.taxNotice"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
}

/** MED-15: saves the receipts of the expenses in [window] as one PDF, after a cover page listing them. */
@Composable
private fun BundleButton(model: BooksModel, label: String, window: Window, memberIds: Set<String>, year: Int) {
    OutlinedButton(onClick = {
        val chooser = JFileChooser().apply { dialogTitle = label; selectedFile = File(model.t("medicalReport.bundleFile", year.toString()) + ".pdf") }
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return@OutlinedButton
        model.act {
            val books = model.books
            val names = books.members.list(includeArchived = true).associate { it.id to it.displayName }
            val expenses = books.medical.expensesIn(window, memberIds)
            val docs = expenses.flatMap { e -> books.medical.documents(MedicalService.EXPENSE, e.id) }.distinctBy { it.id }
            val cover = expenses.map { e ->
                listOf(model.date(e.taxDate), names[e.memberId].orEmpty(), model.t("medService.${e.service}"), e.description.orEmpty(), model.money(e.outOfPocket)).joinToString("   ")
            } + "" + model.t("medicalReport.bundleTotal", model.money(Money.of(window.total, Currency.CAD)), docs.size)
            val pdf = PdfPages.bundle(model.t("medicalReport.bundleTitle", model.date(window.start), model.date(window.end)), cover, docs.map { it.mimeType to books.documents.content(it.id) }, model.t("documents.heicInBundle"))
            val file = chooser.selectedFile.let { if (it.extension.equals("pdf", true)) it else File(it.path + ".pdf") }
            file.writeBytes(pdf)
        }
    }, modifier = Modifier.padding(vertical = 4.dp)) { Text(label) }
}

/**
 * MED-13: which spouse's claim counts for more, from the net incomes the user enters (the books do
 * not hold them). Indicative only: the credit is not refundable, and the return decides.
 */
@Composable
private fun WhoClaims(model: BooksModel, year: Int, window: Window, adults: List<Member>) {
    val locale = model.language.locale
    val cad = Currency.CAD
    val incomes = remember(year) { mutableStateMapOf<String, String>() }
    var max by remember(year) { mutableStateOf(Medical.federalMaxReduction(year)?.let { MoneyFormat.formatAmount(Money.of(it.setScale(2), cad), locale) }.orEmpty()) }
    Text(model.t("medicalReport.whoClaims"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
    Text(model.t("medicalReport.whoClaimsHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (a in adults) {
            AmountInput(model.t("medicalReport.netIncome", a.displayName), incomes[a.id].orEmpty(), cad, locale, Modifier.width(220.dp), model::money) { incomes[a.id] = it }
        }
        AmountInput(model.t("medicalReport.maxReduction", year.toString()), max, cad, locale, Modifier.width(220.dp), model::money) { max = it }
    }
    val entered = adults.mapNotNull { a -> runCatching { parseAmount(incomes[a.id].orEmpty(), cad, locale) }.getOrNull()?.let { a to it.toBigDecimal() } }.toMap()
    if (entered.size < 2) return
    val cap = runCatching { parseAmount(max, cad, locale) }.getOrNull()?.toBigDecimal()
    val ranked = Medical.whoClaims(window.total, entered, cap)
    for ((a, amount) in ranked) Text(model.t("medicalReport.claimable", a.displayName, model.money(Money.of(amount, cad))))
    val (best, top) = ranked.first()
    val gap = top - ranked[1].second
    Text(
        when {
            top.signum() == 0 -> model.t("medicalReport.claimNone")
            gap.signum() == 0 -> model.t("medicalReport.claimEither")
            else -> model.t("medicalReport.claimBest", best.displayName, model.money(Money.of(gap, cad)))
        },
        fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp),
    )
    Text(model.t("medicalReport.claimNote"), style = MaterialTheme.typography.bodySmall)
    if (model.books.province.isQuebec) Text(model.t("medicalReport.claimQuebec"), style = MaterialTheme.typography.bodySmall)
}
