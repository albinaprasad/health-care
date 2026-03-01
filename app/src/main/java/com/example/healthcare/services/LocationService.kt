package com.example.healthcare.services

import android.Manifest
import android.app.*
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.example.healthcare.TokenManager.UrlPreferences
import com.example.healthcare.TokenManager.UserPreferenceSaving
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class LocationService : Service() {

    companion object {
        private const val TAG = "LocationService"
        private const val CHANNEL_ID = "location_channel"
        private const val NOTIFICATION_ID = 1
        private const val LOCATION_INTERVAL_MS = 5000L
        // Reconnect back-off: starts at 3 s, caps at 60 s
        private const val RECONNECT_DELAY_INITIAL_MS = 3_000L
        private const val RECONNECT_DELAY_MAX_MS = 60_000L
    }

    private lateinit var locationClient: FusedLocationProviderClient
    // Save the callback so we can properly remove it later
    private var locationCallback: LocationCallback? = null
    private var webSocket: WebSocket? = null
    private lateinit var client: OkHttpClient
    private lateinit var userPreferenceObj: UserPreferenceSaving
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reconnectDelayMs = RECONNECT_DELAY_INITIAL_MS
    private var isDestroyed = false

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        locationClient = LocationServices.getFusedLocationProviderClient(this)

        // OkHttpClient with keep-alive pings — detects silently dead connections
        client = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)  // sends a WS ping every 20 s
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        userPreferenceObj = UserPreferenceSaving(this)

        startForeground(NOTIFICATION_ID, createNotification())
        connectWebSocket()
    }

    /**
     * START_STICKY: Android will restart this service after killing it,
     * and call onStartCommand again — so we reconnect the WebSocket.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand — reconnecting if needed")
        if (webSocket == null) {
            connectWebSocket()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isDestroyed = true
        mainHandler.removeCallbacksAndMessages(null)
        stopLocationUpdates()
        webSocket?.close(1000, "Service stopped")
        webSocket = null
        client.dispatcher.executorService.shutdown()
        Log.d(TAG, "onDestroy")
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved — stopping service")
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    // ─── WebSocket ────────────────────────────────────────────────────────────

    private fun connectWebSocket() {
        val urlPreferences = UrlPreferences(this)
        CoroutineScope(Dispatchers.IO).launch {
            val wsUrl = urlPreferences.getWsUrlOnce()
            val token = userPreferenceObj.getToken().first()

            if (token.isNullOrBlank()) {
                Log.w(TAG, "No token found — cannot connect WebSocket")
                return@launch
            }

            val fullUrl = wsUrl + token
            Log.d(TAG, "Connecting WebSocket: $fullUrl")

            val request = Request.Builder().url(fullUrl).build()
            webSocket = client.newWebSocket(request, webSocketListener)
        }
    }

    private val webSocketListener = object : WebSocketListener() {

        @RequiresPermission(
            allOf = [Manifest.permission.ACCESS_FINE_LOCATION,
                     Manifest.permission.ACCESS_COARSE_LOCATION]
        )
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected")
            reconnectDelayMs = RECONNECT_DELAY_INITIAL_MS // reset back-off
            startLocationUpdates()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "WebSocket message: $text")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code / $reason")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code / $reason")
            this@LocationService.webSocket = null
            stopLocationUpdates()
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure: ${t.message}")
            this@LocationService.webSocket = null
            stopLocationUpdates()
            scheduleReconnect()
        }
    }

    /**
     * Schedules a reconnect attempt with exponential back-off.
     * Caps at RECONNECT_DELAY_MAX_MS.
     */
    private fun scheduleReconnect() {
        if (isDestroyed) return
        Log.d(TAG, "Reconnecting in ${reconnectDelayMs / 1000}s…")
        mainHandler.postDelayed({
            if (!isDestroyed) connectWebSocket()
        }, reconnectDelayMs)
        // Exponential back-off: double each time, capped at max
        reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(RECONNECT_DELAY_MAX_MS)
    }

    // ─── Location ─────────────────────────────────────────────────────────────

    @RequiresPermission(
        allOf = [Manifest.permission.ACCESS_FINE_LOCATION,
                 Manifest.permission.ACCESS_COARSE_LOCATION]
    )
    private fun startLocationUpdates() {
        // Guard: don't register a second callback if one is already active
        if (locationCallback != null) return

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_INTERVAL_MS
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { sendLocation(it) }
            }
        }

        locationClient.requestLocationUpdates(request, locationCallback!!, mainLooper)
        Log.d(TAG, "Location updates started")
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            locationClient.removeLocationUpdates(it)
            Log.d(TAG, "Location updates stopped")
        }
        locationCallback = null
    }

    private fun sendLocation(location: Location) {
        val lat = location.latitude
        val lon = location.longitude
        val msg = "$lat,$lon"
        Log.d(TAG, "Sending location: $msg")
        val sent = webSocket?.send(msg)
        if (sent == false) {
            Log.w(TAG, "WebSocket send failed — socket may be closed")
        }
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location Tracking",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Running")
            .setContentText("Tracking location in background")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }
}

