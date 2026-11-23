package com.wolfeleo2.fika.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

sealed interface HistoryPeriod {
    data object Today : HistoryPeriod
    data object Week : HistoryPeriod
    data class DateRange(val fromDate: LocalDate, val toDate: LocalDate) : HistoryPeriod
}

data class HistoryUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
    /** Today in Africa/Nairobi; the date picker never goes past it. */
    val today: LocalDate,
    val period: HistoryPeriod = HistoryPeriod.Today,
    val orders: List<ClosedOrder> = emptyList(),
) {
    val deliveredCount: Int get() = orders.count { it.status == OrderStatus.Delivered }
    val cashTotalKes: Int get() = orders.filter { it.outcome == PaymentOutcome.Cash }.sumOf { it.amountKes ?: 0 }
    val mpesaTotalKes: Int get() = orders.filter { it.outcome == PaymentOutcome.Mpesa }.sumOf { it.amountKes ?: 0 }
}

class HistoryViewModel(
    private val repo: OrdersRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState(today = clock.now().toLocalDateTime(NairobiTime).date))
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            repo.changes().collect { load(_state.value.period, showSpinner = false, showSkeleton = false) }
        }
    }

    /** Refresh silently when returning to the History tab or resuming the app. */
    fun onResume() = load(_state.value.period, showSpinner = false, showSkeleton = false)

    fun refresh() = load(_state.value.period, showSpinner = true, showSkeleton = false)

    /** Switch between Today, the last 7 days, or a custom DateRange. */
    fun selectPeriod(period: HistoryPeriod) {
        if (_state.value.period == period) return
        _state.update { it.copy(period = period, loading = true) }
        load(period, showSpinner = false, showSkeleton = true)
    }

    private fun load(period: HistoryPeriod, showSpinner: Boolean, showSkeleton: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (showSpinner) _state.update { it.copy(refreshing = true) }
            else if (showSkeleton) _state.update { it.copy(loading = true) }

            val todayStart = startOfTodayNairobi(clock.now())
            val result = fetchPeriod(period, todayStart)

            _state.update { s ->
                if (s.period != period) return@update s
                s.copy(
                    today = todayStart.toLocalDateTime(NairobiTime).date,
                    loading = false,
                    refreshing = false,
                    offline = result.isFailure,
                    // Keep showing the last good list when offline.
                    orders = result.getOrNull() ?: s.orders,
                )
            }
        }
    }

    /** Days are Africa/Nairobi calendar days; a DateRange is inclusive of both ends. */
    private suspend fun fetchPeriod(
        period: HistoryPeriod,
        todayStart: Instant,
    ): Result<List<ClosedOrder>> = when (period) {
        HistoryPeriod.Today -> repo.closedSince(todayStart)
        HistoryPeriod.Week -> repo.closedSince(todayStart - 6.days)
        is HistoryPeriod.DateRange -> repo.closedSince(
            since = period.fromDate.atStartOfDayIn(NairobiTime),
            until = period.toDate.plus(1, DateTimeUnit.DAY).atStartOfDayIn(NairobiTime),
        )
    }
}
