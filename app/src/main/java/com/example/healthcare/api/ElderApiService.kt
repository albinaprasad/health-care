package com.example.healthcare.api

import com.example.healthcare.dataclasses.ElderSignupRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ElderApiService {

    @POST("api/elder/signup")
    suspend fun signupElder(
        @Body request: ElderSignupRequest
    ): Response<Any>
}