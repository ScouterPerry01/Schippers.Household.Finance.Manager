package ca.schippers.hfm.importers

import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import java.io.InputStream
import java.math.BigDecimal
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * How to read one institution's CSV export (REC-01). Columns are 0-based. Amounts come either
 * from one signed [amountColumn] or from separate [debitColumn] (money out) and [creditColumn]
 * (money in). Saved per institution so the next file imports without questions.
 */
data class CsvMapping(
    val delimiter: Char = ',',
    val hasHeader: Boolean = true,
    val skipRows: Int = 0,
    val dateColumn: Int = 0,
    val dateFormat: String = "yyyy-MM-dd",
    val amountColumn: Int? = null,
    val debitColumn: Int? = null,
    val creditColumn: Int? = null,
    val payeeColumn: Int? = null,
    val memoColumn: Int? = null,
    val balanceColumn: Int? = null,
    /** Amounts written as 1 234,56 rather than 1,234.56. */
    val decimalComma: Boolean = false,
    /** Some card exports show purchases as positive numbers; this flips the signs. */
    val negate: Boolean = false,
    val charset: String = "UTF-8",
) {
    fun toMap(): Map<String, String> = buildMap {
        put("delimiter", if (delimiter == '\t') "TAB" else delimiter.toString())
        put("hasHeader", hasHeader.toString())
        put("skipRows", skipRows.toString())
        put("dateColumn", dateColumn.toString())
        put("dateFormat", dateFormat)
        amountColumn?.let { put("amountColumn", it.toString()) }
        debitColumn?.let { put("debitColumn", it.toString()) }
        creditColumn?.let { put("creditColumn", it.toString()) }
        payeeColumn?.let { put("payeeColumn", it.toString()) }
        memoColumn?.let { put("memoColumn", it.toString()) }
        balanceColumn?.let { put("balanceColumn", it.toString()) }
        put("decimalComma", decimalComma.toString())
        put("negate", negate.toString())
        put("charset", charset)
    }

    fun isValid(): Boolean = amountColumn != null || debitColumn != null || creditColumn != null

    companion object {
        fun fromMap(m: Map<String, String>): CsvMapping = CsvMapping(
            delimiter = m["delimiter"]?.let { if (it == "TAB") '\t' else it.firstOrNull() } ?: ',',
            hasHeader = m["hasHeader"]?.toBoolean() ?: true,
            skipRows = m["skipRows"]?.toIntOrNull() ?: 0,
            dateColumn = m["dateColumn"]?.toIntOrNull() ?: 0,
            dateFormat = m["dateFormat"] ?: "yyyy-MM-dd",
            amountColumn = m["amountColumn"]?.toIntOrNull(),
            debitColumn = m["debitColumn"]?.toIntOrNull(),
            creditColumn = m["creditColumn"]?.toIntOrNull(),
            payeeColumn = m["payeeColumn"]?.toIntOrNull(),
            memoColumn = m["memoColumn"]?.toIntOrNull(),
            balanceColumn = m["balanceColumn"]?.toIntOrNull(),
            decimalComma = m["decimalComma"]?.toBoolean() ?: false,
            negate = m["negate"]?.toBoolean() ?: false,
            charset = m["charset"] ?: "UTF-8",
        )
    }
}

/** The first rows of a CSV file and a guessed mapping, shown to the user before importing. */
data class CsvPreview(val rows: List<List<String>>, val guess: CsvMapping)

class CsvImporter : StatementImporter {
    override val id = "csv"
    override val fileExtensions = setOf("csv", "txt")

    override fun canRead(fileName: String, head: ByteArray): Boolean = fileName.substringAfterLast('.').lowercase() in fileExtensions

