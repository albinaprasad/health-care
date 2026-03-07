package com.example.healthcare.dataclasses

data class FallAlertResponse(
    val elderId: Int,
    val response: String   // "yes" or "no"
)

data class FallAlertResult(
    val success: Boolean,
    val message: String?
)

data class FcmTokenRequest(
    val elderId: Int,
    val fcmToken: String
)
