package ca.schippers.hfm.books

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
    const val BILL = "bill"
    const val VEHICLE = "vehicle"
    const val SERVICE = "service"
    const val PET = "pet"
    const val MEDICATION = "medication"
    const val WARRANTY = "warranty"
}

data class DocumentLink(val entity: String, val entityId: String)

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
) {
    /** The best short name: the title, else the merchant, else the file name. */
    val label: String get() = title ?: merchant ?: fileName ?: id.take(8)
}

/** The outcome of an import: the new document, or the identical one already in the vault (OCR-10). */
data class DocumentImport(val document: VaultDocument, val alreadyInVault: Boolean)

/** OCR-10: another document that looks like the same receipt or bill. */
data class PossibleDuplicate(val document: VaultDocument, val identical: Boolean)

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
            id, id, mimeType, sha, 1, null, now, books.userId, sourceDevice, fileName?.take(255), null, DocumentStatus.INBOX.name,
            null, null, null, null, null, null, null, content.size.toLong(), 0, null, now,
        )
        return DocumentImport(get(id), alreadyInVault = false)
    }

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
        val draft = FieldExtractor.extract(result, today)
        books.ledger(group).ledgerQueries.updateDocumentText(
            pages.toLong(), result.text.take(MAX_TEXT), draft.kind.name, draft.date?.value?.toString() ?: row.doc_date, draft.merchant?.value ?: row.merchant,
            draft.total?.value?.minorUnits ?: row.amount_minor, (draft.total?.value?.currency ?: draft.currency).code, json.encodeToString(StoredDraft.serializer(), StoredDraft.of(draft)),
            engineId, books.now(), documentId,
        )
        return get(documentId)
    }

    // --- Reading --------------------------------------------------------------------------------

    fun get(documentId: String): VaultDocument = locate(documentId).let { (g, row) -> toDocument(g, row) }

    /** SYNC-05: documents waiting for review, newest first. */
    fun inbox(): List<VaultDocument> = books.groups().flatMap { g ->
        books.ledger(g).ledgerQueries.documentsByStatus(DocumentStatus.INBOX.name).executeAsList().map { toDocument(g, it) }
    }.sortedByDescending { it.capturedAt }

    fun inboxCount(): Int = books.groups().sumOf { g ->
        books.ledger(g).ledgerQueries.documentCounts().executeAsList().firstOrNull { it.status == DocumentStatus.INBOX.name }?.total?.toInt() ?: 0
    }

    fun search(query: DocumentQuery): List<VaultDocument> {
        val pattern = query.text?.trim()?.takeIf { it.isNotEmpty() }?.let { "%" + it.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%" }
        return books.groups().flatMap { g ->
            books.ledger(g).ledgerQueries.searchDocuments(
                pattern, query.from?.toString(), query.to?.toString(), query.minAmount?.minorUnits, query.maxAmount?.minorUnits, query.limit.toLong(),
            ).executeAsList().map { toDocument(g, it) }
        }.sortedWith(compareByDescending<VaultDocument> { it.date ?: dateOf(it.capturedAt) }.thenByDescending { it.capturedAt }).take(query.limit)
    }

    /** Documents attached to a record, for example a transaction's receipt. */
    fun documentsFor(entity: String, entityId: String): List<VaultDocument> = books.groups().flatMap { g ->
        books.ledger(g).ledgerQueries.documentsFor(entity, entityId).executeAsList().map { toDocument(g, it) }
    }

    /** OCR-10: the same file, or another document with the same date and amount and a similar merchant. */
    fun duplicates(documentId: String): List<PossibleDuplicate> {
        val doc = get(documentId)
        val date = doc.date ?: return emptyList()
        val amount = doc.amount ?: return emptyList()
        return books.groups().flatMap { g ->
            books.ledger(g).ledgerQueries.similarDocuments(documentId, date.toString(), amount.minorUnits).executeAsList().map { toDocument(g, it) }
        }.filter { other -> other.status != DocumentStatus.DISMISSED && similarNames(doc.merchant, other.merchant) }
            .map { PossibleDuplicate(it, it.sha256 == doc.sha256) }
    }

    // --- Changing -------------------------------------------------------------------------------

    fun update(documentId: String, details: DocumentDetails) {
        val (group, row) = locate(documentId)
        books.require(group, PermissionLevel.EDIT)
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
     * [days] days of the document's date. The closest dates come first.
     */
    fun matches(documentId: String, days: Int = 5): List<TransactionMatch> {
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
        val occurrences = books.bills.occurrences(target.minus(DatePeriod(days = 45)), target.plus(DatePeriod(days = 45)), setOf(billId))
        val due = occurrences.minByOrNull { kotlin.math.abs(it.dueDate.toEpochDays() - target.toEpochDays()) }?.dueDate ?: throw ValidationException("error.noBillDate")
        books.bills.setAmount(billId, due, amount, documentId)
        link(documentId, DocumentEntity.BILL, billId)
        setStatus(documentId, DocumentStatus.FILED)
        return due
    }

    // --- Retention (section 4.4) ----------------------------------------------------------------

    /**
     * Filed documents older than the Canada Revenue Agency's six-year retention period, and not
     * marked to keep, that may be discarded. Nothing is ever deleted automatically.
     */
    fun discardable(today: LocalDate, years: Int = RETENTION_YEARS): List<VaultDocument> {
        val before = today.minus(DatePeriod(years = years))
        return search(DocumentQuery(to = before, limit = 10_000)).filter { it.status == DocumentStatus.FILED && !it.keepForever }
    }

    // --- Helpers --------------------------------------------------------------------------------

    private fun locate(documentId: String): Pair<GroupInfo, DocumentRow> {
        for (group in books.groups()) {
            val row = books.ledger(group).ledgerQueries.documentById(documentId).executeAsOneOrNull() ?: continue
            return group to row
        }
        throw AccessDeniedException("Document not found or not accessible")
    }

    private fun toDocument(group: GroupInfo, row: DocumentRow): VaultDocument {
        val currency = row.currency?.let { runCatching { Currency.of(it) }.getOrNull() } ?: books.rates.baseCurrency
        return VaultDocument(
            row.id, group.id, row.file_name, row.title, DocumentStatus.valueOf(row.status), row.kind?.let { runCatching { DocumentKind.valueOf(it) }.getOrNull() },
            row.doc_date?.let(LocalDate::parse), row.merchant, row.amount_minor?.let { Money.ofMinor(it, currency) }, row.mime_type, row.sha256,
            row.page_count.toInt(), row.size_bytes, row.captured_at, row.captured_by, row.source_device, row.recognized_text,
            row.extraction?.let { runCatching { json.decodeFromString(StoredDraft.serializer(), it).toDraft() }.getOrNull() },
            row.keep_forever == 1L, row.notes,
            books.ledger(group).ledgerQueries.linksForDocument(row.id).executeAsList().map { DocumentLink(it.entity, it.entity_id) },
        )
    }

    private fun dateOf(millis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }

    companion object {
        const val DESKTOP = "desktop"
        const val RETENTION_YEARS = 6
        private const val MAX_BYTES = 50 * 1024 * 1024
        private const val MAX_TEXT = 200_000

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
