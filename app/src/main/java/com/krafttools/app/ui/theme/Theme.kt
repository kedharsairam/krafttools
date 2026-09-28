package com.krafttools.app.ui.theme

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

// Instrument cyan on near-black: precision-tool identity, dark-only
// (documented choice). GitaKraft keeps saffron; KraftTools is its own.
val InstrumentCyan = Color(0xFF56CCF2)
val InkOnCyan = Color(0xFF06222B)

private val DarkColorScheme = darkColorScheme(
    primary = InstrumentCyan,
    onPrimary = InkOnCyan,
    secondary = InstrumentCyan,
    onSecondary = InkOnCyan,
    background = Color(0xFF121214),
    onBackground = Color(0xFFF2F5F7),
    surface = Color(0xFF1C1C20),
    onSurface = Color(0xFFF2F5F7),
    surfaceVariant = Color(0xFF2E2E35),
    onSurfaceVariant = Color(0xFFB4BCC4),
    surfaceContainerHighest = Color(0xFF35363E),
    tertiaryContainer = Color(0xFF18242B),
    onTertiaryContainer = Color(0xFFF2F5F7),
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
