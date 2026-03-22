package com.example.healthcare.api

import com.example.healthcare.dataclasses.ElderSignupRequest
import com.example.healthcare.dataclasses.FallAlertResponse
import com.example.healthcare.dataclasses.FallAlertResult
import com.example.healthcare.dataclasses.FcmTokenRequest
import com.example.healthcare.dataclasses.PrescriptionResponse
import com.example.healthcare.dataclasses.SetHomeRequest
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

    @POST("api/elder/fcm-token")
    suspend fun registerFcmToken(
        @Body request: FcmTokenRequest
    ): Response<Any>

    @POST("api/elder/respond")
    suspend fun sendFallResponse(
        @Body request: FallAlertResponse
    ): Response<FallAlertResult>

    @POST("api/elder/set-home")
    suspend fun setHome(
        @Body request: SetHomeRequest
    ): Response<Any>
}
