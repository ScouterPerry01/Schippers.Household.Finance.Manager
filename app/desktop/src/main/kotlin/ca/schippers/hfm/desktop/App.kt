package ca.schippers.hfm.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.data.Backups
import ca.schippers.hfm.data.WrongPasswordException
import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.security.RecoveryKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser

@Composable
fun App(state: AppState) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TopBar(state)
                when (val screen = state.screen) {
                    is Screen.Main -> MainScreen(screen.model)
                    else -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            when (screen) {
                                Screen.Welcome -> WelcomeScreen(state)
                                Screen.Create -> CreateScreen(state)
                                is Screen.Unlock -> UnlockScreen(state, screen.dir)
                                is Screen.Reset -> ResetScreen(state, screen.dir)
                                is Screen.ShowRecoveryKey -> RecoveryKeyScreen(state, screen)
                                is Screen.Main -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(state: AppState) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(state.t("app.name"), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        for (language in Language.entries) {
            TextButton(onClick = { state.switchLanguage(language) }, enabled = state.language != language) {
                Text(if (language == Language.ENGLISH) "English" else "Français")
            }
        }
        if (state.screen is Screen.Main) {
            OutlinedButton(onClick = state::lock) { Text(state.t("common.lock")) }
        }
    }
}

@Composable
private fun WelcomeScreen(state: AppState) {
    Text(state.t("welcome.title"), style = MaterialTheme.typography.headlineMedium)
    Text(state.t("app.tagline"))
    Button(onClick = { state.screen = Screen.Create }, modifier = Modifier.fillMaxWidth()) { Text(state.t("welcome.create")) }
    OutlinedButton(
        onClick = { chooseFolder(state.t("welcome.open"))?.let { state.screen = Screen.Unlock(it) } },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(state.t("welcome.open")) }
    RestoreButton(state)
    val recent = state.recentHouseholds
    if (recent.isNotEmpty()) {
        Text(state.t("welcome.recent"), style = MaterialTheme.typography.titleSmall)
        for (dir in recent) {
            TextButton(onClick = { state.screen = Screen.Unlock(dir) }) { Text(dir.toString()) }
        }
    }
}

/**
 * BAK-03 / BAK-05: restores a backup into a new folder (never over an existing household), for
 * recovery or for moving to a new computer, then asks for the password as usual.
 */
@Composable
private fun RestoreButton(state: AppState) {
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
        val chooser = JFileChooser().apply {
            dialogTitle = state.t("welcome.restore")
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter(state.t("welcome.restore.fileType"), Backups.EXTENSION)
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@OutlinedButton
        val backup = chooser.selectedFile.toPath()
        val parent = chooseFolder(state.t("welcome.restore.where")) ?: return@OutlinedButton
        val base = backup.fileName.toString().substringBefore("-20").ifBlank { "Household" }
        var target = parent.resolve("$base.hfm")
        var n = 2
        while (Files.exists(target)) target = parent.resolve("$base ($n).hfm").also { n++ }
        busy = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { Backups.restore(backup, target) } }
            busy = false
            result.onSuccess { dir -> state.remember(dir); state.screen = Screen.Unlock(dir) }
                .onFailure { error = state.t("error.generic", it.message ?: it.javaClass.simpleName) }
        }
    }) { Text(state.t(if (busy) "welcome.restoring" else "welcome.restore")) }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun CreateScreen(state: AppState) {
    var parent by remember { mutableStateOf<Path?>(null) }
    var name by remember { mutableStateOf("") }
    var adminName by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Text(state.t("create.title"), style = MaterialTheme.typography.headlineMedium)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { chooseFolder(state.t("create.folder"))?.let { parent = it } }) { Text(state.t("create.folder.choose")) }
        Text(parent?.toString() ?: "", style = MaterialTheme.typography.bodySmall)
    }
    Field(state.t("create.name"), name) { name = it }
    Field(state.t("create.adminName"), adminName) { adminName = it }
    Field(state.t("create.login"), login) { login = it }
    Field(state.t("create.password"), password, secret = true) { password = it }
    Field(state.t("create.password.confirm"), confirm, secret = true) { confirm = it }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { state.screen = Screen.Welcome }, enabled = !busy) { Text(state.t("common.back")) }
        Button(
            enabled = !busy && parent != null && name.isNotBlank() && adminName.isNotBlank() && login.isNotBlank(),
            onClick = {
                error = when {
                    password.length < AppState.MIN_PASSWORD_LENGTH -> state.t("create.password.tooShort", AppState.MIN_PASSWORD_LENGTH)
                    password != confirm -> state.t("create.password.mismatch")
                    else -> null
                }
                if (error != null) return@Button
                busy = true
                scope.launch {
                    try {
                        val dir = parent!!.resolve("${name.trim()}.hfm")
                        val created = withContext(Dispatchers.IO) {
                            state.store.create(dir, name, login, adminName, password.toCharArray(), locale = state.language.locale.toLanguageTag())
                        }
                        state.remember(dir)
                        state.screen = Screen.ShowRecoveryKey(created.session, created.recoveryKey)
                    } catch (e: Exception) {
                        error = state.t("error.generic", e.message ?: e.javaClass.simpleName)
                    } finally {
                        busy = false
                    }
                }
            },
        ) { Text(state.t("create.submit")) }
        if (busy) CircularProgressIndicator(Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun RecoveryKeyScreen(state: AppState, screen: Screen.ShowRecoveryKey) {
    val text = remember(screen) { screen.key.display() }
    Text(state.t("recovery.title"), style = MaterialTheme.typography.headlineMedium)
    Text(state.t("recovery.explain"))
    Card(Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(24.dp))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }) {
            Text(state.t("recovery.copy"))
        }
        Button(onClick = { state.opened(screen.session) }) { Text(state.t("recovery.confirm")) }
    }
}

