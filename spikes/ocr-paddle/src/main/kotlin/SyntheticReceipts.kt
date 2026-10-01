package spike.ocr

import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.random.Random

/** A receipt row: left text and optional right-aligned amount. Reference text = "left right". */
private data class Row(val left: String, val right: String = "", val center: Boolean = false, val bold: Boolean = false)

private data class Spec(
    val name: String,
    val font: String,
    val fontSize: Int,
    val rotationDeg: Double,
    val noise: Double,
    val blur: Boolean,
    val jpegQuality: Float,
    val scale: Double,
    val rows: List<Row>,
)

/** Renders receipt-like test images with known ground truth (<name>.jpg + <name>.txt). */
object SyntheticReceipts {

    private val specs = listOf(
        Spec(
            "synthetic-en-grocery", "Consolas", 26, 2.0, 10.0, false, 0.85f, 1.0,
            listOf(
                Row("FRESHMART GROCERY", center = true, bold = true),
                Row("1450 Bank Street, Ottawa ON", center = true),
                Row("Tel: (613) 555-0142", center = true),
                Row("2026-09-28 14:32   Reg 03", center = true),
                Row(""),
                Row("Bananas 1.21 kg", "2,40 $"),
                Row("Whole Milk 2L", "5,49 $"),
                Row("Sourdough Bread", "4,99 $"),
                Row("Cheddar Cheese 400g", "8,79 $"),
                Row("Free Range Eggs 12", "6,29 $"),
                Row("Coffee Beans 340g", "12,34 $"),
                Row(""),
                Row("SUBTOTAL", "40,30 $"),
                Row("GST/TPS 5%", "0,62 $"),
                Row("HST 13%", "1,62 $"),
                Row("TOTAL", "42,54 $", bold = true),
                Row("VISA **** 4821", "42,54 $"),
                Row(""),
                Row("Thank you for shopping!", center = true),
            ),
        ),
        Spec(
            "synthetic-fr-epicerie", "Courier New", 28, -3.0, 14.0, true, 0.75f, 1.0,
            listOf(
                Row("ÉPICERIE MÉTRO PLUS", center = true, bold = true),
                Row("4825 rue Saint-Denis, Montréal", center = true),
                Row("Reçu no 000184 - Caisse 2", center = true),
                Row("Date : 28/09/2026 à 10:17", center = true),
                Row(""),
                Row("Pâtes fraîches", "4,99 $"),
                Row("Crème glacée érable", "7,49 $"),
                Row("Bœuf haché mi-maigre", "12,34 $"),
                Row("Pommes Cortland", "3,87 $"),
                Row("Café torréfié foncé", "11,99 $"),
                Row("Fromage à pâte ferme", "9,25 $"),
                Row(""),
                Row("Sous-total", "49,93 $"),
                Row("TPS 5 %", "2,50 $"),
                Row("TVQ 9,975 %", "4,98 $"),
                Row("TOTAL", "57,41 $", bold = true),
                Row("Payé par Interac", "57,41 $"),
                Row(""),
                Row("Merci de votre visite !", center = true),
                Row("Économisez avec Moi", center = true),
            ),
        ),
        Spec(
            "synthetic-bilingual-small", "Arial", 20, 5.0, 18.0, true, 0.6f, 0.8,
            listOf(
                Row("PHARMACIE DU QUARTIER / PHARMACY", center = true, bold = true),
                Row("No TPS/GST 812345678 RT0001", center = true),
                Row("No TVQ/QST 1234567890 TQ0001", center = true),
                Row(""),
                Row("Ibuprofène 200 mg x24", "8,97 $"),
                Row("Dentifrice / Toothpaste", "3,49 $"),
                Row("Crème hydratante", "14,99 $"),
                Row("Vitamine D 1000 UI", "10,29 $"),
                Row("Mouchoirs - Tissues", "2,79 $"),
                Row(""),
                Row("Sous-total / Subtotal", "40,53 $"),
                Row("TPS / GST", "1,61 $"),
                Row("TVQ / QST", "3,21 $"),
                Row("TOTAL", "45,35 $", bold = true),
                Row("Remise / Change", "4,65 $"),
                Row("Conservez votre reçu", center = true),
            ),
        ),
    )

