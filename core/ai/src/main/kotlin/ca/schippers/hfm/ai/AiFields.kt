package ca.schippers.hfm.ai

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.DocumentDraft
import ca.schippers.hfm.ocr.DocumentKind
import ca.schippers.hfm.ocr.Extracted
import ca.schippers.hfm.ocr.FieldSource
import ca.schippers.hfm.ocr.MeterReadings
import ca.schippers.hfm.ocr.ReadInstalment
import ca.schippers.hfm.ocr.ReadNumbers
import ca.schippers.hfm.ocr.TaxName
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.math.BigDecimal

/**
 * AI-05: checks that the amounts in an answer add up, as a careful reader would, and turns an
 * answer into the review inbox's fields, each marked as read by AI (section 4.5, step 5).
 */
object AiFields {

    /** Rounding allowed per sum: two cents, or half a cent per added line, whichever is larger. */
    private fun tolerance(lines: Int) = BigDecimal("0.02").max(BigDecimal("0.005").multiply(BigDecimal(lines)))

    /** Confidence given to AI-read fields: checked sums are trusted more. */
    const val CHECKED = 0.95f
    const val UNCHECKED = 0.88f

    /** Why the amounts in [answer] do not add up; empty when they do or cannot be checked. */
    fun arithmetic(typeId: String, answer: JsonObject): List<String> = arithmeticProblems(typeId, answer).map { it.english }

    /** [arithmetic] as problems the screen can show in the user's language (key `aiProblem.sum.<check>`). */
    fun arithmeticProblems(typeId: String, answer: JsonObject): List<AiProblem> = examine(typeId, answer).second

    /** The English name of each sum checked, for the provider and the log. */
    private val SUMS = mapOf(
        "linesSubtotal" to "line items vs. subtotal", "subtotalTotal" to "subtotal + taxes vs. total", "linesTotal" to "line items vs. total",
        "amountDue" to "balance vs. amount due", "newBalance" to "transactions vs. new balance", "closingBalance" to "transactions vs. closing balance",
        "netPay" to "gross less deductions vs. net pay", "grossPay" to "earnings vs. gross pay", "totalPaid" to "lines vs. total paid",
        "totalValue" to "holdings vs. total value", "openingCash" to "opening cash and activity vs. cash balance",
    )

    private fun sumProblem(check: String, sum: BigDecimal, total: BigDecimal) =
        AiProblem("sum.$check", "${SUMS.getValue(check)}: ${sum.toPlainString()} instead of ${total.toPlainString()}", sum.toPlainString(), total.toPlainString())

    /** Whether [answer] has any sum that could be checked. */
    fun hasSums(typeId: String, answer: JsonObject): Boolean = examine(typeId, answer).first

