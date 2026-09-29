package com.krafttools.app.ui

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/**
 * Speedometer and trip arithmetic.
 *
 * Two defects here were about honesty rather than rounding:
 *
 * - "Average speed" was an unweighted mean of per-fix speeds. That
 *   equals true average speed only if fixes arrive at uniform
 *   intervals, which GPS does not promise: batching, multipath and
 *   signal loss all stretch the gaps, and a fix after a 30-second
 *   dropout counted exactly as much as one after 20 ms. Average speed
 *   is distance divided by time, and the distance was already being
 *   tracked, so it is used.
 *
 * - Trip maximum and average were drawn from a WIDER set of fixes than
 *   the distance: the teleport guard rejected a 1800 km/h jump from
 *   the odometer but the same fix still set the trip maximum and
 *   dragged the average. A tunnel-exit fix became "your top speed".
 */
object SpeedMath {

    const val MS_TO_KMH = 3.6f

    /** 3600 / 1609.344, exact. The international statute mile is
     *  1609.344 m by the 1959 international agreement. */
    const val MS_TO_MPH = 2.2369363f

    /** Mean Earth radius, km (IUGG R1 = 6371.0088). */
    const val EARTH_RADIUS_KM = 6371.0

    /** 1 degree of arc on this sphere, in km. */
    const val KM_PER_DEGREE = EARTH_RADIUS_KM * (Math.PI / 180.0)

    fun toKmh(ms: Float): Float = ms * MS_TO_KMH

    fun toMph(ms: Float): Float = ms * MS_TO_MPH

