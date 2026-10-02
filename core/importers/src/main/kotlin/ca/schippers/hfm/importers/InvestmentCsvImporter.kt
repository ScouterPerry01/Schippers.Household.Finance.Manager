package ca.schippers.hfm.importers

import ca.schippers.hfm.money.Currency
import kotlinx.datetime.LocalDate
import java.io.InputStream
import java.math.BigDecimal
import java.text.Normalizer
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Brokerage activity exports in CSV (INV-05), as Canadian brokers write them in English or French.
 * Columns are found by their headings (date, action, symbol, quantity, price, amount, commission),
 * so most files import without a column mapping. Rows whose action is not recognised are listed
 * as warnings rather than guessed.
 */
class InvestmentCsvImporter : InvestmentImporter {
    override val id = "investment-csv"

    override fun canReadInvestments(fileName: String, head: ByteArray): Boolean {
        if (fileName.substringAfterLast('.').lowercase() !in setOf("csv", "txt")) return false
        val header = fold(String(head, Charsets.UTF_8).lineSequence().firstOrNull().orEmpty())
        return listOf(ACTION, QUANTITY).all { names -> names.any { it in header } }
    }

    override fun readInvestments(input: InputStream, options: ImportOptions): List<ImportedInvestmentStatement> {
        val bytes = input.readBytes()
        val text = runCatching { Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString() }.getOrElse { String(bytes, charset("windows-1252")) }
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val delimiter = listOf(',', ';', '\t').maxBy { d -> firstLine.count { it == d } }
        val rows = CsvImporter.rows(text, delimiter)
        if (rows.size < 2) throw ImportException("The file has no activity rows")
        val header = rows.first().map(::fold)
        fun col(names: List<String>): Int? = names.firstNotNullOfOrNull { name -> header.indexOfFirst { it == name }.takeIf { it >= 0 } }
            ?: names.firstNotNullOfOrNull { name -> header.indexOfFirst { name in it }.takeIf { it >= 0 } }
        val dateCol = col(DATE) ?: throw ImportException("No date column found")
        val actionCol = col(ACTION) ?: throw ImportException("No action or type column found")
        val symbolCol = col(SYMBOL)
        val nameCol = col(NAME)?.takeIf { it != symbolCol }
        val quantityCol = col(QUANTITY)
        val priceCol = col(PRICE)
        val amountCol = col(AMOUNT)
        val feeCol = col(FEES)
        val currencyCol = col(CURRENCY)
        val data = rows.drop(1)
        val decimalComma = delimiter == ';' || data.any { r -> listOfNotNull(amountCol, priceCol).any { c -> r.getOrNull(c)?.matches(Regex("""-?[\d\s ]+,\d+""")) == true } }
        val dateFormat = CsvImporter.guessDateFormat(data.mapNotNull { it.getOrNull(dateCol)?.take(10) })
        val formatter = DateTimeFormatter.ofPattern(dateFormat, Locale.CANADA)

        fun number(text: String?): BigDecimal? {
            val t = text?.trim()?.replace(" ", "")?.replace(" ", "")?.replace("$", "")?.ifEmpty { null } ?: return null
            val cleaned = if (decimalComma) t.replace(".", "").replace(',', '.') else t.replace(",", "")
            val negative = cleaned.startsWith("(") && cleaned.endsWith(")")
            return cleaned.trim('(', ')').toBigDecimalOrNull()?.let { if (negative) it.negate() else it }
        }

        val warnings = ArrayList<String>()
        val securities = LinkedHashMap<String, ImportedSecurity>()
        val actions = ArrayList<ImportedInvestmentAction>()
        var currency: Currency? = null
        for ((index, row) in data.withIndex()) {
            fun cell(c: Int?) = c?.let { row.getOrNull(it)?.trim() }?.ifEmpty { null }
            val line = index + 2
            val date = cell(dateCol)?.take(10)?.let { runCatching { java.time.LocalDate.parse(it, formatter) }.getOrNull() }
                ?.let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }
            if (date == null) { warnings += "Line $line: no valid date, skipped."; continue }
            val actionText = cell(actionCol).orEmpty()
            val action = actionOf(actionText)
            if (action == null) { warnings += "Line $line: action \"$actionText\" not recognised, skipped."; continue }
            if (currency == null) currency = cell(currencyCol)?.let { runCatching { Currency.of(it.uppercase()) }.getOrNull() }
            val symbol = cell(symbolCol)?.uppercase()
            val name = cell(nameCol)
            val key = symbol ?: name?.takeIf { action !in setOf(ImportedAction.CASH_IN, ImportedAction.CASH_OUT, ImportedAction.INTEREST, ImportedAction.FEE) }
            if (key != null && key !in securities) securities[key] = ImportedSecurity(key, symbol, name ?: key, null, null, null, null)
            val quantity = number(cell(quantityCol))?.abs()?.takeIf { it.signum() != 0 }
            val price = number(cell(priceCol))?.abs()?.takeIf { it.signum() != 0 }
            val amount = number(cell(amountCol))?.abs()
            val fees = number(cell(feeCol))?.abs()?.takeIf { it.signum() != 0 }
            val gross = when (action) {
                ImportedAction.BUY -> if (quantity != null && price != null) quantity.multiply(price) else amount?.let { it - (fees ?: BigDecimal.ZERO) }
                ImportedAction.SELL -> if (quantity != null && price != null) quantity.multiply(price) else amount?.let { it + (fees ?: BigDecimal.ZERO) }
                else -> amount
            }
            actions += ImportedInvestmentAction(
                null, date, action, key, quantity, price, gross, fees, null, null,
                if (action == ImportedAction.REINVEST) ImportedAction.DIVIDEND else null, cell(nameCol)?.takeIf { symbol != null },
            )
        }
        return listOf(
            ImportedInvestmentStatement(
                "CSV", null, currency ?: options.defaultCurrency, actions.maxOfOrNull { it.date }, null, securities.values.toList(),
                actions.sortedBy { it.date }, emptyList(), warnings,
            ),
        )
    }

    companion object {
        private val DATE = listOf("trade date", "transaction date", "date de transaction", "date de l'operation", "date d'operation", "date", "settlement date")
        private val ACTION = listOf("action", "activity type", "transaction type", "type de transaction", "type d'operation", "activity", "operation", "type")
        private val SYMBOL = listOf("symbol", "symbole", "ticker")
        private val NAME = listOf("description", "security", "titre", "name", "nom")
        private val QUANTITY = listOf("quantity", "quantite", "qty", "units", "unites", "nombre")
        private val PRICE = listOf("price", "prix", "unit price", "cours")
        private val AMOUNT = listOf("net amount", "montant net", "amount", "montant", "value", "valeur")
        private val FEES = listOf("commission", "fees", "frais")
        private val CURRENCY = listOf("currency", "devise")

        /** Lower case, without accents or typographic apostrophes, for matching headings and actions. */
        fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").replace('’', '\'').lowercase().trim()

        /** Recognises the usual English and French action words; null when unsure. */
        fun actionOf(text: String): ImportedAction? {
            val t = fold(text)
            return when {
                t.isEmpty() -> null
                "reinv" in t || "drip" in t || "reinvest" in t -> ImportedAction.REINVEST
                "return of capital" in t || "remboursement de capital" in t || t == "roc" -> ImportedAction.RETURN_OF_CAPITAL
                "split" in t || "fractionnement" in t || "consolidation" in t -> ImportedAction.SPLIT
                "withholding" in t || "foreign tax" in t || "impot etranger" in t || "retenue" in t -> ImportedAction.FEE
                t.startsWith("buy") || "achat" in t || t == "bought" || "purchase" in t -> ImportedAction.BUY
                t.startsWith("sell") || "vente" in t || t == "sold" -> ImportedAction.SELL
                "distribution" in t -> ImportedAction.DISTRIBUTION
                t.startsWith("div") -> ImportedAction.DIVIDEND
                t.startsWith("int") || "interet" in t -> ImportedAction.INTEREST
                "transfer in" in t || "transfert entrant" in t || "deposit" in t || "depot" in t || "contribution" in t || "cotisation" in t -> ImportedAction.CASH_IN
                "transfer out" in t || "transfert sortant" in t || "withdrawal" in t || "retrait" in t -> ImportedAction.CASH_OUT
                "fee" in t || "frais" in t || "commission" in t -> ImportedAction.FEE
                else -> null
            }
        }
    }
}
