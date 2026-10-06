package ca.schippers.hfm.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Chart colours from the validated reference palette (dataviz skill): categorical slots in fixed
 * order, stepped separately for light and dark surfaces, recessive grid and axes, and the reserved
 * status colour for "over budget", always shown with a text label.
 */
class ChartColors(val dark: Boolean) {
    private val light = listOf(0xFF2A78D6, 0xFFEB6834, 0xFF1BAF7A, 0xFFEDA100, 0xFFE87BA4, 0xFF008300, 0xFF4A3AA7, 0xFFE34948)
    private val darkSteps = listOf(0xFF3987E5, 0xFFD95926, 0xFF199E70, 0xFFC98500, 0xFFD55181, 0xFF008300, 0xFF9085E9, 0xFFE66767)

    fun series(slot: Int): Color = Color((if (dark) darkSteps else light)[slot.coerceIn(0, 7)])
    val grid = Color(if (dark) 0xFF2C2C2A else 0xFFE1E0D9)
    val axis = Color(if (dark) 0xFF383835 else 0xFFC3C2B7)
    // Axis labels and notes are text: stepped per theme to reach 4.5:1 (NFR-08, ContrastTest).
    val muted = Color(if (dark) 0xFFBAB8B1 else 0xFF64625C)
    val ink = Color(if (dark) 0xFFFFFFFF else 0xFF0B0B0B)
    val critical = Color(0xFFD03B3B)
    val surface = Color(if (dark) 0xFF1A1A19 else 0xFFFCFCFB)
}

@Composable
fun chartColors(): ChartColors {
    val dark = LocalDarkTheme.current
    return remember(dark) { ChartColors(dark) }
}

/** One series of a bar or line chart; [labels] are the formatted values for tooltips. */
data class Series(val name: String, val values: List<Double>, val labels: List<String>)

