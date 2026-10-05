package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.DeductionKind
import ca.schippers.hfm.books.Member
import ca.schippers.hfm.books.PayDeduction
import ca.schippers.hfm.books.PayEarning
import ca.schippers.hfm.books.PayStub
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.datetime.LocalDate

private data class EarningRow(val description: String, val amount: String)
private data class DeductionRow(val kind: DeductionKind, val description: String, val amount: String)

/**
 * SAL-02: a pay stub entered as one deposit of the net pay, split into gross pay and deductions.
 * [read] fills it from an AI reading; [documentId] is the stub, filed with the deposit. Without
 * [accountId] the user picks the account the pay went into.
 */
@Composable
fun PayStubDialog(model: BooksModel, accountId: String?, read: PayStub? = null, documentId: String? = null, onClose: (Boolean) -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val accounts = remember { books.accounts.list().map { it.account }.filter { it.type.kind == AccountKind.BANK } }
    val members = remember { books.members.list() }
    var account by remember { mutableStateOf(accounts.firstOrNull { it.id == accountId } ?: accounts.firstOrNull()) }
    var member by remember { mutableStateOf<Member?>(null) }
    var employer by remember { mutableStateOf(read?.employer.orEmpty()) }
    var date by remember { mutableStateOf((read?.payDate ?: today()).toString()) }
    fun text(m: Money) = MoneyFormat.formatAmount(m, locale)
    val earnings = remember {
        mutableStateListOf<EarningRow>().apply {
            read?.earnings?.forEach { add(EarningRow(it.description, text(it.amount))) }
            if (isEmpty()) add(EarningRow("", ""))
        }
    }
    val deductions = remember {
        mutableStateListOf<DeductionRow>().apply {
            read?.deductions?.forEach { add(DeductionRow(it.kind, it.description.orEmpty(), text(it.amount))) }
            if (isEmpty()) listOf(DeductionKind.INCOME_TAX, DeductionKind.CPP_QPP, DeductionKind.EI_QPIP).forEach { add(DeductionRow(it, "", "")) }
        }
    }
    val currency = read?.gross?.currency ?: account?.currency ?: ca.schippers.hfm.money.Currency.CAD
    fun amount(s: String) = runCatching { parseAmount(s, currency, locale) }.getOrNull()

    /** The stub as entered; amounts left empty are skipped. */
    fun stub() = PayStub(
        employer,
        runCatching { LocalDate.parse(date.trim()) }.getOrElse { throw ValidationException("error.invalidDate") },
        earnings.mapNotNull { r -> amount(r.amount)?.let { PayEarning(r.description.trim(), it) } },
        deductions.mapNotNull { r -> amount(r.amount)?.abs()?.let { PayDeduction(r.kind, r.description.trim().ifEmpty { null }, it) } },
        read?.printedNet,
        read?.periodStart,
        read?.periodEnd,
    )
    val preview = runCatching { stub().takeIf { it.earnings.isNotEmpty() } }.getOrNull()

    FormDialog(model.t("payStub.title"), model.t("common.save"), model.t("common.cancel"), canSave = account != null && preview != null, onDismiss = { onClose(false) }, onSave = {
        val ok = model.act { books.payStubs.record(account!!.id, stub(), member?.id, documentId) }
        if (ok != null) onClose(true)
    }) {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(model.t("payStub.hint"), style = MaterialTheme.typography.bodySmall)
            TextInput(model.t("payStub.employer"), employer) { employer = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(model.t("payStub.payDate"), date, Modifier.weight(1f)) { date = it }
                Picker(model.t("register.for"), listOf(null) + members, member, { it?.displayName ?: model.t("register.forNobody") }, Modifier.weight(1f)) { member = it }
            }
            if (accountId == null) Picker(model.t("payStub.account"), accounts, account, { it.name }) { account = it }

            Text(model.t("payStub.earnings"), fontWeight = FontWeight.Medium)
            earnings.forEachIndexed { i, r ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextInput(model.t("payStub.description"), r.description, Modifier.weight(1f)) { earnings[i] = r.copy(description = it) }
                    AmountInput(model.t("payStub.amount"), r.amount, currency, locale, Modifier.width(150.dp), model::money) { earnings[i] = r.copy(amount = it) }
                    TextButton(onClick = { earnings.removeAt(i) }, enabled = earnings.size > 1) { Text("✕") }
                }
            }
            TextButton(onClick = { earnings.add(EarningRow("", "")) }) { Text(model.t("payStub.addEarning")) }

            Text(model.t("payStub.deductions"), fontWeight = FontWeight.Medium)
            deductions.forEachIndexed { i, r ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Picker(model.t("payStub.kind"), DeductionKind.entries, r.kind, { model.t("deduction.$it") }, Modifier.width(170.dp)) { deductions[i] = r.copy(kind = it) }
                    TextInput(model.t("payStub.description"), r.description, Modifier.weight(1f)) { deductions[i] = r.copy(description = it) }
                    AmountInput(model.t("payStub.amount"), r.amount, currency, locale, Modifier.width(110.dp), model::money) { deductions[i] = r.copy(amount = it) }
                    TextButton(onClick = { deductions.removeAt(i) }) { Text("✕") }
                }
            }
            TextButton(onClick = { deductions.add(DeductionRow(DeductionKind.OTHER, "", "")) }) { Text(model.t("payStub.addDeduction")) }
        }
        // Always in view below the lines: what the deposit will be.
        if (preview != null) {
            Text(model.t("payStub.totals", model.money(preview.gross), model.money(preview.gross - preview.net), model.money(preview.net)), fontWeight = FontWeight.Medium)
            if (preview.netDisagrees) Text(model.t("payStub.netDisagrees", model.money(preview.printedNet!!)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
