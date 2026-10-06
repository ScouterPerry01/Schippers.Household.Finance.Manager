package ca.schippers.hfm.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The JSON Schema rules used by document types (AI-03, AI-05): checks an answer against its schema
 * on this computer, whatever the provider promises, and prepares a schema for the API, which
 * accepts only part of JSON Schema (types, `enum`, `const`, `anyOf`, `allOf`, string formats and
 * `additionalProperties: false`). Limits it does not accept, such as `minimum` or `maxLength`, are
 * removed before sending and still checked here.
 */
object SchemaCheck {

    /** Keywords the API does not accept; they stay in the file and are checked locally. */
    private val LOCAL_ONLY = setOf(
        "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf", "minLength", "maxLength", "pattern",
        "maxItems", "uniqueItems", "minProperties", "maxProperties",
    )

    /** Metadata kept in the files but not sent. */
    private val METADATA = setOf("\$id", "\$schema", "title", "\$comment")

    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

    /** Problems of [value] against [schema], each with its JSON path, in English; empty when it conforms. */
    fun validate(value: JsonElement, schema: JsonObject): List<String> = validateProblems(value, schema).map { it.english }

    /** [validate] as problems the screen can show in the user's language. */
    fun validateProblems(value: JsonElement, schema: JsonObject): List<AiProblem> = ArrayList<AiProblem>().also { check(value, schema, "$", it) }

    /** The schema as sent to the provider. */
    fun forApi(schema: JsonObject): JsonObject = strip(schema) as JsonObject

    /**
     * Why a schema added by the user cannot be used, or nothing. The API needs every object closed
     * with `additionalProperties: false`, and references and recursion are not supported here.
     */
    fun problems(schema: JsonObject): List<String> = schemaProblems(schema).map { it.english }

    /** [problems] as problems the screen can show in the user's language. */
    fun schemaProblems(schema: JsonObject): List<AiProblem> = ArrayList<AiProblem>().also { findProblems(schema, "$", it) }

    private fun findProblems(s: JsonObject, path: String, out: MutableList<AiProblem>) {
        if ("\$ref" in s || "\$defs" in s || "definitions" in s) out += AiProblem("references", "$path: references (\$ref) are not supported", path)
        val type = types(s)
        if ("object" in type) {
            if (s["additionalProperties"] != JsonPrimitive(false)) out += AiProblem("notClosed", "$path: objects need \"additionalProperties\": false", path)
            s["properties"]?.jsonObject?.forEach { (k, v) -> (v as? JsonObject)?.let { findProblems(it, "$path.$k", out) } }
        }
        (s["items"] as? JsonObject)?.let { findProblems(it, "$path[]", out) }
        listOf("anyOf", "allOf").forEach { key -> (s[key] as? JsonArray)?.forEachIndexed { i, e -> (e as? JsonObject)?.let { findProblems(it, "$path/$key[$i]", out) } } }
        if (type.isEmpty() && "enum" !in s && "const" !in s && "anyOf" !in s && "allOf" !in s) out += AiProblem("noType", "$path: no type", path)
    }

    private fun strip(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(
            e.filterKeys { k -> k !in LOCAL_ONLY && k !in METADATA }
                .filterNot { (k, v) -> k == "minItems" && (v as? JsonPrimitive)?.intOrNull?.let { it > 1 } == true }
                .mapValues { (k, v) -> if (k == "properties") JsonObject(v.jsonObject.mapValues { strip(it.value) }) else strip(v) },
        )
        is JsonArray -> JsonArray(e.map { strip(it) })
        else -> e
    }

    private fun types(s: JsonObject): Set<String> = when (val t = s["type"]) {
        is JsonPrimitive -> setOfNotNull(t.contentOrNull)
        is JsonArray -> t.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
        else -> emptySet()
    }

