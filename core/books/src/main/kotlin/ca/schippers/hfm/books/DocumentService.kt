package ca.schippers.hfm.books

import ca.schippers.hfm.calc.rules.LeadTimes
import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.DocumentDraft
import ca.schippers.hfm.ocr.DocumentKind
import ca.schippers.hfm.ocr.Extracted
import ca.schippers.hfm.ocr.FieldExtractor
import ca.schippers.hfm.ocr.FieldSource
import ca.schippers.hfm.ocr.OcrResult
import ca.schippers.hfm.ocr.TaxName
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import ca.schippers.hfm.data.ledger.Document as DocumentRow

enum class DocumentStatus { INBOX, FILED, DISMISSED }

/** What a document can be attached to (section 4.4). */
object DocumentEntity {
    const val TRANSACTION = "txn"

    /** CAP-08: a spoken note, linked to the document it was recorded with. */
    const val VOICE = "voice"
    const val BILL = "bill"
    const val VEHICLE = "vehicle"
    const val SERVICE = "service"
    const val PET = "pet"
    const val MEDICATION = "medication"
    const val WARRANTY = "warranty"
}

data class DocumentLink(val entity: String, val entityId: String)

/**
 * CAP-07: the account, category and person chosen on the phone with a capture. They are the
 * first choices offered when the document is filed as a new transaction.
 */
data class CaptureChoices(val accountId: String? = null, val categoryId: String? = null, val memberId: String? = null) {
    val isEmpty: Boolean get() = accountId == null && categoryId == null && memberId == null
}

/** A document in the vault, with what was read from it. Amounts are positive. */
data class VaultDocument(
    val id: String,
    val groupId: String,
    val fileName: String?,
    val title: String?,
    val status: DocumentStatus,
    val kind: DocumentKind?,
    val date: LocalDate?,
    val merchant: String?,
    val amount: Money?,
    val mimeType: String,
    val sha256: String,
    val pages: Int,
    val sizeBytes: Long?,
    val capturedAt: Long,
    val capturedBy: String?,
    val sourceDevice: String?,
    val text: String?,
    val draft: DocumentDraft?,
    val keepForever: Boolean,
    val notes: String?,
    val links: List<DocumentLink>,
    /** CAP-07: what was chosen on the phone, if the document came from it. */
    val choices: CaptureChoices? = null,
) {
    /** The best short name: the title, else the merchant, else the file name. */
    val label: String get() = title ?: merchant ?: fileName ?: id.take(8)
}

/** The outcome of an import: the new document, or the identical one already in the vault (OCR-10). */
data class DocumentImport(val document: VaultDocument, val alreadyInVault: Boolean)

/** OCR-10: another document that looks like the same receipt or bill. */
data class PossibleDuplicate(val document: VaultDocument, val identical: Boolean)

/**
 * OCR-07: what was learned about a store: [readKey] is how its name was read (simplified), then the
 * name, kind and category the user gave, and how many corrections taught it.
 */
data class LearnedMerchant(val groupId: String, val readKey: String, val merchant: String?, val kind: DocumentKind?, val categoryId: String?, val uses: Int)

/** Section 4.4 search: by text, date and amount. */
data class DocumentQuery(
    val text: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val minAmount: Money? = null,
    val maxAmount: Money? = null,
    val limit: Int = 500,
)

/** Editable details of a document; the file itself never changes. */
data class DocumentDetails(
    val title: String?,
    val kind: DocumentKind?,
    val date: LocalDate?,
    val merchant: String?,
    val amount: Money?,
    val keepForever: Boolean,
    val notes: String?,
)

/** A transaction that may be the one a receipt or bill belongs to. */
data class TransactionMatch(val transaction: Transaction, val accountName: String, val daysApart: Int)

