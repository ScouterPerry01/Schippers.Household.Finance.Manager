package ca.schippers.hfm.ocr.desktop

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path

/**
 * HEIC photos, as iPhones and many Android phones save them (CAP-03, ADR 0004), read with a
 * decoder the user installs, since HEVC decoding is covered by patent licences (owner's decision,
 * 2026-10-04). RANN's Roost ships no HEVC decoder:
 * - Windows: Windows Imaging Component, through a small helper with no codec in it; HEIC works once
 *   Microsoft's HEIF Image Extensions and HEVC Video Extensions are installed.
 * - Linux: the system's libheif with its HEVC plugin, from the distribution.
 *
 * HEIC counts as available only when a tiny HEIC picture kept in the app actually decodes, upright,
 * so a decoder installed without its HEVC part is caught.
 */
object Heif {

    /** Larger pictures are refused rather than decoded. */
    const val MAX_PIXELS = 120_000_000

    enum class Platform { WINDOWS, LINUX, OTHER }

    val platform: Platform = System.getProperty("os.name").lowercase().let {
        when {
            it.startsWith("windows") -> Platform.WINDOWS
            it.startsWith("linux") -> Platform.LINUX
            else -> Platform.OTHER
        }
    }

    /** HEVC brands of the ISO base media file format; AVIF and other HEIF codecs are not HEIC. */
    private val BRANDS = setOf("heic", "heix", "heim", "heis", "hevc", "hevx", "hevm", "hevs")

    /** True when [bytes] start with a HEIF `ftyp` box naming an HEVC brand. */
    fun isHeic(bytes: ByteArray): Boolean {
        if (bytes.size < 16 || String(bytes, 4, 4, Charsets.ISO_8859_1) != "ftyp") return false
        val boxSize = ((bytes[0].toInt() and 0xFF) shl 24) or ((bytes[1].toInt() and 0xFF) shl 16) or ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        val end = minOf(boxSize, bytes.size)
        // The major brand at 8, the minor version at 12, then the compatible brands.
        return (listOf(8) + (16 until end - 3 step 4)).any { String(bytes, it, 4, Charsets.ISO_8859_1) in BRANDS }
    }

    private val decoder: Decoder? by lazy {
        when (platform) {
            Platform.WINDOWS -> runCatching { WicNative.load(); Decoder { b, side -> WicNative.decode(b, MAX_PIXELS, side) } }.getOrNull()
            Platform.LINUX -> runCatching { SystemLibHeif() }.getOrNull()?.let { lib -> Decoder { b, side -> lib.decode(b, MAX_PIXELS, side) } }
            Platform.OTHER -> null
        }
    }

    /** Whether this computer can read HEIC: the probe picture, stored 64 x 32 with a quarter turn, comes out 32 x 64 with red on top. */
    val available: Boolean by lazy {
        val probe = Heif::class.java.getResourceAsStream("/hfm/heif/probe.heic")?.use { it.readBytes() } ?: return@lazy false
        val img = read(probe, 3200) ?: return@lazy false
        val top = img.getRGB(16, 8)
        img.width == 32 && img.height == 64 && (top shr 16 and 0xFF) > 150 && (top and 0xFF) < 100
    }

    /** What reads HEIC here, for the packaged self-check. */
    fun describe(): String = when {
        !available -> "not installed"
        platform == Platform.WINDOWS -> "Windows Imaging Component"
        else -> "system libheif ${SystemLibHeif.version() ?: ""}".trim()
    }

    /** The picture, upright, at most [maxSide] pixels on its long side; null if it cannot be read here. */
    fun decode(bytes: ByteArray, maxSide: Int): BufferedImage? = if (available) read(bytes, maxSide) else null

    private fun read(bytes: ByteArray, maxSide: Int): BufferedImage? {
        val data = runCatching { decoder?.decode(bytes, maxSide) }.getOrNull() ?: return null
        val (w, h) = data[0] to data[1]
        return BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).also { it.setRGB(0, 0, w, h, data, 2, w) }
    }

    private fun interface Decoder {
        /** `[width, height, 0xFFRRGGBB...]`; throws when the picture cannot be read. */
        fun decode(bytes: ByteArray, maxSide: Int): IntArray
    }
}

/** The JNI helper of `tools/natives/wic` (Windows only). */
internal object WicNative {

    @JvmStatic external fun decode(bytes: ByteArray, maxPixels: Int, maxSide: Int): IntArray

    private var loaded = false

    @Synchronized
    fun load() {
        if (loaded) return
        val arch = if (System.getProperty("os.arch") in listOf("aarch64", "arm64")) "aarch64" else "x86_64"
        val bytes = WicNative::class.java.getResourceAsStream("/hfm/heif/native/Windows/$arch/hfmwic.dll")?.use { it.readBytes() }
            ?: error("no helper for Windows $arch")
        removeOldCopies()
        // A new folder of the user's own, so no one can swap the library.
        val dir = Files.createTempDirectory(PREFIX)
        val lib = dir.resolve("hfmwic.dll")
        Files.write(lib, bytes)
        System.load(lib.toAbsolutePath().toString())
        dir.toFile().deleteOnExit()
        lib.toFile().deleteOnExit()
        loaded = true
    }

    /** Windows cannot delete a loaded library on exit, so earlier copies are removed the next time. */
    private fun removeOldCopies() {
        val temp = Path.of(System.getProperty("java.io.tmpdir"))
        runCatching {
            Files.list(temp).use { s -> s.filter { it.fileName.toString().startsWith(PREFIX) || it.fileName.toString().startsWith("hfm-heif") }.toList() }
                .forEach { runCatching { it.toFile().deleteRecursively() } }
        }
    }

