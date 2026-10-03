package ca.schippers.hfm.update

import ca.schippers.hfm.security.ReleaseSignature
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** How this copy was installed, which decides whether it checks GitHub Releases (DIST-05). */
enum class Channel(val kind: String) {
    DEB("deb"),
    RPM("rpm"),
    APPIMAGE("appimage"),

    /** The phone app from GitHub Releases; the Google Play build is updated by Play. */
    APK("apk"),
}

/** A release's version, compared number by number: 0.10.0 is newer than 0.9.1. */
data class Version(val parts: List<Int>) : Comparable<Version> {
    override fun compareTo(other: Version): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val c = parts.getOrElse(i) { 0 }.compareTo(other.parts.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    override fun toString() = parts.joinToString(".")

    companion object {
        fun parse(text: String): Version {
            val parts = text.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: -1 }
            require(parts.size in 1..4 && parts.all { it in 0..99_999 }) { "Not a version: $text" }
            // Padded, so 1.0 and 1.0.0 are equal as well as comparing equal.
            return Version(parts + List(maxOf(0, 3 - parts.size)) { 0 })
        }
    }
}

/** One downloadable file of a release. */
@Serializable
data class UpdateFile(val kind: String, val name: String, val url: String, val size: Long, val sha256: String)

/**
 * `update.json`, published with every release beside its signature `update.json.minisig`.
 * Notes are keyed by language tag ("en", "fr").
 */
@Serializable
data class UpdateManifest(
    val format: Int = FORMAT,
    val version: String,
    val date: String,
    val notes: Map<String, String> = emptyMap(),
    val files: List<UpdateFile>,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val FORMAT = 1
        fun parse(text: String): UpdateManifest = json.decodeFromString(serializer(), text)
        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    }
}

/** A newer release this copy can install. */
data class UpdateOffer(val version: Version, val date: String, val notes: Map<String, String>, val file: UpdateFile) {
    fun notes(languageTag: String): String = notes[languageTag] ?: notes["en"].orEmpty()
}

/** Thrown when an update must not be used: a bad signature, a damaged manifest or download. */
class UpdateRejected(message: String) : Exception(message)

/**
 * SEC-08: nothing from a release is trusted before its signature is checked against the public
 * key built into the app. The manifest's signature must name `update.json` in its trusted comment,
 * so the signature of another release file cannot be passed off as the manifest's.
 */
class UpdateCheck(private val publicKey: ReleaseSignature.PublicKey) {

    /** The newer release for [channel], or null when this copy is up to date. */
    fun evaluate(manifest: ByteArray, signature: String, current: String, channel: Channel): UpdateOffer? {
        if (manifest.size > MAX_MANIFEST_BYTES) throw UpdateRejected("The update list is too large")
        val comment = try {
            ReleaseSignature.verify(manifest, signature, publicKey)
        } catch (e: ReleaseSignature.BadSignature) {
            throw UpdateRejected("The update list is not signed by RANN: ${e.message}")
        }
        if (comment.split('\t').none { it == "file:$MANIFEST" }) throw UpdateRejected("The signature is not the update list's")
        val parsed = try {
            UpdateManifest.parse(manifest.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw UpdateRejected("The update list cannot be read")
        }
        if (parsed.format != UpdateManifest.FORMAT) throw UpdateRejected("The update list needs a newer version of the app")
        val version = runCatching { Version.parse(parsed.version) }.getOrElse { throw UpdateRejected("The update list has no valid version") }
        if (version <= Version.parse(current)) return null
        val file = parsed.files.firstOrNull { it.kind == channel.kind } ?: return null
        if (!file.url.startsWith("https://")) throw UpdateRejected("The download is not on a secure address")
        // The name becomes a file on disk: never a path, even in a signed list.
        if (!SAFE_NAME.matches(file.name)) throw UpdateRejected("The download's name is not a plain file name")
        if (file.size <= 0 || file.size > MAX_DOWNLOAD_BYTES || !SHA256.matches(file.sha256)) {
            throw UpdateRejected("The update list describes the download incorrectly")
        }
        return UpdateOffer(version, parsed.date, parsed.notes, file)
    }

    companion object {
        const val MANIFEST = "update.json"
        const val RELEASES = "https://github.com/ScouterPerry01/Schippers.Household.Finance.Manager/releases"

        /** Always the newest published release (never a draft or pre-release). */
        const val MANIFEST_URL = "$RELEASES/latest/download/$MANIFEST"
        const val SIGNATURE_URL = "$MANIFEST_URL.minisig"

        const val MAX_MANIFEST_BYTES = 256 * 1024
        const val MAX_DOWNLOAD_BYTES = 1024L * 1024 * 1024
        private val SHA256 = Regex("[0-9a-f]{64}")
        private val SAFE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,199}")

        /** The check with RANN's release key, built into the app. */
        fun release(): UpdateCheck = UpdateCheck(ReleaseKey.publicKey)

        /** SEC-08: checked before install. Throws unless [sha256] and [size] are the manifest's. */
        fun verifyDownload(file: UpdateFile, size: Long, sha256: String) {
            if (size != file.size || !sha256.equals(file.sha256, ignoreCase = true)) {
                throw UpdateRejected("The download does not match the signed update list")
            }
        }

        fun sha256Hex(digest: MessageDigest): String = digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/** RANN's release public key, from `hfm/update/release-key.pub` (made by `tools/release keygen`). */
object ReleaseKey {
    val publicKey: ReleaseSignature.PublicKey by lazy {
        val text = ReleaseKey::class.java.getResourceAsStream("/hfm/update/release-key.pub")
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("The release public key is missing from the build")
        ReleaseSignature.PublicKey.parse(text)
    }
}
