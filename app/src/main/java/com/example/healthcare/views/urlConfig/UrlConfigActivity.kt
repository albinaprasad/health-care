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

    /**
     * Validates the API URL.
     * Must start with "https://" and have a non-empty host after it.
     * Example: https://yevette-savable-laure.ngrok-free.dev
     */
    private fun isValidApiUrl(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        val host = url.removePrefix("https://").trimEnd('/')
        return host.isNotEmpty() && host.contains('.')
    }

    /**
     * Validates the WebSocket URL.
     * Must start with "wss://" and end with "/ws?token=".
     * Example: wss://sam-page-lender-humidity.trycloudflare.com/ws?token=
     */
    private fun isValidWsUrl(url: String): Boolean {
        if (!url.startsWith("wss://")) return false
        val withoutScheme = url.removePrefix("wss://")
        return withoutScheme.contains('.') && url.endsWith("/ws?token=")
    }

    /**
     * Normalises the API URL to always have a trailing slash (required by Retrofit).
     */
    private fun normaliseApiUrl(url: String): String {
        return if (url.endsWith("/")) url else "$url/"
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSave.setOnClickListener {
            val apiUrl = binding.etApiUrl.text.toString().trim()
            val wsUrl = binding.etWsUrl.text.toString().trim()

            // --- Empty check ---
            if (apiUrl.isEmpty()) {
                binding.etApiUrl.error = "API URL cannot be empty"
                binding.etApiUrl.requestFocus()
                return@setOnClickListener
            }
            if (wsUrl.isEmpty()) {
                binding.etWsUrl.error = "WebSocket URL cannot be empty"
                binding.etWsUrl.requestFocus()
                return@setOnClickListener
            }

            // --- Format validation ---
            if (!isValidApiUrl(apiUrl)) {
                binding.etApiUrl.error = "Must start with https://  e.g. https://example.ngrok-free.dev"
                binding.etApiUrl.requestFocus()
                return@setOnClickListener
            }
            if (!isValidWsUrl(wsUrl)) {
                binding.etWsUrl.error = "Must start with wss:// and end with /ws?token=  e.g. wss://example.trycloudflare.com/ws?token="
                binding.etWsUrl.requestFocus()
                return@setOnClickListener
            }

            // Clear any previous errors
            binding.etApiUrl.error = null
            binding.etWsUrl.error = null

            val normalisedApiUrl = normaliseApiUrl(apiUrl)

            lifecycleScope.launch {
                urlPreferences.saveUrls(normalisedApiUrl, wsUrl)
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
