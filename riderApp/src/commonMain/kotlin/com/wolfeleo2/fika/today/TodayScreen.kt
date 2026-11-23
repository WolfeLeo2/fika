package com.wolfeleo2.fika.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.Bag
import com.adamglin.phosphoricons.duotone.CheckCircle
import com.adamglin.phosphoricons.duotone.Clock
import com.adamglin.phosphoricons.duotone.GpsSlash
import com.adamglin.phosphoricons.duotone.Notepad
import com.adamglin.phosphoricons.duotone.Package
import com.adamglin.phosphoricons.duotone.Phone
import com.wolfeleo2.fika.orders.ClosedOrder
import com.wolfeleo2.fika.orders.Greeting
import com.wolfeleo2.fika.orders.Order
import com.wolfeleo2.fika.orders.OrderStatus
import com.wolfeleo2.fika.orders.PaymentMode
import com.wolfeleo2.fika.orders.PaymentOutcome
import com.wolfeleo2.fika.orders.TodayUiState
import com.wolfeleo2.fika.orders.TodayViewModel
import com.wolfeleo2.fika.orders.displayName
import com.wolfeleo2.fika.orders.etaMinutes
import com.wolfeleo2.fika.orders.formatDistance
import com.wolfeleo2.fika.orders.formatKes
import com.wolfeleo2.fika.settings.ThemeMode
import com.wolfeleo2.fika.theme.FikaTheme
import com.wolfeleo2.fika.theme.ShimmerBox
import com.wolfeleo2.fika.theme.segmentedShapes
import com.wolfeleo2.fika.tracking.TrackingBanner
import com.wolfeleo2.fika.tracking.rememberTrackingPermissions
import fika.riderapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Gap between rows of one segmented list; sections get their own top padding. */
private val SegmentGap = 2.dp

@Composable
fun TodayRoute(
    riderName: String?,
    onOpenOrder: (String) -> Unit,
    contentPadding: PaddingValues,
    viewModel: TodayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissions = rememberTrackingPermissions()
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    TodayScreen(
        state = state,
        riderName = riderName,
        onRefresh = viewModel::refresh,
        onOpenOrder = onOpenOrder,
        onResumeTracking = viewModel::resumeTracking,
        onOpenSettings = permissions::openSettings,
        contentPadding = contentPadding,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TodayScreen(
    state: TodayUiState,
    riderName: String?,
    onRefresh: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onResumeTracking: () -> Unit,
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues,
) {
    val uriHandler = LocalUriHandler.current
    val spacing = FikaTheme.spacing
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            // The list scrolls behind the seamless nav bar, so the insets go in here instead of around the box.
            contentPadding = PaddingValues(
                start = spacing.md,
                end = spacing.md,
                top = contentPadding.calculateTopPadding() + spacing.sm,
                bottom = contentPadding.calculateBottomPadding() + spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(SegmentGap),
        ) {
            item { Header(state.greeting, riderName) }

            if (state.offline) {
                item { OfflineBanner(Modifier.padding(top = spacing.sm)) }
            }

            if (state.loading) {
                item(key = "skeleton") {
                    TodaySkeleton(Modifier.padding(top = spacing.md))
                }
            } else {
                state.onTheWay?.let { order ->
                    item(key = order.id) {
                        ActiveOrderCard(
                            order = order,
                            onOpen = { onOpenOrder(order.id) },
                            onCall = { order.customerPhone?.let { uriHandler.openUri("tel:$it") } },
                            modifier = Modifier.animateItem().padding(top = spacing.md),
                        )
                    }
                }

                if ((state.onTheWay != null) && !state.tracking) {
                    item(key = "tracking-banner") {
                        TrackingBanner(state.locationPermission, onResumeTracking, onOpenSettings, Modifier.padding(top = spacing.sm))
                    }
                }

                if (state.upNext.isNotEmpty()) {
                    item(key = "up-next") {
                        val toCollect = state.upNext.sumOf { it.collectKes ?: 0 }
                        SectionHeading(
                            title = stringResource(Res.string.today_up_next_count, state.upNext.size),
                            trailing = if (toCollect > 0) stringResource(Res.string.today_to_collect, formatKes(toCollect)) else null,
                            modifier = Modifier.animateItem().padding(top = spacing.lg, bottom = spacing.xxs),
                        )
                    }
                    itemsIndexed(state.upNext, key = { _, order -> order.id }) { index, order ->
                        QueueRow(order, segmentedShapes(index, state.upNext.size), Modifier.animateItem()) { onOpenOrder(order.id) }
                    }
                }

                // A failed first load leaves the list empty, which must not read as "no deliveries".
                if (state.isEmpty && !state.offline) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxHeight(0.7f),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyState(caughtUp = state.deliveredCount > 0)
                        }
                    }
                }
            }
        }
    }
}

