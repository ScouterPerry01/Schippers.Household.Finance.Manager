package ca.schippers.hfm.desktop

import ca.schippers.hfm.importers.EmailMessage
import ca.schippers.hfm.ocr.OcrLine
import ca.schippers.hfm.ocr.OcrResult
import ca.schippers.hfm.ocr.desktop.DocumentReader
import ca.schippers.hfm.ocr.desktop.FileKind
import ca.schippers.hfm.ocr.desktop.Heif
import ca.schippers.hfm.ocr.desktop.PaddleOcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/** Text recognition on this computer (OCR-01). The models load the first time a document is read. */
object DesktopOcr {
    val reader: DocumentReader by lazy { DocumentReader(PaddleOcrEngine()) }
}

/** What an import did, for the message shown afterwards. */
data class ImportSummary(
    val added: Int,
    val alreadyInVault: Int,
    val unreadable: List<String>,
    /** HEIC photos kept in the vault but not read, for want of a HEIC decoder on this computer. */
    val needHeicDecoder: List<String> = emptyList(),
    /** Section 3.2: what became of the phone transfer files among them. */
    val transfers: List<String> = emptyList(),
) {
    operator fun plus(other: ImportSummary) = ImportSummary(
        added + other.added, alreadyInVault + other.alreadyInVault, unreadable + other.unreadable, needHeicDecoder + other.needHeicDecoder, transfers + other.transfers,
    )

    companion object {
        val NONE = ImportSummary(0, 0, emptyList())
    }
}

/** File types the desktop imports (CAP-03), and the phone's transfer files (section 3.2). */
val IMPORTABLE_EXTENSIONS = setOf("pdf", "jpg", "jpeg", "png", "heic", "heif", "bmp", "gif", "eml", ca.schippers.hfm.sync.BundleFile.EXTENSION)

/**
 * CAP-03, CAP-04: stores each file in the vault, reads its text on this computer and extracts its
 * fields, so it arrives in the review inbox ready to file. Runs off the UI thread.
 */
suspend fun importFiles(model: BooksModel, files: List<Path>, groupId: String): ImportSummary = withContext(Dispatchers.IO) {
    var added = 0
    var existing = 0
    val unreadable = ArrayList<String>()
    val needDecoder = ArrayList<String>()
    val transfers = ArrayList<String>()
    /** Stores one file and reads it; [text] is its text when already known (an email's), so it is not read again. */
    fun importOne(name: String, bytes: ByteArray?, text: String? = null) {
        val kind = bytes?.let(FileKind::of)
        if (bytes == null || kind == null || kind == FileKind.UNSUPPORTED) {
            unreadable += name
            return
        }
        val imported = runCatching { model.books.documents.import(groupId, bytes, name, kind.mimeType) }.getOrElse {
            unreadable += name
            return
        }
        if (imported.alreadyInVault) {
            existing++
            return
        }
        added++
        if (text != null) {
            runCatching {
                val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { OcrLine(it, 1f) }
                model.books.documents.recordText(imported.document.id, 1, OcrResult(lines, 0), "email-text", today())
            }
            return
        }
        if (kind == FileKind.HEIC && !Heif.available) {
            needDecoder += name
            return
        }
        // A file that cannot be read still stays in the vault, for the user to fill in by hand.
        runCatching {
            val read = DesktopOcr.reader.read(bytes)
            model.books.documents.recordText(imported.document.id, read.pages, read.result, if (read.fromTextLayer) "pdf-text" else "paddle-ppocrv5-latin", today())
        }
    }
    for (file in files) {
        // Nothing larger than a vault document or a transfer file is read into memory.
        val bytes = runCatching { readTransferFile(file) }.getOrNull()
        // A phone's transfer file, saved from an email or copied by USB: its items go to the inbox.
        if (bytes != null && file.extension.lowercase() == ca.schippers.hfm.sync.BundleFile.EXTENSION) {
            transfers += model.transferMessage(file.name, model.syncServer.receiveFile(bytes))
            continue
        }
        // CAP-06: an e-receipt saved from the email program: its PDF or picture attachments, or else the email itself.
        if (bytes != null && file.extension.lowercase() == "eml") {
            val email = runCatching { EmailMessage.parse(bytes) }.getOrNull()
            if (email == null) {
                unreadable += file.name
                continue
            }
            val parts = email.attachments.filter { FileKind.of(it.content) != FileKind.UNSUPPORTED }
            if (parts.isNotEmpty()) {
                for (a in parts) importOne(a.fileName, a.content)
            } else {
                val header = listOfNotNull(email.subject, email.from, email.date)
                importOne(file.name.substringBeforeLast('.') + ".pdf", emailPdf(header, email.text), (header + email.text).joinToString("\n"))
            }
            continue
        }
        importOne(file.name, bytes)
    }
    ImportSummary(added, existing, unreadable, needDecoder, transfers)
}

