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
import com.example.healthcare.services.LocationService
import com.example.healthcare.viewModels.WelcomeScreenViewModel
import com.example.healthcare.views.mainScreen.MainScreenActivity
import com.example.healthcare.views.signUp.SignUpActivity
import kotlinx.coroutines.launch

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

        userPreferenceObj = PrefManager.get(this)
        setUplisteners()
        observeViewModels()
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

            SignInView.setLoginButtonClick {  email,password->
                loginUser(email,password)

            }

            SignInView.signUpClick {
                SignUpActivity.startActivity(this@WelcomeActivity)
            }
        }
    }

    fun loginUser(email: String, password: String) {
        lifecycleScope.launch {
            try {
                val requestBody = mapOf(
                    "ElderMail" to email,
                    "Password" to password)
                val response = RetrofitClient.loginApi.loginElder(requestBody)

                if (response.isSuccessful) {

                    val token = response.body()?.get("token")

                    if (token != null) {
                        saveToken(token)
                        MainScreenActivity.startActivity(this@WelcomeActivity)
                    }

                } else {
                    Toast.makeText(this@WelcomeActivity,
                        "Login failed",
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