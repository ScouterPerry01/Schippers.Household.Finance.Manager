package ca.schippers.hfm.ocr

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.text.Normalizer

/**
 * Turns recognised text into the OCR-02 fields of a receipt, bill or invoice, in English and French
 * (OCR-04), with a confidence per field (OCR-05). Plain Kotlin, shared by the phone and the desktop.
 *
 * The rules favour being right over being complete: a field is only filled when a label or a
 * strong pattern supports it, and the totals are checked against the subtotal and taxes
 * (ADR 0004) before they are trusted.
 */
object FieldExtractor {

    fun extract(result: OcrResult, today: LocalDate? = null): DocumentDraft = extract(result.lines, today)

    fun extract(lines: List<OcrLine>, today: LocalDate? = null): DocumentDraft {
        val rows = lines.filter { it.text.isNotBlank() }.map { Row(it.text.trim(), fold(it.text), it.confidence) }
        if (rows.isEmpty()) return DocumentDraft()
        val french = isFrench(rows)
        val currency = currency(rows)

        fun money(a: BigDecimal) = Money.exact(a, currency)

        val subtotal = labelledAmount(rows, SUBTOTAL, exclude = emptyList())?.let { (v, c) -> Extracted(money(v), c) }
        val taxes = taxes(rows).map { (name, v, c) -> name to Extracted(money(v), c) }
        var total = total(rows)?.let { (v, c) -> Extracted(money(v), c) }

        // ADR 0004: check the arithmetic before trusting the total.
        if (total != null && subtotal != null && taxes.isNotEmpty()) {
            val sum = subtotal.value + taxes.fold(Money.zero(currency)) { a, t -> a + t.second.value }
            total = if (sum == total.value) total.copy(confidence = maxOf(total.confidence, 0.95f)) else total.copy(confidence = total.confidence * 0.8f)
        }

        val dueDate = labelledDate(rows, DUE_LABELS, french)
        val docDate = labelledDate(rows, DATE_LABELS, french) ?: firstDate(rows, french, except = dueDate?.value)
        val kind = kind(rows, dueDate != null, taxes.isNotEmpty())

        return DocumentDraft(
            kind = kind,
            merchant = merchant(rows),
            date = docDate?.let { d -> if (today != null && d.value > today) d.copy(confidence = d.confidence * 0.5f) else d },
            total = total,
            subtotal = subtotal,
            taxes = taxes,
            currency = currency,
            paymentMethod = paymentMethod(rows),
            cardLast4 = cardLast4(rows),
            invoiceNumber = labelledToken(rows, INVOICE_LABELS, Regex("""[A-Z0-9][A-Z0-9-]{2,24}""")),
            dueDate = dueDate,
            accountNumber = labelledToken(rows, ACCOUNT_LABELS, Regex("""\d[\d -]{4,24}\d"""))?.let { it.copy(value = it.value.replace(Regex("\\s+"), " ")) },
        )
    }

    // --- Amounts ---------------------------------------------------------------------------------

    private class Row(val text: String, val folded: String, val confidence: Float)

    /**
     * An amount with exactly two decimals, with "," or "." as the decimal separator and spaces,
     * dots or commas between thousands: "1 234,56", "1,234.56", "-12.30 $". Rates such as "9,975 %"
     * or "5%" are not amounts.
     */
    private val AMOUNT = Regex("""(?<![\d.,])(-?)\s?\$?\s?(\d{1,3}(?:[   .,]\d{3})*|\d+)\s?([.,])\s?(\d{2})(?![\d%]|\s?%)""")

    internal fun amounts(text: String): List<BigDecimal> = AMOUNT.findAll(text).map { m ->
        val whole = m.groupValues[2].filter(Char::isDigit)
        BigDecimal("${m.groupValues[1]}$whole.${m.groupValues[4]}")
    }.toList()

    private fun lastAmount(row: Row): BigDecimal? = amounts(row.text).lastOrNull()

