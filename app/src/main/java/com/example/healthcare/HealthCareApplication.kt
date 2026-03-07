package com.example.healthcare

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.healthcare.api.RetrofitClient
import com.example.healthcare.workers.PrescriptionCheckWorker
import java.util.concurrent.TimeUnit

class HealthCareApplication : Application() {

    companion object {
        private const val WORK_NAME = "prescription_check_work"
        private const val FALL_ALERT_CHANNEL_ID = "fall_alert_channel"
    }

    override fun onCreate() {
        super.onCreate()
        RetrofitClient.init(this)
        createFallAlertNotificationChannel()
        schedulePrescriptionPolling()
    }

    private fun createFallAlertNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val channel = NotificationChannel(
                FALL_ALERT_CHANNEL_ID,
                "Fall Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical fall detection alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setSound(
                    alarmSound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun schedulePrescriptionPolling() {
        val workRequest = PeriodicWorkRequestBuilder<PrescriptionCheckWorker>(
            15, TimeUnit.MINUTES          // minimum interval for WorkManager
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,   // don't restart if already scheduled
            workRequest
        )
    }
}
