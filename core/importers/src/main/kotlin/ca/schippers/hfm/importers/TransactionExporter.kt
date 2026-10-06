package ca.schippers.hfm.importers

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/** EXP-02: one line of a split transaction to export; [transferAccount] is set for a transfer line. */
data class ExportSplit(val category: List<String>?, val transferAccount: String?, val amount: BigDecimal, val memo: String?)

/**
 * EXP-02: one transaction to export, in the account's currency: negative for money out. [category]
 * is the category path (parent first) of an unsplit transaction; [splits] holds the lines of a split one.
 */
data class ExportTransaction(
    val id: String,
    val date: LocalDate,
    val payee: String?,
    val memo: String?,
    val amount: BigDecimal,
    val cleared: ExportCleared,
    val category: List<String>?,
    val transferAccount: String?,
    val splits: List<ExportSplit> = emptyList(),
    val tags: List<String> = emptyList(),
)

enum class ExportCleared { UNCLEARED, CLEARED, RECONCILED }

/** What sort of account the transactions come from, which decides the QIF section and the OFX statement. */
enum class ExportAccountKind { BANK, CASH, CREDIT_CARD, ASSET, LIABILITY }

/**
 * EXP-02: selected transactions written as CSV, QIF or OFX, for other software or an accountant.
 * The three formats are read back by RANN's Roost's own importers.
 */
object TransactionExporter {

    /**
     * CSV with a header row ([headers]: date, payee, category, memo, amount, currency, cleared,
     * tags), one row per transaction (a split transaction lists its categories joined by " | ").
     * [separator] and [decimalComma] follow the user's language as the report CSV does.
     */
    fun csv(transactions: List<ExportTransaction>, currency: String, headers: List<String>, separator: Char = ',', decimalComma: Boolean = false): String {
        require(headers.size == 8) { "8 headers expected" }
        fun cell(text: String?): String {
            val t = text.orEmpty()
            return if (t.any { it == separator || it == '"' || it == '\n' || it == '\r' }) "\"" + t.replace("\"", "\"\"") + "\"" else t
        }
        fun amount(a: BigDecimal) = a.toPlainString().let { if (decimalComma) it.replace('.', ',') else it }
        val out = StringBuilder()
        out.append(headers.joinToString(separator.toString(), transform = ::cell)).append("\r\n")
        for (t in transactions) {
            val category = when {
                t.splits.isNotEmpty() -> t.splits.joinToString(" | ") { s -> s.transferAccount?.let { "[$it]" } ?: s.category?.joinToString(":").orEmpty() }
                t.transferAccount != null -> "[${t.transferAccount}]"
                else -> t.category?.joinToString(":")
            }
            val cleared = when (t.cleared) { ExportCleared.UNCLEARED -> ""; ExportCleared.CLEARED -> "c"; ExportCleared.RECONCILED -> "R" }
            out.append(
                listOf(t.date.toString(), t.payee, category, t.memo, amount(t.amount), currency, cleared, t.tags.joinToString(", "))
                    .joinToString(separator.toString(), transform = ::cell),
            ).append("\r\n")
        }
        return out.toString()
    }

    /**
     * QIF as Quicken writes it: the account first, then its transactions with dates as MM/DD/YYYY,
     * categories as Parent:Child, transfers as [Account], splits as S/E/$ lines.
     */
    fun qif(transactions: List<ExportTransaction>, accountName: String, kind: ExportAccountKind): String {
        val type = when (kind) {
            ExportAccountKind.BANK -> "Bank"
            ExportAccountKind.CASH -> "Cash"
            ExportAccountKind.CREDIT_CARD -> "CCard"
            ExportAccountKind.ASSET -> "Oth A"
            ExportAccountKind.LIABILITY -> "Oth L"
        }
        fun line(text: String?) = text.orEmpty().replace("\r", " ").replace("\n", " ")
        val out = StringBuilder()
        out.append("!Account\r\n").append("N").append(line(accountName)).append("\r\n").append("T").append(type).append("\r\n^\r\n")
        out.append("!Type:").append(type).append("\r\n")
        for (t in transactions) {
            out.append("D").append(t.date.toString().split('-').let { (y, m, d) -> "$m/$d/$y" }).append("\r\n")
            out.append("T").append(t.amount.toPlainString()).append("\r\n")
            when (t.cleared) {
                ExportCleared.UNCLEARED -> Unit
                ExportCleared.CLEARED -> out.append("C*\r\n")
                ExportCleared.RECONCILED -> out.append("CX\r\n")
            }
            t.payee?.takeIf { it.isNotBlank() }?.let { out.append("P").append(line(it)).append("\r\n") }
            t.memo?.takeIf { it.isNotBlank() }?.let { out.append("M").append(line(it)).append("\r\n") }
            if (t.splits.isEmpty()) {
                (t.transferAccount?.let { "[${line(it)}]" } ?: t.category?.joinToString(":") { line(it) })?.let { out.append("L").append(it).append("\r\n") }
            } else {
                for (s in t.splits) {
                    out.append("S").append(s.transferAccount?.let { "[${line(it)}]" } ?: s.category?.joinToString(":") { line(it) }.orEmpty()).append("\r\n")
                    s.memo?.takeIf { it.isNotBlank() }?.let { out.append("E").append(line(it)).append("\r\n") }
                    out.append("$").append(s.amount.toPlainString()).append("\r\n")
                }
            }
            out.append("^\r\n")
        }
        return out.toString()
    }

