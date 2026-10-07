package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ca.schippers.hfm.books.Itemized
import ca.schippers.hfm.books.Itemizer
import ca.schippers.hfm.books.ReadReceipt
import ca.schippers.hfm.books.SplitNote
import ca.schippers.hfm.books.TypedItem
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.util.Locale

/** DOC-02: the sales taxes an item can be marked with, as receipts and the AI split name them (RST counts as PST). */
internal val ITEM_TAXES = listOf("GST", "HST", "QST", "PST")

/** DOC-02: one item typed in the itemize table. */
internal class ItemRow(description: String = "", amount: String = "", categoryId: String? = null, taxes: Set<String> = emptySet()) {
    var description by mutableStateOf(description)
    var amount by mutableStateOf(amount)
    var categoryId by mutableStateOf(categoryId)
    var taxes by mutableStateOf(taxes)

    val blank: Boolean get() = description.isBlank() && amount.isBlank()
}

/**
 * DOC-02: what was typed in the itemize table, kept while the dialog is closed and opened again
 * (the transaction dialog cancelled, for instance): the items, and each tax as printed.
 */
internal class ItemizeState(val rows: SnapshotStateList<ItemRow>, val taxes: SnapshotStateMap<String, String>) {

    /** The items with something typed, or null when an amount cannot be read or an item has none. */
    fun items(currency: Currency, locale: Locale): List<TypedItem>? = rows.filterNot { it.blank }.map { row ->
        val amount = runCatching { parseAmount(row.amount, currency, locale) }.getOrNull() ?: return null
        TypedItem(row.description.trim(), amount.toBigDecimal(), row.categoryId, row.taxes)
    }

    /** Each tax typed, by code; null when one cannot be read. */
    fun taxAmounts(currency: Currency, locale: Locale): Map<String, BigDecimal>? = ITEM_TAXES.associateWith { code ->
        val text = taxes[code].orEmpty()
        runCatching { parseAmount(text, currency, locale) }.getOrElse { return null }?.abs()?.toBigDecimal() ?: BigDecimal.ZERO
    }

    companion object {
        /**
         * The table to start from: the items and taxes an AI reading found ([read]), else the taxes
         * read on this computer ([printed]) and two empty lines.
         */
        fun start(read: ReadReceipt?, printed: Map<String, Money>, locale: Locale, currency: Currency): ItemizeState {
            fun text(v: BigDecimal) = MoneyFormat.formatAmount(Money.of(v, currency), locale)
            val rows = mutableStateListOf<ItemRow>()
            read?.lines?.forEach { line ->
                val taxes = line.taxes.orEmpty().map { if (it == "RST") "PST" else it }.filter { it in ITEM_TAXES }.toSet()
                rows += ItemRow(line.description.takeIf { it != "?" }.orEmpty(), text(line.amount), null, taxes)
            }
            while (rows.size < 2) rows += ItemRow()
            val taxes = mutableStateMapOf<String, String>()
            val known = read?.taxes?.takeIf { it.isNotEmpty() }?.entries?.groupBy({ if (it.key == "RST") "PST" else it.key }, { it.value })?.mapValues { (_, v) -> v.fold(BigDecimal.ZERO, BigDecimal::add) }
                ?: printed.mapValues { it.value.abs().toBigDecimal() }
            for ((code, amount) in known) if (code in ITEM_TAXES && amount.signum() != 0) taxes[code] = text(amount)
            return ItemizeState(rows, taxes)
        }
    }
}

/**
 * DOC-02: a receipt or invoice itemized by hand, without AI: each item with its amount, category
 * and the sales taxes charged on it, the taxes as printed, and a running total compared with the
 * receipt's [total] (null when it is not known: the items and taxes are then the total). The taxes
 * are shared over the items as on a receipt split by AI; [onDone] gets the result, whose split
 * lines combine the items of each category.
 */
