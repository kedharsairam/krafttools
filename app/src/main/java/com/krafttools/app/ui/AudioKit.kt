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

/** Linear (not dB) A-weight factor for an energy term. */
fun aWeightLinear(freqHz: Float): Double =
    Math.pow(10.0, (aWeightDb(freqHz) / 20.0).toDouble())
