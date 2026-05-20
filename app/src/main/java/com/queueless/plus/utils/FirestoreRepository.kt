package com.queueless.plus.utils

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import com.queueless.plus.models.AppNotification
import com.queueless.plus.models.ChatMessage
import com.queueless.plus.models.MenuItem
import com.queueless.plus.models.Order
import com.queueless.plus.models.Queue
import com.queueless.plus.models.QueueEntry
import com.queueless.plus.models.Review
import com.queueless.plus.models.User
import kotlinx.coroutines.tasks.await

object FirestoreRepository {

    private val db = FirebaseFirestore.getInstance().apply {
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
    }

    // Collections
    private val usersRef        = db.collection("users")
    private val queuesRef       = db.collection("queues")
    private val queueEntriesRef = db.collection("queueEntries")
    private val ordersRef       = db.collection("orders")
    private val notificationsRef = db.collection("notifications")
    private val reviewsRef      = db.collection("reviews")

    // 🔥 NEW COLLECTION
    private val menuRef         = db.collection("menu")

    // ═════════ USER ═════════

    suspend fun saveUser(user: User) {
        usersRef.document(user.userId).set(user.toMap()).await()
    }

    suspend fun getUser(userId: String): User? {
        val snap = usersRef.document(userId).get().await()
        return if (snap.exists()) snap.toObject(User::class.java) else null
    }

    suspend fun updateFcmToken(userId: String, token: String) {
        usersRef.document(userId).update("fcmToken", token).await()
    }

    suspend fun updateUserName(userId: String, name: String) {
        usersRef.document(userId).update("name", name).await()
    }

    suspend fun updateUserAvatar(userId: String, avatarUrl: String) {
        usersRef.document(userId).update("avatarUrl", avatarUrl).await()
    }

    suspend fun updateUserPoints(userId: String, points: Int) {
        usersRef.document(userId).update("loyaltyPoints", points).await()
    }

    suspend fun addUserPoints(userId: String, delta: Int) {
        val user = getUser(userId) ?: return
        updateUserPoints(userId, user.loyaltyPoints + delta)
    }

    // ═════════ QUEUE ═════════

    suspend fun createQueue(queue: Queue): String {
        val docRef = queuesRef.document()
        val newQueue = queue.copy(queueId = docRef.id)
        docRef.set(newQueue.toMap()).await()
        return docRef.id
    }

    suspend fun getQueues(): List<Queue> {
        val snap = queuesRef.whereEqualTo("isActive", true).get().await()
        return snap.toObjects(Queue::class.java)
    }

    suspend fun getQueue(queueId: String): Queue? {
        val snap = queuesRef.document(queueId).get().await()
        return if (snap.exists()) snap.toObject(Queue::class.java) else null
    }

    suspend fun updateQueue(queueId: String, updates: Map<String, Any>) {
        queuesRef.document(queueId).update(updates).await()
    }

    suspend fun setQueuePaused(queueId: String, paused: Boolean, reason: String) {
        queuesRef.document(queueId).update(
            mapOf(
                "isPaused" to paused,
                "pauseReason" to reason
            )
        ).await()
    }

    suspend fun updateQueueBroadcast(queueId: String, message: String) {
        queuesRef.document(queueId).update("broadcastMessage", message).await()
    }

    suspend fun deleteQueue(queueId: String) {
        queuesRef.document(queueId).update("isActive", false).await()
    }

