package spike.ocr

import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * javax.imageio loader that also applies the JPEG EXIF Orientation tag (ImageIO ignores it, so phone photos
 * come out sideways). Supports JPEG/PNG/BMP/GIF out of the box; HEIC and WebP are NOT supported by ImageIO.
 */
object ImageLoader {

    fun load(path: Path): BufferedImage? {
        val bytes = Files.readAllBytes(path)
        val img = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
        return applyOrientation(img, exifOrientation(bytes))
    }

    /** Minimal EXIF parser: finds APP1 "Exif", reads IFD0 tag 0x0112. Returns 1 when absent. */
    fun exifOrientation(b: ByteArray): Int {
        if (b.size < 4 || (b[0].toInt() and 0xFF) != 0xFF || (b[1].toInt() and 0xFF) != 0xD8) return 1
        var i = 2
        while (i + 4 < b.size) {
            if ((b[i].toInt() and 0xFF) != 0xFF) return 1
            val marker = b[i + 1].toInt() and 0xFF
            val len = ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
            if (marker == 0xDA) return 1 // start of scan: no more metadata
            if (marker == 0xE1 && i + 10 < b.size && String(b, i + 4, 4, Charsets.ISO_8859_1) == "Exif") {
                val tiff = i + 10
                val le = b[tiff].toInt() == 'I'.code
                fun u16(o: Int) = if (le) (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
                else ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
                fun u32(o: Int) = if (le) u16(o) or (u16(o + 2) shl 16) else (u16(o) shl 16) or u16(o + 2)
                val ifd0 = tiff + u32(tiff + 4)
                val entries = u16(ifd0)
                for (e in 0 until entries) {
                    val entry = ifd0 + 2 + e * 12
                    if (u16(entry) == 0x0112) return u16(entry + 8)
                }
                return 1
            }
            i += 2 + len
        }
        return 1
    }

    private fun applyOrientation(img: BufferedImage, orientation: Int): BufferedImage {
        val w = img.width.toDouble(); val h = img.height.toDouble()
        val t = AffineTransform()
        val (nw, nh) = when (orientation) {
            3 -> { t.translate(w, h); t.rotate(Math.PI); img.width to img.height }
            6 -> { t.translate(h, 0.0); t.rotate(Math.PI / 2); img.height to img.width }
            8 -> { t.translate(0.0, w); t.rotate(-Math.PI / 2); img.height to img.width }
            else -> return img // 1 = normal; mirrored variants (2,4,5,7) are rare for phone cameras
        }
        val out = BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB)
        AffineTransformOp(t, AffineTransformOp.TYPE_NEAREST_NEIGHBOR).filter(toRgb(img), out)
        return out
    }

    private fun toRgb(img: BufferedImage): BufferedImage {
        if (img.type == BufferedImage.TYPE_INT_RGB) return img
        val out = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply { drawImage(img, 0, 0, null); dispose() }
        return out
    }
}
