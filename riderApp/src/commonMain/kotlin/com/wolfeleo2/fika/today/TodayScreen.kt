package com.wolfeleo2.fika.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.duotone.Clock
import com.adamglin.phosphoricons.duotone.GpsSlash
import com.adamglin.phosphoricons.duotone.Notepad
import com.adamglin.phosphoricons.duotone.Package
import com.adamglin.phosphoricons.duotone.Phone
import com.adamglin.phosphoricons.regular.CaretRight
import com.wolfeleo2.fika.theme.FikaTheme
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
            // The list scrolls behind the floating nav bar, so the insets go in here instead of around the box.
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
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = spacing.xxl), Alignment.Center) { LoadingIndicator() }
                }
            }

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
                    QueueRow(order, ListItemDefaults.segmentedShapes(index, state.upNext.size), Modifier.animateItem()) { onOpenOrder(order.id) }
                }
            }

            // A failed first load leaves the list empty, which must not read as "no deliveries".
            if (state.isEmpty && !state.offline) {
                item { EmptyState(caughtUp = state.deliveredCount > 0) }
            }

            if (state.closedToday.isNotEmpty()) {
                item(key = "closed") { ClosedSection(state, Modifier.padding(top = spacing.lg)) }
            }
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
private fun EmptyState(caughtUp: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(top = FikaTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xxs),
    ) {
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

            Text(order.items, style = MaterialTheme.typography.bodyMedium)
            order.collectKes?.let {
                Text(stringResource(Res.string.badge_collect, formatKes(it)), style = MaterialTheme.typography.titleLarge)
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
 * One order in the queue. Only exceptions carry color: money to collect, and a missing destination.
 * A prepaid order shows nothing about payment.
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
        colors = ListItemDefaults.segmentedColors(containerColor = scheme.surfaceContainerHigh),
        leadingContent = {
            Icon(if (inBag) PhosphorIcons.Duotone.Package else PhosphorIcons.Duotone.Clock, contentDescription = null)
        },
        overlineContent = {
            Text(stringResource(if (inBag) Res.string.queue_in_bag else Res.string.queue_at_shop, order.code))
        },
        supportingContent = {
            Column {
                if (order.hasDestination) {
                    order.landmark?.let { Text(it) }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xxs)) {
                        Icon(PhosphorIcons.Duotone.GpsSlash, contentDescription = null, modifier = Modifier.size(16.dp), tint = scheme.error)
                        Text(stringResource(Res.string.warning_no_location), color = scheme.error)
                    }
                }
                Text(order.items, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** Today's finished orders, collapsed by default, with the cash the rider is holding. */
@Composable
private fun ClosedSection(state: TodayUiState, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val cashInHand = state.closedToday.filter { it.outcome == PaymentOutcome.Cash }.sumOf { it.amountKes ?: 0 }
    val toggleState = stringResource(if (expanded) Res.string.a11y_expanded else Res.string.a11y_collapsed)

    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics { stateDescription = toggleState }
                .padding(horizontal = FikaTheme.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.today_delivered_count, state.deliveredCount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (cashInHand > 0) {
                    Text(
                        stringResource(Res.string.today_cash_in_hand, formatKes(cashInHand)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                PhosphorIcons.Regular.CaretRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(if (expanded) 90f else 0f),
            )
        }
        AnimatedVisibility(expanded) {
            Column(
                Modifier.padding(top = FikaTheme.spacing.sm, start = FikaTheme.spacing.xxs, end = FikaTheme.spacing.xxs),
                verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.sm),
            ) {
                state.closedToday.forEach { ClosedRow(it) }
            }
        }
    }
}

@Composable
private fun ClosedRow(order: ClosedOrder) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(displayName(order.customerFirstName), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            order.landmark?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val amount = formatKes(order.amountKes ?: 0)
        Text(
            text = when (order.outcome) {
                PaymentOutcome.PaidUpfront -> stringResource(Res.string.outcome_paid_upfront)
                PaymentOutcome.Mpesa -> stringResource(Res.string.outcome_mpesa, amount)
                PaymentOutcome.Cash -> stringResource(Res.string.outcome_cash, amount)
                PaymentOutcome.NotPaid -> stringResource(Res.string.outcome_not_paid)
                PaymentOutcome.Returned -> stringResource(Res.string.outcome_returned)
                PaymentOutcome.Cancelled -> stringResource(Res.string.outcome_cancelled)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
private fun TodayScreenEmptyPreview() {
    FikaTheme {
        TodayScreen(
            state = TodayUiState(
                loading = false,
                                orders = emptyList(),
                closedToday = emptyList(),
                tracking = false,
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
