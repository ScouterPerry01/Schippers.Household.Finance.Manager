package ca.schippers.hfm.ocr.desktop

import ca.schippers.hfm.ocr.OcrLine
import ca.schippers.hfm.ocr.OcrResult
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** What kind of file a document is, from its first bytes rather than its name. */
enum class FileKind(val mimeType: String) {
    PDF("application/pdf"), JPEG("image/jpeg"), PNG("image/png"), OTHER_IMAGE("image/*"), UNSUPPORTED("application/octet-stream");

    companion object {
        fun of(bytes: ByteArray): FileKind = when {
            bytes.size >= 5 && bytes.copyOfRange(0, 5).decodeToString() == "%PDF-" -> PDF
            bytes.size >= 3 && (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xD8 -> JPEG
            bytes.size >= 8 && (bytes[0].toInt() and 0xFF) == 0x89 && bytes.copyOfRange(1, 4).decodeToString() == "PNG" -> PNG
            ImageLoader.isImage(bytes) -> OTHER_IMAGE
            else -> UNSUPPORTED
        }
    }
}

/** A document read for its text, with its page count and a small preview of the first page. */
class ReadDocument(val kind: FileKind, val pages: Int, val result: OcrResult, val fromTextLayer: Boolean)

/**
 * Reads the text of an imported file (CAP-03, OCR-01): images through the OCR engine, PDFs through
 * their text layer when they have one (e-bills usually do, and it is exact) and otherwise by
 * rendering each page and recognising it.
 */
class DocumentReader(private val engine: PaddleOcrEngine) {

    fun read(bytes: ByteArray): ReadDocument = when (val kind = FileKind.of(bytes)) {
        FileKind.PDF -> readPdf(bytes)
        FileKind.UNSUPPORTED -> throw UnsupportedImageException()
        else -> ReadDocument(kind, 1, engine.recognize(ImageLoader.decode(bytes) ?: throw UnsupportedImageException()), fromTextLayer = false)
    }

    /** The first page as a PNG or JPEG image, at most [maxSide] pixels, for previews. */
    fun preview(bytes: ByteArray, maxSide: Int = 1200): BufferedImage? = when (FileKind.of(bytes)) {
        FileKind.PDF -> Loader.loadPDF(bytes).use { pdf -> if (pdf.numberOfPages == 0) null else PDFRenderer(pdf).renderImageWithDPI(0, PREVIEW_DPI, ImageType.RGB) }
        FileKind.UNSUPPORTED -> null
        else -> ImageLoader.decode(bytes)
    }?.let { scale(it, maxSide) }

    private fun readPdf(bytes: ByteArray): ReadDocument = Loader.loadPDF(bytes).use { pdf ->
        val started = System.nanoTime()
        val text = PDFTextStripper().apply { sortByPosition = true }.getText(pdf)
        if (text.count(Char::isLetterOrDigit) >= MIN_TEXT_LAYER) {
            val lines = text.lines().map { it.trim().replace(Regex("\\s{2,}"), "  ") }.filter { it.isNotEmpty() }.map { OcrLine(it, 1f) }
            return ReadDocument(FileKind.PDF, pdf.numberOfPages, OcrResult(lines, (System.nanoTime() - started) / 1_000_000), fromTextLayer = true)
        }
        // A scanned PDF: recognise each page (the first few; statements have their totals early).
        val renderer = PDFRenderer(pdf)
        val lines = ArrayList<OcrLine>()
        for (page in 0 until minOf(pdf.numberOfPages, MAX_OCR_PAGES)) {
            lines += engine.recognize(renderer.renderImageWithDPI(page, OCR_DPI, ImageType.RGB)).lines
        }
        ReadDocument(FileKind.PDF, pdf.numberOfPages, OcrResult(lines, (System.nanoTime() - started) / 1_000_000), fromTextLayer = false)
    }

    private fun scale(img: BufferedImage, maxSide: Int): BufferedImage {
        val longSide = maxOf(img.width, img.height)
        if (longSide <= maxSide) return img
        val f = maxSide.toDouble() / longSide
        val out = BufferedImage((img.width * f).toInt().coerceAtLeast(1), (img.height * f).toInt().coerceAtLeast(1), BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply {
            setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(img, 0, 0, out.width, out.height, null)
            dispose()
        }
        return out
    }

    companion object {
        private const val MIN_TEXT_LAYER = 40
        private const val MAX_OCR_PAGES = 5
        private const val OCR_DPI = 200f
        private const val PREVIEW_DPI = 110f

        fun png(img: BufferedImage): ByteArray = ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
    }
}

/** CAP-02: the pages of a multi-page capture, as one PDF, each page the size of its photo at 150 DPI. */
object PdfPages {
    fun fromJpegs(pages: List<ByteArray>): ByteArray = org.apache.pdfbox.pdmodel.PDDocument().use { doc ->
        for ((i, bytes) in pages.withIndex()) {
            val image = org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromByteArray(doc, bytes, "page-$i.jpg")
            val w = image.width * 72f / 150f
            val h = image.height * 72f / 150f
            val page = org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle(w, h))
            doc.addPage(page)
            org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page).use { it.drawImage(image, 0f, 0f, w, h) }
        }
        java.io.ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }
}
