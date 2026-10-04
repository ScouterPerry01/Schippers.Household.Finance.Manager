package ca.schippers.hfm.ocr.desktop

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ca.schippers.hfm.ocr.Box
import ca.schippers.hfm.ocr.OcrEngine
import ca.schippers.hfm.ocr.OcrLine
import ca.schippers.hfm.ocr.OcrResult
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.FloatBuffer
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

/**
 * PaddleOCR PP-OCRv5 on ONNX Runtime (OCR-01, OCR-04, ADR 0004): text detection, then recognition
 * with the latin model, which reads English and French. All pre- and post-processing is plain
 * Kotlin. The models load on first use; call [close] when the household is locked.
 *
 * Proven in spikes/ocr-paddle: 0 to 1 % character errors on clean receipts, every amount read on
 * real phone photos, about 0.7 s per receipt.
 */
class PaddleOcrEngine(private val threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_THREADS)) : OcrEngine, AutoCloseable {

    override val id: String = "paddle-ppocrv5-latin"

    private class Models(val env: OrtEnvironment, val opts: OrtSession.SessionOptions, val det: OrtSession, val rec: OrtSession, val charset: List<String>)

    private var models: Models? = null

    @Synchronized
    private fun models(): Models = models ?: run {
        val env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions().apply {
            // More threads made it three times slower in the spike.
            setIntraOpNumThreads(threads)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        // CTC charset: index 0 = blank, then the dictionary, then a trailing space.
        val charset = buildList {
            add("")
            resource("ppocrv5_latin_dict.txt").decodeToString().lines().filter { it.isNotEmpty() }.forEach { add(it.trimEnd('\r')) }
            add(" ")
        }
        Models(env, opts, env.createSession(resource("det_v5_mobile.onnx"), opts), env.createSession(resource("rec_latin_v5_mobile.onnx"), opts), charset)
    }.also { models = it }

    override fun recognize(image: ByteArray): OcrResult {
        val img = ImageLoader.decode(image) ?: throw UnsupportedImageException()
        return recognize(img)
    }

    @Synchronized
    fun recognize(image: BufferedImage): OcrResult {
        val started = System.nanoTime()
        val m = models()
        val rgb = toRgb(image)
        val boxes = detect(m, rgb)
        val px = rgb.getRGB(0, 0, rgb.width, rgb.height, null, 0, rgb.width)
        val crops = boxes.map { cropRotated(px, rgb.width, rgb.height, it) }
        val results = arrayOfNulls<Pair<String, Float>>(crops.size)
        // Crops of similar width are recognised together, as PaddleOCR does.
        for (chunk in crops.indices.sortedBy { crops[it][0].size }.chunked(REC_BATCH)) {
            recognizeBatch(m, chunk.map { crops[it] }).forEachIndexed { k, r -> results[chunk[k]] = r }
        }
        val words = boxes.indices.mapNotNull { i -> results[i]!!.let { (text, conf) -> if (text.isBlank()) null else Word(boxes[i], text, conf) } }
        return OcrResult(rows(words), (System.nanoTime() - started) / 1_000_000)
    }

    @Synchronized
    override fun close() {
        models?.let { it.det.close(); it.rec.close(); it.opts.close() }
        models = null
    }

    // --- Detection --------------------------------------------------------------------------------

    /** Rotated rectangle: centre, size (w >= h), angle in radians of the w axis. */
    private data class Rect(val cx: Double, val cy: Double, val w: Double, val h: Double, val angle: Double)

    private class Word(val box: Rect, val text: String, val confidence: Float)

    private fun detect(m: Models, img: BufferedImage): List<Rect> {
        val scale = min(1.0, DET_MAX_SIDE.toDouble() / max(img.width, img.height))
        val dw = max(32, ((img.width * scale) / 32.0).roundToInt() * 32)
        val dh = max(32, ((img.height * scale) / 32.0).roundToInt() * 32)
        val resized = resize(img, dw, dh)
        // BGR with ImageNet mean and standard deviation, as PaddleOCR feeds it.
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        val plane = dw * dh
        val data = FloatArray(3 * plane)
        val px = resized.getRGB(0, 0, dw, dh, null, 0, dw)
        for (i in 0 until plane) {
            val p = px[i]
            data[i] = ((p and 0xFF) / 255f - mean[0]) / std[0]
            data[plane + i] = (((p shr 8) and 0xFF) / 255f - mean[1]) / std[1]
            data[2 * plane + i] = (((p shr 16) and 0xFF) / 255f - mean[2]) / std[2]
        }
        val prob = OnnxTensor.createTensor(m.env, FloatBuffer.wrap(data), longArrayOf(1, 3, dh.toLong(), dw.toLong())).use { input ->
            m.det.run(mapOf(m.det.inputNames.first() to input)).use { out ->
                val fb = (out[0] as OnnxTensor).floatBuffer
                FloatArray(fb.remaining()).also { fb.get(it) }
            }
        }
        val sx = img.width.toDouble() / dw
        val sy = img.height.toDouble() / dh
        return dbPostProcess(prob, dw, dh).map { r ->
            val c = cos(r.angle)
            val s = sin(r.angle)
            Rect(r.cx * sx, r.cy * sy, hypot(r.w * c * sx, r.w * s * sy), hypot(-r.h * s * sx, r.h * c * sy), atan2(r.w * s * sy, r.w * c * sx))
        }
    }

    /** DB post-processing: threshold, 8-connected regions, minimum-area rectangle, score filter, unclip. */
    private fun dbPostProcess(prob: FloatArray, w: Int, h: Int): List<Rect> {
        val labels = IntArray(w * h)
        val stack = IntArray(w * h)
        val result = ArrayList<Rect>()
        var label = 0
        for (start in 0 until w * h) {
            if (prob[start] <= DET_THRESH || labels[start] != 0) continue
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
                xs.add(x)
                ys.add(y)
                scoreSum += prob[idx]
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val n = ny * w + nx
                    if (labels[n] == 0 && prob[n] > DET_THRESH) {
                        labels[n] = label
                        stack[sp++] = n
                    }
                }
            }
            if (xs.size < 4 || scoreSum / xs.size < BOX_THRESH) continue
            val rect = minAreaRect(xs, ys) ?: continue
            if (min(rect.w, rect.h) < MIN_BOX_SIDE) continue
            val d = rect.w * rect.h * UNCLIP_RATIO / (2 * (rect.w + rect.h))
            val grown = normalise(rect.copy(w = rect.w + 2 * d, h = rect.h + 2 * d))
            if (min(grown.w, grown.h) >= MIN_BOX_SIDE + 2) result.add(grown)
        }
        return result
    }

    private fun minAreaRect(xs: List<Int>, ys: List<Int>): Rect? {
        val pts = HashSet<Long>()
        for (i in xs.indices) {
            val x = xs[i]
            val y = ys[i]
            pts.add(pack(x, y)); pts.add(pack(x + 1, y)); pts.add(pack(x, y + 1)); pts.add(pack(x + 1, y + 1))
        }
        val hull = convexHull(pts.map { doubleArrayOf((it shr 32).toInt().toDouble(), (it and 0xffffffffL).toInt().toDouble()) })
        if (hull.size < 3) return null
        var best: Rect? = null
        var bestArea = Double.MAX_VALUE
        for (i in hull.indices) {
            val a = hull[i]
            val b = hull[(i + 1) % hull.size]
            val ang = atan2(b[1] - a[1], b[0] - a[0])
            val c = cos(ang)
            val s = sin(ang)
            var minU = Double.MAX_VALUE
            var maxU = -Double.MAX_VALUE
            var minV = Double.MAX_VALUE
            var maxV = -Double.MAX_VALUE
            for (p in hull) {
                val u = p[0] * c + p[1] * s
                val v = -p[0] * s + p[1] * c
                minU = min(minU, u); maxU = max(maxU, u); minV = min(minV, v); maxV = max(maxV, v)
            }
            val area = (maxU - minU) * (maxV - minV)
            if (area < bestArea) {
                bestArea = area
                val cu = (minU + maxU) / 2
                val cv = (minV + maxV) / 2
                best = Rect(cu * c - cv * s, cu * s + cv * c, maxU - minU, maxV - minV, ang)
            }
        }
        return best?.let(::normalise)
    }

    /** The long side becomes w, with the angle in (-pi/2, pi/2], so text reads left to right. */
    private fun normalise(r: Rect): Rect {
        var w = r.w
        var h = r.h
        var a = r.angle
        if (h > w) { val t = w; w = h; h = t; a += Math.PI / 2 }
        while (a > Math.PI / 2) a -= Math.PI
        while (a <= -Math.PI / 2) a += Math.PI
        return Rect(r.cx, r.cy, w, h, a)
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
        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)
        return lower + upper
    }

    // --- Recognition ------------------------------------------------------------------------------

    /** Samples the rotated rectangle into an upright crop [REC_H] pixels high (bilinear). */
    private fun cropRotated(px: IntArray, iw: Int, ih: Int, r: Rect): Array<FloatArray> {
        val outW = max(8, ceil(REC_H * r.w / r.h).toInt()).coerceAtMost(REC_H * 40)
        val c = cos(r.angle)
        val s = sin(r.angle)
        val su = r.w / outW
        val sv = r.h / REC_H
        val chans = Array(3) { FloatArray(outW * REC_H) }
        for (y in 0 until REC_H) for (x in 0 until outW) {
            val u = (x + 0.5) * su - r.w / 2
            val v = (y + 0.5) * sv - r.h / 2
            val fx = r.cx + u * c - v * s - 0.5
            val fy = r.cy + u * s + v * c - 0.5
            val x0 = floor(fx).toInt()
            val y0 = floor(fy).toInt()
            val ax = fx - x0
            val ay = fy - y0
            for (ch in 0 until 3) {
                fun at(xx: Int, yy: Int): Double = ((px[yy.coerceIn(0, ih - 1) * iw + xx.coerceIn(0, iw - 1)] shr (8 * ch)) and 0xFF).toDouble()
                val value = (at(x0, y0) * (1 - ax) + at(x0 + 1, y0) * ax) * (1 - ay) + (at(x0, y0 + 1) * (1 - ax) + at(x0 + 1, y0 + 1) * ax) * ay
                chans[ch][y * outW + x] = ((value / 255.0 - 0.5) / 0.5).toFloat()
            }
        }
        return chans
    }

    private fun recognizeBatch(m: Models, batch: List<Array<FloatArray>>): List<Pair<String, Float>> {
        val padW = max(REC_MIN_WIDTH, batch.maxOf { it[0].size / REC_H })
        val n = batch.size
        val data = FloatArray(n * 3 * REC_H * padW)
        batch.forEachIndexed { b, chans ->
            val w = chans[0].size / REC_H
            for (ch in 0 until 3) for (y in 0 until REC_H) System.arraycopy(chans[ch], y * w, data, ((b * 3 + ch) * REC_H + y) * padW, w)
        }
        return OnnxTensor.createTensor(m.env, FloatBuffer.wrap(data), longArrayOf(n.toLong(), 3, REC_H.toLong(), padW.toLong())).use { input ->
            m.rec.run(mapOf(m.rec.inputNames.first() to input)).use { out ->
                val t = out[0] as OnnxTensor
                val steps = t.info.shape[1].toInt()
                val classes = t.info.shape[2].toInt()
                val fb = t.floatBuffer
                val probs = FloatArray(fb.remaining()).also { fb.get(it) }
                (0 until n).map { b -> ctcGreedy(m.charset, probs, b * steps * classes, steps, classes) }
            }
        }
    }

    private fun ctcGreedy(charset: List<String>, p: FloatArray, off: Int, steps: Int, classes: Int): Pair<String, Float> {
        val sb = StringBuilder()
        var prev = -1
        var confSum = 0.0
        var n = 0
        for (t in 0 until steps) {
            val base = off + t * classes
            var best = 0
            var bestP = p[base]
            for (c in 1 until classes) if (p[base + c] > bestP) { bestP = p[base + c]; best = c }
            if (best != 0 && best != prev) {
                sb.append(charset.getOrElse(best) { "?" })
                confSum += bestP
                n++
            }
            prev = best
        }
        return sb.toString().trim() to (if (n == 0) 0f else (confSum / n).toFloat())
    }

    // --- Rows -------------------------------------------------------------------------------------

    /**
     * Groups words into visual rows along the dominant text direction, so a tilted receipt still
     * gives "ITEM ..... 12,34" on one line, and joins each row left to right.
     */
    private fun rows(words: List<Word>): List<OcrLine> {
        if (words.isEmpty()) return emptyList()
        val a = words.map { it.box.angle }.sorted()[words.size / 2]
        fun v(word: Word) = -word.box.cx * sin(a) + word.box.cy * cos(a)
        fun u(word: Word) = word.box.cx * cos(a) + word.box.cy * sin(a)
        val rows = ArrayList<MutableList<Word>>()
        for (word in words.sortedBy(::v)) {
            val row = rows.lastOrNull()
            if (row != null && abs(row.map(::v).average() - v(word)) < 0.5 * min(row.first().box.h, word.box.h)) row.add(word) else rows.add(mutableListOf(word))
        }
        return rows.map { row ->
            val sorted = row.sortedBy(::u)
            OcrLine(
                sorted.joinToString(" ") { it.text },
                sorted.minOf { it.confidence },
                Box(
                    row.minOf { (it.box.cx - it.box.w / 2).toInt() }, row.minOf { (it.box.cy - it.box.h / 2).toInt() },
                    row.maxOf { (it.box.cx + it.box.w / 2).toInt() }, row.maxOf { (it.box.cy + it.box.h / 2).toInt() },
                ),
            )
        }
    }

    private fun toRgb(img: BufferedImage): BufferedImage {
        if (img.type == BufferedImage.TYPE_INT_RGB) return img
        return BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB).also { out -> out.createGraphics().apply { drawImage(img, 0, 0, null); dispose() } }
    }

    private fun resize(img: BufferedImage, w: Int, h: Int): BufferedImage = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).also { out ->
        out.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(img, 0, 0, w, h, null)
            dispose()
        }
    }

    private fun resource(name: String): ByteArray =
        javaClass.getResourceAsStream("/hfm/ocr/models/$name")?.use { it.readBytes() } ?: error("OCR model $name is missing")

    companion object {
        const val MAX_THREADS = 4
        private const val DET_MAX_SIDE = 1600
        private const val DET_THRESH = 0.3f
        private const val BOX_THRESH = 0.5f
        private const val UNCLIP_RATIO = 1.6
        private const val MIN_BOX_SIDE = 3.0
        private const val REC_H = 48
        private const val REC_BATCH = 6
        private const val REC_MIN_WIDTH = 320
    }
}

/** The file is not an image the desktop can read (WebP must be converted first). */
class UnsupportedImageException : Exception("Unsupported image format")
