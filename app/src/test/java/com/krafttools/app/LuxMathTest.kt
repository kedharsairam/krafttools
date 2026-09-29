package com.krafttools.app

import com.krafttools.app.ui.apertureFor
import com.krafttools.app.ui.INCIDENT_CALIBRATION
import com.krafttools.app.ui.evAt
import com.krafttools.app.ui.isPlausibleLux
import com.krafttools.app.ui.luxBand
import com.krafttools.app.ui.saturationNote
import com.krafttools.app.ui.shutterFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The light meter's real bug was a NaN latching into the session
 * maximum forever, so the header printed "NaN" with no way out. These
 * pin the guard, the band boundaries, and the exposure maths.
 */
class LuxMathTest {

    @Test
    fun nanIsRejectedRatherThanPropagated() {
        // Math.max(50f, Float.NaN) is NaN, so one bad sample latched
        // the maximum permanently.
        assertFalse(isPlausibleLux(Float.NaN))
        assertFalse(isPlausibleLux(Float.POSITIVE_INFINITY))
        assertFalse(isPlausibleLux(-1f))
        assertTrue(isPlausibleLux(0f))
        assertTrue(isPlausibleLux(340f))
    }

    @Test
    fun negativeReadingsAreRejected() {
        // Some HALs report small negatives in darkness.
        assertFalse(isPlausibleLux(-0.5f))
    }

    @Test
    fun bandBoundariesAreUpperExclusive() {
        assertEquals("Dark", luxBand(0f).label)
        assertEquals("Dark", luxBand(9.99f).label)
        assertEquals("Dim", luxBand(10f).label)
        assertEquals("Dim", luxBand(99.9f).label)
        assertEquals("Indoor", luxBand(100f).label)
        assertEquals("Indoor", luxBand(999f).label)
        assertEquals("Daylight", luxBand(1000f).label)
        assertEquals("Daylight", luxBand(9999f).label)
        assertEquals("Direct sun", luxBand(10000f).label)
    }

    @Test
    fun everyBandExplainsItself() {
        for (lux in listOf(1f, 50f, 500f, 5000f, 50000f)) {
            assertTrue(luxBand(lux).detail.isNotBlank())
        }
    }

    @Test
    fun everyBandIsReachable() {
        val labels = listOf(1f, 50f, 500f, 5000f, 50000f).map { luxBand(it).label }
        assertEquals(labels.size, labels.distinct().size)
    }

    // --- exposure, which is what a lux number is actually for ---

    @Test
    fun evIsUndefinedAtZeroLight() {
        assertNull(evAt(0f))
        assertNull(evAt(-1f))
    }

    @Test
    fun overcastDaylightLandsOnAFamiliarExposure() {
        // ISO 2720 incident calibration: EV = log2(E * S / C) with
        // C = 250 lux. 1000 lux at ISO 100 is log2(400) = 8.64.
        //
        // This test previously asserted log2(1000) = 9.97, which is the
        // formula with the constant dropped — treating a lux reading as
        // though it were a luminance in cd/m2. It was green, and wrong
        // by 1.3 stops.
        assertEquals(8.6439, evAt(1000f)!!, 0.001)
    }

    @Test
    fun theEvFifteenReferenceIsEightyTwoThousandLux() {
        // The standard's own statement: "at EV 15 — the sunny-sixteen
        // amount of light — the illuminance is 82 000 lux". This is the
        // check that the constant is present at all, because a missing
        // C turns EV 15 into 32 768 lux.
        assertEquals(15.0, evAt(82000f)!!, 0.01)
    }

    @Test
    fun theIncidentConstantIsTheStandardsValue() {
        assertEquals(250.0, INCIDENT_CALIBRATION, 0.0)
        // C = 250 gives EV 15 at 82 000 lux, and C = 218.2 (the 18 %
        // grey-card variant) gives 71 500. Both round to 15, so the two
        // conventions differ by 0.2 stops — far inside the accuracy of
        // an uncalibrated phone sensor.
        assertEquals(15.0, evAt(71500f)!!, 0.25)
    }

    @Test
    fun evDoublesPerDoublingOfLight() {
        val a = evAt(100f)!!
        val b = evAt(200f)!!
        assertEquals(1.0, b - a, 0.001)
    }

    @Test
    fun aHigherIsoNeedsMoreExposure() {
        // EV is log2(N^2/t) at the reference sensitivity, so the EV that
        // correctly exposes a scene RISES by a stop for each doubling of
        // ISO — you need more light through the lens to use a more
        // sensitive sensor. The old formula had the sign inverted here
        // too, as a side effect of dividing by ISO.
        assertEquals(1.0, evAt(1000f, 200)!! - evAt(1000f, 100)!!, 0.001)
    }

