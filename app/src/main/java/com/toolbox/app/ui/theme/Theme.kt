package com.toolbox.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// House look: saffron on near-black, dark-only (documented choice).
val Saffron = Color(0xFFFF9F0A)
val InkOnSaffron = Color(0xFF1A1207)

private val DarkColorScheme = darkColorScheme(
    primary = Saffron,
    onPrimary = InkOnSaffron,
    secondary = Saffron,
    onSecondary = InkOnSaffron,
    background = Color(0xFF121212),
    onBackground = Color(0xFFF5EFE4),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF5EFE4),
    surfaceVariant = Color(0xFF2E2E33),
    onSurfaceVariant = Color(0xFFB8B0A4),
    surfaceContainerHighest = Color(0xFF333438),
    tertiaryContainer = Color(0xFF2A2118),
    onTertiaryContainer = Color(0xFFF5EFE4),
)

val ToolboxShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

val ToolboxTypography = Typography(
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun ToolboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = ToolboxTypography,
        shapes = ToolboxShapes,
        content = content,
    )
}
