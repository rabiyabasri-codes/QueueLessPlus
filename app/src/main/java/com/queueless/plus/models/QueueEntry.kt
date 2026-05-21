package com.queueless.plus.models

import com.google.firebase.Timestamp

data class QueueEntry(
    val entryId: String = "",
    val userId: String = "",
    val queueId: String = "",
    val userName: String = "",
    val timestamp: Timestamp = Timestamp.now(),

    // 🟢 Queue status
    val status: String = STATUS_WAITING,   // waiting / completed / left

    val notified: Boolean = false,

    // 🍔 Order system
    val orderId: String = "",
    val orderDetails: String = "",
    val orderStatus: String = ORDER_WAITING

) {

    companion object {
        // Queue status
        const val STATUS_WAITING   = "waiting"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_LEFT      = "left"

        // Order status
        const val ORDER_WAITING    = "waiting"
        const val ORDER_PREPARING  = "preparing"
        const val ORDER_READY      = "ready"
        const val ORDER_COMPLETED  = "completed"
    }

    // 🔥 Firestore safe map
    fun toMap(): Map<String, Any> {
        return hashMapOf(
            "entryId" to entryId,
            "userId" to userId,
            "queueId" to queueId,
            "userName" to userName,
            "timestamp" to timestamp,
            "status" to status,
            "notified" to notified,
            "orderId" to orderId,

            // Order fields (always included for consistency)
            "orderDetails" to orderDetails,
            "orderStatus" to orderStatus
        )
    }
}