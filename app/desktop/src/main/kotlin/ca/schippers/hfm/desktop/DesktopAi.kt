package ca.schippers.hfm.desktop

import ca.schippers.hfm.ai.AiImages
import ca.schippers.hfm.ai.AiModel
import ca.schippers.hfm.ai.AiReader
import ca.schippers.hfm.ai.ClaudeProvider
import ca.schippers.hfm.ai.DocumentType
import ca.schippers.hfm.ai.DocumentTypes
import ca.schippers.hfm.ai.LoadedTypes
import ca.schippers.hfm.ai.PageEdit
import ca.schippers.hfm.ai.SecretStore
import ca.schippers.hfm.books.VaultDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.awt.image.BufferedImage
import java.nio.file.Path

/**
 * Cloud AI reading on this computer (section 4.5, ADR 0009): the user's key in the system's secret
 * store (AI-02), the folder where an advanced user adds document types (AI-03), and one reading from
 * the pages the user approved to the review inbox, with every request logged (AI-06).
 */
object DesktopAi {

    private val demo = System.getProperty("hfm.demo") == "true"

    /** The demo keeps keys in memory, so trying it leaves nothing in the system's secret store. */
    val secrets: SecretStore by lazy { if (demo) ca.schippers.hfm.ai.SessionSecrets() else SecretStore.forThisComputer() }

    /** Where added document types go: `%APPDATA%\RANN's Roost\ai-types`, or `~/.config/ranns-roost/ai-types`. */
    val typesFolder: Path by lazy {
        val home = Path.of(System.getProperty("user.home"))
        if (System.getProperty("os.name").lowercase().startsWith("windows")) {
            Path.of(System.getenv("APPDATA") ?: home.resolve("AppData/Roaming").toString(), "RANN's Roost", "ai-types")
        } else {
            (System.getenv("XDG_CONFIG_HOME")?.let(Path::of) ?: home.resolve(".config")).resolve("ranns-roost").resolve("ai-types")
        }
    }

    fun types(): LoadedTypes = DocumentTypes.load(typesFolder)

    private fun keyName(model: BooksModel) = SecretStore.keyName(ClaudeProvider.ID, model.session.householdId, model.books.userId)

    fun key(model: BooksModel): String? = runCatching { secrets.get(keyName(model)) }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Throws [ca.schippers.hfm.ai.SecretStoreException] when the store refuses. */
    fun saveKey(model: BooksModel, key: String) = secrets.put(keyName(model), key.trim())

    fun removeKey(model: BooksModel) = runCatching { secrets.delete(keyName(model)) }

    /** The demo can be pointed at a local stand-in for the service (`-PaiUrl=`), to try the screens without a key or cost. */
    fun provider(model: BooksModel): ClaudeProvider? = key(model)?.let { ClaudeProvider(it, if (demo) System.getProperty("hfm.demo.aiUrl") else null) }

    fun chosenModel(model: BooksModel): AiModel = AiModel.byId(model.books.ai.settings().model)

    /** The pages as they will be sent, after the user's crop and blurred areas. */
    fun prepare(pages: List<BufferedImage>, edits: List<PageEdit>): List<BufferedImage> =
        pages.mapIndexed { i, page -> AiImages.apply(page, edits.getOrElse(i) { PageEdit() }) }

    /**
     * Sends [pages] (already prepared) to read [documentId] as [type], logs each request in the
     * document's group and keeps the checked reading. Runs off the UI thread; throws
     * [ca.schippers.hfm.ai.AiFailure].
     */
    fun read(model: BooksModel, documentId: String, type: DocumentType, pages: List<BufferedImage>): VaultDocument {
        val provider = provider(model) ?: throw ca.schippers.hfm.ai.AiFailure(ca.schippers.hfm.ai.AiFailure.Reason.KEY)
        val books = model.books
        val reading = AiReader(provider).read(type, pages.map { AiImages.jpeg(it) }, chosenModel(model)) { u ->
            books.ai.record(documentId, u.provider, u.model, u.documentType, u.inputTokens, u.outputTokens, u.costUsd, u.succeeded)
        }
        val answer = Json.encodeToString(JsonObject.serializer(), reading.answer)
        // AI-03: a type the app has no screen for keeps every field as searchable text with the document.
        val text = if (type.kind == null) ca.schippers.hfm.ai.AiFields.text(reading.fields).ifBlank { null } else null
        return books.ai.saveReading(documentId, type.id, type.version, answer, reading.checked, reading.usage.last().model, reading.draft, text)
    }
}