    override fun read(input: InputStream, options: ImportOptions): List<ImportedStatement> {
        val mapping = CsvMapping.fromMap(options.mapping)
        if (!mapping.isValid()) throw ImportException("Choose which column holds the amount")
        val bytes = input.readBytes()
        val rows = rows(decode(bytes, mapping.charset), mapping.delimiter).drop(mapping.skipRows).drop(if (mapping.hasHeader) 1 else 0)
        val currency = options.defaultCurrency
        val formatter = DateTimeFormatter.ofPattern(mapping.dateFormat, Locale.CANADA)

        data class Row(val line: ImportedLine, val balance: Money?)
        val parsed = rows.filter { row -> row.any { it.isNotBlank() } }.mapIndexed { index, row ->
            fun cell(i: Int?) = i?.let { row.getOrNull(it)?.trim() }?.ifEmpty { null }
            val dateText = cell(mapping.dateColumn) ?: throw ImportException("Row ${index + 1}: no date")
            val date = try {
                java.time.LocalDate.parse(dateText, formatter).let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }
            } catch (e: DateTimeParseException) {
                throw ImportException("Row ${index + 1}: \"$dateText\" does not match the date format ${mapping.dateFormat}", e)
            }
            var value = when {
                mapping.amountColumn != null -> number(cell(mapping.amountColumn), mapping, index) ?: BigDecimal.ZERO
                else -> (number(cell(mapping.creditColumn), mapping, index) ?: BigDecimal.ZERO).abs()
                    .subtract((number(cell(mapping.debitColumn), mapping, index) ?: BigDecimal.ZERO).abs())
            }
            if (mapping.negate) value = value.negate()
            Row(
                ImportedLine(null, date, Money.of(value, currency), cell(mapping.payeeColumn), cell(mapping.memoColumn), null),
                number(cell(mapping.balanceColumn), mapping, index)?.let { Money.of(it, currency) },
            )
        }
        val lines = parsed.map { it.line }
        // Some banks list newest first; the balance on the latest row is the closing balance.
        val latest = parsed.withIndex().maxWithOrNull(compareBy({ it.value.line.date }, { if (isNewestFirst(lines)) -it.index else it.index }))
        val closing = latest?.value?.balance
        return listOf(
            ImportedStatement(
                format = "CSV",
                accountNumberHint = null,
                currency = currency,
                periodStart = lines.minOfOrNull { it.date },
                periodEnd = lines.maxOfOrNull { it.date },
                openingBalance = null,
                closingBalance = closing,
                lines = lines,
            ),
        )
    }

    /** Reads the start of a file and guesses its layout from headers in English or French. */
    fun preview(bytes: ByteArray, maxRows: Int = 15): CsvPreview {
        val charset = if (isUtf8(bytes)) "UTF-8" else "windows-1252"
        val text = decode(bytes, charset)
        val delimiter = listOf(',', ';', '\t', '|').maxBy { d -> text.lineSequence().take(5).sumOf { line -> line.count { it == d } } }
        val all = rows(text, delimiter)
        val sample = all.take(maxRows)
        val first = sample.firstOrNull().orEmpty()
        val hasHeader = first.none { looksLikeDate(it) } && first.any { cell -> cell.any(Char::isLetter) }
        val header = if (hasHeader) first.map { it.lowercase(Locale.ROOT).trim() } else emptyList()
        val data = all.drop(if (hasHeader) 1 else 0).take(50)

        fun headerIndex(vararg names: String): Int? =
            header.indexOfFirst { h -> names.any { n -> h == n || h.contains(n) } }.takeIf { it >= 0 }
        fun numericColumns(): List<Int> = (0 until (data.maxOfOrNull { it.size } ?: 0)).filter { c ->
            val values = data.mapNotNull { it.getOrNull(c)?.trim()?.ifEmpty { null } }
            // An amount column is filled on most rows (a cheque-number column is not).
            values.size * 5 >= data.size * 4 && values.all { it.matches(Regex("""[-+(]?[$]?\s?[\d\s.,]+\)?\s?\$?""")) && it.any(Char::isDigit) }
        }

        val dateColumn = headerIndex("date", "posted", "transaction")
            ?: (0 until (data.firstOrNull()?.size ?: 0)).firstOrNull { c -> data.isNotEmpty() && data.all { looksLikeDate(it.getOrNull(c).orEmpty()) } }
            ?: 0
        val debit = headerIndex("debit", "débit", "withdrawal", "retrait", "paiement")
        val credit = headerIndex("credit", "crédit", "deposit", "dépôt", "depot")
        val amount = if (debit == null && credit == null) {
            headerIndex("amount", "montant", "cad$", "cad") ?: numericColumns().firstOrNull { it != dateColumn }
        } else {
            null
        }
        val balance = headerIndex("balance", "solde")
        val payee = headerIndex("description", "payee", "bénéficiaire", "libellé", "name", "nom", "merchant", "marchand")
            ?: (0 until (data.firstOrNull()?.size ?: 0)).firstOrNull { c ->
                c != dateColumn && data.any { row -> row.getOrNull(c).orEmpty().count(Char::isLetter) > 3 }
            }
        val memo = headerIndex("memo", "note", "détail", "detail").takeIf { it != payee }
        val numbers = data.flatMap { row -> listOfNotNull(amount, debit, credit).mapNotNull { row.getOrNull(it) } }
        val decimalComma = numbers.any { Regex("""\d,\d{2}\s*\$?\)?$""").containsMatchIn(it.trim()) } && numbers.none { Regex("""\d\.\d{2}$""").containsMatchIn(it.trim()) }

        return CsvPreview(
            sample,
            CsvMapping(
                delimiter = delimiter,
                hasHeader = hasHeader,
                dateColumn = dateColumn,
                dateFormat = guessDateFormat(data.mapNotNull { it.getOrNull(dateColumn) }),
                amountColumn = amount,
                debitColumn = debit,
                creditColumn = credit,
                payeeColumn = payee,
                memoColumn = memo,
                balanceColumn = balance,
                decimalComma = decimalComma,
                charset = charset,
            ),
        )
    }

    private fun number(text: String?, mapping: CsvMapping, row: Int): BigDecimal? {
        if (text == null) return null
        var s = text.replace("$", "").replace(" ", "").replace(" ", "").replace(" ", "")
        val negative = s.startsWith("(") && s.endsWith(")") || s.endsWith("-")
        s = s.removePrefix("(").removeSuffix(")").removeSuffix("-").removePrefix("+")
        s = if (mapping.decimalComma) s.replace(".", "").replace(',', '.') else s.replace(",", "")
        val value = s.toBigDecimalOrNull() ?: throw ImportException("Row ${row + 1}: \"$text\" is not a number")
        return if (negative) value.abs().negate() else value
    }

    private fun isNewestFirst(lines: List<ImportedLine>): Boolean = lines.size > 1 && lines.first().date > lines.last().date

    companion object {
        private val DATE_FORMATS = listOf("yyyy-MM-dd", "yyyy/MM/dd", "yyyyMMdd", "MM/dd/yyyy", "dd/MM/yyyy", "M/d/yyyy", "d/M/yyyy", "dd-MM-yyyy", "yyyy-M-d")

        /** Picks the first format that reads every sample; day-first is chosen only when a day above 12 proves it. */
        fun guessDateFormat(samples: List<String>): String {
            val values = samples.map { it.trim() }.filter { it.isNotEmpty() }
            if (values.isEmpty()) return "yyyy-MM-dd"
            return DATE_FORMATS.firstOrNull { pattern ->
                val f = DateTimeFormatter.ofPattern(pattern, Locale.CANADA)
                values.all { runCatching { java.time.LocalDate.parse(it, f) }.isSuccess }
            } ?: "yyyy-MM-dd"
        }

        private fun looksLikeDate(s: String): Boolean =
            s.trim().matches(Regex("""\d{4}[-/]?\d{1,2}[-/]?\d{1,2}|\d{1,2}[-/]\d{1,2}[-/]\d{4}"""))

        /** Splits CSV text into rows, honouring quotes ("a, b" and "" for a quote character). */
        fun rows(text: String, delimiter: Char): List<List<String>> {
            val rows = mutableListOf<List<String>>()
            var row = mutableListOf<String>()
            val cell = StringBuilder()
            var quoted = false
            var i = 0
            val s = text.removePrefix("﻿")
            while (i < s.length) {
                val c = s[i]
                when {
                    quoted && c == '"' && s.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                    c == '"' -> quoted = !quoted
                    !quoted && c == delimiter -> { row += cell.toString(); cell.clear() }
                    !quoted && (c == '\n' || c == '\r') -> {
                        if (c == '\r' && s.getOrNull(i + 1) == '\n') i++
                        row += cell.toString(); cell.clear()
                        if (row.any { it.isNotEmpty() }) rows += row
                        row = mutableListOf()
                    }
                    else -> cell.append(c)
                }
                i++
            }
            if (cell.isNotEmpty() || row.isNotEmpty()) {
                row += cell.toString()
                if (row.any { it.isNotEmpty() }) rows += row
            }
            return rows
        }

        private fun decode(bytes: ByteArray, charset: String): String = String(bytes, Charset.forName(charset))

        private fun isUtf8(bytes: ByteArray): Boolean = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes))
            true
        } catch (_: CharacterCodingException) {
            false
        }
    }
}
