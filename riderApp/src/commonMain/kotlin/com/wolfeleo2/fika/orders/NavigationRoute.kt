package com.wolfeleo2.fika.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.duotone.MapPin
import com.adamglin.phosphoricons.duotone.Phone
import com.adamglin.phosphoricons.regular.CaretLeft
import com.wolfeleo2.fika.theme.FikaTheme
import com.wolfeleo2.fika.tracking.NavigationProgress
import com.wolfeleo2.fika.tracking.RemainingTrip
import fika.riderapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Full-screen turn-by-turn to the customer, with a bottom card for calling and finishing the delivery.
 * When in-app navigation can't run, it opens Google Maps instead and returns to the order.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NavigationRoute(orderId: String, onBack: () -> Unit, onDelivered: () -> Unit) {
    val viewModel: OrderDetailViewModel = koinViewModel(key = "order-$orderId", parameters = { parametersOf(orderId) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress = koinInject<NavigationProgress>()
    val uriHandler = LocalUriHandler.current
    val order = state.order

    // The tracking service reads this for each ping; it must not outlive the navigation session.
    DisposableEffect(Unit) { onDispose { progress.remaining.value = null } }
    LaunchedEffect(state.gone) { if (state.gone != null) onBack() }
    LaunchedEffect(order?.hasDestination) { if (order != null && !order.hasDestination) onBack() }

    var trip by remember { mutableStateOf<RemainingTrip?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // The map takes the full space above the bottom panel, bleeding into the status bar.
        // It ends at the panel's top edge so the Navigation SDK controls are never obscured.
        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
            if (order == null || !order.hasDestination) {
                LoadingIndicator()
            } else {
                InAppNavigationView(
                    destLat = order.destLat!!,
                    destLng = order.destLng!!,
                    title = displayName(order.customerName),
                    onRemaining = { seconds, meters ->
                        RemainingTrip(seconds, meters).also { trip = it; progress.remaining.value = it }
                    },
                    onUnavailable = {
                        uriHandler.openUri("google.navigation:q=${order.destLat},${order.destLng}")
                        onBack()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Status bar protection scrim per edge-to-edge guidelines so system icons remain readable over map tiles
            Spacer(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            // Top-left floating action button for quick back navigation
            FloatingActionButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 8.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 6.dp,
                ),
            ) {
                Icon(
                    PhosphorIcons.Regular.CaretLeft,
                    contentDescription = stringResource(Res.string.back),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        if (order != null && order.hasDestination) {
            TripPanel(order = order, trip = trip, onDelivered = onDelivered)
        }
    }
}

/** Who and where, how long is left, a call button, and the way to finish the delivery. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TripPanel(order: Order, trip: RemainingTrip?, onDelivered: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val medium = ButtonDefaults.MediumContainerHeight
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(FikaTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(FikaTheme.spacing.sm),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = displayName(order.customerName),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )

                    // Glanceable Trip Metrics
                    trip?.let {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xs),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text = "${etaMinutes(it.seconds)} ${stringResource(Res.string.eta_minutes_unit)}",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(Res.string.distance_away, formatDistance(it.meters)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Landmark with icon
                    order.landmark?.takeIf { it.isNotBlank() }?.let { landmark ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                PhosphorIcons.Duotone.MapPin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = landmark,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }
                }

                // Call Customer Button
                FilledTonalIconButton(
                    onClick = { order.customerPhone?.let { uriHandler.openUri("tel:$it") } },
                    enabled = order.customerPhone != null,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        PhosphorIcons.Duotone.Phone,
                        contentDescription = stringResource(Res.string.action_call),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Button(
                onClick = onDelivered,
                shapes = ButtonDefaults.shapesFor(medium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FikaTheme.success.success,
                    contentColor = FikaTheme.success.onSuccess,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = medium),
            ) {
                Text(
                    text = stringResource(Res.string.action_delivered),
                    style = ButtonDefaults.textStyleFor(medium).copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

