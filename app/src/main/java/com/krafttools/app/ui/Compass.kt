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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassScreen(onBack: () -> Unit) {
    val accel = rememberSensor(Sensor.TYPE_ACCELEROMETER).values
    val mag = rememberSensor(Sensor.TYPE_MAGNETIC_FIELD).values
    val context = LocalContext.current
    // True north needs magnetic declination, which needs position.
    // Last-known fix only (no tracking, no storage): FINE_LOCATION is
    // already declared for wifi/speed. No fix -> 0° + honest note.
    var declination by remember { mutableStateOf<Float?>(null) }
    var locked by rememberSaveable { mutableStateOf<Float?>(null) }
    val compassView = LocalView.current

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
        val confidence = compassConfidence(tiltDeg, strength, declination != null)
        // True bearing = magnetic + east declination. Displayed always;
        // the dial needle stays magnetic (what the sensor feels) while
        // the headline reads true (what maps use). Both labeled.
        val decl = declination ?: 0f
        val trueNorth = trueBearing(azimuth, decl)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            ReadingHeader(
                value = "${trueNorth.toInt()}° ${cardinal(trueNorth)}",
                unit = null,
                status = confidence.verdict,
            )
            Text(
                text = (
                    "%d° magnetic".format(Locale.ROOT, azimuth.toInt()) +
                        (declination?.let { " · decl %+.1f°".format(Locale.ROOT, it) }
                            ?: " · no fix, decl 0°") +
                        " · %.0f µT".format(Locale.ROOT, strength.toDouble())
                    ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            locked?.let { lock ->
                val rel = compassBearingError(azimuth, lock)
                val arrivedNow = onBearing(azimuth, lock)
                Text(
                    text = "Bearing %d° · %+.0f° %s".format(Locale.ROOT, 
                        lock.toInt(),
                        kotlin.math.abs(rel),
                        if (arrivedNow) "· on bearing" else if (rel >= 0) "right" else "left",
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (arrivedNow) FontWeight.Bold else FontWeight.Medium,
                    color = if (arrivedNow) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Dial(
                azimuth = azimuth,
                lock = locked,
                errorDeg = locked?.let { compassBearingError(azimuth, it) },
                onArrive = { Haptics.thud(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .aspectRatio(1f)
                    .clickable {
                        Haptics.confirm(compassView)
                        locked = if (locked == null) azimuth else null
                    },
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Tilt is not filler: it is the number that explains WHY
            // the heading may be wrong, so it belongs on the panel
            // rather than only in the warning paragraph.
            StatRow {
                StatChip(
                    "tilt",
                    "%.0f°".format(Locale.ROOT, tiltDeg.toDouble()),
                    emphasise = tilted,
                )
                StatChip(
                    "field",
                    "%.0f µT".format(Locale.ROOT, strength.toDouble()),
                    emphasise = disturbed,
                )
            }

            Text(
                text = if (locked == null) {
                    "Tap the dial to lock a bearing."
                } else {
                    "Bearing locked — tap again to release."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ToolHint(
                text = when {
                    disturbed ->
                        (
                            "Metal nearby — the field reads %.0f µT, outside " +
                                "the 25–65 µT Earth range. Move away from " +
                                "magnets, speakers and cases, then wave a " +
                                "figure-8."
                            ).format(Locale.ROOT, strength.toDouble())
                    tilted ->
                        (
                            "Tilted %.0f° — lay the phone flat. Headings " +
                                "measured this far off level read wrong."
                            ).format(Locale.ROOT, tiltDeg.toDouble())
                    else -> "Wave a figure-8 if the needle feels stuck."
                },
                warn = disturbed,
            )
        }
    }
}

/**
 * The compass dial. With a bearing locked it also draws the deviation
 * arc: a red band between where the needle is and where it should be,
 * with the needle's position marked on the ring. That is the glance a
 * navigator actually wants — which way, and how far — without reading
 * a single digit.
 */

@Composable
private fun Dial(
    azimuth: Float,
    modifier: Modifier = Modifier,
    lock: Float? = null,
    errorDeg: Float? = null,
    onArrive: () -> Unit = {},
) {
    val ring = MaterialTheme.colorScheme.outlineVariant
    val needle = MaterialTheme.colorScheme.primary
    val text = MaterialTheme.colorScheme.onSurfaceVariant
    val hub = MaterialTheme.colorScheme.surface
    // Unwrapped angle: 359° -> 0° sweeps forward 1°, never whips back.
    //
    // Deadband first: a magnetometer at rest still reports a fraction
    // of a degree, and each wobble re-targets the spring, so the needle
    // never settles — measured at ~100fps of redraw with the phone
    // lying still, which is a visible tremor and a real battery cost.
    // Nothing below [DIAL_DEADBAND] is information a human can read off
    // a rose, so it is discarded before it reaches the animation.
    var shown by remember { mutableStateOf(azimuth) }
    LaunchedEffect(azimuth) {
        var delta = (azimuth - shown) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        if (kotlin.math.abs(delta) < DIAL_DEADBAND) return@LaunchedEffect
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
    // A magnetometer at rest still reports a fraction of a degree of
    // noise, and the spring treats every wobble as a command to move.
    // The needle then never settles: measured at ~100fps of continuous
    // redraw with the phone lying still on a table, which is a visible
    // tremor and a real battery cost.
    //
    // So the target is quantised to the sensor's real resolution. A
    // human cannot read a tenth of a degree off a compass rose, and
    // below that the noise is not information.
    // Snap when close, so the spring actually terminates: a spring left
    // to asymptote keeps invalidating forever.
    val settled = if (kotlin.math.abs(animated - shown) < 0.05f) shown else animated
    // Cardinals are drawn with Android Paint (Canvas text needs it).
    // The paints are created once; their SIZE is set per frame from the
    // dial radius, because a fixed pixel size is a fixed visual size —
    // which is why these letters used to shrink on a high-density
    // screen and read as an afterthought.
    val cardinalPaint = remember(text) {
        android.graphics.Paint().apply {
            color = text.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD,
            )
            letterSpacing = 0.06f
        }
    }
    val northPaint = remember(needle) {
        android.graphics.Paint().apply {
            color = needle.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD,
            )
        }
    }
    // Fire the arrival haptic on the rising edge only.
    val arrived = errorDeg != null && onBearing(azimuth, lock ?: azimuth)
    LaunchedEffect(arrived) {
        if (arrived) onArrive()
    }

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f * 0.92f
        drawCircle(color = ring, radius = r, style = Stroke(4.dp.toPx()))
        // Cardinal ticks + letters rotate with the world (needle stays up).
        val cardSize = r * 0.155f
        val northSize = r * 0.185f
        cardinalPaint.textSize = cardSize
        northPaint.textSize = northSize
        val minorTick = r * 0.02f
        val majorTick = r * 0.032f
        // Every 5 degrees, the cardinals in bold. A real compass rose
        // is dense with marks; four lonely letters is a clock face.
        for (deg in 0 until 360 step 5) {
            val rad = Math.toRadians(deg.toDouble())
            val isCardinal = deg % 90 == 0
            val isIntercardinal = deg % 45 == 0
            val r1 = if (isCardinal) 0.90f else 0.93f
            val r2 = 0.97f
            drawLine(
                color = if (isCardinal) needle else ring,
                start = Offset(
                    cx + (r * r1 * Math.sin(rad)).toFloat(),
                    cy - (r * r1 * Math.cos(rad)).toFloat(),
                ),
                end = Offset(
                    cx + (r * r2 * Math.sin(rad)).toFloat(),
                    cy - (r * r2 * Math.cos(rad)).toFloat(),
                ),
                strokeWidth = if (isCardinal) majorTick else minorTick,
            )
        }
        // Cardinal letters, counter-rotated so they never read sideways
        // however far the dial has turned.
        rotate(-settled, Offset(cx, cy)) {
            val labels = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
            for ((letter, deg) in labels) {
                val rad = Math.toRadians(deg.toDouble())
                val lx = cx + (r * 0.72f * Math.sin(rad)).toFloat()
                val ly = cy - (r * 0.72f * Math.cos(rad)).toFloat()
                rotate(settled, Offset(lx, ly)) {
                    val paint = if (deg == 0f) northPaint else cardinalPaint
                    // Baseline centred: half the text height down.
                    drawContext.canvas.nativeCanvas.drawText(
                        letter,
                        lx,
                        ly + paint.textSize * 0.36f,
                        paint,
                    )
                }
            }
        }
        // Deviation arc, drawn under the needle: the sweep the needle
        // still has to travel, in the direction it must travel.
        val err = errorDeg
        if (lock != null && err != null && kotlin.math.abs(err) > BEARING_TOLERANCE_DEG) {
            val devColor = Color(0xFFFF4D4D)
            val sweep = kotlin.math.abs(err).coerceAtMost(180f)
            // From the locked bearing to the needle, the short way.
            val startDeg = lock - sweep / 2f
            val arcR = r * 0.86f
            val thickness = 18.dp.toPx()
            drawArc(
                color = devColor.copy(alpha = 0.28f),
                startAngle = startDeg - 90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(cx - arcR, cy - arcR),
                size = Size(arcR * 2f, arcR * 2f),
                style = Stroke(width = thickness),
            )
            // A leading tick at the target so "get here" is marked, not
            // just "you are here".
            val targetRad = Math.toRadians(startDeg.toDouble())
            drawLine(
                color = devColor,
                start = Offset(
                    cx + (arcR * Math.sin(targetRad)).toFloat(),
                    cy - (arcR * Math.cos(targetRad)).toFloat(),
                ),
                end = Offset(
                    cx + ((arcR + thickness * 0.9f) * Math.sin(targetRad)).toFloat(),
                    cy - ((arcR + thickness * 0.9f) * Math.cos(targetRad)).toFloat(),
                ),
                strokeWidth = 5.dp.toPx(),
            )
        }
        // North needle, always up. The north half is solid and the
        // south half is a thin tail, so the pointer has a direction
        // rather than being a bar through the middle.
        val northHalf = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx, cy - r * 0.74f)
            lineTo(cx + r * 0.045f, cy - r * 0.1f)
            lineTo(cx, cy)
            lineTo(cx - r * 0.045f, cy - r * 0.1f)
            close()
        }
        drawPath(northHalf, needle)
        drawLine(
            color = needle.copy(alpha = 0.5f),
            start = Offset(cx, cy + r * 0.02f),
            end = Offset(cx, cy + r * 0.5f),
            strokeWidth = 4.dp.toPx(),
        )
        // Hub: a lit dome, not a hole. A flat fill in the surface color
        // read as a puncture in the dial.
        val hubR = r * 0.13f
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(
                    needle.copy(alpha = 0.55f),
                    hub.copy(alpha = 0.0f),
                ),
                center = Offset(cx, cy),
                radius = hubR,
            ),
            radius = hubR,
            center = Offset(cx, cy),
        )
        drawCircle(color = needle, radius = r * 0.055f, center = Offset(cx, cy))
        drawCircle(
            color = needle.copy(alpha = 0.35f),
            radius = r * 0.085f,
            center = Offset(cx, cy),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}
