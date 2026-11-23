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
import kotlin.time.Clock

data class TodayUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
    val greeting: Greeting = Greeting.Morning,
    val orders: List<Order> = emptyList(),
    val closedToday: List<ClosedOrder> = emptyList(),
    val tracking: Boolean = false,
    val locationPermission: Boolean = true,
) {
    val onTheWay: Order? get() = orders.firstOrNull { it.status == OrderStatus.OnTheWay }
    val upNext: List<Order> get() = orders.filter { it.status != OrderStatus.OnTheWay }
    val toDeliver: Int get() = orders.size
    val toCollectKes: Int get() = orders.sumOf { it.collectKes ?: 0 }
    val deliveredCount: Int get() = closedToday.count { it.status == OrderStatus.Delivered }
    /** Nothing open to deliver (used to distinguish "All caught up" from "No deliveries yet"). */
    val isEmpty: Boolean get() = !loading && orders.isEmpty()
}

class TodayViewModel(
    private val repo: OrdersRepository,
    private val clock: Clock,
    private val tracker: LocationTracker,
) : ViewModel() {
    private val _state = MutableStateFlow(TodayUiState(greeting = greetingFor(clock.now())))
    val state: StateFlow<TodayUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch { repo.changes().collect { load(showSpinner = false) } }
        viewModelScope.launch { tracker.isTracking.collect { on -> _state.update { it.copy(tracking = on) } } }
    }

    /** Call on every resume: the list may have changed while the app was away. */
    fun onResume() = load(showSpinner = false)

    fun refresh() = load(showSpinner = true)

    /** "Resume tracking" after a phone restart. */
    fun resumeTracking() {
        val order = _state.value.onTheWay ?: return
        tracker.start(order.id, displayName(order.customerName).substringBefore(' '))
        _state.update { it.copy(locationPermission = tracker.hasPermission()) }
    }

    private fun load(showSpinner: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (showSpinner) _state.update { it.copy(refreshing = true) }
            val now = clock.now()
            val open = repo.openOrders()
            val closed = repo.closedSince(startOfTodayNairobi(now))
            // Safety net: if nothing is on the way any more (cancelled, delivered elsewhere), tracking must stop.
            if (open.isSuccess && open.getOrThrow().none { it.status == OrderStatus.OnTheWay }) tracker.stop()
            _state.update { s ->
                s.copy(
                    locationPermission = tracker.hasPermission(),
                    loading = false,
                    refreshing = false,
                    // Keep showing the last good data when offline.
                    offline = open.isFailure,
                    greeting = greetingFor(now),
                    orders = open.getOrNull() ?: s.orders,
                    closedToday = closed.getOrNull() ?: s.closedToday,
                )
            }
        }
    }
}