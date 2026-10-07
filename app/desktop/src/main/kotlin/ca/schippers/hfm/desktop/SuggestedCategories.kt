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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Account
import ca.schippers.hfm.books.Category
import ca.schippers.hfm.books.Transaction

/**
 * CAT-03: the categories a statement import filled in from each payee's habits, reviewed in one
 * pass: Keep accepts one, Keep all accepts every one, Change opens the transaction in the form.
 */
@Composable
internal fun SuggestedCategoriesDialog(model: BooksModel, account: Account, categories: Map<String, Category>, onOpen: (Transaction) -> Unit, onClose: () -> Unit) {
    val books = model.books
    val rows = remember(model.revision, account.id) { books.transactions.suggestedCategories(account.id) }
    val payees = remember(model.revision) { books.payees.list(true).associate { it.id to it.name } }
    WideDialog(model.t("suggested.title"), model.t("common.close"), onClose) {
        Column(Modifier.width(760.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(model.t("suggested.explain"), style = MaterialTheme.typography.bodySmall)
            if (rows.isEmpty()) Text(model.t("suggested.none"), Modifier.padding(vertical = 8.dp))
            else HeadingRow {
                ColumnHeading(model.t("register.date"), Modifier.width(100.dp))
                ColumnHeading(model.t("register.payee"), Modifier.weight(1f))
                ColumnHeading(model.t("register.category"), Modifier.weight(1f))
                ColumnHeading(model.t("register.amount"), Modifier.width(110.dp), TextAlign.End)
                ColumnHeading(model.t("table.actions"), Modifier.width(SUGGESTED_ACTIONS_WIDTH))
            }
            for (t in rows) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(model.date(t.date), Modifier.width(100.dp))
                    Text(t.payeeId?.let(payees::get) ?: t.payeeText.orEmpty(), Modifier.weight(1f))
                    Text(t.splits.firstOrNull()?.categoryId?.let { categories[it]?.name(model.language) } ?: model.t("register.uncategorized"), Modifier.weight(1f))
                    MoneyText(model, t.amount, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                    Row(Modifier.width(SUGGESTED_ACTIONS_WIDTH)) {
                        TextButton(onClick = { model.act { books.transactions.acceptSuggestedCategory(t.id) } }) { Text(model.t("suggested.keep")) }
                        TextButton(onClick = { onOpen(t) }) { Text(model.t("suggested.change")) }
                    }
                }
                HorizontalDivider()
            }
            if (rows.isNotEmpty()) {
                Button(onClick = { if (model.act { books.transactions.acceptSuggestedCategories(account.id) } != null) onClose() }, Modifier.padding(top = 8.dp)) {
                    Text(model.t("suggested.keepAll", rows.size))
                }
            }
        }
    }
}

/** Keep and Change, in both languages. */
private val SUGGESTED_ACTIONS_WIDTH = 190.dp
