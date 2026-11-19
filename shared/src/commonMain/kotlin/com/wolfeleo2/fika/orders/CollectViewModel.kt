package com.wolfeleo2.fika.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wolfeleo2.fika.tracking.LocationTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/** How the delivery ended; the screen navigates away once this is set. */
sealed interface CollectDone {
    class Delivered(val customerName: String?, val outcome: PaymentOutcome, val amountKes: Int, val at: Instant) : CollectDone
    data object Returned : CollectDone
}

data class CollectUiState(
    val loading: Boolean = true,
    val order: Order? = null,
    val shop: ShopInfo? = null,
    val method: PayMethod? = null,
    val mpesaCode: String = "",
    val busy: Boolean = false,
    val error: RpcError? = null,
    val done: CollectDone? = null,
    /** The order is no longer on the way (cancelled, reassigned, or closed elsewhere). */
    val gone: Boolean = false,
) {
    val prepaid: Boolean get() = order?.paymentMode == PaymentMode.Prepaid
    val mpesaCodeValid: Boolean get() = mpesaCode.isEmpty() || MPESA_CODE.matches(mpesaCode)
    val canConfirm: Boolean
        get() = order != null && !busy &&
            (prepaid || (method != null && (method != PayMethod.Mpesa || mpesaCodeValid)))
}

private val MPESA_CODE = Regex("[A-Z0-9]{8,12}")

class CollectViewModel(
    private val orderId: String,
    private val repo: OrdersRepository,
    private val shops: ShopRepository,
    private val clock: Clock,
    private val tracker: LocationTracker,
) : ViewModel() {
    private val _state = MutableStateFlow(CollectUiState())
    val state: StateFlow<CollectUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val order = repo.openOrders().getOrNull()?.firstOrNull { it.id == orderId && it.status == OrderStatus.OnTheWay }
            val shop = shops.myShop().getOrNull()
            _state.update { it.copy(loading = false, order = order, shop = shop, gone = order == null) }
        }
    }

    fun selectMethod(method: PayMethod) = _state.update { it.copy(method = method, error = null) }

    /** Uppercase and strip everything but letters and digits, as the server does. */
    fun onMpesaCodeChange(raw: String) =
        _state.update { it.copy(mpesaCode = raw.uppercase().filter(Char::isLetterOrDigit).take(12), error = null) }

    fun dismissError() = _state.update { it.copy(error = null) }

    /** "Paid and delivered" (pay on delivery) or "Delivered" (prepaid). */
    fun confirm() {
        val s = _state.value
        val order = s.order ?: return
        if (!s.canConfirm) return
        val method = if (s.prepaid) null else s.method
        val code = if (method == PayMethod.Mpesa) s.mpesaCode.ifEmpty { null } else null
        val outcome = when {
            s.prepaid -> PaymentOutcome.PaidUpfront
            method == PayMethod.Mpesa -> PaymentOutcome.Mpesa
            else -> PaymentOutcome.Cash
        }
        submit(order, outcome) { repo.markDelivered(order.id, method, code, null) }
    }

    /** "Leave items, not paid": delivered, the customer keeps the items and owes the money. */
    fun leaveUnpaid(reason: String) {
        val order = _state.value.order ?: return
        if (reason.isBlank() || order.paymentMode == PaymentMode.Prepaid) return
        submit(order, PaymentOutcome.NotPaid) { repo.markDelivered(order.id, null, null, reason.trim()) }
    }

    /** "Bring items back": order becomes `returned`. */
    fun bringBack(reason: String) {
        val order = _state.value.order ?: return
        if (reason.isBlank() || _state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val failure = repo.markReturned(order.id, reason.trim()).exceptionOrNull()
            if (failure == null) tracker.stop()
            _state.update { it.copy(busy = false, error = failure.toRpcError(), done = if (failure == null) CollectDone.Returned else null) }
        }
    }

    private fun submit(order: Order, outcome: PaymentOutcome, call: suspend () -> Result<Unit>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val failure = call().exceptionOrNull()
            if (failure == null) tracker.stop()
            _state.update {
                it.copy(
                    busy = false,
                    error = failure.toRpcError(),
                    done = if (failure == null) CollectDone.Delivered(order.customerName, outcome, order.amountKes ?: 0, clock.now()) else null,
                )
            }
        }
    }

    private fun Throwable?.toRpcError(): RpcError? = this?.let { (it as? RpcFailure)?.error ?: RpcError.Unknown }
}
