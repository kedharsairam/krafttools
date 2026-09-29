package com.krafttools.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme

/**
 * The speed gauge.
 *
 * The old one was clipped by a quarter. It drew a 240-degree arc with
 * `r = 0.62 * minDimension` about a pivot at `cy = 0.92 * height`,
 * on a canvas 110dp tall — so `2r` needed 136dp of vertical room and
 * 101dp existed. The lowest 25dp of the arc, both end ticks, and the
 * needle at exactly 0 and at full scale were all drawn off the bottom
 * of the panel.
 *
 * The fix is to derive the radius from the space actually available
 * above the pivot, rather than from the width and hoping.
 */
@Composable
fun SpeedGauge(
    fraction: Float,
    modifier: Modifier = Modifier,
    fullScale: Float,
    unit: String,
) {
    val measurer = rememberTextMeasurer()
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val line = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val ring = MaterialTheme.colorScheme.outline
    val hub = MaterialTheme.colorScheme.surface

    // A real needle has mass. Without the spring the number jitters in
    // its last digit and the needle vibrates with it.
    val needle = remember { Animatable(fraction) }
    LaunchedEffect(fraction) {
        needle.animateTo(
            targetValue = fraction.coerceIn(0f, 1f),
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 260f),
        )
    }

    Canvas(modifier = modifier) {
        val cx = size.width / 2f

        // The arc's fit is solved in SpeedMath.dialGeometry, where a
        // test can prove it; see the note there on why a 240-degree
        // dial needs 1.5r of height and not r.
        val topInset = 4.dp.toPx()
        // Room for the unit caption pinned to the bottom edge.
        val bottomInset = 14.dp.toPx()
        val dial = SpeedMath.dialGeometry(
            size.width,
            size.height,
            topInset,
            bottomInset,
        )
        val radius = dial.radius
        val pivotY = dial.pivotY
        val thickness = dial.strokeWidth
        val startAngle = SpeedMath.DIAL_START_ANGLE
        val sweep = SpeedMath.DIAL_SWEEP

        // Track.
        drawArc(
            color = track,
            startAngle = startAngle,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(cx - radius, pivotY - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = thickness),
        )
        // Filled portion, up to the needle.
        drawArc(
            brush = Brush.sweepGradient(
                listOf(line.copy(alpha = 0.55f), line),
                center = Offset(cx, pivotY),
            ),
            startAngle = startAngle,
            sweepAngle = sweep * needle.value,
            useCenter = false,
            topLeft = Offset(cx - radius, pivotY - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = thickness),
        )

        // Ticks and labels. Every `labelEvery`, with a minor tick in
        // between: an ungraduated dial is a picture.
        val step = fullScale / 12f
        var v = 0f
        var i = 0
        while (v <= fullScale + 0.001f) {
            val a = Math.toRadians((startAngle + sweep * (v / fullScale)).toDouble())
            val major = (i % 2) == 0
            val r1 = radius - thickness * 0.7f
            val r2 = if (major) radius - thickness * 1.5f else radius - thickness * 1.1f
            drawLine(
                color = if (major) ring else ring.copy(alpha = 0.5f),
                start = Offset(
                    cx + (r1 * kotlin.math.cos(a)).toFloat(),
                    pivotY + (r1 * kotlin.math.sin(a)).toFloat(),
                ),
                end = Offset(
                    cx + (r2 * kotlin.math.cos(a)).toFloat(),
                    pivotY + (r2 * kotlin.math.sin(a)).toFloat(),
                ),
                strokeWidth = if (major) 3f else 1.5f,
            )
            if (major) {
                val text = v.toInt().toString()
                val lr = r2 - radius * 0.10f
                val layout = measurer.measure(
                    text,
                    TextStyle(color = ink, fontSize = 10.sp),
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        cx + (lr * kotlin.math.cos(a)).toFloat() - layout.size.width / 2f,
                        pivotY + (lr * kotlin.math.sin(a)).toFloat() - layout.size.height / 2f,
                    ),
                )
            }
            v += step
            i++
        }

        // The needle. The rotation is solved in SpeedMath.needleRotation
        // because it is 270 degrees easy to get wrong: the line is drawn
        // pointing UP, and the arc angle it has to reach is measured from
        // the positive x axis, not from vertical.
        rotate(
            degrees = SpeedMath.needleRotation(needle.value),
            pivot = Offset(cx, pivotY),
        ) {
            drawLine(
                color = line,
                start = Offset(cx, pivotY - radius * 0.22f),
                end = Offset(cx, pivotY - radius * 0.94f),
                strokeWidth = 5f,
            )
        }
        drawCircle(color = hub, radius = radius * 0.09f, center = Offset(cx, pivotY))
        drawCircle(color = line, radius = radius * 0.09f, center = Offset(cx, pivotY), style = Stroke(2.5f))
        drawCircle(color = line, radius = radius * 0.03f, center = Offset(cx, pivotY))

    }
}
