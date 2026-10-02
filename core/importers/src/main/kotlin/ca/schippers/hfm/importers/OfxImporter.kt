package ca.schippers.hfm.importers

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.sum
import kotlinx.datetime.LocalDate
import java.io.InputStream
import java.nio.charset.Charset

/**
 * OFX, QFX (Quicken) and QBO (QuickBooks) statements, in both the SGML form of OFX 1.x, where
 * value tags are usually not closed, and the XML form of OFX 2.x. Bank and credit card
 * statements are read (REC-01); investment statements follow in Phase 3.
 */
class OfxImporter : StatementImporter {
    override val id = "ofx"
    override val fileExtensions = setOf("ofx", "qfx", "qbo")

    override fun canRead(fileName: String, head: ByteArray): Boolean {
        val text = String(head, Charsets.ISO_8859_1).uppercase()
        return fileName.substringAfterLast('.').lowercase() in fileExtensions || "OFXHEADER" in text || "<OFX>" in text
    }

    override fun read(input: InputStream, options: ImportOptions): List<ImportedStatement> {
        val bytes = input.readBytes()
        val text = String(bytes, charsetOf(bytes))
        val start = text.indexOf("<OFX>", ignoreCase = true)
        if (start < 0) throw ImportException("Not an OFX file: no <OFX> element")
        val root = parse(text.substring(start))
        val statements = root.findAll("STMTRS") + root.findAll("CCSTMTRS")
        if (statements.isEmpty()) throw ImportException("The OFX file contains no bank or credit card statement")
        return statements.map { statement(it, options) }
    }

    private fun statement(rs: Element, options: ImportOptions): ImportedStatement {
        val currency = rs.value("CURDEF")?.let { runCatching { Currency.of(it) }.getOrNull() } ?: options.defaultCurrency
        val accountId = (rs.find("BANKACCTFROM") ?: rs.find("CCACCTFROM"))?.value("ACCTID")
        val list = rs.find("BANKTRANLIST")
        val lines = list?.findAll("STMTTRN").orEmpty().map { trn ->
            val name = trn.value("NAME") ?: trn.find("PAYEE")?.value("NAME")
            ImportedLine(
                externalId = trn.value("FITID"),
                date = date(trn.value("DTPOSTED") ?: trn.value("DTUSER") ?: throw ImportException("A transaction has no date")),
                amount = amount(trn.value("TRNAMT") ?: throw ImportException("A transaction has no amount"), currency),
                payee = name?.trim()?.ifEmpty { null },
                memo = trn.value("MEMO")?.trim()?.ifEmpty { null },
                checkNumber = trn.value("CHECKNUM"),
            )
        }
        val closing = rs.find("LEDGERBAL")?.value("BALAMT")?.let { amount(it, currency) }
        return ImportedStatement(
            format = "OFX",
            accountNumberHint = accountId,
            currency = currency,
            periodStart = list?.value("DTSTART")?.let(::date) ?: lines.minOfOrNull { it.date },
            periodEnd = list?.value("DTEND")?.let(::date) ?: lines.maxOfOrNull { it.date },
            // OFX gives the closing (ledger) balance; the opening balance follows from the lines.
            openingBalance = closing?.let { it - lines.map { l -> l.amount }.sum(currency) },
            closingBalance = closing,
            lines = lines,
        )
    }

    // --- Parsing ------------------------------------------------------------------------------

    class Element(val name: String, var text: String? = null) {
        val children = mutableListOf<Element>()

        fun find(tag: String): Element? = children.firstOrNull { it.name == tag } ?: children.firstNotNullOfOrNull { it.find(tag) }
        fun findAll(tag: String): List<Element> = children.flatMap { if (it.name == tag) listOf(it) else it.findAll(tag) }
        fun value(tag: String): String? = children.firstOrNull { it.name == tag }?.text
    }

    /**
     * Builds an element tree from SGML or XML OFX. A tag followed directly by text is a value
     * (its closing tag is optional); any other tag opens an element that a later closing tag ends.
     */
    internal fun parse(body: String): Element {
        val root = Element("ROOT")
        val stack = ArrayDeque<Element>().apply { addLast(root) }
        val tokens = Regex("""<(/?)([A-Za-z0-9.]+)[^>]*>|([^<]+)""").findAll(body).toList()
        var i = 0
        while (i < tokens.size) {
            val m = tokens[i]
            val closing = m.groupValues[1] == "/"
            val tag = m.groupValues[2].uppercase()
            when {
                tag.isEmpty() -> Unit // text between elements
                closing -> {
                    if (stack.any { it.name == tag }) {
                        while (stack.last().name != tag) stack.removeLast()
                        stack.removeLast()
                    }
                }
                else -> {
                    val next = tokens.getOrNull(i + 1)
                    val text = next?.groupValues?.get(3)?.trim()
                    if (!text.isNullOrEmpty()) {
                        stack.last().children += Element(tag, unescape(text))
                        i++
                        val after = tokens.getOrNull(i + 1)
                        if (after != null && after.groupValues[1] == "/" && after.groupValues[2].equals(tag, ignoreCase = true)) i++
                    } else {
                        val element = Element(tag)
                        stack.last().children += element
                        stack.addLast(element)
                    }
                }
            }
            i++
        }
        return root
    }

    private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    /** OFX dates: YYYYMMDD, optionally followed by a time and time zone, which are ignored. */
    private fun date(s: String): LocalDate {
        val digits = s.trim().take(8)
        if (digits.length != 8 || !digits.all(Char::isDigit)) throw ImportException("Invalid OFX date: $s")
        return LocalDate(digits.substring(0, 4).toInt(), digits.substring(4, 6).toInt(), digits.substring(6, 8).toInt())
    }

    /** Amounts use a dot, but some French-language exports use a comma. */
    private fun amount(s: String, currency: Currency): Money {
        val cleaned = s.trim().replace(" ", "").let { if (',' in it && '.' !in it) it.replace(',', '.') else it.replace(",", "") }
        val value = cleaned.toBigDecimalOrNull() ?: throw ImportException("Invalid OFX amount: $s")
        return Money.of(value, currency)
    }

    /** OFX 1.x declares CHARSET:1252 (or 8859-1) in its header; OFX 2.x is XML, UTF-8 by default. */
    private fun charsetOf(bytes: ByteArray): Charset {
        val head = String(bytes, 0, minOf(bytes.size, 1024), Charsets.ISO_8859_1).uppercase()
        Regex("""ENCODING="([A-Z0-9_-]+)"""").find(head)?.let { return runCatching { Charset.forName(it.groupValues[1]) }.getOrDefault(Charsets.UTF_8) }
        val charset = Regex("""CHARSET:\s*([A-Z0-9_-]+)""").find(head)?.groupValues?.get(1)
        val encoding = Regex("""ENCODING:\s*([A-Z0-9_-]+)""").find(head)?.groupValues?.get(1)
        return when {
            encoding == "UTF-8" || encoding == "UNICODE" -> Charsets.UTF_8
            charset == "1252" -> Charset.forName("windows-1252")
            charset == "8859-1" || charset == "ISO-8859-1" -> Charsets.ISO_8859_1
            "OFXHEADER:100" in head -> Charset.forName("windows-1252")
            else -> Charsets.UTF_8
        }
    }
}
