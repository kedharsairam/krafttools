package com.krafttools.app

import com.krafttools.app.ui.spanLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The barometer's trace label.
 *
 * This exists because the trace was labelled "30-minute trace" from the
 * moment it appeared, while holding whatever samples had arrived. The
 * window is 120 samples at 15 s, so it *becomes* a 30-minute trace after
 * thirty minutes — and a user who opens the tool and looks at it for a
 * minute is told they are looking at thirty minutes of pressure
 * history. That is the rubric's first criterion in miniature: the tool
 * cannot tell "no value yet" from "a short value", so the number it
 * states is not the number it has.
 */
class BaroSpanTest {

    @Test
    fun aSingleSampleIsPartialNotZeroMinutes() {
        // "0-minute trace" is not English, and the count is about to
        // change, so anything under a minute is named rather than
        // measured.
        assertEquals("partial", spanLabel(samples = 0))
        assertEquals("partial", spanLabel(samples = 1))
    }

    @Test
    fun justUnderAMinuteIsStillPartial() {
        // 3 samples at 15 s is 45 s. Rounding it to "1-minute" would
        // overstate by a third; rounding down to "0" is not English.
        assertEquals("partial", spanLabel(samples = 3))
    }

    @Test
    fun sixtySecondsIsOneMinute() {
        // 4 samples at 15 s is exactly 60 s. This is the case that used
        // to display "30-minute trace".
        assertEquals("1-minute", spanLabel(samples = 4))
    }

    @Test
    fun aTenMinuteTraceSaysTenMinutes() {
        assertEquals("10-minute", spanLabel(samples = 40))
    }

    @Test
    fun aFullWindowIsThirtyMinutes() {
        // 120 samples at 15 s. The label the screen always used to
        // print, now earned rather than assumed.
        assertEquals("30-minute", spanLabel(samples = 120))
    }

    @Test
    fun spansPastAnHourReadAsHours() {
        // 240 samples at 15 s is exactly one hour.
        assertEquals("1-hour", spanLabel(samples = 240))
        // 300 is 75 minutes, so it is one hour and fifteen minutes
        // rather than being rounded to a bare "1-hour".
        assertEquals("1-hour 15-minute", spanLabel(samples = 300))
    }

    @Test
    fun aSlowerSampleRateIsReportedHonestlyToo() {
        // The same sample count at half the rate is half the span, and
        // the label has to follow. A label computed from the count
        // alone would be wrong the moment a device batches its sensor
        // events, which is common under power saving.
        assertEquals("30-minute", spanLabel(samples = 120, sampleSeconds = 15f))
        assertEquals(
            "15-minute",
            spanLabel(samples = 120, sampleSeconds = 7.5f),
        )
    }
}
