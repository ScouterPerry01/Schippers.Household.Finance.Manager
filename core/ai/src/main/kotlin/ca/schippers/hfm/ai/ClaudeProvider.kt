package ca.schippers.hfm.ai

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.core.Timeout
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.time.Duration
import java.util.Base64

/**
 * Claude through Anthropic's Java SDK (section 4.5): one request per reading, with the page
 * images, the instructions and the schema as structured output, using the user's own key (AI-02).
 * With Claude Opus 5.5 a refused request may be answered by another model the service chooses
 * (server-side fallback); the usage log records the model that answered.
 */
class ClaudeProvider(
    private val apiKey: String,
    private val baseUrl: String? = null,
    /** How long to wait for the connection; an unreachable service is reported quickly. */
    private val connectTimeout: Duration = Duration.ofSeconds(10),
) : AiProvider {

    override val id = ID

    private fun client() = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .apply { baseUrl?.let { baseUrl(it) } }
        // A long statement can take minutes to read; reaching the service should not.
        .timeout(Timeout.builder().connect(connectTimeout).request(Duration.ofMinutes(5)).build())
        .maxRetries(2)
        .build()

    /** Lists the models, which costs nothing, to tell whether the key works. */
    override fun checkKey() {
        val client = client()
        try {
            client.models().list()
        } catch (e: Exception) {
            throw failure(e)
        } finally {
            client.close()
        }
    }

    override fun read(request: AiRequest): AiReply {
        val client = client()
        try {
            val blocks = request.pages.map { page ->
                ContentBlockParam.ofImage(
                    ImageBlockParam.builder()
                        .source(Base64ImageSource.builder().data(Base64.getEncoder().encodeToString(page)).mediaType(Base64ImageSource.MediaType.IMAGE_JPEG).build())
                        .build(),
                )
            } + ContentBlockParam.ofText(TextBlockParam.builder().text(request.task).build())
            val schema = JsonOutputFormat.Schema.builder().putAllAdditionalProperties(request.schema.mapValues { (_, v) -> JsonValue.from(plain(v)) }).build()
            val output = OutputConfig.builder().format(JsonOutputFormat.builder().schema(schema).build())
            if (request.model.effort) output.effort(OutputConfig.Effort.MEDIUM)
            val params = MessageCreateParams.builder()
                .model(request.model.id)
                .maxTokens(MAX_TOKENS)
                .system(request.instructions)
                .addUserMessageOfBlockParams(blocks)
                .outputConfig(output.build())
            if (request.model.fallbacks) {
                params.putAdditionalHeader("anthropic-beta", FALLBACK_BETA).putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            }
            val message = client.messages().create(params.build())
            when (message.stopReason().orElse(null)) {
                StopReason.REFUSAL -> throw AiFailure(AiFailure.Reason.REFUSED)
                StopReason.MAX_TOKENS, StopReason.MODEL_CONTEXT_WINDOW_EXCEEDED -> throw AiFailure(AiFailure.Reason.TOO_LONG)
                else -> {}
            }
            val text = message.content().flatMap { it.text().map { t -> listOf(t.text()) }.orElse(emptyList()) }.joinToString("")
            val usage = message.usage()
            val input = usage.inputTokens() + usage.cacheCreationInputTokens().orElse(0L) + usage.cacheReadInputTokens().orElse(0L)
            return AiReply(text, message.model().asString(), input, usage.outputTokens())
        } catch (e: Exception) {
            throw failure(e)
        } finally {
            client.close()
        }
    }

    /** What went wrong, for the screen (most specific error first). */
    private fun failure(e: Exception): AiFailure = when (e) {
        is AiFailure -> e
        is UnauthorizedException, is PermissionDeniedException -> AiFailure(AiFailure.Reason.KEY, cause = e)
        is RateLimitException -> AiFailure(AiFailure.Reason.LIMIT, cause = e)
        // No credit left is reported as a bad request; so is an image the service cannot read.
        is BadRequestException -> AiFailure(if (e.message?.contains("credit", ignoreCase = true) == true) AiFailure.Reason.LIMIT else AiFailure.Reason.SERVICE, e.message, e)
        is AnthropicServiceException -> AiFailure(AiFailure.Reason.SERVICE, e.message, e)
        is AnthropicIoException -> AiFailure(AiFailure.Reason.NETWORK, e.message, e)
        else -> AiFailure(AiFailure.Reason.SERVICE, e.message, e)
    }

    companion object {
        const val ID = "anthropic"

        /**
         * For the packaged self-check, without sending anything: the SDK's client and its libraries
         * load, and the Java runtime can make the TLS 1.3 connection with elliptic-curve keys.
         */
        fun selfCheck(): String {
            AnthropicOkHttpClient.builder().apiKey("self-check").build().close()
            javax.net.ssl.SSLContext.getInstance("TLSv1.3").init(null, null, null)
            java.security.KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
            java.security.KeyPairGenerator.getInstance("X25519").generateKeyPair()
            return "client ready, TLS 1.3"
        }
        private const val MAX_TOKENS = 16_000L
        private const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

        /** A JSON value as plain Kotlin values, which the SDK serializes. */
        internal fun plain(e: JsonElement): Any? = when (e) {
            is JsonObject -> e.mapValues { plain(it.value) }
            is JsonArray -> e.map { plain(it) }
            JsonNull -> null
            is JsonPrimitive -> when {
                e.isString -> e.content
                e.booleanOrNull != null -> e.booleanOrNull
                else -> e.content.toBigDecimal()
            }
        }
    }
}
