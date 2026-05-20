package com.queueless.plus.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.queueless.plus.R
import com.queueless.plus.utils.AuthManager
import com.queueless.plus.utils.SessionManager
import com.queueless.plus.utils.ThemeUtils

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtils.applyTheme(this, SessionManager(this))
        setContentView(R.layout.activity_splash)

        // Navigate after 2 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            navigateNext()
        }, 2000)
    }

    private fun navigateNext() {
        val destination = if (AuthManager.isLoggedIn) {
            val session = SessionManager(this)
            if (session.isAdmin) AdminPanelActivity::class.java else DashboardActivity::class.java
        } else {
            LoginActivity::class.java
        }
        startActivity(Intent(this, destination))
        finish()
    }
}
