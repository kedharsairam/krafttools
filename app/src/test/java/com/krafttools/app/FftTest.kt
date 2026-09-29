package com.krafttools.app

import com.krafttools.app.ui.Fft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A subtly wrong FFT is worse than a slow one: it returns plausible
 * magnitudes that are quietly wrong, and every reading derived from
 * them is a lie. These check the FFT against the naive DFT it
 * replaces, and pin the properties the spectrum display depends on.
 */
class FftTest {

    /** The definition, kept here as the independent oracle. */
    private fun naiveMagnitudes(input: FloatArray): DoubleArray {
        val n = input.size
        val mags = DoubleArray(n / 2 + 1)
        for (k in 0..n / 2) {
            var re = 0.0
            var im = 0.0
            for (i in 0 until n) {
                val angle = 2.0 * PI * k * i / n
                re += input[i] * cos(angle)
                im -= input[i] * sin(angle)
            }
            mags[k] = kotlin.math.hypot(re, im)
        }
        return mags
    }

    @Test
    fun matchesTheNaiveTransformExactly() {
        for (n in listOf(8, 16, 64, 256, 1024)) {
            val input = FloatArray(n) { i ->
                (sin(2.0 * PI * 7.0 * i / n) * 0.5 +
                    sin(2.0 * PI * 31.0 * i / n) * 0.25).toFloat()
            }
            val fast = Fft.magnitudes(input)
            val slow = naiveMagnitudes(input)
            for (k in 0..n / 2) {
                assertEquals(
                    "n=$n bin=$k: fft ${fast[k]} vs dft ${slow[k]}",
                    slow[k],
                    fast[k],
                    slow[k] * 1e-6 + 1e-9,
                )
            }
        }
    }

    @Test
    fun aPureToneLandsInOneBin() {
        val n = 1024
        val bin = 40
        val input = FloatArray(n) { i ->
            sin(2.0 * PI * bin * i / n).toFloat()
        }
        val mags = Fft.magnitudes(input)
        val peak = mags.indices.maxByOrNull { mags[it] }!!
        assertEquals("a pure tone must not smear", bin, peak)
    }

    @Test
    fun dcBinHoldsTheSumOfTheSignal() {
        val n = 64
        val input = FloatArray(n) { 0.25f }
        val mags = Fft.magnitudes(input)
        assertEquals(n * 0.25, mags[0], 1e-6)
    }

    @Test
    fun silenceIsSilentEverywhere() {
        val mags = Fft.magnitudes(FloatArray(64))
        for (m in mags) assertEquals(0.0, m, 1e-9)
    }

    @Test
    fun negativeFrequenciesDoNotAppearInAOneSidedSpectrum() {
        val n = 64
        val mags = Fft.magnitudes(FloatArray(n))
        assertEquals(n / 2 + 1, mags.size)
    }

    @Test
    fun aSignFlipOnlyMovesPhase() {
        val n = 128
        val a = FloatArray(n) { sin(2.0 * PI * 9.0 * it / n).toFloat() }
        val b = FloatArray(n) { -a[it] }
        val ma = Fft.magnitudes(a)
        val mb = Fft.magnitudes(b)
        for (k in ma.indices) {
            assertEquals("bin $k changed magnitude under a sign flip", ma[k], mb[k], 1e-9)
        }
    }

    @Test
    fun nonPowerOfTwoIsRejectedRatherThanSilentlyWrong() {
        for (n in listOf(3, 100, 1000)) {
            var threw = false
            try {
                Fft.magnitudes(FloatArray(n))
            } catch (_: IllegalArgumentException) {
                threw = true
            }
            assertTrue("length $n should be rejected", threw)
        }
    }

    @Test
    fun inputIsNotMutated() {
        val input = FloatArray(64) { it.toFloat() }
        val copy = input.copyOf()
        Fft.magnitudes(input)
        for (i in input.indices) assertEquals(copy[i], input[i], 0f)
    }

    @Test
    fun magnitudesAreNonNegative() {
        val input = FloatArray(256) { (it % 7 - 3).toFloat() }
        for (m in Fft.magnitudes(input)) assertTrue("negative magnitude $m", m >= 0.0)
    }
}
