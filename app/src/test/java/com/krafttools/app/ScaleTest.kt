package com.krafttools.app

import com.krafttools.app.ui.AutoScale
import com.krafttools.app.ui.niceCeiling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trace scale is what a reader trusts before they trust the
 * waveform. These pin the two properties that matter: the printed
 * ceiling is a number a human reads, and the scale never clips the
 * signal it is scaling.
 */
class ScaleTest {

    @Test
    fun ceilingsLandOnOneTwoOrFive() {
        for (v in listOf(0.037f, 0.11f, 0.9f, 1.3f, 4.2f, 7f, 23f, 480f, 1600f)) {
            val c = niceCeiling(v)
            val mantissa = c / Math.pow(10.0, Math.floor(Math.log10(c.toDouble()))).toFloat()
            assertTrue(
                "$v -> $c is not a 1/2/5 step",
                kotlin.math.abs(mantissa - 1f) < 0.01f ||
                    kotlin.math.abs(mantissa - 2f) < 0.01f ||
                    kotlin.math.abs(mantissa - 5f) < 0.01f,
            )
        }
    }

    @Test
    fun ceilingAlwaysCoversTheSignal() {
        for (v in listOf(0.001f, 0.02f, 0.37f, 3.1f, 99f, 1234f)) {
            assertTrue("ceiling ${niceCeiling(v)} does not cover $v", niceCeiling(v) >= v)
        }
    }

    @Test
    fun degenerateInputsDoNotProduceNaN() {
        for (v in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val c = niceCeiling(v)
            assertTrue("niceCeiling($v) = $c", c.isFinite() && c > 0f)
        }
    }

    @Test
    fun scaleRisesImmediatelyToCoverASpike() {
        val s = AutoScale()
        s.update(0f, 0.02f)
        val after = s.update(8f, 0.02f)
        assertTrue("a spike must not be clipped, ceiling was $after", after >= 8f)
    }

    @Test
    fun scaleFallsBackSoAQuietTraceStillFillsThePanel() {
        val s = AutoScale()
        s.update(50f, 0.02f)
        val loud = s.ceiling
        // Twenty seconds of quiet at 50 Hz.
        repeat(1000) { s.update(0f, 0.02f) }
        assertTrue("scale never came back down: $loud -> ${s.ceiling}", s.ceiling < loud)
    }

    @Test
    fun scaleNeverCollapsesToZero() {
        val s = AutoScale()
        repeat(2000) { s.update(0f, 0.02f) }
        assertTrue("ceiling floor should hold, was ${s.ceiling}", s.ceiling > 0f)
    }

    @Test
    fun scaleIsStableUnderNoise() {
        // Auto-ranging must not flicker the printed scale frame to
        // frame; a jittering ceiling makes the trace look unstable.
        val s = AutoScale()
        s.update(1.0f, 0.02f)
        val first = s.ceiling
        var changes = 0
        repeat(500) {
            s.update(0.9f + (it % 3) * 0.02f, 0.02f)
            if (s.ceiling != first) changes++
        }
        assertTrue("ceiling flickered $changes times", changes <= 2)
    }

    @Test
    fun resetReturnsToTheFloor() {
        val s = AutoScale()
        s.update(100f, 0.02f)
        s.reset()
        assertTrue(s.ceiling <= 0.05f)
    }
}
