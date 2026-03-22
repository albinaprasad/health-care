package com.example.healthcare.services

import android.Manifest
import android.app.*
import android.content.Intent
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
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
        // Reconnect back-off: starts at 3 s, caps at 60 s
        private const val RECONNECT_DELAY_INITIAL_MS = 3_000L
        private const val RECONNECT_DELAY_MAX_MS = 60_000L
        // Camera streaming throttle (~2 FPS)
        private const val FRAME_INTERVAL_MS = 500L
    }

    // ─── Lifecycle owner for CameraX ──────────────────────────────────────────
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private lateinit var locationClient: FusedLocationProviderClient
    // Save the callback so we can properly remove it later
    private var locationCallback: LocationCallback? = null
    private var webSocket: WebSocket? = null
    private lateinit var client: OkHttpClient
    private lateinit var userPreferenceObj: UserPreferenceSaving
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reconnectDelayMs = RECONNECT_DELAY_INITIAL_MS
    private var isDestroyed = false

    // ─── Camera streaming state ───────────────────────────────────────────────
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

        // OkHttpClient with keep-alive pings — detects silently dead connections
        client = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)  // sends a WS ping every 20 s
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        userPreferenceObj = UserPreferenceSaving(this)

        // Load elder ID for camera streaming
        CoroutineScope(Dispatchers.IO).launch {
            elderId = userPreferenceObj.getElderIdOnce()
        }

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

        lifecycleRegistry.currentState = Lifecycle.State.STARTED

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
        stopCameraStreaming()
        webSocket?.close(1000, "Service stopped")
        webSocket = null
        client.dispatcher.executorService.shutdown()
        cameraExecutor.shutdown()

        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED

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
            try {
                val json = JSONObject(text)
                when (json.optString("command")) {
                    "START_STREAM" -> {
                        Log.d(TAG, "START_STREAM received")
                        mainHandler.post { startCameraStreaming() }
                    }
                    "STOP_STREAM" -> {
                        Log.d(TAG, "STOP_STREAM received")
                        mainHandler.post { stopCameraStreaming() }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing WebSocket message", e)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code / $reason")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code / $reason")
            this@LocationService.webSocket = null
            stopLocationUpdates()
            mainHandler.post { stopCameraStreaming() }
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure: ${t.message}")
            this@LocationService.webSocket = null
            stopLocationUpdates()
            mainHandler.post { stopCameraStreaming() }
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

    // ─── Camera Streaming ─────────────────────────────────────────────────────

    private fun startCameraStreaming() {
        if (isStreaming) {
            Log.d(TAG, "Already streaming, ignoring START_STREAM")
            return
        }

        Log.d(TAG, "Starting camera streaming")
        isStreaming = true

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis!!.setAnalyzer(cameraExecutor) { imageProxy ->
                    processFrame(imageProxy)
                }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this@LocationService,
                    cameraSelector,
                    imageAnalysis!!
                )

                Log.d(TAG, "Camera bound and streaming started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera streaming", e)
                isStreaming = false
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCameraStreaming() {
        if (!isStreaming) return
        Log.d(TAG, "Stopping camera streaming")
        isStreaming = false
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding camera", e)
        }
        cameraProvider = null
        imageAnalysis = null
    }

    private fun processFrame(imageProxy: ImageProxy) {
        try {
            if (!isStreaming) {
                imageProxy.close()
                return
            }

            // Throttle: skip frame if too soon
            val now = System.currentTimeMillis()
            if (now - lastFrameTimeMs < FRAME_INTERVAL_MS) {
                imageProxy.close()
                return
            }
            lastFrameTimeMs = now

            // Convert ImageProxy (YUV) → JPEG bytes
            val jpegBytes = imageProxyToJpeg(imageProxy, quality = 50)
            imageProxy.close()

            if (jpegBytes != null) {
                val base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

                val frameJson = JSONObject().apply {
                    put("event", "STREAM_FRAME")
                    put("elder_id", elderId)
                    put("image", base64Image)
                }

                val sent = webSocket?.send(frameJson.toString())
                if (sent == true) {
                    Log.d(TAG, "Sending STREAM_FRAME (${jpegBytes.size} bytes)")
                } else {
                    Log.w(TAG, "Failed to send STREAM_FRAME")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
            imageProxy.close()
        }
    }

    private fun imageProxyToJpeg(image: ImageProxy, quality: Int): ByteArray? {
        return try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(
                nv21,
                ImageFormat.NV21,
                image.width,
                image.height,
                null
            )

            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                Rect(0, 0, image.width, image.height),
                quality,
                out
            )
            out.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "YUV→JPEG conversion failed", e)
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
