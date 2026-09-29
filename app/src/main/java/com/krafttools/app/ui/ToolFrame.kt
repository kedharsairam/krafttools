package com.krafttools.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable

/**
 * The frame every tool sits in.
 *
 * Fourteen tools that each invent their own chrome are fourteen
 * different-looking apps. This file is the answer to that: one top
 * bar, one body rhythm, one readout, one hint, one way to say "this
 * phone cannot do this". A tool supplies its hero and its dock and
 * inherits everything else, which is what makes the set read as one
 * instrument panel rather than a folder of unrelated screens.
 */

/** The one top bar. Title, back affordance, nothing else. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KraftTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to tools",
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/** Scaffold + [KraftTopBar], the opening of every tool screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = { KraftTopBar(title, onBack) },
        content = content,
    )
}

/**
 * The standard tool body: full viewport, house padding, one rhythm of
 * 12dp between docked elements. Tools that fill the hero use
 * `Modifier.weight(1f)` on it; everything after it docks to the
 * bottom, which is what keeps the layout stable as values change.
 */
@Composable
fun ToolColumn(
    padding: androidx.compose.foundation.layout.PaddingValues,
    modifier: Modifier = Modifier,
    horizontal: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    spacing: androidx.compose.ui.unit.Dp = 12.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    // NOT scrollable, deliberately.
    //
    // Adding `verticalScroll` here looked like the fix for large-font
    // overflow and it broke four of the fourteen tools. A `weight(1f)`
    // child inside a scrollable Column is measured against an infinite
    // constraint and collapses: the WiFi channel chart became a sliver
    // with all ten channel numbers printed on top of one another, and
    // the screen below it was nine hundred pixels of dead space. The
    // same collapse emptied the light meter's trace, the EMF plot and
    // the protractor.
    //
    // The overflow itself is real and is fixed per screen, where the
    // instrument gets a bounded height instead of a weight — see
    // Decibel.kt. A shared helper that only four tools use is not worth
    // a fix that damages those four.
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/**
 * A labeled readout. The shape is always the same — small caps label
 * above, one number below — so a row of them reads as a row of
 * instruments rather than a sentence with commas in it.
 */
@Composable
fun StatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasise: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = if (emphasise) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** A row of readouts, evenly spaced. */
@Composable
fun StatRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/** Section heading for a docked block: small, quiet, uppercase. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** The standard explanatory line. Always last, always quiet. */
@Composable
fun ToolHint(text: String, modifier: Modifier = Modifier, warn: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (warn) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/**
 * The hardware gate. Every tool shows this when its sensor is absent,
 * and it is designed rather than apologetic: the tool is not broken,
 * the phone is simply not the hardware for it. A plain two-line
 * message in the middle of an empty screen is the one place this app
 * used to look unfinished.
 */
@Composable
fun NoSensor(
    name: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Text(
                    text = "No $name here",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "This tool needs hardware your phone doesn't " +
                        "have. Everything else in KraftTools still works.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * A caption whose height never changes.
 *
 * These tools are a number and an instrument, and the instrument takes
 * whatever height is left. That makes the instrument's size a function
 * of *everything above it* — so a line of help text that appears when a
 * state changes, or a status line that grows from one row to two, does
 * not add a sentence. It takes space from the meter, and the meter
 * visibly shrinks and re-grows under the user's thumb. On a measuring
 * instrument that reads as the tool flinching.
 *
 * So the caption has a home with a fixed height, and its contents come
 * and go inside it. One row of type, two at most, and the layout below
 * never learns that anything happened.
 */
@Composable
fun ToolCaption(
    text: String?,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Two rows, reserved whether or not there is anything to
            // say. This is the whole point: the height is constant.
            .heightIn(min = if (maxLines > 1) 40.dp else 20.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (text != null) {
            // A short cross-fade, so a changed sentence reads as a
            // change rather than as a jump. The value is resolved
            // outside the layer lambda, which is not a composable
            // scope.
            val alpha = captionAlpha(text)
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { this.alpha = alpha },
            )
        }
    }
}

/**
 * A caption that eases in when the sentence changes.
 *
 * Keyed on the text, so a new sentence gets its own fade and a repeat
 * of the same sentence does not re-animate. The height is fixed either
 * way — this only softens the swap, it does not make room for it.
 */
@Composable
private fun captionAlpha(text: String): Float {
    val alpha = remember(text) { Animatable(0f) }
    LaunchedEffect(text) { alpha.animateTo(1f, tween(200)) }
    return alpha.value
}

/**
 * The state a tool is in for the fraction of a second before its sensor
 * delivers its first sample.
 *
 * Six screens decided "this phone has no accelerometer" by testing
 * whether a sample had arrived yet. On a cold screen that test was
 * false long enough to flash "No accelerometer here" at the user every
 * single time a tool opened — the sensor was there the whole time, the
 * app had simply not heard from it yet. A tool that tells you your
 * hardware is broken for 200 ms on every visit will be believed the
 * first time, and the truth will look like a fault report.
 *
 * So the brief state says what is true: starting. No number, no claim
 * about the hardware, and it costs one frame.
 */
@Composable
fun ToolStarting(
    tool: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(28.dp),
        ) {
            Text(
                text = "Starting…",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Waiting for the $tool's first reading.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
