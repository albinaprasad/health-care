package com.example.healthcare.api

import android.content.Context
import com.example.healthcare.TokenManager.UrlPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private const val DEFAULT_BASE_URL =
        "https://disproportionable-unantagonistic-alvin.ngrok-free.dev"

    private var BASE_URL: String = DEFAULT_BASE_URL

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    private val gson: Gson = GsonBuilder()
        .setLenient()
        .create()

    private var retrofit: Retrofit = buildRetrofit()

    private fun buildRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun init(context: Context) {
        val urlPreferences = UrlPreferences(context.applicationContext)
        val savedUrl = runBlocking { urlPreferences.getApiUrlOnce() }
        if (savedUrl.isNotBlank() && savedUrl != BASE_URL) {
            BASE_URL = savedUrl
            retrofit = buildRetrofit()
        }
    }

    val signUPApi: ElderApiService
        get() = retrofit.create(ElderApiService::class.java)

    val loginApi: ElderApiService
        get() = retrofit.create(ElderApiService::class.java)
}
