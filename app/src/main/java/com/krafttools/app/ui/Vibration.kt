package com.krafttools.app.ui

import android.hardware.Sensor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibrationScreen(onBack: () -> Unit) {
    val accel by rememberSensor(
        Sensor.TYPE_ACCELEROMETER,
        // GAME rate (≈50 Hz): plenty for a viewer. FASTEST (0 µs)
        // requires HIGH_SAMPLING_RATE_SENSORS and crashes without it.
        android.hardware.SensorManager.SENSOR_DELAY_GAME,
    )
    // Rolling history for the trace (UI thread is fine at this rate;
    // the tool is a viewer, not a lab instrument).
    val history = remember { mutableStateListOf<Float>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vibration meter") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to tools",
                        )
                    }
                },
            )
        },
    ) { padding ->
        val g = accel
        if (g == null) {
            NoSensor(
                modifier = Modifier.padding(padding),
                name = "accelerometer",
            )
            return@Scaffold
        }
        // Remove gravity: magnitude deviation from 9.81 ≈ vibration.
        val mag = sqrt((g[0] * g[0] + g[1] * g[1] + g[2] * g[2]).toDouble()).toFloat()
        val vibe = kotlin.math.abs(mag - android.hardware.SensorManager.GRAVITY_EARTH)
        history.add(vibe)
        if (history.size > 120) history.removeAt(0)
        val peak = history.maxOrNull() ?: 0f
        // Dominant frequency via DFT over the 2.4 s window (≈50 Hz GAME
        // rate, 25 Hz Nyquist). Naive O(n²) is fine at n=120 on-device.
        // DC bin skipped: gravity residue would always "win".
        val dominantHz = dominantFrequency(history.toList(), sampleHz = 50f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "%.2f m/s²".format(vibe.toDouble()),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "peak this session %.2f m/s²".format(peak.toDouble()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (dominantHz != null) {
                Text(
                    text = "dominant %.1f Hz · %,.0f RPM".format(
                        dominantHz,
                        dominantHz * 60f,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Trace(
                values = history.toList(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
            Text(
                text = "Lay the phone on the machine. Still table reads " +
                    "near zero; a running motor shows its rhythm.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Trace(values: List<Float>, modifier: Modifier = Modifier) {    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        val midY = size.height / 2f
        drawLine(grid, Offset(0f, midY), Offset(size.width, midY), 2f)
        if (values.size < 2) return@Canvas
        val scale = (size.height / 2f - 8f) / 4f // 4 m/s² full-scale
        var prev = Offset(0f, midY - (values[0].coerceAtMost(4f) * scale))
        for (i in 1 until values.size) {
            val x = size.width * i / (values.size - 1)
            val y = midY - (values[i].coerceAtMost(4f) * scale)
            drawLine(line, prev, Offset(x, y), 4f)
            prev = Offset(x, y)
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
            val d = (samples[i] - mean).toDouble()
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
