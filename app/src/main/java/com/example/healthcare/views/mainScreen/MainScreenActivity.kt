package com.example.healthcare.views.mainScreen

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.healthcare.R
import com.example.healthcare.adapters.CalendarAdapter
import com.example.healthcare.adapters.PrescriptionAdapter
import com.example.healthcare.api.RetrofitClient
import com.example.healthcare.databinding.ActivityMainScreenBinding
import com.example.healthcare.dataclasses.PrescriptionResponse
import com.example.healthcare.dataclasses.calenderDay
import com.example.healthcare.TokenManager.UserPreferenceSaving
import com.example.healthcare.views.alarmScreen.AlarmActivity
import com.example.healthcare.views.alarmScreen.PrescriptionReminderReceiver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.Calendar

class MainScreenActivity : AppCompatActivity() {

    companion object {
        fun startActivity(context: Context) {
            val intent = Intent(context, MainScreenActivity::class.java)
            context.startActivity(intent)
        }
    }

    lateinit var binding: ActivityMainScreenBinding
    private lateinit var userPrefs: UserPreferenceSaving

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Intercept FCM notification clicks
        val type = intent.getStringExtra("type")
        val alertId = intent.getStringExtra("alertId") ?: intent.getStringExtra("alert_id") ?: "unknown"
        val isFallAlert = type == "fall_alert" || intent.extras?.keySet()?.any {
            intent.getStringExtra(it)?.contains("fall", ignoreCase = true) == true
        } == true

        if (isFallAlert) {
            Log.d("MainScreen", "Fall alert notification clicked, redirecting...")
            val alertIntent = Intent(this, com.example.healthcare.views.fallAlert.FallAlertActivity::class.java).apply {
                putExtra(com.example.healthcare.views.fallAlert.FallAlertActivity.EXTRA_ALERT_ID, alertId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(alertIntent)
            finish()
            return
        }

        enableEdgeToEdge()
        binding = ActivityMainScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val bottomNav = binding.bottomNav
        bottomNav.itemIconTintList = null

        userPrefs = UserPreferenceSaving(this)
        createNotificationChannel()

        // Request notification permission on Android 13+ (API 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        setUpListeners()
        setUpNavDrawer()
        setDateAdapter()

        // Show today's date in the header label
        val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH)
        binding.dateTV.text = "Today, ${dateFormat.format(java.util.Date())}"

        // Load the user's name for main screen AND nav drawer
        lifecycleScope.launch {
            val elderName = userPrefs.getElderNameOnce()
            binding.userName.text = elderName
            
            // Update Nav drawer header
            val headerView = binding.navMenu.getHeaderView(0)
            val navUserNameTextView = headerView.findViewById<android.widget.TextView>(R.id.tvMenuUserName)
            navUserNameTextView?.text = elderName
        }

        loadPrescriptions()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra("show_reminder", false)) {
            val medicine = intent.getStringExtra("medicine") ?: "Medicine"
            val dosage = intent.getStringExtra("dosage") ?: ""

            MaterialAlertDialogBuilder(this)
                .setTitle("💊 Time for your medicine!")
                .setMessage("Please take your prescription now:\n\n$medicine — $dosage")
                .setPositiveButton("Dismiss") { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(false)
                .show()
        }
    }

    // ─── Prescription loading ─────────────────────────────────────────────────

