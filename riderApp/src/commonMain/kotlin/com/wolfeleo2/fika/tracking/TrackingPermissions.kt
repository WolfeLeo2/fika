package com.wolfeleo2.fika.tracking

import androidx.compose.runtime.Composable

/** The platform bits of location tracking that need an Activity: permission prompts and system settings. */
interface TrackingPermissions {
    /** Fine or coarse location is granted right now. */
    fun hasLocation(): Boolean

    /** Asks for location (and notifications, for the foreground notification). [onResult] gets whether location was granted. */
    fun request(onResult: (granted: Boolean) -> Unit)

    fun openSettings()

    /** True once per install, and only if the app is not already exempt from battery optimisation. */
    fun shouldAskBatteryExemption(): Boolean

    fun requestBatteryExemption()

    fun skipBatteryExemption()
}

@Composable
expect fun rememberTrackingPermissions(): TrackingPermissions
