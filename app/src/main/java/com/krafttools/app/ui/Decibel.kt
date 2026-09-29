package com.krafttools.app.ui

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.log10
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

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

/** dBFS of a PCM block, plus 90 dB reference: phone-mic convention
 * mapping full-scale to roughly real SPL. Uncalibrated by nature —
 * the offset slider absorbs each mic's sensitivity. */
private const val DB_REFERENCE = 90f
private const val SAMPLE_RATE = 16000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DecibelBody(onBack: () -> Unit) {
    var instantDb by rememberSaveable { mutableFloatStateOf(0f) }
    var leqDb by rememberSaveable { mutableFloatStateOf(0f) }
    var minDb by rememberSaveable { mutableStateOf<Float?>(null) }
    var maxDb by rememberSaveable { mutableStateOf<Float?>(null) }
    var offset by rememberSaveable { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    val spectrum = remember { mutableStateListOf<Float>() }

    // Single owner coroutine: opens AudioRecord, loops PCM blocks,
    // releases on dispose. Mic is never held past this screen.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuf <= 0) {
                error = "Microphone unavailable on this phone."
                return@withContext
            }
            val rec = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 4,
                )
            } catch (e: Exception) {
                error = "Microphone unavailable (${e.javaClass.simpleName})."
                return@withContext
            }
            try {
                rec.startRecording()
            } catch (e: Exception) {
                error = "Microphone unavailable (${e.javaClass.simpleName})."
                try { rec.release() } catch (_: Exception) { }
                return@withContext
            }
            try {
                // 100 ms blocks at 16 kHz: fast meter, cheap math.
                val block = ShortArray(SAMPLE_RATE / 10)
                val ring = FloatArray(2048)
                var ringPos = 0
                // 1 s LAeq window = energy mean of the last 10 blocks.
                val energyWin = FloatArray(10)
                var energyPos = 0
                var energyFill = 0
                while (isActive) {
                    val read = rec.read(block, 0, block.size)
                    if (read <= 0) continue
                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val s = block[i] / 32768.0
                        sumSq += s * s
                        ring[ringPos] = s.toFloat()
                        ringPos = (ringPos + 1) % ring.size
                    }
                    val rms = sqrt(sumSq / read)
                    val blockDb = if (rms <= 0.0) {
                        0f
                    } else {
                        (20 * log10(rms) + DB_REFERENCE).toFloat()
                    }
                    instantDb = (blockDb + offset).coerceIn(0f, 120f)
                    energyWin[energyPos] = (rms * rms).toFloat()
                    energyPos = (energyPos + 1) % energyWin.size
                    if (energyFill < energyWin.size) energyFill++
                    if (energyFill == energyWin.size) {
                        var e = 0f
                        for (v in energyWin) e += v
                        e /= energyWin.size
                        leqDb = if (e <= 0f) {
                            0f
                        } else {
                            ((20 * log10(sqrt(e.toDouble()))) + DB_REFERENCE + offset)
                                .toFloat().coerceIn(0f, 120f)
                        }
                    }
                    minDb = minOf(minDb ?: instantDb, instantDb)
                    maxDb = maxOf(maxDb ?: instantDb, instantDb)
                    // Spectrum every ~0.5 s from the raw ring (not the
                    // smoothed blocks): 8 log bands, 0..1 normalized.
                    if (energyPos % 5 == 0) {
                        val bands = spectrumBands(ring, ringPos)
                        spectrum.clear()
                        spectrum.addAll(bands)
                    }
                }
            } finally {
                try { rec.stop() } catch (_: Exception) { }
                try { rec.release() } catch (_: Exception) { }
            }
        }
    }

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
                text = "%.0f dB".format(instantDb),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            LinearProgressIndicator(
                progress = { (instantDb / 120f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            SpectrumBars(
                values = spectrum.toList(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text("min %.0f".format(minDb ?: 0f), style = MaterialTheme.typography.titleMedium)
                Text("LAeq %.0f".format(leqDb), style = MaterialTheme.typography.titleMedium)
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
            Button(onClick = { minDb = null; maxDb = null }) {
                Text("Reset stats")
            }
        }
    }
}

/** 8 log-spaced bands (63 Hz .. 8 kHz) from a DFT over the ring.
 * Magnitudes normalized 0..1 against the strongest band: shape of
 * the sound, not absolute level (the dB headline owns that). */
private fun spectrumBands(ring: FloatArray, ringPos: Int): List<Float> {
    val n = 1024
    val centers = floatArrayOf(63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f)
    val mags = FloatArray(centers.size)
    for (b in centers.indices) {
        // Direct bin nearest the center: bin = f * n / sampleRate.
        val k = ((centers[b] * n / SAMPLE_RATE).toInt()).coerceIn(1, n / 2 - 1)
        var re = 0.0
        var im = 0.0
        for (i in 0 until n) {
            val s = ring[(ringPos + i) % ring.size].toDouble()
            val angle = 2.0 * Math.PI * k * i / n
            re += s * Math.cos(angle)
            im -= s * Math.sin(angle)
        }
        mags[b] = sqrt(re * re + im * im).toFloat()
    }
    val peak = mags.maxOrNull() ?: 0f
    if (peak <= 0f) return List(centers.size) { 0f }
    // Log-ish compression so quiet bands stay visible.
    return mags.map { (it / peak).coerceIn(0f, 1f) }
}

@Composable
private fun SpectrumBars(values: List<Float>, modifier: Modifier = Modifier) {
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    androidx.compose.foundation.Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val gap = 8f
        val w = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val h = (size.height * v).coerceAtLeast(4f)
            drawRect(
                color = track,
                topLeft = Offset(i * (w + gap), 0f),
                size = Size(w, size.height),
            )
            drawRect(
                color = bar,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
            )
        }
    }
}
