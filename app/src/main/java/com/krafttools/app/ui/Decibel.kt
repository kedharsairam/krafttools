package com.krafttools.app.ui

import android.media.MediaRecorder
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.log10
import kotlinx.coroutines.delay

@Composable
fun DecibelScreen(onBack: () -> Unit) {
    // Gate first so the recorder never touches the mic before consent.
    PermissionGate(
        permission = android.Manifest.permission.RECORD_AUDIO,
        tool = "Sound meter",
        reason = "The meter listens to the microphone for a loudness " +
            "reading only. Nothing is recorded or sent anywhere — " +
            "audio is measured live and discarded.",
    ) {
        DecibelBody(onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DecibelBody(onBack: () -> Unit) {
    val context = LocalContext.current
    // Holder so the poll loop (LaunchedEffect) can read the recorder
    // owned by the lifecycle effect (DisposableEffect) without recreating it.
    val holder = remember { mutableStateOf<MediaRecorder?>(null) }
    var db by remember { mutableFloatStateOf(0f) }
    var minDb by remember { mutableStateOf<Float?>(null) }
    var maxDb by remember { mutableStateOf<Float?>(null) }
    var sum by remember { mutableFloatStateOf(0f) }
    var count by remember { mutableIntStateOf(0) }
    var offset by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }

    // Recorder lives exactly as long as this screen: start on enter,
    // stop + release on leave so the mic is never held in the background.
    DisposableEffect(Unit) {
        // Temp file sink: AMR encoder needs an output path even though
        // we only read amplitudes and delete nothing (cacheDir is transient).
        @Suppress("DEPRECATION") // No-arg ctor works on every API level we support.
        val rec = MediaRecorder()
        try {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            rec.setOutputFile(File(context.cacheDir, "db-meter.tmp").absolutePath)
            rec.prepare()
            rec.start()
            holder.value = rec
        } catch (e: Exception) {
            // Mic busy or revoked mid-start: show why instead of a dead 0 dB.
            error = "Microphone unavailable (${e.javaClass.simpleName})."
            try { rec.release() } catch (_: Exception) { }
        }
        onDispose {
            val r = holder.value
            holder.value = null
            if (r != null) {
                try { r.stop() } catch (_: Exception) { /* already stopped */ }
                try { r.release() } catch (_: Exception) { }
            }
        }
    }

    // Poll amplitude at ~5 Hz: fast enough to feel live, slow enough
    // to avoid churning recomposition on every audio frame.
    LaunchedEffect(Unit) {
        while (true) {
            delay(200)
            val rec = holder.value ?: continue
            val amp = try { rec.maxAmplitude } catch (_: Exception) { 0 }
            // maxAmplitude is 0..32767 with no dB scale, so map it:
            // 1 -> 0 dB floor, 32767 -> ~90 dB ceiling. Silence stays 0.
            val raw = if (amp <= 0) 0f else (20 * log10(amp.toDouble())).toFloat()
            db = (raw + offset).coerceIn(0f, 120f)
            minDb = minOf(minDb ?: db, db)
            maxDb = maxOf(maxDb ?: db, db)
            sum += db
            count += 1
        }
    }
    val avg = if (count > 0) sum / count else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sound meter") },
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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (error != null) {
                Text(error!!, style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            Text(
                text = "%.0f dB".format(db),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            LinearProgressIndicator(
                progress = { (db / 120f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text("min %.0f".format(minDb ?: 0f), style = MaterialTheme.typography.titleMedium)
                Text("avg %.0f".format(avg), style = MaterialTheme.typography.titleMedium)
                Text("max %.0f".format(maxDb ?: 0f), style = MaterialTheme.typography.titleMedium)
            }
            // Phone mics are not calibrated: same room reads differently
            // per device, so this is for comparing (before/after), not for law.
            Text(
                text = "Relative reading only — phone mics are uncalibrated. " +
                    "Use the offset to match a known reference meter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Calibration %+.0f dB".format(offset),
                style = MaterialTheme.typography.titleSmall,
            )
            Slider(
                value = offset,
                onValueChange = { offset = it },
                valueRange = -20f..20f,
                steps = 39,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { minDb = null; maxDb = null; sum = 0f; count = 0 }) {
                Text("Reset stats")
            }
        }
    }
}
