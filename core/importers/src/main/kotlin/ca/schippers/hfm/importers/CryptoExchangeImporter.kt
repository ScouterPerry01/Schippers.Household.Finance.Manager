package ca.schippers.hfm.importers

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/** What happened on an exchange (CR-03, CR-04). Currencies are codes as the file gives them, normalized. */
enum class CryptoEventKind { TRADE, DEPOSIT, WITHDRAWAL, INCOME, FEE }

/**
 * One exchange event. A trade has both sides; a deposit only [received]; a withdrawal only [sent];
 * income only [received]. Amounts are positive. [fee] is charged on top, in [feeCurrency].
 */
data class CryptoEvent(
    val externalId: String?,
    val date: LocalDate,
    val kind: CryptoEventKind,
    val sent: BigDecimal? = null,
    val sentCurrency: String? = null,
    val received: BigDecimal? = null,
    val receivedCurrency: String? = null,
    val fee: BigDecimal? = null,
    val feeCurrency: String? = null,
    val memo: String? = null,
)

data class CryptoExchangeFile(val exchange: String, val events: List<CryptoEvent>, val warnings: List<ImportNote>)

/**
 * Transaction histories exported by Canadian exchanges (CR-03): Kraken's ledger export, Coinbase's
 * transaction report, Shakepay's transaction summary (both layouts) and Newton's history. The file
 * is recognised by its headings; rows that cannot be read are listed, not guessed.
 */
object CryptoExchangeImporter {

    private val FIAT = setOf("CAD", "USD", "EUR", "GBP", "AUD", "CHF", "JPY")

    fun canRead(fileName: String, head: ByteArray): Boolean = detect(String(head, Charsets.UTF_8)) != null

    fun read(bytes: ByteArray): CryptoExchangeFile {
        val text = String(bytes, Charsets.UTF_8).removePrefix("﻿")
        return when (detect(text)) {
            "Kraken" -> kraken(text)
            "Coinbase" -> coinbase(text)
            "Shakepay" -> debitCredit("Shakepay", text)
            "Newton" -> debitCredit("Newton", text)
            else -> throw ImportException("Not a Kraken, Coinbase, Shakepay or Newton history")
        }
    }

    private fun detect(text: String): String? {
        val head = text.lineSequence().take(15).joinToString("\n").lowercase()
        return when {
            "refid" in head && "asset" in head && "amount" in head -> "Kraken"
            "transaction type" in head && "quantity transacted" in head -> "Coinbase"
            ("amount debited" in head && "amount credited" in head) -> "Shakepay"
            "received quantity" in head && "sent quantity" in head -> "Newton"
            else -> null
        }
    }

    fun isFiat(code: String?): Boolean = code != null && code in FIAT

    /** Kraken asset codes: XXBT is BTC, ZCAD is CAD; staking variants (ETH2.S, DOT.S) count as the coin itself. */
    fun normalize(code: String): String {
        val c = code.trim().uppercase().substringBefore('.').let { if (it == "ETH2") "ETH" else it }
        val known = mapOf("XXBT" to "BTC", "XBT" to "BTC", "XXDG" to "DOGE", "XDG" to "DOGE", "XETH" to "ETH", "XLTC" to "LTC", "XXRP" to "XRP", "XXLM" to "XLM", "XETC" to "ETC", "XXMR" to "XMR")
        known[c]?.let { return it }
        if (c.length == 4 && (c[0] == 'Z' && c.substring(1) in FIAT)) return c.substring(1)
        if (c.length == 4 && c[0] == 'X' && c.substring(1) in setOf("BTC", "ETH", "LTC", "XRP", "XLM", "ZEC", "REP", "MLN")) return c.substring(1)
        return c
    }

    // --- Kraken ---------------------------------------------------------------------------------