    /** The amount on a labelled row, or on the next row when the label stands alone. */
    private fun labelledAmount(rows: List<Row>, labels: List<String>, exclude: List<String>): Pair<BigDecimal, Float>? {
        for ((i, row) in rows.withIndex()) {
            if (labels.none { row.folded.contains(it) } || exclude.any { row.folded.contains(it) }) continue
            lastAmount(row)?.let { return it to row.confidence }
            rows.getOrNull(i + 1)?.let { next -> if (amounts(next.text).size == 1) return amounts(next.text).single() to next.confidence * 0.9f }
        }
        return null
    }

    /** The largest amount on a total row; the document's largest amount, with low confidence, otherwise. */
    private fun total(rows: List<Row>): Pair<BigDecimal, Float>? {
        val candidates = rows.withIndex().mapNotNull { (i, row) ->
            if (TOTAL.none { row.folded.contains(it) } || NOT_TOTAL.any { row.folded.contains(it) }) return@mapNotNull null
            val amount = lastAmount(row) ?: rows.getOrNull(i + 1)?.let { amounts(it.text).singleOrNull() } ?: return@mapNotNull null
            amount to row.confidence
        }.filter { it.first.signum() > 0 }
        candidates.maxByOrNull { it.first }?.let { return it }
        val all = rows.flatMap { r -> amounts(r.text).map { it to r.confidence } }.filter { it.first.signum() > 0 }
        return all.maxByOrNull { it.first }?.let { it.first to it.second * 0.5f }
    }

    private fun taxes(rows: List<Row>): List<Triple<TaxName, BigDecimal, Float>> {
        val found = LinkedHashMap<TaxName, Triple<TaxName, BigDecimal, Float>>()
        for (row in rows) {
            if (NOT_TOTAL.any { row.folded.contains(it) && it.startsWith("total") }) continue
            val name = TAX_LABELS.entries.firstOrNull { (_, labels) -> labels.any { Regex("""(^|[^a-z])$it([^a-z]|$)""").containsMatchIn(row.folded) } }?.key ?: continue
            if (name in found) continue
            // "TPS 5 % 1,83" or "TPS #123456789 RT0001 1,83": the amount is the last one on the row.
            val amount = lastAmount(row) ?: continue
            if (amount.signum() >= 0) found[name] = Triple(name, amount, row.confidence)
        }
        return found.values.toList()
    }

    // --- Dates -----------------------------------------------------------------------------------

    private fun labelledDate(rows: List<Row>, labels: List<String>, french: Boolean): Extracted<LocalDate>? {
        for ((i, row) in rows.withIndex()) {
            if (labels.none { row.folded.contains(it) }) continue
            val onRow = dates(row.text, french)
            val date = onRow.firstOrNull() ?: rows.getOrNull(i + 1)?.let { dates(it.text, french).firstOrNull() } ?: continue
            return Extracted(date.first, row.confidence * date.second)
        }
        return null
    }

    private fun firstDate(rows: List<Row>, french: Boolean, except: LocalDate?): Extracted<LocalDate>? {
        for (row in rows) {
            val date = dates(row.text, french).firstOrNull { it.first != except } ?: continue
            return Extracted(date.first, row.confidence * date.second * 0.95f)
        }
        return null
    }

