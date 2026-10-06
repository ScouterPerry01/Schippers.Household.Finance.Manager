package ca.schippers.hfm.ai

import ca.schippers.hfm.ocr.DocumentDraft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal

/** One request to a provider, for the usage log (AI-06). */
data class AiUsage(
    val provider: String,
    val model: String,
    val documentType: String,
    val inputTokens: Long,
    val outputTokens: Long,
    /** Estimated, in US dollars. */
    val costUsd: BigDecimal,
    val succeeded: Boolean,
)

/** A checked reading: the answer, the review fields made from it, and what it cost. */
class AiReading(
    val type: DocumentType,
    val answer: JsonObject,
    val draft: DocumentDraft,
    /** Whether the amounts were checked and add up (some documents have nothing to check). */
    val checked: Boolean,
    val usage: List<AiUsage>,
) {
    /** AI-03: every value read, labelled; what a type the app has no screen for shows and keeps. */
    val fields: List<AiField> get() = AiFields.fields(answer, type.schema)
}

/**
 * Runs one reading (section 4.5, steps 2 to 4): sends the pages with the type's instructions and
 * schema, checks the answer against the schema and its arithmetic (AI-05), and asks once more if
 * either fails, saying what was wrong. If the second answer fails too, it gives up with
 * [AiFailure.Reason.INVALID] so the user enters the fields by hand. Every request is passed to
 * [onUsage] as it completes, including failed ones, since each one is billed.
 */
class AiReader(private val provider: AiProvider) {

    private val json = Json { ignoreUnknownKeys = true }

    fun read(type: DocumentType, pages: List<ByteArray>, model: AiModel, onUsage: (AiUsage) -> Unit = {}): AiReading {
        require(pages.isNotEmpty() && pages.size <= MAX_PAGES) { "1 to $MAX_PAGES pages" }
        val usage = ArrayList<AiUsage>()
        var task = TASK.format(pages.size)
        var problems: List<String> = emptyList()
        repeat(2) { attempt ->
            val request = AiRequest(model, pages, DocumentTypes.common + "\n" + type.instructions, task, SchemaCheck.forApi(type.schema))
            val reply = provider.read(request)
            val price = AiModel.priceOf(reply.modelUsed, model)
            val answer = runCatching { json.parseToJsonElement(reply.text).jsonObject }.getOrNull()
            problems = if (answer == null) listOf("the answer is not a JSON object") else SchemaCheck.validate(answer, type.schema)
            val sums = if (answer != null && problems.isEmpty()) AiFields.arithmetic(type.id, answer) else emptyList()
            val ok = problems.isEmpty() && sums.isEmpty()
            AiUsage(provider.id, reply.modelUsed, type.id, reply.inputTokens, reply.outputTokens, price.cost(reply.inputTokens, reply.outputTokens), ok)
                .also { usage += it; onUsage(it) }
            if (ok) {
                val checkable = AiFields.hasSums(type.id, answer!!)
                return AiReading(type, answer, AiFields.draft(type.id, answer, checkable, type.schema), checkable, usage)
            }
            problems = problems + sums
            if (attempt == 0) task = TASK.format(pages.size) + "\n\n" + RETRY.format(problems.take(5).joinToString("\n- ", "- "))
        }
        throw AiFailure(AiFailure.Reason.INVALID, problems.take(5).joinToString("; "))
    }

    companion object {
        const val MAX_PAGES = 20
        private const val TASK = "Read this document (%d page(s)) and return its fields."
        private const val RETRY = "A first reading had these problems. Look again carefully, especially at the amounts:\n%s"
    }
}
