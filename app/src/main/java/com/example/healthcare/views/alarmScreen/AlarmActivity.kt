package com.example.healthcare.views.alarmScreen

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.healthcare.R
import com.example.healthcare.TokenManager.UserPreferenceSaving
import com.example.healthcare.adapters.PrescriptionAdapter
import com.example.healthcare.api.RetrofitClient
import com.example.healthcare.dataclasses.PrescriptionResponse
import com.example.healthcare.databinding.ActivityAlarmBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmActivity : AppCompatActivity() {

    companion object {
        const val CHANNEL_ID     = "prescription_channel"
        const val NOTIF_ID       = 1001
        const val ACTION_REMIND  = "com.example.healthcare.PRESCRIPTION_REMINDER"

        fun startActivity(context: Context) {
            val intent = Intent(context, AlarmActivity::class.java)
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityAlarmBinding
    private lateinit var userPrefs: UserPreferenceSaving

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        userPrefs = UserPreferenceSaving(this)
        createNotificationChannel()

        binding.btnBack.setOnClickListener { finish() }

        loadPrescriptions()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadPrescriptions() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val token   = userPrefs.getToken().first()
                val elderId = userPrefs.getElderIdOnce()

                Log.d("Rx", "token=$token  elderId=$elderId")

                if (token.isNullOrBlank()) {
                    showError("Not logged in. Please log in again.")
                    return@launch
                }
                if (elderId == -1) {
                    showError("Elder ID not found — please log out and log in again.")
                    return@launch
                }

                val response = RetrofitClient.loginApi.getPrescriptions(
                    token    = "Bearer $token",
                    elderId  = elderId
                )

                showLoading(false)

                if (response.isSuccessful) {
                    val prescriptions = response.body() ?: emptyList()
                    updateSummaryCard(prescriptions)
                    if (prescriptions.isEmpty()) {
                        showEmpty(true)
                    } else {
                        showEmpty(false)
                        setupRecyclerView(prescriptions)
                        schedulePrescriptionAlarms(prescriptions)
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Log.e("Rx", "HTTP ${response.code()} $errorBody")
                    showError("Failed to load prescriptions (${response.code()}): $errorBody")
                }

            } catch (e: Exception) {
                showLoading(false)
                Log.e("Rx", "Exception: ${e.message}", e)
                showError("Error: ${e.message}")

            }
        }
    }

    private fun setupRecyclerView(items: List<PrescriptionResponse>) {
        binding.rvPrescriptions.layoutManager = LinearLayoutManager(this)
        binding.rvPrescriptions.adapter       = PrescriptionAdapter(items)
        binding.rvPrescriptions.visibility    = View.VISIBLE
    }

    private fun updateSummaryCard(prescriptions: List<PrescriptionResponse>) {
        binding.tvTotalCount.text  = prescriptions.size.toString()
        binding.tvActiveCount.text = prescriptions.count { it.isActive }.toString()
    }

    // ─── Alarm scheduling ────────────────────────────────────────────────────

    private fun schedulePrescriptionAlarms(prescriptions: List<PrescriptionResponse>) {
        prescriptions.filter { it.isActive }.forEach { p ->
            parseTimes(p.frequency).forEachIndexed { i, (hour, minute) ->
                scheduleAlarm(p.prescriptionID * 100 + i, hour, minute, p.medicineName, p.dosage)
            }
        }
    }

    /**
     * Parses common Frequency strings into (hour, minute) pairs.
     * Supports: "HH:mm", "HH:mm, HH:mm", "Morning", "Evening",
     *           "Once daily", "Twice daily", "Thrice daily"
     */
    private fun parseTimes(frequency: String): List<Pair<Int, Int>> {
        val f = frequency.trim().lowercase()
        // Detect explicit time patterns like "08:00" or "08:00, 20:00"
        val timeRegex = Regex("""(\d{1,2}):(\d{2})""")
        val matches = timeRegex.findAll(frequency).toList()
        if (matches.isNotEmpty()) {
            return matches.map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        }
        return when {
            "morning"      in f -> listOf(8 to 0)
            "afternoon"    in f -> listOf(13 to 0)
            "evening"      in f -> listOf(18 to 0)
            "night"        in f -> listOf(21 to 0)
            "thrice"       in f -> listOf(8 to 0, 14 to 0, 20 to 0)
            "twice"        in f -> listOf(8 to 0, 20 to 0)
            "once"         in f -> listOf(8 to 0)
            else                -> listOf(8 to 0) // default: 8 AM
        }
    }

    private fun scheduleAlarm(
        requestCode: Int,
        hour: Int,
        minute: Int,
        medicineName: String,
        dosage: String
    ) {
        val alarmManager = getSystemService(AlarmManager::class.java)
        val intent = Intent(this, PrescriptionReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra("medicine", medicineName)
            putExtra("dosage",   dosage)
        }
        val pending = PendingIntent.getBroadcast(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE,      minute)
            set(Calendar.SECOND,      0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Fallback to inexact
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP, cal.timeInMillis,
                AlarmManager.INTERVAL_DAY, pending
            )
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending
            )
        }
    }

    // ─── Notification channel ─────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Prescription Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Alerts you when it is time to take your medicine" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    // ─── UI helpers ───────────────────────────────────────────────────────────

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility    = if (show) View.VISIBLE else View.GONE
        binding.rvPrescriptions.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showEmpty(show: Boolean) {
        binding.tvEmpty.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        showLoading(false)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}

// ─── Broadcast Receiver ───────────────────────────────────────────────────────

class PrescriptionReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medicine = intent.getStringExtra("medicine") ?: "Medicine"
        val dosage   = intent.getStringExtra("dosage")   ?: ""

        Log.d("PrescriptionReminder", "🔔 Receiver fired! medicine=$medicine dosage=$dosage")

        // Post a heads-up notification
        val notifManager = context.getSystemService(NotificationManager::class.java)

        // Ensure channel exists (in case it hasn't been created yet)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = notifManager.getNotificationChannel(AlarmActivity.CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    AlarmActivity.CHANNEL_ID,
                    "Prescription Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Alerts you when it is time to take your medicine" }
                notifManager.createNotificationChannel(channel)
                Log.d("PrescriptionReminder", "Created notification channel in receiver")
            }
        }

        val notification = NotificationCompat.Builder(context, AlarmActivity.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("\uD83D\uDC8A Time to take your medicine!")
            .setContentText("$medicine — $dosage")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notifManager.notify(AlarmActivity.NOTIF_ID, notification)
        Log.d("PrescriptionReminder", "Notification posted")

        // Show a dialog if the app is in the foreground by launching MainScreenActivity
        val dialogIntent = Intent(context, com.example.healthcare.views.mainScreen.MainScreenActivity::class.java).apply {
            putExtra("show_reminder", true)
            putExtra("medicine", medicine)
            putExtra("dosage",   dosage)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(dialogIntent)
    }
}
