package com.krafttools.app

import com.krafttools.app.ui.LEVEL_NEAR_DEG
import com.krafttools.app.ui.LEVEL_TOLERANCE_DEG
import com.krafttools.app.ui.Tilt
import com.krafttools.app.ui.bubbleOffset
import com.krafttools.app.ui.orientationOf
import com.krafttools.app.ui.pitchRoll
import com.krafttools.app.ui.residualTilt
import com.krafttools.app.ui.tiltInstruction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A spirit level is judged entirely on its tolerance and its scaling,
 * and both used to be magic numbers inside a composable. Four defects
 * are pinned here, all of which shipped green:
 *
 *  - `Tilt.magnitude` was `hypot(pitch, roll)`, which is exact on a
 *    single axis and 26° low on a 90° diagonal.
 *  - `tiltInstruction` paired the two axis angles with the wrong
 *    instruction words, so it was 90° out: a low right edge was called
 *    a forward tilt.
 *  - The bubble was drawn on the LOW side, which is a plumb bob, not a
 *    vial.
 *  - "Calibrate" de-rotated by pitch and roll, which is not the
 *    rotation that maps the zero vector onto vertical.
 */
class LevelMathTest {

    private val g = 9.80665

    /**
     * A Tilt built the way the app builds it: from the vector.
     *
     * The total angle is NOT derivable from the two axis angles — they
     * are rotations about different axes, and recovering it from them
     * is out by up to 20°. So every test that wants a particular
     * magnitude has to say which sample produces it, which is what
     * building from a vector does. Constructing `Tilt(3f, 4f)` by hand
     * and expecting a meaningful magnitude is the mistake this whole
     * test class was making.
     */
    private fun tiltOf(
        totalDeg: Float,
        azimuthDeg: Float = 0f,
    ): Tilt {
        val t = Math.toRadians(totalDeg.toDouble())
        val p = Math.toRadians(azimuthDeg.toDouble())
        val a = sampleAt(totalDeg, azimuthDeg)
        val o = orientationOf(a)
        return Tilt(o.pitchDeg, o.rollDeg, totalDeg = o.totalDeg)
    }

    private fun flat() = floatArrayOf(0f, 0f, g.toFloat())

    // --- level and near bands ---

    @Test
    fun aFlatPhoneIsLevel() {
        val (pitch, roll) = pitchRoll(flat())
        assertEquals(0f, pitch, 0.01f)
        assertEquals(0f, roll, 0.01f)
        assertTrue("a flat phone must read level", tiltOf(0f).isLevel)
    }

    @Test
    fun toleranceIsOneDegree() {
        assertTrue(tiltOf(0.9f).isLevel)
        assertTrue(!tiltOf(1.1f).isLevel)
    }

    @Test
    fun theToleranceBoundaryIsStrict() {
        // Exactly at the tolerance is NOT level. Asserted on the
        // comparison rather than through a built sample: the total now
        // comes out of atan2, and 1 degree of tilt arrives as
        // 0.99999994 in single precision, so an exact-boundary
        // assertion through that path would be testing float rounding.
        assertTrue(
            "1.0 degrees must not read level",
            !Tilt(1f, 0f, totalDeg = LEVEL_TOLERANCE_DEG).isLevel,
        )
        assertTrue(
            "just inside the tolerance must read level",
            Tilt(1f, 0f, totalDeg = LEVEL_TOLERANCE_DEG - 0.01f).isLevel,
        )
    }

    @Test
    fun nearBandSitsOutsideTheLevelBand() {
        assertTrue(tiltOf(0.5f).isLevel)
        assertTrue(tiltOf(2f).isNear)
        assertTrue(!tiltOf(2f).isLevel)
        assertTrue(!tiltOf(LEVEL_NEAR_DEG + 0.5f).isNear)
    }

    // --- the total angle ---

    @Test
    fun magnitudeIsTheAngleOffLevel() {
        // A single axis: unambiguous, and the case hypot() got right.
        assertEquals(3f, tiltOf(3f).magnitude, 0.01f)
        assertEquals(30f, tiltOf(30f).magnitude, 0.01f)
        assertEquals(89f, tiltOf(89f).magnitude, 0.01f)
    }

    @Test
    fun magnitudeIsTheTrueAngleOnADiagonal() {
        // The total does not depend on the azimuth, whatever the two
        // axis angles happen to be.
        for (azimuth in listOf(0f, 17f, 45f, 90f, 200f, 315f)) {
            for (total in listOf(0.5f, 5f, 30f, 60f, 88f)) {
                assertEquals(
                    "total=$total azimuth=$azimuth",
                    total,
                    tiltOf(total, azimuth).magnitude,
                    0.02f,
                )
            }
        }
    }

