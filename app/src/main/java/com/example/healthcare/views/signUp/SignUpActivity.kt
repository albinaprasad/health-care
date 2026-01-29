package com.example.healthcare.views.signUp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.healthcare.R
import com.example.healthcare.api.RetrofitClient
import com.example.healthcare.databinding.ActivitySignUpBinding
import com.example.healthcare.dataclasses.ElderSignupRequest
import com.example.healthcare.views.WelcomeActivity
import kotlinx.coroutines.launch

class SignUpActivity : AppCompatActivity() {

    companion object {
        fun startActivity(context: Context) {
            val intent = Intent(context, SignUpActivity::class.java)
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivitySignUpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)   // ✅ MUST be first

        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupSignUpClick()
        setClickListeners()
    }

    fun setClickListeners(){
        with(binding){
            signInBtn.setOnClickListener {
                finish()
            }
        }
    }

    private fun setupSignUpClick() {

        binding.SignUpBtn.setOnClickListener {

            val name = binding.nameInput.text.toString().trim()
            val email = binding.emailInput.text.toString().trim()
            val ageText = binding.ageInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            val gender = when (binding.genderRadioGroup.checkedRadioButtonId) {
                R.id.radioMale -> "Male"
                R.id.radioFemale -> "Female"
                else -> ""
            }

            if (name.isEmpty() || email.isEmpty() || ageText.isEmpty()
                || password.isEmpty() || gender.isEmpty()
            ) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val age = ageText.toIntOrNull()
            if (age == null) {
                Toast.makeText(this, "Enter a valid age", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val request = ElderSignupRequest(
                elderName = name,
                elderMail = email,
                age = age,
                gender = gender,
                password = password
            )

            callSignUpApi(request)

        }
    }

    fun callSignUpApi(request: ElderSignupRequest) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.signUPApi.signupElder(request)

                if (response.isSuccessful) {
                    Toast.makeText(
                        this@SignUpActivity,
                        "Signup successful",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Optional: navigate to main screen
                    // startActivity(Intent(this@SignUpActivity, MainScreenActivity::class.java))
                    // finish()

                } else {

                    when(response.code())
                    {
                        409->  Toast.makeText(
                            this@SignUpActivity,
                            "User already exist",
                            Toast.LENGTH_SHORT
                        ).show()
                        else ->      Toast.makeText(
                            this@SignUpActivity,
                            "Signup failed: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@SignUpActivity,
                    "Network error: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
