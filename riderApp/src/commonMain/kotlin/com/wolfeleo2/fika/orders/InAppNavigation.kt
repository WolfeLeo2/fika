package com.wolfeleo2.fika.orders

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Turn-by-turn navigation to a point, drawn full size. Calls [onRemaining] as the trip progresses and
 * [onUnavailable] once, instead of drawing anything, when it can't run (no API key, terms declined,
 * no location permission, no route, or a platform without it). The caller then falls back to Google Maps.
 */
@Composable
expect fun InAppNavigationView(
    destLat: Double,
    destLng: Double,
    title: String,
    onRemaining: (seconds: Int, meters: Int) -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier,
)
