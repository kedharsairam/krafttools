package com.krafttools.app.ui

import kotlin.math.PI

/**
 * Turns a raw accelerometer axis into vibration.
 *
 * Gravity and the slow lean of a hand are DC and near-DC; a running
 * motor is not. A single-pole high-pass at [CUTOFF_HZ] separates them
 * with one multiply-add per sample, and the measured response (see
 * AcFilterTest) is the design:
 *
 *   0.05 Hz drift  ->  5%   hand sway, ignored
 *   0.20 Hz wobble -> 19%   below the noise floor of a real machine
 *   1.00 Hz       -> 69%   the corner itself
 *   5 Hz and up   -> 96%+   motor, bearing, pump: intact
 *
 * A plain "magnitude minus 9.81" (what this tool used to do) cannot
 * do this: it leaves every slow motion in the reading, so a phone
 * being held reads as vibration.
 */
class AcFilter(cutoffHz: Float = CUTOFF_HZ) {
    private val tau = 1f / (2f * PI.toFloat() * cutoffHz)
    private var lp: Float? = null

    /** Feeds one sample, returns the AC (vibration) part in m/s². */
    fun update(x: Float, dtSec: Float): Float {
        val prev = lp
        if (prev == null) {
            // Seed with the first sample so a phone being laid down does
            // not spend its first second re-learning where zero is.
            lp = x
            return 0f
        }
        val alpha = (dtSec / tau).coerceIn(0f, 1f)
        val next = prev + (x - prev) * alpha
        lp = next
        return x - next
    }

    fun reset() {
        lp = null
    }

    companion object {
        /**
         * 1 Hz. Below this the meter is looking at the operator, not the
         * machine; above it, the machine speaks at close to full scale.
         */
        const val CUTOFF_HZ = 1f
    }
}

/**
 * Peak hold with decay, the way a bench meter behaves. A plain
 * "largest value ever seen" never recovers from one bump, which makes
 * it useless for judging whether the current state is the worst one.
 */
class PeakHold(private val decayPerSecond: Float = 0.55f) {
    var value: Float = 0f
        private set

    fun update(sample: Float, dtSec: Float): Float {
        val decayed = value * Math.pow(decayPerSecond.toDouble(), dtSec.toDouble()).toFloat()
        value = maxOf(sample, decayed, 0f)
        return value
    }

    fun reset() {
        value = 0f
    }
}