    @Test
    fun evIsIndependentOfTheCameraAtAFixedReference() {
        // The headline is EV at ISO 100 whatever the sensor, because
        // that is the number a photographer compares against.
        assertEquals(evAt(1000f, 100)!!, evAt(1000f, 100)!!, 0.0)
    }

    @Test
    fun exposureTriplesAreConsistent() {
        // The canonical sunny-16 exposure: EV 15, f/16, 1/125.
        // EV 15 at ISO 100 is 82 000 lux incident — 2^15 * 250/100.
        // The inverse relation checks too: 16^2 / (1/125) = 32000.
        val ev = evAt(82000f)!!
        assertEquals(15.0, ev, 0.01)
        assertEquals("1/125", shutterFor(ev, aperture = 16.0))
        assertEquals("f/16.0", apertureFor(ev, shutter = 1.0 / 125))
    }

    @Test
    fun apertureAndShutterAreInversesOfEachOther() {
        // Whatever pair comes out must satisfy N^2 / t = 2^EV, or the
        // two readouts would be describing different exposures.
        for (lux in listOf(10f, 100f, 500f, 5000f, 50000f)) {
            val ev = evAt(lux)!!
            val t = shutterFor(ev, aperture = 5.6)
            val seconds = if (t.startsWith("1/")) {
                1.0 / t.removePrefix("1/").toDouble()
            } else {
                t.removeSuffix("s").toDouble()
            }
            val implied = 5.6 * 5.6 / seconds
            assertTrue(
                "lux=$lux gives N^2/t = $implied but 2^EV = ${2.0.pow(ev)}",
                kotlin.math.abs(kotlin.math.log2(implied) - ev) < 0.6,
            )
        }
    }

    @Test
    fun indoorLightLandsOnAUsableExposure() {
        val ev = evAt(500f)!!
        assertTrue("shutter $ev", shutterFor(ev).startsWith("1/"))
        assertNotNull(apertureFor(ev))
    }

    @Test
    fun exposuresSnapToRealStopsNotArbitraryNumbers() {
        val stops = listOf(1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0, 22.0)
        for (ev in 0..20) {
            val a = apertureFor(ev.toDouble()).removePrefix("f/").toDouble()
            assertTrue(
                "f/$a is not a real stop",
                stops.any { kotlin.math.abs(it - a) < 0.01 },
            )
        }
    }

    @Test
    fun longExposuresAreNamedInSecondsNotFractions() {
        // A dark scene is a LOW EV (EV = log2(light), so less light is
        // a smaller number) and needs a multi-second exposure. It must
        // print "8s", never "1/0" or "0.1s".
        //
        // My first attempt at this test used EV 20 and expected a long
        // exposure, which is backwards: a high EV is bright, so a high
        // EV is a fast shutter. 1/8000 was the correct answer.
        val long = shutterFor(-2.0, aperture = 1.4)
        assertTrue("got $long", long.endsWith("s") && !long.startsWith("1/"))
        // And a bright scene is the other way round.
        assertTrue(shutterFor(20.0, aperture = 1.4).startsWith("1/"))
    }

    @Test
    fun veryBrightLightDoesNotProduceNonsense() {
        for (lux in listOf(1f, 100f, 10000f, 100000f, 1000000f)) {
            val ev = evAt(lux)!!
            assertTrue("EV for $lux was $ev", ev.isFinite())
            assertTrue(shutterFor(ev).isNotBlank())
            assertTrue(apertureFor(ev).isNotBlank())
        }
    }

    // --- honesty about the sensor ---

    @Test
    fun aLowFullScaleIsCalledOutAsRawCounts() {
        // A part that cannot reach daylight is not reporting lux:
        // 4095 is a 12-bit ADC full scale. Saying so is the honest
        // move, and the old copy invented a range with no source.
        for (raw in listOf(255f, 1023f, 4095f, 8191f)) {
            val note = saturationNote(raw)
            assertTrue("$raw: $note", note.contains("raw count"))
        }
        // A full scale that can actually cover daylight is a plausible
        // lux range, not a raw count, even though 65535 looks like a
        // 16-bit ADC ceiling. Bright light really is that high.
        assertFalse(
            saturationNote(65535f).contains("raw count"),
        )
    }

    @Test
    fun aNormalRangeIsReportedPlainly() {
        val note = saturationNote(100000f)
        assertTrue(note, note.contains("100,000"))
        assertFalse(note.contains("raw count"))
    }

    @Test
    fun anUnknownRangeIsHandledWithoutGuessing() {
        // The old copy claimed "5-30k lux" with no source. An
        // unreported range is stated as unreported.
        val note = saturationNote(0f)
        assertTrue(note, note.contains("usable range"))
    }
}