    @Test
    fun theTwoAxisAnglesVaryWithAzimuthWhileTheTotalDoesNot() {
        // Why hypot could never be the answer: at a fixed total tilt the
        // axis angles change completely with the direction, but the
        // total does not.
        val a = tiltOf(60f, 0f)
        val b = tiltOf(60f, 45f)
        assertNotEquals(a.pitchDeg, b.pitchDeg)
        assertEquals(a.magnitude, b.magnitude, 0.01f)
    }

    @Test
    fun magnitudeIsUnsigned() {
        // Tilting "the other way" on each axis gives the same total.
        assertEquals(tiltOf(20f, 0f).magnitude, tiltOf(20f, 180f).magnitude, 0.01f)
        assertEquals(tiltOf(20f, 90f).magnitude, tiltOf(20f, 270f).magnitude, 0.01f)
    }

    // --- the instruction ---

    @Test
    fun oneAxisDominanceGetsADirectInstruction() {
        // pitchDeg is built from the device X axis, which runs
        // left/right across the screen. rollDeg is built from the Y
        // axis, which runs bottom-to-top and is therefore a
        // forward/back move. The names are the standard's, not the
        // phone's, and they are the opposite of what they suggest —
        // which is how the old mapping ended up 90° out.
        assertEquals("tilt right", tiltInstruction(tiltOf(8f, 0f)))
        assertEquals("tilt left", tiltInstruction(tiltOf(8f, 180f)))
        // Azimuth 90 puts the TOP edge down, so the fix is to raise it:
        // a backward tilt. Azimuth 270 is the mirror.
        assertEquals("tilt back", tiltInstruction(tiltOf(8f, 90f)))
        assertEquals("tilt forward", tiltInstruction(tiltOf(8f, 270f)))
    }

    @Test
    fun noInstructionWhenBothAxesAreComparable() {
        // A diagonal tilt has no single correct phrase, so say nothing
        // rather than guess wrong.
        assertNull(tiltInstruction(tiltOf(10f, 45f)))
        assertNull(tiltInstruction(tiltOf(10f, 20f)))
    }

    @Test
    fun noInstructionWhenLevel() {
        assertNull(tiltInstruction(tiltOf(0.2f)))
    }

    @Test
    fun theInstructionAgreesWithTheBubble() {
        // The two must never contradict: if the tool says "tilt right"
        // the bubble had better be on the left.
        for (azimuth in listOf(0f, 180f)) {
            val t = tiltOf(8f, azimuth)
            val phrase = tiltInstruction(t) ?: continue
            val (dx, _) = bubbleOffset(t)
            if (phrase == "tilt right") {
                assertTrue("tilt right but bubble on the right", dx < 0f)
            }
            if (phrase == "tilt left") {
                assertTrue("tilt left but bubble on the left", dx > 0f)
            }
        }
    }

    // --- the bubble ---

    @Test
    fun theBubbleMovesToTheHighEdgeNotTheLowOne() {
        // An air bubble rises to the highest point of the vial. A low
        // right edge must put the bubble on the LEFT.
        //
        // The old code drew it on the low side: a plumb bob, not a
        // vial, while the KDoc claimed it matched "any real vial".
        assertTrue(
            "right edge low must put the bubble left",
            bubbleOffset(tiltOf(10f, 0f)).first < 0f,
        )
        assertTrue(
            "left edge low must put the bubble right",
            bubbleOffset(tiltOf(10f, 180f)).first > 0f,
        )
        // "Top edge down" means the BOTTOM edge is the high one, so the
        // bubble goes to the bottom of the screen (screen y is down).
        assertTrue(
            "top edge down means the bottom is high, so bubble is low",
            bubbleOffset(tiltOf(10f, 90f)).second > 0f,
        )
        assertTrue(
            "top edge up means the top is high, so bubble is high",
            bubbleOffset(tiltOf(10f, 270f)).second < 0f,
        )
    }

    @Test
    fun smallTiltsResolvePrecisely() {
        // A level is useless if half a degree does nothing. The
        // square-root response must still separate 0.2° from 0.5°.
        val (a, _) = bubbleOffset(tiltOf(0.2f))
        val (b, _) = bubbleOffset(tiltOf(0.5f))
        assertTrue("half a degree must move the bubble", abs(b) > abs(a) * 1.5f)
    }

    @Test
    fun bubbleRespondsAcrossTheWholeRange() {
        // The other half of the old bug: it must not flatten out early.
        // It is meant to saturate at fullScale — that is what keeps the
        // bubble inside the housing — so responsiveness is asserted up
        // to the knee and the bound is asserted beyond it.
        var previous = 0f
        for (deg in 1..40 step 2) {
            val (b, _) = bubbleOffset(tiltOf(deg.toFloat()))
            assertTrue(
                "bubble stopped responding at $deg degrees " +
                    "($previous -> $b)",
                abs(b) > abs(previous),
            )
            previous = b
        }
        // Past full scale it pins at the rim rather than running off.
        for (deg in listOf(50f, 70f, 89f)) {
            assertEquals(
                "bubble must stay inside the housing at $deg",
                1f,
                abs(bubbleOffset(tiltOf(deg)).first),
                1e-4f,
            )
        }
    }

