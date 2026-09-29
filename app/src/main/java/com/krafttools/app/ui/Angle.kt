package com.krafttools.app.ui

import android.hardware.Sensor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

// Single-axis readout, not a second bubble: the level tool answers
// "is it flat", this one answers "by how many degrees" for miters/shelves.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AngleScreen(onBack: () -> Unit) {
    val gravity by rememberSensor(Sensor.TYPE_ACCELEROMETER)
    // Frozen triple held for reading in awkward positions; live keeps flowing underneath.
    var held by rememberSaveable(saver = floatTripleSaver) { mutableStateOf<Triple<Float, Float, Float>?>(null) }
    // Snap: within 2° of a 45° multiple the display locks onto it.
    // Picture frames and shelves live at these angles; the toggle
    // admits the tool is rounding, not that the phone got better.
    var snap by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current
    fun snap45(v: Float): Float {
        if (!snap) return v
        val q = Math.round(v / 45f) * 45f
        return if (kotlin.math.abs(v - q) <= 2f) q.toFloat() else v
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Angle ruler") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to tools")
                    }
                },
            )
        },
    ) { padding ->
        val g = gravity
        if (g == null) {
            NoSensor(modifier = Modifier.padding(padding), name = "accelerometer")
            return@Scaffold
        }
        val (pitch, roll) = pitchRoll(g)
        // Magnitude from flat combines both axes; max() would hide diagonal tilt.
        val live = Math.hypot(pitch.toDouble(), roll.toDouble()).toFloat()
        val shown = (held ?: Triple(pitch, roll, live)).let { (p, r, m) ->
            Triple(snap45(p), snap45(r), snap45(m))
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReadingHeader(
                value = "%.1f°".format(shown.third),
                unit = null,
                status = if (held != null) {
                    "held — live %.1f° underneath".format(live)
                } else {
                    "from flat"
                },
            )
            // Rotating edge shows roll against a fixed horizon; pitch is numeric
            // since one line cannot show two axes without becoming a bubble again.
            TiltLine(rollDeg = shown.second, modifier = Modifier.fillMaxWidth().height(120.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Large single readouts: pitch = fore/aft tilt, roll = sideways tilt.
                AxisReadout("PITCH", shown.first)
                AxisReadout("ROLL", shown.second)
            }
            if (held == null) {
                Button(onClick = {
                    Haptics.confirm(view)
                    held = Triple(pitch, roll, live)
                }) { Text("Hold") }
            } else {
                OutlinedButton(onClick = { held = null }) { Text("Resume") }
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
                androidx.compose.material3.Switch(
                    checked = snap,
                    onCheckedChange = { snap = it },
                )
            }
            // Honest limits up front: uncalibrated MEMS drifts, so trust relative, not absolute.
            Text(
                "Phone-grade MEMS sensor (~0.5° noise floor). Calibrate on a known-flat surface; best for relative angles, not inspection-grade work.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AxisReadout(label: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("%.1f°".format(value), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun TiltLine(rollDeg: Float, modifier: Modifier = Modifier) {
    val horizon = MaterialTheme.colorScheme.outlineVariant
    val edge = MaterialTheme.colorScheme.primary
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        // Fixed reference: what "flat" looks like on this screen.
        drawLine(horizon, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 2f)
        // Protractor arc behind the edge: 15° ticks, longer each 45°.
        // The arc makes small angles readable that a bare line hides.
        val radius = size.minDimension * 0.42f
        for (deg in -90..90 step 15) {
            val rad = Math.toRadians(deg.toDouble())
            val major = deg % 45 == 0
            // Arc spans upward (protractor position); angles measured
            // from flat, positive clockwise like the roll value.
            val inner = radius * if (major) 0.78f else 0.86f
            val x1 = cx + (inner * Math.sin(rad)).toFloat()
            val y1 = cy - (inner * Math.cos(rad)).toFloat()
            val x2 = cx + (radius * Math.sin(rad)).toFloat()
            val y2 = cy - (radius * Math.cos(rad)).toFloat()
            drawLine(tick, Offset(x1, y1), Offset(x2, y2), if (major) 4f else 2f)
        }
        // Device edge rotates with roll; rotate() keeps the pivot math exact.
        rotate(degrees = -rollDeg, pivot = center) {
            drawLine(edge, Offset(size.width * 0.1f, cy), Offset(size.width * 0.9f, cy), strokeWidth = 8f)
        }
    }
}
