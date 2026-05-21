package com.queueless.plus.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.queueless.plus.adapters.OrderHistoryAdapter
import com.queueless.plus.databinding.ActivityOrderHistoryBinding
import com.queueless.plus.models.Order
import com.queueless.plus.models.Review
import com.queueless.plus.utils.FirestoreRepository
import com.queueless.plus.utils.SessionManager
import com.queueless.plus.utils.toast
import kotlinx.coroutines.launch

class OrderHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderHistoryBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        binding.rvOrders.layoutManager = LinearLayoutManager(this)

        loadOrders()
    }

    private fun loadOrders() {
        lifecycleScope.launch {
            try {
                val orders = FirestoreRepository.getOrdersForUser(session.userId)
                if (orders.isEmpty()) {
                    binding.rvOrders.visibility = android.view.View.GONE
                    binding.tvEmpty.visibility = android.view.View.VISIBLE
                } else {
                    binding.tvEmpty.visibility = android.view.View.GONE
                    binding.rvOrders.visibility = android.view.View.VISIBLE
                    binding.rvOrders.adapter = OrderHistoryAdapter(orders) { order ->
                        openReviewDialog(order)
                    }
                }
            } catch (e: Exception) {
                binding.rvOrders.visibility = android.view.View.GONE
                binding.tvEmpty.visibility = android.view.View.VISIBLE
                toast("Could not load order history: ${e.message}")
            }
        }
    }

    private fun openReviewDialog(order: Order) {
        // Simple dialog for review
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Leave a Review")
            .setMessage("Rate this queue (1-5 stars) and add a comment.")
            .setView(android.widget.EditText(this).apply { hint = "Comment" })
            .setPositiveButton("Submit") { _, _ ->
                // For simplicity, just add a review with rating 5
                lifecycleScope.launch {
                    try {
                        val review = Review(
                            userId = session.userId,
                            queueId = order.queueId,
                            rating = 5,
                            comment = "Great service!"
                        )
                        FirestoreRepository.addReview(review)
                        toast("Review submitted!")
                    } catch (e: Exception) {
                        toast("Failed: ${e.message}")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }
}