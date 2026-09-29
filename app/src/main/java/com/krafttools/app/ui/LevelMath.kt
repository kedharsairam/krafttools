package com.krafttools.app.ui

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Spirit-level arithmetic. A level is a measurement with a tolerance,
 * so the tolerance itself is the spec: what counts as level, how the
 * bubble is scaled, and how the two directions are named. All of it
 * here, where it can be tested, rather than inline in a composable.
 */

/** Inside this, the vial reads level. A machinist's spirit level is
 *  about 0.5 mm/m, which at arm's length is well under a degree. */
const val LEVEL_TOLERANCE_DEG = 1f

/** Inside this, the reading is close enough to trust without looking
 *  again — the "nearly" band the tone and color use. */
const val LEVEL_NEAR_DEG = 3f

/**
 * The signed tilt from level, in degrees, as (pitch, roll) relative to
 * the calibrated zero. Magnitude is the angle off level; sign says
 * which way to move the bubble.
 */
data class Tilt(
    val pitchDeg: Float,
    val rollDeg: Float,
    /** True when the phone is face-down: mathematically level, and
     *  practically useless. A vial that says "level" while upside
     *  down is telling the truth about numbers and lying about the
     *  world. */
    val isUseless: Boolean = false,
) {
    /** Angle off level, always positive. */
    val magnitude: Float get() = hypot(pitchDeg.toDouble(), rollDeg.toDouble()).toFloat()

    /** True inside [LEVEL_TOLERANCE_DEG]. */
    val isLevel: Boolean get() = magnitude < LEVEL_TOLERANCE_DEG

    /** True inside [LEVEL_NEAR_DEG] but not level. */
    val isNear: Boolean get() = magnitude < LEVEL_NEAR_DEG
}

/**
 * The direction to move the phone to reach level, as a short phrase.
 * "Tilt right" means raise the right edge, which lowers the bubble
 * toward it — the same as any real vial.
 */
fun tiltInstruction(tilt: Tilt): String? = when {
    tilt.isLevel -> null
    // One axis dominating by more than 3:1 is a single-axis instruction.
    abs(tilt.rollDeg) > abs(tilt.pitchDeg) * 3f ->
        if (tilt.rollDeg > 0f) "tilt right" else "tilt left"
    abs(tilt.pitchDeg) > abs(tilt.rollDeg) * 3f ->
        if (tilt.pitchDeg > 0f) "tilt forward" else "tilt back"
    else -> null
}

/**
 * Bubble position in [-1, 1] per axis. The old code divided by a fixed
 * 45°, so a bubble that went off the vial at 20° of tilt was silently
 * clamped and stopped responding — the instrument went dead exactly
 * when the user needed it most. A square-root response keeps the
 * bubble inside the housing at every angle while still resolving small
 * tilts precisely, which is the whole point of a vial.
 */
fun bubbleOffset(tilt: Tilt, fullScaleDeg: Float = 45f): Pair<Float, Float> {
    val p = (tilt.pitchDeg / fullScaleDeg).coerceIn(-1f, 1f)
    val r = (tilt.rollDeg / fullScaleDeg).coerceIn(-1f, 1f)
    // sqrt compresses the travel near the edge: linear motion out to
    // the rim, so the bubble never saturates before the housing does.
    fun shape(v: Float) = if (v == 0f) {
        0f
    } else {
        Math.copySign(kotlin.math.sqrt(abs(v)), v)
    }
    return shape(p) to shape(r)
}

/**
 * Pitch and roll in degrees from a gravity vector, plus which way up
 * the phone is.
 *
 * The atan pair alone cannot tell face-up from face-down: both give
 * pitch 0, roll 0, because a 180-degree rotation about a horizontal
 * axis leaves the same projection. A spirit level that reads "level"
 * while lying upside down is worse than one that admits it does not
 * know, so the face is derived from the sign of Z and reported.
 */
data class Orientation(
    val pitchDeg: Float,
    val rollDeg: Float,
    /** True when the screen faces the same way as the ground. */
    val faceUp: Boolean,
) {
    /** A level held face-down is level in the mathematical sense and
     *  useless in the practical one. */
    val isUseless: Boolean get() = !faceUp
}

fun orientationOf(gravity: FloatArray): Orientation {
    val ax = gravity[0].toDouble()
    val ay = gravity[1].toDouble()
    val az = gravity[2].toDouble()
    val pitch = Math.toDegrees(Math.atan2(-ax, Math.hypot(ay, az))).toFloat()
    val roll = Math.toDegrees(Math.atan2(ay, Math.hypot(ax, az))).toFloat()
    // Gravity points into the earth: screen-up on a table reads +Z.
    return Orientation(pitch, roll, faceUp = az >= 0f)
}

/** The two angles on their own, for callers that do not care about face. */
fun pitchRoll(gravity: FloatArray): Pair<Float, Float> {
    val o = orientationOf(gravity)
    return o.pitchDeg to o.rollDeg
}
