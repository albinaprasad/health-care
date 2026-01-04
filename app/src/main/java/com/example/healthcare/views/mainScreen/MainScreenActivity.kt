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
import androidx.recyclerview.widget.GridLayoutManager
import com.example.healthcare.R
import com.example.healthcare.adapters.WelcomeScreenAdapter
import com.example.healthcare.databinding.ActivityMainScreenBinding
import com.example.healthcare.dataclasses.GridItem

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
        setUpAdapter()
    }
    private fun setUpAdapter(){
        val items = listOf(
            GridItem(R.drawable.ic_home_unfilled, "Home"),
            GridItem(R.drawable.ic_email, "Profile"),
            GridItem(R.drawable.ic_user_profile, "Help"),
            GridItem(R.drawable.ic_password, "Prescription")
        )

        binding.recyclerView.layoutManager= GridLayoutManager(this@MainScreenActivity,1)
        binding.recyclerView.adapter = WelcomeScreenAdapter(items)
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