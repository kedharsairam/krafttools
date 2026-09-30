package com.krafttools.app.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * The house haptic vocabulary — one mapping, used everywhere.
 * Follows the platform doctrine: toggles tick, threshold snaps
 * confirm lightly, saves confirm fully, errors reject. Overuse kills
 * meaning, so each call site names its metaphor in a comment.
 *
 * Two channels: view feedback (no permission, honors system toggle)
 * for UI moments, and direct vibration (VIBRATE perm, declared) for
 * instrument events the view system has no constant for.
 */
object Haptics {
    /** Toggle/switch flip, detent tick, snap-to-grid. */
    fun tick(view: View?) {
        view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Save / calibrate / lock acquired. */
    fun confirm(view: View?) {
        view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    /** Rejected / error / limit hit. */
    fun reject(view: View?) {
        view?.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }

    /** Threshold crossing with mass behind it (alert trip, level lock). */
    fun thud(context: Context) {
        buzz(context, 60L)
    }

    /** Short instrument tick independent of any view. */
    fun tap(context: Context) {
        buzz(context, 25L)
    }

    private fun buzz(context: Context, ms: Long) {
        try {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(
                    VibrationEffect.createOneShot(
                        ms,
                        VibrationEffect.DEFAULT_AMPLITUDE,
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(ms)
            }
        } catch (_: Exception) {
        }
    }
}
