package com.krafttools.app

import com.krafttools.app.ui.BaroTrend
import com.krafttools.app.ui.STANDARD_SEA_LEVEL_HPA
import com.krafttools.app.ui.altitudeFromPressure
import com.krafttools.app.ui.baroScale
import com.krafttools.app.ui.classifyTendency
import com.krafttools.app.ui.metresPerHpa
import com.krafttools.app.ui.tendencyHpaPerHour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The altitude formula is the one number in this app that can be
 * checked against an authority, so it is checked against one: the ISA
 * table. Every value below is from that table, not from running the
 * formula and writing down what it said.
 */
class BaroMathTest {

    // International Standard Atmosphere, sea level 1013.25 hPa.
    private val isa = listOf(
        0f to 1013.25f,
        500f to 954.61f,
        1000f to 898.76f,
        2000f to 794.98f,
        3000f to 701.21f,
        5000f to 540.20f,
        8848f to 314.42f,
    )

    @Test
    fun altitudeMatchesTheStandardAtmosphereTable() {
        for ((metres, hpa) in isa) {
            val got = altitudeFromPressure(hpa)
            assertTrue(
                "at $hpa hPa expected ~$metres m, got $got",
                abs(got - metres) < 3f,
            )
        }
    }

    @Test
    fun seaLevelPressureIsZeroAltitude() {
        assertEquals(0f, altitudeFromPressure(STANDARD_SEA_LEVEL_HPA), 0.01f)
    }

    @Test
    fun lowerPressureMeansHigherAltitude() {
        assertTrue(altitudeFromPressure(900f) > altitudeFromPressure(1000f))
        assertTrue(altitudeFromPressure(700f) > altitudeFromPressure(900f))
    }

    @Test
    fun oneHectopascalIsAboutEightMetresAtSeaLevel() {
        // This is the error bar that belongs on the reading: a barometer
        // chip cannot do better than about 0.012 hPa, and one hPa at
        // sea level is ~8.3 m of altitude.
        val d = abs(metresPerHpa(1013.25f))
        assertEquals(8.3f, d, 0.3f)
    }

    @Test
    fun sensitivityIncreasesWithAltitude() {
        // The same pressure change means more altitude up high, because
        // the air is thinner.
        val seaLevel = abs(metresPerHpa(1013f))
        val high = abs(metresPerHpa(700f))
        assertTrue("expected higher sensitivity aloft: $seaLevel vs $high", high > seaLevel)
    }

    @Test
    fun aCustomSeaLevelReferenceShiftsTheZero() {
        // QNH 1000 means the station is at sea-level pressure 1000, so
        // 1000 hPa there reads as zero altitude.
        assertEquals(0f, altitudeFromPressure(1000f, 1000f), 0.01f)
        // Lower pressure than the reference means ABOVE sea level —
        // 990 hPa against a 1000 hPa sea level is +85 m, not below.
        assertEquals(85f, altitudeFromPressure(990f, 1000f), 1f)
        assertTrue(
            "higher pressure than the reference should be below zero",
            altitudeFromPressure(1010f, 1000f) < 0f,
        )
    }

    @Test
    fun degenerateInputDoesNotProduceNaN() {
        for (bad in listOf(0f, -1f, -1013f)) {
            val a = altitudeFromPressure(bad)
            assertTrue("altitude($bad) = $a", a.isFinite())
        }
        assertTrue(altitudeFromPressure(1013f, 0f).isFinite())
    }

    // --- the scale, which is the bug this file exists to prevent ---

    @Test
    fun aFlatBarometerTraceIsNotPinnedToTheTopOfThePanel() {
        // The bug: scaling to the maximum drew 1004 hPa as a line at
        // the very top, hiding a trend that was the entire point.
        val flat = List(60) { 1004.0f }
        val scale = baroScale(flat)
        val n = scale.norm(1004f)
        assertTrue("flat trace normalised to $n, should sit mid-panel", n in 0.4f..0.6f)
    }

    @Test
    fun aSmallVariationStillFillsThePanel() {
        // Weather moves 1-2 hPa over an afternoon. That must be visible.
        val trace = List(60) { 1004.0f + it * 0.02f }
        val scale = baroScale(trace)
        val low = scale.norm(1004f)
        val high = scale.norm(1004f + 59 * 0.02f)
        assertTrue("variation spans only ${high - low} of the panel", high - low > 0.5f)
    }

    @Test
    fun theScaleIsCentredOnTheMedian() {
        val trace = List(40) { 1000f + it * 0.1f }
        val scale = baroScale(trace)
        val sorted = trace.sorted()
        assertEquals(sorted[19] + (sorted[20] - sorted[19]) / 2f, scale.centre, 0.01f)
    }

    @Test
    fun theMedianIgnoresAnOutlierThatTheMeanWouldNot() {
        val trace = MutableList(40) { 1000f }
        trace[0] = 1100f
        val mean = trace.average().toFloat()
        val scale = baroScale(trace)
        // One 1100 hPa sample among forty at 1000 pulls the mean to
        // 1002.5 — a real bias, and on a barometer 2.5 hPa is 21 m of
        // phantom altitude. The median does not move at all.
        assertEquals(1000f, scale.centre, 0.01f)
        assertEquals(1002.5f, mean, 0.01f)
    }

    @Test
    fun aDoorSlamDoesNotCrushHalfAnHourOfWeather() {
        // The bug this scale exists to prevent. A slam is a genuine
        // 8 hPa excursion; half an hour of weather is about 0.6 hPa.
        // Sizing the axis to the maximum let the slam own it, and the
        // weather rendered across 2% of the panel — a technically
        // correct graph that showed nothing at all.
        val trace = MutableList(60) { 1004f + it * 0.01f }
        trace[30] = 1012f
        val scale = baroScale(trace)
        val before = scale.norm(trace[10])
        val after = scale.norm(trace[50])
        assertTrue(
            "weather crushed to ${after - before} of the panel by one spike",
            after - before > 0.3f,
        )
    }

