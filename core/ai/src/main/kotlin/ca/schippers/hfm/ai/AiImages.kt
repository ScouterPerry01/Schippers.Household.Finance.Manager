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
     * Covers [area] with one flat grey, the colour the preview shows. Nothing of what was under it
     * is kept, not even an average: blocks of average colour can give back text whose font is known
     * (Phase 5 security review).
     */
    private fun hide(img: BufferedImage, area: Rectangle) {
        if (area.width <= 0 || area.height <= 0) return
        img.createGraphics().apply {
            color = java.awt.Color(HIDDEN)
            fillRect(area.x, area.y, area.width, area.height)
            dispose()
        }
    }

    /** The grey of a hidden area, in the preview and in the picture sent. */
    const val HIDDEN = 0x6B6B6B

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
