package ca.schippers.hfm.importers

import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.nio.charset.Charset

/** QIF account kinds, as Quicken, GnuCash and Moneydance write them. */
enum class QifAccountKind { BANK, CASH, CREDIT_CARD, ASSET, LIABILITY, INVESTMENT }

data class QifAccount(val name: String, val kind: QifAccountKind, val description: String?, val creditLimit: BigDecimal?)

data class QifCategory(val path: List<String>, val income: Boolean, val description: String?, val taxRelated: Boolean)

/** One line of a split transaction; [transferAccount] is set for "[Account]" lines. */
data class QifSplit(val category: List<String>?, val transferAccount: String?, val amount: BigDecimal, val memo: String?)

/** A bank, cash, card or other-account transaction. */
data class QifTransaction(
    val account: String,
    val date: QifDate,
    val amount: BigDecimal,
    val payee: String?,
    val memo: String?,
    val number: String?,
    val cleared: Char?,
    val category: List<String>?,
    val transferAccount: String?,
    val classes: List<String>,
    val splits: List<QifSplit>,
)

/** An investment action (Buy, Sell, Div, ReinvDiv, XIn...), kept for the investment module (Phase 3). */
data class QifInvestment(
    val account: String,
    val date: QifDate,
    val action: String,
    val security: String?,
    val price: BigDecimal?,
    val quantity: BigDecimal?,
    val amount: BigDecimal?,
    val commission: BigDecimal?,
    val memo: String?,
    val transferAccount: String?,
    val transferAmount: BigDecimal?,
)

/** A date as written; its day and month order is decided for the whole file (see [QifFile.dateOrder]). */
data class QifDate(val first: Int, val second: Int, val year: Int, val yearFirst: Boolean) {
    fun toLocalDate(order: DateOrder): LocalDate? = runCatching {
        when {
            yearFirst -> LocalDate(year, first, second)
            order == DateOrder.DAY_MONTH -> LocalDate(year, second, first)
            else -> LocalDate(year, first, second)
        }
    }.getOrNull()
}

enum class DateOrder { MONTH_DAY, DAY_MONTH }

data class QifFile(
    val accounts: List<QifAccount>,
    val categories: List<QifCategory>,
    val classes: List<String>,
    val transactions: List<QifTransaction>,
    val investments: List<QifInvestment>,
    /** What the dates say: decided when any day is over 12, otherwise null (ask the user). */
    val dateOrder: DateOrder?,
    val warnings: List<String>,
)

/**
 * Reads Quicken Interchange Format files (OTH-05): Quicken's QIF export, and the QIF written by
 * GnuCash and Moneydance. Accounts, categories, classes, transactions with splits and transfers,
 * and investment actions. QIF has no fixed date order or encoding, so both are worked out.
 */
object QifParser {

    fun parse(bytes: ByteArray): QifFile = parse(decode(bytes))

