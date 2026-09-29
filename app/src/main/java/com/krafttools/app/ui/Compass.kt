package com.krafttools.app.ui

import android.hardware.Sensor
import android.location.Location
import android.location.LocationManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    // True north needs magnetic declination, which needs position.
    // Last-known fix only (no tracking, no storage): FINE_LOCATION is
    // already declared for wifi/speed. No fix -> 0° + honest note.
    var declination by remember { mutableStateOf<Float?>(null) }
    var locked by rememberSaveable { mutableStateOf<Float?>(null) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        try {
            val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE)
                as LocationManager
            val fix: Location? =
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            declination = fix?.let {
                android.hardware.GeomagneticField(
                    it.latitude.toFloat(),
                    it.longitude.toFloat(),
                    it.altitude.toFloat(),
                    it.time,
                ).declination
            }
        } catch (_: SecurityException) {
            declination = null
        }
        onDispose { }
    }

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
        // Tilt quality: compass math assumes a flat phone (gravity along
        // device -Z). Past ~35° of tilt the heading degrades fast — say
        // so instead of showing a confident wrong number.
        val gMag = Math.sqrt(
            (accel!![0] * accel!![0] + accel!![1] * accel!![1] + accel!![2] * accel!![2]).toDouble(),
        ).toFloat()
        val tiltDeg = if (gMag > 0.1f) {
            Math.toDegrees(
                Math.acos((kotlin.math.abs(accel!![2]) / gMag).toDouble().coerceIn(-1.0, 1.0)),
            ).toFloat()
        } else {
            0f
        }
        val tilted = tiltDeg > 35f
        // True bearing = magnetic + east declination. Displayed always;
        // the dial needle stays magnetic (what the sensor feels) while
        // the headline reads true (what maps use). Both labeled.
        val decl = declination ?: 0f
        val trueNorth = (azimuth + decl + 360f) % 360f

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "${trueNorth.toInt()}° ${cardinal(trueNorth)} true",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${azimuth.toInt()}° magnetic" +
                    (declination?.let { " · decl %+.1f°".format(it) }
                        ?: " · no fix, decl 0°") +
                    " · %.0f µT".format(strength.toDouble()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            locked?.let { lock ->
                val rel = ((azimuth - lock + 540f) % 360f) - 180f
                Text(
                    text = "Bearing ${lock.toInt()}° · %+.0f° %s".format(
                        kotlin.math.abs(rel),
                        if (rel >= 0) "right" else "left",
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Dial(
                azimuth = azimuth,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clickable { locked = if (locked == null) azimuth else null },
            )
            Text(
                text = if (locked == null) {
                    "Tap the dial to lock a bearing."
                } else {
                    "Bearing locked — tap again to release."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (disturbed) {
                Text(
                    text = "Metal nearby — move away from magnets and cases, " +
                        "then wave a figure-8 to recalibrate.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (tilted) {
                Text(
                    text = "Tilted %.0f° — lay the phone flat; headings " +
                        "measured tilted read wrong.".format(tiltDeg.toDouble()),
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
    // Unwrapped angle: 359° -> 0° sweeps forward 1°, never whips back.
    var shown by remember { mutableStateOf(azimuth) }
    LaunchedEffect(azimuth) {
        var delta = (azimuth - shown) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        shown += delta
    }
    val animated by androidx.compose.animation.core.animateFloatAsState(
        targetValue = shown,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.7f,
            stiffness = 120f,
        ),
        label = "dial",
    )
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f * 0.92f
        drawCircle(color = ring, radius = r, style = Stroke(4f))
        // Cardinal ticks rotate with the world (needle stays up).
        rotate(-animated, Offset(cx, cy)) {
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
