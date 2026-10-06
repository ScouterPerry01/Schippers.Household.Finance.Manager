package ca.schippers.hfm.desktop

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * NFR-08 (WCAG 2.1 AA, 1.4.3): hint text, drawn in the theme's outline colour, and the charts'
 * muted text reach 4.5:1 on every surface they sit on, in the light and the dark theme.
 */
class ContrastTest {

    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb) / minOf(la, lb)
    }

    private fun surfaces(s: ColorScheme) = mapOf(
        "background" to s.background, "surface" to s.surface, "surfaceVariant" to s.surfaceVariant,
        "surfaceContainerLowest" to s.surfaceContainerLowest, "surfaceContainerLow" to s.surfaceContainerLow,
        "surfaceContainer" to s.surfaceContainer, "surfaceContainerHigh" to s.surfaceContainerHigh,
        "surfaceContainerHighest" to s.surfaceContainerHighest,
    )

    private fun check(name: String, text: Color, on: Map<String, Color>): List<String> {
        val lines = on.map { (surface, color) -> "%s on %s: %.2f:1".format(name, surface, ratio(text, color)) to ratio(text, color) }
        lines.forEach { println(it.first) }
        return lines.filter { it.second < 4.5 }.map { it.first }
    }

    @Test
    fun `hint text reaches 4,5 to 1 in both themes`() {
        val failures = ArrayList<String>()
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            val scheme = appColorScheme(dark)
            failures += check("$theme outline", scheme.outline, surfaces(scheme))
            failures += check("$theme onSurfaceVariant", scheme.onSurfaceVariant, surfaces(scheme))
            val chart = ChartColors(dark)
            failures += check("$theme chart muted", chart.muted, surfaces(scheme) + ("chart surface" to chart.surface))
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `the defaults the fix replaces`() {
        // For the record (docs/phase5-exit-check.md): Material's own outline colours.
        check("default light outline", lightColorScheme().outline, surfaces(lightColorScheme()))
        check("default dark outline", darkColorScheme().outline, surfaces(darkColorScheme()))
        check("old chart muted light", Color(0xFF898781), mapOf("chart surface" to ChartColors(false).surface))
        check("old chart muted dark", Color(0xFF898781), mapOf("chart surface" to ChartColors(true).surface))
    }
}
