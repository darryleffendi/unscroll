package com.darryl.unscroll.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-process channel from the service to the settings screen's debug line. */
object ServiceStatus {
    data class Snapshot(val connected: Boolean = false, val packageName: String? = null, val screen: String? = null, val action: String? = null)

    private val state = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = state

    fun connected(on: Boolean) {
        state.value = if (on) state.value.copy(connected = true) else Snapshot()
    }

    fun update(pkg: String?, screen: String?, action: String?) {
        state.value = state.value.copy(packageName = pkg, screen = screen, action = action)
    }
}
