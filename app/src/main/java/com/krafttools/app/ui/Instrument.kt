package com.krafttools.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * The shared reading header: every measurement tool speaks here.
 * Giant tabular numeral (never jitters) + unit + one status word.
 * Consistency across fourteen tools IS the instrument-panel feel.
 */
@Composable
fun ReadingHeader(
    value: String,
    unit: String?,
    status: String?,
    live: Boolean = true,
    mirror: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (unit != null) "$value $unit" else value,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = if (live) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.graphicsLayer {
                // HUD mirror mode (speedometer windshield use).
                scaleX = if (mirror) -1f else 1f
            },
        )
        if (status != null) {
            Text(
                text = status.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Auto-ranging ceiling, the way a bench meter behaves: snap up to
 * cover the signal instantly, then fall back slowly so a quiet trace
 * uses the whole panel instead of hugging the floor. Steps are
 * 1/2/5 x 10^k so the printed scale is always a number someone can
 * read at a glance — never "0.037".
 */
class AutoScale(
    private val minCeiling: Float = 0.05f,
    private val decayPerSecond: Float = 0.6f,
    private val headroom: Float = 1.35f,
) {
    var ceiling: Float = minCeiling
        private set

    // Unquantized ceiling. Quantizing on the decay path is what pins
    // the scale: niceCeiling(99.9) is 100, and 100 decays back to 100,
    // so the range never recovers after a spike. The raw value decays
    // smoothly and only the printed ceiling snaps to a 1/2/5 rung.
    private var raw: Float = minCeiling

    /** Feed the window's peak; returns the ceiling to draw against. */
    fun update(peak: Float, dtSec: Float): Float {
        val wanted = (peak * headroom).coerceAtLeast(0f)
        raw = if (wanted >= raw) {
            wanted
        } else {
            val factor = Math.pow(decayPerSecond.toDouble(), dtSec.toDouble()).toFloat()
            maxOf(raw * factor, minCeiling)
        }
        ceiling = maxOf(niceCeiling(raw), minCeiling)
        return ceiling
    }

    fun reset() {
        raw = minCeiling
        ceiling = minCeiling
    }
}

/**
 * How many decimals a scale of this span can afford. Returned as a
 * ready-made format string because `"%.*f".format(Locale.ROOT, n, v)` is a trap:
 * Kotlin's format takes Any?, so the Int lands in the value slot and
 * the precision star then reads it as a precision.
 */
private fun decimalsFor(span: Float): Int = when {
    span >= 10f -> 0
    span >= 1f -> 1
    span >= 0.1f -> 2
    else -> 3
}

/** Round up to the next 1, 2 or 5 times a power of ten. */
fun niceCeiling(v: Float): Float {
    if (v <= 0f || !v.isFinite()) return 1f
    val exp = Math.pow(10.0, Math.floor(Math.log10(v.toDouble()))).toFloat()
    val m = v / exp
    val nice = when {
        m <= 1f -> 1f
        m <= 2f -> 2f
        m <= 5f -> 5f
        else -> 10f
    }
    return nice * exp
}

/**
 * The shared trace graph: gradient fill under the line, a labelled
 * value gutter, gridlines, peak dot. Vibration, barometer, WiFi and
 * the sound spectrum all draw through here — one look, one behavior,
 * one scale. The gutter is not decoration: an unlabelled grid is a
 * picture, a labelled one is an instrument.
 */
@Composable
fun TraceGraph(
    values: List<Float>,
    modifier: Modifier = Modifier,
    max: Float? = null,
    min: Float? = null,
    peak: Float? = null,
    showZero: Boolean = false,
    logScale: Boolean = false,
) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = ink,
        fontSize = 10.sp,
        fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
    )
    Canvas(modifier = modifier) {
        // A non-zero floor is legitimate: a barometer's signal is a
        // small variation high up the range, and a zero-based axis
        // draws it as a flat line against the top edge.
        val ceiling = max ?: (values.maxOrNull() ?: 1f).coerceAtLeast(0.001f)
        val floor = min ?: if (showZero) 0f else ceiling * 0.25f
        val range = (ceiling - floor).takeIf { it > 1e-6f } ?: 1f
        val span0 = range
        val decimals0 = decimalsFor(span0)
        val ceilingText = "%.${decimals0}f".format(Locale.ROOT, ceiling)
        val zeroText = "0"
        val gutter = maxOf(
            measurer.measure(ceilingText, labelStyle).size.width.toFloat(),
            measurer.measure(zeroText, labelStyle).size.width.toFloat(),
        ) + 10.dp.toPx()
        val x0 = gutter
        val plotW = size.width - x0
        // A log axis is the right one for a quantity that spans
        // decades. The gridlines become 1/2/5 per decade rather than
        // even fractions of the span, which is also how light meters
        // are actually graduated.
        val logLo = kotlin.math.log10(maxOf(floor, 1e-3f))
        val logHi = kotlin.math.log10(maxOf(ceiling, maxOf(floor, 1e-3f) * 10f))
        val norm: (Float) -> Float = { v: Float ->
            if (logScale) {
                ((kotlin.math.log10(maxOf(v, 1e-3f)) - logLo) / (logHi - logLo))
                    .coerceIn(0f, 1f)
            } else {
                ((v - floor) / range).coerceIn(0f, 1f)
            }
        }

        // Horizontal gridlines: quiet structure, not decoration, each
        // one labelled in the gutter.
        // Decades, not every 1/2/5. A light meter is graduated by the
        // decade with minor marks inside it; printing a label at every
        // 2 and 5 as well gave seventeen labels and none of them
        // readable.
        val lines: List<Float>
        if (logScale) {
            val lo = maxOf(floor, 1e-3f)
            val hi = maxOf(ceiling, lo * 10f)
            val firstDecade = kotlin.math.floor(kotlin.math.log10(lo)).toInt()
            val lastDecade = kotlin.math.ceil(kotlin.math.log10(hi)).toInt()
            val ticks = mutableListOf<Float>()
            for (d in firstDecade..lastDecade) {
                val decade = Math.pow(10.0, d.toDouble()).toFloat()
                if (decade in lo..hi) ticks += decade
            }
            lines = ticks
        } else {
            lines = listOf(0.25f, 0.5f, 0.75f, 1f)
        }
        val span = range
        val decimals = decimalsFor(span)
        for (f in lines) {
            val y = size.height * (1f - norm(f))
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
            // Minor marks at 2x and 5x each decade, unlabelled.
            if (logScale) {
                for (m in listOf(2f, 5f)) {
                    val minor = f * m
                    if (minor > floor && minor < ceiling) {
                        val my = size.height * (1f - norm(minor))
                        drawLine(
                            grid.copy(alpha = 0.4f),
                            Offset(0f, my),
                            Offset(size.width, my),
                            1.5f,
                        )
                    }
                }
            }
            val text = if (f >= 1000f) {
                "%.0fk".format(Locale.ROOT, f / 1000f)
            } else if (f >= 1f) {
                "%.0f".format(Locale.ROOT, f)
            } else {
                "%.1f".format(Locale.ROOT, f)
            }
            val layout = measurer.measure(text, labelStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(x0 - 6.dp.toPx() - layout.size.width, y - layout.size.height / 2f),
            )
        }
        if (showZero && !logScale) {
            val layout = measurer.measure(zeroText, labelStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(x0 - 6.dp.toPx() - layout.size.width, size.height - layout.size.height),
            )
        }
        if (values.size < 2) return@Canvas

        val stepX = plotW / (values.size - 1)
        val pts = values.mapIndexed { i, v ->
            Offset(x0 + i * stepX, size.height * (1f - norm(v)))
        }
        // Gradient wash under the line first, line second.
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(pts.first().x, size.height)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, size.height)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(line.copy(alpha = 0.35f), line.copy(alpha = 0.02f)),
            ),
        )
        for (i in 1 until pts.size) {
            drawLine(line, pts[i - 1], pts[i], 5.dp.toPx())
        }
        // Peak dot: the one number that matters in a trace.
        if (peak != null) {
            val idx = values.indexOf(values.maxOrNull() ?: peak)
            if (idx >= 0) {
                drawCircle(
                    color = line,
                    radius = 9.dp.toPx(),
                    center = Offset(x0 + idx * stepX, size.height * (1f - norm(values[idx]))),
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.55f),
                    radius = 9.dp.toPx(),
                    center = Offset(x0 + idx * stepX, size.height * (1f - norm(values[idx]))),
                    style = Stroke(3.dp.toPx()),
                )
            }
        }
    }
}

/**
 * The house segmented-control palette. M3's stock active segment is a
 * flat filled pill; here the active segment is the instrument lit from
 * within — cyan text on tinted glass with a cyan bezel — so mode
 * selectors read as part of the same panel as the dial and the trace.
 */
@Composable
fun instrumentSegmentedColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
    activeContentColor = MaterialTheme.colorScheme.primary,
    activeBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
    inactiveContainerColor = Color.Transparent,
    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    inactiveBorderColor = MaterialTheme.colorScheme.outline,
)
