package org.openstrap.app.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.Route
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.theme.ControlSurface
import org.openstrap.app.ui.workout.PhoneSummary
import org.openstrap.app.ui.workout.SplitRow
import org.openstrap.app.ui.workout.SummaryCopy

/**
 * The Splits list's rows as chips along the full-screen map, name over time; the [selected] one lit white. Tapping a
 * chip selects it, tapping the selected one clears it ([onSelect] null).
 */
// The rows are remembered once per summary, so a stable list type wouldn't let this skip any more often.
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
internal fun SplitPicker(rows: List<SplitRow>, selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(rows) { i, row ->
            val on = i == selected
            val ink = if (on) ControlSurface else Color.White
            Column(
                Modifier.clip(CircleShape).background(if (on) Color.White else ControlSurface)
                    .pressClickable({ onSelect(if (on) null else i) }, CircleShape)
                    .semantics { this.selected = on }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(row.name, color = ink, style = GlowType.body, maxLines = 1)
                Text(row.time, color = ink.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.caption, maxLines = 1)
            }
        }
    }
}

/** The Splits list's rows along [route], one per [Route.legs] kilometre shown: the list and the map's picker share them. */
internal fun splitRowsFor(summary: PhoneSummary, route: List<Route.Point>): List<SplitRow> = SummaryCopy.splitRows(
    Route.splits(route, summary.pauses),
    summary.history,
    route.firstOrNull()?.t,
    summary.distanceMeters?.takeIf { !summary.distanceFromSteps },
)
