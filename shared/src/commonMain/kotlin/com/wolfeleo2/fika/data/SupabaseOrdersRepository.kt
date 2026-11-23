package com.wolfeleo2.fika.data

import com.wolfeleo2.fika.orders.ClosedOrder
import com.wolfeleo2.fika.orders.Order
import com.wolfeleo2.fika.orders.OrderStatus
import com.wolfeleo2.fika.orders.PayMethod
import com.wolfeleo2.fika.orders.OrdersRepository
import com.wolfeleo2.fika.orders.PaymentMode
import com.wolfeleo2.fika.orders.PaymentOutcome
import com.wolfeleo2.fika.orders.ProfileRepository
import com.wolfeleo2.fika.orders.RpcError
import com.wolfeleo2.fika.orders.RpcFailure
import com.wolfeleo2.fika.orders.mapRpcError
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order as SortOrder
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random
import kotlin.time.Instant

@Serializable
private class CustomerDto(val name: String? = null, val phone: String? = null, val landmark: String? = null)

@Serializable
private class OrderDto(
    val id: String,
    val code: String,
    val status: String,
    val items: String,
    @SerialName("payment_mode") val paymentMode: String,
    @SerialName("payment_status") val paymentStatus: String,
    @SerialName("amount_kes") val amountKes: Int? = null,
    @SerialName("dest_lat") val destLat: Double? = null,
    @SerialName("dest_lng") val destLng: Double? = null,
    @SerialName("rider_notes") val riderNotes: String? = null,
    val landmark: String? = null,
    @SerialName("eta_seconds") val etaSeconds: Int? = null,
    @SerialName("distance_m") val distanceM: Int? = null,
    val customers: CustomerDto? = null,
)

@Serializable
private class ClosedDto(
    val id: String,
    val status: String,
    @SerialName("customer_first_name") val customerFirstName: String? = null,
    val landmark: String? = null,
    @SerialName("payment_mode") val paymentMode: String,
    @SerialName("payment_status") val paymentStatus: String,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("amount_kes") val amountKes: Int? = null,
)

@Serializable
private class StatusDto(val status: String)

@Serializable
private class ProfileDto(@SerialName("full_name") val fullName: String? = null)

private val OPEN = listOf("assigned", "picked_up", "on_the_way")
private val CLOSED = listOf("delivered", "returned")

/** Runs [block], rethrowing cancellation and wrapping everything else in a failed [Result]. */
private inline fun <T> result(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

class SupabaseOrdersRepository(private val supabase: SupabaseClient) : OrdersRepository, ProfileRepository {

    override suspend fun openOrders(): Result<List<Order>> = result {
        supabase.from("orders")
            .select(
                Columns.raw(
                    "id, code, status, items, payment_mode, payment_status, amount_kes, dest_lat, dest_lng, rider_notes, landmark, " +
                        "eta_seconds, distance_m, customers(name, phone, landmark)",
                ),
            ) {
                filter { isIn("status", OPEN) }
                order("created_at", SortOrder.DESCENDING)
            }
            .decodeList<OrderDto>()
            .map {
                Order(
                    id = it.id,
                    code = it.code,
                    status = OrderStatus.fromDb(it.status),
                    customerName = it.customers?.name,
                    customerPhone = it.customers?.phone,
                    landmark = it.landmark ?: it.customers?.landmark,
                    items = it.items,
                    paymentMode = if (it.paymentMode == "prepaid") PaymentMode.Prepaid else PaymentMode.PayOnDelivery,
                    paid = it.paymentStatus == "paid",
                    amountKes = it.amountKes,
                    destLat = it.destLat,
                    destLng = it.destLng,
                    riderNotes = it.riderNotes,
                    etaSeconds = it.etaSeconds,
                    distanceM = it.distanceM,
                )
            }
    }

    override suspend fun closedSince(since: Instant, until: Instant?): Result<List<ClosedOrder>> = result {
        supabase.from("rider_history")
            .select(Columns.raw("id, status, customer_first_name, landmark, payment_mode, payment_status, payment_method, amount_kes")) {
                filter {
                    isIn("status", CLOSED)
                    gte("closed_at", since.toString())
                    until?.let { lt("closed_at", it.toString()) }
                }
                order("closed_at", SortOrder.DESCENDING)
            }
            .decodeList<ClosedDto>()
            .map {
                val status = OrderStatus.fromDb(it.status)
                ClosedOrder(
                    id = it.id,
                    status = status,
                    customerFirstName = it.customerFirstName,
                    landmark = it.landmark,
                    outcome = when {
                        status == OrderStatus.Returned -> PaymentOutcome.Returned
                        it.paymentMode == "prepaid" -> PaymentOutcome.PaidUpfront
                        it.paymentStatus != "paid" -> PaymentOutcome.NotPaid
                        it.paymentMethod == "mpesa" -> PaymentOutcome.Mpesa
                        else -> PaymentOutcome.Cash
                    },
                    amountKes = it.amountKes,
                )
            }
    }

    override fun changes(): Flow<Unit> = flow {
        var backoffMs = 1_000L
        while (true) {
            // Unique topic per collector: supabase-kt hands back the same (already joined) channel for a repeated
            // name, and postgresChangeFlow cannot be added after joining.
            val channel = supabase.channel("rider-orders-${Random.nextLong().toULong()}")
            try {
                val events = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "orders" }
                channel.subscribe(blockUntilSubscribed = true)
                emit(Unit) // catch up on anything missed while disconnected
                backoffMs = 1_000L
                events.collect { emit(Unit) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // fall through and resubscribe
            } finally {
                withContext(NonCancellable) { runCatching { supabase.realtime.removeChannel(channel) } }
            }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
        }
    }

    override suspend fun statusOf(orderId: String): Result<OrderStatus?> = result {
        supabase.from("orders")
            .select(Columns.list("status")) { filter { eq("id", orderId) } }
            .decodeList<StatusDto>().firstOrNull()?.let { OrderStatus.fromDb(it.status) }
    }

    override suspend fun markPickedUp(orderId: String) = rpc("rider_mark_picked_up", orderId)

    override suspend fun startDelivery(orderId: String) = rpc("rider_start_delivery", orderId)

    override suspend fun markDelivered(orderId: String, method: PayMethod?, mpesaCode: String?, unpaidReason: String?) =
        rpc("rider_mark_delivered", orderId) {
            // The RPC parameters default to null, so only send what applies.
            method?.let { put("payment_method", it.db) }
            mpesaCode?.let { put("mpesa_code", it) }
            unpaidReason?.let { put("unpaid_reason", it) }
        }

    override suspend fun markReturned(orderId: String, reason: String) =
        rpc("rider_mark_returned", orderId) { put("return_reason", reason) }

    private suspend fun rpc(name: String, orderId: String, extra: JsonObjectBuilder.() -> Unit = {}): Result<Unit> = try {
        supabase.postgrest.rpc(name, buildJsonObject { put("order_id", orderId); extra() })
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: PostgrestRestException) {
        Result.failure(RpcFailure(mapRpcError(e.message, e.hint)))
    } catch (e: HttpRequestException) {
        Result.failure(RpcFailure(RpcError.NoConnection))
    } catch (e: Exception) {
        Result.failure(RpcFailure(RpcError.Unknown))
    }

    override suspend fun riderName(): String? =
        result { supabase.from("profiles").select(Columns.list("full_name")).decodeList<ProfileDto>() }
            .getOrNull()?.firstOrNull()?.fullName
}
