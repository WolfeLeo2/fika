package com.wolfeleo2.fika.orders

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Turn-by-turn navigation to a point, drawn full size and in the app's light or dark look ([darkTheme]). Each time
 * [overviewRequest] increases the map zooms out to the whole route until the rider recentres, without Google's own header and ETA card (the caller draws
 * those from the guidance the app receives). Calls [onRemaining] as the trip progresses, [onArrived] at the
 * destination, and
 * [onUnavailable] once, instead of drawing anything, when it can't run (no API key, terms declined,
 * no location permission, no route, or a platform without it). The caller then falls back to Google Maps.
 */
/**
 * Ends any running navigation session and drops cached map views. The session is kept alive while the rider moves
 * between the order and the navigation screen, so call this when the delivery is over or the rider signs out.
 */
expect fun endNavigationSession()

@Composable
expect fun InAppNavigationView(
    destLat: Double,
    destLng: Double,
    title: String,
    darkTheme: Boolean,
    overviewRequest: Int,
    onRemaining: (seconds: Int, meters: Int) -> Unit,
    onArrived: () -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier,
)
