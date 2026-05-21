package com.queueless.plus.activities

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.Timestamp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.queueless.plus.adapters.QueueEntryAdapter
import com.queueless.plus.databinding.ActivityQueueDetailBinding
import com.queueless.plus.models.Queue
import com.queueless.plus.models.QueueEntry
import com.queueless.plus.utils.FirestoreRepository
import com.queueless.plus.utils.NotificationScheduler
import com.queueless.plus.utils.SessionManager
import com.queueless.plus.utils.formatWaitTime
import com.queueless.plus.utils.toast
import kotlinx.coroutines.launch

class QueueDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QUEUE_ID = "extra_queue_id"
        const val EXTRA_ENTRY_ID = "extra_entry_id"
    }

    private lateinit var binding: ActivityQueueDetailBinding
    private lateinit var session: SessionManager
    private var queue: Queue? = null
    private var isUserInQueue = false
    private var currentEntryId: String = ""
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationRequestCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQueueDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        val queueId = intent.getStringExtra(EXTRA_QUEUE_ID) ?: run {
            finish(); return
        }

        setupToolbar()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        loadQueueData(queueId)
        setupEntryListener(queueId)
        binding.btnEnableLocation.setOnClickListener {
            requestLocationPermission()
        }
        binding.btnShowQR.setOnClickListener {
            showQRCode()
        }
        binding.btnShare.setOnClickListener {
            shareQueue()
        }
        binding.btnOpenChat.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun loadQueueData(queueId: String) {
        lifecycleScope.launch {
            try {
                queue = FirestoreRepository.getQueue(queueId) ?: return@launch

                queue?.let { q ->
                    supportActionBar?.title = q.queueName
                    binding.tvDescription.text = q.description
                    binding.tvServiceTime.text = "Avg. service time: ${q.avgServiceTime} min"
                    binding.tvLocation.text = q.location

                    // Load rating
                    lifecycleScope.launch {
                        try {
                            val avgRating = FirestoreRepository.getAverageRating(q.queueId)
                            binding.tvRating.text = if (avgRating > 0) "⭐ ${String.format("%.1f", avgRating)}/5" else "No ratings yet"
                        } catch (e: Exception) {
                            binding.tvRating.text = "Rating unavailable"
                        }
                    }
                    binding.tvQueuePaused.visibility =
                        if (q.isPaused) android.view.View.VISIBLE else android.view.View.GONE

                    if (q.broadcastMessage.isNotBlank()) {
                        binding.tvQueueNotice.text = "Notice: ${q.broadcastMessage}"
                        binding.tvQueueNotice.visibility = android.view.View.VISIBLE
                    } else {
                        binding.tvQueueNotice.visibility = android.view.View.GONE
                    }

                    isUserInQueue =
                        FirestoreRepository.isUserInQueue(session.userId, q.queueId)

                    val entry = FirestoreRepository.getUserEntry(session.userId, q.queueId)
                    currentEntryId = entry?.entryId ?: ""

                    updateJoinButton()
                }

            } catch (e: Exception) {
                toast("Error loading queue: ${e.message}")
            }
        }
    }

    private fun setupEntryListener(queueId: String) {
        val adapter = QueueEntryAdapter()
        binding.rvQueueEntries.adapter = adapter

        FirestoreRepository.listenToQueueEntries(queueId) { entries ->
            adapter.submitList(entries)
            binding.tvQueueCount.text = "${entries.size} waiting"

            queue?.let { q ->
                val totalWait = entries.size * q.avgServiceTime
                binding.tvEstimatedWait.text =
                    "Est. total wait: ${totalWait.formatWaitTime()}"
            }
        }
    }

    private fun updateJoinButton() {
        if (isUserInQueue) {
            binding.btnJoinQueue.text = "View My Status"
            binding.btnJoinQueue.isEnabled = true
            binding.btnJoinQueue.setOnClickListener {
                openUserStatus()
            }
        } else if (queue?.isPaused == true) {
            binding.btnJoinQueue.text = "Queue Paused"
            binding.btnJoinQueue.isEnabled = false
            binding.btnJoinQueue.setOnClickListener(null)
        } else {
            binding.btnJoinQueue.text = "Join Queue"
            binding.btnJoinQueue.isEnabled = true
            binding.btnJoinQueue.setOnClickListener {
                joinQueue()
            }
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fetchCurrentLocation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                locationRequestCode
            )
        }
    }

    private fun fetchCurrentLocation() {
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        binding.tvYourLocation.text =
                            "Your location: ${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}"
                    } else {
                        fusedLocationClient.lastLocation
                            .addOnSuccessListener { last ->
                                if (last != null) {
                                    binding.tvYourLocation.text =
                                        "Your location: ${String.format("%.4f", last.latitude)}, ${String.format("%.4f", last.longitude)}"
                                } else {
                                    toast("Unable to get location. Please ensure GPS is enabled.")
                                }
                            }
                            .addOnFailureListener {
                                toast("Unable to get location. Please ensure GPS is enabled.")
                            }
                    }
                }
                .addOnFailureListener {
                    toast("Location request failed. Please enable location services.")
                }
        } catch (e: Exception) {
            toast("Location error: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchCurrentLocation()
            } else {
                toast("Location permission denied")
            }
        }
    }

    private fun joinQueue() {
        val q = queue ?: return
        if (q.isPaused) {
            toast("Queue is paused. Please try later.")
            return
        }
        binding.btnJoinQueue.isEnabled = false

        lifecycleScope.launch {
            try {

                val entry = QueueEntry(
                    userId = session.userId,
                    queueId = q.queueId,
                    userName = session.userName,
                    timestamp = Timestamp.now(),
                    status = QueueEntry.STATUS_WAITING
                )

                val entryId = FirestoreRepository.joinQueue(entry)
                runCatching {
                    FirestoreRepository.pushNotification(
                        userId = session.userId,
                        title = "Queue joined",
                        message = "You joined ${q.queueName}. Track your live status now."
                    )
                }

                // Schedule notification for when turn is approaching
                runCatching {
                    val estimatedWait = FirestoreRepository.getEstimatedWaitTime(session.userId, q)
                    val notificationDelay = (estimatedWait * 0.8).toInt() // Notify 80% through wait time
                    if (notificationDelay > 0) {
                        NotificationScheduler(this@QueueDetailActivity).scheduleQueueNotification(q.queueName, notificationDelay)
                    }
                }

                currentEntryId = entryId
                isUserInQueue = true

                toast("Joined queue!")

                val intent = Intent(this@QueueDetailActivity, UserStatusActivity::class.java).apply {
                    putExtra(UserStatusActivity.EXTRA_ENTRY_ID, entryId)
                    putExtra(UserStatusActivity.EXTRA_QUEUE_ID, q.queueId)
                }

                startActivity(intent)

            } catch (e: Exception) {
                toast("Failed: ${e.message}")
                binding.btnJoinQueue.isEnabled = true
            }
        }
    }

    private fun openUserStatus() {

        if (currentEntryId.isEmpty()) {
            toast("Try again")
            return
        }

        val intent = Intent(this, UserStatusActivity::class.java).apply {
            putExtra(UserStatusActivity.EXTRA_QUEUE_ID, queue?.queueId)
            putExtra(UserStatusActivity.EXTRA_ENTRY_ID, currentEntryId)
        }

        startActivity(intent)
    }

    private fun showQRCode() {
        val queueId = queue?.queueId ?: return
        try {
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(queueId, BarcodeFormat.QR_CODE, 400, 400)
            val barcodeEncoder = BarcodeEncoder()
            val bitmap: Bitmap = barcodeEncoder.createBitmap(bitMatrix)

            val imageView = ImageView(this).apply {
                setImageBitmap(bitmap)
                setPadding(20, 20, 20, 20)
            }

            AlertDialog.Builder(this)
                .setTitle("QR Code for ${queue?.queueName}")
                .setView(imageView)
                .setPositiveButton("Share") { _, _ ->
                    shareQueue()
                }
                .setNegativeButton("Close", null)
                .show()
        } catch (e: Exception) {
            toast("Failed to generate QR code")
        }
    }

    private fun shareQueue() {
        val queue = queue ?: return
        val shareText = "Join the queue for ${queue.queueName} at ${queue.location}. Download QueueLess+ app!"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "Share Queue"))
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
