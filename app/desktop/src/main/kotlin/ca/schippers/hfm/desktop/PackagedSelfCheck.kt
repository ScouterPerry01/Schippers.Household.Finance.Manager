package ca.schippers.hfm.desktop

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.ocr.desktop.PaddleOcrEngine
import ca.schippers.hfm.security.KdfParams
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Checks that an installed package can still load its native libraries, since packaging keeps only
 * the target platform's (ADR 0004): text recognition (ONNX Runtime) and an encrypted household
 * (SQLite3 Multiple Ciphers). Run by the release workflow on each package it builds, as
 * `JAVA_TOOL_OPTIONS=-Dhfm.selfcheck=<report file>` before starting the installed app; it writes
 * the report and exits, without showing a window. No effect otherwise.
 */
internal object PackagedSelfCheck {

    fun runIfRequested() {
        val report = System.getProperty("hfm.selfcheck")?.let(Path::of) ?: return
        val lines = mutableListOf<String>()
        val ok = runCatching {
            lines += "ocr: " + ocr()
            lines += "database: " + database()
        }.onFailure { lines += "FAILED: $it" }.isSuccess
        Files.write(report, lines + if (ok) "OK" else "FAILED")
        exitProcess(if (ok) 0 else 1)
    }

    private fun ocr(): String {
        val image = BufferedImage(520, 90, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            color = Color.WHITE
            fillRect(0, 0, image.width, image.height)
            color = Color.BLACK
            font = Font(Font.SANS_SERIF, Font.BOLD, 40)
            drawString("TOTAL 12.34", 30, 60)
            dispose()
        }
        val text = PaddleOcrEngine().use { it.recognize(image).text }
        check("12.34" in text) { "OCR read \"$text\"" }
        return text
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
