package ca.schippers.hfm.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Asset
import ca.schippers.hfm.books.AssetKind
import ca.schippers.hfm.books.AssetService
import ca.schippers.hfm.books.AssetStatus
import ca.schippers.hfm.books.AssetWarranty
import ca.schippers.hfm.books.AssetWarrantyKind
import ca.schippers.hfm.books.CoverageStatus
import ca.schippers.hfm.books.InsuranceClaim
import ca.schippers.hfm.books.InsuranceClaimStatus
import ca.schippers.hfm.books.InsurancePolicy
import ca.schippers.hfm.books.InsuranceService
import ca.schippers.hfm.books.LinkRole
import ca.schippers.hfm.books.LinkTarget
import ca.schippers.hfm.books.MeterUnit
import ca.schippers.hfm.books.PolicyBeneficiary
import ca.schippers.hfm.books.PolicyKind
import ca.schippers.hfm.books.PremiumFrequency
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.books.ValueMethod
import ca.schippers.hfm.books.Transaction
import ca.schippers.hfm.books.WarrantyClaim
import ca.schippers.hfm.calc.rules.Thresholds
import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

private enum class AssetsTab { ASSETS, UPKEEP, PROJECTS, CONTRACTORS, COVERED, INSURANCE }

/** AST, WAR and INS: the home and other assets, what covers them, and insurance policies. */
@Composable
fun AssetsScreen(model: BooksModel) {
    var tab by remember { mutableStateOf(if (model.focusContractorId != null) AssetsTab.CONTRACTORS else AssetsTab.ASSETS) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(model.t("assets.title"), style = MaterialTheme.typography.titleLarge)
        Text(model.t("assets.hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        PrimaryTabRow(selectedTabIndex = tab.ordinal, modifier = Modifier.padding(vertical = 8.dp)) {
            for (t in AssetsTab.entries) Tab(selected = tab == t, onClick = { tab = t }, text = { Text(model.t("assets.tab.$t")) })
        }
        when (tab) {
            AssetsTab.ASSETS -> AssetsTabView(model)
            AssetsTab.UPKEEP -> {
                var open by remember { mutableStateOf<Asset?>(null) }
                UpkeepTab(model) { open = it }
                open?.let { a -> AssetDialog(model, a) { open = null } }
            }
            AssetsTab.PROJECTS -> ProjectsTab(model)
            AssetsTab.CONTRACTORS -> ContractorsTab(model)
            AssetsTab.COVERED -> CoveredTab(model)
            AssetsTab.INSURANCE -> InsuranceTab(model)
        }
    }
}

private fun dateOrNull(text: String): LocalDate? = text.trim().ifEmpty { null }?.let { runCatching { LocalDate.parse(it) }.getOrElse { throw ValidationException("error.invalidDate") } }

private fun intOrNull(text: String): Int? = text.trim().ifEmpty { null }?.let { it.toIntOrNull() ?: throw ValidationException("error.invalidNumber") }

// --- Assets (AST-01 to AST-05) -------------------------------------------------------------------

@Composable
private fun AssetsTabView(model: BooksModel) {
    val books = model.books
    var showDisposed by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Asset?>(null) }
    val all = remember(model.revision, showDisposed) { books.assets.list(showDisposed) }
    val group = remember(model.revision) { model.defaultDocumentGroup() ?: books.groups().first().id }
    // AST-02: each asset under the one it is part of.
    val rows = remember(all) {
        val byParent = all.groupBy { it.parentId?.takeIf { p -> all.any { a -> a.id == p } } }
        val out = ArrayList<Pair<Asset, Int>>()
        fun add(parent: String?, depth: Int) {
            for (a in byParent[parent].orEmpty().sortedBy { it.name.lowercase() }) {
                out += a to depth
                add(a.id, depth + 1)
            }
        }
        add(null, 0)
        out
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { editing = Asset("", group, AssetKind.APPLIANCE, "") }) { Text(model.t("assets.add")) }
        LabeledCheckbox(model.t("assets.showDisposed"), showDisposed) { showDisposed = it }
    }
    if (rows.isEmpty()) Text(model.t("assets.none"), Modifier.padding(vertical = 12.dp))
    LazyColumn(Modifier.padding(top = 8.dp)) {
        items(rows, key = { it.first.id }) { (a, depth) ->
            Row(Modifier.fillMaxWidth().clickable { editing = a }.padding(start = (depth * 24).dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.name + if (a.status != AssetStatus.ACTIVE) " · " + model.t("assetStatus.${a.status}") else "", fontWeight = if (depth == 0) FontWeight.Bold else FontWeight.Normal)
                    Text(listOfNotNull(model.t("assetKind.${a.kind}"), a.make, a.model, a.location).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                a.valueOn(today())?.let { MoneyText(model, it) } ?: a.purchasePrice?.let { Text(model.money(it), color = MaterialTheme.colorScheme.outline) }
            }
            HorizontalDivider()
        }
    }
    editing?.let { a -> AssetDialog(model, a) { editing = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssetDialog(model: BooksModel, existing: Asset, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    // AST-01: an asset's amounts are in the household's base currency (or the one it was saved in).
    val cur = if (existing.id.isBlank()) books.rates.baseCurrency else existing.currency
    fun amt(m: Money?) = m?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()
    val others = remember { books.assets.list().filter { it.id != existing.id } }
    val members = remember { books.members.list() }
    var kind by remember { mutableStateOf(existing.kind) }
    var name by remember { mutableStateOf(existing.name) }
    var parentId by remember { mutableStateOf(existing.parentId) }
    var make by remember { mutableStateOf(existing.make.orEmpty()) }
    var modelName by remember { mutableStateOf(existing.model.orEmpty()) }
    var serial by remember { mutableStateOf(existing.serialNumber.orEmpty()) }
    var purchaseDate by remember { mutableStateOf(existing.purchaseDate?.toString().orEmpty()) }
    var seller by remember { mutableStateOf(existing.seller.orEmpty()) }
    var price by remember { mutableStateOf(amt(existing.purchasePrice)) }
    var transactionId by remember { mutableStateOf(existing.transactionId) }
    var find by remember { mutableStateOf("") }
    var location by remember { mutableStateOf(existing.location.orEmpty()) }
    var ownerId by remember { mutableStateOf(existing.ownerMemberId) }
    var method by remember { mutableStateOf(existing.valueMethod) }
    var value by remember { mutableStateOf(amt(existing.value)) }
    var years by remember { mutableStateOf((existing.depreciationYears ?: Thresholds.depreciationYears(today())).toString()) }
    var residual by remember { mutableStateOf((existing.residualPercent ?: Thresholds.depreciationResidualPercent(today())).stripTrailingZeros().toPlainString()) }
    var inNetWorth by remember { mutableStateOf(existing.inNetWorth) }
    var status by remember { mutableStateOf(existing.status) }
    var disposalDate by remember { mutableStateOf(existing.disposalDate?.toString().orEmpty()) }
    var disposalPrice by remember { mutableStateOf(amt(existing.disposalPrice)) }
    // SAL-03, AST-05: the sale in the books (its payee is the buyer).
    var saleId by remember { mutableStateOf(existing.disposalTransactionId) }
    var notes by remember { mutableStateOf(existing.notes.orEmpty()) }
    var meter by remember { mutableStateOf(existing.meter) }
    var saved by remember { mutableStateOf(existing.takeIf { it.id.isNotBlank() }) }
    var warranty by remember { mutableStateOf<AssetWarranty?>(null) }
    var asking by remember { mutableStateOf(false) }
    val linked = transactionId?.let { id -> remember(id) { runCatching { books.transactions.get(id) }.getOrNull() } }
    val hits = remember(find) { if (find.trim().length < 2) emptyList() else books.search.search(find, locale, 30).transactions.filter { it.transaction.amount.isNegative } }

    WideDialog(model.t(if (existing.id.isBlank()) "assets.add" else "assets.asset"), model.t("common.close"), onClose) {
        Column(Modifier.width(780.dp).heightIn(max = 640.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("assets.kind"), AssetKind.entries, kind, { model.t("assetKind.$it") }, Modifier.weight(1f)) { kind = it }
                TextInput(model.t("assets.name"), name, Modifier.weight(1f)) { name = it }
                Picker(model.t("assets.partOf"), listOf(null) + others, others.firstOrNull { it.id == parentId }, { it?.name ?: model.t("common.none") }, Modifier.weight(1f)) { parentId = it?.id }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("assets.make"), make, Modifier.weight(1f)) { make = it }
                TextInput(model.t("assets.model"), modelName, Modifier.weight(1f)) { modelName = it }
                TextInput(model.t("assets.serial"), serial, Modifier.weight(1f)) { serial = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(model.t("assets.purchaseDate"), purchaseDate, Modifier.weight(1f)) { purchaseDate = it }
                TextInput(model.t("assets.seller"), seller, Modifier.weight(1f)) { seller = it }
                AmountInput(model.t("assets.price"), price, cur, locale, Modifier.weight(1f), model::money) { price = it }
            }
            // AST-01: the purchase in the books, found by payee or memo.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    linked?.let { t -> model.t("assets.purchase", model.date(t.date), t.payeeText ?: "", model.money(-t.amount)) } ?: model.t("assets.noPurchase"),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                )
                if (linked != null) TextButton(onClick = { transactionId = null }) { Text(model.t("assets.unlink")) }
                TextInput(model.t("assets.findPurchase"), find, Modifier.width(220.dp)) { find = it }
                if (hits.isNotEmpty()) {
                    Picker(model.t("assets.pick"), hits, null, { h -> "${model.date(h.transaction.date)} · ${h.payeeName.orEmpty()} · ${model.money(-h.transaction.amount)}" }, Modifier.width(260.dp)) { h ->
                        transactionId = h.transaction.id
                        if (purchaseDate.isBlank()) purchaseDate = h.transaction.date.toString()
                        if (price.isBlank()) price = MoneyFormat.formatAmount(-h.transaction.amount, locale)
                        if (seller.isBlank()) seller = h.payeeName.orEmpty()
                        find = ""
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("assets.location"), location, Modifier.weight(1f)) { location = it }
                Picker(model.t("assets.owner"), listOf(null) + members, members.firstOrNull { it.id == ownerId }, { it?.displayName ?: model.t("assets.household") }, Modifier.weight(1f)) { ownerId = it?.id }
                // MNT-03: engine hours, or kilometres for an RV.
                Picker(model.t("assets.meter"), listOf(null) + MeterUnit.entries, meter, { it?.let { u -> model.t("meterUnit.$u") } ?: model.t("common.none") }, Modifier.weight(1f)) { meter = it }
            }
            // AST-03: what it is worth.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Picker(model.t("assets.valueMethod"), ValueMethod.entries, method, { model.t("valueMethod.$it") }, Modifier.width(220.dp)) { method = it }
                when (method) {
                    ValueMethod.MANUAL -> AmountInput(model.t("assets.value"), value, cur, locale, Modifier.width(180.dp), model::money) { value = it }
                    ValueMethod.DEPRECIATION -> {
                        TextInput(model.t("assets.years"), years, Modifier.width(120.dp)) { years = it }
                        TextInput(model.t("assets.residual"), residual, Modifier.width(150.dp)) { residual = it }
                    }
                    ValueMethod.NONE -> Unit
                }
                if (method != ValueMethod.NONE) LabeledCheckbox(model.t("assets.inNetWorth"), inNetWorth) { inNetWorth = it }
            }
            // AST-05: sold, given away or thrown out.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("assets.status"), AssetStatus.entries, status, { model.t("assetStatus.$it") }, Modifier.weight(1f)) { status = it }
                if (status != AssetStatus.ACTIVE) {
                    DateInput(model.t("assets.disposalDate"), disposalDate, Modifier.weight(1f)) { disposalDate = it }
                    if (status == AssetStatus.SOLD) AmountInput(model.t("assets.salePrice"), disposalPrice, cur, locale, Modifier.weight(1f), model::money) { disposalPrice = it }
                }
            }
            if (status == AssetStatus.SOLD) {
                SaleLink(model, saleId, onUnlink = { saleId = null }) { t ->
                    saleId = t.id
                    if (disposalDate.isBlank()) disposalDate = t.date.toString()
                    if (disposalPrice.isBlank()) disposalPrice = MoneyFormat.formatAmount(t.amount, locale)
                }
                saleResult(model, runCatching { parseAmount(price, cur, locale) }.getOrNull(), runCatching { parseAmount(disposalPrice, cur, locale) }.getOrNull())
                    ?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            TextInput(model.t("account.notes"), notes, singleLine = false) { notes = it }
            // CON-04: who services or insures it, as contacts.
            LinkedContacts(model, LinkTarget.ASSET, saved?.id, listOf(LinkRole.SERVICE, LinkRole.INSURER, LinkRole.OTHER), memberIds = setOfNotNull(ownerId), groupId = existing.groupId)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    model.act {
                        books.assets.save(
                            existing.copy(
                                id = saved?.id.orEmpty(), kind = kind, name = name, parentId = parentId, make = make, model = modelName, serialNumber = serial,
                                purchaseDate = dateOrNull(purchaseDate), seller = seller, purchasePrice = parseAmount(price, cur, locale), transactionId = transactionId,
                                location = location, ownerMemberId = ownerId, valueMethod = method, value = parseAmount(value, cur, locale), valueDate = today().takeIf { method == ValueMethod.MANUAL },
                                depreciationYears = intOrNull(years), residualPercent = residual.trim().ifEmpty { null }?.let { runCatching { MoneyFormat.parseDecimal(it, locale) }.getOrElse { throw ValidationException("error.invalidNumber") } },
                                inNetWorth = inNetWorth && method != ValueMethod.NONE, status = status, disposalDate = dateOrNull(disposalDate).takeIf { status != AssetStatus.ACTIVE },
                                disposalPrice = parseAmount(disposalPrice, cur, locale).takeIf { status == AssetStatus.SOLD },
                                disposalTransactionId = saleId.takeIf { status == AssetStatus.SOLD }, notes = notes, meter = meter,
                            ),
                        )
                    }?.let { saved = it }
                }) { Text(model.t("common.save")) }
                if (saved != null) TextButton(onClick = { asking = true }) { Text(model.t("common.delete")) }
            }

            saved?.let { s ->
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(model.t("assets.coverage"), style = MaterialTheme.typography.titleSmall)
                val coverage = remember(model.revision, s.id) { books.assets.coverage(s.id, today()) }
                if (coverage.isEmpty()) Text(model.t("assets.noCoverage"), style = MaterialTheme.typography.bodySmall)
                for (c in coverage) {
                    Row(Modifier.fillMaxWidth().let { m -> c.warranty?.let { w -> m.clickable { warranty = w } } ?: m }.padding(vertical = 3.dp)) {
                        Text(listOf(model.t(c.kindKey), c.label).filter { it.isNotBlank() }.joinToString(" · "), Modifier.weight(1f))
                        Text(coverageEnd(model, c), style = MaterialTheme.typography.bodySmall, color = if (c.active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)
                    }
                }
                OutlinedButton(onClick = { warranty = AssetWarranty("", s.groupId, s.id, AssetWarrantyKind.MANUFACTURER, startDate = s.purchaseDate) }) { Text(model.t("assets.addWarranty")) }
                DocumentsBlock(model, AssetService.ENTITY, s.id, s.groupId, "assets.photos")
                AssetUpkeepBlock(model, s)
            }
        }
    }
    warranty?.let { w -> WarrantyDialog(model, w) { warranty = null } }
    saved?.let { s ->
        if (asking) {
            AskBeforeDeleting(model, model.t("assets.delete.asset", s.name), onDismiss = { asking = false }) {
                (model.act { books.assets.delete(s.id) } != null).also { if (it) onClose() }
            }
        }
    }
}

