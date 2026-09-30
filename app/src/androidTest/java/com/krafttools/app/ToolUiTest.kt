package com.krafttools.app

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import org.junit.Assert.assertTrue
import org.junit.After
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
     * Hand the clock back before the rule tears anything down.
     *
     * This is the last of the frozen-clock consequences, and it was the
     * one that actually hung the suite.
     *
     * Every test in this file stops the clock, because a tool with a
     * live sensor is an animation with no end and a running clock turns
     * every synchronisation point into a timeout. But
     * `createAndroidComposeRule` finishes a test by calling
     * `Instrumentation.waitForIdleSync()`, and that waits for the main
     * looper to drain. A stopped clock is a clock that will never
     * deliver another frame, so anything the framework still had queued
     * could never be finished — and the teardown sat in
     * `Idler.waitForIdle` until the 600 second ceiling killed it. The
     * sweep itself had already run; the rule could not let go.
     *
     * The clock is the test's device, and it has to be handed back
     * before the door closes, or the framework waits forever for a frame
     * that a test is holding hostage. The tool screens are all left by
     * now, so nothing is animating and nothing is sampling; letting it
     * run again costs nothing and unblocks the teardown.
     */
    @After
    fun handTheClockBack() {
        trace("@After: handing the clock back")
        rule.mainClock.autoAdvance = true
    }

    /**
     * A hard ceiling, so a regression that makes a screen non-idle
     * fails the test rather than wedging the suite. A test that hangs
     * is worse than one that fails: it blocks everything after it and
     * hides whatever else is broken.
     */
    @get:Rule(order = 0)
    val hardStop = Timeout.seconds(600)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Every tile, and the instrument each one is expected to expose.
     *
     * Four of these were wrong when this sweep first ran, in a way worth
     * recording: vibration, light, EMF and barometer were all listed as
     * expecting `null`, so the one property that matters most on those
     * screens — that they have a name at all — was never checked. The
     * light meter's row had the sound meter's name pasted into it, so
     * even if it had been checked it could not have failed.
     *
     * They are no longer optional. Every tool must expose an instrument
     * by name, and the four that draw a trace are the four that were
     * silent.
     */
    private val tools = listOf(
        "Spirit level" to "Spirit level vial",
        "Compass" to "Compass dial",
        "Torch + strobe" to "Torch lamp",
        "Vibration meter" to "Vibration trace",
        "Tally + stopwatch" to "Tally marks",
        "QR scanner" to "QR scanner",
        "WiFi analyzer" to "WiFi channel occupancy",
        "Light meter" to "Light trace",
        "Metal + EMF" to "EMF trace",
        "Sound meter" to "Sound level scale",
        "Color picker" to "Colour sampler",
        "Angle ruler" to "Protractor",
        "Speedometer" to "Speedometer dial",
        "Barometer" to "Barometer trace",
    )

    /**
     * The sweep's own ceiling, in the time it is allowed to take.
     *
     * It has to sit inside [hardStop], because a sweep that reaches its
     * own limit can still fail with a message, while one that hits the
     * JUnit rule is killed with nothing. The per-step budgets are set so
     * that fourteen tools cannot exceed this: the slowest tool is the
     * compass, which waits on two sensors, at 3 + 5 + 12 + 4 seconds.
     */
    private val SWEEP_BUDGET_MS = 480_000L

    /** The first tile on the grid, used as the sign that the grid is back. */
    private val FIRST_TILE = "Spirit level"

    private val clickable =
        SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)

    @Before
    fun setUpTheWorld() {
        // With the clock running, a live sensor is an animation with no
        // end and every wait in the framework is a timeout.
        rule.mainClock.autoAdvance = false

        // Grant the runtime permissions up front.
        //
        // This is the thing a test has to arrange that a person does
        // not: five of the fourteen tools sit behind a permission gate,
        // and a gate that has not been answered shows its RATIONALE —
        // not the tool. So opening the compass without location granted
        // and then waiting for "Compass dial" waits fifteen seconds for
        // a dial sitting behind a dialog the test never dismissed, and
        // reports a working screen as having no instrument in it.
        //
        // Not a harness problem and not an app problem: the test not
        // putting the world in the state it means to test in, which is
        // the one thing a test is for.
        val instrumentation =
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
        val app = instrumentation.targetContext.packageName
        for (permission in listOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
        )) {
            runCatching {
                instrumentation.uiAutomation.grantRuntimePermission(
                    app, permission,
                )
            }
        }
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
        // `hasText("")` reads as "any node with text" and is not:
        // without `substring`, it is an equality test, so it matched
        // only nodes whose text is the empty string — none of them. The
        // missing-hardware check therefore passed on every tool in
        // every run, including the six that used to flash the card.
        // "has any Text key" is what was meant.
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
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
        budgetMs: Long = 5_000,
    ): Boolean {
        val deadline = System.currentTimeMillis() + budgetMs
        while (System.currentTimeMillis() < deadline) {
            if (countNodes(description) > 0) return true
            rule.mainClock.advanceTimeByFrame()
            Thread.sleep(40)
        }
        return countNodes(description) > 0
    }

    /**
     * Wait for a navigation transition to finish.
     *
     * A transition animates two screens at once, so for as long as it
     * runs there are two back buttons in the tree and two instruments:
     * the sweep caught the compass's dial and the torch's lamp on the
     * same screen, with the grid's own tiles still present at x = -683,
     * halfway off the left edge. `onNodeWithContentDescription` then
     * has nothing unambiguous to press, and the test reported the torch
     * as a tool you cannot leave — which is a thing a person tapping
     * that button ten times a day never experiences.
     *
     * With the clock stopped the transition advances only when the test
     * advances it, so "wait until the new screen exists" is not enough;
     * the old one has to be gone too. Exactly one back button is the
     * test for that, and it is the cheapest one available: every
     * screen in this app has precisely one.
     */
    private fun settle(instrument: String, budgetMs: Long = 5_000): Boolean {
        val deadline = System.currentTimeMillis() + budgetMs
        var saw = "never looked"
        while (System.currentTimeMillis() < deadline) {
            if (arrived(instrument)) {
                // Arrived is not finished.
                //
                // The instrument is in place while the screen being
                // left is still sliding away, and that screen's controls
                // are in the tree the whole time — which is how the
                // colour sampler got blamed for a 127x40dp control at
                // (401, 1910) that belonged to the sound meter's chrome
                // and that no user could see. Enumerating every possible
                // predecessor is the wrong fix; the transition is over
                // when its animation is, so run it out.
                rule.mainClock.advanceTimeBy(600)
                rule.mainClock.advanceTimeByFrame()
                return true
            }
            // What did it look like? "The transition never finished" is
            // only actionable if it says which of the two ways it failed
            // — the instrument never appeared at all, or it appeared off
            // screen and stayed there.
            saw = read {
                val nodes = rule.onAllNodes(hasContentDescription(instrument))
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                if (nodes.isEmpty()) {
                    "no node named \"$instrument\" at all, with " +
                        "${visibleBackButtonsOnScreen()} back buttons"
                } else {
                    val b = nodes.first().boundsInRoot
                    "\"$instrument\" at (${b.left.toInt()}," +
                        "${b.top.toInt()})-(${b.right.toInt()}," +
                        "${b.bottom.toInt()}) with " +
                        "${visibleBackButtonsOnScreen()} back buttons"
                }
            }
            rule.mainClock.advanceTimeByFrame()
            Thread.sleep(40)
        }
        trace("  settle gave up: $saw")
        return arrived(instrument)
    }

    /**
     * Is any part of the grid still on the display?
     *
     * The tool arriving is only half of a transition being over; the
     * grid leaving is the other half, and without this the sweep blamed
     * the colour sampler for a 127x40dp control at x = 791 that ran off
     * the right edge of a 1080 pixel screen. It was a tile from the grid
     * still sliding away — a control no user could see, on a tool that
     * had already arrived.
     */
    private fun gridStillOnScreen(except: String): Boolean = read {
        val metrics = rule.activity.resources.displayMetrics
        // "QR scanner" is both a tile and the instrument on that tool's
        // screen, so the name being waited for has to come out of the
        // set — otherwise the grid check finds the tool that has just
        // arrived and reports it as not yet arrived.
        val grid = tools.map { it.first }.toSet() - except
        rule.onAllNodes(gridTileIn(grid))
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .any { node ->
                val b = node.boundsInRoot
                b.right > 0f && b.left < metrics.widthPixels &&
                    b.bottom > 0f && b.top < metrics.heightPixels
            }
    }

    private fun gridTileIn(names: Set<String>): SemanticsMatcher =
        SemanticsMatcher("is a grid tile") { node ->
            node.config.contains(SemanticsProperties.ContentDescription) &&
                node.config[SemanticsProperties.ContentDescription]
                    .any { it in names }
        }

    private fun visibleBackButtonsOnScreen(): Int = read {
        rule.onAllNodes(hasContentDescription("Back to tools") and onDisplay())
            .fetchSemanticsNodes(atLeastOneRootRequired = false).size
    }

    /**
     * Has the tool actually arrived?
     *
     * Counting visible back buttons is not enough, and the sweep learned
     * that the hard way: it reported a 127x40dp target on the colour
     * sampler, and the control turned out to be at x = 1251 on a 1080
     * pixel wide display — a ghost from a transition that had not
     * finished, on a screen that was still sliding in. One back button
     * had arrived while the rest of the screen had not, so the test
     * measured a node that no user could see and blamed a tool for it.
     *
     * The instrument is a better sentinel than the chrome: it is the
     * largest thing on the screen, it is named, and every one of the
     * fourteen has one. When the instrument is fully inside the display,
     * the transition is over.
     */
    private fun arrived(instrument: String): Boolean {
        if (visibleBackButtons() != 1) return false
        if (gridStillOnScreen(except = instrument)) return false
        val metrics = rule.activity.resources.displayMetrics
        return read {
            rule.onAllNodes(hasContentDescription(instrument))
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .any { node ->
                    // Its centre is on the display, and it is wide
                    // enough to be the instrument rather than a sliver
                    // of one on its way past.
                    //
                    // Requiring the whole rect to fit was the obvious
                    // version and it is wrong: a vial that fills its
                    // column can lay out one pixel taller than the
                    // screen, and then "arrived" is never true and the
                    // spirit level is reported as unopenable.
                    val b = node.boundsInRoot
                    val cx = (b.left + b.right) / 2f
                    val cy = (b.top + b.bottom) / 2f
                    cx in 0f..metrics.widthPixels.toFloat() &&
                        cy in 0f..metrics.heightPixels.toFloat() &&
                        b.width > metrics.widthPixels * 0.4f
                }
        }
    }

    /**
     * Back buttons that are actually on the display.
     *
     * Counting every back button in the tree is not the same question,
     * and the difference is the whole bug. A navigation transition
     * leaves the outgoing screen composed and slides it off the edge:
     * the grid's tiles sat at x = -940 and the incoming back arrow at
     * x = 1091, on a 1080 pixel wide display. Both are in the tree, so
     * "exactly one exists" was satisfied at some instant while two
     * were visible-but-clipped, and the press that followed had no
     * unambiguous target — which is why the torch was reported as a
     * tool you cannot leave, and why nothing about the torch was wrong.
     */
    private fun visibleBackButtons(): Int = read {
        rule.onAllNodes(hasContentDescription("Back to tools") and onDisplay())
            .fetchSemanticsNodes(atLeastOneRootRequired = false).size
    }

    /** The one back button a person could actually press. */
    private fun backButton(): SemanticsNodeInteraction =
        rule.onAllNodes(hasContentDescription("Back to tools") and onDisplay())
            .onFirst()

    private fun onDisplay(): SemanticsMatcher {
        val metrics = rule.activity.resources.displayMetrics
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()
        return SemanticsMatcher("is on the display") { node ->
            val b = node.boundsInRoot
            b.width > 0f && b.height > 0f &&
                b.left >= -1f && b.top >= -1f &&
                b.right <= w + 1f && b.bottom <= h + 1f
        }
    }

    /**
     * Scroll a lazy grid until a tile is composed, and return whether it
     * ever was.
     *
     * The scroll has to go through [read] like everything else here, and
     * getting that wrong is what wedged the whole suite for six hundred
     * seconds. `performScrollToNode` fetches the scrollable's node
     * first, and that fetch calls `waitForIdle()` — which, because
     * Espresso is on the classpath, asks Espresso's idling resource
     * whether the app has gone quiet. On a screen with a live sensor it
     * never does. So the scroll blocked inside Espresso's idling wait
     * and the sweep was killed by its own timeout, four tools in, with
     * a stack trace pointing at the framework and no statement about
     * which tool it was on.
     *
     * Every other read in this file already suppressed that
     * synchronisation, so the obvious repair was to put the scroll
     * inside [read] too. That deadlocks differently and just as
     * permanently: `performScrollToNode` advances the clock after each
     * scroll so the lazy grid can lay out, and the clock can only be
     * advanced from the looper — which is now blocked running the
     * runnable `runOnUiThread` posted. The helper is unusable from both
     * sides.
     *
     * So the scroll is done the way a person does it, with a touch.
     * That runs on the test thread, where advancing the clock is
     * allowed, and it needs no special arrangement at all.
     */
    private fun reveal(tile: String, budgetMs: Long = 4_000): Boolean {
        val deadline = System.currentTimeMillis() + budgetMs
        while (System.currentTimeMillis() < deadline) {
            if (countNodes(tile) > 0) return true
            // The one place the clock is allowed to run.
            //
            // A swipe is a gesture, and a gesture asks for frames. With
            // the clock stopped, that frame is queued and never drawn,
            // and Espresso's idle check — which counts pending
            // Choreographer frames — can never be satisfied:
            //
            //   MAIN_LOOPER_HAS_IDLED(last message: { callback=android.view.Choreogr })
            //
            // It spun for sixty seconds and failed, and because the grid
            // is the last thing standing between the sweep and the four
            // tools below the fold, the sweep died there every time.
            //
            // The grid is also the one screen in this app where a
            // running clock costs nothing: no sensor is sampling, no
            // animation is running, and nothing on it is infinite. That
            // is what makes this safe, and it is why the rule is
            // "the grid may tick" rather than "the clock may run".
            rule.mainClock.autoAdvance = true
            runCatching {
                rule.onNode(hasScrollAction())
                    .performTouchInput { swipeUp() }
            }
            // Let the fling finish before stopping the clock, and this
            // is the part that actually mattered. A swipe ends with the
            // list still settling; stopping the clock at that instant
            // strands the frame it was about to draw, and every press
            // afterwards then spends sixty seconds proving that the
            // app is not idle. Stopping the clock on a settled screen
            // leaves nothing pending.
            //
            // The wait is safe here and only here: the grid is the one
            // screen with no sensor and no animation, so "run until
            // idle" is a thing that actually finishes.
            runCatching { rule.waitForIdle() }
            rule.mainClock.autoAdvance = false
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

    /**
     * Open a tool, or say precisely why it did not open.
     *
     * The first version of this returned a bare `false`, so a sweep that
     * failed said "Torch + strobe did not open" and nothing else — not
     * whether the tile never scrolled into view, whether the click was
     * rejected, or whether the tool's chrome never appeared. Three
     * entirely different bugs produce the same word.
     */
    private fun open(tile: String, instrument: String): String? {
        // The grid is a LazyVerticalGrid, so only visible tiles are
        // composed. Six of the first seven of these tests failed on
        // "Color picker is not displayed" and read like a missing tool
        // rather than a missing scroll.
        if (!reveal(tile)) return "the tile never scrolled into view"
        press(rule.onNodeWithContentDescription(tile))?.let {
            return "the tile could not be pressed — $it"
        }
        if (!waitForNode("Back to tools")) {
            return "the tile was clicked but the tool's own screen " +
                "never appeared"
        }
        // The tool's chrome appears on the first frame; its instrument
        // is composed on the next. Checking for it the instant the back
        // arrow exists reported the spirit level as having no
        // instrument at all, which it very much does.
        rule.mainClock.advanceTimeByFrame()
        if (!settle(instrument)) {
            return "the transition into it never finished, so the " +
                "previous screen is still composed — " + describeScreen()
        }
        return null
    }

    /**
     * Has the grid arrived?
     *
     * The same mistake as [settle], in the other direction, and it cost
     * the sweep seven tools. `goHome` used to be satisfied by the grid's
     * title appearing, but the title is in the tree the instant the
     * transition begins — while the tool that is leaving is still
     * composed, back arrow and all. So the sweep pressed "Light meter",
     * found a back button that belonged to the WiFi analyzer ghost,
     * believed the Light meter had opened, and then spent five seconds
     * waiting for a screen that was never coming.
     *
     * The grid's own arrival test is the mirror image of a tool's: no
     * back button on the display, and one of its tiles actually in
     * place.
     */
    private fun arrivedHome(): Boolean {
        if (visibleBackButtonsOnScreen() != 0) return false
        val metrics = rule.activity.resources.displayMetrics
        return read {
            rule.onAllNodes(hasContentDescription(FIRST_TILE))
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .any { node ->
                    val b = node.boundsInRoot
                    val cx = (b.left + b.right) / 2f
                    cx in 0f..metrics.widthPixels.toFloat() &&
                        b.width > metrics.widthPixels * 0.1f
                }
        }
    }

    /** Return to the grid, or say precisely why the way back failed. */
    private fun goHome(): String? {
        press(backButton())?.let {
            return "the back button — $it"
        }
        // The grid's own title is a Text, not a labelled control, so it
        // has no content description — an earlier version waited for one
        // that does not exist, on a grid that had come back perfectly.
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && !arrivedHome()) {
            rule.mainClock.advanceTimeByFrame()
            Thread.sleep(40)
        }
        return if (arrivedHome() || waitForText("KraftTools")) null else {
            // The click landed, so the fault is either the transition
            // never completing or the grid not being what came back.
            // Saying which is the difference between a one-line fix and
            // another afternoon.
            val tree = describeScreen()
            if (countNodes("Back to tools") != 0) {
                "the click landed but the transition never finished — " +
                    tree
            } else {
                "the click landed, the tool was left, but the grid is " +
                    "not what came back — $tree"
            }
        }
    }

    /**
     * JUnit's first line of an exception, which is the part that says
     * what went wrong. The rest is a stack trace through the framework.
     */
    private fun Throwable.short(): String =
        message?.lineSequence()?.firstOrNull()?.take(160)
            ?: this::class.simpleName
            ?: "unknown"

    /**
     * Press a control, by touch if that works and by its declared action
     * if it does not.
     *
     * This exists because of one specific failure: on the torch, and
     * only the torch, `performClick()` failed with "Failed to inject
     * touch input" — instantly, in zero milliseconds, which is the
     * framework declining before it waits for anything. Tapping that
     * same back button by hand, from the grid, returns to the grid
     * every time, so the app was never the problem and reporting the
     * torch as unreachable would have been false.
     *
     * Both halves matter. Touch injection is the real path and is tried
     * first, because a control that cannot be touched IS broken. The
     * fallback asks whether the control is at least wired to its action,
     * and the answer is recorded rather than swallowed: a tool whose
     * back arrow injects as a touch but does nothing is a different
     * bug from one whose arrow is dead, and the message says which.
     */
    /**
     * Every labelled control on the screen, with its size and whether it
     * is clickable.
     *
     * This exists because "Failed to perform OnClick action" says the
     * node has no action, and not which node was found or what else
     * was there instead. On the torch the node carrying
     * "Back to tools" has no action, while on the compass the identically
     * named node does — and a real back button on a screen is either
     * pressable or it is not. Printing the tree turns that from a
     * mystery into a fact.
     */
    private fun describeScreen(): String = read {
        val d = density()
        // Likewise: `hasContentDescription("")` matches an empty
        // description, not any description.
        rule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
        )
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .map { node ->
                val name = node.config[SemanticsProperties.ContentDescription]
                    .joinToString("").take(28)
                // OnClick is an action, not a value, so it lives in the
                // config's keys rather than being read out of it.
                val clickable =
                    node.config.contains(SemanticsActions.OnClick)
                val w = (node.size.width / d).toInt()
                val h = (node.size.height / d).toInt()
                val at = node.boundsInRoot
                val metrics = rule.activity.resources.displayMetrics
                val offScreen = at.right <= 0f || at.left >= metrics.widthPixels ||
                    at.bottom <= 0f || at.top >= metrics.heightPixels
                "\"$name\" ${w}x${h}dp at " +
                    "(${at.left.toInt()},${at.top.toInt()})" +
                    (if (clickable) " clickable" else " NOT clickable") +
                    (if (offScreen) " OFF SCREEN" else "")
            }.joinToString("; ")
    }.ifBlank { "the screen has no labelled controls at all" }

    /**
     * A node's label, or nothing.
     *
     * A control with no content description has no such key at all, and
     * reading it as though it did is an exception rather than an empty
     * string — which is itself the finding: a clickable that a screen
     * reader cannot name.
     */
    private fun label(node: SemanticsNode): String =
        if (node.config.contains(SemanticsProperties.ContentDescription)) {
            node.config[SemanticsProperties.ContentDescription].joinToString("")
        } else {
            ""
        }

    /**
     * Every node in the merged tree, with its size, position and words.
     *
     * Written because two different dumps of the same screen disagreed:
     * uiautomator saw two 363x48dp controls on the colour sampler, and
     * the Compose tree saw an 89x48 and a 126x40, overlapping, near the
     * bottom. One of them is looking at a screen the other never had —
     * most likely the palette swatches, which exist only once the camera
     * has delivered. Without the full tree this is a coin flip.
     */
    private fun describeTree(): String = read {
        val d = density()
        rule.onAllNodes(SemanticsMatcher("anything") { true })
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .mapNotNull { node ->
                val words = buildString {
                    if (node.config.contains(SemanticsProperties.Text)) {
                        append(node.config[SemanticsProperties.Text]
                            .joinToString("") { it.text })
                    }
                    if (node.config.contains(SemanticsProperties.ContentDescription)) {
                        append("[")
                        append(node.config[SemanticsProperties.ContentDescription]
                            .joinToString(""))
                        append("]")
                    }
                }.take(22)
                val at = node.boundsInRoot
                if (at.width < 1f || at.height < 1f) return@mapNotNull null
                val w = (at.width / d).toInt()
                val h = (at.height / d).toInt()
                if (w < 20 || h < 12) return@mapNotNull null
                "${w}x$h@${at.left.toInt()},${at.top.toInt()}" +
                    (if (node.config.contains(SemanticsActions.OnClick)) "*" else "") +
                    (if (words.isBlank()) "" else " '$words'")
            }.joinToString(" ")
    }

    /** Every clickable control on screen, with its size, for the record. */
    private fun describeAll(): String = read {
        val d = density()
        rule.onAllNodes(clickable)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .joinToString(" | ") { node ->
                val named = label(node).ifBlank { "(none)" }.take(18)
                val at = node.boundsInRoot
                "$named ${(node.size.width / d).toInt()}x" +
                    "${(node.size.height / d).toInt()}dp@${at.left.toInt()}"
            }
    }

    private fun press(node: SemanticsNodeInteraction): String? =
        runCatching { node.performClick() }.exceptionOrNull()?.let { touch ->
            runCatching {
                node.performSemanticsAction(SemanticsActions.OnClick)
            }.fold(
                onSuccess = {
                    "tapping it failed to inject a touch (${touch.short()}) " +
                        "but its action fired, so it is reachable"
                },
                onFailure = { action ->
                    "tapping it failed to inject a touch " +
                        "(${touch.short()}) and its action did not fire " +
                        "either (${action.short()}). " + describeScreen()
                },
            )
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

    /**
     * Say where the sweep is, as it goes.
     *
     * A suite killed by a timeout says nothing about how far it got,
     * and this one was being killed by a timeout. Six hundred seconds
     * of "TestTimedOutException" is not a fact about the app, it is the
     * absence of one. Instrumented stdout is swallowed by the runner,
     * so this goes to logcat, where it outlives the process.
     */
    private fun trace(message: String) {
        android.util.Log.i("KraftUi", message)
    }

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
        val began = System.currentTimeMillis()
        var visited = 0

        for ((tile, expectedInstrument) in tools) {
            val toolBegan = System.currentTimeMillis()
            trace("ENTER $tile")
            fun took() = "%.0fs".format(
                (System.currentTimeMillis() - toolBegan) / 1000f,
            )

            val why = open(tile, expectedInstrument)
            trace("  opened=$why (${took()})")
            if (why != null) {
                problems += "$tile did not open — $why (${took()})"
                break
            }
            val claimsMissing = read { missingHardwareText() }
            trace("  claimsMissingHardware=$claimsMissing (${took()})")
            if (claimsMissing) {
                problems += "$tile claimed the hardware is missing"
            }
            // Every tool, not eleven of them. Vibration, light, EMF and
            // barometer were listed as expecting nothing, and those four
            // are exactly the four that draw a trace and so had no name
            // to announce — the check was skipped on the only screens
            // that needed it.
            val named = waitForNode(expectedInstrument, budgetMs = 12_000)
            trace("  instrument=$named (${took()})")
            if (!named) {
                problems += "$tile exposed no instrument named " +
                    "\"$expectedInstrument\" (${took()})"
            }
            read {
                rule.onAllNodes(clickable)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
            }.forEach { node ->
                val w = node.size.width / density()
                val h = node.size.height / density()
                if (w < 47f || h < 47f) {
                    // Say which control. "Color picker has a 127x40dp
                    // target" is a fact to look up; "the third swatch in
                    // the palette row" is a fix.
                    val named = label(node).ifBlank { "(no label)" }
                    val at = node.boundsInRoot
                    problems += "$tile has a ${"%.0f".format(w)}x" +
                        "${"%.0f".format(h)}dp target: $named at " +
                        "(${at.left.toInt()},${at.top.toInt()}). " +
                        "The whole screen: ${read { describeTree() }}"
                }
            }
            val gone = goHome()
            trace("  goHome=$gone (${took()})")
            gone?.let {
                problems += "$tile could not be left — $it (${took()})"
                break
            }

            visited++
            trace("  OK, back on the grid (${took()})")
            // The sweep's own budget, checked between tools, so that a
            // run which is going too slowly says so and names the tool
            // it was on. It used to have no budget of its own: its
            // worst case was four waits per tool at 4s, 8s, 15s and 4s
            // — 31 seconds times fourteen, 434 — against a 420 second
            // ceiling, so the worst case was a silent kill with no
            // message and nothing learned. A test that cannot finish
            // should say why it could not finish.
            val spent = System.currentTimeMillis() - began
            if (spent > SWEEP_BUDGET_MS) {
                problems += "the sweep took ${spent / 1000}s of its " +
                    "${SWEEP_BUDGET_MS / 1000}s budget to reach tool " +
                    "$visited of ${tools.size}, and stopped there " +
                    "rather than being killed by the timeout with " +
                    "nothing to show"
                break
            }
        }

        trace("SWEEP DONE, $visited of ${tools.size}, problems=$problems")
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
        val why = open("Tally + stopwatch", "Tally marks")
        assertTrue("the tally did not open — ${why.orEmpty()}", why == null)
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