@Composable
internal fun ItemizeDialog(
    model: BooksModel,
    state: ItemizeState,
    currency: Currency,
    total: Money?,
    date: LocalDate,
    onDismiss: () -> Unit,
    onDone: (Itemized) -> Unit,
) {
    val locale = model.language.locale
    val tree = remember { model.books.categories.tree() }
    val taxes = state.taxAmounts(currency, locale)
    // The taxes typed: only those can be ticked on an item (a tick left on a tax since cleared counts for nothing).
    val charged = ITEM_TAXES.filter { code -> taxes?.get(code)?.signum()?.let { it != 0 } == true }
    val items = state.items(currency, locale)?.map { it.copy(taxes = it.taxes.intersect(charged.toSet())) }
    val typedSum = items?.let { list -> Money.of(list.fold(BigDecimal.ZERO) { a, i -> a + i.amount }, currency) + Money.of(taxes.orEmpty().values.fold(BigDecimal.ZERO, BigDecimal::add), currency) }
    val result = if (items != null && taxes != null) {
        runCatching { Itemizer.itemize(items, taxes, total?.abs() ?: typedSum ?: Money.zero(currency), date) }.getOrNull()
    } else {
        null
    }
    val shareOf = result?.let { r -> state.rows.filterNot { it.blank }.zip(r.shares).associate { (row, s) -> row to s.share } }.orEmpty()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 1000.dp).padding(24.dp).walkTarget("itemize.dialog"), shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(model.t("itemize.title"), style = MaterialTheme.typography.headlineSmall)
                Text(model.t("itemize.hint"), style = MaterialTheme.typography.bodySmall)
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (row in state.rows) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextInput(model.t("itemize.item"), row.description, Modifier.weight(1f)) { row.description = it }
                            AmountInput(model.t("register.amount"), row.amount, currency, locale, Modifier.width(120.dp), model::money) { row.amount = it }
                            Picker(
                                model.t("register.category"), listOf(null) + tree, tree.firstOrNull { it.first.id == row.categoryId },
                                { it?.first?.name(model.language) ?: model.t("itemize.sameCategory") }, Modifier.width(270.dp), indent = { it?.second ?: 0 },
                            ) { row.categoryId = it?.first?.id }
                            for (code in charged) {
                                FilterChip(code in row.taxes, { row.taxes = if (code in row.taxes) row.taxes - code else row.taxes + code }, { Text(model.t("taxName.$code")) }, leadingIcon = if (code in row.taxes) { { Text("✓") } } else null)
                            }
                            Text(shareOf[row]?.let(model::money).orEmpty(), Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            RemoveButton(model.t("itemize.remove"), enabled = state.rows.size > 1) { state.rows.remove(row) }
                        }
                    }
                }
                OutlinedButton(onClick = { state.rows += ItemRow() }, modifier = Modifier.walkTarget("itemize.add")) { Text(model.t("itemize.add")) }
                Text(model.t("itemize.taxes"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (code in ITEM_TAXES) {
                        AmountInput(model.t("taxName.$code"), state.taxes[code].orEmpty(), currency, locale, Modifier.width(140.dp), model::money) { state.taxes[code] = it }
                    }
                }
                if (charged.isNotEmpty()) Text(model.t("itemize.tickHint"), style = MaterialTheme.typography.bodySmall)
                // The running total, against the receipt's.
                if (result != null) {
                    Text(
                        model.t("itemize.sum", model.money(result.itemsTotal), model.money(result.taxesTotal), model.money(result.itemsTotal + result.taxesTotal)),
                        fontWeight = FontWeight.Medium,
                    )
                    when {
                        total == null -> Text(model.t("itemize.noTotal"), style = MaterialTheme.typography.bodySmall)
                        result.matches -> Text(model.t("itemize.matches", model.money(result.total)), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                        else -> Text(model.t("itemize.difference", model.money(result.difference), model.money(result.total)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (result.note != SplitNote.NONE) Text(model.t("itemize.note.${result.note}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                } else if (items == null || taxes == null) {
                    ErrorText(model.t("itemize.unreadable"))
                } else {
                    Text(model.t("itemize.empty"), style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text(model.t("common.cancel")) }
                    Button(enabled = result != null, onClick = { result?.let(onDone) }, modifier = Modifier.walkTarget("itemize.use")) { Text(model.t("itemize.use")) }
                }
            }
        }
    }
}
