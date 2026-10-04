package ca.schippers.hfm.books

import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.importers.ImportedLine
import ca.schippers.hfm.importers.ImportedStatement
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.DocumentDraft
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal
import java.math.RoundingMode

/** A user's choices for cloud AI reading (AI-01): off until they turn it on, and asking before each document. */
data class AiSettings(
    val enabled: Boolean = false,
    /** Show what will be sent and wait for the user before each document (AI-01, AI-04). */
    val confirmEach: Boolean = true,
    /** The model chosen, or null for the default. */
    val model: String? = null,
)

/** The checked answer kept with a document after an AI reading. */
data class StoredAiReading(
    val typeId: String,
    val schemaVersion: String,
    /** The answer as JSON, as the schema defines it. */
    val answer: String,
    val checked: Boolean,
    val model: String,
    val readAt: Long,
)

/** AI-06: one request in the usage log. */
data class AiUsageEntry(
    val usedAt: Long,
    val documentId: String?,
    /** The document's short name, while the document exists. */
    val documentLabel: String?,
    val provider: String,
    val model: String,
    val typeId: String,
    val inputTokens: Long,
    val outputTokens: Long,
    /** Estimated, in US dollars, from the list price. */
    val costUsd: BigDecimal,
    val succeeded: Boolean,
)

/**
 * The household side of cloud AI reading (section 4.5): each user's settings, the checked answer
 * kept with its document, and the usage log (AI-06). Readings and the log are kept in the ledger
 * of the document's group, so a private group's documents, readings and costs stay private
 * (HH-11). The requests themselves are made by the desktop app with the user's own key (AI-02).
 */
class AiService internal constructor(private val books: Books) {

    private fun key(name: String) = "ai.${books.userId}.$name"

    /** The signed-in user's settings; each user brings their own key and chooses for themselves. */
    fun settings(): AiSettings = AiSettings(
        enabled = books.setting(key("enabled")) == "true",
        confirmEach = books.setting(key("confirmEach")) != "false",
        model = books.setting(key("model"))?.takeIf { it.isNotBlank() },
    )

    fun saveSettings(settings: AiSettings) {
        books.putSetting(key("enabled"), settings.enabled.toString())
        books.putSetting(key("confirmEach"), settings.confirmEach.toString())
        books.putSetting(key("model"), settings.model.orEmpty())
        books.session.audit("UPDATE", "ai_settings", books.userId, if (settings.enabled) "on" else "off")
    }

    /** AI-06: logs one request about [documentId], in that document's group, as it completes. */
    fun record(documentId: String, provider: String, model: String, typeId: String, inputTokens: Long, outputTokens: Long, costUsd: BigDecimal, succeeded: Boolean) {
        val (group, _) = books.documents.locate(documentId)
        books.require(group, PermissionLevel.CAPTURE_ONLY)
        books.ledger(group).aiQueries.insertAiUsage(
            Ids.newId(), books.now(), books.userId, documentId, provider, model, typeId, inputTokens, outputTokens,
            costUsd.movePointRight(6).setScale(0, RoundingMode.HALF_UP).toLong(), if (succeeded) 1 else 0,
        )
    }

    /**
     * Keeps a checked reading with its document and puts its fields in the review inbox, marked
     * as read by AI. The document's recognised text and file are unchanged.
     */
    fun saveReading(documentId: String, typeId: String, schemaVersion: String, answer: String, checked: Boolean, model: String, draft: DocumentDraft): VaultDocument {
        val (group, _) = books.documents.locate(documentId)
        books.require(group, PermissionLevel.CAPTURE_ONLY)
        books.ledger(group).aiQueries.saveDocumentAi(documentId, typeId, schemaVersion, answer, if (checked) 1 else 0, model, books.now(), books.userId)
        // The audit log is readable by every user: it records that a document was sent, never what it holds (PRV-01).
        books.session.audit("AI_READ", "document", documentId, model)
        return books.documents.recordDraft(documentId, draft, "ai:$model")
    }

    fun reading(documentId: String): StoredAiReading? {
        val (group, _) = books.documents.locate(documentId)
        return books.ledger(group).aiQueries.documentAi(documentId).executeAsOneOrNull()?.let {
            StoredAiReading(it.type_id, it.schema_version, it.answer, it.checked == 1L, it.model, it.read_at)
        }
    }

