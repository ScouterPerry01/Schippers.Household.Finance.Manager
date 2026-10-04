package ca.schippers.hfm.ai

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.DocumentKind
import ca.schippers.hfm.ocr.FieldSource
import ca.schippers.hfm.ocr.TaxName
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.math.BigDecimal
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** AI-03, AI-05, AI-06: document types, checks on answers, retries and the usage log, without a network. */
class AiReadingTest {

    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
    private val types = DocumentTypes.load()
    private val receipt = types.get("receipt")!!

    private val goodReceipt = """
        {"merchant":"IGA Extra","date":"2026-09-06","currency":"CAD",
         "line_items":[{"description":"LAIT 2%","amount":6.49},{"description":"POULET","amount":17.98}],
         "subtotal":24.47,"taxes":[{"name":"GST","amount":0.00},{"name":"QST","amount":0.00}],"total":24.47,
         "payment_method":"credit","card_last4":"1234"}
    """

    @Test
    fun `every shipped type loads and suits the API`() {
        assertEquals(DocumentTypes.BUILT_IN, types.types.map { it.id })
        for (t in types.types) {
            assertEquals(emptyList(), SchemaCheck.problems(t.schema), t.id)
            assertTrue(t.version.startsWith("hfm/${t.id}/v"), t.version)
            assertTrue(t.instructions.isNotBlank())
            assertNotEquals(null, t.kind, t.id)
        }
        assertTrue("YYYY-MM-DD" in DocumentTypes.common)
    }

    @Test
    fun `answers are checked against the schema`() {
        assertEquals(emptyList(), SchemaCheck.validate(obj(goodReceipt), receipt.schema))
        val bad = SchemaCheck.validate(obj("""{"merchant":"X","date":"06/09/2026","total":"12,34","colour":"red","taxes":[{"name":"VAT","amount":1}]}"""), receipt.schema)
        assertTrue(bad.any { "\"currency\" is missing" in it }, bad.toString())
        assertTrue(bad.any { "not a date" in it }, bad.toString())
        assertTrue(bad.any { "$.total: expected number" in it }, bad.toString())
        assertTrue(bad.any { "\"colour\" is not allowed" in it }, bad.toString())
        assertTrue(bad.any { "$.taxes[0].name" in it }, bad.toString())
    }

    @Test
    fun `limits the API does not take are removed before sending and checked here`() {
        val schema = obj(
            """{"${'$'}id":"user/tip/v1","title":"tip","type":"object","additionalProperties":false,"required":["percent"],
                "properties":{"percent":{"type":"number","minimum":0,"maximum":30},"note":{"type":"string","maxLength":5}}}""",
        )
        val api = SchemaCheck.forApi(schema)
        assertTrue("minimum" !in api.toString() && "maxLength" !in api.toString() && "title" !in api && "\$id" !in api)
        assertEquals(listOf("$.percent: above 30.0"), SchemaCheck.validate(obj("""{"percent":45}"""), schema))
        assertEquals(1, SchemaCheck.validate(obj("""{"percent":5,"note":"too long"}"""), schema).size)
        val open = SchemaCheck.problems(obj("""{"type":"object","properties":{"a":{"${'$'}ref":"#/x"}}}"""))
        assertTrue(open.any { "additionalProperties" in it } && open.any { "references" in it }, open.toString())
    }

