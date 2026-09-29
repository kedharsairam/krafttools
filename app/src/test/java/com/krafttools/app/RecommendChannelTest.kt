package com.krafttools.app

import com.krafttools.app.ui.ChannelLoad
import com.krafttools.app.ui.WifiBand
import com.krafttools.app.ui.WifiChannel
import com.krafttools.app.ui.centreMhz
import com.krafttools.app.ui.nonOverlapping
import com.krafttools.app.ui.overlapFraction
import com.krafttools.app.ui.recommendChannel
import com.krafttools.app.ui.recommendationCaveat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which channel to move to when the whole 2.4 GHz plan is occupied.
 *
 * The old rule was "the channel with the fewest networks on it". That
 * is the wrong measure, and not by a small margin: a channel's own
 * count says nothing about the channels either side of it, which is
 * the entire problem in 2.4 GHz. Channel 3 can hold two networks
 * comfortably and still sit inside *half* of channel 1's 20 MHz. With
 * channel 1 the busiest in the band — which is exactly the situation on
 * a dense apartment block — the old rule recommended moving the user
 * into the crowd it was trying to avoid.
 */
class RecommendChannelTest {

    private fun ch(n: Int, band: WifiBand = WifiBand.BAND_2) =
        WifiChannel(band, n)

    private fun load(n: Int, count: Int, band: WifiBand = WifiBand.BAND_2) =
        ChannelLoad(ch(n, band), null, count)

    // --- The overlap arithmetic itself -----------------------------------

    @Test
    fun centreFrequenciesFollowEachBandsFormula() {
        // 802.11's actual anchor points, not one formula for all three.
        assertEquals(2412.0, centreMhz(ch(1)), 0.001)
        assertEquals(2472.0, centreMhz(ch(13)), 0.001)
        assertEquals(5180.0, centreMhz(ch(36, WifiBand.BAND_5)), 0.001)
        // 6 GHz is anchored at 5950 MHz. Treating it like 2.4 GHz puts
        // every 6 GHz channel thousands of MHz from where it belongs.
        assertEquals(5955.0, centreMhz(ch(1, WifiBand.BAND_6)), 0.001)
    }

    @Test
    fun aChannelOverlapsItselfCompletely() {
        assertEquals(1.0, overlapFraction(ch(6), ch(6)), 0.0001)
    }

    @Test
    fun overlapFallsLinearlyToZeroAtFourChannelsOfSeparation() {
        // 20 MHz wide, 5 MHz apart: one channel of separation shares
        // 15 of 20 MHz, two share a quarter, three a twentieth, four
        // nothing. This is why 1/6/11 is the 2.4 GHz plan.
        assertEquals(0.75, overlapFraction(ch(1), ch(2)), 0.0001)
        assertEquals(0.50, overlapFraction(ch(1), ch(3)), 0.0001)
        assertEquals(0.25, overlapFraction(ch(1), ch(4)), 0.0001)
        assertEquals(0.00, overlapFraction(ch(1), ch(5)), 0.0001)
    }

    @Test
    fun theNonOverlappingPlanIsExactlyZeroOverlap() {
        val plan = nonOverlapping(WifiBand.BAND_2)
        assertEquals(listOf(1, 6, 11), plan.map { it.number })
        for (a in plan) {
            for (b in plan) {
                if (a.number == b.number) continue
                assertEquals(
                    "channels ${a.number} and ${b.number} overlap, so " +
                        "the 2.4 GHz plan is wrong",
                    0.0,
                    overlapFraction(a, b),
                    0.0001,
                )
            }
        }
    }

    @Test
    fun channelsInDifferentBandsNeverOverlap() {
        assertEquals(
            0.0,
            overlapFraction(ch(6), ch(6, WifiBand.BAND_5)),
            0.0001,
        )
    }

    // --- The recommendation ----------------------------------------------

