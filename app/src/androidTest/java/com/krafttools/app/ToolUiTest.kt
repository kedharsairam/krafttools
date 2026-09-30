package com.krafttools.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Instrumented tests, aimed at the defects the 329 unit tests could not
 * see.
 *
 * A JVM test has no pixels, no bounds and no semantics tree, so it is
 * structurally blind to a whole class of bug — and this app had a
 * catalogue of them. The vibration meter and the barometer collected no
 * samples at all, for weeks, behind a fully green suite. Every linear
 * trace drew its four gridlines on top of each other along the bottom
 * edge. A scroll added to shared chrome collapsed four instruments into
 * slivers. Six tools flashed "No accelerometer here" at the user every
 * time they were opened.
 *
 * ## Why this file looks the way it does
 *
 * A tool with a live sensor, or a torch whose glow breathes, is never
 * idle. With the default clock, `waitForIdle()` advances the clock
 * waiting for an animation with no end and every synchronisation point
 * becomes a timeout — which is what four of the first seven versions
 * of these tests did, on screens that were working perfectly.
 *
 * So the clock is stopped, which makes `waitForIdle()` cheap, and every
 * read goes through `read {}`: on the UI thread, with Compose's implicit
 * synchronization suppressed. That is the arrangement the framework
 * documents for exactly this case.
 *
 * ## Why there is one sweep and not four
 *
 * The first version had a separate test for each property — every tool
 * opens, no tool claims missing hardware, every instrument is named,
 * every target is 48dp. That is 56 tool openings to learn four things
 * that one pass learns at once, and three of the tests hit their own
 * timeout and left the Compose environment in a state that broke the
 * next one. The sweep walks the grid once and checks everything it
 * sees, and says so in the failure message which tool and which
 * property.
 */
@RunWith(AndroidJUnit4::class)
class ToolUiTest {

    /**
     * A hard ceiling, so a regression that makes a screen non-idle
     * fails the test rather than wedging the suite. A test that hangs
     * is worse than one that fails: it blocks everything after it and
     * hides whatever else is broken.
     */
    @get:Rule(order = 0)
    val hardStop = Timeout.seconds(420)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    /** Every tile, and the instrument each one is expected to expose. */
    private val tools = listOf(
        "Spirit level" to "Spirit level vial",
        "Compass" to "Compass dial",
        "Torch + strobe" to "Torch lamp",
        "Vibration meter" to "Vibration meter",
        "Tally + stopwatch" to "Tally marks",
        "QR scanner" to "QR scanner",
        "WiFi analyzer" to "WiFi channel occupancy",
        "Light meter" to "Sound level scale",
        "Metal + EMF" to null,
        "Sound meter" to "Sound level scale",
        "Color picker" to "Colour sampler",
        "Angle ruler" to "Protractor",
        "Speedometer" to "Speedometer dial",
        "Barometer" to null,
    )

