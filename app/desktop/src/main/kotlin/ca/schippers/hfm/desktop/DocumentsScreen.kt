package ca.schippers.hfm.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.DocumentDetails
import ca.schippers.hfm.books.DocumentEntity
import ca.schippers.hfm.books.DocumentQuery
import ca.schippers.hfm.books.DocumentStatus
import ca.schippers.hfm.books.TransactionDraft
import ca.schippers.hfm.books.SplitDraft
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.books.VaultDocument
import ca.schippers.hfm.money.MoneyFormat
import ca.schippers.hfm.ocr.DocumentKind
import ca.schippers.hfm.ocr.Extracted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private enum class DocumentsTab { INBOX, ALL, RETENTION }

/** Section 4.4, CAP-03, CAP-04, SYNC-05: the document vault and the review inbox. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DocumentsScreen(model: BooksModel) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(DocumentsTab.INBOX) }
    var reviewing by remember { mutableStateOf<VaultDocument?>(null) }
    var busy by remember { mutableStateOf(false) }
    var dragOver by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    fun import(files: List<Path>) {
        val group = model.defaultDocumentGroup() ?: run { model.error = model.t("error.noEditableGroup"); return }
        if (files.isEmpty() || busy) return
        busy = true
        scope.launch {
            val summary = runCatching { importFiles(model, files, group) }.getOrElse { ImportSummary(0, 0, files.map { it.fileName.toString() }) }
            model.lastImportMessage = model.importMessage(summary)
            busy = false
            tab = DocumentsTab.INBOX
            model.changed()
        }
    }

    // CAP-04: files dropped anywhere on the screen are imported.
    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { dragOver = true }
            override fun onExited(event: DragAndDropEvent) { dragOver = false }
            override fun onEnded(event: DragAndDropEvent) { dragOver = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragOver = false
                @Suppress("UNCHECKED_CAST")
                val files = runCatching { event.awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File> }.getOrNull() ?: return false
                import(files.map { it.toPath() })
                return true
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(12.dp)
            .dragAndDropTarget(shouldStartDragAndDrop = { it.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) }, target = dropTarget)
            .then(if (dragOver) Modifier.border(3.dp, MaterialTheme.colorScheme.primary) else Modifier),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(model.t("nav.documents"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { showSettings = true }) { Text(model.t("documents.watchFolder")) }
            Button(enabled = !busy, onClick = { import(chooseFiles(model)) }) { Text(model.t(if (busy) "documents.reading" else "documents.import")) }
        }
        Text(model.t("documents.dropHint"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        model.lastImportMessage?.let { Text(it, modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.primary) }
        val inboxCount = remember(model.revision) { model.books.documents.inboxCount() }
        PrimaryTabRow(selectedTabIndex = tab.ordinal, modifier = Modifier.padding(vertical = 8.dp)) {
            for (t in DocumentsTab.entries) {
                Tab(selected = tab == t, onClick = { tab = t }, text = { Text(model.t("documents.tab.${t.name}", inboxCount)) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                DocumentsTab.INBOX -> InboxTab(model) { reviewing = it }
                DocumentsTab.ALL -> AllTab(model) { reviewing = it }
                DocumentsTab.RETENTION -> RetentionTab(model) { reviewing = it }
            }
        }
    }

    reviewing?.let { doc -> ReviewDialog(model, doc.id) { reviewing = null } }
    if (showSettings) WatchFolderDialog(model) { showSettings = false }
}

private fun chooseFiles(model: BooksModel): List<Path> {
    val chooser = JFileChooser().apply {
        dialogTitle = model.t("documents.import")
        isMultiSelectionEnabled = true
        fileFilter = FileNameExtensionFilter(model.t("documents.fileTypes"), *IMPORTABLE_EXTENSIONS.toTypedArray())
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFiles.map { it.toPath() } else emptyList()
}

// --- Lists ----------------------------------------------------------------------------------------

@Composable
private fun InboxTab(model: BooksModel, onOpen: (VaultDocument) -> Unit) {
    val docs = remember(model.revision) { model.books.documents.inbox() }
    LazyColumn {
        if (docs.isEmpty()) item { Text(model.t("documents.inboxEmpty"), Modifier.padding(8.dp)) }
        items(docs, key = { it.id }) { doc -> DocumentRow(model, doc, highlight = true) { onOpen(doc) } }
    }
}

@Composable
private fun AllTab(model: BooksModel, onOpen: (VaultDocument) -> Unit) {
    val locale = model.language.locale
    var text by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var min by remember { mutableStateOf("") }
    var max by remember { mutableStateOf("") }
    val base = model.books.rates.baseCurrency
    fun date(s: String) = runCatching { LocalDate.parse(s.trim()) }.getOrNull()
    val query = DocumentQuery(text.ifBlank { null }, date(from), date(to), runCatching { parseAmount(min, base, locale) }.getOrNull(), runCatching { parseAmount(max, base, locale) }.getOrNull())
    val docs = remember(model.revision, query) { model.books.documents.search(query) }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("documents.searchText"), text, Modifier.weight(2f)) { text = it }
            DateInput(model.t("documents.from"), from, Modifier.weight(1f)) { from = it }
            DateInput(model.t("documents.to"), to, Modifier.weight(1f)) { to = it }
            TextInput(model.t("documents.minAmount"), min, Modifier.weight(0.8f)) { min = it }
            TextInput(model.t("documents.maxAmount"), max, Modifier.weight(0.8f)) { max = it }
        }
        LazyColumn(Modifier.padding(top = 8.dp)) {
            if (docs.isEmpty()) item { Text(model.t("documents.none"), Modifier.padding(8.dp)) }
            items(docs, key = { it.id }) { doc -> DocumentRow(model, doc, highlight = false) { onOpen(doc) } }
        }
    }
}

@Composable
private fun RetentionTab(model: BooksModel, onOpen: (VaultDocument) -> Unit) {
    val docs = remember(model.revision) { model.books.documents.discardable(today()) }
    Column {
        Text(model.t("documents.retentionExplain", RETENTION_YEARS), style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.padding(top = 8.dp)) {
            if (docs.isEmpty()) item { Text(model.t("documents.nothingToDiscard"), Modifier.padding(8.dp)) }
            items(docs, key = { it.id }) { doc -> DocumentRow(model, doc, highlight = false) { onOpen(doc) } }
        }
    }
}

@Composable
private fun DocumentRow(model: BooksModel, doc: VaultDocument, highlight: Boolean, onOpen: () -> Unit) {
    val duplicates = remember(model.revision, doc.id) { if (highlight) model.books.documents.duplicates(doc.id) else emptyList() }
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text((doc.date ?: dateOfMillis(doc.capturedAt)).let(model::date), Modifier.width(110.dp))
            Column(Modifier.weight(1f)) {
                Text(doc.label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        doc.kind?.let { model.t("documentKind.$it") },
                        doc.fileName?.takeIf { it != doc.label },
                        model.t("documents.pages", doc.pages).takeIf { doc.pages > 1 },
                        doc.links.size.takeIf { it > 0 }?.let { model.t("documents.linked", it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (duplicates.isNotEmpty()) Text(model.t("documents.possibleDuplicate"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text(doc.amount?.let(model::money).orEmpty(), Modifier.width(120.dp), fontWeight = FontWeight.Bold)
            Button(onClick = onOpen) { Text(model.t(if (highlight) "documents.review" else "documents.open")) }
        }
    }
}

// --- Review (SYNC-05, OCR-05, BILL-03) --------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewDialog(model: BooksModel, documentId: String, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val doc = remember(model.revision, documentId) { runCatching { books.documents.get(documentId) }.getOrNull() }
    if (doc == null) {
        onClose()
        return
    }
    var preview by remember(documentId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(documentId) {
        preview = withContext(Dispatchers.IO) { runCatching { DesktopOcr.reader.preview(books.documents.content(documentId))?.toComposeImageBitmap() }.getOrNull() }
    }
    val currency = doc.amount?.currency ?: books.rates.baseCurrency
    var title by remember(documentId) { mutableStateOf(doc.title ?: doc.merchant.orEmpty()) }
    var date by remember(documentId) { mutableStateOf((doc.date ?: dateOfMillis(doc.capturedAt)).toString()) }
    var amount by remember(documentId) { mutableStateOf(doc.amount?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var kind by remember(documentId) { mutableStateOf(doc.kind ?: DocumentKind.OTHER) }
    var keep by remember(documentId) { mutableStateOf(doc.keepForever) }
    var notes by remember(documentId) { mutableStateOf(doc.notes.orEmpty()) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    /** Saves what the user corrected before any filing action. */
    fun saveDetails(): Boolean = model.act {
        val d = runCatching { LocalDate.parse(date.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }
        books.documents.update(documentId, DocumentDetails(title, kind, d, title.ifBlank { null }, parseAmount(amount, currency, locale), keep, notes))
    } != null

    WideDialog(doc.label, model.t("common.close"), onClose) {
        Row(Modifier.heightIn(max = 620.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // The document itself.
            Box(Modifier.width(360.dp).heightIn(min = 300.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant).verticalScroll(rememberScrollState())) {
                val image = preview
                if (image != null) {
                    Image(image, doc.label, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                } else {
                    Text(model.t("documents.loadingPreview"), Modifier.padding(16.dp))
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val draft = doc.draft
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewedField(model, draft?.merchant) { TextInput(model.t("documents.merchant"), title, it) { v -> title = v } }
                    Picker(model.t("documents.kind"), DocumentKind.entries, kind, { model.t("documentKind.$it") }, Modifier.width(170.dp)) { kind = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewedField(model, draft?.date) { DateInput(model.t("report.date"), date, it) { v -> date = v } }
                    ReviewedField(model, draft?.total) { AmountInput(model.t("documents.total"), amount, currency, locale, it, model::money) { v -> amount = v } }
                }
                ExtractedDetails(model, doc)
                Duplicates(model, doc)
                LabeledCheckbox(model.t("documents.keepForever"), keep) { keep = it }
                TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                FilingActions(model, doc, ::saveDetails, onCreate = { creating = true }, onDone = onClose)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (doc.status == DocumentStatus.INBOX) {
                        OutlinedButton(onClick = { if (saveDetails() && model.act { books.documents.setStatus(documentId, DocumentStatus.FILED) } != null) onClose() }) {
                            Text(model.t("documents.fileOnly"))
                        }
                    } else {
                        OutlinedButton(onClick = { if (saveDetails()) onClose() }) { Text(model.t("common.save")) }
                    }
                    TextButton(onClick = { saveCopy(model, doc) }) { Text(model.t("documents.saveCopy")) }
                    TextButton(onClick = { confirmDelete = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
                }
                doc.text?.takeIf { it.isNotBlank() }?.let {
                    Text(model.t("documents.recognisedText"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    Text(it.take(4000), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (creating) NewTransactionDialog(model, doc, title, date, amount) { done -> creating = false; if (done) onClose() }
    if (confirmDelete) {
        FormDialog(model.t("documents.delete.title"), model.t("common.delete"), model.t("common.cancel"), onDismiss = { confirmDelete = false }, onSave = {
            if (model.act { books.documents.delete(documentId) } != null) {
                confirmDelete = false
                onClose()
            }
        }) { Text(model.t("documents.delete.body", doc.label)) }
    }
}

/** OCR-05: a field read with low confidence is outlined and marked "check". */
@Composable
private fun <T> androidx.compose.foundation.layout.RowScope.ReviewedField(model: BooksModel, extracted: Extracted<T>?, content: @Composable (Modifier) -> Unit) {
    Column(Modifier.weight(1f)) {
        content(Modifier.fillMaxWidth())
        if (extracted?.needsReview == true) Text(model.t("documents.check"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun ExtractedDetails(model: BooksModel, doc: VaultDocument) {
    val draft = doc.draft ?: return
    val parts = listOfNotNull(
        draft.subtotal?.let { model.t("documents.subtotal", model.money(it.value)) },
        draft.taxes.takeIf { it.isNotEmpty() }?.joinToString(", ") { (name, v) -> "${model.t("taxName.$name")} ${model.money(v.value)}" },
        draft.paymentMethod?.value,
        draft.cardLast4?.let { model.t("documents.card", it.value) },
        draft.invoiceNumber?.let { model.t("documents.invoiceNo", it.value) },
        draft.dueDate?.let { model.t("documents.dueOn", model.date(it.value)) },
        draft.accountNumber?.let { model.t("documents.accountNo", it.value) },
    )
    if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Duplicates(model: BooksModel, doc: VaultDocument) {
    val dups = remember(model.revision, doc.id) { model.books.documents.duplicates(doc.id) }
    for (d in dups) {
        Text(
            model.t("documents.duplicateOf", d.document.label, model.date(d.document.date ?: dateOfMillis(d.document.capturedAt))),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Matching transactions, the bill it belongs to, or a new transaction (SYNC-05, BILL-03). */
@Composable
private fun FilingActions(model: BooksModel, doc: VaultDocument, saveDetails: () -> Boolean, onCreate: () -> Unit, onDone: () -> Unit) {
    val books = model.books
    val matches = remember(model.revision, doc.id) { books.documents.matches(doc.id) }
    val bill = remember(model.revision, doc.id) { if (doc.kind == DocumentKind.BILL || doc.kind == DocumentKind.INVOICE) books.documents.billFor(doc.id) else null }
    val linked = remember(model.revision, doc.id) { linkedDescriptions(model, doc) }
    if (linked.isNotEmpty()) {
        Text(model.t("documents.attachedTo"), style = MaterialTheme.typography.labelLarge)
        for (l in linked) Text(l, style = MaterialTheme.typography.bodySmall)
    }
    Text(model.t("documents.fileIt"), style = MaterialTheme.typography.labelLarge)
    if (bill != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.t("documents.billSuggestion", bill.name), Modifier.weight(1f))
            Button(onClick = {
                if (saveDetails()) model.act { books.documents.fileWithBill(doc.id, bill.id) }?.let { onDone() }
            }) { Text(model.t("documents.recordOnBill")) }
        }
    }
    for (m in matches.take(4)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${model.date(m.transaction.date)} · ${m.accountName} · ${m.transaction.payeeText ?: ""} ${model.money(m.transaction.amount)}",
                Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            OutlinedButton(onClick = { if (saveDetails() && model.act { books.documents.fileWithTransaction(doc.id, m.transaction.id) } != null) onDone() }) {
                Text(model.t("documents.attach"))
            }
        }
    }
    if (matches.isEmpty() && bill == null) Text(model.t("documents.noMatch"), style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { if (saveDetails()) onCreate() }) { Text(model.t("documents.newTransaction")) }
}

private fun linkedDescriptions(model: BooksModel, doc: VaultDocument): List<String> = doc.links.mapNotNull { link ->
    val books = model.books
    runCatching {
        when (link.entity) {
            DocumentEntity.TRANSACTION -> books.transactions.get(link.entityId).let { "${model.date(it.date)} · ${it.payeeText.orEmpty()} ${model.money(it.amount)}" }
            DocumentEntity.BILL -> books.bills.list(includeInactive = true).first { it.id == link.entityId }.name
            else -> null
        }
    }.getOrNull()
}

/** Creates the transaction the receipt describes, with the receipt attached. */
@Composable
private fun NewTransactionDialog(model: BooksModel, doc: VaultDocument, payee: String, date: String, amount: String, onClose: (Boolean) -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val currency = doc.amount?.currency ?: books.rates.baseCurrency
    val accounts = remember { books.accounts.list().map { it.account }.filter { it.currency == currency } }
    val tree = remember { books.categories.tree() }
    val people = remember { model.peopleAndPets() }
    val vehicles = remember { model.vehicleChoices() }
    val last4 = doc.draft?.cardLast4?.value
    // The card whose last digits are on the receipt, else a credit card, else the first account.
    var accountId by remember {
        mutableStateOf(
            (accounts.firstOrNull { last4 != null && it.numberMasked?.endsWith(last4) == true }
                ?: accounts.firstOrNull { it.type == ca.schippers.hfm.domain.AccountType.CREDIT_CARD } ?: accounts.firstOrNull())?.id,
        )
    }
    val payeeDefault = remember { books.payees.list().firstOrNull { ca.schippers.hfm.books.DocumentService.similarNames(it.name, payee) } }
    var categoryId by remember { mutableStateOf(payeeDefault?.defaultCategoryId) }
    var forId by remember { mutableStateOf<String?>(null) }
    var assetId by remember { mutableStateOf<String?>(null) }
    val account = accounts.firstOrNull { it.id == accountId }
    FormDialog(model.t("documents.newTransaction"), model.t("common.save"), model.t("common.cancel"), canSave = account != null, onDismiss = { onClose(false) }, onSave = {
        val ok = model.act {
            val value = parseAmount(amount, account!!.currency, locale) ?: throw ValidationException("error.amountRequired")
            val d = runCatching { LocalDate.parse(date.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }
            // A receipt is money out; a refund slip would be entered from the register.
            books.documents.fileAsTransaction(
                doc.id,
                TransactionDraft(
                    account.id, d, -value.abs(), (payeeDefault?.name ?: payee).ifBlank { null },
                    listOf(SplitDraft(categoryId, -value.abs())), memberId = forId, assetId = assetId,
                ),
            )
        }
        if (ok != null) onClose(true)
    }) {
        Text(listOf(payee, date, amount).filter { it.isNotBlank() }.joinToString(" · "), fontWeight = FontWeight.Medium)
        Picker(model.t("documents.paidWith"), accounts, account, { it.name }) { accountId = it.id }
        Picker(model.t("register.category"), listOf(null) + tree, tree.firstOrNull { it.first.id == categoryId }, { it?.first?.name(model.language) ?: model.t("register.uncategorized") }, indent = { it?.second ?: 0 }) {
            categoryId = it?.first?.id
        }
        Picker(model.t("register.for"), listOf(null) + people, people.firstOrNull { it.id == forId }, { it?.name ?: model.t("register.forNobody") }) { forId = it?.id }
        if (vehicles.isNotEmpty()) Picker(model.t("register.vehicle"), listOf(null) + vehicles, vehicles.firstOrNull { it.first == assetId }, { it?.second ?: model.t("common.none") }) { assetId = it?.first }
    }
}

private fun saveCopy(model: BooksModel, doc: VaultDocument) {
    val ext = when (doc.mimeType) { "application/pdf" -> "pdf"; "image/png" -> "png"; else -> "jpg" }
    val chooser = JFileChooser().apply {
        dialogTitle = model.t("documents.saveCopy")
        selectedFile = File(doc.fileName ?: "${doc.label}.$ext")
    }
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return
    model.act { chooser.selectedFile.writeBytes(model.books.documents.content(doc.id)) }
}

// --- Watched folder (CAP-04) -----------------------------------------------------------------------

@Composable
private fun WatchFolderDialog(model: BooksModel, onClose: () -> Unit) {
    val books = model.books
    var folder by remember { mutableStateOf(books.setting(WATCH_FOLDER)) }
    var groupId by remember { mutableStateOf(books.setting(WATCH_GROUP) ?: model.defaultDocumentGroup()) }
    FormDialog(model.t("documents.watchFolder"), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        model.act {
            books.putSetting(WATCH_FOLDER, folder.orEmpty())
            groupId?.let { books.putSetting(WATCH_GROUP, it) }
        }
        onClose()
    }) {
        Text(model.t("documents.watchExplain", IMPORTED_DIR), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(folder?.ifBlank { null } ?: model.t("documents.noWatchFolder"), Modifier.weight(1f))
            TextButton(onClick = { chooseDirectory(model.t("documents.watchFolder"))?.let { folder = it.toString() } }) { Text(model.t("backup.chooseFolder")) }
            if (!folder.isNullOrBlank()) TextButton(onClick = { folder = "" }) { Text(model.t("documents.stopWatching")) }
        }
        GroupPicker(model, groupId, enabled = true) { groupId = it.id }
    }
}

internal fun dateOfMillis(millis: Long): LocalDate =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }

