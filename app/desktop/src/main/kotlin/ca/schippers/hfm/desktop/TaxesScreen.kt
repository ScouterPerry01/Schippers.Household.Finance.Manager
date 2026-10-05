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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.OutlinedButton
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
import ca.schippers.hfm.books.ChecklistSlip
import ca.schippers.hfm.books.DocumentEntity
import ca.schippers.hfm.books.Donation
import ca.schippers.hfm.books.DonationReceipt
import ca.schippers.hfm.books.Instalment
import ca.schippers.hfm.books.InstalmentService
import ca.schippers.hfm.books.InstalmentState
import ca.schippers.hfm.books.PackageItem
import ca.schippers.hfm.books.PackageLine
import ca.schippers.hfm.books.PersonPackage
import ca.schippers.hfm.books.TaxPackage
import ca.schippers.hfm.books.SlipChecklistService
import ca.schippers.hfm.books.SlipStatus
import ca.schippers.hfm.books.SlipType
import ca.schippers.hfm.books.TaxAuthority
import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.domain.TaxFlag
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat

private enum class TaxesTab { SLIPS, DONATIONS, INSTALMENTS, YEAR_END }

/** Phase 5b: the tax year in one place: the slips (TAX-01), donations and their receipts (OTH-01), instalments (TAX-03) and the year-end package (TAX-02). */
@Composable
fun TaxesScreen(model: BooksModel) {
    var tab by remember { mutableStateOf(TaxesTab.SLIPS) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(model.t("nav.taxes"), style = MaterialTheme.typography.titleLarge)
        Text(model.t("taxes.hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        PrimaryTabRow(selectedTabIndex = tab.ordinal, modifier = Modifier.padding(vertical = 8.dp)) {
            for (t in TaxesTab.entries) Tab(selected = tab == t, onClick = { tab = t }, text = { Text(model.t("taxes.tab.$t")) })
        }
        when (tab) {
            TaxesTab.SLIPS -> SlipsTab(model)
            TaxesTab.DONATIONS -> DonationsTab(model)
            TaxesTab.INSTALMENTS -> InstalmentsTab(model)
            TaxesTab.YEAR_END -> PackageTab(model)
        }
    }
}

private fun BooksModel.personName(id: String?): String =
    books.members.list(includeArchived = true).firstOrNull { it.id == id }?.displayName ?: t("taxes.household")

// --- Donations (OTH-01) ------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DonationsTab(model: BooksModel) {
    val books = model.books
    val thisYear = today().year
    var year by remember { mutableStateOf(thisYear) }
    var editing by remember { mutableStateOf<Donation?>(null) }
    val gifts = remember(model.revision, year) { books.donations.list(year) }
    val totals = remember(model.revision, year) { books.donations.totals(year) }

    Picker(model.t("taxes.year"), (thisYear downTo thisYear - 6).toList(), year, { it.toString() }, Modifier.width(190.dp)) { year = it }
    Text(model.t("taxes.donationsHint"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
    if (totals.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            for (t in totals) OutlinedCard {
                Column(Modifier.padding(12.dp).width(220.dp)) {
                    Text(model.personName(t.memberId), fontWeight = FontWeight.Medium)
                    Text(model.t("taxes.charitableTotal", model.money(t.charitable)))
                    if (!t.political.isZero) Text(model.t("taxes.politicalTotal", model.money(t.political)))
                    if (t.missingReceipts > 0) Text(model.t("taxes.missingReceipts", t.missingReceipts), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (gifts.isEmpty()) Text(model.t("taxes.noDonations"), Modifier.padding(vertical = 12.dp))
    LazyColumn(Modifier.padding(top = 8.dp)) {
        items(gifts, key = { "${it.transactionId}/${it.memberId}/${it.kind}" }) { d ->
            Row(Modifier.fillMaxWidth().clickable { editing = d }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(model.date(d.date), Modifier.width(100.dp))
                Text(model.personName(d.memberId), Modifier.width(110.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Column(Modifier.weight(1f)) {
                    val name = d.receipt?.charity ?: d.memo?.takeIf { d.payroll }?.let { model.t("taxes.throughPayroll", it, d.payee.orEmpty()) } ?: d.payee.orEmpty()
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val status = when {
                        d.receipt?.received == true || d.documents > 0 -> "taxes.receiptIn"
                        d.payroll -> "taxes.receiptOnT4"
                        else -> "taxes.receiptMissing"
                    }
                    Text(
                        listOf(model.t("tax.${d.kind}"), model.t(status)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (d.hasReceipt) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    MoneyText(model, d.amount)
                    if (d.eligible != d.amount) Text(model.t("taxes.eligibleShort", model.money(d.eligible)), style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
        }
    }
    editing?.let { d -> DonationDialog(model, d) { editing = null } }
}

/** What the official receipt says, and the receipt itself filed with the transaction. */
@Composable
private fun DonationDialog(model: BooksModel, d: Donation, onClose: () -> Unit) {
    val locale = model.language.locale
    var charity by remember { mutableStateOf(d.receipt?.charity ?: (if (d.payroll) d.memo else null) ?: d.payee.orEmpty()) }
    var registration by remember { mutableStateOf(d.receipt?.registration.orEmpty()) }
    var number by remember { mutableStateOf(d.receipt?.receiptNumber.orEmpty()) }
    var eligible by remember { mutableStateOf((d.eligibleShare ?: d.receipt?.eligible)?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var received by remember { mutableStateOf(d.receipt?.received ?: (d.documents > 0)) }
    FormDialog(model.t("taxes.receiptTitle"), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            model.books.donations.setReceipt(
                d.transactionId,
                DonationReceipt(charity, registration, number, parseAmount(eligible, d.amount.currency, locale)?.abs(), received),
                d.memberId, d.kind,
            )
        }
        if (ok != null) onClose()
    }) {
        Text(listOfNotNull(model.date(d.date), d.payee, model.money(d.amount)).joinToString(" · "), fontWeight = FontWeight.Medium)
        TextInput(model.t("taxes.charity"), charity) { charity = it }
        if (d.kind == TaxFlag.CHARITABLE) TextInput(model.t("taxes.registration"), registration, supporting = model.t("taxes.registrationHint")) { registration = it }
        TextInput(model.t("taxes.receiptNumber"), number) { number = it }
        AmountInput(model.t("taxes.eligible"), eligible, d.amount.currency, locale, Modifier.fillMaxWidth(), model::money) { eligible = it }
        Text(model.t("taxes.eligibleHint"), style = MaterialTheme.typography.bodySmall)
        LabeledCheckbox(model.t("taxes.received"), received) { received = it }
        DocumentsBlock(model, DocumentEntity.TRANSACTION, d.transactionId, d.groupId, "taxes.receiptFiles")
    }
}

/** The tax year being prepared: last year until the end of June, then this year. */
private fun taxSeasonYear(): Int = today().let { if (it.month <= kotlinx.datetime.Month.JUNE) it.year - 1 else it.year }

// --- Slip checklist (TAX-01) -------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlipsTab(model: BooksModel) {
    val books = model.books
    val thisYear = today().year
    var year by remember { mutableStateOf(taxSeasonYear()) }
    var open by remember { mutableStateOf<ChecklistSlip?>(null) }
    var adding by remember { mutableStateOf(false) }
    val slips = remember(model.revision, year) { books.slipChecklist.checklist(year) }
    // Slips still expected are pointed out once they should all have arrived.
    val late = today() > kotlinx.datetime.LocalDate(year + 1, 3, 31)

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Picker(model.t("taxes.year"), (thisYear downTo thisYear - 6).toList(), year, { it.toString() }, Modifier.width(190.dp)) { year = it }
        OutlinedButton(onClick = { adding = true }, modifier = Modifier.padding(top = 8.dp)) { Text(model.t("slips.add")) }
    }
    Text(model.t("slips.hint"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
    if (slips.isEmpty()) Text(model.t("slips.none"), Modifier.padding(vertical = 12.dp))
    LazyColumn(Modifier.padding(top = 8.dp)) {
        slips.groupBy { it.memberId }.forEach { (member, mine) ->
            item(key = "h/$member") {
                val received = mine.count { it.status == SlipStatus.RECEIVED }
                val counted = mine.count { it.status != SlipStatus.NOT_EXPECTED }
                Text(model.personName(member) + " · " + model.t("slips.progress", received, counted), fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            }
            items(mine, key = { it.id }) { slip ->
                Row(Modifier.fillMaxWidth().clickable { open = slip }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(model.t("slipType.${slip.type}"), Modifier.width(270.dp).padding(end = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Column(Modifier.weight(1f)) {
                        Text(slip.issuer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(slip.reason?.let { model.t("slipReason.$it") } ?: model.t("slips.addedByHand"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            model.t("slipStatus.${slip.status}"),
                            color = when (slip.status) {
                                SlipStatus.RECEIVED -> MaterialTheme.colorScheme.primary
                                SlipStatus.EXPECTED -> if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                SlipStatus.NOT_EXPECTED -> MaterialTheme.colorScheme.outline
                            },
                        )
                        if (slip.documents > 0) Text(model.t("slips.filesShort", slip.documents), style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()
            }
        }
    }
    open?.let { slip -> SlipDialog(model, slip) { open = null } }
    if (adding) AddSlipDialog(model, year) { adding = false }
}

/** One slip: received or not expected this year, and the slip itself in the vault. */
@Composable
private fun SlipDialog(model: BooksModel, slip: ChecklistSlip, onClose: () -> Unit) {
    var status by remember { mutableStateOf(slip.status) }
    FormDialog(model.t("slipType.${slip.type}"), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        if (model.act { model.books.slipChecklist.setStatus(slip, status) } != null) onClose()
    }) {
        Text(listOf(model.personName(slip.memberId), slip.issuer, slip.year.toString()).joinToString(" · "), fontWeight = FontWeight.Medium)
        slip.reason?.let { Text(model.t("slipReason.$it"), style = MaterialTheme.typography.bodySmall) }
        Picker(model.t("slips.status"), SlipStatus.entries, status, { model.t("slipStatus.$it") }) { status = it }
        DocumentsBlock(model, SlipChecklistService.ENTITY, slip.id, slip.groupId, "slips.files")
        if (slip.manual) {
            TextButton(onClick = { if (model.act { model.books.slipChecklist.remove(slip) } != null) onClose() }) {
                Text(model.t("slips.remove"), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** A slip the books cannot know about: a contract's T4A, a day camp's RL-24, a slip from abroad. */
@Composable
private fun AddSlipDialog(model: BooksModel, year: Int, onClose: () -> Unit) {
    val members = remember { model.books.members.list() }
    var member by remember { mutableStateOf(members.firstOrNull()) }
    var type by remember { mutableStateOf(SlipType.T4A) }
    var issuer by remember { mutableStateOf("") }
    val group = remember { model.defaultDocumentGroup() ?: model.books.groups().first().id }
    FormDialog(model.t("slips.addTitle", year.toString()), model.t("common.save"), model.t("common.cancel"), canSave = issuer.isNotBlank(), onDismiss = onClose, onSave = {
        if (model.act { model.books.slipChecklist.add(year, member?.id, type, issuer, group) } != null) onClose()
    }) {
        Picker(model.t("report.person"), listOf(null) + members, member, { it?.displayName ?: model.t("taxes.household") }) { member = it }
        Picker(model.t("slips.type"), SlipType.entries, type, { model.t("slipType.$it") }) { type = it }
        TextInput(model.t("slips.issuer"), issuer) { issuer = it }
    }
}

// --- Instalments (TAX-03) ----------------------------------------------------------------------------

@Composable
private fun InstalmentsTab(model: BooksModel) {
    val books = model.books
    val thisYear = today().year
    var year by remember { mutableStateOf(thisYear) }
    var editing by remember { mutableStateOf<InstalmentPlan?>(null) }
    val schedule = remember(model.revision, year) { books.instalments.schedule(year) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Picker(model.t("taxes.year"), (thisYear + 1 downTo thisYear - 3).toList(), year, { it.toString() }, Modifier.width(190.dp)) { year = it }
        OutlinedButton(onClick = { editing = InstalmentPlan(null, null, TaxAuthority.CRA, emptyList()) }, modifier = Modifier.padding(top = 8.dp)) { Text(model.t("instalments.setUp")) }
    }
    Text(model.t("instalments.hint"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
    if (schedule.isEmpty()) Text(model.t("instalments.none"), Modifier.padding(vertical = 12.dp))
    LazyColumn(Modifier.padding(top = 8.dp)) {
        schedule.groupBy { Triple(it.accountId, it.memberId, it.authority) }.forEach { (key, rows) ->
            item(key = "h/$key") {
                Row(Modifier.fillMaxWidth().clickable { editing = InstalmentPlan(key.first, key.second, key.third, rows) }.padding(top = 12.dp, bottom = 4.dp)) {
                    Text(model.personName(key.second) + " · " + model.t("taxAuthority.${key.third}"), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(model.t("instalments.edit"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
            items(rows, key = { "${it.accountId}/${it.memberId}/${it.authority}/${it.dueDate}" }) { i ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(model.date(i.dueDate), Modifier.width(110.dp))
                    val part = i.covered.isPositive && i.state != InstalmentState.PAID
                    Text(
                        model.t("instalmentState.${i.state}") + if (part) " · " + model.t("instalments.covered", model.money(i.covered)) else "",
                        Modifier.weight(1f),
                        color = when (i.state) {
                            InstalmentState.PAID -> MaterialTheme.colorScheme.primary
                            InstalmentState.LATE -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                    MoneyText(model, i.amount)
                }
                HorizontalDivider()
            }
        }
    }
    editing?.let { plan -> InstalmentDialog(model, year, plan) { editing = null } }
}

/** One person's instalments to one authority, as being edited; [rows] are those already saved. */
private data class InstalmentPlan(val accountId: String?, val memberId: String?, val authority: TaxAuthority, val rows: List<Instalment>)

@Composable
private fun InstalmentDialog(model: BooksModel, year: Int, plan: InstalmentPlan, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val members = remember { books.members.list() }
    val accounts = remember { books.accounts.list().map { it.account }.filter { it.type.kind == AccountKind.BANK } }
    var member by remember { mutableStateOf(members.firstOrNull { it.id == plan.memberId } ?: members.firstOrNull()) }
    var authority by remember { mutableStateOf(plan.authority) }
    var account by remember { mutableStateOf(accounts.firstOrNull { it.id == plan.accountId } ?: accounts.firstOrNull()) }
    val amounts = remember {
        mutableStateListOf<String>().apply {
            for (i in 0 until 4) add(plan.rows.firstOrNull { it.dueDate == InstalmentService.dueDate(year, i) }?.amount?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty())
        }
    }
    FormDialog(model.t("instalments.title", year.toString()), model.t("common.save"), model.t("common.cancel"), canSave = account != null, onDismiss = onClose, onSave = {
        val ok = model.act {
            val a = account!!
            val values = amounts.map { parseAmount(it, a.currency, locale)?.abs() ?: Money.zero(a.currency) }
            // Moving the plan to another account, person or authority replaces the old one.
            if (plan.accountId != null && (plan.accountId != a.id || plan.memberId != member?.id || plan.authority != authority)) {
                books.instalments.save(plan.accountId, plan.memberId, year, plan.authority, List(4) { Money.zero(a.currency) })
            }
            books.instalments.save(a.id, member?.id, year, authority, values)
        }
        if (ok != null) onClose()
    }) {
        Text(model.t("instalments.dialogHint"), style = MaterialTheme.typography.bodySmall)
        Picker(model.t("report.person"), listOf(null) + members, member, { it?.displayName ?: model.t("taxes.household") }) { member = it }
        Picker(model.t("instalments.authority"), TaxAuthority.entries, authority, { model.t("taxAuthority.$it") }) { authority = it }
        Picker(model.t("instalments.account"), accounts, account, { it.name }) { account = it }
        val currency = account?.currency ?: Currency.CAD
        for (i in 0 until 4) {
            AmountInput(model.t("instalments.due", model.date(InstalmentService.dueDate(year, i))), amounts[i], currency, locale, Modifier.fillMaxWidth(), model::money) { amounts[i] = it }
        }
    }
}

// --- Year-end package (TAX-02) -----------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackageTab(model: BooksModel) {
    val books = model.books
    val locale = model.language.locale
    val thisYear = today().year
    var year by remember { mutableStateOf(taxSeasonYear()) }
    val pkg = remember(model.revision, year) { books.taxPackage.build(year) }
    var who by remember(year) { mutableStateOf(pkg.people.firstOrNull()?.memberId) }
    val person = pkg.people.firstOrNull { it.memberId == who } ?: pkg.people.firstOrNull()
    var message by remember { mutableStateOf<String?>(null) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Picker(model.t("taxes.year"), (thisYear downTo thisYear - 6).toList(), year, { it.toString() }, Modifier.width(190.dp)) { year = it }
        if (pkg.people.isNotEmpty()) {
            Picker(model.t("report.person"), pkg.people, person, { model.personName(it.memberId) }, Modifier.width(220.dp)) { who = it.memberId }
        }
    }
    Text(model.t("package.hint"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
    if (person == null) {
        Text(model.t("package.none"), Modifier.padding(vertical = 12.dp))
        return
    }
    val table = packageTable(model, year, person)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        androidx.compose.material3.Button(onClick = {
            model.act { exportPackageFolder(model, pkg) }?.let { dir -> message = model.t("package.exported", dir.path) }
        }) { Text(model.t("package.exportFolder")) }
        for (format in ExportFormat.entries) {
            OutlinedButton(onClick = { model.act { ReportExport.save(table, format, locale, model.t("report.export")) } }) { Text(model.t("report.export.$format")) }
        }
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    LazyColumn(Modifier.padding(top = 8.dp)) {
        person.lines.groupBy { it.item.section }.forEach { (section, lines) ->
            item(key = "s/$section") {
                Text(model.t("packageSection.$section"), fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            }
            items(lines, key = { "${it.item}/${it.detail}" }) { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(model.t("packageItem.${l.item}"), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(packageDetail(model, l).orEmpty(), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    Text(l.item.line.orEmpty(), Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    MoneyText(model, l.amount, modifier = Modifier.width(140.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                HorizontalDivider()
            }
        }
        item(key = "notes") {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (note in table.notes) Text(note, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** A line's detail in the user's language: a payer as is, an instalment's authority or a sales tax by name. */
private fun packageDetail(model: BooksModel, l: PackageLine): String? = when (l.item) {
    PackageItem.INSTALMENTS -> l.detail?.let { model.t("taxAuthority.$it") }
    PackageItem.SALES_TAX_PAID -> l.detail?.let { model.t("taxName.$it") }
    else -> l.detail
}

/** One person's package as a table, for PDF, Excel or CSV; the notes say what is still missing and what to check. */
private fun packageTable(model: BooksModel, year: Int, p: PersonPackage): ReportTable {
    val notes = buildList {
        if (p.missingSlips.isNotEmpty()) add(model.t("package.missing", p.missingSlips.joinToString(", ") { "${model.t("slipType.${it.type}")} (${it.issuer})" }))
        if (p.lines.any { it.item == PackageItem.RRSP_CONTRIBUTIONS }) add(model.t("package.rrspNote", year.toString(), (year + 1).toString()))
        if (p.province == ca.schippers.hfm.calc.Province.QC) add(model.t("package.quebecNote"))
        add(model.t("package.notice"))
    }
    return ReportTable(
        model.t("package.title", model.personName(p.memberId), year.toString()),
        model.t("package.subtitle", model.date(today())),
        listOf(model.t("package.column.section"), model.t("package.column.item"), model.t("package.column.detail"), model.t("package.column.line"), model.t("package.column.amount")),
        p.lines.map { l -> listOf(model.t("packageSection.${l.item.section}"), model.t("packageItem.${l.item}"), packageDetail(model, l), l.item.line, l.amount) },
        notes,
    )
}

/**
 * The package for an accountant: a folder with each person's summary (PDF and Excel) and a copy of
 * the slips and receipts filed for them. Returns the folder, or null when cancelled.
 */
private fun exportPackageFolder(model: BooksModel, pkg: TaxPackage): java.io.File? {
    val chooser = javax.swing.JFileChooser().apply {
        dialogTitle = model.t("package.exportFolder")
        fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
    }
    if (chooser.showSaveDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) return null
    fun safe(name: String) = name.replace(Regex("""[\\/:*?"<>|]"""), "-").trim().take(120)
    val dir = java.io.File(chooser.selectedFile, safe(model.t("package.folder", pkg.year.toString()))).apply { mkdirs() }
    val locale = model.language.locale
    for (p in pkg.people) {
        val name = safe(model.personName(p.memberId))
        val table = packageTable(model, pkg.year, p)
        ReportExport.write(table, ExportFormat.PDF, java.io.File(dir, "$name.pdf"), locale)
        ReportExport.write(table, ExportFormat.XLSX, java.io.File(dir, "$name.xlsx"), locale)
        if (p.documents.isEmpty()) continue
        val docs = java.io.File(dir, name).apply { mkdirs() }
        val used = HashSet<String>()
        for (d in p.documents) {
            val doc = model.books.documents.get(d.documentId)
            val ext = doc.fileName?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() } ?: doc.mimeType.substringAfter('/').replace("jpeg", "jpg")
            var file = safe(d.name)
            var n = 2
            while (!used.add(file.lowercase())) file = safe(d.name) + " ($n)".also { n++ }
            java.io.File(docs, "$file.$ext").writeBytes(model.books.documents.content(d.documentId))
        }
    }
    runCatching { java.awt.Desktop.getDesktop().open(dir) }
    return dir
}