    private fun kraken(text: String): CryptoExchangeFile {
        val rows = table(text, "refid")
        val warnings = ArrayList<ImportNote>()
        val events = ArrayList<CryptoEvent>()
        for ((refid, legs) in rows.groupBy { it["refid"].orEmpty() }) {
            val first = legs.first()
            val date = date(first["time"]) ?: run { warnings += ImportNote.of("krakenNoDate", "Kraken $refid: no date", refid); continue }
            val type = first["type"].orEmpty().lowercase()
            fun amount(r: Map<String, String>) = number(r["amount"]) ?: BigDecimal.ZERO
            fun fee(r: Map<String, String>) = number(r["fee"])?.takeIf { it.signum() != 0 }
            fun asset(r: Map<String, String>) = normalize(r["asset"].orEmpty())
            when {
                type == "trade" || type == "spend" || type == "receive" || legs.size == 2 && legs.all { it["type"] in setOf("spend", "receive", "trade") } -> {
                    val out = legs.firstOrNull { amount(it).signum() < 0 }
                    val inn = legs.firstOrNull { amount(it).signum() > 0 }
                    if (out == null || inn == null) { warnings += ImportNote.of("krakenOneSide", "Kraken $refid: a trade with one side only", refid); continue }
                    val feeLeg = legs.firstOrNull { fee(it) != null }
                    events += CryptoEvent(refid, date, CryptoEventKind.TRADE, amount(out).abs(), asset(out), amount(inn), asset(inn), feeLeg?.let(::fee), feeLeg?.let(::asset), "Kraken trade")
                }
                type == "deposit" -> legs.forEach { events += CryptoEvent("$refid:${asset(it)}", date, CryptoEventKind.DEPOSIT, received = amount(it), receivedCurrency = asset(it), fee = fee(it), feeCurrency = asset(it), memo = "Kraken deposit") }
                type == "withdrawal" -> legs.forEach { events += CryptoEvent("$refid:${asset(it)}", date, CryptoEventKind.WITHDRAWAL, sent = amount(it).abs(), sentCurrency = asset(it), fee = fee(it), feeCurrency = asset(it), memo = "Kraken withdrawal") }
                type == "staking" || type == "earn" && first["subtype"].orEmpty().lowercase() in setOf("reward", "") ->
                    legs.filter { amount(it).signum() > 0 }.forEach { events += CryptoEvent("$refid:${asset(it)}", date, CryptoEventKind.INCOME, received = amount(it), receivedCurrency = asset(it), memo = "Kraken staking reward") }
                type in setOf("transfer", "earn", "margin", "rollover", "adjustment") -> Unit // moves between Kraken's own sub-accounts
                else -> warnings += ImportNote.of("krakenType", "Kraken $refid: \"$type\" not supported", refid, type)
            }
        }
        return CryptoExchangeFile("Kraken", events.sortedBy { it.date }, warnings)
    }

    // --- Coinbase -------------------------------------------------------------------------------

    private fun coinbase(text: String): CryptoExchangeFile {
        val rows = table(text, "transaction type")
        val warnings = ArrayList<ImportNote>()
        val events = ArrayList<CryptoEvent>()
        for ((i, r) in rows.withIndex()) {
            val date = date(r["timestamp"]) ?: run { warnings += ImportNote.of("coinbaseNoDate", "Coinbase line ${i + 1}: no date", i + 1); continue }
            val type = r["transaction type"].orEmpty().lowercase()
            val asset = normalize(r["asset"].orEmpty())
            val qty = number(r["quantity transacted"])?.abs() ?: BigDecimal.ZERO
            val fiat = normalize(r["spot price currency"].orEmpty().ifBlank { "CAD" })
            val total = number(r["total (inclusive of fees and/or spread)"] ?: r["total"])?.abs()
            val id = r["id"]?.ifBlank { null }
            val notes = r["notes"]
            when {
                type.endsWith("buy") || type == "purchase" -> events += CryptoEvent(id, date, CryptoEventKind.TRADE, total, fiat, qty, asset, memo = "Coinbase buy")
                type.endsWith("sell") -> events += CryptoEvent(id, date, CryptoEventKind.TRADE, qty, asset, total, fiat, memo = "Coinbase sell")
                type == "send" || type == "withdrawal" && !isFiat(asset) -> events += CryptoEvent(id, date, CryptoEventKind.WITHDRAWAL, sent = qty, sentCurrency = asset, memo = notes ?: "Coinbase send")
                type == "receive" || type == "deposit" && !isFiat(asset) -> events += CryptoEvent(id, date, CryptoEventKind.DEPOSIT, received = qty, receivedCurrency = asset, memo = notes ?: "Coinbase receive")
                type == "deposit" -> events += CryptoEvent(id, date, CryptoEventKind.DEPOSIT, received = qty, receivedCurrency = asset, memo = "Coinbase deposit")
                type == "withdrawal" -> events += CryptoEvent(id, date, CryptoEventKind.WITHDRAWAL, sent = qty, sentCurrency = asset, memo = "Coinbase withdrawal")
                "income" in type || "reward" in type || "earn" in type -> events += CryptoEvent(id, date, CryptoEventKind.INCOME, received = qty, receivedCurrency = asset, memo = r["transaction type"])
                type == "convert" -> {
                    // "Converted 0.01 ETH to 0.0005 BTC"
                    val m = Regex("""(?i)converted\s+([\d.,]+)\s+(\w+)\s+to\s+([\d.,]+)\s+(\w+)""").find(notes.orEmpty())
                    if (m == null) { warnings += ImportNote.of("coinbaseConvert", "Coinbase line ${i + 1}: a conversion without its notes", i + 1); continue }
                    events += CryptoEvent(id, date, CryptoEventKind.TRADE, number(m.groupValues[1]), normalize(m.groupValues[2]), number(m.groupValues[3]), normalize(m.groupValues[4]), memo = "Coinbase convert")
                }
                else -> warnings += ImportNote.of("coinbaseType", "Coinbase line ${i + 1}: \"${r["transaction type"]}\" not supported", i + 1, r["transaction type"].orEmpty())
            }
        }
        return CryptoExchangeFile("Coinbase", events.sortedBy { it.date }, warnings)
    }

