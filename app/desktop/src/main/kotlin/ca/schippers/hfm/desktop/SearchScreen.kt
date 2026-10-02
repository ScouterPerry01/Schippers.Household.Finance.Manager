package ca.schippers.hfm.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** OTH-03: one search box for everything; Enter searches, Ctrl+F comes here. */
@Composable
fun SearchBox(state: AppState, model: BooksModel) {
    var text by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun run() {
        val query = text.trim()
        if (query.length < 2) return
        scope.launch {
            val results = withContext(Dispatchers.IO) { model.books.search.search(query, model.language.locale) }
            model.search = query to results
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(state.t("search.placeholder")) },
        singleLine = true,
        modifier = Modifier.width(320.dp).focusRequester(state.searchFocus).onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                run()
                true
            } else {
                false
            }
        },
    )
}

/** The results, grouped by kind; choosing one opens it where it lives. */
@Composable
fun SearchResultsDialog(model: BooksModel) {
    val (query, results) = model.search ?: return
    fun close() {
        model.search = null
    }
    fun open(section: Section) {
        model.reconcilingStatementId = null
        model.section = section
        close()
    }
    WideDialog(model.t("search.title", query), model.t("common.close"), ::close) {
            Column(Modifier.width(820.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (results.isEmpty) Text(model.t("search.none"))
                if (results.transactions.isNotEmpty()) {
                    Group(model.t("search.transactions", results.transactions.size))
                    for (hit in results.transactions) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                model.selectedAccountId = hit.account.id
                                model.focusTransactionId = hit.transaction.id
                                open(Section.ACCOUNTS)
                            }.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(model.date(hit.transaction.date), Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall)
                            Text(hit.account.name, Modifier.width(160.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(hit.payeeName.orEmpty(), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(hit.transaction.memo.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            MoneyText(model, hit.transaction.amount, modifier = Modifier.width(120.dp), textAlign = TextAlign.End)
                        }
                    }
                }
                Simple(model.t("search.accounts"), results.accounts.map { it.name }) { i ->
                    model.selectedAccountId = results.accounts[i].id
                    open(Section.ACCOUNTS)
                }
                Simple(model.t("search.payees"), results.payees.map { it.name }) { open(Section.PAYEES) }
                Simple(model.t("search.categories"), results.categories.map { it.name(model.language) }) { open(Section.CATEGORIES) }
                Simple(model.t("search.bills"), results.bills.map { it.name }) { open(Section.BILLS) }
                Simple(model.t("search.institutions"), results.institutions.map { it.name }) { open(Section.INSTITUTIONS) }
            }
    }
}

@Composable
private fun Group(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Simple(title: String, names: List<String>, onClick: (Int) -> Unit) {
    if (names.isEmpty()) return
    Group("$title (${names.size})")
    names.forEachIndexed { i, name ->
        Text(name, Modifier.fillMaxWidth().clickable { onClick(i) }.padding(vertical = 3.dp))
    }
}

/** SEC-02 and related security settings for this computer. */
@Composable
fun SecurityScreen(model: BooksModel, state: AppState) {
    val choices = listOf(1, 5, 10, 15, 30, 60, 0)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(model.t("nav.security"), style = MaterialTheme.typography.titleLarge)
        Text(model.t("security.autoLock.explain"), style = MaterialTheme.typography.bodySmall)
        Picker(model.t("security.autoLock"), choices, state.autoLockMinutes, { if (it == 0) model.t("security.never") else model.t("security.minutes", it) }, Modifier.width(260.dp)) {
            state.setAutoLock(it)
        }
        Text(model.t("security.recoveryReminder"), style = MaterialTheme.typography.bodySmall)
    }
}