/** Matches ActiveOrderCard + Up next segmented rows to avoid layout shift while loading. */
@Composable
private fun TodaySkeleton(modifier: Modifier = Modifier) {
    val spacing = FikaTheme.spacing
    val scheme = MaterialTheme.colorScheme

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        // ActiveOrderCard placeholder
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = FikaTheme.shapes.large,
            color = scheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                ShimmerBox(Modifier.width(68.dp).height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        ShimmerBox(Modifier.width(168.dp).height(24.dp))
                        ShimmerBox(Modifier.width(136.dp).height(16.dp))
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        ShimmerBox(Modifier.width(64.dp).height(32.dp))
                        ShimmerBox(Modifier.width(48.dp).height(12.dp))
                    }
                }
                ShimmerBox(Modifier.fillMaxWidth(0.75f).height(16.dp))
                Row(Modifier.padding(top = spacing.xxs), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    ShimmerBox(Modifier.weight(1f).height(52.dp), shape = FikaTheme.shapes.medium)
                    ShimmerBox(Modifier.size(52.dp), shape = FikaTheme.shapes.medium)
                }
            }
        }

        // Up next heading + 2 segmented queue rows
        Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ShimmerBox(Modifier.width(96.dp).height(16.dp))
                ShimmerBox(Modifier.width(84.dp).height(14.dp))
            }
            QueueRowSkeleton(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
            QueueRowSkeleton(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
        }
    }
}

@Composable
private fun QueueRowSkeleton(shape: RoundedCornerShape) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = FikaTheme.spacing.md, vertical = FikaTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FikaTheme.spacing.md),
        ) {
            ShimmerBox(Modifier.size(24.dp), shape = FikaTheme.shapes.small)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ShimmerBox(Modifier.width(92.dp).height(11.dp))
                ShimmerBox(Modifier.width(132.dp).height(16.dp))
                ShimmerBox(Modifier.width(156.dp).height(13.dp))
            }
            ShimmerBox(Modifier.width(72.dp).height(14.dp))
        }
    }
}

@Composable
private fun Header(greeting: Greeting, riderName: String?) {
    val hello = stringResource(
        when (greeting) {
            Greeting.Morning -> Res.string.greeting_morning
            Greeting.Afternoon -> Res.string.greeting_afternoon
            Greeting.Evening -> Res.string.greeting_evening
        },
    )
    Text(
        text = if (riderName != null) stringResource(Res.string.greeting_with_name, hello, riderName) else hello,
        style = MaterialTheme.typography.headlineSmallEmphasized,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
internal fun OfflineBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = FikaTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = stringResource(Res.string.error_no_connection),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = FikaTheme.spacing.md, vertical = FikaTheme.spacing.xs),
        )
    }
}

