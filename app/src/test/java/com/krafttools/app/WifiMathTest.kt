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
    fun theFourPointNineGigahertzBandIsThe802Point11JGrid() {
        // 802.11j, Japan: f = 4000 + 5n, the 5 GHz formula with the base
        // 1000 MHz lower. The old special case was
        // 182 + (f - 4915)/5, which put every real channel one low and
        // invented a channel 182 for 4915 — not a 20 MHz centre
        // frequency. The test asserted that wrong answer, so the bug
        // was green.
        val ieee = mapOf(4920 to 184, 4940 to 188, 4960 to 192, 4980 to 196)
        for ((mhz, ch) in ieee) {
            assertEquals(
                "$mhz MHz is IEEE channel $ch, not ${channelOf(mhz)?.number}",
                ch,
                channelOf(mhz)?.number,
            )
            assertEquals(WifiBand.BAND_5, channelOf(mhz)?.band)
        }
    }

    @Test
    fun aFrequencyThatIsNotAChannelCentreIsRejectedNotInvented() {
        // 4915 is the bottom edge of the band, not a centre frequency.
        // The old code returned a confident channel 182 for it.
        assertNull("4915 is not a 20 MHz centre", channelOf(4915))
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

    @Test
    fun theTwoPointFourPlanIsExactlyOneSixEleven() {
        // The classic misconception is that 2.4 GHz has 13 usable
        // non-overlapping channels. With 20 MHz channels and 5 MHz
        // spacing they overlap at 4 channels, so there are three.
        assertEquals(
            listOf(1, 6, 11),
            nonOverlapping(WifiBand.BAND_2).map { it.number },
        )
    }

    @Test
    fun theSixGigaPlanIsTwentyMegahertzSpaced() {
        // 6 GHz numbers are 5 MHz apart, so non-overlapping 20 MHz
        // channels are 4 numbers apart. Step 5 proposed channel 6 at
        // 5980 MHz, overlapping both 5955 and 5995 by a full 20 MHz —
        // the "join a busy channel" failure the function prevents.
        val chans = nonOverlapping(WifiBand.BAND_6).map { it.number }
        assertEquals(listOf(1, 5, 9, 13), chans.take(4))
        assertTrue("6 GHz plan should reach the top of the band", 233 in chans)
        // Adjacent entries must be at least 4 numbers (20 MHz) apart.
        val gaps = chans.zipWithNext { a, b -> b - a }
        assertTrue("gap of ${gaps.min()}, needs >= 4", gaps.min() >= 4)
    }

    @Test
    fun theFiveGigaPlanAvoidsDfsAndIncludesUniiThree() {
        // A channel the user's router UI cannot offer is advice nobody
        // can act on. 68-96 is DFS: it needs radar avoidance and cannot
        // sit in an 80 MHz block. 149-161 is non-DFS and was missing.
        val chans = nonOverlapping(WifiBand.BAND_5).map { it.number }
        assertEquals(listOf(36, 40, 44, 48, 149, 153, 157, 161), chans)
        for (dfs in listOf(68, 72, 80, 96)) {
            assertTrue(
                "channel $dfs is DFS and must not be recommended",
                dfs !in chans,
            )
        }
    }
}
