package com.krafttools.app

import com.krafttools.app.ui.LEVEL_NEAR_DEG
import com.krafttools.app.ui.LEVEL_TOLERANCE_DEG
import com.krafttools.app.ui.Tilt
import com.krafttools.app.ui.bubbleOffset
import com.krafttools.app.ui.orientationOf
import com.krafttools.app.ui.pitchRoll
import com.krafttools.app.ui.tiltInstruction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * A spirit level is judged entirely on its tolerance and its scaling,
 * and both used to be magic numbers inside a composable. The bubble
 * bug this pins down was the worst of it: divide by a fixed 45 degrees
 * and the bubble clamps and stops responding at 20 degrees of tilt —
 * the instrument dies exactly when the user needs it most.
 */
class LevelMathTest {

    @Test
    fun aFlatPhoneIsLevel() {
        // Accelerometer flat on a table: gravity straight down -Z.
        val (pitch, roll) = pitchRoll(floatArrayOf(0f, 0f, 9.81f))
        assertEquals(0f, pitch, 0.01f)
        assertEquals(0f, roll, 0.01f)
        val t = Tilt(pitch, roll)
        assertTrue("a flat phone must read level", t.isLevel)
    }

    @Test
    fun toleranceIsOneDegree() {
        assertTrue(Tilt(0.9f, 0f).isLevel)
        assertTrue(!Tilt(1.1f, 0f).isLevel)
        // The boundary is strict: exactly at the tolerance is not level.
        assertTrue(!Tilt(LEVEL_TOLERANCE_DEG, 0f).isLevel)
    }

    @Test
    fun nearBandSitsOutsideTheLevelBand() {
        assertTrue(Tilt(0.5f, 0f).isLevel)
        assertTrue(Tilt(2f, 0f).isNear)
        assertTrue(!Tilt(2f, 0f).isLevel)
        assertTrue(!Tilt(LEVEL_NEAR_DEG + 0.5f, 0f).isNear)
    }

    @Test
    fun magnitudeIsTheAngleOffLevel() {
        // 3-4-5 triangle: exactly 5 degrees.
        val t = Tilt(3f, 4f)
        assertEquals(5f, t.magnitude, 1e-4f)
    }

    @Test
    fun magnitudeIsAlwaysPositive() {
        assertEquals(5f, Tilt(-3f, -4f).magnitude, 1e-4f)
    }

    @Test
    fun bubbleStaysInsideTheHousingAtAnyTilt() {
        // The bug: at 45 degrees and beyond the old scale saturated.
        for (pitch in -90..90 step 5) {
            for (roll in -90..90 step 5) {
                val (bp, br) = bubbleOffset(Tilt(pitch.toFloat(), roll.toFloat()))
                assertTrue("bubble escaped at $pitch/$roll: $bp,$br",
                    abs(bp) <= 1f && abs(br) <= 1f)
            }
        }
    }

    @Test
    fun bubbleRespondsAcrossTheWholeRange() {
        // The other half of the bug: it must not flatten out early
        // either. Distinct tilts must produce distinct positions.
        var previous = 0f
        for (deg in 1..40 step 2) {
            val (b, _) = bubbleOffset(Tilt(deg.toFloat(), 0f))
            assertTrue("bubble stopped responding at $deg degrees ($previous -> $b)",
                b > previous)
            previous = b
        }
    }

    @Test
    fun bubbleIsCenteredAtZero() {
        val (p, r) = bubbleOffset(Tilt(0f, 0f))
        assertEquals(0f, p, 0f)
        assertEquals(0f, r, 0f)
    }

    @Test
    fun bubblePreservesTheSignOfTheTilt() {
        assertTrue(bubbleOffset(Tilt(10f, 0f)).first > 0f)
        assertTrue(bubbleOffset(Tilt(-10f, 0f)).first < 0f)
        assertTrue(bubbleOffset(Tilt(0f, 10f)).second > 0f)
        assertTrue(bubbleOffset(Tilt(0f, -10f)).second < 0f)
    }