    /**
     * OFX 2.1 (XML): a bank or credit card statement holding the transactions, each with its id as
     * FITID so importing the same file twice finds the duplicates. [balance] is the account's
     * balance on [asOf], which OFX requires.
     */
    fun ofx(transactions: List<ExportTransaction>, accountId: String, currency: String, kind: ExportAccountKind, balance: BigDecimal, asOf: LocalDate): String {
        fun esc(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        fun day(d: LocalDate) = d.toString().replace("-", "")
        val card = kind == ExportAccountKind.CREDIT_CARD
        val start = transactions.minOfOrNull { it.date } ?: asOf
        val end = transactions.maxOfOrNull { it.date } ?: asOf
        val out = StringBuilder()
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n")
        out.append("<?OFX OFXHEADER=\"200\" VERSION=\"211\" SECURITY=\"NONE\" OLDFILEUID=\"NONE\" NEWFILEUID=\"NONE\"?>\n")
        out.append("<OFX>\n<SIGNONMSGSRSV1><SONRS><STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>")
        out.append("<DTSERVER>").append(day(asOf)).append("</DTSERVER><LANGUAGE>ENG</LANGUAGE></SONRS></SIGNONMSGSRSV1>\n")
        out.append(if (card) "<CREDITCARDMSGSRSV1><CCSTMTTRNRS>" else "<BANKMSGSRSV1><STMTTRNRS>")
        out.append("<TRNUID>0</TRNUID><STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>\n")
        out.append(if (card) "<CCSTMTRS>" else "<STMTRS>").append("<CURDEF>").append(esc(currency)).append("</CURDEF>\n")
        if (card) {
            out.append("<CCACCTFROM><ACCTID>").append(esc(accountId)).append("</ACCTID></CCACCTFROM>\n")
        } else {
            val type = if (kind == ExportAccountKind.BANK || kind == ExportAccountKind.CASH) "CHECKING" else "CREDITLINE"
            out.append("<BANKACCTFROM><BANKID>0</BANKID><ACCTID>").append(esc(accountId)).append("</ACCTID><ACCTTYPE>").append(type).append("</ACCTTYPE></BANKACCTFROM>\n")
        }
        out.append("<BANKTRANLIST><DTSTART>").append(day(start)).append("</DTSTART><DTEND>").append(day(end)).append("</DTEND>\n")
        for (t in transactions) {
            out.append("<STMTTRN><TRNTYPE>").append(if (t.amount.signum() < 0) "DEBIT" else "CREDIT").append("</TRNTYPE>")
            out.append("<DTPOSTED>").append(day(t.date)).append("</DTPOSTED>")
            out.append("<TRNAMT>").append(t.amount.toPlainString()).append("</TRNAMT>")
            out.append("<FITID>").append(esc(t.id)).append("</FITID>")
            t.payee?.takeIf { it.isNotBlank() }?.let { out.append("<NAME>").append(esc(it.trim().take(32))).append("</NAME>") }
            t.memo?.takeIf { it.isNotBlank() }?.let { out.append("<MEMO>").append(esc(it.trim().replace('\n', ' ').take(255))).append("</MEMO>") }
            out.append("</STMTTRN>\n")
        }
        out.append("</BANKTRANLIST>\n<LEDGERBAL><BALAMT>").append(balance.toPlainString()).append("</BALAMT><DTASOF>").append(day(asOf)).append("</DTASOF></LEDGERBAL>\n")
        out.append(if (card) "</CCSTMTRS></CCSTMTTRNRS></CREDITCARDMSGSRSV1>\n" else "</STMTRS></STMTTRNRS></BANKMSGSRSV1>\n")
        out.append("</OFX>\n")
        return out.toString()
    }
}
