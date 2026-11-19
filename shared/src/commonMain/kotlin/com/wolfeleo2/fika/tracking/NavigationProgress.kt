package com.wolfeleo2.fika.tracking

import kotlinx.coroutines.flow.MutableStateFlow

/** Time and distance left on the route, as reported by the navigator. */
class RemainingTrip(val seconds: Int, val meters: Int)

/**
 * The in-app navigation screen writes the remaining trip here and the tracking service reads it for each
 * ping (`eta_seconds`, `distance_m`). Null whenever no navigation session is running.
 */
class NavigationProgress {
    val remaining = MutableStateFlow<RemainingTrip?>(null)
}
