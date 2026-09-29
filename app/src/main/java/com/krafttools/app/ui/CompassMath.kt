package com.krafttools.app.ui

import kotlin.math.abs

/**
 * Compass arithmetic, in one place with the wrapping rules stated. Every
 * bearing calculation here is a modulo of 360, and a modulo of 360 is
 * where off-by-a-turn bugs live: a heading that reads 359° when the
 * answer is 1° is worse than showing nothing, because it is confident.
 */

/** Bring any angle into [0, 360). */
fun compassWrap(deg: Float): Float = ((deg % 360f) + 360f) % 360f

/**
 * Signed shortest error from [bearing] to [now], in (-180, 180].
 * Positive means the needle is right of (clockwise from) the bearing.
 * The half-turn case resolves to +180 so "ahead" and "behind" are
 * never ambiguous.
 */
fun compassBearingError(now: Float, bearing: Float): Float {
    val delta = compassWrap(now - bearing + 180f)
    return delta - 180f
}

/** Magnetic azimuth plus declination, wrapped onto the dial. */
fun trueBearing(azimuth: Float, declination: Float): Float =
    compassWrap(azimuth + declination)

private val CARDINALS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

/**
 * The nearest of the eight compass points. Boundaries sit at the
 * midpoint (22.5° between N and NE), not on the multiples of 45 — the
 * old version rounded 22.5 up to NE, so "almost north" read as
 * "north-east".
 */
fun cardinal(azimuth: Float): String {
    val a = compassWrap(azimuth)
    return CARDINALS[(((a + 22.5f) / 45f).toInt()) % 8]
}

/**
 * How far off the dial is a reading, and how much to trust it. The
 * honest answer to "can I believe this heading" is not a boolean —
 * tilt and magnetic disturbance both degrade the number gradually.
 */
data class CompassConfidence(
    /** 0 = unusable, 1 = a flat phone in clean air. */
    val quality: Float,
    /** The one word shown to the user. */
    val verdict: String,
)

/**
 * Grades a heading for display. Tilt is the dominant error source:
 * the rotation matrix assumes gravity lies along device -Z, and past
 * ~35° the heading degrades quickly. A disturbed field (phone next to
 * a car body, a magnet, a laptop) is the other, and it is worse,
 * because it is silent.
 */
fun compassConfidence(
    tiltDeg: Float,
    strengthUt: Float,
    haveFix: Boolean,
): CompassConfidence {
    val fieldOk = strengthUt in 20f..70f
    val tiltOk = tiltDeg <= 35f
    val tiltPenalty = (tiltDeg / 60f).coerceIn(0f, 1f)
    val quality = when {
        !fieldOk -> 0.15f
        tiltDeg > 35f -> 0.6f - tiltPenalty * 0.4f
        else -> 1f - tiltPenalty * 0.3f
    }
    val verdict = when {
        !fieldOk -> "metal nearby"
        tiltDeg > 35f -> "lay it flat"
        tiltDeg > 15f -> "tilted"
        !haveFix -> "magnetic"
        else -> "true north"
    }
    return CompassConfidence(quality.coerceIn(0f, 1f), verdict)
}

/** The bearing-error band, in degrees, that reads as "you are there". */
internal const val BEARING_TOLERANCE_DEG = 3f

/** True when the needle is within [BEARING_TOLERANCE_DEG] of a lock. */
fun onBearing(now: Float, bearing: Float): Boolean =
    abs(compassBearingError(now, bearing)) <= BEARING_TOLERANCE_DEG
