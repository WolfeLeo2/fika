package com.wolfeleo2.fika.tracking

import androidx.compose.runtime.Composable

/** iOS is not shipped; this keeps the target compiling. */
@Composable
actual fun rememberTrackingPermissions(): TrackingPermissions = object : TrackingPermissions {
    override fun hasLocation() = false
    override fun request(onResult: (granted: Boolean) -> Unit) = onResult(false)
    override fun openSettings() {}
    override fun shouldAskBatteryExemption() = false
    override fun requestBatteryExemption() {}
    override fun skipBatteryExemption() {}
}
