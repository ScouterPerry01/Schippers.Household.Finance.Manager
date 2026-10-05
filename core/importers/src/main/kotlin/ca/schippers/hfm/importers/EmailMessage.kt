package ca.schippers.hfm.importers

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.Base64

/**
 * CAP-06: an e-receipt saved from an email program as an .eml file (RFC 5322 with MIME). Only what a
 * receipt needs: who sent it, when, the subject, the text, and the attached files. The desktop never
 * signs in to a mailbox; the user saves or drags the message.
 */
data class EmailMessage(
    val from: String?,
    val subject: String?,
    val date: String?,
    /** The message's text: the plain part, or the HTML part with its tags removed. */
    val text: String,
    val attachments: List<Attachment>,
) {
    data class Attachment(val fileName: String, val mimeType: String, val content: ByteArray)

    companion object {
        /** Reads an .eml file; malformed parts are skipped rather than failing the whole message. */
        fun parse(bytes: ByteArray): EmailMessage {
            val part = Part.parse(bytes)
            val plain = ArrayList<String>()
            val html = ArrayList<String>()
            val files = ArrayList<Attachment>()
            fun walk(p: Part) {
                val type = p.contentType
                when {
                    type.startsWith("multipart/") -> p.children().forEach(::walk)
                    type == "message/rfc822" -> walk(Part.parse(p.body()))
                    p.fileName != null || p.disposition == "attachment" || !type.startsWith("text/") ->
                        files += Attachment(p.fileName ?: "attachment", type, p.body())
                    type == "text/html" -> html += p.text()
                    else -> plain += p.text()
                }
            }
            walk(part)
            val text = plain.joinToString("\n").ifBlank { html.joinToString("\n") { htmlToText(it) } }
            return EmailMessage(part.header("from")?.let(::decodeWords), part.header("subject")?.let(::decodeWords), part.header("date"), text.trim(), files)
        }

        /** Visible text of an HTML body: block ends become line breaks, tags and scripts go, entities are decoded. */
        fun htmlToText(html: String): String = html
            .replace(Regex("(?is)<(script|style|head)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<br\\s*/?>|</(p|div|tr|li|h[1-6]|table)>"), "\n")
            .replace(Regex("(?i)</t[dh]>"), "\t")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
            .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: "" }
            .lines().map { it.replace(Regex("[ \\t]+"), " ").trim() }.filter { it.isNotEmpty() }.joinToString("\n")

        /** RFC 2047: "=?UTF-8?B?...?=" and "=?ISO-8859-1?Q?...?=" in headers. */
        fun decodeWords(value: String): String = Regex("=\\?([^?]+)\\?([BbQq])\\?([^?]*)\\?=(\\s+(?==\\?))?").replace(value) { m ->
            val charset = runCatching { Charset.forName(m.groupValues[1]) }.getOrDefault(Charsets.UTF_8)
            val data = m.groupValues[3]
            val bytes = if (m.groupValues[2].equals("B", true)) runCatching { Base64.getMimeDecoder().decode(data) }.getOrDefault(ByteArray(0))
            else quotedPrintable(data.replace('_', ' '))
            String(bytes, charset)
        }.trim()

        /** Quoted-printable: "=C3=A9" is a byte, "=" at a line end joins the lines; [text] holds one byte per character. */
        internal fun quotedPrintable(text: String): ByteArray {
            val out = ByteArrayOutputStream()
            val s = text.replace(Regex("=\\r?\\n"), "")
            var i = 0
            while (i < s.length) {
                val hex = if (s[i] == '=') s.substring(i + 1, minOf(i + 3, s.length)) else null
                if (hex != null && hex.length == 2 && hex.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }) {
                    out.write(hex.toInt(16))
                    i += 3
                } else {
                    out.write(s[i].code and 0xFF)
                    i++
                }
            }
            return out.toByteArray()
        }
    }

    /** One MIME part: its headers and raw body. */
    private class Part(private val headers: Map<String, String>, private val raw: ByteArray) {

        fun header(name: String): String? = headers[name]

        val contentType: String get() = (headers["content-type"] ?: "text/plain").substringBefore(';').trim().lowercase()

        val disposition: String? get() = headers["content-disposition"]?.substringBefore(';')?.trim()?.lowercase()

        val fileName: String? get() = (param(headers["content-disposition"], "filename") ?: param(headers["content-type"], "name"))?.let(::decodeWords)

        private val charset: Charset get() = param(headers["content-type"], "charset")?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8

        fun body(): ByteArray = when (headers["content-transfer-encoding"]?.trim()?.lowercase()) {
            "base64" -> runCatching { Base64.getMimeDecoder().decode(String(raw, Charsets.US_ASCII).filter { !it.isWhitespace() }) }.getOrDefault(ByteArray(0))
            "quoted-printable" -> quotedPrintable(String(raw, Charsets.ISO_8859_1))
            else -> raw
        }

        fun text(): String = String(body(), charset)

        fun children(): List<Part> {
            val boundary = param(headers["content-type"], "boundary") ?: return emptyList()
            val text = String(raw, Charsets.ISO_8859_1)
            val delimiter = "--$boundary"
            return text.split(delimiter).drop(1)
                .takeWhile { !it.startsWith("--") }
                .map { chunk -> parse(chunk.removePrefix("\r\n").removePrefix("\n").toByteArray(Charsets.ISO_8859_1)) }
        }

        companion object {
            fun parse(bytes: ByteArray): Part {
                val text = String(bytes, Charsets.ISO_8859_1)
                val split = Regex("\\r?\\n\\r?\\n").find(text)
                val head = if (split != null) text.substring(0, split.range.first) else text
                val body = if (split != null) text.substring(split.range.last + 1) else ""
                val headers = LinkedHashMap<String, String>()
                var current: String? = null
                for (line in head.split(Regex("\\r?\\n"))) {
                    if ((line.startsWith(" ") || line.startsWith("\t")) && current != null) {
                        headers[current] = headers.getValue(current) + " " + line.trim()
                    } else if (':' in line) {
                        current = line.substringBefore(':').trim().lowercase()
                        if (current !in headers) headers[current] = line.substringAfter(':').trim()
                    }
                }
                return Part(headers, body.trimEnd('\r', '\n').toByteArray(Charsets.ISO_8859_1))
            }

            fun param(header: String?, name: String): String? {
                header ?: return null
                val m = Regex("(?i)(?:^|;)\\s*$name\\*?=\\s*(\"([^\"]*)\"|([^;\\s]+))").find(header) ?: return null
                val value = m.groupValues[2].ifEmpty { m.groupValues[3] }
                // RFC 2231: filename*=UTF-8''re%C3%A7u.pdf
                val extended = Regex("(?i)^([a-z0-9-]+)''(.*)$").find(value) ?: return value
                return runCatching { java.net.URLDecoder.decode(extended.groupValues[2].replace("+", "%2B"), extended.groupValues[1]) }.getOrDefault(extended.groupValues[2])
            }
        }
    }
}
