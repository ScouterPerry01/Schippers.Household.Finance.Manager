package ca.schippers.hfm.data.jdbc

import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.WrongPasswordException
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.security.DecryptionException
import ca.schippers.hfm.security.KdfParams
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** HH-05, HH-06, HH-08, HH-11: users, roles, and what each can open. */
class UserManagementTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private val dir get() = temp.resolve("U.hfm")

    private fun setUp(): Pair<String, String> {
        val created = store.create(dir, "U", "perry", "Perry", "pw".toCharArray())
        val shared = created.session.core.coreQueries.groups().executeAsList().single().partition_id
        val marie = created.session.addUser("marie", "Marie", Role.MEMBER, "mpw".toCharArray()).userId
        created.session.close()
        return shared to marie
    }

    @Test
    fun `a member sees shared groups only when granted, an administrator sees them all`() {
        val (shared, marie) = setUp()
        store.unlock(dir, "marie", "mpw".toCharArray()).use { assertFalse(it.canOpen(shared)) }

        store.unlock(dir, "perry", "pw".toCharArray()).use { it.setRole(marie, Role.ADMINISTRATOR) }
        store.unlock(dir, "marie", "mpw".toCharArray()).use { assertTrue(it.canOpen(shared), "administrators hold every shared key") }

        store.unlock(dir, "perry", "pw".toCharArray()).use { it.setRole(marie, Role.MEMBER) }
        store.unlock(dir, "marie", "mpw".toCharArray()).use { assertFalse(it.canOpen(shared), "the key goes when the role does") }

        store.unlock(dir, "perry", "pw".toCharArray()).use { perry ->
            val groupId = perry.core.coreQueries.groups().executeAsList().single().id
            perry.setPermission(groupId, marie, PermissionLevel.VIEW)
            perry.setRole(marie, Role.ADMINISTRATOR)
            perry.setRole(marie, Role.MEMBER)
        }
        store.unlock(dir, "marie", "mpw".toCharArray()).use { assertTrue(it.canOpen(shared), "an explicit permission survives a role change") }
    }

    @Test
    fun `there is always an administrator, and members cannot manage users`() {
        val (_, marie) = setUp()
        store.unlock(dir, "perry", "pw".toCharArray()).use { perry ->
            assertFailsWith<IllegalStateException> { perry.setRole(perry.userId, Role.MEMBER) }
            assertFailsWith<IllegalArgumentException> { perry.setActive(perry.userId, false) }
        }
        store.unlock(dir, "marie", "mpw".toCharArray()).use { m ->
            assertFailsWith<AccessDeniedException> { m.setRole(marie, Role.ADMINISTRATOR) }
        }
    }

    @Test
    fun `a deactivated user cannot sign in, and passwords can be changed`() {
        val (_, marie) = setUp()
        store.unlock(dir, "perry", "pw".toCharArray()).use { it.setActive(marie, false) }
        assertFailsWith<AccessDeniedException> { store.unlock(dir, "marie", "mpw".toCharArray()) }
        store.unlock(dir, "perry", "pw".toCharArray()).use { it.setActive(marie, true) }

        store.unlock(dir, "marie", "mpw".toCharArray()).use { m ->
            assertFailsWith<WrongPasswordException> { m.changePassword("wrong".toCharArray(), "new".toCharArray()) }
            m.changePassword("mpw".toCharArray(), "nouveau".toCharArray())
        }
        assertFailsWith<WrongPasswordException> { store.unlock(dir, "marie", "mpw".toCharArray()) }
        store.unlock(dir, "marie", "nouveau".toCharArray()).close()
    }

    @Test
    fun `data sealed for a user opens only for that user`() {
        val (_, marie) = setUp()
        val secret = "pair key".encodeToByteArray()
        val sealed = store.unlock(dir, "perry", "pw".toCharArray()).use { it.sealFor(marie, secret, "device:pixel") }
        store.unlock(dir, "marie", "mpw".toCharArray()).use { assertContentEquals(secret, it.openSealed(sealed, "device:pixel")) }
        store.unlock(dir, "perry", "pw".toCharArray()).use { assertFailsWith<DecryptionException> { it.openSealed(sealed, "device:pixel") } }
        store.unlock(dir, "marie", "mpw".toCharArray()).use { assertFailsWith<DecryptionException>("bound to its context") { it.openSealed(sealed, "device:other") } }
    }
}
