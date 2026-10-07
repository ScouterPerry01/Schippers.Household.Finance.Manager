package ca.schippers.hfm.desktop

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Account
import ca.schippers.hfm.books.DocumentStatus
import ca.schippers.hfm.books.Metal
import ca.schippers.hfm.books.MetalForm
import ca.schippers.hfm.books.MetalItem
import ca.schippers.hfm.books.MetalMoney
import ca.schippers.hfm.books.MetalStorage
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.calc.metals.WeightUnit
import ca.schippers.hfm.domain.AccountStatus
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.ocr.desktop.FileKind
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat
import ca.schippers.hfm.money.sum
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.util.Locale
import javax.swing.JFileChooser

/** PM-01 to PM-04: the coins, bars and rounds in a precious metals account, valued from spot prices. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetalsView(model: BooksModel, account: Account) {
    val books = model.books
    val locale = model.language.locale
    val valuations = remember(model.revision, account.id) { books.metals.valuations(account.id, today()) }
    val sold = remember(model.revision, account.id) { books.metals.items(account.id, includeSold = true).filter { !it.held } }
    var editing by remember(account.id) { mutableStateOf<MetalItem?>(null) }
    var adding by remember(account.id) { mutableStateOf(false) }
    val c = account.currency
    val value = valuations.map { it.value ?: it.item.cost ?: Money.zero(c) }.sum(c)
    val cost = valuations.mapNotNull { it.item.cost }.sum(c)

    Text(account.name, style = MaterialTheme.typography.titleLarge)
    Text(model.t("accountType.${account.type}"), style = MaterialTheme.typography.bodySmall)
    FlowRow(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Stat(model.t("investments.marketValue"), model.money(value))
        Stat(model.t("investments.bookCost"), model.money(cost))
        Stat(model.t("investments.unrealized"), model.money(value - cost))
        for ((metal, list) in valuations.groupBy { it.item.metal }) {
            Stat(model.t("metal.$metal"), model.t("metals.fineOz", fine(list.sumOf { it.item.fineOunces }, locale)))
        }
    }
    if (valuations.any { it.spot == null }) Text(model.t("metals.noSpot"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    Button(onClick = { adding = true }) { Text(model.t("metals.add")) }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        listOf("metals.item" to 2f, "metals.fine" to 1f, "investments.marketValue" to 1f, "investments.bookCost" to 1f, "investments.gain" to 1f).forEachIndexed { i, (key, w) ->
            Text(model.t(key), Modifier.weight(w).semantics { heading() }, fontWeight = FontWeight.Bold, textAlign = if (i == 0) TextAlign.Start else TextAlign.End, style = MaterialTheme.typography.bodySmall)
        }
    }
    HorizontalDivider()
    LazyColumn {
        if (valuations.isEmpty()) item { Text(model.t("metals.none"), Modifier.padding(8.dp)) }
        items(valuations, key = { it.item.id }) { v ->
            val i = v.item
            Row(Modifier.fillMaxWidth().clickable { editing = i }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(2f)) {
                    Text("${i.quantity} × ${i.description}", fontWeight = FontWeight.Medium)
                    Text(
                        listOfNotNull(model.t("metal.${i.metal}"), "${fine(i.weight, locale)} ${model.t("weightUnit.${i.unit}")}", fine(i.purity, locale), model.t("metalStorage.${i.storage}") + (i.storageDetail?.let { " · $it" } ?: ""), if (i.insured) model.t("metals.insured") else null).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(model.t("metals.fineOz", fine(i.fineOunces, locale)), Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
                Text(v.value?.let(model::money) ?: "–", Modifier.weight(1f), textAlign = TextAlign.End)
                Text(i.cost?.let(model::money) ?: "–", Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
                Text(v.gain?.let(model::money) ?: "", Modifier.weight(1f), textAlign = TextAlign.End, color = if (v.gain?.isNegative == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider()
        }
        if (sold.isNotEmpty()) {
            item { Text(model.t("metals.sold"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp)) }
            item {
                HeadingRow {
                    ColumnHeading(model.t("metals.item"), Modifier.weight(2f))
                    ColumnHeading(model.t("metals.soldOn"), Modifier.weight(1f))
                    ColumnHeading(model.t("column.proceeds"), Modifier.weight(1f), TextAlign.End)
                }
            }
            items(sold, key = { "s" + it.id }) { i ->
                Row(Modifier.fillMaxWidth().clickable { editing = i }.padding(vertical = 4.dp)) {
                    Text("${i.quantity} × ${i.description}", Modifier.weight(2f), style = MaterialTheme.typography.bodySmall)
                    Text(i.disposalDate?.let(model::date).orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(i.proceeds?.let(model::money).orEmpty(), Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (adding || editing != null) MetalItemDialog(model, account, editing) { adding = false; editing = null }
}

private fun fine(v: BigDecimal, locale: Locale): String =
    java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 4 }.format(v)

private fun plain(v: BigDecimal?, locale: Locale): String =
    v?.stripTrailingZeros()?.let { if (it.scale() < 0) it.setScale(0) else it }?.toPlainString()?.replace('.', java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator).orEmpty()

/** PM-01, PM-03, PM-04: one item, its storage and insurance, its sale and its certificates. */
@Composable
private fun MetalItemDialog(model: BooksModel, account: Account, existing: MetalItem?, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val c = account.currency
    fun amt(m: Money?) = m?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()
    var metal by remember { mutableStateOf(existing?.metal ?: Metal.GOLD) }
    var form by remember { mutableStateOf(existing?.form ?: MetalForm.COIN) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var quantity by remember { mutableStateOf(existing?.quantity?.toString() ?: "1") }
    var weight by remember { mutableStateOf(plain(existing?.weight, locale).ifEmpty { "1" }) }
    var unit by remember { mutableStateOf(existing?.unit ?: WeightUnit.OZT) }
    var purity by remember { mutableStateOf(plain(existing?.purity, locale).ifEmpty { plain(BigDecimal("0.9999"), locale) }) }
    var serials by remember { mutableStateOf(existing?.serialNumbers.orEmpty()) }
    var dealer by remember { mutableStateOf(existing?.dealer.orEmpty()) }
    var bought by remember { mutableStateOf(existing?.purchaseDate?.toString() ?: today().toString()) }
    var cost by remember { mutableStateOf(amt(existing?.cost)) }
    var premium by remember { mutableStateOf(plain(existing?.premiumPercent, locale)) }
    var storage by remember { mutableStateOf(existing?.storage ?: MetalStorage.HOME_SAFE) }
    var where by remember { mutableStateOf(existing?.storageDetail.orEmpty()) }
    var insured by remember { mutableStateOf(existing?.insured ?: false) }
    var insurance by remember { mutableStateOf(existing?.insuranceNote.orEmpty()) }
    var soldOn by remember { mutableStateOf(existing?.disposalDate?.toString().orEmpty()) }
    var proceeds by remember { mutableStateOf(amt(existing?.proceeds)) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    val documents = remember(model.revision, existing?.id) { existing?.let { books.metals.documents(it.id) }.orEmpty() }
    // M-34: the accounts the purchase was paid from and the sale deposited to, in the account's currency.
    val linked = remember(existing?.id) { existing?.let { books.metals.money(it.id) } ?: MetalMoney() }
    val moneyAccounts = remember {
        books.accounts.list(includeClosed = true).map { it.account }
            .filter { it.currency == c && it.id != account.id && it.type !in setOf(AccountType.PRECIOUS_METALS, AccountType.CRYPTO_WALLET) }
            .filter { it.status == AccountStatus.OPEN || it.id == linked.paidFromAccountId || it.id == linked.depositedToAccountId }
    }
    var paidFrom by remember { mutableStateOf(moneyAccounts.firstOrNull { it.id == linked.paidFromAccountId }) }
    var depositedTo by remember { mutableStateOf(moneyAccounts.firstOrNull { it.id == linked.depositedToAccountId }) }
    fun dec(text: String) = text.trim().ifEmpty { null }?.let { runCatching { MoneyFormat.parseDecimal(it, locale) }.getOrElse { throw ValidationException("error.invalidNumber") } }
    fun date(text: String) = text.trim().ifEmpty { null }?.let { runCatching { LocalDate.parse(it) }.getOrElse { throw ValidationException("error.invalidDate") } }

    FormDialog(model.t(if (existing == null) "metals.add" else "metals.edit"), model.t("common.save"), model.t("common.cancel"), canSave = description.isNotBlank(), onDismiss = onClose, onSave = {
        fun item() = MetalItem(
            existing?.id.orEmpty(), account.id, metal, form, description, dec(weight) ?: throw ValidationException("error.quantityRequired"), unit,
            dec(purity) ?: throw ValidationException("error.purity"), quantity.trim().toIntOrNull() ?: throw ValidationException("error.quantityRequired"),
            serials, dealer, date(bought), parseAmount(cost, c, locale), dec(premium), storage, where, insured, insurance, date(soldOn), parseAmount(proceeds, c, locale), notes,
        )
        val money = MetalMoney(paidFrom?.id, depositedTo?.id)
        val ok = model.act(retryConfirmed = { books.metals.save(item(), money, confirmReconciled = true).also { onClose() } }) { books.metals.save(item(), money) }
        if (ok != null) onClose()
    }) {
        Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("prices.metal"), Metal.entries, metal, { model.t("metal.$it") }, Modifier.weight(1f)) { metal = it }
                Picker(model.t("metals.form"), MetalForm.entries, form, { model.t("metalForm.$it") }, Modifier.weight(1f)) { form = it }
            }
            TextInput(model.t("metals.description"), description, supporting = model.t("metals.descriptionHint")) { description = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("investments.quantity"), quantity, Modifier.weight(0.7f)) { quantity = it }
                TextInput(model.t("metals.weight"), weight, Modifier.weight(1f)) { weight = it }
                Picker(model.t("metals.unit"), WeightUnit.entries, unit, { model.t("weightUnit.$it") }, Modifier.weight(1f)) { unit = it }
                TextInput(model.t("metals.purity"), purity, Modifier.weight(1f), supporting = model.t("metals.purityHint")) { purity = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(model.t("metals.bought"), bought, Modifier.weight(1f)) { bought = it }
                AmountInput(model.t("metals.cost"), cost, c, locale, Modifier.weight(1f), model::money) { cost = it }
                TextInput(model.t("metals.premium"), premium, Modifier.weight(1f), supporting = model.t("metals.premiumHint")) { premium = it }
            }
            Picker(model.t("metals.paidFrom"), listOf<Account?>(null) + moneyAccounts, paidFrom, { it?.name ?: model.t("metals.noAccount") }) { paidFrom = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("metals.dealer"), dealer, Modifier.weight(1f)) { dealer = it }
                TextInput(model.t("metals.serials"), serials, Modifier.weight(1f)) { serials = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("metals.storage"), MetalStorage.entries, storage, { model.t("metalStorage.$it") }, Modifier.weight(1f)) { storage = it }
                TextInput(model.t("metals.where"), where, Modifier.weight(1f)) { where = it }
            }
            Text(model.t("metals.moneyHint"), style = MaterialTheme.typography.bodySmall)
            LabeledCheckbox(model.t("metals.insuredField"), insured) { insured = it }
            if (insured) TextInput(model.t("metals.insurance"), insurance) { insurance = it }
            if (existing != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateInput(model.t("metals.soldOn"), soldOn, Modifier.weight(1f)) { soldOn = it }
                    AmountInput(model.t("investments.proceeds"), proceeds, c, locale, Modifier.weight(1f), model::money) { proceeds = it }
                }
                Picker(model.t("metals.depositedTo"), listOf<Account?>(null) + moneyAccounts, depositedTo, { it?.name ?: model.t("metals.noAccount") }) { depositedTo = it }
                Text(model.t("metals.documents"), style = MaterialTheme.typography.labelLarge)
                for (doc in documents) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(doc.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { model.act { books.metals.detach(existing.id, doc.id) } }) { Text(model.t("metals.detach")) }
                    }
                }
                TextButton(onClick = { attachFile(model, account, existing) }) { Text(model.t("metals.attach")) }
                TextButton(onClick = {
                    if (model.act(retryConfirmed = { books.metals.delete(account.id, existing.id, confirmReconciled = true); onClose() }) { books.metals.delete(account.id, existing.id) } != null) onClose()
                }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
            }
            TextInput(model.t("account.notes"), notes, singleLine = false) { notes = it }
        }
    }
}

/** PM-04: a certificate or photo, stored in the vault (already filed) and linked to the item. */
private fun attachFile(model: BooksModel, account: Account, item: MetalItem) {
    val chooser = JFileChooser().apply { dialogTitle = model.t("metals.attach") }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
    val file = chooser.selectedFile
    model.act {
        val bytes = file.readBytes()
        val kind = FileKind.of(bytes)
        if (kind == FileKind.UNSUPPORTED) throw ValidationException("error.unsupportedFile")
        val doc = model.books.documents.import(account.groupId, bytes, file.name, kind.mimeType).document
        model.books.documents.setStatus(doc.id, DocumentStatus.FILED)
        model.books.metals.attach(item.id, doc.id)
    }
}
