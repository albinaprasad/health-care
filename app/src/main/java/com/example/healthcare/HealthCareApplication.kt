package com.example.healthcare

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.healthcare.api.RetrofitClient
import com.example.healthcare.workers.PrescriptionCheckWorker
import java.util.concurrent.TimeUnit

class HealthCareApplication : Application() {

    companion object {
        private const val WORK_NAME = "prescription_check_work"
    }

    override fun onCreate() {
        super.onCreate()
        RetrofitClient.init(this)
        schedulePrescriptionPolling()
    }

    private fun schedulePrescriptionPolling() {
        val workRequest = PeriodicWorkRequestBuilder<PrescriptionCheckWorker>(
            15, TimeUnit.MINUTES          // minimum interval for WorkManager
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,   // don't restart if already scheduled
            workRequest
        )
    }
}
