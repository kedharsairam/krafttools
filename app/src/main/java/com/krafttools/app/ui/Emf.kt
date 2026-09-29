package com.krafttools.app.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Metal and electromagnetic field meter.
 *
 * The old version thresholded the raw total field, which cannot work:
 * Earth's field is 22-70 µT (IGRF-14), so a threshold inside that band
 * is a false alarm wherever the ambient field happens to sit. With the
 * old 60 µT default the tool screamed at nothing across northern
 * Canada, Siberia and Antarctica.
 *
 * Now it does what every real detector does: capture a baseline B₀
 * somewhere known to be clear, and report the *deviation* from it. The
 * same object reads the same 3 µT in Ottawa and in Nairobi.
 *
 * Three other corrections, all of which used to make the tool lie:
 *
 * - Peak latched forever and survived process death, so one walk past
 *   a car door became a permanent "peak" from a session where the
 *   sensor was off. Now a decaying peak hold.
 * - The alarm rendered in the *quieter* colour, because it passed
 *   `live = !alert`. The single most important state in the tool was
 *   the least prominent thing on it.
 * - The advice was to wave a figure-8 to calibrate. That is a compass
 *   technique for hard-iron bias in the x/y axes; it has no effect on
 *   a rotation-invariant magnitude. It also claimed the tool finds
 *   "metal", which is only true of ferromagnetic metal — aluminium and
 *   copper are invisible to a static-field sensor.
 */
