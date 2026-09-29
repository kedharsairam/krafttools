package com.krafttools.app.ui

import android.hardware.Sensor
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign

/**
 * Spirit level.
 *
 * Extracted from Toolbox.kt, where it was a 166-line composable in the
 * file that holds the navigation host and the tool registry. Every
 * other tool has its own file; this one did not, which is how it came
 * to be the largest function in the app.
 */
@Composable
fun LevelScreen(onBack: () -> Unit) {
    val gravity = rememberSensor(Sensor.TYPE_ACCELEROMETER).values
    // The raw vector, not a pitch/roll pair. De-rotating by the two
    // angles does not reproduce the rotation that actually maps this
    // vector onto vertical — it leaves several degrees of residual even
    // on a diagonal zero. Storing the vector makes it exact.
    var zero by rememberSaveable(saver = floatTripleSaver) {
        mutableStateOf<Triple<Float, Float, Float>?>(null)
    }
    var sound by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    val view = LocalView.current
    // Beep engine: ToneGenerator needs no permission. A short tick
    // quickens as level approaches, going solid inside 1° — level
    // behind furniture without looking. Haptic ticks the crossing.
    val tone = remember {
        try {
            android.media.ToneGenerator(
                android.media.AudioManager.STREAM_MUSIC, 60,
            )
        } catch (_: Exception) {
            null
        }
    }
    DisposableEffect(Unit) {
        onDispose { try { tone?.release() } catch (_: Exception) { } }
    }
    var wasLevel by rememberSaveable { mutableStateOf(false) }

    ToolScaffold(title = "Spirit level", onBack = onBack) { padding ->
        val g = gravity
        if (g == null) {
            NoSensor(
                modifier = Modifier.padding(padding),
                name = "accelerometer",
            )
            return@ToolScaffold
        }
        val orientation = orientationOf(g)
        val zeroVec = zero?.let { floatArrayOf(it.first, it.second, it.third) }
        val zeroOrient = zeroVec?.let { orientationOf(it) }
        val zp = zeroOrient?.pitchDeg ?: 0f
        val zr = zeroOrient?.rollDeg ?: 0f
        val tilt = Tilt(
            orientation.pitchDeg - zp,
            orientation.rollDeg - zr,
            isUseless = orientation.isUseless,
            // From the sample, not from the two axis angles: those are
            // rotations about different axes and the total is not
            // recoverable from them.
            // With no zero captured the reference is VERTICAL, not the
            // current sample: passing the sample as its own zero made
            // the residual identically zero, so an uncalibrated spirit
            // level read 0.00 degrees and "level" forever.
            totalDeg = residualTilt(g, zeroVec ?: floatArrayOf(0f, 0f, g[2])),
        )
        val level = tilt.magnitude
        val isLevel = tilt.isLevel && !tilt.isUseless
        val near = tilt.isNear

        // Phase B: the success moment. Crossing into level flashes the
        // vial green and confirms with a haptic — the whole point of a
        // spirit level is knowing you got there without looking.
        LaunchedEffect(isLevel) {
            if (isLevel && !wasLevel) Haptics.confirm(view)
            wasLevel = isLevel
        }
        // Keyed on the foreground flag as well. A phone lying flat and
        // level kept beeping every 600 ms behind a locked screen:
        // 80.7% CPU in the foreground, still 6.6% with the app in the
        // background. The beep is the one thing on this screen the user
        // cannot see, and it is the one thing that has to stop.
        val foreground = rememberIsForeground()
        LaunchedEffect(isLevel, sound, foreground) {
            if (!sound || !foreground) return@LaunchedEffect
            while (isLevel && foreground) {
                try {
                    tone?.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 120)
                } catch (_: Exception) {
                }
                kotlinx.coroutines.delay(600)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            ReadingHeader(
                value = "%.1f".format(Locale.ROOT, level.toDouble()),
                unit = "°",
                status = when {
                    tilt.isUseless -> "turn over"
                    isLevel -> "level"
                    near -> "nearly"
                    else -> "tilted"
                },
                live = isLevel || near,
            )
            // The instruction is the actionable part: which way to move.
            // One row, always. This sentence changes with every degree
            // of tilt, and a second row here took the vial's height
            // with it — the bubble visibly resized while the user was
            // trying to level something. Its colour may change; its
            // height may not.
            Text(
                text = tiltInstruction(tilt) ?: if (isLevel) "hold still" else "—",
                style = MaterialTheme.typography.titleMedium,
                color = if (isLevel) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            val (bx, by) = bubbleOffset(tilt)
            // The vial is a Canvas: without this a screen reader
            // announces nothing at all on this screen, and the whole
            // point of a spirit level is knowing you got there without
            // looking. The spoken value is the same verdict the hero
            // shows, plus the instruction, because the instruction is
            // the actionable part.
            Bubble(
                dx = bx,
                dy = by,
                level = isLevel,
                near = near,
                useless = tilt.isUseless,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .instrumentSemantics(
                        label = "Spirit level vial",
                        value = when {
                            tilt.isUseless ->
                                "phone is face down, reading is meaningless"
                            isLevel -> "level, within 1 degree"
                            else -> buildString {
                                append("%.1f degrees off level".format(Locale.ROOT, level))
                                tiltInstruction(tilt)?.let {
                                    append(", ").append(it)
                                }
                                if (near) append(", nearly level")
                            }
                        },
                    ),
            )

            // Raw pitch and roll: the numbers behind the bubble, so a
            // user can read a surface the vial cannot resolve.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                AngleStat("pitch", tilt.pitchDeg)
                AngleStat("roll", tilt.rollDeg)
                AngleStat("zero", if (zero == null) null else 0f)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Sound",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = sound,
                    onCheckedChange = {
                        Haptics.tick(view)
                        sound = it
                    },
    modifier = Modifier.touchTarget(),
)
            }
            Text(
                                text = "Lay flat, then Calibrate.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    Haptics.confirm(view)
                    zero = Triple(g[0], g[1], g[2])
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(if (zero == null) "Calibrate this surface" else "Re-calibrate")
            }
        }
    }
}

