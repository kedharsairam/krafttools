package com.krafttools.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorchScreen(onBack: () -> Unit) {
    PermissionGate(
        permission = android.Manifest.permission.CAMERA,
        tool = "Torch",
        reason = "The flashlight LED lives behind the camera stack, " +
            "so Android asks for camera access. The lens is never " +
            "opened and no picture is ever taken.",
    
        onBack = onBack,) {
        TorchBody(onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TorchBody(onBack: () -> Unit) {
    val context = LocalContext.current
    // LED presence check stays local (drives the NoSensor gate);
    // all writes funnel through TorchState (QS-tile agreement).
    val cameraId = remember {
        com.krafttools.app.tiles.TorchState.flashId(context)
    }
    // rememberSaveable, not remember: rotating the screen used to
    // turn the torch OFF and reset the mode, the strobe rate and
    // the auto-off timer, because all of it was plain `remember`
    // and died on the configuration change.
    var on by rememberSaveable { mutableStateOf(false) }
    var strobe by rememberSaveable { mutableStateOf(false) }
    var sos by rememberSaveable { mutableStateOf(false) }
    // Single mode index drives everything (was two independent
    // switches that could disagree): 0 steady, 1 strobe, 2 SOS.
    var mode by rememberSaveable { mutableStateOf(0) }
    val view = LocalView.current
    // Sync with LED truth on entry: the QS tile (or a dead process)
    // may have left the bulb on while this screen thinks off.
    LaunchedEffect(Unit) {
        on = com.krafttools.app.tiles.TorchState.lit
    }
    var rateHz by rememberSaveable { mutableFloatStateOf(4f) }
    var autoOffMin by rememberSaveable { mutableStateOf(0) }
    var autoOffLeftSec by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    var strobeJob by remember { mutableStateOf<Job?>(null) }
    var timerJob by remember { mutableStateOf<Job?>(null) }

    fun setTorch(state: Boolean) {
        // Funnel through process truth so the QS tile never disagrees.
        on = com.krafttools.app.tiles.TorchState.setTorch(context, state)
    }

    /** Stop everything: used by mode switches, timer fire, and dispose. */
    fun stopAll() {
        strobeJob?.cancel()
        strobeJob = null
        timerJob?.cancel()
        timerJob = null
        strobe = false
        sos = false
        autoOffLeftSec = 0L
        setTorch(false)
    }

    fun armAutoOff() {
        timerJob?.cancel()
        timerJob = null
        autoOffLeftSec = 0L
        if (autoOffMin <= 0) return
        val deadline = android.os.SystemClock.elapsedRealtime() + autoOffMin * 60_000L
        timerJob = scope.launch {
            while (isActive) {
                val left =
                    (deadline - android.os.SystemClock.elapsedRealtime()) / 1000L
                if (left <= 0) {
                    stopAll()
                    break
                }
                autoOffLeftSec = left
                delay(1000)
            }
        }
    }

    /** International Morse SOS: ··· −−− ···, then 2s silence, repeat. */
    fun startSos() {
        stopAll()
        sos = true
        setTorch(false)
        armAutoOff()
        strobeJob = scope.launch {
            val pattern = sosPattern()
            while (isActive) {
                for ((litMs, gapMs) in pattern) {
                    setTorch(true)
                    delay(litMs)
                    setTorch(false)
                    delay(gapMs)
                }
            }
        }
    }

    fun startStrobeFx() {
        stopAll()
        strobe = true
        setTorch(false)
        armAutoOff()
        strobeJob = scope.launch {
            var lit = false
            while (isActive) {
                lit = !lit
                setTorch(lit)
                val period = (1000.0 / rateHz)
                    .toLong().coerceAtLeast(80L)
                delay(period / 2)
            }
        }
    }

    /** Apply the segmented mode to a live bulb. */
    fun applyMode(m: Int) {
        when (m) {
            1 -> startStrobeFx()
            2 -> startSos()
            else -> {
                stopAll()
                setTorch(true)
                armAutoOff()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Loops only. The torch itself is NOT switched off: a user
            // who turns it on and leaves the screen expects it to stay
            // on, and the quick-settings tile depends on the light
            // outliving this screen. Turning it off here also meant
            // simply rotating the device put the torch out.
            strobeJob?.cancel()
            timerJob?.cancel()
        }
    }

    ToolScaffold(

        title = "Torch + strobe",

        onBack = onBack,

    ) { padding ->
        if (cameraId == null) {
            NoSensor(
                modifier = Modifier.padding(padding),
                name = "flashlight LED",
            )
            return@ToolScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.weight(0.15f))

            // The hero is the lamp itself: it brightens on the real flash
            // cadence, so the strobe rate and the Morse timing are visible,
            // not just described. Tapping it is the power control.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                LampDisc(
                    lit = on || strobe || sos,
                    strobe = strobe,
                    sos = sos,
                    rateHz = rateHz,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clickable {
                            Haptics.confirm(view)
                            if (on || strobe || sos) stopAll() else applyMode(mode)
                        }
                        .instrumentSemantics(
                            label = "Torch lamp",
                            value = when {
                                sos -> "on, SOS signal"
                                strobe -> "on, strobing at %.0f hertz".format(
                                    Locale.ROOT,
                                    rateHz.toDouble(),
                                )
                                on -> "on"
                                else -> "off"
                            },
                            hint = if (on || strobe || sos) {
                                "double tap to switch off"
                            } else {
                                "double tap to switch on"
                            },
                            onClickAction = {
                                Haptics.confirm(view)
                                if (on || strobe || sos) stopAll() else applyMode(mode)
                            },
                        ),
                )
            }

            Text(
                text = when {
                    sos -> "SOS"
                    strobe -> "STROBE"
                    on -> "ON"
                    else -> "OFF"
                },
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = if (on || strobe || sos) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )

            Text(
                text = if (on || strobe || sos) {
                    "TAP THE LAMP TO SWITCH OFF"
                } else {
                    "TAP THE LAMP TO LIGHT IT"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Armed timer reads as a draining bar, not just a number.
            if (autoOffLeftSec > 0 && autoOffMin > 0) {
                AutoOffBar(
                    leftSec = autoOffLeftSec,
                    totalSec = autoOffMin * 60,
                )
            }

            // One segmented control for the mode (was two switches):
            // exactly one is ever active, and switching restarts live.
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .touchTarget(),
            ) {
                val labels = listOf("Steady", "Strobe", "SOS")
                labels.forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = mode == i,
                        onClick = {
                            Haptics.tick(view)
                            mode = i
                            // Live-switch only when burning; otherwise the
                            // row just arms the next mode for the lamp.
                            if (on || strobe || sos) applyMode(i)
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                        colors = instrumentSegmentedColors(),
                        modifier = Modifier.touchTarget(),
                    ) {
                        Text(label)
                    }
                }
            }

            if (strobe) {
                Text(
                    text = ("%.1f Hz — photosensitive epilepsy warning: " +
                        "look away from the flash.").format(Locale.ROOT, rateHz.toDouble()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = rateHz,
                    onValueChange = { rateHz = it },
                    valueRange = 1f..12f,
                    steps = 10,
                    modifier = Modifier.touchTarget(),
                )
            }
            if (sos) {
                Text(
                    text = "International distress: ··· −−− ···, repeating. " +
                        "Same epilepsy caution as strobe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!strobe && !sos) {
                Text(
                    text = "Strobe tops out at 12 Hz. Never point it at " +
                        "anyone's face; the LED also gets hot.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "Auto-off",
                style = MaterialTheme.typography.titleMedium,
            )
            AutoOffSegmented(
                minutes = autoOffMin,
                onPick = {
                    Haptics.tick(view)
                    autoOffMin = it
                    if (on || strobe || sos) {
                        armAutoOff()
                    } else {
                        timerJob?.cancel()
                        timerJob = null
                        autoOffLeftSec = 0L
                    }
                },
            )
        }

    }
}

/**
 * The lamp: a machined bezel, concentric rings, and a filament core that
 * tracks the true brightness of the output — steady, strobing at the
 * selected rate, or blinking the Morse cadence step for step. Matching
 * the visual to the real timing is the whole point: you can judge a
 * strobe rate by eye before pointing it at anyone.
 */
@Composable
private fun LampDisc(
    lit: Boolean,
    strobe: Boolean,
    sos: Boolean,
    rateHz: Float,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.outline
    val glow = remember { Animatable(0f) }

    LaunchedEffect(lit, strobe, sos, rateHz) {
        when {
            strobe -> {
                val half = (1000f / rateHz.coerceAtLeast(1f) / 2f).toInt()
                    .coerceAtLeast(40)
                while (true) {
                    glow.animateTo(1f, tween(half / 2, easing = LinearEasing))
                    glow.animateTo(0f, tween(half / 2, easing = LinearEasing))
                }
            }
            sos -> {
                while (true) {
                    for ((litMs, gapMs) in sosPattern()) {
                        glow.animateTo(1f, tween(litMs.toInt(), easing = LinearEasing))
                        glow.animateTo(0f, tween(gapMs.toInt(), easing = LinearEasing))
                    }
                }
            }
            lit -> glow.animateTo(1f, tween(110))
            else -> glow.animateTo(0f, tween(180))
        }
    }

    Canvas(modifier = modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        val brightness = glow.value

        // Hot core out to a soft rim: a real emitter, not a flat disc.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.10f + 0.85f * brightness),
                    accent.copy(alpha = 0.08f + 0.55f * brightness),
                    accent.copy(alpha = 0f),
                ),
                center = c,
                radius = r * 0.98f,
            ),
            radius = r * 0.98f,
            center = c,
        )
        // Filament: the hot point at the centre.
        drawCircle(
            color = Color.White.copy(alpha = 0.25f + 0.75f * brightness),
            radius = r * (0.06f + 0.05f * brightness),
            center = c,
        )
        // Machined bezel + concentric rings: the instrument language.
        drawCircle(color = ring, radius = r * 0.97f, center = c, style = Stroke(width = 4.dp.toPx()))
        for (i in 1..3) {
            drawCircle(
                color = ring.copy(alpha = 0.5f),
                radius = r * (0.97f - i * 0.055f),
                center = c,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
        // Dial ticks every 30 degrees: the bezel is calibrated.
        for (i in 0 until 12) {
            val a = Math.toRadians((i * 30).toDouble())
            val long = i % 3 == 0
            val r0 = r * 0.86f
            val r1 = r * (if (long) 0.78f else 0.82f)
            drawLine(
                color = ring.copy(alpha = if (long) 0.9f else 0.5f),
                start = Offset(c.x + (r0 * kotlin.math.cos(a)).toFloat(), c.y + (r0 * kotlin.math.sin(a)).toFloat()),
                end = Offset(c.x + (r1 * kotlin.math.cos(a)).toFloat(), c.y + (r1 * kotlin.math.sin(a)).toFloat()),
                strokeWidth = if (long) 3.dp.toPx() else 1.5.dp.toPx(),
            )
        }
        // Off state keeps a lit-looking pilot so the control never reads dead.
        if (brightness < 0.05f) {
            drawCircle(
                color = accent.copy(alpha = 0.22f),
                radius = r * 0.035f,
                center = c,
            )
        }
    }
}

/** Draining bar for the armed auto-off timer. */
@Composable
private fun AutoOffBar(leftSec: Long, totalSec: Int) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outline
    val fraction = (leftSec.toFloat() / totalSec.coerceAtLeast(1)).coerceIn(0f, 1f)
    val label = "Auto-off in %d:%02d".format(Locale.ROOT, leftSec / 60, leftSec % 60)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
        ) {
            val y = size.height / 2f
            drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = size.height)
            drawLine(
                accent,
                Offset(0f, y),
                Offset(size.width * fraction, y),
                strokeWidth = size.height,
            )
        }
    }
}

/** International Morse SOS as (lit, gap) millisecond steps. */
private fun sosPattern(): List<Pair<Long, Long>> {
    val u = 200L
    return listOf(
        u to u, u to u, u to 3 * u, // ···
        3 * u to u, 3 * u to u, 3 * u to 3 * u, // −−−
        u to u, u to u, u to 7 * u, // ··· + word gap
    )
}

@Composable
private fun AutoOffSegmented(minutes: Int, onPick: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
                    .fillMaxWidth()
                    .touchTarget(),
    ) {
        val options = listOf(0 to "Off", 1 to "1 min", 5 to "5 min", 15 to "15 min")
        options.forEachIndexed { i, (mins, label) ->
            SegmentedButton(
                selected = mins == minutes,
                onClick = { onPick(mins) },
                modifier = Modifier.touchTarget(),
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                colors = instrumentSegmentedColors()
                    ) {
                Text(label)
            }
        }
    }
}
