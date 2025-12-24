package com.example.healthcare.views.mainScreen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.healthcare.R
import com.example.healthcare.databinding.ActivityMainScreenBinding

class MainScreenActivity : AppCompatActivity() {

    companion object{
        fun startActivity(context: Context){
            val intent = Intent(context, MainScreenActivity::class.java)
            context.startActivity(intent)
        }
    }
    lateinit var binding: ActivityMainScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Only apply top padding to push content below status bar
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

       setUpListeners()
        setUpNavDrawer()
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
                        Toast.makeText(this@MainScreenActivity, "Prescription clicked", Toast.LENGTH_SHORT).show()
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


}