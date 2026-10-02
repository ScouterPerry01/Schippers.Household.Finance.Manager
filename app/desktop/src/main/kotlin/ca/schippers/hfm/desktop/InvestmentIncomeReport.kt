package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.EnteredSlip
import ca.schippers.hfm.books.PersonInvestmentIncome
import ca.schippers.hfm.calc.invest.SlipKind
import ca.schippers.hfm.calc.invest.TaxSlips
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat

/**
 * Section 12, investment income and capital gains (INV-08, PROV-07): per person and tax year, the
 * T5 and T3 slips (with RL-3 and RL-16 in Quebec), estimated from the books until entered, and the
 * capital gains for Schedule 3.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InvestmentIncomeReport(model: BooksModel, year: Int, memberId: String?) {
    val books = model.books
    val report = remember(model.revision, year) { books.taxSlips.report(year) }
    var entering by remember { mutableStateOf(false) }
    val people = report.people.filter { memberId == null || it.member?.id == memberId }

    Text(model.t("report.INVESTMENT_INCOME"), style = MaterialTheme.typography.titleLarge)
    Text(model.t("income.subtitle", year.toString()), style = MaterialTheme.typography.bodySmall)
    MissingRates(model, report.missingRates)
    Text(model.t("income.hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 4.dp))
    OutlinedButton(onClick = { entering = true }) { Text(model.t("income.enterSlip")) }
    if (people.isEmpty()) Text(model.t("income.none", year.toString()), Modifier.padding(vertical = 8.dp))
    for (p in people) PersonSection(model, p, year)
    // TAX-04: an organizational aid, not tax advice.
    Text(model.t("income.taxNotice"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 16.dp))
    if (entering) SlipDialog(model, year) { entering = false }
}

@Composable
private fun PersonSection(model: BooksModel, p: PersonInvestmentIncome, year: Int) {
    val name = p.member?.displayName ?: model.t("income.noOwner")
    Text("$name · ${model.t("province.${p.province.name}")}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
    for (kind in SlipKind.entries) {
        val lines = p.slips.filter { it.kind == kind }
        if (lines.isEmpty()) continue
        val codes = TaxSlips.boxCodes(kind).filter { c -> lines.any { it.boxes.containsKey(c) } }
        val totals = p.totals(kind)
        Text(model.t("slip.$kind"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
        TableView(
            model,
            ReportTable(
                "${model.t("slip.$kind")} · $name · $year", model.t("report.inCurrency", "CAD"),
                listOf(model.t("nav.accounts"), model.t("investments.security"), model.t("income.source")) + codes.map { model.t("income.box", it) },
                lines.map { l ->
                    listOf(l.account.name, l.security?.label.orEmpty(), model.t(if (l.entered != null) "income.entered" else "income.estimated") + if (l.shareOf > 1) " · 1/${l.shareOf}" else "") +
                        codes.map { l.boxes[it] }
                } + listOf(listOf<Any?>(model.t("report.total"), "", "") + codes.map { totals[it] }),
            ),
            startOpen = true,
        )
    }
    if (!p.cryptoIncome.isZero) {
        Text(model.t("income.crypto", model.money(p.cryptoIncome)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
    }
    if (p.gains.isNotEmpty()) {
        Text(model.t("income.gains", model.money(p.netGain!!), model.money(p.taxableGain!!)), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        TableView(
            model,
            ReportTable(
                model.t("investments.gainsOf", year.toString()) + " · " + name, model.t("report.inCurrency", model.books.rates.baseCurrency.code),
                listOf(model.t("report.date"), model.t("investments.security"), model.t("investments.proceeds"), model.t("investments.acb"), model.t("investments.gain"), ""),
                p.gains.map { g ->
                    val d = g.gain.disposition
                    listOf(d.date, g.gain.security.label + if (g.shareOf > 1) " · 1/${g.shareOf}" else "", g.proceeds, g.cost, g.amount, if (d.possibleSuperficialLoss) model.t("investments.superficial") else "")
                },
            ),
            startOpen = true,
        )
    }
}

/** Enter a slip's boxes for a whole account (and fund), prefilled with what the books estimate. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlipDialog(model: BooksModel, year: Int, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val accounts = remember { books.taxSlips.accounts() }
    var account by remember { mutableStateOf(accounts.firstOrNull()) }
    var kind by remember { mutableStateOf(SlipKind.T5) }
    val funds = remember(account) {
        val ids = account?.let { a -> books.investments.transactions(a.id).mapNotNull { it.securityId }.toSet() }.orEmpty()
        books.investments.securities(includeArchived = true).filter { it.id in ids }
    }
    var fund by remember(account) { mutableStateOf(funds.firstOrNull()) }
    val forFund = kind == SlipKind.T3 || kind == SlipKind.RL16
    val securityId = fund?.id.takeIf { forFund }
    val existing = remember(account, kind, securityId) { books.taxSlips.slips(year).firstOrNull { it.accountId == account?.id && it.kind == kind && it.securityId == securityId } }
    val fields = remember(account, kind, securityId) {
        val start = existing?.boxes ?: account?.let { books.taxSlips.estimate(it.id, year, kind, securityId) }.orEmpty()
        mutableStateMapOf<String, String>().apply {
            TaxSlips.boxCodes(kind).forEach { c -> put(c, start[c]?.let { MoneyFormat.formatAmount(Money.of(it, Currency.CAD), locale) }.orEmpty()) }
        }
    }
    fun parse(t: String) = t.trim().ifEmpty { null }?.let { runCatching { MoneyFormat.parseDecimal(it, locale) }.getOrNull() }
    val valid = fields.values.all { it.isBlank() || parse(it) != null }
    FormDialog(
        model.t("income.slipTitle", year.toString()), model.t("common.save"), model.t("common.cancel"),
        canSave = account != null && valid && (!forFund || fund != null),
        onDismiss = onClose,
        onSave = {
            val ok = model.act {
                val boxes = fields.mapNotNull { (k, v) -> parse(v)?.let { k to it } }.toMap()
                books.taxSlips.save(EnteredSlip(existing?.id.orEmpty(), account!!.id, year, kind, securityId, boxes))
            }
            if (ok != null) onClose()
        },
    ) {
        Column(Modifier.width(520.dp).heightIn(max = 600.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Picker(model.t("nav.accounts"), accounts, account, { it.name }) { account = it }
            Picker(model.t("income.slip"), SlipKind.entries, kind, { model.t("slip.$it") }) { kind = it }
            if (forFund) Picker(model.t("income.fund"), funds, fund, { "${it.label} · ${it.name}" }) { fund = it }
            Text(model.t(if (existing != null) "income.editing" else "income.prefilled"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (code in TaxSlips.boxCodes(kind)) {
                    TextInput(model.t("income.box", code), fields[code].orEmpty(), Modifier.width(150.dp), error = if (fields[code].orEmpty().isNotBlank() && parse(fields[code].orEmpty()) == null) model.t("error.invalidNumber") else null) { fields[code] = it }
                }
            }
            if (existing != null) {
                TextButton(onClick = { model.act { books.taxSlips.delete(existing.accountId, existing.id) }; onClose() }) { Text(model.t("income.removeSlip")) }
            }
        }
    }
}
