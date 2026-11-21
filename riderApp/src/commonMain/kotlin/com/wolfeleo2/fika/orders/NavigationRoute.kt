package com.wolfeleo2.fika.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.bold.ArrowsClockwise
import com.adamglin.phosphoricons.bold.FlagCheckered
import com.adamglin.phosphoricons.bold.NavigationArrow
import com.adamglin.phosphoricons.duotone.MapPin
import com.adamglin.phosphoricons.duotone.MapTrifold
import com.adamglin.phosphoricons.duotone.Package
import com.adamglin.phosphoricons.duotone.Phone
import com.adamglin.phosphoricons.regular.ArrowSquareOut
import com.adamglin.phosphoricons.regular.CaretDown
import com.adamglin.phosphoricons.regular.CaretLeft
import com.adamglin.phosphoricons.regular.CaretUp
import com.wolfeleo2.fika.theme.FikaTheme
import com.wolfeleo2.fika.theme.rememberDarkTheme
import com.wolfeleo2.fika.tracking.NavGuidance
import com.wolfeleo2.fika.tracking.NavigationProgress
import com.wolfeleo2.fika.tracking.RemainingTrip
import com.wolfeleo2.fika.tracking.TurnKind
import fika.riderapp.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The banner is near black in both themes, like Uber's: it must read in direct sun and against any map tile. */
private val BannerColor = Color(0xFF101114)
private val BannerText = Color.White
private val BannerMuted = Color(0xFFB8BCC4)
private val BannerChip = Color(0xFF26292F)

/** Handle (20 dp) plus the collapsed header (64 dp). The navigation bar inset is added on top, as empty space. */
private val PeekHeight = 84.dp