    @Test
    fun theBubbleStaysInsideTheHousingAtEveryAngle() {
        // The reason for the square-root response: a linear one clamps
        // at 45° and the instrument dies when the user needs it.
        for (deg in listOf(1f, 10f, 30f, 45f, 60f, 89f)) {
            val (dx, dy) = bubbleOffset(tiltOf(deg, 37f))
            assertTrue("dx out of range at $deg: $dx", abs(dx) <= 1f)
            assertTrue("dy out of range at $deg: $dy", abs(dy) <= 1f)
        }
    }

    @Test
    fun bubbleIsSymmetric() {
        val (a, _) = bubbleOffset(tiltOf(12f))
        val (b, _) = bubbleOffset(tiltOf(12f, 180f))
        assertEquals(a, -b, 1e-5f)
    }

    @Test
    fun aLevelPhoneHasNoBubbleOffset() {
        val (dx, dy) = bubbleOffset(tiltOf(0f))
        assertEquals(0f, dx, 1e-6f)
        assertEquals(0f, dy, 1e-6f)
    }

    // --- calibration ---

    @Test
    fun calibratingAFlatSurfaceReadsZero() {
        assertEquals(0f, residualTilt(flat(), flat()), 0.001f)
    }

    @Test
    fun calibrationRemovesTheOffsetItCapturedExactly() {
        // The old version de-rotated by pitch and roll, which is not
        // the rotation that maps the zero vector onto vertical, so
        // calibrating on a surface 1.5 degrees off still read 1.5
        // degrees off — the calibration did nothing.
        for (zeroDeg in listOf(0f, 1.5f, 5f, 12f)) {
            for (azimuth in listOf(0f, 40f, 90f, 200f)) {
                val zv = sampleAt(zeroDeg, azimuth)
                assertEquals(
                    "zero=$zeroDeg azimuth=$azimuth",
                    0f,
                    residualTilt(zv, zv),
                    0.001f,
                )
            }
        }
    }

    @Test
    fun calibrationRespondsLinearlyAwayFromTheZero() {
        val z = Math.toRadians(3.0)
        val zero = floatArrayOf(
            (-g * sin(z)).toFloat(),
            0f,
            (g * cos(z)).toFloat(),
        )
        for (extra in listOf(0.5f, 1f, 2f, 5f, 10f, 30f)) {
            val t = Math.toRadians(3.0 + extra)
            val a = floatArrayOf(
                (-g * sin(t)).toFloat(),
                0f,
                (g * cos(t)).toFloat(),
            )
            assertEquals(
                "extra=$extra",
                extra,
                residualTilt(a, zero),
                0.01f,
            )
        }
    }

    @Test
    fun calibrationStillRespondsAtADiagonalZero() {
        // The case the old de-rotation got wrong: a zero captured at
        // 40 degrees of azimuth.
        val t = Math.toRadians(2.0)
        val p = Math.toRadians(40.0)
        val zero = floatArrayOf(
            (-g * sin(t) * cos(p)).toFloat(),
            (-g * sin(t) * sin(p)).toFloat(),
            (g * cos(t)).toFloat(),
        )
        assertEquals(0f, residualTilt(zero, zero), 0.001f)
        val t2 = Math.toRadians(7.0)
        val away = floatArrayOf(
            (-g * sin(t2) * cos(p)).toFloat(),
            (-g * sin(t2) * sin(p)).toFloat(),
            (g * cos(t2)).toFloat(),
        )
        // The residual is the angle between the two orientations, and
        // 2 -> 7 degrees along the same azimuth is exactly that.
        assertEquals(5f, residualTilt(away, zero), 0.01f)
    }

    @Test
    fun anUncalibratedPhoneReportsItsRawTilt() {
        // With a vertical reference, the residual is just the tilt.
        // This is the path taken when the user has never calibrated,
        // and it was broken: the reference used to be the sample
        // itself, so the answer was always 0 and an uncalibrated spirit
        // level read "level" at any angle.
        val vertical = floatArrayOf(0f, 0f, g.toFloat())
        for (deg in listOf(0f, 0.5f, 1f, 10f, 45f, 80f)) {
            assertEquals(deg, residualTilt(sampleAt(deg), vertical), 0.01f)
        }
    }

    /** An accelerometer sample at a known total tilt and azimuth. */
    private fun sampleAt(totalDeg: Float, azimuthDeg: Float = 0f): FloatArray {
        val t = Math.toRadians(totalDeg.toDouble())
        val p = Math.toRadians(azimuthDeg.toDouble())
        return floatArrayOf(
            (-g * sin(t) * cos(p)).toFloat(),
            (-g * sin(t) * sin(p)).toFloat(),
            (g * cos(t)).toFloat(),
        )
    }
}
