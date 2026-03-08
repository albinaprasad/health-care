package com.example.healthcare.views

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.healthcare.TokenManager.PrefManager
import com.example.healthcare.TokenManager.UserPreferenceSaving
import com.example.healthcare.api.RetrofitClient
import com.example.healthcare.databinding.ActivityWelcomeBinding
import com.example.healthcare.dataclasses.FcmTokenRequest
import com.example.healthcare.services.LocationService
import com.example.healthcare.viewModels.WelcomeScreenViewModel
import com.example.healthcare.views.mainScreen.MainScreenActivity
import com.example.healthcare.views.signUp.SignUpActivity
import com.example.healthcare.views.urlConfig.UrlConfigActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.messaging.FirebaseMessaging
class WelcomeActivity : AppCompatActivity() {
    companion object {

        private const val PERMISSION_FINE_LOCATION = 1
        private const val PERMISSION_BACKGROUND_LOCATION = 2

        fun startActivity(context: Context) {
            val intent = Intent(context, WelcomeActivity::class.java)
            context.startActivity(intent)
        }
    }

    lateinit var userPreferenceObj: UserPreferenceSaving
    lateinit var binding: ActivityWelcomeBinding
    private val viewmodel: WelcomeScreenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()


        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Intercept FCM notification clicks
        val type = intent.getStringExtra("type")
        val alertId = intent.getStringExtra("alertId") ?: intent.getStringExtra("alert_id") ?: "unknown"
        val isFallAlert = type == "fall_alert" || intent.extras?.keySet()?.any { 
            intent.getStringExtra(it)?.contains("fall", ignoreCase = true) == true 
        } == true

