package com.krafttools.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.krafttools.app.MainActivity
import com.krafttools.app.data.BaroStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first

/**
 * Barometer trend widget. No workers, no timers, no background
 * sampling — a widget that costs nothing until touched is the only kind
 * this app ships.
 *
 * Tapping opens the app. It does NOT refresh the widget: this class
 * makes no `updateAll` call and there is no route extra on the intent,
 * so it lands on the tool grid rather than the barometer. The old
 * KDoc claimed tapping "updates from the last screen reading", which
 * was never implemented.
 */
class BaroWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val reading = try {
            BaroStore(context).reading.first()
        } catch (_: Exception) {
            null
        }
        provideContent {
            WidgetBody(
                hpa = reading?.hpa,
                trend = reading?.trend,
                atMillis = reading?.atMillis ?: 0L,
            )
        }
    }
}

@Composable
private fun WidgetBody(hpa: Float?, trend: String?, atMillis: Long) {
    val time = if (atMillis > 0L) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(atMillis))
    } else {
        "--:--"
    }
    // Default widget styling (system picks light/dark): no hardcoded
    // colors — Glance 1.1 ColorProviders would fight the launcher theme.
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (hpa != null) "%.1f hPa".format(Locale.ROOT, hpa) else "— hPa",
            style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                // Honest about where the tap goes: the tool grid.
                text = trend ?: "Tap to open",
                style = TextStyle(fontSize = 13.sp),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = time,
                style = TextStyle(fontSize = 13.sp),
            )
        }
    }
}

class BaroWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BaroWidget()
}
