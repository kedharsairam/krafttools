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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorchScreen(onBack: () -> Unit) {
    PermissionGate(
        permission = android.Manifest.permission.CAMERA,
        tool = "Torch",
        reason = "The flashlight LED lives behind the camera stack, " +
            "so Android asks for camera access. The lens is never " +
            "opened and no picture is ever taken.",
    ) {
        TorchBody(onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TorchBody(onBack: () -> Unit) {
    val context = LocalContext.current
    // LED presence check stays local (drives the NoSensor gate);
    // all writes funnel through TorchState (QS-tile agreement).
    val cameraId = remember {
        com.krafttools.app.tiles.TorchState.flashId(context)
    }
    var on by remember { mutableStateOf(false) }
    var strobe by remember { mutableStateOf(false) }
    var sos by remember { mutableStateOf(false) }
    // Sync with LED truth on entry: the QS tile (or a dead process)
    // may have left the bulb on while this screen thinks off.
    LaunchedEffect(Unit) {
        on = com.krafttools.app.tiles.TorchState.lit
    }
    var rateHz by remember { mutableFloatStateOf(4f) }
    var autoOffMin by remember { mutableStateOf(0) }
    var autoOffLeftSec by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    var strobeJob by remember { mutableStateOf<Job?>(null) }
    var timerJob by remember { mutableStateOf<Job?>(null) }

    fun setTorch(state: Boolean) {
        // Funnel through process truth so the QS tile never disagrees.
        on = com.krafttools.app.tiles.TorchState.setTorch(context, state)
    }

    /** Stop everything: used by mode switches, timer fire, and dispose. */
    fun stopAll() {
        strobeJob?.cancel()
        strobeJob = null
        timerJob?.cancel()
        timerJob = null
        strobe = false
        sos = false
        autoOffLeftSec = 0L
        setTorch(false)
    }

    fun armAutoOff() {
        timerJob?.cancel()
        timerJob = null
        autoOffLeftSec = 0L
        if (autoOffMin <= 0) return
        val deadline = android.os.SystemClock.elapsedRealtime() + autoOffMin * 60_000L
        timerJob = scope.launch {
            while (isActive) {
                val left =
                    (deadline - android.os.SystemClock.elapsedRealtime()) / 1000L
                if (left <= 0) {
                    stopAll()
                    break
                }
                autoOffLeftSec = left
                delay(1000)
            }
        }
    }

    /** International Morse SOS: ··· −−− ···, then 2s silence, repeat. */
    fun startSos() {
        stopAll()
        sos = true
        setTorch(false)
        armAutoOff()
        strobeJob = scope.launch {
            val u = 200L
            // S O S as (on-ms, off-ms) steps; trailing word gap included.
            val pattern = listOf(
                u to u, u to u, u to 3 * u, // ···
                3 * u to u, 3 * u to u, 3 * u to 3 * u, // −−−
                u to u, u to u, u to 7 * u, // ··· + word gap
            )
            while (isActive) {
                for ((litMs, gapMs) in pattern) {
                    setTorch(true)
                    delay(litMs)
                    setTorch(false)
                    delay(gapMs)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            strobeJob?.cancel()
            timerJob?.cancel()
            com.krafttools.app.tiles.TorchState.setTorch(context, false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Torch") },
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
        if (cameraId == null) {
            NoSensor(
                modifier = Modifier.padding(padding),
                name = "flashlight LED",
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = when {
                    sos -> "SOS"
                    strobe -> "STROBE"
                    on -> "ON"
                    else -> "OFF"
                },
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = if (on || strobe || sos) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (autoOffLeftSec > 0) {
                Text(
                    text = "Auto-off in %d:%02d".format(
                        autoOffLeftSec / 60,
                        autoOffLeftSec % 60,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = {
                    if (on || strobe || sos) {
                        stopAll()
                    } else {
                        stopAll()
                        setTorch(true)
                        armAutoOff()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (on || strobe || sos) "Turn off" else "Turn on")
            }
            ModeRow(
                label = "Strobe",
                active = strobe,
                onToggle = { want ->
                    if (want) {
                        stopAll()
                        strobe = true
                        setTorch(false)
                        armAutoOff()
                        strobeJob = scope.launch {
                            var lit = false
                            while (isActive) {
                                lit = !lit
                                setTorch(lit)
                                val period = (1000.0 / rateHz)
                                    .toLong().coerceAtLeast(80L)
                                delay(period / 2)
                            }
                        }
                    } else {
                        stopAll()
                    }
                },
            )
            ModeRow(
                label = "SOS signal",
                active = sos,
                onToggle = { want -> if (want) startSos() else stopAll() },
            )
            if (strobe) {
                Text(
                    text = "%.1f Hz — photosensitive epilepsy warning: " +
                        "look away from the flash.".format(rateHz.toDouble()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = rateHz,
                    onValueChange = { rateHz = it },
                    valueRange = 1f..12f,
                    steps = 10,
                )
            }
            if (sos) {
                Text(
                    text = "International distress: ··· −−− ···, repeating. " +
                        "Same epilepsy caution as strobe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!strobe && !sos) {
                Text(
                    text = "Strobe tops out at 12 Hz. Never point it at " +
                        "anyone's face; the LED also gets hot.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Auto-off",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            AutoOffRow(
                minutes = autoOffMin,
                onPick = {
                    autoOffMin = it
                    if (on || strobe || sos) {
                        armAutoOff()
                    } else {
                        timerJob?.cancel()
                        timerJob = null
                        autoOffLeftSec = 0L
                    }
                },
            )
        }
    }
}

@Composable
private fun ModeRow(label: String, active: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.Switch(
            checked = active,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun AutoOffRow(minutes: Int, onPick: (Int) -> Unit) {
    val options = listOf(0 to "Off", 1 to "1 min", 5 to "5 min", 15 to "15 min")
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((mins, label) in options) {
            if (mins == minutes) {
                Button(onClick = { onPick(mins) }) {
                    Text(label)
                }
            } else {
                OutlinedButton(onClick = { onPick(mins) }) {
                    Text(label)
                }
            }
        }
    }
}