    fun generate(dir: Path) {
        Files.createDirectories(dir)
        specs.forEachIndexed { i, s -> render(s, dir, Random(42 + i)) }
    }

    private fun render(s: Spec, dir: Path, rnd: Random) {
        val paperW = (s.fontSize * 22)
        val lineH = (s.fontSize * 1.45).toInt()
        val margin = s.fontSize
        val paperH = margin * 2 + lineH * s.rows.size
        val paper = BufferedImage(paperW, paperH, BufferedImage.TYPE_INT_RGB)
        val g = paper.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(250, 248, 240)
        g.fillRect(0, 0, paperW, paperH)
        val truth = StringBuilder()
        s.rows.forEachIndexed { i, row ->
            val font = Font(s.font, if (row.bold) Font.BOLD else Font.PLAIN, s.fontSize)
            g.font = font
            g.color = Color(30 + rnd.nextInt(20), 30 + rnd.nextInt(20), 40 + rnd.nextInt(20))
            val fm = g.fontMetrics
            val y = margin + i * lineH + fm.ascent
            if (row.center) g.drawString(row.left, (paperW - fm.stringWidth(row.left)) / 2, y)
            else g.drawString(row.left, margin, y)
            if (row.right.isNotEmpty()) g.drawString(row.right, paperW - margin - fm.stringWidth(row.right), y)
            val line = listOf(row.left, row.right).filter { it.isNotEmpty() }.joinToString(" ")
            if (line.isNotEmpty()) truth.append(line).append('\n')
        }
        g.dispose()

        // place on a darker "table" background, rotated, with uneven lighting (a phone photo stand-in)
        val sc = s.scale
        val canvasW = (paperW * sc * 1.35).toInt()
        val canvasH = (paperH * sc * 1.2).toInt()
        val photo = BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_RGB)
        val pg = photo.createGraphics()
        pg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        pg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        pg.color = Color(92, 78, 64)
        pg.fillRect(0, 0, canvasW, canvasH)
        val at = AffineTransform()
        at.translate(canvasW / 2.0, canvasH / 2.0)
        at.rotate(Math.toRadians(s.rotationDeg))
        at.scale(sc, sc)
        at.translate(-paperW / 2.0, -paperH / 2.0)
        pg.drawImage(paper, at, null)
        // lighting gradient (shadow toward one corner)
        pg.paint = GradientPaint(0f, 0f, Color(0, 0, 0, 0), canvasW.toFloat(), canvasH.toFloat(), Color(0, 0, 0, 70))
        pg.fillRect(0, 0, canvasW, canvasH)
        pg.dispose()

        var img = photo
        if (s.blur) {
            val gauss3 = floatArrayOf(0.0625f, 0.125f, 0.0625f, 0.125f, 0.25f, 0.125f, 0.0625f, 0.125f, 0.0625f)
            img = ConvolveOp(Kernel(3, 3, gauss3), ConvolveOp.EDGE_NO_OP, null).filter(img, null)
        }
        // gaussian sensor noise
        for (y in 0 until img.height) for (x in 0 until img.width) {
            val p = img.getRGB(x, y)
            val n = (rnd.nextDouble() + rnd.nextDouble() + rnd.nextDouble() - 1.5) * s.noise * 1.4
            fun c(v: Int) = (v + n).toInt().coerceIn(0, 255)
            img.setRGB(x, y, (c((p shr 16) and 0xFF) shl 16) or (c((p shr 8) and 0xFF) shl 8) or c(p and 0xFF))
        }

        val out = dir.resolve("${s.name}.jpg").toFile()
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val param = writer.defaultWriteParam.apply { compressionMode = ImageWriteParam.MODE_EXPLICIT; compressionQuality = s.jpegQuality }
        ImageIO.createImageOutputStream(out).use { ios ->
            writer.output = ios
            writer.write(null, IIOImage(img, null, null), param)
        }
        writer.dispose()
        Files.writeString(dir.resolve("${s.name}.txt"), truth.toString(), Charsets.UTF_8)
    }
}