    private fun examine(typeId: String, answer: JsonObject): Pair<Boolean, List<AiProblem>> {
        val out = ArrayList<AiProblem>()
        var compared = false
        fun expect(check: String, parts: List<BigDecimal>, total: BigDecimal?) {
            if (total == null || parts.isEmpty()) return
            compared = true
            val sum = parts.fold(BigDecimal.ZERO, BigDecimal::add)
            if ((sum - total).abs() > tolerance(parts.size)) out += sumProblem(check, sum, total)
        }
        val lines = answer.list("line_items").mapNotNull { it.num("amount") }
        val taxes = answer.list("taxes").mapNotNull { it.num("amount") }
        when (typeId) {
            "receipt", "invoice" -> {
                val subtotal = answer.num("subtotal")
                if (subtotal != null) {
                    expect("linesSubtotal", lines, subtotal)
                    expect("subtotalTotal", listOf(subtotal) + taxes + listOfNotNull(answer.num("tip")), answer.num("total"))
                } else if (taxes.isEmpty()) {
                    expect("linesTotal", lines, answer.num("total"))
                }
            }
            "bill" -> {
                val previous = answer.num("previous_balance")
                val paid = answer.num("payments_received")
                val charges = answer.num("new_charges")
                if (previous != null && paid != null && charges != null) expect("amountDue", listOf(previous, paid.negate(), charges), answer.num("amount_due"))
            }
            "card_statement" -> answer.num("previous_balance")?.let { previous ->
                expect("newBalance", listOf(previous) + answer.list("transactions").mapNotNull { it.num("amount") }, answer.num("new_balance"))
            }
            "bank_statement" -> answer.num("opening_balance")?.let { opening ->
                expect("closingBalance", listOf(opening) + answer.list("transactions").mapNotNull { it.num("amount") }, answer.num("closing_balance"))
            }
            "pay_stub" -> answer.num("gross_pay")?.let { gross ->
                expect("netPay", listOf(gross) + answer.list("deductions").mapNotNull { it.num("amount")?.negate() }, answer.num("net_pay"))
                val earnings = answer.list("earnings").mapNotNull { it.num("amount") }
                expect("grossPay", earnings, gross)
            }
            "eob" -> expect("totalPaid", answer.list("lines").mapNotNull { it.num("amount_paid") }, answer.num("total_paid"))
            "trade_confirmation" -> for ((i, t) in answer.list("trades").withIndex()) {
                val quantity = t.num("quantity")
                val price = t.num("price")
                val gross = t.num("gross_amount")
                val net = t.num("net_amount")
                val fees = listOfNotNull(t.num("commission"), t.num("other_fees"))
                val sell = t.str("action") == "sell"
                val label = "trade ${i + 1}"
                val number = (i + 1).toString()
                // Prices are often rounded on the page: allow two cents, or 0.05 % of the trade.
                fun near(a: BigDecimal, b: BigDecimal) = (a - b).abs() <= BigDecimal("0.02").max(b.abs().multiply(BigDecimal("0.0005")))
                if (quantity != null && price != null && gross != null) {
                    compared = true
                    val product = quantity.multiply(price)
                    if (!near(product, gross)) out += AiProblem("sum.tradeGross", "$label units x price vs. gross amount: ${product.stripTrailingZeros().toPlainString()} instead of ${gross.toPlainString()}", number, product.stripTrailingZeros().toPlainString(), gross.toPlainString())
                }
                val base = gross ?: if (quantity != null && price != null) quantity.multiply(price) else null
                if (base != null && net != null) {
                    compared = true
                    val feeSum = fees.fold(BigDecimal.ZERO, BigDecimal::add)
                    val expected = if (sell) base - feeSum else base + feeSum
                    if (!near(expected, net)) out += AiProblem(
                        if (sell) "sum.tradeNetSell" else "sum.tradeNetBuy",
                        "$label gross ${if (sell) "less" else "plus"} fees vs. net amount: ${expected.stripTrailingZeros().toPlainString()} instead of ${net.toPlainString()}",
                        number, expected.stripTrailingZeros().toPlainString(), net.toPlainString(),
                    )
                }
            }
            "investment_statement" -> {
                answer.num("opening_cash_balance")?.let { opening ->
                    expect("openingCash", listOf(opening) + answer.list("activity").mapNotNull { it.num("amount") }, answer.num("cash_balance"))
                }
                val values = answer.list("holdings").mapNotNull { it.num("market_value") } + listOfNotNull(answer.num("cash_balance"))
                val total = answer.num("total_value")
                // Statements round each value; allow a dollar, or 0.1 % of large accounts.
                if (total != null && values.isNotEmpty()) {
                    compared = true
                    val sum = values.fold(BigDecimal.ZERO, BigDecimal::add)
                    val allowed = BigDecimal.ONE.max(total.abs().multiply(BigDecimal("0.001")))
                    if ((sum - total).abs() > allowed) out += sumProblem("totalValue", sum, total)
                }
            }
        }
        return compared to out
    }

