package com.krafttools.app

import com.krafttools.app.ui.aWeightDb
import com.krafttools.app.ui.dominantFrequency
import com.krafttools.app.ui.hann
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/** Math-kit guarantees: weighting curve, window shape, peak picker. */
class AudioKitTest {
    @Test
    fun aWeightingIsZeroAt1kHz() {
        // Definition point of the A curve.
        assertEquals(0.0f, aWeightDb(1000f), 0.6f)
    }

    @Test
    fun aWeightingDiscountsBass() {
        // 100 Hz reads ~19 dB down; the whole point of A-weighting.
        assertTrue(aWeightDb(100f) < -15f)
    }

    @Test
    fun hannEndpointsAreZero() {
        assertEquals(0.0, hann(0, 128), 1e-9)
        assertEquals(0.0, hann(127, 128), 1e-3)
        assertEquals(1.0, hann(64, 128), 1e-9)
    }

    @Test
    fun dominantFrequencyFindsTone() {
        // 5 Hz sine at 50 Hz sampling, 120 samples.
        val samples = List(120) { i ->
            sin(2.0 * Math.PI * 5.0 * i / 50.0).toFloat()
        }
        val f = dominantFrequency(samples, 50f)
        assertTrue("expected ~5 Hz, got $f", f != null && kotlin.math.abs(f - 5f) < 1.5f)
    }

    @Test
    fun dominantFrequencyRejectsFlatline() {
        assertNull(dominantFrequency(List(120) { 0.01f }, 50f))
    }
}