        if (isFallAlert) {
            Log.d("WelcomeActivity", "Fall alert notification clicked, redirecting...")
            val alertIntent = Intent(this, com.example.healthcare.views.fallAlert.FallAlertActivity::class.java).apply {
                putExtra(com.example.healthcare.views.fallAlert.FallAlertActivity.EXTRA_ALERT_ID, alertId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(alertIntent)
            finish()
            return
        }

        userPreferenceObj = PrefManager.get(this)
        RetrofitClient.init(this)

        lifecycleScope.launch {
            val elderId = userPreferenceObj.getElderIdOnce()
            if (elderId != -1) {
                // User is already logged in, navigate straight to main screen
                android.util.Log.d("WelcomeActivity", "User already logged in with elderId=$elderId, auto-login.")
                MainScreenActivity.startActivity(this@WelcomeActivity)
                finish()
            } else {
                // Show welcome/login screen
                setUplisteners()
                observeViewModels()
            }
        }
    }

    private fun checkPermission() {

        // Step 1: Check FINE location
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED


        if (!fineLocationGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSION_FINE_LOCATION
            )
            return
        }

        // Step 2: Check BACKGROUND location (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val backgroundLocationGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!backgroundLocationGranted) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    PERMISSION_BACKGROUND_LOCATION
                )
                return
            }
        }

        // Step 3: Start service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            startLocationService()
        } else {

        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startLocationService() {

        try {
            val intent = Intent(this, LocationService::class.java)
            startForegroundService(intent)

        } catch (e: Exception) {

        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isNotEmpty()) {
            val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED

            if (granted) {
                checkPermission()
            }
        }
    }

    private fun setUplisteners() {
        with(binding) {
            btnContinue.setOnClickListener {
                viewmodel.onContinueButtonClicked()
            }

            btnSettings.setOnClickListener {
                UrlConfigActivity.startActivity(this@WelcomeActivity)
            }

            SignInView.setLoginButtonClick { email, password ->
                loginUser(email, password)
            }

            SignInView.signUpClick {
                SignUpActivity.startActivity(this@WelcomeActivity)
            }
        }
    }

    fun loginUser(email: String, password: String) {
        lifecycleScope.launch {
            try {
                // Get FCM token first
                val fcmToken = try {
                    FirebaseMessaging.getInstance().token.await()
                } catch (e: Exception) {
                    Log.e("Login", "Failed to get FCM token", e)
                    ""
                }

                val requestBody = mapOf(
                    "elderMail" to email,
                    "password" to password,
                    "FCMToken" to fcmToken
                )
                val response = RetrofitClient.loginApi.loginElder(requestBody)

                if (response.isSuccessful) {
                    val body = response.body()

                    // Gson deserialises JSON strings as String and numbers as Double
                    val token    = body?.get("token") as? String
                    // elderId comes back as a JSON number → Gson gives us Double
                    val elderIdRaw = body?.get("elderId")
                    val elderId  = when (elderIdRaw) {
                        is Double -> elderIdRaw.toInt()
                        is Int    -> elderIdRaw
                        is String -> elderIdRaw.toIntOrNull() ?: -1
                        else      -> -1
                    }
                    
                    // Try to guess name from token
                    var elderName = (body?.get("name") as? String) ?: (body?.get("elderName") as? String)
                    if (elderName == null && token != null) {
                        try {
                            val parts = token.split(".")
                            if (parts.size == 3) {
                                val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
                                // Very basic extraction: look for email address
                                val emailMatch = "\"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress\":\"([^\"]+)\"".toRegex().find(payload)
                                if (emailMatch != null) {
                                    elderName = emailMatch.groupValues[1].split("@").first().replaceFirstChar { it.uppercase() }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("Login", "Failed to parse JWT", e)
                        }
                    }
                    
                    val finalName = elderName ?: "User"

                    Log.d("Login", "token=$token  elderId=$elderId  elderName=$finalName  raw=$elderIdRaw")

                    if (token != null) {
                        saveToken(token)
                        userPreferenceObj.saveElderId(elderId)
                        userPreferenceObj.saveElderName(finalName)
                        userPreferenceObj.saveFcmToken(fcmToken)
                        MainScreenActivity.startActivity(this@WelcomeActivity)
                    }

                } else {
                    Toast.makeText(this@WelcomeActivity,
                        "Login failed (${response.code()})",
                        Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(this@WelcomeActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private  fun saveToken(token: String) {
        lifecycleScope.launch {
            userPreferenceObj.saveToken(token)
            Log.i("abc", "saveToken: $token")
            checkPermission()
        }

    }

    private fun registerFcmToken(elderId: Int) {
        lifecycleScope.launch {
            try {
                val fcmToken = FirebaseMessaging.getInstance().token.await()
                Log.d("Login", "FCM token: $fcmToken")

                // Save locally
                userPreferenceObj.saveFcmToken(fcmToken)

                // Send to server
                val response = RetrofitClient.loginApi.registerFcmToken(
                    FcmTokenRequest(elderId = elderId, fcmToken = fcmToken)
                )
                Log.d("Login", "FCM token registered: ${response.isSuccessful}")
            } catch (e: Exception) {
                Log.e("Login", "Failed to register FCM token", e)
            }
        }
    }

    private fun observeViewModels(){
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED){
                viewmodel.animationState.collect { value ->
                    if(value){
                        animateViews()
                    }
                }
            }
        }
    }

    private fun animateViews() {
        with(binding) {
            SignInView.alpha = 0f
            SignInView.visibility = View.VISIBLE

            welcomeText.alpha = 1f
            welcomeDescription.alpha = 1f
            btnContinue.alpha = 1f

            // Fade away the dot animation
            dotAnimationView.animate()
                .alpha(0f)
                .setDuration(1500)
                .start()

            SignInView.animate()
                .alpha(1f)
                .setDuration(1500)
                .start()

            welcomeText.animate()
                .alpha(0f)
                .setDuration(1500)
                .start()

            welcomeDescription.animate()
                .alpha(0f)
                .setDuration(1500)
                .start()

            btnContinue.animate()
                .alpha(0f)
                .setDuration(1500)
                .withEndAction {
                    welcomeText.visibility = View.GONE
                    welcomeDescription.visibility = View.GONE
                    btnContinue.visibility = View.GONE
                }
                .start()
        }
    }
}