package com.krafttools.app.ui

import android.hardware.Sensor
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmfScreen(onBack: () -> Unit) {
    val mag by rememberSensor(Sensor.TYPE_MAGNETIC_FIELD)
    var threshold by rememberSaveable { mutableFloatStateOf(60f) }
    var peak by rememberSaveable { mutableFloatStateOf(0f) }
    // Alert history: timestamped crossings, newest first, capped.
    // Answers "was that spike the fridge or the microwave?" later.
    val crossings = rememberSaveable(saver = stringListSaver) { mutableStateListOf<String>() }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Metal + EMF") },
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
        val m = mag
        if (m == null) {
            NoSensor(modifier = Modifier.padding(padding), name = "magnetometer")
            return@Scaffold
        }
        // Total field is rotation-independent; per-axis values alone mislead
        // when the phone is tilted, so the headline number is the magnitude.
        val total = Math.sqrt(
            (m[0] * m[0] + m[1] * m[1] + m[2] * m[2]).toDouble(),
        ).toFloat()
        if (total > peak) peak = total
        // Rising-edge buzz: keying on the boolean fires once per crossing,
        // not on every sensor frame while the field stays high.
        val alert = total >= threshold
        LaunchedEffect(alert) {
            if (!alert) return@LaunchedEffect
            val stamp = java.text.SimpleDateFormat(
                "HH:mm:ss",
                java.util.Locale.getDefault(),
            ).format(java.util.Date())
            crossings.add(0, "$stamp · %.1f µT".format(total.toDouble()))
            if (crossings.size > 20) crossings.removeLast()
            val vib = context.getSystemService(Vibrator::class.java) ?: return@LaunchedEffect
            if (!vib.hasVibrator()) return@LaunchedEffect
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(200)
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReadingHeader(
                value = "%.1f".format(total.toDouble()),
                unit = "µT",
                status = if (alert) "above threshold — metal or wire nearby"
                else "Ambient field",
                live = !alert,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Raw axes so a single spiking axis (e.g. a magnet under
                // one corner) is visible instead of hidden in the total.
                Text("x %.1f".format(m[0].toDouble()), style = MaterialTheme.typography.bodyLarge)
                Text("y %.1f".format(m[1].toDouble()), style = MaterialTheme.typography.bodyLarge)
                Text("z %.1f".format(m[2].toDouble()), style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Peak %.1f µT".format(peak.toDouble()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                OutlinedButton(onClick = { peak = total }) {
                    Text("Reset peak")
                }
            }
            if (crossings.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Crossings (${crossings.size})",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    OutlinedButton(onClick = { crossings.clear() }) {
                        Text("Clear")
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    crossings.take(5).forEach { entry ->
                        Text(
                            text = entry,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                text = "Alert at %.0f µT".format(threshold.toDouble()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = threshold,
                onValueChange = { threshold = it },
                // 40 uT sits just above Earth's max so idle rarely trips;
                // 200 uT still catches small magnets without slider fiddling.
                valueRange = 40f..200f,
            )
            Text(
                text = "Earth's field is 25–65 µT. Cases and magnets pollute " +
                    "readings — test in the open, and wave a figure-8 to " +
                    "calibrate. No permissions needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