@Composable
fun Legend(names: List<String>, colors: ChartColors) {
    if (names.size < 2) return
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        names.forEachIndexed { i, name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(colors.series(i), RoundedCornerShape(2.dp)))
                Text(name, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Round axis steps: 1, 2 or 5 times a power of ten. */
private fun niceTicks(minValue: Double, maxValue: Double, count: Int = 5): List<Double> {
    val lo = min(0.0, minValue)
    val hi = max(0.0, maxValue).let { if (it == lo) lo + 1 else it }
    val raw = (hi - lo) / count
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 5.0, 10.0).map { it * magnitude }.first { it >= raw }
    val start = floor(lo / step) * step
    val end = ceil(hi / step) * step
    return generateSequence(start) { it + step }.takeWhile { it <= end + step / 2 }.toList()
}

/**
 * Bars for several series per period, e.g. income vs expense per month (section 12). One y-axis,
 * a hover tooltip per period, click to drill down into a period and series.
 */
@Composable
fun GroupedBarChart(
    periods: List<String>,
    series: List<Series>,
    axisFormat: (Double) -> String,
    modifier: Modifier = Modifier,
    onClick: ((period: Int, series: Int) -> Unit)? = null,
) {
    val colors = chartColors()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var hover by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    val all = series.flatMap { it.values }
    val ticks = niceTicks(all.minOrNull() ?: 0.0, all.maxOrNull() ?: 1.0)
    val axisStyle = TextStyle(fontSize = 11.sp, color = colors.muted)

    Column(modifier) {
        Legend(series.map { it.name }, colors)
        Box(Modifier.fillMaxWidth().height(260.dp).padding(top = 8.dp)) {
            var layout by remember { mutableStateOf(BarLayout(0f, 0f, 0f, 0f)) }
            Canvas(
                Modifier.fillMaxWidth().height(260.dp)
                    .pointerInput(periods, series) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val position = event.changes.first().position
                                when (event.type) {
                                    PointerEventType.Exit -> hover = null
                                    PointerEventType.Move -> hover = layout.periodAt(position.x, periods.size)?.let { it to position }
                                    PointerEventType.Release -> layout.periodAt(position.x, periods.size)?.let { p ->
                                        val s = layout.seriesAt(position.x, p, series.size)
                                        if (s != null) onClick?.invoke(p, s)
                                    }
                                }
                            }
                        }
                    },
            ) {
                val left = 64.dp.toPx()
                val bottom = size.height - 22.dp.toPx()
                val top = 6.dp.toPx()
                val lo = ticks.first()
                val hi = ticks.last()
                fun y(v: Double) = (bottom - (v - lo) / (hi - lo) * (bottom - top)).toFloat()
                layout = BarLayout(left, size.width, top, bottom)

                for (t in ticks) {
                    val ty = y(t)
                    drawLine(if (t == 0.0) colors.axis else colors.grid, Offset(left, ty), Offset(size.width, ty), strokeWidth = 1f)
                    val text = measurer.measure(axisFormat(t), axisStyle)
                    drawText(text, topLeft = Offset(left - text.size.width - 6.dp.toPx(), ty - text.size.height / 2))
                }
                val groupWidth = (size.width - left) / max(1, periods.size)
                val gap = 2.dp.toPx()
                val barWidth = min(28.dp.toPx(), (groupWidth * 0.7f - gap * (series.size - 1)) / max(1, series.size))
                periods.forEachIndexed { p, label ->
                    val groupStart = left + p * groupWidth + (groupWidth - (barWidth * series.size + gap * (series.size - 1))) / 2
                    if (hover?.first == p) drawRect(colors.grid.copy(alpha = 0.5f), Offset(left + p * groupWidth, top), Size(groupWidth, bottom - top))
                    series.forEachIndexed { s, ser ->
                        val v = ser.values.getOrElse(p) { 0.0 }
                        val x = groupStart + s * (barWidth + gap)
                        roundedBar(colors.series(s), x, y(0.0), y(v), barWidth)
                    }
                    val text = measurer.measure(label, axisStyle)
                    if (text.size.width < groupWidth - 4 || p % 2 == 0) {
                        drawText(text, topLeft = Offset(left + p * groupWidth + (groupWidth - text.size.width) / 2, bottom + 4.dp.toPx()))
                    }
                }
            }
            hover?.let { (p, position) ->
                Tooltip(position, density) {
                    Text(periods[p], fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    series.forEachIndexed { s, ser -> TooltipRow(colors.series(s), ser.name, ser.labels.getOrElse(p) { "" }) }
                }
            }
        }
    }
}

private class BarLayout(val left: Float, val right: Float, val top: Float, val bottom: Float) {
    fun periodAt(x: Float, count: Int): Int? {
        if (x < left || x > right || count == 0) return null
        return ((x - left) / ((right - left) / count)).toInt().coerceIn(0, count - 1)
    }

    fun seriesAt(x: Float, period: Int, count: Int): Int? {
        val groupWidth = (right - left) / count.coerceAtLeast(1)
        val within = (x - left - period * groupWidth) / groupWidth
        return if (count == 0) null else (within * count).toInt().coerceIn(0, count - 1)
    }
}

/** A bar with 4px rounded corners at the data end only, anchored square on the baseline. */
private fun DrawScope.roundedBar(color: Color, x: Float, baseY: Float, valueY: Float, width: Float) {
    val topY = min(baseY, valueY)
    val height = abs(baseY - valueY)
    if (height < 0.5f) return
    val r = min(4.dp.toPx(), height)
    val path = Path().apply {
        if (valueY <= baseY) {
            moveTo(x, baseY)
            lineTo(x, topY + r)
            quadraticTo(x, topY, x + r, topY)
            lineTo(x + width - r, topY)
            quadraticTo(x + width, topY, x + width, topY + r)
            lineTo(x + width, baseY)
        } else {
            moveTo(x, baseY)
            lineTo(x, valueY - r)
            quadraticTo(x, valueY, x + r, valueY)
            lineTo(x + width - r, valueY)
            quadraticTo(x + width, valueY, x + width, valueY - r)
            lineTo(x + width, baseY)
        }
        close()
    }
    drawPath(path, color)
}

