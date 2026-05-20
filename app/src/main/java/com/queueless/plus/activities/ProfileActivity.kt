package com.queueless.plus.activities

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage
import com.queueless.plus.databinding.ActivityProfileBinding
import com.queueless.plus.utils.FirestoreRepository
import com.queueless.plus.utils.SessionManager
import com.queueless.plus.utils.ThemeUtils
import com.queueless.plus.utils.toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var session: SessionManager
    private val storage = FirebaseStorage.getInstance()
    private val PICK_IMAGE_REQUEST = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        ThemeUtils.applyTheme(this, session)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Profile"

        loadUserProfile()

        binding.btnSaveProfile.setOnClickListener { saveProfile() }
        binding.btnChangeAvatar.setOnClickListener { openImagePicker() }
        binding.btnOpenNotificationCenter.setOnClickListener {
            startActivity(Intent(this, NotificationCenterActivity::class.java))
        }

        binding.btnOpenChat.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
        binding.btnOpenFavoritesRecent.setOnClickListener {
            startActivity(Intent(this, FavoritesRecentActivity::class.java))
        }

        binding.spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val language = if (position == 1) "hi" else "en"
                setLocale(language)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun loadUserProfile() {
        lifecycleScope.launch {
            try {
                val user = FirestoreRepository.getUser(session.userId)
                user?.let {
                    binding.etName.setText(it.name)
                    binding.tvEmail.text = "Email: ${it.email}"
                    binding.tvRole.text = "Role: ${it.role}"
                    binding.tvPoints.text = "Loyalty Points: ${it.loyaltyPoints}"
                    if (it.avatarUrl.isNotEmpty()) {
                        Glide.with(this@ProfileActivity)
                            .load(it.avatarUrl)
                            .circleCrop()
                            .into(binding.ivAvatar)
                    }
                }
                binding.spinnerLanguage.setSelection(if (getCurrentLanguage() == "hi") 1 else 0)
            } catch (e: Exception) {
                toast("Failed to load profile: ${e.message}")
            }
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val imageUri = data.data
            imageUri?.let { uploadAvatar(it) }
        }
    }

    private fun uploadAvatar(imageUri: Uri) {
        lifecycleScope.launch {
            try {
                val storageRef = storage.reference.child("avatars/${session.userId}.jpg")
                storageRef.putFile(imageUri).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                FirestoreRepository.updateUserAvatar(session.userId, downloadUrl)
                Glide.with(this@ProfileActivity)
                    .load(downloadUrl)
                    .circleCrop()
                    .into(binding.ivAvatar)
                toast("Avatar updated")
            } catch (e: Exception) {
                toast("Failed to upload avatar: ${e.message}")
            }
        }
    }

    private fun saveProfile() {
        val updatedName = binding.etName.text?.toString()?.trim().orEmpty()
        if (updatedName.isEmpty()) {
            toast("Name cannot be empty")
            return
        }
        lifecycleScope.launch {
            try {
                FirestoreRepository.updateUserName(session.userId, updatedName)
                session.userName = updatedName
                toast("Profile updated")
            } catch (e: Exception) {
                toast("Failed to update profile: ${e.message}")
            }
        }
    }

    private fun getCurrentLanguage(): String {
        return resources.configuration.locale.language
    }

    private fun setLocale(language: String) {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
        recreate()
    }
}