/**
 * Full-screen turn-by-turn in the style of a ride-hailing driver app.
 *
 * - Top: a dark banner with the next maneuver, its distance and the road, and a "Then" chip for the step after.
 *   Built from the Navigation SDK's turn feed, so Google's own header and ETA card are switched off.
 * - Bottom sheet, collapsed: back, time and distance left, who the delivery is for, and a way to open it.
 * - Bottom sheet, expanded: the order (where, notes, items, payment), call, Google Maps, and Delivered.
 *   It opens by itself on arrival, since that is when those are needed.
 *
 * When in-app navigation can't run, it opens Google Maps instead and returns to the order.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)
@Composable
fun NavigationRoute(orderId: String, onBack: () -> Unit, onDelivered: () -> Unit) {
    val viewModel: OrderDetailViewModel = koinViewModel(key = "order-$orderId", parameters = { parametersOf(orderId) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress = koinInject<NavigationProgress>()
    val guidance by progress.guidance.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val density = LocalDensity.current
    val order = state.order

    // The tracking service and the banner read these; they must not outlive the navigation session.
    DisposableEffect(Unit) {
        onDispose {
            progress.remaining.value = null
            progress.guidance.value = null
        }
    }
    LaunchedEffect(state.gone) {
        if (state.gone != null) {
            endNavigationSession()
            onBack()
        }
    }
    LaunchedEffect(order?.hasDestination) { if (order != null && !order.hasDestination) onBack() }

    var trip by remember { mutableStateOf<RemainingTrip?>(null) }
    var arrived by remember { mutableStateOf(false) }
    var bannerPx by remember { mutableIntStateOf(0) }
    var bannerSlim by rememberSaveable { mutableStateOf(false) }
    var stepsOpen by rememberSaveable { mutableStateOf(false) }
    var overviewRequest by remember { mutableIntStateOf(0) }
    val darkTheme = rememberDarkTheme()

    val sheet = rememberBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        enabledValues = setOf(SheetValue.PartiallyExpanded, SheetValue.Expanded),
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheet)
    val scope = rememberCoroutineScope()
    val expanded = sheet.currentValue == SheetValue.Expanded
    val toggle: () -> Unit = { scope.launch { if (expanded) sheet.partialExpand() else sheet.expand() } }

    LaunchedEffect(arrived) {
        if (arrived) {
            stepsOpen = false
            sheet.expand()
        }
    }
    // Back closes whatever is open first (the steps, then the details), then leaves.
    BackHandler(enabled = stepsOpen || expanded) {
        if (stepsOpen) stepsOpen = false else scope.launch { sheet.partialExpand() }
    }

    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = PeekHeight + navBar,
        sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContentColor = MaterialTheme.colorScheme.onSurface,
        sheetShadowElevation = 12.dp,
        sheetDragHandle = { SheetHandle(expanded, toggle) },
        sheetContent = {
            if (order != null) {
                TripSheet(
                    order = order,
                    trip = trip,
                    arrived = arrived,
                    expanded = expanded,
                    navBar = navBar,
                    onToggle = toggle,
                    onBack = onBack,
                    onOpenGoogleMaps = { uriHandler.openUri("google.navigation:q=${order.destLat},${order.destLng}") },
                    onDelivered = onDelivered,
                )
            }
        },
        containerColor = BannerColor,
        modifier = Modifier.fillMaxSize(),
    ) { inner ->
        Box(Modifier.fillMaxSize()) {
            // The map sits between the banner and the collapsed sheet, so the SDK's own controls (recenter, speed
            // limit, compass) are never hidden behind either.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = with(density) { bannerPx.toDp() }, bottom = inner.calculateBottomPadding()),
                contentAlignment = Alignment.Center,
            ) {
                if (order == null || !order.hasDestination) {
                    LoadingIndicator()
                } else {
                    InAppNavigationView(
                        destLat = order.destLat!!,
                        destLng = order.destLng!!,
                        title = displayName(order.customerName),
                        darkTheme = darkTheme,
                        overviewRequest = overviewRequest,
                        onRemaining = { seconds, meters -> RemainingTrip(seconds, meters).also { trip = it; progress.remaining.value = it } },
                        onArrived = { arrived = true },
                        onUnavailable = {
                            uriHandler.openUri("google.navigation:q=${order.destLat},${order.destLng}")
                            onBack()
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (!arrived) {
                        // Zooms out to the whole route; the SDK's own recenter button brings the camera back.
                        FilledTonalIconButton(
                            onClick = { overviewRequest++ },
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(48.dp),
                        ) {
                            Icon(PhosphorIcons.Duotone.MapTrifold, stringResource(Res.string.nav_route_overview), Modifier.size(24.dp))
                        }
                    }
                }
            }
            val steps = guidance
            // The steps list opens over the map, under the banner, so the map is not resized while it moves.
            AnimatedVisibility(
                visible = stepsOpen && steps != null && !arrived,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = with(density) { bannerPx.toDp() }),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                if (steps != null) StepsList(steps, displayName(order?.customerName))
            }
            if (order != null) {
                TurnBanner(
                    guidance = guidance,
                    arrived = arrived,
                    fallbackName = displayName(order.customerName),
                    slim = bannerSlim,
                    stepsOpen = stepsOpen,
                    onToggleSteps = { if (guidance != null && !arrived) stepsOpen = !stepsOpen },
                    onToggleSlim = { bannerSlim = !bannerSlim; stepsOpen = false },
                    modifier = Modifier.align(Alignment.TopCenter).onSizeChanged { bannerPx = it.height },
                )
            }
        }
    }
}

/**
 * What to do next, kept minimal like a ride-hailing driver app: the arrow, how far, and the road. A small "Then" pill
 * previews the step after. Tap the banner for every step; tap the grab bar under it to shrink it to one thin line and
 * give the map more room.
 */