/**
 * A line per series over time (net worth, forecast). Crosshair and tooltip follow the pointer;
 * an optional [threshold] draws a reference line (e.g. zero for a low-balance warning).
 */
@Composable
fun LineChart(
    labels: List<String>,
    series: List<Series>,
    axisFormat: (Double) -> String,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 240.dp,
    threshold: Double? = null,
) {
    val colors = chartColors()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var hover by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    val all = series.flatMap { it.values } + listOfNotNull(threshold)
    val ticks = niceTicks(all.minOrNull() ?: 0.0, all.maxOrNull() ?: 1.0, 4)
    val axisStyle = TextStyle(fontSize = 11.sp, color = colors.muted)

    Column(modifier) {
        Legend(series.map { it.name }, colors)
        Box(Modifier.fillMaxWidth().height(height).padding(top = 8.dp)) {
            var plot by remember { mutableStateOf(0f to 1f) }
            Canvas(
                Modifier.fillMaxWidth().height(height).pointerInput(labels) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val position = event.changes.first().position
                            val (left, width) = plot
                            hover = if (event.type == PointerEventType.Exit || labels.size < 2 || position.x < left) {
                                null
                            } else {
                                ((position.x - left) / width * (labels.size - 1)).roundToInt().coerceIn(0, labels.size - 1) to position
                            }
                        }
                    }
                },
            ) {
                val left = 64.dp.toPx()
                val bottom = size.height - 22.dp.toPx()
                val top = 6.dp.toPx()
                val width = size.width - left - 8.dp.toPx()
                plot = left to width
                val lo = ticks.first()
                val hi = ticks.last()
                fun y(v: Double) = (bottom - (v - lo) / (hi - lo) * (bottom - top)).toFloat()
                fun x(i: Int) = if (labels.size < 2) left + width / 2 else left + width * i / (labels.size - 1)

                for (t in ticks) {
                    drawLine(if (t == 0.0) colors.axis else colors.grid, Offset(left, y(t)), Offset(size.width, y(t)), 1f)
                    val text = measurer.measure(axisFormat(t), axisStyle)
                    drawText(text, topLeft = Offset(left - text.size.width - 6.dp.toPx(), y(t) - text.size.height / 2))
                }
                threshold?.let { drawLine(colors.critical, Offset(left, y(it)), Offset(size.width, y(it)), 1.5f) }
                val every = max(1, ceil(labels.size / 8.0).toInt())
                labels.forEachIndexed { i, label ->
                    // Every n-th label, plus the last one, never two labels too close together.
                    if (i == labels.lastIndex || i % every == 0 && labels.lastIndex - i >= every) {
                        val text = measurer.measure(label, axisStyle)
                        drawText(text, topLeft = Offset((x(i) - text.size.width / 2).coerceIn(left, size.width - text.size.width), bottom + 4.dp.toPx()))
                    }
                }
                hover?.let { (i, _) -> drawLine(colors.axis, Offset(x(i), top), Offset(x(i), bottom), 1f) }
                series.forEachIndexed { s, ser ->
                    if (ser.values.isEmpty()) return@forEachIndexed
                    val path = Path()
                    ser.values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
                    drawPath(path, colors.series(s), style = Stroke(width = 2.dp.toPx()))
                    hover?.let { (i, _) ->
                        ser.values.getOrNull(i)?.let { v ->
                            drawCircle(colors.surface, 6.dp.toPx(), Offset(x(i), y(v)))
                            drawCircle(colors.series(s), 4.dp.toPx(), Offset(x(i), y(v)))
                        }
                    }
                }
            }
            hover?.let { (i, position) ->
                Tooltip(position, density) {
                    Text(labels[i], fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    series.forEachIndexed { s, ser -> TooltipRow(colors.series(s), ser.name, ser.labels.getOrElse(i) { "" }) }
                }
            }
        }
    }
}