    private fun check(v: JsonElement, s: JsonObject, path: String, out: MutableList<AiProblem>) {
        (s["anyOf"] as? JsonArray)?.let { options ->
            if (options.none { o -> validate(v, o.jsonObject).isEmpty() }) out += AiProblem("noForm", "$path: matches none of the allowed forms", path)
        }
        (s["allOf"] as? JsonArray)?.forEach { o -> check(v, o.jsonObject, path, out) }
        s["const"]?.let { if (it != v) out += AiProblem("mustBe", "$path: must be $it", path, it.toString()) }
        (s["enum"] as? JsonArray)?.let { if (v !in it) out += AiProblem("notOneOf", "$path: ${short(v)} is not one of ${it.joinToString { e -> short(e) }}", path, short(v), it.joinToString { e -> short(e) }) }
        val type = types(s)
        if (type.isNotEmpty() && type.none { matches(v, it) }) {
            out += AiProblem("expected", "$path: expected ${type.joinToString(" or ")}, found ${short(v)}", path, type.joinToString(" | "), short(v))
            return
        }
        when {
            v is JsonObject -> {
                val props = s["properties"]?.jsonObject ?: JsonObject(emptyMap())
                s["required"]?.jsonArray?.forEach { r -> r.jsonPrimitive.contentOrNull?.let { if (it !in v) out += AiProblem("missing", "$path: \"$it\" is missing", path, it) } }
                for ((k, child) in v) {
                    val sub = props[k]
                    if (sub == null) {
                        if (s["additionalProperties"] == JsonPrimitive(false)) out += AiProblem("notAllowed", "$path: \"$k\" is not allowed", path, k)
                    } else {
                        check(child, sub.jsonObject, "$path.$k", out)
                    }
                }
                s["minProperties"]?.jsonPrimitive?.intOrNull?.let { if (v.size < it) out += AiProblem("fewerFields", "$path: fewer than $it fields", path, it.toString()) }
            }
            v is JsonArray -> {
                (s["items"] as? JsonObject)?.let { item -> v.forEachIndexed { i, e -> check(e, item, "$path[$i]", out) } }
                s["minItems"]?.jsonPrimitive?.intOrNull?.let { if (v.size < it) out += AiProblem("fewerItems", "$path: fewer than $it items", path, it.toString()) }
                s["maxItems"]?.jsonPrimitive?.intOrNull?.let { if (v.size > it) out += AiProblem("moreItems", "$path: more than $it items", path, it.toString()) }
            }
            v is JsonPrimitive && v.isString -> {
                val text = v.content
                if (s["format"]?.jsonPrimitive?.contentOrNull == "date" && !isDate(text)) out += AiProblem("notDate", "$path: \"$text\" is not a date (YYYY-MM-DD)", path, text)
                s["minLength"]?.jsonPrimitive?.intOrNull?.let { if (text.length < it) out += AiProblem("shorter", "$path: shorter than $it characters", path, it.toString()) }
                s["maxLength"]?.jsonPrimitive?.intOrNull?.let { if (text.length > it) out += AiProblem("longer", "$path: longer than $it characters", path, it.toString()) }
                s["pattern"]?.jsonPrimitive?.contentOrNull?.let { p -> if (runCatching { !Regex(p).containsMatchIn(text) }.getOrDefault(false)) out += AiProblem("noMatch", "$path: does not match $p", path, p) }
            }
            v is JsonPrimitive && v != JsonNull -> {
                val d = v.doubleOrNull ?: return
                s["minimum"]?.jsonPrimitive?.doubleOrNull?.let { if (d < it) out += AiProblem("below", "$path: below $it", path, it.toString()) }
                s["maximum"]?.jsonPrimitive?.doubleOrNull?.let { if (d > it) out += AiProblem("above", "$path: above $it", path, it.toString()) }
            }
        }
    }

    private fun matches(v: JsonElement, type: String): Boolean = when (type) {
        "object" -> v is JsonObject
        "array" -> v is JsonArray
        "null" -> v == JsonNull
        "string" -> v is JsonPrimitive && v.isString
        "boolean" -> v is JsonPrimitive && !v.isString && v.booleanOrNull != null
        "integer" -> v is JsonPrimitive && !v.isString && v.longOrNull != null
        "number" -> v is JsonPrimitive && !v.isString && v != JsonNull && v.content.toBigDecimalOrNull() != null
        else -> true
    }

    private fun isDate(text: String): Boolean =
        DATE.matches(text) && runCatching { kotlinx.datetime.LocalDate.parse(text) }.isSuccess

    private fun short(e: JsonElement): String = e.toString().let { if (it.length > 40) it.take(37) + "..." else it }
}
