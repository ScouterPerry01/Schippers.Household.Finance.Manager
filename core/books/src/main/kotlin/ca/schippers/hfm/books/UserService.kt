package ca.schippers.hfm.books

import ca.schippers.hfm.calc.rules.PasswordRules
import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.AddedUser
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.Role

/** A sign-in account (HH-05) as the Users screen shows it. */
data class HouseholdUser(
    val id: String,
    val loginName: String,
    val displayName: String,
    val role: Role,
    val memberId: String?,
    val active: Boolean,
    val isMe: Boolean,
)

/** One user's access to one group (HH-07, HH-08): owners and administrators have it without asking. */
data class GroupAccess(val group: GroupInfo, val ownerUserId: String?, val levels: Map<String, PermissionLevel>)

/** HH-13: something a user did. [where] is "household" or the account group's name. */
data class ActivityEntry(val at: Long, val userId: String?, val userName: String, val action: String, val entity: String, val details: String?, val where: String)

/**
 * Users, roles and access (HH-05 to HH-09, HH-13). The keys behind every rule live in
 * [ca.schippers.hfm.data.HouseholdSession]; this service adds validation, names and the
 * activity log.
 */
class UserService internal constructor(private val books: Books) {

    private val session get() = books.session

    val isAdministrator: Boolean get() = books.role == Role.ADMINISTRATOR

    fun list(): List<HouseholdUser> = books.core.users().executeAsList().map {
        HouseholdUser(it.id, it.login_name, it.display_name, Role.valueOf(it.role), it.member_id, it.active == 1L, it.id == books.userId)
    }

    fun add(loginName: String, displayName: String, role: Role, password: CharArray, memberId: String? = null): AddedUser {
        validate(loginName.isNotBlank() && loginName.trim().none(Char::isWhitespace), "error.loginName")
        validate(displayName.isNotBlank(), "error.nameRequired")
        Passwords.check(passwordRules(), password, loginName.trim())
        validate(list().none { it.loginName.equals(loginName.trim(), ignoreCase = true) }, "error.loginTaken")
        val added = session.addUser(loginName.trim(), displayName.trim(), role, password)
        if (memberId != null) session.linkMember(added.userId, memberId)
        return added
    }

    fun setRole(userId: String, role: Role) = guard { session.setRole(userId, role) }

    fun setActive(userId: String, active: Boolean) = guard { session.setActive(userId, active) }

    fun update(userId: String, displayName: String, memberId: String?) {
        validate(displayName.isNotBlank(), "error.nameRequired")
        session.renameUser(userId, displayName)
        if (isAdministrator) session.linkMember(userId, memberId)
    }

    /** M-71: a new login name for [userId]: the user's own, or anyone's for an administrator. */
    fun changeLoginName(userId: String, loginName: String) {
        validate(loginName.isNotBlank() && loginName.trim().none(Char::isWhitespace), "error.loginName")
        validate(list().none { it.id != userId && it.loginName.equals(loginName.trim(), ignoreCase = true) }, "error.loginTaken")
        if (list().first { it.id == userId }.loginName == loginName.trim()) return
        guard { session.changeLoginName(userId, loginName) }
    }

    fun changePassword(current: CharArray, newPassword: CharArray) {
        Passwords.check(passwordRules(), newPassword, list().firstOrNull { it.isMe }?.loginName)
        guard { session.changePassword(current, newPassword) }
    }

    /** The household's password rules in effect today (Rates and rules, security.password.*). */
    fun passwordRules(): PasswordRules = PasswordRules.on(books.today())

    /**
     * An administrator sets the household's password rules, in effect from today for every
     * password chosen from now on; passwords already set keep working. Only what changed is added.
     */
    fun setPasswordRules(rules: PasswordRules) {
        requireAdmin(books)
        validate(rules.minLength in PasswordRules.MIN..PasswordRules.MAX, "error.passwordRuleLength", PasswordRules.MIN, PasswordRules.MAX)
        val current = passwordRules()
        val today = books.today()
        fun set(key: String, now: Any, was: Any) {
            if (now != was) books.rateRules.add("security.password.$key", null, today, now.toString())
        }
        set("minLength", rules.minLength, current.minLength)
        set("capitals", rules.capitals, current.capitals)
        set("smallLetters", rules.smallLetters, current.smallLetters)
        set("digits", rules.digits, current.digits)
        set("symbols", rules.symbols, current.symbols)
        set("notLoginName", rules.notLoginName, current.notLoginName)
    }

