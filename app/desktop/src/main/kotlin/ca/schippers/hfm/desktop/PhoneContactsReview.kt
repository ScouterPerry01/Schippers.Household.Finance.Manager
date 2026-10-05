package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Contact
import ca.schippers.hfm.books.ContactKind
import ca.schippers.hfm.books.ReceivedContact
import ca.schippers.hfm.sync.RefContactDetail
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * CON-07: the contacts made on a paired phone, waiting on the Contacts screen. Each is added as a
 * new contact (the contact form opens filled in, with Store in), added to a contact it seems to
 * duplicate, or discarded. [onShow] selects a contact once it is added or completed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhoneContactsDialog(model: BooksModel, onShow: (String) -> Unit, onClose: () -> Unit) {
    val books = model.books
    val waiting = remember(model.revision) { runCatching { books.phoneContacts.waiting() }.getOrDefault(emptyList()) }
    val duplicates = remember(waiting) { waiting.associate { w -> w.id to runCatching { books.phoneContacts.duplicates(w) }.getOrDefault(emptyList()) } }
    var adding by remember { mutableStateOf<Pair<ReceivedContact, Contact>?>(null) }
    var discarding by remember { mutableStateOf<ReceivedContact?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    WideDialog(model.t("contacts.fromPhoneTitle"), model.t("common.close"), onClose) {
        Column(Modifier.width(760.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(model.t("contacts.fromPhoneExplain"), style = MaterialTheme.typography.bodyMedium)
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (waiting.isEmpty()) Text(model.t("contacts.fromPhoneNone"))
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (w in waiting) {
                    val c = w.contact
                    val same = duplicates[w.id].orEmpty()
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium)
                            c.purpose?.let { Text(model.t("contacts.whatForIs", it), color = MaterialTheme.colorScheme.primary) }
                            val who = listOfNotNull(
                                model.t(if (c.person) "contacts.person" else "contacts.organization"),
                                c.organizationName?.takeIf { c.person },
                                c.kinds.filter { k -> ContactKind.entries.any { it.name == k } }.map { model.t("contactKind.$it") }.joinToString(", ").ifEmpty { null },
                            )
                            Text(who.joinToString(" · "))
                            for (p in c.phones) Detail(model, "detailType.PHONE", p)
                            for (e in c.emails) Detail(model, "detailType.EMAIL", e)
                            c.address?.let { Text(model.t("contacts.labelled", model.t("contacts.address")) + " " + it) }
                            c.notes?.let { Text(model.t("contacts.labelled", model.t("calendar.notes")) + " " + it) }
                            Text(
                                model.t("contacts.fromPhoneReceived", received(model, w.receivedAt), w.deviceName ?: model.t("contacts.fromPhoneDevice")),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (same.isNotEmpty()) {
                                Text(model.t("contacts.fromPhoneSame"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 4.dp))
                                for (e in same) {
                                    TextButton(onClick = {
                                        model.act { books.phoneContacts.merge(w.id, e.id) }?.let { merged ->
                                            message = model.t("contacts.fromPhoneMerged", merged.label)
                                            onShow(merged.id)
                                        }
                                    }) { Text(model.t("contacts.fromPhoneMerge", e.label)) }
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    // Store in: where it waits when the user may add there, else where contacts usually go.
                                    val group = model.editableGroups().firstOrNull { it.id == w.groupId } ?: model.defaultGroupForContacts()
                                    if (group == null) model.error = model.t("error.noEditableGroup")
                                    else model.act { books.phoneContacts.draft(w, group.id) }?.let { adding = w to it }
                                }) { Text(model.t("contacts.fromPhoneAdd")) }
                                TextButton(onClick = { discarding = w }) { Text(model.t("contacts.fromPhoneDiscard"), color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }

    adding?.let { (w, draft) ->
        ContactDialog(model, draft, save = { books.phoneContacts.add(w.id, it) }) { saved ->
            adding = null
            saved?.let { onShow(it.id) }
        }
    }
    discarding?.let { w ->
        FormDialog(
            model.t("contacts.fromPhoneDiscard"), model.t("contacts.fromPhoneDiscard"), model.t("common.cancel"),
            onDismiss = { discarding = null }, onSave = { if (model.act { books.phoneContacts.discard(w.id) } != null) discarding = null },
        ) { Text(model.t("contacts.fromPhoneDiscardQuestion", w.contact.name)) }
    }
}

@Composable
private fun Detail(model: BooksModel, typeKey: String, d: RefContactDetail) {
    Text(model.t("contacts.labelled", d.label ?: model.t(typeKey)) + " " + d.value, fontWeight = FontWeight.Normal)
}

private fun received(model: BooksModel, millis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(model.language.locale)
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
