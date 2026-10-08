package ca.schippers.hfm.books

import ca.schippers.hfm.importers.ImportNote
import ca.schippers.hfm.importers.ImportedAction
import ca.schippers.hfm.importers.ImportedInvestmentAction
import ca.schippers.hfm.importers.ImportedInvestmentStatement
import ca.schippers.hfm.importers.ImportedPosition
import ca.schippers.hfm.importers.ImportedSecurity
import ca.schippers.hfm.money.Currency
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.math.BigDecimal

/**
 * INV-05: turns a trade confirmation or an investment statement read by AI (schemas
 * `hfm/trade_confirmation/v1`, `hfm/investment_statement/v1` and `v2`) into the statement a
 * brokerage file gives, so it is imported the same way ([BrokerageImportService]).
 */
internal object AiInvestments {

    fun statement(typeId: String, answer: JsonObject, accountCurrency: Currency): ImportedInvestmentStatement {
        val currency = currencyOf(answer.text("currency")) ?: accountCurrency
        validate(currency == accountCurrency, "error.aiInvestmentCurrency", currency.code, accountCurrency.code)
        val securities = LinkedHashMap<String, ImportedSecurity>()
        val warnings = ArrayList<ImportNote>()
        /** The key of the security a line names: its symbol, else its name; null when it names neither. */
        fun security(o: JsonObject, price: BigDecimal? = null, on: LocalDate? = null): String? {
            val symbol = o.text("symbol")?.uppercase()
            val name = o.text("description") ?: symbol ?: return null
            val key = symbol ?: name
            val known = securities[key]
            if (known == null || (price != null && known.price == null)) {
                securities[key] = ImportedSecurity(key, symbol, name, o.text("identifier") ?: known?.identifier, null, price ?: known?.price, on ?: known?.priceDate)
            }
            return key
        }
        return when (typeId) {
            "trade_confirmation" -> {
                val number = answer.text("confirmation_number")
                val actions = answer.list("trades").mapNotNull { t ->
                    val date = t.date("trade_date")
                    if (date == null) {
                        warnings += ImportNote.of("aiTradeNoDate", "A trade without a trade date was skipped.")
                        return@mapNotNull null
                    }
                    if (currencyOf(t.text("currency"))?.let { it != accountCurrency } == true) {
                        warnings += ImportNote.of("aiTradeCurrency", "$date: a trade in ${t.text("currency")} was skipped; the account is in ${accountCurrency.code}.", date, t.text("currency").orEmpty(), accountCurrency.code)
                        return@mapNotNull null
                    }
                    val sell = t.text("action") == "sell"
                    val quantity = t.number("quantity")?.abs()
                    val price = t.number("price")?.abs()
                    val fees = listOfNotNull(t.number("commission"), t.number("other_fees")).map { it.abs() }.fold(BigDecimal.ZERO, BigDecimal::add)
                    val net = t.number("net_amount")?.abs()
                    val gross = t.number("gross_amount")?.abs()
                        ?: if (quantity != null && price != null) quantity.multiply(price) else net?.let { if (sell) it + fees else it - fees }
                    val memo = listOfNotNull(
                        t.date("settlement_date")?.let { "Settles $it" },
                        number?.let { "Confirmation $it" },
                    ).joinToString(" · ").ifEmpty { null }
                    ImportedInvestmentAction(
                        null, date, if (sell) ImportedAction.SELL else ImportedAction.BUY, security(t, price, date), quantity, price, gross,
                        fees.takeIf { it.signum() != 0 }, null, null, null, memo,
                    )
                }
                ImportedInvestmentStatement(BrokerageImportService.AI_FORMAT, answer.text("account_number_last_digits"), currency, null, null, securities.values.toList(), actions, emptyList(), warnings)
            }
            "investment_statement" -> {
                val asOf = answer.date("period_end")
                val positions = answer.list("holdings").mapNotNull { h ->
                    val quantity = h.number("quantity") ?: return@mapNotNull null
                    val price = h.number("price") ?: h.number("market_value")?.let { v -> quantity.takeIf { it.signum() != 0 }?.let { v.divide(it, java.math.MathContext.DECIMAL64) } }
                    val key = security(h, price?.takeIf { it.signum() > 0 }, asOf) ?: return@mapNotNull null
                    ImportedPosition(key, quantity, price)
                }
                val actions = answer.list("activity").mapNotNull { a -> activity(a, ::security, warnings) }
                ImportedInvestmentStatement(
                    BrokerageImportService.AI_FORMAT, answer.text("account_number_last_digits"), currency, asOf, answer.number("cash_balance"),
                    securities.values.toList(), actions, positions, warnings,
                )
            }
            else -> throw ValidationException("error.aiNoStatement")
        }
    }

