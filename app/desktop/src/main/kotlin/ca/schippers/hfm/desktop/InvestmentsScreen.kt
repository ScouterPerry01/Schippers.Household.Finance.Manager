package ca.schippers.hfm.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Account
import ca.schippers.hfm.books.AccountHoldings
import ca.schippers.hfm.books.AssetClass
import ca.schippers.hfm.books.IncomeType
import ca.schippers.hfm.books.InvestmentImportResult
import ca.schippers.hfm.books.InvestmentKind
import ca.schippers.hfm.books.InvestmentTxn
import ca.schippers.hfm.books.Region
import ca.schippers.hfm.books.Security
import ca.schippers.hfm.books.SecurityKind
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.importers.ImportedInvestmentStatement
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat
import ca.schippers.hfm.money.sum
import kotlinx.datetime.LocalDate
import java.io.File
import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** What the left column has selected: an account, the ACB and gains view, or the securities list. */
private sealed interface InvView {
    data class Of(val accountId: String) : InvView
    data object Gains : InvView
    data object Securities : InvView
}

private sealed interface InvAction {
    data class Edit(val txn: InvestmentTxn?) : InvAction
    data class EditSecurity(val security: Security?, val onSaved: (Security) -> Unit = {}) : InvAction
    data object Prices : InvAction
    data class Import(val file: File) : InvAction
    data class Statement(val statementId: String?) : InvAction
}

/** INV-01 to INV-05, REC-08: investment accounts, holdings, transactions, ACB and statements. */
@Composable
fun InvestmentsScreen(model: BooksModel) {
    val books = model.books
    // Non-registered accounts first, as listed.
    val all = remember(model.revision) { runCatching { books.investments.allHoldings(today()) }.getOrDefault(emptyList()).sortedBy { it.account.type.isRegistered } }
    // Wallets are shown at their value in the base currency, with the coins underneath (CR-05).
    val base = books.rates.baseCurrency
    fun shown(h: AccountHoldings): Money = if (h.account.currency.isCrypto) books.rates.convert(h.totalValue, base, today()) ?: h.totalValue else h.totalValue
    val kept = remember(model.revision) { books.brokerage.keptQuickenFiles() }
    var view by remember { mutableStateOf<InvView?>(model.selectedAccountId?.takeIf { id -> all.any { it.account.id == id } }?.let { InvView.Of(it) }) }
    val shown = view ?: all.firstOrNull()?.let { InvView.Of(it.account.id) } ?: InvView.Securities
    var action by remember { mutableStateOf<InvAction?>(null) }
    var keptResult by remember { mutableStateOf<InvestmentImportResult?>(null) }
    var exchangeFile by remember { mutableStateOf<File?>(null) }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(300.dp).fillMaxHeight().padding(12.dp)) {
            Text(model.t("nav.investments"), style = MaterialTheme.typography.titleLarge)
            LazyColumn(Modifier.weight(1f)) {
                if (all.isEmpty()) item { Text(model.t("investments.none"), Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall) }
                for ((registered, list) in all.groupBy { it.account.type.isRegistered }.toSortedMap()) {
                    item { Text(model.t(if (registered) "investments.registered" else "investments.nonRegistered"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                    items(list, key = { it.account.id }) { h ->
                        val selected = shown == InvView.Of(h.account.id)
                        Row(Modifier.fillMaxWidth().clickable { view = InvView.Of(h.account.id) }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(h.account.name, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                Text(
                                    if (h.account.currency.isCrypto) model.money(h.totalValue) else model.t("accountType.${h.account.type}"),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            MoneyText(model, shown(h), bold = selected)
                        }
                    }
                }
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    NavigationDrawerItem(label = { Text(model.t("investments.gains")) }, selected = shown == InvView.Gains, onClick = { view = InvView.Gains })
                    NavigationDrawerItem(label = { Text(model.t("investments.securities")) }, selected = shown == InvView.Securities, onClick = { view = InvView.Securities })
                    // CR-03: an exchange's history brings its wallets with it.
                    TextButton(onClick = { chooseExchangeFile(model)?.let { exchangeFile = it } }) { Text(model.t("wallet.importTitle")) }
                }
            }
            for (h in all.map(::shown).groupBy { it.currency }.toSortedMap(compareBy { it.code })) {
                Row(Modifier.padding(4.dp)) {
                    Text(model.t("accounts.total", h.key.code), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    MoneyText(model, h.value.sum(h.key), bold = true)
                }
            }
        }
        VerticalDivider()
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            // OTH-05: history kept by an earlier Quicken import can be read now.
            for (doc in kept) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(model.t("investments.keptQuicken", doc.label), Modifier.weight(1f))
                        Button(onClick = { keptResult = model.act { books.brokerage.importKeptQuickenFile(doc.id) } }) { Text(model.t("investments.importKept")) }
                    }
                }
            }
            when (val v = shown) {
                is InvView.Of -> all.firstOrNull { it.account.id == v.accountId }?.let { h ->
                    when (h.account.type) {
                        AccountType.CRYPTO_WALLET -> WalletView(model, h.account)
                        AccountType.PRECIOUS_METALS -> MetalsView(model, h.account)
                        else -> AccountView(model, h) { action = it }
                    }
                }
                InvView.Gains -> GainsView(model)
                InvView.Securities -> SecuritiesView(model) { action = it }
            }
        }
    }

    val account = (shown as? InvView.Of)?.let { v -> all.firstOrNull { it.account.id == v.accountId }?.account }
    when (val a = action) {
        is InvAction.Edit -> if (account != null) TransactionDialog(model, account, a.txn, onNewSecurity = { cb -> action = InvAction.EditSecurity(null) { s -> cb(s); action = InvAction.Edit(a.txn) } }) { action = null }
        is InvAction.EditSecurity -> SecurityDialog(model, a.security, account?.currency) { saved -> action = null; saved?.let(a.onSaved) }
        InvAction.Prices -> if (account != null) PricesDialog(model, account) { action = null }
        is InvAction.Import -> ImportDialog(model, a.file, account) { action = null }
        is InvAction.Statement -> if (account != null) StatementDialog(model, account, a.statementId) { action = null }
        null -> Unit
    }
    keptResult?.let { r -> ImportResultDialog(model, r) { keptResult = null } }
    exchangeFile?.let { f -> ExchangeImportDialog(model, f) { exchangeFile = null } }
}

