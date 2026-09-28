package com.krafttools.app.ui

import android.hardware.Sensor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val CARDINALS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

fun cardinal(azimuth: Float): String {
    val a = ((azimuth % 360f) + 360f) % 360f
    return CARDINALS[((a + 22.5f) / 45f).toInt() % 8]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassScreen(onBack: () -> Unit) {
    val accel by rememberSensor(Sensor.TYPE_ACCELEROMETER)
    val mag by rememberSensor(Sensor.TYPE_MAGNETIC_FIELD)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compass") },
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
        if (accel == null || mag == null) {
            NoSensor(
                modifier = Modifier.padding(padding),
                name = "compass (accelerometer + magnetometer)",
            )
            return@Scaffold
        }
        val rot = FloatArray(9)
        val incl = FloatArray(9)
        val ok = android.hardware.SensorManager.getRotationMatrix(
            rot, incl, accel, mag,
        )
        var azimuth = 0f
        if (ok) {
            val orient = FloatArray(3)
            android.hardware.SensorManager.getOrientation(rot, orient)
            azimuth = Math.toDegrees(orient[0].toDouble()).toFloat()
            if (azimuth < 0f) azimuth += 360f
        }
        // Magnetic strength sanity: Earth's field is ~25-65 uT. Far
        // outside that means metal nearby — say so, don't lie.
        val strength = Math.sqrt(
            (mag!![0] * mag!![0] + mag!![1] * mag!![1] + mag!![2] * mag!![2]).toDouble(),
        ).toFloat()
        val disturbed = strength < 20f || strength > 70f

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "${azimuth.toInt()}° ${cardinal(azimuth)}",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "%.0f µT".format(strength.toDouble()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Dial(
                azimuth = azimuth,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            if (disturbed) {
                Text(
                    text = "Metal nearby — move away from magnets and cases, " +
                        "then wave a figure-8 to recalibrate.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Wave a figure-8 if the needle feels stuck.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Dial(azimuth: Float, modifier: Modifier = Modifier) {
    val ring = MaterialTheme.colorScheme.outlineVariant
    val needle = MaterialTheme.colorScheme.primary
    val text = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f * 0.92f
        drawCircle(color = ring, radius = r, style = Stroke(4f))
        // Cardinal ticks rotate with the world (needle stays up).
        rotate(-azimuth, Offset(cx, cy)) {
            val labels = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
            for ((_, deg) in labels) {
                val rad = Math.toRadians(deg.toDouble())
                val x1 = cx + (r * 0.82f * Math.sin(rad)).toFloat()
                val y1 = cy - (r * 0.82f * Math.cos(rad)).toFloat()
                val x2 = cx + (r * 0.95f * Math.sin(rad)).toFloat()
                val y2 = cy - (r * 0.95f * Math.cos(rad)).toFloat()
                drawLine(
                    color = if (deg == 0f) needle else text,
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = if (deg == 0f) 8f else 5f,
                )
            }
        }
        // North needle always points up.
        drawLine(
            color = needle,
            start = Offset(cx, cy + r * 0.55f),
            end = Offset(cx, cy - r * 0.7f),
            strokeWidth = 10f,
        )
        drawCircle(color = needle, radius = r * 0.07f, center = Offset(cx, cy))
    }
}