    /** Dates on a line, each with how sure the reading is (day-month order can be ambiguous). */
    internal fun dates(text: String, french: Boolean): List<Pair<LocalDate, Float>> {
        val out = ArrayList<Pair<LocalDate, Float>>()
        val folded = fold(text)
        // 2026-03-12, 2026/03/12, 2026.03.12
        Regex("""(?<!\d)(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})(?!\d)""").findAll(text).forEach { m ->
            date(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { out += it to 1f }
        }
        // 12/03/2026 or 03/12/2026: the order depends on the values and the language.
        Regex("""(?<!\d)(\d{1,2})[-/.](\d{1,2})[-/.](20\d{2}|\d{2})(?!\d)""").findAll(text).forEach { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            val y = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            when {
                a > 12 -> date(y, b, a)?.let { out += it to 0.95f }
                b > 12 -> date(y, a, b)?.let { out += it to 0.95f }
                french -> date(y, b, a)?.let { out += it to 0.7f }
                else -> date(y, a, b)?.let { out += it to 0.6f }
            }
        }
        // 12 mars 2026, March 12, 2026, 12-MAR-26
        Regex("""(?<![a-z])(\d{1,2})(?:er)?[ -]([a-z]{3,9})\.?[ -,]*(20\d{2}|\d{2})(?!\d)""").findAll(folded).forEach { m ->
            val month = month(m.groupValues[2]) ?: return@forEach
            date(m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }, month, m.groupValues[1].toInt())?.let { out += it to 0.95f }
        }
        Regex("""(?<![a-z])([a-z]{3,9})\.? (\d{1,2}),? (20\d{2})""").findAll(folded).forEach { m ->
            val month = month(m.groupValues[1]) ?: return@forEach
            date(m.groupValues[3].toInt(), month, m.groupValues[2].toInt())?.let { out += it to 0.95f }
        }
        return out
    }

    private fun date(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate(y, m, d) }.getOrNull()?.takeIf { y in 2000..2099 }

    private fun month(name: String): Int? = MONTHS.entries.firstOrNull { (prefix, _) -> name.startsWith(prefix) }?.value

    // --- Text fields -----------------------------------------------------------------------------

    /** The business name: usually the first line with words that is not a greeting, address, phone or amount. */
    private fun merchant(rows: List<Row>): Extracted<String>? {
        for ((i, row) in rows.take(6).withIndex()) {
            val t = row.text
            if (t.count(Char::isLetter) < 3) continue
            if (GREETINGS.any { row.folded.contains(it) }) continue
            if (Regex("""\d{3}[ .-]\d{3}[ .-]\d{4}""").containsMatchIn(t) || amounts(t).isNotEmpty() || dates(t, false).isNotEmpty()) continue
            if (Regex("""^\d+[ ,]""").containsMatchIn(t)) continue // street address
            val penalty = if (i == 0) 0.9f else 0.75f
            return Extracted(t.trim().trim('*', '-', '=', ' '), row.confidence * penalty)
        }
        return null
    }

    private fun paymentMethod(rows: List<Row>): Extracted<String>? {
        for (row in rows.asReversed()) {
            val method = PAYMENT.entries.firstOrNull { (_, words) -> words.any { Regex("""(^|[^a-z])$it([^a-z]|$)""").containsMatchIn(row.folded) } }?.key ?: continue
            return Extracted(method, row.confidence)
        }
        return null
    }

    /** OCR-02: only masked card numbers ("**** 1234", "XXXXXXXXXXXX1234") are read, never full ones. */
    private fun cardLast4(rows: List<Row>): Extracted<String>? {
        val masked = Regex("""(?:[*xX#•]{4,}|[*xX#•]{2,}\s?[*xX#•]{2,})\s?(\d{4})(?!\d)""")
        for (row in rows) masked.find(row.text)?.let { return Extracted(it.groupValues[1], row.confidence) }
        return null
    }

    private fun labelledToken(rows: List<Row>, labels: List<String>, pattern: Regex): Extracted<String>? {
        for ((i, row) in rows.withIndex()) {
            val label = labels.firstOrNull { row.folded.contains(it) } ?: continue
            val after = row.text.substring(minOf(row.text.length, labelEnd(row, label))).trimStart(' ', ':', '#', '.', '°', '-')
            val value = pattern.find(after.uppercase())?.value ?: rows.getOrNull(i + 1)?.let { pattern.find(it.text.uppercase())?.value } ?: continue
            if (value.count(Char::isDigit) < 3) continue
            return Extracted(value.trim(), row.confidence * 0.95f)
        }
        return null
    }

