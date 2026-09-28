package com.krafttools.app.ui

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    val manager = remember {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    val cameraId = remember {
        try {
            manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
    }
    var on by remember { mutableStateOf(false) }
    var strobe by remember { mutableStateOf(false) }
    var rateHz by remember { mutableFloatStateOf(4f) }
    val scope = rememberCoroutineScope()
    var strobeJob by remember { mutableStateOf<Job?>(null) }

    fun setTorch(state: Boolean) {
        if (cameraId == null) return
        try {
            manager.setTorchMode(cameraId, state)
            on = state
        } catch (_: Exception) {
            on = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            strobeJob?.cancel()
            try {
                if (cameraId != null) manager.setTorchMode(cameraId, false)
            } catch (_: Exception) {
            }
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
                text = if (strobe) "STROBE" else if (on) "ON" else "OFF",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = if (on || strobe) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Button(
                onClick = {
                    strobeJob?.cancel()
                    strobeJob = null
                    strobe = false
                    setTorch(!on)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (on) "Turn off" else "Turn on")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Strobe",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.Switch(
                    checked = strobe,
                    onCheckedChange = { want ->
                        strobeJob?.cancel()
                        strobeJob = null
                        if (want) {
                            strobe = true
                            setTorch(false)
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
                            strobe = false
                            setTorch(false)
                        }
                    },
                )
            }
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
            } else {
                Text(
                    text = "Strobe tops out at 12 Hz. Never point it at " +
                        "anyone's face; the LED also gets hot.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