/** WAR-04: "until 2027-05-01", "ended 2026-01-31", "ended: 500 hours used", or "no end date". */
private fun coverageEnd(model: BooksModel, c: CoverageStatus): String {
    val until = c.until
    return when {
        c.usedUp -> model.t("assets.endedHours", c.warranty?.endHours ?: 0)
        until != null -> model.t(if (c.active) "assets.until" else "assets.ended", model.date(until))
        else -> model.t("assets.noEnd")
    }
}

@Composable
private fun WarrantyDialog(model: BooksModel, existing: AssetWarranty, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val cards = remember { books.accounts.list().map { it.account }.filter { it.type.kind == AccountKind.CREDIT } }
    var kind by remember { mutableStateOf(existing.kind) }
    var provider by remember { mutableStateOf(existing.provider.orEmpty()) }
    var coverage by remember { mutableStateOf(existing.coverage.orEmpty()) }
    var start by remember { mutableStateOf(existing.startDate?.toString().orEmpty()) }
    var end by remember { mutableStateOf(existing.endDate?.toString().orEmpty()) }
    var hours by remember { mutableStateOf(existing.endHours?.toString().orEmpty()) }
    var phone by remember { mutableStateOf(existing.phone.orEmpty()) }
    var cardId by remember { mutableStateOf(existing.cardAccountId) }
    var notes by remember { mutableStateOf(existing.notes.orEmpty()) }
    var problem by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf("") }
    var covered by remember { mutableStateOf("") }
    val claims = remember(model.revision, existing.id) { if (existing.id.isBlank()) emptyList() else books.assets.claims(existing.id) }
    val base = books.rates.baseCurrency
    var asking by remember { mutableStateOf(false) }
    var deletingClaim by remember { mutableStateOf<WarrantyClaim?>(null) }
    WideDialog(model.t("assets.warranty"), model.t("common.close"), onClose) {
        Column(Modifier.width(640.dp).heightIn(max = 600.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("assets.warrantyKind"), AssetWarrantyKind.entries, kind, { model.t("assetWarranty.$it") }, Modifier.weight(1f)) { kind = it }
                TextInput(model.t("assets.provider"), provider, Modifier.weight(1f)) { provider = it }
            }
            if (kind == AssetWarrantyKind.CARD_EXTENDED) {
                Picker(model.t("assets.card"), cards, cards.firstOrNull { it.id == cardId }, { it.name }) { cardId = it.id }
                Text(model.t("assets.cardHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            TextInput(model.t("assets.covers"), coverage) { coverage = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(model.t("assets.start"), start, Modifier.weight(1f)) { start = it }
                DateInput(model.t("assets.end"), end, Modifier.weight(1f)) { end = it }
                TextInput(model.t("assets.endHours"), hours, Modifier.weight(1f)) { hours = it }
            }
            TextInput(model.t("assets.claimPhone"), phone) { phone = it }
            TextInput(model.t("account.notes"), notes, singleLine = false) { notes = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    model.act {
                        books.assets.saveWarranty(existing.copy(kind = kind, provider = provider, coverage = coverage, startDate = dateOrNull(start), endDate = dateOrNull(end), endHours = intOrNull(hours), phone = phone, cardAccountId = cardId.takeIf { kind == AssetWarrantyKind.CARD_EXTENDED }, notes = notes))
                    }?.let { onClose() }
                }) { Text(model.t("common.save")) }
                if (existing.id.isNotBlank()) TextButton(onClick = { asking = true }) { Text(model.t("common.delete")) }
            }
            if (existing.id.isNotBlank()) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(model.t("assets.claims"), style = MaterialTheme.typography.titleSmall)
                for (c in claims) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${model.date(c.date)} · ${c.problem}" + (c.outcome?.let { " → $it" }.orEmpty()), Modifier.weight(1f))
                        c.covered?.let { Text(model.t("assets.coveredAmount", model.money(it)), style = MaterialTheme.typography.bodySmall) }
                        TextButton(onClick = { deletingClaim = c }) { Text(model.t("common.delete")) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextInput(model.t("assets.problem"), problem, Modifier.weight(1f)) { problem = it }
                    TextInput(model.t("assets.outcome"), outcome, Modifier.weight(1f)) { outcome = it }
                    AmountInput(model.t("assets.coveredLabel"), covered, base, locale, Modifier.width(140.dp), model::money) { covered = it }
                    OutlinedButton(onClick = {
                        model.act { books.assets.saveClaim(WarrantyClaim("", existing.id, today(), problem, outcome, parseAmount(covered, base, locale))) }?.let { problem = ""; outcome = ""; covered = "" }
                    }) { Text(model.t("assets.addClaim")) }
                }
                DocumentsBlock(model, AssetService.WARRANTY, existing.id, existing.groupId, "assets.proof")
            }
        }
    }
    if (asking) {
        AskBeforeDeleting(model, model.t("assets.delete.warranty", model.t("assetWarranty.${existing.kind}")), onDismiss = { asking = false }) {
            (model.act { books.assets.deleteWarranty(existing.id) } != null).also { if (it) onClose() }
        }
    }
    deletingClaim?.let { c ->
        AskBeforeDeleting(model, model.t("assets.delete.claim", c.problem, model.date(c.date)), onDismiss = { deletingClaim = null }) {
            model.act { books.assets.deleteClaim(existing.id, c.id) } != null
        }
    }
}