/** One labeled angle under the vial. */
@Composable
private fun AngleStat(label: String, degrees: Float?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (degrees == null) "—" else "%+.1f°".format(Locale.ROOT, degrees),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The vial. Machined bezel, a target cross at the centre, and a bubble
 * that lags on a spring like liquid in a real tube. Crossing into
 * level washes the housing green (Phase B) — a level that confirms
 * only in text is a level you have to read.
 */
@Composable
private fun Bubble(
    dx: Float,
    dy: Float,
    level: Boolean,
    near: Boolean,
    useless: Boolean,
    modifier: Modifier = Modifier,
) {
    val ring = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    val success = Color(0xFF3DDC84)
    val danger = MaterialTheme.colorScheme.error
    // Spring physics on the bubble: it lags and settles like a real
    // vial instead of teleporting with the sensor.
    val adx by androidx.compose.animation.core.animateFloatAsState(
        targetValue = dx,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.55f,
            stiffness = 220f,
        ),
        label = "bubbleX",
    )
    val ady by androidx.compose.animation.core.animateFloatAsState(
        targetValue = dy,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.55f,
            stiffness = 220f,
        ),
        label = "bubbleY",
    )
    // The wash blooms in rather than snapping, so success feels like
    // an event instead of a state change.
    val flash by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (level) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.6f,
            stiffness = 260f,
        ),
        label = "levelFlash",
    )
    val bubbleColor = when {
        level -> success
        useless -> danger
        near -> accent
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        // Machined bezel: outer ring + cardinal ticks give the vial
        // a physical housing instead of floating lines.
        drawCircle(color = ring, radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
        drawCircle(color = ring, radius = r * 0.97f, style = androidx.compose.ui.graphics.drawscope.Stroke(10.dp.toPx()))
        // Success wash, inside the housing.
        if (flash > 0.01f) {
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        success.copy(alpha = 0.22f * flash),
                        success.copy(alpha = 0f),
                    ),
                    center = Offset(cx, cy),
                    radius = r * 0.95f,
                ),
                radius = r * 0.95f,
                center = Offset(cx, cy),
            )
        }
        for (deg in 0 until 360 step 15) {
            val rad = Math.toRadians(deg.toDouble())
            val major = deg % 90 == 0
            val r1 = if (major) 0.86f else 0.92f
            drawLine(
                color = if (major) ring else ring.copy(alpha = 0.6f),
                start = Offset(
                    cx + (r * r1 * Math.sin(rad)).toFloat(),
                    cy - (r * r1 * Math.cos(rad)).toFloat(),
                ),
                end = Offset(
                    cx + (r * 0.97f * Math.sin(rad)).toFloat(),
                    cy - (r * 0.97f * Math.cos(rad)).toFloat(),
                ),
                strokeWidth = if (major) 5.dp.toPx() else 2.dp.toPx(),
            )
        }
        // Target cross: what "level" looks like, drawn even when empty.
        val targetR = r * 0.25f
        drawCircle(color = ring, radius = targetR, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
        drawLine(
            color = ring.copy(alpha = 0.7f),
            start = Offset(cx - targetR * 1.5f, cy),
            end = Offset(cx + targetR * 1.5f, cy),
            strokeWidth = 2.dp.toPx(),
        )
        drawLine(
            color = ring.copy(alpha = 0.7f),
            start = Offset(cx, cy - targetR * 1.5f),
            end = Offset(cx, cy + targetR * 1.5f),
            strokeWidth = 2.dp.toPx(),
        )
        // The bubble: a body with a highlight, not a flat dot.
        val bR = r * 0.16f
        val bc = Offset(cx + adx * r * 0.8f, cy + ady * r * 0.8f)
        drawCircle(
            color = bubbleColor.copy(alpha = 0.25f),
            radius = bR * 1.9f,
            center = bc,
        )
        drawCircle(color = bubbleColor, radius = bR, center = bc)
        drawCircle(
            color = Color.White.copy(alpha = 0.45f),
            radius = bR * 0.38f,
            center = Offset(bc.x - bR * 0.3f, bc.y - bR * 0.3f),
        )
    }
}
