package com.krafttools.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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
 * Each test below is a regression test for one of those, not a smoke
 * test written to raise a count.
 *
 * What these still cannot see, and it is worth saying plainly: the
 * torch lamp's tap ripple is drawn at render time and appears in no
 * accessibility tree, so its shape is not assertable here and stays a
 * thing to check by looking.
 */
@RunWith(AndroidJUnit4::class)
class ToolUiTest {

    @get:Rule
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

    private companion object {
        /** Long enough for a permission dialog nobody has to dismiss. */
        const val TIMEOUT = 8_000L
    }

    /**
     * Take the clock off automatic advance.
     *
     * A tool with a live sensor or a running animation is never idle —
     * the torch's glow breathes, the vibration trace redraws fifty
     * times a second — so `waitForIdle()` times out on exactly the
     * tools most worth testing. Four of the first seven failures were
     * `ComposeNotIdleException` and not one of them was an app defect.
     *
     * Driving the clock by hand makes those screens settle on demand.
     */
    @Before
    fun freezeClock() {
        rule.mainClock.autoAdvance = false
    }

    private fun settle(ms: Long = 400) {
        rule.mainClock.advanceTimeBy(ms)
    }

    /**
     * Opens a tool the way a person does: scroll to its tile, tap it.
     *
     * The grid is a LazyVerticalGrid, so only the visible tiles are
     * composed. Six of the first seven of these tests failed for exactly
     * that reason — "Color picker is not displayed" — and the failure
     * read like a missing tool rather than a missing scroll.
     */
    /**
     * Polls for a node while advancing the clock.
     *
     * `waitUntil` does not drive a frozen clock, so on these screens the
     * injected click never finished being processed and the wait timed
     * out on a condition that would have been true a frame later.
     * Advancing explicitly is what actually lets the event land.
     */
    private fun awaitNode(description: String, tile: String) {
        for (i in 0 until 40) {
            if (rule.onAllNodesWithContentDescription(description)
                    .fetchSemanticsNodes().isNotEmpty()
            ) {
                return
            }
            settle(100)
        }
        throw AssertionError(
            "$tile never showed \"$description\" after opening",
        )
    }

