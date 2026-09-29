package com.krafttools.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * WiFi analyzer.
 *
 * The channel maths moved to WifiMath.kt because three of it was
 * wrong, and each made the graph lie rather than merely round:
 *
 * - Channel 14 is the one 2.4 GHz channel not on f = 2407 + 5n. It is
 *   2484 MHz, and the general formula produced 15 — a channel that
 *   does not exist.
 * - 2.4 GHz ch1 and 6 GHz ch1 are both "1". The old graph keyed on a
 *   bare Int, so on any 6 GHz device the two bands merged into one
 *   bar. The key carries the band now.
 * - Level 0 means "the access point did not report a strength", and
 *   the old mapping turned that into a FULL bar, so hidden networks
 *   sorted to the top of the list.
 *
 * Two honesty problems on top. The bar chart was titled "crowded
 * channels" while plotting the single strongest network on each —
 * twenty weak networks drew exactly like one strong one — so it now
 * plots the count. And the advice was to move to "the shortest bar",
 * when the graph only drew occupied channels, so that always meant
 * joining the channel that was already busy. Empty channels are drawn
 * now, and the recommendation prefers one.
 */
private data class Net(
    val ssid: String,
    val level: Int,
    val channel: WifiChannel?,
)

@Composable
fun WifiScreen(onBack: () -> Unit) {
    PermissionGate(
        permission = android.Manifest.permission.ACCESS_FINE_LOCATION,
        tool = "WiFi analyzer",
        reason = "Android only hands scan results (network names and " +
            "signal strengths) to apps holding location permission. " +
            "Your location is never read, stored, or sent anywhere — " +
            "the permission is just the key to the scan API.",
        onBack = onBack,
    ) {
        WifiBody(onBack)
    }
}

