package com.krafttools.app.ui

import android.content.Context
import android.hardware.Sensor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.SafetyCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

private data class Tool(
    val route: String,
    val name: String,
    val icon: ImageVector,
    val live: Boolean,
)

private val TOOLS = listOf(
    Tool("level", "Spirit level", Icons.Filled.Balance, live = true),
    Tool("compass", "Compass", Icons.Filled.Explore, live = true),
    Tool("torch", "Torch + strobe", Icons.Filled.FlashlightOn, live = true),
    Tool("vibration", "Vibration meter", Icons.Filled.Vibration, live = true),
    Tool("tally", "Tally + stopwatch", Icons.Filled.Timer, live = true),
    Tool("qr", "QR scanner", Icons.Filled.QrCode2, live = true),
    Tool("wifi", "WiFi analyzer", Icons.Filled.Wifi, live = true),
    Tool("lux", "Light meter", Icons.Filled.Lightbulb, live = true),
    Tool("emf", "Metal + EMF", Icons.Filled.SafetyCheck, live = true),
    Tool("db", "Sound meter", Icons.Filled.Mic, live = true),
    Tool("color", "Color picker", Icons.Filled.Palette, live = true),
    Tool("angle", "Angle ruler", Icons.Filled.AspectRatio, live = true),
    Tool("speed", "Speedometer", Icons.Filled.Speed, live = true),
    Tool("pressure", "Barometer", Icons.Filled.Thermostat, live = true),
)

@Composable
fun ToolboxNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "grid") {
        composable("grid") {
            ToolGrid(onOpen = { route ->
                if (TOOLS.any { it.route == route && it.live }) {
                    nav.navigate(route)
                }
            })
        }
        composable("level") { LevelScreen(onBack = { nav.popBackStack() }) }
        composable("compass") { CompassScreen(onBack = { nav.popBackStack() }) }
        composable("torch") { TorchScreen(onBack = { nav.popBackStack() }) }
        composable("vibration") {
            VibrationScreen(onBack = { nav.popBackStack() })
        }
        composable("tally") { TallyScreen(onBack = { nav.popBackStack() }) }
        composable("qr") { QrScreen(onBack = { nav.popBackStack() }) }
        composable("wifi") { WifiScreen(onBack = { nav.popBackStack() }) }
        composable("lux") { LuxScreen(onBack = { nav.popBackStack() }) }
        composable("emf") { EmfScreen(onBack = { nav.popBackStack() }) }
        composable("db") { DecibelScreen(onBack = { nav.popBackStack() }) }
        composable("color") {
            ColorPickerScreen(onBack = { nav.popBackStack() })
        }
        composable("angle") { AngleScreen(onBack = { nav.popBackStack() }) }
        composable("speed") { SpeedScreen(onBack = { nav.popBackStack() }) }
        composable("pressure") {
            BarometerScreen(onBack = { nav.popBackStack() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolGrid(onOpen: (String) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("KraftTools") }) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(TOOLS, key = { it.route }) { tool ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (tool.live) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.4f)
                        .clickable { onOpen(tool.route) },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                            tint = if (tool.live) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = tool.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (tool.live) "Ready" else "Soon",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (tool.live) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LevelScreen(onBack: () -> Unit) {
    val gravity by rememberSensor(Sensor.TYPE_ACCELEROMETER)
    var zero by rememberSaveable(saver = floatPairSaver) { mutableStateOf<Pair<Float, Float>?>(null) }
    var sound by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    // Beep engine: ToneGenerator needs no permission. A short tick
    // quickens as level approaches, going solid inside 1° — level
    // behind furniture without looking. Haptic ticks the crossing.
    val tone = remember {
        try {
            android.media.ToneGenerator(
                android.media.AudioManager.STREAM_MUSIC, 60,
            )
        } catch (_: Exception) {
            null
        }
    }
    DisposableEffect(Unit) {
        onDispose { try { tone?.release() } catch (_: Exception) { } }
    }
    var wasLevel by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spirit level") },
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
        val g = gravity
        if (g == null) {
            NoSensor(
                modifier = Modifier.padding(padding),
                name = "accelerometer",
            )
            return@Scaffold
        }
        val (pitch, roll) = pitchRoll(g)
        val (zp, zr) = zero ?: (0f to 0f)
        val dp = pitch - zp
        val dr = roll - zr
        val level = Math.hypot(dp.toDouble(), dr.toDouble()).toFloat()
        val isLevel = level < 1f

        // Audio + haptic feedback, driven by composition: crossing into
        // level ticks once (vibrate 30ms); inside level, a soft beat.
        LaunchedEffect(isLevel) {
            if (isLevel && !wasLevel) {
                try {
                    (context.getSystemService(Context.VIBRATOR_SERVICE)
                        as android.os.Vibrator).let { vib ->
                        if (vib.hasVibrator()) {
                            vib.vibrate(
                                android.os.VibrationEffect.createOneShot(
                                    30,
                                    android.os.VibrationEffect.DEFAULT_AMPLITUDE,
                                ),
                            )
                        }
                    }
                } catch (_: Exception) {
                }
            }
            wasLevel = isLevel
        }
        LaunchedEffect(isLevel, sound) {
            if (!sound) return@LaunchedEffect
            while (isLevel) {
                try {
                    tone?.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 120)
                } catch (_: Exception) {
                }
                kotlinx.coroutines.delay(600)
            }
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
                text = "%.1f°".format(level.toDouble()),
                style = MaterialTheme.typography.displayLarge,
                color = if (level < 1f) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = if (isLevel) "Level" else "Tilted",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Bubble(
                dx = (dr / 45f).coerceIn(-1f, 1f),
                dy = (dp / 45f).coerceIn(-1f, 1f),
                level = isLevel,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Sound",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.Switch(
                    checked = sound,
                    onCheckedChange = { sound = it },
                )
            }
            Text(
                text = "Lay flat, then tap Calibrate to zero this surface.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.Button(
                onClick = { zero = pitch to roll },
            ) {
                Text(if (zero == null) "Calibrate" else "Re-calibrate")
            }
        }
    }
}

@Composable
private fun Bubble(dx: Float, dy: Float, level: Boolean, modifier: Modifier = Modifier) {
    val ring = MaterialTheme.colorScheme.outlineVariant
    val bubble = if (level) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        drawCircle(color = ring, radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
        drawCircle(color = ring, radius = r * 0.25f, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
        drawCircle(
            color = bubble,
            radius = r * 0.16f,
            center = Offset(size.width / 2f + dx * r * 0.8f, size.height / 2f + dy * r * 0.8f),
        )
    }
}

/** The hardware gate every tool shows when its sensor is absent. */
@Composable
fun NoSensor(modifier: Modifier = Modifier, name: String) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No $name on this phone.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "This tool needs hardware yours doesn't have.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
