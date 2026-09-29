package com.krafttools.app.ui

import kotlin.math.roundToInt

/**
 * WiFi channel arithmetic.
 *
 * Three defects, all of which made the graph wrong rather than merely
 * imprecise:
 *
 * 1. Channel 14 is the one 2.4 GHz channel that does not follow
 *    f = 2407 + 5n. It is 2484 MHz, and the old arithmetic turned it
 *    into channel 15 — a channel that does not exist.
 * 2. 2.4 GHz channel 1 and 6 GHz channel 1 are both `1`. The graph
 *    keyed on a bare Int, so on any 6 GHz device (every Pixel 7 and
 *    later, most current routers) the two bands were merged into one
 *    bar showing the stronger of the two. The key has to carry the
 *    band.
 * 3. Android reports level 0 for an access point that does not
 *    publish a strength, and the old mapping turned that into a
 *    FULL-strength bar — so hidden networks sorted to the top of the
 *    list. No client reports 0 dBm as a received level.
 */

/** Which radio band a frequency belongs to. */
enum class WifiBand(val label: String, val short: String) {
    BAND_2("2.4 GHz", "2.4"),
    BAND_5("5 GHz", "5"),
    BAND_6("6 GHz", "6"),
    UNKNOWN("unknown", "?"),
}

/**
 * A channel, qualified by its band. Two channels are only the same
 * channel if both the band and the number agree.
 */
data class WifiChannel(val band: WifiBand, val number: Int) {
    val key: String get() = "${band.short}:$number"

    val label: String get() = "${band.short} ch $number"

    /** A channel is usable if it is a real one for its band. */
    val isValid: Boolean
        get() = when (band) {
            WifiBand.BAND_2 -> number in 1..14
            WifiBand.BAND_5 -> number in 36..177
            WifiBand.BAND_6 -> number in 1..233
            WifiBand.UNKNOWN -> false
        }
}

/**
 * Channel for a frequency, or null if it is not one we can name.
 *
 * Channel 14 is special-cased: 2484 MHz is the sole exception to
 * f = 2407 + 5n, and the general formula yields the non-existent 15.
 */
fun channelOf(frequencyMhz: Int): WifiChannel? {
    if (frequencyMhz == 2484) return WifiChannel(WifiBand.BAND_2, 14)
    return when (frequencyMhz) {
        in 2412..2472 -> {
            val n = (frequencyMhz - 2407) / 5
            WifiChannel(WifiBand.BAND_2, n)
        }
        // 802.11j (Japan, 4.9 GHz): f = 4000 + 5n, the 5 GHz formula
        // with the base 1000 MHz lower. The old special case was
        // 182 + (f - 4915)/5, which put every real channel one low —
        // 4920 read as 183 where IEEE says 184, 4980 read as 195 where
        // it says 196 — and invented a channel 182 for 4915, which is
        // not a 20 MHz centre frequency at all. The test asserted
        // 4915 -> 182, so the bug was green.
        in 4920..4980 -> WifiChannel(WifiBand.BAND_5, (frequencyMhz - 4000) / 5)
        in 5180..5825 -> WifiChannel(WifiBand.BAND_5, (frequencyMhz - 5000) / 5)
        in 5955..7115 -> WifiChannel(WifiBand.BAND_6, (frequencyMhz - 5950) / 5)
        else -> null
    }
}

/** How a signal is described, using the conventional quality points. */
fun signalQuality(levelDbm: Int?): String = when {
    levelDbm == null -> "strength not reported"
    levelDbm >= -50 -> "excellent"
    levelDbm >= -60 -> "good"
    levelDbm >= -70 -> "reliable"
    levelDbm >= -80 -> "marginal"
    levelDbm >= -90 -> "weak"
    else -> "very weak"
}

/**
 * The non-overlapping channels in a band, so a recommendation can
 * point at a channel nobody is on. Recommending the "shortest bar"
 * among occupied channels recommends joining the one channel that is
 * already busy — the classic WiFi-analyzer failure.
 */
fun nonOverlapping(band: WifiBand): List<WifiChannel> = when (band) {
    // 2.4 GHz: 5 channels apart, because 20 MHz channels overlap at
    // anything closer. 1, 6 and 11 are the whole plan.
    WifiBand.BAND_2 -> listOf(1, 6, 11).map { WifiChannel(band, it) }

    // 5 GHz: UNII-1 and UNII-3 only. The old range was 36..165 step 4,
    // which ran straight through the DFS band (68-96, needing
    // radar-avoidance and unusable in an 80 MHz block) and omitted
    // 149-161 — the non-DFS channels a consumer router can actually be
    // set to. Recommending a channel the user's router UI cannot offer
    // is advice nobody can act on.
    WifiBand.BAND_5 ->
        (listOf(36, 40, 44, 48) + listOf(149, 153, 157, 161))
            .map { WifiChannel(band, it) }

    // 6 GHz: channel numbers are 5 MHz apart, so non-overlapping
    // 20 MHz channels are 4 numbers apart. The old step of 5 proposed
    // channel 6 at 5980 MHz, which overlaps both channel 5 and channel
    // 9 by a full 20 MHz — the exact "join a busy channel" failure
    // this function exists to prevent.
    WifiBand.BAND_6 -> (1..233 step 4).map { WifiChannel(band, it) }

    WifiBand.UNKNOWN -> emptyList()
}

/** How crowded a channel is, which is what a channel graph should show. */
data class ChannelLoad(
    val channel: WifiChannel,
    /** Strongest signal seen on it, dBm, or null. */
    val strongestDbm: Int?,
    /** How many access points are using it. */
    val count: Int,
) {
    /** Crowding is a count, not a height: 20 weak networks on one
     *  channel is a crowded channel, and it used to draw identically
     *  to one strong network. */
    val crowded: Boolean get() = count >= 3
}

/** The lower of two channels wins when only one is listed. */
fun recommendChannel(loads: List<ChannelLoad>, band: WifiBand): WifiChannel? {
    val inBand = loads.filter { it.channel.band == band }
    val present = inBand.associateBy { it.channel.key }
    // Prefer a channel nobody is on at all; otherwise the least busy.
    val empty = nonOverlapping(band).firstOrNull { present[it.key] == null }
    if (empty != null) return empty
    return inBand.minByOrNull { it.count }?.channel
}
