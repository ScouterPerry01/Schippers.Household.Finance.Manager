package ca.schippers.hfm.ocr.desktop

import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.imageio.ImageReadParam

/**
 * Decodes JPEG, PNG, BMP and GIF images (CAP-03). Phone photos are turned upright using their EXIF
 * orientation, which ImageIO ignores, and very large photos are reduced while decoding so a 50
 * megapixel picture does not need a gigabyte of memory. HEIC and WebP are not supported.
 */
object ImageLoader {

    /** Long side kept when decoding; recognition works on at most 1600 pixels anyway. */
    private const val MAX_SIDE = 3200

    fun decode(bytes: ByteArray): BufferedImage? {
        val img = read(bytes) ?: return null
        return applyOrientation(img, exifOrientation(bytes))
    }

    fun isImage(bytes: ByteArray): Boolean = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
        input != null && ImageIO.getImageReaders(input).hasNext()
    }

    private fun read(bytes: ByteArray): BufferedImage? = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
        val reader = input?.let { ImageIO.getImageReaders(it) }?.takeIf { it.hasNext() }?.next() ?: return null
        try {
            reader.input = input
            val longSide = maxOf(reader.getWidth(0), reader.getHeight(0))
            val param: ImageReadParam = reader.defaultReadParam
            val step = (longSide + MAX_SIDE - 1) / MAX_SIDE
            if (step > 1) param.setSourceSubsampling(step, step, 0, 0)
            reader.read(0, param)
        } finally {
            reader.dispose()
        }
    }

    /** Reads the EXIF orientation tag (0x0112) of a JPEG; 1 (upright) when there is none. */
    internal fun exifOrientation(b: ByteArray): Int {
        if (b.size < 4 || (b[0].toInt() and 0xFF) != 0xFF || (b[1].toInt() and 0xFF) != 0xD8) return 1
        var i = 2
        while (i + 4 < b.size) {
            if ((b[i].toInt() and 0xFF) != 0xFF) return 1
            val marker = b[i + 1].toInt() and 0xFF
            val len = ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
            if (marker == 0xDA) return 1
            if (marker == 0xE1 && i + 10 < b.size && String(b, i + 4, 4, Charsets.ISO_8859_1) == "Exif") {
                val tiff = i + 10
                val le = b[tiff].toInt() == 'I'.code
                fun u16(o: Int) = if (le) (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) else ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
                fun u32(o: Int) = if (le) u16(o) or (u16(o + 2) shl 16) else (u16(o) shl 16) or u16(o + 2)
                val ifd0 = tiff + u32(tiff + 4)
                if (ifd0 + 2 > b.size) return 1
                for (e in 0 until u16(ifd0)) {
                    val entry = ifd0 + 2 + e * 12
                    if (entry + 10 > b.size) return 1
                    if (u16(entry) == 0x0112) return u16(entry + 8)
                }
                return 1
            }
            i += 2 + len
        }
        return 1
    }

    private fun applyOrientation(img: BufferedImage, orientation: Int): BufferedImage {
        val w = img.width.toDouble()
        val h = img.height.toDouble()
        val t = AffineTransform()
        val (nw, nh) = when (orientation) {
            3 -> { t.translate(w, h); t.rotate(Math.PI); img.width to img.height }
            6 -> { t.translate(h, 0.0); t.rotate(Math.PI / 2); img.height to img.width }
            8 -> { t.translate(0.0, w); t.rotate(-Math.PI / 2); img.height to img.width }
            else -> return img
        }
        val rgb = if (img.type == BufferedImage.TYPE_INT_RGB) img else BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB).also { o ->
            o.createGraphics().apply { drawImage(img, 0, 0, null); dispose() }
        }
        return BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB).also { AffineTransformOp(t, AffineTransformOp.TYPE_BILINEAR).filter(rgb, it) }
    }
}
