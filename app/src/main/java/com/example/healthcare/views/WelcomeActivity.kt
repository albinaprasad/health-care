package com.example.healthcare.views

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.healthcare.databinding.ActivityWelcomeBinding
import com.example.healthcare.viewModels.WelcomeScreenViewModel
import com.example.healthcare.views.mainScreen.MainScreenActivity
import kotlinx.coroutines.launch

class WelcomeActivity : AppCompatActivity() {
    lateinit var binding: ActivityWelcomeBinding
    private val viewmodel: WelcomeScreenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setUplisteners()
        observeViewModels()


    }

    private fun setUplisteners() {
        with(binding) {
            btnContinue.setOnClickListener {
                viewmodel.onContinueButtonClicked()
            }
            SignInView.setLoginButtonClick {
                MainScreenActivity.startActivity(this@WelcomeActivity)

            }

        }
    }


    private fun observeViewModels(){
       lifecycleScope.launch {
           repeatOnLifecycle(Lifecycle.State.STARTED){

               viewmodel.animationState.collect { value ->
                   if(value == true){
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


            imageView.animate()
                .translationY(-500f)
                .setDuration(900)
                .start()

            // Animate SignInView fading in
            SignInView.animate()
                .alpha(1f)
                .setDuration(900)
                .start()

            // Animate the welcome texts and button fading out together
            welcomeText.animate()
                .alpha(0f)
                .setDuration(900)
                .start()

            welcomeDescription.animate()
                .alpha(0f)
                .setDuration(900)
                .start()

            btnContinue.animate()
                .alpha(0f)
                .setDuration(900)
                .withEndAction {
                    // Only hide them after their fade-out completes
                    welcomeText.visibility = View.GONE
                    welcomeDescription.visibility = View.GONE
                    btnContinue.visibility = View.GONE
                }
                .start()
        }


    }
}