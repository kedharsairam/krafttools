package com.krafttools.app.ui

import kotlin.math.roundToInt

/**
 * Colour maths for the picker, away from the camera plumbing so it can
 * be tested without a device.
 *
 * The bug this replaces was the worst kind of quiet one. Android
 * cameras deliver `YUV_420_888` in **limited range** — luma 16-235,
 * chroma 16-240 — and the code applied the **full-range** BT.601
 * matrix with no (Y − 16) offset. Measured consequence:
 *
 *   scene white (235,128,128)  ->  #FF64FF   instead of  #FFFFFF
 *   scene black (16,128,128)   ->  #C300F3   instead of  #000000
 *   mid grey    (126,128,128)  ->  #FF00FF   instead of  #808080
 *
 * A black surface read as bright magenta. For a tool whose entire
 * product is emitting a hex to a designer, that is not a cosmetic
 * error, and nothing on screen could tell the user.
 */
object ColorMath {

    /**
     * BT.601 **limited range** YUV to sRGB, the range Android actually
     * delivers. Coefffficients per ITU-R BT.601; the 219/224 scaling
     * folded into 1.164.
     */
    fun yuvToRgb(y: Int, u: Int, v: Int): Triple<Int, Int, Int> {
        val yl = 1.164 * (y - 16)
        val ul = u - 128
        val vl = v - 128
        return Triple(
            (yl + 1.596 * vl).roundToInt().coerceIn(0, 255),
            (yl - 0.392 * ul - 0.813 * vl).roundToInt().coerceIn(0, 255),
            (yl + 2.017 * ul).roundToInt().coerceIn(0, 255),
        )
    }

    /** sRGB relative luminance per WCAG 2.x. */
    fun relativeLuminance(r: Int, g: Int, b: Int): Double =
        0.2126 * linearise(r) + 0.7152 * linearise(g) + 0.0722 * linearise(b)

    private fun linearise(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    /**
     * WCAG contrast ratio between two colours, 1.0 to 21.0. Symmetric:
     * the order of arguments does not change the result, which is what
     * makes it safe to compute once and show either way round.
     */
    fun contrast(a: Int, b: Int): Double {
        val la = relativeLuminance(a shr 16 and 0xFF, a shr 8 and 0xFF, a and 0xFF)
        val lb = relativeLuminance(b shr 16 and 0xFF, b shr 8 and 0xFF, b and 0xFF)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * The verdict a designer actually needs. "Contrast 4.2" is a number
     * to look up; a checker that never renders a verdict is not a
     * checker.
     */
    enum class ContrastVerdict(val label: String, val detail: String) {
        AAA("AAA", "passes every text size"),
        AA("AA", "passes normal text"),
        AA_LARGE("AA large", "passes large text and UI only"),
        FAIL("Fail", "below every threshold"),
    }

    /**
     * WCAG 2.2 thresholds: 4.5:1 for AA normal text, 3:1 for AA large
     * text and non-text UI, 7:1 for AAA normal text. Large text is
     * 18pt, or 14pt bold.
     */
    fun verdict(ratio: Double): ContrastVerdict = when {
        ratio >= 7.0 -> ContrastVerdict.AAA
        ratio >= 4.5 -> ContrastVerdict.AA
        ratio >= 3.0 -> ContrastVerdict.AA_LARGE
        else -> ContrastVerdict.FAIL
    }
}

/**
 * A plain-language description of a colour, for a screen reader.
 *
 * A hex code is a name only to someone who can see the swatch. This
 * gives the rough hue family and, where it is decisive, whether the
 * colour is dark or light, so "copied #5C605E" becomes something a
 * blind user can actually judge.
 */
fun describeColour(r: Int, g: Int, b: Int): String {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val lightness = (max + min) / 2.0 / 255.0
    val tone = when {
        lightness < 0.12 -> "near black"
        lightness > 0.92 -> "near white"
        lightness < 0.35 -> "dark"
        lightness > 0.72 -> "light"
        else -> "mid tone"
    }
    if (max == min) {
        return if (tone == "dark") "a dark grey" else "a light grey"
    }
    val delta = (max - min).toDouble()
    val hue = when (max) {
        r -> 60.0 * (((g - b) / delta) % 6.0)
        g -> 60.0 * (((b - r) / delta) + 2.0)
        else -> 60.0 * (((r - g) / delta) + 4.0)
    }.let { if (it < 0) it + 360.0 else it }
    val family = when {
        hue < 15 || hue >= 345 -> "red"
        hue < 45 -> "orange"
        hue < 70 -> "yellow"
        hue < 160 -> "green"
        hue < 200 -> "teal"
        hue < 250 -> "blue"
        hue < 290 -> "purple"
        else -> "pink"
    }
    return "a $tone $family"
}