@Composable
private fun WifiBody(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val manager = remember {
        context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    var nets by remember { mutableStateOf<List<Net>>(emptyList()) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var everScanned by rememberSaveable { mutableStateOf(false) }
    var rejected by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var band by rememberSaveable { mutableStateOf(WifiBand.BAND_2) }

    // The radio being off is a different problem from location being
    // off, and the old empty-state blamed location either way.
    val wifiOn = remember { manager.isWifiEnabled }

    fun readCached() {
        try {
            nets = manager.scanResults
                .filter { it.SSID.isNotBlank() }
                .distinctBy { it.BSSID }
                .map { r -> Net(r.SSID, r.level, channelOf(r.frequency)) }
                .sortedByDescending { it.level }
        } catch (_: SecurityException) {
            nets = emptyList()
        }
    }

    DisposableEffect(Unit) {
        readCached()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    return
                }
                scanning = false
                everScanned = true
                rejected = !intent.getBooleanExtra(
                    WifiManager.EXTRA_RESULTS_UPDATED, false,
                )
                readCached()
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Group by band-qualified key, and count the networks: crowding is
    // a count, which is what the chart is claiming to show.
    val loads = remember(nets) {
        nets.groupBy { it.channel?.key ?: "?:0" }
            .mapNotNull { (key, group) ->
                val ch = group.first().channel
                    ?: return@mapNotNull null
                ChannelLoad(
                    channel = ch,
                    strongestDbm = group.maxOfOrNull { it.level },
                    count = group.size,
                )
            }
            .sortedBy { it.channel.number }
    }
    val inBand = loads.filter { it.channel.band == band }
    val occupied = inBand.associateBy { it.channel.number }
    // Show the occupied channels AND the non-overlapping ones, so the
    // chart is a spectrum rather than three slabs. Only those two sets
    // matter: channels nobody is on that overlap a neighbour are not
    // worth a bar.
    val numbers = (occupied.keys + nonOverlapping(band).map { it.number })
        .distinct()
        .sorted()
    val shown = numbers.map { n ->
        occupied[n] ?: ChannelLoad(
            WifiChannel(band, n),
            null,
            if (nonOverlapping(band).any { it.number == n }) 0 else 1,
        )
    }
    val recommendation = remember(loads) { recommendChannel(loads, band) }
    val selectedNets = nets.filter { it.channel?.key == selected }

    ToolScaffold("WiFi analyzer", onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ToolColumn(
                padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                modifier = Modifier.weight(1f),
            ) {
                ReadingHeader(
                    value = "${nets.size}",
                    unit = if (nets.size == 1) "network" else "networks",
                    status = if (!wifiOn) {
                        "WiFi is switched off"
                    } else if (recommendation != null) {
                        "try ${recommendation.label}"
                    } else {
                        "scanning"
                    },
                    live = scanning,
                )

                // Band selector: the three radios are genuinely
                // different, and the chart is per band.
                SingleChoiceBandRow(
                    selected = band,
                    onPick = {
                        Haptics.tick(view)
                        band = it
                        selected = null
                    },
                )

                ChannelChart(
                    loads = shown,
                    recommended = recommendation,
                    onPick = { ch ->
                        Haptics.tick(view)
                        selected = if (selected == ch.key) null else ch.key
                    },
                )

                if (!wifiOn) {
                    ToolHint(
                        "The WiFi radio is off, so there is nothing to " +
                            "hear. Switch WiFi on and scan again — this is " +
                            "a different problem from location being off.",
                        warn = true,
                    )
                } else if (rejected) {
                    ToolHint(
                        "Android refused the scan. It throttles them, and " +
                            "location services must be on. Wait a few " +
                            "seconds and try again.",
                        warn = true,
                    )
                } else if (nets.isEmpty() && everScanned) {
                    ToolHint(
                        "No networks returned. Location services must be " +
                            "switched on for scans to return — Android's " +
                            "rule, not ours.",
                    )
                }

                if (selected != null) {
                    SectionLabel("On ${selected}")
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(selectedNets, key = { it.ssid + it.level }) { n ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(10.dp),
                                    )
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = n.ssid,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = if (n.level == 0) {
                                        "— dBm"
                                    } else {
                                        "%d dBm".format(n.level)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                    ToolHint(
                        "Bars show how many networks share each channel, " +
                            "so a crowded one is obvious. Empty channels are " +
                            "drawn too — a quiet one is the one worth moving " +
                            "to. Tap a bar to list its networks.",
                    )
                }
            }

            Button(
                onClick = {
                    Haptics.confirm(view)
                    rejected = false
                    val ok = try {
                        manager.startScan()
                    } catch (_: SecurityException) {
                        false
                    }
                    if (ok) {
                        scanning = true
                    } else {
                        // Throttled or refused: say so rather than
                        // showing a spinner that never resolves.
                        everScanned = true
                        rejected = nets.isEmpty()
                    }
                },
                enabled = !scanning,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(if (scanning) "Scanning…" else "Scan now")
            }
        }
    }
}

@Composable
private fun SingleChoiceBandRow(
    selected: WifiBand,
    onPick: (WifiBand) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val bands = listOf(WifiBand.BAND_2, WifiBand.BAND_5, WifiBand.BAND_6)
        bands.forEachIndexed { i, b ->
            SegmentedButton(
                selected = b == selected,
                onClick = { onPick(b) },
                shape = SegmentedButtonDefaults.itemShape(i, bands.size),
                colors = instrumentSegmentedColors(),
            ) {
                Text(b.short)
            }
        }
    }
}

/**
 * The channel chart. Height is the COUNT of networks — the chart is
 * labelled "crowded", so it has to show crowding. The recommended
 * channel is outlined, and a channel nobody is on is a flat stub
 * rather than an absence, because "nothing here" is the answer the
 * user is looking for.
 */
@Composable
private fun ChannelChart(
    loads: List<ChannelLoad>,
    recommended: WifiChannel?,
    onPick: (WifiChannel) -> Unit,
) {
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val idle = MaterialTheme.colorScheme.outline
    val rec = warnAmber
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = androidx.compose.ui.text.TextStyle(
        color = label,
        fontSize = 9.sp,
        fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
    )
    val maxCount = (loads.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (loads.isEmpty()) return@Canvas
            val gap = 2.dp.toPx()
            val w = (size.width - gap * (loads.size - 1)) / loads.size
            val base = size.height * 0.86f
            val maxH = size.height * 0.72f
            loads.forEachIndexed { i, load ->
                val x = i * (w + gap)
                val empty = load.count == 0
                val h = if (empty) {
                    3.dp.toPx()
                } else {
                    // dp-accurate stroke weights: the old raw pixel
                    // gap made the bars 2dp apart on a 3x screen while
                    // the labels were laid out 6dp apart, so the two
                // did not line up.
                    (maxH * (load.count.toFloat() / maxCount)).coerceAtLeast(6f)
                }
                drawRoundRect(
                    color = if (empty) idle else track,
                    topLeft = Offset(x, base - 3.dp.toPx()),
                    size = Size(w, 3.dp.toPx()),
                )
                if (!empty) {
                    drawRoundRect(
                        color = if (load.crowded) warnAmber else bar,
                        topLeft = Offset(x, base - h),
                        size = Size(w, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                    )
                }
                if (recommended?.key == load.channel.key) {
                    drawRoundRect(
                        color = rec,
                        topLeft = Offset(x, base - h - 4.dp.toPx()),
                        size = Size(w, h + 6.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
                    )
                }
                val text = load.channel.number.toString()
                val layout = measurer.measure(text, labelStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        (x + w / 2f - layout.size.width / 2f).coerceIn(
                            0f,
                            (size.width - layout.size.width).coerceAtLeast(0f),
                        ),
                        base + 2f,
                    ),
                )
            }
        }
        Text(
            text = "networks per channel — taller is more crowded",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