    fun parse(text: String): QifFile {
        val accounts = LinkedHashMap<String, QifAccount>()
        val categories = ArrayList<QifCategory>()
        val classes = ArrayList<String>()
        val transactions = ArrayList<QifTransaction>()
        val investments = ArrayList<QifInvestment>()
        val warnings = ArrayList<String>()
        var section = ""
        var currentAccount: QifAccount? = null
        var autoSwitch = false
        val record = ArrayList<String>()

        fun accountFor(kind: QifAccountKind): String {
            val account = currentAccount ?: QifAccount(DEFAULT_ACCOUNT, kind, null, null).also { accounts.putIfAbsent(it.name, it); currentAccount = it }
            return account.name
        }

        fun flush() {
            if (record.isEmpty()) return
            val fields = record.toList()
            record.clear()
            val kind = sectionKind(section)
            when {
                section == "account" -> {
                    val name = value(fields, 'N') ?: return
                    val account = QifAccount(name, accountKind(value(fields, 'T')), value(fields, 'D'), value(fields, 'L')?.let(::amount))
                    accounts[name] = accounts[name]?.let { if (it.kind == account.kind) it else account } ?: account
                    currentAccount = accounts[name]
                }
                section == "cat" -> value(fields, 'N')?.let { n ->
                    categories += QifCategory(n.split(':').map(String::trim), fields.any { it.startsWith("I") }, value(fields, 'D'), fields.any { it == "T" || it.startsWith("T") })
                }
                section == "class" -> value(fields, 'N')?.let { classes += it }
                section == "invst" -> parseInvestment(fields, accountFor(QifAccountKind.INVESTMENT), warnings)?.let { investments += it }
                kind != null -> parseTransaction(fields, accountFor(kind), warnings)?.let { transactions += it }
                else -> Unit // memorized transactions, prices, securities and the like are not needed
            }
        }

        for (raw in text.lines()) {
            val line = raw.trimEnd('\r')
            if (line.isBlank()) continue
            if (line.startsWith("!")) {
                flush()
                val header = line.substring(1).trim().lowercase()
                when {
                    header == "option:autoswitch" -> { autoSwitch = true; section = "account" }
                    header == "clear:autoswitch" -> autoSwitch = false
                    header == "account" -> section = "account"
                    header.startsWith("type:") -> section = header.removePrefix("type:").trim().replace(" ", "")
                    else -> section = header
                }
                continue
            }
            if (line == "^") {
                flush()
                continue
            }
            record += line
        }
        flush()
        if (!autoSwitch && accounts.isEmpty() && transactions.isNotEmpty()) warnings += "No account list: everything was read into one account."
        return QifFile(accounts.values.toList(), categories, classes, transactions, investments, dateOrder(transactions.map { it.date } + investments.map { it.date }), warnings)
    }

    /** The order of day and month: decided by any value over 12; null when every date could be either. */
    private fun dateOrder(dates: List<QifDate>): DateOrder? {
        val loose = dates.filter { !it.yearFirst }
        return when {
            loose.any { it.first > 12 } -> DateOrder.DAY_MONTH
            loose.any { it.second > 12 } -> DateOrder.MONTH_DAY
            loose.isEmpty() -> DateOrder.MONTH_DAY
            else -> null
        }
    }

    private fun parseTransaction(fields: List<String>, account: String, warnings: MutableList<String>): QifTransaction? {
        val date = value(fields, 'D')?.let(::date) ?: run { warnings += "A transaction without a valid date was skipped in $account."; return null }
        val amount = (value(fields, 'T') ?: value(fields, 'U'))?.let(::amount) ?: BigDecimal.ZERO
        val (category, transfer, classList) = categoryField(value(fields, 'L'))
        val splits = ArrayList<QifSplit>()
        var splitCategory: String? = null
        var splitMemo: String? = null
        for (f in fields) {
            when (f.firstOrNull()) {
                'S' -> {
                    splitCategory = f.substring(1)
                    splitMemo = null
                }
                'E' -> splitMemo = f.substring(1)
                '$' -> {
                    val (c, t, _) = categoryField(splitCategory)
                    splits += QifSplit(c, t, amount(f.substring(1)) ?: BigDecimal.ZERO, splitMemo?.ifBlank { null })
                    splitCategory = null
                }
            }
        }
        return QifTransaction(
            account, date, amount, value(fields, 'P')?.ifBlank { null }, value(fields, 'M')?.ifBlank { null }, value(fields, 'N')?.ifBlank { null },
            value(fields, 'C')?.firstOrNull(), category, transfer, classList, splits,
        )
    }

    private fun parseInvestment(fields: List<String>, account: String, warnings: MutableList<String>): QifInvestment? {
        val date = value(fields, 'D')?.let(::date) ?: run { warnings += "An investment action without a valid date was skipped in $account."; return null }
        val (_, transfer, _) = categoryField(value(fields, 'L'))
        return QifInvestment(
            account, date, value(fields, 'N').orEmpty(), value(fields, 'Y'), value(fields, 'I')?.let(::amount), value(fields, 'Q')?.let(::amount),
            (value(fields, 'T') ?: value(fields, 'U'))?.let(::amount), value(fields, 'O')?.let(::amount), value(fields, 'M'), transfer, value(fields, '$')?.let(::amount),
        )
    }

