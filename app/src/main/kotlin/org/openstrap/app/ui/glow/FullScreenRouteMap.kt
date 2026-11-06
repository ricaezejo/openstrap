package org.openstrap.app.ui.glow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.data.Route
import org.openstrap.app.ui.dashboard.LocalHeartZones
import org.openstrap.app.ui.workout.PhoneSummary

/** The route cut into stretches in the colour of the heart zone each was run in, for the card's map and the full one. */
@Composable
internal fun rememberZoneStretches(summary: PhoneSummary): List<Pair<List<Route.Point>, Int>> {
    val heartZones = LocalHeartZones.current
    return remember(summary, heartZones) {
        Route.byZone(summary.route, summary.history, summary.analysis.maxHeartRate, heartZones)
    }
}

/**
 * The route map as a view that takes the page's place, like the share screen: no window of its own, so its state saves
 * and restores with the page's. The tab bar is hidden while it shows; Back or [onClose] closes it.
 */
@Composable
internal fun FullScreenRouteMapScreen(summary: PhoneSummary, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val hideTabBar = LocalHideTabBar.current
    DisposableEffect(Unit) {
        hideTabBar(true)
        onDispose { hideTabBar(false) }
    }
    FullScreenRouteMapContent(summary, onClose)
}

/**
 * The map and its pills: Close top-end, Route only (or Show map) top-start, and Fit route top-centre
 * once a drag has stopped the map following the route. Km markers sit along the line, and the split picker along the
 * bottom draws the chosen kilometre with a white casing and fits it.
 */
@Composable
internal fun FullScreenRouteMapContent(summary: PhoneSummary, onClose: () -> Unit) {
    val stretches = rememberZoneStretches(summary)
    var routeOnly by rememberSaveable { mutableStateOf(false) }
    var follow by remember { mutableStateOf(true) }
    val legs = remember(summary) { Route.legs(summary.route, summary.pauses) }
    val kmMarkers = remember(legs) { legs.filter { it.split.meters >= 1000.0 }.map { it.split.index + 1 to it.points.last() } }
    val rows = remember(summary) { splitRowsFor(summary, summary.route) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var pickerHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    // From the screen's bottom edge to the picker's top, so the fitted route and the map's credit stay above it.
    val underPicker = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + 12.dp + pickerHeight
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        WorkoutMap(
            stretches,
            routeOnly,
            Modifier.fillMaxSize(),
            follow = follow,
            fitPadding = PaddingValues(start = 24.dp, top = 96.dp, end = 24.dp, bottom = maxOf(96.dp, underPicker + 24.dp)),
            creditBottom = underPicker + 8.dp,
            onPanned = { follow = false },
            kmMarkers = kmMarkers,
            highlight = selected?.let { legs.getOrNull(it)?.points }.orEmpty(),
        )
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
            val routeOnlyLabel = if (routeOnly) R.string.glow_summary_map_show_map else R.string.glow_summary_map_route_only
            IconPill(stringResource(routeOnlyLabel), { routeOnly = !routeOnly }, Modifier.align(Alignment.TopStart))
            if (!follow) {
                IconPill(stringResource(R.string.glow_summary_map_fit), { follow = true }, Modifier.align(Alignment.TopCenter))
            }
            IconPill(stringResource(R.string.glow_summary_map_close), onClose, Modifier.align(Alignment.TopEnd))
            if (rows.isNotEmpty()) {
                SplitPicker(
                    rows,
                    selected,
                    onSelect = {
                        selected = it
                        follow = true
                    },
                    Modifier.align(Alignment.BottomStart).onSizeChanged { pickerHeight = with(density) { it.height.toDp() } },
                )
            }
        }
    }
}