    private const val PREFIX = "hfm-wic"
}

// --- The system's libheif (Linux) --------------------------------------------------------------------

@Structure.FieldOrder("code", "subcode", "message")
open class HeifError : Structure() {
    @JvmField var code = 0
    @JvmField var subcode = 0
    @JvmField var message: Pointer? = null

    class ByValue : HeifError(), Structure.ByValue

    fun text(): String = message?.getString(0, "UTF-8") ?: "libheif error $code"
}

interface LibHeif : Library {
    fun heif_init(params: Pointer?): HeifError.ByValue
    fun heif_get_version(): String
    fun heif_context_alloc(): Pointer?
    fun heif_context_free(context: Pointer)
    fun heif_context_read_from_memory_without_copy(context: Pointer, memory: Pointer, size: Long, options: Pointer?): HeifError.ByValue
    fun heif_context_get_primary_image_handle(context: Pointer, handle: PointerByReference): HeifError.ByValue
    fun heif_image_handle_get_width(handle: Pointer): Int
    fun heif_image_handle_get_height(handle: Pointer): Int
    fun heif_image_handle_release(handle: Pointer)
    fun heif_decode_image(handle: Pointer, image: PointerByReference, colorspace: Int, chroma: Int, options: Pointer?): HeifError.ByValue
    fun heif_image_get_width(image: Pointer, channel: Int): Int
    fun heif_image_get_height(image: Pointer, channel: Int): Int
    fun heif_image_get_plane_readonly(image: Pointer, channel: Int, stride: IntByReference): Pointer?
    fun heif_image_scale_image(image: Pointer, scaled: PointerByReference, width: Int, height: Int, options: Pointer?): HeifError.ByValue
    fun heif_image_release(image: Pointer)
}

/**
 * The distribution's libheif (`libheif.so.1`), with whatever HEVC decoder its plugins provide.
 * libheif applies the file's rotation and mirroring itself.
 */
internal class SystemLibHeif {
    private val lib: LibHeif = load()

    init {
        // Loads the decoder plugins the distribution installed (libheif 1.14 and later).
        runCatching { lib.heif_init(null) }
    }

    fun decode(bytes: ByteArray, maxPixels: Int, maxSide: Int): IntArray {
        val memory = Memory(bytes.size.toLong().coerceAtLeast(1)).apply { write(0, bytes, 0, bytes.size) }
        val ctx = lib.heif_context_alloc() ?: error("out of memory")
        var handle: Pointer? = null
        var image: Pointer? = null
        try {
            check(lib.heif_context_read_from_memory_without_copy(ctx, memory, bytes.size.toLong(), null))
            val h = PointerByReference()
            check(lib.heif_context_get_primary_image_handle(ctx, h))
            handle = h.value
            require(lib.heif_image_handle_get_width(handle).toLong() * lib.heif_image_handle_get_height(handle) <= maxPixels) { "image too large" }
            val img = PointerByReference()
            check(lib.heif_decode_image(handle, img, COLORSPACE_RGB, CHROMA_INTERLEAVED_RGB, null))
            image = img.value
            var w = lib.heif_image_get_width(image, CHANNEL_INTERLEAVED)
            var hgt = lib.heif_image_get_height(image, CHANNEL_INTERLEAVED)
            val long = maxOf(w, hgt)
            if (maxSide in 1 until long) {
                val scaled = PointerByReference()
                check(lib.heif_image_scale_image(image, scaled, (w.toLong() * maxSide / long).toInt().coerceAtLeast(1), (hgt.toLong() * maxSide / long).toInt().coerceAtLeast(1), null))
                lib.heif_image_release(image)
                image = scaled.value
                w = lib.heif_image_get_width(image, CHANNEL_INTERLEAVED)
                hgt = lib.heif_image_get_height(image, CHANNEL_INTERLEAVED)
            }
            val stride = IntByReference()
            val plane = lib.heif_image_get_plane_readonly(image, CHANNEL_INTERLEAVED, stride) ?: error("no picture in the file")
            val out = IntArray(2 + w * hgt)
            out[0] = w
            out[1] = hgt
            val row = ByteArray(w * 3)
            for (y in 0 until hgt) {
                plane.read(y.toLong() * stride.value, row, 0, row.size)
                for (x in 0 until w) {
                    val i = x * 3
                    out[2 + y * w + x] = (0xFF shl 24) or ((row[i].toInt() and 0xFF) shl 16) or ((row[i + 1].toInt() and 0xFF) shl 8) or (row[i + 2].toInt() and 0xFF)
                }
            }
            return out
        } finally {
            image?.let(lib::heif_image_release)
            handle?.let(lib::heif_image_handle_release)
            lib.heif_context_free(ctx)
            memory.close()
        }
    }

    private fun check(error: HeifError) {
        if (error.code != 0) throw java.io.IOException(error.text())
    }

    companion object {
        private const val COLORSPACE_RGB = 1
        private const val CHROMA_INTERLEAVED_RGB = 10
        private const val CHANNEL_INTERLEAVED = 10

        private fun load(): LibHeif = runCatching { Native.load("heif", LibHeif::class.java) }.getOrElse { Native.load("libheif.so.1", LibHeif::class.java) }

        fun version(): String? = runCatching { load().heif_get_version() }.getOrNull()
    }
}
