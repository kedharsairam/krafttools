package com.krafttools.app

import com.krafttools.app.ui.EARTH_FIELD_MAX_UT
import com.krafttools.app.ui.EARTH_FIELD_MIN_UT
import com.krafttools.app.ui.EmfVerdict
import com.krafttools.app.ui.FieldBaseline
import com.krafttools.app.ui.Schmitt
import com.krafttools.app.ui.SENSOR_CEILING_UT
import com.krafttools.app.ui.classifyDeviation
import com.krafttools.app.ui.deviationFrom
import com.krafttools.app.ui.emfCapabilityNote
import com.krafttools.app.ui.fieldMagnitude
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The EMF tool alarmed permanently wherever the ambient field sat
 * above its threshold, which IGRF-14 puts at up to 70 µT. These pin
 * the fix — threshold on deviation from a captured baseline, not on the
 * raw field — and the reference values come from IGRF-14 rather than
 * from whatever the code happened to assume.
 */
class EmfMathTest {

    // --- magnitude ---

    @Test
    fun magnitudeIsRotationInvariant() {
        // Turning the phone must not change the reading; that is what
        // makes a total-field magnitude the right headline.
        val base = fieldMagnitude(30f, 40f, 0f)
        assertEquals(50f, base, 1e-3f)
        // Same vector, different orientation.
        assertEquals(50f, fieldMagnitude(0f, 30f, 40f), 1e-3f)
        assertEquals(50f, fieldMagnitude(40f, 0f, 30f), 1e-3f)
        assertEquals(50f, fieldMagnitude(-30f, -40f, 0f), 1e-3f)
    }

    @Test
    fun magnitudeIsNeverNegative() {
        for (v in listOf(-90f, -1f, 0f, 1f, 90f, 600f)) {
            assertTrue("magnitude was negative", fieldMagnitude(v, v, v) >= 0f)
        }
    }

    @Test
    fun aZeroVectorIsZero() {
        assertEquals(0f, fieldMagnitude(0f, 0f, 0f), 0f)
    }

    // --- the reason the tool existed is now correct ---

    @Test
    fun earthsRangeIsWhereIgRfSaysItIs() {
        // IGRF-14: 22,071 nT minimum over the South Atlantic Anomaly,
        // up to about 70,000 nT at high latitude.
        assertEquals(22f, EARTH_FIELD_MIN_UT, 0.5f)
        assertEquals(70f, EARTH_FIELD_MAX_UT, 0.5f)
    }

    @Test
    fun aBaselineIsAcceptedOnlyInsideEarthsRange() {
        assertTrue(FieldBaseline(50f).isSane)
        assertTrue(FieldBaseline(22.5f).isSane)
        assertTrue(FieldBaseline(69f).isSane)
        // A magnet held against the phone is not a baseline.
        assertFalse(FieldBaseline(500f).isSane)
        assertFalse(FieldBaseline(0f).isSane)
    }

    @Test
    fun deviationCancelsGeography() {
        // The whole point: the same object reads the same deviation in
        // Ottawa and in Nairobi, which a raw threshold never could.
        val inCanada = deviationFrom(58f, 61f)
        val inKenya = deviationFrom(38f, 41f)
        assertEquals(3f, inCanada, 1e-4f)
        assertEquals(3f, inKenya, 1e-4f)
    }

    @Test
    fun deviationIsSigned() {
        assertTrue(deviationFrom(50f, 55f) > 0f)
        assertTrue(deviationFrom(50f, 45f) < 0f)
        assertEquals(0f, deviationFrom(50f, 50f), 0f)
    }

    // --- hysteresis ---

    @Test
    fun schmittDoesNotStutterOnNoise() {
        // The bug: comparing a boolean directly against the threshold,
        // so noise either side of it buzzed several times a second.
        val s = Schmitt()
        s.update(4f, tripUt = 5f)          // clear
        // Noise straddling the trip point for a second at 16.7 Hz.
        var crossings = 0
        var wasEngaged = false
        for (i in 0 until 50) {
            val noise = if (i % 2 == 0) 4.9f else 5.1f
            val on = s.update(noise, tripUt = 5f)
            if (on != wasEngaged) crossings++
            wasEngaged = on
        }
        assertEquals(
            "the alarm re-triggered $crossings times on noise",
            1,
            crossings,
        )
    }

