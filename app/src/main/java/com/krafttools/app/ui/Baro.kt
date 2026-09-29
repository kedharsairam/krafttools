package com.krafttools.app.ui

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.pow

// Barometer + altimeter. Barometer chips report absolute station pressure;
// altitude is derived, never measured, so both numbers are shown with
// the assumption (sea-level reference) stated next to them.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarometerScreen(onBack: () -> Unit) {
    val reading by rememberSensor(Sensor.TYPE_PRESSURE)
    // 120 slots x one sample per ~15 s = ~30 min window. Time-gated below
    // because SENSOR_DELAY_UI fires far faster than weather moves.
    val history = rememberSaveable(saver = floatListSaver) { mutableStateListOf<Float>() }
    var lastSample by rememberSaveable { mutableStateOf(0L) }
    var seaLevelInput by rememberSaveable { mutableStateOf("1013.25") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Barometer") },
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
        val values = reading
        if (values == null) {
            // Most phones ship no barometer; gate before touching history.
            NoSensor(modifier = Modifier.padding(padding), name = "barometer")
            return@Scaffold
        }
        val pressure = values[0]
        val now = System.currentTimeMillis()
        if (now - lastSample > 15_000) {
            history.add(pressure)
            if (history.size > 120) history.removeAt(0)
            lastSample = now
        }
        // Rolling mean rides on the trace data so spikes (doors, HVAC)
        // stay visible in the graph while the headline stays readable.
        val smooth = if (history.isEmpty()) pressure else history.average().toFloat()
        // Tendency: first-half vs second-half mean, scaled to hPa/hour.
        // Needs ~10 min of data before it says anything (below that it
        // would just narrate noise). Zambretti-lite: direction + rate
        // only, no letter dial — the full forecaster lives in the
        // future Weather app with wind input and 3 h windows.
        val tendency = if (history.size >= 40) {
            val half = history.size / 2
            val first = history.take(half).average()
            val second = history.drop(half).average()
            val spanHours = (half * 15.0) / 3600.0
            (second - first) / spanHours
        } else {
            null
        }
        val forecast = tendency?.let { t ->
            when {
                t <= -2.0 -> "Falling fast — rain likely within hours"
                t < -0.5 -> "Falling — change coming"
                t <= 0.5 -> "Steady — no change ahead"
                t < 2.0 -> "Rising — improving"
                else -> "Rising fast — fair spell coming"
            }
        }
        val rawAlt = SensorManager.getAltitude(
            SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure,
        )
        val p0 = seaLevelInput.toFloatOrNull()?.takeIf { it > 800 && it < 1200 }
        // Hypsometric form inline (no extra dependency for one line):
        // alt = 44330 * (1 - (p/p0)^0.1903).
        val calAlt = p0?.let { 44330f * (1 - (pressure / it).toDouble().pow(0.1903)).toFloat() }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "%.2f hPa".format(smooth.toDouble()),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "station pressure (30-min average)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = forecast
                    ?: "Trend appears after ~10 min of readings.",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (forecast != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            PressureTrace(
                values = history.toList(),
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )
            Text(
                text = "Uncalibrated altitude %.0f m (assumes 1013.25 hPa)".format(rawAlt.toDouble()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = seaLevelInput,
                onValueChange = { seaLevelInput = it },
                label = { Text("Sea-level reference (hPa)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (calAlt != null) "Calibrated altitude %.0f m".format(calAlt.toDouble())
                    else "Enter a QNH between 800 and 1200 hPa",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                // Same hPa shift could be weather or a stairwell: the
                // sensor cannot tell, so neither does this screen.
                text = "A falling trace can mean weather or climbing stairs. " +
                    "Doors and HVAC gust the sensor; trust the average, not a spike.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PressureTrace(values: List<Float>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        val midY = size.height / 2f
        drawLine(grid, Offset(0f, midY), Offset(size.width, midY), 2f)
        if (values.size < 2) return@Canvas
        // Autoscale to the window: weather moves ~1 hPa/hr, so a fixed
        // scale would render a flat line. Guard the flat case explicitly.
        val min = values.minOrNull() ?: return@Canvas
        val max = values.maxOrNull() ?: return@Canvas
        val span = (max - min).coerceAtLeast(0.1f)
        var prev = Offset(0f, size.height - ((values[0] - min) / span) * (size.height - 16f) - 8f)
        for (i in 1 until values.size) {
            val x = size.width * i / (values.size - 1)
            val y = size.height - ((values[i] - min) / span) * (size.height - 16f) - 8f
            drawLine(line, prev, Offset(x, y), 4f)
            prev = Offset(x, y)
        }
    }
}
