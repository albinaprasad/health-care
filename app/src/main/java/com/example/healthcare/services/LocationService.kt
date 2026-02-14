package com.example.healthcare.services

import android.Manifest
import android.app.*
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*

class LocationService : Service() {

    private lateinit var locationClient: FusedLocationProviderClient
    val TIME_INTERVAL = 5000L

    @RequiresApi(Build.VERSION_CODES.O)
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onCreate() {
        super.onCreate()
        Log.d("ABC", "LocationService created")
        locationClient = LocationServices.getFusedLocationProviderClient(this)

        startForeground(1, createNotification())
        startLocationUpdates()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startLocationUpdates() {

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            TIME_INTERVAL // every 5 seconds
        ).build()

        locationClient.requestLocationUpdates(
            request,
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    for (location in result.locations) {
                        showLocation(location)
                    }
                }
            },
            mainLooper
        )
    }

    private fun showLocation(location: Location) {
        val lat = location.latitude
        val lon = location.longitude

        //log coordinates
        Log.d("ABC","Latitude: $lat  Longitude: $lon")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotification(): Notification {

        val channelId = "location_channel"

        val channel = NotificationChannel(
            channelId,
            "Location Tracking",
            NotificationManager.IMPORTANCE_LOW
        )

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Location Running")
            .setContentText("Tracking location in background")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
    }
    private fun stopLocationUpdates() {
        locationClient.removeLocationUpdates(object : LocationCallback() {})
    }

    override fun onBind(intent: Intent?): IBinder? = null


    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d("ABC", "App removed from recent apps")
        stopLocationUpdates()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }
}