    /** HH-07, HH-08: every group the signed-in user can see, with each user's level. */
    fun access(): List<GroupAccess> {
        val users = list()
        return books.groups().map { g ->
            val explicit = books.core.permissionsForGroup(g.id).executeAsList().associate { it.user_id to PermissionLevel.valueOf(it.level) }
            val levels = users.associate { u ->
                val level = when {
                    g.ownerUserId == u.id -> PermissionLevel.EDIT
                    g.ownerUserId == null && u.role == Role.ADMINISTRATOR -> PermissionLevel.EDIT
                    else -> explicit[u.id] ?: PermissionLevel.NONE
                }
                // M-78: a viewer's access stops at View, whatever was granted before.
                u.id to if (u.role == Role.VIEWER) minOf(level, PermissionLevel.VIEW) else level
            }
            GroupAccess(g, g.ownerUserId, levels)
        }
    }

    /**
     * Gives [userId] a level on a group; only the owner of a private group or an administrator may.
     * M-78: a viewer can be given View at most.
     */
    fun setAccess(groupId: String, userId: String, level: PermissionLevel) {
        val target = list().firstOrNull { it.id == userId }
        validate(target?.role != Role.VIEWER || level <= PermissionLevel.VIEW, "error.viewerViewOnly")
        guard { session.setPermission(groupId, userId, level) }
    }

    /**
     * HH-13: the activity log. Administrators see everyone's; others see their own. Changes to
     * transactions come from the ledgers the signed-in user can open.
     */
    fun activity(userId: String? = null, limit: Int = 300): List<ActivityEntry> {
        val who = if (isAdministrator) userId else books.userId
        val names = list().associate { it.id to it.displayName }
        val household = books.core.auditFor(who, limit.toLong()).executeAsList().map {
            ActivityEntry(it.at, it.user_id, names[it.user_id].orEmpty(), it.action, it.entity, it.details, HOUSEHOLD)
        }
        val ledgers = books.groups().flatMap { g ->
            books.ledger(g).ledgerQueries.recentChanges(who, limit.toLong()).executeAsList().map {
                ActivityEntry(it.at, it.user_id, names[it.user_id].orEmpty(), it.action, it.entity, null, g.name)
            }
        }
        return (household + ledgers).sortedByDescending { it.at }.take(limit)
    }

    /** Session refusals become messages the screens can show. */
    private fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: IllegalStateException) {
        throw ValidationException("error.lastAdministrator").apply { initCause(e) }
    } catch (e: IllegalArgumentException) {
        throw ValidationException("error.notYourself").apply { initCause(e) }
    } catch (e: ca.schippers.hfm.data.WrongPasswordException) {
        throw ValidationException("error.wrongPassword").apply { initCause(e) }
    } catch (e: AccessDeniedException) {
        throw e
    }

    companion object {
        const val HOUSEHOLD = "household"
        /** The actions recorded in the activity log, each with a name in both languages. */
        val ACTIONS = listOf(
            "ACTIVATE", "BACKUP", "BACKUP_SETTINGS", "CHANGE_PASSWORD", "CREATE", "DEACTIVATE", "DELETE", "DELETE_BUDGET", "EXPORT", "IMPORT", "LINK",
            "MATCH", "PAID", "PAIR", "RECONCILE", "RESET_PASSWORD", "REVEAL", "REVOKE", "SET_BUDGET", "SET_PERMISSION", "SET_RATE", "SET_ROLE",
            "UNDO_RECONCILE", "UPDATE",
        )

        /** What the actions apply to, each with a name in both languages. */
        val ENTITIES = listOf(
            "account", "account_group", "app_user", "bill", "category", "category_rule", "contact", "credit_card", "device", "fx_rate", "household",
            "institution", "member", "pet", "qif", "statement", "txn",
        )
    }
}
