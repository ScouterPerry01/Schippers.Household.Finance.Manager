package ca.schippers.hfm.data

import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.security.Aead
import ca.schippers.hfm.security.DecryptionException
import ca.schippers.hfm.security.Hkdf
import ca.schippers.hfm.security.KdfParams
import ca.schippers.hfm.security.KeyPair
import ca.schippers.hfm.security.PasswordKdf
import ca.schippers.hfm.security.Random
import ca.schippers.hfm.security.RecoveryKey
import ca.schippers.hfm.security.SealedBox
import java.nio.file.Files
import java.nio.file.Path

/** A household folder was created; the administrator must print or save [recoveryKey] now. */
class CreatedHousehold(val session: HouseholdSession, val recoveryKey: RecoveryKey)

/**
 * Creates and unlocks household folders.
 *
 * A household is a folder (`My Household.hfm/`) holding `household.json`, an encrypted core
 * database, one encrypted ledger database per account group, and later the document vault.
 * See docs/adr/0002-household-storage-and-encryption.md.
 */
class HouseholdStore(
    private val drivers: EncryptedDriverFactory,
    private val kdfParams: KdfParams = KdfParams(),
    private val clock: () -> Long = System::currentTimeMillis,
    /**
     * The Argon2id settings for passwords set from now on (Rates and rules, security.argon2.*);
     * [kdfParams] when not given. Existing passwords keep the settings stored with them.
     */
    private val newPasswordKdf: (() -> KdfParams)? = null,
) {

    fun isHousehold(dir: Path): Boolean = HeaderFile.exists(dir)

    fun create(
        dir: Path,
        householdName: String,
        adminLogin: String,
        adminDisplayName: String,
        password: CharArray,
        baseCurrency: String = "CAD",
        locale: String = "fr-CA",
        /** PROV-01: the province or territory code, e.g. "ON". */
        province: String = "QC",
        /** The name of the household's first shared group, in the household's language. */
        sharedGroupName: String = "Shared",
    ): CreatedHousehold {
        require(householdName.isNotBlank()) { "Household name is required" }
        require(adminLogin.isNotBlank()) { "Login name is required" }
        Files.createDirectories(dir)
        require(Files.list(dir).use { it.findFirst().isEmpty }) { "The folder $dir is not empty" }

        val householdId = Ids.newId()
        val adminId = Ids.newId()
        val keys = KeyPair.generate()
        val recovery = RecoveryKey.generate()
        val coreKey = Random.key()
        val corePartition = Partition(Ids.newId(), PartitionKind.CORE, "core.db")

        val header = HouseholdHeader(
            householdId = householdId,
            users = listOf(newUserKeys(householdId, adminId, adminLogin, password, keys, recovery)),
            partitions = listOf(corePartition),
            grants = listOf(seal(householdId, corePartition.id, adminId, keys.publicKey, coreKey)),
        )
        HeaderFile.write(dir, header)

        val session = HouseholdSession(this, dir, header, adminId, keys, mutableMapOf(corePartition.id to coreKey))
        val now = clock()
        session.core.transaction {
            session.core.coreQueries.insertHousehold(householdId, householdName.trim(), baseCurrency, locale, now, province)
            session.core.coreQueries.insertUser(adminId, adminLogin.trim(), adminDisplayName.trim(), Role.ADMINISTRATOR.name, null, keys.publicKey, locale, now)
        }
        session.audit("CREATE", "household", householdId)
        session.createGroup(sharedGroupName.trim().ifEmpty { "Shared" }, private = false)
        return CreatedHousehold(session, recovery)
    }

    fun unlock(dir: Path, loginName: String, password: CharArray): HouseholdSession {
        val header = HeaderFile.read(dir)
        val user = header.user(loginName) ?: run {
            // Spend the same effort as a real attempt so timing does not reveal which names exist.
            PasswordKdf.derive(password, ByteArray(PasswordKdf.SALT_BYTES), kdfParams)
            throw WrongPasswordException()
        }
        val wrappingKey = PasswordKdf.derive(password, user.salt.unb64(), user.kdf)
        val privateKey = try {
            Aead.open(wrappingKey, user.wrappedPrivateKey.unb64(), userKeyAad(header.householdId, user.userId))
        } catch (_: DecryptionException) {
            throw WrongPasswordException()
        } finally {
            wrappingKey.fill(0)
        }
        return openSession(dir, header, user, KeyPair.fromPrivate(privateKey))
    }

    /**
     * Signs in with the printed recovery key and sets a new password (SEC-07). [check] sees the
     * household open before anything changes, to apply its password rules; whatever it throws
     * leaves the old password in place.
     */
    fun resetPassword(dir: Path, loginName: String, recoveryKey: RecoveryKey, newPassword: CharArray, check: (HouseholdSession) -> Unit = {}): HouseholdSession {
        val header = HeaderFile.read(dir)
        val user = header.user(loginName) ?: throw WrongPasswordException()
        val recoveryWrap = recoveryWrappingKey(recoveryKey, user.userId)
        val privateKey = try {
            Aead.open(recoveryWrap, user.recoveryWrappedPrivateKey.unb64(), recoveryAad(header.householdId, user.userId))
        } catch (_: DecryptionException) {
            throw WrongPasswordException()
        } finally {
            recoveryWrap.fill(0)
        }
        val keys = KeyPair.fromPrivate(privateKey)
        // The check's session wipes its key when it closes, so it gets a copy.
        try {
            openSession(dir, header, user, KeyPair.fromPrivate(privateKey.copyOf())).use(check)
        } catch (e: Exception) {
            privateKey.fill(0)
            throw e
        }
        val kdf = kdfForNewPassword()
        val salt = Random.bytes(PasswordKdf.SALT_BYTES)
        val newWrap = PasswordKdf.derive(newPassword, salt, kdf)
        val updatedUser = user.copy(
            kdf = kdf,
            salt = salt.b64(),
            wrappedPrivateKey = Aead.seal(newWrap, keys.privateKey, userKeyAad(header.householdId, user.userId)).b64(),
        )
        newWrap.fill(0)
        val updated = header.copy(users = header.users.map { if (it.userId == user.userId) updatedUser else it })
        HeaderFile.write(dir, updated)
        return openSession(dir, updated, updatedUser, keys).also { it.audit("RESET_PASSWORD", "app_user", user.userId) }
    }

    private fun openSession(dir: Path, header: HouseholdHeader, user: UserKeys, keys: KeyPair): HouseholdSession {
        val partitionKeys = mutableMapOf<String, ByteArray>()
        for (grant in header.grants.filter { it.userId == user.userId }) {
            partitionKeys[grant.partitionId] = SealedBox.open(keys, grant.sealedKey.unb64(), grantAad(header.householdId, grant.partitionId, user.userId))
        }
        val session = HouseholdSession(this, dir, header, user.userId, keys, partitionKeys)
        // The header is not encrypted; check the user's public key against the authenticated core database.
        val stored = session.core.coreQueries.userById(user.userId).executeAsOneOrNull()
        if (stored == null || !stored.public_key.contentEquals(keys.publicKey) || stored.active == 0L) {
            session.close()
            throw AccessDeniedException("This user account is not active in this household")
        }
        return session
    }

    // --- Used by HouseholdSession -------------------------------------------------------------

    internal fun openDriver(dir: Path, partition: Partition, key: ByteArray) =
        drivers.open(dir.resolve(partition.file), key)

    internal fun now(): Long = clock()

    private fun kdfForNewPassword(): KdfParams = newPasswordKdf?.invoke() ?: kdfParams

    internal fun verifyPassword(header: HouseholdHeader, userId: String, password: CharArray): Boolean {
        val user = header.users.firstOrNull { it.userId == userId } ?: return false
        val wrappingKey = PasswordKdf.derive(password, user.salt.unb64(), user.kdf)
        return try {
            Aead.open(wrappingKey, user.wrappedPrivateKey.unb64(), userKeyAad(header.householdId, userId)).fill(0)
            true
        } catch (_: DecryptionException) {
            false
        } finally {
            wrappingKey.fill(0)
        }
    }

    internal fun newUserKeys(
        householdId: String,
        userId: String,
        loginName: String,
        password: CharArray,
        keys: KeyPair,
        recovery: RecoveryKey,
    ): UserKeys {
        val kdf = kdfForNewPassword()
        val salt = Random.bytes(PasswordKdf.SALT_BYTES)
        val wrap = PasswordKdf.derive(password, salt, kdf)
        val recoveryWrap = recoveryWrappingKey(recovery, userId)
        try {
            return UserKeys(
                userId = userId,
                loginName = loginName.trim(),
                kdf = kdf,
                salt = salt.b64(),
                publicKey = keys.publicKey.b64(),
                wrappedPrivateKey = Aead.seal(wrap, keys.privateKey, userKeyAad(householdId, userId)).b64(),
                recoveryWrappedPrivateKey = Aead.seal(recoveryWrap, keys.privateKey, recoveryAad(householdId, userId)).b64(),
            )
        } finally {
            wrap.fill(0)
            recoveryWrap.fill(0)
        }
    }

    internal fun seal(householdId: String, partitionId: String, userId: String, publicKey: ByteArray, key: ByteArray) =
        Grant(partitionId, userId, SealedBox.seal(publicKey, key, grantAad(householdId, partitionId, userId)).b64())

    internal fun writeHeader(dir: Path, header: HouseholdHeader) = HeaderFile.write(dir, header)

    /** Wraps a user's private key under a new password; the recovery wrapping is unchanged. */
    internal fun rewrapPassword(header: HouseholdHeader, userId: String, keys: KeyPair, newPassword: CharArray): HouseholdHeader {
        val user = header.users.first { it.userId == userId }
        val kdf = kdfForNewPassword()
        val salt = Random.bytes(PasswordKdf.SALT_BYTES)
        val wrap = PasswordKdf.derive(newPassword, salt, kdf)
        try {
            val updated = user.copy(kdf = kdf, salt = salt.b64(), wrappedPrivateKey = Aead.seal(wrap, keys.privateKey, userKeyAad(header.householdId, userId)).b64())
            return header.copy(users = header.users.map { if (it.userId == userId) updated else it })
        } finally {
            wrap.fill(0)
        }
    }

    private fun recoveryWrappingKey(recovery: RecoveryKey, userId: String) =
        Hkdf.derive(recovery.bytes, userId.toByteArray(), "hfm/recovery/v1")

    private fun userKeyAad(householdId: String, userId: String) = "hfm/user-key/v1|$householdId|$userId".toByteArray()
    private fun recoveryAad(householdId: String, userId: String) = "hfm/user-recovery/v1|$householdId|$userId".toByteArray()
    private fun grantAad(householdId: String, partitionId: String, userId: String) =
        "hfm/grant/v1|$householdId|$partitionId|$userId".toByteArray()
}