// --- One account --------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountView(model: BooksModel, h: AccountHoldings, onAction: (InvAction) -> Unit) {
    val account = h.account
    var tab by remember(account.id) { mutableStateOf(0) }
    val zero = Money.zero(account.currency)
    val market = h.holdings.map { it.marketValue ?: it.bookCost }.fold(zero) { a, b -> a + b }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(account.name, style = MaterialTheme.typography.titleLarge)
            Text(model.t("accountType.${account.type}") + if (account.type.isRegistered) " · " + model.t("investments.noTaxPool") else "", style = MaterialTheme.typography.bodySmall)
        }
    }
    FlowRow(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Stat(model.t("investments.totalValue"), model.money(h.totalValue))
        Stat(model.t("investments.cash"), model.money(h.cash))
        Stat(model.t("investments.marketValue"), model.money(market))
        Stat(model.t("investments.bookCost"), model.money(h.bookCost))
        Stat(model.t("investments.unrealized"), model.money(market - h.bookCost))
    }
    if (h.missingPrices.isNotEmpty()) {
        Text(model.t("investments.missingPrices", h.missingPrices.joinToString { it.label }), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = { onAction(InvAction.Edit(null)) }) { Text(model.t("investments.add")) }
        OutlinedButton(onClick = { onAction(InvAction.Prices) }, enabled = h.holdings.isNotEmpty()) { Text(model.t("investments.updatePrices")) }
        OutlinedButton(onClick = { chooseBrokerageFile(model)?.let { onAction(InvAction.Import(it)) } }) { Text(model.t("investments.import")) }
        OutlinedButton(onClick = { onAction(InvAction.Statement(null)) }) { Text(model.t("investments.enterStatement")) }
    }
    PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 12.dp)) {
        Tab(tab == 0, { tab = 0 }, text = { Text(model.t("investments.holdings")) })
        Tab(tab == 1, { tab = 1 }, text = { Text(model.t("investments.transactions")) })
        Tab(tab == 2, { tab = 2 }, text = { Text(model.t("investments.statements")) })
    }
    when (tab) {
        0 -> HoldingsTable(model, h)
        1 -> TransactionsTable(model, account) { onAction(InvAction.Edit(it)) }
        else -> StatementsList(model, account) { onAction(InvAction.Statement(it)) }
    }
}