    /** "Food:Groceries/Vacation" is a category with a class; "[Savings]" is a transfer. */
    private fun categoryField(text: String?): Triple<List<String>?, String?, List<String>> {
        if (text.isNullOrBlank()) return Triple(null, null, emptyList())
        val slash = text.indexOf('/')
        val main = if (slash >= 0) text.substring(0, slash) else text
        val classes = if (slash >= 0) text.substring(slash + 1).split(':').map(String::trim).filter(String::isNotEmpty) else emptyList()
        val trimmed = main.trim()
        return if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            Triple(null, trimmed.substring(1, trimmed.length - 1).trim(), classes)
        } else {
            Triple(trimmed.split(':').map(String::trim).filter(String::isNotEmpty).ifEmpty { null }, null, classes)
        }
    }

    private fun value(fields: List<String>, code: Char): String? = fields.firstOrNull { it.firstOrNull() == code }?.substring(1)?.trim()

    /**
     * "12/31/2023", "12/31'23", " 1/ 5'99", "31.12.2023", "2023-12-31". Two-digit years before 70
     * are in this century, as Quicken writes them.
     */
    internal fun date(text: String): QifDate? {
        val t = text.replace(" ", "")
        Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$""").matchEntire(t)?.let { m ->
            return QifDate(m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[1].toInt(), yearFirst = true)
        }
        val m = Regex("""^(\d{1,2})[-/.](\d{1,2})(?:['/.-](\d{2,4}))?$""").matchEntire(t) ?: return null
        val y = m.groupValues[3].ifEmpty { return null }.toInt().let { if (it < 100) (if (it < 70) 2000 + it else 1900 + it) else it }
        return QifDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), y, yearFirst = false)
    }

    /** "1,234.56", "-1 234,56", "(12.30)": the last "." or "," followed by one or two digits is the decimal point. */
    internal fun amount(text: String): BigDecimal? {
        var t = text.trim().replace(" ", "").replace(" ", "").replace("$", "")
        var negative = false
        if (t.startsWith("(") && t.endsWith(")")) {
            negative = true
            t = t.substring(1, t.length - 1)
        }
        if (t.startsWith("-")) { negative = !negative; t = t.substring(1) }
        if (t.isEmpty()) return null
        val lastSep = t.indexOfLast { it == '.' || it == ',' }
        val normal = if (lastSep >= 0 && t.length - lastSep - 1 in 1..2) {
            t.substring(0, lastSep).filter(Char::isDigit) + "." + t.substring(lastSep + 1)
        } else {
            t.filter(Char::isDigit)
        }
        return normal.toBigDecimalOrNull()?.let { if (negative) it.negate() else it }
    }

    private fun accountKind(type: String?): QifAccountKind = when (type?.lowercase()?.replace(" ", "")) {
        "cash" -> QifAccountKind.CASH
        "ccard", "creditcard" -> QifAccountKind.CREDIT_CARD
        "otha", "asset" -> QifAccountKind.ASSET
        "othl", "liability" -> QifAccountKind.LIABILITY
        "invst", "port", "401(k)/403(b)", "mutual", "investment" -> QifAccountKind.INVESTMENT
        else -> QifAccountKind.BANK
    }

    private fun sectionKind(section: String): QifAccountKind? = when (section) {
        "bank" -> QifAccountKind.BANK
        "cash" -> QifAccountKind.CASH
        "ccard" -> QifAccountKind.CREDIT_CARD
        "otha" -> QifAccountKind.ASSET
        "othl" -> QifAccountKind.LIABILITY
        else -> null
    }

    /** QIF has no declared encoding: UTF-8 when valid, otherwise Windows-1252 (Quicken on Windows). */
    fun decode(bytes: ByteArray): String {
        val utf8 = Charsets.UTF_8.newDecoder()
        return runCatching { utf8.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }.getOrElse { String(bytes, Charset.forName("windows-1252")) }.removePrefix("﻿")
    }

    private const val DEFAULT_ACCOUNT = "Quicken"
}
