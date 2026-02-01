package com.example.healthcare.views

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
    private var onLoginClick :(()-> Unit)? = null
    private var onSignUPClick :(()-> Unit)? = null


    init
    {
        binding = CustomSignInLayoutBinding.inflate(LayoutInflater.from(context), this, true)

        binding.loginBtn.setOnClickListener {
            onLoginClick?.invoke()
        }
        binding.signUpText.setOnClickListener {
            onSignUPClick?.invoke()
        }
    }

    fun setLoginButtonClick(listener: ()-> Unit){
        onLoginClick = listener

    }

    fun signUpClick(listener: () -> Unit){
        onSignUPClick = listener
    }


}