package ca.schippers.hfm.ocr.desktop

import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path

/**
 * HEIC photos, as iPhones and many Android phones save them (CAP-03, ADR 0004). They are decoded by
 * libheif with libde265, linked into one small JNI library per platform (`tools/natives`), kept in
 * the jar like the OCR models; packaging keeps only the target platform's copy. On a platform
 * without one, HEIC files are refused as unreadable, as before.
 */
object Heif {

    /** Larger pictures are refused rather than decoded (about 360 MB while decoding). */
    const val MAX_PIXELS = 120_000_000

    /** HEVC brands of the ISO base media file format; AVIF and other HEIF codecs are not decoded. */
    private val BRANDS = setOf("heic", "heix", "heim", "heis", "hevc", "hevx", "hevm", "hevs")

    /** True when [bytes] start with a HEIF `ftyp` box naming an HEVC brand. */
    fun isHeic(bytes: ByteArray): Boolean {
        if (bytes.size < 16 || String(bytes, 4, 4, Charsets.ISO_8859_1) != "ftyp") return false
        val boxSize = ((bytes[0].toInt() and 0xFF) shl 24) or ((bytes[1].toInt() and 0xFF) shl 16) or ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        val end = minOf(boxSize, bytes.size)
        // The major brand at 8, the minor version at 12, then the compatible brands.
        return (listOf(8) + (16 until end - 3 step 4)).any { String(bytes, it, 4, Charsets.ISO_8859_1) in BRANDS }
    }

    /** Whether this computer can decode HEIC: the native library exists for it and loads. */
    val available: Boolean get() = HeifNative.loaded

    /** The libheif version, for the packaged self-check. */
    fun version(): String {
        check(available) { "HEIC decoder not available" }
        return HeifNative.version()
    }

    /** The picture, upright, at most [maxSide] pixels on its long side; null if it cannot be read. */
    fun decode(bytes: ByteArray, maxSide: Int): BufferedImage? {
        if (!available) return null
        val data = runCatching { HeifNative.decode(bytes, MAX_PIXELS, maxSide) }.getOrNull() ?: return null
        val (w, h) = data[0] to data[1]
        return BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).also { it.setRGB(0, 0, w, h, data, 2, w) }
    }
}

/** The JNI functions of `tools/natives/heif/hfm_heif.c`. */
internal object HeifNative {

    val loaded: Boolean by lazy { load() }

    @JvmStatic external fun version(): String

    /** `[width, height, 0xFFRRGGBB...]`; throws IOException when the file cannot be read. */
    @JvmStatic external fun decode(bytes: ByteArray, maxPixels: Int, maxSide: Int): IntArray

    private fun load(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        val (folder, file) = when {
            os.startsWith("windows") -> "Windows" to "hfmheif.dll"
            os.startsWith("linux") -> "Linux" to "libhfmheif.so"
            else -> return false
        }
        val arch = if (System.getProperty("os.arch") in listOf("aarch64", "arm64")) "aarch64" else "x86_64"
        val bytes = HeifNative::class.java.getResourceAsStream("/hfm/heif/native/$folder/$arch/$file")?.use { it.readBytes() } ?: return false
        return runCatching {
            removeOldCopies()
            // A new folder only this user can open (0700 on Linux), so no one can swap the library.
            val dir = Files.createTempDirectory(PREFIX)
            val lib = dir.resolve(file)
            Files.write(lib, bytes)
            System.load(lib.toAbsolutePath().toString())
            dir.toFile().deleteOnExit()
            lib.toFile().deleteOnExit()
            true
        }.getOrDefault(false)
    }

    /** Windows cannot delete a loaded library on exit, so earlier copies are removed the next time. */
    private fun removeOldCopies() {
        val temp = Path.of(System.getProperty("java.io.tmpdir"))
        runCatching {
            Files.list(temp).use { s -> s.filter { it.fileName.toString().startsWith(PREFIX) }.toList() }.forEach { dir ->
                runCatching { dir.toFile().deleteRecursively() }
            }
        }
    }

    private const val PREFIX = "hfm-heif"
}
