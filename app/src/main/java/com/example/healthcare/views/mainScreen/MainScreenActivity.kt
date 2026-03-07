package com.example.healthcare.views.mainScreen

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.healthcare.R
import com.example.healthcare.adapters.CalendarAdapter
import com.example.healthcare.adapters.WelcomeScreenAdapter
import com.example.healthcare.databinding.ActivityMainScreenBinding
import com.example.healthcare.dataclasses.GridItem
import com.example.healthcare.dataclasses.calenderDay
import com.example.healthcare.views.alarmScreen.AlarmActivity
import com.example.healthcare.views.signUp.SignUpActivity
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainScreenActivity : AppCompatActivity() {

    companion object{
        fun startActivity(context: Context){
            val intent = Intent(context, MainScreenActivity::class.java)
            context.startActivity(intent)
        }
    }
    lateinit var binding: ActivityMainScreenBinding

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
            android.util.Log.d("MainScreen", "Fall alert notification clicked, redirecting...")
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
            // Only apply top padding to push content below status bar
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val bottomNav = binding.bottomNav
        bottomNav.itemIconTintList = null

       setUpListeners()
        setUpNavDrawer()
        setUpAdapter()
        setDateAdapter()
    }
    private fun setUpAdapter(){
        val items = listOf(
            GridItem(R.drawable.ic_home_unfilled, "Home"),
            GridItem(R.drawable.ic_email, "Profile"),
            GridItem(R.drawable.ic_user_profile, "Help"),
            GridItem(R.drawable.ic_password, "Prescription")
        )

        binding.recyclerView.layoutManager = GridLayoutManager(this@MainScreenActivity, 2)
        binding.recyclerView.adapter = WelcomeScreenAdapter(items) { item ->
            when (item.title) {
                "Prescription" -> AlarmActivity.startActivity(this@MainScreenActivity)
                else -> { /* other items - handle as needed */ }
            }
        }
    }


    fun setUpListeners(){
        with(binding){
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
                        AlarmActivity.startActivity(this@MainScreenActivity)
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
            if(binding.drawerLayout.isDrawerOpen(GravityCompat.START)){
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun setDateAdapter() {
        val date = generateCurrentMonthDay()
        binding.dateRV.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.dateRV.adapter = CalendarAdapter(date)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun generateCurrentMonthDay(): List<calenderDay>{
        val list =mutableListOf<calenderDay>()
        val today = LocalDate.now()
        val currentMonth = YearMonth.now()
        for (day in 1..currentMonth.lengthOfMonth()) {
            val date = currentMonth.atDay(day)

            val letter = date.dayOfWeek.name.first().toString()

            list.add(
                calenderDay(
                    dayName =letter,
                    date = day,
                    isToday = date == today
                )
            )
        }
        return list

    }


}