@Composable
fun EmfScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current

    var total by remember { mutableFloatStateOf(0f) }
    var baseline by remember { mutableStateOf<Float?>(null) }
    var threshold by remember { mutableFloatStateOf(10f) }
    var accuracy by remember { mutableStateOf(SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) }
    var haveSample by remember { mutableStateOf(false) }
    var alert by remember { mutableStateOf(false) }

    val schmitt = remember { Schmitt() }
    val peakHold = remember { PeakHold(decayPerSecond = 0.75f) }
    val window = remember { ArrayDeque<Float>(90) }
    var tick by remember { mutableIntStateOf(0) }

    // GAME rate (50 Hz) rather than UI rate (16.7 Hz). A transient
    // lasts a few samples, so peak detection at 16.7 Hz reads
    // systematically low — and 50 Hz needs no permission, because
    // HIGH_SAMPLING_RATE_SENSORS is only required above 200 Hz.
    DisposableEffect(Unit) {
        val manager = context.getSystemService(android.content.Context.SENSOR_SERVICE)
            as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (sensor == null) {
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val v = event.values
                    if (v.size < 3) return
                    val m = fieldMagnitude(v[0], v[1], v[2])
                    if (!m.isFinite() || m < 0f) return
                    total = m
                    haveSample = true
                    window.addLast(m)
                    while (window.size > 90) window.removeFirst()
                    tick++
                }

                override fun onAccuracyChanged(sensor: Sensor, a: Int) {
                    accuracy = a
                }
            }
            val ok = manager.registerListener(
                listener, sensor, SensorManager.SENSOR_DELAY_GAME,
            )
            if (!ok) haveSample = false
            onDispose { manager.unregisterListener(listener) }
        }
    }

    // Peak hold and the alarm both run off the sample, not off the
    // frame, so neither depends on how often the screen redraws.
    LaunchedEffect(tick) {
        if (!haveSample) return@LaunchedEffect
        peakHold.update(total, 1f / 50f)
        val base = baseline
        val dev = if (base == null) 0f else deviationFrom(base, total)
        alert = schmitt.update(dev, threshold)
    }
    LaunchedEffect(threshold) { schmitt.reset() }
    LaunchedEffect(baseline) { schmitt.reset() }
    // Referencing the tick keeps the collection loop's writes live.
    val liveTick = tick
    require(liveTick >= 0)

    ToolScaffold("Metal + EMF", onBack) { padding ->
        if (!haveSample) {
            NoSensor(modifier = Modifier.padding(padding), name = "magnetometer")
            return@ToolScaffold
        }

        val base = baseline
        val dev = if (base == null) 0f else deviationFrom(base, total)
        val verdict = if (base == null) {
            null
        } else {
            classifyDeviation(dev, threshold)
        }
        val series = window.toList()
    // A centred band around the baseline: a 3 uT detection on a 40 uT
    // ambient is invisible on a zero-based axis, and the baseline is
    // the only interesting value on this screen.
    val band = remember(base) { EmfScale() }

        ToolColumn(padding) {
            Spacer(modifier = Modifier.height(4.dp))
            ReadingHeader(
                value = if (base == null) {
                    "%.1f".format(total.toDouble())
                } else {
                    "%+.1f".format(dev.toDouble())
                },
                unit = "µT",
                // The alarm is the most important state on this screen,
                // so it gets the *loudest* colour, not the quiet one.
                status = when {
                    base == null -> "ambient — set a baseline"
                    alert -> "ALERT · ${verdict?.label}"
                    else -> verdict?.label ?: "—"
                },
                live = alert,
            )

            // The trace is the instrument: a 3 µT blip on a 40 µT
            // ambient is invisible without one.
            TraceGraph(
                values = series,
                max = band.ceiling(series),
                min = band.floor(series),
                peak = series.maxOrNull() ?: 0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            StatRow {
                StatChip("ambient", "%.1f".format(total.toDouble()))
                StatChip(
                    "baseline",
                    base?.let { "%.1f".format(it) } ?: "—",
                    emphasise = base == null,
                )
                StatChip("peak", "%.1f".format(peakHold.value.toDouble()))
                StatChip("trigger", "±%.0f".format(threshold.toDouble()))
            }

            // Magnetometer accuracy is the cheapest metal indicator
            // available and it was being thrown away:
            // SENSOR_STATUS_ACCURACY_LOW means hard-iron interference is
            // present, which is exactly "ferromagnetic object nearby".
            if (accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                ToolHint(
                    "The sensor is reporting interference — a hard-iron " +
                        "object such as steel is close enough to distort the " +
                        "reading. That is a detection in its own right.",
                    warn = true,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        Haptics.confirm(view)
                        // Seed a sensible baseline from the current
                        // ambient rather than demanding a magic number.
                        baseline = total
                        peakHold.reset()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text(if (base == null) "Set baseline" else "Re-set")
                }
                OutlinedButton(
                    onClick = {
                        Haptics.tick(view)
                        baseline = null
                        peakHold.reset()
                    },
                    enabled = base != null,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text("Clear")
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Trigger",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "±%.0f µT".format(threshold.toDouble()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = threshold,
                // Far enough to reach a real magnet: a small neodymium
                // at 10 cm is 1 000-5 000 µT. The old ceiling of 200 µT
                // could not reach anything past about 25 cm.
                onValueChange = { threshold = it },
                valueRange = 2f..2000f,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            )

            ToolHint(
                if (base == null) {
                    "Hold the phone away from metal and tap Set baseline. " +
                        "Everything is then reported as a change from there, " +
                        "so it reads the same wherever you are."
                } else {
                    emfCapabilityNote() +
                        " Watch the trace: bring something close and the " +
                        "reading moves off the baseline."
                },
            )
        }
    }
}


/**
 * The vertical scale for the EMF trace: centred on the baseline with
 * enough band to show a detection, and a floor so a perfectly quiet
 * field does not magnify noise into a storm.
 */
private class EmfScale(private val minSpanUt: Float = 4f) {
    fun floor(series: List<Float>): Float {
        val b = series.minOrNull() ?: 0f
        return b - (b - (series.maxOrNull() ?: b)).coerceAtLeast(minSpanUt) * 0.1f
    }

    fun ceiling(series: List<Float>): Float {
        val hi = series.maxOrNull() ?: 0f
        return hi + (hi - (series.minOrNull() ?: hi)).coerceAtLeast(minSpanUt) * 0.1f
    }
}
