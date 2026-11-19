package com.wolfeleo2.fika.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeTracker(var permission: Boolean = true) : LocationTracker {
    private val tracking = MutableStateFlow(false)
    override val isTracking: StateFlow<Boolean> = tracking
    val started = mutableListOf<Pair<String, String>>()
    var stops = 0

    override fun hasPermission() = permission

    override fun start(orderId: String, customerFirstName: String): Boolean {
        if (!permission) return false
        started += orderId to customerFirstName
        tracking.value = true
        return true
    }

    override fun stop() {
        stops++
        tracking.value = false
    }
}
