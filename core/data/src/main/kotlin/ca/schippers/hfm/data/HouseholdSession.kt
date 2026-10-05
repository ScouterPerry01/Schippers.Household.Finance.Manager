package ca.schippers.hfm.data

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import ca.schippers.hfm.data.core.CoreDatabase
import ca.schippers.hfm.data.ledger.LedgerDatabase
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.security.KeyPair
import ca.schippers.hfm.security.Random
import ca.schippers.hfm.security.SealedBox
import ca.schippers.hfm.security.RecoveryKey
import java.nio.file.Path

class AddedUser(val userId: String, val recoveryKey: RecoveryKey)

/**
 * One signed-in user's view of an unlocked household. It holds only the partition keys this user
 * was granted, so ledgers they may not see cannot be opened at all (HH-11).
 * Close it to lock the household (SEC-02); keys are wiped from memory.
 */
class HouseholdSession internal constructor(
    private val store: HouseholdStore,
    val dir: Path,
    header: HouseholdHeader,
    val userId: String,
    private val keys: KeyPair,
    private val partitionKeys: MutableMap<String, ByteArray>,
) : AutoCloseable {

    var header: HouseholdHeader = header
        private set

    private val drivers = mutableMapOf<String, SqlDriver>()
    private val ledgers = mutableMapOf<String, LedgerDatabase>()
    private var closed = false

    private val coreDatabase: CoreDatabase by lazy { CoreDatabase(open(header.corePartition, CoreDatabase.Schema)) }

    /** The core database; refused once the household is locked (SEC-02), even though it was opened before. */
    val core: CoreDatabase
        get() {
            checkOpen()
            return coreDatabase
        }

    val householdId: String get() = header.householdId

    val role: Role get() = Role.valueOf(core.coreQueries.userById(userId).executeAsOne().role)

    fun canOpen(partitionId: String): Boolean = partitionId in partitionKeys

    /** Re-authentication before revealing sensitive values such as full account numbers (SEC-04). */
    fun verifyPassword(password: CharArray): Boolean = store.verifyPassword(header, userId, password)

    fun ledger(partitionId: String): LedgerDatabase {
        checkOpen()
        return ledgers.getOrPut(partitionId) {
            if (!canOpen(partitionId)) throw AccessDeniedException("You do not have access to this account group")
            LedgerDatabase(open(header.partition(partitionId), LedgerDatabase.Schema))
        }
    }

    /** The encrypted document files of a partition; the same access rules as its ledger (HH-11). */
    fun vault(partitionId: String): DocumentVault {
        checkOpen()
        val key = partitionKeys[partitionId] ?: throw AccessDeniedException("You do not have access to this account group")
        return DocumentVault(dir.resolve(DocumentVault.DIR), householdId, partitionId, key)
    }

    /** Ledger for an account group, by group id. */
    fun groupLedger(groupId: String): LedgerDatabase {
        val group = core.coreQueries.groups().executeAsList().first { it.id == groupId }
        return ledger(group.partition_id)
    }

    /**
     * Creates an account group with its own encrypted ledger (HH-07). A private group belongs to
     * the signed-in user and is readable by nobody else until shared (HH-09). A shared group is
     * readable by every administrator.
     */
    fun createGroup(name: String, private: Boolean): String {
        checkOpen()
        require(name.isNotBlank()) { "Group name is required" }
        if (!private) requireAdministrator()
        val groupId = Ids.newId()
        val partition = Partition(Ids.newId(), PartitionKind.LEDGER, "ledger-${groupId}.db")
        val key = Random.key()

        val recipients = buildSet {
            add(userId)
            if (!private) addAll(activeUsers().filter { it.role == Role.ADMINISTRATOR.name }.map { it.id })
        }
        val grants = recipients.map { id -> store.seal(householdId, partition.id, id, publicKeyOf(id), key) }
        updateHeader(header.copy(partitions = header.partitions + partition, grants = header.grants + grants))
        partitionKeys[partition.id] = key

        val sortOrder = core.coreQueries.groups().executeAsList().size.toLong()
        core.coreQueries.insertGroup(groupId, name.trim(), partition.id, if (private) userId else null, sortOrder, store.now())
        ledger(partition.id) // creates the schema
        audit("CREATE", "account_group", groupId, if (private) "private" else "shared")
        return groupId
    }

    /** Adds a sign-in account (HH-05). Only administrators may add users. */
    fun addUser(loginName: String, displayName: String, role: Role, password: CharArray): AddedUser {
        checkOpen()
        requireAdministrator()
        require(header.user(loginName) == null) { "The login name $loginName is already used" }
        val newId = Ids.newId()
        val newKeys = KeyPair.generate()
        val recovery = RecoveryKey.generate()
        val coreKey = partitionKeys.getValue(header.corePartition.id)

        val grants = mutableListOf(store.seal(householdId, header.corePartition.id, newId, newKeys.publicKey, coreKey))
        if (role == Role.ADMINISTRATOR) {
            for (group in core.coreQueries.groups().executeAsList().filter { it.owner_user_id == null }) {
                val key = partitionKeys[group.partition_id] ?: continue
                grants += store.seal(householdId, group.partition_id, newId, newKeys.publicKey, key)
            }
        }
        val userKeys = store.newUserKeys(householdId, newId, loginName, password, newKeys, recovery)
        core.coreQueries.insertUser(newId, loginName.trim(), displayName.trim(), role.name, null, newKeys.publicKey, null, store.now())
        updateHeader(header.copy(users = header.users + userKeys, grants = header.grants + grants))
        newKeys.privateKey.fill(0)
        audit("CREATE", "app_user", newId, role.name)
        return AddedUser(newId, recovery)
    }

    /**
     * Sets a user's permission on an account group (HH-08). Granting any level above NONE gives the
     * user the group's key. Setting NONE removes their key grant; full revocation of a key the user
     * may already have seen needs key rotation, planned with SYNC-08 in Phase 2.
     */
    fun setPermission(groupId: String, targetUserId: String, level: PermissionLevel) {
        checkOpen()
        val group = core.coreQueries.groups().executeAsList().first { it.id == groupId }
        if (group.owner_user_id != userId) requireAdministrator()
        val key = partitionKeys[group.partition_id] ?: throw AccessDeniedException("You do not have access to this account group")

        val others = header.grants.filterNot { it.partitionId == group.partition_id && it.userId == targetUserId }
        val grants = if (level == PermissionLevel.NONE) {
            others
        } else {
            others + store.seal(householdId, group.partition_id, targetUserId, publicKeyOf(targetUserId), key)
        }
        core.coreQueries.setPermission(groupId, targetUserId, level.name)
        updateHeader(header.copy(grants = grants))
        audit("SET_PERMISSION", "account_group", groupId, "$targetUserId=$level")
    }

    /**
     * HH-06: changes a user's role. A new administrator receives the keys of every shared group;
     * a former administrator keeps only the groups they were given explicitly. The household always
     * keeps at least one active administrator.
     */
    fun setRole(targetUserId: String, role: Role) {
        checkOpen()
        requireAdministrator()
        val target = core.coreQueries.userById(targetUserId).executeAsOne()
        if (target.role == Role.ADMINISTRATOR.name && role != Role.ADMINISTRATOR) requireAnotherAdministrator(targetUserId)
        val shared = core.coreQueries.groups().executeAsList().filter { it.owner_user_id == null }
        var grants = header.grants
        for (group in shared) {
            val key = partitionKeys[group.partition_id] ?: continue
            val has = grants.any { it.partitionId == group.partition_id && it.userId == targetUserId }
            val explicit = core.coreQueries.permissionsForGroup(group.id).executeAsList().any { it.user_id == targetUserId && it.level != PermissionLevel.NONE.name }
            when {
                role == Role.ADMINISTRATOR && !has -> grants = grants + store.seal(householdId, group.partition_id, targetUserId, publicKeyOf(targetUserId), key)
                role != Role.ADMINISTRATOR && has && !explicit -> grants = grants.filterNot { it.partitionId == group.partition_id && it.userId == targetUserId }
            }
        }
        core.coreQueries.setUserRole(role.name, targetUserId)
        updateHeader(header.copy(grants = grants))
        audit("SET_ROLE", "app_user", targetUserId, role.name)
    }

    /** A deactivated user can no longer sign in; their past entries stay. */
    fun setActive(targetUserId: String, active: Boolean) {
        checkOpen()
        requireAdministrator()
        require(targetUserId != userId || active) { "You cannot deactivate yourself" }
        if (!active && core.coreQueries.userById(targetUserId).executeAsOne().role == Role.ADMINISTRATOR.name) requireAnotherAdministrator(targetUserId)
        core.coreQueries.setUserActive(if (active) 1 else 0, targetUserId)
        audit(if (active) "ACTIVATE" else "DEACTIVATE", "app_user", targetUserId)
    }

    fun renameUser(targetUserId: String, displayName: String) {
        checkOpen()
        if (targetUserId != userId) requireAdministrator()
        require(displayName.isNotBlank()) { "A name is required" }
        core.coreQueries.renameUser(displayName.trim(), targetUserId)
    }

    /**
     * M-71: changes a login name, the user's own or anyone's for an administrator. The name is in
     * the core database and in the household's header (which unlock reads before any key is open),
     * so both change together; it is not part of any encryption.
     */
    fun changeLoginName(targetUserId: String, loginName: String) {
        checkOpen()
        if (targetUserId != userId) requireAdministrator()
        val name = loginName.trim()
        require(name.isNotEmpty() && name.none(Char::isWhitespace)) { "A login name has no spaces" }
        require(header.users.none { it.userId != targetUserId && it.loginName.equals(name, ignoreCase = true) }) { "The login name $name is already used" }
        core.transaction {
            core.coreQueries.setLoginName(name, targetUserId)
            updateHeader(header.copy(users = header.users.map { if (it.userId == targetUserId) it.copy(loginName = name) else it }))
        }
        audit("RENAME_LOGIN", "app_user", targetUserId)
    }

    /** M-71: renames the household; for an administrator. The folder keeps its name. */
    fun renameHousehold(name: String) {
        checkOpen()
        requireAdministrator()
        require(name.isNotBlank()) { "A name is required" }
        core.coreQueries.renameHousehold(name.trim())
        audit("RENAME", "household", null)
    }

    /** HH-09: the household member a user is, so their own accounts and phones are theirs. */
    fun linkMember(targetUserId: String, memberId: String?) {
        checkOpen()
        requireAdministrator()
        core.coreQueries.linkUserToMember(memberId, targetUserId)
    }

    /** Changes the signed-in user's password; the recovery key stays valid. */
    fun changePassword(current: CharArray, newPassword: CharArray) {
        checkOpen()
        if (!verifyPassword(current)) throw WrongPasswordException()
        updateHeader(store.rewrapPassword(header, userId, keys, newPassword))
        audit("CHANGE_PASSWORD", "app_user", userId)
    }

    /** Seals data so that only [targetUserId] can open it, with their private key (HH-11). */
    fun sealFor(targetUserId: String, plaintext: ByteArray, context: String): ByteArray =
        SealedBox.seal(publicKeyOf(targetUserId), plaintext, "hfm/user-sealed/v1|$householdId|$targetUserId|$context".toByteArray())

    /** Opens data sealed for the signed-in user with [sealFor]. */
    fun openSealed(sealed: ByteArray, context: String): ByteArray {
        checkOpen()
        return SealedBox.open(keys, sealed, "hfm/user-sealed/v1|$householdId|$userId|$context".toByteArray())
    }

    private fun requireAnotherAdministrator(exceptUserId: String) {
        val others = activeUsers().count { it.role == Role.ADMINISTRATOR.name && it.id != exceptUserId }
        if (others == 0) throw IllegalStateException("The household needs at least one administrator")
    }

    fun audit(action: String, entity: String, entityId: String?, details: String? = null) {
        core.coreQueries.insertAudit(Ids.newId(), store.now(), userId, action, entity, entityId, details)
    }

    override fun close() {
        if (closed) return
        closed = true
        drivers.values.forEach { runCatching { it.close() } }
        drivers.clear()
        ledgers.clear()
        partitionKeys.values.forEach { it.fill(0) }
        partitionKeys.clear()
        keys.privateKey.fill(0)
    }

    private fun open(partition: Partition, schema: SqlSchema<QueryResult.Value<Unit>>): SqlDriver {
        checkOpen()
        val key = partitionKeys[partition.id] ?: throw AccessDeniedException("You do not have access to this data")
        val driver = store.openDriver(dir, partition, key)
        drivers[partition.id] = driver
        SchemaManager.prepare(driver, schema, dir.resolve(partition.file))
        return driver
    }

    /**
     * Runs [block] while every open database holds its write lock, so files copied inside are
     * consistent (database file plus write-ahead log). Readers continue; writers wait.
     */
    internal fun <T> withWritesPaused(block: () -> T): T {
        checkOpen()
        core // make sure the core database is open, so it is locked too
        val open = drivers.values.toList()
        fun lockFrom(i: Int): T {
            if (i == open.size) return block()
            val driver = open[i]
            var result: Result<T>? = null
            object : TransacterImpl(driver) {}.transaction {
                // Rewriting the schema version is a write, which takes the database's write lock.
                driver.execute(null, "PRAGMA user_version = ${SchemaManager.userVersion(driver)}", 0)
                result = runCatching { lockFrom(i + 1) }
            }
            return result!!.getOrThrow()
        }
        return lockFrom(0)
    }

    /**
     * EXP-01: every table of every database this user can open, for the full export. The core
     * database is named "core"; ledgers are named after their account group.
     */
    fun readableTables(): Map<String, List<TableDump>> {
        checkOpen()
        val result = LinkedHashMap<String, List<TableDump>>()
        core // opens the core database if needed
        result["core"] = TableDump.all(drivers.getValue(header.corePartition.id))
        for (group in core.coreQueries.groups().executeAsList()) {
            if (!canOpen(group.partition_id)) continue
            ledger(group.partition_id)
            result["ledger - ${group.name}"] = TableDump.all(drivers.getValue(group.partition_id))
        }
        return result
    }

    /** A copy of a partition key this user holds, to open a backup's copy of that database for checking. */
    internal fun partitionKeyForBackupCheck(partitionId: String): ByteArray? = partitionKeys[partitionId]?.copyOf()

    private fun activeUsers() = core.coreQueries.users().executeAsList().filter { it.active == 1L }

    /** Public keys come from the authenticated core database, never from the unencrypted header. */
    private fun publicKeyOf(id: String): ByteArray =
        core.coreQueries.userById(id).executeAsOneOrNull()?.public_key
            ?: throw IllegalArgumentException("Unknown user $id")

    private fun requireAdministrator() {
        if (role != Role.ADMINISTRATOR) throw AccessDeniedException("Only an administrator can do this")
    }

    private fun updateHeader(updated: HouseholdHeader) {
        store.writeHeader(dir, updated)
        header = updated
    }

    private fun checkOpen() = check(!closed) { "The household is locked" }
}
