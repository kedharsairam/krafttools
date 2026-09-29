package com.krafttools.app

import com.krafttools.app.ui.compassBearingError
import com.krafttools.app.ui.cardinal
import com.krafttools.app.ui.compassWrap
import com.krafttools.app.ui.trueBearing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Compass arithmetic is small and completely deterministic, which makes
 * it exactly the kind of code that accumulates quiet off-by-360 bugs.
 * These pin the wrapping rules, because a bearing that reads 359° when
 * the answer is 1° is worse than no bearing at all.
 */
class CompassMathTest {

    @Test
    fun wrapBringsAnyAngleIntoZeroToThreeSixty() {
        for (a in listOf(0f, 45f, 359.9f, 360f, 361f, 720f, -1f, -45f, -360f, -400f, 1000f)) {
            val w = compassWrap(a)
            assertTrue("$a wrapped to $w", w >= 0f && w < 360f)
        }
    }

    @Test
    fun wrapLeavesAnglesAlone() {
        assertEquals(0f, compassWrap(0f), 1e-4f)
        assertEquals(90f, compassWrap(90f), 1e-4f)
        assertEquals(180f, compassWrap(180f), 1e-4f)
    }

    @Test
    fun wrapCrossesNorthCorrectly() {
        // 359 -> 0 must move forward 1 degree, not back 359.
        assertEquals(0f, compassWrap(360f), 1e-4f)
        assertEquals(359.5f, compassWrap(359.5f), 1e-4f)
    }

    @Test
    fun cardinalsAreTheNearestOfTheEightPoints() {
        assertEquals("N", cardinal(0f))
        assertEquals("N", cardinal(10f))
        assertEquals("NE", cardinal(45f))
        assertEquals("E", cardinal(90f))
        assertEquals("SE", cardinal(135f))
        assertEquals("S", cardinal(180f))
        assertEquals("SW", cardinal(225f))
        assertEquals("W", cardinal(270f))
        assertEquals("NW", cardinal(315f))
    }

    @Test
    fun cardinalsSplitAtTheMidpoint() {
        // The boundary between N and NE is 22.5 degrees, not 45.
        assertEquals("N", cardinal(22f))
        assertEquals("NE", cardinal(23f))
        assertEquals("NE", cardinal(67f))
        assertEquals("E", cardinal(68f))
    }

    @Test
    fun cardinalsWrapAtNorth() {
        assertEquals("N", cardinal(359f))
        assertEquals("N", cardinal(-1f))
        assertEquals("N", cardinal(360f))
        // NW spans 292.5..337.5, so 350 is 12.5 degrees into N.
        assertEquals("NW", cardinal(337f))
        assertEquals("N", cardinal(338f))
        // Just either side of the N/NW boundary.
        assertEquals("NW", cardinal(300f))
        assertEquals("N", cardinal(350f))
    }

    @Test
    fun everyCardinalNameIsTwoLettersOrLess() {
        // These render inside a rotating dial; a long name would clip.
        for (a in 0 until 360 step 5) {
            assertTrue("cardinal($a) = ${cardinal(a.toFloat())} is too long",
                cardinal(a.toFloat()).length <= 2)
        }
    }

    @Test
    fun trueBearingAddsDeclination() {
        assertEquals(100f, trueBearing(90f, 10f), 1e-3f)
        assertEquals(80f, trueBearing(90f, -10f), 1e-3f)
        assertEquals(0f, trueBearing(350f, 10f), 1e-3f)
    }

    @Test
    fun trueBearingStaysInRangeForEveryDeclination() {
        // Declination runs roughly -30 to +30, but the model can return
        // more; the result must never escape the dial either way.
        for (az in 0 until 360 step 7) {
            for (decl in listOf(-180f, -90f, -30f, 0f, 30f, 90f, 180f)) {
                val t = trueBearing(az.toFloat(), decl)
                assertTrue("trueBearing($az, $decl) = $t", t >= 0f && t < 360f)
            }
        }
    }

    @Test
    fun bearingErrorIsSignedAndSmall() {
        // Positive means the needle is right of the locked bearing.
        assertEquals(10f, compassBearingError(100f, 90f), 1e-3f)
        assertEquals(-10f, compassBearingError(90f, 100f), 1e-3f)
        assertEquals(0f, compassBearingError(90f, 90f), 1e-3f)
    }

    @Test
    fun bearingErrorCrossesNorthTheShortWay() {
        // Locked at 350, needle at 10: ten degrees RIGHT, not 340 left.
        assertEquals(20f, compassBearingError(10f, 350f), 1e-3f)
        assertEquals(-20f, compassBearingError(350f, 10f), 1e-3f)
        assertEquals(5f, compassBearingError(0f, 355f), 1e-3f)
        assertEquals(-5f, compassBearingError(355f, 0f), 1e-3f)
    }

    @Test
    fun bearingErrorIsNeverMoreThanHalfATurn() {
        for (a in 0 until 360 step 3) {
            for (b in 0 until 360 step 3) {
                val e = compassBearingError(a.toFloat(), b.toFloat())
                assertTrue("error $e for $a vs $b", abs(e) <= 180.0001f)
            }
        }
    }

    @Test
    fun oppositeBearingsAreExactlyAHalfTurn() {
        assertEquals(180f, abs(compassBearingError(180f, 0f)), 1e-3f)
        assertEquals(180f, abs(compassBearingError(0f, 180f)), 1e-3f)
    }
}
