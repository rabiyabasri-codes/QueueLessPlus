package com.queueless.plus.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import com.queueless.plus.R
import com.queueless.plus.adapters.CartAdapter
import com.queueless.plus.adapters.MenuAdapter
import com.queueless.plus.databinding.ActivityOrderBinding
import com.queueless.plus.models.CartItem
import com.queueless.plus.models.MenuItem
import com.queueless.plus.models.Order
import com.queueless.plus.models.QueueEntry
import com.queueless.plus.utils.FirestoreRepository
import com.queueless.plus.utils.SessionManager
import com.queueless.plus.utils.toast
import kotlinx.coroutines.launch

class OrderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderBinding
    private lateinit var session: SessionManager

    private var entryId: String = ""
    private var queueId: String = ""
    private var editingOrderId: String? = null
    private var paymentConfirmedMethod: String? = null

    private val cart = mutableListOf<CartItem>()
    private var latestMenu: List<MenuItem> = emptyList()

    private lateinit var cartAdapter: CartAdapter
    private lateinit var menuAdapter: MenuAdapter

    private val paymentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(PaymentActivity.EXTRA_PAYMENT_METHOD)?.let { method ->
                paymentConfirmedMethod = method
                toast("$method payment completed")
            }
        }
    }

    companion object {
        const val EXTRA_PAYMENT_METHOD = "extra_payment_method"
        const val EXTRA_PAYMENT_AMOUNT = "extra_payment_amount"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        entryId = intent.getStringExtra("ENTRY_ID") ?: ""
        queueId = intent.getStringExtra("QUEUE_ID") ?: ""

        if (entryId.isEmpty()) {
            toast("Entry not ready yet ⏳")
        }

        setupToolbar()
        setupMenu()
        setupCart()
        setupSwipeToDelete()
        setupSearch()
        setupPaymentSelection()

        loadExistingOrder()
        updateTotal()

        binding.btnPlaceOrder.setOnClickListener {
            placeOrder()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Order Food 🍔"
    }

    // 🔥 MENU FROM FIREBASE
    private fun setupMenu() {

        binding.rvMenu.layoutManager = LinearLayoutManager(this)

        menuAdapter = MenuAdapter(
            emptyList(),
            onAdd = { item -> addToCart(item) },
            onToggleFavorite = { item ->
                val nowFavorite = session.toggleFavoriteItem(item.name)
                toast(if (nowFavorite) "Added to favorites" else "Removed from favorites")
                updateVisibleMenu()
            }
        )

        binding.rvMenu.adapter = menuAdapter

        FirestoreRepository.listenToMenu { list ->
            latestMenu = list
            updateVisibleMenu()
        }
    }

    private fun updateVisibleMenu() {
        val sorted = latestMenu.sortedWith(
            compareByDescending<MenuItem> { session.isFavoriteItem(it.name) }
                .thenBy { it.name.lowercase() }
        )
        menuAdapter.updateData(sorted)
        menuAdapter.filter(binding.etMenuSearch.text?.toString().orEmpty())
    }

    private fun setupCart() {
        binding.rvCart.layoutManager = LinearLayoutManager(this)

        cartAdapter = CartAdapter(cart) {
            updateTotal()
        }

        binding.rvCart.adapter = cartAdapter
    }

    private fun setupSearch() {
        binding.etMenuSearch.doAfterTextChanged { text ->
            menuAdapter.filter(text?.toString().orEmpty())
        }
    }

    private fun setupPaymentSelection() {
        binding.paymentMethodGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedMethod = when (checkedId) {
                binding.rbUPI.id -> "UPI / Wallet"
                binding.rbCard.id -> "Card Payment"
                else -> "Cash on Delivery"
            }

            if (selectedMethod != "Cash on Delivery") {
                openPaymentPage(selectedMethod, cart.sumOf { it.price * it.quantity })
            } else {
                paymentConfirmedMethod = null
            }
        }
    }

    private fun openPaymentPage(method: String, amount: Int) {
        val intent = Intent(this, PaymentActivity::class.java).apply {
            putExtra(PaymentActivity.EXTRA_PAYMENT_METHOD, method)
            putExtra(PaymentActivity.EXTRA_PAYMENT_AMOUNT, amount)
        }
        paymentLauncher.launch(intent)
    }

    private fun loadExistingOrder() {
        if (entryId.isBlank()) return

        lifecycleScope.launch {
            try {
                val order = FirestoreRepository.getOrderForEntry(entryId)
                if (order != null) {
                    editingOrderId = order.orderId
                    paymentConfirmedMethod = if (order.paymentMethod != "Cash on Delivery") order.paymentMethod else null
                    parseOrderItems(order.items)
                    binding.btnPlaceOrder.text = "Update Order"
                    updateTotal()
                }
            } catch (_: Exception) {
                // Ignore if no existing order is found
            }
        }
    }

    private fun parseOrderItems(items: String) {
        cart.clear()
        items.lines().forEach { line ->
            val parts = line.trim().split(" x")
            if (parts.size == 2) {
                val name = parts[0].trim()
                val quantity = parts[1].toIntOrNull() ?: 1
                val item = latestMenu.find { it.name == name }
                cart.add(CartItem(name, item?.price ?: 0, quantity))
            }
        }
        cartAdapter.notifyDataSetChanged()
    }

    private fun addToCart(item: MenuItem) {

        val existing = cart.find { it.name == item.name }

        if (existing != null) {
            existing.quantity++
        } else {
            cart.add(CartItem(item.name, item.price, 1))
        }

        cartAdapter.notifyDataSetChanged()
        updateTotal()
    }

    private fun updateTotal() {
        val total = cart.sumOf { it.price * it.quantity }
        binding.tvTotal.text = "Total: ₹$total"
    }

    private fun setupSwipeToDelete() {

        val swipe = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

                val position = viewHolder.adapterPosition

                if (position >= 0 && position < cart.size) {
                    cart.removeAt(position)
                    cartAdapter.notifyItemRemoved(position)
                    updateTotal()
                }
            }
        }

        ItemTouchHelper(swipe).attachToRecyclerView(binding.rvCart)
    }

    private fun placeOrder() {

        if (cart.isEmpty()) {
            toast("Cart is empty")
            return
        }

        if (entryId.isEmpty()) {
            toast("Try again in a moment ⏳")
            return
        }

        val total = cart.sumOf { it.price * it.quantity }

        val selectedPaymentMethod = try {
            val radioButton = binding.paymentMethodGroup.findViewById<android.widget.RadioButton>(
                binding.paymentMethodGroup.checkedRadioButtonId
            )
            radioButton?.text?.toString().orEmpty().ifBlank { "Cash on Delivery" }
        } catch (t: Throwable) {
            "Cash on Delivery"
        }

        if (selectedPaymentMethod != "Cash on Delivery" && paymentConfirmedMethod != selectedPaymentMethod) {
            toast("Complete $selectedPaymentMethod payment first")
            openPaymentPage(selectedPaymentMethod, total)
            return
        }

        binding.btnPlaceOrder.isEnabled = false

        val orderText = cart.joinToString("\n") {
            "${it.name} x${it.quantity}"
        }

        val detailedOrderText = "$orderText\nPayment: $selectedPaymentMethod"

        lifecycleScope.launch {
            try {
                val order = Order(
                    orderId = editingOrderId.orEmpty(),
                    userId = session.userId,
                    queueId = queueId,
                    items = orderText,
                    total = total,
                    paymentMethod = selectedPaymentMethod,
                    status = "Placed",
                    timestamp = System.currentTimeMillis()
                )

                val orderId = if (editingOrderId.isNullOrBlank()) {
                    FirestoreRepository.createOrder(order)
                } else {
                    FirestoreRepository.updateOrder(order)
                    editingOrderId!!
                }

                FirestoreRepository.updateOrderDetails(entryId, detailedOrderText)
                FirestoreRepository.attachOrderToEntry(entryId, orderId)
                FirestoreRepository.updateQueueEntryOrderStatus(entryId, QueueEntry.ORDER_WAITING)

                // Add loyalty points
                FirestoreRepository.addUserPoints(session.userId, 10) // 10 points per order

                session.addRecentOrder(orderText, total)

                FirestoreRepository.pushNotification(
                    userId = session.userId,
                    title = "Order placed",
                    message = "Your order for ₹$total has been placed."
                )

                toast("Order placed ₹$total")

                val intent = Intent(this@OrderActivity, UserStatusActivity::class.java)
                intent.putExtra(UserStatusActivity.EXTRA_QUEUE_ID, queueId)
                startActivity(intent)

                finish()
            } catch (e: Exception) {
                toast("Error: ${e.message}")
                binding.btnPlaceOrder.isEnabled = true
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}