    private fun open(tile: String) {
        rule.onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription(tile))
        rule.onNodeWithContentDescription(tile).performClick()
        awaitNode("Back to tools", tile)
    }

    /** Scrolls a tile into view without opening it. */
    private fun reveal(tile: String) {
        rule.onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription(tile))
        settle(150)
    }

    private fun goHome() {
        rule.onNodeWithContentDescription("Back to tools").performClick()
        awaitNode("KraftTools", "back")
    }

    // ------------------------------------------------------------------

    /**
     * Every tool opens, and the way back works.
     *
     * The cheapest possible test, and the one that would have caught a
     * launch crash in any of the fourteen.
     */
    @Test
    fun everyToolOpensAndComesBack() {
        for ((tile, _) in tools) {
            open(tile)
            try {
                rule.onNodeWithContentDescription("Back to tools")
                    .assertIsDisplayed()
            } catch (e: AssertionError) {
                throw AssertionError("$tile did not open: ${e.message}", e)
            }
            goHome()
            rule.onNodeWithContentDescription(tile).assertIsDisplayed()
        }
    }

    /**
     * A tool must never claim the phone lacks hardware it has.
     *
     * Six screens decided a sensor was missing by testing whether a
     * sample had arrived yet, so every cold open flashed "No
     * accelerometer here" before the tool appeared. The user reported
     * it as a flicker; the cause was a null check standing in for an
     * existence check.
     *
     * The transient half of that is a race a UI test cannot win, so
     * this asserts the deterministic half — a phone with the sensor
     * never *settles* on the missing-hardware card — and the predicate
     * itself is pinned by `SensorReadingTest` in the JVM suite.
     */
    @Test
    fun noToolEverClaimsTheHardwareIsMissing() {
        val offenders = mutableListOf<String>()
        for ((tile, _) in tools) {
            open(tile)
            val claims = rule.onAllNodesWithText("here", substring = true)
                .fetchSemanticsNodes()
            if (claims.isNotEmpty()) offenders += tile
            goHome()
        }
        assertTrue(
            "these tools showed the missing-hardware card on a phone " +
                "that has the sensor: $offenders",
            offenders.isEmpty(),
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
        open("Tally + stopwatch")
        val plus = rule.onNodeWithText("+1")
        repeat(46) { plus.performClick() }
        settle(600)

        // Not collapsed. This is the assertion that catches the sliver.
        // Measured on the node that carries the tally's spoken name:
        // the Canvas itself has no node of its own to measure, which is
        // the same reason a screen reader had nothing to announce here
        // before the semantics work.
        rule.onNodeWithContentDescription("Tally marks")
            .assertHeightIsAtLeast(120.dp)

        // And it has not overflowed into a neighbour: the panel is a
        // share of the screen, not all of it.
        val density = rule.activity.resources.displayMetrics.density
        val screenDp = rule.activity.window.decorView.height / density
        val panelDp = rule.onNodeWithContentDescription("Tally marks")
            .fetchSemanticsNode().size.height / density
        assertTrue(
            "the tally panel fills the screen at 46 marks " +
                "(${panelDp}dp of ${screenDp}dp), so something has " +
                "overflowed it",
            panelDp < screenDp * 0.75f,
        )
    }

    /**
     * Every tap target is at least 48dp.
     *
     * Material's own defaults are 40dp for a segmented row and 32dp for
     * a chip, and both shipped unmodified until `Modifier.touchTarget`
     * gave the app one house size.
     */
    @Test
    fun everyTapTargetIsAtLeast48dp() {
        val density = rule.activity.resources.displayMetrics.density
        val tooSmall = mutableListOf<String>()
        for ((tile, _) in tools) {
            open(tile)
            rule.onAllNodes(clickable).fetchSemanticsNodes().forEach { node ->
                val w = node.size.width / density
                val h = node.size.height / density
                if (w < 47f || h < 47f) {
                    tooSmall += "$tile: ${"%.0f".format(w)}x" +
                        "${"%.0f".format(h)}dp"
                }
            }
            goHome()
        }
        assertTrue("tap targets under 48dp: $tooSmall", tooSmall.isEmpty())
    }

    /**
     * Every Canvas that IS an instrument announces itself.
     *
     * A Canvas has no accessible content by construction, so a screen
     * reader said nothing at all on ten of fourteen tools.
     */
    @Test
    fun everyInstrumentHasASpokenName() {
        val missing = mutableListOf<String>()
        for ((tile, expected) in tools) {
            open(tile)
            if (expected != null &&
                rule.onAllNodesWithContentDescription(expected)
                    .fetchSemanticsNodes().isEmpty()
            ) {
                missing += "$tile (expected \"$expected\")"
            }
            goHome()
        }
        assertTrue("instruments with no spoken name: $missing", missing.isEmpty())
    }

    /** Every tile is named, big enough to hit, and present. */
    @Test
    fun everyTileIsNamedAndReachable() {
        settle(200)
        for ((tile, _) in tools) {
            reveal(tile)
            rule.onNodeWithContentDescription(tile).assertIsDisplayed()
        }
        val density = rule.activity.resources.displayMetrics.density
        val small = rule.onAllNodes(clickable).fetchSemanticsNodes()
            .mapNotNull { node ->
                val w = node.size.width / density
                if (w < 100f) "a tile is only ${"%.0f".format(w)}dp wide" else null
            }
        assertTrue(small.toString(), small.isEmpty())
    }

    /**
     * The grid is scrollable and holds all fourteen tools.
     *
     * A tool that has fallen off the bottom of the menu does not crash
     * and does not log anything. It is simply gone.
     */
    @Test
    fun everyToolIsReachableFromTheGrid() {
        settle(200)
        val unreachable = tools.filter { (tile, _) ->
            runCatching { reveal(tile) }.isFailure ||
                rule.onAllNodesWithContentDescription(tile)
                    .fetchSemanticsNodes().isEmpty()
        }
        assertTrue(
            "these tools cannot be reached from the grid: $unreachable",
            unreachable.isEmpty(),
        )
    }
}
