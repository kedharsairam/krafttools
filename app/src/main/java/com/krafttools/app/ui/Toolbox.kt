package com.krafttools.app.ui
import com.krafttools.app.R

import androidx.compose.ui.res.painterResource
import androidx.annotation.DrawableRes
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
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.size

private data class Tool(
    val route: String,
    val name: String,
    val icon: ImageVector,
    /**
     * A drawn glyph, when the honest one is not in the icon set.
     *
     * Material has no bubble level, and its nearest neighbour is a pair
     * of weighing scales — which measures mass, not angle. One tool in
     * this list is worth a glyph of its own rather than the closest
     * available substitute.
     */
    @DrawableRes val art: Int? = null,
)

private val TOOLS = listOf(
    Tool("level", "Spirit level", Icons.Filled.Balance, art = R.drawable.ic_spirit_level),
    Tool("compass", "Compass", Icons.Filled.Explore),
    Tool("torch", "Torch + strobe", Icons.Filled.FlashlightOn),
    Tool("vibration", "Vibration meter", Icons.Filled.Vibration),
    Tool("tally", "Tally + stopwatch", Icons.Filled.Timer),
    Tool("qr", "QR scanner", Icons.Filled.QrCode2),
    Tool("wifi", "WiFi analyzer", Icons.Filled.Wifi),
    Tool("lux", "Light meter", Icons.Filled.Lightbulb),
    Tool("emf", "Metal + EMF", Icons.Filled.SettingsInputAntenna),
    Tool("db", "Sound meter", Icons.Filled.Mic),
    Tool("color", "Color picker", Icons.Filled.Palette),
    Tool("angle", "Angle ruler", Icons.Filled.Architecture),
    Tool("speed", "Speedometer", Icons.Filled.Speed),
    Tool("pressure", "Barometer", Icons.Filled.Air),
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
            // Every tool is live, so the grid used to re-check the
            // flag on every tap before navigating. The flag is gone.
            ToolGrid(onOpen = { route -> nav.navigate(route) })
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
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.25f)
                        .clickable { onOpen(tool.route) }
                        .semantics {
                            contentDescription = tool.name
                        },
                ) {
                    // A tile is a picture of the tool and its name. The
                    // icon is the picture, so it is centred and large
                    // enough to be recognised at a glance; the name sits
                    // on the bottom edge, which is where a label belongs
                    // when the thing it labels is above it.
                    //
                    // It used to carry a "Ready" line under the name.
                    // All fourteen tools said "Ready", so it was a
                    // constant wearing a status label's clothes — and
                    // the `live` flag behind it had no false value left
                    // in the app. Both are gone.
                    // The icon and its name are one group, centred.
                    // Pinning the icon to the top edge and the name to
                    // the bottom left a band of empty card between them
                    // that read as a layout mistake rather than as
                    // breathing room.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (tool.art != null) {
                            Icon(
                                painter = painterResource(tool.art),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(46.dp),
                            )
                        } else {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            // 46dp rather than 38. In a 183dp-wide
                            // tile, 38 is a third smaller than the
                            // icon wants to be, and the group left
                            // 70dp of empty card below it. 46 is a
                            // quarter of the width, which is the
                            // proportion a grid of tiles wants.
                            modifier = Modifier.size(46.dp),
                        )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = tool.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