/**
 * The encrypted document vault (section 4.4): captured and imported receipts, bills and papers,
 * their recognised text and extracted fields, the review inbox, links to the records they
 * support, duplicate detection (OCR-10) and the retention flag.
 *
 * Text recognition itself happens outside, on the device (OCR-01): the desktop reads the file
 * and passes the result to [recordText].
 */
class DocumentService internal constructor(private val books: Books) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    // --- Import (CAP-03, CAP-04) ----------------------------------------------------------------

    /**
     * Stores a file in the group's vault and puts it in the review inbox. An identical file
     * already in the vault is not stored twice (OCR-10).
     */
    fun import(groupId: String, content: ByteArray, fileName: String?, mimeType: String, sourceDevice: String = DESKTOP): DocumentImport {
        val group = books.group(groupId)
        books.require(group, PermissionLevel.CAPTURE_ONLY)
        validate(content.isNotEmpty() && content.size <= MAX_BYTES, "error.documentSize")
        val sha = sha256(content)
        for (g in books.groups()) {
            books.ledger(g).ledgerQueries.documentsBySha(sha).executeAsList().firstOrNull()?.let { return DocumentImport(toDocument(g, it), alreadyInVault = true) }
        }
        val id = Ids.newId()
        books.session.vault(group.partitionId).put(id, content)
        val now = books.now()
        books.ledger(group).ledgerQueries.insertDocument(
            id, id, mimeType, sha, 1, null, now, books.userId, sourceDevice, fileName?.let(::importedFileName)?.take(255), null, DocumentStatus.INBOX.name,
            null, null, null, null, null, null, null, content.size.toLong(), 0, null, now,
        )
        return DocumentImport(get(id), alreadyInVault = false)
    }

    /**
     * CAP-08: keeps a spoken note (a WAV file from the phone) in the vault beside [documentId], filed
     * at once so it never waits in the review inbox on its own.
     */
    fun attachVoice(documentId: String, wav: ByteArray, sourceDevice: String = DESKTOP): VaultDocument {
        val (group, _) = locate(documentId)
        validate(ca.schippers.hfm.sync.VoiceWav.isVoiceNote(wav), "error.voiceNote")
        val voice = import(group.id, wav, "voice-note.wav", "audio/wav", sourceDevice).document
        link(voice.id, DocumentEntity.VOICE, documentId)
        setStatus(voice.id, DocumentStatus.FILED)
        return voice
    }

    /** CAP-08: the spoken notes kept with a document. */
    fun voiceNotes(documentId: String): List<VaultDocument> = documentsFor(DocumentEntity.VOICE, documentId)

    /** The original file, decrypted. */
    fun content(documentId: String): ByteArray {
        val (group, _) = locate(documentId)
        return books.session.vault(group.partitionId).get(documentId)
    }

    /**
     * Stores what was read from the document and extracts its fields (OCR-02, OCR-05). The fields
     * become the document's date, merchant and amount until the user changes them.
     */
    fun recordText(documentId: String, pages: Int, result: OcrResult, engineId: String, today: LocalDate): VaultDocument {
        val (group, row) = locate(documentId)
        books.require(group, PermissionLevel.CAPTURE_ONLY)
        val extracted = FieldExtractor.extract(result, today)
        val key = readKey(extracted.merchant?.value)
        val draft = applyLearned(group, key, extracted)
        books.ledger(group).ledgerQueries.updateDocumentText(
            pages.toLong(), result.text.take(MAX_TEXT), draft.kind.name, draft.date?.value?.toString() ?: row.doc_date, draft.merchant?.value ?: row.merchant,
            draft.total?.value?.minorUnits ?: row.amount_minor, (draft.total?.value?.currency ?: draft.currency).code,
            json.encodeToString(StoredDraft.serializer(), StoredDraft.of(draft).copy(readKey = key, chosen = storedChoices(row))), engineId, books.now(), documentId,
        )
        return get(documentId)
    }

    /**
     * CAP-07: keeps the account, category and person chosen on the phone with the document, for
     * when it is filed. They are kept beside what was read, and survive a later reading.
     */
    fun recordChoices(documentId: String, choices: CaptureChoices) {
        val (group, row) = locate(documentId)
        books.require(group, PermissionLevel.CAPTURE_ONLY)
        val chosen = StoredChoices(choices.accountId, choices.categoryId, choices.memberId).takeUnless { choices.isEmpty }
        val stored = row.extraction?.let { runCatching { json.decodeFromString(StoredDraft.serializer(), it) }.getOrNull() }
        val extraction = when {
            stored != null -> json.encodeToString(StoredDraft.serializer(), stored.copy(chosen = chosen))
            chosen != null -> json.encodeToString(StoredChosen.serializer(), StoredChosen(chosen))
            else -> null
        }
        books.ledger(group).aiQueries.updateDocumentDraft(
            row.kind, row.doc_date, row.merchant, row.amount_minor, row.currency, extraction, row.ocr_engine, books.now(), documentId,
        )
    }

    /** CAP-07: the phone's choices stored with the document, whether or not anything was read from it. */
    private fun storedChoices(row: DocumentRow): StoredChoices? =
        row.extraction?.let { runCatching { json.decodeFromString(StoredChosen.serializer(), it) }.getOrNull() }?.chosen

    // --- Learning from corrections (OCR-07) ------------------------------------------------------

    /**
     * The merchant as it was read, reduced so the same store reads the same way: lower case,
     * without accents, digits or punctuation, its first three words. Null when too little is left.
     */
    internal fun readKey(merchant: String?): String? = merchant?.let { FieldExtractor.fold(it) }
        ?.replace(Regex("[^a-z]+"), " ")?.split(' ')?.filter { it.length > 1 }?.take(3)?.joinToString(" ")?.takeIf { it.length >= 3 }

    /** The merchant's name and document kind as the user last corrected them, if they did. */
    private fun applyLearned(group: GroupInfo, key: String?, draft: DocumentDraft): DocumentDraft {
        val memory = key?.let { books.ledger(group).learningQueries.merchantMemory(it).executeAsOneOrNull() } ?: return draft
        return draft.copy(
            merchant = memory.merchant?.let { Extracted(it, maxOf(LEARNED, draft.merchant?.confidence ?: 0f), draft.merchant?.source ?: FieldSource.ON_DEVICE) } ?: draft.merchant,
            kind = memory.kind?.let { runCatching { DocumentKind.valueOf(it) }.getOrNull() } ?: draft.kind,
        )
    }

    /** How the document's merchant was read, for learning from what the user changes. */
    private fun storedKey(row: DocumentRow): String? = row.extraction?.let { runCatching { json.decodeFromString(StoredDraft.serializer(), it) }.getOrNull() }
        ?.let { it.readKey ?: readKey(it.merchant?.v) }

    /** OCR-07: what was learned from the user's corrections, in every group they can see. */
    fun learned(): List<LearnedMerchant> = books.groups().flatMap { g ->
        books.ledger(g).learningQueries.allMerchantMemory().executeAsList().map {
            LearnedMerchant(g.id, it.read_key, it.merchant, it.kind?.let { k -> runCatching { DocumentKind.valueOf(k) }.getOrNull() }, it.category_id, it.uses.toInt())
        }
    }

    /** OCR-07: forgets what was learned for a store, so its next documents are read as they come. Needs edit rights. */
    fun forgetLearned(groupId: String, readKey: String) {
        val group = books.group(groupId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).learningQueries.forgetMerchant(readKey)
    }

    /** OCR-07: the category last used when filing a document from this merchant. */
    fun learnedCategory(documentId: String): String? {
        val (group, row) = locate(documentId)
        val key = storedKey(row) ?: return null
        return books.ledger(group).learningQueries.merchantMemory(key).executeAsOneOrNull()?.category_id
            ?.takeIf { id -> books.categories.list().any { it.id == id } }
    }

    /**
     * Stores fields read another way, such as by cloud AI (section 4.5): they become the
     * document's kind, date, merchant and amount, and its recognised text is kept.
     */
    internal fun recordDraft(documentId: String, read: DocumentDraft, engineId: String, readText: String? = null): VaultDocument {
        val (group, row) = locate(documentId)
        books.require(group, PermissionLevel.CAPTURE_ONLY)
        val key = readKey(read.merchant?.value)
        val draft = applyLearned(group, key, read)
        val date = draft.date?.value?.toString() ?: row.doc_date
        val merchant = draft.merchant?.value ?: row.merchant
        val amount = draft.total?.value?.minorUnits ?: row.amount_minor
        val currency = (draft.total?.value?.currency ?: draft.currency).code
        val stored = json.encodeToString(StoredDraft.serializer(), StoredDraft.of(draft).copy(readKey = key, chosen = storedChoices(row)))
        val marker = READ_TEXT_MARKER
        if (readText == null && row.recognized_text?.contains(marker) != true) {
            books.ledger(group).aiQueries.updateDocumentDraft(draft.kind.name, date, merchant, amount, currency, stored, engineId, books.now(), documentId)
        } else {
            // AI-03: the fields read are kept after the recognised text, replacing those of an earlier reading, so they can be searched.
            val text = listOfNotNull(recognisedOnly(row.recognized_text), readText?.let { "$marker\n$it" }).joinToString("\n\n").take(MAX_TEXT).ifEmpty { null }
            books.ledger(group).ledgerQueries.updateDocumentText(row.page_count, text, draft.kind.name, date, merchant, amount, currency, stored, engineId, books.now(), documentId)
        }
        return get(documentId)
    }

    // --- Reading --------------------------------------------------------------------------------

    fun get(documentId: String): VaultDocument = locate(documentId).let { (g, row) -> toDocument(g, row) }

    /**
     * SYNC-05, HH-12: documents waiting for review, newest first: the user's own captures, and for
     * an administrator also those of shared groups.
     */
    fun inbox(): List<VaultDocument> = books.groups().flatMap { g ->
        toDocuments(g, books.ledger(g).ledgerQueries.documentsByStatus(DocumentStatus.INBOX.name).executeAsList().filter { reviewsHere(g, it.captured_by) })
    }.sortedByDescending { it.capturedAt }

    fun inboxCount(): Int = books.groups().sumOf { g ->
        books.ledger(g).ledgerQueries.documentsByStatus(DocumentStatus.INBOX.name).executeAsList().count { reviewsHere(g, it.captured_by) }
    }

    private fun reviewsHere(group: GroupInfo, capturedBy: String?): Boolean =
        capturedBy == books.userId || (!group.isPrivate && books.role == ca.schippers.hfm.domain.Role.ADMINISTRATOR)

    fun search(query: DocumentQuery): List<VaultDocument> {
        val pattern = query.text?.trim()?.takeIf { it.isNotEmpty() }?.let { "%" + it.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%" }
        return books.groups().flatMap { g ->
            toDocuments(
                g,
                books.ledger(g).ledgerQueries.searchDocuments(
                    pattern, query.from?.toString(), query.to?.toString(), query.minAmount?.minorUnits, query.maxAmount?.minorUnits, query.limit.toLong(),
                ).executeAsList(),
            )
        }.sortedWith(compareByDescending<VaultDocument> { it.date ?: dateOf(it.capturedAt) }.thenByDescending { it.capturedAt }).take(query.limit)
    }

    /** EST-01: every document marked to keep forever, in every group this user can open, newest first. */
    fun kept(): List<VaultDocument> = books.groups().flatMap { g -> toDocuments(g, books.ledger(g).ledgerQueries.keptDocuments().executeAsList()) }
        .sortedWith(compareByDescending<VaultDocument> { it.date ?: dateOf(it.capturedAt) }.thenByDescending { it.capturedAt })

    /** Documents attached to a record, for example a transaction's receipt. */
    fun documentsFor(entity: String, entityId: String): List<VaultDocument> = books.groups().flatMap { g ->
        toDocuments(g, books.ledger(g).ledgerQueries.documentsFor(entity, entityId).executeAsList())
    }

    /** OCR-10: the same file, or another document with the same date and amount and a similar merchant. */
    fun duplicates(documentId: String): List<PossibleDuplicate> {
        val doc = get(documentId)
        val date = doc.date ?: return emptyList()
        val amount = doc.amount ?: return emptyList()
        return books.groups().flatMap { g ->
            toDocuments(g, books.ledger(g).ledgerQueries.similarDocuments(documentId, date.toString(), amount.minorUnits).executeAsList())
        }.filter { other -> other.status != DocumentStatus.DISMISSED && similarNames(doc.merchant, other.merchant) }
            .map { PossibleDuplicate(it, it.sha256 == doc.sha256) }
    }

    // --- Changing -------------------------------------------------------------------------------

    fun update(documentId: String, details: DocumentDetails) {
        val (group, row) = locate(documentId)
        books.require(group, PermissionLevel.EDIT)
        // OCR-07: a name or kind the user changed is remembered for the next documents read the same way.
        storedKey(row)?.let { key ->
            val read = toDocument(group, row).draft
            val name = (details.merchant ?: details.title).blankToNull()
            val renamed = name?.takeIf { it != read?.merchant?.value }
            val rekinded = details.kind?.takeIf { it != read?.kind }?.name
            if (renamed != null || rekinded != null) books.ledger(group).learningQueries.rememberMerchant(key, renamed, rekinded, null, books.now())
        }
        with(details) {
            books.ledger(group).ledgerQueries.updateDocumentDetails(
                title.blankToNull(), row.status, kind?.name, date?.toString(), merchant.blankToNull(), amount?.minorUnits, amount?.currency?.code ?: row.currency,
                if (keepForever) 1 else 0, notes.blankToNull(), books.now(), documentId,
            )
        }
    }

    fun setStatus(documentId: String, status: DocumentStatus) {
        val (group, _) = locate(documentId)
        books.require(group, PermissionLevel.CAPTURE_ONLY)
        books.ledger(group).ledgerQueries.setDocumentStatus(status.name, books.now(), documentId)
    }

    /** Deletes the document and its file. The records it was attached to stay. */
    fun delete(documentId: String) {
        val (group, _) = locate(documentId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).ledgerQueries.deleteDocument(documentId)
        books.session.vault(group.partitionId).delete(documentId)
    }

    fun link(documentId: String, entity: String, entityId: String) {
        val (group, _) = locate(documentId)
        books.require(group, PermissionLevel.CAPTURE_ONLY)
        books.ledger(group).ledgerQueries.linkDocument(documentId, entity, entityId)
    }

    fun unlink(documentId: String, entity: String, entityId: String) {
        val (group, _) = locate(documentId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).ledgerQueries.unlinkDocument(documentId, entity, entityId)
    }

    // --- Filing (SYNC-05, BILL-03) ----------------------------------------------------------------

    /**
     * Transactions that may be the one this receipt or bill belongs to: the same amount, within
     * [days] days of the document's date (the document match window of Rates and rules). The
     * closest dates come first.
     */
    fun matches(documentId: String, days: Int = LeadTimes.documentMatch(books.today())): List<TransactionMatch> {
        val doc = get(documentId)
        val date = doc.date ?: return emptyList()
        val amount = doc.amount ?: return emptyList()
        val linked = doc.links.filter { it.entity == DocumentEntity.TRANSACTION }.map { it.entityId }.toSet()
        val accounts = books.accounts.list(includeClosed = true).associateBy { it.account.id }
        return books.groups().flatMap { g ->
            books.ledger(g).ledgerQueries.txnsNear(date.minus(DatePeriod(days = days)).toString(), date.plus(DatePeriod(days = days)).toString(), amount.minorUnits)
                .executeAsList().mapNotNull { row ->
                    val account = accounts[row.account_id]?.account ?: return@mapNotNull null
                    if (account.currency != amount.currency || row.id in linked) return@mapNotNull null
                    val txn = books.transactions.get(row.id)
                    TransactionMatch(txn, account.name, kotlin.math.abs(LocalDate.parse(row.date).toEpochDays() - date.toEpochDays()).toInt())
                }
        }.sortedBy { it.daysApart }
    }

    /** Files the document with an existing transaction. */
    fun fileWithTransaction(documentId: String, transactionId: String) {
        books.transactions.get(transactionId)
        link(documentId, DocumentEntity.TRANSACTION, transactionId)
        setStatus(documentId, DocumentStatus.FILED)
    }

    /** Creates the transaction a receipt describes, attaches the receipt, and files it. */
    fun fileAsTransaction(documentId: String, draft: TransactionDraft): Transaction {
        val txn = books.transactions.create(draft)
        fileWithTransaction(documentId, txn.id)
        // TX-04: the sales taxes the receipt shows, when they are in the transaction's currency.
        get(documentId).draft?.taxes?.filter { (_, v) -> v.value.currency == txn.amount.currency && !v.value.isNegative }
            ?.groupBy({ it.first }, { it.second.value })?.mapValues { (_, v) -> v.reduce(Money::plus) }
            ?.takeIf { it.isNotEmpty() && it.values.fold(Money.zero(txn.amount.currency), Money::plus).minorUnits <= kotlin.math.abs(txn.amount.minorUnits) }
            ?.let { books.transactions.setSalesTaxes(txn.id, it) }
        // OCR-07: a single category is suggested next time; a receipt split by items is not one.
        draft.splits.mapNotNull { it.categoryId }.distinct().singleOrNull()?.let { category ->
            val (group, row) = locate(documentId)
            storedKey(row)?.let { books.ledger(group).learningQueries.rememberMerchant(it, null, null, category, books.now()) }
        }
        return txn
    }

    /**
     * BILL-03: the bill this document probably belongs to, by its payee or name, or its account
     * number. Null when nothing matches well enough.
     */
    fun billFor(documentId: String): Bill? {
        val doc = get(documentId)
        val account = doc.draft?.accountNumber?.value?.filter(Char::isDigit)
        val bills = books.bills.list()
        account?.takeIf { it.length >= 4 }?.let { digits ->
            bills.firstOrNull { b -> b.payeeAccountNumber?.filter(Char::isDigit)?.let { it.isNotEmpty() && (it.endsWith(digits.takeLast(4)) || digits.endsWith(it.takeLast(4))) } == true }?.let { return it }
        }
        val merchant = doc.merchant ?: return null
        return bills.firstOrNull { similarNames(merchant, it.payeeName) || similarNames(merchant, it.name) }
    }

    /**
     * BILL-03: records the captured bill's amount on the bill's due date nearest to the
     * document's due date (or date), attaches it, and files it. Returns the due date used.
     */
    fun fileWithBill(documentId: String, billId: String): LocalDate {
        val doc = get(documentId)
        val amount = doc.amount ?: throw ValidationException("error.amountRequired")
        val target = doc.draft?.dueDate?.value ?: doc.date ?: dateOf(doc.capturedAt)
        val window = LeadTimes.billMatch(target)
        val occurrences = books.bills.occurrences(target.minus(DatePeriod(days = window)), target.plus(DatePeriod(days = window)), setOf(billId))
        val due = occurrences.minByOrNull { kotlin.math.abs(it.dueDate.toEpochDays() - target.toEpochDays()) }?.dueDate ?: throw ValidationException("error.noBillDate")
        books.bills.setAmount(billId, due, amount, documentId)
        link(documentId, DocumentEntity.BILL, billId)
        setStatus(documentId, DocumentStatus.FILED)
        return due
    }

    // --- Retention (section 4.4) ----------------------------------------------------------------

    /**
     * Filed documents older than the retention period (Rates and rules; the Canada Revenue Agency's
     * six years built in), and not marked to keep, that may be discarded. Nothing is ever deleted
     * automatically.
     */
    fun discardable(today: LocalDate, years: Int = LeadTimes.documentRetention(today)): List<VaultDocument> {
        val before = today.minus(DatePeriod(years = years))
        return search(DocumentQuery(to = before, limit = 10_000)).filter { it.status == DocumentStatus.FILED && !it.keepForever }
    }

    // --- Helpers --------------------------------------------------------------------------------

    internal fun locate(documentId: String): Pair<GroupInfo, DocumentRow> {
        for (group in books.groups()) {
            val row = books.ledger(group).ledgerQueries.documentById(documentId).executeAsOneOrNull() ?: continue
            return group to row
        }
        throw AccessDeniedException("Document not found or not accessible")
    }

    private fun toDocument(group: GroupInfo, row: DocumentRow): VaultDocument =
        toDocument(group, row, books.ledger(group).ledgerQueries.linksForDocument(row.id).executeAsList().map { DocumentLink(it.entity, it.entity_id) })

    /** Several documents with their links read in a few queries, not one per document (NFR-02). */
    private fun toDocuments(group: GroupInfo, rows: List<DocumentRow>): List<VaultDocument> {
        val queries = books.ledger(group).ledgerQueries
        val links = rows.map { it.id }.chunked(LINK_BATCH).flatMap { ids -> queries.linksForDocuments(ids).executeAsList() }
            .groupBy({ it.document_id }, { DocumentLink(it.entity, it.entity_id) })
        return rows.map { toDocument(group, it, links[it.id].orEmpty()) }
    }

    private fun toDocument(group: GroupInfo, row: DocumentRow, links: List<DocumentLink>): VaultDocument {
        val currency = row.currency?.let { runCatching { Currency.of(it) }.getOrNull() } ?: books.rates.baseCurrency
        return VaultDocument(
            row.id, group.id, row.file_name, row.title, DocumentStatus.valueOf(row.status), row.kind?.let { runCatching { DocumentKind.valueOf(it) }.getOrNull() },
            row.doc_date?.let(LocalDate::parse), row.merchant, row.amount_minor?.let { Money.ofMinor(it, currency) }, row.mime_type, row.sha256,
            row.page_count.toInt(), row.size_bytes, row.captured_at, row.captured_by, row.source_device, row.recognized_text,
            row.extraction?.let { runCatching { json.decodeFromString(StoredDraft.serializer(), it).toDraft() }.getOrNull() },
            row.keep_forever == 1L, row.notes, links,
            storedChoices(row)?.let { CaptureChoices(it.account, it.category, it.member) },
        )
    }

    private fun dateOf(millis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }

    companion object {
        const val DESKTOP = "desktop"
        private const val MAX_BYTES = 50 * 1024 * 1024
        private const val MAX_TEXT = 200_000

        /** Separates a document's recognised text from the fields an AI reading added (AI-03). */
        const val READ_TEXT_MARKER = "=== AI ==="

        /** A document's recognised text without the fields an AI reading added. */
        fun recognisedOnly(text: String?): String? = text?.substringBefore(READ_TEXT_MARKER)?.trimEnd()?.takeIf { it.isNotEmpty() }

        /** Confidence of a merchant name the user taught (OCR-07). */
        private const val LEARNED = 0.95f

        /** Ids per query when reading links, well under SQLite's limit on parameters. */
        private const val LINK_BATCH = 500

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /** "IGA Extra Famille Jodoin" and "IGA" are the same business; "Metro" and "IGA" are not. */
        fun similarNames(a: String?, b: String?): Boolean {
            if (a.isNullOrBlank() || b.isNullOrBlank()) return a.isNullOrBlank() && b.isNullOrBlank()
            fun words(s: String) = FieldExtractor.fold(s).split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }.toSet()
            val wa = words(a)
            val wb = words(b)
            if (wa.isEmpty() || wb.isEmpty()) return false
            val shorter = if (wa.size <= wb.size) wa else wb
            val longer = if (shorter === wa) wb else wa
            return shorter.all { it in longer }
        }
    }
}

