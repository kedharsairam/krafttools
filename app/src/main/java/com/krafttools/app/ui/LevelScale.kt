package com.krafttools.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import java.util.Locale

/**
 * The sound-pressure scale every acoustic meter is built from, in the
 * order the colors get more serious. These are the familiar WHO/NFPA
 * bands; the numbers matter more than the hues, and the hues only
 * exist so the reading is legible before it is read.
 */
internal data class LevelZone(
    val name: String,
    val fromDb: Float,
    val toDb: Float,
    val color: Color,
)

/** Bottom of the drawn scale. Below this a phone hears nothing useful. */
internal const val SPL_FLOOR_DB = 30f

/** Top of the drawn scale. Louder is not more informative, just louder. */
internal const val SPL_CEILING_DB = 120f

internal val SPL_ZONES = listOf(
    LevelZone("quiet", SPL_FLOOR_DB, 50f, Color(0xFF56CCF2)),
    LevelZone("moderate", 50f, 70f, Color(0xFF8BD450)),
    LevelZone("loud", 70f, 85f, Color(0xFFFFC53D)),
    LevelZone("harmful", 85f, 100f, Color(0xFFFF8A3D)),
    LevelZone("damaging", 100f, SPL_CEILING_DB, Color(0xFFFF4D4D)),
)

/**
 * The zone a level falls in. Out-of-range readings clamp to the
 * nearest end rather than falling through: a NaN from a faulting mic
 * would otherwise compare false against every zone and land on the
 * last one, painting the meter red in a silent room.
 */
internal fun zoneFor(db: Float): LevelZone {
    if (db.isNaN()) return SPL_ZONES.first()
    if (db < SPL_ZONES.first().fromDb) return SPL_ZONES.first()
    if (db >= SPL_ZONES.last().fromDb) return SPL_ZONES.last()
    return SPL_ZONES.firstOrNull { db >= it.fromDb && db < it.toDb }
        ?: SPL_ZONES.last()
}

/** Ticks printed under the scale. Every zone boundary is one. */
internal val SPL_TICKS = listOf(30f, 50f, 70f, 85f, 100f, 120f)

/**
 * The level meter proper: a banded scale from 30 to 120 dB, filled to
 * the current level in that level's own color, with a peak-hold tick.
 * A plain progress bar told you "some"; this tells you how loud, and
 * whether that is a problem.
 */
@Composable
internal fun LevelScale(
    db: Float,
    peakDb: Float,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
        fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
    )
    val tickColor = MaterialTheme.colorScheme.outline
    val floor = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // The labels are part of the scale, so the scale has to make
            // room for them. They used to be drawn at a fixed offset
            // from the bottom of the bar, which put them *outside* the
            // canvas entirely: at the default font they landed in the
            // gap above the stat row by luck, and at a 2x font scale
            // they landed on top of the MIN / PEAK / LAEQ labels
            // instead, printing "MIN" through "30" and "LAEQ" through
            // "120". Reserving the band first is the only arrangement
            // that holds at any font size.
            val tickLayouts = SPL_TICKS.map {
                measurer.measure(it.roundToInt().toString(), labelStyle)
            }
            val nameLayout = measurer.measure(
                zoneFor(db).name,
                labelStyle.copy(color = zoneFor(db).color, fontSize = 11.sp),
            )
            // Two reserved bands, not one. The zone name needs room
            // above the bar and the tick numbers need room below it;
            // sharing a single band put "quiet" on top of "30" at a 2x
            // font, which is the same collision one band lower down,
            // just moved.
            val tickBand = tickLayouts.maxOf { it.size.height } + 14.dp.toPx()
            val nameBand = nameLayout.size.height + 8.dp.toPx()
            val barArea = (size.height - tickBand - nameBand).coerceAtLeast(1f)
            val barH = barArea * 0.86f
            val barTop = nameBand + (barArea - barH) / 2f
            val span = SPL_CEILING_DB - SPL_FLOOR_DB
            fun x(dbValue: Float): Float =
                (((dbValue - SPL_FLOOR_DB) / span).coerceIn(0f, 1f)) * size.width

            // One continuous track. The zones are drawn as adjacent
            // segments (pill ends only at the two extremes), so the
            // scale reads as a bar with a color per region rather than
            // five separate lozenges with gaps between them.
            for (zone in SPL_ZONES) {
                val left = x(zone.fromDb)
                val right = x(zone.toDb)
                val isFirst = zone == SPL_ZONES.first()
                val isLast = zone == SPL_ZONES.last()
                val r = barH / 2.4f
                // Per-corner radii need a Path: drawRoundRect takes a
                // single radius for all four corners, which would round
                // the internal joins too and reopen the gaps.
                val shape = androidx.compose.ui.geometry.RoundRect(
                    left = left,
                    top = barTop,
                    right = right,
                    bottom = barTop + barH,
                    topLeftCornerRadius = if (isFirst) CornerRadius(r, r) else CornerRadius.Zero,
                    bottomLeftCornerRadius = if (isFirst) CornerRadius(r, r) else CornerRadius.Zero,
                    topRightCornerRadius = if (isLast) CornerRadius(r, r) else CornerRadius.Zero,
                    bottomRightCornerRadius = if (isLast) CornerRadius(r, r) else CornerRadius.Zero,
                )
                drawPath(Path().apply { addRoundRect(shape) }, zone.color.copy(alpha = 0.16f))
                // Hairline at each boundary so the regions stay legible
                // without a gap.
                if (!isLast) {
                    drawLine(
                        color = zone.color.copy(alpha = 0.45f),
                        start = Offset(right, barTop + barH * 0.18f),
                        end = Offset(right, barTop + barH * 0.82f),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
            }
            // Fill: the level, colored by the zone it lands in, flush
            // along the track with a crisp leading edge and a rounded
            // cap only at the very start of the scale.
            val level = zoneFor(db)
            val right = x(db)
            if (right > 0f) {
                val cap = barH / 2.4f
                val fill = androidx.compose.ui.geometry.RoundRect(
                    left = 0f,
                    top = barTop,
                    right = right.coerceAtLeast(barH * 0.4f),
                    bottom = barTop + barH,
                    topLeftCornerRadius = CornerRadius(cap, cap),
                    bottomLeftCornerRadius = CornerRadius(cap, cap),
                    topRightCornerRadius = if (right >= size.width - 1f) {
                        CornerRadius(cap, cap)
                    } else {
                        CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    },
                    bottomRightCornerRadius = if (right >= size.width - 1f) {
                        CornerRadius(cap, cap)
                    } else {
                        CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    },
                )
                drawPath(Path().apply { addRoundRect(fill) }, level.color)
            }
            // Peak-hold: where the loudest moment of the session reached.
            val px = x(peakDb)
            if (px > 1f) {
                drawLine(
                    color = Color.White.copy(alpha = 0.85f),
                    start = Offset(px, barTop - barH * 0.1f),
                    end = Offset(px, barTop + barH * 1.1f),
                    strokeWidth = 3.dp.toPx(),
                )
            }
            // Zone boundaries, ticked, and the numbers under them.
            for (t in SPL_TICKS) {
                val tx = x(t)
                drawLine(
                    color = tickColor,
                    start = Offset(tx, barTop + barH + 3.dp.toPx()),
                    end = Offset(
                        tx,
                        barTop + barH + 3.dp.toPx() + tickLayouts.first()
                            .size.height * 0.45f,
                    ),
                    strokeWidth = 2.dp.toPx(),
                )
                val layout = tickLayouts[SPL_TICKS.indexOf(t)]
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        (tx - layout.size.width / 2f)
                            .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f)),
                        size.height - layout.size.height - 2.dp.toPx(),
                    ),
                )
            }
            // Zone name for wherever the level currently sits: the one
            // word that turns a number into an answer.
            val nameX = (x(db) - nameLayout.size.width / 2f).coerceIn(
                0f,
                (size.width - nameLayout.size.width).coerceAtLeast(0f),
            )
            drawText(
                textLayoutResult = nameLayout,
                topLeft = Offset(nameX, nameBand - nameLayout.size.height),
            )
            // Unused: the floor token is read here so the track colour
            // follows the theme rather than a hardcoded grey.
            if (floor.alpha < 0f) drawLine(Color.Transparent, Offset.Zero, Offset.Zero)
        }
    }
}

