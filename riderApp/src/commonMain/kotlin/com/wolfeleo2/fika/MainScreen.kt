package com.wolfeleo2.fika

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.duotone.Package
import com.adamglin.phosphoricons.duotone.User
import com.adamglin.phosphoricons.fill.NavigationArrow
import com.adamglin.phosphoricons.fill.Package as PackageFill
import com.adamglin.phosphoricons.fill.User as UserFill
import com.wolfeleo2.fika.orders.MainViewModel
import com.wolfeleo2.fika.orders.Order
import com.wolfeleo2.fika.orders.TodayViewModel
import com.wolfeleo2.fika.orders.displayName
import com.wolfeleo2.fika.orders.etaMinutes
import com.wolfeleo2.fika.orders.firstName
import com.wolfeleo2.fika.orders.initials
import com.wolfeleo2.fika.theme.EmphasizedAccelerate
import com.wolfeleo2.fika.theme.EmphasizedDecelerate
import com.wolfeleo2.fika.theme.FikaTheme
import com.wolfeleo2.fika.today.TodayRoute
import fika.riderapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private enum class Tab(val label: StringResource, val icon: ImageVector, val selectedIcon: ImageVector) {
    Today(Res.string.nav_today, PhosphorIcons.Duotone.Package, PhosphorIcons.Fill.PackageFill),
    Account(Res.string.nav_account, PhosphorIcons.Duotone.User, PhosphorIcons.Fill.UserFill),
}

/** Space the floating toolbar takes at the bottom (64 dp bar plus its margin), so lists end clear of it. */
private val NavClearance = 88.dp

/** Extra space while the active delivery chip sits above the toolbar. */
private val ChipClearance = 56.dp

/** Signed-in shell: Today and Account, with a floating toolbar over the content. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(
    snackbarHostState: SnackbarHostState,
    onOpenOrder: (String) -> Unit,
    viewModel: MainViewModel = koinViewModel(),
    // Held here as well as in the Today tab, so the active delivery stays known on every tab.
    todayViewModel: TodayViewModel = koinViewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    val name by viewModel.riderName.collectAsStateWithLifecycle()
    val shop by viewModel.shop.collectAsStateWithLifecycle()
    val today by todayViewModel.state.collectAsStateWithLifecycle()
    val activeOrder = today.onTheWay.takeIf { tab != Tab.Today }

    // Back from any other tab goes to Today first, as Android users expect, instead of leaving the app.
    BackHandler(enabled = tab != Tab.Today) { tab = Tab.Today }

    val reduced = rememberReducedMotion()
    val slide = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val arrive = tween<Float>(260, delayMillis = 60, easing = EmphasizedDecelerate)
    val leave = tween<Float>(120, easing = EmphasizedAccelerate)

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val bottom = insets.calculateBottomPadding() + NavClearance + if (activeOrder != null) ChipClearance else 0.dp
    val contentPadding = PaddingValues(top = insets.calculateTopPadding(), bottom = bottom)
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                if (reduced) {
                    ContentTransform(EnterTransition.None, ExitTransition.None)
                } else {
                    // Tabs sit side by side, so content moves the way the rider moved: right for Account, left for Today.
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    ContentTransform(
                        slideInHorizontally(slide) { dir * it / 5 } + fadeIn(arrive),
                        slideOutHorizontally(slide) { -dir * it / 5 } + fadeOut(leave),
                    )
                }
            },
            label = "tabs",
        ) { current ->
            when (current) {
                Tab.Today -> TodayRoute(firstName(name), onOpenOrder, contentPadding, todayViewModel)
                Tab.Account -> AccountScreen(name, shop, onSignOut = viewModel::signOut, contentPadding = contentPadding)
            }
        }
        TopScrim(insets.calculateTopPadding(), Modifier.align(Alignment.TopCenter))
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = bottom))
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (activeOrder != null) ActiveDeliveryChip(activeOrder) { onOpenOrder(activeOrder.id) }
            FloatingNav(tab, initials(name)) { tab = it }
        }
    }
}

/**
 * Fades scrolling content out under the status bar so its icons stay legible, in place of an app bar.
 * Solid behind the system icons, then fading to clear.
 */
@Composable
private fun TopScrim(statusBarHeight: Dp, modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier
            .fillMaxWidth()
            .height(statusBarHeight + 24.dp)
            .background(Brush.verticalGradient(0f to surface, 0.6f to surface, 1f to surface.copy(alpha = 0f))),
    )
}

/** Tracking runs while the rider is on another tab, so the delivery stays visible and one tap away. */
@Composable
private fun ActiveDeliveryChip(order: Order, onClick: () -> Unit) {
    val name = displayName(order.customerName).substringBefore(' ')
    val text = order.etaSeconds
        ?.let { stringResource(Res.string.active_delivery_eta, name, etaMinutes(it)) }
        ?: stringResource(Res.string.active_delivery, name)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 4.dp,
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = FikaTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xs),
        ) {
            Icon(PhosphorIcons.Fill.NavigationArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The M3 Expressive floating toolbar. Only the selected tab shows its label, the others are icon-only. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FloatingNav(selected: Tab, initials: String?, onSelect: (Tab) -> Unit) {
    HorizontalFloatingToolbar(expanded = true) {
        Tab.entries.forEach { tab -> NavItem(tab, tab == selected, initials) { onSelect(tab) } }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NavItem(tab: Tab, selected: Boolean, initials: String?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(tab.label)
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight).semantics { contentDescription = label },
        // The default checked shape is a rounded square; the toolbar around it is a full pill.
        shapes = ToggleButtonDefaults.shapes(checkedShape = CircleShape),
        colors = ToggleButtonDefaults.toggleButtonColors(
            containerColor = Color.Transparent,
            contentColor = scheme.onSurfaceVariant,
            checkedContainerColor = scheme.primary,
            checkedContentColor = scheme.onPrimary,
        ),
    ) {
        if (tab == Tab.Account) {
            RiderAvatar(initials, 28.dp)
        } else {
            Icon(if (selected) tab.selectedIcon else tab.icon, null, Modifier.size(28.dp))
        }
        AnimatedVisibility(selected) {
            Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}