    /**
     * One activity line of a statement. Its amount is the cash effect, money in positive: a
     * purchase's gross value is its units times price, else what it cost less the commission.
     */
    private fun activity(a: JsonObject, security: (JsonObject, BigDecimal?, LocalDate?) -> String?, warnings: MutableList<ImportNote>): ImportedInvestmentAction? {
        val date = a.date("date") ?: return null
        val type = a.text("type") ?: "other"
        val amount = a.number("amount")
        val cash = amount?.abs()
        val quantity = a.number("quantity")?.abs()
        val price = a.number("price")?.abs()
        val fees = a.number("commission")?.abs()?.takeIf { it.signum() != 0 }
        val withheld = a.number("tax_withheld")?.abs()?.takeIf { it.signum() != 0 }
        val memo = a.text("description")
        val units = if (quantity != null && price != null) quantity.multiply(price) else null
        fun act(action: ImportedAction, value: BigDecimal?, qty: BigDecimal? = null, px: BigDecimal? = null, fee: BigDecimal? = null, tax: BigDecimal? = null, income: ImportedAction? = null) =
            ImportedInvestmentAction(null, date, action, security(a, px, date.takeIf { px != null }), qty, px, value, fee, tax, null, income, memo)
        return when (type) {
            "buy" -> act(ImportedAction.BUY, units ?: cash?.let { it - (fees ?: BigDecimal.ZERO) }, quantity, price, fees)
            "sell" -> act(ImportedAction.SELL, units ?: cash?.let { it + (fees ?: BigDecimal.ZERO) }, quantity, price, fees)
            // Income is printed net of tax withheld; the books keep the gross and the tax.
            "dividend" -> act(ImportedAction.DIVIDEND, cash?.let { it + (withheld ?: BigDecimal.ZERO) }, tax = withheld)
            "interest" -> act(ImportedAction.INTEREST, cash?.let { it + (withheld ?: BigDecimal.ZERO) }, tax = withheld)
            "distribution" -> act(ImportedAction.DISTRIBUTION, cash?.let { it + (withheld ?: BigDecimal.ZERO) }, tax = withheld)
            "reinvestment" -> if (quantity == null) {
                warnings += ImportNote.of("aiReinvestNoUnits", "$date: a reinvestment without units was skipped.", date)
                null
            } else {
                act(ImportedAction.REINVEST, units ?: cash, quantity, price, income = ImportedAction.DISTRIBUTION.takeIf { memo?.contains("distrib", ignoreCase = true) == true } ?: ImportedAction.DIVIDEND)
            }
            "return_of_capital" -> act(ImportedAction.RETURN_OF_CAPITAL, cash)
            "fee" -> act(ImportedAction.FEE, cash)
            "contribution" -> act(ImportedAction.CASH_IN, cash).copy(securityKey = null)
            "withdrawal" -> act(ImportedAction.CASH_OUT, cash).copy(securityKey = null)
            "transfer" -> if (quantity == null && amount != null && amount.signum() != 0) {
                act(if (amount.signum() > 0) ImportedAction.CASH_IN else ImportedAction.CASH_OUT, cash).copy(securityKey = null)
            } else {
                warnings += ImportNote.of("aiTransferUnits", "$date: a transfer of units (${memo.orEmpty()}) was skipped; enter it with its book cost.", date, memo.orEmpty())
                null
            }
            else -> {
                warnings += ImportNote.of("aiNotImported", "$date: \"${memo.orEmpty()}\" was not imported; enter it by hand if it belongs in the books.", date, memo.orEmpty())
                null
            }
        }?.takeIf { it.amount != null || it.quantity != null }
    }

    private fun currencyOf(code: String?): Currency? = code?.let { runCatching { Currency.of(it.uppercase()) }.getOrNull() }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    /** A number exactly as written in the answer, never through floating point. */
    private fun JsonObject.number(key: String): BigDecimal? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.let(ca.schippers.hfm.ocr.ReadNumbers::parse)

    private fun JsonObject.date(key: String): LocalDate? = text(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun JsonObject.list(key: String): List<JsonObject> = (this[key] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
}
