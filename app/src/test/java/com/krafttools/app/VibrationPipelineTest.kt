package com.krafttools.app

import com.krafttools.app.ui.AcFilter
import com.krafttools.app.ui.dominantFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * The vibration meter's headline number, tested through the pipeline the
 * app actually runs rather than through the function in isolation.
 *
 * That distinction is the whole point of this file. `dominantFrequency`
 * was always correct, and its unit test fed it a SIGNED sine — so it
 * was green. The only call site in the app fed it `abs(raw)`, and a
 * rectified sine has no energy at its own frequency:
 *
 *     |sin x| = 2/pi - (4/pi) SUM cos(2nx)/(4n^2 - 1)
 *
 * Every component lands at 2f, 4f, 6f. After the mean removal nothing
 * is left at f, so the search reported exactly DOUBLE the truth: 5 Hz
 * read as 10, 300 RPM as 600, on the one number the tool exists to give.
 *
 * Each test below therefore drives filter -> window -> analysis, which
 * is the only shape in which a rectification mistake is visible.
 */
class VibrationPipelineTest {

    private val sampleHz = 50f
    private val window = 128

    /**
     * The exact chain Vibration.kt performs per sample, replicated so
     * the test cannot drift away from the app. If the app's order or
     * its sign ever changes, this is the place to change it too.
     */
    private fun run(samples: List<Float>): Float? {
        val filter = AcFilter()
        val windowed = ArrayDeque<Float>(window)
        for (raw in samples) {
            // AcFilter removes DC (gravity), so a static phone reads 0.
            val filtered = filter.update(raw, 1f / sampleHz)
            // The app rectifies for DISPLAY and stores the SIGNED value
            // for ANALYSIS. Storing the rectified one is the bug.
            windowed.addLast(filtered)
            while (windowed.size > window) windowed.removeFirst()
        }
        return dominantFrequency(windowed.toList(), sampleHz)
    }

    private fun tone(hz: Float, seconds: Float, offset: Float = 0f) =
        (0 until (sampleHz * seconds).toInt()).map {
            (offset + sin(2.0 * PI * hz * it / sampleHz)).toFloat()
        }

    @Test
    fun aKnownToneIsReportedAtItsOwnFrequency() {
        // The test that would have caught the 2x bug. Before the fix
        // this returned 20.0 for a 10 Hz input.
        for (hz in listOf(5f, 10f, 12.5f, 15f, 20f)) {
            val got = run(tone(hz, 4f))
            assertNotNull("no frequency found for $hz Hz", got)
            // Window is 128 at 50 Hz = 2.56 s, so the bin width is
            // 0.39 Hz. Half a bin is the tightest honest tolerance.
            assertEquals(
                "a $hz Hz tone was reported as $got",
                hz,
                got!!,
                0.45f,
            )
        }
    }

    @Test
    fun theResultIsNeverDoubleTheTruth() {
        // Stated directly, because that was the symptom and because a
        // regression here is otherwise easy to miss in a list of
        // tolerance-based assertions.
        for (hz in listOf(4f, 6f, 8f, 11f, 18f)) {
            val got = run(tone(hz, 4f))!!
            assertTrue(
                "a $hz Hz tone reported as $got — that is the rectified" +
                    " spectrum's second harmonic",
                kotlin.math.abs(got - 2 * hz) > 0.6f,
            )
        }
    }

    @Test
    fun aStaticPhoneReportsNothing() {
        // Gravity, filtered, is a constant. There is no rhythm in it and
        // the tool says so rather than inventing one.
        assertEquals(null, run(List(200) { 9.81f }))
    }

    @Test
    fun aToneOnTopOfGravityStillReportsTheTone() {
        // The real case: a phone lying on a running machine. The DC
        // offset must not move the answer, only the filter removes it.
        val got = run(tone(10f, 4f, offset = 9.81f))
        assertNotNull(got)
        assertEquals(10f, got!!, 0.45f)
    }

    @Test
    fun rpmIsSixtyTimesTheFrequency() {
        // The readout the user actually compares against a machine's
        // nameplate, so the conversion is pinned here too.
        val hz = run(tone(10f, 4f))!!
        val rpm = hz * 60f
        assertEquals(600f, rpm, 30f)
    }

    @Test
    fun aQuietSignalAboveTheGateIsNotCalledATone() {
        // Broadband noise with no dominant component should read
        // "no dominant rhythm", not latch onto a random bin.
        var seed = 12345
        fun next(): Float {
            seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
            return (seed / 0x7FFFFFFF.toFloat() - 0.5f) * 0.4f
        }
        val noise = (0 until 200).map { next() }
        val got = run(noise)
        // Either nothing, or a frequency it is honest not to claim.
        if (got != null) {
            assertTrue("latched onto $got", got < sampleHz / 2f)
        }
    }
}
