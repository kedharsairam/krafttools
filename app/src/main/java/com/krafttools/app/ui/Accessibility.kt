package com.krafttools.app.ui

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * Accessibility for the instruments.
 *
 * Fourteen of these screens are a `Canvas` and a number. A Canvas has
 * no accessible content by construction, so before this file existed
 * a screen reader announced *nothing at all* on the spirit level, the
 * compass, the protractor, the tally, the torch or the WiFi spectrum —
 * not a poor description, none. A blind user could not tell that a
 * tool was working, let alone what it read.
 *
 * Every instrument therefore carries a spoken label and a spoken
 * value, and every tappable instrument says what tapping will do.
 *
 * Three rules, and they are not interchangeable:
 *
 *  - `label` says WHAT the thing is. It never changes.
 *  - `value` says what it currently reads. It changes constantly, so
 *    it goes in `stateDescription` rather than `contentDescription`;
 *    putting a changing number in the content description makes
 *    TalkBack re-announce the whole label on every sample, several
 *    times a second, which is unusable rather than merely verbose.
 *  - `hint` says what an action will do, and is only present when
 *    there is an action.
 */
@Composable
fun Modifier.instrumentSemantics(
    label: String,
    value: String,
    hint: String? = null,
    onClickAction: (() -> Unit)? = null,
    role: Role? = if (onClickAction != null) Role.Button else null,
    /** A reading that updates several times a second should be polite,
     *  so a screen reader finishes the current sentence first. */
    live: Boolean = true,
): Modifier = semantics(mergeDescendants = true) {
    contentDescription = label
    stateDescription = value
    if (live) liveRegion = LiveRegionMode.Polite
    role?.let { this.role = it }
    if (onClickAction != null) {
        // The action's own label is the hint, so TalkBack announces
        // "double tap to lock a bearing" rather than a bare "double
        // tap" that gives the user no idea what they are about to do.
        onClick(
            label = hint ?: label,
            action = { onClickAction(); true },
        )
    }
}

/** The minimum touch target this app ships, in dp. */
val MinTouchTarget = 48.dp

/**
 * A control that must be at least [MinTouchTarget] tall.
 *
 * Material's own defaults fall short of it: a `SegmentedButton` row is
 * 40dp and a `FilterChip` is 32dp, both of which are below the 48dp
 * that Android's accessibility guidance asks for and awkward to hit
 * one-handed. Applied consistently so the whole app has one target
 * size rather than three.
 *
 * BOTH dimensions, and that is not a detail. This was
 * `heightIn(min = 48.dp)` for most of its life, which quietly fixed the
 * height of a square control and left the width at Material's 40dp —
 * so the shared back button measured 40 x 48 and the instrumented
 * sweep reported it on every one of the fourteen tools. A modifier
 * named `touchTarget` that only constrains one axis is a lie in a
 * smaller way than the bug it was meant to prevent.
 */
@Composable
fun Modifier.touchTarget(): Modifier =
    sizeIn(minWidth = MinTouchTarget, minHeight = MinTouchTarget)