@Composable
private fun SectionHeading(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = FikaTheme.spacing.xxs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        trailing?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun EmptyState(caughtUp: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xs),
    ) {
        Icon(
            imageVector = if (caughtUp) PhosphorIcons.Duotone.CheckCircle else PhosphorIcons.Duotone.Package,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(if (caughtUp) Res.string.today_caught_up else Res.string.today_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.today_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The delivery in progress. Glance order: customer and ETA, then what to carry or collect, then one action.
 * Text stays on the container's own content color so contrast comes from the theme, not from alpha.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActiveOrderCard(order: Order, onOpen: () -> Unit, onCall: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val medium = ButtonDefaults.MediumContainerHeight

    Surface(
        onClick = onOpen,
        modifier = modifier,
        shape = FikaTheme.shapes.large,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(FikaTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.sm)) {
            Text(stringResource(Res.string.order_code, order.code), style = MaterialTheme.typography.labelMedium)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(displayName(order.customerName), style = MaterialTheme.typography.headlineMedium)
                    order.landmark?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                }
                order.etaSeconds?.let { eta ->
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(etaMinutes(eta).toString(), style = MaterialTheme.typography.displayMedium)
                            Text(
                                text = " " + stringResource(Res.string.eta_minutes_unit),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = FikaTheme.spacing.xxs),
                            )
                        }
                        order.distanceM?.let {
                            Text(stringResource(Res.string.distance_away, formatDistance(it)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            order.riderNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xs)) {
                    Icon(PhosphorIcons.Duotone.Notepad, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(notes, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Row(Modifier.padding(top = FikaTheme.spacing.xxs), horizontalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xs)) {
                Button(
                    onClick = onOpen,
                    shapes = ButtonDefaults.shapesFor(medium),
                    modifier = Modifier.weight(1f).heightIn(min = medium),
                ) {
                    Text(stringResource(Res.string.action_open_delivery), style = ButtonDefaults.textStyleFor(medium))
                }
                FilledTonalButton(
                    onClick = onCall,
                    shapes = ButtonDefaults.shapesFor(medium),
                    modifier = Modifier.size(medium),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(PhosphorIcons.Duotone.Phone, stringResource(Res.string.action_call), Modifier.size(24.dp))
                }
            }
        }
    }
}

/**
 * One order in the queue. Lean layout: order code + status, customer name, and destination (or missing-GPS warning).
 * Package details live on OrderDetailScreen. Only exceptions carry color: money to collect and missing destination.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QueueRow(order: Order, shapes: ListItemShapes, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val inBag = order.status == OrderStatus.PickedUp
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        // The page is plain surface, so the rows need their own tone to read as rows.
        colors = ListItemDefaults.segmentedColors(containerColor = scheme.surfaceContainer),
        leadingContent = {
            Icon(if (inBag) PhosphorIcons.Duotone.Bag else PhosphorIcons.Duotone.Clock, contentDescription = null)
        },
        overlineContent = {
            Text(stringResource(if (inBag) Res.string.queue_in_bag else Res.string.queue_at_shop, order.code))
        },
        supportingContent = {
            if (order.hasDestination) {
                order.landmark?.let { Text(it) }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xxs)) {
                    Icon(PhosphorIcons.Duotone.GpsSlash, contentDescription = null, modifier = Modifier.size(16.dp), tint = scheme.error)
                    Text(stringResource(Res.string.warning_no_location), color = scheme.error)
                }
            }
        },
        trailingContent = {
            order.collectKes?.let {
                Text(stringResource(Res.string.badge_collect, formatKes(it)), style = MaterialTheme.typography.titleSmall, color = scheme.tertiary)
            }
        },
    ) {
        Text(displayName(order.customerName), style = MaterialTheme.typography.titleMedium)
    }
}

@Preview
@Composable
private fun TodayScreenPreview() {
    val sampleOrders = listOf(
        Order(
            id = "ord-1",
            code = "FK-104",
            status = OrderStatus.OnTheWay,
            customerName = "Wanjiku Mwangi",
            customerPhone = "+254712345678",
            landmark = "Delta Towers, 4th Floor",
            items = "2x Flat White, 1x Croissant",
            paymentMode = PaymentMode.Prepaid,
            paid = true,
            amountKes = 850,
            destLat = -1.268,
            destLng = 36.811,
            riderNotes = "Call upon arrival",
            etaSeconds = 480,
            distanceM = 1400,
        ),
        Order(
            id = "ord-2",
            code = "FK-105",
            status = OrderStatus.PickedUp,
            customerName = "Brian Ochieng",
            customerPhone = "+254722334455",
            landmark = "Riverside Square, Block B",
            items = "1x Iced Latte, 1x Blueberry Muffin",
            paymentMode = PaymentMode.PayOnDelivery,
            paid = false,
            amountKes = 650,
            destLat = -1.261,
            destLng = 36.804,
            riderNotes = null,
            etaSeconds = null,
            distanceM = null,
        ),
        Order(
            id = "ord-3",
            code = "FK-106",
            status = OrderStatus.Assigned,
            customerName = "Amina Hassan",
            customerPhone = "+254733445566",
            landmark = null,
            items = "3x Cappuccino",
            paymentMode = PaymentMode.Prepaid,
            paid = true,
            amountKes = 1200,
            destLat = null,
            destLng = null,
            riderNotes = null,
            etaSeconds = null,
            distanceM = null,
        ),
    )

    val sampleClosed = listOf(
        ClosedOrder(
            id = "ord-0",
            status = OrderStatus.Delivered,
            customerFirstName = "Kevin",
            landmark = "ABC Place",
            outcome = PaymentOutcome.PaidUpfront,
            amountKes = 500,
        ),
        ClosedOrder(
            id = "ord-00",
            status = OrderStatus.Delivered,
            customerFirstName = "Faith",
            landmark = "Sarit Centre",
            outcome = PaymentOutcome.Mpesa,
            amountKes = 750,
        ),
    )

    FikaTheme {
        TodayScreen(
            state = TodayUiState(
                loading = false,
                orders = sampleOrders,
                closedToday = sampleClosed,
                tracking = true,
                locationPermission = true,
            ),
            riderName = "Leo",
            onRefresh = {},
            onOpenOrder = {},
            onResumeTracking = {},
            onOpenSettings = {},
            contentPadding = PaddingValues(0.dp),
        )
    }
}

@Preview
@Composable
private fun TodayScreenLoadingPreview() {
    FikaTheme {
        TodayScreen(
            state = TodayUiState(loading = true),
            riderName = "Leo",
            onRefresh = {},
            onOpenOrder = {},
            onResumeTracking = {},
            onOpenSettings = {},
            contentPadding = PaddingValues(0.dp),
        )
    }
}