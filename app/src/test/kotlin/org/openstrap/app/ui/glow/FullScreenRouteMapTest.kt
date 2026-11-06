package org.openstrap.app.ui.glow

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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

    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun text(id: Int) = app.getString(id)

    /** A 3 m/s run east along the equator, a fix a second, [seconds] long. */
    private fun summary(seconds: Int = 40): PhoneSummary {
        val w = AppWorkout(start, start + seconds, 1)
        val route = (0..seconds).map { Route.Point(start + it, 0.0, it * 3.0 / 111_195.0, 5f) }
        val analysis = WorkoutRecorder.analyse(w, emptyList(), 190, HeartZones.STANDARD)
        return PhoneSummary(analysis, emptyList(), start, start + seconds, null, route = route)
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

    @Test fun `the picker lists each kilometre and the last part`() {
        show { FullScreenRouteMapContent(summary(seconds = 1167)) {} }
        for (km in 1..3) rules.compose.onNodeWithText(app.getString(R.string.workout_summary_split_km, km)).assertExists()
        val last = text(R.string.workout_summary_split_last).substringBefore('%')
        rules.compose.onNodeWithText(last, substring = true).assertExists()
    }

    @Test fun `tapping a split selects it and tapping it again clears it`() {
        show { FullScreenRouteMapContent(summary(seconds = 1167)) {} }
        val km2 = rules.compose.onNodeWithText(app.getString(R.string.workout_summary_split_km, 2))
        km2.performClick()
        km2.assertIsSelected()
        km2.performClick()
        km2.assertIsNotSelected()
    }

    @Test fun `a run under a kilometre shows only its last-part chip`() {
        show { FullScreenRouteMapContent(summary(seconds = 134)) {} }
        rules.compose.onAllNodesWithText(app.getString(R.string.workout_summary_split_km, 1)).assertCountEquals(0)
        val chip = rules.compose.onNodeWithText(text(R.string.workout_summary_split_last).substringBefore('%'), substring = true)
        chip.performClick()
        chip.assertIsSelected()
        chip.performClick()
        chip.assertIsNotSelected()
    }
}
