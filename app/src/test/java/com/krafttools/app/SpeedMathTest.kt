package com.krafttools.app

import com.krafttools.app.ui.SpeedMath
import com.krafttools.app.ui.formatElapsed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The odometer arithmetic has closed-form answers, which makes it
 * exactly the kind of code where a wrong sign or a wrong constant
 * produces plausible numbers. Every value below is derived, not
 * remembered: the statute mile is 1609.344 m by international
 * agreement, and the mean Earth radius is 6371.0088 km (IUGG R1).
 */
class SpeedMathTest {

    // --- unit conversion, exact ---

    @Test
    fun metresPerSecondToKmhIsExactlyThreePointSix() {
        // 1 m = 0.001 km, 1 s = 1/3600 h.
        assertEquals(3.6f, SpeedMath.MS_TO_KMH, 1e-6f)
        assertEquals(3.6f, SpeedMath.toKmh(1f), 1e-5f)
        assertEquals(36f, SpeedMath.toKmh(10f), 1e-4f)
    }

    @Test
    fun metresPerSecondToMphIsTheStatuteMile() {
        // 3600 / 1609.344, exact.
        val expected = 3600.0 / 1609.344
        assertEquals(expected.toFloat(), SpeedMath.toMph(1f), 1e-5f)
        // 25 m/s is 90 km/h, which is 55.9 mph.
        assertEquals(55.92f, SpeedMath.toMph(25f), 0.01f)
    }

    @Test
    fun conversionsRoundTrip() {
        for (ms in listOf(0f, 1f, 10f, 30f, 55f)) {
            // toKmh then back to m/s. My first version converted twice.
            assertEquals(ms, SpeedMath.toKmh(ms) / 3.6f, 1e-4f)
        }
    }

    // --- haversine, against closed forms ---

    @Test
    fun identicalPointsAreZeroApart() {
        assertEquals(0.0, SpeedMath.haversineKm(51.5, -0.12, 51.5, -0.12), 1e-9)
    }

    @Test
    fun aQuarterTurnIsAQuarterOfTheCircumference() {
        // 90 degrees of longitude is a QUARTER of the way round, not
        // half: pi*R/2. My first version of this expected pi*R and
        // would have failed against a correct implementation.
        val quarter = Math.PI * SpeedMath.EARTH_RADIUS_KM / 2
        assertEquals(quarter, SpeedMath.haversineKm(0.0, 0.0, 0.0, 90.0), 1e-6)
        assertEquals(quarter, SpeedMath.haversineKm(0.0, 0.0, 0.0, -90.0), 1e-6)
    }

    @Test
    fun oppositeSidesAreHalfTheCircumferenceApart() {
        assertEquals(
            Math.PI * SpeedMath.EARTH_RADIUS_KM,
            SpeedMath.haversineKm(0.0, 0.0, 0.0, 180.0),
            1e-6,
        )
    }

    @Test
    fun oneDegreeOfArcMatchesTheKnownConstant() {
        // 6371 * pi / 180 = 111.195 km on this sphere.
        assertEquals(SpeedMath.KM_PER_DEGREE, SpeedMath.haversineKm(0.0, 0.0, 1.0, 0.0), 1e-6)
        assertEquals(111.195, SpeedMath.KM_PER_DEGREE, 0.01)
    }

    @Test
    fun haversineCrossesTheAntiMeridian() {
        // 179E to 179W is two degrees, not 358. This is the reason for
        // the haversine form over the law of cosines.
        val across = SpeedMath.haversineKm(0.0, 179.0, 0.0, -179.0)
        val equivalent = SpeedMath.haversineKm(0.0, 0.0, 0.0, 2.0)
        assertEquals(equivalent, across, 1e-9)
        assertTrue("got $across", abs(across - 222.4) < 0.5)
    }

    @Test
    fun distanceIsSymmetric() {
        val a = SpeedMath.haversineKm(51.5, -0.12, 40.7, -74.0)
        val b = SpeedMath.haversineKm(40.7, -74.0, 51.5, -0.12)
        assertEquals(a, b, 1e-9)
    }

    @Test
    fun distanceIsNeverNegative() {
        for (lat in listOf(-89.0, -45.0, 0.0, 45.0, 89.0)) {
            for (lon in listOf(-179.0, -90.0, 0.0, 90.0, 179.0)) {
                assertTrue(
                    SpeedMath.haversineKm(0.0, 0.0, lat, lon) >= 0.0,
                )
            }
        }
    }