@Composable
private fun TurnBanner(
    guidance: NavGuidance?,
    arrived: Boolean,
    fallbackName: String,
    slim: Boolean,
    stepsOpen: Boolean,
    onToggleSteps: () -> Unit,
    onToggleSlim: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deliveringTo = stringResource(Res.string.active_delivery, fallbackName)
    val icon = when {
        arrived -> PhosphorIcons.Bold.FlagCheckered
        guidance?.rerouting == true -> PhosphorIcons.Bold.ArrowsClockwise
        guidance != null -> guidance.step.turn.icon()
        else -> PhosphorIcons.Bold.NavigationArrow
    }
    // "0 m" says nothing; show a distance only when there is one to act on.
    val distance = if (arrived || guidance == null || guidance.rerouting) null
        else guidance.distanceToStepMeters?.takeIf { it >= 10 }?.let { formatDistance(it) }
    val road = when {
        arrived -> stringResource(Res.string.nav_arrived_title)
        guidance == null -> stringResource(Res.string.nav_starting)
        guidance.rerouting -> stringResource(Res.string.nav_rerouting)
        else -> guidance.step.road ?: deliveringTo
    }
    val next = guidance?.next?.takeIf { !arrived && !guidance.rerouting }
    Column(modifier.fillMaxWidth().background(BannerColor).statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = guidance != null && !arrived,
                    role = Role.Button,
                    onClickLabel = stringResource(if (stepsOpen) Res.string.nav_hide_steps else Res.string.nav_show_steps),
                    onClick = onToggleSteps,
                )
                .padding(horizontal = 20.dp, vertical = if (slim) 6.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (slim) 12.dp else 16.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(if (slim) 28.dp else 40.dp), tint = BannerText)
            if (slim) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    distance?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = BannerText, maxLines = 1) }
                    Text(road, style = MaterialTheme.typography.titleMedium, color = BannerMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Column(Modifier.weight(1f)) {
                    distance?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = BannerMuted) }
                    Text(road, style = MaterialTheme.typography.titleLarge, color = BannerText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                next?.let {
                    Row(
                        Modifier.background(BannerChip, CircleShape).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(stringResource(Res.string.nav_then), style = MaterialTheme.typography.labelLarge, color = BannerMuted)
                        Icon(it.turn.icon(), contentDescription = null, modifier = Modifier.size(20.dp), tint = BannerText)
                    }
                }
            }
        }
        // Grab bar: shrinks or grows the banner.
        Box(
            Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(if (slim) Res.string.nav_grow_banner else Res.string.nav_shrink_banner),
                    onClick = onToggleSlim,
                )
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).background(BannerMuted, CircleShape))
        }
    }
}

/** Every step of the route ahead, one row each: arrow, road, and the distance from the step before it. */
@Composable
private fun StepsList(guidance: NavGuidance, deliveringTo: String) {
    val rows = listOf(guidance.step to guidance.distanceToStepMeters) + guidance.upcoming.map { it to it.distanceFromPreviousMeters }
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 380.dp).background(BannerColor),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
    ) {
        itemsIndexed(rows) { index, (step, meters) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(step.turn.icon(), contentDescription = null, modifier = Modifier.size(28.dp), tint = BannerText)
                Text(
                    text = step.road ?: stringResource(if (step.turn == TurnKind.Arrive) Res.string.nav_destination else Res.string.nav_continue),
                    style = MaterialTheme.typography.titleMedium,
                    color = BannerText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                meters?.takeIf { it >= 10 }?.let { Text(formatDistance(it), style = MaterialTheme.typography.titleMedium, color = BannerMuted) }
            }
            if (index < rows.lastIndex) HorizontalDivider(color = BannerChip)
        }
    }
}

/** Small grab bar, 20 dp tall. Tapping it opens or closes the sheet, for riders who don't drag. */
@Composable
private fun SheetHandle(expanded: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(if (expanded) Res.string.nav_hide_details else Res.string.nav_show_details),
                onClick = onToggle,
            )
            .padding(top = 10.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 36.dp, height = 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
    }
}

