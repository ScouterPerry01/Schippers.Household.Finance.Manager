package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.money.AmountExpression
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import java.util.Locale

@Composable
fun TextInput(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    secret: Boolean = false,
    error: String? = null,
    supporting: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        enabled = enabled,
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier,
    )
}

/**
 * Amount entry with the built-in calculator (MAN-03): typing "12,50 + 3" shows "= 15,50 $".
 * [parseAmount] turns the text into money, or null when it is empty.
 */
@Composable
fun AmountInput(label: String, value: String, currency: Currency, locale: Locale, modifier: Modifier = Modifier, format: (Money) -> String, enabled: Boolean = true, onChange: (String) -> Unit) {
    val result = runCatching { parseAmount(value, currency, locale) }
    val supporting = when {
        value.isBlank() -> null
        result.isFailure -> "?"
        AmountExpression.isExpression(value) -> "= ${result.getOrNull()?.let(format)}"
        else -> null
    }
    TextInput(label, value, modifier, error = if (result.isFailure) supporting else null, supporting = supporting, enabled = enabled, onChange = onChange)
}

fun parseAmount(text: String, currency: Currency, locale: Locale): Money? =
    if (text.isBlank()) null else AmountExpression.evaluate(text, currency, locale)

/** ISO date entry (2026-03-05, the Canadian standard in both languages); + and - change the day. */
@Composable
fun DateInput(label: String, value: String, modifier: Modifier = Modifier, hint: String? = null, enabled: Boolean = true, onChange: (String) -> Unit) {
    val valid = runCatching { LocalDate.parse(value.trim()) }.isSuccess
    OutlinedTextField(
        value = value,
        onValueChange = { text ->
            val current = runCatching { LocalDate.parse(value.trim()) }.getOrNull()
            when {
                current != null && text == "$value+" -> onChange(current.plus(DatePeriod(days = 1)).toString())
                current != null && text == "$value-" -> onChange(current.plus(DatePeriod(days = -1)).toString())
                else -> onChange(text)
            }
        },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = value.isNotBlank() && !valid,
        supportingText = hint?.let { { Text(it) } },
        modifier = modifier,
    )
}

/**
 * A searchable drop-down. Typing filters the options; [allowFreeText] keeps whatever was typed
 * (used for payees, where a new name creates a new payee).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Picker(
    label: String,
    options: List<T>,
    selected: T?,
    display: (T) -> String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    indent: (T) -> Int = { 0 },
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var filter by remember(selected) { mutableStateOf<String?>(null) }
    // A null option (e.g. "Everyone", "(none)") shows its own label when nothing is chosen.
    @Suppress("UNCHECKED_CAST")
    val text = filter ?: when {
        selected != null -> display(selected)
        options.any { it == null } -> display(null as T)
        else -> ""
    }
    val shown = if (filter.isNullOrBlank()) options else options.filter { display(it).contains(filter!!, ignoreCase = true) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { filter = it; expanded = true },
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false; filter = null }, modifier = Modifier.heightIn(max = 360.dp)) {
            for (option in shown.take(MAX_SHOWN)) {
                DropdownMenuItem(
                    text = { Text(display(option), modifier = Modifier.padding(start = (indent(option) * 16).dp)) },
                    onClick = {
                        onSelect(option)
                        filter = null
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Free text with suggestions: used for payees (MAN-02). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestInput(label: String, value: String, suggestions: List<String>, modifier: Modifier = Modifier, onChange: (String) -> Unit, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val shown = if (value.isBlank()) emptyList() else suggestions.filter { it.contains(value, ignoreCase = true) && !it.equals(value, ignoreCase = true) }.take(MAX_SHOWN)
    ExposedDropdownMenuBox(expanded = expanded && shown.isNotEmpty(), onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); expanded = true },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = expanded && shown.isNotEmpty(), onDismissRequest = { expanded = false }) {
            for (s in shown) {
                DropdownMenuItem(text = { Text(s) }, onClick = { onPick(s); expanded = false })
            }
        }
    }
}

@Composable
fun LabeledCheckbox(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Text(label)
    }
}

/** A dialog with a form body and Save / Cancel buttons. */
@Composable
fun FormDialog(title: String, saveLabel: String, cancelLabel: String, canSave: Boolean = true, onSave: () -> Unit, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.width(520.dp).onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onDismiss(); true } else false
                },
            ) { content() }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = canSave) { Text(saveLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelLabel) } },
    )
}

/**
 * A small "✕" button that removes a line or deletes a record. Screen readers announce [label]
 * (not "multiplication sign"), and the label also shows as a tooltip on hover.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RemoveButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    androidx.compose.foundation.TooltipArea(
        tooltip = {
            androidx.compose.material3.Surface(shape = MaterialTheme.shapes.small, tonalElevation = 4.dp, shadowElevation = 2.dp) {
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(6.dp))
            }
        },
        modifier = modifier,
    ) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = label },
        ) { Text("✕", modifier = Modifier.clearAndSetSemantics { }) }
    }
}

@Composable
fun ErrorText(text: String?) {
    if (text != null) Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

/**
 * The question asked before anything is deleted: [question] says what goes and what stays, with
 * any choice in [extra]. [onDelete] deletes and returns whether it worked; the question then closes.
 */
@Composable
fun AskBeforeDeleting(model: BooksModel, question: String, onDismiss: () -> Unit, extra: @Composable () -> Unit = {}, onDelete: () -> Boolean) {
    FormDialog(model.t("common.delete"), model.t("common.delete"), model.t("common.cancel"), onDismiss = onDismiss, onSave = { if (onDelete()) onDismiss() }) {
        Text(question)
        extra()
    }
}

private const val MAX_SHOWN = 200

/**
 * A dialog wider than Material's alert dialog (which stops at 560 dp), for tables such as search
 * results and report drill-downs.
 */
@Composable
fun WideDialog(title: String, closeLabel: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(Modifier.widthIn(max = 900.dp).padding(24.dp), shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                content()
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(closeLabel) }
                }
            }
        }
    }
}