    @Test
    fun aRealDistanceIsInTheRightBallpark() {
        // London to Paris is about 343 km. Assert a range, not a value:
        // a spherical model is not a survey.
        val d = SpeedMath.haversineKm(51.5074, -0.1278, 48.8566, 2.3522)
        assertTrue("London-Paris came out $d", d in 330.0..360.0)
    }

    // --- the teleport guard ---

    @Test
    fun aNormalStepIsAccepted() {
        assertTrue(SpeedMath.isPlausibleStep(0.05))
        assertTrue(SpeedMath.isPlausibleStep(0.4))
    }

    @Test
    fun aJumpIsRejected() {
        // 0.5 km/s is 1800 km/h. Nothing driven produces that as a
        // one-second step.
        assertTrue(!SpeedMath.isPlausibleStep(0.6))
        assertTrue(!SpeedMath.isPlausibleStep(12.0))
    }

    @Test
    fun theGuardBoundaryIsInclusive() {
        assertTrue(SpeedMath.isPlausibleStep(SpeedMath.TELEPORT_KM))
    }

    // --- average speed: distance over time, not a mean of samples ---

    @Test
    fun averageSpeedIsDistanceOverTime() {
        // 10 km in 600 s is 16.67 m/s = 60 km/h.
        val avg = SpeedMath.averageSpeedMs(10.0, 600.0)!!
        assertEquals(16.667f, avg, 1e-2f)
        assertEquals(60f, SpeedMath.toKmh(avg), 0.05f)
    }

    @Test
    fun aStationaryTripHasNoAverage() {
        assertNull(SpeedMath.averageSpeedMs(0.0, 600.0))
    }

    @Test
    fun anInstantaneousTripHasNoAverage() {
        assertNull(SpeedMath.averageSpeedMs(10.0, 0.0))
    }

    @Test
    fun unevenFixIntervalsDoNotSkewTheAverage() {
        // The bug this fixes. Two readings: 1 km, then 9 km. A sample
        // mean of (5 m/s, 45 m/s) is 25 m/s. Distance over time is
        // 10 km in 1000 s = 10 m/s, which is the truth, and the
        // difference is entirely the unequal weighting.
        val truth = SpeedMath.averageSpeedMs(10.0, 1000.0)!!
        assertEquals(10f, truth, 1e-3f)
    }

    // --- the gauge ---

    @Test
    fun theGaugeIsFullAtItsFullScale() {
        assertEquals(1f, SpeedMath.gaugeFraction(120f, metric = true), 1e-6f)
        assertEquals(1f, SpeedMath.gaugeFraction(75f, metric = false), 1e-6f)
    }

    @Test
    fun theGaugeClampsBeyondFullScale() {
        // 200 km/h is past the end of the dial, not a negative needle.
        assertEquals(1f, SpeedMath.gaugeFraction(200f, metric = true), 1e-6f)
        assertEquals(0f, SpeedMath.gaugeFraction(0f, metric = true), 1e-6f)
    }

    @Test
    fun theTwoUnitScalesAreNotIdenticalSpeeds() {
        // 120 km/h is 74.56 mph, so at 120 km/h the imperial needle
        // sits just short of full. Worth knowing rather than assuming.
        val mph = SpeedMath.toMph(120f / 3.6f)
        assertTrue("120 km/h came out as $mph mph", mph < 75f)
    }

    // --- the display filter ---

    @Test
    fun theFirstSampleIsTheEstimateNotADilutedValue() {
        // The bug the 3-fix moving average had: it started from an
        // empty list, so the first readings were shown raw and the
        // third arrived already 33% diluted.
        assertEquals(50f, SpeedMath.smoothedSample(null, 50f), 1e-4f)
    }

    @Test
    fun theFilterConvergesOnAConstantSignal() {
        var v: Float? = null
        repeat(40) { v = SpeedMath.smoothedSample(v, 42f) }
        assertEquals(42f, v!!, 1e-3f)
    }

    @Test
    fun theFilterHalvesTheGapEachStep() {
        // alpha = 0.5, so each step closes exactly half the distance to
        // the target. This is the check that alpha is what it says.
        val first = SpeedMath.smoothedSample(0f, 100f)
        assertEquals(50f, first, 1e-4f)
        val second = SpeedMath.smoothedSample(first, 100f)
        assertEquals(75f, second, 1e-4f)
    }

    @Test
    fun theFilterNeverOvershoots() {
        // A window average cannot overshoot either, but the property is
        // what makes the reading safe to show: an EMA fed 0 after 100
        // must approach 0 monotonically, never go below it.
        var v = 100f
        repeat(20) { v = SpeedMath.smoothedSample(v, 0f) }
        assertTrue("overshot to $v", v >= 0f)
        assertEquals(0f, v, 1f)
    }

