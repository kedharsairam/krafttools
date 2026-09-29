package com.krafttools.app.ui

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Protractor arithmetic.
 *
 * The old hero number composed pitch and roll with a Euclidean hypot
 * *in angle space*. That is wrong: pitch and roll are not orthogonal
 * coordinates on a sphere, so hypot(pitch, roll) is only correct for a
 * single-axis tilt and badly wrong for a diagonal one.
 *
 *   45° diagonal  ->  read 42.4°  (2.6° low)
 *   90° diagonal  ->  read 63.6°  (26.4° low — a protractor lying)
 *   90° on one axis->  read 90.0°  (exact)
 *
 * The true angle between the device's +Z axis and gravity is a single
 * atan2 of the horizontal component against the vertical one, and it
 * is exact on all three.
 */

/** The angle between the device face and the horizontal plane, 0..90. */
fun tiltFromFlat(gravity: FloatArray): Float {
    val ax = gravity[0].toDouble()
    val ay = gravity[1].toDouble()
    val az = gravity[2].toDouble()
    return Math.toDegrees(atan2(hypot(ax, ay), az)).toFloat().coerceIn(0f, 90f)
}

/**
 * Which way downhill is, in the phone's own screen plane, in degrees
 * clockwise from the right edge. This is the one number a protractor
 * needle can show: a single direction that folds both tilt axes
 * together, which a plain horizontal edge could not — a pure 45° tilt
 * drew dead flat while the header read 45°.
 *
 * The argument is an ACCELEROMETER reading, which is the specific
 * force `a = −g`, not the gravity vector itself. This is the whole
 * subtlety: because the accelerometer reports the reaction to gravity
 * rather than gravity, its horizontal component always points UPHILL.
 * For a surface descending in unit direction `d`, `g·d > 0`, so
 * `a·d = −g·d < 0` — the measured horizontal vector is `−d`.
 *
 * The old code returned `atan2(ay, ax)`, which is the uphill bearing,
 * so every reading was 180° out. A slope descending to the right was
 * reported as descending to the left. Negating both components is the
 * whole fix.
 *
 *   a = (−1.70, 0, 9.66)  (right edge low)  ->   0°,  downhill is right
 *   a = (0, +1.70, 9.66)  (top edge down)   ->  90°,  downhill is down
 *   a = (+1.70, 0, 9.66)  (left edge low)   -> 180°,  downhill is left
 *   a = (0, −1.70, 9.66)  (top edge up)     -> 270°,  downhill is up
 *
 * Note the second column above: `a_y > 0` means the TOP edge is DOWN,
 * because the device's +Y axis points up the screen and the reaction
 * pushes it up when the top end drops.
 */
fun downhillAzimuth(gravity: FloatArray): Float {
    val ax = gravity[0].toDouble()
    val ay = gravity[1].toDouble()
    if (hypot(ax, ay) < 1e-6) return 0f
    val deg = Math.toDegrees(atan2(-ay, -ax)).toFloat()
    return ((deg % 360f) + 360f) % 360f
}

/**
 * Snap to a multiple of [step] degrees when within [band] of one, and
 * say whether it snapped. Returning the flag rather than only the value
 * is what stops three independently-snapped numbers from disagreeing —
 * snapping pitch, roll and the magnitude separately produced three
 * answers to one question.
 */
fun snapTo(value: Float, step: Float = 45f, band: Float = 2f): SnapResult {
    if (step <= 0f) return SnapResult(value, false)
    val q = Math.round(value / step) * step
    return if (kotlin.math.abs(value - q) <= band) {
        SnapResult(q, true)
    } else {
        SnapResult(value, false)
    }
}

data class SnapResult(val value: Float, val snapped: Boolean)

/** Common protractor graduations, in degrees. */
val PROTRACTOR_MAJOR = listOf(0f, 45f, 90f, 135f, 180f)
val PROTRACTOR_MINOR = (0 until 180 step 15).map { it.toFloat() }
