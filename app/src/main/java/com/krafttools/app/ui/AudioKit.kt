package com.krafttools.app.ui

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Shared audio/math kit: A-weighting, Hann windows, level helpers.
 * One copy — Decibel and Vibration drink from the same well so the
 * two tools can never disagree about what a decibel is.
 */

/** A-weighting in dB for a center frequency (IEC 61672 curve).
 * Phone-mic LAeq without this over-reads bass and under-reads hiss. */
fun aWeightDb(freqHz: Float): Float {
    val f2 = freqHz.toDouble() * freqHz
    val num = 12194.0 * 12194.0 * f2 * f2
    val den = (f2 + 20.6 * 20.6) * (f2 + 12194.0 * 12194.0) *
        sqrt((f2 + 107.7 * 107.7) * (f2 + 737.9 * 737.9))
    return (20 * log10(num / den)).toFloat() + 2.0f
}

/** Hann window multiplier for sample i of n: kills spectral leakage
 * so a pure tone lands in one bin instead of smearing everywhere. */
fun hann(i: Int, n: Int): Double =
    0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / n))

/**
 * Linear A-weight factor for an AMPLITUDE term: 10^(A/20).
 *
 * This is the amplitude (pressure) ratio, and it is the square root of
 * the energy (power) ratio. It was documented as being "for an energy
 * term" and was then applied to `mags[k] * mags[k]` — which made the
 * sound meter weight every bin's POWER by the amplitude factor, so
 * bass came out up to 50 dB too loud: 100 Hz was +19.1 dB high, 63 Hz
 * +26.2 dB. The module's own note said "phone-mic LAeq without this
 * over-reads bass", and the mistake was in the factor rather than in a
 * missing one.
 *
 * Use [aWeightPower] when the term being weighted is a square.
 */
fun aWeightLinear(freqHz: Float): Double =
    Math.pow(10.0, (aWeightDb(freqHz) / 20.0).toDouble())

/**
 * Linear A-weight factor for an ENERGY (power) term: 10^(A/10).
 *
 * Decibels are a power ratio, so the linear factor matching a dB
 * figure is 10^(dB/10). Weighting `mags[k]^2` needs this, not
 * [aWeightLinear]. The two differ by exactly the A-weighting curve in
 * dB, so using the wrong one is a large systematic error rather than a
 * rounding difference.
 */
fun aWeightPower(freqHz: Float): Double {
    val a = aWeightLinear(freqHz)
    return a * a
}