    /**
     * Great-circle distance in km. The haversine form is used rather
     * than the law of cosines precisely because it stays accurate for
     * small separations — which is every step a trip odometer takes.
     */
    fun haversineKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_KM * asin(kotlin.math.sqrt(a))
    }

    /** A step above this is a jump, not a drive. 0.5 km/s = 1800 km/h. */
    const val TELEPORT_KM = 0.5

    /** Whether a step is plausible and should be added to the odometer. */
    fun isPlausibleStep(km: Double): Boolean = km in 0.0..TELEPORT_KM

    /**
     * Average speed as distance over time, which is what the word
     * means. Returns null until there is both a distance and a
     * duration, because a trip that has not moved has no average.
     */
    fun averageSpeedMs(distanceKm: Double, elapsedSeconds: Double): Float? {
        if (elapsedSeconds <= 0.0 || distanceKm <= 0.0) return null
        return (distanceKm * 1000.0 / elapsedSeconds).toFloat()
    }

    /**
     * Smoothing factor for the displayed speed.
     *
     * The old display smoothed with a 3-fix moving average. That has
     * two defects a single-seeded EMA does not: it starts from an empty
     * list so the first two readings are shown raw, and a window
     * average lags by half its width — at 1 Hz that is over a second
     * of lag, on a number whose whole purpose is to be current.
     *
     * alpha = 2/(N+1) for an N-fix equivalent window, so 0.5 is the
     * same responsiveness as the 3-fix average it replaces, with no
     * window lag and no cold start.
     */
    const val EMA_ALPHA = 0.5f

    /**
     * One step of the displayed-speed filter. Seeded on the first
     * sample: the first reading IS the estimate, rather than a value
     * diluted by an empty history.
     */
    fun smoothedSample(current: Float?, sample: Float): Float = when {
        current == null -> sample
        else -> current + (sample - current) * EMA_ALPHA
    }

    /**
     * Geometry of the speed dial, in pixels.
     *
     * Extracted from the composable so the fit can be proved by a test
     * rather than eyeballed on a screen. A 240-degree arc drawn from
     * screen angle 150 to 390 has both ends at 150 and 30 degrees, and
     * sin of each is +0.5. Screen y grows downward, so both ends hang
     * BELOW the pivot by 0.5r while the top of the arc is 1.0r above
     * it. A radius sized from the width alone therefore needs 1.5r of
     * vertical room, not r — which is how the old gauge drew both end
     * ticks and the needle at 0% and 100% off the bottom of the panel.
     */
    data class Dial(
        val radius: Float,
        val pivotY: Float,
        val strokeWidth: Float,
    ) {
        /** Highest point of the arc, in pixels from the top. */
        val top: Float get() = pivotY - radius

        /** Lowest point of the arc: the ends, 0.5r below the pivot. */
        val bottom: Float get() = pivotY + radius * 0.5f
    }

    /** Horizontal reach of a 240-degree arc: 2 * r * sin(60 degrees). */
    const val ARC_WIDTH_FACTOR = 1.7320508f

    /** Vertical room a 240-degree arc needs: 1.0r above plus 0.5r below. */
    const val ARC_HEIGHT_FACTOR = 1.5f

    /**
     * The largest dial that fits in [width] x [height] with the given
     * insets. The pivot is placed from the radius, not the reverse, so
     * the result always fits.
     */
    fun dialGeometry(
        width: Float,
        height: Float,
        topInset: Float,
        bottomInset: Float,
    ): Dial {
        // coerceAtLeast(0f) because a panel too small for the insets
        // gives a NEGATIVE radius, and drawArc throws on a negative size
        // rather than drawing nothing. Compose can genuinely hand a
        // zero-height Canvas during a layout pass.
        val radius = (minOf(
            width / ARC_WIDTH_FACTOR,
            (height - topInset - bottomInset) / ARC_HEIGHT_FACTOR,
        ) * 0.96f).coerceAtLeast(0f)
        return Dial(
            radius = radius,
            pivotY = topInset + radius,
            strokeWidth = (radius * 0.10f).coerceIn(4f, 14f),
        )
    }

    /**
     * Natural height / width of a 240-degree dial: 1.5r / 1.732r.
     *
     * Laying the canvas out at this ratio means the dial always exactly
     * fills it. The old layout gave the canvas all the leftover height,
     * and a width-limited dial uses none of it, so roughly 400dp of the
     * screen was dead black space below the gauge.
     *
     * @see DIAL_WIDTH_OVER_HEIGHT
     */
    // Compose's aspectRatio takes WIDTH / HEIGHT, which is the opposite
    // way round from the geometry above, so it is defined from the same
    // two factors rather than written as a literal — and named
    // accordingly. Getting this backwards gives a canvas 1.5r too tall,
    // and the extra space appears as dead black below the dial.
    const val DIAL_WIDTH_OVER_HEIGHT = ARC_WIDTH_FACTOR / ARC_HEIGHT_FACTOR

    /** Screen angle, in degrees, at which the dial's arc begins. */
    const val DIAL_START_ANGLE = 150f

    /** How far the dial's arc sweeps. */
    const val DIAL_SWEEP = 240f

    /** Screen angle for straight up, since screen y grows downward. */
    const val SCREEN_UP_ANGLE = 270f

    /**
     * Rotation, in degrees, that puts the needle at [fraction] of the
     * dial.
     *
     * The needle is drawn as a line pointing straight UP, which is
     * screen angle 270. Compose's `rotate` turns clockwise, so
     * placing that line at arc angle t means rotating by t - 270. The
     * code used to pass t itself, which is a constant 270-degree error:
     * at 0 km/h the cyan fill sat on the lower-left of the dial and the
     * needle pointed to the lower-right, so the dial and the number
     * disagreed about what the car was doing.
     */
    fun needleRotation(fraction: Float): Float =
        DIAL_START_ANGLE + DIAL_SWEEP * fraction.coerceIn(0f, 1f) - SCREEN_UP_ANGLE

    /**
     * Below this the fix is precise enough to trust as a speed.
     *
     * Approximate location reports accuracies of 1000-2000 m, which
     * sounds extreme but is Android's documented behaviour for a coarse
     * fix. At that error the position is still good enough to compute a
     * speed from successive fixes; what it is not good enough for is the
     * trip odometer, because the teleport guard rejects any step over
     * 500 m and every step at this accuracy is over it.
     */
    const val PRECISE_ENOUGH_METRES = 30f

    /** Whether a fix is precise enough to contribute to the odometer. */
    fun countsTowardsTrip(accuracyMetres: Float?): Boolean =
        accuracyMetres != null &&
            accuracyMetres.isFinite() &&
            accuracyMetres < PRECISE_ENOUGH_METRES

    /**
     * Why the odometer is not moving, in the user's terms. A trip
     * showing 0.00 km forever with no explanation reads as a broken
     * odometer; the real reason is the permission the user chose.
     */
    fun tripAccuracyNote(accuracyMetres: Float?): String? = when {
        accuracyMetres == null || !accuracyMetres.isFinite() ->
            "No fix yet."
        // The format argument is bound to the WHOLE parenthesised
        // concatenation. Writing "a" + "b".format(x) applies the format
        // to "b" alone and prints the placeholder literally in the
        // first half — which is exactly what it did here, so the note
        // read "only good to %.0f m". FormatStringTest exists for this.
        accuracyMetres >= 1000f -> (
            "Location is set to approximate, so fixes are only good to " +
                "about %.0f m. That is fine for speed, but the trip " +
                "distance needs metre-level fixes and will stay at " +
                "0.00 km until you turn on precise location."
            ).format(accuracyMetres)
        !countsTowardsTrip(accuracyMetres) -> (
            "The current fix is only good to %.0f m, so it is too coarse " +
                "to add to the trip distance. Go outside and wait for a " +
                "tighter fix."
            ).format(accuracyMetres)
        else -> null
    }

    /** Full-scale for the gauge, per unit. */
    fun fullScale(metric: Boolean): Float = if (metric) 120f else 75f

    /**
     * Where the needle sits, 0..1. 120 km/h and 75 mph are both about
     * the same physical speed but not exactly: 75 mph is 120.72 km/h.
     */
    fun gaugeFraction(speed: Float, metric: Boolean): Float =
        (speed / fullScale(metric)).coerceIn(0f, 1f)
}

/** Format a trip duration the way a trip computer does. */
fun formatElapsed(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}