/** The extracted fields as stored with the document (OCR-05: each with its confidence and source). */
@Serializable
internal data class StoredField(val v: String, val c: Float, val s: String = FieldSource.ON_DEVICE.name)

@Serializable
internal data class StoredDraft(
    val kind: String,
    val currency: String,
    val merchant: StoredField? = null,
    val date: StoredField? = null,
    val total: StoredField? = null,
    val subtotal: StoredField? = null,
    val taxes: Map<String, StoredField> = emptyMap(),
    val paymentMethod: StoredField? = null,
    val cardLast4: StoredField? = null,
    val invoiceNumber: StoredField? = null,
    val dueDate: StoredField? = null,
    val accountNumber: StoredField? = null,
    /** OCR-07: how the merchant was read, before any learned correction. */
    val readKey: String? = null,
    /** CAP-07: what was chosen on the phone. */
    val chosen: StoredChoices? = null,
) {
    fun toDraft(): DocumentDraft {
        val c = Currency.of(currency)
        fun <T> StoredField.to(parse: (String) -> T) = Extracted(parse(v), this.c, FieldSource.valueOf(s))
        fun money(s: String) = Money.parse(s, c)
        return DocumentDraft(
            DocumentKind.valueOf(kind), merchant?.to { it }, date?.to(LocalDate::parse), total?.to(::money), subtotal?.to(::money),
            taxes.map { (k, f) -> TaxName.valueOf(k) to f.to(::money) }, c, paymentMethod?.to { it }, cardLast4?.to { it }, invoiceNumber?.to { it },
            dueDate?.to(LocalDate::parse), accountNumber?.to { it },
        )
    }

    companion object {
        fun of(d: DocumentDraft): StoredDraft {
            fun <T> Extracted<T>.stored(text: (T) -> String = { it.toString() }) = StoredField(text(value), confidence, source.name)
            fun Extracted<Money>.amount() = stored { it.toBigDecimal().toPlainString() }
            return StoredDraft(
                d.kind.name, d.currency.code, d.merchant?.stored(), d.date?.stored(), d.total?.amount(), d.subtotal?.amount(),
                d.taxes.associate { (name, value) -> name.name to value.amount() }, d.paymentMethod?.stored(), d.cardLast4?.stored(),
                d.invoiceNumber?.stored(), d.dueDate?.stored(), d.accountNumber?.stored(),
            )
        }
    }
}

/** CAP-07: the account, category and person chosen on the phone, by id. */
@Serializable
internal data class StoredChoices(val account: String? = null, val category: String? = null, val member: String? = null)

/** Reads the phone's choices alone, also when nothing was read from the document (no [StoredDraft]). */
@Serializable
internal data class StoredChosen(val chosen: StoredChoices? = null)

/**
 * A name for a file saved to disk, made from a name the user or an outside file gave (a record's
 * name, an email attachment's): no folder separators, characters Windows refuses or control
 * characters, and no dots at either end, so it can never be "." or ".." or reach outside the
 * folder it is saved in (Phase 5 security review).
 */
fun plainFileName(name: String): String =
    name.map { if (it.isISOControl() || it in "\\/:*?\"<>|") '-' else it }.joinToString("").trim().trim('.').trim().ifEmpty { "-" }

/**
 * The name kept with an imported document: an email attachment's or a phone's file name may hold a
 * folder ("../../x.pdf", "C:\Users\..."); only its last part is kept, as a [plainFileName].
 */
internal fun importedFileName(name: String): String = plainFileName(name.substringAfterLast('/').substringAfterLast('\\'))
