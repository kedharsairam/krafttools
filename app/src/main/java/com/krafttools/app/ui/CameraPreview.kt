package com.krafttools.app.ui

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The camera plumbing both camera tools share, in one place.
 *
 * Every one of these was a bug in one tool or the other before it was
 * a helper, and three of them could crash the app:
 *
 * - `ProcessCameraProvider.getInstance(ctx).get()` blocks the UI thread
 *   for 50-300 ms (over a second cold) and throws
 *   `ExecutionException` if the camera service is down. Calling that
 *   inside a composition `factory` froze the frame and could crash.
 * - There was no camera-hardware gate. `bindToLifecycle` with
 *   `DEFAULT_BACK_CAMERA` throws `IllegalArgumentException` where there
 *   is no back camera — Chromebooks, some tablets, emulators.
 * - The analyzer ran on `getMainExecutor`. CameraX's own docs require a
 *   background executor, and a ZXing pass over 640x480 is 5-30 ms, so
 *   this guaranteed dropped frames while fighting the preview surface.
 * - `unbindAll()` is global: it unbinds other screens' cameras too.
 *
 * A secondary lesson: when the back camera is absent the right move is
 * to *use the front one*, not to show a dead screen. The gate is for
 * devices with no camera at all.
 */
private const val TAG = "KraftCamera"

/** True when this device has any camera at all. */
fun hasAnyCamera(context: Context): Boolean =
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

/** A single-thread executor for one camera screen, shut down on
 *  dispose. One per screen, never a leaked pool. */
@Composable
fun rememberAnalysisExecutor(): ExecutorService =
    remember { Executors.newSingleThreadExecutor() }

/**
 * A camera preview bound to this composable's lifecycle, with a
 * background analyzer.
 *
 * @param onFrame receives each analysed frame on [analysisExecutor]; the
 *   caller owns closing it.
 * @param onError a human-readable reason when the camera cannot start,
 *   so a tool can explain itself rather than showing a black rectangle.
 * @param torch when true, exposes a torch toggle through [onTorchState].
 */
@Composable
fun CameraPreview(
    lifecycleOwner: LifecycleOwner,
    analysisExecutor: ExecutorService,
    onFrame: (ImageProxy) -> Unit,
    modifier: Modifier = Modifier,
    onError: (String) -> Unit = {},
    onTorchState: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var torchAvailable by remember { mutableStateOf(false) }

    // Report capability and state separately: one callback carrying
    // "has a torch" and "torch is on" is indistinguishable from "no
    // flash" on the first pass, which is why the button never appeared.
    LaunchedEffect(camera) {
        val c = camera ?: return@LaunchedEffect
        val hasFlash = try {
            c.cameraInfo.hasFlashUnit()
        } catch (_: Exception) {
            false
        }
        torchAvailable = hasFlash
        onTorchState?.invoke(hasFlash)
    }

    LaunchedEffect(torchOn, camera) {
        val c = camera ?: return@LaunchedEffect
        if (!torchAvailable) return@LaunchedEffect
        try {
            c.cameraControl.enableTorch(torchOn)
        } catch (_: Exception) {
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            try {
                provider?.unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val view = PreviewView(ctx)
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                try {
                    val p = future.get()
                    provider = p
                    bindCamera(
                        provider = p,
                        view = view,
                        lifecycleOwner = lifecycleOwner,
                        executor = analysisExecutor,
                        onFrame = onFrame,
                        onCamera = { camera = it },
                        onError = onError,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "camera provider failed", e)
                    onError("The camera service did not respond.")
                }
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
    )
}

private fun bindCamera(
    provider: ProcessCameraProvider,
    view: PreviewView,
    lifecycleOwner: LifecycleOwner,
    executor: ExecutorService,
    onFrame: (ImageProxy) -> Unit,
    onCamera: (Camera?) -> Unit,
    onError: (String) -> Unit,
) {
    try {
        // Prefer the back camera, but a front camera is a camera: on a
        // device without one, refusing to scan would be absurd.
        val selector = when {
            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                CameraSelector.DEFAULT_BACK_CAMERA
            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                CameraSelector.DEFAULT_FRONT_CAMERA
            else -> {
                onError("This device has no camera.")
                onCamera(null)
                return
            }
        }
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(view.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(executor) { image -> onFrame(image) } }
        provider.unbindAll()
        onCamera(provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis))
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "no camera to bind", e)
        onError("This device has no usable camera.")
        onCamera(null)
    } catch (e: Exception) {
        Log.w(TAG, "bind failed", e)
        onError("The camera could not be started.")
        onCamera(null)
    }
}
