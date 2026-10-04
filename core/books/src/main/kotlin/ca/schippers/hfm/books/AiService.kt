package ca.schippers.hfm.books

import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.ocr.DocumentDraft
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
}
