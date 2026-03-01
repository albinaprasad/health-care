package com.example.healthcare.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.healthcare.R
import com.example.healthcare.TokenManager.UserPreferenceSaving
import com.example.healthcare.api.RetrofitClient
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

class PrescriptionCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "PrescriptionWorker"
        private const val CHANNEL_ID = "prescription_change_channel"
        private const val NOTIF_ID   = 2001
    }

    override suspend fun doWork(): Result {
        return try {
            val prefs   = UserPreferenceSaving(applicationContext)
            val token   = prefs.getToken().first()
            val elderId = prefs.getElderIdOnce()

            if (token.isNullOrBlank() || elderId == -1) {
                Log.d(TAG, "Not logged in — skipping prescription check")
                return Result.success()
            }

            val response = RetrofitClient.loginApi.getPrescriptions(
                token   = "Bearer $token",
                elderId = elderId
            )

            if (!response.isSuccessful) {
                Log.w(TAG, "API error ${response.code()} — will retry")
                return Result.retry()
            }

            val prescriptions = response.body() ?: emptyList()

            // Build a deterministic string from the prescriptions and hash it
            val dataString = prescriptions
                .sortedBy { it.prescriptionID }
                .joinToString("|") { p ->
                    "${p.prescriptionID},${p.medicineName},${p.dosage},${p.frequency},${p.isActive},${p.notes},${p.prescriptionDate}"
                }
            val newHash = md5(dataString)

            val oldHash = prefs.getPrescriptionHashOnce()

            Log.d(TAG, "oldHash=$oldHash  newHash=$newHash")

            if (oldHash.isNotEmpty() && oldHash != newHash) {
                // Prescription data has changed!
                Log.i(TAG, "Prescription change detected — showing notification")
                showChangeNotification()
            }

            // Always save latest hash
            prefs.savePrescriptionHash(newHash)

            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Error checking prescriptions: ${e.message}", e)
            Result.retry()
        }
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun showChangeNotification() {
        val notifManager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel (safe to call multiple times)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Prescription Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when your prescriptions have been updated"
            }
            notifManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("\uD83D\uDCCB Prescription Updated")
            .setContentText("Your prescriptions have been changed. Open the app to review.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notifManager.notify(NOTIF_ID, notification)
    }
}
