package com.example.healthcare.views.fallAlert

import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.healthcare.TokenManager.UserPreferenceSaving
import com.example.healthcare.api.RetrofitClient
import com.example.healthcare.databinding.ActivityFallAlertBinding
import com.example.healthcare.dataclasses.FallAlertResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FallAlertActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FallAlertActivity"
        const val EXTRA_ALERT_ID = "extra_alert_id"
        private const val FALL_ALERT_NOTIFICATION_ID = 999
    }

    private lateinit var binding: ActivityFallAlertBinding
    private var alertId: String = "unknown"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityFallAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alertId = intent.getStringExtra(EXTRA_ALERT_ID) ?: "unknown"

        // Dismiss the notification since the user is seeing the activity
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(FALL_ALERT_NOTIFICATION_ID)

        binding.btnImOkay.setOnClickListener {
            sendResponse("yes")
            finish()
        }

        binding.btnNeedHelp.setOnClickListener {
            sendResponse("no")
            finish()
        }
    }

    private fun sendResponse(userResponse: String) {
        // Disable buttons to prevent double tap
        binding.btnImOkay.isEnabled = false
        binding.btnNeedHelp.isEnabled = false

        val prefs = UserPreferenceSaving(applicationContext)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val elderId = prefs.getElderIdOnce()
                val request = FallAlertResponse(
                    elderId = elderId,
                    response = userResponse
                )

                val response = RetrofitClient.signUPApi.sendFallResponse(request)
                Log.d(TAG, "Fall response sent: ${response.isSuccessful}")

                withContext(Dispatchers.Main) {
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send fall response", e)
                withContext(Dispatchers.Main) {
                    // Re-enable buttons so user can retry
                    binding.btnImOkay.isEnabled = true
                    binding.btnNeedHelp.isEnabled = true
                }
            }
        }
    }
}
