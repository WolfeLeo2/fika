package com.wolfeleo2.fika.data

import com.wolfeleo2.fika.orders.OrderStatus
import com.wolfeleo2.fika.tracking.Ping
import com.wolfeleo2.fika.tracking.ReportResult
import com.wolfeleo2.fika.tracking.TrackingRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private class ReportDto(@SerialName("order_id") val orderId: String, val status: String)

class SupabaseTrackingRepository(private val supabase: SupabaseClient) : TrackingRepository {
    override suspend fun report(ping: Ping, etaSeconds: Int?, distanceM: Int?): Result<ReportResult?> = try {
        val body = supabase.postgrest.rpc(
            "rider_report_position",
            buildJsonObject {
                put("lat", ping.lat)
                put("lng", ping.lng)
                // Optional parameters default to null on the server, so only send what we have.
                ping.heading?.let { put("heading", it) }
                ping.speed?.let { put("speed", it) }
                ping.accuracy?.let { put("accuracy", it) }
                etaSeconds?.let { put("eta_seconds", it) }
                distanceM?.let { put("distance_m", it) }
            },
        ).decodeAsOrNull<ReportDto>()
        Result.success(body?.let { ReportResult(it.orderId, OrderStatus.fromDb(it.status)) })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
