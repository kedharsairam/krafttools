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
import java.util.Locale

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
