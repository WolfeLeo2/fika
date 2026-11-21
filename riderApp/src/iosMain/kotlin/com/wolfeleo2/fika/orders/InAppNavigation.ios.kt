package com.wolfeleo2.fika.orders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

/** iOS is not shipped; there is no in-app navigation there. */
@Composable
actual fun InAppNavigationView(
    destLat: Double,
    destLng: Double,
    title: String,
    darkTheme: Boolean,
    overviewRequest: Int,
    onRemaining: (seconds: Int, meters: Int) -> Unit,
    onArrived: () -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier,
) {
    LaunchedEffect(Unit) { onUnavailable() }
}

actual fun endNavigationSession() = Unit
