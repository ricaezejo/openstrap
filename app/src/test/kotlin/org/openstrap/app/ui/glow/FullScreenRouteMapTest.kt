package org.openstrap.app.ui.glow

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openstrap.app.R
import org.openstrap.app.data.HeartZones
import org.openstrap.app.data.Route
import org.openstrap.app.data.WorkoutRecorder
import org.openstrap.app.data.db.AppWorkout
import org.openstrap.app.testing.ComposeRules
import org.openstrap.app.ui.workout.PhoneSummary
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The full-screen route map: its pills, and the summary's route card map that opens it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FullScreenRouteMapTest {
    @get:Rule val rules = ComposeRules()

    private val start = 1_792_000_000L

    private fun text(id: Int) = ApplicationProvider.getApplicationContext<android.app.Application>().getString(id)

    /** A 3 m/s run east along the equator, a fix a second. */
    private fun summary(): PhoneSummary {
        val w = AppWorkout(start, start + 40, 1)
        val route = (0..40).map { Route.Point(start + it, 0.0, it * 3.0 / 111_195.0, 5f) }
        val analysis = WorkoutRecorder.analyse(w, emptyList(), 190, HeartZones.STANDARD)
        return PhoneSummary(analysis, emptyList(), start, start + 40, null, route = route)
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        rules.compose.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { content() } }
    }

    @Test fun `closing calls onClose`() {
        var closed = 0
        show { FullScreenRouteMapContent(summary()) { closed++ } }
        rules.compose.onNodeWithText(text(R.string.glow_summary_map_close)).performClick()
        assertEquals(1, closed)
    }

    @Test fun `route only can be switched on and back`() {
        show { FullScreenRouteMapContent(summary()) {} }
        rules.compose.onNodeWithText(text(R.string.glow_summary_map_route_only)).performClick()
        rules.compose.onNodeWithText(text(R.string.glow_summary_map_show_map)).performClick()
        rules.compose.onNodeWithText(text(R.string.glow_summary_map_route_only)).assertExists()
    }

    @Test fun `fit route shows only after the map was panned`() {
        show { FullScreenRouteMapContent(summary()) {} }
        rules.compose.onAllNodesWithText(text(R.string.glow_summary_map_fit)).assertCountEquals(0)
    }

    @Test fun `the summary's route card map opens it`() {
        var opened = 0
        show { RouteMap(summary(), onOpen = { opened++ }) }
        rules.compose.onNodeWithContentDescription(text(R.string.glow_summary_map_open)).performClick()
        assertEquals(1, opened)
    }
}
