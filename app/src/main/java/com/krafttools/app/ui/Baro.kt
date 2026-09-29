package com.krafttools.app.ui

import android.hardware.Sensor
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale

/** Seconds between samples. Weather moves slower than this. */
private const val SAMPLE_SECONDS = 15f

/** Samples kept: 120 at 15 s is a 30-minute window. */
private const val WINDOW = 120

/** A reading outside this is a fault, not weather. Sea level is 1013,
 *  Death Valley bottom is about 873, Everest summit about 314. */
private val PLAUSIBLE_HPA = 300f..1100f

/**
 * Barometer and altimeter.
 *
 * A barometer chip reports *station* pressure; altitude is derived from
 * it and never measured, so both numbers are shown with the assumption
 * next to them. Three things about this screen were wrong and all three
 * made the instrument lie:
 *
 * 1. The hero was the 30-minute mean, labelled "station pressure". A
 *    mean is not a station pressure, and at `%.2f` it moved about once
 *    every six minutes while the altitude lines beneath it visibly
 *    jittered. The headline has to be the live reading; the mean is a
 *    separate, labelled thing.
 * 2. The trace was drawn against a zero-based axis, so 1004 hPa sat
 *    0.05 dp below the top of the panel and the gutter read "253 / 507
 *    / 760". A barometer's signal IS the variation, so the scale is
 *    centred on the median and sized by a percentile (BaroMath).
 * 3. A single door slam could announce "rain likely within hours",
 *    because the tendency had no outlier rejection. It does now.
 */
