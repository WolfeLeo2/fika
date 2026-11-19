package com.wolfeleo2.fika.orders

/** Stable error codes the rider RPCs raise (in the Postgres error's message or hint). */
enum class RpcError(val code: String?) {
    ShopSuspended("shop_suspended"),
    RiderInactive("rider_inactive"),
    NotARider("not_a_rider"),
    NotYourOrder("not_your_order"),
    AlreadyOnTheWay("already_on_the_way"),
    InvalidStatus("invalid_status"),
    InvalidPayment("invalid_payment"),
    InvalidPosition("invalid_position"),
    ReasonRequired("reason_required"),
    RateLimited("rate_limited"),
    NoConnection(null),
    Unknown(null),
}

class RpcFailure(val error: RpcError) : Exception(error.name)

/** Finds a known code in the error's message or hint. */
fun mapRpcError(message: String?, hint: String?): RpcError {
    val text = "${message.orEmpty()} ${hint.orEmpty()}"
    return RpcError.entries.firstOrNull { it.code != null && text.contains(it.code) } ?: RpcError.Unknown
}
