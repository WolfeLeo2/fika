package com.wolfeleo2.fika.orders

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Preview map for the order: the destination pin and, once location is granted, the rider's own position.
 * Not interactive, so the page behind it still scrolls. Draws nothing when the platform can't show a map
 * (no Maps API key, or iOS), so callers put spacing in [modifier], not around the call.
 */
@Composable
expect fun DeliveryMap(destLat: Double, destLng: Double, title: String, modifier: Modifier)
