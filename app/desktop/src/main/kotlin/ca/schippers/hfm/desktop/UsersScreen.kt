package ca.schippers.hfm.desktop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.HouseholdUser
import ca.schippers.hfm.books.UserService
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.security.RecoveryKey
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class UsersTab { USERS, ACCESS, ACTIVITY }

/** HH-05 to HH-09, HH-13: sign-in accounts, who may open which group, and what each user did. */
@Composable
fun UsersScreen(model: BooksModel) {
    val admin = model.books.users.isAdministrator
    var tab by remember { mutableStateOf(UsersTab.USERS) }
    var editing by remember { mutableStateOf<HouseholdUser?>(null) }
    var adding by remember { mutableStateOf(false) }
    var newKey by remember { mutableStateOf<Pair<String, RecoveryKey>?>(null) }
    var changingPassword by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.t("nav.users"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { changingPassword = true }) { Text(model.t("users.changePassword")) }
            if (admin) Button(onClick = { adding = true }, modifier = Modifier.padding(start = 8.dp)) { Text(model.t("users.add")) }
        }
        PrimaryTabRow(selectedTabIndex = tab.ordinal, modifier = Modifier.padding(vertical = 8.dp)) {
            for (t in UsersTab.entries) Tab(selected = tab == t, onClick = { tab = t }, text = { Text(model.t("users.tab.${t.name}")) })
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                UsersTab.USERS -> UserList(model) { editing = it }
                UsersTab.ACCESS -> AccessMatrix(model)
                UsersTab.ACTIVITY -> Activity(model)
            }
        }
    }

    if (adding) AddUserDialog(model, onClose = { adding = false }) { name, key -> adding = false; newKey = name to key }
    editing?.let { EditUserDialog(model, it) { editing = null } }
    newKey?.let { (name, key) -> RecoveryKeyDialog(model, name, key) { newKey = null } }
    if (changingPassword) ChangePasswordDialog(model) { changingPassword = false }
}

@Composable
private fun UserList(model: BooksModel, onEdit: (HouseholdUser) -> Unit) {
    val users = remember(model.revision) { model.books.users.list() }
    val members = remember(model.revision) { model.books.members.list(includeArchived = true).associate { it.id to it.displayName } }
    LazyColumn {
        item { Text(model.t("users.explain"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp)) }
        items(users, key = { it.id }) { u ->
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(u.displayName + if (u.isMe) " (${model.t("users.you")})" else "", fontWeight = FontWeight.Medium)
                        Text(
                            listOfNotNull(
                                model.t("users.loginIs", u.loginName), model.t("role.${u.role}"),
                                u.memberId?.let { members[it] }?.let { model.t("users.isMember", it) },
                                if (!u.active) model.t("users.inactive") else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (model.books.users.isAdministrator || u.isMe) TextButton(onClick = { onEdit(u) }) { Text(model.t("common.edit")) }
                }
            }
        }
    }
}