@Composable
fun BarometerScreen(onBack: () -> Unit) {
    val reading = rememberSensor(Sensor.TYPE_PRESSURE).values
    val context = LocalContext.current
    val view = LocalView.current
    val baroStore = remember { com.krafttools.app.data.BaroStore(context) }
    val scope = rememberCoroutineScope()

    // Real sample timestamps, so the tendency is a genuine rate rather
    // than a nominal one. `System.currentTimeMillis` could also step
    // backwards — an NTP correction would make the sampling gate
    // permanently negative and freeze the tool for the session.
    val samples = remember { ArrayDeque<Float>(WINDOW) }
    val times = remember { ArrayDeque<Long>(WINDOW) }
    var lastSampleAt by remember { mutableStateOf(0L) }
    var lastWidgetArrow by remember { mutableStateOf<String?>(null) }
    var fault by remember { mutableStateOf<String?>(null) }

    var seaLevelInput by rememberSaveable { mutableStateOf("1013.25") }

    // Collection in the sensor's cadence, not the frame's. Appending
    // during composition is the pattern that corrupted the vibration
    // FFT: the window filled at the refresh rate while the analysis
    // assumed the sample rate.
    LaunchedEffect(Unit) {
        snapshotFlow { reading }.collect { values ->
            val pressure = values?.getOrNull(0) ?: return@collect
            val now = SystemClock.elapsedRealtime()
            if (now - lastSampleAt < SAMPLE_SECONDS * 1000) return@collect
            lastSampleAt = now
            if (pressure !in PLAUSIBLE_HPA) {
                // A 0 hPa reading would otherwise print "44 330 m".
                fault = "Sensor reported %.0f hPa, which is not a pressure."
                    .format(Locale.ROOT, pressure.toDouble())
                return@collect
            }
            fault = null
            samples.addLast(pressure)
            times.addLast(now)
            while (samples.size > WINDOW) {
                samples.removeFirst()
                times.removeFirst()
            }
        }
    }

    val window = samples.toList()
    val rate = remember(window) {
        if (window.size < 2) {
            SAMPLE_SECONDS
        } else {
            ((times.last() - times.first()) / 1000f) / (window.size - 1)
        }
    }
    val tendency = remember(window, rate) { tendencyHpaPerHour(window, rate) }
    val trend = tendency?.let { classifyTendency(it) }
    val scale = remember(window) { baroScale(window) }
    val smooth = remember(window) {
        if (window.isEmpty()) 0f else window.average().toFloat()
    }
    val peakLow = remember(window) { window.minOrNull() ?: 0f }
    val peakHigh = remember(window) { window.maxOrNull() ?: 0f }

    // The widget gets ONE definition, written on change. It used to be
    // written twice per cycle — the instantaneous value, then the
    // 30-minute mean — from two independent coroutines, so the number
    // on the home screen was whichever won.
    LaunchedEffect(trend) {
        val arrow = trend?.arrow
        if (arrow != null && arrow != lastWidgetArrow && samples.isNotEmpty()) {
            lastWidgetArrow = arrow
            scope.launch {
                try {
                    baroStore.save(smooth, arrow)
                } catch (_: Exception) {
                }
            }
        }
    }

    ToolScaffold("Barometer", onBack) { padding ->
        val pressure = reading?.getOrNull(0)
        if (pressure == null) {
            // Most phones ship no barometer. Gate before touching state.
            NoSensor(
                modifier = Modifier.padding(padding),
                name = "barometer",
            )
            return@ToolScaffold
        }

        val p0 = seaLevelInput.toFloatOrNull()?.takeIf { it in 800f..1200f }
        val calAlt = p0?.let { altitudeFromPressure(pressure, it) }
        val rawAlt = altitudeFromPressure(pressure, STANDARD_SEA_LEVEL_HPA)
        // The error that matters: one hPa is about 8 m, so a 0.012 hPa
        // chip is roughly 10 cm — but a mis-set QNH is hundreds of
        // metres, which is why the reference is editable.
        val errorM = kotlin.math.abs(metresPerHpa(pressure, p0 ?: STANDARD_SEA_LEVEL_HPA))

        ToolColumn(padding) {
            Spacer(modifier = Modifier.height(4.dp))
            ReadingHeader(
                value = "%.2f".format(Locale.ROOT, pressure.toDouble()),
                unit = "hPa",
                status = when {
                    fault != null -> "sensor fault"
                    trend != null -> "${trend.label}"
                    else -> "trend after ~10 min"
                },
                live = fault == null,
            )

            fault?.let {
                ToolHint(it, warn = true)
            }

            // The trace, centred on the median and sized to the
            // variation. Its gutter is the real range it spans.
            SectionLabel(
                "30-minute trace · %.2f–%.2f hPa".format(Locale.ROOT, 
                    scale.floor.toDouble(),
                    scale.ceiling.toDouble(),
                ),
            )
            TraceGraph(
                values = window,
                max = scale.ceiling,
                min = scale.floor,
                peak = peakHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            StatRow {
                StatChip("mean", if (window.isEmpty()) "—" else "%.2f".format(Locale.ROOT, smooth.toDouble()))
                StatChip("low", "%.2f".format(Locale.ROOT, peakLow.toDouble()))
                StatChip("high", "%.2f".format(Locale.ROOT, peakHigh.toDouble()))
                StatChip(
                    "trend",
                    tendency?.let { "%+.2f".format(Locale.ROOT, it.toDouble()) } ?: "—",
                    emphasise = trend != null && trend != BaroTrend.STEADY,
                )
            }

            Spacer(modifier = Modifier.weight(0.15f))

            // Two altitudes, both derived, both labelled with what they
            // assume. The difference between them is the calibration.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AltitudeCard(
                    label = "assuming 1013.25 hPa",
                    metres = rawAlt,
                    modifier = Modifier.weight(1f),
                )
                AltitudeCard(
                    label = p0?.let { "assuming ${it.toInt()} hPa" } ?: "no reference set",
                    metres = calAlt,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = seaLevelInput,
                    onValueChange = { seaLevelInput = it },
                    label = { Text("Sea-level reference") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        Haptics.tick(view)
                        seaLevelInput = STANDARD_SEA_LEVEL_HPA.toString()
                    },
                    enabled = p0 != null && p0 != STANDARD_SEA_LEVEL_HPA,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Reset")
                }
            }

            ToolHint(
                (
                    "One hPa is about %.0f m here, so the reading is good " +
                        "to roughly %.1f m — and a mis-set reference is the " +
                        "only thing that will really ruin it. A falling " +
                        "trace can mean weather or climbing stairs; doors " +
                        "and HVAC gust the sensor, so trust the mean over " +
                        "a spike."
                    ).format(Locale.ROOT, errorM, errorM * 0.012),
            )
        }
    }
}

/** One derived altitude, with the assumption printed on it. */
@Composable
private fun AltitudeCard(
    label: String,
    metres: Float?,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (metres == null) "—" else "%.0f m".format(Locale.ROOT, metres.toDouble()),
            style = MaterialTheme.typography.headlineMedium,
            color = if (metres == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
