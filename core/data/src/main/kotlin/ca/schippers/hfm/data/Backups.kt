package ca.schippers.hfm.data

import app.cash.sqldelight.db.QueryResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/** What a backup file contains; stored as `manifest.json` inside it. */
@Serializable
data class BackupManifest(
    val format: Int = 1,
    val householdId: String,
    val createdAt: Long,
    val createdBy: String,
    val files: List<BackupEntry>,
)

@Serializable
data class BackupEntry(val path: String, val size: Long, val sha256: String)

data class BackupInfo(val file: Path, val householdId: String, val createdAt: Instant, val size: Long)

/**
 * A problem found by the test-restore check: [key] names it in the message files (backupProblem.*),
 * with its [args]; [english] is the same in English, for logs and errors.
 */
data class BackupProblem(val key: String, val args: List<String>, val english: String)

/** Result of the test-restore check (BAK-03). */
data class VerifyResult(val ok: Boolean, val issues: List<BackupProblem>, val checkedDatabases: Int) {
    val problems: List<String> get() = issues.map { it.english }
}

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Backups (BAK-01 to BAK-05). A backup is one `.hfmbak` file: a ZIP of the household folder's
 * files plus a manifest of checksums. The databases inside stay encrypted with their own keys,
 * and the key ring is protected by each user's password and recovery key, so a backup reveals no
 * financial data and restoring it needs the same credentials as the household itself.
 *
 * See docs/adr/0005-backups.md.
 */
object Backups {
    const val EXTENSION = "hfmbak"
    private const val MANIFEST = "manifest.json"
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /**
     * Writes a backup of the open household into [targetDir]. Database writes pause while the
     * files are copied so the copy is consistent; this takes a fraction of a second.
     */
    fun create(session: HouseholdSession, targetDir: Path, now: LocalDateTime = LocalDateTime.now()): BackupInfo {
        Files.createDirectories(targetDir)
        val baseName = session.dir.name.removeSuffix(".hfm")
        val target = targetDir.resolve("$baseName-${now.format(stamp)}.$EXTENSION")
        val temp = targetDir.resolve(target.name + ".part")
        val entries = mutableListOf<BackupEntry>()

        session.withWritesPaused {
            ZipOutputStream(Files.newOutputStream(temp)).use { zip ->
                for (file in householdFiles(session.dir)) {
                    val relative = session.dir.relativize(file).toString().replace('\\', '/')
                    val digest = MessageDigest.getInstance("SHA-256")
                    zip.putNextEntry(ZipEntry(relative))
                    Files.newInputStream(file).use { input -> copy(input, zip, digest) }
                    zip.closeEntry()
                    entries += BackupEntry(relative, file.fileSize(), hex(digest.digest()))
                }
                val manifest = BackupManifest(householdId = session.householdId, createdAt = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), createdBy = session.userId, files = entries)
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
                zip.closeEntry()
            }
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        session.audit("BACKUP", "household", session.householdId, target.name)
        return info(target) ?: throw BackupException("The backup could not be read back")
    }

    /** The files that make up a household: key ring, databases with their logs, and the document vault. */
    private fun householdFiles(dir: Path): List<Path> = Files.walk(dir).use { paths ->
        paths.filter { it.isRegularFile() }
            .filter { p ->
                val relative = dir.relativize(p)
                val top = relative.getName(0).toString()
                val name = p.name
                top != "backups" && !name.endsWith(".tmp") && !name.endsWith("-shm") &&
                    (name == HouseholdHeader.FILE_NAME || name.endsWith(".db") || name.endsWith(".db-wal") || top == "vault")
            }
            .sorted()
            .toList()
    }

    /** Reads a backup's manifest without extracting it, or null if the file is not a backup. */
    fun info(file: Path): BackupInfo? = runCatching {
        val manifest = readManifest(file) ?: return null
        BackupInfo(file, manifest.householdId, Instant.ofEpochMilli(manifest.createdAt), file.fileSize())
    }.getOrNull()

    /** Backups of one household in a folder, newest first. */
    fun list(dir: Path, householdId: String): List<BackupInfo> {
        if (!dir.isDirectory()) return emptyList()
        return Files.list(dir).use { s -> s.filter { it.name.endsWith(".$EXTENSION") }.toList() }
            .mapNotNull(::info)
            .filter { it.householdId == householdId }
            .sortedByDescending { it.createdAt }
    }

    /** BAK-01: keeps the [keep] newest backups of the household and deletes older ones. */
    fun prune(dir: Path, householdId: String, keep: Int): List<Path> {
        require(keep >= 1) { "Keep at least one backup" }
        val old = list(dir, householdId).drop(keep).map { it.file }
        old.forEach { Files.deleteIfExists(it) }
        return old
    }

