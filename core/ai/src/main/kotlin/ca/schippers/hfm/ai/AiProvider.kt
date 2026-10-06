package ca.schippers.hfm.ai

import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A cloud service that reads document images into JSON that follows a schema (section 4.5,
 * AI-07). Only Claude exists at 1.0; another provider, or a local model, implements this.
 */
interface AiProvider {
    /** Stable id stored in the usage log, such as `anthropic`. */
    val id: String

    /** Sends the pages and the schema once; throws [AiFailure]. */
    fun read(request: AiRequest): AiReply

    /** Checks the key with a request that costs nothing; throws [AiFailure] when it does not work. */
    fun checkKey()
}

/** What is sent: page images (JPEG, already cropped and blurred by the user), and the instructions and schema. */
class AiRequest(
    val model: AiModel,
    val pages: List<ByteArray>,
    val instructions: String,
    val task: String,
    /** The schema as the API accepts it ([SchemaCheck.forApi]). */
    val schema: JsonObject,
)

/** The provider's answer before it is checked, with what it cost. */
class AiReply(val text: String, val modelUsed: String, val inputTokens: Long, val outputTokens: Long)

/** A model the user can choose, with its list price in US dollars per million tokens, for the usage log's estimate (AI-06). */
data class AiModel(
    val id: String,
    val label: String,
    val inputPerMillion: BigDecimal,
    val outputPerMillion: BigDecimal,
    /** Whether the model takes the effort setting. */
    val effort: Boolean,
    /** Whether a refused request may be answered by another model chosen by the service. */
    val fallbacks: Boolean,
) {
    /**
     * What a reading of [pages] (as sent, in pixels) is likely to cost, before sending: about one
     * input token per 750 pixels, the instructions and schema, and the answer, longer for statements.
     */
    fun estimate(pages: List<Pair<Int, Int>>, typeId: String): BigDecimal {
        val input = 1_500L + pages.sumOf { (w, h) -> w.toLong() * h / 750 }
        val perPage = if (typeId.endsWith("statement")) 2_500L else 500L
        return cost(input, 300L + perPage * pages.size)
    }

    /** Estimated cost in US dollars, from the list price; the provider's bill is what counts. */
    fun cost(inputTokens: Long, outputTokens: Long): BigDecimal =
        (inputPerMillion * BigDecimal(inputTokens) + outputPerMillion * BigDecimal(outputTokens)).divide(MILLION, 4, RoundingMode.HALF_UP)

    companion object {
        private val MILLION = BigDecimal(1_000_000)

        /** Claude models offered in settings, most capable first; Claude Opus 5.5 is the default. */
        val CLAUDE = listOf(
            AiModel("claude-opus-5-5", "Claude Opus 5.5", BigDecimal("4"), BigDecimal("20"), effort = true, fallbacks = true),
            AiModel("claude-sonnet-5-5", "Claude Sonnet 5.5", BigDecimal("2"), BigDecimal("10"), effort = true, fallbacks = false),
            AiModel("claude-haiku-4-5", "Claude Haiku 4.5", BigDecimal("1"), BigDecimal("5"), effort = false, fallbacks = false),
        )

        val DEFAULT: AiModel get() = CLAUDE.first()

        fun byId(id: String?): AiModel = CLAUDE.firstOrNull { it.id == id } ?: DEFAULT

        /** The price of the model that actually answered (a fallback may differ from the one asked). */
        fun priceOf(modelUsed: String, asked: AiModel): AiModel = CLAUDE.firstOrNull { modelUsed.startsWith(it.id) } ?: asked
    }
}

/**
 * A problem with an answer or a document type: [code] names its text (key `aiProblem.<code>`),
 * [args] fill it (the JSON path first), and [english] is what is sent back to the provider and logged.
 */
data class AiProblem(val code: String, val english: String, val args: List<String>) {
    constructor(code: String, english: String, vararg args: String) : this(code, english, args.toList())

    override fun toString() = english
}

/** Why a reading failed, so the screen can say what to do; [problems] for an answer that could not be checked. */
class AiFailure(val reason: Reason, detail: String? = null, cause: Throwable? = null, val problems: List<AiProblem> = emptyList()) : Exception(detail ?: reason.name, cause) {
    enum class Reason {
        /** The key is missing, wrong or revoked. */
        KEY,
        /** Too many requests or no credit left; try later. */
        LIMIT,
        /** The service is unreachable. */
        NETWORK,
        /** The service declined to read this document. */
        REFUSED,
        /** The answer did not follow the schema or its amounts did not add up, even after one retry. */
        INVALID,
        /** The document is too long for one request. */
        TOO_LONG,
        /** No pages, or more than [AiReader.MAX_PAGES]. */
        PAGES,
        /** Any other error from the service. */
        SERVICE,
    }
}
