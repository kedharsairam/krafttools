package com.krafttools.app.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Light meter.
 *
 * The bug here was quiet and permanent: a NaN from a silent or
 * faulting HAL latched into the session maximum, because `Math.max`
 * returns NaN and it propagates. The header then printed "NaN" for
 * the rest of the session with no way out. Readings are now validated
 * before anything accumulates.
 *
 * The min and max were also collected in a `LaunchedEffect` keyed on
 * the reading, which Compose cancels and relaunches whenever the key
 * changes — so two distinct values inside one frame meant the first
 * was dropped before its body ran. Accumulation belongs in the sensor
 * callback, where it is now.
 *
 * Finally, the tool now says what it actually knows. The old copy
 * claimed phone sensors "saturate at ~5-30k lux" — a guess with no
 * source. The exact bound is `Sensor.getMaximumRange()`, and a full
 * scale too low to reach daylight means the part is reporting a raw
 * ADC count, not lux. Both are now stated from the real number.
 */
@Composable
fun LuxScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current

    var lux by remember { mutableFloatStateOf(0f) }
    var minLux by remember { mutableFloatStateOf(Float.NaN) }
    var maxLux by remember { mutableFloatStateOf(Float.NaN) }
    var hold by remember { mutableStateOf(false) }
    var heldLux by remember { mutableStateOf<Float?>(null) }
    var haveSample by remember { mutableStateOf(false) }
    var peakHold by remember { mutableStateOf(false) }
    var rejected by remember { mutableIntStateOf(0) }
    var maxRange by remember { mutableFloatStateOf(0f) }
    var tick by remember { mutableIntStateOf(0) }

    val window = remember { ArrayDeque<Float>(120) }
    val peak = remember { PeakHold(decayPerSecond = 0.8f) }

    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (sensor == null) {
            onDispose { }
        } else {
            // The exact, knowable bound — rather than a guess in prose.
            maxRange = sensor.maximumRange
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val v = event.values.getOrNull(0) ?: return
                    // Validate here, in the callback, so a bad sample
                    // never reaches the accumulators at all.
                    if (!isPlausibleLux(v)) {
                        rejected++
                        return
                    }
                    lux = v
                    haveSample = true
                    // Min/max in the callback: one comparison per
                    // sample, no coroutine, nothing dropped.
                    if (minLux.isNaN() || v < minLux) minLux = v
                    if (maxLux.isNaN() || v > maxLux) maxLux = v
                    window.addLast(v)
                    while (window.size > 120) window.removeFirst()
                    // The peak-hold decay is per-SAMPLE, so it belongs
                    // in the callback. In a LaunchedEffect(tick) it
                    // advanced at the display rate instead, and at
                    // SENSOR_DELAY_UI the key changes several times per
                    // frame.
                    peak.update(v, 1f / 15f)
                    tick++
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { manager.unregisterListener(listener) }
        }
    }

    // tick is read here purely to subscribe this composable to sample
    // arrivals; every accumulator is updated in the callback itself.
    val liveTick = tick
    require(liveTick >= 0)

    ToolScaffold("Light meter", onBack) { padding ->
        if (!haveSample) {
            NoSensor(modifier = Modifier.padding(padding), name = "light sensor")
            return@ToolScaffold
        }

        val shown = heldLux ?: lux
        val band = luxBand(shown)
        val ev = evAt(shown)
        val series = window.toList()

        ToolColumn(padding) {
            Spacer(modifier = Modifier.height(4.dp))
            ReadingHeader(
                value = if (shown >= 100f) {
                    "%.0f".format(Locale.ROOT, shown.toDouble())
                } else {
                    "%.1f".format(Locale.ROOT, shown.toDouble())
                },
                unit = "lx",
                status = band.label,
                live = heldLux == null,
            )
            Text(
                text = band.detail,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // The trace is the instrument here. Illuminance spans five
            // orders of magnitude, so it gets a log axis graduated
            // 1/2/5 per decade — the way a light meter is actually
            // marked. On a linear axis 17.7 lx sat at 13% of the panel.
            TraceGraph(
                values = series,
                min = 0.1f,
                max = 100000f,
                peak = peak.value,
                logScale = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            StatRow {
                StatChip("min", if (minLux.isNaN()) "—" else fmt(minLux))
                StatChip("max", if (maxLux.isNaN()) "—" else fmt(maxLux))
                StatChip("peak", fmt(peak.value))
                StatChip("ev", ev?.let { "%.1f".format(Locale.ROOT, it) } ?: "—")
            }

            // A lux number is only useful as an exposure, so give the
            // exposure. The relation is EV = log2(N²/t); a test checks
            // the two readouts against each other.
            if (ev != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                ) {
                    ExposureStat("at f/5.6", shutterFor(ev, aperture = 5.6))
                    ExposureStat("at 1/60", apertureFor(ev, shutter = 1.0 / 60.0))
                }
            }

            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        Haptics.confirm(view)
                        heldLux = lux
                        hold = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text("Hold")
                }
                OutlinedButton(
                    onClick = {
                        Haptics.tick(view)
                        heldLux = null
                        hold = false
                    },
                    enabled = hold,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text("Resume")
                }
            }

            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        Haptics.confirm(view)
                        minLux = Float.NaN
                        maxLux = Float.NaN
                        peak.reset()
                        window.clear()
                        rejected = 0
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text("Reset")
                }
                Button(
                    onClick = {
                        Haptics.confirm(view)
                        peakHold = !peakHold
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text(if (peakHold) "Peak held" else "Hold peak")
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Peak hold",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = peakHold,
                    onCheckedChange = {
                        Haptics.tick(view)
                        peakHold = it
                    },
                )
            }

            ToolHint(saturationNote(maxRange))
            if (rejected > 0) {
                ToolHint(
                    "$rejected reading${if (rejected == 1) " was" else "s were"} " +
                        "rejected as impossible, rather than poisoning the " +
                        "minimum and maximum.",
                    warn = true,
                )
            }
        }
    }
}

private fun fmt(v: Float): String =
    if (v >= 100f) "%.0f".format(Locale.ROOT, v.toDouble()) else "%.1f".format(Locale.ROOT, v.toDouble())

@Composable
private fun ExposureStat(label: String, value: String) {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
