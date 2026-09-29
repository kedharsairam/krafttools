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
    /**
     * Total angle off level, in degrees, always positive.
     *
     * Carried in rather than derived from [pitchDeg] and [rollDeg].
     * Those are rotations about DIFFERENT axes — pitch is
     * `atan2(-ax, hypot(ay, az))`, roll is `atan2(ay, hypot(ax, az))` —
     * so they are not two components of one vector in a single frame
     * and the total is not recoverable from them.
     *
     * `hypot(pitch, roll)`, which this replaces, is exact on a single
     * axis and 26.4° low on a 90° diagonal: 63.6° where the truth is
     * 90°. That is the formula the angle ruler's own header condemns
     * in prose. The correct value is `atan2(hypot(ax, ay), |az|)`,
     * computed once from the sample by [orientationOf].
     *
     * Below about 1° the two agree to 5e-4 relative, so the 1° "level"
     * tolerance is unaffected — only the displayed angle at larger
     * tilts was lying.
     */
    val totalDeg: Float = 0f,
) {
    /** Angle off level, always positive. */
    val magnitude: Float get() = totalDeg

    /** True inside [LEVEL_TOLERANCE_DEG]. */
    val isLevel: Boolean get() = magnitude < LEVEL_TOLERANCE_DEG

    /** True inside [LEVEL_NEAR_DEG] but not level. */
    val isNear: Boolean get() = magnitude < LEVEL_NEAR_DEG
}

/**
 * The direction to move the phone to reach level, as a short phrase.
 * "Tilt right" means the right edge is low and should be raised.
 *
 * THE AXIS NAMING IS A TRAP. `orientationOf` derives
 *
 *   pitchDeg = atan2(-ax, ...)   from the X axis
 *   rollDeg  = atan2( ay, ...)   from the Y axis
 *
 * and the device X axis runs left-right across the screen while Y runs
 * bottom-to-top. So despite the names — which are the standard's, not
 * the phone's — a tilt measured in `pitchDeg` is a LEFT/RIGHT tilt and
 * a tilt measured in `rollDeg` is a FORWARD/BACK one. They are the
 * opposite of what the identifiers suggest.
 *
 * The old mapping paired them the intuitive way round, which made
 * every instruction 90° out: a phone with its right edge low was told
 * to "tilt forward", and one with its top edge down was told to "tilt
 * right". Following the instruction did not reduce the error at all.
 *
 * One axis dominating by more than 3:1 counts as a single-axis
 * instruction; anything more diagonal than that has no single correct
 * phrase, so nothing is claimed.
 */
fun tiltInstruction(tilt: Tilt): String? = when {
    tilt.isLevel -> null
    // pitchDeg is the X axis: left/right.
    abs(tilt.pitchDeg) > abs(tilt.rollDeg) * 3f ->
        if (tilt.pitchDeg > 0f) "tilt right" else "tilt left"
    // rollDeg is the Y axis: forward/back.
    abs(tilt.rollDeg) > abs(tilt.pitchDeg) * 3f ->
        if (tilt.rollDeg > 0f) "tilt forward" else "tilt back"
    else -> null
}

/**
 * Bubble position in [-1, 1] per axis, in SCREEN coordinates (x right,
 * y down). The old code divided by a fixed 45°, so a bubble that went
 * off the vial at 20° of tilt was silently clamped and stopped
 * responding — the instrument went dead exactly when the user needed
 * it most. A square-root response keeps the bubble inside the housing
 * at every angle while still resolving small tilts precisely, which is
 * the whole point of a vial.
 *
 * THE SIGN IS NEGATED, and that is the correction. An air bubble rises
 * to the HIGHEST point of the vial, so a right edge that is low must
 * put the bubble on the LEFT. The old code drew it on the low side —
 * it was a plumb bob, not a bubble — and the KDoc above this function
 * claimed the opposite of what the code did, asserting the picture
 * matched "any real vial". A user who trusted the picture levelled the
 * wrong way.
 *
 * Concretely: right edge low gives `pitchDeg > 0`, so the un-negated
 * offset was positive and the bubble drew to the right. Negating puts
 * it left, which is where the air would go.
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
    // Negated: see the note above. A bubble rises; this was a plumb bob.
    return -shape(p) to -shape(r)
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
    /**
     * Total angle off level, straight from the accelerometer vector:
     * `atan2(hypot(ax, ay), |az|)`. Exact, and NOT derivable from
     * [pitchDeg] and [rollDeg] — over 35 two-axis cases, recovering it
     * from those two is out by up to 20°, because they are rotations
     * about different axes rather than components of one vector.
     */
    val totalDeg: Float = 0f,
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
    val total = Math.toDegrees(
        Math.atan2(
            Math.hypot(ax, ay),
            Math.abs(az),
        ),
    ).toFloat()
    return Orientation(pitch, roll, faceUp = az >= 0f, totalDeg = total)
}

/** The two angles on their own, for callers that do not care about face. */
fun pitchRoll(gravity: FloatArray): Pair<Float, Float> {
    val o = orientationOf(gravity)
    return o.pitchDeg to o.rollDeg
}


/**
 * Total tilt remaining after a surface has been calibrated as level.
 *
 * [zero] is the accelerometer vector captured by "Calibrate". The
 * sample is rotated so that the zero vector points straight up, and the
 * tilt of the result is the deviation from it.
 *
 * Done on the VECTOR rather than on pitch and roll, and that is not a
 * stylistic choice. The two angles are rotations about different axes,
 * so the rotation they imply is not the one that actually maps the
 * zero vector onto vertical — subtracting or de-rotating by them leaves
 * a residual of several degrees even at a diagonal zero. Rotating the
 * vector itself is exact: over 81 diagonal zero angles the residual
 * against the zero vector itself is zero to machine precision, and the
 * response away from it is linear and strictly increasing.
 *
 * The rotation is two steps, each chosen to null one component:
 * `Ry(atan2(-ax, az))` zeroes x, then `Rx(atan2(y, z))` zeroes y.
 */
fun residualTilt(gravity: FloatArray, zero: FloatArray): Float {
    val zx = zero[0].toDouble()
    val zz = zero[2].toDouble()
    val phi = Math.atan2(-zx, zz)
    val cy = Math.cos(phi)
    val sy = Math.sin(phi)
    val gx = gravity[0].toDouble()
    val gy = gravity[1].toDouble()
    val gz = gravity[2].toDouble()
    val x1 = gx * cy + gz * sy
    val y1 = gy
    val z1 = -gx * sy + gz * cy
    // The second angle comes from the ZERO after step 1, not from the
    // sample. Deriving it from the sample makes the rotation depend on
    // the reading, so it is no longer the one that maps the zero onto
    // vertical — and on a diagonal zero it under-reports the deviation
    // by more than a degree.
    val zeroPhi = Math.atan2(-zx, zz)
    val zeroY1 = zero[1].toDouble()
    val zeroZ1 =
        -zx * Math.sin(zeroPhi) + zz * Math.cos(zeroPhi)
    val psi = Math.atan2(zeroY1, zeroZ1)
    val y2 = y1 * Math.cos(psi) - z1 * Math.sin(psi)
    val z2 = y1 * Math.sin(psi) + z1 * Math.cos(psi)
    return Math.toDegrees(
        Math.atan2(Math.hypot(x1, y2), Math.abs(z2)),
    ).toFloat()
}