    private fun loadPrescriptions() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val token = userPrefs.getToken().first()
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
                    token = "Bearer $token",
                    elderId = elderId
                )

                showLoading(false)

                if (response.isSuccessful) {
                    val prescriptions = response.body() ?: emptyList()
                    updateSummaryCard(prescriptions)
                    if (prescriptions.isEmpty()) {
                        showEmpty(true)
                    } else {
                        showEmpty(false)
                        setupPrescriptionRecyclerView(prescriptions)
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

    private fun setupPrescriptionRecyclerView(items: List<PrescriptionResponse>) {
        binding.rvPrescriptions.layoutManager = LinearLayoutManager(this)
        binding.rvPrescriptions.adapter = PrescriptionAdapter(items)
        binding.rvPrescriptions.visibility = View.VISIBLE
    }

    private fun updateSummaryCard(prescriptions: List<PrescriptionResponse>) {
        binding.tvTotalCount.text = prescriptions.size.toString()
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

    private fun parseTimes(frequency: String): List<Pair<Int, Int>> {
        val f = frequency.trim().lowercase()
        val timeRegex = Regex("""(\d{1,2}):(\d{2})""")
        val matches = timeRegex.findAll(frequency).toList()
        if (matches.isNotEmpty()) {
            return matches.map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        }
        return when {
            "morning"   in f -> listOf(8 to 0)
            "afternoon" in f -> listOf(13 to 0)
            "evening"   in f -> listOf(18 to 0)
            "night"     in f -> listOf(21 to 0)
            "thrice"    in f -> listOf(8 to 0, 14 to 0, 20 to 0)
            "twice"     in f -> listOf(8 to 0, 20 to 0)
            "once"      in f -> listOf(8 to 0)
            else              -> listOf(8 to 0)
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
            action = AlarmActivity.ACTION_REMIND
            putExtra("medicine", medicineName)
            putExtra("dosage", dosage)
        }
        val pending = PendingIntent.getBroadcast(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
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

    // ─── UI helpers ───────────────────────────────────────────────────────────

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.rvPrescriptions.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showEmpty(show: Boolean) {
        binding.tvEmpty.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        showLoading(false)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // ─── Notification channel ─────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AlarmActivity.CHANNEL_ID,
                "Prescription Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Alerts you when it is time to take your medicine" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    // ─── Listeners & Navigation ───────────────────────────────────────────────

    fun setUpListeners() {
        with(binding) {
            navbtn.setOnClickListener {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }
    }

    fun setUpNavDrawer() {
        with(binding) {
            navMenu.setNavigationItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> {
                        Toast.makeText(this@MainScreenActivity, "Home clicked", Toast.LENGTH_SHORT).show()
                    }

                    R.id.nav_prescription -> {
                        // Smoothly scroll prescription list into view
                        binding.rvPrescriptions.smoothScrollToPosition(0)
                    }

                    R.id.nav_logout -> {
                        lifecycleScope.launch {
                            com.example.healthcare.TokenManager.PrefManager.get(this@MainScreenActivity).clearToken()
                            val intent = Intent(this@MainScreenActivity, com.example.healthcare.views.WelcomeActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                    }
                }
                drawerLayout.closeDrawer(GravityCompat.START)
                true
            }
        }
        binding.main.setOnClickListener {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
    }

    // ─── Calendar ─────────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.O)
    fun setDateAdapter() {
        val days = generateCurrentMonthDay()
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.dateRV.layoutManager = layoutManager
        binding.dateRV.adapter = CalendarAdapter(days)

        // Auto-scroll so today's date appears in the centre of the screen
        val todayIndex = days.indexOfFirst { it.isToday }
        if (todayIndex >= 0) {
            binding.dateRV.post {
                val rvWidth = binding.dateRV.width
                // Estimate item width (~68dp = 56 minWidth + 12 margin)
                val itemWidthPx = (68 * resources.displayMetrics.density).toInt()
                val offset = (rvWidth / 2) - (itemWidthPx / 2)
                layoutManager.scrollToPositionWithOffset(todayIndex, offset)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun generateCurrentMonthDay(): List<calenderDay> {
        val list = mutableListOf<calenderDay>()
        val today = LocalDate.now()
        val currentMonth = YearMonth.now()
        for (day in 1..currentMonth.lengthOfMonth()) {
            val date = currentMonth.atDay(day)
            val letter = date.dayOfWeek.name.first().toString()
            list.add(
                calenderDay(
                    dayName = letter,
                    date = day,
                    isToday = date == today
                )
            )
        }
        return list
    }
}