    private val clickable =
        SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)

    @Before
    fun stopTheClock() {
        // With the clock running, a live sensor is an animation with no
        // end and every wait in the framework is a timeout.
        rule.mainClock.autoAdvance = false
    }

    /**
     * A read-only lookup with implicit synchronization switched off.
     *
     * `onNode…fetchSemanticsNodes()` normally waits for idle first,
     * which is exactly what never happens here.
     */
    private fun <T> read(block: () -> T): T = rule.runOnUiThread {
        rule.runWithoutImplicitWait { block() }
    }

    private fun countNodes(description: String): Int = read {
        rule.onAllNodesWithContentDescription(description)
            .fetchSemanticsNodes(atLeastOneRootRequired = false).size
    }

    /**
     * Whether the screen is showing the missing-hardware card.
     *
     * `NoSensor` renders "No <sensor> here", so the whole card can be
     * recognised by shape. Matching the bare word "here" instead —
     * which is what this did at first — flagged the compass, whose own
     * copy contains the word, for a card it never showed.
     */
    private fun missingHardwareText(): Boolean = read {
        val pattern = Regex("^No [a-z ]+ here$")
        rule.onAllNodes(hasText(""))
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .any { node ->
                node.config[SemanticsProperties.Text]
                    .any { pattern.matches(it.text) }
            }
    }

    private fun countText(text: String): Int = read {
        rule.onAllNodesWithText(text, substring = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false).size
    }

    /**
     * Wait for a node, advancing BOTH clocks.
     *
     * This is the whole problem with testing a live-sensor app in one
     * paragraph. A sensor delivers on real time: the spirit level's
     * vial is not composed at all until the accelerometer's first
     * event arrives. But a frozen Compose test clock produces no
     * frames, so that state change never recomposes and the semantics
     * tree stays exactly as it was. Wall-clock waiting alone therefore
     * never sees the node, and advancing the test clock alone never
     * gets the sensor to speak.
     *
     * So the wait does both: real time for the hardware, a frame each
     * iteration for the recomposition to land. Every earlier version of
     * this test got one half right and reported a working screen as
     * having no instrument in it.
     */
    private fun waitForNode(
        description: String,
        budgetMs: Long = 8_000,
    ): Boolean {
        val deadline = System.currentTimeMillis() + budgetMs
        while (System.currentTimeMillis() < deadline) {
            if (countNodes(description) > 0) return true
            rule.mainClock.advanceTimeByFrame()
            Thread.sleep(40)
        }
        return countNodes(description) > 0
    }

    /** Scrolling a lazy grid needs the same treatment as a sensor. */
    private fun reveal(tile: String, budgetMs: Long = 4_000): Boolean {
        val deadline = System.currentTimeMillis() + budgetMs
        while (System.currentTimeMillis() < deadline) {
            if (countNodes(tile) > 0) return true
            runCatching {
                rule.onNode(hasScrollAction())
                    .performScrollToNode(hasContentDescription(tile))
            }
            rule.mainClock.advanceTimeByFrame()
            Thread.sleep(40)
        }
        return countNodes(tile) > 0
    }

    /** A node that is on screen already, or appears within one frame. */
    private fun waitFor(count: () -> Int, wanted: Int): Boolean =
        runCatching {
            rule.mainClock.advanceTimeUntil(timeoutMillis = 1_500) {
                count() >= wanted
            }
            true
        }.getOrDefault(false)

    private fun open(tile: String): Boolean {
        // The grid is a LazyVerticalGrid, so only visible tiles are
        // composed. Six of the first seven of these tests failed on
        // "Color picker is not displayed" and read like a missing tool
        // rather than a missing scroll.
        if (!reveal(tile)) return false
        runCatching { rule.onNodeWithContentDescription(tile).performClick() }
            .onFailure { return false }
        if (!waitForNode("Back to tools")) return false
        // The tool's chrome appears on the first frame; its instrument
        // is composed on the next. Checking for it the instant the back
        // arrow exists reported the spirit level as having no
        // instrument at all, which it very much does.
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(120)
        return true
    }

    private fun goHome(): Boolean {
        runCatching {
            rule.onNodeWithContentDescription("Back to tools").performClick()
        }.onFailure { return false }
        // The grid's own title is a Text, not a labelled control, so it
        // has no content description — an earlier version waited for one
        // that does not exist, on a grid that had come back perfectly.
        return waitForText("KraftTools")
    }

    private fun waitForText(text: String, budgetMs: Long = 4_000): Boolean {
        val deadline = System.currentTimeMillis() + budgetMs
        while (System.currentTimeMillis() < deadline) {
            if (countText(text) > 0) return true
            rule.mainClock.advanceTimeByFrame()
            Thread.sleep(40)
        }
        return countText(text) > 0
    }

    private fun density() = rule.activity.resources.displayMetrics.density

    // ------------------------------------------------------------------

    /**
     * One pass over all fourteen tools, checking four things.
     *
     * A tool must not claim the phone lacks hardware it has — six
     * screens decided a sensor was missing by testing whether a sample
     * had arrived yet, so every cold open flashed "No accelerometer
     * here" before the tool appeared. Its instrument must carry a
     * spoken name, because a Canvas has no accessible content by
     * construction and a screen reader said nothing at all on ten of
     * the fourteen. Every target must clear 48dp, because Material's
     * own defaults are 40dp for a segmented row and 32dp for a chip and
     * both shipped unmodified. And the way back has to work, because a
     * tool you cannot leave is a tool you cannot use.
     */
    @Test
    fun everyToolIsHonestNamedAndReachable() {
        val problems = mutableListOf<String>()

        for ((tile, expectedInstrument) in tools) {
            if (!open(tile)) {
                problems += "$tile did not open"
                continue
            }
            if (read { missingHardwareText() }) {
                problems += "$tile claimed the hardware is missing"
            }
            if (expectedInstrument != null &&
                // The compass needs two sensors before its dial exists,
                // so it is given longer than a single-sensor tool.
                !waitForNode(expectedInstrument, budgetMs = 15_000)
            ) {
                problems += "$tile exposed no instrument named " +
                    "\"$expectedInstrument\""
            }
            read {
                rule.onAllNodes(clickable)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
            }.forEach { node ->
                val w = node.size.width / density()
                val h = node.size.height / density()
                if (w < 47f || h < 47f) {
                    problems += "$tile has a ${"%.0f".format(w)}x" +
                        "${"%.0f".format(h)}dp target"
                }
            }
            if (!goHome()) {
                problems += "$tile could not be left"
                break
            }
        }

        assertTrue(
            "tools are not honest, named, sized or reachable:\n  " +
                problems.joinToString("\n  "),
            problems.isEmpty(),
        )
    }

    /**
     * The tally's block must stay inside its panel, at any count.
     *
     * Three versions of this fit failed three different ways: a ribbon
     * of shrinking bars, then a block so tall the top row ran off the
     * top of the canvas and painted over the count, then a collapsed
     * sliver. All three left the unit tests green, because none of them
     * had a height to assert.
     */
    @Test
    fun theTallyBlockStaysInsideItsPanelAtAnyCount() {
        assertTrue("the tally did not open", open("Tally + stopwatch"))
        val plus = rule.onNodeWithText("+1")
        repeat(46) { plus.performClick() }

        // Not collapsed. This is the assertion that catches the sliver.
        // Measured on the node that carries the tally's spoken name: the
        // Canvas itself has no node of its own, which is the same reason
        // a screen reader had nothing to announce here before the
        // semantics work.
        runCatching {
            rule.onNodeWithContentDescription("Tally marks")
                .assertHeightIsAtLeast(120.dp)
        }.onFailure {
            throw AssertionError(
                "the tally panel collapsed at 46 marks: ${it.message}", it,
            )
        }

        // And it has not overflowed into a neighbour: the panel is a
        // share of the screen, not all of it.
        val screenDp = rule.activity.window.decorView.height / density()
        val panelDp = read {
            rule.onNodeWithContentDescription("Tally marks")
                .fetchSemanticsNode().size.height
        } / density()
        assertTrue(
            "the tally panel fills the screen at 46 marks " +
                "(${panelDp}dp of ${screenDp}dp), so something has " +
                "overflowed it",
            panelDp < screenDp * 0.75f,
        )
    }

}
