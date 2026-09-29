package com.krafttools.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min

/**
 * Tally + stopwatch. Full-viewport composition:
 *   hero count  ->  the tally-mark wall (also the primary tap target)
 *   stopwatch   ->  docked lap log that absorbs the remaining height.
 * Nothing is padded out with blankness; every zone is instrument.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TallyScreen(onBack: () -> Unit) {
    var count by rememberSaveable { mutableStateOf(0) }
    var running by rememberSaveable { mutableStateOf(false) }
    var elapsedMs by rememberSaveable { mutableLongStateOf(0L) }
    val laps = rememberSaveable(saver = stringListSaver) { mutableStateListOf<String>() }
    val view = LocalView.current

    // Volume keys count while this screen is up (installed here,
    // cleared below — MainActivity only forwards when installed).
    DisposableEffect(Unit) {
        TallyVolumeKeys.onVolume = { count++ }
        onDispose { TallyVolumeKeys.onVolume = null }
    }

    // Stopwatch ticker: 100 ms ticks while running.
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = android.os.SystemClock.elapsedRealtime()
        while (running) {
            delay(100)
            val now = android.os.SystemClock.elapsedRealtime()
            elapsedMs += now - last
            last = now
        }
    }

    ToolScaffold(

        title = "Tally + stopwatch",

        onBack = onBack,

    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Optical weight, not filler: the hero sits slightly above
            // centre the way a gauge does, the wall below carries the eye.
            Spacer(modifier = Modifier.weight(0.22f))

            ReadingHeader(
                value = "$count",
                unit = null,
                status = gateReadout(count),
            )

            // The wall is the instrument AND the biggest touch target:
            // pocket counting works with gloves, eyes elsewhere.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.25f)
                    .padding(horizontal = 6.dp)
                    .clickable {
                        Haptics.tick(view)
                        count++
                    }
                    .instrumentSemantics(
                        label = "Tally marks",
                        value = "$count of ${count + 1} in this group of five",
                        hint = "double tap to add one mark",
                        onClickAction = {
                            Haptics.tick(view)
                            count++
                        },
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                TallyMarks(count = count, modifier = Modifier.fillMaxSize())
                if (count == 0) {
                    Text(
                        text = "TAP ANYWHERE, OR USE THE VOLUME KEYS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            Text(
                text = formatStopwatch(elapsedMs),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        Haptics.confirm(view)
                        running = !running
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(if (running) "Pause" else "Start")
                }
                OutlinedButton(
                    onClick = {
                        Haptics.tick(view)
                        laps.add(0, "#${laps.size + 1}  ${formatStopwatch(elapsedMs)}")
                    },
                    enabled = running,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Lap")
                }
                OutlinedButton(
                    onClick = {
                        Haptics.confirm(view)
                        running = false
                        elapsedMs = 0L
                        laps.clear()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Clear")
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        Haptics.tick(view)
                        count++
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("+1")
                }
                OutlinedButton(
                    onClick = {
                        Haptics.tick(view)
                        if (count > 0) count--
                    },
                    enabled = count > 0,
                    modifier = Modifier.touchTarget(),
                ) {
                    Text("−1")
                }
                OutlinedButton(
                    onClick = {
                        Haptics.confirm(view)
                        count = 0
                    },
                    enabled = count > 0,
                    modifier = Modifier.touchTarget(),
                ) {
                    Text("Reset")
                }
            }

            LapLog(
                laps = laps,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }

    }
}

/**
 * The tally wall: real tally marks, five to a gate (four strokes, one
 * diagonal). Completed groups cool to graphite, the group in progress
 * burns cyan and springs up from the baseline. The baseline is a ruled
 * scale with per-gate ticks, so an empty screen still reads as an
 * instrument waiting for input rather than a blank rectangle.
 */
