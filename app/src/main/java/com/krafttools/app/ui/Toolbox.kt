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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
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
fun ToolboxNav(startRoute: String? = null) {
    val nav = rememberNavController()
    val valid = TOOLS.map { it.route }.toSet()
    val start = if (startRoute in valid) startRoute!! else "grid"
    NavHost(
        navController = nav,
        startDestination = start,
        // Phase C: directional transitions, so moving between
        // instruments feels like turning a page rather than cutting.
        // Forward navigation slides in from the trailing edge, back
        // slides out to it — the gesture the user's thumb already
        // expects from every other app on the phone.
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(280, easing = FastOutSlowInEasing),
            ) + fadeIn(tween(180))
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(280, easing = FastOutSlowInEasing),
            ) + fadeOut(tween(140))
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(280, easing = FastOutSlowInEasing),
            ) + fadeIn(tween(180))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(280, easing = FastOutSlowInEasing),
            ) + fadeOut(tween(140))
        },
    ) {
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
    val view = LocalView.current
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
        val orientation = orientationOf(g)
        val (zp, zr) = zero ?: (0f to 0f)
        val tilt = Tilt(
            orientation.pitchDeg - zp,
            orientation.rollDeg - zr,
            isUseless = orientation.isUseless,
        )
        val level = tilt.magnitude
        val isLevel = tilt.isLevel && !tilt.isUseless
        val near = tilt.isNear

        // Phase B: the success moment. Crossing into level flashes the
        // vial green and confirms with a haptic — the whole point of a
        // spirit level is knowing you got there without looking.
        LaunchedEffect(isLevel) {
            if (isLevel && !wasLevel) Haptics.confirm(view)
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            ReadingHeader(
                value = "%.1f".format(level.toDouble()),
                unit = "°",
                status = when {
                    tilt.isUseless -> "turn over"
                    isLevel -> "level"
                    near -> "nearly"
                    else -> "tilted"
                },
                live = isLevel || near,
            )
            // The instruction is the actionable part: which way to move.
            Text(
                text = tiltInstruction(tilt) ?: if (isLevel) "hold still" else "—",
                style = MaterialTheme.typography.titleMedium,
                color = if (isLevel) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            val (bx, by) = bubbleOffset(tilt)
            Bubble(
                dx = bx,
                dy = by,
                level = isLevel,
                near = near,
                useless = tilt.isUseless,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            // Raw pitch and roll: the numbers behind the bubble, so a
            // user can read a surface the vial cannot resolve.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                AngleStat("pitch", tilt.pitchDeg)
                AngleStat("roll", tilt.rollDeg)
                AngleStat("zero", if (zero == null) null else 0f)
            }

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
                    onCheckedChange = {
                        Haptics.tick(view)
                        sound = it
                    },
                )
            }
            Text(
                text = "Lay flat, then Calibrate to zero this surface. " +
                    "The vial is level within 1°.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    Haptics.confirm(view)
                    zero = orientation.pitchDeg to orientation.rollDeg
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(if (zero == null) "Calibrate this surface" else "Re-calibrate")
            }
        }
    }
}

