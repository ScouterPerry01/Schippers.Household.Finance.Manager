package ca.schippers.hfm.ai

import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * AI-04: a page as it will be sent, after the user's crop and blurred areas (such as a full
 * account number). Blurring replaces the pixels before the picture is encoded, so what is hidden
 * never leaves the computer. Rectangles are in the page's own pixels.
 */
data class PageEdit(val crop: Rectangle? = null, val blur: List<Rectangle> = emptyList())

object AiImages {

    /** Long side sent; the service reduces larger pictures anyway. */
    const val MAX_SIDE = 2000

    /** The page as shown in the preview and as sent: cropped, blurred, reduced. */
    fun apply(page: BufferedImage, edit: PageEdit): BufferedImage {
        val rgb = BufferedImage(page.width, page.height, BufferedImage.TYPE_INT_RGB).also { out ->
            out.createGraphics().apply { drawImage(page, 0, 0, null); dispose() }
        }
        for (area in edit.blur) hide(rgb, area.intersection(Rectangle(0, 0, rgb.width, rgb.height)))
        val cropped = edit.crop?.intersection(Rectangle(0, 0, rgb.width, rgb.height))?.takeIf { it.width > 0 && it.height > 0 }
            ?.let { rgb.getSubimage(it.x, it.y, it.width, it.height) } ?: rgb
        return reduce(cropped)
    }

    /** The JPEG bytes sent to the provider. */
    fun jpeg(image: BufferedImage, quality: Float = 0.88f): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            val param = writer.defaultWriteParam.apply { compressionMode = ImageWriteParam.MODE_EXPLICIT; compressionQuality = quality }
            writer.write(null, IIOImage(image, null, null), param)
            writer.dispose()
        }
        return out.toByteArray()
    }

    /**
     * Covers [area] with blocks of their average colour, each at least half the area's shorter
     * side, so no character can be recovered by sharpening.
     */
    private fun hide(img: BufferedImage, area: Rectangle) {
        if (area.width <= 0 || area.height <= 0) return
        val block = maxOf(12, minOf(area.width, area.height) / 2)
        var y = area.y
        while (y < area.y + area.height) {
            var x = area.x
            val h = minOf(block, area.y + area.height - y)
            while (x < area.x + area.width) {
                val w = minOf(block, area.x + area.width - x)
                var r = 0L; var g = 0L; var b = 0L
                for (yy in y until y + h) for (xx in x until x + w) {
                    val p = img.getRGB(xx, yy); r += p shr 16 and 0xFF; g += p shr 8 and 0xFF; b += p and 0xFF
                }
                val n = (w * h).toLong()
                val avg = (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
                for (yy in y until y + h) for (xx in x until x + w) img.setRGB(xx, yy, avg)
                x += block
            }
            y += block
        }
    }

    private fun reduce(img: BufferedImage): BufferedImage {
        val long = maxOf(img.width, img.height)
        if (long <= MAX_SIDE) return img
        val f = MAX_SIDE.toDouble() / long
        return BufferedImage((img.width * f).toInt().coerceAtLeast(1), (img.height * f).toInt().coerceAtLeast(1), BufferedImage.TYPE_INT_RGB).also { out ->
            out.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                drawImage(img, 0, 0, out.width, out.height, null)
                dispose()
            }
        }
    }
}
