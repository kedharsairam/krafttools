package com.krafttools.app

import androidx.compose.ui.graphics.Color
import com.krafttools.app.ui.SPL_CEILING_DB
import com.krafttools.app.ui.SPL_FLOOR_DB
import com.krafttools.app.ui.SPL_TICKS
import com.krafttools.app.ui.SPL_ZONES
import com.krafttools.app.ui.formatBand
import com.krafttools.app.ui.zoneFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/** Hue in degrees, 0 = red, 120 = green, 240 = blue. */
private fun hueDegrees(c: Color): Float {
    val max = maxOf(c.red, c.green, c.blue)
    val min = minOf(c.red, c.green, c.blue)
    val delta = max - min
    if (delta < 1e-6f) return 0f
    val h = when (max) {
        c.red -> 60f * (((c.green - c.blue) / delta) % 6f)
        c.green -> 60f * (((c.blue - c.red) / delta) + 2f)
        else -> 60f * (((c.red - c.green) / delta) + 4f)
    }
    return (h + 360f) % 360f
}

/**
 * The sound meter's scale is the part a user trusts before they trust
 * the number. These pin the zone boundaries against the published
 * bands, because a meter that colors 75 dB "moderate" is worse than
 * no meter at all.
 */
class LevelScaleTest {

    @Test
    fun zonesCoverTheWholeScaleWithNoGaps() {
        // Walk the scale in 0.5 dB steps: every value must land in
        // exactly one zone. A gap would draw as uncolored track.
        var count = 0
        var db = SPL_FLOOR_DB
        while (db <= SPL_CEILING_DB) {
            val zone = zoneFor(db)
            assertTrue("$db dB landed in no zone", zone in SPL_ZONES)
            count++
            db += 0.5f
        }
        assertTrue(count > 100)
    }

    @Test
    fun zoneBoundariesAreContiguous() {
        for (i in 0 until SPL_ZONES.size - 1) {
            assertEquals(
                "zone ${SPL_ZONES[i].name} does not meet ${SPL_ZONES[i + 1].name}",
                SPL_ZONES[i].toDb,
                SPL_ZONES[i + 1].fromDb,
                1e-4f,
            )
        }
    }

    @Test
    fun scaleStartsAtTheFloorAndEndsAtTheCeiling() {
        assertEquals(SPL_FLOOR_DB, SPL_ZONES.first().fromDb, 1e-4f)
        assertEquals(SPL_CEILING_DB, SPL_ZONES.last().toDb, 1e-4f)
    }

    @Test
    fun knownLevelsLandInTheRightZone() {
        // Reference levels people actually look up.
        assertEquals("quiet", zoneFor(35f).name)
        assertEquals("quiet", zoneFor(49.9f).name)
        assertEquals("moderate", zoneFor(50f).name)
        assertEquals("moderate", zoneFor(60f).name)
        assertEquals("moderate", zoneFor(69.9f).name)
        assertEquals("loud", zoneFor(70f).name)
        assertEquals("loud", zoneFor(84.9f).name)
        assertEquals("harmful", zoneFor(85f).name)
        assertEquals("harmful", zoneFor(99.9f).name)
        assertEquals("damaging", zoneFor(100f).name)
    }

    @Test
    fun outOfRangeLevelsClampInsteadOfFailing() {
        assertEquals("quiet", zoneFor(-40f).name)
        assertEquals("damaging", zoneFor(400f).name)
    }

    @Test
    fun aNaNReadingDoesNotPaintTheMeterRed() {
        // A NaN from a faulting mic fails every comparison. It must
        // resolve to the quiet end, not fall through to "damaging" —
        // the meter screaming in a silent room is worse than useless.
        assertEquals("quiet", zoneFor(Float.NaN).name)
    }

    @Test
    fun silenceAndOutOfRangeClampToTheQuietEnd() {
        assertEquals("quiet", zoneFor(0f).name)
        assertEquals("quiet", zoneFor(-120f).name)
    }

