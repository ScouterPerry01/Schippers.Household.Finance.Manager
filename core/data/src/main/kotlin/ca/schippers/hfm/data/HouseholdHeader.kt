package ca.schippers.hfm.data

import ca.schippers.hfm.security.KdfParams
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64

/**
 * `household.json`: the only unencrypted file in a household folder. It holds no financial data,
 * only what is needed to unlock: each user's login name, password salt and wrapped private key,
 * and the partition keys sealed to each user who may open that partition.
 *
 * Every wrapped value is authenticated and bound to its household, user and partition, so values
 * cannot be swapped between entries. See docs/adr/0002-household-storage-and-encryption.md.
 */
@Serializable
data class HouseholdHeader(
    val format: Int = CURRENT_FORMAT,
    val householdId: String,
    val users: List<UserKeys>,
    val partitions: List<Partition>,
    val grants: List<Grant>,
) {
    fun user(loginName: String): UserKeys? = users.firstOrNull { it.loginName.equals(loginName, ignoreCase = true) }
    fun partition(id: String): Partition = partitions.first { it.id == id }
    val corePartition: Partition get() = partitions.single { it.kind == PartitionKind.CORE }

    companion object {
        const val CURRENT_FORMAT = 1
        const val FILE_NAME = "household.json"
    }
}

@Serializable
data class UserKeys(
    val userId: String,
    val loginName: String,
    val kdf: KdfParams,
    val salt: String,
    val publicKey: String,
    /** The user's X25519 private key, encrypted with their password-derived key. */
    val wrappedPrivateKey: String,
    /** The same private key, encrypted with a key derived from the user's printed recovery key (SEC-07). */
    val recoveryWrappedPrivateKey: String,
)

enum class PartitionKind { CORE, LEDGER }

@Serializable
data class Partition(val id: String, val kind: PartitionKind, val file: String)

/** A partition's 256-bit database key, sealed to one user's public key. */
@Serializable
data class Grant(val partitionId: String, val userId: String, val sealedKey: String)

internal object HeaderFile {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    fun exists(dir: Path): Boolean = Files.exists(dir.resolve(HouseholdHeader.FILE_NAME))

    fun read(dir: Path): HouseholdHeader {
        val file = dir.resolve(HouseholdHeader.FILE_NAME)
        if (!Files.exists(file)) throw NotAHouseholdException(dir)
        val header = json.decodeFromString(HouseholdHeader.serializer(), Files.readString(file))
        if (header.format > HouseholdHeader.CURRENT_FORMAT) throw NewerFormatException(header.format, HouseholdHeader.CURRENT_FORMAT)
        return header
    }

    /** Writes atomically: a crash leaves either the old or the new header, never a partial one (NFR-05). */
    fun write(dir: Path, header: HouseholdHeader) {
        val target = dir.resolve(HouseholdHeader.FILE_NAME)
        val temp = dir.resolve("${HouseholdHeader.FILE_NAME}.tmp")
        Files.writeString(temp, json.encodeToString(HouseholdHeader.serializer(), header))
        if (Files.exists(target)) {
            Files.copy(target, dir.resolve("${HouseholdHeader.FILE_NAME}.bak"), StandardCopyOption.REPLACE_EXISTING)
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}

internal fun ByteArray.b64(): String = Base64.getEncoder().encodeToString(this)
internal fun String.unb64(): ByteArray = Base64.getDecoder().decode(this)

class NotAHouseholdException(dir: Path) : Exception("No household found in $dir")
class NewerFormatException(found: Int, supported: Int) :
    Exception("This household was saved by a newer version of the application (format $found; this version supports $supported)")
class WrongPasswordException : Exception("Wrong user name or password")
class AccessDeniedException(message: String) : Exception(message)
