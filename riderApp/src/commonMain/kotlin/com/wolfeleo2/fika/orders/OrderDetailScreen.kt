package com.wolfeleo2.fika.orders

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.toShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.bold.Check
import com.adamglin.phosphoricons.duotone.GpsSlash
import com.adamglin.phosphoricons.duotone.NavigationArrow
import com.adamglin.phosphoricons.duotone.Notepad
import com.adamglin.phosphoricons.duotone.Phone
import com.adamglin.phosphoricons.fill.NavigationArrow
import com.adamglin.phosphoricons.regular.CaretLeft
import com.wolfeleo2.fika.theme.FikaTheme
import com.wolfeleo2.fika.today.OfflineBanner
import com.wolfeleo2.fika.tracking.BatteryDialog
import com.wolfeleo2.fika.tracking.LocationReasonDialog
import com.wolfeleo2.fika.tracking.TrackingBanner
import com.wolfeleo2.fika.tracking.rememberTrackingPermissions
import fika.riderapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun OrderDetailRoute(
    orderId: String,
    onBack: () -> Unit,
    onDelivered: () -> Unit,
    onNavigate: () -> Unit,
) {
    val viewModel: OrderDetailViewModel = koinViewModel(key = "order-$orderId", parameters = { parametersOf(orderId) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissions = rememberTrackingPermissions()
    var askReason by remember { mutableStateOf(false) }
    var askBattery by remember { mutableStateOf(false) }

    // A started delivery opens in-app navigation, after the one-time battery question if it is due.
    LaunchedEffect(state.justStarted) {
        if (!state.justStarted) return@LaunchedEffect
        delay(250.milliseconds)
        viewModel.onStartedHandled()
        if (permissions.shouldAskBatteryExemption()) askBattery = true else onNavigate()
    }

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose {}
    }

    OrderDetailScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::onResume,
        onPickedUp = viewModel::markPickedUp,
        // Location is asked for on the first Start delivery, with a reason. Declining never blocks the delivery.
        onStartDelivery = { if (permissions.hasLocation()) viewModel.startDelivery() else askReason = true },
        onNavigate = onNavigate,
        onDelivered = onDelivered,
        onResumeTracking = viewModel::resumeTracking,
        onOpenSettings = permissions::openSettings,
    )
    if (askReason) {
        LocationReasonDialog(
            onContinue = { askReason = false; permissions.request { viewModel.startDelivery() } },
            onSkip = { askReason = false; viewModel.startDelivery() },
        )
    }
    if (askBattery) {
        BatteryDialog(
            onAllow = { askBattery = false; permissions.requestBatteryExemption(); onNavigate() },
            onLater = { askBattery = false; permissions.skipBatteryExemption(); onNavigate() },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OrderDetailScreen(
    state: OrderDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPickedUp: () -> Unit,
    onStartDelivery: () -> Unit,
    onNavigate: () -> Unit,
    onDelivered: () -> Unit,
    onResumeTracking: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val order = state.order
    val gone = state.gone
    val scheme = MaterialTheme.colorScheme
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val errorText = state.error?.let { rpcErrorText(it) }
    LaunchedEffect(errorText) { errorText?.let { snackbar.showSnackbar(it) } }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (order != null) stringResource(Res.string.order_code, order.code) else stringResource(Res.string.detail_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(PhosphorIcons.Regular.CaretLeft, stringResource(Res.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surface,
                    scrolledContainerColor = scheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (order != null && gone == null) {
                BottomActionSection(order, state, onPickedUp, onStartDelivery, onNavigate, onDelivered)
            }
        },
    ) { innerPadding ->
        when {
            gone != null -> Box(Modifier.fillMaxSize().padding(innerPadding)) { GoneMessage(gone, order, onBack) }
            order == null -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                if (state.loading) {
                    LoadingIndicator()
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.md),
                        modifier = Modifier.padding(FikaTheme.spacing.lg),
                    ) {
                        Text(stringResource(Res.string.error_no_connection), style = MaterialTheme.typography.bodyLarge, color = scheme.error)
                        Button(onClick = onRetry, shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight)) {
                            Text(stringResource(Res.string.action_try_again), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                        }
                    }
                }
            }
            else -> OrderContent(order, state, innerPadding, onResumeTracking, onOpenSettings)
        }
    }
}

@Composable
private fun GoneMessage(reason: GoneReason, order: Order?, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(FikaTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when (reason) {
                GoneReason.Reassigned -> stringResource(Res.string.gone_reassigned, displayName(order?.customerName).substringBefore(' '))
                GoneReason.Cancelled -> stringResource(Res.string.gone_cancelled)
                GoneReason.Finished -> stringResource(Res.string.gone_finished)
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Button(
            onClick = onBack,
            shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
            modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
        ) {
            Text(stringResource(Res.string.back), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
        }
    }
}

@Composable
private fun OrderContent(
    order: Order,
    state: OrderDetailUiState,
    innerPadding: PaddingValues,
    onResumeTracking: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val spacing = FikaTheme.spacing
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        CustomerHeader(
            order = order,
            onCall = { order.customerPhone?.let { uriHandler.openUri("tel:$it") } },
            // The in-progress route lives behind the bottom bar's Navigate; before that this previews it in Google Maps.
            onDirections = if (order.status != OrderStatus.OnTheWay && order.hasDestination) {
                { uriHandler.openUri("google.navigation:q=${order.destLat},${order.destLng}") }
            } else null,
        )
        StatusProgress(order.status, Modifier.padding(top = spacing.md))

        order.etaSeconds?.takeIf { order.status == OrderStatus.OnTheWay }?.let { eta ->
            EtaRow(eta, order.distanceM, Modifier.padding(top = spacing.lg))
        }
        if (order.hasDestination) {
            DeliveryMap(
                destLat = order.destLat!!,
                destLng = order.destLng!!,
                title = displayName(order.customerName),
                modifier = Modifier.padding(top = spacing.lg).fillMaxWidth().height(180.dp).clip(FikaTheme.shapes.large),
            )
        }
        if (state.offline) OfflineBanner(Modifier.padding(top = spacing.md))
        if (order.status == OrderStatus.OnTheWay && !state.tracking) {
            TrackingBanner(state.locationPermission, onResumeTracking, onOpenSettings, Modifier.padding(top = spacing.md))
        }
        if (!order.hasDestination) MissingDestinationBanner(Modifier.padding(top = spacing.md))

        OrderSummary(order, Modifier.padding(top = spacing.lg))
        order.riderNotes?.takeIf { it.isNotBlank() }?.let { RiderNotesCallout(it, Modifier.padding(top = spacing.md)) }
    }
}

@Composable
private fun CustomerHeader(order: Order, onCall: () -> Unit, onDirections: (() -> Unit)?) {
    Row(horizontalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xs)) {
        Column(Modifier.weight(1f)) {
            Text(displayName(order.customerName), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            order.landmark?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        onDirections?.let {
            FilledTonalIconButton(onClick = it, modifier = Modifier.size(56.dp)) {
                Icon(PhosphorIcons.Duotone.NavigationArrow, stringResource(Res.string.action_google_maps), Modifier.size(24.dp))
            }
        }
        FilledTonalIconButton(onClick = onCall, enabled = order.customerPhone != null, modifier = Modifier.size(56.dp)) {
            Icon(PhosphorIcons.Duotone.Phone, stringResource(Res.string.action_call), Modifier.size(24.dp))
        }
    }
}

/** Three steps toward the customer: assigned, picked up, on the way. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusProgress(status: OrderStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val green = FikaTheme.success
    val step = when (status) {
        OrderStatus.Assigned -> 1
        OrderStatus.PickedUp -> 2
        else -> 3
    }
    val label = stringResource(
        when (status) {
            OrderStatus.Assigned -> Res.string.status_assigned
            OrderStatus.PickedUp -> Res.string.status_picked_up
            OrderStatus.OnTheWay -> Res.string.status_on_the_way
            OrderStatus.Delivered -> Res.string.status_delivered
            OrderStatus.Returned -> Res.string.status_returned
            OrderStatus.Cancelled -> Res.string.status_cancelled
        },
    )
    val description = stringResource(Res.string.detail_step, label, step)

    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(3) { index ->
                val isCompleted = index < step
                val color by animateColorAsState(
                    if (isCompleted) green.success else scheme.surfaceContainerHighest,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "step-$index",
                )
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .background(color, CircleShape),
                    )
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(color, MaterialShapes.Cookie9Sided.toShape()),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isCompleted) {
                            Icon(
                                imageVector = PhosphorIcons.Bold.Check,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = green.onSuccess,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .background(color, CircleShape),
                    )
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun EtaRow(etaSeconds: Int, distanceM: Int?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(etaMinutes(etaSeconds).toString(), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = " " + stringResource(Res.string.eta_minutes_unit),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = FikaTheme.spacing.xxs),
        )
        distanceM?.let {
            Text(
                text = stringResource(Res.string.distance_away, formatDistance(it)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = FikaTheme.spacing.sm, bottom = FikaTheme.spacing.xxs),
            )
        }
    }
}

@Composable
private fun MissingDestinationBanner(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = FikaTheme.shapes.medium, color = scheme.errorContainer, contentColor = scheme.onErrorContainer) {
        Row(
            Modifier.padding(FikaTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(FikaTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PhosphorIcons.Duotone.GpsSlash, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(stringResource(Res.string.banner_no_location), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** What to carry and what to collect, in one block: they are read together at the door. */
@Composable
private fun OrderSummary(order: Order, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val collect = order.collectKes
    Surface(modifier.fillMaxWidth(), shape = FikaTheme.shapes.large, color = scheme.surfaceContainerHigh, contentColor = scheme.onSurface) {
        Column(Modifier.padding(FikaTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.md)) {
            Column(verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xxs)) {
                Text(stringResource(Res.string.detail_items), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                Text(order.items, style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider(color = scheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column(verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xxs)) {
                    Text(
                        stringResource(if (collect != null) Res.string.detail_to_collect else Res.string.detail_payment),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                    )
                    if (collect != null) {
                        Text(formatKes(collect), style = MaterialTheme.typography.headlineSmall, color = scheme.tertiary)
                    } else {
                        Text(
                            stringResource(if (order.paymentMode == PaymentMode.Prepaid) Res.string.outcome_paid_upfront else Res.string.detail_paid),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                if (collect != null) {
                    Text(stringResource(Res.string.detail_pay_on_delivery), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RiderNotesCallout(notes: String, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth(),
        shape = FikaTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(Modifier.padding(FikaTheme.spacing.md), horizontalArrangement = Arrangement.spacedBy(FikaTheme.spacing.sm)) {
            Icon(PhosphorIcons.Duotone.Notepad, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xxs)) {
                Text(stringResource(Res.string.detail_note), style = MaterialTheme.typography.labelLarge)
                Text(notes, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** One primary action per status. Starting the delivery is a swipe, the rest are taps. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BottomActionSection(
    order: Order,
    state: OrderDetailUiState,
    onPickedUp: () -> Unit,
    onStartDelivery: () -> Unit,
    onNavigate: () -> Unit,
    onDelivered: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val medium = ButtonDefaults.MediumContainerHeight
    Surface(Modifier.fillMaxWidth().navigationBarsPadding(), color = scheme.surfaceContainer, contentColor = scheme.onSurface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = FikaTheme.spacing.md, vertical = FikaTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xs),
        ) {
            when (order.status) {
                OrderStatus.Assigned -> Button(
                    onClick = onPickedUp,
                    enabled = !state.busy,
                    shapes = ButtonDefaults.shapesFor(medium),
                    modifier = Modifier.fillMaxWidth().heightIn(min = medium),
                ) {
                    if (state.busy) LoadingIndicator(Modifier.size(24.dp))
                    else Text(stringResource(Res.string.action_picked_up), style = ButtonDefaults.textStyleFor(medium))
                }
                OrderStatus.PickedUp -> {
                    SwipeToConfirm(
                        label = stringResource(Res.string.action_swipe_start),
                        enabled = state.canStart || state.busy,
                        busy = state.busy,
                        onConfirm = onStartDelivery,
                    )
                    Text(
                        text = stringResource(if (state.otherOnTheWay) Res.string.start_blocked else Res.string.action_start_delivery_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = FikaTheme.spacing.xs),
                    )
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xs)) {
                    FilledTonalButton(
                        onClick = onNavigate,
                        enabled = order.hasDestination,
                        shapes = ButtonDefaults.shapesFor(medium),
                        modifier = Modifier.weight(1f).heightIn(min = medium),
                    ) {
                        Icon(PhosphorIcons.Fill.NavigationArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            text = stringResource(Res.string.action_navigate),
                            style = ButtonDefaults.textStyleFor(medium),
                            modifier = Modifier.padding(start = FikaTheme.spacing.xs),
                        )
                    }
                    Button(
                        onClick = onDelivered,
                        shapes = ButtonDefaults.shapesFor(medium),
                        colors = ButtonDefaults.buttonColors(containerColor = FikaTheme.success.success, contentColor = FikaTheme.success.onSuccess),
                        modifier = Modifier.weight(1.4f).heightIn(min = medium),
                    ) {
                        Text(stringResource(Res.string.action_delivered), style = ButtonDefaults.textStyleFor(medium))
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun OrderDetailAssignedPreview() {
    FikaTheme {
        OrderDetailScreen(
            state = OrderDetailUiState(
                loading = false,
                order = Order(
                    id = "ord-1",
                    code = "FK-104",
                    status = OrderStatus.Assigned,
                    customerName = "Wanjiku Mwangi",
                    customerPhone = "+254712345678",
                    landmark = "Delta Towers, Westlands (Drop off at 4th floor reception)",
                    items = "2x Flat White, 1x Almond Croissant",
                    paymentMode = PaymentMode.PayOnDelivery,
                    paid = false,
                    amountKes = 850,
                    destLat = -1.268,
                    destLng = 36.811,
                    riderNotes = "Call when arriving at security gate for badge entry.",
                    etaSeconds = null,
                    distanceM = null,
                ),
            ),
            onBack = {},
            onRetry = {},
            onPickedUp = {},
            onStartDelivery = {},
            onNavigate = {},
            onDelivered = {},
            onResumeTracking = {},
            onOpenSettings = {},
        )
    }
}

@Preview
@Composable
private fun OrderDetailOnTheWayPreview() {
    FikaTheme {
        OrderDetailScreen(
            state = OrderDetailUiState(
                loading = false,
                order = Order(
                    id = "ord-2",
                    code = "FK-105",
                    status = OrderStatus.OnTheWay,
                    customerName = "Brian Ochieng",
                    customerPhone = "+254722334455",
                    landmark = "Riverside Square, Apt 3B",
                    items = "1x Iced Latte, 1x Blueberry Muffin",
                    paymentMode = PaymentMode.Prepaid,
                    paid = true,
                    amountKes = 650,
                    destLat = -1.261,
                    destLng = 36.804,
                    riderNotes = null,
                    etaSeconds = 480,
                    distanceM = 1200,
                ),
                tracking = true,
            ),
            onBack = {},
            onRetry = {},
            onPickedUp = {},
            onStartDelivery = {},
            onNavigate = {},
            onDelivered = {},
            onResumeTracking = {},
            onOpenSettings = {},
        )
    }
}
