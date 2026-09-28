package com.krafttools.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiScreen(onBack: () -> Unit) {
    PermissionGate(
        permission = android.Manifest.permission.ACCESS_FINE_LOCATION,
        tool = "WiFi analyzer",
        reason = "Android only hands scan results (network names and " +
            "signal strengths) to apps holding location permission. " +
            "Your location is never read, stored, or sent anywhere — " +
            "the permission is just the key to the scan API.",
    ) {
        WifiBody(onBack)
    }
}

private data class Net(
    val ssid: String,
    val bssid: String,
    val level: Int,
    val freq: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiBody(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    var nets by remember { mutableStateOf<List<Net>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var everScanned by remember { mutableStateOf(false) }
    var rejected by remember { mutableStateOf(false) }

    fun readCached(): Boolean {
        return try {
            val cached = manager.scanResults
                .filter { it.SSID.isNotBlank() }
                .distinctBy { it.BSSID }
                .sortedByDescending { it.level }
                .map { r -> Net(r.SSID, r.BSSID, r.level, bandOf(r)) }
            if (cached.isNotEmpty()) {
                nets = cached
                true
            } else {
                false
            }
        } catch (_: SecurityException) {
            false
        }
    }

    DisposableEffect(Unit) {
        // Framework cache first: the system scans on its own cadence,
        // so results are often already here with zero battery cost.
        readCached()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    return
                }
                scanning = false
                everScanned = true
                if (!intent.getBooleanExtra(
                        WifiManager.EXTRA_RESULTS_UPDATED, false,
                    )
                ) {
                    // Scan rejected (throttled): keep old results, say so.
                    rejected = nets.isEmpty()
                    readCached()
                    return
                }
                rejected = false
                readCached()
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiFi analyzer") },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    rejected = false
                    val ok = try {
                        manager.startScan()
                    } catch (_: SecurityException) {
                        false
                    }
                    if (ok) {
                        scanning = true
                    } else {
                        // Throttled or refused: say so instead of a
                        // fake spinner. Cached results (if any) stay.
                        everScanned = true
                        rejected = nets.isEmpty()
                    }
                },
                enabled = !scanning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (scanning) "Scanning…" else "Scan now")
            }
            if (rejected) {
                Text(
                    text = "Scan was refused — Android throttles scans " +
                        "and needs location switched on. Wait a few " +
                        "seconds and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (nets.isEmpty() && !scanning && everScanned) {
                Text(
                    text = "No networks returned. Location services must " +
                        "be switched on for scans to return — Android's " +
                        "rule, not ours.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (nets.isEmpty() && !scanning) {
                Text(
                    text = "Shows every network the phone can hear, with " +
                        "signal strength per band. Tap Scan — or wait, " +
                        "recent system scans may already be listed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(nets, key = { it.bssid }) { net ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = net.ssid,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${net.level} dBm",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = "${net.freq} · ${net.bssid}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LinearProgressIndicator(
                                progress = { signalFraction(net.level) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun bandOf(r: ScanResult): String = when {
    r.frequency in 2400..2500 -> "2.4 GHz"
    r.frequency in 4900..5900 -> "5 GHz"
    r.frequency in 5925..7125 -> "6 GHz"
    else -> "${r.frequency} MHz"
}

/** dBm (-100..-30) into 0..1 for the bar. */
private fun signalFraction(level: Int): Float =
    ((level + 100).coerceIn(0, 70) / 70f)