    /**
     * BAK-03 test-restore: checks every file against its checksum, and that no database is stored
     * unencrypted. With [session], the databases this user can open are also extracted to a
     * temporary folder, opened with their keys, and checked with SQLite's integrity check.
     */
    fun verify(file: Path, session: HouseholdSession? = null, drivers: EncryptedDriverFactory? = null): VerifyResult {
        val problems = mutableListOf<BackupProblem>()
        fun problem(key: String, english: String, vararg args: String) { problems += BackupProblem("backupProblem.$key", args.toList(), english) }
        val manifest = readManifest(file) ?: return VerifyResult(false, listOf(BackupProblem("backupProblem.notBackup", emptyList(), "Not a backup file")), 0)
        if (session != null && manifest.householdId != session.householdId) problem("otherHousehold", "This backup belongs to another household")
        val expected = manifest.files.associateBy { it.path }
        val seen = HashSet<String>()
        val temp = Files.createTempDirectory("hfm-verify")
        var checked = 0
        try {
            ZipInputStream(Files.newInputStream(file)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == MANIFEST) continue
                    val wanted = expected[entry.name]
                    if (wanted == null) {
                        problem("unexpected", "Unexpected file ${entry.name}", entry.name)
                        continue
                    }
                    val out = safeResolve(temp, entry.name)
                    Files.createDirectories(out.parent)
                    val digest = MessageDigest.getInstance("SHA-256")
                    Files.newOutputStream(out).use { copy(zip, it, digest) }
                    if (hex(digest.digest()) != wanted.sha256) problem("damaged", "${entry.name} is damaged", entry.name)
                    if (entry.name.endsWith(".db") && Files.newInputStream(out).use { it.readNBytes(16) }.decodeToString().startsWith("SQLite format 3")) {
                        problem("notEncrypted", "${entry.name} is not encrypted", entry.name)
                    }
                    seen += entry.name
                }
            }
            (expected.keys - seen).forEach { problem("missing", "$it is missing", it) }
            if (HouseholdHeader.FILE_NAME !in seen) problem("noKeyRing", "The key ring is missing")

            if (session != null && drivers != null && problems.isEmpty()) {
                for (partition in session.header.partitions) {
                    val key = session.partitionKeyForBackupCheck(partition.id) ?: continue
                    val db = temp.resolve(partition.file)
                    if (!Files.exists(db)) continue
                    try {
                        val driver = drivers.open(db, key)
                        try {
                            val result = driver.executeQuery(null, "PRAGMA quick_check", { c -> c.next(); QueryResult.Value(c.getString(0)) }, 0).value
                            if (result != "ok") problem("integrity", "${partition.file}: $result", partition.file, result.toString())
                            checked++
                        } finally {
                            driver.close()
                        }
                    } catch (e: Exception) {
                        problem("cannotOpen", "${partition.file} cannot be opened: ${e.message}", partition.file, e.message.orEmpty())
                    } finally {
                        key.fill(0)
                    }
                }
            }
        } catch (e: Exception) {
            problem("unreadable", "The backup cannot be read: ${e.message}", e.message.orEmpty())
        } finally {
            temp.toFile().deleteRecursively()
        }
        return VerifyResult(problems.isEmpty(), problems, checked)
    }

    /**
     * BAK-03 / BAK-05: restores a backup into a new household folder [destination], which must
     * not exist or be empty. Every file is checked against its checksum first. The restored
     * household is then unlocked with the usual password or recovery key.
     */
    fun restore(file: Path, destination: Path): Path {
        val check = verify(file)
        if (!check.ok) throw BackupException("The backup is damaged: ${check.problems.joinToString("; ")}")
        if (Files.exists(destination) && Files.list(destination).use { it.findFirst().isPresent }) {
            throw BackupException("The folder $destination is not empty")
        }
        val temp = destination.resolveSibling(destination.name + ".restoring")
        temp.toFile().deleteRecursively()
        Files.createDirectories(temp)
        ZipInputStream(Files.newInputStream(file)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == MANIFEST || entry.isDirectory) continue
                val out = safeResolve(temp, entry.name)
                Files.createDirectories(out.parent)
                Files.newOutputStream(out).use { zip.copyTo(it) }
            }
        }
        if (Files.exists(destination)) Files.delete(destination)
        Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE)
        return destination
    }

    /** Never extract outside the target folder, whatever the archive says ("zip slip"). */
    private fun safeResolve(dir: Path, name: String): Path {
        val resolved = dir.resolve(name).normalize()
        if (!resolved.startsWith(dir)) throw BackupException("Unsafe path in backup: $name")
        return resolved
    }

    /** Reads the manifest from the archive's index, without reading the (possibly damaged) data before it. */
    private fun readManifest(file: Path): BackupManifest? {
        if (!file.isRegularFile()) return null
        return runCatching {
            ZipFile(file.toFile()).use { zip ->
                val entry = zip.getEntry(MANIFEST) ?: return null
                json.decodeFromString(BackupManifest.serializer(), zip.getInputStream(entry).readBytes().decodeToString())
            }
        }.getOrNull()
    }

    private fun copy(input: InputStream, out: java.io.OutputStream, digest: MessageDigest) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
            out.write(buffer, 0, n)
        }
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
