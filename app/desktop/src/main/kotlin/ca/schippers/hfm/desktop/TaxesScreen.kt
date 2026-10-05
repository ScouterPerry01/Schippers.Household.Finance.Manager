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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.DocumentEntity
import ca.schippers.hfm.books.Donation
import ca.schippers.hfm.books.DonationReceipt
import ca.schippers.hfm.domain.TaxFlag
import ca.schippers.hfm.money.MoneyFormat

private enum class TaxesTab { DONATIONS }

/** Phase 5b: the tax year in one place, starting with donations and their official receipts (OTH-01). */
@Composable
fun TaxesScreen(model: BooksModel) {
    var tab by remember { mutableStateOf(TaxesTab.DONATIONS) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(model.t("nav.taxes"), style = MaterialTheme.typography.titleLarge)
        Text(model.t("taxes.hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        PrimaryTabRow(selectedTabIndex = tab.ordinal, modifier = Modifier.padding(vertical = 8.dp)) {
            for (t in TaxesTab.entries) Tab(selected = tab == t, onClick = { tab = t }, text = { Text(model.t("taxes.tab.$t")) })
        }
        when (tab) {
            TaxesTab.DONATIONS -> DonationsTab(model)
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

    Picker(model.t("taxes.year"), (thisYear downTo thisYear - 6).toList(), year, { it.toString() }, Modifier.width(140.dp)) { year = it }
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
    var eligible by remember { mutableStateOf(d.receipt?.eligible?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var received by remember { mutableStateOf(d.receipt?.received ?: (d.documents > 0)) }
    FormDialog(model.t("taxes.receiptTitle"), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            model.books.donations.setReceipt(
                d.transactionId,
                DonationReceipt(charity, registration, number, parseAmount(eligible, d.amount.currency, locale)?.abs(), received),
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
