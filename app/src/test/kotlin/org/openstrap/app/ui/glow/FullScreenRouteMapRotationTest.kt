package org.openstrap.app.ui.glow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
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

/** Rotating the phone saves the screen's state and builds it again from that: the full-screen map must come back as it was. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FullScreenRouteMapRotationTest {
    @get:Rule val rules = ComposeRules()

    private val start = 1_792_000_000L
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private fun text(id: Int) = app.getString(id)

    /** A 3 m/s run east along the equator, a fix a second: 1167 s is about 3.5 km, so Km 1 to 3 and a last part. */
    private fun summary(seconds: Int = 1167): PhoneSummary {
        val w = AppWorkout(start, start + seconds, 1)
        val route = (0..seconds).map { Route.Point(start + it, 0.0, it * 3.0 / 111_195.0, 5f) }
        val analysis = WorkoutRecorder.analyse(w, emptyList(), 190, HeartZones.STANDARD)
        return PhoneSummary(analysis, emptyList(), start, start + seconds, null, route = route)
    }

    private fun km(n: Int) = app.getString(R.string.workout_summary_split_km, n)
    private val last get() = text(R.string.workout_summary_split_last).substringBefore('%')

    private fun rotating(content: @Composable () -> Unit): StateRestorationTester {
        val tester = StateRestorationTester(rules.compose)
        tester.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { content() } }
        return tester
    }

    private fun assertOnlyKm2Selected() {
        rules.compose.onNodeWithText(km(2)).assertIsSelected()
        rules.compose.onNodeWithText(km(1)).assertIsNotSelected()
        rules.compose.onNodeWithText(km(3)).assertIsNotSelected()
        rules.compose.onNodeWithText(last, substring = true).assertIsNotSelected()
    }

    @Test fun `the content keeps the selected split through a rotation`() {
        val tester = rotating { FullScreenRouteMapContent(summary()) {} }
        rules.compose.onNodeWithText(km(2)).performClick()
        assertOnlyKm2Selected()
        tester.emulateSavedInstanceStateRestore()
        assertOnlyKm2Selected()
    }

    @Test fun `the screen keeps the selected split through a rotation`() {
        val tester = rotating { FullScreenRouteMapScreen(summary()) {} }
        rules.compose.onNodeWithText(km(2)).performClick()
        assertOnlyKm2Selected()
        tester.emulateSavedInstanceStateRestore()
        assertOnlyKm2Selected()
    }

    @Test fun `the screen keeps Route only through a rotation`() {
        val tester = rotating { FullScreenRouteMapScreen(summary()) {} }
        rules.compose.onNodeWithText(text(R.string.glow_summary_map_route_only)).performClick()
        rules.compose.onNodeWithText(text(R.string.glow_summary_map_show_map)).assertExists()
        tester.emulateSavedInstanceStateRestore()
        rules.compose.onNodeWithText(text(R.string.glow_summary_map_show_map)).assertExists()
    }

    @Test fun `the screen hides the tab bar while it is shown`() {
        var hidden = false
        rotating { CompositionLocalProvider(LocalHideTabBar provides { hidden = it }) { FullScreenRouteMapScreen(summary()) {} } }
        rules.compose.waitForIdle()
        assertTrue(hidden)
    }
}