// --- Is it covered? (WAR-04) ---------------------------------------------------------------------

@Composable
private fun CoveredTab(model: BooksModel) {
    var query by remember { mutableStateOf("") }
    val items = remember(model.revision, query) { model.books.assets.findCovered(query, today()) }
    TextInput(model.t("assets.searchCovered"), query, Modifier.width(420.dp), supporting = model.t("assets.searchCoveredHint")) { query = it }
    LazyColumn(Modifier.padding(top = 8.dp)) {
        items(items, key = { it.id }) { item ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name + if (item.isVehicle) " · " + model.t("assets.vehicle") else "", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(
                        model.t(if (item.covered) "assets.covered" else "assets.notCovered"),
                        color = if (item.covered) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    )
                }
                for (c in item.coverage) {
                    Text(
                        listOf(model.t(c.kindKey), c.label).filter { it.isNotBlank() }.joinToString(" · ") + " · " + coverageEnd(model, c),
                        style = MaterialTheme.typography.bodySmall, color = if (c.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

// --- Insurance (INS-01 to INS-05) ----------------------------------------------------------------

@Composable
private fun InsuranceTab(model: BooksModel) {
    val books = model.books
    val policies = remember(model.revision) { books.insurance.policies() }
    val uninsured = remember(model.revision) { books.insurance.uninsured(today()) }
    val life = remember(model.revision) { books.insurance.lifeSummary() }
    var editing by remember { mutableStateOf<InsurancePolicy?>(null) }
    val group = remember(model.revision) { model.defaultDocumentGroup() ?: books.groups().first().id }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Button(onClick = { editing = InsurancePolicy("", group, PolicyKind.HOME, "") }) { Text(model.t("insurance.add")) }
        if (policies.isEmpty()) Text(model.t("insurance.none"), Modifier.padding(vertical = 12.dp))
        for (p in policies) {
            Row(Modifier.fillMaxWidth().clickable { editing = p }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${model.t("policyKind.${p.kind}")} · ${p.insurer}" + if (!p.active) " · " + model.t("medical.inactive") else "", fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(p.policyNumber, p.renewalDate?.let { model.t("insurance.renews", model.date(it)) }, p.coverage?.let { model.t("insurance.coverageOf", model.money(it)) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                    )
                }
                p.annualPremium?.let { Text(model.t("insurance.perYear", model.money(it))) }
            }
            HorizontalDivider()
        }
        // INS-02: what no policy covers.
        if (uninsured.isNotEmpty()) {
            Text(model.t("insurance.uninsured"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            Text(model.t("insurance.uninsuredHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            for (u in uninsured) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(u.name + if (u.isVehicle) " · " + model.t("assets.vehicle") else "", Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    u.value?.let { Text(model.money(it), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        // INS-05: life and disability cover, for estate planning.
        if (life.isNotEmpty()) {
            Text(model.t("insurance.life"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            for (l in life) {
                Text(
                    "${model.t("policyKind.${l.policy.kind}")} · ${l.policy.insurer} · ${model.memberName(l.policy.insuredMemberId)}" + (l.policy.coverage?.let { " · " + model.money(it) }.orEmpty()),
                )
                Text(
                    l.beneficiaries.joinToString { b -> b.name + (b.sharePercent?.let { " ${it.stripTrailingZeros().toPlainString()} %" }.orEmpty()) + if (b.contingent) " (" + model.t("insurance.contingent") + ")" else "" }
                        .ifEmpty { model.t("insurance.noBeneficiary") },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
    editing?.let { p -> PolicyDialog(model, p) { editing = null } }
}

private fun BooksModel.memberName(id: String?): String = books.members.list(includeArchived = true).firstOrNull { it.id == id }?.displayName.orEmpty()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PolicyDialog(model: BooksModel, existing: InsurancePolicy, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val cad = Currency.CAD
    fun amt(m: Money?) = m?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()
    val members = remember { books.members.list() }
    val items = remember { books.assets.list().map { it.id to it.name } + books.vehicles.list().map { it.id to "${it.name} (${model.t("assets.vehicle")})" } }
    var kind by remember { mutableStateOf(existing.kind) }
    var insurer by remember { mutableStateOf(existing.insurer) }
    var broker by remember { mutableStateOf(existing.broker.orEmpty()) }
    var number by remember { mutableStateOf(existing.policyNumber.orEmpty()) }
    var insured by remember { mutableStateOf(existing.insuredMemberId) }
    var premium by remember { mutableStateOf(amt(existing.premium)) }
    var frequency by remember { mutableStateOf(existing.frequency) }
    var deductible by remember { mutableStateOf(amt(existing.deductible)) }
    var coverage by remember { mutableStateOf(amt(existing.coverage)) }
    var coverageNotes by remember { mutableStateOf(existing.coverageNotes.orEmpty()) }
    var start by remember { mutableStateOf(existing.startDate?.toString().orEmpty()) }
    var renewal by remember { mutableStateOf(existing.renewalDate?.toString().orEmpty()) }
    var active by remember { mutableStateOf(existing.active) }
    var notes by remember { mutableStateOf(existing.notes.orEmpty()) }
    val covered = remember { mutableStateListOf<String>().apply { addAll(existing.assetIds) } }
    var saved by remember { mutableStateOf(existing.takeIf { it.id.isNotBlank() }) }
    var renewTo by remember { mutableStateOf("") }
    var renewPremium by remember { mutableStateOf("") }
    var beneficiary by remember { mutableStateOf("") }
    var share by remember { mutableStateOf("") }
    var contingent by remember { mutableStateOf(false) }
    var claim by remember { mutableStateOf<InsuranceClaim?>(null) }
    var asking by remember { mutableStateOf(false) }
    var deletingBeneficiary by remember { mutableStateOf<PolicyBeneficiary?>(null) }
    val lifeKinds = setOf(PolicyKind.LIFE, PolicyKind.DISABILITY, PolicyKind.CRITICAL_ILLNESS, PolicyKind.LONG_TERM_CARE)

    WideDialog(model.t(if (existing.id.isBlank()) "insurance.add" else "insurance.policy"), model.t("common.close"), onClose) {
        Column(Modifier.width(780.dp).heightIn(max = 640.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("insurance.kind"), PolicyKind.entries, kind, { model.t("policyKind.$it") }, Modifier.weight(1f)) { kind = it }
                TextInput(model.t("insurance.insurer"), insurer, Modifier.weight(1f)) { insurer = it }
                TextInput(model.t("insurance.broker"), broker, Modifier.weight(1f)) { broker = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("insurance.number"), number, Modifier.weight(1f)) { number = it }
                Picker(model.t("insurance.insured"), listOf(null) + members, members.firstOrNull { it.id == insured }, { it?.displayName ?: model.t("assets.household") }, Modifier.weight(1f)) { insured = it?.id }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountInput(model.t("insurance.premium"), premium, cad, locale, Modifier.weight(1f), model::money) { premium = it }
                Picker(model.t("insurance.frequency"), PremiumFrequency.entries, frequency, { model.t("premiumFrequency.$it") }, Modifier.weight(1f)) { frequency = it }
                AmountInput(model.t("insurance.deductible"), deductible, cad, locale, Modifier.weight(1f), model::money) { deductible = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountInput(model.t(if (kind in lifeKinds) "insurance.benefit" else "insurance.coverage"), coverage, cad, locale, Modifier.weight(1f), model::money) { coverage = it }
                TextInput(model.t("insurance.coverageNotes"), coverageNotes, Modifier.weight(2f)) { coverageNotes = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(model.t("insurance.start"), start, Modifier.weight(1f)) { start = it }
                DateInput(model.t("insurance.renewal"), renewal, Modifier.weight(1f)) { renewal = it }
            }
            if (kind !in lifeKinds && items.isNotEmpty()) {
                Text(model.t("insurance.covers"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((id, label) in items) LabeledCheckbox(label, id in covered) { on -> if (on) covered += id else covered -= id }
                }
            }
            LabeledCheckbox(model.t("medical.active"), active) { active = it }
            TextInput(model.t("account.notes"), notes, singleLine = false) { notes = it }
            // CON-04: the insurer and broker as contacts, with their phones; the text fields above stay as typed.
            LinkedContacts(model, LinkTarget.POLICY, saved?.id, listOf(LinkRole.INSURER, LinkRole.BROKER, LinkRole.ADVISOR, LinkRole.OTHER), suggestedName = insurer, memberIds = setOfNotNull(insured), groupId = existing.groupId)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    model.act {
                        books.insurance.save(
                            existing.copy(
                                id = saved?.id.orEmpty(), kind = kind, insurer = insurer, broker = broker, policyNumber = number, insuredMemberId = insured, premium = parseAmount(premium, cad, locale),
                                frequency = frequency, deductible = parseAmount(deductible, cad, locale), coverage = parseAmount(coverage, cad, locale), coverageNotes = coverageNotes,
                                startDate = dateOrNull(start), renewalDate = dateOrNull(renewal), active = active, notes = notes, assetIds = covered.toSet(),
                            ),
                        )
                    }?.let { saved = it }
                }) { Text(model.t("common.save")) }
                if (saved != null) TextButton(onClick = { asking = true }) { Text(model.t("common.delete")) }
            }

            saved?.let { s ->
                // INS-03: renewal and premiums year over year.
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(model.t("insurance.premiums"), style = MaterialTheme.typography.titleSmall)
                val premiums = remember(model.revision, s.id) { books.insurance.premiums(s.id) }
                for (p in premiums) Text("${model.date(p.startDate)} · ${model.money(p.premium)}", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DateInput(model.t("insurance.newRenewal"), renewTo, Modifier.width(200.dp)) { renewTo = it }
                    AmountInput(model.t("insurance.newPremium"), renewPremium, cad, locale, Modifier.width(180.dp), model::money) { renewPremium = it }
                    OutlinedButton(onClick = {
                        model.act { books.insurance.renew(s.id, dateOrNull(renewTo) ?: throw ValidationException("error.invalidDate"), parseAmount(renewPremium, cad, locale)) }?.let { r ->
                            saved = r; start = r.startDate?.toString().orEmpty(); renewal = r.renewalDate?.toString().orEmpty(); premium = amt(r.premium); renewTo = ""; renewPremium = ""
                        }
                    }) { Text(model.t("insurance.renew")) }
                }
                // INS-05: beneficiaries.
                if (kind in lifeKinds) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text(model.t("insurance.beneficiaries"), style = MaterialTheme.typography.titleSmall)
                    val list = remember(model.revision, s.id) { books.insurance.beneficiaries(s.id) }
                    for (b in list) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(b.name + (b.sharePercent?.let { " · ${it.stripTrailingZeros().toPlainString()} %" }.orEmpty()) + if (b.contingent) " · " + model.t("insurance.contingent") else "", Modifier.weight(1f))
                            TextButton(onClick = { deletingBeneficiary = b }) { Text(model.t("common.delete")) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextInput(model.t("insurance.beneficiaryName"), beneficiary, Modifier.weight(1f)) { beneficiary = it }
                        TextInput(model.t("insurance.share"), share, Modifier.width(120.dp)) { share = it }
                        LabeledCheckbox(model.t("insurance.contingent"), contingent) { contingent = it }
                        OutlinedButton(onClick = {
                            model.act {
                                books.insurance.saveBeneficiary(PolicyBeneficiary("", s.id, beneficiary, sharePercent = share.trim().ifEmpty { null }?.let { BigDecimal(it.replace(',', '.')) }, contingent = contingent))
                            }?.let { beneficiary = ""; share = ""; contingent = false }
                        }) { Text(model.t("medical.add")) }
                    }
                }
                // INS-04: claims.
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(model.t("insurance.claims"), style = MaterialTheme.typography.titleSmall)
                val claims = remember(model.revision, s.id) { books.insurance.claims(s.id) }
                for (c in claims) {
                    Row(Modifier.fillMaxWidth().clickable { claim = c }.padding(vertical = 3.dp)) {
                        Text("${model.date(c.date)} · ${c.description}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(model.t("insuranceClaim.${c.status}") + (c.paid?.let { " · " + model.money(it) }.orEmpty()), style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(onClick = { claim = InsuranceClaim("", s.id, today(), "") }) { Text(model.t("insurance.addClaim")) }
                DocumentsBlock(model, InsuranceService.POLICY, s.id, s.groupId, "insurance.documents")
            }
        }
    }
    claim?.let { c -> ClaimDialog(model, c, items, saved?.groupId ?: existing.groupId) { claim = null } }
    saved?.let { s ->
        if (asking) {
            AskBeforeDeleting(model, model.t("insurance.delete.policy", "${model.t("policyKind.${s.kind}")} · ${s.insurer}"), onDismiss = { asking = false }) {
                (model.act { books.insurance.delete(s.id) } != null).also { if (it) onClose() }
            }
        }
        deletingBeneficiary?.let { b ->
            AskBeforeDeleting(model, model.t("insurance.delete.beneficiary", b.name), onDismiss = { deletingBeneficiary = null }) {
                model.act { books.insurance.deleteBeneficiary(s.id, b.id) } != null
            }
        }
    }
}

@Composable
private fun ClaimDialog(model: BooksModel, existing: InsuranceClaim, items: List<Pair<String, String>>, groupId: String, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val cad = Currency.CAD
    fun amt(m: Money?) = m?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()
    var date by remember { mutableStateOf(existing.date.toString()) }
    var description by remember { mutableStateOf(existing.description) }
    var assetId by remember { mutableStateOf(existing.assetId) }
    var number by remember { mutableStateOf(existing.claimNumber.orEmpty()) }
    var status by remember { mutableStateOf(existing.status) }
    var claimed by remember { mutableStateOf(amt(existing.claimed)) }
    var deductible by remember { mutableStateOf(amt(existing.deductible)) }
    var paid by remember { mutableStateOf(amt(existing.paid)) }
    var paidDate by remember { mutableStateOf(existing.paidDate?.toString().orEmpty()) }
    FormDialog(model.t("insurance.claim"), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            books.insurance.saveClaim(
                existing.copy(
                    date = dateOrNull(date) ?: today(), description = description, assetId = assetId, claimNumber = number, status = status, claimed = parseAmount(claimed, cad, locale),
                    deductible = parseAmount(deductible, cad, locale), paid = parseAmount(paid, cad, locale), paidDate = dateOrNull(paidDate),
                ),
            )
        }
        if (ok != null) onClose()
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t("report.date"), date, Modifier.weight(1f)) { date = it }
            TextInput(model.t("medical.reference"), number, Modifier.weight(1f)) { number = it }
        }
        TextInput(model.t("medical.description"), description) { description = it }
        Picker(model.t("insurance.item"), listOf(null) + items, items.firstOrNull { it.first == assetId }, { it?.second ?: model.t("common.none") }) { assetId = it?.first }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker(model.t("assets.status"), InsuranceClaimStatus.entries, status, { model.t("insuranceClaim.$it") }, Modifier.weight(1f)) { status = it }
            AmountInput(model.t("medical.claimed"), claimed, cad, locale, Modifier.weight(1f), model::money) { claimed = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountInput(model.t("insurance.deductible"), deductible, cad, locale, Modifier.weight(1f), model::money) { deductible = it }
            AmountInput(model.t("medical.paid"), paid, cad, locale, Modifier.weight(1f), model::money) { paid = it }
            DateInput(model.t("medical.paidOnDate"), paidDate, Modifier.weight(1f)) { paidDate = it }
        }
        // INS-04: the claim's documents (photos of the damage, estimates, the insurer's letters), once it is saved.
        if (existing.id.isNotBlank()) {
            DocumentsBlock(model, InsuranceService.CLAIM, existing.id, groupId, "insurance.claimDocuments")
        } else {
            Text(model.t("insurance.claimDocumentsLater"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * SAL-03, AST-05: the sale deposit in the books linked to a sold item or vehicle (its payee is the
 * buyer), with a search among deposits to link one. [onPick] receives the chosen deposit.
 */
@Composable
internal fun SaleLink(model: BooksModel, saleId: String?, onUnlink: () -> Unit, onPick: (Transaction) -> Unit) {
    val books = model.books
    val locale = model.language.locale
    var findSale by remember { mutableStateOf("") }
    val sale = saleId?.let { id -> remember(id) { runCatching { books.transactions.get(id) }.getOrNull() } }
    val saleHits = remember(findSale) { if (findSale.trim().length < 2) emptyList() else books.search.search(findSale, locale, 30).transactions.filter { it.transaction.amount.isPositive } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            sale?.let { t -> model.t("assets.sale", model.date(t.date), t.payeeText ?: "", model.money(t.amount)) } ?: model.t("assets.noSale"),
            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
        )
        if (sale != null) TextButton(onClick = onUnlink) { Text(model.t("assets.unlink")) }
        TextInput(model.t("assets.findSale"), findSale, Modifier.width(220.dp)) { findSale = it }
        if (saleHits.isNotEmpty()) {
            Picker(model.t("assets.pickSale"), saleHits, null, { h -> "${model.date(h.transaction.date)} · ${h.payeeName.orEmpty()} · ${model.money(h.transaction.amount)}" }, Modifier.width(260.dp)) { h ->
                onPick(h.transaction)
                findSale = ""
            }
        }
    }
}

/**
 * SAL-03: what a sale gained or lost against the purchase price (both in the same currency), as
 * a line to show; null when either price is missing.
 */
internal fun saleResult(model: BooksModel, purchase: Money?, sale: Money?): String? {
    if (purchase == null || sale == null || purchase.currency != sale.currency) return null
    val result = sale - purchase
    return when {
        result.isPositive -> model.t("assets.saleGain", model.money(result))
        result.isNegative -> model.t("assets.saleLoss", model.money(-result))
        else -> model.t("assets.saleEven")
    }
}
