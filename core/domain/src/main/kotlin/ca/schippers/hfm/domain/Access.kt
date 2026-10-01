package ca.schippers.hfm.domain

/** User roles (HH-06). */
enum class Role {
    /** Everything, including users and settings. */
    ADMINISTRATOR,

    /** Access as granted per account group or account. */
    MEMBER,

    /** Read-only access as granted. */
    VIEWER,
}

/** Permission levels per account group or account (HH-08), in increasing order of access. */
enum class PermissionLevel {
    NONE,
    VIEW,
    CAPTURE_ONLY,
    EDIT,
    ;

    fun allows(required: PermissionLevel): Boolean = this >= required
}

data class AccountGroupAccess(
    val groupId: String,
    /** The owner of a private group (HH-09); null for shared groups. */
    val ownerUserId: String?,
    /** Explicit grants by user id (HH-07). */
    val grants: Map<String, PermissionLevel>,
)

/**
 * Decides what a signed-in user may do with an account group (HH-06 to HH-10).
 *
 * This is the application-level check. Private groups are additionally protected by encryption:
 * a user without a grant does not hold the group's key at all (HH-11).
 */
object AccessPolicy {

    fun levelFor(userId: String, role: Role, group: AccountGroupAccess): PermissionLevel {
        val granted = when {
            group.ownerUserId == userId -> PermissionLevel.EDIT
            // Administrators manage every shared group, but private groups only when granted.
            group.ownerUserId == null && role == Role.ADMINISTRATOR -> PermissionLevel.EDIT
            else -> group.grants[userId] ?: PermissionLevel.NONE
        }
        // Viewers never edit, whatever they were granted.
        return if (role == Role.VIEWER && granted > PermissionLevel.VIEW) PermissionLevel.VIEW else granted
    }

    fun can(userId: String, role: Role, group: AccountGroupAccess, required: PermissionLevel): Boolean =
        levelFor(userId, role, group).allows(required)
}
