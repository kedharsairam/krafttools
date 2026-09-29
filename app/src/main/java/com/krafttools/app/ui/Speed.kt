package com.krafttools.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedScreen(onBack: () -> Unit) {
    PermissionGate(
        permission = android.Manifest.permission.ACCESS_FINE_LOCATION,
        tool = "Speedometer",
        reason = "GPS position is read on this phone only to work out " +
            "your speed. Positions are never stored or sent anywhere.",
    ) {
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
    if (!manager.allProviders.contains(LocationManager.GPS_PROVIDER)) {
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
    val speedWin = remember { mutableStateListOf<Float>() }
    var accuracy by remember { mutableStateOf<Float?>(null) }
    var gpsOn by rememberSaveable { mutableStateOf(manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) }
    var metric by rememberSaveable { mutableStateOf(true) }
    var hud by rememberSaveable { mutableStateOf(false) }
    // Trip computer: distance accumulates by haversine between fixes
    // while active. Positions live in RAM only, die with the screen.
    var tripActive by rememberSaveable { mutableStateOf(false) }
    var tripKm by rememberSaveable { mutableStateOf(0f) }
    var tripMaxMs by rememberSaveable { mutableStateOf(0f) }
    var tripSum by rememberSaveable { mutableStateOf(0.0) }
    var tripN by rememberSaveable { mutableStateOf(0) }
    var lastLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var lastLon by rememberSaveable { mutableStateOf<Double?>(null) }
    fun startTrip() {
        if (tripActive) {
            tripActive = false
            lastLat = null
            return
        }
        tripActive = true
        tripKm = 0f
        tripMaxMs = 0f
        tripSum = 0.0
        tripN = 0
        lastLat = null
    }
    // (tripAvg inlined at the call site for unit correctness)
    fun toShown(ms: Float): Float = if (metric) ms * 3.6f else ms * 2.23694f
    fun avgShown(): Double =
        if (tripN > 0) toShown((tripSum / tripN).toFloat()).toDouble() else 0.0

    DisposableEffect(manager) {
        // Framework GPS only: no Play Services dependency, works offline.
        // 1s / 1m is the sweet spot — faster drains battery without
        // better numbers, since phone GPS chips top out near 1 Hz.
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                speedMs = loc.speed
                speedWin.add(loc.speed)
                if (speedWin.size > 3) speedWin.removeAt(0)
                accuracy = if (loc.hasAccuracy()) loc.accuracy else null
                gpsOn = true
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
                        if (step < 0.5) tripKm += step.toFloat()
                    }
                    lastLat = loc.latitude
                    lastLon = loc.longitude
                    if (loc.speed > tripMaxMs) tripMaxMs = loc.speed
                    tripSum += loc.speed
                    tripN += 1
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
                speedMs = it.speed
                if (it.hasAccuracy()) accuracy = it.accuracy
            }
        } catch (_: SecurityException) { }
        onDispose { manager.removeUpdates(listener) }
    }

    val shownRaw = if (metric) speedMs * 3.6f else speedMs * 2.23694f
    // Displayed number is the smoothed one; trip computer eats raw.
    val shown = if (speedWin.isEmpty()) {
        shownRaw
    } else {
        val avg = speedWin.average().toFloat()
        if (metric) avg * 3.6f else avg * 2.23694f
    }
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
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
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
                Text(
                    "±%.0f m accuracy".format(accuracy!!.toDouble()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (tripActive || tripKm > 0f) {
                    Text(
                        text = "Trip %.1f km · max %.0f %s · avg %.0f %s".format(
                            tripKm,
                            toShown(tripMaxMs),
                            unit,
                            avgShown(),
                            unit,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
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
                Button(onClick = { startTrip() }) {
                    Text(if (tripActive) "Stop trip" else "Start trip")
                }
                OutlinedButton(
                    onClick = {
                        tripActive = false
                        tripKm = 0f
                        tripMaxMs = 0f
                        tripSum = 0.0
                        tripN = 0
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
