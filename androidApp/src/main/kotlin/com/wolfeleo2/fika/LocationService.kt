package com.wolfeleo2.fika

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.wolfeleo2.fika.orders.NavigationSession
import com.wolfeleo2.fika.tracking.LocationTracker
import com.wolfeleo2.fika.tracking.NavigationProgress
import com.wolfeleo2.fika.tracking.Ping
import com.wolfeleo2.fika.tracking.PositionReporter
import com.wolfeleo2.fika.tracking.ReportOutcome
import com.wolfeleo2.fika.tracking.TrackingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get

/**
 * Foreground service (type `location`) that reports the rider's position every ~10 s while an order is on
 * the way. It runs only in the foreground-service sense: no background location permission is used.
 */
class LocationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tracker: AndroidLocationTracker by lazy { get<LocationTracker>() as AndroidLocationTracker }
    private var callback: LocationCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission") // checked by AndroidLocationTracker.start before the service is started
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        startForeground(NOTIFICATION_ID, notification(name), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        tracker.running.value = true

        // A restart (new order) replaces the previous subscription and starts a fresh reporter.
        stopUpdates()
        val reporter = PositionReporter(get<TrackingRepository>())
        val progress = get<NavigationProgress>()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    scope.launch {
                        // ETA and distance come from the in-app navigator; null when navigation isn't running.
                        val trip = progress.remaining.value
                        if (reporter.onLocation(location.toPing(), trip?.seconds, trip?.meters) == ReportOutcome.Stop) stopSelf()
                    }
                }
            }
        }
        callback = cb
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(UPDATE_INTERVAL_MS / 2)
            .build()
        LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(request, cb, Looper.getMainLooper())
        // Not sticky: after a kill the app offers "Resume tracking" instead of restarting silently.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Tracking stops when the order leaves on_the_way, which is also when the navigation session must end.
        NavigationSession.end()
        stopUpdates()
        scope.cancel()
        tracker.running.value = false
        super.onDestroy()
    }

    private fun stopUpdates() {
        callback?.let { LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(it) }
        callback = null
    }

    private fun notification(customerFirstName: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.tracking_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.tracking_title, customerFirstName))
            .setContentText(getString(R.string.tracking_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun Location.toPing() = Ping(
        lat = latitude,
        lng = longitude,
        heading = if (hasBearing()) bearing else null,
        speed = if (hasSpeed()) speed else null,
        accuracy = if (hasAccuracy()) accuracy else null,
        atMillis = time,
    )

    companion object {
        const val ACTION_START = "com.wolfeleo2.fika.START_TRACKING"
        const val EXTRA_ORDER_ID = "order_id"
        const val EXTRA_NAME = "name"
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1
        private const val UPDATE_INTERVAL_MS = 10_000L
    }
}
