package com.example.healthcare.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.healthcare.R
import com.example.healthcare.TokenManager.UserPreferenceSaving
import com.example.healthcare.api.RetrofitClient
import com.example.healthcare.dataclasses.FcmTokenRequest
import com.example.healthcare.views.fallAlert.FallAlertActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FcmService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FcmService"
        const val FALL_ALERT_CHANNEL_ID = "fall_alert_channel"
        private const val FALL_ALERT_NOTIFICATION_ID = 999
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")

        val prefs = UserPreferenceSaving(applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            prefs.saveFcmToken(token)

            val elderId = prefs.getElderIdOnce()
            if (elderId != -1) {
                try {
                    val response = RetrofitClient.signUPApi.registerFcmToken(
                        FcmTokenRequest(elderId = elderId, fcmToken = token)
                    )
                    Log.d(TAG, "FCM token registered: ${response.isSuccessful}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register FCM token", e)
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message received - data: ${message.data}")
        Log.d(TAG, "FCM message received - notification title: ${message.notification?.title}")
        Log.d(TAG, "FCM message received - notification body: ${message.notification?.body}")

        val data = message.data
        val type = data["type"]

        // Handle fall alert from data payload
        val isFallAlert = type == "fall_alert"
                || message.notification?.title?.contains("fall", ignoreCase = true) == true
                || message.notification?.body?.contains("fall", ignoreCase = true) == true

        if (isFallAlert) {
            val alertId = data["alertId"] ?: data["alert_id"] ?: "unknown"
            Log.d(TAG, "Fall alert detected! alertId=$alertId")
            showFallAlertNotification(alertId)
            launchFallAlertActivity(alertId)
        }
    }

    private fun showFallAlertNotification(alertId: String) {
        val intent = Intent(this, FallAlertActivity::class.java).apply {
            putExtra(FallAlertActivity.EXTRA_ALERT_ID, alertId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, FALL_ALERT_NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(this, FALL_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⚠️ Fall Detected!")
            .setContentText("A fall has been detected. Tap to respond.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(FALL_ALERT_NOTIFICATION_ID, notification)
    }

    private fun launchFallAlertActivity(alertId: String) {
        try {
            val intent = Intent(this, FallAlertActivity::class.java).apply {
                putExtra(FallAlertActivity.EXTRA_ALERT_ID, alertId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            Log.d(TAG, "FallAlertActivity launched successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch FallAlertActivity", e)
        }
    }
}
