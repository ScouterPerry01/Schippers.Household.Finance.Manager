package hfm.spike.hello

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * JVM side of the hfm-hello contract: spawn the helper, optional single line on stdin, one JSON object on stdout,
 * exit code = outcome. This is the class that would move into the production app (Windows-only module).
 *
 * Secrets travel only via the pipes (stdin/stdout), never via argv (argv is readable by other processes).
 */
class WindowsHelloClient(
    private val exe: Path,
    private val timeoutSeconds: Long = 180, // the user may take a while to answer the Hello prompt
) {
    data class Response(val exitCode: Int, val fields: Map<String, Any?>, val stderr: String) {
        val ok: Boolean get() = exitCode == EXIT_OK
        val status: String? get() = fields["status"] as? String
        fun str(key: String): String? = fields[key] as? String
        override fun toString() = "exit=$exitCode $fields" + if (stderr.isBlank()) "" else " stderr=$stderr"
    }

    companion object {
        const val EXIT_OK = 0
        const val EXIT_NOT_VERIFIED = 1
        const val EXIT_UNAVAILABLE = 2
        const val EXIT_KEY_NOT_FOUND = 3
        const val EXIT_KEY_EXISTS = 4
        const val EXIT_ERROR = 5
        const val EXIT_USAGE = 64
    }

    fun isAvailable(): Boolean = Files.isRegularFile(exe) && run(listOf("available")).ok

    fun available(): Response = run(listOf("available"))

    /** Yes/no Hello prompt. [hwnd] = owner window (Compose Desktop: `ComposeWindow.windowHandle`). */
    fun verify(reason: String, hwnd: Long? = null): Response =
        run(listOf("verify", sanitize(reason)) + hwndArgs(hwnd))

    fun createKey(name: String, replace: Boolean = false): Response =
        run(listOf("create-key", name) + if (replace) listOf("--replace") else emptyList())

    /** Returns the 32-byte key derived from the Hello signature, or null (see [Response.status] for why). */
    fun deriveKey(name: String, challenge: ByteArray? = null): Pair<ByteArray?, Response> {
        val args = mutableListOf("derive-key", name)
        if (challenge != null) args += listOf("--challenge", b64(challenge))
        val r = run(args)
        return (if (r.ok) r.str("key")?.let(::unb64) else null) to r
    }

    fun deleteKey(name: String): Response = run(listOf("delete-key", name))

    fun ngcCreate(name: String, hwnd: Long? = null, reason: String? = null, replace: Boolean = false): Response =
        run(listOf("ngc-create", name) + hwndArgs(hwnd) + reasonArgs(reason) + if (replace) listOf("--replace") else emptyList())

    /** Wraps [secret] with the public half of the NGC key (no prompt). Returns the base64 blob to persist. */
    fun ngcWrap(name: String, secret: ByteArray): Pair<String?, Response> {
        val r = run(listOf("ngc-wrap", name), stdin = b64(secret))
        return (if (r.ok) r.str("blob") else null) to r
    }

    /** Unwraps [blob] with the Hello-protected private key (prompt). */
    fun ngcUnwrap(name: String, blob: String, hwnd: Long? = null, reason: String? = null): Pair<ByteArray?, Response> {
        val r = run(listOf("ngc-unwrap", name) + hwndArgs(hwnd) + reasonArgs(reason), stdin = blob)
        return (if (r.ok) r.str("secret")?.let(::unb64) else null) to r
    }

    fun ngcDelete(name: String): Response = run(listOf("ngc-delete", name))

    // ---------------------------------------------------------------------------------------------

    private fun run(args: List<String>, stdin: String? = null): Response {
        val p = ProcessBuilder(listOf(exe.toString()) + args)
            .redirectErrorStream(false)
            .start()
        p.outputStream.use { os -> if (stdin != null) os.write((stdin + "\n").toByteArray(StandardCharsets.UTF_8)) }
        // Drain stderr on a separate thread so neither pipe can block.
        var err = ""
        val errThread = Thread { err = p.errorStream.readBytes().toString(StandardCharsets.UTF_8) }.apply { start() }
        val out = p.inputStream.readBytes().toString(StandardCharsets.UTF_8)
        if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            return Response(EXIT_ERROR, mapOf("status" to "Timeout"), "")
        }
        errThread.join(1000)
        val fields = runCatching { MiniJson.parseObject(out.trim()) }
            .getOrElse { mapOf("status" to "BadOutput", "raw" to out) }
        return Response(p.exitValue(), fields, err)
    }

    private fun hwndArgs(hwnd: Long?) = if (hwnd != null && hwnd != 0L) listOf("--hwnd", hwnd.toString()) else emptyList()
    private fun reasonArgs(reason: String?) = if (reason != null) listOf("--reason", sanitize(reason)) else emptyList()

    /** Windows command-line quoting from the JVM is fragile with embedded quotes; keep prompt text plain. */
    private fun sanitize(s: String) = s.replace("\"", "'").replace("\\", "/")

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String) = Base64.getDecoder().decode(s)
}

/** Just enough JSON for the helper's flat, single-object output (strings, numbers, booleans, null). */
internal object MiniJson {
    fun parseObject(s: String): Map<String, Any?> {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun expect(c: Char) { ws(); require(i < s.length && s[i] == c) { "expected '$c' at $i" }; i++ }
        fun str(): String {
            expect('"')
            val sb = StringBuilder()
            while (s[i] != '"') {
                val c = s[i++]
                if (c != '\\') { sb.append(c); continue }
                when (val e = s[i++]) {
                    'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                    'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                    'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                    else -> sb.append(e)
                }
            }
            i++
            return sb.toString()
        }
        fun value(): Any? {
            ws()
            return when {
                s[i] == '"' -> str()
                s.startsWith("true", i) -> { i += 4; true }
                s.startsWith("false", i) -> { i += 5; false }
                s.startsWith("null", i) -> { i += 4; null }
                else -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] in "-+.eE")) i++
                    s.substring(start, i).toLong()
                }
            }
        }
        val map = LinkedHashMap<String, Any?>()
        expect('{')
        ws()
        if (s[i] == '}') return map
        while (true) {
            val k = str(); expect(':'); map[k] = value(); ws()
            if (s[i] == ',') { i++; continue }
            expect('}'); return map
        }
    }
}
