package com.wolfeleo2.fika.orders

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface OrdersRepository {
    /** The rider's assigned, picked up and on-the-way orders, newest first. */
    suspend fun openOrders(): Result<List<Order>>

    /** Delivered, returned and cancelled orders closed at or after [since], newest first. */
    /** Closed orders with `since <= closed_at < until` (no upper bound when [until] is null). */
    suspend fun closedSince(since: Instant, until: Instant? = null): Result<List<ClosedOrder>>

    /**
     * Emits on every change to the rider's orders. A reassigned order sends nothing, so callers
     * must also refetch on resume and pull to refresh.
     */
    fun changes(): Flow<Unit>

    /** Status of one of the rider's own orders in any state, or null if it is not theirs (any more). */
    suspend fun statusOf(orderId: String): Result<OrderStatus?>

    /** `rider_mark_picked_up`. Failures carry an [RpcFailure]. */
    suspend fun markPickedUp(orderId: String): Result<Unit>

    /** `rider_start_delivery`. Failures carry an [RpcFailure]. */
    suspend fun startDelivery(orderId: String): Result<Unit>

    /**
     * `rider_mark_delivered`. Pay-on-delivery orders need exactly one of [method] (how they paid) or
     * [unpaidReason] (delivered, customer kept the items, did not pay); prepaid orders need neither.
     */
    suspend fun markDelivered(orderId: String, method: PayMethod?, mpesaCode: String?, unpaidReason: String?): Result<Unit>

    /** `rider_mark_returned`: the rider is bringing the items back. */
    suspend fun markReturned(orderId: String, reason: String): Result<Unit>
}

/** The rider's shop, for the till number and the "Call shop" button. */
class ShopInfo(
    val name: String,
    val phone: String?,
    val tillNumber: String?,
    val paybillNumber: String?,
    val paybillAccount: String?,
)

interface ShopRepository {
    suspend fun myShop(): Result<ShopInfo>
}

interface ProfileRepository {
    suspend fun riderName(): String?
}