    /**
     * The fields the review inbox shows for an answer of type [typeId]. Statements, pay stubs and
     * explanations of benefits give their issuer, date and main amount; their lines are used by
     * the screens built for them.
     */
    fun draft(typeId: String, answer: JsonObject, checked: Boolean, schema: JsonObject? = null): DocumentDraft {
        val c = if (checked) CHECKED else UNCHECKED
        val currency = answer.str("currency")?.let { runCatching { Currency.of(it.uppercase()) }.getOrNull() } ?: Currency.CAD
        fun <T> ai(v: T?) = v?.let { Extracted(it, c, FieldSource.CLOUD_AI) }
        fun money(v: BigDecimal?) = v?.let { runCatching { Money.exact(it.setScale(currency.minorUnits, java.math.RoundingMode.HALF_UP), currency) }.getOrNull() }
        fun date(key: String) = answer.str(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val taxes = answer.list("taxes").mapNotNull { t ->
            val name = runCatching { TaxName.valueOf(t.str("name") ?: "") }.getOrDefault(TaxName.OTHER)
            money(t.num("amount"))?.let { name to Extracted(it, c, FieldSource.CLOUD_AI) }
        }
        val kind = DocumentType.kindFor(typeId) ?: DocumentKind.OTHER
        return when (typeId) {
            "receipt" -> DocumentDraft(
                kind, ai(answer.str("merchant")), ai(date("date")), ai(money(answer.num("total"))), ai(money(answer.num("subtotal"))), taxes, currency,
                ai(answer.str("payment_method")), ai(answer.str("card_last4")?.takeLast(4)), ai(answer.str("receipt_number")),
            )
            "bill" -> DocumentDraft(
                kind, ai(answer.str("biller")), ai(date("bill_date")), ai(money(answer.num("amount_due"))), null, taxes, currency,
                invoiceNumber = ai(answer.str("statement_number")), dueDate = ai(date("due_date")), accountNumber = ai(answer.str("account_number")),
                meter = ai(meterReadings(answer["meter_readings"])),
                instalments = ai(
                    answer.list("instalments").mapNotNull { i -> i.str("due_date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { d -> money(i.num("amount"))?.takeIf { it.isPositive }?.let { ReadInstalment(d, it) } } }
                        .distinctBy { it.dueDate }.sortedBy { it.dueDate }.take(ReadInstalment.MAX).takeIf { it.isNotEmpty() },
                ),
            )
            "invoice" -> DocumentDraft(
                kind, ai(answer.str("issuer")), ai(date("invoice_date")), ai(money(answer.num("total"))), ai(money(answer.num("subtotal"))), taxes, currency,
                invoiceNumber = ai(answer.str("invoice_number")), dueDate = ai(date("due_date")),
            )
            "card_statement" -> DocumentDraft(
                kind, ai(answer.str("issuer")), ai(date("period_end")), ai(money(answer.num("new_balance"))), currency = currency,
                cardLast4 = ai(answer.str("card_last4")?.takeLast(4)), dueDate = ai(date("payment_due_date")),
            )
            "bank_statement" -> DocumentDraft(kind, ai(answer.str("institution")), ai(date("period_end")), ai(money(answer.num("closing_balance"))), currency = currency)
            "investment_statement" -> DocumentDraft(kind, ai(answer.str("institution")), ai(date("period_end")), ai(money(answer.num("total_value"))), currency = currency)
            "pay_stub" -> DocumentDraft(kind, ai(answer.str("employer")), ai(date("pay_date")), ai(money(answer.num("net_pay"))), currency = currency)
            "eob" -> DocumentDraft(kind, ai(answer.str("insurer")), ai(date("statement_date")), ai(money(answer.num("total_paid"))), currency = currency)
            "trade_confirmation" -> {
                val trades = answer.list("trades")
                val nets = trades.mapNotNull { it.num("net_amount") }
                DocumentDraft(
                    kind, ai(answer.str("institution")), ai(trades.firstNotNullOfOrNull { it.str("trade_date") }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }),
                    ai(money(nets.takeIf { it.size == trades.size && it.isNotEmpty() }?.fold(BigDecimal.ZERO, BigDecimal::add))), currency = currency,
                    invoiceNumber = ai(answer.str("confirmation_number")),
                )
            }
            // AI-03: a type added by the user. Its name, date and amount are guessed from the field
            // names; every field is shown and kept as text ([fields]).
            else -> {
                val props = schema?.get("properties") as? JsonObject
                fun first(names: List<String>, ok: (String) -> Boolean) = names.firstOrNull { it in answer && ok(it) }
                val nameKey = first(NAME_KEYS) { answer.str(it) != null }
                val dateKey = props?.entries?.firstOrNull { (k, v) -> ((v as? JsonObject)?.get("format") as? JsonPrimitive)?.contentOrNull == "date" && date(k) != null }?.key
                    ?: answer.keys.firstOrNull { "date" in it && date(it) != null }
                val amountKey = first(AMOUNT_KEYS) { answer.num(it) != null }
                DocumentDraft(DocumentKind.OTHER, ai(nameKey?.let { answer.str(it) }), ai(dateKey?.let(::date)), ai(money(amountKey?.let { answer.num(it) })), currency = currency)
            }
        }
    }

    /** BILL-17, BILL-19: a bill's meter readings (schema hfm/bill/v2 and later), or null when it gave none. */
    private fun meterReadings(e: JsonElement?): MeterReadings? {
        val o = e as? JsonObject ?: return null
        fun date(key: String) = o.str(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val unit = when (o.str("unit")?.lowercase()) {
            "kwh" -> "KWH"
            "m3" -> "M3"
            else -> null
        }
        return MeterReadings(o.num("previous_reading"), date("previous_reading_date"), o.num("current_reading"), date("current_reading_date"), o.num("amount_used"), unit)
            .takeUnless { it.isEmpty }
    }

    private val NAME_KEYS = listOf("merchant", "issuer", "institution", "biller", "vendor", "payee", "employer", "insurer", "company", "provider", "sender", "name", "title")
    private val AMOUNT_KEYS = listOf("total", "amount_due", "total_amount", "amount", "total_paid", "net_pay", "balance", "total_value", "value")

    /**
     * AI-03: every value in [answer], in the order of [schema] when it is known, as a label and a
     * text, to show and search a reading of a type the app has no screen for. Labels are the
     * schema's titles, else the field names; a list of objects gives one line per item.
     */
    fun fields(answer: JsonObject, schema: JsonObject? = null): List<AiField> {
        val out = ArrayList<AiField>()
        walk(answer, schema?.get("properties") as? JsonObject, "", out)
        return out
    }

    /** The searchable text of [fields], one "label: value" per line. */
    fun text(fields: List<AiField>): String = fields.joinToString("\n") { "${it.label}: ${it.value}" }

    private fun walk(obj: JsonObject, props: JsonObject?, prefix: String, out: MutableList<AiField>) {
        for ((k, v) in ordered(obj, props)) {
            val def = props?.get(k) as? JsonObject
            val name = prefix + label(k, def)
            when {
                v is JsonArray && v.any { it is JsonObject } ->
                    v.forEachIndexed { i, item -> valueText(item, def?.get("items") as? JsonObject)?.let { out += AiField("$name ${i + 1}", it) } }
                v is JsonObject -> walk(v, def?.get("properties") as? JsonObject, "$name › ", out)
                else -> valueText(v, def)?.let { out += AiField(name, it) }
            }
        }
    }

    private fun label(key: String, def: JsonObject?): String = (def?.get("title") as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }

    private fun valueText(e: JsonElement, def: JsonObject?): String? = when (e) {
        is JsonPrimitive -> e.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        is JsonArray -> e.mapNotNull { valueText(it, def?.get("items") as? JsonObject) }.joinToString(", ").ifEmpty { null }
        is JsonObject -> {
            val inner = def?.get("properties") as? JsonObject
            ordered(e, inner).mapNotNull { (k, v) -> valueText(v, inner?.get(k) as? JsonObject)?.let { "${label(k, inner?.get(k) as? JsonObject)}: $it" } }
                .joinToString(" · ").ifEmpty { null }
        }
    }

    /** The fields of [obj] in the schema's order, then any others. */
    private fun ordered(obj: JsonObject, props: JsonObject?): List<Pair<String, JsonElement>> {
        val known = props?.keys.orEmpty().filter { it in obj }
        return (known + obj.keys.filter { it !in known }).map { it to obj.getValue(it) }
    }

    private fun JsonElement.str(key: String): String? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    /** A number exactly as written in the answer, never through floating point. */
    private fun JsonElement.num(key: String): BigDecimal? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.content?.let(ReadNumbers::parse)

    private fun JsonObject.list(key: String): List<JsonElement> = (this[key] as? JsonArray) ?: emptyList()
}

/** AI-03: one value of a reading, labelled for the screen. */
data class AiField(val label: String, val value: String)
