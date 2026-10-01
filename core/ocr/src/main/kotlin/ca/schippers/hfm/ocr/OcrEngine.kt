package ca.schippers.hfm.ocr

import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate

/**
 * Text recognition on the user's own device (OCR-01). Android uses ML Kit; the desktop uses
 * PaddleOCR through ONNX Runtime (see spikes/ocr-paddle). Both return the same shape, so the field
 * extractors that turn text into a draft transaction are shared.
 */
interface OcrEngine {
    val id: String
    fun recognize(image: ByteArray): OcrResult
}

data class OcrResult(val lines: List<OcrLine>, val elapsedMillis: Long)

data class OcrLine(val text: String, val confidence: Float, val box: Box)

data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Where an extracted value came from, so the review screen can mark AI-read and low-confidence fields (OCR-05, AI step 5). */
enum class FieldSource { ON_DEVICE, CLOUD_AI, USER }

data class Extracted<T>(val value: T, val confidence: Float, val source: FieldSource) {
    val needsReview: Boolean get() = source != FieldSource.USER && confidence < REVIEW_THRESHOLD

    companion object {
        const val REVIEW_THRESHOLD = 0.85f
    }
}

enum class TaxName { GST, HST, QST, PST, OTHER }

/** OCR-02 fields of a receipt, bill or invoice; every value is confirmed by the user before it is booked. */
data class DocumentDraft(
    val merchant: Extracted<String>? = null,
    val date: Extracted<LocalDate>? = null,
    val total: Extracted<Money>? = null,
    val taxes: List<Pair<TaxName, Extracted<Money>>> = emptyList(),
    val paymentMethod: Extracted<String>? = null,
    val cardLast4: Extracted<String>? = null,
    val invoiceNumber: Extracted<String>? = null,
    val dueDate: Extracted<LocalDate>? = null,
    val accountNumber: Extracted<String>? = null,
)
