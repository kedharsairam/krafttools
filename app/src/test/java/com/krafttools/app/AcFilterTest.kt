package com.krafttools.app

import com.krafttools.app.ui.AcFilter
import com.krafttools.app.ui.PeakHold
import com.krafttools.app.ui.VibrationAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/**
 * The AC filter is what makes the vibration meter honest: it decides
 * what counts as vibration and what is the operator. These tests pin
 * the frequency response, not just the code path, because the whole
 * tool's credibility rests on that one curve.
 */
class AcFilterTest {

    /** Peak output for a unit-amplitude sine at [hz], after settling. */
    private fun response(hz: Float, seconds: Float = 30f, dt: Float = 0.005f): Float {
        val f = AcFilter()
        var peak = 0f
        var t = 0f
        while (t < seconds) {
            val x = sin(2.0 * Math.PI * hz * t).toFloat()
            val y = f.update(x, dt)
            if (t > 8f) peak = maxOf(peak, kotlin.math.abs(y))
            t += dt
        }
        return peak
    }

    @Test
    fun constantInputReadsAsZero() {
        val f = AcFilter()
        var last = 0f
        repeat(200) { last = f.update(9.81f, 1f / 50f) }
        assertEquals(0f, last, 1e-4f)
    }

    @Test
    fun firstSampleDefinesTheBaseline() {
        assertEquals(0f, AcFilter().update(3.3f, 0.02f), 1e-6f)
    }

    @Test
    fun handSwayIsRejected() {
        // 0.1 Hz: a hand or a table edge. Must be nearly gone.
        assertTrue("0.1 Hz sway should be under 15%, was ${response(0.1f)}",
            response(0.1f) < 0.15f)
    }

    @Test
    fun slowDriftIsRejected() {
        assertTrue("0.05 Hz drift should be under 8%, was ${response(0.05f)}",
            response(0.05f) < 0.08f)
    }

    @Test
    fun machineContentIsIntact() {
        // 5 Hz upward: this is the content the tool exists to show.
        for (hz in listOf(5f, 10f, 24f, 40f)) {
            val r = response(hz)
            assertTrue("$hz Hz should pass at over 90%, was $r", r > 0.90f)
        }
    }

    @Test
    fun responseRisesMonotonicallyThroughTheBand() {
        // A band-pass that dips somewhere in the middle would report a
        // machine as still at exactly the speed it runs at.
        var previous = 0f
        var hz = 0.1f
        while (hz <= 20f) {
            val r = response(hz, seconds = 20f)
            assertTrue(
                "response fell at $hz Hz ($previous -> $r)",
                r > previous - 0.02f,
            )
            previous = r
            hz *= 1.5f
        }
    }

    @Test
    fun resetClearsTheBaseline() {
        val f = AcFilter()
        f.update(9.81f, 0.02f)
        f.reset()
        assertEquals(0f, f.update(9.81f, 0.02f), 1e-6f)
    }

    @Test
    fun peakHoldDecaysSoItCanRecover() {
        val p = PeakHold(decayPerSecond = 0.5f)
        p.update(4f, 0.02f)
        assertEquals(4f, p.value, 1e-4f)
        // One second at half per second: back to roughly 2.
        p.update(0f, 1f)
        assertTrue("peak should have decayed, was ${p.value}", p.value in 1.9f..2.1f)
    }

    @Test
    fun peakHoldNeverGoesNegative() {
        val p = PeakHold()
        p.update(1f, 0.02f)
        repeat(1000) { p.update(0f, 0.02f) }
        assertTrue("peak floor should be zero, was ${p.value}", p.value >= 0f)
    }

    @Test
    fun everyAxisHasALabelAndAUnit() {
        for (a in VibrationAxis.entries) {
            assertTrue(a.label.isNotBlank())
            assertTrue(a.unit.isNotBlank())
        }
        assertEquals(4, VibrationAxis.entries.size)
    }

    @Test
    fun axisComponentsAreDistinctAndInRange() {
        // An accelerometer reports three values. Mapping an axis to an
        // index outside that is an out-of-bounds crash on first sample.
        val components = VibrationAxis.entries.mapNotNull { it.component }
        assertEquals(3, components.size)
        assertEquals(components.sorted(), components.distinct().sorted())
        for (c in components) {
            assertTrue("component $c is not a valid axis", c in 0..2)
        }
        assertEquals(null, VibrationAxis.TOTAL.component)
    }
}