    /** Where [label] ends in the original row text (the folded text has the same length). */
    private fun labelEnd(row: Row, label: String): Int = row.folded.indexOf(label) + label.length

    /**
     * OCR-08: the kind of document, from its wording in English or French. Statements, pay stubs
     * and explanations of benefits need several of their typical phrases, so a receipt that
     * mentions a balance is still a receipt.
     */
    private fun kind(rows: List<Row>, hasDueDate: Boolean, hasTaxes: Boolean): DocumentKind {
        val text = rows.joinToString(" ") { it.folded }
        fun hits(words: List<String>) = words.count { text.contains(it) }
        return when {
            hits(EOB_WORDS) >= 2 -> DocumentKind.EOB
            hits(PAY_STUB_WORDS) >= 3 -> DocumentKind.PAY_STUB
            hits(INVESTMENT_WORDS) >= 3 -> DocumentKind.INVESTMENT_STATEMENT
            hits(CARD_WORDS) >= 1 && hits(STATEMENT_WORDS) >= 1 -> DocumentKind.CARD_STATEMENT
            hits(STATEMENT_WORDS) + hits(BANK_WORDS) >= 2 && hits(BANK_WORDS) >= 1 -> DocumentKind.BANK_STATEMENT
            hasDueDate || BILL_WORDS.any { text.contains(it) } -> if (text.contains("invoice") || text.contains("facture no")) DocumentKind.INVOICE else DocumentKind.BILL
            hasTaxes || RECEIPT_WORDS.any { text.contains(it) } || paymentMethod(rows) != null -> DocumentKind.RECEIPT
            else -> DocumentKind.OTHER
        }
    }

    private fun currency(rows: List<Row>): Currency {
        val text = rows.joinToString(" ") { it.text }
        return when {
            Regex("""\bUSD\b|US\s?\$""").containsMatchIn(text) -> Currency.USD
            Regex("""\bEUR\b|€""").containsMatchIn(text) -> Currency.EUR
            else -> Currency.CAD
        }
    }

    private fun isFrench(rows: List<Row>): Boolean {
        val text = " " + rows.joinToString(" ") { it.folded } + " "
        val fr = FRENCH_WORDS.count { text.contains(" $it ") }
        val en = ENGLISH_WORDS.count { text.contains(" $it ") }
        return fr > en
    }

