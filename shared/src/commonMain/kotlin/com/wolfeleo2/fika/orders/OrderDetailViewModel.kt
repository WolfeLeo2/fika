package com.wolfeleo2.fika.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wolfeleo2.fika.tracking.LocationTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the order is no longer in the rider's open list. */
enum class GoneReason { Reassigned, Cancelled, Finished }

data class OrderDetailUiState(
    val loading: Boolean = true,
    val order: Order? = null,
    /** The rider already has a different order on the way, so Start delivery is blocked. */
    val otherOnTheWay: Boolean = false,
    val gone: GoneReason? = null,
    /** An action is in flight; blocks double submits. */
    val busy: Boolean = false,
    val error: RpcError? = null,
    val offline: Boolean = false,
    /** Tracking is running right now. */
    val tracking: Boolean = false,
    /** Fine or coarse location is granted (re-read on resume, so it updates after the settings screen). */
    val locationPermission: Boolean = true,
    /** Delivery was just started; the screen consumes this to ask once about battery optimisation. */
    val justStarted: Boolean = false,
) {
    val canStart: Boolean get() = order?.status == OrderStatus.PickedUp && !otherOnTheWay && !busy
}

class OrderDetailViewModel(
    private val orderId: String,
    private val repo: OrdersRepository,
    private val tracker: LocationTracker,
) : ViewModel() {
    private val _state = MutableStateFlow(OrderDetailUiState())
    val state: StateFlow<OrderDetailUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch { repo.changes().collect { load() } }
        viewModelScope.launch { tracker.isTracking.collect { on -> _state.update { it.copy(tracking = on) } } }
    }

    fun onResume() = load()

    fun markPickedUp() = act { repo.markPickedUp(orderId) }

    fun startDelivery() {
        if (!_state.value.canStart) return
        act {
            repo.startDelivery(orderId).also { result ->
                if (result.isSuccess) {
                    // Without permission the delivery still goes ahead; the screen shows a banner.
                    startTracking()
                    _state.update { it.copy(justStarted = true) }
                }
            }
        }
    }

    fun onStartedHandled() = _state.update { it.copy(justStarted = false) }

    /** "Resume tracking": the phone restarted or the service was killed while the order was on the way. */
    fun resumeTracking() {
        if (_state.value.order?.status == OrderStatus.OnTheWay) startTracking()
        _state.update { it.copy(locationPermission = tracker.hasPermission()) }
    }

    private fun startTracking() {
        val name = displayName(_state.value.order?.customerName).substringBefore(' ')
        tracker.start(orderId, name)
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun act(call: suspend () -> Result<Unit>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val failure = call().exceptionOrNull()
            _state.update {
                it.copy(busy = false, error = failure?.let { e -> (e as? RpcFailure)?.error ?: RpcError.Unknown })
            }
            load()
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val open = repo.openOrders().getOrElse {
                _state.update { s -> s.copy(loading = false, offline = true) }
                return@launch
            }
            val order = open.firstOrNull { it.id == orderId }
            val gone = if (order != null) null else when (repo.statusOf(orderId).getOrNull()) {
                null -> GoneReason.Reassigned
                OrderStatus.Cancelled -> GoneReason.Cancelled
                else -> GoneReason.Finished
            }
            _state.update { s ->
                s.copy(
                    loading = false,
                    offline = false,
                    // Keep the last known order so the screen can name it in the "moved" message.
                    order = order ?: s.order,
                    otherOnTheWay = open.any { it.id != orderId && it.status == OrderStatus.OnTheWay },
                    gone = gone,
                    locationPermission = tracker.hasPermission(),
                )
            }
        }
    }
}
