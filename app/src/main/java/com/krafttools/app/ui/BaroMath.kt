package com.krafttools.app.ui

import kotlin.math.abs
import kotlin.math.pow

/**
 * Barometric arithmetic, in one tested place.
 *
 * The important thing about a barometer is that its signal is *small*:
 * a barometer chip is good to about 0.012 hPa, and the entire story of
 * a day's weather is 10–40 hPa. So anything that plots pressure against
 * a zero-based axis is a picture of a flat line, and any scale that
 * snaps to a "nice" number bigger than the variation is worse than no
 * graph. Both mistakes are easy to make and invisible until the trace
 * is on screen.
 */

/** International Standard Atmosphere pressure at sea level, in hPa. */
const val STANDARD_SEA_LEVEL_HPA = 1013.25f

/**
 * Altitude from station pressure and a sea-level reference, in metres.
 *
 * The hypsometric form: alt = 44330 * (1 - (p / p0) ^ 0.1903), where
 * 44330 is R*T/(M*g) for the standard atmosphere and 0.1903 is 1/5.255
 * (5.255 being the standard atmosphere's lapse-rate exponent).
 *
 * Accuracy against the ISA table: under 2 m from sea level to the
 * summit of Everest, which is far better than the ~8 m that one hPa is
 * worth at sea level.
 */
fun altitudeFromPressure(
    pressureHpa: Float,
    seaLevelHpa: Float = STANDARD_SEA_LEVEL_HPA,
): Float {
    if (pressureHpa <= 0f || seaLevelHpa <= 0f) return 0f
    return 44330f * (1 - (pressureHpa / seaLevelHpa).toDouble().pow(0.1903)).toFloat()
}

/**
 * Metres of altitude per hPa of pressure change, positive when
 * pressure rises. Used to put an error bar on the reading instead of
 * implying a precision the sensor does not have: at sea level one hPa
 * is about 8 m, at 3000 m about 11 m.
 */
fun metresPerHpa(pressureHpa: Float, seaLevelHpa: Float = STANDARD_SEA_LEVEL_HPA): Float =
    altitudeFromPressure(pressureHpa - 1f, seaLevelHpa) - altitudeFromPressure(
        pressureHpa,
        seaLevelHpa,
    )

/**
 * A pressure trace's vertical scale. Unlike every other trace in the
 * app this one is centred on the mean and sized to the *variation*,
 * because a barometer's signal is the change, not the value. Zero
 * based or snapped-to-nice would draw 1004.0 hPa as a flat line at the
 * top of the panel and hide the entire trend.
 */
data class BaroScale(val centre: Float, val span: Float) {
    /** Normalize a pressure into 0..1 for drawing. */
    fun norm(p: Float): Float = ((p - centre) / span + 0.5f).coerceIn(0f, 1f)

    /** The value at the bottom of the panel. */
    val floor: Float get() = centre - span / 2f

    /** The value at the top of the panel. */
    val ceiling: Float get() = centre + span / 2f
}

/**
 * Fit a scale to a pressure window. Centred on the **median** and sized
 * by a **percentile** of the deviation, not the mean and the maximum.
 *
 * The reason is physical, not aesthetic. A door slam is a genuine 8 hPa
 * excursion; the entire weather story of half an hour is about 0.6 hPa.
 * Sizing to the maximum let the slam own the axis and crushed the
 * weather into 2% of the panel — the graph was technically correct and
 * completely useless. A median centre and a 90th-percentile spread put
 * the weather across ~45% of the panel, and the slam clips off the top,
 * which is the honest depiction: it *is* off the scale.
 *
 * The span never drops below [minSpanHpa], so a dead-flat sensor shows
 * a centred line instead of amplifying noise into a storm.
 */
fun baroScale(values: List<Float>, minSpanHpa: Float = 0.4f): BaroScale {
    if (values.isEmpty()) return BaroScale(STANDARD_SEA_LEVEL_HPA, minSpanHpa * 2f)
    val sorted = values.sorted()
    val n = sorted.size
    val median = if (n % 2 == 1) {
        sorted[n / 2]
    } else {
        (sorted[n / 2 - 1] + sorted[n / 2]) / 2f
    }
    // 90th percentile of the deviation: ignores the few samples that
    // are doors and air conditioning.
    val devs = values.map { abs(it - median) }.sorted()
    val spread = devs[(n - 1) * 90 / 100]
    val half = maxOf(spread * 1.6f, minSpanHpa)
    return BaroScale(median, half * 2f)
}

/**
 * Barometric tendency in hPa per hour, from a pressure window sampled
 * at a fixed interval. Returns null until there is enough history to
 * say anything: below about 40 samples the answer is noise, and a
 * confident-sounding number from noise is worse than none.
 *
 * The rate is taken between the centres of the first and second half
 * of the window, which is the honest estimate — comparing the two
 * means against the *whole* window would halve the reported rate.
 */
fun tendencyHpaPerHour(
    values: List<Float>,
    intervalSeconds: Float = 15f,
    minSamples: Int = 40,
): Float? {
    if (values.size < minSamples) return null
    val half = values.size / 2
    if (half < 2) return null
    val first = values.take(half).average().toFloat()
    val second = values.drop(half).average().toFloat()
    // Time between the centres of the two halves.
    val spanHours = (half * intervalSeconds) / 3600f
    if (spanHours <= 0f) return null
    return (second - first) / spanHours
}

/** How a tendency should be described to a person. */
enum class BaroTrend(val label: String, val arrow: String) {
    FALLING_FAST("Falling fast — rain likely within hours", "↓ falling"),
    FALLING("Falling — change coming", "↓ falling"),
    STEADY("Steady — no change ahead", "→ steady"),
    RISING("Rising — improving", "↑ rising"),
    RISING_FAST("Rising fast — fair spell coming", "↑ rising"),
}

/**
 * Classify a tendency in hPa/hour. Thresholds follow the meteorological
 * convention of calling anything past about 2 hPa/h a *rapid* change:
 * that is 6 hPa in three hours, which is the difference between a
 * passing shower and a front arriving.
 */
fun classifyTendency(hpaPerHour: Float): BaroTrend = when {
    hpaPerHour <= -2f -> BaroTrend.FALLING_FAST
    hpaPerHour < -0.5f -> BaroTrend.FALLING
    hpaPerHour <= 0.5f -> BaroTrend.STEADY
    hpaPerHour < 2f -> BaroTrend.RISING
    else -> BaroTrend.RISING_FAST
}
