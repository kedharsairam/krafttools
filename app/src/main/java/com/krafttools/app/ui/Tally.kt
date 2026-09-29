package com.krafttools.app.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TallyScreen(onBack: () -> Unit) {
    var count by rememberSaveable { mutableStateOf(0) }
    var running by rememberSaveable { mutableStateOf(false) }
    var elapsedMs by rememberSaveable { mutableLongStateOf(0L) }
    val laps = rememberSaveable(saver = stringListSaver) { mutableStateListOf<String>() }

    // Volume keys count while this screen is up (installed here,
    // cleared below — MainActivity only forwards when installed).
    DisposableEffect(Unit) {
        TallyVolumeKeys.onVolume = { count++ }
        onDispose { TallyVolumeKeys.onVolume = null }
    }

    // Stopwatch ticker: 100 ms ticks while running.
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = android.os.SystemClock.elapsedRealtime()
        while (running) {
            delay(100)
            val now = android.os.SystemClock.elapsedRealtime()
            elapsedMs += now - last
            last = now
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tally + stopwatch") },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { count++ }) {
                    Text("+1")
                }
                OutlinedButton(onClick = { if (count > 0) count-- }) {
                    Text("−1")
                }
                OutlinedButton(onClick = { count = 0 }) {
                    Text("Reset")
                }
            }
            Text(
                text = formatStopwatch(elapsedMs),
                style = MaterialTheme.typography.displayMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { running = !running }) {
                    Text(if (running) "Pause" else "Start")
                }
                OutlinedButton(
                    onClick = {
                        laps.add(0, "#${laps.size + 1}  ${formatStopwatch(elapsedMs)}")
                    },
                    enabled = running,
                ) {
                    Text("Lap")
                }
                OutlinedButton(
                    onClick = {
                        running = false
                        elapsedMs = 0L
                        laps.clear()
                    },
                ) {
                    Text("Clear")
                }
            }
            Text(
                text = "Tip: the volume keys count too — pocket counting.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                laps.take(6).forEach { lap ->
                    Text(
                        text = lap,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}

private fun formatStopwatch(ms: Long): String {
    val totalTenths = ms / 100
    val tenths = totalTenths % 10
    val seconds = (totalTenths / 10) % 60
    val minutes = (totalTenths / 600) % 60
    val hours = totalTenths / 36000
    return if (hours > 0) {
        "%d:%02d:%02d.%d".format(hours, minutes, seconds, tenths)
    } else {
        "%02d:%02d.%d".format(minutes, seconds, tenths)
    }
}

/**
 * Volume-key counting, owned by the tally screen. MainActivity forwards
 * volume presses here only while this handler is installed (set on
 * entering tally, cleared on leaving) — so volume behaves normally
 * everywhere else. No permission, no focus tricks.
 */
object TallyVolumeKeys {
    var onVolume: (() -> Unit)? = null
}
