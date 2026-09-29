package com.krafttools.app

import com.krafttools.app.ui.ColorMath
import com.krafttools.app.ui.ColorMath.ContrastVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The colour tool had two defects that a user could not see and could
 * not work around. Both are pinned here against exact reference values
 * from BT.601 and WCAG, not against what the code happened to produce.
 */
class ColorMathTest {

    // --- BT.601 limited range, the range Android actually delivers ---

    @Test
    fun sceneWhiteIsWhite() {
        val (r, g, b) = ColorMath.yuvToRgb(235, 128, 128)
        assertTrue("white came out as $r,$g,$b", r >= 250 && g >= 250 && b >= 250)
    }

    @Test
    fun sceneBlackIsBlack() {
        // The bug: the old full-range matrix emitted #C300F3 here — a
        // black surface read as bright magenta.
        val (r, g, b) = ColorMath.yuvToRgb(16, 128, 128)
        assertEquals(0, r)
        assertEquals(0, g)
        assertEquals(0, b)
    }

    @Test
    fun midGreyIsMidGrey() {
        // The old code emitted #FF00FF for this.
        val (r, g, b) = ColorMath.yuvToRgb(126, 128, 128)
        assertTrue("grey came out as $r,$g,$b", r in 120..132 && g in 120..132 && b in 120..132)
    }

    @Test
    fun neutralStaysNeutralAcrossTheRange() {
        // With u and v centred, every channel must agree. A matrix
        // error shows up here first.
        for (y in 16..235 step 20) {
            val (r, g, b) = ColorMath.yuvToRgb(y, 128, 128)
            assertTrue("at y=$y: $r,$g,$b", kotlin.math.abs(r - g) <= 2 && kotlin.math.abs(g - b) <= 2)
        }
    }

    @Test
    fun outputIsAlwaysInRange() {
        for (y in 0..255 step 7) {
            for (u in 0..255 step 31) {
                for (v in 0..255 step 31) {
                    val (r, g, b) = ColorMath.yuvToRgb(y, u, v)
                    for (c in listOf(r, g, b)) {
                        assertTrue("channel $c out of range", c in 0..255)
                    }
                }
            }
        }
    }

    @Test
    fun aSaturatedRedStaysRed() {
        // Chroma 240 pushes red; green and blue should stay low.
        val (r, g, b) = ColorMath.yuvToRgb(81, 90, 240)
        assertTrue("red channel was $r", r >= 240)
        assertTrue("green leaked to $g", g < 40)
        assertTrue("blue leaked to $b", b < 40)
    }

    // --- WCAG contrast, exact reference pairs ---

    private fun contrast(hex: String): Double = ColorMath.contrast(
        hex.removePrefix("#").toInt(16),
        0,
    )

    @Test
    fun blackOnWhiteIsTheMaximum() {
        // The formula self-caps here; no clamp is needed.
        assertEquals(21.0, ColorMath.contrast(0x000000, 0xFFFFFF), 0.01)
    }

    @Test
    fun identicalColoursHaveNoContrast() {
        assertEquals(1.0, ColorMath.contrast(0x123456, 0x123456), 0.001)
        assertEquals(1.0, ColorMath.contrast(0xFFFFFF, 0xFFFFFF), 0.001)
    }

    @Test
    fun contrastIsSymmetric() {
        // Order must not matter, or a "text on background" ratio and a
        // "background on text" ratio would disagree.
        assertEquals(
            ColorMath.contrast(0x767676, 0xFFFFFF),
            ColorMath.contrast(0xFFFFFF, 0x767676),
            1e-9,
        )
    }

    @Test
    fun theLightestGreyPassingAaIsWhatItClaims() {
        // #767676 on white is the canonical "just passes AA" grey.
        assertEquals(4.54, ColorMath.contrast(0x767676, 0xFFFFFF), 0.02)
        // One step darker just fails.
        assertEquals(4.48, ColorMath.contrast(0x777777, 0xFFFFFF), 0.02)
    }

    @Test
    fun knownColourPairsMatchTheirPublishedRatios() {
        val cases = listOf(
            0x0000FF to 8.59,
            0xFF0000 to 4.00,
            0x00FF00 to 1.37,
            0x008000 to 5.14,
        )
        for ((hex, expected) in cases) {
            assertEquals(
                "#%06X".format(hex),
                expected,
                ColorMath.contrast(hex, 0xFFFFFF),
                0.02,
            )
        }
    }

    @Test
    fun thisAppsOwnPaletteIsCheckedAgainstItsBackground() {
        // The house cyan on the house near-black. If this ever drops
        // below 4.5 the whole instrument identity stops being legible.
        assertTrue(
            "instrument cyan is ${ColorMath.contrast(0x56CCF2, 0x121214)}:1",
            ColorMath.contrast(0x56CCF2, 0x121214) >= 4.5,
        )
        assertTrue(
            "onSurfaceVariant is ${ColorMath.contrast(0xB4BCC4, 0x121214)}:1",
            ColorMath.contrast(0xB4BCC4, 0x121214) >= 4.5,
        )
    }

    // --- the verdict, which is the point of a checker ---

    @Test
    fun verdictsFollowTheWcagThresholds() {
        assertEquals(ContrastVerdict.AAA, ColorMath.verdict(7.0))
        assertEquals(ContrastVerdict.AA, ColorMath.verdict(4.5))
        assertEquals(ContrastVerdict.AA_LARGE, ColorMath.verdict(3.0))
        assertEquals(ContrastVerdict.FAIL, ColorMath.verdict(2.99))
    }

    @Test
    fun theCanonicalGreyIsRatedAsPassing() {
        // #767676 is the canonical "just passes AA" grey.
        assertEquals(ContrastVerdict.AA, ColorMath.verdict(4.54))
        // #777777, one step darker, fails AA for normal text — but it
        // is still above 3:1, so large text and UI pass. Rating it
        // "Fail" would be wrong; the whole point of the three bands is
        // that a ratio is not a single yes/no.
        assertEquals(ContrastVerdict.AA_LARGE, ColorMath.verdict(4.48))
    }

    @Test
    fun onlyRatiosBelowThreeFailOutright() {
        // Pure green on white is 1.37:1: unusable for text of any size
        // and for any UI element.
        assertEquals(ContrastVerdict.FAIL, ColorMath.verdict(1.37))
    }

    @Test
    fun everyVerdictHasSomethingToSay() {
        for (v in ContrastVerdict.entries) {
            assertTrue(v.label.isNotBlank())
            assertTrue(v.detail.isNotBlank())
        }
    }

    @Test
    fun contrastIsAlwaysInTheLegalRange() {
        for (a in listOf(0, 0x333333, 0x808080, 0xCCCCCC, 0xFFFFFF)) {
            for (b in listOf(0, 0x333333, 0x808080, 0xCCCCCC, 0xFFFFFF)) {
                val c = ColorMath.contrast(a, b)
                assertTrue("contrast($a,$b) = $c", c >= 1.0 && c <= 21.0)
            }
        }
    }
}
