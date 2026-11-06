package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import org.openstrap.app.ui.components.ListDetail
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

/**
 * The map's state through what a real device does: the page moves between the phone's stack and the wide screen's pane
 * (different places in the layout tree), and a rotation builds everything again around the same ViewModelStore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w900dp-h891dp")
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

    private fun assertOnlyKm2Selected() {
        rules.compose.onNodeWithText(km(2)).assertIsSelected()
        rules.compose.onNodeWithText(km(1)).assertIsNotSelected()
        rules.compose.onNodeWithText(km(3)).assertIsNotSelected()
        rules.compose.onNodeWithText(last, substring = true).assertIsNotSelected()
    }

    private val store = ViewModelStore()
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
    private var wide by mutableStateOf(false)
    private var generation by mutableStateOf(0)

    /** The workout page as a pane or a stack page, hosting the map as the real pages do. */
    @Composable private fun Page() {
        val model = viewModel<RouteMapModel>()
        Text("open map", Modifier.clickable { model.open(start) })
        if (model.isOpen(start)) FullScreenRouteMap(summary(), model) { model.close() }
    }

    private fun host() = rules.compose.setContent {
        CompositionLocalProvider(LocalInspectionMode provides true, LocalViewModelStoreOwner provides owner) {
            key(generation) {
                Box(Modifier.width(if (wide) 800.dp else 400.dp)) {
                    ListDetail("p", { true }, { listOf("p") }, 720.dp, detail = { Page() }, list = { Text("list") })
                }
            }
        }
    }

    private fun layout(isWide: Boolean) { wide = isWide; rules.compose.waitForIdle() }
    private fun rotate() { generation++; rules.compose.waitForIdle() }
    private fun assertClosed() = rules.compose.onAllNodesWithText(text(R.string.glow_summary_map_close)).assertCountEquals(0)

    @Test fun `an open map keeps its split and Route only across layouts and rotations`() {
        host()
        rules.compose.onNodeWithText("open map").performClick()
        rules.compose.onNodeWithText(km(2)).performClick()
        rules.compose.onNodeWithText(text(R.string.glow_summary_map_route_only)).performClick()
        layout(true)
        assertOnlyKm2Selected()
        rules.compose.onNodeWithText(text(R.string.glow_summary_map_show_map)).assertExists()
        rotate()
        assertOnlyKm2Selected()
        layout(false)
        assertOnlyKm2Selected()
    }

    @Test fun `a closed map stays closed across layouts and rotations`() {
        host()
        layout(true)
        rules.compose.onNodeWithText("open map").performClick()
        rules.compose.onNodeWithText(text(R.string.glow_summary_map_close)).performClick()
        assertClosed()
        layout(false)
        assertClosed()
        layout(true)
        assertClosed()
        rotate()
        assertClosed()
    }
}
