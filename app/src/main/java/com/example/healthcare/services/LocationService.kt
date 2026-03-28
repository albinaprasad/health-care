package com.example.healthcare.services

import android.Manifest
import android.app.*
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
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
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LocationService : Service(), LifecycleOwner {

    companion object {
        private const val TAG = "LocationService"
        private const val CHANNEL_ID = "location_channel"
        private const val NOTIFICATION_ID = 1
        private const val LOCATION_INTERVAL_MS = 5000L
        private const val RECONNECT_DELAY_INITIAL_MS = 3_000L
        private const val RECONNECT_DELAY_MAX_MS = 60_000L
        private const val FRAME_INTERVAL_MS = 500L
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private lateinit var locationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var webSocket: WebSocket? = null
    private var cameraWebSocket: WebSocket? = null
    private lateinit var client: OkHttpClient
    private lateinit var userPreferenceObj: UserPreferenceSaving
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reconnectDelayMs = RECONNECT_DELAY_INITIAL_MS
    private var isDestroyed = false

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var isStreaming = false
    private var lastFrameTimeMs = 0L
    private var elderId: Int = -1

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        locationClient = LocationServices.getFusedLocationProviderClient(this)

        client = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        userPreferenceObj = UserPreferenceSaving(this)

        // Load elderId first — both sockets need it before connecting
        CoroutineScope(Dispatchers.IO).launch {
            elderId = userPreferenceObj.getElderIdOnce()
            Log.d(TAG, "Elder ID loaded: $elderId")
            // Connect both sockets only after elderId is known
            connectWebSocket()
            connectCameraWebSocket()
        }

        startForeground(NOTIFICATION_ID, createNotification())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        // FIX: advance to RESUMED so CameraX can bind to this LifecycleOwner
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        // Only reconnect if elderId is already loaded (otherwise onCreate coroutine handles it)
        if (webSocket == null && elderId != -1) connectWebSocket()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isDestroyed = true
        mainHandler.removeCallbacksAndMessages(null)
        stopLocationUpdates()
        stopCameraStreaming()
        webSocket?.close(1000, "Service stopped")
        cameraWebSocket?.close(1000, "Service stopped")
        webSocket = null
        cameraWebSocket = null
        client.dispatcher.executorService.shutdown()
        cameraExecutor.shutdown()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved — stopping service")
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    // ─── Location WebSocket ───────────────────────────────────────────────────

    private fun connectWebSocket() {
        val urlPreferences = UrlPreferences(this)
        CoroutineScope(Dispatchers.IO).launch {
            val wsUrl = urlPreferences.getWsUrlOnce()
            val token = userPreferenceObj.getToken().first()

            if (token.isNullOrBlank()) {
                Log.w(TAG, "No token — cannot connect location WebSocket")
                return@launch
            }

            val fullUrl = wsUrl + token
            Log.d(TAG, "Connecting location WebSocket: $fullUrl")
            webSocket = client.newWebSocket(
                Request.Builder().url(fullUrl).build(),
                locationWebSocketListener
            )
        }
    }

    private val locationWebSocketListener = object : WebSocketListener() {

        @RequiresPermission(allOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ])
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "Location WebSocket connected")
            reconnectDelayMs = RECONNECT_DELAY_INITIAL_MS
            mainHandler.post { startLocationUpdates() }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Location WS message (unexpected): $text")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Location WebSocket closed: $code")
            this@LocationService.webSocket = null
            stopLocationUpdates()
            mainHandler.post { stopCameraStreaming() }
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "Location WebSocket failure: ${t.message}")
            this@LocationService.webSocket = null
            stopLocationUpdates()
            mainHandler.post { stopCameraStreaming() }
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (isDestroyed) return
        Log.d(TAG, "Reconnecting in ${reconnectDelayMs / 1000}s…")
        mainHandler.postDelayed({
            if (!isDestroyed) connectWebSocket()
        }, reconnectDelayMs)
        reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(RECONNECT_DELAY_MAX_MS)
    }

    // ─── Camera WebSocket ─────────────────────────────────────────────────────

    private fun connectCameraWebSocket() {
        val urlPreferences = UrlPreferences(this)
        CoroutineScope(Dispatchers.IO).launch {
            val apiUrl = urlPreferences.getApiUrlOnce()
            val wsBase = apiUrl
                .trimEnd('/')
                .replace(Regex("^https"), "wss")
                .replace(Regex("^http"), "ws")
            val fullUrl = "$wsBase/ws/video?elderId=$elderId"
            Log.d(TAG, "Connecting camera WebSocket: $fullUrl")
            cameraWebSocket = client.newWebSocket(
                Request.Builder().url(fullUrl).build(),
                cameraWebSocketListener
            )
        }
    }

    private val cameraWebSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "Camera WebSocket connected — waiting for START_STREAM")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Camera WS message: $text")
            try {
                val json = JSONObject(text)
                when (json.optString("command")) {
                    "START_STREAM" -> mainHandler.post { startCameraStreaming() }
                    "STOP_STREAM"  -> mainHandler.post { stopCameraStreaming() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing camera WS message", e)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "Camera WebSocket failure: ${t.message}")
            cameraWebSocket = null
            isStreaming = false
            mainHandler.postDelayed({
                if (!isDestroyed) connectCameraWebSocket()
            }, 5000)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Camera WebSocket closed: $code")
            cameraWebSocket = null
        }
    }

    // ─── Location ─────────────────────────────────────────────────────────────

    @RequiresPermission(allOf = [
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ])
    private fun startLocationUpdates() {
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
        val msg = "${location.latitude},${location.longitude}"
        Log.d(TAG, "Sending location: $msg")
        when (webSocket?.send(msg)) {
            true  -> Log.d(TAG, "Location send OK")
            false -> Log.w(TAG, "Location send FAILED — buffer full or socket closed")
            null  -> Log.w(TAG, "Location send SKIPPED — socket is null")
        }
    }

    // ─── Camera Streaming ─────────────────────────────────────────────────────

    private fun startCameraStreaming() {
        if (isStreaming) {
            Log.d(TAG, "Already streaming")
            return
        }
        if (elderId == -1) {
            Log.e(TAG, "elderId not loaded")
            return
        }
        if (cameraWebSocket == null) {
            Log.e(TAG, "Camera socket not connected — cannot stream")
            return
        }
        Log.d(TAG, "START_STREAM received — binding camera")
        isStreaming = true
        bindCamera()
    }

    private fun bindCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cameraProvider = future.get()

                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(cameraExecutor, ::processFrame) }

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build(),
                    imageAnalysis!!
                )
                Log.d(TAG, "Camera bound — streaming")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind camera", e)
                isStreaming = false
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCameraStreaming() {
        if (!isStreaming) return
        Log.d(TAG, "Stopping camera streaming")
        isStreaming = false
        // Don't close cameraWebSocket — keep it alive for next START_STREAM
        try { cameraProvider?.unbindAll() } catch (e: Exception) {
            Log.e(TAG, "Error unbinding camera", e)
        }
        cameraProvider = null
        imageAnalysis = null
    }

    private fun processFrame(imageProxy: ImageProxy) {
        try {
            if (!isStreaming) { imageProxy.close(); return }

            val now = System.currentTimeMillis()
            if (now - lastFrameTimeMs < FRAME_INTERVAL_MS) { imageProxy.close(); return }
            lastFrameTimeMs = now

            val jpegBytes = imageProxyToJpeg(imageProxy, quality = 50)
            imageProxy.close()

            if (jpegBytes != null) {
                val json = JSONObject().apply {
                    put("event", "STREAM_FRAME")
                    put("elder_id", elderId)
                    put("image", Base64.encodeToString(jpegBytes, Base64.NO_WRAP))
                }
                val sent = cameraWebSocket?.send(json.toString())
                if (sent == true) {
                    Log.d(TAG, "STREAM_FRAME sent (${jpegBytes.size} bytes)")
                } else {
                    Log.w(TAG, "STREAM_FRAME send failed — socket not ready")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "processFrame error", e)
            try { imageProxy.close() } catch (_: Exception) {}
        }
    }

    // FIX: use Bitmap as intermediate — avoids device-specific YUV pixel-stride
    // differences that cause the manual NV21 approach to produce black images
    private fun imageProxyToJpeg(image: ImageProxy, quality: Int): ByteArray? {
        return try {
            val bitmap = image.toBitmap()
            val out = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
            bitmap.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Bitmap→JPEG failed", e)
            null
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
