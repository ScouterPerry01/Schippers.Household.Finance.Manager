package ca.schippers.hfm.desktop

import ca.schippers.hfm.books.DocumentService
import ca.schippers.hfm.ocr.desktop.DocumentReader
import ca.schippers.hfm.ocr.desktop.FileKind
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
data class ImportSummary(val added: Int, val alreadyInVault: Int, val unreadable: List<String>) {
    operator fun plus(other: ImportSummary) = ImportSummary(added + other.added, alreadyInVault + other.alreadyInVault, unreadable + other.unreadable)

    companion object {
        val NONE = ImportSummary(0, 0, emptyList())
    }
}

/** File types the desktop imports (CAP-03). */
val IMPORTABLE_EXTENSIONS = setOf("pdf", "jpg", "jpeg", "png", "heic", "heif", "bmp", "gif")

/**
 * CAP-03, CAP-04: stores each file in the vault, reads its text on this computer and extracts its
 * fields, so it arrives in the review inbox ready to file. Runs off the UI thread.
 */
suspend fun importFiles(model: BooksModel, files: List<Path>, groupId: String): ImportSummary = withContext(Dispatchers.IO) {
    var added = 0
    var existing = 0
    val unreadable = ArrayList<String>()
    for (file in files) {
        val bytes = runCatching { Files.readAllBytes(file) }.getOrNull()
        val kind = bytes?.let(FileKind::of)
        if (bytes == null || kind == null || kind == FileKind.UNSUPPORTED) {
            unreadable += file.name
            continue
        }
        val imported = runCatching { model.books.documents.import(groupId, bytes, file.name, kind.mimeType) }.getOrElse {
            unreadable += file.name
            continue
        }
        if (imported.alreadyInVault) {
            existing++
            continue
        }
        added++
        // A file that cannot be read still stays in the vault, for the user to fill in by hand.
        runCatching {
            val read = DesktopOcr.reader.read(bytes)
            model.books.documents.recordText(imported.document.id, read.pages, read.result, if (read.fromTextLayer) "pdf-text" else "paddle-ppocrv5-latin", today())
        }
    }
    ImportSummary(added, existing, unreadable)
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
                if (summary != null && summary.added > 0) {
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
    t("documents.imported", s.added),
    s.alreadyInVault.takeIf { it > 0 }?.let { t("documents.alreadyInVault", it) },
    s.unreadable.takeIf { it.isNotEmpty() }?.let { t("documents.unreadable", it.joinToString(", ")) },
).joinToString(" ")

const val WATCH_FOLDER = "documents.watchFolder"
const val WATCH_GROUP = "documents.watchGroup"
const val IMPORTED_DIR = "Imported"
private const val WATCH_INTERVAL_MS = 20_000L

/** Keeps DocumentService's retention period visible to the screens. */
val RETENTION_YEARS = DocumentService.RETENTION_YEARS