    @Test
    fun anEmptyChannelInThePlanIsChosenFirst() {
        // 1 and 6 busy, 11 clear. The plan wins even though channel 13
        // is emptier still, because 13 sits inside 11's neighbourhood.
        val loads = listOf(load(1, 9), load(6, 4))
        assertEquals(11, recommendChannel(loads, WifiBand.BAND_2)?.number)
    }

    @Test
    fun anEmptyChannelWinsEvenWhenThePlanIsFull() {
        // Every plan channel busy, but channel 9 is completely empty —
        // and 9 overlaps nothing in use. The old rule would have
        // recommended whichever occupied channel had the lowest count.
        val loads = listOf(
            load(1, 9), load(6, 9), load(11, 9),
            load(2, 3), load(7, 3),
            load(9, 0),
        )
        assertEquals(9, recommendChannel(loads, WifiBand.BAND_2)?.number)
    }

    @Test
    fun aQuietChannelInsideTheBusiestOneIsNotRecommended() {
        // The defect. Channel 1 carries nine networks; channel 3 carries
        // one. Counting networks says "3". Overlap says channel 3 would
        // sit inside half of channel 1, so it is the worst place in the
        // band to move to, and the answer must not be 3.
        val loads = listOf(
            load(1, 9), load(6, 5), load(11, 5),
            load(3, 1),
        )
        val pick = recommendChannel(loads, WifiBand.BAND_2)
        assertTrue(
            "recommended ${pick?.number}, which sits inside half of " +
                "channel 1's 20 MHz — the old 'fewest networks' rule",
            pick?.number != 3,
        )
    }

    @Test
    fun aBusyChannelIsStillRecommendedWhenThereIsNoAlternative() {
        // With every channel occupied, the answer is the one whose
        // overlap-weighted cost is lowest — and it must still be a real
        // channel of that band.
        val loads = (1..13).map { load(it, if (it == 1) 9 else 2) }
        val pick = recommendChannel(loads, WifiBand.BAND_2)
        assertTrue(pick != null)
        assertTrue("picked ${pick?.number}", pick!!.number in 1..14)
    }

    @Test
    fun aBandWithNoNetworksRecommendsItsFirstPlanChannel() {
        // My first expectation here was that an empty band yields null.
        // That was wrong: if no 2.4 GHz networks are visible, the band
        // is clear, and "try channel 1" is a usable answer rather than
        // no answer. Null is reserved for a band that cannot be
        // reasoned about at all.
        val loads = listOf(load(36, 4, WifiBand.BAND_5))
        assertEquals(
            1,
            recommendChannel(loads, WifiBand.BAND_2)?.number,
        )
    }

    @Test
    fun aChannelSeenWithZeroNetworksCountsAsClean() {
        // The radio reported the channel; nobody is on it. Presence is
        // not occupancy, and conflating the two put a compromise
        // warning on a screen whose recommendation was perfectly clean.
        val loads = listOf(load(1, 9), load(6, 5), load(11, 0))
        assertNull(recommendationCaveat(loads, WifiBand.BAND_2))
    }

    // --- Saying so out loud ---------------------------------------------

    @Test
    fun aPlanChannelNeedsNoCaveat() {
        val loads = listOf(load(1, 9), load(6, 9), load(11, 0))
        assertNull(
            "channel 11 is clean; there is nothing to apologise for",
            recommendationCaveat(loads, WifiBand.BAND_2),
        )
    }

    @Test
    fun aCompromiseIsExplained() {
        // Every channel of the plan occupied. The user is about to move
        // a router on the strength of a number; a bare number is not
        // enough to act on when it is a compromise.
        val loads = listOf(load(1, 9), load(6, 5), load(11, 5), load(3, 1))
        val caveat = recommendationCaveat(loads, WifiBand.BAND_2)
        assertTrue("no caveat given", caveat != null)
        assertTrue(
            "the caveat does not name the occupied channels: $caveat",
            caveat!!.contains("1, 6, 11"),
        )
    }
}
