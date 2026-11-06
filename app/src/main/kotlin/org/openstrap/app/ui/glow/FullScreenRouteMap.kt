package org.openstrap.app.ui.glow

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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
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
 * What the full-screen map keeps through a rotation: whether it is open, Route only and the selected split. It lives in
 * the Route card, never inside the dialog: `rememberSaveable` inside a dialog's window is lost when the activity is
 * recreated.
 */
@Stable
internal class RouteMapState {
    var open by mutableStateOf(false)
    var routeOnly by mutableStateOf(false)
    var selected by mutableStateOf<Int?>(null)

    companion object {
        val Saver = listSaver<RouteMapState, Any?>(
            save = { listOf(it.open, it.routeOnly, it.selected) },
            restore = { RouteMapState().apply { open = it[0] as Boolean; routeOnly = it[1] as Boolean; selected = it[2] as Int? } },
        )
    }
}

@Composable
internal fun rememberRouteMapState(): RouteMapState = rememberSaveable(saver = RouteMapState.Saver) { RouteMapState() }

/** The route map over the whole screen, in its own window so it covers the rail, the panes and the bars; Back or [onClose] closes it. */
@Composable
internal fun FullScreenRouteMap(summary: PhoneSummary, state: RouteMapState, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        NoWindowAnimation()
        FullScreenRouteMapContent(summary, state, onClose)
    }
}

/** A rotation builds the dialog again, so its window's entrance animation would show the map popping open once more. */
@Composable
private fun NoWindowAnimation() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { window?.setWindowAnimations(-1) }
}

/**
 * The map and its pills: Close top-end, Route only (or Show map) top-start, and Fit route top-centre
 * once a drag has stopped the map following the route. Km markers sit along the line, and the split picker along the
 * bottom draws the chosen kilometre with a white casing and fits it.
 */
@Composable
internal fun FullScreenRouteMapContent(summary: PhoneSummary, state: RouteMapState, onClose: () -> Unit) {
    val stretches = rememberZoneStretches(summary)
    val routeOnly = state.routeOnly
    var follow by remember { mutableStateOf(true) }
    val legs = remember(summary) { Route.legs(summary.route, summary.pauses) }
    val kmMarkers = remember(legs) { legs.filter { it.split.meters >= 1000.0 }.map { it.split.index + 1 to it.points.last() } }
    val rows = remember(summary) { splitRowsFor(summary, summary.route) }
    val selected = state.selected
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
            IconPill(stringResource(routeOnlyLabel), { state.routeOnly = !routeOnly }, Modifier.align(Alignment.TopStart))
            if (!follow) {
                IconPill(stringResource(R.string.glow_summary_map_fit), { follow = true }, Modifier.align(Alignment.TopCenter))
            }
            IconPill(stringResource(R.string.glow_summary_map_close), onClose, Modifier.align(Alignment.TopEnd))
            if (rows.isNotEmpty()) {
                SplitPicker(
                    rows,
                    selected,
                    onSelect = {
                        state.selected = it
                        follow = true
                    },
                    Modifier.align(Alignment.BottomStart).onSizeChanged { pickerHeight = with(density) { it.height.toDp() } },
                )
            }
        }
    }
}
