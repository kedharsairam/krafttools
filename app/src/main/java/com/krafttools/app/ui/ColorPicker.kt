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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    ) {
        ColorPickerBody(onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorPickerBody(onBack: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current
    var rgb by remember { mutableStateOf(Triple(0, 0, 0)) }
    var frozen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Palette: frozen captures kept for the session, newest first.
    // Designers collect candidates; each row copies its HEX on tap.
    val palette = remember { mutableStateListOf<Triple<Int, Int, Int>>() }
    // Analyzer closure captures these; int array survives recomposition
    // without triggering it (color state alone drives redraws).
    val frameCount = remember { mutableIntStateOf(0) }

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
            // Contrast ratio (WCAG): text-legibility check for designers.
            // Computed live against black and white.
            val (r, g, b) = rgb
            val lum = { c: Int ->
                val v = c / 255.0
                if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
            }
            val l = 0.2126 * lum(r) + 0.7152 * lum(g) + 0.0722 * lum(b)
            val onBlack = (l + 0.05) / 0.05
            val onWhite = 1.05 / (l + 0.05)
            Text(
                text = "Contrast %.1f on black · %.1f on white".format(onBlack, onWhite),
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

/** Average a ~24x24 center patch, YUV_420_888 -> RGB via BT.601.
 *  Center patch (not full frame): immune to edges/vignetting, and
 *  matches the on-screen reticle so sampling feels predictable. */
@OptIn(ExperimentalGetImage::class)
private fun sampleCenter(image: ImageProxy): Triple<Int, Int, Int>? {
    val yuv = image.image ?: return null
    val w = image.width
    val h = image.height
    val half = 12
    val cx = w / 2
    val cy = h / 2
    val yPlane = yuv.planes[0]
    val uPlane = yuv.planes[1]
    val vPlane = yuv.planes[2]
    val yBuf = yPlane.buffer
    val uBuf = uPlane.buffer
    val vBuf = vPlane.buffer
    var rSum = 0L
    var gSum = 0L
    var bSum = 0L
    var n = 0L
    for (dy in -half..half) {
        for (dx in -half..half) {
            val x = (cx + dx).coerceIn(0, w - 1)
            val y = (cy + dy).coerceIn(0, h - 1)
            val yVal = (yBuf.get(y * yPlane.rowStride + x).toInt() and 0xFF)
            // Chroma is 2x subsampled: one U/V sample covers a 2x2 luma block.
            val ux = (x / 2 * uPlane.pixelStride)
                .coerceIn(0, uBuf.capacity() - 1)
            val uy = (y / 2 * uPlane.rowStride)
            val vx = (x / 2 * vPlane.pixelStride)
                .coerceIn(0, vBuf.capacity() - 1)
            val vy = (y / 2 * vPlane.rowStride)
            val uPos = (uy + ux).coerceIn(0, uBuf.capacity() - 1)
            val vPos = (vy + vx).coerceIn(0, vBuf.capacity() - 1)
            val u = (uBuf.get(uPos).toInt() and 0xFF) - 128
            val v = (vBuf.get(vPos).toInt() and 0xFF) - 128
            // Float BT.601 kept: integer fixed-point saves nothing here at
            // ~625 px per analyzed frame, and float reads as the spec.
            val r = (yVal + 1.402 * v).roundToInt().coerceIn(0, 255)
            val g = (yVal - 0.344136 * u - 0.714136 * v)
                .roundToInt().coerceIn(0, 255)
            val b = (yVal + 1.772 * u).roundToInt().coerceIn(0, 255)
            rSum += r
            gSum += g
            bSum += b
            n++
        }
    }
    if (n == 0L) return null
    return Triple((rSum / n).toInt(), (gSum / n).toInt(), (bSum / n).toInt())
}