    // --- Shakepay and Newton: what left and what arrived ----------------------------------------

    private fun debitCredit(exchange: String, text: String): CryptoExchangeFile {
        val rows = table(text, if (exchange == "Newton") "received quantity" else "amount debited")
        val warnings = ArrayList<ImportNote>()
        val events = ArrayList<CryptoEvent>()
        for ((i, r) in rows.withIndex()) {
            val date = date(r["date"]) ?: run { warnings += ImportNote.of("exchangeNoDate", "$exchange line ${i + 1}: no date", exchange, i + 1); continue }
            val sent = number(r["amount debited"] ?: r["sent quantity"])?.abs()?.takeIf { it.signum() != 0 }
            val sentCur = (r["debit currency"] ?: r["asset debited"] ?: r["sent currency"])?.ifBlank { null }?.let(::normalize)
            val received = number(r["amount credited"] ?: r["received quantity"])?.abs()?.takeIf { it.signum() != 0 }
            val receivedCur = (r["credit currency"] ?: r["asset credited"] ?: r["received currency"])?.ifBlank { null }?.let(::normalize)
            val type = listOfNotNull(r["transaction type"], r["type"], r["tag"], r["description"]).joinToString(" ").lowercase()
            val id = r["blockchain transaction id"]?.ifBlank { null }
            val fee = number(r["fee amount"])?.abs()?.takeIf { it.signum() != 0 }
            val feeCur = r["fee currency"]?.ifBlank { null }?.let(::normalize)
            val income = listOf("reward", "shakingsats", "cashback", "referral", "promo", "bonus").any { it in type }
            events += when {
                sent != null && received != null -> CryptoEvent(id, date, CryptoEventKind.TRADE, sent, sentCur, received, receivedCur, fee, feeCur, "$exchange trade")
                received != null && income -> CryptoEvent(id, date, CryptoEventKind.INCOME, received = received, receivedCurrency = receivedCur, memo = "$exchange reward")
                received != null -> CryptoEvent(id, date, CryptoEventKind.DEPOSIT, received = received, receivedCurrency = receivedCur, fee = fee, feeCurrency = feeCur, memo = "$exchange deposit")
                sent != null -> CryptoEvent(id, date, CryptoEventKind.WITHDRAWAL, sent = sent, sentCurrency = sentCur, fee = fee, feeCurrency = feeCur, memo = "$exchange withdrawal")
                else -> { warnings += ImportNote.of("exchangeNoAmounts", "$exchange line ${i + 1}: no amounts", exchange, i + 1); continue }
            }
        }
        return CryptoExchangeFile(exchange, events.sortedBy { it.date }, warnings)
    }

    // --- Helpers ---------------------------------------------------------------------------------

    /** Rows as maps from lower-case heading to value, starting at the first line that contains [marker]. */
    private fun table(text: String, marker: String): List<Map<String, String>> {
        val lines = text.lines()
        val start = lines.indexOfFirst { marker in it.lowercase() }
        if (start < 0) return emptyList()
        val rows = CsvImporter.rows(lines.drop(start).joinToString("\n"), ',')
        val header = rows.first().map { it.trim().lowercase() }
        return rows.drop(1).map { r -> header.indices.associate { header[it] to r.getOrElse(it) { "" }.trim() } }
    }

    private fun number(text: String?): BigDecimal? =
        text?.replace(Regex("""[^\d.\-eE]"""), "")?.ifEmpty { null }?.toBigDecimalOrNull()

    /** 2024-01-15T14:22:01Z, 2024-01-15 14:22:01 UTC, 2024-01-15, 01/15/2024. */
    private fun date(text: String?): LocalDate? {
        val t = text?.trim().orEmpty()
        Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(t)?.let { m -> return runCatching { LocalDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull() }
        Regex("""^(\d{1,2})/(\d{1,2})/(\d{4})""").find(t)?.let { m -> return runCatching { LocalDate(m.groupValues[3].toInt(), m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull() }
        return null
    }
}
