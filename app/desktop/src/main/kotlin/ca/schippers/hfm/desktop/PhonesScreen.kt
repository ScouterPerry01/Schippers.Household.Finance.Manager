package ca.schippers.hfm.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.PairedDevice
import ca.schippers.hfm.sync.PairingInvitation
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.delay
import java.awt.image.BufferedImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** SYNC-03, SYNC-07, SYNC-08, SYNC-09: pairing phones and managing them. */
@Composable
fun PhonesScreen(model: BooksModel) {
    val books = model.books
    val devices = remember(model.revision) { books.sync.devices() }
    val groups = remember(model.revision) { books.groups().associate { it.id to it.name } }
    var pairing by remember { mutableStateOf<PairingInvitation?>(null) }
    var editing by remember { mutableStateOf<PairedDevice?>(null) }
    val address = remember { SyncServer.localAddress() }
    val server = model.syncServer

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.t("nav.phones"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(enabled = server.running && address != null, onClick = {
                pairing = books.sync.invitation(model.desktopName(), address!!, server.port, System.currentTimeMillis())
            }) { Text(model.t("phones.pair")) }
        }
        Text(model.t("phones.explain"), style = MaterialTheme.typography.bodySmall)
        Text(
            when {
                !server.running -> model.t("phones.notListening", model.syncError.orEmpty())
                address == null -> model.t("phones.noNetwork")
                else -> model.t("phones.listening", "$address:${server.port}")
            },
            color = if (server.running && address != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        LazyColumn(Modifier.padding(top = 8.dp)) {
            if (devices.isEmpty()) item { Text(model.t("phones.none"), Modifier.padding(8.dp)) }
            items(devices, key = { it.id }) { d ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name + if (d.revoked) " (${model.t("phones.removed")})" else "", fontWeight = FontWeight.Medium)
                            Text(
                                listOfNotNull(
                                    model.t("phones.pairedOn", dateTime(d.pairedAt)),
                                    d.lastSeen?.let { model.t("phones.lastSeen", dateTime(it)) } ?: model.t("phones.neverSynced"),
                                    model.t("phones.items", d.itemsReceived),
                                    d.groupId?.let { groups[it] }?.let { model.t("phones.storesIn", it) },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (d.revoked) {
                            TextButton(onClick = { model.act { books.sync.forget(d.id) } }) { Text(model.t("phones.forget")) }
                        } else {
                            TextButton(onClick = { editing = d }) { Text(model.t("common.edit")) }
                            OutlinedButton(onClick = { model.act { books.sync.revoke(d.id, System.currentTimeMillis()) } }) {
                                Text(model.t("phones.remove"), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    pairing?.let { invitation -> PairingDialog(model, invitation, devices.size) { pairing = null } }
    editing?.let { d -> DeviceDialog(model, d) { editing = null } }
}

private fun dateTime(millis: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** The QR code for the phone to scan; closes by itself once the phone has paired. */
@Composable
private fun PairingDialog(model: BooksModel, invitation: PairingInvitation, devicesBefore: Int, onClose: () -> Unit) {
    val qr = remember(invitation) { qrImage(invitation.toQrText(), 320) }
    var secondsLeft by remember(invitation) { mutableStateOf((ca.schippers.hfm.books.SyncService.INVITATION_MILLIS / 1000).toInt()) }
    val paired = remember(model.revision) { model.books.sync.devices().size > devicesBefore }
    LaunchedEffect(invitation) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
            if (secondsLeft % 3 == 0) model.changed()
        }
    }
    WideDialog(model.t("phones.pair"), model.t("common.close"), onClose) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(qr, model.t("phones.pair"), Modifier.size(320.dp))
            Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (paired) {
                    Text(model.t("phones.paired"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(model.t("phones.scanSteps"))
                    Text(model.t("phones.sameWifi"), style = MaterialTheme.typography.bodySmall)
                    Text(model.t("phones.firewall"), style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (secondsLeft > 0) model.t("phones.expiresIn", secondsLeft / 60, "%02d".format(secondsLeft % 60)) else model.t("phones.expired"),
                        color = if (secondsLeft > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                    )
                    Text("${invitation.host}:${invitation.port}", style = MaterialTheme.typography.bodySmall)
                    // For a phone whose camera cannot read the screen: the same link as text, to paste on the phone.
                    TextButton(onClick = {
                        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(invitation.toQrText()), null)
                    }) { Text(model.t("phones.copyText")) }
                }
            }
        }
    }
}

@Composable
private fun DeviceDialog(model: BooksModel, device: PairedDevice, onClose: () -> Unit) {
    var name by remember { mutableStateOf(device.name) }
    var groupId by remember { mutableStateOf(device.groupId) }
    FormDialog(model.t("phones.editDevice"), model.t("common.save"), model.t("common.cancel"), canSave = name.isNotBlank(), onDismiss = onClose, onSave = {
        if (model.act { model.books.sync.update(device.id, name, groupId) } != null) onClose()
    }) {
        TextInput(model.t("phones.deviceName"), name) { name = it }
        GroupPicker(model, groupId, enabled = true) { groupId = it.id }
        Text(model.t("phones.groupHint"), style = MaterialTheme.typography.bodySmall)
    }
}

/** A QR code with a white margin, drawn with ZXing. */
private fun qrImage(text: String, size: Int): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2))
    val img = BufferedImage(matrix.width, matrix.height, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until matrix.height) for (x in 0 until matrix.width) img.setRGB(x, y, if (matrix[x, y]) 0x000000 else 0xFFFFFF)
    return img.toComposeImageBitmap()
}
