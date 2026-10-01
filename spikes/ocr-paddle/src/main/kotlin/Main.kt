package spike.ocr

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension

/**
 * Spike runner.
 *  run                -> regenerate synthetic samples, OCR every image in samples/ with each rec model
 *  run --args="path"  -> OCR a single image file with the latin model
 */
fun main(args: Array<String>) {
    val models = Paths.get("models")
    val samples = Paths.get("samples")
    val outDir = Paths.get("out").also { Files.createDirectories(it) }
    require(Files.exists(models.resolve("det_v5_mobile.onnx"))) { "Models missing - run ./download-models.sh first" }

    val recVariants = listOf(
        "latin" to ("rec_latin_v5_mobile.onnx" to "ppocrv5_latin_dict.txt"),
        "en" to ("rec_en_v5_mobile.onnx" to "ppocrv5_en_dict.txt"),
    )

    val images: List<Path> = if (args.isNotEmpty()) listOf(Paths.get(args[0])) else {
        SyntheticReceipts.generate(samples)
        samples.listDirectoryEntries().filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png") }.sorted()
    }
    println("JVM ${System.getProperty("java.version")}, ${Runtime.getRuntime().availableProcessors()} cores, ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")

    val summary = StringBuilder()
    for ((variant, files) in (if (args.isNotEmpty()) recVariants.take(1) else recVariants)) {
        println("\n=== rec model: $variant ===")
        val t0 = System.nanoTime()
        val detMaxSide = System.getProperty("det.maxSide", "1600").toInt()
        val ocr = PaddleOcr(models.resolve("det_v5_mobile.onnx"), models.resolve(files.first), models.resolve(files.second),
            detMaxSide = detMaxSide,
            threads = System.getProperty("ort.threads", "4").toInt(),
            recBatch = System.getProperty("rec.batch", "6").toInt(),
            recMinWidth = System.getProperty("rec.minWidth", "64").toInt(),
        )
        val loadMs = (System.nanoTime() - t0) / 1_000_000
        println("model load (env + det + rec sessions): $loadMs ms")
        summary.append("[$variant] load=${loadMs}ms\n")

        ocr.use {
            for (img in images) {
                val tRead = System.nanoTime()
                val image = ImageLoader.load(img) ?: run { println("cannot decode $img (HEIC/WebP unsupported by ImageIO)"); null } ?: continue
                val readMs = (System.nanoTime() - tRead) / 1_000_000

                // cold run, then 3 warm runs
                val cold = OcrTimings()
                var t = System.nanoTime()
                val lines = ocr.recognize(image, cold)
                val coldMs = (System.nanoTime() - t) / 1_000_000
                val warm = OcrTimings()
                t = System.nanoTime()
                repeat(3) { ocr.recognize(image, warm) }
                val warmMs = (System.nanoTime() - t) / 1_000_000 / 3

                val text = PaddleOcr.toText(lines)
                Files.writeString(outDir.resolve("${img.nameWithoutExtension}.$variant.txt"), text, Charsets.UTF_8)
                println("\n--- ${img.fileName} (${image.width}x${image.height}), decode ${readMs}ms, boxes=${cold.boxes}")
                println("cold ${coldMs}ms (det ${cold.detMs} / rec ${cold.recMs}); warm avg ${warmMs}ms (det ${warm.detMs / 3} [onnx ${warm.detInferMs / 3}] / rec ${warm.recMs / 3} [onnx ${warm.recInferMs / 3}])")
                println(text.prependIndent("  | "))
                val minConf = lines.minOfOrNull { it.confidence } ?: 0f
                var line = "[$variant] ${img.fileName}: ${image.width}x${image.height} boxes=${cold.boxes} cold=${coldMs}ms warm=${warmMs}ms (det ${warm.detMs / 3} [onnx ${warm.detInferMs / 3}] rec ${warm.recMs / 3} [onnx ${warm.recInferMs / 3}]) minConf=${"%.2f".format(minConf)}"

                val truthFile = img.resolveSibling("${img.nameWithoutExtension}.txt")
                if (Files.exists(truthFile)) {
                    val truth = Files.readString(truthFile, Charsets.UTF_8)
                    val m = Metrics.compare(truth, text)
                    println("  CER=${pct(m.cer)}  CER(no-space)=${pct(m.cerNoSpace)}  amounts ${m.amountsFound}/${m.amountsTotal}  accented words ${m.accentFound}/${m.accentTotal}")
                    if (m.missedAccent.isNotEmpty()) println("  missed accented words: ${m.missedAccent}")
                    if (m.missedAmounts.isNotEmpty()) println("  missed amounts: ${m.missedAmounts}")
                    line += " CER=${pct(m.cer)} CERnoSpace=${pct(m.cerNoSpace)} amounts=${m.amountsFound}/${m.amountsTotal} accentWords=${m.accentFound}/${m.accentTotal}"
                }
                val expectFile = img.resolveSibling("${img.nameWithoutExtension}.expect.txt")
                if (Files.exists(expectFile)) {
                    val expected = Files.readAllLines(expectFile, Charsets.UTF_8).filter { it.isNotBlank() }
                    val hay = text.filterNot { it.isWhitespace() }
                    val missed = expected.filterNot { hay.contains(it.filterNot { c -> c.isWhitespace() }) }
                    println("  expected snippets found ${expected.size - missed.size}/${expected.size}")
                    if (missed.isNotEmpty()) println("  missed snippets: $missed")
                    line += " snippets=${expected.size - missed.size}/${expected.size}"
                }
                summary.append(line).append('\n')
            }
        }
    }
    println("\n=== SUMMARY ===\n$summary")
    Files.writeString(outDir.resolve("summary.txt"), summary.toString(), Charsets.UTF_8)
}

private fun pct(d: Double) = "%.2f%%".format(d * 100)

object Metrics {
    data class Result(
        val cer: Double, val cerNoSpace: Double,
        val amountsFound: Int, val amountsTotal: Int, val missedAmounts: List<String>,
        val accentFound: Int, val accentTotal: Int, val missedAccent: List<String>,
    )

    private val amountRe = Regex("""\b\d+[.,]\d{2}\b( \$)?""")
    private val accentRe = Regex("""[A-Za-zÀ-ÿŒœ]*[À-ÿŒœ][A-Za-zÀ-ÿŒœ]*""")

    fun normalise(s: String) = s.lines().map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotEmpty() }.joinToString("\n")

    fun compare(truthRaw: String, hypRaw: String): Result {
        val truth = normalise(truthRaw)
        val hyp = normalise(hypRaw)
        val cer = levenshtein(truth, hyp).toDouble() / truth.length
        val t2 = truth.filterNot { it.isWhitespace() }
        val h2 = hyp.filterNot { it.isWhitespace() }
        val cerNs = levenshtein(t2, h2).toDouble() / t2.length
        // amounts: compare with spaces removed so "12,34$" vs "12,34 $" is not penalised
        val amounts = amountRe.findAll(truth).map { it.value.replace(" ", "") }.toList()

        val hypBag = h2
        val missedAmounts = amounts.filterNot { hypBag.contains(it) }
        val accents = accentRe.findAll(truth).map { it.value }.toList()
        val hypWords = hyp.split(Regex("[\\s/,.:!-]+")).toSet()
        val missedAccent = accents.filterNot { it in hypWords }
        return Result(cer, cerNs, amounts.size - missedAmounts.size, amounts.size, missedAmounts,
            accents.size - missedAccent.size, accents.size, missedAccent)
    }

    fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
