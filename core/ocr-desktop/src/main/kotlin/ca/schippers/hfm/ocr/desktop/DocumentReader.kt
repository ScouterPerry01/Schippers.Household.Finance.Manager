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
    PDF("application/pdf"), JPEG("image/jpeg"), PNG("image/png"), HEIC("image/heic"), OTHER_IMAGE("image/*"), UNSUPPORTED("application/octet-stream");

    companion object {
        fun of(bytes: ByteArray): FileKind = when {
            bytes.size >= 5 && bytes.copyOfRange(0, 5).decodeToString() == "%PDF-" -> PDF
            bytes.size >= 3 && (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xD8 -> JPEG
            bytes.size >= 8 && (bytes[0].toInt() and 0xFF) == 0x89 && bytes.copyOfRange(1, 4).decodeToString() == "PNG" -> PNG
            Heif.isHeic(bytes) -> HEIC
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
        FileKind.HEIC -> if (Heif.available) ReadDocument(kind, 1, engine.recognize(Heif.decode(bytes, 3200) ?: throw UnsupportedImageException()), fromTextLayer = false) else throw HeicDecoderMissingException()
        else -> ReadDocument(kind, 1, engine.recognize(ImageLoader.decode(bytes) ?: throw UnsupportedImageException()), fromTextLayer = false)
    }

    /** The first page as a PNG or JPEG image, at most [maxSide] pixels, for previews. */
    fun preview(bytes: ByteArray, maxSide: Int = 1200): BufferedImage? = when (FileKind.of(bytes)) {
        FileKind.PDF -> Loader.loadPDF(bytes).use { pdf -> if (pdf.numberOfPages == 0) null else PDFRenderer(pdf).renderImageWithDPI(0, PREVIEW_DPI, ImageType.RGB) }
        FileKind.UNSUPPORTED -> null
        else -> ImageLoader.decode(bytes)
    }?.let { scale(it, maxSide) }

    /**
     * Section 4.5: the pages to show before an AI reading and, once the user has cropped and
     * blurred them, to send. An image is one page; a PDF gives up to [maxPages] pages at [dpi].
     */
    fun pageImages(bytes: ByteArray, maxPages: Int = 20, dpi: Float = AI_DPI): List<BufferedImage> = when (FileKind.of(bytes)) {
        FileKind.PDF -> Loader.loadPDF(bytes).use { pdf ->
            val renderer = PDFRenderer(pdf)
            (0 until minOf(pdf.numberOfPages, maxPages)).map { renderer.renderImageWithDPI(it, dpi, ImageType.RGB) }
        }
        FileKind.UNSUPPORTED -> emptyList()
        else -> listOfNotNull(ImageLoader.decode(bytes))
    }

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

    private fun scale(img: BufferedImage, maxSide: Int): BufferedImage = scaleDown(img, maxSide)

    companion object {
        /** DOC-01: how many pages the viewer can show: a PDF's pages, one for an image, none for anything else. */
        fun pageCount(bytes: ByteArray): Int = when (FileKind.of(bytes)) {
            FileKind.PDF -> Loader.loadPDF(bytes).use { it.numberOfPages }
            FileKind.UNSUPPORTED -> 0
            else -> 1
        }

        /**
         * DOC-01: page [index] (from 0) for the viewer, sharp enough to zoom into: a PDF page at
         * [dpi], an image at most [maxSide] pixels; null when there is no such page.
         */
        fun page(bytes: ByteArray, index: Int, maxSide: Int = VIEW_SIDE, dpi: Float = VIEW_DPI): BufferedImage? = when (FileKind.of(bytes)) {
            FileKind.PDF -> Loader.loadPDF(bytes).use { pdf -> if (index !in 0 until pdf.numberOfPages) null else PDFRenderer(pdf).renderImageWithDPI(index, dpi, ImageType.RGB) }
            FileKind.UNSUPPORTED -> null
            else -> if (index == 0) ImageLoader.decode(bytes) else null
        }?.let { scaleDown(it, maxSide) }

        private fun scaleDown(img: BufferedImage, maxSide: Int): BufferedImage {
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

        private const val VIEW_SIDE = 2400
        private const val VIEW_DPI = 150f
        private const val MIN_TEXT_LAYER = 40
        private const val MAX_OCR_PAGES = 5
        private const val OCR_DPI = 200f
        private const val PREVIEW_DPI = 110f
        private const val AI_DPI = 150f

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

    /**
     * AST-04: a home inventory for an insurer after a loss: each item's description lines, then its
     * photos, several to a page.
     */
    fun inventory(title: String, summary: List<String>, items: List<Pair<List<String>, List<ByteArray>>>): ByteArray = org.apache.pdfbox.pdmodel.PDDocument().use { doc ->
        val font = org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA)
        val bold = org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD)
        fun safe(text: String, f: org.apache.pdfbox.pdmodel.font.PDType1Font) = text.map { c -> if (runCatching { f.encode(c.toString()) }.isSuccess) c else '?' }.joinToString("")
        val box = org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER
        var page = org.apache.pdfbox.pdmodel.PDPage(box).also { doc.addPage(it) }
        var cs = org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)
        var y = box.height - 56f
        fun newPage() {
            cs.close()
            page = org.apache.pdfbox.pdmodel.PDPage(box).also { doc.addPage(it) }
            cs = org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)
            y = box.height - 56f
        }
        fun line(text: String, f: org.apache.pdfbox.pdmodel.font.PDType1Font, size: Float) {
            if (y < 56f) newPage()
            cs.beginText(); cs.setFont(f, size); cs.newLineAtOffset(56f, y); cs.showText(safe(text, f)); cs.endText()
            y -= size + 4f
        }
        try {
            line(title, bold, 14f)
            summary.forEach { line(it, font, 10f) }
            y -= 10f
            for ((lines, photos) in items) {
                if (y < 140f) newPage()
                lines.forEachIndexed { i, t -> line(t, if (i == 0) bold else font, if (i == 0) 11f else 9f) }
                // Photos in a row, up to 160 points tall.
                var x = 56f
                for (bytes in photos) {
                    val image = runCatching { pdfImage(doc, bytes, "photo") }.getOrNull() ?: continue
                    val scale = minOf(160f / image.height, 220f / image.width)
                    val w = image.width * scale
                    val h = image.height * scale
                    if (x + w > box.width - 56f) { x = 56f; y -= 170f }
                    if (y - h < 56f) { newPage(); x = 56f }
                    cs.drawImage(image, x, y - h, w, h)
                    x += w + 10f
                }
                if (photos.isNotEmpty()) y -= 170f
                y -= 8f
            }
        } finally {
            cs.close()
        }
        java.io.ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }

    /**
     * MED-15: one PDF of supporting receipts: a cover page listing [cover] lines, then each
     * document in turn, PDFs page by page and images on a page of their own. A HEIC photo this
     * computer cannot read gets a page saying [heicMissing] instead.
     */
    fun bundle(title: String, cover: List<String>, documents: List<Pair<String, ByteArray>>, heicMissing: String = "HEIC photo: no HEIC decoder installed"): ByteArray = org.apache.pdfbox.pdmodel.PDDocument().use { doc ->
        val font = org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA)
        val bold = org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD)
        // The standard fonts cover Western European text; anything else is replaced so the page still prints.
        fun safe(text: String, f: org.apache.pdfbox.pdmodel.font.PDType1Font) = text.map { c -> if (runCatching { f.encode(c.toString()) }.isSuccess) c else '?' }.joinToString("")
        val lines = listOf(title) + cover
        lines.chunked(48).forEachIndexed { pageIndex, chunk ->
            val page = org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER)
            doc.addPage(page)
            org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
                var y = page.mediaBox.height - 56f
                chunk.forEachIndexed { i, line ->
                    val f = if (pageIndex == 0 && i == 0) bold else font
                    cs.beginText()
                    cs.setFont(f, if (pageIndex == 0 && i == 0) 14f else 10f)
                    cs.newLineAtOffset(56f, y)
                    cs.showText(safe(line, f))
                    cs.endText()
                    y -= if (pageIndex == 0 && i == 0) 24f else 14f
                }
            }
        }
        val opened = ArrayList<org.apache.pdfbox.pdmodel.PDDocument>()
        try {
            for ((mime, bytes) in documents) {
                if (mime == "application/pdf") {
                    val source = org.apache.pdfbox.Loader.loadPDF(bytes).also { opened += it }
                    for (p in source.pages) doc.importPage(p)
                } else if (Heif.isHeic(bytes) && !Heif.available) {
                    val page = org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER)
                    doc.addPage(page)
                    org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
                        cs.beginText(); cs.setFont(font, 11f); cs.newLineAtOffset(56f, page.mediaBox.height - 72f); cs.showText(safe(heicMissing, font)); cs.endText()
                    }
                } else {
                    val image = pdfImage(doc, bytes, "receipt")
                    val box = org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER
                    val scale = minOf((box.width - 72f) / image.width, (box.height - 72f) / image.height, 1f)
                    val page = org.apache.pdfbox.pdmodel.PDPage(box)
                    doc.addPage(page)
                    org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page).use { it.drawImage(image, 36f, box.height - 36f - image.height * scale, image.width * scale, image.height * scale) }
                }
            }
            java.io.ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        } finally {
            opened.forEach { it.close() }
        }
    }

    /** An image for a PDF page; PDFBox does not read HEIC, so those photos go in as JPEG. */
    private fun pdfImage(doc: org.apache.pdfbox.pdmodel.PDDocument, bytes: ByteArray, name: String): org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject =
        if (FileKind.of(bytes) == FileKind.HEIC) {
            org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromImage(doc, ImageLoader.decode(bytes) ?: throw HeicDecoderMissingException(), 0.9f)
        } else {
            org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromByteArray(doc, bytes, name)
        }
}