    @Test
    fun overTheTopStaysDamaging() {
        assertEquals("damaging", zoneFor(120f).name)
        assertEquals("damaging", zoneFor(1000f).name)
    }

    @Test
    fun zonesGetMoreSeriousWithLoudness() {
        // Hue must rotate monotonically from cool to hot. Neither
        // per-channel nor luma is the right model: amber and orange
        // differ almost only in green, and a green and a cyan of equal
        // lightness look nothing alike. Hue is what the eye actually
        // reads off a warning scale.
        var previous = 361f
        for (z in SPL_ZONES) {
            val h = hueDegrees(z.color)
            assertTrue(
                "zone ${z.name} (hue $h) is not cooler-to-hotter than the " +
                    "zone before it (hue $previous)",
                h < previous,
            )
            previous = h
        }
    }

    @Test
    fun theScaleEndsAtRedAndStartsAtCyan() {
        assertTrue("scale does not start cool", hueDegrees(SPL_ZONES.first().color) > 150f)
        assertTrue("scale does not end hot", hueDegrees(SPL_ZONES.last().color) < 15f)
    }

    @Test
    fun everyZoneIsBrightEnoughToReadOnNearBlack() {
        // The house ground is #121214. A dark zone color would be a
        // band of nothing on the track.
        for (z in SPL_ZONES) {
            val luma = 0.299f * z.color.red + 0.587f * z.color.green +
                0.114f * z.color.blue
            assertTrue("zone ${z.name} is too dark to read: $luma", luma > 0.35f)
        }
    }

    @Test
    fun theMostSevereZoneIsRed() {
        // The one place a color carries meaning outright: red must mean
        // damaging, so it has to actually be red.
        val worst = SPL_ZONES.last().color
        assertTrue(
            "the damaging zone is not red enough: $worst",
            worst.red > 0.8f && worst.green < 0.5f && worst.blue < 0.5f,
        )
    }

    @Test
    fun everyZoneColorIsDistinct() {
        // Two zones sharing a color would make the scale ambiguous
        // exactly where reading it quickly matters most.
        for (i in 0 until SPL_ZONES.size) {
            for (j in i + 1 until SPL_ZONES.size) {
                assertTrue(
                    "${SPL_ZONES[i].name} and ${SPL_ZONES[j].name} share a color",
                    SPL_ZONES[i].color != SPL_ZONES[j].color,
                )
            }
        }
    }

    @Test
    fun ticksIncludeEveryZoneBoundary() {
        for (z in SPL_ZONES) {
            assertTrue(
                "${z.fromDb} dB is a zone boundary with no tick",
                SPL_TICKS.any { kotlin.math.abs(it - z.fromDb) < 1e-4f },
            )
        }
    }

    @Test
    fun ticksAreInsideTheScale() {
        for (t in SPL_TICKS) {
            assertTrue("tick $t is below the floor", t >= SPL_FLOOR_DB)
            assertTrue("tick $t is above the ceiling", t <= SPL_CEILING_DB)
        }
    }

    @Test
    fun bandLabelsAreCompact() {
        assertEquals("63", formatBand(63f))
        assertEquals("125", formatBand(125f))
        assertEquals("500", formatBand(500f))
        assertEquals("1k", formatBand(1000f))
        assertEquals("2k", formatBand(2000f))
        assertEquals("8k", formatBand(8000f))
        // A fractional kilohertz keeps one decimal rather than
        // printing "1.5k" and "1k" the same width.
        assertEquals("1.5k", formatBand(1500f))
        for (hz in listOf(63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f)) {
            assertTrue(
                "label for $hz is too wide for a band",
                formatBand(hz).length <= 4,
            )
        }
    }

    @Test
    fun bandLabelsRoundRatherThanTruncate() {
        // Truncation would label 1600 Hz as "1k" and quietly lie.
        assertEquals("1.6k", formatBand(1600f))
        assertTrue(formatBand(2400f).contains("2.4"))
    }
}
