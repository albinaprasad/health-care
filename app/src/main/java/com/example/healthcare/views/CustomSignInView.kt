package com.example.healthcare.views

import android.R
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.healthcare.databinding.CustomSignInLayoutBinding

class CustomSignInView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
): ConstraintLayout(context,attrs,defStyleAttr) {

    private val binding : CustomSignInLayoutBinding
    private var onLoginClick: ((String, String) -> Unit)? = null

    private var onSignUPClick :(()-> Unit)? = null


    init
    {
        binding = CustomSignInLayoutBinding.inflate(LayoutInflater.from(context), this, true)

        binding.loginBtn.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if(email.isEmpty()){
                binding.emailInput.error = "Email required"
                return@setOnClickListener
            }

            if (password.isEmpty()){
                binding.passwordInput.error = "password required"
                return@setOnClickListener
            }
            onLoginClick?.invoke(email,password)
        }
        binding.signUpText.setOnClickListener {
            onSignUPClick?.invoke()
        }
    }

    fun setLoginButtonClick(listener: (String, String) -> Unit){
        onLoginClick = listener

    }

    fun signUpClick(listener: () -> Unit){
        onSignUPClick = listener
    }


}