package com.krafttools.app.ui

import android.hardware.Sensor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Light meter: raw ambient-light sensor, no calibration or permissions needed.
// Min/max are session-only so a covered sensor doesn't pin a stale extreme forever.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuxScreen(onBack: () -> Unit) {
    val light by rememberSensor(Sensor.TYPE_LIGHT)
    var minLux by remember { mutableStateOf<Float?>(null) }
    var maxLux by remember { mutableStateOf<Float?>(null) }
    // Hold freezes the headline for reading in awkward positions
    // (behind furniture); min/max keep tracking live underneath.
    var held by remember { mutableStateOf(false) }
    var heldLux by remember { mutableStateOf<Float?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Light meter") },
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
        val values = light
        if (values == null) {
            NoSensor(modifier = Modifier.padding(padding), name = "light sensor")
            return@Scaffold
        }
        val lux = values[0]
        if (held && heldLux == null) heldLux = lux
        if (!held) heldLux = null
        val shown = heldLux ?: lux

        // Hold extremes only on real readings; reset restores both to current.
        LaunchedEffect(lux) {
            minLux = minOf(minLux ?: lux, lux)
            maxLux = maxOf(maxLux ?: lux, lux)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "%.1f lux".format(shown) + if (held) " (held)" else "",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = luxLabel(shown),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Null only before first LaunchedEffect pass; fall back to live lux.
                MinMaxStat("Min", minLux ?: lux)
                MinMaxStat("Max", maxLux ?: lux)
            }
            Button(onClick = { minLux = lux; maxLux = lux }) {
                Text("Reset min/max")
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Hold reading",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.Switch(
                    checked = held,
                    onCheckedChange = { held = it },
                )
            }
            Text(
                text = "Relative, not lab: phone sensors saturate (~5–30k lux) " +
                    "and cover glass skews readings. Good for comparing rooms, not certifying them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MinMaxStat(label: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "%.1f".format(value),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// Buckets match everyday intuition, not CIE bins — this is a rough field gauge.
private fun luxLabel(lux: Float): String = when {
    lux < 10 -> "Dark"
    lux < 100 -> "Dim"
    lux < 1_000 -> "Indoor"
    lux < 10_000 -> "Bright daylight"
    else -> "Direct sun"
}
