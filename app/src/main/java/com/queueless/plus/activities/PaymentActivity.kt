package com.queueless.plus.activities

import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.queueless.plus.databinding.ActivityPaymentBinding

class PaymentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentBinding

    companion object {
        const val EXTRA_PAYMENT_METHOD = "extra_payment_method"
        const val EXTRA_PAYMENT_AMOUNT = "extra_payment_amount"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Complete Payment"

        val method = intent.getStringExtra(EXTRA_PAYMENT_METHOD).orEmpty()
        val amount = intent.getIntExtra(EXTRA_PAYMENT_AMOUNT, 0)

        binding.tvPaymentMethod.text = method
        binding.tvAmount.text = "Amount: ₹$amount"
        binding.tvDescription.text = "This is a dummy payment flow. Tap Pay Now to simulate completing your $method payment."

        binding.btnPayNow.setOnClickListener {
            setResult(Activity.RESULT_OK, intent.apply {
                putExtra(EXTRA_PAYMENT_METHOD, method)
            })
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
