package com.wolfeleo2.fika

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.wolfeleo2.fika.theme.EmphasizedAccelerate
import com.wolfeleo2.fika.theme.EmphasizedDecelerate
import com.wolfeleo2.fika.orders.CollectRoute
import com.wolfeleo2.fika.orders.DeliveredRoute
import com.wolfeleo2.fika.orders.NavigationRoute
import com.wolfeleo2.fika.orders.OrderDetailRoute
import com.wolfeleo2.fika.orders.PaymentOutcome
import com.wolfeleo2.fika.orders.timeLabel
import fika.riderapp.generated.resources.Res
import fika.riderapp.generated.resources.returned_message
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.getString

@Serializable
private data object MainKey : NavKey

@Serializable
private data class OrderKey(val id: String) : NavKey

@Serializable
private data class NavigateKey(val id: String) : NavKey

@Serializable
private data class CollectKey(val id: String) : NavKey

@Serializable
private data class DeliveredKey(val customerName: String?, val outcome: String, val amountKes: Int, val time: String) : NavKey

/**
 * Signed-in navigation: the tab shell, with order detail, navigation, collect and the success screen pushed on top.
 *
 * Motion, from M3 Expressive springs with emphasized easing on the fades:
 * - Going deeper (shell to order): the new screen glides in from the right, the old one drifts left and fades.
 * - Modal steps (navigation, collect): slide up over the order, which recedes slightly. Back slides it down.
 * - Success: zooms in from the centre, because it is a moment and not a step.
 * Under reduced motion every change is an instant swap.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppNav() {
    val backStack = remember { NavBackStack<NavKey>(MainKey) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val back = { backStack.removeLastOrNull(); Unit }
    val home = { while (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    val motion = MaterialTheme.motionScheme
    val reduced = rememberReducedMotion()
    val slide = motion.defaultSpatialSpec<IntOffset>()
    val zoom = motion.defaultSpatialSpec<Float>()
    val arrive = tween<Float>(260, delayMillis = 60, easing = EmphasizedDecelerate)
    val leave = tween<Float>(120, easing = EmphasizedAccelerate)
    val instant = ContentTransform(EnterTransition.None, ExitTransition.None)
    // Popping reveals the screen underneath, so the one leaving must stay on top while it animates out.
    val push = if (reduced) instant else ContentTransform(
        slideInHorizontally(slide) { it / 3 } + fadeIn(arrive),
        slideOutHorizontally(slide) { -it / 6 } + fadeOut(leave),
    )
    val pop = if (reduced) instant else ContentTransform(
        slideInHorizontally(slide) { -it / 6 } + fadeIn(arrive),
        slideOutHorizontally(slide) { it / 3 } + fadeOut(leave),
        targetContentZIndex = -1f,
    )
    val modalPush = if (reduced) instant else ContentTransform(
        slideInVertically(slide) { it / 4 } + fadeIn(arrive),
        scaleOut(zoom, targetScale = 0.94f) + fadeOut(leave),
    )
    val modalPop = if (reduced) instant else ContentTransform(
        scaleIn(zoom, initialScale = 0.94f) + fadeIn(arrive),
        slideOutVertically(slide) { it / 4 } + fadeOut(leave),
        targetContentZIndex = -1f,
    )
    val celebrate = if (reduced) instant else ContentTransform(
        scaleIn(zoom, initialScale = 0.88f) + fadeIn(arrive),
        fadeOut(leave),
    )
    // The screen a modal step returns to must also slide the modal down, so it carries the pop spec.
    val modalReturn = NavDisplay.popTransitionSpec { modalPop } + NavDisplay.predictivePopTransitionSpec { _ -> modalPop }

    NavDisplay(
        backStack = backStack,
        onBack = back,
        transitionSpec = { push },
        popTransitionSpec = { pop },
        predictivePopTransitionSpec = { _ -> pop },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<MainKey> { MainScreen(snackbar, onOpenOrder = { backStack.add(OrderKey(it)) }) }
            entry<OrderKey>(metadata = modalReturn) { key ->
                OrderDetailRoute(
                    orderId = key.id,
                    onBack = back,
                    onDelivered = { backStack.add(CollectKey(key.id)) },
                    onNavigate = { backStack.add(NavigateKey(key.id)) },
                )
            }
            entry<NavigateKey>(metadata = NavDisplay.transitionSpec { modalPush } + modalReturn) { key ->
                NavigationRoute(orderId = key.id, onBack = back, onDelivered = { backStack.add(CollectKey(key.id)) })
            }
            entry<CollectKey>(metadata = NavDisplay.transitionSpec { modalPush }) { key ->
                CollectRoute(
                    orderId = key.id,
                    onClose = back,
                    onDelivered = { d ->
                        home()
                        backStack.add(DeliveredKey(d.customerName, d.outcome.name, d.amountKes, timeLabel(d.at)))
                    },
                    onReturned = {
                        home()
                        scope.launch { snackbar.showSnackbar(getString(Res.string.returned_message)) }
                    },
                )
            }
            entry<DeliveredKey>(metadata = NavDisplay.transitionSpec { celebrate }) { key ->
                DeliveredRoute(
                    customerName = key.customerName,
                    outcome = PaymentOutcome.valueOf(key.outcome),
                    amountKes = key.amountKes,
                    time = key.time,
                    onNext = { id -> home(); backStack.add(OrderKey(id)) },
                    onHome = { home() },
                )
            }
        },
    )
}
