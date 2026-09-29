package com.krafttools.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krafttools.app.R

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
    // M3's stock secondaryContainer is mauve — a stranger in an
    // instrument panel. Every container that lights up lights up cyan.
    secondaryContainer = Color(0xFF12313C),
    onSecondaryContainer = InstrumentCyan,
)

val ToolboxShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

// Brand voice: Space Grotesk (OFL) for numerals and headlines —
// instrument-panel character. One variable file, four weight
// instances; body text stays system Roboto (long-form readability
// plus clean Devanagari fallback, which Grotesk lacks).
private val Grotesk = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.space_grotesk, FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.space_grotesk, FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.space_grotesk, FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

val ToolboxTypography = Typography(
    // "tnum" is tabular figures: every digit occupies the same advance
    // width. Without it a live readout in a proportional face SHIFTS
    // horizontally as the value changes, because "1" is narrower than
    // "8", so a speedometer counting 9 -> 10 visibly jitters and a
    // clocked value appears to crawl. It matters on every style that
    // ever shows a number that changes, which in this app is most of
    // them: the speed dial, the bearing, the altitude, the exposure
    // value, the trigger threshold, the sound offset.
    displayLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp, fontFamily = Grotesk, fontFeatureSettings = "tnum"),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp, fontFamily = Grotesk, fontFeatureSettings = "tnum"),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp, fontFamily = Grotesk, fontFeatureSettings = "tnum"),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp, fontFamily = Grotesk, fontFeatureSettings = "tnum"),
    bodyLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    // The gutter labels on every trace, and the channel numbers on the
    // WiFi spectrum, are numbers too — and they are measured and drawn
    // into a canvas, where a width change shifts the bar underneath.
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp, fontFeatureSettings = "tnum"),
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
