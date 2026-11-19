package com.wolfeleo2.fika.tracking

import com.wolfeleo2.fika.orders.OrderStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One position sample. [atMillis] is the fix time in epoch milliseconds. */
class Ping(
    val lat: Double,
    val lng: Double,
    val heading: Float? = null,
    val speed: Float? = null,
    val accuracy: Float? = null,
    val atMillis: Long,
)

/** What `rider_report_position` returns: the rider's most recently started order and its status. */
class ReportResult(val orderId: String, val status: OrderStatus)

interface TrackingRepository {
    /** `rider_report_position`. A null result means the rider has no started order at all. */
    suspend fun report(ping: Ping, etaSeconds: Int?, distanceM: Int?): Result<ReportResult?>
}

/**
 * Starts and stops the platform's location tracking (an Android foreground service). Tracking runs only
 * while an order is `on_the_way`.
 */
interface LocationTracker {
    val isTracking: StateFlow<Boolean>

    /** Fine or coarse location is granted. */
    fun hasPermission(): Boolean

    /** Starts tracking for the order. Returns false, and does nothing, if location permission is missing. */
    fun start(orderId: String, customerFirstName: String): Boolean

    fun stop()
}

const val MIN_MOVE_METERS = 15.0
const val MAX_QUIET_MILLIS = 30_000L

/** Haversine distance in meters. */
fun distanceMeters(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
    fun rad(d: Double) = d * PI / 180
    val dLat = rad(bLat - aLat)
    val dLng = rad(bLng - aLng)
    val h = sin(dLat / 2).pow(2) + cos(rad(aLat)) * cos(rad(bLat)) * sin(dLng / 2).pow(2)
    return 2 * 6_371_000.0 * asin(sqrt(h))
}

/** Skip a ping when the rider moved less than 15 m and less than 30 s have passed since the last one sent. */
fun shouldSend(last: Ping?, next: Ping): Boolean =
    last == null ||
        distanceMeters(last.lat, last.lng, next.lat, next.lng) >= MIN_MOVE_METERS ||
        next.atMillis - last.atMillis >= MAX_QUIET_MILLIS

enum class ReportOutcome {
    /** Moved too little and too recently; nothing sent. */
    Skipped,
    Sent,

    /** The send failed (offline). Nothing is queued: the next sample is simply tried instead. */
    Failed,

    /** The order is no longer on the way (delivered, returned or cancelled): stop tracking. */
    Stop,
}

/**
 * Decides which samples to send and reads the server's answer. It deliberately keeps no backlog: a ping
 * that fails is dropped, and the next sample (about 10 s later) is sent once the network is back.
 */
class PositionReporter(private val repo: TrackingRepository) {
    private val lock = Mutex()
    private var lastSent: Ping? = null

    suspend fun onLocation(ping: Ping, etaSeconds: Int? = null, distanceM: Int? = null): ReportOutcome = lock.withLock {
        if (!shouldSend(lastSent, ping)) return ReportOutcome.Skipped
        val result = repo.report(ping, etaSeconds, distanceM).getOrElse { return ReportOutcome.Failed }
        lastSent = ping
        if (result?.status == OrderStatus.OnTheWay) ReportOutcome.Sent else ReportOutcome.Stop
    }
}
