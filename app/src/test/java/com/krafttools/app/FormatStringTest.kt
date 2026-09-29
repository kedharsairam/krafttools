package com.krafttools.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Kotlin's `+` binds looser than `.format`, so
 * `"a %.1f b" + "c".format(x)` formats only the *second* literal and
 * prints the placeholder in the first verbatim to the user. It has
 * happened three times in this codebase — a torch strobe rate, a
 * compass tilt warning, and a barometer error bar — and each time it
 * reached a real screen before a test caught it.
 *
 * This is a source lint rather than a unit test because the defect is
 * in how the expression is *written*, not in what it computes. The
 * value it produces is a String either way.
 */
class FormatStringTest {

    private fun sourceFiles(): List<File> {
        val root = File("src/main/java/com/krafttools/app")
        if (!root.exists()) return emptyList()
        return root.walkTopDown().filter { it.extension == "kt" }.toList()
    }

    @Test
    fun noConcatenationAppliesFormatToOnlyTheLastLiteral() {
        // A literal that ENDS a line with "+", followed within a few
        // lines by a literal carrying .format(.
        val trailingPlus = Regex("\"\\s*\\+\\s*$")
        val carriesFormat = Regex("\"\\s*\\.format\\(")
        // A line that ends the current expression: the concat is over,
        // so any .format beyond it belongs to something else. Without
        // this the lint reads the next `when` branch as a continuation.
        val endsExpression = Regex("^\\s*(\\)|\\)|else\\b|\\w.*->|\\}|\\))")
        val offenders = mutableListOf<String>()

        for (file in sourceFiles()) {
            val lines = file.readLines()
            for (i in lines.indices) {
                if (!trailingPlus.containsMatchIn(lines[i])) continue
                var hit = false
                for (ahead in lines.drop(i + 1).take(4)) {
                    if (endsExpression.containsMatchIn(ahead)) break
                    if (carriesFormat.containsMatchIn(ahead)) hit = true
                }
                if (hit) offenders += "${file.name}:${i + 1}"
            }
        }
        assertTrue(
            "format() applied to only the last literal of a concatenation:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun theRuleItselfIsUnderstood() {
        // The bug: only the trailing literal is formatted, so the
        // placeholder in the leading one survives to the screen.
        val bad = "%.1f hPa" + "measured".format(1.0)
        assertTrue("the premise no longer holds: $bad", bad.contains("%.1f"))

        // The fix: parenthesise the whole concatenation.
        val good = ("%.1f hPa" + " measured").format(1.0)
        assertEquals("1.0 hPa measured", good)
    }

    @Test
    fun theLintWouldCatchTheBugItExistsFor() {
        val trailingPlus = Regex("\"\\s*\\+\\s*$")
        val carriesFormat = Regex("\"\\s*\\.format\\(")
        val buggy = listOf(
            "\"%.1f Hz — look away\" +",
            "    \"from the flash.\".format(rate),",
        )
        assertTrue(trailingPlus.containsMatchIn(buggy[0]))
        assertTrue(carriesFormat.containsMatchIn(buggy[1]))

        val fixed = listOf(
            "(\"%.1f Hz — look away\" +",
            "    \" from the flash.\").format(rate),",
        )
        // Still a trailing "+" inside the parens, but the .format is no
        // longer attached to a lone literal, so the rule must not fire.
        assertTrue(
            "the fixed form should not match the trailing-plus rule",
            trailingPlus.containsMatchIn(fixed[0]) &&
                !carriesFormat.containsMatchIn(fixed[1]),
        )
    }
}