    @Test
    fun theFilterHandlesNegativeSpeedsWithoutBreaking() {
        // Walking backwards in a car park is a real reading, not a
        // glitch. The gauge clamps at zero, the filter must not blow up.
        var v: Float? = null
        repeat(30) { v = SpeedMath.smoothedSample(v, -3f) }
        assertEquals(-3f, v!!, 1e-3f)
    }

    // --- dial geometry: the bug this proves cannot come back ---
    //
    // The old gauge sized its radius from the width alone
    // (0.62 * minDimension) about a pivot at 0.92 * height, then drew a
    // 240-degree arc about it. Screen y grows downward, so an arc
    // spanning 150..390 degrees has BOTH ends 0.5r BELOW the pivot,
    // which means it needs 1.5r of height and not r. On a 110dp panel
    // the ends needed 25dp more than existed, so both end ticks and the
    // needle at exactly 0% and at full scale were drawn off the bottom
    // of the panel and never seen.

    private fun assertDialFits(label: String, w: Float, h: Float) {
        val d = SpeedMath.dialGeometry(w, h, topInset = 4f, bottomInset = 14f)
        assertTrue(
            "$label: arc top ${d.top} is above the panel",
            d.top >= 0f,
        )
        assertTrue(
            "$label: arc bottom ${d.bottom} exceeds panel height $h",
            d.bottom <= h,
        )
        // Horizontally too: the ends reach 0.866r each way.
        assertTrue(
            "$label: arc is ${d.radius * SpeedMath.ARC_WIDTH_FACTOR} wide, panel is $w",
            d.radius * SpeedMath.ARC_WIDTH_FACTOR <= w,
        )
    }

    @Test
    fun theDialFitsThePanelItIsActuallyGiven() {
        // The 110dp case is the one that shipped broken.
        assertDialFits("360x110", 360f, 110f)
        assertDialFits("360x150", 360f, 150f)
        assertDialFits("360x200", 360f, 200f)
        assertDialFits("360x260", 360f, 260f)
        assertDialFits("360x320", 360f, 320f)
        assertDialFits("360x400", 360f, 400f)
    }

    @Test
    fun theDialFitsAwideAndShortPanel() {
        // Width-limited rather than height-limited: the radius must come
        // from whichever constraint binds first.
        assertDialFits("600x150", 600f, 150f)
        assertDialFits("400x90", 400f, 90f)
    }

    @Test
    fun theDialFitsANarrowAndTallPanel() {
        // Very tall and narrow: height-limited, and the dial must not
        // grow so large that it overflows sideways.
        assertDialFits("320x500", 320f, 500f)
        assertDialFits("280x600", 280f, 600f)
    }

    @Test
    fun theDialNeverOverflowsOnAnyReasonablePanel() {
        // A sweep across realistic phone geometry. The old formula fails
        // every one of these.
        for (w in listOf(280f, 320f, 360f, 400f, 480f, 600f)) {
            for (h in listOf(90f, 110f, 140f, 180f, 220f, 280f, 360f, 500f)) {
                assertDialFits("${w}x$h", w, h)
            }
        }
    }

    @Test
    fun theOldFormulaIsShownToOverflowWhereTheNewOneDoesNot() {
        // If the old geometry ever appears to "fit" 110dp, this test is
        // measuring something different from what shipped.
        val w = 360f
        val h = 110f
        val oldRadius = 0.62f * minOf(w, h)
        val oldPivot = 0.92f * h
        val oldBottom = oldPivot + 0.5f * oldRadius
        assertTrue(
            "the old formula should overflow the 110dp panel, got $oldBottom",
            oldBottom > h,
        )
        val d = SpeedMath.dialGeometry(w, h, 4f, 14f)
        assertTrue("the new one must not", d.bottom <= h)
    }

    @Test
    fun theDialGrowsWithTheSpaceItIsGiven() {
        // A bigger panel must give a bigger dial, or it is ignoring one
        // of the constraints.
        val small = SpeedMath.dialGeometry(360f, 150f, 4f, 14f)
        val large = SpeedMath.dialGeometry(360f, 300f, 4f, 14f)
        assertTrue(
            "no growth from 150dp to 300dp: ${small.radius} -> ${large.radius}",
            large.radius > small.radius * 1.5f,
        )
    }

    @Test
    fun aDegeneratePanelDoesNotProduceNegativeGeometry() {
        // A zero-height panel must not yield a negative radius, which
        // would throw inside drawArc.
        val d = SpeedMath.dialGeometry(360f, 0f, 4f, 14f)
        assertTrue("radius was ${d.radius}", d.radius >= 0f)
        val wide = SpeedMath.dialGeometry(0f, 0f, 0f, 0f)
        assertTrue("radius was ${wide.radius}", wide.radius >= 0f)
    }

