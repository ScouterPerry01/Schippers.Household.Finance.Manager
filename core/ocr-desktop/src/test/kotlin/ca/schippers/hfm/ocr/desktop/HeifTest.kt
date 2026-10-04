package ca.schippers.hfm.ocr.desktop

import org.apache.pdfbox.Loader
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CAP-03: HEIC photos through the bundled libheif (made with libheif's own encoder, x265). */
class HeifTest {

    private fun resource(name: String) = checkNotNull(javaClass.getResourceAsStream("/heic/$name")).use { it.readBytes() }

    private fun jpeg(): ByteArray = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", it) }.toByteArray()

    private fun BufferedImage.rgb(x: Int, y: Int) = getRGB(x, y).let { Triple(it shr 16 and 0xFF, it shr 8 and 0xFF, it and 0xFF) }

    @Test
    fun `the decoder loads on this platform`() {
        assertTrue(Heif.available, "the native library is missing or failed to load")
        assertTrue(Heif.version().startsWith("1."), Heif.version())
    }

    @Test
    fun `HEIC is recognised by its brand`() {
        assertTrue(Heif.isHeic(resource("rotated.heic")))
        assertEquals(FileKind.HEIC, FileKind.of(resource("rotated.heic")))
        assertFalse(Heif.isHeic(jpeg()))
        assertFalse(Heif.isHeic(ByteArray(64)))
        // An AVIF file is HEIF too, but not HEVC.
        val avif = byteArrayOf(0, 0, 0, 0x1C) + "ftypavif".toByteArray() + ByteArray(4) + "mif1miafavif".toByteArray()
        assertFalse(Heif.isHeic(avif))
    }

    @Test
    fun `photos are turned upright`() {
        // Stored 64 x 32, red on the left and blue on the right, with a quarter turn clockwise.
        val img = assertNotNull(ImageLoader.decode(resource("rotated.heic")))
        assertEquals(32 to 64, img.width to img.height)
        val (r1, _, b1) = img.rgb(16, 8)
        val (r2, _, b2) = img.rgb(16, 56)
        assertTrue(r1 > 180 && b1 < 80, "red at the top")
        assertTrue(b2 > 180 && r2 < 80, "blue at the bottom")
    }

    @Test
    fun `large photos are reduced while decoding`() {
        val img = assertNotNull(Heif.decode(resource("rotated.heic"), maxSide = 16))
        assertEquals(8 to 16, img.width to img.height)
    }

    @Test
    fun `damaged files are refused, not crashed on`() {
        val bytes = resource("rotated.heic")
        assertNull(Heif.decode(bytes.copyOf(bytes.size / 2), 3200))
        assertNull(Heif.decode(bytes.copyOf(40), 3200))
    }

    @Test
    fun `text is read from a HEIC photo`() {
        PaddleOcrEngine().use { engine ->
            val text = DocumentReader(engine).read(resource("receipt-line.heic")).result.text
            assertTrue("12.34" in text, text)
        }
    }

    @Test
    fun `HEIC photos go into PDFs as JPEG`() {
        val bundle = PdfPages.bundle("Receipts", listOf("one photo"), listOf("image/heic" to resource("receipt-line.heic")))
        Loader.loadPDF(bundle).use { assertEquals(2, it.numberOfPages) }
        val inventory = PdfPages.inventory("Inventory", emptyList(), listOf(listOf("Chair") to listOf(resource("rotated.heic"))))
        Loader.loadPDF(inventory).use { doc ->
            assertEquals(1, doc.getPage(0).resources.xObjectNames.count(), "the photo is on the page")
        }
    }
}