    @Test
    fun schmittReleasesBelowItsHysteresisBand() {
        val s = Schmitt()
        s.update(10f, tripUt = 5f)
        assertTrue(s.engaged)
        // Still above the release point, so it stays engaged.
        s.update(4.5f, tripUt = 5f)
        assertTrue("released too early", s.engaged)
        // Below the release band it lets go.
        s.update(4f, tripUt = 5f)
        assertFalse(s.engaged)
    }

    @Test
    fun schmittTripsOnMagnitudeEitherSide() {
        val s = Schmitt()
        s.update(-12f, tripUt = 5f)
        assertTrue("a strong negative deviation must also trip", s.engaged)
    }

    @Test
    fun schmittResetsCleanly() {
        val s = Schmitt()
        s.update(10f, tripUt = 5f)
        s.reset()
        assertFalse(s.engaged)
    }

    // --- classification ---

    @Test
    fun verdictsFollowTheDeviation() {
        assertEquals(EmfVerdict.CLEAR, classifyDeviation(0.5f, tripUt = 10f))
        assertEquals(EmfVerdict.NEAR, classifyDeviation(5f, tripUt = 10f))
        assertEquals(EmfVerdict.STRONG, classifyDeviation(15f, tripUt = 10f))
    }

    @Test
    fun aVeryStrongReadingIsStillAStrongReading() {
        // Not "saturated — too strong to measure". A fridge magnet is
        // precisely what this tool exists to find, and answering with
        // "I cannot measure that" would be the wrong reply to the one
        // reading that matters.
        assertEquals(EmfVerdict.STRONG, classifyDeviation(9500f, tripUt = 10f))
        assertEquals(EmfVerdict.STRONG, classifyDeviation(600f, tripUt = 10f))
    }

    @Test
    fun theVerdictLadderOnlyEverMeansMore() {
        // Monotonic in magnitude: a bigger deviation is never a
        // quieter answer.
        var previous = EmfVerdict.CLEAR
        for (d in listOf(0f, 1f, 2f, 5f, 10f, 50f, 500f, 5000f)) {
            val v = classifyDeviation(d, tripUt = 10f)
            assertTrue(
                "a larger reading gave a weaker verdict at $d uT",
                v.ordinal >= previous.ordinal,
            )
            previous = v
        }
    }

    @Test
    fun verdictIsSymmetricInSign() {
        assertEquals(
            classifyDeviation(20f, tripUt = 10f),
            classifyDeviation(-20f, tripUt = 10f),
        )
    }

    @Test
    fun everyVerdictIsNamed() {
        for (v in EmfVerdict.entries) {
            assertTrue("${v.name} has no label", v.label.isNotBlank())
        }
    }

    // --- honesty about what the instrument can see ---

    @Test
    fun theCapabilityNoteSaysWhatItCannotSee() {
        val note = emfCapabilityNote().lowercase()
        // A user hunting for aluminium needs to be told, or the tool
        // will convince them there is nothing there.
        for (material in listOf("aluminium", "copper", "brass", "plastic")) {
            assertTrue(
                "the note never mentions $material",
                note.contains(material),
            )
        }
        assertTrue("the note should say what it does see", note.contains("iron"))
    }

    @Test
    fun theNoteDoesNotClaimTheFigure8Matters() {
        // The old advice was to wave a figure-8 to "calibrate". That
        // is a compass technique for hard-iron bias in the x/y axes;
        // it has no effect on a rotation-invariant magnitude.
        assertFalse(
            "the note reintroduces the figure-8 myth",
            emfCapabilityNote().contains("figure-8", ignoreCase = true),
        )
    }
}
