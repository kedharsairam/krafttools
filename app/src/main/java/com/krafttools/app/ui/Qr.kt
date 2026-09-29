package com.krafttools.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer

/**
 * QR and barcode scanner.
 *
 * The decode path had a bug that failed *quietly*, which is the worst
 * kind: `PlanarYUVLuminanceSource` assumes a tightly packed luminance
 * buffer, but a camera HAL pads every row to a 16-byte boundary. Row r
 * really starts at r * rowStride, ZXing read it at r * width, and the
 * image was sheared diagonally — destroying the finder patterns a QR
 * code is built from. The symptom was "the scanner just cannot decode
 * anything", with nothing to point at. LumaPlane honours the stride,
 * and the test proves both halves: the naive read fails on a real
 * padded QR, the stride-aware read recovers it.
 *
 * The rest of the work here was the ordinary kind: run the analyzer on
 * a background executor (CameraX requires it; a ZXing pass is 5-30 ms
 * and the main thread cannot afford that 30 times a second), stop
 * blocking the UI thread on the camera provider, gate on camera
 * hardware, crop the decode region to the reticle instead of scanning
 * the whole room, and give the viewfinder a scrim — which is the
 * standard scanner idiom and tells the eye where to aim.
 */
private val SCAN_FORMATS = listOf(
    BarcodeFormat.QR_CODE,
    BarcodeFormat.DATA_MATRIX,
    BarcodeFormat.AZTEC,
    BarcodeFormat.PDF_417,
    BarcodeFormat.CODE_128,
    BarcodeFormat.CODE_39,
    BarcodeFormat.CODE_93,
    BarcodeFormat.EAN_13,
    BarcodeFormat.EAN_8,
    BarcodeFormat.UPC_A,
    BarcodeFormat.UPC_E,
    BarcodeFormat.ITF,
    BarcodeFormat.CODABAR,
)

/** Human names for the symbologies this scanner accepts. */
fun symbologyName(format: BarcodeFormat?): String = when (format) {
    BarcodeFormat.QR_CODE -> "QR code"
    BarcodeFormat.DATA_MATRIX -> "Data Matrix"
    BarcodeFormat.AZTEC -> "Aztec"
    BarcodeFormat.PDF_417 -> "PDF417"
    BarcodeFormat.CODE_128 -> "Code 128"
    BarcodeFormat.CODE_39 -> "Code 39"
    BarcodeFormat.CODE_93 -> "Code 93"
    BarcodeFormat.EAN_13 -> "EAN-13"
    BarcodeFormat.EAN_8 -> "EAN-8"
    BarcodeFormat.UPC_A -> "UPC-A"
    BarcodeFormat.UPC_E -> "UPC-E"
    BarcodeFormat.ITF -> "ITF"
    BarcodeFormat.CODABAR -> "Codabar"
    else -> "code"
}

/** One decoded result: the text plus what kind of code it was. */
data class ScanResult(
    val text: String,
    val format: BarcodeFormat?,
)

@Composable
fun QrScreen(onBack: () -> Unit) {
    PermissionGate(
        permission = android.Manifest.permission.CAMERA,
        tool = "QR scanner",
        onBack = onBack,
        reason = "Point at a code and its text appears below. Frames never " +
            "leave the phone — decoding happens on-device, and nothing is " +
            "uploaded anywhere.",
    ) {
        QrBody(onBack)
    }
}

