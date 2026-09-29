package com.krafttools.app

import com.krafttools.app.ui.fixAge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The age of the fix behind the compass's declination.
 *
 * `getLastKnownLocation` returns whatever the platform is holding,
 * which may be thirty seconds old or four years old. The screen said
 * "decl +4.2°" either way, so a correction computed for a position the
 * user has not visited in a decade was presented as today's. The
 * declination is still applied — it drifts about 0.1° per year, so a
 * decade-old value is usually still within a degree — but an
 * unauditable number is not a number, so the age is now stated.
 */
class FixAgeTest {

    private fun days(n: Int) = n * 24L * 60 * 60 * 1000

    @Test
    fun aFreshFixIsNotWorthTalkingAbout() {
        assertEquals("from a fix seconds old", fixAge(0))
        assertEquals("from a fix seconds old", fixAge(30_000))
    }

    @Test
    fun minutesHoursAndDaysEachGetTheirOwnWording() {
        assertEquals("from a 5-minute-old fix", fixAge(5 * 60_000))
        assertEquals("from a 3-hour-old fix", fixAge(3L * 3_600_000))
        assertEquals("from a 4-day-old fix", fixAge(days(4)))
    }

    @Test
    fun aStaleFixIsNamedAsStale() {
        // The case that matters. Six months is past the point where
        // "true north" is a fair description of what was computed.
        assertEquals("from a fix 6 months old", fixAge(days(182)))
    }

    @Test
    fun yearsArePluralised() {
        assertEquals("from a fix 1 year old", fixAge(days(400)))
        assertEquals("from a fix 4 years old", fixAge(days(365 * 4)))
    }

    @Test
    fun aFixFromTheFutureIsNotSilentlyAccepted() {
        // A device with a badly wrong clock, or a mock location, can
        // hand back a timestamp in the future. "seconds old" would be a
        // lie, so it is called out.
        assertEquals("from a future fix", fixAge(-60_000))
    }

    @Test
    fun theThresholdsDoNotLeaveGaps() {
        // Each band must hand over to the next without a value falling
        // between two branches, which is the classic way this kind of
        // helper silently produces "from a -1-day-old fix".
        val probes = longArrayOf(
            0, 59_999, 60_000, 119_999,
            60 * 60_000 - 1, 60 * 60_000, 24L * 3_600_000 - 1, 24L * 3_600_000,
            days(59), days(60), days(364), days(365), days(400),
        )
        for (age in probes) {
            val said = fixAge(age)
            assertTrue(
                "fixAge($age) produced $said",
                said.startsWith("from a ") || said == "from a future fix",
            )
            assertTrue(
                "fixAge($age) produced $said",
                !said.contains("-1") && !said.contains("null"),
            )
        }
    }
}
