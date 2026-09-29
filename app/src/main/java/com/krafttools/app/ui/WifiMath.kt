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
        // The 4.9 GHz band (Japan, channels 182-196) sits below the
        // 5 GHz formula and would otherwise divide to a negative.
        in 4915..4980 -> WifiChannel(WifiBand.BAND_5, 182 + (frequencyMhz - 4915) / 5)
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
fun nonOverlapping(band: WifiBand, step: Int = 5): List<WifiChannel> = when (band) {
    WifiBand.BAND_2 -> (1..13 step step).map { WifiChannel(band, it) }
    WifiBand.BAND_5 -> (36..165 step 4).map { WifiChannel(band, it) }
    WifiBand.BAND_6 -> (1..233 step step).map { WifiChannel(band, it) }
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
