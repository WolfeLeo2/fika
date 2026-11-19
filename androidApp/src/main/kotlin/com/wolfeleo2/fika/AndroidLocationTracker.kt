package com.wolfeleo2.fika

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.wolfeleo2.fika.tracking.LocationTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Starts and stops [LocationService]. The service flips [running] as it comes up and goes down. */
class AndroidLocationTracker(private val context: Context) : LocationTracker {
    internal val running = MutableStateFlow(false)
    override val isTracking: StateFlow<Boolean> = running.asStateFlow()

    override fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun start(orderId: String, customerFirstName: String): Boolean {
        if (!hasPermission()) return false
        return try {
            context.startForegroundService(
                Intent(context, LocationService::class.java)
                    .setAction(LocationService.ACTION_START)
                    .putExtra(LocationService.EXTRA_ORDER_ID, orderId)
                    .putExtra(LocationService.EXTRA_NAME, customerFirstName),
            )
            running.value = true
            true
        } catch (e: Exception) {
            // e.g. started while the app is not allowed to run a foreground service.
            false
        }
    }

    override fun stop() {
        context.stopService(Intent(context, LocationService::class.java))
        running.value = false
    }
}