/** CAP-06: an email without attachments as a PDF of its header and text, to keep in the vault. */
private fun emailPdf(header: List<String>, text: String): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val document = org.openpdf.text.Document(org.openpdf.text.PageSize.LETTER, 42f, 42f, 42f, 42f)
    org.openpdf.text.pdf.PdfWriter.getInstance(document, out)
    document.open()
    val bold = org.openpdf.text.FontFactory.getFont(org.openpdf.text.FontFactory.HELVETICA_BOLD, 10f)
    val body = org.openpdf.text.FontFactory.getFont(org.openpdf.text.FontFactory.HELVETICA, 10f)
    for (h in header) document.add(org.openpdf.text.Paragraph(h, bold))
    document.add(org.openpdf.text.Paragraph(" "))
    for (line in text.lines()) document.add(org.openpdf.text.Paragraph(line.ifBlank { " " }, body))
    document.close()
    return out.toByteArray()
}

/** The group new documents go to: the shared group the user can add to, or else any. */
fun BooksModel.defaultDocumentGroup(): String? =
    books.groups().filter { it.level.allows(ca.schippers.hfm.domain.PermissionLevel.CAPTURE_ONLY) }.let { g -> (g.firstOrNull { !it.isPrivate } ?: g.firstOrNull())?.id }

/**
 * CAP-04: imports files saved into the watched folder (a scanner's output, downloaded e-bills).
 * Each imported file is moved into an "Imported" folder inside it, so nothing is read twice.
 */
suspend fun watchFolder(model: BooksModel) {
    while (true) {
        val folder = model.books.setting(WATCH_FOLDER)?.let { Path.of(it) }
        val group = model.books.setting(WATCH_GROUP)?.takeIf { id -> model.books.groups().any { it.id == id } } ?: model.defaultDocumentGroup()
        if (folder != null && group != null && withContext(Dispatchers.IO) { Files.isDirectory(folder) }) {
            val files = withContext(Dispatchers.IO) {
                Files.list(folder).use { s -> s.filter { it.isRegularFile() && it.extension.lowercase() in IMPORTABLE_EXTENSIONS && !it.name.startsWith(".") }.toList() }
                    // A file still being written by a scanner is left for the next round.
                    .filter { System.currentTimeMillis() - Files.getLastModifiedTime(it).toMillis() > 5_000 }
            }
            if (files.isNotEmpty()) {
                val summary = runCatching { importFiles(model, files, group) }.getOrNull()
                withContext(Dispatchers.IO) {
                    val done = Files.createDirectories(folder.resolve(IMPORTED_DIR))
                    for (f in files) runCatching { Files.move(f, uniqueTarget(done, f.name), StandardCopyOption.ATOMIC_MOVE) }
                }
                if (summary != null && (summary.added > 0 || summary.transfers.isNotEmpty())) {
                    model.lastImportMessage = model.importMessage(summary)
                    model.changed()
                }
            }
        }
        delay(WATCH_INTERVAL_MS)
    }
}

private fun uniqueTarget(dir: Path, name: String): Path {
    var target = dir.resolve(name)
    var n = 2
    while (Files.exists(target)) {
        val dot = name.lastIndexOf('.')
        target = dir.resolve(if (dot > 0) "${name.substring(0, dot)} ($n)${name.substring(dot)}" else "$name ($n)")
        n++
    }
    return target
}

fun BooksModel.importMessage(s: ImportSummary): String = listOfNotNull(
    t("documents.imported", s.added).takeIf { s.added > 0 || s.transfers.isEmpty() },
    s.alreadyInVault.takeIf { it > 0 }?.let { t("documents.alreadyInVault", it) },
    s.unreadable.takeIf { it.isNotEmpty() }?.let { t("documents.unreadable", it.joinToString(", ")) },
    s.needHeicDecoder.takeIf { it.isNotEmpty() }?.let { t("documents.heicNoDecoder", it.joinToString(", ")) + " " + heicDecoderHint() },
).plus(s.transfers).joinToString(" ")

/** How to install a HEIC decoder on this computer (ADR 0004). */
fun BooksModel.heicDecoderHint(): String = t(if (Heif.platform == Heif.Platform.WINDOWS) "documents.heicHintWindows" else "documents.heicHintLinux")

const val WATCH_FOLDER = "documents.watchFolder"
const val WATCH_GROUP = "documents.watchGroup"
const val IMPORTED_DIR = "Imported"
private const val WATCH_INTERVAL_MS = 20_000L

