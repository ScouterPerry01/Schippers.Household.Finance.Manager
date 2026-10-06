package ca.schippers.hfm.desktop

import ca.schippers.hfm.books.FileReply
import ca.schippers.hfm.sync.BundleFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

enum class TransferOutcome { RECEIVED, OWNER_AWAY, OTHER_HOUSEHOLD, NOT_PAIRED, NOT_A_BUNDLE }

/** What happened to one transfer file from a phone; [reply] is the answer to leave for the phone. */
data class TransferReceipt(val outcome: TransferOutcome, val received: Int = 0, val reply: FileReply? = null, val owner: String? = null)

/**
 * Section 3.2: the folder a phone's transfer files arrive in when it is away from home: a folder of
 * the user's own Google Drive, OneDrive, Dropbox or Nextcloud, kept in step by that provider's app
 * on this computer. Every 20 seconds, each new request is imported and removed, and the reply is
 * left beside it for the phone to collect. Requests for another household, or for a user who is not
 * signed in, are left alone; those that cannot be read are moved aside.
 */
suspend fun watchTransferFolder(model: BooksModel) {
    while (true) {
        val folder = model.books.setting(TRANSFER_FOLDER)?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
        if (folder != null && withContext(Dispatchers.IO) { Files.isDirectory(folder) }) {
            runCatching { checkTransferFolder(model, folder) }
                .onFailure { model.transferStatus = model.t("transfer.folderError", it.message ?: it.javaClass.simpleName) }
        }
        delay(TRANSFER_INTERVAL_MS)
    }
}

/** One look at the transfer folder. Returns how many items arrived. */
suspend fun checkTransferFolder(model: BooksModel, folder: Path): Int = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    val requests = Files.list(folder).use { s ->
        s.filter { it.isRegularFile() && it.name.startsWith(BundleFile.TO_DESKTOP) && it.name.endsWith("." + BundleFile.EXTENSION) }.toList()
    }
        // A file the provider is still writing is left for the next round.
        .filter { now - Files.getLastModifiedTime(it).toMillis() > 5_000 }
    var received = 0
    for (file in requests) {
        // Anyone who can write to the cloud folder can leave a file there: a huge one is not read, and
        // one that fails in an unexpected way does not stop the others (Phase 5 security review).
        val bytes = readTransferFile(file)
        val receipt = if (bytes == null) {
            TransferReceipt(TransferOutcome.NOT_A_BUNDLE)
        } else {
            runCatching { model.syncServer.receiveFile(bytes) }
                .onFailure { model.transferStatus = model.t("transfer.folderError", it.message ?: it.javaClass.simpleName) }
                .getOrNull() ?: continue
        }
        when (receipt.outcome) {
            TransferOutcome.RECEIVED -> {
                val reply = receipt.reply!!
                // Written under another name first, so the phone never reads half a reply.
                val part = folder.resolve(reply.name + ".part")
                Files.write(part, reply.bytes)
                Files.move(part, folder.resolve(reply.name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                Files.deleteIfExists(file)
                received += receipt.received
            }
            TransferOutcome.NOT_PAIRED, TransferOutcome.NOT_A_BUNDLE -> {
                val aside = Files.createDirectories(folder.resolve(NOT_IMPORTED_DIR))
                Files.move(file, aside.resolve(file.name), StandardCopyOption.REPLACE_EXISTING)
            }
            TransferOutcome.OWNER_AWAY -> model.transferStatus = model.t("transfer.ownerAway", receipt.owner.orEmpty())
            TransferOutcome.OTHER_HOUSEHOLD -> Unit
        }
    }
    // Replies a phone never collected (it was removed, or reset) do not pile up.
    staleReplies(folder, now).forEach { runCatching { Files.delete(it) } }
    if (requests.isNotEmpty()) {
        model.transferStatus = model.t("transfer.checked", received, java.time.LocalTime.now().withNano(0).withSecond(0).toString())
        if (received > 0) model.changed()
    }
    received
}

/** A transfer file's bytes, or null when it is larger than any the phone writes ([MAX_TRANSFER_BYTES]). */
fun readTransferFile(file: Path): ByteArray? = if (Files.size(file) > MAX_TRANSFER_BYTES) null else Files.readAllBytes(file)

/**
 * The replies left longer than [REPLY_KEPT_MS] (60 days), to remove. Only reply files: another file
 * whose name happens to start the same way is the user's own.
 */
fun staleReplies(folder: Path, now: Long): List<Path> = Files.list(folder).use { s ->
    s.filter { it.isRegularFile() && it.name.startsWith(BundleFile.TO_PHONE + "-") && it.name.endsWith("." + BundleFile.EXTENSION) && now - Files.getLastModifiedTime(it).toMillis() > REPLY_KEPT_MS }.toList()
}

/** The message for a transfer file brought in by hand (chosen or dropped), with no reply left for the phone. */
fun BooksModel.transferMessage(name: String, r: TransferReceipt): String = when (r.outcome) {
    TransferOutcome.RECEIVED -> t("transfer.fileReceived", name, r.received)
    TransferOutcome.OWNER_AWAY -> t("transfer.fileOwnerAway", name, r.owner.orEmpty())
    TransferOutcome.OTHER_HOUSEHOLD -> t("transfer.fileOtherHousehold", name)
    TransferOutcome.NOT_PAIRED -> t("transfer.fileNotPaired", name)
    TransferOutcome.NOT_A_BUNDLE -> t("transfer.fileUnreadable", name)
}

const val TRANSFER_FOLDER = "sync.transferFolder"
const val NOT_IMPORTED_DIR = "Not imported"

/** The largest transfer file read, as for a request over Wi-Fi; the phone writes files of about 15 MB. */
const val MAX_TRANSFER_BYTES = 200L * 1024 * 1024
private const val TRANSFER_INTERVAL_MS = 20_000L
const val REPLY_KEPT_MS = 60L * 24 * 3600 * 1000
