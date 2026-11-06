package org.openstrap.app.ui.glow

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * What the full-screen route map keeps: which workout's map is open ([openFor], its start), Route only and the selected
 * split. A ViewModel, not `rememberSaveable`: a page's saved state is keyed by where it sits in the layout tree, and the
 * phone's stack and the wide screen's pane put it in different places, so each orientation kept its own stale copy.
 */
class RouteMapModel : ViewModel() {
    var openFor by mutableStateOf<Long?>(null)
        private set
    var routeOnly by mutableStateOf(false)
    var selected by mutableStateOf<Int?>(null)

    fun isOpen(start: Long) = openFor == start

    fun open(start: Long) {
        openFor = start
        routeOnly = false
        selected = null
    }

    fun close() {
        openFor = null
    }
}
