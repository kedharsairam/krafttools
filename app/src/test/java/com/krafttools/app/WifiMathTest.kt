package com.krafttools.app

import com.krafttools.app.ui.ChannelLoad
import com.krafttools.app.ui.WifiBand
import com.krafttools.app.ui.WifiChannel
import com.krafttools.app.ui.channelOf
import com.krafttools.app.ui.nonOverlapping
import com.krafttools.app.ui.recommendChannel
import com.krafttools.app.ui.signalQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every frequency here is from the IEEE tables, not from the code:
 * 2.4 GHz is f = 2407 + 5n (802.11-2016 table 18-9), 5 GHz is
 * f = 5000 + 5n (table E.4), 6 GHz is f = 5950 + 5n.
 */
class WifiMathTest {

    // --- 2.4 GHz ---

    @Test
    fun theTwoPointFourGhzTableIsExact() {
        val expected = mapOf(
            2412 to 1, 2417 to 2, 2422 to 3, 2427 to 4, 2432 to 5,
            2437 to 6, 2442 to 7, 2447 to 8, 2452 to 9, 2457 to 10,
            2462 to 11, 2467 to 12, 2472 to 13,
        )
        for ((mhz, ch) in expected) {
            assertEquals("$mhz", ch, channelOf(mhz)?.number)
            assertEquals("$mhz band", WifiBand.BAND_2, channelOf(mhz)?.band)
        }
    }

    @Test
    fun channelFourteenIsTheSpecialCase() {
        // 2484 MHz is the ONE 2.4 GHz channel not on 2407+5n. The old
        // arithmetic produced channel 15, which does not exist.
        val ch = channelOf(2484)
        assertEquals(14, ch?.number)
        assertEquals(WifiBand.BAND_2, ch?.band)
        // And the general formula would indeed have got it wrong.
        assertEquals(15, (2484 - 2407) / 5)
    }

    // --- 5 GHz ---

    @Test
    fun theFiveGhzTableIsExact() {
        val expected = mapOf(
            5180 to 36, 5200 to 40, 5220 to 44, 5240 to 48, 5260 to 52,
            5745 to 149, 5765 to 153, 5785 to 157, 5805 to 161, 5825 to 165,
        )
        for ((mhz, ch) in expected) {
            assertEquals("$mhz", ch, channelOf(mhz)?.number)
            assertEquals(WifiBand.BAND_5, channelOf(mhz)?.band)
        }
    }

    @Test
    fun theFourPointNineGigahertzBandDoesNotGoNegative() {
        // Japan, channels 182-196 at 4915-4980. Below the 5 GHz
        // formula, so the general case divided to a negative channel
        // and the AP vanished from the list.
        val ch = channelOf(4915)
        assertEquals(182, ch?.number)
        assertTrue("channel must be positive, was ${ch?.number}", (ch?.number ?: 0) > 0)
    }

    // --- 6 GHz ---

    @Test
    fun theSixGhzTableIsExact() {
        val expected = mapOf(
            5955 to 1, 5975 to 5, 6135 to 37, 6435 to 97, 6735 to 157, 7115 to 233,
        )
        for ((mhz, ch) in expected) {
            assertEquals("$mhz", ch, channelOf(mhz)?.number)
            assertEquals(WifiBand.BAND_6, channelOf(mhz)?.band)
        }
    }

    @Test
    fun bandsDoNotCollideOnTheChannelNumber() {
        // The bug the graph hit: keyed on a bare Int, 2.4 ch1 and 6 ch1
        // are both 1 and were merged into one bar.
        val two = channelOf(2412)!!
        val six = channelOf(5955)!!
        assertEquals(two.number, six.number)
        assertNotEquals("the keys must differ", two.key, six.key)
    }

    @Test
    fun everyChannelHasAUniqueKeyAcrossAllBands() {
        val seen = mutableSetOf<String>()
        val freqs = listOf(
            2412, 2437, 2462, 2484, 5180, 5745, 5825, 5955, 6135, 7115,
        )
        for (f in freqs) {
            val ch = channelOf(f)!!
            assertTrue("duplicate key ${ch.key} at $f", seen.add(ch.key))
        }
    }

