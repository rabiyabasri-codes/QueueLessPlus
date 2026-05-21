package com.queueless.plus.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.ListenerRegistration
import com.queueless.plus.adapters.ManageEntryAdapter
import com.queueless.plus.databinding.ActivityManageQueueBinding
import com.queueless.plus.models.Queue
import com.queueless.plus.models.QueueEntry
import com.queueless.plus.utils.FirestoreRepository
import com.queueless.plus.utils.SessionManager
import com.queueless.plus.utils.hide
import com.queueless.plus.utils.requireAdminAccess
import com.queueless.plus.utils.show
import com.queueless.plus.utils.toast
import kotlinx.coroutines.launch

class ManageQueueActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QUEUE_ID = "extra_queue_id"
    }

    private lateinit var binding: ActivityManageQueueBinding
    private lateinit var session: SessionManager
    private lateinit var adapter: ManageEntryAdapter
    private var entriesListener: ListenerRegistration? = null
    private var queue: Queue? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageQueueBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)
        if (!requireAdminAccess(session)) return

        val queueId = intent.getStringExtra(EXTRA_QUEUE_ID)
        if (queueId.isNullOrEmpty()) {
            toast("Invalid queue")
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        loadQueue(queueId)
        attachEntriesListener(queueId)
        setupControls(queueId)
    }

    // 🔥 Load queue details
    private fun loadQueue(queueId: String) {
        lifecycleScope.launch {
            try {
                queue = FirestoreRepository.getQueue(queueId)
                supportActionBar?.title = "Manage: ${queue?.queueName ?: "Queue"}"
                renderQueueControls()
            } catch (e: Exception) {
                toast("Failed to load queue")
            }
        }
    }

    private fun renderQueueControls() {
        val q = queue ?: return
        binding.btnPauseResume.text = if (q.isPaused) "Resume Queue" else "Pause Queue"
        binding.etBroadcast.setText(q.broadcastMessage)
    }

    // 🔥 Setup RecyclerView
    private fun setupRecyclerView() {
        adapter = ManageEntryAdapter(
            onServed = { entry -> markServed(entry) },
            onRemove = { entry -> removeEntry(entry) },
            onPreparing = { entry -> updateOrder(entry, QueueEntry.ORDER_PREPARING) },
            onReady = { entry -> updateOrder(entry, QueueEntry.ORDER_READY) }
        )

        binding.rvEntries.adapter = adapter
    }

    // 🔥 Listen to queue entries (REAL-TIME)
    private fun attachEntriesListener(queueId: String) {
        binding.progressBar.show()

        entriesListener = FirestoreRepository.listenToQueueEntries(queueId) { entries ->

            if (isFinishing) return@listenToQueueEntries

            binding.progressBar.hide()
            adapter.submitList(entries)

            binding.tvCount.text = "${entries.size} people waiting"

            if (entries.isEmpty()) {
                binding.tvEmpty.show()
            } else {
                binding.tvEmpty.hide()
            }
        }
    }

    private fun setupControls(queueId: String) {
        binding.btnPauseResume.setOnClickListener {
            val q = queue ?: return@setOnClickListener
            val nextPaused = !q.isPaused
            val reason = if (nextPaused) "Temporarily paused by admin" else ""

            lifecycleScope.launch {
                try {
                    FirestoreRepository.setQueuePaused(queueId, nextPaused, reason)
                    queue = q.copy(isPaused = nextPaused, pauseReason = reason)
                    renderQueueControls()
                    toast(if (nextPaused) "Queue paused" else "Queue resumed")
                } catch (e: Exception) {
                    toast("Failed: ${e.message}")
                }
            }
        }

        binding.btnSendBroadcast.setOnClickListener {
            val message = binding.etBroadcast.text?.toString()?.trim().orEmpty()
            if (message.isEmpty()) {
                toast("Enter a message first")
                return@setOnClickListener
            }
            lifecycleScope.launch {
                try {
                    FirestoreRepository.updateQueueBroadcast(queueId, message)
                    val waitingEntries = FirestoreRepository.getWaitingEntries(queueId)
                    waitingEntries.forEach { entry ->
                        FirestoreRepository.pushNotification(
                            userId = entry.userId,
                            title = "Queue update",
                            message = message
                        )
                    }
                    queue = queue?.copy(broadcastMessage = message)
                    toast("Notice sent")
                } catch (e: Exception) {
                    toast("Failed: ${e.message}")
                }
            }
        }

        binding.btnClearBroadcast.setOnClickListener {
            lifecycleScope.launch {
                try {
                    FirestoreRepository.updateQueueBroadcast(queueId, "")
                    queue = queue?.copy(broadcastMessage = "")
                    binding.etBroadcast.setText("")
                    toast("Notice cleared")
                } catch (e: Exception) {
                    toast("Failed: ${e.message}")
                }
            }
        }

        binding.btnAutoSkipNoShow.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val staleEntries = FirestoreRepository.getStaleWaitingEntries(queueId, 10)
                    var skipped = 0
                    for (entry in staleEntries) {
                        FirestoreRepository.updateEntryStatus(entry.entryId, QueueEntry.STATUS_LEFT)
                        skipped++
                    }
                    toast("Auto-skipped $skipped no-shows")
                } catch (e: Exception) {
                    toast("Failed: ${e.message}")
                }
            }
        }
    }

    // 🔥 Update order status (Preparing / Ready)
    private fun updateOrder(entry: QueueEntry, status: String) {
        lifecycleScope.launch {
            try {
                FirestoreRepository.updateQueueEntryOrderStatus(entry.entryId, status)
                toast("Order updated to ${status.uppercase()}")
            } catch (e: Exception) {
                toast("Error: ${e.message}")
            }
        }
    }

    // 🔥 Mark user served
    private fun markServed(entry: QueueEntry) {
        lifecycleScope.launch {
            try {
                FirestoreRepository.updateQueueEntryOrderStatus(entry.entryId, QueueEntry.ORDER_COMPLETED)
                FirestoreRepository.updateEntryStatus(
                    entry.entryId,
                    QueueEntry.STATUS_COMPLETED
                )

                if (entry.orderId.isNotBlank()) {
                    FirestoreRepository.updateOrderStatus(entry.orderId, "Completed")
                }

                FirestoreRepository.pushNotification(
                    userId = entry.userId,
                    title = "Order completed",
                    message = "Your order is completed and received."
                )

                toast("${entry.userName} served")
            } catch (e: Exception) {
                toast("Error: ${e.message}")
            }
        }
    }

    // 🔥 Remove user
    private fun removeEntry(entry: QueueEntry) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Remove from Queue")
            .setMessage("Remove ${entry.userName}?")
            .setPositiveButton("Remove") { _, _ ->
                lifecycleScope.launch {
                    try {
                        FirestoreRepository.updateEntryStatus(
                            entry.entryId,
                            QueueEntry.STATUS_LEFT
                        )
                        toast("Removed successfully")
                    } catch (e: Exception) {
                        toast("Error: ${e.message}")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        entriesListener?.remove()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}