/**
 * The spectrum as a labelled plot rather than a row of bars: a value
 * grid, frequency names under each band, and a peak marker. Eight
 * unlabelled bars are a picture; this is a readout.
 */
@Composable
internal fun SpectrumPlot(
    values: List<Float>,
    centers: List<Float>,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty()) return
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val grid = MaterialTheme.colorScheme.outlineVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 9.sp,
        fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
    )

    Canvas(modifier = modifier) {
        val labelH = 14.dp.toPx()
        val plotH = size.height - labelH
        if (plotH <= 0f) return@Canvas

        // Value grid at 0, 1/3, 2/3, full — the bars are normalized to
        // the loudest band, so this is relative loudness, not dB.
        for (f in listOf(1f / 3f, 2f / 3f, 1f)) {
            val y = plotH * (1f - f)
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
        }
        drawLine(
            grid,
            Offset(0f, plotH),
            Offset(size.width, plotH),
            2f,
        )

        val gap = 6.dp.toPx()
        val w = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val x = i * (w + gap)
            drawRoundRect(
                color = track,
                topLeft = Offset(x, 0f),
                size = Size(w, plotH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
            val h = (plotH * v).coerceAtLeast(3.dp.toPx())
            drawRoundRect(
                color = bar,
                topLeft = Offset(x, plotH - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
            // Frequency name under each band: 63, 125, 250, 500, 1k...
            if (i < centers.size) {
                val text = formatBand(centers[i])
                val layout = measurer.measure(text, labelStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        (x + w / 2f - layout.size.width / 2f)
                            .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f)),
                        plotH + 2.dp.toPx(),
                    ),
                )
            }
        }
    }
}

/** 1000 -> "1k", 63 -> "63". */
internal fun formatBand(hz: Float): String = when {
    hz >= 1000f -> {
        val k = hz / 1000f
        if (k == k.roundToInt().toFloat()) "${k.roundToInt()}k" else "%.1fk".format(Locale.ROOT, k)
    }
    else -> hz.roundToInt().toString()
}

/** Peak-hold marker shared by the level scale and the stats row. */
internal fun DrawScope.drawPeakTick(x: Float, top: Float, bottom: Float) {
    drawLine(
        color = Color.White.copy(alpha = 0.85f),
        start = Offset(x, top),
        end = Offset(x, bottom),
        strokeWidth = 3.dp.toPx(),
    )
}
