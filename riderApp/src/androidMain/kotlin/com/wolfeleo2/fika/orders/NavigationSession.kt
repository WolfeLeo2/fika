package com.wolfeleo2.fika.orders

import com.google.android.libraries.navigation.Navigator
import kotlin.math.roundToLong

/**
 * Keeps one navigator, and the destination it is routing to, alive while the rider leaves and re-enters the
 * navigation screen. Asking the SDK for a route is what Google bills (once per destination), so coming back to the
 * same place must reuse the running route instead of requesting it again. Rerouting and restarting guidance are free.
 *
 * It ends when the delivery does (delivered, returned, cancelled, tracking stopped) or the rider signs out.
 */
object NavigationSession {
    internal var navigator: Navigator? = null
    internal var destinationKey: String? = null

    /** Same place to about a metre, so re-opening an order, or a second order to the same customer, matches. */
    internal fun keyFor(lat: Double, lng: Double) = "${(lat * 1e5).roundToLong()},${(lng * 1e5).roundToLong()}"

    /** Stops guidance and frees the navigator and the cached preview map. Call on the main thread. */
    fun end() {
        navigator?.let { runCatching { it.stopGuidance(); it.clearDestinations(); it.cleanup() } }
        navigator = null
        destinationKey = null
        MapCache.clear()
    }
}
