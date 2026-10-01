package ca.schippers.hfm.domain

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdsTest {

    @Test
    fun `ids are version 7, unique and time ordered`() {
        val ids = List(10_000) { Ids.newUuid() }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.version() == 7 && it.variant() == 2 })
        assertEquals(ids.map { it.toString() }, ids.map { it.toString() }.sorted())
    }

    @Test
    fun `ids stay ordered when the clock steps back`() {
        val a = Ids.newUuid(nowMillis = 2_000_000_000_000)
        val b = Ids.newUuid(nowMillis = 1_000_000_000_000)
        assertTrue(a.toString() < b.toString())
    }

    @Test
    fun `validation`() {
        assertTrue(Ids.isValid(Ids.newId()))
        assertFalse(Ids.isValid("not-an-id"))
        assertTrue(Ids.isValid(UUID.randomUUID().toString()))
    }
}

class AccessPolicyTest {

    private val admin = "admin"
    private val spouse = "spouse"
    private val teen = "teen"

    private val shared = AccountGroupAccess("shared", ownerUserId = null, grants = mapOf(spouse to PermissionLevel.EDIT, teen to PermissionLevel.VIEW))
    private val spousePrivate = AccountGroupAccess("spouse-private", ownerUserId = spouse, grants = emptyMap())

    @Test
    fun `administrator edits shared groups`() {
        assertEquals(PermissionLevel.EDIT, AccessPolicy.levelFor(admin, Role.ADMINISTRATOR, shared))
    }

    @Test
    fun `members get what they are granted`() {
        assertEquals(PermissionLevel.EDIT, AccessPolicy.levelFor(spouse, Role.MEMBER, shared))
        assertEquals(PermissionLevel.VIEW, AccessPolicy.levelFor(teen, Role.MEMBER, shared))
        assertEquals(PermissionLevel.NONE, AccessPolicy.levelFor("stranger", Role.MEMBER, shared))
    }

    @Test
    fun `private groups are private even from the administrator`() {
        assertEquals(PermissionLevel.EDIT, AccessPolicy.levelFor(spouse, Role.MEMBER, spousePrivate))
        assertEquals(PermissionLevel.NONE, AccessPolicy.levelFor(admin, Role.ADMINISTRATOR, spousePrivate))
        val sharedWithAdmin = spousePrivate.copy(grants = mapOf(admin to PermissionLevel.VIEW))
        assertEquals(PermissionLevel.VIEW, AccessPolicy.levelFor(admin, Role.ADMINISTRATOR, sharedWithAdmin))
    }

    @Test
    fun `viewers never edit`() {
        val group = shared.copy(grants = mapOf(teen to PermissionLevel.EDIT))
        assertEquals(PermissionLevel.VIEW, AccessPolicy.levelFor(teen, Role.VIEWER, group))
        assertFalse(AccessPolicy.can(teen, Role.VIEWER, group, PermissionLevel.CAPTURE_ONLY))
    }
}
