package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Account
import ca.schippers.hfm.books.BenefitKind
import ca.schippers.hfm.books.CardBenefit
import ca.schippers.hfm.books.CardHolder
import ca.schippers.hfm.books.Coverage
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.datetime.LocalDate

/** "Sam ····1234". */
internal fun cardLabel(h: CardHolder): String = h.name + (h.lastDigits?.let { " ····$it" }.orEmpty())

/** "Purchase protection until 2026-04-10", or what an extended warranty adds. */
internal fun coverageText(model: BooksModel, c: Coverage): String = when {
    c.until != null -> model.t("cards.coveredUntil", model.t("benefit.${c.benefit.kind}"), model.date(c.until!!))
    c.benefit.kind == BenefitKind.EXTENDED_WARRANTY -> model.t("cards.warrantyAdds", c.benefit.months ?: 0)
    else -> model.t("benefit.${c.benefit.kind}")
}

/**
 * CC-04 and CC-05: the cards on the account (the main cardholder and supplementary cards), what
 * each spent this year, the card's benefits, and purchases they still protect.
 */
@Composable
internal fun CardsDialog(model: BooksModel, account: Account, onClose: () -> Unit) {
    val books = model.books
    val today = today()
    val holders = remember(model.revision) { books.creditCards.holders(account.id) }
    val benefits = remember(model.revision) { books.creditCards.benefits(account.id) }
    val spending = remember(model.revision) { books.creditCards.spendingByHolder(account.id, LocalDate(today.year, 1, 1), today) }
    val protected = remember(model.revision) { books.creditCards.protectedPurchases(account.id, today) }
    val payees = remember(model.revision) { books.payees.list(true).associate { it.id to it.name } }
    var holder by remember { mutableStateOf<CardHolder?>(null) }
    var benefit by remember { mutableStateOf<CardBenefit?>(null) }

    WideDialog(model.t("cards.title", account.name), model.t("common.close"), onClose) {
        Column(Modifier.width(760.dp).heightIn(max = 600.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(model.t("cards.holders"), style = MaterialTheme.typography.titleSmall)
            Text(model.t("cards.holdersHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            for (h in holders) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(cardLabel(h) + if (h.isPrimary) " · " + model.t("cards.main") else " · " + model.t("cards.supplementary"), Modifier.weight(1f))
                    TextButton(onClick = { holder = h }) { Text(model.t("common.edit")) }
                    TextButton(onClick = { model.act { books.creditCards.deleteHolder(account.id, h.id) } }) { Text(model.t("common.delete")) }
                }
            }
            OutlinedButton(onClick = { holder = CardHolder("", account.id, "") }) { Text(model.t("cards.addHolder")) }

            if (spending.isNotEmpty() && holders.size > 1) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(model.t("cards.spending", today.year.toString()), style = MaterialTheme.typography.titleSmall)
                for (s in spending) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(s.holder?.let(::cardLabel) ?: model.t("cards.mainCard"), Modifier.weight(1f))
                        MoneyText(model, s.spent)
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text(model.t("cards.benefits"), style = MaterialTheme.typography.titleSmall)
            Text(model.t("cards.benefitsHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            for (b in benefits) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(model.t("benefit.${b.kind}") + (b.description?.let { " · $it" }.orEmpty()))
                        Text(benefitDetails(model, b), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    TextButton(onClick = { benefit = b }) { Text(model.t("common.edit")) }
                    TextButton(onClick = { model.act { books.creditCards.deleteBenefit(account.id, b.id) } }) { Text(model.t("common.delete")) }
                }
            }
            OutlinedButton(onClick = { benefit = CardBenefit("", account.id, BenefitKind.PURCHASE_PROTECTION) }) { Text(model.t("cards.addBenefit")) }

            if (protected.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(model.t("cards.protected"), style = MaterialTheme.typography.titleSmall)
                for ((t, coverage) in protected) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(model.date(t.date), Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall)
                        Text(t.payeeId?.let(payees::get) ?: t.payeeText.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(coverage.joinToString(" · ") { coverageText(model, it) }, Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall)
                        MoneyText(model, t.amount)
                    }
                }
            }
        }
    }
    holder?.let { h -> HolderDialog(model, h) { holder = null } }
    benefit?.let { b -> BenefitDialog(model, account, b) { benefit = null } }
}

private fun benefitDetails(model: BooksModel, b: CardBenefit): String = listOfNotNull(
    b.days?.let { model.t("cards.days", it) },
    b.months?.let { model.t("cards.months", it) },
    b.maxYears?.let { model.t("cards.maxYears", it) },
    b.limit?.let { model.t("cards.limitOf", model.money(it)) },
    b.notes,
).joinToString(" · ")

@Composable
private fun HolderDialog(model: BooksModel, existing: CardHolder, onClose: () -> Unit) {
    val people = remember { model.books.members.list() }
    var name by remember { mutableStateOf(existing.name) }
    var memberId by remember { mutableStateOf(existing.memberId) }
    var digits by remember { mutableStateOf(existing.lastDigits.orEmpty()) }
    var primary by remember { mutableStateOf(existing.isPrimary) }
    FormDialog(
        model.t(if (existing.id.isBlank()) "cards.addHolder" else "cards.editHolder"), model.t("common.save"), model.t("common.cancel"),
        canSave = name.isNotBlank(), onDismiss = onClose,
        onSave = {
            if (model.act { model.books.creditCards.saveHolder(existing.copy(name = name, memberId = memberId, lastDigits = digits, isPrimary = primary)) } != null) onClose()
        },
    ) {
        Picker(model.t("cards.person"), listOf(null) + people, people.firstOrNull { it.id == memberId }, { it?.displayName ?: model.t("common.none") }) { m ->
            memberId = m?.id
            if (m != null && name.isBlank()) name = m.displayName
        }
        TextInput(model.t("cards.holderName"), name) { name = it }
        TextInput(model.t("cards.lastDigits"), digits, supporting = model.t("cards.lastDigitsHint")) { digits = it }
        LabeledCheckbox(model.t("cards.isMain"), primary) { primary = it }
    }
}

@Composable
private fun BenefitDialog(model: BooksModel, account: Account, existing: CardBenefit, onClose: () -> Unit) {
    val locale = model.language.locale
    var kind by remember { mutableStateOf(existing.kind) }
    var description by remember { mutableStateOf(existing.description.orEmpty()) }
    var days by remember { mutableStateOf(existing.days?.toString().orEmpty()) }
    var months by remember { mutableStateOf(existing.months?.toString().orEmpty()) }
    var maxYears by remember { mutableStateOf(existing.maxYears?.toString().orEmpty()) }
    var limit by remember { mutableStateOf(existing.limit?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var notes by remember { mutableStateOf(existing.notes.orEmpty()) }
    fun int(t: String) = t.trim().ifEmpty { null }?.toIntOrNull()
    FormDialog(
        model.t(if (existing.id.isBlank()) "cards.addBenefit" else "cards.editBenefit"), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose,
        onSave = {
            val ok = model.act {
                model.books.creditCards.saveBenefit(
                    existing.copy(
                        kind = kind, description = description, days = int(days), months = int(months), maxYears = int(maxYears),
                        limit = parseAmount(limit, account.currency, locale), notes = notes,
                    ),
                )
            }
            if (ok != null) onClose()
        },
    ) {
        Picker(model.t("cards.benefitKind"), BenefitKind.entries, kind, { model.t("benefit.$it") }) { kind = it }
        TextInput(model.t("cards.description"), description) { description = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("cards.daysLabel"), days, Modifier.weight(1f)) { days = it }
            TextInput(model.t("cards.monthsLabel"), months, Modifier.weight(1f)) { months = it }
            TextInput(model.t("cards.maxYearsLabel"), maxYears, Modifier.weight(1f)) { maxYears = it }
        }
        AmountInput(model.t("cards.limit"), limit, account.currency, locale, Modifier.fillMaxWidth(), model::money) { limit = it }
        TextInput(model.t("account.notes"), notes, singleLine = false) { notes = it }
        Text(model.t("cards.benefitHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}
