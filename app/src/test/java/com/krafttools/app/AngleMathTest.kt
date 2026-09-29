package com.krafttools.app

import com.krafttools.app.ui.downhillAzimuth
import com.krafttools.app.ui.snapTo
import com.krafttools.app.ui.tiltFromFlat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * A protractor that under-reads by 26 degrees is not a protractor.
 * These pin the correct angle against exact vectors: for a tilt of
 * theta at azimuth phi, the gravity vector is
 * g(sin(theta)cos(phi), sin(theta)sin(phi), cos(theta)), and the
 * diagonal cases reduce to exact values.
 */
class AngleMathTest {

    private val g = 9.80665

    /**
     * An ACCELEROMETER reading for a tilt of [thetaDeg] at azimuth
     * [phiDeg], where phi = 0 means the RIGHT edge is low.
     *
     * This was a gravity vector, not an accelerometer reading, and that
     * is why it ratified a 180-degree error. An accelerometer measures
     * the specific force a = -g, so the horizontal components are
     * negated relative to gravity while Z stays positive (a phone lying
     * screen-up on a table reads +g on Z, which is the convention the
     * app uses everywhere). Feeding the gravity vector to a function
     * that expects the accelerometer reading puts two sign errors in
     * opposition, and they cancel in the assertion.
     */
    private fun accelAt(thetaDeg: Float, phiDeg: Float): FloatArray {
        val t = Math.toRadians(thetaDeg.toDouble())
        val p = Math.toRadians(phiDeg.toDouble())
        val s = sin(t)
        return floatArrayOf(
            (-g * s * cos(p)).toFloat(),
            (-g * s * sin(p)).toFloat(),
            (g * cos(t)).toFloat(),
        )
    }

    @Test
    fun aFlatPhoneReadsZero() {
        assertEquals(0f, tiltFromFlat(floatArrayOf(0f, 0f, g.toFloat())), 0.01f)
    }

    @Test
    fun aPhoneOnItsEdgeReadsNinety() {
        assertEquals(90f, tiltFromFlat(floatArrayOf(g.toFloat(), 0f, 0f)), 0.01f)
        assertEquals(90f, tiltFromFlat(floatArrayOf(0f, g.toFloat(), 0f)), 0.01f)
    }

    @Test
    fun singleAxisTiltsAreExact() {
        // The old hypot(pitch,roll) happened to be right here, so these
        // guard against a regression in the other direction.
        for (deg in listOf(10f, 30f, 45f, 60f, 80f)) {
            val a = accelAt(deg, 0f)
            assertEquals("$deg deg on X", deg, tiltFromFlat(a), 0.05f)
            val b = accelAt(deg, 90f)
            assertEquals("$deg deg on Y", deg, tiltFromFlat(b), 0.05f)
        }
    }

    @Test
    fun diagonalTiltsAreExact() {
        // The bug: hypot(pitch,roll) read 42.4 for 45 and 63.6 for 90.
        for (theta in listOf(15f, 30f, 45f, 60f, 75f, 90f)) {
            for (phi in listOf(0f, 30f, 45f, 60f, 90f, 135f, 200f)) {
                val a = accelAt(theta, phi)
                assertEquals(
                    "true tilt $theta at azimuth $phi",
                    theta,
                    tiltFromFlat(a),
                    0.1f,
                )
            }
        }
    }

    @Test
    fun theOldFormulaWouldHaveFailedThese() {
        // Documents the regression this replaced, so it cannot be
        // reintroduced thinking it is equivalent.
        val a = accelAt(90f, 45f)
        val ax = a[0].toDouble()
        val ay = a[1].toDouble()
        val az = a[2].toDouble()
        val pitch = Math.toDegrees(
            kotlin.math.atan2(-ax, kotlin.math.hypot(ay, az)),
        )
        val roll = Math.toDegrees(kotlin.math.atan2(ay, kotlin.math.hypot(ax, az)))
        val old = kotlin.math.hypot(pitch, roll)
        assertEquals(90f, tiltFromFlat(a), 0.1f)
        assertTrue(
            "the old composition should still be wrong here, was $old",
            old < 70.0,
        )
    }

    @Test
    fun tiltIsAlwaysWithinNinety() {
        // atan2 can exceed 90 for a face-down device; a protractor has
        // no such reading, so it clamps rather than showing 180.
        val faceDown = floatArrayOf(0f, 0f, -g.toFloat())
        val t = tiltFromFlat(faceDown)
        assertTrue("got $t", t in 0f..90f)
    }

    @Test
    fun theAngleIsIndependentOfAzimuth() {
        // Rotating the phone about the vertical must not change how far
        // from flat it is — only which way downhill points.
        val base = tiltFromFlat(accelAt(50f, 0f))
        for (phi in listOf(15f, 45f, 90f, 180f, 270f)) {
            assertEquals(
                "azimuth $phi changed the tilt",
                base,
                tiltFromFlat(accelAt(50f, phi)),
                0.05f,
            )
        }
    }

