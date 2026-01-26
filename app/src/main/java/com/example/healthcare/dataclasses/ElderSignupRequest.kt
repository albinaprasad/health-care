package com.example.healthcare.dataclasses

data class ElderSignupRequest(
    val elderName: String,
    val elderMail: String,
    val age: Int,
    val gender: String,
    val password: String
)
