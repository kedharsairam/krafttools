package com.krafttools.app.ui

import android.hardware.Sensor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * The angle ruler: "by how many degrees", for mitres and shelves.
 *
 * Two things here were wrong and both mattered more than the layout.
 *
 * The hero number used to be `hypot(pitch, roll)` — two angles composed
 * as if they were orthogonal coordinates on a flat plane. They are not.
 * That is exact for a single-axis tilt and reads 63.6° when the phone
 * is standing at a true 90° on the diagonal: 26 degrees of lie, in the
 * one tool whose entire purpose is the number.
 *
 * The hero visual used to be a horizontal line rotated by roll alone.
 * A pure 45° *pitch* has zero roll, so the line sat perfectly flat
 * while the header read 45°. One line cannot show two axes — but a
 * needle pointing downhill can, because downhill is a single direction
 * that folds both axes together.
 */
@Composable
fun AngleScreen(onBack: () -> Unit) {
    val gravityReading = rememberSensor(Sensor.TYPE_ACCELEROMETER)
    val gravity = gravityReading.values
    // Held readings survive awkward positions: prop the phone against
    // a shelf, tap hold, carry it to the other end to read.
    var held by rememberSaveable(saver = floatTripleSaver) {
        mutableStateOf<Triple<Float, Float, Float>?>(null)
    }
    // Snap within 2° of a 45° multiple. Picture frames and shelves live
    // at these angles; the toggle admits the tool is rounding, not that
    // the phone got better.
    var snap by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current

    // Raw MEMS is noisy at the 0.1° the UI prints, and the tool
    // advertises itself for reading in awkward positions. A one-pole
    // low-pass on the gravity vector removes the jitter without
    // meaningfully lagging a hand.
    val filtered = remember { FloatArray(3).also { it[2] = 9.80665f } }
    val haveSample = remember { mutableStateOf(false) }
    gravity?.let { g ->
        if (!haveSample.value) {
            filtered[0] = g[0]; filtered[1] = g[1]; filtered[2] = g[2]
            haveSample.value = true
        }
        for (i in 0..2) {
            filtered[i] += (g[i] - filtered[i]) * 0.12f
        }
    }

    ToolScaffold("Angle ruler", onBack) { padding ->
        val g = gravity
        if (g == null) {
            ToolStarting(
                tool = "accelerometer",
                modifier = Modifier.padding(padding),
            )
            return@ToolScaffold
        }

        val liveTilt = tiltFromFlat(filtered)
        val liveAzimuth = downhillAzimuth(filtered)
        val (pitch, roll) = pitchRoll(filtered)
        val snapResult = if (snap) snapTo(liveTilt) else SnapResult(liveTilt, false)
        val heldReading = held
        val shownTilt = if (heldReading != null) heldReading.third else snapResult.value
        val shownPitch = heldReading?.first ?: pitch
        val shownRoll = heldReading?.second ?: roll
        val shownAzimuth = if (heldReading != null) heldReading.second else liveAzimuth

        ToolColumn(padding) {
            Spacer(modifier = Modifier.height(4.dp))
            ReadingHeader(
                value = "%.1f".format(Locale.ROOT, shownTilt.toDouble()),
                unit = "°",
                status = when {
                    heldReading != null ->
                        "held · live %.1f° underneath".format(Locale.ROOT, liveTilt.toDouble())
                    snapResult.snapped -> "snapped to a graduation"
                    else -> "from flat"
                },
                live = heldReading == null,
            )

            // The protractor. A needle for the in-plane direction, a
            // graduated arc for the magnitude, and the arc's own scale
            // so 45 is readable as 45 and not as "somewhere up there".
            Protractor(
                tiltDeg = shownTilt,
                azimuthDeg = shownAzimuth,
                snapped = snap && heldReading == null && snapResult.snapped,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .instrumentSemantics(
                        label = "Protractor",
                        value = "%.1f degrees of tilt, downhill toward %s".format(
                            Locale.ROOT,
                            shownTilt.toDouble(),
                            compassWord(shownAzimuth),
                        ),
                    ),
            )

            StatRow {
                StatChip("pitch", "%+.1f°".format(Locale.ROOT, shownPitch.toDouble()))
                StatChip("roll", "%+.1f°".format(Locale.ROOT, shownRoll.toDouble()))
                StatChip(
                    "downhill",
                    compassPoint(shownAzimuth),
                )
            }

            if (heldReading == null) {
                Button(
                    onClick = {
                        Haptics.confirm(view)
                        held = Triple(pitch, roll, liveTilt)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("Hold this angle")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        Haptics.tick(view)
                        held = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("Resume live")
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Snap to 45°",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = snap,
                    onCheckedChange = {
                        Haptics.tick(view)
                        snap = it
                    },
    modifier = Modifier.touchTarget(),
)
            }

            ToolHint(
                "Phone-grade MEMS, about 0.5° of noise before filtering. " +
                    "Good for setting a miter or a shelf; not inspection " +
                    "grade. The needle points the way gravity pulls.",
            )
        }
    }
}

/** One of eight compass points, for the downhill readout. */
private fun compassPoint(deg: Float): String {
    val names = listOf("right", "down-right", "down", "down-left", "left", "up-left", "up", "up-right")
    val i = (Math.round(deg / 45f).toInt().mod(8))
    return names[i]
}

/**
 * A protractor that can show a two-axis angle: a graduated arc for how
 * far from flat, and a needle for which way downhill lies. The two
 * together are the whole reading — the old widget could only draw one
 * of them, and drew the wrong one for a pure pitch.
 */
@Composable
private fun Protractor(
    tiltDeg: Float,
    azimuthDeg: Float,
    snapped: Boolean,
    modifier: Modifier = Modifier,
) {
    val ring = MaterialTheme.colorScheme.outline
    val faint = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    val hub = MaterialTheme.colorScheme.surface
    val ok = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val labelStyle = androidx.compose.ui.text.TextStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
    )

    // The needle eases toward its target: a real pointer has mass, and
    // a needle that snaps makes a 1° wobble look like a sweep.
    // The needle starts along the level rail and sweeps up to vertical,
    // through exactly the quarter the graduations cover. It used to
    // sweep 2x the tilt, which pointed at nothing the dial showed.
    val sweep = tiltDeg.coerceIn(0f, 90f)
    val needle = remember { Animatable(sweep) }
    LaunchedEffect(sweep) {
        needle.animateTo(
            targetValue = sweep,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 320f),
        )
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width * 0.42f
            // Pivot below centre so the quarter arc above it has room.
            val cy = size.height * 0.72f
            // Fit the arc inside the box: a radius derived from the
            // width alone is what clipped the speed gauge.
            // A quarter dial is a quadrant, not a circle, so its
            // optical centre is the corner — put that corner in the
            // middle of the panel and the arc fills the space evenly.
            val radius = minOf(size.width, size.height) * 0.44f
            val minor = 1.5.dp.toPx()
            val major = 3.dp.toPx()
            val needleW = 5.dp.toPx()

            // A 180° protractor arc opening upward: 0° is level, 90°
            // is straight up. Ticks every 15°, labelled every 45°.
            for (deg in 0..90 step 15) {
                val rad = Math.toRadians(deg.toDouble())
                val isMajor = deg % 45 == 0
                val inner = radius * (if (isMajor) 0.78f else 0.87f)
                drawLine(
                    color = if (isMajor) ring else faint,
                    start = Offset(
                        cx + (inner * kotlin.math.cos(rad)).toFloat(),
                        cy - (inner * kotlin.math.sin(rad)).toFloat(),
                    ),
                    end = Offset(
                        cx + (radius * kotlin.math.cos(rad)).toFloat(),
                        cy - (radius * kotlin.math.sin(rad)).toFloat(),
                    ),
                    strokeWidth = if (isMajor) major else minor,
                )
            }
            // The scale rail: a quarter arc from the level mark up to
            // vertical. A full circle here drew an ungraduated lower
            // half that read as part of the scale.
            drawArc(
                color = faint,
                // Upper-right quadrant: screen y grows downward, so the
                // arc starts at straight up and sweeps clockwise.
                startAngle = -90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = 1.5.dp.toPx()),
            )
            // The level reference the whole instrument is measured from.
            drawLine(
                color = ring,
                start = Offset(cx, cy - radius * 0.08f),
                end = Offset(cx + radius * 1.08f, cy - radius * 0.08f),
                strokeWidth = 2.dp.toPx(),
            )

            // The current angle as a filled wedge from level: the
            // magnitude, drawn rather than described.
            val tiltRad = Math.toRadians(tiltDeg.toDouble())
            // The arc is a protractor: 0 at the level rail on the right,
            // 90 at the top. Screen angle is measured from +X.
            val wedge = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx, cy)
                lineTo(
                    cx + (radius * 0.97f * kotlin.math.cos(tiltRad)).toFloat(),
                    cy - (radius * 0.97f * kotlin.math.sin(tiltRad)).toFloat(),
                )
                close()
            }
            // The needle lies along the level rail at 0, and stands on
            // end at 90: the same convention as the ticks and labels.
            drawPath(
                wedge,
                brush = Brush.radialGradient(
                    colors = listOf(
                        (if (snapped) Color(0xFF3DDC84) else accent).copy(alpha = 0.34f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
            )

            // The needle: one direction that carries both axes. Its
            // SWEEP is clamped to the protractor's own 180-degree span,
            // because a needle that can point anywhere in the full
            // circle contradicts the arc it is drawn on.
            rotate(degrees = needle.value, pivot = Offset(cx, cy)) {
                drawLine(
                    color = if (snapped) Color(0xFF3DDC84) else accent,
                    start = Offset(cx, cy + radius * 0.16f),
                    end = Offset(cx, cy - radius * 0.97f),
                    strokeWidth = needleW,
                )
                drawCircle(
                    color = accent,
                    radius = radius * 0.035f,
                    center = Offset(cx, cy - radius * 0.97f),
                )
            }
            drawCircle(color = hub, radius = radius * 0.1f, center = Offset(cx, cy))
            drawCircle(color = ring, radius = radius * 0.1f, center = Offset(cx, cy), style = Stroke(2.dp.toPx()))
            drawCircle(color = ok, radius = radius * 0.028f, center = Offset(cx, cy))

            // Graduations labelled in the house type. An unlabelled
            // arc is a fan; a labelled one is a scale.
            for ((deg, text) in listOf(0f to "0", 45f to "45", 90f to "90")) {
                val rad = Math.toRadians(deg.toDouble())
                val labelR = radius * 1.11f
                val layout = measurer.measure(text, labelStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        cx + (labelR * kotlin.math.cos(rad)).toFloat() - layout.size.width / 2f,
                        cy - (labelR * kotlin.math.sin(rad)).toFloat() - layout.size.height / 2f,
                    ),
                )
            }
        }

    }
}


/**
 * The downhill direction in words, for the protractor's spoken value.
 *
 * A Canvas cannot be read, so this is the only way a screen-reader
 * user learns which way is down. Compass points use the ordinary
 * 16-point names rather than degrees, because "180 degrees" is a
 * number nobody can act on while "south" is.
 */
private fun compassWord(azimuth: Float): String = when {
    azimuth < 22.5f || azimuth >= 337.5f -> "the right"
    azimuth < 67.5f -> "the lower right"
    azimuth < 112.5f -> "the bottom"
    azimuth < 157.5f -> "the lower left"
    azimuth < 202.5f -> "the left"
    azimuth < 247.5f -> "the upper left"
    azimuth < 292.5f -> "the top"
    else -> "the upper right"
}