    /** Lower case without accents, same length as the input, so positions carry over. */
    fun fold(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull() ?: c
            sb.append(base.lowercaseChar())
        }
        return sb.toString()
    }

    // --- Vocabulary (folded: lower case, no accents) ---------------------------------------------

    private val SUBTOTAL = listOf("sous-total", "sous total", "subtotal", "sub-total", "sub total", "s-total", "s/total")
    private val TOTAL = listOf("total", "montant du", "montant a payer", "a payer", "amount due", "balance due", "net a payer", "solde du", "total due", "grand total")
    private val NOT_TOTAL = SUBTOTAL + listOf("total des taxes", "total taxes", "total tax", "economies", "savings", "you saved", "vous avez economise", "total articles", "total items", "nombre d'articles", "points", "rabais total", "total discount")
    private val TAX_LABELS = linkedMapOf(
        TaxName.HST to listOf("hst", "tvh"),
        TaxName.GST to listOf("gst", "tps"),
        TaxName.QST to listOf("qst", "tvq"),
        TaxName.PST to listOf("pst", "tvp", "rst"),
    )
    private val DUE_LABELS = listOf("date d'echeance", "date d echeance", "echeance", "due date", "payment due", "payable avant", "a payer avant", "payable by", "please pay by", "date limite", "pay by")
    private val DATE_LABELS = listOf("date de facturation", "date de la facture", "date de facture", "bill date", "invoice date", "statement date", "date du releve", "billing date", "date:", "date :")
    private val INVOICE_LABELS = listOf("invoice number", "invoice no", "invoice #", "numero de facture", "no de facture", "n de facture", "facture no", "facture #", "no facture", "no. de facture", "receipt #", "recu no", "transaction #", "trans #", "no de transaction", "numero de transaction", "order #", "commande no")
    private val ACCOUNT_LABELS = listOf("account number", "account no", "account #", "acct", "numero de compte", "no de compte", "n de compte", "compte no", "numero de client", "no de client", "customer number", "client no", "numero de reference", "reference number")
    private val GREETINGS = listOf("bienvenue", "welcome", "merci", "thank you", "recu", "receipt", "facture", "invoice", "copie", "copy", "client", "customer")
    private val PAYMENT = linkedMapOf(
        "Visa" to listOf("visa"),
        "Mastercard" to listOf("mastercard", "master card", "mc"),
        "American Express" to listOf("amex", "american express"),
        "Interac" to listOf("interac", "debit", "paiement direct", "carte de debit"),
        "Cash" to listOf("cash", "comptant", "argent comptant", "especes"),
        "Gift card" to listOf("gift card", "carte-cadeau", "carte cadeau"),
    )
    private val BILL_WORDS = listOf("amount due", "montant du", "montant a payer", "payable avant", "date d'echeance", "due date", "billing period", "periode de facturation", "your bill", "votre facture")
    private val EOB_WORDS = listOf(
        "explanation of benefits", "releve de prestations", "statement of benefits", "eligible amount", "montant admissible",
        "claim number", "numero de demande", "numero de la demande", "plan member", "coordination of benefits", "montant rembourse", "amount reimbursed",
    )
    private val PAY_STUB_WORDS = listOf(
        "pay stub", "talon de paie", "pay statement", "releve de paie", "gross pay", "salaire brut", "net pay", "salaire net",
        "deductions", "retenues", "year to date", "cumul annuel", "ei premium", "assurance-emploi", "rqap", "qpip", "pay period", "periode de paie",
    )
    private val INVESTMENT_WORDS = listOf(
        "portfolio", "portefeuille", "holdings", "titres detenus", "book value", "valeur comptable", "market value", "valeur marchande",
        "asset allocation", "repartition de l", "units", "unites", "investment statement", "releve de placement",
    )
    /** Words only a credit card statement uses. */
    private val CARD_WORDS = listOf("minimum payment", "paiement minimum", "credit limit", "limite de credit", "available credit", "credit disponible")
    /** Words of any account statement. */
    private val STATEMENT_WORDS = listOf("previous balance", "solde precedent", "new balance", "nouveau solde", "statement period", "periode du releve", "statement date", "date du releve")
    /** Words of a bank account statement. */
    private val BANK_WORDS = listOf(
        "opening balance", "closing balance", "solde d'ouverture", "solde de cloture", "solde d’ouverture", "solde de fermeture",
        "account statement", "releve de compte", "bank statement", "releve bancaire", "withdrawals", "retraits", "deposits", "depots",
    )
    private val RECEIPT_WORDS = listOf("receipt", "recu", "ticket de caisse", "caisse", "cashier", "caissier", "change", "monnaie")
    private val MONTHS = linkedMapOf(
        "jan" to 1, "fev" to 2, "feb" to 2, "mar" to 3, "avr" to 4, "apr" to 4, "mai" to 5, "may" to 5, "juin" to 6, "jun" to 6,
        "juil" to 7, "jul" to 7, "aou" to 8, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )
    private val FRENCH_WORDS = listOf("le", "la", "les", "de", "du", "des", "et", "total", "taxes", "tps", "tvq", "merci", "montant", "facture", "date", "payer", "votre", "compte", "sous-total")
    private val ENGLISH_WORDS = listOf("the", "and", "of", "your", "thank", "you", "amount", "due", "account", "tax", "subtotal", "bill", "pay", "gst", "hst", "pst", "receipt")
}
