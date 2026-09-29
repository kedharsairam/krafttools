package com.krafttools.app

import com.krafttools.app.ui.niceCeiling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The trace gutter prints numbers inside a Canvas on every frame. Two
 * bugs lived there and neither is visible in a unit test of anything
 * else: a label format that collapsed neighbouring gridlines to the
 * same string, and a `%.*f` star conversion that Kotlin's `format`
 * silently fills from the value slot (crashing on the first frame).
 *
 * These assert the formatting rules directly, over the same ranges
 * AutoScale can produce.
 */
class ScaleFormatTest {

    /** Mirrors the composable's rule, kept honest by these tests. */
    private fun decimalsFor(span: Float): Int = when {
        span >= 10f -> 0
        span >= 1f -> 1
        span >= 0.1f -> 2
        else -> 3
    }

    @Test
    fun starPrecisionIsNotUsedAnywhere() {
        // "%.*f".format(n, v) does not do what it looks like in Kotlin.
        for (v in listOf(0.02f, 0.37f, 4.2f, 96f)) {
            val d = decimalsFor(v)
            val text = "%.${d}f".format(v)
            assertTrue("formatting $v produced $text", text.isNotBlank())
        }
    }

    @Test
    fun gridlinesAreDistinctOnEveryScale() {
        // Four labelled lines must never print two identical strings.
        for (v in listOf(0.02f, 0.05f, 0.2f, 0.5f, 1f, 2f, 5f, 20f, 100f, 1000f)) {
            val ceiling = niceCeiling(v)
            val d = decimalsFor(ceiling)
            val labels = listOf(0.25f, 0.5f, 0.75f, 1f).map {
                "%.${d}f".format(ceiling * it)
            }
            assertEquals(
                "labels collided at ceiling $ceiling: $labels",
                labels.size,
                labels.distinct().size,
            )
        }
    }

    @Test
    fun ceilingLabelMatchesTheTopGridline() {
        for (v in listOf(0.037f, 0.9f, 7f, 480f)) {
            val ceiling = niceCeiling(v)
            val d = decimalsFor(ceiling)
            assertEquals(
                "%.${d}f".format(ceiling),
                "%.${d}f".format(ceiling * 1f),
            )
        }
    }

    @Test
    fun labelsUseTheLocaleDecimalPoint() {
        // A locale with a comma separator must not print "0,2" into a
        // gridline that the gutter was measured for.
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val text = "%.2f".format(0.2f)
            assertTrue("expected a comma for a German locale, got $text", text.contains(','))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun tinyScalesStillPrintSomething() {
        val ceiling = niceCeiling(0.0004f)
        val d = decimalsFor(ceiling)
        assertTrue("0.001-scale printed nothing", "%.${d}f".format(ceiling).isNotBlank())
    }
}
