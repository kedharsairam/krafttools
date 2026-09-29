package com.krafttools.app.ui

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * In-place iterative radix-2 FFT.
 *
 * The naive DFT this replaces cost O(n²) transcendentals: 1024 samples
 * meant 523k cos/sin pairs every analysis, on the audio thread, twice
 * a second. This is O(n log n) — about 10k butterflies for the same
 * answer, roughly fifty times less work.
 *
 * Requires a power-of-two length. Verified against the naive DFT in
 * FftTest, because a subtly wrong FFT is worse than a slow one: it
 * produces plausible numbers that are quietly incorrect.
 */
object Fft {
    /**
     * Magnitude spectrum of [input] (length must be a power of two,
     * at least 2), bins 0 until n/2 inclusive (the one-sided spectrum
     * of a real signal). Input is copied, not modified.
     */
    fun magnitudes(input: FloatArray): DoubleArray {
        val n = input.size
        require(n >= 2 && (n and (n - 1)) == 0) {
            "FFT length must be a power of two, got $n"
        }
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        for (i in 0 until n) re[i] = input[i].toDouble()

        // Bit-reversal permutation: this is what turns the data into
        // the order the butterflies below expect.
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
            var m = n shr 1
            while (m in 1..j) {
                j -= m
                m = m shr 1
            }
            j += m
        }

        // Cooley-Tukey: log2(n) passes, halving the stride each time.
        var len = 2
        while (len <= n) {
            val angle = -2.0 * Math.PI / len
            val wRe = cos(angle)
            val wIm = sin(angle)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                val half = len shr 1
                for (k in 0 until half) {
                    val a = i + k
                    val b = a + half
                    val tRe = re[b] * curRe - im[b] * curIm
                    val tIm = re[b] * curIm + im[b] * curRe
                    re[b] = re[a] - tRe
                    im[b] = im[a] - tIm
                    re[a] += tRe
                    im[a] += tIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }

        val half = n / 2
        val mags = DoubleArray(half + 1)
        for (k in 0..half) mags[k] = sqrt(re[k] * re[k] + im[k] * im[k])
        return mags
    }
}
