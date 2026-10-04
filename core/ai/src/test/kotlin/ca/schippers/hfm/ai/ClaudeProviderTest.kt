package ca.schippers.hfm.ai

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The request the SDK sends and how answers and errors come back, against a local server playing
 * the Messages API (no network, no real key).
 */
class ClaudeProviderTest {

    private class Seen(val headers: Map<String, String>, val body: JsonObject)

    private val seen = CopyOnWriteArrayList<Seen>()
    private var server: HttpServer? = null

    @AfterEach
    fun stop() {
        server?.stop(0)
    }

    private fun serve(status: Int, body: String): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { ex ->
            val headers = ex.requestHeaders.entries.associate { it.key.lowercase() to it.value.joinToString(",") }
            seen += Seen(headers, Json.parseToJsonElement(ex.requestBody.readBytes().decodeToString()).jsonObject)
            val bytes = body.encodeToByteArray()
            ex.responseHeaders.add("content-type", "application/json")
            ex.responseHeaders.add("x-should-retry", "false")
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        s.start()
        server = s
        return "http://127.0.0.1:${s.address.port}"
    }

    private fun reply(text: String, stop: String = "end_turn", model: String = "claude-opus-5-5") = """
        {"id":"msg_1","type":"message","role":"assistant","model":"$model",
         "content":[{"type":"text","text":${Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(text))}}],
         "stop_reason":"$stop","stop_sequence":null,
         "usage":{"input_tokens":1500,"output_tokens":220,"cache_creation_input_tokens":0,"cache_read_input_tokens":0}}
    """.trimIndent()

    private val receipt = DocumentTypes.load().get("receipt")!!
    private val page = byteArrayOf(-1, -40, -1, -32, 1, 2, 3)

    private fun request(model: AiModel = AiModel.DEFAULT) = AiRequest(model, listOf(page), "instructions", "Read this", SchemaCheck.forApi(receipt.schema))

    @Test
    fun `the request carries the key, the page, the schema, effort and the fallback`() {
        val url = serve(200, reply("""{"merchant":"IGA","date":"2026-10-01","total":12.34,"currency":"CAD"}"""))
        val answer = ClaudeProvider("sk-test-key", url).read(request())
        assertEquals("claude-opus-5-5", answer.modelUsed)
        assertEquals(1500L to 220L, answer.inputTokens to answer.outputTokens)
        assertTrue("\"total\":12.34" in answer.text)

        val s = seen.single()
        assertEquals("sk-test-key", s.headers["x-api-key"])
        assertTrue("server-side-fallback-2026-07-01" in (s.headers["anthropic-beta"] ?: ""), s.headers.toString())
        assertEquals("claude-opus-5-5", s.body["model"]!!.jsonPrimitive.content)
        assertEquals("default", s.body["fallbacks"]!!.jsonPrimitive.content)
        assertEquals("instructions", s.body["system"]!!.jsonPrimitive.content)
        val content = s.body["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray
        val image = content[0].jsonObject
        assertEquals("image", image["type"]!!.jsonPrimitive.content)
        assertEquals("image/jpeg", image["source"]!!.jsonObject["media_type"]!!.jsonPrimitive.content)
        assertEquals(Base64.getEncoder().encodeToString(page), image["source"]!!.jsonObject["data"]!!.jsonPrimitive.content)
        assertEquals("Read this", content[1].jsonObject["text"]!!.jsonPrimitive.content)
        val output = s.body["output_config"]!!.jsonObject
        assertEquals("medium", output["effort"]!!.jsonPrimitive.content)
        val format = output["format"]!!.jsonObject
        assertEquals("json_schema", format["type"]!!.jsonPrimitive.content)
        assertEquals(SchemaCheck.forApi(receipt.schema), format["schema"]!!.jsonObject)
        assertTrue("\$id" !in format["schema"]!!.jsonObject, "metadata is not sent")
    }

    @Test
    fun `Haiku gets neither effort nor fallback`() {
        val url = serve(200, reply("{}", model = "claude-haiku-4-5"))
        ClaudeProvider("k", url).read(request(AiModel.byId("claude-haiku-4-5")))
        val s = seen.single()
        assertTrue("fallbacks" !in s.body)
        assertTrue("effort" !in s.body["output_config"]!!.jsonObject)
        assertTrue("server-side-fallback" !in (s.headers["anthropic-beta"] ?: ""))
    }

    @Test
    fun `refusals, long documents and errors are told apart`() {
        assertEquals(AiFailure.Reason.REFUSED, assertFailsWith<AiFailure> { ClaudeProvider("k", serve(200, reply("", "refusal"))).read(request()) }.reason)
        stop()
        assertEquals(AiFailure.Reason.TOO_LONG, assertFailsWith<AiFailure> { ClaudeProvider("k", serve(200, reply("{", "max_tokens"))).read(request()) }.reason)
        stop()
        val error = """{"type":"error","error":{"type":"%s","message":"%s"}}"""
        assertEquals(AiFailure.Reason.KEY, assertFailsWith<AiFailure> { ClaudeProvider("bad", serve(401, error.format("authentication_error", "invalid x-api-key"))).read(request()) }.reason)
        stop()
        assertEquals(AiFailure.Reason.LIMIT, assertFailsWith<AiFailure> { ClaudeProvider("k", serve(429, error.format("rate_limit_error", "slow down"))).read(request()) }.reason)
        stop()
        assertEquals(
            AiFailure.Reason.LIMIT,
            assertFailsWith<AiFailure> { ClaudeProvider("k", serve(400, error.format("invalid_request_error", "Your credit balance is too low"))).read(request()) }.reason,
        )
        stop()
        val closed = java.net.ServerSocket(0).use { it.localPort }
        val started = System.nanoTime()
        assertEquals(AiFailure.Reason.NETWORK, assertFailsWith<AiFailure> { ClaudeProvider("k", "http://127.0.0.1:$closed", java.time.Duration.ofSeconds(2)).read(request()) }.reason)
        assertTrue(System.nanoTime() - started < 30_000_000_000L, "an unreachable service is reported within seconds")
    }
}