/** One labeled angle under the vial. */
@Composable
private fun AngleStat(label: String, degrees: Float?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (degrees == null) "—" else "%+.1f°".format(degrees),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The vial. Machined bezel, a target cross at the centre, and a bubble
 * that lags on a spring like liquid in a real tube. Crossing into
 * level washes the housing green (Phase B) — a level that confirms
 * only in text is a level you have to read.
 */
@Composable
private fun Bubble(
    dx: Float,
    dy: Float,
    level: Boolean,
    near: Boolean,
    useless: Boolean,
    modifier: Modifier = Modifier,
) {
    val ring = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    val success = Color(0xFF3DDC84)
    val danger = MaterialTheme.colorScheme.error
    // Spring physics on the bubble: it lags and settles like a real
    // vial instead of teleporting with the sensor.
    val adx by androidx.compose.animation.core.animateFloatAsState(
        targetValue = dx,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.55f,
            stiffness = 220f,
        ),
        label = "bubbleX",
    )
    val ady by androidx.compose.animation.core.animateFloatAsState(
        targetValue = dy,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.55f,
            stiffness = 220f,
        ),
        label = "bubbleY",
    )
    // The wash blooms in rather than snapping, so success feels like
    // an event instead of a state change.
    val flash by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (level) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.6f,
            stiffness = 260f,
        ),
        label = "levelFlash",
    )
    val bubbleColor = when {
        level -> success
        useless -> danger
        near -> accent
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        // Machined bezel: outer ring + cardinal ticks give the vial
        // a physical housing instead of floating lines.
        drawCircle(color = ring, radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
        drawCircle(color = ring, radius = r * 0.97f, style = androidx.compose.ui.graphics.drawscope.Stroke(10f))
        // Success wash, inside the housing.
        if (flash > 0.01f) {
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        success.copy(alpha = 0.22f * flash),
                        success.copy(alpha = 0f),
                    ),
                    center = Offset(cx, cy),
                    radius = r * 0.95f,
                ),
                radius = r * 0.95f,
                center = Offset(cx, cy),
            )
        }
        for (deg in 0 until 360 step 15) {
            val rad = Math.toRadians(deg.toDouble())
            val major = deg % 90 == 0
            val r1 = if (major) 0.86f else 0.92f
            drawLine(
                color = if (major) ring else ring.copy(alpha = 0.6f),
                start = Offset(
                    cx + (r * r1 * Math.sin(rad)).toFloat(),
                    cy - (r * r1 * Math.cos(rad)).toFloat(),
                ),
                end = Offset(
                    cx + (r * 0.97f * Math.sin(rad)).toFloat(),
                    cy - (r * 0.97f * Math.cos(rad)).toFloat(),
                ),
                strokeWidth = if (major) 5f else 2f,
            )
        }
        // Target cross: what "level" looks like, drawn even when empty.
        val targetR = r * 0.25f
        drawCircle(color = ring, radius = targetR, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
        drawLine(
            color = ring.copy(alpha = 0.7f),
            start = Offset(cx - targetR * 1.5f, cy),
            end = Offset(cx + targetR * 1.5f, cy),
            strokeWidth = 2f,
        )
        drawLine(
            color = ring.copy(alpha = 0.7f),
            start = Offset(cx, cy - targetR * 1.5f),
            end = Offset(cx, cy + targetR * 1.5f),
            strokeWidth = 2f,
        )
        // The bubble: a body with a highlight, not a flat dot.
        val bR = r * 0.16f
        val bc = Offset(cx + adx * r * 0.8f, cy + ady * r * 0.8f)
        drawCircle(
            color = bubbleColor.copy(alpha = 0.25f),
            radius = bR * 1.9f,
            center = bc,
        )
        drawCircle(color = bubbleColor, radius = bR, center = bc)
        drawCircle(
            color = Color.White.copy(alpha = 0.45f),
            radius = bR * 0.38f,
            center = Offset(bc.x - bR * 0.3f, bc.y - bR * 0.3f),
        )
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
    // Spring physics on the bubble: it lags and settles like a real
    // vial instead of teleporting with the sensor.
    val adx by androidx.compose.animation.core.animateFloatAsState(
        targetValue = dx,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.55f,
            stiffness = 220f,
        ),
        label = "bubbleX",
    )
    val ady by androidx.compose.animation.core.animateFloatAsState(
        targetValue = dy,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.55f,
            stiffness = 220f,
        ),
        label = "bubbleY",
    )
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        // Machined bezel: outer ring + cardinal ticks give the vial
        // a physical housing instead of floating lines.
        drawCircle(color = ring, radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
        drawCircle(color = ring, radius = r * 0.97f, style = androidx.compose.ui.graphics.drawscope.Stroke(10f))
        for (deg in listOf(0f, 90f, 180f, 270f)) {
            val rad = Math.toRadians(deg.toDouble())
            val x1 = size.width / 2f + (r * 0.88f * Math.sin(rad)).toFloat()
            val y1 = size.height / 2f - (r * 0.88f * Math.cos(rad)).toFloat()
            val x2 = size.width / 2f + (r * 0.97f * Math.sin(rad)).toFloat()
            val y2 = size.height / 2f - (r * 0.97f * Math.cos(rad)).toFloat()
            drawLine(ring, Offset(x1, y1), Offset(x2, y2), 5f)
        }
        drawCircle(color = ring, radius = r * 0.25f, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
        drawCircle(
            color = bubble,
            radius = r * 0.16f,
            center = Offset(size.width / 2f + adx * r * 0.8f, size.height / 2f + ady * r * 0.8f),
        )
    }
}

