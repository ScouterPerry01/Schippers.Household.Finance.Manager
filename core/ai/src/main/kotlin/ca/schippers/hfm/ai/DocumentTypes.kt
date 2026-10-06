package ca.schippers.hfm.ai

import ca.schippers.hfm.ocr.DocumentKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

/**
 * A kind of document the AI can read (AI-03): a versioned JSON Schema saying exactly which fields
 * to return, and a short instruction. [id] names the files, such as `receipt`.
 */
data class DocumentType(
    val id: String,
    /** The schema's `$id`, such as `hfm/receipt/v1`, recorded with each reading. */
    val version: String,
    val schema: JsonObject,
    val instructions: String,
    val builtIn: Boolean,
) {
    /** The document kind it fills in, if it is one the books know. */
    val kind: DocumentKind? get() = KINDS[id]

    companion object {
        private val KINDS = mapOf(
            "receipt" to DocumentKind.RECEIPT, "bill" to DocumentKind.BILL, "invoice" to DocumentKind.INVOICE,
            "card_statement" to DocumentKind.CARD_STATEMENT, "bank_statement" to DocumentKind.BANK_STATEMENT,
            "investment_statement" to DocumentKind.INVESTMENT_STATEMENT, "pay_stub" to DocumentKind.PAY_STUB, "eob" to DocumentKind.EOB,
        )

        fun kindFor(id: String): DocumentKind? = KINDS[id]

        /** The type that reads a document of [kind]. */
        fun idFor(kind: DocumentKind): String = KINDS.entries.firstOrNull { it.value == kind }?.key ?: "receipt"
    }
}

/** A file in the user's folder that could not be used, and why ([reason] in English, [problems] for the screen). */
data class RejectedType(val file: String, val reason: String, val problems: List<AiProblem> = emptyList()) {
    constructor(file: String, problem: AiProblem) : this(file, problem.english, listOf(problem))
}

data class LoadedTypes(val types: List<DocumentType>, val rejected: List<RejectedType>) {
    fun get(id: String): DocumentType? = types.firstOrNull { it.id == id }
}

/**
 * The document types shipped with the app, plus any an advanced user puts in [userFolder] (AI-03):
 * `<id>.json` holds the schema and an optional `<id>.txt` the instruction. A file with the same id
 * as a shipped type replaces it. Every schema must suit the API (closed objects, no references);
 * one that does not is listed with its reason and not used.
 */
object DocumentTypes {

    val BUILT_IN = listOf("receipt", "bill", "invoice", "card_statement", "bank_statement", "investment_statement", "pay_stub", "eob")

    private val ID = Regex("[a-z][a-z0-9_]{0,39}")
    private const val MAX_FILE = 256 * 1024

    private val json = Json { ignoreUnknownKeys = true }

    /** The instruction every request starts with. */
    val common: String by lazy { resource("prompts/common.txt") }

    fun load(userFolder: Path? = null): LoadedTypes {
        val types = LinkedHashMap<String, DocumentType>()
        for (id in BUILT_IN) {
            val schema = json.parseToJsonElement(resource("schemas/$id.json")).jsonObject
            types[id] = DocumentType(id, versionOf(schema, id), schema, resource("prompts/$id.txt").trim(), builtIn = true)
        }
        val rejected = ArrayList<RejectedType>()
        if (userFolder != null && Files.isDirectory(userFolder)) {
            val files = Files.list(userFolder).use { s -> s.filter { it.isRegularFile() && it.extension == "json" }.sorted().toList() }
            for (file in files) {
                val id = file.nameWithoutExtension
                val reason = when {
                    !ID.matches(id) -> AiProblem("fileName", "the file name must be lowercase letters, digits and _")
                    Files.size(file) > MAX_FILE -> AiProblem("fileSize", "larger than 256 KB", "256")
                    else -> null
                }
                if (reason != null) {
                    rejected += RejectedType(file.fileName.toString(), reason)
                    continue
                }
                val schema = runCatching { json.parseToJsonElement(file.readText()).jsonObject }.getOrElse {
                    rejected += RejectedType(file.fileName.toString(), AiProblem("fileNotJson", "not a JSON object: ${it.message}", it.message.orEmpty()))
                    continue
                }
                val problems = SchemaCheck.schemaProblems(schema)
                if (problems.isNotEmpty()) {
                    rejected += RejectedType(file.fileName.toString(), problems.take(3).joinToString("; ") { it.english }, problems.take(3))
                    continue
                }
                val text = file.resolveSibling("$id.txt").takeIf { Files.isRegularFile(it) }?.readText()?.trim()
                val instructions = text ?: types[id]?.instructions ?: "Return the fields the schema asks for."
                types[id] = DocumentType(id, versionOf(schema, id), schema, instructions, builtIn = false)
            }
        }
        return LoadedTypes(types.values.toList(), rejected)
    }

    private fun versionOf(schema: JsonObject, id: String) = schema["\$id"]?.jsonPrimitive?.contentOrNull ?: "user/$id"

    private fun resource(path: String): String =
        DocumentTypes::class.java.getResourceAsStream("/hfm/ai/$path")?.use { it.readBytes().decodeToString() } ?: error("missing /hfm/ai/$path")
}