/** A labelled value bar for ranked lists (spending by category or payee, budgets). */
data class RankedBar(
    val label: String,
    val value: Double,
    val valueText: String,
    /** Budget target: drawn as a marker line on the bar. */
    val target: Double? = null,
    val note: String? = null,
    /** Over budget: drawn in the status colour, with [note] as its text label. */
    val alert: Boolean = false,
)

/**
 * Horizontal bars, largest first, with direct value labels. Clicking a bar drills down (RPT-01).
 * The bar's tooltip is its own row of text, so identity never depends on colour.
 */
@Composable
fun RankedBars(bars: List<RankedBar>, modifier: Modifier = Modifier, slot: Int = 0, onClick: ((Int) -> Unit)? = null) {
    val colors = chartColors()
    val maxValue = bars.maxOfOrNull { max(it.value, it.target ?: 0.0) }?.takeIf { it > 0 } ?: 1.0
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        bars.forEachIndexed { i, bar ->
            var hovered by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth()
                    .background(if (hovered) colors.grid.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(4.dp))
                    .let { if (onClick != null) it.clickable { onClick(i) } else it }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent()
                                if (e.type == PointerEventType.Enter) hovered = true
                                if (e.type == PointerEventType.Exit) hovered = false
                            }
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(bar.label, Modifier.width(220.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Canvas(Modifier.weight(1f).height(18.dp)) {
                    // A faint track up to the target, so an empty budget still reads as "0 of X".
                    bar.target?.let { t -> roundedBarHorizontal(colors.grid, (t / maxValue * size.width).toFloat(), size.height * 0.7f, size.height) }
                    val w = (bar.value.coerceAtLeast(0.0) / maxValue * size.width).toFloat()
                    roundedBarHorizontal(if (bar.alert) colors.critical else colors.series(slot), w, size.height * 0.7f, size.height)
                    bar.target?.let { t ->
                        val tx = (t / maxValue * size.width).toFloat()
                        drawLine(colors.ink, Offset(tx, 0f), Offset(tx, size.height), 2.dp.toPx())
                    }
                }
                Column(Modifier.width(190.dp).padding(start = 8.dp), horizontalAlignment = Alignment.End) {
                    Text(bar.valueText, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                    bar.note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = if (bar.alert) colors.critical else colors.muted)
                    }
                }
            }
        }
    }
}

private fun DrawScope.roundedBarHorizontal(color: Color, width: Float, barHeight: Float, rowHeight: Float) {
    if (width < 0.5f) return
    val top = (rowHeight - barHeight) / 2
    val r = min(4.dp.toPx(), width)
    val path = Path().apply {
        moveTo(0f, top)
        lineTo(width - r, top)
        quadraticTo(width, top, width, top + r)
        lineTo(width, top + barHeight - r)
        quadraticTo(width, top + barHeight, width - r, top + barHeight)
        lineTo(0f, top + barHeight)
        close()
    }
    drawPath(path, color)
}

@Composable
private fun Tooltip(position: Offset, density: androidx.compose.ui.unit.Density, content: @Composable () -> Unit) {
    val offset = with(density) { IntOffset((position.x + 12.dp.toPx()).roundToInt(), (position.y - 8.dp.toPx()).roundToInt()) }
    Surface(
        modifier = Modifier.offset { offset },
        shape = RoundedCornerShape(6.dp),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
}

@Composable
private fun TooltipRow(color: Color, name: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Text(name, Modifier.padding(start = 6.dp).width(110.dp), style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

/** Compact axis labels: 12 500 → "12,5 k". */
fun compactNumber(value: Double, locale: java.util.Locale): String {
    val nf = java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    return when {
        abs(value) >= 1_000_000 -> nf.format(value / 1_000_000) + " M"
        abs(value) >= 1_000 -> nf.format(value / 1_000) + " k"
        else -> nf.format(value)
    }
}
