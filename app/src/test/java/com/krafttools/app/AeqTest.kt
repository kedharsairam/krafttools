package com.krafttools.app

import com.krafttools.app.ui.aWeightDb
import com.krafttools.app.ui.aWeightLinear
import com.krafttools.app.ui.hann
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The LAeq path once had a 24 dB normalization error: it summed eight
 * display bands instead of the full spectrum, and omitted Parseval and
 * the Hann power gain. These pin the arithmetic so that class of bug
 * cannot come back quietly.
 */
class AeqTest {

    /** Parseval: the two-sided sum of |X(k)|² is n times the
     *  time-domain energy of x. (Not n² — that is the amplitude
     *  relation, not the energy one.) */
    @Test
    fun parsevalHoldsForTheSpectrum() {
        val n = 256
        val input = FloatArray(n) { i ->
            (0.6 * sin(2.0 * PI * 11.0 * i / n) +
                0.3 * sin(2.0 * PI * 29.0 * i / n)).toFloat()
        }
        val mags = com.krafttools.app.ui.Fft.magnitudes(input)
        var timeEnergy = 0.0
        for (v in input) timeEnergy += v.toDouble() * v
        var specEnergy = 0.0
        for (k in mags.indices) specEnergy += mags[k] * mags[k]
        // One-sided: bin 0 and Nyquist count once, the rest twice.
        val doubled = specEnergy * 2 -
            mags[0] * mags[0] - mags[n / 2] * mags[n / 2]
        val expected = timeEnergy * n
        assertEquals("Parseval", expected, doubled, expected * 1e-6)
    }

    @Test
    fun hannWindowHasThePowerGainWeCompensateFor() {
        // Mean-square gain of a Hann window is 0.375. The LAeq
        // normalization divides by exactly this; if the window ever
        // changed, the reading would shift by a fixed offset.
        val n = 1024
        var sum = 0.0
        for (i in 0 until n) sum += hann(i, n) * hann(i, n)
        assertEquals(0.375, sum / n, 1e-9)
    }

    @Test
    fun aWeightingMatchesItsReferencePoints() {
        // IEC 61672: 0 dB at 1 kHz, about -4.5 dB at 100 Hz (effectively
        // 0 above 2 kHz), and the curve falls away hard at both ends.
        assertEquals(0f, aWeightDb(1000f), 0.1f)
        assertTrue("100 Hz should be attenuated, was ${aWeightDb(100f)}",
            aWeightDb(100f) < -3.5f)
        assertTrue("20 Hz should be heavily attenuated, was ${aWeightDb(20f)}",
            aWeightDb(20f) < -50f)
        // Reference values: -1.1 dB at 8 kHz, -2.5 at 10 kHz, -6.7 at
        // 16 kHz. It approaches 0 dB only well below 2 kHz.
        assertEquals(-2.5f, aWeightDb(10000f), 0.2f)
        assertEquals(-1.1f, aWeightDb(8000f), 0.2f)
        assertEquals(-6.7f, aWeightDb(16000f), 0.3f)
        assertEquals(+1.2f, aWeightDb(2000f), 0.1f)
    }

    @Test
    fun aWeightingPeaksBetweenTwoAndFourKilohertz() {
        // The curve is not monotonic: it rises from 0 dB at 1 kHz to a
        // broad maximum around 2.5-3 kHz, then falls away. Asserting
        // monotonicity here is a mistake the reference table corrects.
        val at1k = aWeightDb(1000f)
        val at3k = aWeightDb(3000f)
        val at16k = aWeightDb(16000f)
        assertTrue("3 kHz should be above 1 kHz", at3k > at1k)
        assertTrue("16 kHz should be well below the peak", at16k < at3k - 5f)
        // The peak is shallow — under 2 dB — and sits in the 2-4 kHz
        // region where the ear is most sensitive.
        var peak = 0f
        var peakAt = 0f
        for (f in 2000..4000 step 50) {
            val v = aWeightDb(f.toFloat())
            if (v > peak) {
                peak = v
                peakAt = f.toFloat()
            }
        }
        assertTrue("peak at $peakAt Hz is outside 2-4 kHz", peakAt in 2000f..4000f)
        assertTrue("the A-weighting peak should be shallow, was $peak", peak < 2f)
    }

    @Test
    fun linearWeightIsTheSquareRootOfTheDbWeight() {
        // Energy terms get a power ratio; getting this wrong by a
        // factor of two is a 3 dB error on every A-weighted reading.
        for (f in listOf(31.5f, 125f, 1000f, 4000f, 10000f)) {
            val fromDb = Math.pow(10.0, (aWeightDb(f) / 20.0).toDouble())
            assertEquals("weight at $f Hz", fromDb, aWeightLinear(f), 1e-9)
        }
    }

    @Test
    fun bassIsAttenuatedRelativeToOneKilohertz() {
        // This is the whole point of A-weighting: without it, LAeq
        // over-reads traffic and under-reads hiss.
        assertTrue(
            "63 Hz must weigh less than 1 kHz",
            aWeightLinear(63f) < aWeightLinear(1000f) * 0.05,
        )
    }

    @Test
    fun windowIsZeroAtTheStartAndOneInTheMiddle() {
        // This is the PERIODIC Hann window: exactly 0 at i=0 and at
        // i=n, and it tiles seamlessly across blocks. A symmetric
        // window would divide by n-1 and give 0 at n-1 instead — wrong
        // choice here, since a discontinuity between blocks leaks.
        assertEquals(0.0, hann(0, 1024), 1e-12)
        assertEquals(1.0, hann(512, 1024), 1e-12)
        assertEquals(0.0, hann(1024, 1024), 1e-9)
        // The last sampled index is small but deliberately not zero.
        assertTrue("hann(1023) should be tiny", hann(1023, 1024) < 1e-5)
    }
}
