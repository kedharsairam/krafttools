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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
 * The shared trace graph: gradient fill under the line, gridlines,
 * peak dot. Vibration, barometer (and anything with a history) all
 * draw through here — one look, one behavior.
 */
@Composable
fun TraceGraph(
    values: List<Float>,
    modifier: Modifier = Modifier,
    max: Float? = null,
    peak: Float? = null,
) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val ceiling = max ?: (values.maxOrNull() ?: 1f).coerceAtLeast(0.001f)
        val norm = { v: Float -> (v / ceiling).coerceIn(0f, 1f) }
        // Horizontal gridlines: quiet structure, not decoration.
        for (f in listOf(0.25f, 0.5f, 0.75f)) {
            val y = size.height * (1f - f)
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 2f)
        }
        val stepX = size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v ->
            Offset(i * stepX, size.height * (1f - norm(v)))
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
            drawLine(line, pts[i - 1], pts[i], 5f)
        }
        // Peak dot: the one number that matters in a trace.
        if (peak != null) {
            val idx = values.indexOf(values.maxOrNull() ?: peak)
            if (idx >= 0) {
                drawCircle(
                    color = line,
                    radius = 9f,
                    center = Offset(idx * stepX, size.height * (1f - norm(values[idx]))),
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.55f),
                    radius = 9f,
                    center = Offset(idx * stepX, size.height * (1f - norm(values[idx]))),
                    style = Stroke(3f),
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