    fun listenToQueues(onUpdate: (List<Queue>) -> Unit): ListenerRegistration {
        return queuesRef
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snap, _ ->
                snap?.let {
                    onUpdate(
                        it.toObjects(Queue::class.java)
                            .sortedBy { queue -> queue.currentCount * queue.avgServiceTime }
                    )
                }
            }
    }

    suspend fun getActiveQueuesSortedByWaitTime(): List<Queue> {
        return getQueues().sortedBy { it.currentCount * it.avgServiceTime }
    }

    suspend fun createOrder(order: Order): String {
        val docRef = ordersRef.document()
        val newOrder = order.copy(orderId = docRef.id)
        docRef.set(newOrder).await()
        return docRef.id
    }

    suspend fun updateOrder(order: Order) {
        if (order.orderId.isBlank()) return
        ordersRef.document(order.orderId).set(order).await()
    }

    suspend fun getOrdersForUser(userId: String): List<Order> {
        val snap = ordersRef
            .whereEqualTo("userId", userId)
            .get()
            .await()
        return snap.toObjects(Order::class.java)
            .sortedByDescending { it.timestamp }
    }

    suspend fun updateOrderStatus(orderId: String, status: String) {
        ordersRef.document(orderId).update("status", status).await()
    }

    suspend fun getOrderById(orderId: String): Order? {
        val snap = ordersRef.document(orderId).get().await()
        return if (snap.exists()) snap.toObject(Order::class.java) else null
    }

    suspend fun getOrderForEntry(entryId: String): Order? {
        val entry = getQueueEntryById(entryId) ?: return null
        return if (entry.orderId.isNotBlank()) getOrderById(entry.orderId) else null
    }

    suspend fun attachOrderToEntry(entryId: String, orderId: String) {
        queueEntriesRef.document(entryId)
            .update("orderId", orderId)
            .await()
    }

    suspend fun getQueueEntryById(entryId: String): QueueEntry? {
        val snap = queueEntriesRef.document(entryId).get().await()
        return if (snap.exists()) snap.toObject(QueueEntry::class.java) else null
    }

    // ═════════ QUEUE ENTRY ═════════

    suspend fun joinQueue(entry: QueueEntry): String {
        val docRef = queueEntriesRef.document()
        val newEntry = entry.copy(entryId = docRef.id)
        docRef.set(newEntry.toMap()).await()
        runCatching { incrementQueueCount(entry.queueId, 1) }
        return docRef.id
    }

    suspend fun getUserEntry(userId: String, queueId: String): QueueEntry? {
        val snap = queueEntriesRef
            .whereEqualTo("userId", userId)
            .whereEqualTo("queueId", queueId)
            .whereEqualTo("status", QueueEntry.STATUS_WAITING)
            .limit(1)
            .get()
            .await()

        return snap.documents.firstOrNull()?.toObject(QueueEntry::class.java)
    }

    suspend fun getWaitingEntries(queueId: String): List<QueueEntry> {
        val snap = queueEntriesRef
            .whereEqualTo("queueId", queueId)
            .whereEqualTo("status", QueueEntry.STATUS_WAITING)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .await()
        return snap.toObjects(QueueEntry::class.java)
    }

    suspend fun getStaleWaitingEntries(queueId: String, olderThanMinutes: Long): List<QueueEntry> {
        val cutoffMillis = System.currentTimeMillis() - olderThanMinutes * 60_000
        val cutoffTime = Timestamp(cutoffMillis / 1000, ((cutoffMillis % 1000) * 1_000_000).toInt())
        val snap = queueEntriesRef
            .whereEqualTo("queueId", queueId)
            .whereEqualTo("status", QueueEntry.STATUS_WAITING)
            .whereLessThan("timestamp", cutoffTime)
            .get()
            .await()
        return snap.toObjects(QueueEntry::class.java)
    }

    fun listenToQueueEntries(
        queueId: String,
        onUpdate: (List<QueueEntry>) -> Unit
    ): ListenerRegistration {
        return queueEntriesRef
            .whereEqualTo("queueId", queueId)
            .whereEqualTo("status", QueueEntry.STATUS_WAITING)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                snap?.let { onUpdate(it.toObjects(QueueEntry::class.java)) }
            }
    }

    fun listenToUserEntry(
        userId: String,
        queueId: String,
        onUpdate: (QueueEntry?) -> Unit
    ): ListenerRegistration {
        return queueEntriesRef
            .whereEqualTo("userId", userId)
            .whereEqualTo("queueId", queueId)
            .whereEqualTo("status", QueueEntry.STATUS_WAITING)
            .addSnapshotListener { snap, _ ->
                val entry = snap?.documents?.firstOrNull()
                    ?.toObject(QueueEntry::class.java)
                onUpdate(entry)
            }
    }

    fun listenToEntry(
        entryId: String,
        onUpdate: (QueueEntry?) -> Unit
    ): ListenerRegistration {
        return queueEntriesRef
            .document(entryId)
            .addSnapshotListener { snapshot, _ ->
                val entry = snapshot?.toObject(QueueEntry::class.java)
                onUpdate(entry)
            }
    }

    suspend fun updateEntryStatus(entryId: String, status: String) {
        val entrySnapshot = queueEntriesRef.document(entryId).get().await()
        val entry = entrySnapshot.toObject(QueueEntry::class.java)

        queueEntriesRef.document(entryId)
            .update("status", status)
            .await()

        if (entry == null) return
        val leavingWaitingState =
            entry.status == QueueEntry.STATUS_WAITING &&
                status != QueueEntry.STATUS_WAITING
        val returningToWaitingState =
            entry.status != QueueEntry.STATUS_WAITING &&
                status == QueueEntry.STATUS_WAITING

        if (leavingWaitingState) runCatching { incrementQueueCount(entry.queueId, -1) }
        if (returningToWaitingState) runCatching { incrementQueueCount(entry.queueId, 1) }
    }

    suspend fun markEntryNotified(entryId: String) {
        queueEntriesRef.document(entryId)
            .update("notified", true)
            .await()
    }

    suspend fun isUserInQueue(userId: String, queueId: String): Boolean {
        val snap = queueEntriesRef
            .whereEqualTo("userId", userId)
            .whereEqualTo("queueId", queueId)
            .whereEqualTo("status", QueueEntry.STATUS_WAITING)
            .get()
            .await()
        return !snap.isEmpty
    }

    // ═════════ ORDER SYSTEM ═════════

    suspend fun updateQueueEntryOrderStatus(entryId: String, status: String) {
        val entry = getQueueEntryById(entryId)

        queueEntriesRef.document(entryId)
            .update("orderStatus", status)
            .await()

        val orderId = entry?.orderId.orEmpty()
        if (orderId.isNotBlank()) {
            updateOrderStatus(orderId, status.replaceFirstChar { it.uppercase() })
        }
    }

    suspend fun updateOrderDetails(entryId: String, order: String) {
        queueEntriesRef.document(entryId)
            .update("orderDetails", order)
            .await()
    }

    // ═════════ MENU SYSTEM (🔥 NEW) ═════════

    fun listenToMenu(onResult: (List<MenuItem>) -> Unit): ListenerRegistration {
        return menuRef.addSnapshotListener { snapshot, _ ->
            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(MenuItem::class.java)?.copy(id = doc.id)
            } ?: emptyList()

            onResult(list)
        }
    }

    suspend fun addMenuItem(item: MenuItem) {
        val doc = menuRef.document()
        menuRef.document(doc.id).set(item.copy(id = doc.id)).await()
    }

    suspend fun deleteMenuItem(id: String) {
        menuRef.document(id).delete().await()
    }

    suspend fun incrementQueueCount(queueId: String, delta: Int) {
        if (queueId.isBlank() || delta == 0) return
        db.runTransaction { transaction ->
            val queueDoc = queuesRef.document(queueId)
            val snapshot = transaction.get(queueDoc)
            val currentCount = snapshot.getLong("currentCount") ?: 0L
            val nextCount = (currentCount + delta).coerceAtLeast(0)
            transaction.update(queueDoc, "currentCount", nextCount)
        }.await()
    }

    suspend fun getTodayOrders(): List<Order> {
        val startOfDayMillis = System.currentTimeMillis() - (System.currentTimeMillis() % 86_400_000)
        val snap = ordersRef
            .whereGreaterThanOrEqualTo("timestamp", startOfDayMillis)
            .get()
            .await()
        return snap.toObjects(Order::class.java)
    }

    suspend fun getActiveQueueCount(): Int {
        val snap = queuesRef
            .whereEqualTo("isActive", true)
            .get()
            .await()
        return snap.size()
    }

    suspend fun getTodayServedEntriesCount(): Int {
        val startOfDayMillis = System.currentTimeMillis() - (System.currentTimeMillis() % 86_400_000)
        val cutoffTime = Timestamp(startOfDayMillis / 1000, ((startOfDayMillis % 1000) * 1_000_000).toInt())
        val snap = queueEntriesRef
            .whereEqualTo("status", QueueEntry.STATUS_COMPLETED)
            .whereGreaterThanOrEqualTo("timestamp", cutoffTime)
            .get()
            .await()
        return snap.size()
    }

    // ═════════ LOGIC ═════════

    suspend fun getUserPosition(userId: String, queueId: String): Int {
        val entries = getWaitingEntries(queueId)
        val index = entries.indexOfFirst { it.userId == userId }
        return if (index >= 0) index + 1 else -1
    }

    suspend fun getEstimatedWaitTime(userId: String, queue: Queue): Int {
        val position = getUserPosition(userId, queue.queueId)
        if (position <= 0) return 0
        val usersAhead = position - 1
        return usersAhead * queue.avgServiceTime
    }

    suspend fun getLatestActiveEntry(userId: String): QueueEntry? {
        val snap = queueEntriesRef
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", QueueEntry.STATUS_WAITING)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
        return snap.documents.firstOrNull()?.toObject(QueueEntry::class.java)
    }

    suspend fun pushNotification(userId: String, title: String, message: String) {
        val doc = notificationsRef.document()
        val payload = AppNotification(
            id = doc.id,
            userId = userId,
            title = title,
            message = message,
            timestamp = System.currentTimeMillis(),
            read = false
        )
        doc.set(payload).await()
    }

    fun listenToNotifications(
        userId: String,
        onResult: (List<AppNotification>) -> Unit
    ): ListenerRegistration {
        return notificationsRef
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val items = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(AppNotification::class.java)
                } ?: emptyList()
                onResult(items)
            }
    }

    suspend fun markNotificationRead(id: String) {
        notificationsRef.document(id).update("read", true).await()
    }

    // ═════════ REVIEWS ═════════

    suspend fun addReview(review: Review) {
        val doc = reviewsRef.document()
        reviewsRef.document(doc.id).set(review.copy(reviewId = doc.id)).await()
    }

    suspend fun getReviewsForQueue(queueId: String): List<Review> {
        val snap = reviewsRef.whereEqualTo("queueId", queueId).get().await()
        return snap.toObjects(Review::class.java)
    }

    suspend fun getAverageRating(queueId: String): Double {
        val reviews = getReviewsForQueue(queueId)
        return if (reviews.isEmpty()) 0.0 else reviews.map { it.rating }.average()
    }

    // ═════════ CHAT SYSTEM ═════════

    fun sendChatMessage(message: ChatMessage, callback: (Boolean) -> Unit) {
        db.collection("chat").add(message)
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    fun listenToChatMessages(callback: (List<ChatMessage>) -> Unit): ListenerRegistration {
        return db.collection("chat")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val messages = snapshot?.documents?.mapNotNull { it.toObject(ChatMessage::class.java) } ?: emptyList()
                callback(messages)
            }
    }
}
