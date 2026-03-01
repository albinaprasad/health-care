package com.example.healthcare.api

import com.example.healthcare.dataclasses.ElderSignupRequest
import com.example.healthcare.dataclasses.PrescriptionResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface ElderApiService {

    @POST("api/elder/signup")
    suspend fun signupElder(
        @Body request: ElderSignupRequest
    ): Response<Any>

    @POST("api/elder/login")
    suspend fun loginElder(
        @Body request: Map<String, String>
    ): Response<Map<String, Any>>

    @GET("api/elder/prescription/{elderId}")
    suspend fun getPrescriptions(
        @Header("Authorization") token: String,
        @Path("elderId") elderId: Int
    ): Response<List<PrescriptionResponse>>
}
