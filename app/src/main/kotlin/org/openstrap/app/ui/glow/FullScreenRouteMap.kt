package org.openstrap.app.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

/** The route map over the whole screen, in its own window so it covers the bars; Back or [onClose] closes it. */
@Composable
internal fun FullScreenRouteMap(summary: PhoneSummary, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) { FullScreenRouteMapContent(summary, onClose) }
}

/**
 * The map and its pills, without the window: Close top-end, Route only (or Show map) top-start, and Fit route top-centre
 * once a drag has stopped the map following the route. The bottom edge stays free for what is put there.
 */
@Composable
internal fun FullScreenRouteMapContent(summary: PhoneSummary, onClose: () -> Unit) {
    val stretches = rememberZoneStretches(summary)
    var routeOnly by rememberSaveable { mutableStateOf(false) }
    var follow by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        WorkoutMap(
            stretches,
            routeOnly,
            Modifier.fillMaxSize(),
            follow = follow,
            fitPadding = PaddingValues(horizontal = 24.dp, vertical = 96.dp),
            onPanned = { follow = false },
        )
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
            val routeOnlyLabel = if (routeOnly) R.string.glow_summary_map_show_map else R.string.glow_summary_map_route_only
            IconPill(stringResource(routeOnlyLabel), { routeOnly = !routeOnly }, Modifier.align(Alignment.TopStart))
            if (!follow) {
                IconPill(stringResource(R.string.glow_summary_map_fit), { follow = true }, Modifier.align(Alignment.TopCenter))
            }
            IconPill(stringResource(R.string.glow_summary_map_close), onClose, Modifier.align(Alignment.TopEnd))
        }
    }
}
