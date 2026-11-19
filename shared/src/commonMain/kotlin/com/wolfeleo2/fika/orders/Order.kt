package com.wolfeleo2.fika.orders

enum class OrderStatus(val db: String) {
    Assigned("assigned"), PickedUp("picked_up"), OnTheWay("on_the_way"),
    Delivered("delivered"), Returned("returned"), Cancelled("cancelled");

    companion object {
        fun fromDb(value: String) = entries.first { it.db == value }
    }
}

enum class PayMethod(val db: String) { Cash("cash"), Mpesa("mpesa") }

enum class PaymentMode { Prepaid, PayOnDelivery }

/** How a closed order ended, for the History and Today lists. */
enum class PaymentOutcome { PaidUpfront, Mpesa, Cash, NotPaid, Returned, Cancelled }

/** An open order (assigned, picked up or on the way). */
class Order(
    val id: String,
    val code: String,
    val status: OrderStatus,
    val customerName: String?,
    val customerPhone: String?,
    val landmark: String?,
    val items: String,
    val paymentMode: PaymentMode,
    val paid: Boolean,
    val amountKes: Int?,
    val destLat: Double?,
    val destLng: Double?,
    val riderNotes: String?,
    val etaSeconds: Int?,
    val distanceM: Int?,
) {
    val hasDestination: Boolean get() = destLat != null && destLng != null

    /** KES the rider has to collect, or null when there is nothing to collect. */
    val collectKes: Int? get() = if (paymentMode == PaymentMode.PayOnDelivery && !paid) amountKes else null
}

/** A delivered, returned or cancelled order from `rider_history`. */
class ClosedOrder(
    val id: String,
    val status: OrderStatus,
    val customerFirstName: String?,
    val landmark: String?,
    val outcome: PaymentOutcome,
    val amountKes: Int?,
)
