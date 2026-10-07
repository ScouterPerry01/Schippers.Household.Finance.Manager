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
            transfers += model.receiveByHand(file.name, bytes)
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

/**
 * CAP-06: an email without attachments as a PDF of its header and text, to keep in the vault. The
 * standard PDF font covers Western European letters only; an email with other characters (Greek,
 * Cyrillic, Chinese, Arabic...) is written with a font of the computer that has them, embedded, so
 * they are not lost ([unicodeFont]).
 */
internal fun emailPdf(header: List<String>, text: String, fonts: List<String> = SYSTEM_FONTS): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val document = org.openpdf.text.Document(org.openpdf.text.PageSize.LETTER, 42f, 42f, 42f, 42f)
    org.openpdf.text.pdf.PdfWriter.getInstance(document, out)
    document.open()
    val all = (header + text).joinToString("\n")
    val wide = if (java.nio.charset.Charset.forName("windows-1252").newEncoder().canEncode(all)) null else unicodeFont(all, fonts)
    val bold = wide?.let { org.openpdf.text.Font(it, 10f, org.openpdf.text.Font.BOLD) } ?: org.openpdf.text.FontFactory.getFont(org.openpdf.text.FontFactory.HELVETICA_BOLD, 10f)
    val body = wide?.let { org.openpdf.text.Font(it, 10f) } ?: org.openpdf.text.FontFactory.getFont(org.openpdf.text.FontFactory.HELVETICA, 10f)
    for (h in header) document.add(org.openpdf.text.Paragraph(h, bold))
    document.add(org.openpdf.text.Paragraph(" "))
    for (line in text.lines()) document.add(org.openpdf.text.Paragraph(line.ifBlank { " " }, body))
    document.close()
    return out.toByteArray()
}

/**
 * The first of [fonts] (font files of the computer; "file.ttc,0" for one font of a collection) that
 * has every character of [text], or else the first one that opens; null when none does.
 */
internal fun unicodeFont(text: String, fonts: List<String>): org.openpdf.text.pdf.BaseFont? {
    var fallback: org.openpdf.text.pdf.BaseFont? = null
    for (path in fonts) {
        if (!java.io.File(if (".ttc," in path) path.substringBeforeLast(",") else path).isFile) continue
        val font = runCatching { org.openpdf.text.pdf.BaseFont.createFont(path, org.openpdf.text.pdf.BaseFont.IDENTITY_H, org.openpdf.text.pdf.BaseFont.EMBEDDED) }.getOrNull() ?: continue
        if (text.all { it.isWhitespace() || it.isSurrogate() || font.charExists(it.code) }) return font
        if (fallback == null) fallback = font
    }
    return fallback
}

/** Fonts with many scripts on Windows, Linux and macOS, the widest first. */
internal val SYSTEM_FONTS: List<String> = listOf(
    "C:/Windows/Fonts/arialuni.ttf", "C:/Windows/Fonts/segoeui.ttf", "C:/Windows/Fonts/arial.ttf", "C:/Windows/Fonts/msyh.ttc,0", "C:/Windows/Fonts/malgun.ttf",
    "C:/Windows/Fonts/YuGothR.ttc,0",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/dejavu/DejaVuSans.ttf", "/usr/share/fonts/dejavu-sans-fonts/DejaVuSans.ttf",
    "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf", "/usr/share/fonts/google-noto/NotoSans-Regular.ttf",
    "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0", "/usr/share/fonts/google-noto-cjk/NotoSansCJK-Regular.ttc,0",
    "/Library/Fonts/Arial Unicode.ttf", "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
)

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
                // CAP-04, CAP-06: a file that could not be read is moved with the others (so it is not tried every
                // 20 seconds), and the message says so, naming it.
                if (summary != null && (summary.added > 0 || summary.transfers.isNotEmpty() || summary.unreadable.isNotEmpty())) {
                    model.lastImportMessage = model.importMessage(summary) +
                        (if (summary.unreadable.isNotEmpty()) " " + model.t("documents.watchUnreadable", IMPORTED_DIR) else "")
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