/** HH-07, HH-08: a level per user and group; owners and administrators have theirs already. */
@Composable
private fun AccessMatrix(model: BooksModel) {
    val books = model.books
    val users = remember(model.revision) { books.users.list().filter { it.active } }
    val access = remember(model.revision) { books.users.access() }
    Column(Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
        Text(model.t("users.accessExplain"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(900.dp).padding(bottom = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.t("users.group"), Modifier.width(240.dp), fontWeight = FontWeight.Medium)
            for (u in users) Text(u.displayName, Modifier.width(190.dp), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        HorizontalDivider()
        for (a in access) {
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(240.dp)) {
                    Text(a.group.name)
                    Text(model.t(if (a.group.isPrivate) "users.privateGroup" else "users.sharedGroup"), style = MaterialTheme.typography.bodySmall)
                }
                for (u in users) {
                    val level = a.levels[u.id] ?: PermissionLevel.NONE
                    val implicit = a.ownerUserId == u.id || (a.ownerUserId == null && u.role == Role.ADMINISTRATOR)
                    val mayChange = !implicit && (books.users.isAdministrator || a.ownerUserId == model.session.userId)
                    Box(Modifier.width(190.dp).padding(end = 8.dp)) {
                        if (implicit) {
                            Text(model.t(if (a.ownerUserId == u.id) "users.owner" else "users.byRole"), style = MaterialTheme.typography.bodySmall)
                        } else {
                            Picker("", PermissionLevel.entries, level, { model.t("permission.$it") }, enabled = mayChange) { chosen ->
                                model.act { books.users.setAccess(a.group.id, u.id, chosen) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** HH-13. */
@Composable
private fun Activity(model: BooksModel) {
    val books = model.books
    val users = remember(model.revision) { books.users.list() }
    var who by remember { mutableStateOf<HouseholdUser?>(null) }
    val entries = remember(model.revision, who) { books.users.activity(who?.id) }
    val format = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
    Column {
        if (books.users.isAdministrator) {
            Picker(model.t("users.who"), listOf(null) + users, who, { it?.displayName ?: model.t("users.everyone") }, Modifier.width(320.dp)) { who = it }
        } else {
            Text(model.t("users.ownActivity"), style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(Modifier.padding(top = 8.dp)) {
            if (entries.isEmpty()) item { Text(model.t("users.noActivity")) }
            items(entries) { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(format.format(Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault())), Modifier.width(150.dp), style = MaterialTheme.typography.bodySmall)
                    Text(e.userName, Modifier.width(160.dp), style = MaterialTheme.typography.bodySmall)
                    Text((if (e.action in UserService.ACTIONS) model.t("action.${e.action}") else e.action) + " · " + (if (e.entity in UserService.ENTITIES) model.t("entity.${e.entity}") else e.entity), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(if (e.where == UserService.HOUSEHOLD) model.t("users.household") else e.where, Modifier.width(200.dp), style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
        }
    }
}

// --- Dialogs ---------------------------------------------------------------------------------------

@Composable
private fun AddUserDialog(model: BooksModel, onClose: () -> Unit, onAdded: (String, RecoveryKey) -> Unit) {
    val members = remember { model.books.members.list() }
    var login by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(Role.MEMBER) }
    var memberId by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    FormDialog(
        model.t("users.add"), model.t("common.save"), model.t("common.cancel"),
        canSave = login.isNotBlank() && name.isNotBlank() && password.isNotEmpty() && password == confirm, onDismiss = onClose,
        onSave = {
            model.act { model.books.users.add(login, name, role, password.toCharArray(), memberId) }?.let { onAdded(name, it.recoveryKey) }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("users.name"), name, Modifier.weight(1f)) { name = it }
            TextInput(model.t("unlock.login"), login, Modifier.weight(1f)) { login = it.trim() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker(model.t("users.role"), Role.entries, role, { model.t("role.$it") }, Modifier.weight(1f)) { role = it }
            Picker(model.t("users.member"), listOf(null) + members, members.firstOrNull { it.id == memberId }, { it?.displayName ?: model.t("common.none") }, Modifier.weight(1f)) { memberId = it?.id }
        }
        Text(model.t("role.${role}.explain"), style = MaterialTheme.typography.bodySmall)
        TextInput(model.t("users.password"), password, secret = true, supporting = model.t("users.passwordHint", UserService.MIN_PASSWORD)) { password = it }
        TextInput(model.t("users.passwordConfirm"), confirm, secret = true, error = if (confirm.isNotEmpty() && confirm != password) model.t("users.passwordMismatch") else null) { confirm = it }
    }
}

@Composable
private fun EditUserDialog(model: BooksModel, user: HouseholdUser, onClose: () -> Unit) {
    val books = model.books
    val members = remember { books.members.list() }
    var name by remember { mutableStateOf(user.displayName) }
    var memberId by remember { mutableStateOf(user.memberId) }
    var role by remember { mutableStateOf(user.role) }
    var active by remember { mutableStateOf(user.active) }
    FormDialog(model.t("users.edit"), model.t("common.save"), model.t("common.cancel"), canSave = name.isNotBlank(), onDismiss = onClose, onSave = {
        val ok = model.act {
            books.users.update(user.id, name, memberId)
            if (books.users.isAdministrator) {
                if (role != user.role) books.users.setRole(user.id, role)
                if (active != user.active) books.users.setActive(user.id, active)
            }
        }
        if (ok != null) onClose()
    }) {
        TextInput(model.t("users.name"), name) { name = it }
        if (books.users.isAdministrator) {
            Picker(model.t("users.member"), listOf(null) + members, members.firstOrNull { it.id == memberId }, { it?.displayName ?: model.t("common.none") }) { memberId = it?.id }
            Picker(model.t("users.role"), Role.entries, role, { model.t("role.$it") }, enabled = !user.isMe) { role = it }
            Text(model.t("role.${role}.explain"), style = MaterialTheme.typography.bodySmall)
            if (!user.isMe) LabeledCheckbox(model.t("users.activeField"), active) { active = it }
        }
    }
}

/** SEC-07: the new user's recovery key, shown once, to print or write down. */
@Composable
private fun RecoveryKeyDialog(model: BooksModel, name: String, key: RecoveryKey, onClose: () -> Unit) {
    val text = remember(key) { key.display() }
    WideDialog(model.t("users.recoveryTitle", name), model.t("users.recoverySaved"), onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(model.t("users.recoveryExplain", name))
            Card(Modifier.fillMaxWidth()) {
                SelectionContainer { Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(24.dp)) }
            }
            OutlinedButton(onClick = { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }) { Text(model.t("recovery.copy")) }
        }
    }
}

@Composable
private fun ChangePasswordDialog(model: BooksModel, onClose: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    FormDialog(model.t("users.changePassword"), model.t("common.save"), model.t("common.cancel"), canSave = current.isNotEmpty() && next.isNotEmpty() && next == confirm, onDismiss = onClose, onSave = {
        if (model.act { model.books.users.changePassword(current.toCharArray(), next.toCharArray()) } != null) onClose()
    }) {
        TextInput(model.t("users.currentPassword"), current, secret = true) { current = it }
        TextInput(model.t("users.password"), next, secret = true, supporting = model.t("users.passwordHint", UserService.MIN_PASSWORD)) { next = it }
        TextInput(model.t("users.passwordConfirm"), confirm, secret = true, error = if (confirm.isNotEmpty() && confirm != next) model.t("users.passwordMismatch") else null) { confirm = it }
        Text(model.t("users.passwordRecoveryNote"), style = MaterialTheme.typography.bodySmall)
    }
}
