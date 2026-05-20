package com.queueless.plus.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.ListenerRegistration
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.queueless.plus.adapters.AdminQueueAdapter
import com.queueless.plus.databinding.ActivityAdminPanelBinding
import com.queueless.plus.models.Queue
import com.queueless.plus.utils.FirestoreRepository
import com.queueless.plus.utils.AuthManager
import com.queueless.plus.utils.SessionManager
import com.queueless.plus.utils.ThemeUtils
import com.queueless.plus.utils.hide
import com.queueless.plus.utils.requireAdminAccess
import com.queueless.plus.utils.show
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AdminPanelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminPanelBinding
    private lateinit var session: SessionManager
    private lateinit var adapter: AdminQueueAdapter
    private var queueListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        session = SessionManager(this)
        ThemeUtils.applyTheme(this, session)

        binding = ActivityAdminPanelBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (!requireAdminAccess(session)) return

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Admin Panel"
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        setupRecyclerView()

        binding.fabCreateQueue.setOnClickListener {
            startActivity(Intent(this, CreateQueueActivity::class.java))
        }

        binding.btnManageMenu.setOnClickListener {
            startActivity(Intent(this, AdminMenuActivity::class.java))
        }

        binding.btnAnalytics.setOnClickListener {
            startActivity(Intent(this, AdminAnalyticsActivity::class.java))
        }

        binding.btnScanQr.setOnClickListener {
            startActivity(Intent(this, QRScanActivity::class.java))
        }

        binding.btnViewUserDashboard.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }

        binding.btnAdminLogout.setOnClickListener {
            AuthManager.logout()
            session.clear()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        attachQueueListener()
    }

    private fun setupRecyclerView() {
        adapter = AdminQueueAdapter(
            onManage = { queue -> openManageQueue(queue) },
            onDelete = { queue -> deleteQueue(queue) },
            onGenerateQr = { queue -> showQueueQrDialog(queue) }
        )
        binding.rvAdminQueues.adapter = adapter
    }

    private fun attachQueueListener() {
        binding.progressBar.show()

        queueListener = FirestoreRepository.listenToQueues { queues ->
            binding.progressBar.hide()
            adapter.submitList(queues)

            if (queues.isEmpty()) {
                binding.tvEmpty.show()
            } else {
                binding.tvEmpty.hide()
            }
        }
    }

    private fun openManageQueue(queue: Queue) {
        val intent = Intent(this, ManageQueueActivity::class.java).apply {
            putExtra(ManageQueueActivity.EXTRA_QUEUE_ID, queue.queueId)
        }
        startActivity(intent)
    }

    private fun deleteQueue(queue: Queue) {
        AlertDialog.Builder(this)
            .setTitle("Delete Queue")
            .setMessage("Are you sure you want to delete '${queue.queueName}'?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    FirestoreRepository.deleteQueue(queue.queueId)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showQueueQrDialog(queue: Queue) {
        val qrContent = "queueless://join?queueId=${queue.queueId}"
        val size = 500

        try {
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(qrContent, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }

            val imageView = ImageView(this).apply {
                setImageBitmap(bitmap)
                setPadding(24, 24, 24, 24)
            }

            AlertDialog.Builder(this)
                .setTitle("QR for ${queue.queueName}")
                .setView(imageView)
                .setPositiveButton("Close", null)
                .setNeutralButton("Share") { _, _ ->
                    shareQueueQr(queue, qrContent)
                }
                .show()
        } catch (exception: Exception) {
            AlertDialog.Builder(this)
                .setTitle("QR Generation Failed")
                .setMessage("Unable to create QR code for ${queue.queueName}.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun shareQueueQr(queue: Queue, qrContent: String) {
        val shareText = "Join ${queue.queueName} on QueueLess+ using this link:\n$qrContent"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Join ${queue.queueName}")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, "Share QR link"))
    }

    override fun onDestroy() {
        super.onDestroy()
        queueListener?.remove()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