    @Test
    fun bubbleIsSymmetric() {
        val (a, _) = bubbleOffset(Tilt(12f, 0f))
        val (b, _) = bubbleOffset(Tilt(-12f, 0f))
        assertEquals(a, -b, 1e-6f)
    }

    @Test
    fun smallTiltsResolvePrecisely() {
        // A level is useless if half a degree does nothing. The
        // square-root response must still separate 0.2 from 0.5.
        val (a, _) = bubbleOffset(Tilt(0.2f, 0f))
        val (b, _) = bubbleOffset(Tilt(0.5f, 0f))
        assertTrue("half a degree must move the bubble", b > a * 1.5f)
    }

    @Test
    fun instructionIsNullWhenLevel() {
        assertNull(tiltInstruction(Tilt(0.2f, 0.1f)))
    }

    @Test
    fun oneAxisDominanceGetsADirectInstruction() {
        assertEquals("tilt right", tiltInstruction(Tilt(0f, 10f)))
        assertEquals("tilt left", tiltInstruction(Tilt(0f, -10f)))
        assertEquals("tilt forward", tiltInstruction(Tilt(10f, 0f)))
        assertEquals("tilt back", tiltInstruction(Tilt(-10f, 0f)))
    }

    @Test
    fun noInstructionWhenBothAxesAreComparable() {
        // A diagonal tilt has no single right answer, so say nothing
        // rather than guess wrong.
        assertNull(tiltInstruction(Tilt(10f, 10f)))
        assertNull(tiltInstruction(Tilt(10f, -8f)))
    }

    @Test
    fun pitchRollReadsSideways() {
        // Convention: pitch = atan2(-ax, ...), roll = atan2(ay, ...).
        // Gravity along +X (phone on its right edge) is therefore a
        // pitch of -90, not a roll — the two are easy to swap in a test
        // and the error is invisible until the bubble moves wrongly.
        val (p1, r1) = pitchRoll(floatArrayOf(9.81f, 0f, 0f))
        assertEquals(90f, abs(p1), 0.5f)
        assertEquals(0f, abs(r1), 0.5f)
        // Gravity along +Y (top edge up) is the roll.
        val (p2, r2) = pitchRoll(floatArrayOf(0f, 9.81f, 0f))
        assertEquals(0f, abs(p2), 0.5f)
        assertEquals(90f, abs(r2), 0.5f)
        // The left edge mirrors the right.
        val (p3, r3) = pitchRoll(floatArrayOf(-9.81f, 0f, 0f))
        assertEquals(90f, abs(p3), 0.5f)
        assertEquals(0f, abs(r3), 0.5f)
    }

    @Test
    fun faceUpIsDetected() {
        // The atan pair alone cannot tell these apart — both are
        // pitch 0, roll 0 — so the face has to come from the sign of Z.
        val up = orientationOf(floatArrayOf(0f, 0f, 9.81f))
        val down = orientationOf(floatArrayOf(0f, 0f, -9.81f))
        assertTrue("a table-flat phone is face up", up.faceUp)
        assertTrue("an upside-down phone is not", !down.faceUp)
        assertTrue("face-down is flagged useless", down.isUseless)
        assertTrue("!up.isUseless", !up.isUseless)
    }

    @Test
    fun faceUpSurvivesTilting() {
        // Rotating a face-up phone never flips the reported face until
        // it actually passes through vertical.
        for (deg in 0..80 step 10) {
            val rad = Math.toRadians(deg.toDouble())
            val g = floatArrayOf(
                (9.81 * Math.sin(rad)).toFloat(),
                0f,
                (9.81 * Math.cos(rad)).toFloat(),
            )
            assertTrue("face flipped at $deg degrees", orientationOf(g).faceUp)
        }
    }

    @Test
    fun pitchRollSurvivesAZeroVector() {
        // Some devices report a zero vector briefly on wake.
        val (p, r) = pitchRoll(floatArrayOf(0f, 0f, 0f))
        assertTrue("pitch must be finite, was $p", p.isFinite())
        assertTrue("roll must be finite, was $r", r.isFinite())
    }
}