    @Test
    fun theDialLeavesRoomForTheStrokeItDraws() {
        // The outermost drawn thing is the stroke, centred on the path,
        // so it reaches half a stroke width beyond the computed arc.
        // The 0.96 safety factor has to cover that.
        for (h in listOf(110f, 200f, 320f)) {
            val d = SpeedMath.dialGeometry(360f, h, 4f, 14f)
            assertTrue(
                "h=$h: bottom ${d.bottom} + half stroke exceeds $h",
                d.bottom + d.strokeWidth / 2f <= h,
            )
        }
    }

    @Test
    fun theDialAspectIsWidthOverHeightNotTheOtherWayRound() {
        // Compose's aspectRatio takes width/height. The geometry gives
        // 1.732r wide by 1.5r tall, so the ratio is 1.732/1.5 and NOT
        // 1.5/1.732. Passing it the wrong way round makes the canvas
        // 1.5r too tall, and the surplus shows as dead space below the
        // dial — which is exactly what the first attempt looked like.
        assertEquals(
            1.7320508f / 1.5f,
            SpeedMath.DIAL_WIDTH_OVER_HEIGHT,
            1e-5f,
        )
        // Sanity: a 328dp-wide panel yields a 284dp-tall dial, and
        // 328 / 284 is the ratio above.
        val width = 328f
        val height = width / SpeedMath.DIAL_WIDTH_OVER_HEIGHT
        assertEquals(284f, height, 1f)
    }

    @Test
    fun theAspectAndTheGeometryAgreeOnTheDialSize() {
        // The aspect ratio and dialGeometry must describe the same
        // shape, or the canvas is sized for one dial and the drawing
        // solves for another.
        for (w in listOf(280f, 328f, 400f, 600f)) {
            val fromAspect = w / SpeedMath.DIAL_WIDTH_OVER_HEIGHT
            val expectedR = w / SpeedMath.ARC_WIDTH_FACTOR * 0.96f
            val expectedH = SpeedMath.ARC_HEIGHT_FACTOR * expectedR
            assertEquals(
                "width $w: aspect gives ${fromAspect}dp, geometry gives ${expectedH}dp",
                expectedH,
                fromAspect,
                expectedH * 0.15f,
            )
        }
    }

    // --- approximate location, which is a permission the user chooses ---
    //
    // With "approximate" granted, Android reports fixes good only to
    // 1000-2000 m. That is fine for a speed, and useless for a trip:
    // the teleport guard rejects any step over 500 m, so every step at
    // that accuracy is rejected and the odometer sits at 0.00 km
    // forever. Saying so beats an instrument that looks broken.

    @Test
    fun aPreciseFixCountsTowardsTheTrip() {
        assertTrue(SpeedMath.countsTowardsTrip(5f))
        assertTrue(SpeedMath.countsTowardsTrip(25f))
    }

    @Test
    fun anApproximateFixDoesNotCountTowardsTheTrip() {
        // 2000 m is what Android actually reports for a coarse fix.
        assertTrue(!SpeedMath.countsTowardsTrip(2000f))
        assertTrue(!SpeedMath.countsTowardsTrip(1000f))
        assertTrue(!SpeedMath.countsTowardsTrip(65f))
    }

    @Test
    fun aMissingOrImpossibleAccuracyDoesNotCount() {
        assertTrue(!SpeedMath.countsTowardsTrip(null))
        assertTrue(!SpeedMath.countsTowardsTrip(Float.NaN))
        assertTrue(!SpeedMath.countsTowardsTrip(Float.POSITIVE_INFINITY))
    }

    @Test
    fun theApproximateCaseIsExplained() {
        val note = SpeedMath.tripAccuracyNote(2000f)!!
        assertTrue(note, note.contains("approximate"))
        assertTrue("the note should name the remedy", note.contains("precise"))
    }

    @Test
    fun noNoteEverPrintsAPlaceholder() {
        // "a" + "b".format(x) applies the format to "b" alone and
        // leaves a literal %.0f in the first half. That reached the
        // screen on the approximate branch before this test existed.
        for (a in listOf(null, 0f, 5f, 65f, 999f, 1000f, 2000f, 12000f,
            Float.NaN, Float.POSITIVE_INFINITY)) {
            val note = SpeedMath.tripAccuracyNote(a) ?: continue
            assertFalse(
                "accuracy=$a produced an unformatted note: $note",
                note.contains("%"),
            )
            assertTrue(
                "accuracy=$a produced an empty note",
                note.isNotBlank(),
            )
        }
    }

