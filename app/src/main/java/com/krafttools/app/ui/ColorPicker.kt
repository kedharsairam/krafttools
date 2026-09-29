package com.krafttools.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerScreen(onBack: () -> Unit) {
    PermissionGate(
        permission = android.Manifest.permission.CAMERA,
        tool = "Color picker",
        reason = "Point at a surface and the center spot's color appears below. " +
            "Frames never leave the phone — sampling happens on-device.",
    
        onBack = onBack,) {
        ColorPickerBody(onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorPickerBody(onBack: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current
    var rgb by rememberSaveable(saver = intTripleSaver) { mutableStateOf(Triple(0, 0, 0)) }
    var frozen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    // Palette: frozen captures kept for the session, newest first.
    // Designers collect candidates; each row copies its HEX on tap.
    val palette = rememberSaveable(saver = intTripleListSaver) { mutableStateListOf<Triple<Int, Int, Int>>() }
    // Analyzer closure captures these; int array survives recomposition
    // without triggering it (color state alone drives redraws).
    val frameCount = rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Color picker") },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { view ->
                            val provider =
                                ProcessCameraProvider.getInstance(ctx).get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(view.surfaceProvider)
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(
                                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST,
                                )
                                .build()
                                .also { ia ->
                                    ia.setAnalyzer(
                                        ContextCompat.getMainExecutor(ctx),
                                    ) { image ->
                                        try {
                                            // 30fps analysis redraws constantly; every
                                            // 3rd frame is still ~10Hz, plenty live.
                                            frameCount.intValue++
                                            if (!frozen &&
                                                frameCount.intValue % 3 == 0
                                            ) {
                                                sampleCenter(image)?.let { rgb = it }
                                            }
                                        } catch (_: Exception) {
                                        } finally {
                                            image.close()
                                        }
                                    }
                                }
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycle,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                            // No manual release: bindToLifecycle ties the camera
                            // to the lifecycle, unbinding automatically on dispose.
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                // Center-spot reticle so users know exactly what is sampled.
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(8.dp),
                        ),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Large swatch: the reading itself, glanceable from arm's length.
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Color(
                                android.graphics.Color.rgb(
                                    rgb.first,
                                    rgb.second,
                                    rgb.third,
                                ),
                            ),
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(12.dp),
                        ),
                )
                Column {
                    Text(
                        text = "#%02X%02X%02X".format(
                            rgb.first,
                            rgb.second,
                            rgb.third,
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "R ${rgb.first}  G ${rgb.second}  B ${rgb.third}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (frozen) {
                Button(onClick = { frozen = false }) { Text("Resume") }
            } else {
                OutlinedButton(onClick = { frozen = true }) { Text("Freeze") }
            }
            OutlinedButton(
                onClick = {
                    if (palette.none { it == rgb }) {
                        palette.add(0, rgb)
                        if (palette.size > 12) palette.removeLast()
                    }
                },
            ) {
                Text("Save swatch")
            }
            // Contrast: the reason a designer opens this tool. It now
            // says PASS or FAIL, not just a number to look up.
            val (r, g, b) = rgb
            val packed = (r shl 16) or (g shl 8) or b
            val onWhite = ColorMath.contrast(packed, 0xFFFFFF)
            val onBlack = ColorMath.contrast(packed, 0x000000)
            val best = maxOf(onWhite, onBlack)
            val verdict = ColorMath.verdict(best)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = verdict.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = when (verdict) {
                        ColorMath.ContrastVerdict.FAIL -> MaterialTheme.colorScheme.error
                        ColorMath.ContrastVerdict.AA_LARGE -> warnAmber
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
                Text(
                    text = "%.1f:1 on %s".format(
                        best,
                        if (onWhite >= onBlack) "white" else "black",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Also %.1f:1 on %s. %s".format(
                    minOf(onWhite, onBlack),
                    if (onWhite >= onBlack) "black" else "white",
                    verdict.detail,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (palette.isNotEmpty()) {
                Text(
                    text = "Palette (${palette.size}) — tap a swatch to copy its HEX.",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    count = palette.size,
                    key = { palette[it].toString() + it },
                ) { i ->
                    val (pr, pg, pb) = palette[i]
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(android.graphics.Color.rgb(pr, pg, pb)))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable {
                                val hex = "#%02X%02X%02X".format(pr, pg, pb)
                                val clip = ClipData.newPlainText("hex", hex)
                                (context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as ClipboardManager).setPrimaryClip(clip)
                            },
                    )
                }
            }
            Text(
                text = "Approximate only — auto white-balance shifts hues, " +
                    "so never use this as a Pantone reference.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The house "nearly, but not quite" amber, shared with the level
 *  scale's zones so a warning looks the same everywhere. */
internal val warnAmber = androidx.compose.ui.graphics.Color(0xFFFFC53D)

/**
 * Average a 25x25 centre patch and convert to sRGB.
 *
 * Two defects fixed here, one of them total:
 *
 * The matrix was the full-range BT.601 applied to **limited-range**
 * data, which is what Android actually delivers. Measured: a black
 * surface came out #C300F3, mid grey came out #FF00FF. ColourMath now
 * does the limited-range conversion and its tests pin the exact
 * reference points.
 *
 * The chroma reads were clamped against `capacity()` rather than
 * `limit()`. `ByteBuffer.get` validates against the limit, and camera
 * buffers are page-padded so capacity routinely exceeds the data — an
 * index in between throws. It was swallowed by the caller's catch, so
 * the symptom was the colour readout silently freezing rather than a
 * crash, which is a much harder bug to report.
 */
@OptIn(ExperimentalGetImage::class)
internal fun sampleCenter(image: ImageProxy): Triple<Int, Int, Int>? {
    val yuv = image.image ?: return null
    val w = image.width
    val h = image.height
    if (w <= 0 || h <= 0) return null
    val half = 12
    val cx = w / 2
    val cy = h / 2
    if (yuv.planes.size < 3) return null
    val yPlane = yuv.planes[0]
    val uPlane = yuv.planes[1]
    val vPlane = yuv.planes[2]
    val yBuf = yPlane.buffer
    val uBuf = uPlane.buffer
    val vBuf = vPlane.buffer
    // limit(), never capacity(): get(int) bounds-checks the limit.
    val yLimit = minOf(yBuf.capacity(), yBuf.limit())
    val uLimit = minOf(uBuf.capacity(), uBuf.limit())
    val vLimit = minOf(vBuf.capacity(), vBuf.limit())

    var rSum = 0L
    var gSum = 0L
    var bSum = 0L
    var n = 0L
    for (dy in -half..half) {
        for (dx in -half..half) {
            val x = (cx + dx).coerceIn(0, w - 1)
            val y = (cy + dy).coerceIn(0, h - 1)
            val yi = y * yPlane.rowStride + x
            if (yi >= yLimit) continue
            val yVal = yBuf.get(yi).toInt() and 0xFF
            // Chroma is 2x subsampled: one U/V sample covers 2x2 luma.
            val uPos = (y / 2 * uPlane.rowStride + x / 2 * uPlane.pixelStride)
                .coerceIn(0, uLimit - 1)
            val vPos = (y / 2 * vPlane.rowStride + x / 2 * vPlane.pixelStride)
                .coerceIn(0, vLimit - 1)
            val u = uBuf.get(uPos).toInt() and 0xFF
            val v = vBuf.get(vPos).toInt() and 0xFF
            val (r, g, b) = ColorMath.yuvToRgb(yVal, u, v)
            rSum += r
            gSum += g
            bSum += b
            n++
        }
    }
    if (n == 0L) return null
    return Triple((rSum / n).toInt(), (gSum / n).toInt(), (bSum / n).toInt())
}