    @Test
    fun `a user's folder adds and replaces types, and bad files are listed`() {
        val dir = Files.createTempDirectory("ai-types")
        try {
            Files.writeString(dir.resolve("donation.json"), """{"type":"object","additionalProperties":false,"required":["charity"],"properties":{"charity":{"type":"string"}}}""")
            Files.writeString(dir.resolve("donation.txt"), "An official donation receipt.")
            Files.writeString(dir.resolve("receipt.json"), types.get("receipt")!!.schema.toString().replace("hfm/receipt/v1", "user/receipt/v2"))
            Files.writeString(dir.resolve("Bad Name.json"), "{}")
            Files.writeString(dir.resolve("open.json"), """{"type":"object","properties":{}}""")
            val loaded = DocumentTypes.load(dir)
            assertEquals("An official donation receipt.", loaded.get("donation")!!.instructions)
            assertEquals("user/receipt/v2", loaded.get("receipt")!!.version)
            assertEquals(false, loaded.get("receipt")!!.builtIn)
            assertEquals(types.get("receipt")!!.instructions, loaded.get("receipt")!!.instructions, "the shipped instruction stays without a .txt")
            assertEquals(setOf("Bad Name.json", "open.json"), loaded.rejected.map { it.file }.toSet())
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `sums that do not add up are found`() {
        assertEquals(emptyList(), AiFields.arithmetic("receipt", obj(goodReceipt)))
        val wrong = AiFields.arithmetic("receipt", obj("""{"line_items":[{"description":"a","amount":10}],"subtotal":10,"taxes":[{"name":"HST","amount":1.30}],"total":12.30}"""))
        assertEquals(1, wrong.size, wrong.toString())
        val statement = obj("""{"opening_balance":1000.00,"closing_balance":850.50,"transactions":[{"date":"2026-09-01","description":"a","amount":-200},{"date":"2026-09-02","description":"b","amount":50.50}]}""")
        assertEquals(emptyList(), AiFields.arithmetic("bank_statement", statement))
        val stub = obj("""{"gross_pay":3000,"net_pay":2100,"deductions":[{"kind":"income_tax_federal","description":"Impôt fédéral","amount":600},{"kind":"cpp","description":"RPC","amount":180}]}""")
        assertEquals(1, AiFields.arithmetic("pay_stub", stub).size, "2220 would be right")
        assertEquals(false, AiFields.hasSums("receipt", obj("""{"merchant":"x","total":5}""")))
    }

    @Test
    fun `answers become review fields marked as read by AI, amounts exact`() {
        val draft = AiFields.draft("receipt", obj(goodReceipt), checked = true)
        assertEquals(DocumentKind.RECEIPT, draft.kind)
        assertEquals("IGA Extra", draft.merchant!!.value)
        assertEquals(LocalDate(2026, 9, 6), draft.date!!.value)
        assertEquals(Money.parse("24.47", Currency.CAD), draft.total!!.value)
        assertEquals(FieldSource.CLOUD_AI, draft.total!!.source)
        assertEquals(AiFields.CHECKED, draft.total!!.confidence)
        assertEquals(listOf(TaxName.GST, TaxName.QST), draft.taxes.map { it.first })
        assertEquals("1234", draft.cardLast4!!.value)
        val usd = AiFields.draft("bill", obj("""{"biller":"AWS","amount_due":19.999,"currency":"usd","due_date":"2026-10-20"}"""), checked = false)
        assertEquals(Money.parse("20.00", Currency.USD), usd.total!!.value)
        assertEquals(LocalDate(2026, 10, 20), usd.dueDate!!.value)
        assertEquals(DocumentKind.CARD_STATEMENT, AiFields.draft("card_statement", obj("""{"issuer":"Visa","new_balance":10}"""), true).kind)
    }

    private class Scripted(vararg answers: String) : AiProvider {
        private val queue = ArrayDeque(answers.toList())
        val tasks = ArrayList<String>()
        override val id = "test"
        override fun read(request: AiRequest): AiReply {
            tasks += request.task
            return AiReply(queue.removeFirst(), "claude-opus-5-5", 2000, 300)
        }
    }

    @Test
    fun `a wrong answer is asked again once, saying what was wrong, and every request is logged`() {
        val provider = Scripted("""{"merchant":"IGA","date":"2026-09-06","currency":"CAD","subtotal":20,"taxes":[],"total":24.47}""", goodReceipt)
        val logged = ArrayList<AiUsage>()
        val reading = AiReader(provider).read(receipt, listOf(byteArrayOf(1)), AiModel.DEFAULT) { logged += it }
        assertEquals("IGA Extra", reading.draft.merchant!!.value)
        assertTrue(reading.checked)
        assertEquals(2, logged.size)
        assertEquals(listOf(false, true), logged.map { it.succeeded })
        assertTrue("subtotal + taxes vs. total" in provider.tasks[1], provider.tasks[1])
        // 2000 input tokens at $4 and 300 output at $20 per million.
        assertEquals(BigDecimal("0.0140"), logged[0].costUsd)
    }

    @Test
    fun `two wrong answers give up so the user enters the fields`() {
        val logged = ArrayList<AiUsage>()
        val failure = assertFailsWith<AiFailure> { AiReader(Scripted("not json", """{"merchant":"x"}""")).read(receipt, listOf(byteArrayOf(1)), AiModel.DEFAULT) { logged += it } }
        assertEquals(AiFailure.Reason.INVALID, failure.reason)
        assertEquals(2, logged.size, "both requests are billed and logged")
    }

    @Test
    fun `blurred areas are flattened before the page is encoded`() {
        val page = BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB).also { img ->
            img.createGraphics().apply {
                color = Color.WHITE; fillRect(0, 0, 400, 200)
                color = Color.BLACK; font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.BOLD, 28); drawString("4540 1234 5678 9012", 20, 60)
                dispose()
            }
        }
        val sent = AiImages.apply(page, PageEdit(blur = listOf(Rectangle(10, 30, 380, 40))))
        val inside = (10 until 390).flatMap { x -> (30 until 70).map { y -> sent.getRGB(x, y) } }.toSet()
        assertTrue(inside.size <= 40, "only a few flat blocks remain: ${inside.size} colours")
        val cropped = AiImages.apply(page, PageEdit(crop = Rectangle(0, 0, 200, 100)))
        assertEquals(200 to 100, cropped.width to cropped.height)
        val jpeg = AiImages.jpeg(AiImages.apply(BufferedImage(5000, 2500, BufferedImage.TYPE_INT_RGB), PageEdit()))
        val decoded = ImageIO.read(jpeg.inputStream())
        assertEquals(AiImages.MAX_SIDE to 1000, decoded.width to decoded.height)
    }

    @Test
    fun `the key store keeps and forgets a key`() {
        val store = SecretStore.forThisComputer()
        val name = "RANN's Roost/ai/test/" + java.util.UUID.randomUUID()
        try {
            store.put(name, "sk-ant-test-é")
        } catch (e: SecretStoreException) {
            // A Linux machine without a running keyring (such as a CI runner) refuses; the app then keeps the key for the session.
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "no keyring: ${e.message}")
        }
        try {
            assertEquals("sk-ant-test-é", store.get(name))
        } finally {
            store.delete(name)
        }
        assertEquals(null, store.get(name))
        if (System.getProperty("os.name").lowercase().startsWith("windows")) assertEquals(SecretStore.Kind.WINDOWS, store.kind)
    }
}
