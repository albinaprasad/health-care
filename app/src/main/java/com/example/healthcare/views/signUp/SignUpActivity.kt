package com.example.healthcare.views.signUp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.healthcare.R
import com.example.healthcare.databinding.ActivitySignUpBinding
import com.example.healthcare.databinding.ActivityWelcomeBinding
import com.example.healthcare.views.mainScreen.MainScreenActivity
import org.json.JSONObject

class SignUpActivity : AppCompatActivity() {
    companion object{
        fun startActivity(context: Context){
            val intent = Intent(context, SignUpActivity::class.java)
            context.startActivity(intent)
        }
    }

    lateinit var binding: ActivitySignUpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

       loginButtonClick()
    }

    private fun loginButtonClick() {


        with(binding) {
            binding.SignUpBtn.setOnClickListener {

                // Get input values
                val name = nameInput.text.toString().trim()
                val email = emailInput.text.toString().trim()
                val age = ageInput.text.toString().trim()
                val password = passwordInput.text.toString().trim()

                val gender = when (genderRadioGroup.checkedRadioButtonId) {
                    R.id.radioMale -> "Male"
                    R.id.radioFemale -> "Female"
                    else -> ""
                }

                // Basic validation
                if (name.isEmpty() || email.isEmpty() || age.isEmpty() || gender.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this@SignUpActivity, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Create JSON
                val jsonObject = JSONObject().apply {
                    put("elderName", name)
                    put("elderMail", email)
                    put("age", age.toInt())
                    put("gender", gender)
                    put("password", password)
                }

                // Convert to string
                val jsonString = jsonObject.toString()

                // Log / send / save
                Log.d("SIGN_UP_JSON", jsonString)
            }
        }

    }
}