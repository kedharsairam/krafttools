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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalView
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
import java.util.Locale

@Composable
fun DecibelScreen(onBack: () -> Unit) {
    // Gate first so the recorder never touches the mic before consent.
    PermissionGate(
        permission = android.Manifest.permission.RECORD_AUDIO,
        tool = "Sound meter",
        reason = "The meter listens to the microphone for a loudness " +
            "reading only. Nothing is recorded or sent anywhere — " +
            "audio is measured live and discarded.",
    
        onBack = onBack,) {
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
    val view = LocalView.current

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
                // 1 s LAeq window: written ONLY by the spectrum pass below
                // (A-weighted spectral energy). The time-domain RMS above
                // feeds the instant headline, never the average — mixing
                // weighted and unweighted energies would corrupt both.
                val energyWin = FloatArray(2)
                var energyPos = 0
                var energyFill = 0
                var blockCount = 0L
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
                    if (energyFill < energyWin.size) energyFill++
                    // `>=`, not `==`. energyFill is incremented in TWO
                    // places in this block, so it overshoots the window size on
                    // the first pass and `==` is never true again: LAeq was
                    // frozen at 0 dB for the whole session, and the header
                    // read "LAeq 0 dB" permanently.
                    if (energyFill >= energyWin.size) {
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
                    // smoothed blocks). Its A-weighted energy feeds the
                    // LAeq window, so bars and number share one domain.
                    blockCount++
                    if (blockCount % 5 == 0L) {
                        val spec = spectrumBands(ring, ringPos)
                        spectrum.clear()
                        spectrum.addAll(spec.bands)
                        // Normalize to mean-square pressure: two-sided spectrum
                        // (×2), DFT length squared, and the Hann window's
                        // power gain (0.375) — without all three the LAeq
                        // reads tens of dB off. Lands within a few dB of
                        // the instant headline in steady noise, as it must.
                        val n = 1024.0
                        energyWin[energyPos] =
                            (2.0 * spec.energy / (n * n * 0.375)).toFloat()
                        energyPos = (energyPos + 1) % energyWin.size
                        if (energyFill < energyWin.size) energyFill++
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (error != null) {
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }
            ReadingHeader(
                value = "%.0f".format(Locale.ROOT, instantDb),
                unit = "dB",
                status = "LAeq %.0f dB · 1 s".format(Locale.ROOT, leqDb),
                live = instantDb > 0f,
            )

            // The banded scale is the answer; the big numeral above it
            // is just how the answer is spoken. A progress bar said
            // "some".
            LevelScale(
                db = instantDb,
                peakDb = maxDb ?: 0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatChip("min", minDb?.let { "%.0f".format(Locale.ROOT, it) } ?: "—")
                StatChip("peak", maxDb?.let { "%.0f".format(Locale.ROOT, it) } ?: "—")
                StatChip("laeq", "%.0f".format(Locale.ROOT, leqDb))
            }

            Spacer(modifier = Modifier.weight(0.2f))

            // Labelled frequency plot, not a row of anonymous bars.
            SectionLabel("Spectrum")
            SpectrumPlot(
                values = spectrum.toList(),
                centers = BAND_CENTERS.toList(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            Spacer(modifier = Modifier.weight(0.2f))

            // Phone mics are not calibrated: the same room reads
            // differently on every device, so this is for comparing
            // (before/after), never for compliance.
            Text(
                text = "Relative reading only — phone microphones are " +
                    "uncalibrated. Match a known reference meter with the " +
                    "offset below to compare like for like.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Calibration",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "%+.0f dB".format(Locale.ROOT, offset),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (offset == 0f) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Slider(
                value = offset,
                onValueChange = { offset = it },
                valueRange = -20f..20f,
                steps = 39,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        Haptics.tick(view)
                        minDb = null
                        maxDb = null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text("Reset stats")
                }
                // Equal weight, equal weight: these are the same rank of
                // action, so they get the same width.
                // Snap the offset back to zero: a meter with a forgotten
                // calibration is worse than an uncalibrated one.
                OutlinedButton(
                    onClick = {
                        Haptics.tick(view)
                        offset = 0f
                    },
                    enabled = offset != 0f,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text("Zero")
                }
            }
        }
    }
}


/** The eight display bands, shared by the analysis and its labels. */
private val BAND_CENTERS = floatArrayOf(63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f)

/** 8 log-spaced bands (63 Hz .. 8 kHz) from an FFT over the ring.
 * Magnitudes normalized 0..1 against the strongest band: shape of
 * the sound, not absolute level (the dB headline owns that). */
private data class Spectrum(
    val bands: List<Float>,
    /** Total A-weighted linear energy (for LAeq, not display). */
    val energy: Double,
)

private fun spectrumBands(ring: FloatArray, ringPos: Int): Spectrum {
    val n = 1024
    val centers = BAND_CENTERS
    // Full-spectrum A-weighted energy for LAeq: EVERY bin 1..511, not
    // just the 8 display bands. Summing 8 narrow bins would under-read
    // by an order of magnitude (all inter-bin energy goes missing).
    // Window once, then transform: the Hann window is applied in the
    // time domain, so the FFT sees a clean signal. The naive DFT this
    // replaced cost 523k cos/sin pairs per analysis, on the audio
    // thread, twice a second.
    val windowed = FloatArray(n)
    for (i in 0 until n) {
        windowed[i] = (ring[(ringPos + i) % ring.size] * hann(i, n)).toFloat()
    }
    val magsAll = Fft.magnitudes(windowed)

    // LAeq needs EVERY bin, not the eight display bands: summing eight
    // narrow bins drops all the inter-bin energy and under-reads by an
    // order of magnitude.
    var energy = 0.0
    for (k in 1..n / 2) {
        val freq = k * SAMPLE_RATE.toFloat() / n
        if (freq in 20f..16000f) {
            // aWeightPower, not aWeightLinear: the term being weighted is a
            // square, and decibels are a power ratio.
            energy += magsAll[k] * magsAll[k] * aWeightPower(freq)
        }
    }
    // Display bands read straight off the same transform.
    val mags = FloatArray(centers.size)
    for (b in centers.indices) {
        val k = ((centers[b] * n / SAMPLE_RATE).toInt()).coerceIn(1, n / 2)
        mags[b] = magsAll[k].toFloat()
    }
    val peak = mags.maxOrNull() ?: 0f
    if (peak <= 0f) return Spectrum(List(centers.size) { 0f }, 0.0)
    // Log-ish compression so quiet bands stay visible.
    return Spectrum(mags.map { (it / peak).coerceIn(0f, 1f) }, energy)
}
