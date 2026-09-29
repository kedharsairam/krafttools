package com.krafttools.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedScreen(onBack: () -> Unit) {
    PermissionGate(
        permission = android.Manifest.permission.ACCESS_FINE_LOCATION,
        // Android 12+ lets the user grant "approximate" instead, which
        // denies FINE while granting COARSE. Both are declared, and
        // either one is enough for a scan or a position fix.
        alsoAccepts = android.Manifest.permission.ACCESS_COARSE_LOCATION,
        tool = "Speedometer",
        reason = "GPS position is read on this phone only to work out " +
            "your speed. Positions are never stored or sent anywhere.",
    
        onBack = onBack,) {
        SpeedBody(onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission") // Gated above: body only runs once granted.
@Composable
private fun SpeedBody(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember {
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    // No GPS hardware at all: say so instead of spinning forever.
    // `allProviders` is an IPC round-trip to the location service, and
    // this used to run on every recomposition — twice a second, for the
    // whole time the tool is open. It cannot change while the screen is
    // alive, so it is read once.
    val hasGps = remember { manager.allProviders.contains(LocationManager.GPS_PROVIDER) }
    if (!hasGps) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Speedometer") },
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
            NoSensor(modifier = Modifier.padding(padding), name = "GPS")
        }
        return
    }

    var speedMs by rememberSaveable { mutableStateOf(0f) }
    // Display smoothing: 3-fix moving average kills GPS jitter at
    // walking pace. Trip math uses RAW fixes (averaging would shave
    // real distance), so the two deliberately disagree by design.
    // Display smoothing. A 3-fix moving average has two problems: it
    // starts from an empty list, so the first two readings are shown
    // as-is, and a window average lags by half its width. A seeded
    // exponential moving average has neither — the first sample is the
    // estimate, and there is no window lag.
    var shownMs by remember { mutableFloatStateOf(0f) }
    var emaSeeded by remember { mutableStateOf(false) }
    var accuracy by remember { mutableStateOf<Float?>(null) }
    var gpsOn by rememberSaveable { mutableStateOf(manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) }
    var metric by rememberSaveable { mutableStateOf(true) }
    var hud by rememberSaveable { mutableStateOf(false) }
    val speedView = LocalView.current
    // Trip computer: distance accumulates by haversine between fixes
    // while active. Positions live in RAM only, die with the screen.
    var tripActive by rememberSaveable { mutableStateOf(false) }
    var tripKm by rememberSaveable { mutableStateOf(0f) }
    var tripMaxMs by rememberSaveable { mutableStateOf(0f) }
    // Elapsed time is tracked directly: average speed is distance over
    // time, and time cannot be recovered from a sample count.
    var tripStartMs by rememberSaveable { mutableStateOf(0L) }
    var tripEndMs by rememberSaveable { mutableStateOf(0L) }
    var lastLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var lastLon by rememberSaveable { mutableStateOf<Double?>(null) }
    fun startTrip() {
        val now = System.currentTimeMillis()
        if (tripActive) {
            tripActive = false
            tripEndMs = now
            lastLat = null
            return
        }
        tripActive = true
        tripKm = 0f
        tripMaxMs = 0f
        tripStartMs = now
        tripEndMs = now
        lastLat = null
    }
    // Average speed is distance divided by time. It used to be an
    // unweighted MEAN of per-fix speeds, which is only the same thing
    // if fixes arrive at uniform intervals — and GPS does not promise
    // that. Batching, multipath and signal loss all stretch the gaps,
    // so a fix after a 30-second dropout counted exactly as much as one
    // 20 ms later, and a single tunnel exit could halve the average.
    fun toShown(ms: Float): Float =
        if (metric) SpeedMath.toKmh(ms) else SpeedMath.toMph(ms)
    // A function, not a val: Kotlin only allows a custom `get()` on a
    // class property, and this is a local.
    fun tripElapsedMs(): Long =
        (if (tripActive) System.currentTimeMillis() else tripEndMs) - tripStartMs
    fun avgShown(): Double? {
        if (tripStartMs == 0L || tripKm <= 0f) return null
        return SpeedMath.averageSpeedMs(tripKm.toDouble(), tripElapsedMs() / 1000.0)
            ?.let { toShown(it).toDouble() }
    }

    DisposableEffect(manager) {
        // Framework GPS only: no Play Services dependency, works offline.
        // 1s / 1m is the sweet spot — faster drains battery without
        // better numbers, since phone GPS chips top out near 1 Hz.
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                gpsOn = true
                // Accuracy is STICKY. It used to be set to null by any
                // fix that did not carry one, which threw the whole
                // speedometer away and showed "waiting for GPS fix"
                // in the middle of a drive. A provider that has told us
                // how good it is has not un-told us by going quiet.
                if (loc.hasAccuracy()) accuracy = loc.accuracy
                // hasSpeed() distinguishes "standing still" (0 m/s) from
                // "the provider does not know" (also 0 m/s). Treating
                // the second as the first parks a car in a tunnel at
                // 0 km/h and makes a 0 km trip look like a real one.
                val known = loc.hasSpeed()
                if (known) {
                    speedMs = loc.speed
                    shownMs = SpeedMath.smoothedSample(
                        if (emaSeeded) shownMs else null,
                        loc.speed,
                    )
                    emaSeeded = true
                }
                if (tripActive && loc.hasAccuracy() && loc.accuracy < 25f) {
                    // Junk fixes (tunnels, first-fix jumps) would invent
                    // kilometers: only accumulate fixes better than 25 m.
                    val prevLat = lastLat
                    val prevLon = lastLon
                    if (prevLat != null && prevLon != null) {
                        val step = haversineKm(
                            prevLat, prevLon,
                            loc.latitude, loc.longitude,
                        )
                        // Single-second teleport guard: nothing road-legal
                        // moves 500 m in a second.
                        //
                        // Everything the trip reports has to come from
                        // the SAME population of fixes. These three lines
                        // used to sit OUTSIDE this guard, so a rejected
                        // 1800 km/h teleport still set the trip maximum
                        // and dragged the average — a tunnel exit
                        // reported as your top speed.
                        if (step < 0.5) {
                            tripKm += step.toFloat()
                            if (known && loc.speed > tripMaxMs) {
                                tripMaxMs = loc.speed
                            }
                        }
                    }
                    lastLat = loc.latitude
                    lastLon = loc.longitude
                }
            }

            override fun onProviderEnabled(provider: String) { gpsOn = true }
            override fun onProviderDisabled(provider: String) { gpsOn = false }
            @Deprecated("Deprecated in framework")
            override fun onStatusChanged(p: String?, s: Int, e: Bundle?) { }
        }
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, listener)
        } catch (_: SecurityException) { /* Gate guarantees grant; belt and braces. */ }
        try {
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                // hasSpeed() here too: a stale last-known fix with no
                // speed reports 0 m/s, which is indistinguishable from
                // genuinely parked.
                if (it.hasSpeed()) {
                    speedMs = it.speed
                    shownMs = it.speed
                    emaSeeded = true
                }
                if (it.hasAccuracy()) accuracy = it.accuracy
            }
        } catch (_: SecurityException) { }
        onDispose { manager.removeUpdates(listener) }
    }

    // Displayed number is the smoothed one; trip computer eats raw.
    val shown = if (emaSeeded) toShown(shownMs) else toShown(speedMs)
    val unit = if (metric) "km/h" else "mph"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Speedometer") },
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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            // Top-aligned. Centring the column distributed the leftover
            // height as a gap between the dial and the accuracy figure,
            // which read as a layout bug rather than as breathing room.
            // The dial centres itself in the weighted region instead.
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!gpsOn) {
                Text("GPS is switched off.", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Turn on location in system settings, then come back — " +
                        "without the GPS radio this tool has nothing to read.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (accuracy == null) {
                Text("waiting for GPS fix…", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Go outside with a clear view of the sky. First fix " +
                        "usually takes 10–60 seconds.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ReadingHeader(
                    value = "%.0f".format(shown.toDouble()),
                    unit = unit,
                    status = null,
                    mirror = hud,
                )
                // The dial is laid out at its natural aspect — 1.5r tall
                // by 1.732r wide for a 240-degree arc — so it exactly
                // fills its canvas instead of leaving about 400dp of
                // dead space beneath it. The Box takes the leftover
                // height and centres the dial in it, so the slack reads
                // as even breathing room rather than a gap under one
                // element.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    SpeedGauge(
                        fraction = SpeedMath.gaugeFraction(shown, metric),
                        fullScale = SpeedMath.fullScale(metric),
                        unit = unit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(SpeedMath.DIAL_WIDTH_OVER_HEIGHT),
                    )
                }
                Text(
                    "±%.0f m accuracy".format(accuracy!!.toDouble()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // A trip that has not moved yet still has a duration,
                // and it used to render nothing at all in that state —
                // so starting a trip and sitting still looked identical
                // to never having started one.
                if (tripActive || tripStartMs != 0L) {
                    StatRow {
                        StatChip("trip", "%.2f km".format(tripKm))
                        StatChip(
                            "elapsed",
                            formatElapsed(tripElapsedMs() / 1000),
                        )
                        StatChip(
                            "max",
                            if (tripMaxMs > 0f) {
                                "%.0f %s".format(toShown(tripMaxMs), unit)
                            } else {
                                "—"
                            },
                        )
                        // Average is distance over time, and a trip that
                        // has not moved has no average — which is a
                        // different statement from "average 0".
                        StatChip(
                            "avg",
                            avgShown()?.let { "%.0f %s".format(it, unit) } ?: "—",
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = metric,
                    onClick = { metric = true },
                    label = { Text("km/h") },
                )
                FilterChip(
                    selected = !metric,
                    onClick = { metric = false },
                    label = { Text("mph") },
                )
                FilterChip(
                    selected = hud,
                    onClick = { hud = !hud },
                    label = { Text("HUD") },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    Haptics.confirm(speedView)
                    startTrip()
                }) {
                    Text(if (tripActive) "Stop trip" else "Start trip")
                }
                OutlinedButton(
                    onClick = {
                        tripActive = false
                        tripKm = 0f
                        tripMaxMs = 0f
                        tripStartMs = 0L
                        tripEndMs = 0L
                        lastLat = null
                    },
                ) {
                    Text("Clear trip")
                }
            }
            Text(
                "Phone GPS is ±3–5 m on a good day, so the number lags " +
                    "1–2 s behind reality. Tunnels, roofs, and city " +
                    "canyons kill it — treat this as a guide, not a speed camera.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Haversine distance in km between two fixes. */
private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return 2 * r * Math.asin(kotlin.math.sqrt(a))
}

/**
 * Arc gauge: 240° sweep with ticks every 1/8 and a needle for the
 * current fraction. The number above owns precision; the gauge owns
 * glanceability — together they read at any distance.
 */
@Composable
private fun SpeedGauge(fraction: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val needle = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.92f
        val r = size.minDimension * 0.62f
        // 240° sweep: 150° .. 30° going through top (-90°).
        val start = 150f
        val sweep = 240f
        drawArc(
            color = track,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(10f),
        )
        for (i in 0..8) {
            val a = Math.toRadians((start + sweep * i / 8).toDouble())
            val x1 = cx + (r * 0.82f * Math.cos(a)).toFloat()
            val y1 = cy + (r * 0.82f * Math.sin(a)).toFloat()
            val x2 = cx + (r * 0.98f * Math.cos(a)).toFloat()
            val y2 = cy + (r * 0.98f * Math.sin(a)).toFloat()
            drawLine(track, Offset(x1, y1), Offset(x2, y2), 4f)
        }
        val na = Math.toRadians((start + sweep * fraction.coerceIn(0f, 1f)).toDouble())
        drawLine(
            color = needle,
            start = Offset(cx, cy),
            end = Offset(
                (cx + (r * 0.78f * Math.cos(na))).toFloat(),
                (cy + (r * 0.78f * Math.sin(na))).toFloat(),
            ),
            strokeWidth = 9f,
        )
        drawCircle(color = needle, radius = 12f, center = Offset(cx, cy))
    }
}