    /**
     * OCR-09: a bank or credit card statement read by AI, as a statement for [accountId], ready for
     * import and reconciliation (section 8). Card statements print charges as positive amounts and
     * the balance owed as positive; the books keep a card's charges and balance owed as negative.
     */
    fun statement(documentId: String, accountId: String): ImportedStatement {
        val reading = reading(documentId) ?: throw ValidationException("error.aiNoStatement")
        validate(reading.typeId in STATEMENT_TYPES, "error.aiNoStatement")
        val (_, account) = books.accounts.locate(accountId)
        val card = reading.typeId == "card_statement"
        val answer = Json.parseToJsonElement(reading.answer).jsonObject
        val currency = answer.text("currency")?.let { runCatching { Currency.of(it.uppercase()) }.getOrNull() } ?: account.currency
        fun money(key: String, from: JsonObject = answer): Money? = (from[key] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBigDecimalOrNull()
            ?.let { Money.of(if (card) it.negate() else it, currency) }
        fun date(key: String, from: JsonObject = answer) = from.text(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val lines = (answer["transactions"] as? JsonArray).orEmpty().mapNotNull { e ->
            val t = e as? JsonObject ?: return@mapNotNull null
            val day = date("date", t) ?: return@mapNotNull null
            val amount = money("amount", t) ?: return@mapNotNull null
            ImportedLine(null, day, amount, t.text("description"), null, null)
        }
        return ImportedStatement(
            "AI", answer.text(if (card) "card_last4" else "account_number_last_digits"), currency, date("period_start"), date("period_end"),
            money(if (card) "previous_balance" else "opening_balance"), money(if (card) "new_balance" else "closing_balance"), lines,
        )
    }

    /**
     * OCR-09: imports the statement read from [documentId] into [accountId]: lines already in the
     * books are matched, the rest added, as for a downloaded statement. The same document cannot
     * be imported twice.
     */
    fun importStatement(documentId: String, accountId: String): ImportResult =
        books.statements.import(accountId, statement(documentId, accountId), books.documents.get(documentId).label, books.documents.content(documentId))

    /**
     * OCR-03: a receipt or invoice read by AI, split by its items, each with its share of [total]
     * (the amount paid), by the receipt's tax codes when it shows them ([ItemSplitter]). Null when
     * the reading has fewer than two items or they add up to nothing.
     */
    fun itemSplit(documentId: String, total: Money): ItemSplit? {
        val reading = reading(documentId)?.takeIf { it.typeId == "receipt" || it.typeId == "invoice" } ?: return null
        val answer = Json.parseToJsonElement(reading.answer).jsonObject
        val lines = (answer["line_items"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val amount = o.number("amount") ?: return@mapNotNull null
            val taxes = (o["taxes"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.uppercase() }?.toSet()
            ReceiptLine(o.text("description") ?: "?", amount, taxes)
        }
        val printed = (answer["taxes"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val amount = o.number("amount") ?: return@mapNotNull null
            (o.text("name")?.uppercase() ?: "OTHER") to amount
        }.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.fold(BigDecimal.ZERO, BigDecimal::add) }
        return ItemSplitter.split(lines, printed, total)
    }

    private fun JsonObject.number(key: String): BigDecimal? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBigDecimalOrNull()

    /** AI-06: the signed-in user's requests between two instants (epoch milliseconds), newest first. */
    fun usage(fromMillis: Long, toMillis: Long): List<AiUsageEntry> = books.groups().flatMap { g ->
        val q = books.ledger(g).aiQueries
        q.aiUsageBetween(fromMillis, toMillis).executeAsList().filter { it.user_id == books.userId }.map { row ->
            val label = row.document_id?.let { id -> runCatching { books.documents.get(id).label }.getOrNull() }
            AiUsageEntry(
                row.used_at, row.document_id, label, row.provider, row.model, row.type_id, row.input_tokens, row.output_tokens,
                BigDecimal(row.cost_micro_usd).movePointLeft(6), row.succeeded == 1L,
            )
        }
    }.sortedByDescending { it.usedAt }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        /** Document types whose readings can become a statement (OCR-09). */
        val STATEMENT_TYPES = setOf("bank_statement", "card_statement")
    }
}
