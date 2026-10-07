package ca.schippers.hfm.ai

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.DocumentKind
import ca.schippers.hfm.ocr.FieldSource
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** INV-05 and AI-03: trade confirmations, investment statement version 2, and types added by the user. */
class AiTypesTest {

    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
    private val types = DocumentTypes.load()

    private val confirmation = """
        {"institution":"TD Direct Investing","account_number_last_digits":"5512","confirmation_number":"C-1","currency":"CAD",
         "trades":[
           {"action":"buy","trade_date":"2026-10-01","settlement_date":"2026-10-02","symbol":"XEQT","description":"iShares Core Equity ETF Portfolio",
            "quantity":100,"price":34.55,"gross_amount":3455.00,"commission":9.95,"net_amount":3464.95},
           {"action":"sell","trade_date":"2026-10-01","settlement_date":"2026-10-02","symbol":"ZAG","description":"BMO Aggregate Bond Index ETF",
            "quantity":50,"price":13.9025,"gross_amount":695.13,"commission":9.95,"other_fees":0.03,"net_amount":685.15}]}
    """

    @Test
    fun `a trade confirmation is a shipped type that suits the API`() {
        val type = types.get("trade_confirmation")!!
        assertEquals("hfm/trade_confirmation/v1", type.version)
        assertEquals(DocumentKind.TRADE_CONFIRMATION, type.kind)
        assertEquals(emptyList(), SchemaCheck.problems(type.schema))
        assertEquals(emptyList(), SchemaCheck.validate(obj(confirmation), type.schema))
        assertEquals("hfm/investment_statement/v2", types.get("investment_statement")!!.version)
    }

    @Test
    fun `a bill (version 2) gives its statement number and a utility's meter readings (BILL-19)`() {
        val type = types.get("bill")!!
        assertEquals("hfm/bill/v2", type.version)
        assertTrue(SchemaCheck.schemaProblems(type.schema).isEmpty())
        val answer = obj(
            """{"biller":"Hydro Ottawa","account_number":"6 1234 5678 9","statement_number":"2026-0914","bill_date":"2026-09-14","due_date":"2026-10-06",
                "amount_due":138.91,"currency":"CAD","meter_readings":{"previous_reading":45678,"previous_reading_date":"2026-08-12",
                "current_reading":46321,"current_reading_date":"2026-09-11","amount_used":643,"unit":"kWh"}}""",
        )
        assertTrue(SchemaCheck.validate(answer, type.schema).isEmpty())
        val draft = AiFields.draft("bill", answer, checked = true)
        assertEquals("2026-0914", draft.invoiceNumber?.value)
        assertEquals("6 1234 5678 9", draft.accountNumber?.value)
        assertEquals(LocalDate(2026, 9, 14), draft.date?.value)
        val m = draft.meter!!
        assertEquals(FieldSource.CLOUD_AI, m.source)
        assertEquals(java.math.BigDecimal(45678), m.value.previous)
        assertEquals(LocalDate(2026, 9, 11), m.value.currentDate)
        assertEquals(java.math.BigDecimal(643), m.value.used)
        assertEquals("KWH", m.value.unit)
        // A phone bill has no readings.
        assertEquals(null, AiFields.draft("bill", obj("""{"biller":"Bell","amount_due":95.00,"currency":"CAD"}"""), checked = true).meter)
    }

    @Test
    fun `a trade's units, price, fees and net amount must agree`() {
        val answer = obj(confirmation)
        assertTrue(AiFields.hasSums("trade_confirmation", answer))
        assertEquals(emptyList(), AiFields.arithmetic("trade_confirmation", answer), "a rounded price is allowed")
        val wrongNet = obj(confirmation.replace("3464.95", "3445.05"))
        assertTrue(AiFields.arithmetic("trade_confirmation", wrongNet).single().startsWith("trade 1 gross plus fees"), AiFields.arithmetic("trade_confirmation", wrongNet).toString())
        val wrongUnits = obj(confirmation.replace("\"quantity\":100", "\"quantity\":10"))
        assertTrue(AiFields.arithmetic("trade_confirmation", wrongUnits).any { it.startsWith("trade 1 units x price") })
        // A sale subtracts its fees.
        val sale = obj(confirmation.replace("685.15", "705.11"))
        assertTrue(AiFields.arithmetic("trade_confirmation", sale).single().startsWith("trade 2 gross less fees"))
    }

