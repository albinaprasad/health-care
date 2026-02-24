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

    @POST("api/elder/login")
    suspend fun loginElder(
        @Body request:  Map<String, String>
    ): Response<Map<String, String>>
}