@Composable
private fun TallyMarks(count: Int, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val rule = MaterialTheme.colorScheme.outline
    // Expressive spring: the newest mark overshoots slightly, then settles.
    val grow = remember { Animatable(1f) }
    LaunchedEffect(count) {
        if (count == 0) return@LaunchedEffect
        grow.snapTo(0f)
        grow.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 520f),
        )
    }

    Canvas(modifier = modifier) {
        val baseY = size.height * 0.9f
        val maxH = size.height * 0.74f
        val groups = (count + 4) / 5

        // Fit: the tallest mark height that still lays every gate out
        // on the wall.
        //
        // The old code coupled height, column count and row count and
        // iterated four times hoping to settle. It does not settle: each
        // answer changes the question, and the loop ended wherever it
        // happened to be on the fourth pass. Past about forty marks that
        // landed on a single row of ever-shorter bars marching off the
        // right-hand edge — technically a fit, visually a smear, and a
        // waste of the space above the baseline.
        //
        // So the row count is decided first and the height follows from
        // it, which is a one-way dependency that cannot oscillate. The
        // block also stays roughly as wide as it is tall rather than
        // stretching into a ribbon.
        val targetRows = if (groups <= 1) {
            1
        } else {
            ceil(groups / MAX_COLUMNS.toFloat()).toInt().coerceAtLeast(1)
        }
        var h = min(maxH, baseY / (targetRows * ROW_PITCH))
        var cols = columnsFor(size.width, h)
        // If the height that fits those rows leaves the block narrow,
        // spend the spare width on more columns and re-fit once.
        repeat(2) {
            val wanted = (groups + targetRows - 1) / targetRows
            if (cols >= wanted) return@repeat
            cols = wanted.coerceAtMost(columnsFor(size.width, h))
        }
        val groupGap = h * 0.3f
        val slotW = h * 0.6f + groupGap

        // The ruled scale exists at every count, including zero: an empty
        // wall is a calibrated panel waiting for marks, not a void.
        for (c in 0..cols) {
            val x = c * slotW - groupGap / 2f
            if (x < 0f || x > size.width) continue
            // Faint guide up the full height, firm tick at the baseline:
            // the slot exists before it is ever filled.
            drawLine(
                color = rule.copy(alpha = 0.18f),
                start = Offset(x, baseY - h * 0.1f),
                end = Offset(x, 0f),
                strokeWidth = 1.5.dp.toPx(),
            )
            drawLine(
                color = rule,
                start = Offset(x, baseY),
                end = Offset(x, baseY - h * 0.1f),
                strokeWidth = 2.dp.toPx(),
            )
        }
        // Ceiling at the settled mark height: defines the scale's top.
        drawLine(
            color = rule.copy(alpha = 0.12f),
            start = Offset(0f, baseY - h),
            end = Offset(size.width, baseY - h),
            strokeWidth = 1.5.dp.toPx(),
        )
        drawLine(
            color = rule.copy(alpha = 0.7f),
            start = Offset(0f, baseY),
            end = Offset(size.width, baseY),
            strokeWidth = 3.dp.toPx(),
        )

        if (groups == 0) return@Canvas

        val gapX = h * 0.15f
        val groupW = gapX * 4f
        val rowPitch = h * ROW_PITCH
        val sw = (h * 0.07f).coerceAtLeast(2.dp.toPx())

        repeat(groups) { g ->
            val col = g % cols
            val row = g / cols
            val x0 = col * (groupW + groupGap)
            val y0 = baseY - row * rowPitch
            val marks = (count - g * 5).coerceIn(0, 5)
            val isLive = g == groups - 1
            val color = if (isLive) accent else ink
            // Only the live gate animates; finished gates are settled ink.
            val hh = h * (if (isLive) grow.value else 1f)
            if (hh <= 0f || y0 < -h) return@repeat
            for (i in 0 until marks.coerceAtMost(4)) {
                val x = x0 + i * gapX
                // A touch of slant: hand-cut strokes, not machine bars.
                val slant = h * 0.05f
                drawLine(
                    color = color,
                    start = Offset(x, y0),
                    end = Offset(x + slant, y0 - hh),
                    strokeWidth = sw,
                )
            }
            if (marks == 5) {
                drawLine(
                    color = color,
                    start = Offset(x0 - h * 0.1f, y0 - hh * 0.05f),
                    end = Offset(x0 + groupW + h * 0.1f, y0 - hh * 0.95f),
                    strokeWidth = sw * 1.15f,
                )
            }
        }
    }
}

/** Row spacing as a multiple of mark height — the wall's vertical rhythm. */
private const val ROW_PITCH = 1.12f

/**
 * The most gates a row may hold before the block wraps.
 *
 * A tally wall is a block of gates, not a scroll. Past eight the marks
 * stop being countable at a glance and start being a scale, which is
 * the thing this tool exists not to be.
 */
private const val MAX_COLUMNS = 8

/** Gate language: five to a gate, the way the marks are actually drawn. */
private fun gateReadout(count: Int): String {
    if (count == 0) return "tally"
    val into = count % 5
    val gate = count / 5 + 1
    return if (into == 0) "gate $gate complete" else "$into into gate $gate"
}

/** How many five-gate groups fit across [width] at a given mark height. */
private fun columnsFor(width: Float, h: Float): Int {
    val gateW = h * 0.6f
    val gap = h * 0.3f
    return ((width + gap) / (gateW + gap)).toInt().coerceAtLeast(1)
}

/**
 * Docked lap log. Fills whatever height is left, so the screen ends in
 * a panel rather than a void — and an empty panel says so plainly.
 */
@Composable
private fun LapLog(laps: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "LAPS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (laps.isEmpty()) "—" else "${laps.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = if (laps.isEmpty()) Alignment.Center else Alignment.TopCenter,
        ) {
            if (laps.isEmpty()) {
                Text(
                    text = "No laps yet. Start the clock, then tap Lap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    laps.take(8).forEach { lap ->
                        Text(
                            text = lap,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

private fun formatStopwatch(ms: Long): String {
    val totalTenths = ms / 100
    val tenths = totalTenths % 10
    val seconds = (totalTenths / 10) % 60
    val minutes = (totalTenths / 600) % 60
    val hours = totalTenths / 36000
    return if (hours > 0) {
        "%d:%02d:%02d.%d".format(Locale.ROOT, hours, minutes, seconds, tenths)
    } else {
        "%02d:%02d.%d".format(Locale.ROOT, minutes, seconds, tenths)
    }
}

/**
 * Volume-key counting, owned by the tally screen. MainActivity forwards
 * volume presses here only while this handler is installed (set on
 * entering tally, cleared on leaving) — so volume behaves normally
 * everywhere else. No permission, no focus tricks.
 */
object TallyVolumeKeys {
    var onVolume: (() -> Unit)? = null
}
