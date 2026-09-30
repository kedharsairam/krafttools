package com.krafttools.app.ui

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt
import java.util.Locale

/** Which channel of the accelerometer the meter is watching. */
enum class VibrationAxis(
    val label: String,
    val unit: String,
    /** Index into SensorEvent.values, or null for the vector magnitude. */
    val component: Int?,
) {
    TOTAL("Total", "m/s²", null),
    X("X", "m/s²", 0),
    Y("Y", "m/s²", 1),
    Z("Z", "m/s²", 2),
}

/** Samples kept on screen. 100 at the 50 Hz GAME rate is a 2 s window. */
private const val WINDOW = 100

/** Nominal sample rate. GAME (≈50 Hz) is what we register at. */
private const val SAMPLE_HZ = 50f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibrationScreen(onBack: () -> Unit) {
    val accelReading = rememberSensorReading(
        Sensor.TYPE_ACCELEROMETER,
        // GAME rate (≈50 Hz): plenty for a viewer. FASTEST (0 µs)
        // requires HIGH_SAMPLING_RATE_SENSORS and crashes without it.
        SensorManager.SENSOR_DELAY_GAME,
    )
    val accel = accelReading.values
    val view = LocalView.current

    // Per-channel rolling windows, plus the live reading per channel.
    val windows = remember { VibrationAxis.entries.associateWith { ArrayDeque<Float>(WINDOW) } }
    val trackers = remember { VibrationAxis.entries.associateWith { AcFilter() } }
    val live = remember { VibrationAxis.entries.associateWith { mutableFloatStateOf(0f) } }
    val peakHold = remember { PeakHold() }
    val scale = remember { AutoScale() }
    var peak by remember { mutableFloatStateOf(0f) }
    var dominantHz by remember { mutableStateOf<Float?>(null) }
    var axis by remember { mutableStateOf(VibrationAxis.TOTAL) }
    // Bumped once per collected sample so the trace redraws on data,
    // not on every recomposition the app happens to have.
    var tick by remember { mutableIntStateOf(0) }

    // Collect in the sensor's cadence, not the frame's. Appending during
    // composition made the window fill at the display refresh rate while
    // the FFT still assumed 50 Hz — the reported frequency was wrong by
    // whatever the frame rate happened to be.
    LaunchedEffect(Unit) {
        var last = 0L
        var sinceAnalysis = 0
        // Collect on the reading's own version, not on the array
        // reference. `snapshotFlow { accel }` captured a coroutine-local
        // val that nothing could ever change, so this emitted once —
        // null, before the sensor's first event — and never again. The
        // analysis window was therefore empty for the life of the
        // process and the trace sat on zero forever.
        snapshotFlow { accelReading.version }.collect {
            val g = accelReading.values ?: return@collect
            val now = android.os.SystemClock.elapsedRealtime()
            val dt = if (last == 0L) 1f / SAMPLE_HZ else ((now - last) / 1000f).coerceIn(0.001f, 0.2f)
            last = now
            val mag = sqrt(
                (g[0] * g[0] + g[1] * g[1] + g[2] * g[2]).toDouble(),
            ).toFloat()
            VibrationAxis.entries.forEach { ch ->
                // Total watches the vector magnitude, an axis watches
                // that axis; both have their DC (gravity, hand lean)
                // removed, so both read zero for a phone lying still.
                val component = ch.component
                val raw = trackers.getValue(ch).update(
                    if (component == null) mag else g[component],
                    dt,
                )
                // The headline number is an amplitude and must never
                // read negative, so `live` gets the rectified value.
                live.getValue(ch).value = kotlin.math.abs(raw)
                // The ANALYSIS window gets the SIGNED sample. This was
                // the rectified one, and a rectified sine has no energy
                // at its own frequency: |sin x| = 2/pi - (4/pi)SUM
                // cos(2nx)/(4n^2 - 1), so every component sits at 2f, 4f,
                // 6f. After DC removal nothing remains at f, and the
                // dominant-frequency search duly reported exactly
                // DOUBLE the truth — 5 Hz read as 10, 300 RPM as 600.
                // The unit test passed because it fed the function a
                // signed sine directly, so the only call site in the app
                // was the one that was wrong.
                val w = windows.getValue(ch)
                w.addLast(raw)
                while (w.size > WINDOW) w.removeFirst()
            }
            if (++sinceAnalysis >= 4) {
                sinceAnalysis = 0
                dominantHz = dominantFrequency(windows.getValue(axis).toList(), SAMPLE_HZ)
            }
            tick++
        }
    }
    // Referencing the tick is what keeps the window live: without it
    // nothing in this composition depends on collected samples.
    val liveTick = tick
    require(liveTick >= 0)

    ToolScaffold(

        title = "Vibration meter",

        onBack = onBack,

    ) { padding ->
        if (accel == null) {
            ToolStarting(
                tool = "accelerometer",
                modifier = Modifier.padding(padding),
            )
            return@ToolScaffold
        }
        val value = live.getValue(axis).value
        val dt = 1f / SAMPLE_HZ
        peak = peakHold.update(value, dt)
        val window = windows.getValue(axis).toList()
        scale.update(window.maxOrNull() ?: 0f, dt)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReadingHeader(
                value = "%.2f".format(Locale.ROOT, value.toDouble()),
                unit = axis.unit,
                status = "peak hold %.2f".format(Locale.ROOT, peak.toDouble()),
            )
            Text(
                text = dominantHz?.let {
                    "%.1f Hz · %,.0f RPM".format(Locale.ROOT, it, it * 60f)
                } ?: "no dominant rhythm yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (dominantHz != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            // The waveform is the point of this tool, so it fills the
            // space it has. What made an idle panel look broken was
            // never the empty area — it was the gutter printing "0.1"
            // over itself, which is fixed in TraceGraph.
            TraceGraph(
                values = window,
                label = "Vibration trace",
                spokenValue = "%.2f %s".format(
                    Locale.ROOT, value.toDouble(), axis.unit,
                ),
                max = scale.ceiling,
                peak = window.maxOrNull() ?: 0f,
                showZero = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            ToolCaption(
                text = if (window.isEmpty()) {
                    "Waiting for the accelerometer — lay the phone on " +
                        "the machine you want to measure."
                } else {
                    null
                },
            )

            // Axis selector, phyphox style: which channel the trace and
            // the number are talking about, stated explicitly.
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                VibrationAxis.entries.forEachIndexed { i, ch ->
                    SegmentedButton(
                        selected = axis == ch,
                        onClick = {
                            Haptics.tick(view)
                            axis = ch
                            peakHold.reset()
                            peak = 0f
                            dominantHz = dominantFrequency(
                                windows.getValue(ch).toList(),
                                SAMPLE_HZ,
                            )
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            i,
                            VibrationAxis.entries.size,
                        ),
                        colors = instrumentSegmentedColors(),
                        modifier = Modifier.touchTarget(),
                    ) {
                        Text(ch.label)
                    }
                }
            }
            Text(
                                text = "Lay the phone on the machine.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

    }
}

/**
 * Dominant frequency of a vibration window via DFT magnitude.
 * Returns null when the window is too flat to mean anything
 * (still table: no peak worth naming). Caller supplies the nominal
 * sample rate; GAME-rate jitter only smears bins, never invents them.
 */
fun dominantFrequency(samples: List<Float>, sampleHz: Float): Float? {
    val n = samples.size
    if (n < 32) return null
    val mean = samples.average().toFloat()
    var energy = 0.0
    for (s in samples) {
        val d = (s - mean).toDouble()
        energy += d * d
    }
    if (energy < 1e-6) return null
    var bestBin = -1
    var bestMag = 0.0
    // Skip bin 0 (DC): mean removal already, but residual bias lives on.
    for (k in 1 until n / 2) {
        var re = 0.0
        var im = 0.0
        for (i in samples.indices) {
            val angle = 2.0 * Math.PI * k * i / n
            // Hann window: unwindowed DFT smears one tone across bins.
            val d = (samples[i] - mean).toDouble() * hann(i, n)
            re += d * Math.cos(angle)
            im -= d * Math.sin(angle)
        }
        val mag = re * re + im * im
        if (mag > bestMag) {
            bestMag = mag
            bestBin = k
        }
    }
    if (bestBin < 0) return null
    // Significance gate: peak must own a real share of the energy,
    // else broadband noise gets a confident-sounding number.
    if (bestMag / energy < 4.0) return null
    return bestBin * sampleHz / n
}
