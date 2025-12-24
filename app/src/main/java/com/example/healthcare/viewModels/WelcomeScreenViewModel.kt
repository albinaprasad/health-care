package com.example.healthcare.viewModels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class WelcomeScreenViewModel: ViewModel() {

    private var _animationState = MutableStateFlow(false)
    var animationState : StateFlow<Boolean> = _animationState

    fun onContinueButtonClicked(){
        _animationState.value = true
    }
}