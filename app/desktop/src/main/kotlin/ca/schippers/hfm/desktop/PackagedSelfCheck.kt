package ca.schippers.hfm.desktop

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.ocr.desktop.Heif
import ca.schippers.hfm.ocr.desktop.PaddleOcrEngine
import ca.schippers.hfm.security.KdfParams
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Checks that an installed package can still load its native libraries, since packaging keeps only
 * the target platform's (ADR 0004), and only the JDK modules listed in the build: those modules, text recognition (ONNX Runtime), an encrypted household
 * (SQLite3 Multiple Ciphers), and HEIC photos when the computer has a HEIC decoder (the user installs
 * it; "not installed" is reported, not a failure). Run by the release workflow on each package it builds, as
 * `JAVA_TOOL_OPTIONS=-Dhfm.selfcheck=<report file>` before starting the installed app; it writes
 * the report and exits, without showing a window. No effect otherwise.
 */
internal object PackagedSelfCheck {

    fun runIfRequested() {
        val report = System.getProperty("hfm.selfcheck")?.let(Path::of) ?: return
        val lines = mutableListOf<String>()
        val ok = runCatching {
            lines += "runtime: " + runtime()
            lines += "ocr: " + ocr()
            lines += "heic: " + heic()
            lines += "ai: " + ca.schippers.hfm.ai.ClaudeProvider.selfCheck()
            lines += "database: " + database()
        }.onFailure { lines += "FAILED: $it" }.isSuccess
        Files.write(report, lines + if (ok) "OK" else "FAILED")
        exitProcess(if (ok) 0 else 1)
    }

    /**
     * One class from each JDK module the packaging keeps beyond the base ones (`modules(...)` in
     * build.gradle.kts). A module left out fails only when its first class is used, so the window, the
     * update check or the phone listener would break on the user's computer although the rest passed.
     */
    private fun runtime(): String {
        val classes = listOf(
            "java.lang.instrument.Instrumentation", // java.instrument
            "javax.naming.Context", // java.naming
            "java.net.http.HttpClient", // java.net.http: rates, prices, the update check
            "java.util.prefs.Preferences", // java.prefs
            "java.sql.Connection", // java.sql
            "com.sun.net.httpserver.HttpServer", // jdk.httpserver: the listener for phones
            "sun.misc.Unsafe", // jdk.unsupported
        )
        classes.forEach { Class.forName(it) }
        // Made as the window opens; its HTTP client stopped the app there when java.net.http was missing.
        Class.forName("ca.schippers.hfm.desktop.GitHubSource")
        return "${classes.size} JDK modules present"
    }

    /** A receipt line saved as a picture, so the check needs no system fonts (a bare Linux has none). */
    private fun ocr(): String {
        val image = checkNotNull(PackagedSelfCheck::class.java.getResourceAsStream("/hfm/selfcheck.png")).use { it.readBytes() }
        val text = PaddleOcrEngine().use { it.recognize(image).text }
        check("12.34" in text) { "OCR read \"$text\"" }
        return text
    }

    /** The same receipt line saved as a HEIC photo, read through the system's decoder when there is one. */
    private fun heic(): String {
        if (!Heif.available) return Heif.describe()
        val image = checkNotNull(PackagedSelfCheck::class.java.getResourceAsStream("/hfm/selfcheck.heic")).use { it.readBytes() }
        val text = PaddleOcrEngine().use { it.recognize(image).text }
        check("12.34" in text) { "OCR read \"$text\" from the HEIC photo" }
        return "${Heif.describe()}, read \"$text\""
    }

    private fun database(): String {
        val temp = Files.createTempDirectory("hfm-selfcheck")
        try {
            val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
            val dir = temp.resolve("check.hfm")
            store.create(dir, "Check", "check", "Check", "self-check-password".toCharArray()).session.close()
            store.unlock(dir, "check", "self-check-password".toCharArray()).use { session ->
                check(session.core.coreQueries.household().executeAsOne().name == "Check")
            }
            return "created and reopened"
        } finally {
            temp.toFile().deleteRecursively()
        }
    }
}
