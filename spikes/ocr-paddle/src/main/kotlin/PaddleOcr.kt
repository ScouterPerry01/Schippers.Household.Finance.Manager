package spike.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** One recognised text line: rotated box in source-image coordinates, text, and mean CTC confidence. */
data class OcrLine(val box: RotatedRect, val text: String, val confidence: Float)

data class OcrTimings(var detMs: Long = 0, var detInferMs: Long = 0, var recMs: Long = 0, var recInferMs: Long = 0, var boxes: Int = 0)

/** Rotated rectangle: centre, size (w >= h after normalisation), angle in radians of the "w" axis. */
data class RotatedRect(val cx: Double, val cy: Double, val w: Double, val h: Double, val angle: Double)

class PaddleOcr(
    detModel: Path,
    recModel: Path,
    dictFile: Path,
    private val detMaxSide: Int = 1600,
    private val detThresh: Float = 0.3f,
    private val boxThresh: Float = 0.5f,
    private val unclipRatio: Double = 1.6,
    private val minBoxSide: Double = 3.0,
    private val recBatch: Int = 6,
    private val recMinWidth: Int = 320,
    threads: Int = Runtime.getRuntime().availableProcessors().coerceAtMost(8),
) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val opts = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(threads)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }
    private val det: OrtSession = env.createSession(detModel.toString(), opts)
    private val rec: OrtSession = env.createSession(recModel.toString(), opts)

    /** CTC charset: index 0 = blank, then dictionary lines, then a trailing space (use_space_char=True). */
    private val charset: List<String> = buildList {
        add("")
        Files.readAllLines(dictFile, Charsets.UTF_8).forEach { add(it.trimEnd('\r', '\n')) }
        add(" ")
    }

    init {
        val outInfo = rec.outputInfo.values.first().info.toString()
        println("  rec output: $outInfo, charset size ${charset.size}")
    }

    fun recognize(image: BufferedImage, timings: OcrTimings = OcrTimings()): List<OcrLine> {
        val rgb = toRgb(image)
        var t = System.nanoTime()
        val boxes = detect(rgb, timings)
        timings.detMs += (System.nanoTime() - t) / 1_000_000
        timings.boxes += boxes.size
        t = System.nanoTime()
        val px = rgb.getRGB(0, 0, rgb.width, rgb.height, null, 0, rgb.width)
        val crops = boxes.map { cropRotated(px, rgb.width, rgb.height, it) }
        val results = arrayOfNulls<Pair<String, Float>>(crops.size)
        // batch crops of similar width together (like Paddle's rec_batch_num=6) to amortise per-call overhead
        val order = crops.indices.sortedBy { crops[it][0].size }
        for (chunk in order.chunked(recBatch)) {
            val ti = System.nanoTime()
            val decoded = recognizeBatch(chunk.map { crops[it] })
            timings.recInferMs += (System.nanoTime() - ti) / 1_000_000
            chunk.forEachIndexed { k, idx -> results[idx] = decoded[k] }
        }
        val lines = boxes.indices.mapNotNull { i ->
            val (text, conf) = results[i]!!
            if (text.isBlank()) null else OcrLine(boxes[i], text, conf)
        }
        timings.recMs += (System.nanoTime() - t) / 1_000_000
        return lines
    }

    // ---------------------------------------------------------------- detection

    private fun detect(img: BufferedImage, timings: OcrTimings): List<RotatedRect> {
        val srcW = img.width
        val srcH = img.height
        val scale = min(1.0, detMaxSide.toDouble() / max(srcW, srcH))
        val dw = max(32, ((srcW * scale) / 32.0).roundToInt() * 32)
        val dh = max(32, ((srcH * scale) / 32.0).roundToInt() * 32)
        val resized = resize(img, dw, dh)

        // PaddleOCR feeds BGR with ImageNet mean/std (values listed in the config are applied in BGR order).
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        val data = FloatArray(3 * dw * dh)
        val plane = dw * dh
        val px = resized.getRGB(0, 0, dw, dh, null, 0, dw)
        for (i in 0 until plane) {
            val p = px[i]
            val b = (p and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val r = ((p shr 16) and 0xFF) / 255f
            data[i] = (b - mean[0]) / std[0]
            data[plane + i] = (g - mean[1]) / std[1]
            data[2 * plane + i] = (r - mean[2]) / std[2]
        }
        val prob: FloatArray
        val ti = System.nanoTime()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, dh.toLong(), dw.toLong())).use { input ->
            det.run(mapOf(det.inputNames.first() to input)).use { out ->
                val fb = (out[0] as OnnxTensor).floatBuffer
                prob = FloatArray(fb.remaining()).also { fb.get(it) }
            }
        }
        timings.detInferMs += (System.nanoTime() - ti) / 1_000_000

        val sx = srcW.toDouble() / dw
        val sy = srcH.toDouble() / dh
        return dbPostProcess(prob, dw, dh).map { r ->
            // map to source coordinates (non-uniform scale is tiny; treat axes separately)
            val cosA = cos(r.angle); val sinA = sin(r.angle)
            val w = hypot(r.w * cosA * sx, r.w * sinA * sy)
            val h = hypot(-r.h * sinA * sx, r.h * cosA * sy)
            RotatedRect(r.cx * sx, r.cy * sy, w, h, atan2(r.w * sinA * sy, r.w * cosA * sx))
        }.sortedWith(compareBy({ it.cy }, { it.cx }))
    }

    /** DB post-processing: binarise, 8-connected components, min-area rect, score filter, unclip. */
    private fun dbPostProcess(prob: FloatArray, w: Int, h: Int): List<RotatedRect> {
        val labels = IntArray(w * h)
        val result = ArrayList<RotatedRect>()
        val stack = IntArray(w * h)
        var label = 0
        for (start in 0 until w * h) {
            if (prob[start] <= detThresh || labels[start] != 0) continue
            label++
            var sp = 0
            stack[sp++] = start
            labels[start] = label
            val xs = ArrayList<Int>()
            val ys = ArrayList<Int>()
            var scoreSum = 0.0
            while (sp > 0) {
                val idx = stack[--sp]
                val x = idx % w
                val y = idx / w
                xs.add(x); ys.add(y)
                scoreSum += prob[idx]
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val n = ny * w + nx
                    if (labels[n] == 0 && prob[n] > detThresh) {
                        labels[n] = label
                        stack[sp++] = n
                    }
                }
            }
            if (xs.size < 4) continue
            val score = scoreSum / xs.size
            if (score < boxThresh) continue
            val rect = minAreaRect(xs, ys) ?: continue
            if (min(rect.w, rect.h) < minBoxSide) continue
            // unclip: offset polygon by D = A * r / L (Vatti clipper in PaddleOCR; for a rectangle this is exact enough)
            val area = rect.w * rect.h
            val perim = 2 * (rect.w + rect.h)
            val d = area * unclipRatio / perim
            val grown = normalise(rect.copy(w = rect.w + 2 * d, h = rect.h + 2 * d))
            if (min(grown.w, grown.h) < minBoxSide + 2) continue
            result.add(grown)
        }
        return result
    }

    /** Minimum-area enclosing rectangle via convex hull + rotating edges. Pixel centres are expanded by 0.5. */
    private fun minAreaRect(xs: List<Int>, ys: List<Int>): RotatedRect? {
        val pts = HashSet<Long>()
        for (i in xs.indices) {
            val x = xs[i]; val y = ys[i]
            // pixel corners
            pts.add(pack(x, y)); pts.add(pack(x + 1, y)); pts.add(pack(x, y + 1)); pts.add(pack(x + 1, y + 1))
        }
        val hull = convexHull(pts.map { doubleArrayOf((it shr 32).toInt().toDouble(), (it and 0xffffffffL).toInt().toDouble()) })
        if (hull.size < 3) return null
        var best: RotatedRect? = null
        var bestArea = Double.MAX_VALUE
        for (i in hull.indices) {
            val a = hull[i]; val b = hull[(i + 1) % hull.size]
            val ang = atan2(b[1] - a[1], b[0] - a[0])
            val c = cos(ang); val s = sin(ang)
            var minU = Double.MAX_VALUE; var maxU = -Double.MAX_VALUE
            var minV = Double.MAX_VALUE; var maxV = -Double.MAX_VALUE
            for (p in hull) {
                val u = p[0] * c + p[1] * s
                val v = -p[0] * s + p[1] * c
                minU = min(minU, u); maxU = max(maxU, u); minV = min(minV, v); maxV = max(maxV, v)
            }
            val area = (maxU - minU) * (maxV - minV)
            if (area < bestArea) {
                bestArea = area
                val cu = (minU + maxU) / 2; val cv = (minV + maxV) / 2
                best = RotatedRect(cu * c - cv * s, cu * s + cv * c, maxU - minU, maxV - minV, ang)
            }
        }
        return best?.let { normalise(it) }
    }

    /** Make w the long side and keep the angle in (-pi/2, pi/2] so text reads left-to-right. */
    private fun normalise(r: RotatedRect): RotatedRect {
        var w = r.w; var h = r.h; var a = r.angle
        if (h > w) { val t = w; w = h; h = t; a += Math.PI / 2 }
        while (a > Math.PI / 2) a -= Math.PI
        while (a <= -Math.PI / 2) a += Math.PI
        return RotatedRect(r.cx, r.cy, w, h, a)
    }

    private fun pack(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)

    private fun convexHull(points: List<DoubleArray>): List<DoubleArray> {
        val p = points.sortedWith(compareBy({ it[0] }, { it[1] }))
        if (p.size < 3) return p
        fun cross(o: DoubleArray, a: DoubleArray, b: DoubleArray) = (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
        val lower = ArrayList<DoubleArray>()
        for (pt in p) { while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), pt) <= 0) lower.removeAt(lower.size - 1); lower.add(pt) }
        val upper = ArrayList<DoubleArray>()
        for (pt in p.asReversed()) { while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), pt) <= 0) upper.removeAt(upper.size - 1); upper.add(pt) }
        lower.removeAt(lower.size - 1); upper.removeAt(upper.size - 1)
        return lower + upper
    }

    // ---------------------------------------------------------------- recognition

    private val recH = 48

    /** Sample the rotated rect into an upright crop of height [recH] (bilinear), preserving aspect ratio. */
    private fun cropRotated(px: IntArray, iw: Int, ih: Int, r: RotatedRect): Array<FloatArray> {
        val outW = max(8, ceil(recH * r.w / r.h).toInt()).coerceAtMost(recH * 40)
        val c = cos(r.angle); val s = sin(r.angle)
        val su = r.w / outW; val sv = r.h / recH
        val chans = Array(3) { FloatArray(outW * recH) }
        for (y in 0 until recH) for (x in 0 until outW) {
            val u = (x + 0.5) * su - r.w / 2
            val v = (y + 0.5) * sv - r.h / 2
            val fx = r.cx + u * c - v * s - 0.5
            val fy = r.cy + u * s + v * c - 0.5
            val x0 = floor(fx).toInt(); val y0 = floor(fy).toInt()
            val ax = fx - x0; val ay = fy - y0
            for (ch in 0 until 3) {
                fun at(xx: Int, yy: Int): Double {
                    val cx = xx.coerceIn(0, iw - 1); val cy = yy.coerceIn(0, ih - 1)
                    // channel order B,G,R (Paddle/OpenCV convention)
                    return ((px[cy * iw + cx] shr (8 * ch)) and 0xFF).toDouble()
                }
                val v00 = at(x0, y0); val v10 = at(x0 + 1, y0); val v01 = at(x0, y0 + 1); val v11 = at(x0 + 1, y0 + 1)
                val value = (v00 * (1 - ax) + v10 * ax) * (1 - ay) + (v01 * (1 - ax) + v11 * ax) * ay
                chans[ch][y * outW + x] = ((value / 255.0 - 0.5) / 0.5).toFloat()
            }
        }
        return chans
    }

    private fun recognizeBatch(batch: List<Array<FloatArray>>): List<Pair<String, Float>> {
        // Paddle rec_image_shape 3x48x320; pad with 0 (= mid-grey after normalisation) on the right
        val padW = max(recMinWidth, batch.maxOf { it[0].size / recH })
        val n = batch.size
        val data = FloatArray(n * 3 * recH * padW)
        batch.forEachIndexed { b, chans ->
            val w = chans[0].size / recH
            for (ch in 0 until 3) for (y in 0 until recH) {
                System.arraycopy(chans[ch], y * w, data, ((b * 3 + ch) * recH + y) * padW, w)
            }
        }
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(n.toLong(), 3, recH.toLong(), padW.toLong())).use { input ->
            rec.run(mapOf(rec.inputNames.first() to input)).use { out ->
                val t = out[0] as OnnxTensor
                val shape = t.info.shape // [N, T, C]
                val steps = shape[1].toInt(); val classes = shape[2].toInt()
                val fb = t.floatBuffer
                val probs = FloatArray(fb.remaining()).also { fb.get(it) }
                return (0 until n).map { b -> ctcGreedy(probs, b * steps * classes, steps, classes) }
            }
        }
    }

    private fun ctcGreedy(p: FloatArray, off: Int, steps: Int, classes: Int): Pair<String, Float> {
        val sb = StringBuilder()
        var prev = -1
        var confSum = 0.0; var n = 0
        for (t in 0 until steps) {
            val base = off + t * classes
            var best = 0; var bestP = p[base]
            for (c in 1 until classes) { val v = p[base + c]; if (v > bestP) { bestP = v; best = c } }
            if (best != 0 && best != prev) {
                sb.append(charset.getOrElse(best) { "?" })
                confSum += bestP; n++
            }
            prev = best
        }
        return sb.toString().trim() to (if (n == 0) 0f else (confSum / n).toFloat())
    }

    // ---------------------------------------------------------------- utils

    private fun toRgb(img: BufferedImage): BufferedImage {
        if (img.type == BufferedImage.TYPE_INT_RGB) return img
        val out = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply { drawImage(img, 0, 0, null); dispose() }
        return out
    }

    private fun resize(img: BufferedImage, w: Int, h: Int): BufferedImage {
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(img, 0, 0, w, h, null)
        g.dispose()
        return out
    }

    override fun close() {
        det.close(); rec.close(); opts.close()
    }

    companion object {
        /** Group lines into visual rows (y-overlap) and join left-to-right; rows separated by '\n'. */
        fun toText(lines: List<OcrLine>): String {
            if (lines.isEmpty()) return ""
            // project centres onto the axis perpendicular to the dominant text direction, so a tilted
            // receipt still groups "item ...... 12,34 $" into one row
            val a = lines.map { it.box.angle }.sorted()[lines.size / 2]
            fun v(l: OcrLine) = -l.box.cx * sin(a) + l.box.cy * cos(a)
            fun u(l: OcrLine) = l.box.cx * cos(a) + l.box.cy * sin(a)
            val rows = ArrayList<MutableList<OcrLine>>()
            for (l in lines.sortedBy { v(it) }) {
                val row = rows.lastOrNull()
                if (row != null && abs(row.map { v(it) }.average() - v(l)) < 0.5 * min(row.first().box.h, l.box.h)) row.add(l)
                else rows.add(mutableListOf(l))
            }
            return rows.joinToString("\n") { r -> r.sortedBy { u(it) }.joinToString(" ") { it.text } }
        }
    }
}
