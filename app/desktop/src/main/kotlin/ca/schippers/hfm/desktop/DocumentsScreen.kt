package ca.schippers.hfm.desktop

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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.calc.rules.LeadTimes
import ca.schippers.hfm.books.DocumentDetails
import ca.schippers.hfm.books.DocumentEntity
import ca.schippers.hfm.books.DocumentQuery
import ca.schippers.hfm.books.DocumentStatus
import ca.schippers.hfm.books.TransactionDraft
import ca.schippers.hfm.books.SplitDraft
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.books.VaultDocument
import ca.schippers.hfm.money.Money
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
    // OTH-03: a document chosen in the search results opens here.
    LaunchedEffect(model.focusDocumentId) {
        model.focusDocumentId?.let { id ->
            reviewing = runCatching { model.books.documents.get(id) }.getOrNull()
            model.focusDocumentId = null
        }
    }
    var busy by remember { mutableStateOf(false) }
    var dragOver by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLearned by remember { mutableStateOf(false) }

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
            TextButton(onClick = { showLearned = true }) { Text(model.t("learned.button")) }
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
    if (showLearned) LearnedDialog(model) { showLearned = false }
}

/** OCR-07: what was learned from corrections, store by store, each of which can be forgotten. */
@Composable
private fun LearnedDialog(model: BooksModel, onClose: () -> Unit) {
    val books = model.books
    val learned = remember(model.revision) { books.documents.learned() }
    val categories = remember(model.revision) { books.categories.list(includeArchived = true).associateBy { it.id } }
    var forgetting by remember { mutableStateOf<ca.schippers.hfm.books.LearnedMerchant?>(null) }
    WideDialog(model.t("learned.title"), model.t("common.close"), onClose) {
        Column(Modifier.width(720.dp).heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(model.t("learned.hint"), style = MaterialTheme.typography.bodySmall)
            if (learned.isEmpty()) Text(model.t("learned.none"), Modifier.padding(vertical = 8.dp))
            for (l in learned) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(model.t("learned.read", l.readKey), style = MaterialTheme.typography.bodySmall)
                        Text(
                            listOfNotNull(
                                l.merchant,
                                l.kind?.let { model.t("documentKind.$it") },
                                l.categoryId?.let { categories[it]?.name(model.language) },
                                model.t("learned.uses", l.uses),
                            ).joinToString(" · "),
                        )
                    }
                    TextButton(onClick = { forgetting = l }) { Text(model.t("learned.forget")) }
                }
                HorizontalDivider()
            }
        }
    }
    forgetting?.let { l ->
        AskBeforeDeleting(model, model.t("learned.forgetQuestion", l.merchant ?: l.readKey), onDismiss = { forgetting = null }) {
            model.act { books.documents.forgetLearned(l.groupId, l.readKey) } != null
        }
    }
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
        Text(model.t("documents.retentionExplain", LeadTimes.documentRetention(today())), style = MaterialTheme.typography.bodySmall)
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
    val currency = doc.amount?.currency ?: books.rates.baseCurrency
    var title by remember(documentId, doc.draft) { mutableStateOf(doc.title ?: doc.merchant.orEmpty()) }
    var date by remember(documentId, doc.draft) { mutableStateOf((doc.date ?: dateOfMillis(doc.capturedAt)).toString()) }
    var amount by remember(documentId, doc.draft) { mutableStateOf(doc.amount?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var kind by remember(documentId, doc.draft) { mutableStateOf(doc.kind ?: DocumentKind.OTHER) }
    var keep by remember(documentId) { mutableStateOf(doc.keepForever) }
    var notes by remember(documentId) { mutableStateOf(doc.notes.orEmpty()) }
    var creating by remember { mutableStateOf(false) }
    // DOC-02: the items typed by hand, kept while the dialog is open, and the result handed to the new transaction.
    var itemizing by remember { mutableStateOf(false) }
    var itemizeState by remember(documentId) { mutableStateOf<ItemizeState?>(null) }
    var itemized by remember { mutableStateOf<ca.schippers.hfm.books.Itemized?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    /** Saves what the user corrected before any filing action. */
    fun saveDetails(): Boolean = model.act {
        val d = runCatching { LocalDate.parse(date.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }
        books.documents.update(documentId, DocumentDetails(title, kind, d, title.ifBlank { null }, parseAmount(amount, currency, locale), keep, notes))
    } != null

    WideDialog(doc.label, model.t("common.close"), onClose) {
        Row(Modifier.heightIn(max = 620.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // The document itself, page by page (DOC-01).
            DocumentViewer(model, doc, Modifier.width(400.dp).height(600.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val draft = doc.draft
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewedField(model, draft?.merchant) { TextInput(model.t("documents.merchant"), title, it) { v -> title = v } }
                    Picker(model.t("documents.kind"), DocumentKind.entries, kind, { model.t("documentKind.$it") }, Modifier.width(240.dp)) { kind = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewedField(model, draft?.date) { DateInput(model.t("report.date"), date, it) { v -> date = v } }
                    ReviewedField(model, draft?.total) { AmountInput(model.t("documents.total"), amount, currency, locale, it, model::money) { v -> amount = v } }
                }
                ExtractedDetails(model, doc)
                AiPart(model, doc, kind, onClose)
                // AI-03: shown even once AI reading is turned off, since the fields are kept with the document.
                val reading = remember(model.revision, doc.id) { runCatching { books.ai.reading(doc.id) }.getOrNull() }
                if (reading != null && ca.schippers.hfm.ai.DocumentType.kindFor(reading.typeId) == null) ReadFieldsPart(model, reading)
                // SAL-02: a pay stub is recorded with or without AI; what AI read fills the form, else it is typed.
                if (kind == DocumentKind.PAY_STUB) PayStubPart(model, doc, onClose)
                // MED-08: an explanation of benefits proposes the claims it may answer.
                if (kind == DocumentKind.EOB) {
                    EobPart(
                        model, doc,
                        runCatching { parseAmount(amount, currency, locale) }.getOrNull() ?: doc.amount,
                        runCatching { LocalDate.parse(date.trim()) }.getOrNull() ?: doc.date,
                        onClose,
                    )
                }
                VoicePart(model, doc)
                Duplicates(model, doc)
                LabeledCheckbox(model.t("documents.keepForever"), keep) { keep = it }
                TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                FilingActions(model, doc, kind, ::saveDetails, onCreate = { creating = true }, onDone = onClose, onItemize = {
                    if (itemizeState == null) {
                        val printed = doc.draft?.taxes.orEmpty().filter { it.first != ca.schippers.hfm.ocr.TaxName.OTHER }
                            .groupBy({ it.first.name }, { it.second.value }).mapValues { (_, v) -> v.reduce(Money::plus) }
                        itemizeState = ItemizeState.start(runCatching { books.ai.readReceipt(documentId) }.getOrNull(), printed, locale, currency)
                    }
                    itemizing = true
                })
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
                // The fields an AI reading added to the text are shown above, with the reading.
                ca.schippers.hfm.books.DocumentService.recognisedOnly(doc.text)?.let {
                    Text(model.t("documents.recognisedText"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    Text(it.take(4000), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (itemizing) {
        itemizeState?.let { state ->
            ItemizeDialog(
                model, state, currency, runCatching { parseAmount(amount, currency, locale) }.getOrNull()?.abs(),
                runCatching { LocalDate.parse(date.trim()) }.getOrNull() ?: doc.date ?: today(),
                onDismiss = { itemizing = false },
            ) { result ->
                itemized = result
                itemizing = false
                creating = true
            }
        }
    }
    if (creating) NewTransactionDialog(model, doc, title, date, amount, itemized) { done -> creating = false; itemized = null; if (done) onClose() }
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
        if (extracted?.source == ca.schippers.hfm.ocr.FieldSource.CLOUD_AI) Text(model.t("ai.field"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        if (extracted?.needsReview == true) Text(model.t("documents.check"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    }
}

/**
 * Section 4.5: reading the document with cloud AI, offered when the user turned it on, and pointed
 * out when the fields read on this computer are uncertain (step 1). [kind] is the kind chosen in
 * the dialog, saved or not: it picks what the AI is asked to read.
 */
@Composable
private fun AiPart(model: BooksModel, doc: VaultDocument, kind: DocumentKind, onClose: () -> Unit) {
    val settings = remember(model.revision) { model.books.ai.settings() }
    if (!settings.enabled || doc.mimeType == "text/plain") return
    // AI-01: a paid request only for someone who may save its answer (capture rights on the document's group).
    val canSave = remember(model.revision, doc.groupId) {
        model.books.groups().firstOrNull { it.id == doc.groupId }?.level?.allows(ca.schippers.hfm.domain.PermissionLevel.CAPTURE_ONLY) == true
    }
    if (!canSave) return
    val hasKey = remember(model.revision) { DesktopAi.key(model) != null }
    val reading = remember(model.revision, doc.id) { runCatching { model.books.ai.reading(doc.id) }.getOrNull() }
    val draft = doc.draft
    val uncertain = draft?.total == null || listOfNotNull(draft.merchant, draft.date, draft.total).any { it.needsReview }
    var asking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hasKey) {
            OutlinedButton(enabled = !busy, onClick = {
                failure = null
                if (settings.confirmEach) {
                    asking = true
                } else {
                    // AI-01: the user chose not to be asked; every page is sent as it is.
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val type = DesktopAi.types().let { t -> t.get(ca.schippers.hfm.ai.DocumentType.idFor(kind)) ?: t.types.first() }
                                val pages = DesktopOcr.reader.pageImages(model.books.documents.content(doc.id))
                                DesktopAi.read(model, doc.id, type, DesktopAi.prepare(pages, emptyList()))
                            }
                        }
                        busy = false
                        model.changed()
                        result.onFailure { failure = model.aiFailure(it) }
                    }
                }
            }) { Text(model.t("ai.readWithAi")) }
        } else {
            Text(model.t("ai.addKeyFirst"), style = MaterialTheme.typography.bodySmall)
        }
        val note = when {
            busy -> model.t("ai.read.sending")
            reading != null -> model.t("ai.readBy", ca.schippers.hfm.ai.AiModel.priceOf(reading.model, ca.schippers.hfm.ai.AiModel.DEFAULT).label, model.date(dateOfMillis(reading.readAt)))
            uncertain -> model.t("ai.uncertain")
            else -> null
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    failure?.let { ErrorText(it) }
    if (reading != null && reading.typeId in ca.schippers.hfm.books.AiService.STATEMENT_TYPES) StatementPart(model, doc, reading.typeId, onClose)
    if (reading != null && reading.typeId in ca.schippers.hfm.books.AiService.INVESTMENT_TYPES) InvestmentPart(model, doc, reading.typeId, onClose)
    if (asking) AiReadDialog(model, doc, kind) { asking = false }
}

/**
 * OCR-09: a bank or card statement read by AI goes into an account as a statement, matched with
 * what is already in the books, and opens in reconciliation (section 8).
 */
@Composable
private fun StatementPart(model: BooksModel, doc: VaultDocument, typeId: String, onClose: () -> Unit) {
    val kind = if (typeId == "card_statement") ca.schippers.hfm.domain.AccountKind.CREDIT else ca.schippers.hfm.domain.AccountKind.BANK
    val accounts = remember(model.revision) { model.books.accounts.list().map { it.account }.filter { it.type.kind == kind && it.status != ca.schippers.hfm.domain.AccountStatus.CLOSED } }
    if (accounts.isEmpty()) return
    val digits = remember(doc.id, model.revision) { runCatching { model.books.ai.statement(doc.id, accounts.first().id).accountNumberHint }.getOrNull()?.filter(Char::isDigit) }
    var account by remember(doc.id) {
        mutableStateOf(accounts.firstOrNull { a -> digits != null && digits.length >= 3 && a.numberMasked?.filter(Char::isDigit)?.endsWith(digits.takeLast(4)) == true } ?: accounts.first())
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Picker(model.t("ai.statementAccount"), accounts, account, { it.name }, Modifier.weight(1f)) { account = it }
        Button(onClick = {
            val result = model.act { model.books.ai.importStatement(doc.id, account.id) } ?: return@Button
            model.lastImport = result
            model.selectedAccountId = account.id
            model.reconcilingStatementId = result.statementId
            model.section = Section.ACCOUNTS
            onClose()
        }) { Text(model.t("ai.reconcileStatement")) }
    }
}

/**
 * INV-05: a trade confirmation read by AI becomes its trades in an investment account; an
 * investment statement brings its activity and waits, with its holdings and cash, for the check of
 * the statement (REC-08). What is already in the account is matched, not added again.
 */
@Composable
private fun InvestmentPart(model: BooksModel, doc: VaultDocument, typeId: String, onClose: () -> Unit) {
    val accounts = remember(model.revision) { model.books.investments.accounts() }
    if (accounts.isEmpty()) {
        Text(model.t("ai.noInvestmentAccount"), style = MaterialTheme.typography.bodySmall)
        return
    }
    val suggested = remember(doc.id, model.revision) { runCatching { model.books.ai.suggestInvestmentAccount(doc.id) }.getOrNull() }
    var account by remember(doc.id) { mutableStateOf(accounts.firstOrNull { it.id == suggested } ?: accounts.first()) }
    var result by remember(doc.id) { mutableStateOf<ca.schippers.hfm.books.InvestmentImportResult?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Picker(model.t("ai.investmentAccount"), accounts, account, { it.name }, Modifier.weight(1f)) { account = it; result = null }
        Button(enabled = result == null, onClick = { result = model.act { model.books.ai.importInvestments(doc.id, account.id) } }) {
            Text(model.t(if (typeId == "trade_confirmation") "ai.addTrades" else "ai.importInvestmentStatement"))
        }
    }
    val r = result ?: return
    Text(model.t("investments.importResult", r.added, r.securitiesCreated), style = MaterialTheme.typography.bodySmall)
    if (r.alreadyThere > 0) Text(model.t("investments.alreadyThere", r.alreadyThere), style = MaterialTheme.typography.bodySmall)
    r.warnings.take(10).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
    OutlinedButton(onClick = {
        model.selectedAccountId = account.id
        model.openInvestmentStatementId = r.statementId
        model.section = Section.INVESTMENTS
        onClose()
    }) { Text(model.t(if (r.statementId != null) "ai.checkInvestmentStatement" else "ai.openInvestmentAccount")) }
}

/**
 * AI-03: the fields of a document type added by the user, which the app has no screen for, listed
 * as the type's schema names them. They are also kept with the document's text, for searching.
 */
@Composable
private fun ReadFieldsPart(model: BooksModel, reading: ca.schippers.hfm.books.StoredAiReading) {
    val fields = remember(reading) {
        runCatching {
            val answer = kotlinx.serialization.json.Json.parseToJsonElement(reading.answer) as kotlinx.serialization.json.JsonObject
            ca.schippers.hfm.ai.AiFields.fields(answer, DesktopAi.types().get(reading.typeId)?.schema)
        }.getOrDefault(emptyList())
    }
    Text(model.t("ai.readFields", reading.typeId), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
    if (fields.isEmpty()) Text(model.t("ai.readFieldsNone"), style = MaterialTheme.typography.bodySmall)
    androidx.compose.foundation.text.selection.SelectionContainer {
        Column {
            for (f in fields) {
                Row(Modifier.padding(vertical = 1.dp)) {
                    Text(f.label, Modifier.width(200.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Text(f.value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * SAL-02: a pay stub becomes the deposit of its net pay, split into gross pay and deductions: filled
 * in when the stub was read by AI, typed by hand otherwise.
 */
@Composable
private fun PayStubPart(model: BooksModel, doc: VaultDocument, onClose: () -> Unit) {
    var open by remember(doc.id) { mutableStateOf(false) }
    Button(onClick = { open = true }) { Text(model.t("payStub.record")) }
    if (open) {
        val read = remember(doc.id) { model.books.ai.payStub(doc.id, ca.schippers.hfm.money.Currency.CAD) }
        PayStubDialog(model, null, read, doc.id) { done -> open = false; if (done) onClose() }
    }
}

/**
 * MED-08: claims waiting for payment that this explanation of benefits may answer (claimed at least
 * its amount, submitted on or before its date), likeliest first; one click attaches it to the claim.
 */
@Composable
private fun EobPart(model: BooksModel, doc: VaultDocument, amount: ca.schippers.hfm.money.Money?, date: LocalDate?, onClose: () -> Unit) {
    val books = model.books
    if (amount == null || !amount.isPositive) {
        Text(model.t("documents.eobNeedsAmount"), style = MaterialTheme.typography.bodySmall)
        return
    }
    val candidates = remember(model.revision, amount, date) { books.medical.eobCandidates(amount, date ?: today()).take(5) }
    val plans = remember(model.revision) { books.medical.plans().associate { it.id to it.name } }
    val people = remember { books.members.list().associate { it.id to it.displayName } }
    Text(model.t("documents.eobMatches"), style = MaterialTheme.typography.titleSmall)
    if (candidates.isEmpty()) Text(model.t("documents.eobNone"), style = MaterialTheme.typography.bodySmall)
    for ((e, c) in candidates) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                model.t("documents.eobLine", people[e.memberId].orEmpty(), model.t("medService.${e.service}"), model.date(e.serviceDate), model.money(c.claimed), plans[c.planId].orEmpty()),
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = {
                val done = model.act {
                    books.medical.attach(ca.schippers.hfm.books.MedicalService.CLAIM, c.id, doc.id)
                    books.documents.setStatus(doc.id, DocumentStatus.FILED)
                }
                if (done != null) { model.lastImportMessage = model.t("documents.eobAttached"); onClose() }
            }) { Text(model.t("documents.eobAttach")) }
        }
    }
}

/** CAP-08: the spoken note recorded with a capture on the phone, played on this computer. */
@Composable
private fun VoicePart(model: BooksModel, doc: VaultDocument) {
    val voices = remember(model.revision, doc.id) { model.books.documents.voiceNotes(doc.id) }
    if (voices.isEmpty()) return
    var playing by remember { mutableStateOf<javax.sound.sampled.Clip?>(null) }
    androidx.compose.runtime.DisposableEffect(doc.id) { onDispose { playing?.close() } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (v in voices) {
            OutlinedButton(onClick = {
                playing?.let { it.close(); playing = null; return@OutlinedButton }
                model.act {
                    val stream = javax.sound.sampled.AudioSystem.getAudioInputStream(java.io.ByteArrayInputStream(model.books.documents.content(v.id)))
                    val clip = javax.sound.sampled.AudioSystem.getClip().apply { open(stream); start() }
                    clip.addLineListener { e -> if (e.type == javax.sound.sampled.LineEvent.Type.STOP) { clip.close(); playing = null } }
                    playing = clip
                }
            }) { Text(model.t(if (playing != null) "documents.voiceStop" else "documents.voicePlay")) }
        }
        Text(model.t("documents.voiceHint"), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ExtractedDetails(model: BooksModel, doc: VaultDocument) {
    val draft = doc.draft ?: return
    val parts = listOfNotNull(
        draft.subtotal?.let { model.t("documents.subtotal", model.money(it.value)) },
        draft.taxes.takeIf { it.isNotEmpty() }?.joinToString(", ") { (name, v) -> "${model.t("taxName.$name")} ${model.money(v.value)}" },
        // AI readings give a payment kind from the schema (cash, debit, credit...); on-device reading gives what was printed.
        draft.paymentMethod?.value?.let { v -> if (v in AI_PAYMENTS) model.t("aiPayment.$v") else v },
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

/**
 * Matching transactions, the bill it belongs to, or a new transaction (SYNC-05, BILL-03). [kind] is
 * the kind chosen in the dialog, saved or not: the choices follow it at once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilingActions(model: BooksModel, doc: VaultDocument, kind: DocumentKind, saveDetails: () -> Boolean, onCreate: () -> Unit, onDone: () -> Unit, onItemize: () -> Unit) {
    val books = model.books
    val matches = remember(model.revision, doc.id) { books.documents.matches(doc.id) }
    val bill = remember(model.revision, doc.id, kind) { if (kind == DocumentKind.BILL || kind == DocumentKind.INVOICE) books.documents.billFor(doc.id) else null }
    val linked = remember(model.revision, doc.id) { linkedDescriptions(model, doc) }
    if (linked.isNotEmpty()) {
        Text(model.t("documents.attachedTo"), style = MaterialTheme.typography.labelLarge)
        for (l in linked) Text(l, style = MaterialTheme.typography.bodySmall)
    }
    // A statement, pay stub or explanation of benefits is not one transaction: it is filed as is,
    // or (statements read by AI) reconciled above.
    if (kind in SUMMARY_KINDS) return
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { if (saveDetails()) onCreate() }) { Text(model.t("documents.newTransaction")) }
        // DOC-02: a receipt or invoice split by its items, typed by hand.
        if (kind == DocumentKind.RECEIPT || kind == DocumentKind.INVOICE) OutlinedButton(onClick = { if (saveDetails()) onItemize() }) { Text(model.t("documents.itemize")) }
    }
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
private fun NewTransactionDialog(model: BooksModel, doc: VaultDocument, payee: String, date: String, amount: String, itemized: ca.schippers.hfm.books.Itemized?, onClose: (Boolean) -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val currency = doc.amount?.currency ?: books.rates.baseCurrency
    val accounts = remember { books.accounts.list().map { it.account }.filter { it.currency == currency } }
    val tree = remember { books.categories.tree() }
    val people = remember { model.peopleAndPets() }
    val vehicles = remember { model.vehicleChoices() }
    val last4 = doc.draft?.cardLast4?.value
    // CAP-07: what was chosen on the phone comes first, when it still exists here.
    val chosen = doc.choices
    // The account chosen on the phone, else the card whose last digits are on the receipt, else a credit card, else the first account.
    var accountId by remember {
        mutableStateOf(
            (accounts.firstOrNull { it.id == chosen?.accountId } ?: accounts.firstOrNull { last4 != null && it.numberMasked?.endsWith(last4) == true }
                ?: accounts.firstOrNull { it.type == ca.schippers.hfm.domain.AccountType.CREDIT_CARD } ?: accounts.firstOrNull())?.id,
        )
    }
    val payeeDefault = remember { books.payees.list().firstOrNull { ca.schippers.hfm.books.DocumentService.similarNames(it.name, payee) } }
    // The category chosen on the phone, else the payee's own, else the one last used for documents from this merchant (OCR-07).
    var categoryId by remember {
        mutableStateOf(
            chosen?.categoryId?.takeIf { id -> tree.any { it.first.id == id } } ?: payeeDefault?.defaultCategoryId ?: runCatching { books.documents.learnedCategory(doc.id) }.getOrNull(),
        )
    }
    var forId by remember { mutableStateOf(chosen?.memberId?.takeIf { id -> people.any { it.id == id } }) }
    var assetId by remember { mutableStateOf<String?>(null) }
    val account = accounts.firstOrNull { it.id == accountId }
    // OCR-03: the items of a receipt read by AI, each with its share of the taxes.
    val split = remember { if (itemized != null) null else parseAmount(amount, currency, locale)?.let { total -> runCatching { books.ai.itemSplit(doc.id, total) }.getOrNull() } }
    val shares = split?.shares.orEmpty()
    var byItems by remember { mutableStateOf(false) }
    val itemCategories = remember { mutableStateListOf<String?>().apply { repeat(shares.size) { add(null) } } }
    FormDialog(model.t("documents.newTransaction"), model.t("common.save"), model.t("common.cancel"), canSave = account != null, onDismiss = { onClose(false) }, onSave = {
        val ok = model.act {
            // DOC-02: items typed by hand come to the receipt's total, or are the total when none was given.
            val value = itemized?.total ?: parseAmount(amount, account!!.currency, locale) ?: throw ValidationException("error.amountRequired")
            val d = runCatching { LocalDate.parse(date.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }
            // A receipt is money out; a refund slip would be entered from the register.
            val splits = if (itemized != null) {
                itemized.splits(categoryId, negative = true)
            } else if (byItems && shares.isNotEmpty()) {
                // Items with the same category become one split, noting what it covers.
                ca.schippers.hfm.books.ItemSplitter.combine(shares, { itemCategories[it] ?: categoryId }, negative = true)
            } else {
                listOf(SplitDraft(categoryId, -value.abs()))
            }
            books.documents.fileAsTransaction(
                doc.id,
                TransactionDraft(account!!.id, d, -value.abs(), (payeeDefault?.name ?: payee).ifBlank { null }, splits, memberId = forId, assetId = assetId),
            )
        }
        if (ok != null) onClose(true)
    }) {
        Text(listOf(payee, date, itemized?.total?.let(model::money) ?: amount).filter { it.isNotBlank() }.joinToString(" · "), fontWeight = FontWeight.Medium)
        Picker(model.t("documents.paidWith"), accounts, account, { it.name }) { accountId = it.id }
        Picker(model.t(if (itemized != null) "itemize.otherItemsCategory" else "register.category"), listOf(null) + tree, tree.firstOrNull { it.first.id == categoryId }, { it?.first?.name(model.language) ?: model.t("register.uncategorized") }, indent = { it?.second ?: 0 }) {
            categoryId = it?.first?.id
        }
        Picker(model.t("register.for"), listOf(null) + people, people.firstOrNull { it.id == forId }, { it?.name ?: model.t("register.forNobody") }) { forId = it?.id }
        if (vehicles.isNotEmpty()) Picker(model.t("register.vehicle"), listOf(null) + vehicles, vehicles.firstOrNull { it.first == assetId }, { it?.second ?: model.t("common.none") }) { assetId = it?.first }
        // DOC-02: the split lines the items typed by hand make.
        if (itemized != null) {
            val lines = itemized.splits(categoryId, negative = true)
            Text(model.t("itemize.lines", lines.size), style = MaterialTheme.typography.labelLarge)
            for (line in lines) {
                Text(
                    listOfNotNull(tree.firstOrNull { it.first.id == line.categoryId }?.first?.name(model.language) ?: model.t("register.uncategorized"), model.money(line.amount.abs()), line.memo).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (shares.isNotEmpty()) {
            LabeledCheckbox(model.t("documents.splitByItems", shares.size), byItems) { byItems = it }
            if (byItems) {
                Text(model.t("documents.splitByItemsHint") + " " + model.t("documents.splitNote.${split?.note}"), style = MaterialTheme.typography.bodySmall)
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    shares.forEachIndexed { i, s ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(s.description, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            // The taxes the receipt marks on the line, in the user's words (TPS, TVQ in French).
                            Text(s.taxes.sorted().joinToString(" ") { model.t("taxName.$it") }, Modifier.width(70.dp), style = MaterialTheme.typography.bodySmall)
                            Text(model.money(s.share), Modifier.width(80.dp), style = MaterialTheme.typography.bodySmall)
                            val chosen = itemCategories[i] ?: categoryId
                            Picker(
                                model.t("register.category"), listOf(null) + tree, tree.firstOrNull { it.first.id == chosen },
                                { it?.first?.name(model.language) ?: model.t("register.uncategorized") }, Modifier.width(220.dp), indent = { it?.second ?: 0 },
                            ) { itemCategories[i] = it?.first?.id }
                        }
                    }
                }
            }
        }
    }
}

private fun saveCopy(model: BooksModel, doc: VaultDocument) {
    val ext = when (doc.mimeType) { "application/pdf" -> "pdf"; "image/png" -> "png"; "image/heic" -> "heic"; else -> "jpg" }
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

private val AI_PAYMENTS = setOf("cash", "debit", "credit", "gift_card", "other")

/** Documents that summarise many transactions rather than record one. */
private val SUMMARY_KINDS = setOf(DocumentKind.CARD_STATEMENT, DocumentKind.BANK_STATEMENT, DocumentKind.INVESTMENT_STATEMENT, DocumentKind.PAY_STUB, DocumentKind.EOB, DocumentKind.TRADE_CONFIRMATION)
