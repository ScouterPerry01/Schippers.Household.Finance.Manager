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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Account
import ca.schippers.hfm.books.AccountDraft
import ca.schippers.hfm.books.CryptoImportResult
import ca.schippers.hfm.books.CryptoIncomeKind
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.books.WalletDetails
import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.importers.CryptoExchangeImporter
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** What the wallet view is asking for. */
private enum class WalletAction { BUY, SELL, CONVERT, MOVE, FEE, INCOME, DETAILS }

/** CR-01 to CR-06: one crypto-asset wallet: balance, value, pooled ACB, recording and watch-only sync. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalletView(model: BooksModel, account: Account) {
    val books = model.books
    val scope = rememberCoroutineScope()
    var action by remember(account.id) { mutableStateOf<WalletAction?>(null) }
    var status by remember(account.id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val value = remember(model.revision, account.id) { books.crypto.value(account, today()) }
    val details = remember(model.revision, account.id) { books.crypto.details(account.id) }
    val pool = remember(model.revision, account.id) { books.crypto.acb(today()).pools.firstOrNull { account.id in it.accountIds } }
    // The ACB covers every wallet in the pool, so it is compared with all of them together.
    val poolValue = remember(model.revision, pool) {
        pool?.let { p ->
            val values = books.accounts.list(includeClosed = true).map { it.account }.filter { it.id in p.accountIds }.map { books.crypto.value(it, today()).value }
            if (values.any { it == null }) null else values.filterNotNull().fold(Money.zero(books.rates.baseCurrency)) { a, b -> a + b }
        }
    }
    val lines = remember(model.revision, account.id) { books.transactions.register(account.id).map { it.transaction }.asReversed() }
    val names = remember(model.revision) { books.accounts.list(includeClosed = true).associate { it.account.id to it.account.name } }
    val unlinked = lines.count { it.transfer == null && it.investmentId == null && it.amount.isNegative && it.splits.all { s -> s.categoryId == null } }

    Text(account.name, style = MaterialTheme.typography.titleLarge)
    Text(listOfNotNull(books.crypto.coinSecurity(account.currency).name, details.watch?.let { model.t("wallet.watching") }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
    FlowRow(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Stat(model.t("wallet.balance"), model.money(value.balance))
        Stat(model.t("investments.marketValue"), value.value?.let(model::money) ?: model.t("investments.noPrice"))
        value.priceDate?.let { Stat(model.t("wallet.priceOn"), model.date(it)) }
        pool?.let {
            Stat(model.t("wallet.pooledAcb", it.accountIds.size), model.money(it.acb))
            poolValue?.let { v -> Stat(model.t(if (it.accountIds.size > 1) "wallet.pooledGain" else "investments.unrealized"), model.money(v - it.acb)) }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = { action = WalletAction.BUY }) { Text(model.t("wallet.buy")) }
        OutlinedButton(onClick = { action = WalletAction.SELL }) { Text(model.t("wallet.sell")) }
        OutlinedButton(onClick = { action = WalletAction.CONVERT }) { Text(model.t("wallet.convert")) }
        OutlinedButton(onClick = { action = WalletAction.MOVE }) { Text(model.t("wallet.move")) }
        OutlinedButton(onClick = { action = WalletAction.FEE }) { Text(model.t("wallet.fee")) }
        OutlinedButton(onClick = { action = WalletAction.INCOME }) { Text(model.t("wallet.income")) }
        OutlinedButton(onClick = { action = WalletAction.DETAILS }) { Text(model.t("wallet.details")) }
        if (details.watch != null) {
            OutlinedButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { books.crypto.sync(account.id, Http::get) } }
                    status = r.fold({ model.t("wallet.synced", it.added, it.addresses) }, { (it as? ValidationException)?.message(model.language) ?: model.t("rates.failed", it.message.orEmpty()) })
                    busy = false
                    model.changed()
                }
            }) { Text(model.t("wallet.sync")) }
        }
    }
    status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    if (unlinked > 0) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.t("wallet.unlinked", unlinked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            TextButton(onClick = { model.act { books.crypto.linkTransfers() }?.let { status = model.t("wallet.linked", it) } }) { Text(model.t("wallet.link")) }
        }
    }
    HorizontalDivider(Modifier.padding(top = 8.dp))
    LazyColumn {
        if (lines.isEmpty()) item { Text(model.t("wallet.noLines"), Modifier.padding(8.dp)) }
        items(lines, key = { it.id }) { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(model.date(t.date), Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall)
                Text(
                    listOfNotNull(t.memo ?: t.payeeText, t.transfer?.let { "→ " + names[it.otherAccountId].orEmpty() }, t.originalAmount?.let(model::money)).joinToString(" · "),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                )
                MoneyText(model, t.amount, modifier = Modifier.width(170.dp), textAlign = TextAlign.End)
            }
            HorizontalDivider()
        }
    }
    action?.let { a -> WalletDialog(model, account, a) { action = null } }
}

/** One dialog for the wallet actions; fields change with the action. */
@Composable
private fun WalletDialog(model: BooksModel, account: Account, action: WalletAction, onClose: () -> Unit) {
    if (action == WalletAction.DETAILS) return WalletDetailsDialog(model, account, onClose)
    val books = model.books
    val locale = model.language.locale
    val coin = account.currency
    val all = remember { books.accounts.list().map { it.account } }
    val fiat = all.filter { !it.currency.isCrypto && it.type.kind in setOf(AccountKind.BANK, AccountKind.CREDIT) }
    val otherWallets = all.filter { it.type == AccountType.CRYPTO_WALLET && it.id != account.id && (if (action == WalletAction.CONVERT) it.currency != coin else it.currency == coin) }
    var other by remember { mutableStateOf(if (action in setOf(WalletAction.BUY, WalletAction.SELL)) fiat.firstOrNull() else otherWallets.firstOrNull()) }
    var date by remember { mutableStateOf(today().toString()) }
    var coins by remember { mutableStateOf("") }
    var otherAmount by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(CryptoIncomeKind.STAKING) }
    var memo by remember { mutableStateOf("") }
    val needsOther = action in setOf(WalletAction.BUY, WalletAction.SELL, WalletAction.CONVERT, WalletAction.MOVE)
    FormDialog(model.t("wallet.$action") + " · " + account.name, model.t("common.save"), model.t("common.cancel"), canSave = !needsOther || other != null, onDismiss = onClose, onSave = {
        val ok = model.act {
            val d = runCatching { LocalDate.parse(date.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }
            val amount = parseAmount(coins, coin, locale) ?: throw ValidationException("error.amountPositive")
            fun second(c: Currency) = parseAmount(otherAmount, c, locale)
            val m = memo.ifBlank { null }
            when (action) {
                WalletAction.BUY -> books.crypto.buy(account.id, other!!.id, d, amount, second(other!!.currency) ?: throw ValidationException("error.amountPositive"), m)
                WalletAction.SELL -> books.crypto.sell(account.id, other!!.id, d, amount, second(other!!.currency) ?: throw ValidationException("error.amountPositive"), m)
                WalletAction.CONVERT -> books.crypto.convert(account.id, other!!.id, d, amount, second(other!!.currency) ?: throw ValidationException("error.amountPositive"), m)
                WalletAction.MOVE -> books.crypto.move(account.id, other!!.id, d, amount, second(coin), m)
                WalletAction.FEE -> books.crypto.fee(account.id, d, amount, m)
                WalletAction.INCOME -> books.crypto.income(account.id, d, amount, kind, m)
                WalletAction.DETAILS -> Unit
            }
        }
        if (ok != null) onClose()
    }) {
        Text(model.t("wallet.$action.hint"), style = MaterialTheme.typography.bodySmall)
        if (needsOther) {
            val choices = if (action in setOf(WalletAction.BUY, WalletAction.SELL)) fiat else otherWallets
            if (choices.isEmpty()) Text(model.t(if (action in setOf(WalletAction.BUY, WalletAction.SELL)) "wallet.noFiat" else "wallet.noOtherWallet"), color = MaterialTheme.colorScheme.error)
            Picker(model.t("wallet.other.$action"), choices, other, { "${it.name} (${it.currency.code})" }) { other = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t("report.date"), date, Modifier.weight(1f)) { date = it }
            AmountInput(model.t("wallet.coins.$action", coin.code), coins, coin, locale, Modifier.weight(1f), model::money) { coins = it }
        }
        if (action == WalletAction.MOVE) {
            AmountInput(model.t("wallet.networkFee", coin.code), otherAmount, coin, locale, Modifier.fillMaxWidth(), model::money) { otherAmount = it }
        } else if (needsOther) {
            other?.let { o -> AmountInput(model.t("wallet.otherAmount.$action", o.currency.code), otherAmount, o.currency, locale, Modifier.fillMaxWidth(), model::money) { otherAmount = it } }
        }
        if (action == WalletAction.INCOME) Picker(model.t("wallet.incomeKind"), CryptoIncomeKind.entries, kind, { model.t("cryptoIncome.$it") }) { kind = it }
        TextInput(model.t("register.memo"), memo) { memo = it }
    }
}

/** CR-02 and CR-06: the cost of coins already held when the history starts, and the watched address or key. */
@Composable
private fun WalletDetailsDialog(model: BooksModel, account: Account, onClose: () -> Unit) {
    val locale = model.language.locale
    val base = model.books.rates.baseCurrency
    val d = remember { model.books.crypto.details(account.id) }
    var cost by remember { mutableStateOf(d.openingCost?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var watch by remember { mutableStateOf(d.watch.orEmpty()) }
    FormDialog(model.t("wallet.details") + " · " + account.name, model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act { model.books.crypto.saveDetails(d.copy(openingCost = parseAmount(cost, base, locale), watch = watch)) }
        if (ok != null) onClose()
    }) {
        if (account.openingBalance.isPositive) {
            Text(model.t("wallet.openingHint", model.money(account.openingBalance)), style = MaterialTheme.typography.bodySmall)
            AmountInput(model.t("wallet.openingCost", base.code), cost, base, locale, Modifier.fillMaxWidth(), model::money) { cost = it }
        }
        if (account.currency == Currency.BTC) {
            Text(model.t("wallet.watchHint"), style = MaterialTheme.typography.bodySmall)
            TextInput(model.t("wallet.watch"), watch, singleLine = false) { watch = it }
        } else {
            Text(model.t("wallet.watchBitcoinOnly"), style = MaterialTheme.typography.bodySmall)
        }
    }
}

fun chooseExchangeFile(model: BooksModel): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = model.t("wallet.importTitle")
        fileFilter = FileNameExtensionFilter(model.t("wallet.importType"), "csv")
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

/** CR-03: an exchange's history: an account for each fiat currency, a wallet for each coin, then the import. */
@Composable
fun ExchangeImportDialog(model: BooksModel, file: File, onClose: () -> Unit) {
    val books = model.books
    val read = remember(file) { runCatching { CryptoExchangeImporter.read(file.readBytes()) } }
    val exchange = read.getOrNull()
    val plan = remember(exchange) { exchange?.let { books.crypto.plan(it) } }
    val accounts = remember { books.accounts.list().map { it.account } }
    val fiat = remember(plan) { mutableStateMapOf<String, Account?>().apply { plan?.fiat?.forEach { c -> put(c, accounts.firstOrNull { it.currency.code == c && it.name.contains(plan.exchange, ignoreCase = true) }) } } }
    val wallets = remember(plan) { mutableStateMapOf<String, Account?>().apply { plan?.coins?.forEach { c -> put(c, accounts.firstOrNull { it.type == AccountType.CRYPTO_WALLET && it.currency.code == c && it.name.contains(plan.exchange, ignoreCase = true) }) } } }
    var groupId by remember { mutableStateOf(model.defaultGroupForPersonalRecords()?.id) }
    var result by remember { mutableStateOf<CryptoImportResult?>(null) }
    result?.let { r ->
        WideDialog(model.t("quicken.done"), model.t("common.close"), onClose) {
            Text(model.t("wallet.importResult", r.added, r.walletsCreated, r.linked))
            if (r.alreadyThere > 0) Text(model.t("investments.alreadyThere", r.alreadyThere))
            if (r.warnings.isNotEmpty()) Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) { r.warnings.take(50).forEach { Text(it, style = MaterialTheme.typography.bodySmall) } }
        }
        return
    }
    FormDialog(model.t("investments.importOf", file.name), model.t("quicken.go"), model.t("common.cancel"), canSave = plan != null && groupId != null, onDismiss = onClose, onSave = {
        result = model.act {
            val p = plan!!
            val fiatIds = p.fiat.associateWith { c ->
                fiat[c]?.id ?: books.accounts.create(AccountDraft(groupId!!, "${p.exchange} $c", AccountType.CASH, Currency.of(c), Money.zero(Currency.of(c)), exchange!!.events.minOf { it.date })).id
            }
            val owner = model.books.members.list().firstOrNull { m -> model.session.core.coreQueries.userById(model.session.userId).executeAsOneOrNull()?.member_id == m.id }
            books.crypto.importExchange(exchange!!, groupId!!, fiatIds, p.coins.associateWith { wallets[it]?.id }, setOfNotNull(owner?.id))
        }
    }) {
        read.exceptionOrNull()?.let { ErrorText(model.t("wallet.importUnknown")) }
        plan?.let { p ->
            Text(model.t("wallet.importSummary", p.exchange, p.events), fontWeight = FontWeight.Bold)
            Text(model.t("wallet.importHint"), style = MaterialTheme.typography.bodySmall)
            for (c in p.fiat) {
                Picker(model.t("wallet.fiatAccount", c), listOf<Account?>(null) + accounts.filter { it.currency.code == c && !it.currency.isCrypto }, fiat[c],
                    { it?.name ?: model.t("wallet.createAccount", "${p.exchange} $c") }) { fiat[c] = it }
            }
            for (c in p.coins) {
                Picker(model.t("wallet.coinWallet", c), listOf<Account?>(null) + accounts.filter { it.type == AccountType.CRYPTO_WALLET && it.currency.code == c }, wallets[c],
                    { it?.name ?: model.t("wallet.createAccount", "${p.exchange} $c") }) { wallets[c] = it }
            }
            val groups = remember { model.editableGroups() }
            if (groups.size > 1) Picker(model.t("calendar.storeIn"), groups, groups.firstOrNull { it.id == groupId }, { it.name }) { groupId = it.id }
            p.warnings.take(10).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