@Composable
private fun UnlockScreen(state: AppState, dir: Path) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Text(state.t("unlock.title"), style = MaterialTheme.typography.headlineMedium)
    Text(dir.toString(), style = MaterialTheme.typography.bodySmall)
    Field(state.t("unlock.login"), login) { login = it }
    Field(state.t("unlock.password"), password, secret = true) { password = it }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { state.screen = Screen.Welcome }, enabled = !busy) { Text(state.t("common.back")) }
        Button(enabled = !busy && login.isNotBlank() && password.isNotEmpty(), onClick = {
            busy = true
            error = null
            scope.launch {
                try {
                    val session = withContext(Dispatchers.IO) { state.store.unlock(dir, login, password.toCharArray()) }
                    password = ""
                    state.opened(session)
                } catch (_: WrongPasswordException) {
                    error = state.t("unlock.wrong")
                } catch (e: Exception) {
                    error = state.t("error.generic", e.message ?: e.javaClass.simpleName)
                } finally {
                    busy = false
                }
            }
        }) { Text(state.t("unlock.submit")) }
        if (busy) CircularProgressIndicator(Modifier.padding(start = 8.dp))
    }
    TextButton(onClick = { state.screen = Screen.Reset(dir) }) { Text(state.t("unlock.forgot")) }
}

@Composable
private fun ResetScreen(state: AppState, dir: Path) {
    var login by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Text(state.t("reset.title"), style = MaterialTheme.typography.headlineMedium)
    Field(state.t("unlock.login"), login) { login = it }
    Field(state.t("reset.recoveryKey"), key) { key = it }
    Field(state.t("reset.newPassword"), password, secret = true) { password = it }
    Field(state.t("create.password.confirm"), confirm, secret = true) { confirm = it }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { state.screen = Screen.Unlock(dir) }, enabled = !busy) { Text(state.t("common.back")) }
        Button(enabled = !busy && login.isNotBlank() && key.isNotBlank(), onClick = {
            val parsed = runCatching { RecoveryKey.parse(key) }.getOrNull()
            error = when {
                parsed == null -> state.t("reset.invalidKey")
                password.length < AppState.MIN_PASSWORD_LENGTH -> state.t("create.password.tooShort", AppState.MIN_PASSWORD_LENGTH)
                password != confirm -> state.t("create.password.mismatch")
                else -> null
            }
            if (error != null || parsed == null) return@Button
            busy = true
            scope.launch {
                try {
                    val session = withContext(Dispatchers.IO) { state.store.resetPassword(dir, login, parsed, password.toCharArray()) }
                    state.opened(session)
                } catch (_: WrongPasswordException) {
                    error = state.t("reset.invalidKey")
                } catch (e: Exception) {
                    error = state.t("error.generic", e.message ?: e.javaClass.simpleName)
                } finally {
                    busy = false
                }
            }
        }) { Text(state.t("reset.submit")) }
    }
}

@Composable
private fun Field(label: String, value: String, secret: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun chooseFolder(title: String): Path? {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.toPath().takeIf { Files.isDirectory(it) }
    } else {
        null
    }
}
