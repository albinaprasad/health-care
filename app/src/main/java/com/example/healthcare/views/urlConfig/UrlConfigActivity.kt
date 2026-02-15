package com.example.healthcare.views.urlConfig

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.healthcare.TokenManager.UrlPreferences
import com.example.healthcare.databinding.ActivityUrlConfigBinding
import kotlinx.coroutines.launch

class UrlConfigActivity : AppCompatActivity() {

    companion object {
        fun startActivity(context: Context) {
            val intent = Intent(context, UrlConfigActivity::class.java)
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityUrlConfigBinding
    private lateinit var urlPreferences: UrlPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityUrlConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        urlPreferences = UrlPreferences(this)

        loadSavedUrls()
        setupListeners()
    }

    private fun loadSavedUrls() {
        lifecycleScope.launch {
            val apiUrl = urlPreferences.getApiUrlOnce()
            val wsUrl = urlPreferences.getWsUrlOnce()
            binding.etApiUrl.setText(apiUrl)
            binding.etWsUrl.setText(wsUrl)
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSave.setOnClickListener {
            val apiUrl = binding.etApiUrl.text.toString().trim()
            val wsUrl = binding.etWsUrl.text.toString().trim()

            if (apiUrl.isEmpty() || wsUrl.isEmpty()) {
                Toast.makeText(this, "Please fill in both URLs", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                urlPreferences.saveUrls(apiUrl, wsUrl)
                Toast.makeText(
                    this@UrlConfigActivity,
                    "URLs saved! Restart the app for changes to take effect.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }
}
