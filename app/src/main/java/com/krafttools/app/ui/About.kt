package com.krafttools.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.krafttools.app.BuildConfig

/**
 * What this app is, and what it will not do.
 *
 * Every other screen in this app is an instrument that shows a number.
 * This one is the only place the app talks about itself, so it had better
 * be worth reading — which is a low bar that most about screens fail.
 *
 * The rule it follows is the same one the rest of the app follows: one
 * idea per line, no paragraph, and nothing claimed that the code does not
 * actually do. The claims here are all checkable, which is why they are
 * phrased the way they are:
 *
 *  - "no internet permission" is true because the manifest has no
 *    `INTERNET` entry, not because of a promise.
 *  - "no background work" is true because every sensor is registered in
 *    a `DisposableEffect` and unregistered when its screen leaves.
 *  - "three values" is the honest number. An earlier draft of this file
 *    said the app stores nothing, and that was false: `BaroStore` writes
 *    the barometer's last reading, its trend and a timestamp to
 *    DataStore. Claiming otherwise to make a better sentence would have
 *    been the exact dishonesty this app exists to avoid.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    ToolScaffold("About", onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // The one scrollable screen in the app, and it earns it:
                // this is reference material, longer than any viewport,
                // and a list you cannot reach the end of is worse than
                // one that scrolls. Nothing here is an instrument, so
                // the weight rules that stop a meter from reflowing do
                // not apply.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Fourteen phone instruments, and what each " +
                    "one can honestly measure.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Group(
                "What it will not do",
                listOf(
                    "No internet permission. It has no way to send " +
                        "anything anywhere.",
                    "No accounts, no ads, no analytics, no crash " +
                        "reporting.",
                    "No background work. A sensor runs only while " +
                        "its screen is open.",
                    "No readings are recorded. A trace lives in " +
                        "memory and is gone when you leave.",
                ),
            )

            Group(
                "What it stores",
                listOf(
                    // The honest number, not the flattering one.
                    "Three values, all from the barometer: its last " +
                        "reading, its trend, and when you took it.",
                    "Nothing else is written to disk.",
                ),
            )

            Group(
                "Permissions, and why",
                listOf(
                    "Camera — torch, QR, light, colour.",
                    "Microphone — the sound meter.",
                    "Location — to correct the compass, read WiFi " +
                        "names, and measure speed.",
                    "Vibration — haptics and the tally.",
                    "Each is asked for by the tool that needs it, " +
                        "with its reason, and never at launch.",
                ),
            )

            Group(
                "Honest limits",
                listOf(
                    "Phone microphones are uncalibrated. The sound " +
                        "meter shows a level, not a decibel rating.",
                    "The angle ruler is a MEMS sensor, not a " +
                        "surveyor's.",
                    "A compass reads magnetic north until you " +
                        "correct it.",
                ),
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Text(
                text = "Version ${BuildConfig.VERSION_NAME} · " +
                    "MIT licence",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Source and releases: github.com/kedharsairam/" +
                    "krafttools",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A heading and its lines, the way a settings pane groups things. */
@Composable
private fun Group(heading: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
