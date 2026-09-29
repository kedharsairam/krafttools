package com.krafttools.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScreen(onBack: () -> Unit) {
    PermissionGate(
        permission = android.Manifest.permission.CAMERA,
        tool = "QR scanner",
        reason = "Point at a code and its text appears below. " +
            "Frames never leave the phone — decoding happens on-device, " +
            "and nothing is uploaded anywhere.",
    ) {
        QrBody(onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrBody(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var result by remember { mutableStateOf<String?>(null) }
    var paused by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR scanner") },
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
                                        decodeYuv(image)?.let { text ->
                                            if (!paused) result = text
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
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            result?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            val clip = ClipData.newPlainText("qr", text)
                            (context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager).setPrimaryClip(clip)
                        },
                    ) {
                        Text("Copy")
                    }
                    OutlinedButton(
                        onClick = {
                            paused = !paused
                            if (!paused) result = null
                        },
                    ) {
                        Text(if (paused) "Scan again" else "Freeze")
                    }
                }
            } ?: Text(
                text = "Point the camera at a QR or barcode.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** YUV frame -> decoded text, or null. PlanarYUV path: no allocation
 * beyond the luminance source, fast enough per-frame. */
@OptIn(ExperimentalGetImage::class)
private fun decodeYuv(image: androidx.camera.core.ImageProxy): String? {
    val yuv = image.image ?: return null
    val planes = yuv.planes
    val y = planes[0].buffer
    val data = ByteArray(y.remaining())
    y.get(data)
    val source = PlanarYUVLuminanceSource(
        data,
        image.width,
        image.height,
        0,
        0,
        image.width,
        image.height,
        false,
    )
    return try {
        MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: Exception) {
        // Most frames contain no code; trying is the whole job.
        null
    }
}