    // --- downhill azimuth, the number a needle can actually show ---

    @Test
    fun downhillPointsWhereGravityPulls() {
        // Each case names the physical state, because the sign of an
        // accelerometer reading is exactly what is easy to get wrong.
        // RIGHT EDGE LOW: the device +X axis tips down, so gravity has a
        // +X component in device coordinates and a = -g has a -X one.
        assertEquals(
            "right edge low means downhill is to the right",
            0f,
            downhillAzimuth(floatArrayOf(-g.toFloat(), 0f, 0f)),
            0.5f,
        )
        // TOP EDGE DOWN: device +Y tips down, so a_y is positive.
        assertEquals(
            "top edge down means downhill is toward the bottom",
            90f,
            downhillAzimuth(floatArrayOf(0f, -g.toFloat(), 0f)),
            0.5f,
        )
        assertEquals(
            "left edge low means downhill is to the left",
            180f,
            downhillAzimuth(floatArrayOf(g.toFloat(), 0f, 0f)),
            0.5f,
        )
        assertEquals(
            "top edge up means downhill is toward the top",
            270f,
            downhillAzimuth(floatArrayOf(0f, g.toFloat(), 0f)),
            0.5f,
        )
    }

    @Test
    fun theOppositeTiltGivesTheOppositeDirection() {
        // A 180-degree error in azimuth is invisible if every test uses
        // one sign. Flipping the tilt must flip the answer.
        for (phi in listOf(0f, 30f, 90f, 200f, 300f)) {
            val a = accelAt(40f, phi)
            val b = accelAt(40f, phi + 180f)
            val first = downhillAzimuth(a)
            val second = downhillAzimuth(b)
            val delta = kotlin.math.abs(
                ((first - second + 540f) % 360f) - 180f,
            )
            assertEquals(
                "at phi=$phi the two tilts gave $first and $second",
                180f,
                delta,
                0.5f,
            )
        }
    }

    @Test
    fun downhillAzimuthSpansTheFullCircle() {
        for (phi in listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)) {
            val a = downhillAzimuth(accelAt(50f, phi))
            assertTrue("azimuth $phi produced $a", a in 0f..360f)
        }
    }

    @Test
    fun aFlatPhoneHasNoPreferredDirection() {
        // No downhill to point at; the needle should hold still rather
        // than spin on sensor noise.
        assertEquals(0f, downhillAzimuth(floatArrayOf(0f, 0f, g.toFloat())), 0f)
    }

    @Test
    fun aPurePitchPointsRightNotNowhere() {
        // This is the case the old horizontal edge could not draw. A
        // 45 degree PITCH has roll of exactly zero, so the old widget
        // drew a perfectly level line while the header read 45. The
        // needle direction is 0 = right, because the right edge is the
        // one that dropped — a definite direction, not "no direction".
        val a = accelAt(45f, 0f)
        assertEquals(0f, downhillAzimuth(a), 0.5f)
    }

    @Test
    fun theNeedleSeparatesTheTwoTiltsTheOldLineCouldNot() {
        // Pitch and roll that produce the same old line must produce
        // different needle directions. Under the old roll-only drawing
        // these two were indistinguishable.
        val purePitch = accelAt(45f, 0f)
        val pureRoll = accelAt(45f, 90f)
        assertTrue(
            "pitch and roll drew the same",
            kotlin.math.abs(downhillAzimuth(purePitch) - downhillAzimuth(pureRoll)) > 45f,
        )
    }

    // --- snapping ---

    @Test
    fun snapPullsExactGraduationsToThemselves() {
        for (v in listOf(0f, 45f, 90f, 135f, 180f)) {
            val r = snapTo(v)
            assertTrue("$v should snap", r.snapped)
            assertEquals(v, r.value, 1e-4f)
        }
    }

    @Test
    fun snapLeavesAnglesOutsideTheBand() {
        val r = snapTo(20f)
        assertFalse(r.snapped)
        assertEquals(20f, r.value, 1e-4f)
    }

    @Test
    fun theSnapBandIsTwoDegreesAndInclusive() {
        assertTrue(snapTo(1.9f).snapped)
        assertTrue(snapTo(2.0f).snapped)
        assertFalse(snapTo(2.1f).snapped)
    }

    @Test
    fun snapWorksOnEitherSideOfAGraduation() {
        assertEquals(0f, snapTo(-1.5f).value, 1e-4f)
        assertEquals(45f, snapTo(46.5f).value, 1e-4f)
        assertEquals(90f, snapTo(88.5f).value, 1e-4f)
    }

    @Test
    fun aZeroStepIsRefusedRatherThanDividingByZero() {
        val r = snapTo(33f, step = 0f)
        assertFalse(r.snapped)
        assertEquals(33f, r.value, 1e-4f)
    }
}
