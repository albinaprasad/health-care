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
import java.nio.ByteBuffer
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
        // Camera streaming throttle (~2 FPS)
        private const val FRAME_INTERVAL_MS = 500L
    }

    // ─── Lifecycle owner for CameraX ──────────────────────────────────────────
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private lateinit var locationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    // ─── Location WebSocket ───────────────────────────────────────────────────
    private var locationWebSocket: WebSocket? = null
    private var locationReconnectDelayMs = RECONNECT_DELAY_INITIAL_MS

    // ─── Video WebSocket ──────────────────────────────────────────────────────
    private var videoWebSocket: WebSocket? = null
    private var videoReconnectDelayMs = RECONNECT_DELAY_INITIAL_MS
    private var videoWebSocketConnected = false

    private lateinit var client: OkHttpClient
    private lateinit var userPreferenceObj: UserPreferenceSaving
    private val mainHandler = Handler(Looper.getMainLooper())
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

        // FIX 2: CameraX requires RESUMED state — advance through all states
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        locationClient = LocationServices.getFusedLocationProviderClient(this)

        client = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        userPreferenceObj = UserPreferenceSaving(this)

        // FIX 4: Load elder ID before starting camera. Camera won't start until
        //        elderId is loaded (guarded in startCameraStreaming).
        CoroutineScope(Dispatchers.IO).launch {
            elderId = userPreferenceObj.getElderIdOnce()
            Log.d(TAG, "Loaded elderId=$elderId")
            // Once we have the elderId, connect the video WebSocket
            mainHandler.post { connectVideoWebSocket() }
        }

        startForeground(NOTIFICATION_ID, createNotification())
        connectLocationWebSocket()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED // FIX 2

        if (locationWebSocket == null) connectLocationWebSocket()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isDestroyed = true
        mainHandler.removeCallbacksAndMessages(null)
        stopLocationUpdates()
        stopCameraStreaming()
        locationWebSocket?.close(1000, "Service stopped")
        locationWebSocket = null
        videoWebSocket?.close(1000, "Service stopped")
        videoWebSocket = null
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

    // ─── Location WebSocket ───────────────────────────────────────────────────

    // FIX 1: Location WebSocket connects to /ws/location and sends JSON LocationMsg
    private fun connectLocationWebSocket() {
        val urlPreferences = UrlPreferences(this)
        CoroutineScope(Dispatchers.IO).launch {
            val wsUrl = urlPreferences.getWsUrlOnce()
            val token = userPreferenceObj.getToken().first()

            if (token.isNullOrBlank()) {
                Log.w(TAG, "No token — cannot connect location WebSocket")
                return@launch
            }

            // Derive base host from stored WS URL
            // Stored format: wss://host/ws?token=  → we need wss://host/ws/location?token=TOKEN
            val locationUrl = buildLocationWsUrl(wsUrl, token)
            Log.d(TAG, "Connecting location WebSocket: $locationUrl")

            val request = Request.Builder().url(locationUrl).build()
            locationWebSocket = client.newWebSocket(request, locationWsListener)
        }
    }

    private val locationWsListener = object : WebSocketListener() {

        @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION,
                                     Manifest.permission.ACCESS_COARSE_LOCATION])
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "Location WebSocket connected")
            locationReconnectDelayMs = RECONNECT_DELAY_INITIAL_MS
            // FIX 2: Advance lifecycle to RESUMED so CameraX can work
            mainHandler.post {
                if (lifecycleRegistry.currentState != Lifecycle.State.RESUMED) {
                    lifecycleRegistry.currentState = Lifecycle.State.STARTED
                    lifecycleRegistry.currentState = Lifecycle.State.RESUMED
                }
                startLocationUpdates()
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Location WS message: $text")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Location WebSocket closed: $code")
            this@LocationService.locationWebSocket = null
            stopLocationUpdates()
            scheduleLocationReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "Location WebSocket failure: ${t.message}")
            this@LocationService.locationWebSocket = null
            stopLocationUpdates()
            scheduleLocationReconnect()
        }
    }

    private fun scheduleLocationReconnect() {
        if (isDestroyed) return
        Log.d(TAG, "Location WS reconnecting in ${locationReconnectDelayMs / 1000}s…")
        mainHandler.postDelayed({
            if (!isDestroyed) connectLocationWebSocket()
        }, locationReconnectDelayMs)
        locationReconnectDelayMs = (locationReconnectDelayMs * 2).coerceAtMost(RECONNECT_DELAY_MAX_MS)
    }

    // ─── Video WebSocket ──────────────────────────────────────────────────────

    // FIX 1: Video WebSocket connects to /ws/video?elderId=X
    private fun connectVideoWebSocket() {
        if (elderId == -1) {
            Log.w(TAG, "elderId not loaded yet — video WebSocket connection deferred")
            return
        }
        val urlPreferences = UrlPreferences(this)
        CoroutineScope(Dispatchers.IO).launch {
            val wsUrl = urlPreferences.getWsUrlOnce()
            val videoUrl = buildVideoWsUrl(wsUrl, elderId)
            Log.d(TAG, "Connecting video WebSocket: $videoUrl")

            val request = Request.Builder().url(videoUrl).build()
            videoWebSocket = client.newWebSocket(request, videoWsListener)
        }
    }

    private val videoWsListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "Video WebSocket connected")
            videoReconnectDelayMs = RECONNECT_DELAY_INITIAL_MS
            videoWebSocketConnected = true
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Video WS message: $text")
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
                Log.e(TAG, "Error parsing video WS message", e)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Video WebSocket closed: $code")
            this@LocationService.videoWebSocket = null
            videoWebSocketConnected = false
            mainHandler.post { stopCameraStreaming() }
            scheduleVideoReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "Video WebSocket failure: ${t.message}")
            this@LocationService.videoWebSocket = null
            videoWebSocketConnected = false
            mainHandler.post { stopCameraStreaming() }
            scheduleVideoReconnect()
        }
    }

    private fun scheduleVideoReconnect() {
        if (isDestroyed) return
        Log.d(TAG, "Video WS reconnecting in ${videoReconnectDelayMs / 1000}s…")
        mainHandler.postDelayed({
            if (!isDestroyed) connectVideoWebSocket()
        }, videoReconnectDelayMs)
        videoReconnectDelayMs = (videoReconnectDelayMs * 2).coerceAtMost(RECONNECT_DELAY_MAX_MS)
    }

    // ─── URL builders ─────────────────────────────────────────────────────────

    /**
     * Converts `wss://host/ws?token=` → `wss://host/ws/location?token=TOKEN`
     * Handles both `/ws?token=` and `/ws/location?token=` inputs gracefully.
     */
    private fun buildLocationWsUrl(storedWsUrl: String, token: String): String {
        // Strip trailing token value (if any) and path
        val base = extractBaseHost(storedWsUrl) // e.g. wss://host
        return "$base/ws/location?token=$token"
    }

    /**
     * Converts `wss://host/ws?token=` → `wss://host/ws/video?elderId=X`
     * Backend's /ws/video authenticates by elderId, not JWT.
     */
    private fun buildVideoWsUrl(storedWsUrl: String, elderId: Int): String {
        val base = extractBaseHost(storedWsUrl)
        return "$base/ws/video?elderId=$elderId"
    }

    /**
     * Extracts the scheme+host from a WebSocket URL.
     * e.g. "wss://example.com/ws?token=" → "wss://example.com"
     */
    private fun extractBaseHost(url: String): String {
        // Find end of scheme (wss:// or ws://)
        val schemeEnd = url.indexOf("://")
        if (schemeEnd == -1) return url
        val afterScheme = url.indexOf('/', schemeEnd + 3)
        return if (afterScheme == -1) url else url.substring(0, afterScheme)
    }

    // ─── Location ─────────────────────────────────────────────────────────────

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION,
                                 Manifest.permission.ACCESS_COARSE_LOCATION])
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

    // FIX 1: Send location as JSON matching backend's LocationMsg record
    private fun sendLocation(location: Location) {
        val lat = location.latitude
        val lon = location.longitude
        val msg = JSONObject().apply {
            put("elderId", elderId)
            put("lat", lat)
            put("lon", lon)
        }.toString()
        Log.d(TAG, "Sending location: $msg")
        val sent = locationWebSocket?.send(msg)
        if (sent == false) {
            Log.w(TAG, "Location WebSocket send failed")
        }
    }

    // ─── Camera Streaming ─────────────────────────────────────────────────────

    private fun startCameraStreaming() {
        if (isStreaming) {
            Log.d(TAG, "Already streaming, ignoring START_STREAM")
            return
        }

        // FIX 4: Guard — only stream if elderId is loaded
        if (elderId == -1) {
            Log.w(TAG, "elderId not loaded, cannot start streaming")
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
                    this@LocationService,  // FIX 2: lifecycle is now RESUMED
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
        // FIX 4: Guard against RejectedExecutionException after executor shutdown
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

            val jpegBytes = imageProxyToJpeg(imageProxy, quality = 50)
            imageProxy.close()

            if (jpegBytes != null) {
                val base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

                val frameJson = JSONObject().apply {
                    put("event", "STREAM_FRAME")
                    put("elder_id", elderId)
                    put("image", base64Image)
                }

                // FIX 1: Send camera frames on videoWebSocket, not locationWebSocket
                val sent = videoWebSocket?.send(frameJson.toString())
                if (sent == true) {
                    Log.d(TAG, "Sent STREAM_FRAME (${jpegBytes.size} bytes)")
                } else {
                    Log.w(TAG, "Failed to send STREAM_FRAME — video WebSocket not ready")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
            try { imageProxy.close() } catch (_: Exception) {}
        }
    }

    // FIX 3: Proper YUV_420_888 → NV21 conversion accounting for pixel stride
    private fun imageProxyToJpeg(image: ImageProxy, quality: Int): ByteArray? {
        return try {
            val width = image.width
            val height = image.height

            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]

            val yRowStride = yPlane.rowStride
            val uvRowStride = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride

            val nv21 = ByteArray(width * height * 3 / 2)

            // Copy Y plane row by row (accounting for row padding)
            val yBuffer: ByteBuffer = yPlane.buffer
            var outOffset = 0
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(nv21, outOffset, width)
                outOffset += width
            }

            // Interleave V and U into NV21 format (V first, then U)
            val uBuffer: ByteBuffer = uPlane.buffer
            val vBuffer: ByteBuffer = vPlane.buffer
            val halfHeight = height / 2
            val halfWidth = width / 2
            for (row in 0 until halfHeight) {
                for (col in 0 until halfWidth) {
                    val uvIndex = row * uvRowStride + col * uvPixelStride
                    nv21[outOffset++] = vBuffer.get(uvIndex) // V
                    nv21[outOffset++] = uBuffer.get(uvIndex) // U
                }
            }

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), quality, out)
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