@Composable
private fun QrBody(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val view = androidx.compose.ui.platform.LocalView.current
    // Saveable, not just remembered: rotating while frozen used to
    // destroy the decoded text and keep the frozen flag, leaving a
    // "Scan again" button with nothing behind it.
    var text by rememberSaveable { mutableStateOf<String?>(null) }
    var format by rememberSaveable { mutableStateOf<String?>(null) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var cameraFault by rememberSaveable { mutableStateOf<String?>(null) }
    var torchUsable by rememberSaveable { mutableStateOf(false) }
    var torchOn by rememberSaveable { mutableStateOf(false) }

    val executor = rememberAnalysisExecutor()
    val hasCamera = remember { hasAnyCamera(context) }

    // One reader, reused. Building a MultiFormatReader per frame
    // allocated ~9 MB/s and is the slowest possible configuration.
    val reader = remember {
        MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to SCAN_FORMATS,
                    // TRY_HARDER costs several times the time per frame
                    // and rarely rescues a code the reticle framed.
                    DecodeHintType.TRY_HARDER to false,
                ),
            )
        }
    }

    if (!hasCamera) {
        ToolScaffold("QR scanner", onBack) { padding ->
            NoSensor(
                modifier = Modifier.padding(padding),
                name = "camera",
            )
        }
        return
    }

    ToolScaffold("QR scanner", onBack) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                CameraPreview(
                    lifecycleOwner = lifecycle,
                    analysisExecutor = executor,
                    onFrame = { image: ImageProxy ->
                        // Runs on the executor, never the main thread.
                        val found = try {
                            decodeFrame(image, reader)
                        } catch (_: Exception) {
                            null
                        } finally {
                            image.close()
                        }
                        if (found != null) {
                            // One hop to the main thread, and only when
                            // there is something new to show.
                            postToMain {
                                if (!paused) {
                                    text = found.text
                                    format = symbologyName(found.format)
                                }
                            }
                        }
                    },
                    onError = { cameraFault = it },
                    onTorchState = { torchUsable = it },
                    modifier = Modifier.fillMaxSize(),
                )

                // The scrim: everything outside the reticle is dimmed,
                // which is both the standard scanner idiom and the
                // reason the eye knows where to point.
                ScannerReticle(paused = paused)

                // Torch: for a scanner in a dim room, the most-wanted
                // control there is, and it used to be missing.
                if (torchUsable) {
                    OutlinedButton(
                        onClick = {
                            Haptics.tick(view)
                            torchOn = !torchOn
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .heightIn(min = 48.dp),
                    ) {
                        Text(if (torchOn) "Torch on" else "Torch")
                    }
                }

                cameraFault?.let {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(16.dp),
                            )
                            .padding(20.dp),
                    ) {
                        Text(it, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // The dock. The freeze control lives OUTSIDE the result
            // block: it used to be unreachable until the first decode,
            // so a mis-read could not be stopped from overwriting.
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (text != null) {
                    Text(
                        text = format ?: "code",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = text!!,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                Haptics.confirm(view)
                                copyToClipboard(context, text!!)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        ) {
                            Text("Copy")
                        }
                        OutlinedButton(
                            onClick = {
                                Haptics.tick(view)
                                paused = !paused
                                if (!paused) {
                                    text = null
                                    format = null
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        ) {
                            Text(if (paused) "Scan again" else "Freeze")
                        }
                    }
                } else {
                    ToolHint(
                        if (paused) {
                            "Frozen. Tap Scan again when you are ready."
                        } else {
                            "Line the code up inside the brackets. Decoding " +
                                "happens on this device; nothing is sent anywhere."
                        },
                    )
                }
            }
        }
    }
}

private fun postToMain(block: () -> Unit) {
    android.os.Handler(android.os.Looper.getMainLooper()).post(block)
}

private fun copyToClipboard(context: Context, text: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("KraftTools", text))
    } catch (_: Exception) {
    }
}

/**
 * Decode one frame, cropping to the centre square the reticle frames.
 * Scanning the whole frame meant decoding dark room and screen
 * reflections as well as the code — slower, and more false positives.
 */
internal fun decodeFrame(
    image: ImageProxy,
    reader: MultiFormatReader,
): ScanResult? {
    val luma = LumaPlane.pack(image) ?: return null
    val w = image.width
    val h = image.height
    if (w <= 0 || h <= 0) return null
    val side = minOf(w, h) * 3 / 5
    val left = (w - side) / 2
    val top = (h - side) / 2
    return try {
        val source = PlanarYUVLuminanceSource(
            luma, w, h, left, top, side, side, false,
        )
        val result: Result = reader.decode(
            BinaryBitmap(HybridBinarizer(source)),
        )
        ScanResult(result.text, result.barcodeFormat)
    } catch (_: Exception) {
        // Most frames contain no code. That is not an error.
        null
    } finally {
        reader.reset()
    }
}

/** The viewfinder: a scrim with a clear window and corner brackets. */
@Composable
private fun ScannerReticle(paused: Boolean) {
    val accent = if (paused) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.primary
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val inset = size.minDimension * 0.2f
        val left = inset
        val top = inset
        val right = size.width - inset
        val bottom = size.height - inset
        val scrim = Color.Black.copy(alpha = 0.55f)
        // Four scrim panels around the window.
        drawRect(scrim, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width, top))
        drawRect(scrim, topLeft = Offset(0f, bottom), size = androidx.compose.ui.geometry.Size(size.width, size.height - bottom))
        drawRect(scrim, topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(left, bottom - top))
        drawRect(scrim, topLeft = Offset(right, top), size = androidx.compose.ui.geometry.Size(size.width - right, bottom - top))

        // Brackets, sized in dp so they are the same physical length
        // at every density. Raw pixel literals made these 24dp on a 3x
        // screen and 72dp on a 1x one.
        val len = 28.dp.toPx()
        val w = 3.dp.toPx()
        val corners = listOf(
            Offset(left, top) to listOf(1f, 1f),
            Offset(right, top) to listOf(-1f, 1f),
            Offset(left, bottom) to listOf(1f, -1f),
            Offset(right, bottom) to listOf(-1f, -1f),
        )
        for ((corner, dir) in corners) {
            drawLine(accent, corner, Offset(corner.x + len * dir[0], corner.y), strokeWidth = w)
            drawLine(accent, corner, Offset(corner.x, corner.y + len * dir[1]), strokeWidth = w)
        }
    }
}
