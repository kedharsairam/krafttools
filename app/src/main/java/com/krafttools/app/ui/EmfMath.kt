package com.krafttools.app.ui

import kotlin.math.abs

/**
 * Magnetometer arithmetic.
 *
 * The old tool reported the raw total field and thresholded it. That
 * cannot work: Earth's field is 25-70 µT (IGRF-14, whose minimum is
 * 22.07 µT over the South Atlantic Anomaly and whose maximum is
 * ~70 µT), so a threshold anywhere in that band is a false alarm
 * wherever the ambient field happens to sit. With the old 60 µT
 * default, the tool alarmed continuously across northern Canada,
 * Siberia and Antarctica with nothing nearby at all.
 *
 * The fix is the one every real metal detector uses: record a baseline
 * B₀ somewhere known to be clear, then report and threshold on the
 * *deviation* from it. Ambient geography cancels.
 */

/** Earth's total field range, µT. IGRF-14. */
const val EARTH_FIELD_MIN_UT = 22f
const val EARTH_FIELD_MAX_UT = 70f

/**
 * A magnetometer's declared full-scale range is a property of the part,
 * not of this app: read `Sensor.getMaximumRange()`. This is only the
 * fallback when that is unavailable, and it is deliberately generous.
 */
const val SENSOR_CEILING_UT = 3000f

/** Rotation-invariant magnitude of a three-axis field vector, µT. */
fun fieldMagnitude(x: Float, y: Float, z: Float): Float =
    kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()

/**
 * A captured background reading. Everything the tool reports is a
 * deviation from this, which is the only way to separate "there is
 * something here" from "you are in Canada".
 */
data class FieldBaseline(val microTesla: Float) {
    val isSane: Boolean get() = microTesla in EARTH_FIELD_MIN_UT..EARTH_FIELD_MAX_UT
}

/**
 * Deviation from the baseline, µT. Signed, so a caller can say
 * "higher than here" rather than just "different".
 */
fun deviationFrom(baselineUt: Float, currentUt: Float): Float = currentUt - baselineUt

/**
 * A Schmitt trigger, so a field sitting exactly on the threshold does
 * not stutter the alarm several times a second. It trips at [tripUt]
 * and only releases at [releaseUt], which sits a fixed fraction below
 * it. The old code compared a boolean directly against the threshold,
 * so magnetometer noise of a few tenths produced a rising edge
 * repeatedly and buzzed continuously.
 */
class Schmitt(private val releaseFraction: Float = 0.85f) {
    var engaged: Boolean = false
        private set

    /** Feed the deviation; returns true while the alarm should sound. */
    fun update(deviationUt: Float, tripUt: Float): Boolean {
        val release = abs(tripUt) * releaseFraction
        engaged = if (engaged) abs(deviationUt) > release else abs(deviationUt) > abs(tripUt)
        return engaged
    }

    fun reset() {
        engaged = false
    }
}

/** How the deviation should be described to a person. */
enum class EmfVerdict(val label: String) {
    CLEAR("clear of the baseline"),
    NEAR("something is close"),
    STRONG("strong — metal or current nearby"),
}

/**
 * Classify a deviation in µT, on a ladder that only ever means "more".
 *
 * The thresholds are on *deviation*, so they read the same in every
 * country: a fridge magnet is ~10 000 µT, a small neodymium at 10 cm is
 * 1 000-5 000 µT, and at 30 cm it is 100-500 µT.
 *
 * There is deliberately no "saturated" verdict here. Calling a fridge
 * magnet "too strong to measure" is the wrong answer for the one thing
 * the tool exists to find — a big reading is a *finding*, not a
 * failure. Running past the part's full scale is a separate note about
 * the instrument, handled by the caller.
 */
fun classifyDeviation(deviationUt: Float, tripUt: Float): EmfVerdict = when {
    abs(deviationUt) < 2f -> EmfVerdict.CLEAR
    abs(deviationUt) < abs(tripUt) -> EmfVerdict.NEAR
    else -> EmfVerdict.STRONG
}

/**
 * What this instrument can and cannot see, said in words the user can
 * act on. A static magnetometer responds to ferromagnetic material
 * only — iron, nickel, cobalt and their alloys. Aluminium, copper,
 * brass, gold and plastic are invisible to it. It does respond to
 * current-carrying conductors, which is the other thing people want.
 */
fun emfCapabilityNote(): String =
    "Reads a static field, so it sees ferromagnetic material — iron, " +
        "nickel, cobalt, and things like a fridge magnet. Aluminium, " +
        "copper, brass and plastic are invisible to it. It also " +
        "responds to current in a wire, so a live cable will show."
