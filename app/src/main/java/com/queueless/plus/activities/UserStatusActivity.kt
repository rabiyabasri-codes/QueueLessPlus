package com.queueless.plus.activities

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.CountDownTimer
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.queueless.plus.databinding.ActivityUserStatusBinding
import com.queueless.plus.models.QueueEntry
import com.queueless.plus.utils.FirestoreRepository
import com.queueless.plus.utils.SessionManager
import com.queueless.plus.utils.formatWaitTime
import com.queueless.plus.utils.hide
import com.queueless.plus.utils.show
import com.queueless.plus.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UserStatusActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QUEUE_ID = "extra_queue_id"
        const val EXTRA_ENTRY_ID = "extra_entry_id"
    }

    private lateinit var binding: ActivityUserStatusBinding
    private lateinit var session: SessionManager

    private var entryListener: ListenerRegistration? = null
    private var entriesListener: ListenerRegistration? = null
    private var queueMetaListener: ListenerRegistration? = null
    private var countdownTimer: CountDownTimer? = null

    private var currentEntry: QueueEntry? = null
    private var entryId: String = ""
    private var avgServiceMinutes: Int = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserStatusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        val queueId = intent.getStringExtra(EXTRA_QUEUE_ID) ?: run {
            finish(); return
        }

        entryId = intent.getStringExtra(EXTRA_ENTRY_ID) ?: ""

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Queue Status"

        loadQueueMeta(queueId)
        attachListeners(queueId)

        binding.btnLeaveQueue.setOnClickListener {
            leaveQueue()
        }

        binding.btnOrder.setOnClickListener {

            val entry = currentEntry

            if (entry == null) {
                toast("Still joining queue... ⏳")
                return@setOnClickListener
            }

            val intent = Intent(this, OrderActivity::class.java)
            intent.putExtra("ENTRY_ID", entry.entryId)
            intent.putExtra("QUEUE_ID", queueId)
            startActivity(intent)
        }
    }

    private fun attachListeners(queueId: String) {

        // 🔥 Listen to specific entry
        if (entryId.isNotEmpty()) {
            entryListener = FirestoreRepository.listenToEntry(entryId) { entry ->
                handleEntryUpdate(entry)
            }
        } else {
            entryListener = FirestoreRepository.listenToUserEntry(
                session.userId,
                queueId
            ) { entry ->
                handleEntryUpdate(entry)
            }
        }

        // 🔥 Listen to full queue
        entriesListener = FirestoreRepository.listenToQueueEntries(queueId) { entries ->

            val position = entries.indexOfFirst {
                it.userId == session.userId
            } + 1

            if (position <= 0) return@listenToQueueEntries

            val usersAhead = position - 1
            val waitMinutes = usersAhead * avgServiceMinutes

            binding.tvPosition.text = "#$position"
            binding.tvUsersAhead.text = "$usersAhead ahead of you"
            binding.tvWaitTime.text = waitMinutes.formatWaitTime()

            startCountdown(waitMinutes)

            // 🔔 Notify when near
            if (position <= 2 && currentEntry?.notified == false) {

                binding.tvNotifyBanner.show()
                binding.tvBannerText.text = "🔔 Almost your turn!"

                currentEntry?.entryId?.let { id ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        FirestoreRepository.markEntryNotified(id)
                    }
                }
            }
        }

        queueMetaListener = FirebaseFirestore.getInstance()
            .collection("queues")
            .document(queueId)
            .addSnapshotListener { snapshot, _ ->
                val paused = snapshot?.getBoolean("isPaused") == true
                val message = snapshot?.getString("broadcastMessage").orEmpty()

                binding.btnOrder.isEnabled = !paused
                if (message.isNotBlank()) {
                    binding.tvNotifyBanner.show()
                    binding.tvBannerText.text = message
                } else if (!paused) {
                    binding.tvNotifyBanner.hide()
                }
            }
    }

    private fun loadQueueMeta(queueId: String) {
        lifecycleScope.launch {
            val queue = FirestoreRepository.getQueue(queueId)
            avgServiceMinutes = queue?.avgServiceTime?.takeIf { it > 0 } ?: 5
        }
    }

    // 🔥 HANDLE ENTRY UPDATE + QR
    private fun handleEntryUpdate(entry: QueueEntry?) {

        currentEntry = entry

        if (entry == null) {
            binding.tvOrder.text = "Order: Not available"
            binding.tvOrderStatus.text = "Status: Not in queue"
            binding.ivQR.hide()
            return
        }

        // 🍔 Order
        binding.tvOrder.text =
            "Order: ${if (entry.orderDetails.isEmpty()) "Not placed" else entry.orderDetails}"

        // 📦 Status
        if (entry.status == QueueEntry.STATUS_COMPLETED || entry.orderStatus == QueueEntry.ORDER_COMPLETED) {
            binding.tvOrderStatus.text = "Status: Completed and received"
            binding.btnOrder.isEnabled = false
        } else {
            binding.tvOrderStatus.text = "Status: ${entry.orderStatus.uppercase()}"
            binding.btnOrder.isEnabled = true
        }

        binding.btnOrder.text = if (entry.orderDetails.isBlank()) "Place Order" else "Update Order"

        // 🔳 Generate QR
        generateQR(entry.entryId)

        // 🔔 Ready alert
        if (entry.orderStatus == QueueEntry.ORDER_READY) {
            toast("Your order is ready! 🍔")
        }
    }

    // 🔥 QR CODE GENERATOR
    private fun generateQR(entryId: String) {
        try {
            val encoder = BarcodeEncoder()
            val bitmap: Bitmap = encoder.encodeBitmap(
                entryId,
                BarcodeFormat.QR_CODE,
                400,
                400
            )
            binding.ivQR.setImageBitmap(bitmap)
            binding.ivQR.show()
        } catch (e: Exception) {
            binding.ivQR.hide()
        }
    }

    private fun startCountdown(waitMinutes: Int) {

        countdownTimer?.cancel()

        val totalMs = waitMinutes * 60 * 1000L

        countdownTimer = object : CountDownTimer(totalMs, 1000) {
            override fun onTick(millisLeft: Long) {
                val m = millisLeft / 60000
                val s = (millisLeft % 60000) / 1000
                binding.tvCountdown.text = String.format("%02d:%02d", m, s)
            }

            override fun onFinish() {
                binding.tvCountdown.text = "00:00"
            }
        }.start()
    }

    private fun leaveQueue() {

        val entry = currentEntry ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            FirestoreRepository.updateEntryStatus(
                entry.entryId,
                QueueEntry.STATUS_LEFT
            )
        }

        toast("You have left the queue.")
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        entryListener?.remove()
        entriesListener?.remove()
        queueMetaListener?.remove()
        countdownTimer?.cancel()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}