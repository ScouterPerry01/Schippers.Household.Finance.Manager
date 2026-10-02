package ca.schippers.hfm.data

import ca.schippers.hfm.security.Aead
import ca.schippers.hfm.security.Hkdf
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Encrypted document files for one partition (section 4.4, SEC-01, ADR 0002).
 *
 * Each document is one file, `vault/<partition>/<document>.hfmdoc`, sealed with AES-256-GCM under
 * a key derived from the partition's key. The household, partition and document ids are bound in as
 * authenticated data, so a file cannot be swapped for another or moved to a different group, and
 * nobody without the group's key can read it (HH-11).
 */
class DocumentVault internal constructor(
    private val root: Path,
    private val householdId: String,
    private val partitionId: String,
    partitionKey: ByteArray,
) {
    private val key = Hkdf.derive(partitionKey, householdId.encodeToByteArray(), INFO)
    private val dir: Path get() = root.resolve(partitionId)

    fun put(documentId: String, content: ByteArray) {
        requireSafe(documentId)
        Files.createDirectories(dir)
        val target = file(documentId)
        val temp = Files.createTempFile(dir, ".$documentId", ".tmp")
        try {
            Files.write(temp, MAGIC + Aead.seal(key, content, aad(documentId)))
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    /** @throws ca.schippers.hfm.security.DecryptionException when the file was altered or belongs elsewhere. */
    fun get(documentId: String): ByteArray {
        requireSafe(documentId)
        val bytes = Files.readAllBytes(file(documentId))
        require(bytes.size > MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Not a vault file" }
        return Aead.open(key, bytes.copyOfRange(MAGIC.size, bytes.size), aad(documentId))
    }

    fun exists(documentId: String): Boolean = Files.exists(file(documentId))

    fun delete(documentId: String) {
        requireSafe(documentId)
        Files.deleteIfExists(file(documentId))
    }

    /** Bytes used on disk by this partition's documents. */
    fun sizeOnDisk(): Long = if (!Files.isDirectory(dir)) 0 else Files.list(dir).use { s -> s.mapToLong { Files.size(it) }.sum() }

    internal fun file(documentId: String): Path = dir.resolve("$documentId$EXTENSION")

    private fun aad(documentId: String) = "$householdId|$partitionId|$documentId".encodeToByteArray()

    private fun requireSafe(id: String) = require(id.isNotEmpty() && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }) { "Invalid document id" }

    companion object {
        const val DIR = "vault"
        const val EXTENSION = ".hfmdoc"
        private const val INFO = "hfm vault v1"
        private val MAGIC = "HFMDOC1\n".encodeToByteArray()
    }
}