    @Test
    fun theSpikeItselfClipsOffScaleRatherThanSquashingTheTrace() {
        // Clipping is the honest depiction: the slam really is off the
        // scale, and the panel should say so by running out, not by
        // silently rescaling to fit.
        val trace = MutableList(60) { 1004f + it * 0.01f }
        trace[30] = 1012f
        val scale = baroScale(trace)
        assertEquals(1f, scale.norm(1012f), 1e-4f)
    }

    @Test
    fun anUndisturbedWindowIsUnaffected() {
        // The robust estimator must not cost anything when there is no
        // outlier: a plain weather trace still fills the panel.
        val trace = List(60) { 1004f + it * 0.02f }
        val scale = baroScale(trace)
        assertTrue(
            "a clean trace should still fill the panel",
            scale.norm(trace[59]) - scale.norm(trace[0]) > 0.5f,
        )
    }

    @Test
    fun scaleNeverCollapsesBelowTheFloor() {
        val dead = List(30) { 1013.25f }
        val scale = baroScale(dead)
        assertTrue("span collapsed to ${scale.span}", scale.span >= 0.8f)
    }

    @Test
    fun emptyHistoryStillProducesAUsableScale() {
        val scale = baroScale(emptyList())
        assertTrue(scale.span > 0f)
        assertTrue(scale.norm(1013.25f) in 0f..1f)
    }

    @Test
    fun normalizedValuesAlwaysLandInsideThePanel() {
        val trace = List(50) { 900f + it * 3f }
        val scale = baroScale(trace)
        for (p in trace + listOf(0f, 2000f)) {
            val n = scale.norm(p)
            assertTrue("norm($p) = $n escaped the panel", n in 0f..1f)
        }
    }

    // --- tendency ---

    @Test
    fun tendencyIsNullUntilThereIsEnoughHistory() {
        assertNull(tendencyHpaPerHour(List(39) { 1000f }))
        assertNull(tendencyHpaPerHour(emptyList()))
    }

    @Test
    fun aSteadySensorReadsAsNoTendency() {
        val flat = List(60) { 1004f }
        val t = tendencyHpaPerHour(flat)!!
        assertEquals(0f, t, 1e-3f)
        assertEquals(BaroTrend.STEADY, classifyTendency(t))
    }

    @Test
    fun aFallingSensorReadsNegative() {
        // 1 hPa per 15 s over half a window.
        // 1 hPa per 15 s sample: the half-centres are 2 hPa apart over
        // 0.125 h, so the rate is -16 hPa/h — an extreme but exactly
        // correct value for that ramp.
        val falling = List(60) { 1000f - it * (1f / 15f) }
        val t = tendencyHpaPerHour(falling)!!
        assertEquals(-16f, t, 0.01f)
        assertEquals(BaroTrend.FALLING_FAST, classifyTendency(t))
    }

    @Test
    fun aRisingSensorReadsPositive() {
        val rising = List(60) { 1000f + it * (0.5f / 15f) }
        val t = tendencyHpaPerHour(rising)!!
        assertTrue("expected a rise, got $t", t > 0f)
    }

    @Test
    fun tendencyIsMeasuredBetweenTheHalfCentresNotTheWholeWindow() {
        // A ramp of 1 hPa/hour: 15 s per sample means +1/240 hPa a
        // sample. Over the 30 samples between the two half-centres the
        // means should differ by 30/240 = 0.125 hPa, and the rate
        // must come out at 1 hPa/h. Dividing by the whole window would
        // report half of it.
        val perSample = 1f / 240f
        val ramp = List(60) { 1000f + it * perSample }
        val t = tendencyHpaPerHour(ramp)!!
        assertEquals(1f, t, 0.02f)
    }

    @Test
    fun tendencyRespectsTheSamplingInterval() {
        val perSample = 1f / 240f
        val ramp = List(60) { 1000f + it * perSample }
        // The same samples taken twice as slowly span twice the time,
        // so the reported rate HALVES. It is a per-hour figure, and the
        // window it divides by really is twice as long.
        val fast = tendencyHpaPerHour(ramp, intervalSeconds = 15f)!!
        val slow = tendencyHpaPerHour(ramp, intervalSeconds = 30f)!!
        assertEquals(1f, fast, 0.02f)
        assertEquals(0.5f, slow, 0.02f)
    }

    @Test
    fun thresholdsMatchTheMeteorologicalConvention() {
        assertEquals(BaroTrend.FALLING_FAST, classifyTendency(-2f))
        assertEquals(BaroTrend.FALLING, classifyTendency(-1f))
        assertEquals(BaroTrend.STEADY, classifyTendency(0f))
        assertEquals(BaroTrend.STEADY, classifyTendency(0.5f))
        assertEquals(BaroTrend.RISING, classifyTendency(1f))
        assertEquals(BaroTrend.RISING_FAST, classifyTendency(2f))
    }

    @Test
    fun theSteadyBandIsSymmetric() {
        // +0.4 and -0.4 hPa/h are the same weather.
        assertEquals(
            classifyTendency(0.4f),
            classifyTendency(-0.4f),
        )
    }

    @Test
    fun everyTrendHasBothALabelAndAnArrow() {
        for (t in BaroTrend.entries) {
            assertTrue("${t.name} has no label", t.label.isNotBlank())
            assertTrue("${t.name} has no arrow", t.arrow.isNotBlank())
        }
    }
}