    @Test
    fun theApproximateNoteQuotesTheRealFigure() {
        // Android reports ~2000 m for a coarse fix; the note has to say
        // the number it was actually given, not a guess.
        assertTrue(SpeedMath.tripAccuracyNote(2000f)!!.contains("2000"))
        assertTrue(SpeedMath.tripAccuracyNote(1500f)!!.contains("1500"))
    }

    @Test
    fun aMerelyLooseFixGetsADifferentNoteThanApproximateLocation() {
        // 65 m is a normal fix indoors, not a permission problem, and
        // telling the user to change their location settings would be
        // wrong advice.
        val note = SpeedMath.tripAccuracyNote(65f)!!
        assertTrue(note, note.contains("65"))
        assertTrue(
            "should not blame the permission choice: $note",
            !note.contains("approximate"),
        )
    }

    @Test
    fun aGoodFixProducesNoNoteAtAll() {
        assertNull(SpeedMath.tripAccuracyNote(5f))
    }

    // --- the needle: a constant 270-degree error, and it was invisible
    // until the gauge was un-clipped ---
    //
    // The needle is drawn as a line pointing straight UP, which is
    // screen angle 270 (screen y grows downward). Compose's `rotate`
    // turns clockwise, so putting that line on the arc at angle t means
    // rotating by t - 270. The code passed t. That is a constant
    // 270-degree error, and it was hidden by the clipping: at 0 km/h
    // the cyan fill sat on the lower-left of the dial while the needle
    // pointed to the lower-right, so the dial and the number disagreed
    // about what the car was doing.

    @Test
    fun theNeedleSitsAtTheZeroEndWhenTheSpeedIsZero() {
        // Screen angle 150: cos = -0.866, so the needle points LEFT,
        // which is the low end of the dial.
        assertEquals(-120f, SpeedMath.needleRotation(0f), 1e-3f)
    }

    @Test
    fun theNeedleSitsAtTheTopWhenHalfScale() {
        // Screen angle 270 is straight up, and a straight-up line needs
        // no rotation at all.
        assertEquals(0f, SpeedMath.needleRotation(0.5f), 1e-3f)
    }

    @Test
    fun theNeedleSitsAtTheFarEndAtFullScale() {
        assertEquals(120f, SpeedMath.needleRotation(1f), 1e-3f)
    }

    @Test
    fun theNeedleSweepsMonotonically() {
        var previous = SpeedMath.needleRotation(0f)
        for (f in listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1f)) {
            val r = SpeedMath.needleRotation(f)
            assertTrue(
                "needle went backwards at fraction $f: $previous -> $r",
                r > previous,
            )
            previous = r
        }
    }

    @Test
    fun theNeedleIsClampedLikeTheFill() {
        // A speed past full scale must not swing the needle off the dial.
        assertEquals(
            SpeedMath.needleRotation(1f),
            SpeedMath.needleRotation(4.2f),
            1e-3f,
        )
        assertEquals(
            SpeedMath.needleRotation(0f),
            SpeedMath.needleRotation(-3f),
            1e-3f,
        )
    }

    @Test
    fun theNeedleAndTheArcAgreeAtEveryPoint() {
        // The property that actually matters: for any speed, the needle
        // lands on the same arc angle as the filled portion. Before the
        // fix this was off by a constant 270 degrees everywhere, which
        // is exactly the kind of error no single-point test catches.
        for (step in 0..40) {
            val f = step / 40f
            val arcAngle = SpeedMath.DIAL_START_ANGLE +
                SpeedMath.DIAL_SWEEP * f
            val needle = SpeedMath.needleRotation(f)
            val backOntoTheArc = needle + SpeedMath.SCREEN_UP_ANGLE
            assertEquals(
                "at fraction $f the needle is at arc angle $backOntoTheArc, " +
                    "but the fill is at $arcAngle",
                arcAngle,
                backOntoTheArc,
                1e-3f,
            )
        }
    }

    // --- formatting ---

    @Test
    fun elapsedTimeIsFormattedLikeATripComputer() {
        assertEquals("0:00", formatElapsed(0))
        assertEquals("0:07", formatElapsed(7))
        assertEquals("1:05", formatElapsed(65))
        assertEquals("59:59", formatElapsed(3599))
        assertEquals("1:00:00", formatElapsed(3600))
        assertEquals("2:03:04", formatElapsed(7384))
    }

    @Test
    fun elapsedTimeNeverGoesNegative() {
        assertTrue(formatElapsed(0).isNotBlank())
    }
}