    @Test
    fun `a trade confirmation becomes review fields`() {
        val draft = AiFields.draft("trade_confirmation", obj(confirmation), checked = true)
        assertEquals(DocumentKind.TRADE_CONFIRMATION, draft.kind)
        assertEquals("TD Direct Investing", draft.merchant!!.value)
        assertEquals(LocalDate(2026, 10, 1), draft.date!!.value)
        assertEquals(Money.parse("4150.10", Currency.CAD), draft.total!!.value)
        assertEquals("C-1", draft.invoiceNumber!!.value)
        assertEquals(FieldSource.CLOUD_AI, draft.total!!.source)
    }

    @Test
    fun `an investment statement's opening cash and activity must reach its cash balance`() {
        val type = types.get("investment_statement")!!
        val good = obj(
            """{"institution":"TD","period_start":"2026-10-01","period_end":"2026-10-31","currency":"CAD","opening_cash_balance":10000.00,"cash_balance":6552.05,
                "total_value":10012.05,"holdings":[{"symbol":"XEQT","description":"iShares Core Equity ETF Portfolio","quantity":100,"price":34.60,"market_value":3460.00}],
                "activity":[{"date":"2026-10-01","settlement_date":"2026-10-02","type":"buy","symbol":"XEQT","quantity":100,"price":34.55,"commission":9.95,"amount":-3464.95},
                            {"date":"2026-10-15","type":"dividend","symbol":"XEQT","amount":17.00,"tax_withheld":3.00}]}""",
        )
        assertEquals(emptyList(), SchemaCheck.validate(good, type.schema))
        assertEquals(emptyList(), AiFields.arithmetic("investment_statement", good))
        val bad = obj(good.toString().replace("6552.05", "6652.05").replace("10012.05", "10112.05"))
        assertEquals(1, AiFields.arithmetic("investment_statement", bad).size, AiFields.arithmetic("investment_statement", bad).toString())
    }

    @Test
    fun `a type added by the user shows every field it read, named by its schema`() {
        val folder = Files.createTempDirectory("ai-types")
        try {
            Files.writeString(
                folder.resolve("property_tax.json"),
                """{"${'$'}id":"user/property_tax/v1","type":"object","additionalProperties":false,"required":["municipality"],
                    "properties":{"municipality":{"type":"string","title":"Municipality"},"roll_number":{"type":"string"},
                    "notice_date":{"type":"string","format":"date"},"amount_due":{"type":"number"},"currency":{"type":"string"},
                    "instalments":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["due","amount"],
                       "properties":{"due":{"type":"string","format":"date"},"amount":{"type":"number"}}}}}}""",
            )
            val type = DocumentTypes.load(folder).get("property_tax")!!
            assertEquals(null, type.kind)
            val answer = obj(
                """{"amount_due":4120.50,"municipality":"Ville de Gatineau","roll_number":"1234-56","notice_date":"2026-02-15","currency":"CAD",
                    "instalments":[{"due":"2026-03-15","amount":2060.25},{"due":"2026-06-15","amount":2060.25}],"extra":"kept"}""",
            )
            val fields = AiFields.fields(answer, type.schema)
            assertEquals(
                listOf(
                    AiField("Municipality", "Ville de Gatineau"), AiField("Roll number", "1234-56"), AiField("Notice date", "2026-02-15"),
                    AiField("Amount due", "4120.50"), AiField("Currency", "CAD"),
                    AiField("Instalments 1", "Due: 2026-03-15 · Amount: 2060.25"), AiField("Instalments 2", "Due: 2026-06-15 · Amount: 2060.25"),
                    AiField("Extra", "kept"),
                ),
                fields,
                "in the schema's order, then anything else; amounts exactly as written",
            )
            assertTrue("Roll number: 1234-56" in AiFields.text(fields))

            val draft = AiFields.draft("property_tax", answer, checked = false, schema = type.schema)
            assertEquals(DocumentKind.OTHER, draft.kind)
            assertEquals(LocalDate(2026, 2, 15), draft.date!!.value)
            assertEquals(Money.parse("4120.50", Currency.CAD), draft.total!!.value)
            assertEquals(null, draft.merchant, "no field name says who sent it")
            // Without its schema (the file was removed), the fields are still listed by name.
            assertEquals("Municipality", AiFields.fields(answer).first { it.value == "Ville de Gatineau" }.label)
        } finally {
            folder.toFile().deleteRecursively()
        }
    }
}
