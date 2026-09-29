package com.krafttools.app.ui

import kotlin.math.pow

/**
 * Illuminance arithmetic.
 *
 * Two things here were quietly wrong. A NaN from a silent or faulting
 * HAL latched into the session maximum forever — `Math.max` returns
 * NaN, and `maxOf` over a list containing one propagates it — so the
 * header printed "NaN" for the rest of the session with no way out.
 * And the min/max were accumulated in a `LaunchedEffect` keyed on the
 * reading, which is cancelled and relaunched whenever the key changes:
 * two distinct lux values inside one frame meant the first was
 * dropped before its body ever ran.
 *
 * Accumulation belongs in the sensor callback, one comparison at a
 * time, which is where it is now.
 */

/** Reject readings a light sensor should never produce. */
fun isPlausibleLux(value: Float): Boolean =
    value.isFinite() && value >= 0f

/**
 * How the sensor's own declared range is described. `getMaximumRange`
 * is the exact, knowable bound — the old copy guessed "5-30k lux"
 * with no source, which is exactly the kind of unsourced number this
 * app is supposed to avoid.
 */
fun saturationNote(maximumRange: Float): String = when {
    maximumRange <= 0f -> "This sensor does not report a usable range."
    // Anything that cannot cover daylight is not reporting lux. The
    // classic raw-count full scales are 1023, 4095 and 65535.
    maximumRange < 10000f ->
        (
            "This sensor reports a raw count, not lux: its full scale is " +
                "%.0f, which is below daylight. The reading is therefore " +
                "approximate."
            ).format(maximumRange)
    else ->
        "Reads to about %,.0f lux before it saturates.".format(maximumRange)
}

/** A named band, with the value the reading came from. */
data class LuxBand(val label: String, val detail: String)

/**
 * Name an illuminance reading. The boundaries are the ones a
 * photographer or an electrician would recognise; they are convention,
 * not a standard, and the copy says so.
 *
 * Exact intervals: [0,10) dark, [10,100) dim, [100,1000) indoor,
 * [1000,10000) bright daylight, [10000,inf) direct sun.
 */
fun luxBand(lux: Float): LuxBand = when {
    lux < 10f -> LuxBand("Dark", "starlight to a candle at arm's length")
    lux < 100f -> LuxBand("Dim", "a dark room, or a lit room at dusk")
    lux < 1000f -> LuxBand("Indoor", "home or office lighting")
    lux < 10000f -> LuxBand("Daylight", "overcast, or out of direct sun")
    else -> LuxBand("Direct sun", "bright sun on a surface")
}

/** Photo exposure equivalents, which is what a number in lux is for. */
fun evAt(lux: Float, iso: Int = 100): Double? {
    if (lux <= 0f) return null
    // EV100 = log2(lux * 100 / iso) for a reflectance of ~18%.
    return kotlin.math.log2(lux.toDouble() * 100.0 / iso)
}

/** Standard shutter speeds, in seconds. */
private val SHUTTERS = listOf(
    1.0 / 8000, 1.0 / 4000, 1.0 / 2000, 1.0 / 1000, 1.0 / 500, 1.0 / 250,
    1.0 / 125, 1.0 / 60, 1.0 / 30, 1.0 / 15, 1.0 / 8, 1.0 / 4,
    1.0 / 2, 1.0, 2.0, 4.0, 8.0, 15.0, 30.0,
)

/** Standard f-numbers. */
private val F_STOPS = listOf(1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0, 22.0)

/** Format a shutter duration the way a camera does. */
fun formatShutter(seconds: Double): String = if (seconds >= 1.0) {
    "%.0fs".format(seconds)
} else {
    "1/%.0f".format(1.0 / seconds)
}

/**
 * Shutter speed for a given EV at a given f-number.
 *
 * The relation is EV = log2(N² / t), so t = N² / 2^EV. An earlier
 * version used 1 / 2^EV, which silently assumes an f-number of 1 and
 * was out by a factor of N² — bright sun came out as 1/8000 instead of
 * the correct 1/125.
 */
fun shutterFor(ev: Double, aperture: Double = 5.6): String {
    val exact = (aperture * aperture) / 2.0.pow(ev)
    val best = SHUTTERS.minByOrNull {
        kotlin.math.abs(kotlin.math.ln(it / exact))
    }!!
    return formatShutter(best)
}

/**
 * f-number for a given EV at a given shutter. The inverse of the
 * relation above: N = sqrt(2^EV * t).
 */
fun apertureFor(ev: Double, shutter: Double = 1.0 / 60.0): String {
    val exact = kotlin.math.sqrt(2.0.pow(ev) * shutter)
    val best = F_STOPS.minByOrNull {
        kotlin.math.abs(kotlin.math.ln(it / exact))
    }!!
    return "f/%.1f".format(best)
}
