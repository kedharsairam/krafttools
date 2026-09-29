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
 * needle can show: a single direction that folds pitch and roll
 * together, which the old horizontal edge could not — a pure 45°
 * *pitch* drew dead flat while the header read 45°.
 *
 *   gravity +X (right edge down) -> 0°,    needle points right
 *   gravity +Y (top edge down)   -> 90°,   needle points down
 *   gravity -X (left edge down)  -> 180°,  needle points left
 *   gravity -Y (top edge up)     -> 270°,  needle points up
 */
fun downhillAzimuth(gravity: FloatArray): Float {
    val ax = gravity[0].toDouble()
    val ay = gravity[1].toDouble()
    if (hypot(ax, ay) < 1e-6) return 0f
    val deg = Math.toDegrees(atan2(ay, ax)).toFloat()
    return ((deg % 360f) + 360f) % 360f
}

/** True when the device is standing on an edge, within [eps] of level. */
fun isVertical(gravity: FloatArray, eps: Float = 0.01f): Boolean {
    val ax = gravity[0].toDouble()
    val ay = gravity[1].toDouble()
    val az = gravity[2].toDouble()
    val magnitude = Math.hypot(ax, Math.hypot(ay, az))
    return Math.hypot(ax, ay) > 0f && kotlin.math.abs(az) <= eps * magnitude
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