@Composable
private fun HoldingsTable(model: BooksModel, h: AccountHoldings) {
    val locale = model.language.locale
    val headers = listOf(model.t("investments.security"), model.t("investments.quantity"), model.t("investments.price"), model.t("investments.marketValue"), model.t("investments.bookCost"), model.t("investments.gain"))
    Column(Modifier.padding(top = 8.dp)) {
        Row { headers.forEachIndexed { i, t -> Text(t, Modifier.weight(if (i == 0) 2.5f else 1f).padding(horizontal = 6.dp), fontWeight = FontWeight.Bold, textAlign = if (i == 0) TextAlign.Start else TextAlign.End, style = MaterialTheme.typography.bodySmall) } }
        HorizontalDivider()
        if (h.holdings.isEmpty()) Text(model.t("investments.noHoldings"), Modifier.padding(8.dp))
        LazyColumn {
            items(h.holdings, key = { it.security.id }) { p ->
                val gain = p.gain
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(2.5f).padding(horizontal = 6.dp)) {
                        Text(p.security.label, fontWeight = FontWeight.Medium)
                        Text(p.security.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // M-35: a bond's or GIC's coupon and maturity.
                        val terms = listOfNotNull(
                            p.security.couponRate?.let { model.t("investments.couponOf", decimal(it.movePointRight(2), locale)) },
                            p.security.maturity?.let { model.t("investments.maturesOn", model.date(it)) },
                        )
                        if (terms.isNotEmpty()) Text(terms.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Cell(quantity(p.quantity, locale))
                    Cell(p.price?.let { decimal(it, locale) + " " + p.security.currency.code }.orEmpty() + (p.priceDate?.let { "\n" + model.date(it) } ?: model.t("investments.noPrice")))
                    Cell(p.marketValue?.let(model::money) ?: "–")
                    Cell(model.money(p.bookCost))
                    Text(
                        gain?.let { g -> model.money(g) + percentOf(g, p.bookCost, locale) } ?: "–", Modifier.weight(1f).padding(horizontal = 6.dp), textAlign = TextAlign.End,
                        color = if (gain?.isNegative == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(text: String) =
    Text(text, Modifier.weight(1f).padding(horizontal = 6.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)

private fun percentOf(gain: Money, cost: Money, locale: Locale): String {
    if (!cost.isPositive) return ""
    val pct = gain.toBigDecimal().divide(cost.toBigDecimal(), MathContext.DECIMAL64).movePointRight(2)
    return "\n" + String.format(locale, "%+.1f %%", pct.toDouble())
}

private fun quantity(q: BigDecimal, locale: Locale): String =
    java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 6 }.format(q)

/** Every digit kept, with the locale's decimal separator and no grouping, for fields the user edits. */
private fun editable(v: BigDecimal, locale: Locale): String =
    v.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }.toPlainString()
        .replace('.', java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator)

private fun decimal(v: BigDecimal, locale: Locale): String =
    java.text.NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 2; maximumFractionDigits = 6 }.format(v)

@Composable
private fun TransactionsTable(model: BooksModel, account: Account, onEdit: (InvestmentTxn) -> Unit) {
    val books = model.books
    val txns = remember(model.revision, account.id) { books.investments.transactions(account.id).asReversed() }
    val securities = remember(model.revision) { books.investments.securities(includeArchived = true).associateBy { it.id } }
    val locale = model.language.locale
    Column(Modifier.padding(top = 8.dp)) {
        if (txns.isEmpty()) Text(model.t("investments.noTransactions"), Modifier.padding(8.dp))
        LazyColumn {
            items(txns, key = { it.id }) { t ->
                Row(Modifier.fillMaxWidth().clickable { onEdit(t) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(model.date(t.date), Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall)
                    Text(kindLabel(model, t.kind, t.incomeType), Modifier.width(250.dp), style = MaterialTheme.typography.bodySmall)
                    Text(
                        listOfNotNull(t.securityId?.let { securities[it]?.label }, t.otherSecurityId?.let { "→ " + securities[it]?.label }).joinToString(" "),
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        listOfNotNull(t.quantity?.let { quantity(it, locale) }, t.price?.let { "@ " + decimal(it, locale) }, t.ratio?.let { "× " + quantity(it, locale) }).joinToString(" "),
                        Modifier.width(170.dp), style = MaterialTheme.typography.bodySmall,
                    )
                    // No cash moved (a notional distribution, units moved in): the amount, greyed.
                    if (t.cashEffect.isZero && !t.amount.isZero) {
                        Text(model.money(t.amount), Modifier.width(130.dp), textAlign = TextAlign.End, color = MaterialTheme.colorScheme.outline)
                    } else {
                        MoneyText(model, t.cashEffect, modifier = Modifier.width(130.dp), textAlign = TextAlign.End)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

private fun kindLabel(model: BooksModel, kind: InvestmentKind, income: IncomeType?): String = when (kind) {
    InvestmentKind.INCOME -> model.t("incomeType.${income ?: "OTHER"}")
    InvestmentKind.REINVEST -> model.t("investments.reinvested", model.t("incomeType.${income ?: "OTHER"}").lowercase(model.language.locale))
    else -> model.t("invKind.$kind")
}

@Composable
private fun StatementsList(model: BooksModel, account: Account, onOpen: (String) -> Unit) {
    val statements = remember(model.revision, account.id) { model.books.investments.statements(account.id) }
    Column(Modifier.padding(top = 8.dp)) {
        Text(model.t("investments.statementsHint"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
        if (statements.isEmpty()) Text(model.t("investments.noStatements"), Modifier.padding(8.dp))
        for (s in statements) {
            Row(Modifier.fillMaxWidth().clickable { onOpen(s.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(model.date(s.date), Modifier.width(110.dp))
                Text(s.cash?.let { model.t("investments.cashOf", model.money(it)) }.orEmpty(), Modifier.weight(1f))
                Text(model.t("investments.positionsCount", s.positions.size), Modifier.weight(1f))
                Text(
                    model.t(if (s.reconciled) "investments.reconciled" else "investments.toCheck"),
                    color = if (s.reconciled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            HorizontalDivider()
        }
    }
}

// --- Capital gains and ACB (INV-03) -----------------------------------------------------------------

@Composable
private fun GainsView(model: BooksModel) {
    val books = model.books
    val years = (today().year downTo today().year - 10).toList()
    var year by remember { mutableStateOf(today().year) }
    val report = remember(model.revision) { books.investments.acb(today()) }
    val members = remember { books.members.list(includeArchived = true).associate { it.id to it.displayName } }
    fun owners(ids: Set<String>) = ids.mapNotNull(members::get).sorted().joinToString(", ").ifEmpty { model.t("investments.household") }
    val base = books.rates.baseCurrency
    val gains = report.gains.filter { it.disposition.date.year == year }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(model.t("investments.gains"), style = MaterialTheme.typography.titleLarge)
        Text(model.t("investments.acbHint", base.code), style = MaterialTheme.typography.bodySmall)
        if (report.missingRates.isNotEmpty()) Text(model.t("report.missingRates", report.missingRates.joinToString { it.code }), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        report.problems.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.Bottom) {
            Picker(model.t("loans.year"), years, year, { it.toString() }, Modifier.width(140.dp)) { year = it }
            Stat(model.t("investments.netGain", year.toString()), model.money(gains.map { it.disposition.gain }.sum(base)))
            Stat(model.t("investments.taxable"), model.money(gains.map { it.disposition.gain }.sum(base).times(BigDecimal("0.5"))))
        }
        if (gains.any { it.disposition.possibleSuperficialLoss }) {
            Text(model.t("investments.superficialHint"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        TableView(
            model,
            ReportTable(
                model.t("investments.gainsOf", year.toString()), model.t("report.inCurrency", base.code),
                listOf(model.t("report.date"), model.t("investments.security"), model.t("investments.owners"), model.t("investments.quantity"), model.t("investments.proceeds"), model.t("investments.acb"), model.t("investments.gain"), ""),
                gains.map { g ->
                    val d = g.disposition
                    listOf(d.date, g.security.label, owners(g.ownerMemberIds), quantity(d.quantity, model.language.locale), d.proceeds, d.cost, d.gain, if (d.possibleSuperficialLoss) model.t("investments.superficial") else "")
                },
            ),
            startOpen = true,
        )
        Text(model.t("investments.pools"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        TableView(
            model,
            ReportTable(
                model.t("investments.pools"), model.date(today()),
                listOf(model.t("investments.security"), model.t("investments.owners"), model.t("investments.quantity"), model.t("investments.acb"), model.t("investments.acbPerUnit")),
                report.pools.map { p -> listOf(p.security.label + " · " + p.security.name, owners(p.ownerMemberIds), quantity(p.quantity, model.language.locale), p.acb, p.perUnit?.let { decimal(it, model.language.locale) }.orEmpty()) },
            ),
            startOpen = true,
        )
        // PROV-06: the Quebec return has its own schedule as well.
        Text(model.t(if (books.province.isQuebec) "investments.taxNotice.QC" else "investments.taxNotice"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
    }
}

// --- Securities (INV-01) ------------------------------------------------------------------------------

@Composable
private fun SecuritiesView(model: BooksModel, onAction: (InvAction) -> Unit) {
    val securities = remember(model.revision) { model.books.investments.securities(includeArchived = true) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(model.t("investments.securities"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Button(onClick = { onAction(InvAction.EditSecurity(null)) }) { Text(model.t("investments.addSecurity")) }
    }
    Text(model.t("investments.securitiesHint"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
    LazyColumn {
        if (securities.isEmpty()) item { Text(model.t("investments.noSecurities"), Modifier.padding(8.dp)) }
        items(securities, key = { it.id }) { s ->
            val price = remember(model.revision, s.id) { model.books.investments.price(s.id, today()) }
            Row(Modifier.fillMaxWidth().clickable { onAction(InvAction.EditSecurity(s)) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.label, Modifier.width(110.dp), fontWeight = FontWeight.Medium)
                Text(s.name + if (s.archived) " (${model.t("investments.archived")})" else "", Modifier.weight(1f))
                Text(model.t("securityKind.${s.kind}"), Modifier.width(150.dp), style = MaterialTheme.typography.bodySmall)
                Text(model.t("assetClass.${s.assetClass}"), Modifier.width(150.dp), style = MaterialTheme.typography.bodySmall)
                Text(price?.let { (d, p) -> "${decimal(p, model.language.locale)} ${s.currency.code} · ${model.date(d)}" } ?: model.t("investments.noPrice"), Modifier.width(220.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
        }
    }
}

// --- Dialogs --------------------------------------------------------------------------------------------

private fun parseDecimal(text: String, locale: Locale): BigDecimal? = text.trim().ifEmpty { null }?.let {
    runCatching { MoneyFormat.parseDecimal(it, locale) }.getOrElse { throw ValidationException("error.invalidNumber") }
}

private fun dateOf(text: String): LocalDate = runCatching { LocalDate.parse(text.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }

/** Which fields a kind of transaction uses. */
private val WITH_QUANTITY = setOf(InvestmentKind.BUY, InvestmentKind.SELL, InvestmentKind.REINVEST, InvestmentKind.TRANSFER_IN, InvestmentKind.TRANSFER_OUT, InvestmentKind.MERGER)
private val WITH_PRICE = setOf(InvestmentKind.BUY, InvestmentKind.SELL, InvestmentKind.REINVEST)
private val WITH_AMOUNT = InvestmentKind.entries.toSet() - setOf(InvestmentKind.SPLIT, InvestmentKind.MERGER, InvestmentKind.TRANSFER_OUT)
private val WITH_FEES = setOf(InvestmentKind.BUY, InvestmentKind.SELL, InvestmentKind.REINVEST)

/** INV-02: adds or changes one investment transaction. */
@Composable
private fun TransactionDialog(model: BooksModel, account: Account, existing: InvestmentTxn?, onNewSecurity: ((Security) -> Unit) -> Unit, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val c = account.currency
    val securities = remember(model.revision) { books.investments.securities() }
    fun amt(m: Money?) = m?.takeIf { !it.isZero }?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()
    fun dec(v: BigDecimal?) = v?.let { editable(it, locale) }.orEmpty()
    var kind by remember { mutableStateOf(existing?.kind ?: InvestmentKind.BUY) }
    var incomeType by remember { mutableStateOf(existing?.incomeType ?: IncomeType.DIVIDEND) }
    var securityId by remember { mutableStateOf(existing?.securityId) }
    var otherId by remember { mutableStateOf(existing?.otherSecurityId) }
    var date by remember { mutableStateOf((existing?.date ?: today()).toString()) }
    var qty by remember { mutableStateOf(dec(existing?.quantity)) }
    var price by remember { mutableStateOf(dec(existing?.price)) }
    var amount by remember { mutableStateOf(amt(existing?.amount)) }
    var amountTouched by remember { mutableStateOf(existing != null) }
    var fees by remember { mutableStateOf(amt(existing?.fees)) }
    var withheld by remember { mutableStateOf(amt(existing?.withheld)) }
    var ratio by remember { mutableStateOf(dec(existing?.ratio)) }
    var memo by remember { mutableStateOf(existing?.memo.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val security = securities.firstOrNull { it.id == securityId }

    // Quantity times price, as a starting amount the user can still change.
    if (!amountTouched && kind in WITH_PRICE) {
        val q = runCatching { parseDecimal(qty, locale) }.getOrNull()
        val p = runCatching { parseDecimal(price, locale) }.getOrNull()
        if (q != null && p != null) {
            val value = Money.of(q.multiply(p).multiply(security?.multiplier ?: BigDecimal.ONE), c)
            val text = MoneyFormat.formatAmount(value, locale)
            if (text != amount) amount = text
        }
    }

    FormDialog(
        model.t(if (existing == null) "investments.add" else "investments.edit") + " · " + account.name, model.t("common.save"), model.t("common.cancel"),
        onDismiss = onClose,
        onSave = {
            // M-26: a change to a transaction whose cash lines are reconciled is asked first, as in the register.
            fun build(): InvestmentTxn {
                val zero = Money.zero(c)
                return InvestmentTxn(
                    existing?.id.orEmpty(), account.id, dateOf(date), kind, securityId.takeIf { kind != InvestmentKind.FEE || it != null },
                    if (kind in WITH_QUANTITY) parseDecimal(qty, locale) else null, if (kind in WITH_PRICE) parseDecimal(price, locale) else null,
                    if (kind in WITH_AMOUNT) parseAmount(amount, c, locale) ?: zero else zero,
                    if (kind in WITH_FEES) parseAmount(fees, c, locale) ?: zero else zero,
                    if (kind == InvestmentKind.INCOME) parseAmount(withheld, c, locale) ?: zero else zero,
                    if (kind in setOf(InvestmentKind.INCOME, InvestmentKind.REINVEST)) incomeType else null,
                    if (kind in setOf(InvestmentKind.SPLIT, InvestmentKind.MERGER)) parseDecimal(ratio, locale) else null,
                    if (kind == InvestmentKind.MERGER) otherId else null, existing?.externalId, memo,
                )
            }
            val saved = model.act(retryConfirmed = { books.investments.save(build(), confirmReconciled = true).also { onClose() } }) {
                books.investments.save(build())
            }
            if (saved != null) onClose()
        },
    ) {
        Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("investments.kind"), InvestmentKind.entries, kind, { model.t("invKind.$it") }, Modifier.weight(1f)) { kind = it }
                DateInput(model.t("report.date"), date, Modifier.weight(1f)) { date = it }
            }
            Text(model.t("invKind.$kind.hint"), style = MaterialTheme.typography.bodySmall)
            if (kind in setOf(InvestmentKind.INCOME, InvestmentKind.REINVEST)) {
                Picker(model.t("investments.incomeType"), IncomeType.entries, incomeType, { model.t("incomeType.$it") }) { incomeType = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val optional = kind in setOf(InvestmentKind.INCOME, InvestmentKind.FEE)
                Picker(
                    model.t(if (kind == InvestmentKind.MERGER) "investments.oldSecurity" else "investments.security"),
                    (if (optional) listOf<Security?>(null) else emptyList()) + securities, security, { it?.let { s -> "${s.label} · ${s.name}" } ?: model.t("common.none") }, Modifier.weight(1f),
                ) { securityId = it?.id }
                TextButton(onClick = { onNewSecurity { securityId = it.id } }) { Text(model.t("investments.newSecurity")) }
            }
            if (kind == InvestmentKind.MERGER) {
                Picker(model.t("investments.newSecurityReceived"), securities, securities.firstOrNull { it.id == otherId }, { "${it.label} · ${it.name}" }) { otherId = it.id }
            }
            if (kind in WITH_QUANTITY || kind in WITH_PRICE) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (kind in WITH_QUANTITY) TextInput(model.t(if (kind == InvestmentKind.MERGER) "investments.oldUnits" else "investments.quantity"), qty, Modifier.weight(1f)) { qty = it }
                    if (kind in WITH_PRICE) TextInput(model.t("investments.priceIn", security?.currency?.code ?: c.code), price, Modifier.weight(1f)) { price = it }
                }
            }
            if (kind in setOf(InvestmentKind.SPLIT, InvestmentKind.MERGER)) {
                TextInput(model.t("investments.ratio"), ratio, supporting = model.t("investments.ratioHint")) { ratio = it }
            }
            if (kind in WITH_AMOUNT || kind in WITH_FEES) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (kind in WITH_AMOUNT) AmountInput(model.t("investments.amount.$kind"), amount, c, locale, Modifier.weight(1f), model::money) { amount = it; amountTouched = true }
                    if (kind in WITH_FEES) AmountInput(model.t("investments.fees"), fees, c, locale, Modifier.weight(1f), model::money) { fees = it }
                    if (kind == InvestmentKind.INCOME) AmountInput(model.t("investments.withheld"), withheld, c, locale, Modifier.weight(1f), model::money) { withheld = it }
                }
            }
            TextInput(model.t("register.memo"), memo) { memo = it }
            if (existing != null) TextButton(onClick = { confirmDelete = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmDelete && existing != null) {
        FormDialog(model.t("investments.delete.title"), model.t("common.delete"), model.t("common.cancel"), onDismiss = { confirmDelete = false }, onSave = {
            val closeAll = { confirmDelete = false; onClose() }
            if (model.act(retryConfirmed = { books.investments.delete(account.id, existing.id, confirmReconciled = true); closeAll() }) {
                    books.investments.delete(account.id, existing.id)
                } != null
            ) closeAll()
        }) { Text(model.t("investments.delete.body")) }
    }
}

/** INV-01: adds or changes a security. Returns the saved security, or null when cancelled. */
@Composable
private fun SecurityDialog(model: BooksModel, existing: Security?, defaultCurrency: Currency?, onClose: (Security?) -> Unit) {
    val locale = model.language.locale
    var symbol by remember { mutableStateOf(existing?.symbol.orEmpty()) }
    var exchange by remember { mutableStateOf(existing?.exchange.orEmpty()) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var kind by remember { mutableStateOf(existing?.kind ?: SecurityKind.ETF) }
    var currency by remember { mutableStateOf(existing?.currency?.code ?: defaultCurrency?.code ?: model.books.rates.baseCurrency.code) }
    var assetClass by remember { mutableStateOf(existing?.assetClass ?: AssetClass.EQUITY) }
    var region by remember { mutableStateOf(existing?.region ?: Region.CANADA) }
    // INV-07: how a balanced or global fund divides, in percent.
    val classMix = remember { mutableStateMapOf<AssetClass, String>().apply { MIX_CLASSES.forEach { c -> put(c, existing?.classMix?.get(c)?.let { decimal(it, locale) }.orEmpty()) } } }
    val regionMix = remember { mutableStateMapOf<Region, String>().apply { MIX_REGIONS.forEach { r -> put(r, existing?.regionMix?.get(r)?.let { decimal(it, locale) }.orEmpty()) } } }
    var multiplier by remember { mutableStateOf(existing?.multiplier?.let { editable(it, locale) } ?: "1") }
    var maturity by remember { mutableStateOf(existing?.maturity?.toString().orEmpty()) }
    var coupon by remember { mutableStateOf(existing?.couponRate?.movePointRight(2)?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var archived by remember { mutableStateOf(existing?.archived ?: false) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var price by remember { mutableStateOf("") }
    var priceDate by remember { mutableStateOf(today().toString()) }
    val history = remember(model.revision) { existing?.let { model.books.investments.prices(it.id).take(8) }.orEmpty() }
    val cur = runCatching { Currency.of(currency.trim().uppercase()) }.getOrNull()

    FormDialog(
        model.t(if (existing == null) "investments.addSecurity" else "investments.editSecurity"), model.t("common.save"), model.t("common.cancel"),
        canSave = name.isNotBlank() && cur != null, onDismiss = { onClose(null) },
        onSave = {
            val saved = model.act {
                val s = model.books.investments.saveSecurity(
                    Security(
                        existing?.id.orEmpty(), symbol, exchange, name, kind, cur!!, assetClass, region,
                        parseDecimal(multiplier, locale) ?: BigDecimal.ONE, maturity.trim().ifEmpty { null }?.let(::dateOf),
                        parseDecimal(coupon, locale)?.movePointLeft(2), notes, archived,
                        mixOf(classMix, locale).takeIf { assetClass == AssetClass.BALANCED },
                        mixOf(regionMix, locale).takeIf { region == Region.GLOBAL },
                    ),
                )
                parseDecimal(price, locale)?.let { model.books.investments.setPrice(s.id, dateOf(priceDate), it) }
                s
            }
            if (saved != null) onClose(saved)
        },
    ) {
        Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("investments.symbol"), symbol, Modifier.weight(1f)) { symbol = it }
                TextInput(model.t("investments.exchange"), exchange, Modifier.weight(1f), supporting = model.t("investments.exchangeHint")) { exchange = it }
            }
            TextInput(model.t("investments.name"), name) { name = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("investments.securityKind"), SecurityKind.entries, kind, { model.t("securityKind.$it") }, Modifier.weight(1f)) { k ->
                    kind = k
                    multiplier = when (k) { SecurityKind.OPTION -> "100"; SecurityKind.BOND -> editable(BigDecimal("0.01"), locale); else -> "1" }
                    if (k in setOf(SecurityKind.BOND, SecurityKind.GIC)) assetClass = AssetClass.FIXED_INCOME
                }
                TextInput(model.t("account.currency"), currency, Modifier.weight(1f), error = if (cur == null) model.t("error.unknownCurrency") else null) { currency = it.uppercase() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("investments.assetClass"), AssetClass.entries, assetClass, { model.t("assetClass.$it") }, Modifier.weight(1f)) { assetClass = it }
                Picker(model.t("investments.region"), Region.entries, region, { model.t("region.$it") }, Modifier.weight(1f)) { region = it }
            }
            if (assetClass == AssetClass.BALANCED) MixInputs(model, model.t("investments.classMix"), MIX_CLASSES, classMix) { model.t("assetClass.$it") }
            if (region == Region.GLOBAL) MixInputs(model, model.t("investments.regionMix"), MIX_REGIONS, regionMix) { model.t("region.$it") }
            TextInput(model.t("investments.multiplier"), multiplier, supporting = model.t("investments.multiplierHint")) { multiplier = it }
            if (kind in setOf(SecurityKind.BOND, SecurityKind.GIC)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateInput(model.t("investments.maturity"), maturity, Modifier.weight(1f)) { maturity = it }
                    TextInput(model.t("investments.coupon"), coupon, Modifier.weight(1f)) { coupon = it }
                }
            }
            Text(model.t("investments.priceHint"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("investments.priceIn", cur?.code ?: currency), price, Modifier.weight(1f)) { price = it }
                DateInput(model.t("report.date"), priceDate, Modifier.weight(1f)) { priceDate = it }
            }
            for ((d, p) in history) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${model.date(d)}   ${decimal(p, locale)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { model.act { model.books.investments.deletePrice(existing!!.id, d) } }) { Text(model.t("common.delete")) }
                }
            }
            TextInput(model.t("account.notes"), notes, singleLine = false) { notes = it }
            if (existing != null) LabeledCheckbox(model.t("investments.archive"), archived) { archived = it }
        }
    }
}

private val MIX_CLASSES = listOf(AssetClass.EQUITY, AssetClass.FIXED_INCOME, AssetClass.CASH)
private val MIX_REGIONS = listOf(Region.CANADA, Region.US, Region.INTERNATIONAL, Region.EMERGING)

/** The percentages entered, or null when none were (the fund then counts whole in its class or region). */
private fun <K> mixOf(fields: Map<K, String>, locale: Locale): Map<K, BigDecimal>? =
    fields.mapNotNull { (k, v) -> parseDecimal(v, locale)?.let { k to it } }.toMap().takeIf { it.isNotEmpty() }

/** A row of percentage fields for a fund's mix, with their total. */
@Composable
private fun <K> MixInputs(model: BooksModel, title: String, keys: List<K>, fields: MutableMap<K, String>, label: (K) -> String) {
    val locale = model.language.locale
    val total = fields.values.mapNotNull { parseDecimal(it, locale) }.fold(BigDecimal.ZERO, BigDecimal::add)
    val entered = fields.values.any { it.isNotBlank() }
    Text(title, style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (k in keys) TextInput("${label(k)} %", fields[k].orEmpty(), Modifier.weight(1f)) { fields[k] = it }
    }
    Text(
        if (entered) model.t("investments.mixTotal", decimal(total, locale)) else model.t("investments.mixHint"),
        style = MaterialTheme.typography.bodySmall,
        color = if (entered && total.compareTo(BigDecimal(100)) != 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
    )
}

/** INV-04: today's price for every holding of the account, in one go. */
@Composable
private fun PricesDialog(model: BooksModel, account: Account, onClose: () -> Unit) {
    val locale = model.language.locale
    val holdings = remember { model.books.investments.holdings(account.id, today()).holdings }
    var date by remember { mutableStateOf(today().toString()) }
    val prices = remember { mutableStateMapOf<String, String>().apply { holdings.forEach { put(it.security.id, it.price?.let { p -> editable(p, locale) }.orEmpty()) } } }
    FormDialog(model.t("investments.updatePrices") + " · " + account.name, model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            val d = dateOf(date)
            for (h in holdings) parseDecimal(prices[h.security.id].orEmpty(), locale)?.let { p -> if (p.compareTo(h.price ?: BigDecimal.ZERO) != 0 || h.priceDate != d) model.books.investments.setPrice(h.security.id, d, p) }
        }
        if (ok != null) onClose()
    }) {
        Text(model.t("investments.pricesHint"), style = MaterialTheme.typography.bodySmall)
        DateInput(model.t("report.date"), date, Modifier.fillMaxWidth()) { date = it }
        for (h in holdings) {
            TextInput(
                "${h.security.label} (${h.security.currency.code})", prices[h.security.id].orEmpty(),
                supporting = h.priceDate?.let { model.t("investments.lastPrice", model.date(it)) },
            ) { prices[h.security.id] = it }
        }
    }
}

/** REC-08: a statement's cash and holdings against the books; new statements start from the books' figures. */
@Composable
private fun StatementDialog(model: BooksModel, account: Account, statementId: String?, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    var id by remember { mutableStateOf(statementId) }
    val check = remember(model.revision, id) { id?.let { books.investments.check(account.id, it) } }
    if (check == null) {
        var date by remember { mutableStateOf(today().toString()) }
        val held = remember(date) { runCatching { books.investments.holdings(account.id, dateOf(date)) }.getOrNull() }
        // M-33: the books' cash on the statement date, until the user types the statement's own.
        var typedCash by remember { mutableStateOf<String?>(null) }
        val cash = typedCash ?: held?.let { MoneyFormat.formatAmount(it.cash, locale) }.orEmpty()
        val quantities = remember { mutableStateMapOf<String, String>() }
        FormDialog(model.t("investments.enterStatement") + " · " + account.name, model.t("investments.compare"), model.t("common.cancel"), onDismiss = onClose, onSave = {
            val saved = model.act {
                books.investments.saveStatement(
                    account.id, dateOf(date), parseAmount(cash, account.currency, locale),
                    held?.holdings.orEmpty().associate { h -> h.security.id to (parseDecimal(quantities[h.security.id] ?: editable(h.quantity, locale), locale) ?: BigDecimal.ZERO) },
                    "MANUAL",
                )
            }
            if (saved != null) id = saved.id
        }) {
            Text(model.t("investments.statementEntryHint"), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(model.t("investments.statementDate"), date, Modifier.weight(1f)) { date = it }
                AmountInput(model.t("investments.cash"), cash, account.currency, locale, Modifier.weight(1f), model::money) { typedCash = it }
            }
            for (h in held?.holdings.orEmpty()) {
                TextInput(h.security.label + " · " + h.security.name, quantities[h.security.id] ?: editable(h.quantity, locale)) { quantities[h.security.id] = it }
            }
        }
        return
    }
    WideDialog(model.t("investments.statementOf", model.date(check.statement.date)), model.t("common.close"), onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text("", Modifier.weight(2f))
                Text(model.t("investments.onStatement"), Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                Text(model.t("investments.inBooks"), Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            }
            HorizontalDivider()
            check.statement.cash?.let { cash ->
                CompareRow(model.t("investments.cash"), model.money(cash), model.money(check.booksCash), check.cashMatches)
            }
            for (p in check.positions) CompareRow("${p.security.label} · ${p.security.name}", quantity(p.statement, locale), quantity(p.books, locale), p.matches)
            HorizontalDivider()
            Text(
                model.t(if (check.matches) "investments.allMatch" else "investments.differences"),
                color = if (check.matches) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!check.statement.reconciled) {
                    Button(onClick = { if (model.act { books.investments.reconcile(account.id, check.statement.id) } != null) onClose() }, enabled = check.matches) { Text(model.t("investments.markReconciled")) }
                }
                TextButton(onClick = { if (model.act { books.investments.deleteStatement(account.id, check.statement.id) } != null) onClose() }) {
                    Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun CompareRow(label: String, statement: String, books: String, matches: Boolean) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(2f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(statement, Modifier.weight(1f), textAlign = TextAlign.End)
        Text(books, Modifier.weight(1f), textAlign = TextAlign.End, color = if (matches) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error, fontWeight = if (matches) FontWeight.Normal else FontWeight.Bold)
    }
}

fun chooseBrokerageFile(model: BooksModel): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = model.t("investments.import")
        fileFilter = FileNameExtensionFilter(model.t("investments.fileType"), "ofx", "qfx", "csv")
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

/** INV-05: reads a brokerage file, lets the user pick the account for each statement in it, and imports. */
@Composable
private fun ImportDialog(model: BooksModel, file: File, current: Account?, onClose: () -> Unit) {
    val books = model.books
    val read = remember(file) { runCatching { books.brokerage.read(file.name, file.readBytes()) } }
    val accounts = remember { books.investments.accounts() }
    var result by remember { mutableStateOf<InvestmentImportResult?>(null) }
    val statements = read.getOrNull().orEmpty()
    val targets = remember(statements) { mutableStateMapOf<Int, String?>().apply { statements.forEachIndexed { i, s -> put(i, books.brokerage.suggestAccount(s) ?: current?.id) } } }
    result?.let { r -> ImportResultDialog(model, r, onClose); return }
    FormDialog(model.t("investments.importOf", file.name), model.t("quicken.go"), model.t("common.cancel"), canSave = statements.isNotEmpty() && targets.values.all { it != null }, onDismiss = onClose, onSave = {
        result = model.act {
            statements.mapIndexed { i, s -> books.brokerage.import(targets[i]!!, s) }.reduce { a, b ->
                InvestmentImportResult(a.added + b.added, a.alreadyThere + b.alreadyThere, a.securitiesCreated + b.securitiesCreated, a.statementSaved || b.statementSaved, a.warnings + b.warnings)
            }
        }
    }) {
        read.exceptionOrNull()?.let { e -> ErrorText((e as? ValidationException)?.message(model.language) ?: model.t("error.generic", e.message ?: "")) }
        statements.forEachIndexed { i, s -> StatementTarget(model, s, accounts, accounts.firstOrNull { it.id == targets[i] }) { targets[i] = it.id } }
    }
}

@Composable
private fun StatementTarget(model: BooksModel, s: ImportedInvestmentStatement, accounts: List<Account>, selected: Account?, onPick: (Account) -> Unit) {
    Text(
        listOfNotNull(s.format, s.accountNumberHint?.let { "•••• " + it.takeLast(4) }, s.asOf?.let(model::date), model.t("investments.actionsCount", s.actions.size)).joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
    )
    Picker(model.t("quicken.into"), accounts, selected, { it.name }, onSelect = onPick)
}

@Composable
private fun ImportResultDialog(model: BooksModel, r: InvestmentImportResult, onClose: () -> Unit) {
    WideDialog(model.t("quicken.done"), model.t("common.close"), onClose) {
        Text(model.t("investments.importResult", r.added, r.securitiesCreated))
        if (r.alreadyThere > 0) Text(model.t("investments.alreadyThere", r.alreadyThere))
        if (r.statementSaved) Text(model.t("investments.statementSaved"))
        if (r.warnings.isNotEmpty()) {
            Text(model.t("quicken.notes"), style = MaterialTheme.typography.labelLarge)
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) { r.warnings.take(50).forEach { Text(it, style = MaterialTheme.typography.bodySmall) } }
        }
    }
}
