package ca.schippers.hfm.ocr

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/**
 * Text recognition on the user's own device (OCR-01). Android uses ML Kit; the desktop uses
 * PaddleOCR through ONNX Runtime (ADR 0004). Both return the same shape, so the field extractors
 * that turn text into a draft transaction are shared.
 */
interface OcrEngine {
    val id: String

    /** Recognizes one page image (JPEG, PNG, BMP, GIF). */
    fun recognize(image: ByteArray): OcrResult
}

/** [lines] are visual rows in reading order: words on the same row of a receipt are joined. */
data class OcrResult(val lines: List<OcrLine>, val elapsedMillis: Long) {
    val text: String get() = lines.joinToString("\n") { it.text }
}

data class OcrLine(val text: String, val confidence: Float, val box: Box = Box(0, 0, 0, 0))

data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Where an extracted value came from, so the review screen can mark AI-read and low-confidence fields (OCR-05, AI step 5). */
enum class FieldSource { ON_DEVICE, CLOUD_AI, USER }

data class Extracted<T>(val value: T, val confidence: Float, val source: FieldSource = FieldSource.ON_DEVICE) {
    val needsReview: Boolean get() = source != FieldSource.USER && confidence < REVIEW_THRESHOLD

    companion object {
        const val REVIEW_THRESHOLD = 0.85f
    }
}

enum class TaxName { GST, HST, QST, PST, OTHER }

/** OCR-08: what kind of document this is. */
enum class DocumentKind { RECEIPT, BILL, INVOICE, OTHER, CARD_STATEMENT, BANK_STATEMENT, INVESTMENT_STATEMENT, PAY_STUB, EOB, TRADE_CONFIRMATION }

/** OCR-02 fields of a receipt, bill or invoice; every value is confirmed by the user before it is booked. */
data class DocumentDraft(
    val kind: DocumentKind = DocumentKind.OTHER,
    val merchant: Extracted<String>? = null,
    val date: Extracted<LocalDate>? = null,
    val total: Extracted<Money>? = null,
    val subtotal: Extracted<Money>? = null,
    val taxes: List<Pair<TaxName, Extracted<Money>>> = emptyList(),
    val currency: Currency = Currency.CAD,
    val paymentMethod: Extracted<String>? = null,
    val cardLast4: Extracted<String>? = null,
    val invoiceNumber: Extracted<String>? = null,
    val dueDate: Extracted<LocalDate>? = null,
    val accountNumber: Extracted<String>? = null,
    /** BILL-17, BILL-19: a utility bill's meter readings, when it shows them. */
    val meter: Extracted<MeterReadings>? = null,
)

/**
 * BILL-17: the meter readings a utility bill shows: the previous and current readings with their
 * dates, and the amount used, in [unit] ("KWH" or "M3") when the bill says. Any part may be missing.
 */
data class MeterReadings(
    val previous: BigDecimal? = null,
    val previousDate: LocalDate? = null,
    val current: BigDecimal? = null,
    val currentDate: LocalDate? = null,
    val used: BigDecimal? = null,
    val unit: String? = null,
) {
    val isEmpty: Boolean get() = previous == null && current == null && used == null

    /** The amount used: as printed, else the current reading less the previous one. */
    val usedOrComputed: BigDecimal? get() = used ?: if (previous != null && current != null && current >= previous) current - previous else null
}
