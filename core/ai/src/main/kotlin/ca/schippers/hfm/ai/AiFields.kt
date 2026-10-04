package ca.schippers.hfm.ai

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.DocumentDraft
import ca.schippers.hfm.ocr.DocumentKind
import ca.schippers.hfm.ocr.Extracted
import ca.schippers.hfm.ocr.FieldSource
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
    fun arithmetic(typeId: String, answer: JsonObject): List<String> = examine(typeId, answer).second

    /** Whether [answer] has any sum that could be checked. */
    fun hasSums(typeId: String, answer: JsonObject): Boolean = examine(typeId, answer).first

    private fun examine(typeId: String, answer: JsonObject): Pair<Boolean, List<String>> {
        val out = ArrayList<String>()
        var compared = false
        fun expect(label: String, parts: List<BigDecimal>, total: BigDecimal?) {
            if (total == null || parts.isEmpty()) return
            compared = true
            val sum = parts.fold(BigDecimal.ZERO, BigDecimal::add)
            if ((sum - total).abs() > tolerance(parts.size)) out += "$label: ${sum.toPlainString()} instead of ${total.toPlainString()}"
        }
        val lines = answer.list("line_items").mapNotNull { it.num("amount") }
        val taxes = answer.list("taxes").mapNotNull { it.num("amount") }
        when (typeId) {
            "receipt", "invoice" -> {
                val subtotal = answer.num("subtotal")
                if (subtotal != null) {
                    expect("line items vs. subtotal", lines, subtotal)
                    expect("subtotal + taxes vs. total", listOf(subtotal) + taxes + listOfNotNull(answer.num("tip")), answer.num("total"))
                } else if (taxes.isEmpty()) {
                    expect("line items vs. total", lines, answer.num("total"))
                }
            }
            "bill" -> {
                val previous = answer.num("previous_balance")
                val paid = answer.num("payments_received")
                val charges = answer.num("new_charges")
                if (previous != null && paid != null && charges != null) expect("balance vs. amount due", listOf(previous, paid.negate(), charges), answer.num("amount_due"))
            }
            "card_statement" -> answer.num("previous_balance")?.let { previous ->
                expect("transactions vs. new balance", listOf(previous) + answer.list("transactions").mapNotNull { it.num("amount") }, answer.num("new_balance"))
            }
            "bank_statement" -> answer.num("opening_balance")?.let { opening ->
                expect("transactions vs. closing balance", listOf(opening) + answer.list("transactions").mapNotNull { it.num("amount") }, answer.num("closing_balance"))
            }
            "pay_stub" -> answer.num("gross_pay")?.let { gross ->
                expect("gross less deductions vs. net pay", listOf(gross) + answer.list("deductions").mapNotNull { it.num("amount")?.negate() }, answer.num("net_pay"))
                val earnings = answer.list("earnings").mapNotNull { it.num("amount") }
                expect("earnings vs. gross pay", earnings, gross)
            }
            "eob" -> expect("lines vs. total paid", answer.list("lines").mapNotNull { it.num("amount_paid") }, answer.num("total_paid"))
            "investment_statement" -> {
                val values = answer.list("holdings").mapNotNull { it.num("market_value") } + listOfNotNull(answer.num("cash_balance"))
                val total = answer.num("total_value")
                // Statements round each value; allow a dollar, or 0.1 % of large accounts.
                if (total != null && values.isNotEmpty()) {
                    compared = true
                    val sum = values.fold(BigDecimal.ZERO, BigDecimal::add)
                    val allowed = BigDecimal.ONE.max(total.abs().multiply(BigDecimal("0.001")))
                    if ((sum - total).abs() > allowed) out += "holdings vs. total value: ${sum.toPlainString()} instead of ${total.toPlainString()}"
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
    fun draft(typeId: String, answer: JsonObject, checked: Boolean): DocumentDraft {
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
                dueDate = ai(date("due_date")), accountNumber = ai(answer.str("account_number")),
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
            else -> DocumentDraft(DocumentKind.OTHER, currency = currency)
        }
    }

    private fun JsonElement.str(key: String): String? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    /** A number exactly as written in the answer, never through floating point. */
    private fun JsonElement.num(key: String): BigDecimal? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBigDecimalOrNull()

    private fun JsonObject.list(key: String): List<JsonElement> = (this[key] as? JsonArray) ?: emptyList()
}
