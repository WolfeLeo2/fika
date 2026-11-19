package com.wolfeleo2.fika.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Numbers for the success screen: today's count and the next order to offer. */
data class DeliveredUiState(val doneToday: Int = 0, val next: Order? = null)

class DeliveredViewModel(repo: OrdersRepository, clock: Clock) : ViewModel() {
    private val _state = MutableStateFlow(DeliveredUiState())
    val state: StateFlow<DeliveredUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val next = repo.openOrders().getOrNull()?.firstOrNull { it.status != OrderStatus.OnTheWay }
            val done = repo.closedSince(startOfTodayNairobi(clock.now())).getOrNull()
                ?.count { it.status == OrderStatus.Delivered } ?: 0
            _state.update { DeliveredUiState(done, next) }
        }
    }
}
