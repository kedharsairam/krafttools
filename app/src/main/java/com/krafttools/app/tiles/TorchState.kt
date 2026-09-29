package com.krafttools.app.tiles

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

/**
 * Process-wide LED truth, fed by the system's own torch callback.
 *
 * Android has no "is the torch on?" query, and the LED survives the
 * death of whatever turned it on — so the app screen and the QS tile
 * would disagree forever without this. One callback, registered once
 * from MainActivity (app scope: never unregistered, nothing leaks
 * past the process). Every writer funnels through [setTorch].
 */
object TorchState {
    @Volatile
    var lit: Boolean = false
        private set

    private var callback: CameraManager.TorchCallback? = null

    fun observe(context: Context) {
        if (callback != null) return
        try {
            val manager =
                context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            callback = object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    lit = enabled
                }
            }.also { manager.registerTorchCallback(it, Handler(Looper.getMainLooper())) }
        } catch (_: Exception) {
            callback = null
        }
    }

    fun flashId(context: Context): String? {
        val manager =
            context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Returns the state actually set (false when no LED / on error). */
    fun setTorch(context: Context, state: Boolean): Boolean {
        val id = flashId(context) ?: return false.also { lit = false }
        return try {
            (context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager)
                .setTorchMode(id, state)
            lit = state
            state
        } catch (_: Exception) {
            lit = false
            false
        }
    }
}