/**
 * The collapsed header (always visible) and, below it, the delivery details that open on drag, tap or arrival.
 * Collapsed holds only what a driver glances at while riding; the rest waits for a stop.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TripSheet(
    order: Order,
    trip: RemainingTrip?,
    arrived: Boolean,
    expanded: Boolean,
    navBar: Dp,
    onToggle: () -> Unit,
    onBack: () -> Unit,
    onOpenGoogleMaps: () -> Unit,
    onDelivered: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val medium = ButtonDefaults.MediumContainerHeight
    val firstName = displayName(order.customerName)

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        // Collapsed header
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(PhosphorIcons.Regular.CaretLeft, stringResource(Res.string.back), Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    arrived -> Text(stringResource(Res.string.nav_arrived_title), style = MaterialTheme.typography.headlineSmall, color = scheme.primary)
                    trip != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${etaMinutes(trip.seconds)} ${stringResource(Res.string.eta_minutes_unit)}", style = MaterialTheme.typography.headlineSmall)
                        Icon(PhosphorIcons.Duotone.Package, contentDescription = null, modifier = Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
                        Text(formatDistance(trip.meters), style = MaterialTheme.typography.headlineSmall)
                    }
                    else -> Text(stringResource(Res.string.nav_starting), style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    stringResource(Res.string.active_delivery, firstName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (expanded) PhosphorIcons.Regular.CaretDown else PhosphorIcons.Regular.CaretUp,
                    stringResource(if (expanded) Res.string.nav_hide_details else Res.string.nav_show_details),
                    Modifier.size(24.dp),
                )
            }
        }

        // The collapsed sheet is the header plus the navigation bar inset. Keeping that inset as empty space here stops
        // the first line of the details from peeking out under the header.
        Spacer(Modifier.height(navBar))

        // Details, revealed by expanding
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp + navBar), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HorizontalDivider(color = scheme.outlineVariant)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(firstName, style = MaterialTheme.typography.titleLarge)
                order.landmark?.takeIf { it.isNotBlank() }?.let {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(PhosphorIcons.Duotone.MapPin, contentDescription = null, modifier = Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                    }
                }
            }

            order.riderNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                Surface(Modifier.fillMaxWidth(), shape = FikaTheme.shapes.medium, color = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer) {
                    Column(Modifier.padding(FikaTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xxs)) {
                        Text(stringResource(Res.string.detail_notes), style = MaterialTheme.typography.labelLarge)
                        Text(notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.detail_items), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                    Text(order.items, style = MaterialTheme.typography.bodyLarge)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val collect = order.collectKes
                    Text(
                        stringResource(if (collect != null) Res.string.detail_to_collect else Res.string.detail_paid),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                    )
                    if (collect != null) Text(formatKes(collect), style = MaterialTheme.typography.headlineSmall, color = scheme.tertiary)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { order.customerPhone?.let { uriHandler.openUri("tel:$it") } },
                    enabled = order.customerPhone != null,
                    shapes = ButtonDefaults.shapesFor(medium),
                    modifier = Modifier.weight(1f).heightIn(min = medium),
                ) {
                    Icon(PhosphorIcons.Duotone.Phone, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(Res.string.action_call_short), Modifier.padding(start = 8.dp), style = ButtonDefaults.textStyleFor(medium))
                }
                FilledTonalButton(
                    onClick = onOpenGoogleMaps,
                    shapes = ButtonDefaults.shapesFor(medium),
                    modifier = Modifier.weight(1f).heightIn(min = medium),
                ) {
                    Icon(PhosphorIcons.Regular.ArrowSquareOut, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(Res.string.action_google_maps), Modifier.padding(start = 8.dp), style = ButtonDefaults.textStyleFor(medium))
                }
            }

            Button(
                onClick = onDelivered,
                shapes = ButtonDefaults.shapesFor(medium),
                colors = ButtonDefaults.buttonColors(containerColor = FikaTheme.success.success, contentColor = FikaTheme.success.onSuccess),
                modifier = Modifier.fillMaxWidth().heightIn(min = medium),
            ) { Text(stringResource(Res.string.action_delivered), style = ButtonDefaults.textStyleFor(medium)) }
        }
    }
}