    @Test
    fun channelsAreValidForTheirBand() {
        for (f in listOf(2412, 2484, 5180, 5825, 5955, 7115)) {
            assertTrue("$f -> ${channelOf(f)}", channelOf(f)!!.isValid)
        }
    }

    @Test
    fun outOfBandFrequenciesAreRejectedRatherThanGuessed() {
        for (f in listOf(0, 100, 1800, 2400, 2407, 3000, 8000, 20000)) {
            val ch = channelOf(f)
            assertNull("$f should not resolve to a channel, got $ch", ch)
        }
    }

    @Test
    fun qualityWordsMatchTheConvention() {
        assertEquals("excellent", signalQuality(-40))
        assertEquals("good", signalQuality(-55))
        assertEquals("reliable", signalQuality(-65))
        assertEquals("marginal", signalQuality(-75))
        assertEquals("weak", signalQuality(-85))
        assertEquals("very weak", signalQuality(-110))
        assertEquals("strength not reported", signalQuality(null))
    }

    // --- crowding and the recommendation ---

    @Test
    fun crowdingIsACountNotASignalHeight() {
        val one = ChannelLoad(WifiChannel(WifiBand.BAND_2, 6), -80, 1)
        val many = ChannelLoad(WifiChannel(WifiBand.BAND_2, 6), -55, 12)
        assertTrue("twelve networks is not crowded?", many.crowded)
        assertTrue("one network is crowded?", !one.crowded)
    }

    @Test
    fun theRecommendationPrefersAnEmptyChannel() {
        // In 2.4 GHz the non-overlapping channels are exactly 1, 6 and
        // 11, so a recommendation can only ever be one of those. My
        // first test wrongly expected channel 2, which overlaps its
        // neighbours and would be a bad recommendation.
        // Channel 1 is busy; 6 and 11 are free, so 6 is the answer.
        val loads = listOf(
            ChannelLoad(WifiChannel(WifiBand.BAND_2, 1), -40, 3),
            ChannelLoad(WifiChannel(WifiBand.BAND_2, 2), -45, 4),
            ChannelLoad(WifiChannel(WifiBand.BAND_2, 3), -50, 2),
        )
        val rec = recommendChannel(loads, WifiBand.BAND_2)!!
        assertEquals(6, rec.number)
    }

    @Test
    fun theRecommendationFallsBackToTheLeastCrowdedNonOverlappingChannel() {
        // Every one of 1/6/11 is busy; the honest answer is the
        // quietest of them, not an overlapping channel.
        val loads = listOf(
            ChannelLoad(WifiChannel(WifiBand.BAND_2, 1), -40, 9),
            ChannelLoad(WifiChannel(WifiBand.BAND_2, 6), -50, 4),
            ChannelLoad(WifiChannel(WifiBand.BAND_2, 11), -60, 1),
        )
        val rec = recommendChannel(loads, WifiBand.BAND_2)!!
        assertEquals(11, rec.number)
        assertTrue("picked an overlapping channel", rec.number in listOf(1, 6, 11))
    }

    @Test
    fun aFullyOccupiedBandStillYieldsAnAnswer() {
        // When nothing is free, the least crowded non-overlapping
        // channel is the useful answer. Returning nothing would leave
        // the user with no next step at all.
        val loads = (1..13).map { ChannelLoad(WifiChannel(WifiBand.BAND_2, it), -50, 4) }
        val rec = recommendChannel(loads, WifiBand.BAND_2)
        assertNotNull("a fully occupied band must still advise", rec)
        assertTrue(rec!!.number in listOf(1, 6, 11))
    }

    @Test
    fun theRecommendationStaysInsideTheBand() {
        val loads = listOf(ChannelLoad(WifiChannel(WifiBand.BAND_6, 1), -40, 9))
        val rec = recommendChannel(loads, WifiBand.BAND_6)!!
        assertEquals(WifiBand.BAND_6, rec.band)
    }

    @Test
    fun nonOverlappingChannelsAreRealOnes() {
        for (band in listOf(WifiBand.BAND_2, WifiBand.BAND_5, WifiBand.BAND_6)) {
            val list = nonOverlapping(band)
            assertTrue("$band produced nothing", list.isNotEmpty())
            assertTrue("$band produced an invalid channel", list.all { it.isValid })
        }
    }
}
