package com.slideindex.app.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import androidx.core.graphics.createBitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.slideindex.app.R
import com.slideindex.app.overlay.FloatBallOcrRegions
import com.slideindex.app.perf.PickPerf
import com.slideindex.app.util.MediaProjectionStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground service that keeps a MediaProjection session for fast regional screen capture.
 */
class ScreenCaptureService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = readResultData(intent)
                if (resultCode != Activity.RESULT_OK || data == null) {
                    SessionState.markNotReady()
                    stopSelf()
                    return START_NOT_STICKY
                }
                try {
                    startForeground(NOTIFICATION_ID, buildNotification())
                } catch (error: SecurityException) {
                    Log.e(TAG, "capture FGS denied; project_media not granted", error)
                    MediaProjectionStore.clear(this)
                    SessionState.markNotReady()
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (!startCaptureSession(resultCode, data)) {
                    runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopCaptureSession()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopCaptureSession()
        SessionState.clearInstance(this)
        super.onDestroy()
    }

    private fun startCaptureSession(resultCode: Int, data: Intent): Boolean {
        if (ScreenRecordService.isRecording) {
            Log.w(TAG, "screen record active; skip capture session")
            SessionState.markNotReady()
            return false
        }
        stopCaptureSessionInternal()
        val projectionManager = getSystemService(MediaProjectionManager::class.java) ?: run {
            SessionState.markNotReady()
            return false
        }
        val projection = try {
            projectionManager.getMediaProjection(resultCode, data)
        } catch (error: SecurityException) {
            Log.e(TAG, "getMediaProjection denied", error)
            MediaProjectionStore.clear(this)
            SessionState.markNotReady()
            return false
        } ?: run {
            SessionState.markNotReady()
            return false
        }
        mediaProjection = projection
        projection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    mainHandler.post {
                        stopCaptureSessionInternal()
                        SessionState.markNotReady()
                        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                        stopSelf()
                    }
                }
            },
            mainHandler,
        )

        val (width, height) = FloatBallOcrRegions.accessibilityScreenSizePx(this)
        val captureWidth = width.coerceAtLeast(2)
        val captureHeight = height.coerceAtLeast(2)
        val density = resources.displayMetrics.densityDpi
        val reader = ImageReader.newInstance(
            captureWidth,
            captureHeight,
            PixelFormat.RGBA_8888,
            IMAGE_READER_MAX_IMAGES,
        )
        imageReader = reader
        virtualDisplay = projection.createVirtualDisplay(
            "SlideIndexScreenCapture",
            captureWidth,
            captureHeight,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            null,
        )
        SessionState.bindInstance(this)
        SessionState.markReady()
        return true
    }

    private suspend fun captureFrameInternal(): Bitmap? = SessionState.captureMutex.withLock {
        val reader = imageReader ?: return null
        drainReader(reader)
        repeat(FRAME_RETRY_COUNT) {
            val frame = reader.acquireLatestImage()
            if (frame != null) {
                return try {
                    imageToBitmap(frame)
                } finally {
                    frame.close()
                }
            }
            delay(FRAME_RETRY_DELAY_MS)
        }
        null
    }

    private fun drainReader(reader: ImageReader) {
        while (true) {
            val stale = reader.acquireLatestImage() ?: break
            stale.close()
        }
    }

    private fun stopCaptureSession() {
        stopCaptureSessionInternal()
        SessionState.markNotReady()
    }

    private fun stopCaptureSessionInternal() {
        imageReader?.let { reader ->
            drainReader(reader)
            reader.close()
        }
        imageReader = null
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.stop()
        mediaProjection = null
        SessionState.clearInstance(this)
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(getString(R.string.screen_capture_notification_active))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.screen_capture_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun readResultData(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
    }

    private object SessionState {
        val captureMutex = Mutex()
        private val isReady = AtomicBoolean(false)
        private var readySignal = CompletableDeferred<Unit>()
        private var instance: ScreenCaptureService? = null

        val sessionActive: Boolean
            get() = isReady.get() && instance != null

        fun bindInstance(service: ScreenCaptureService) {
            instance = service
        }

        fun clearInstance(service: ScreenCaptureService) {
            if (instance === service) {
                instance = null
            }
        }

        fun resetForStart() {
            isReady.set(false)
            readySignal = CompletableDeferred()
        }

        fun markReady() {
            isReady.set(true)
            readySignal.complete(Unit)
        }

        fun markNotReady() {
            isReady.set(false)
            if (!readySignal.isCompleted) {
                readySignal.complete(Unit)
            }
        }

        suspend fun awaitReady(): Boolean {
            if (sessionActive) return true
            withTimeoutOrNull(READY_TIMEOUT_MS) {
                if (!readySignal.isCompleted) {
                    readySignal.await()
                }
            }
            return sessionActive
        }

        suspend fun captureDisplayBitmap(): Bitmap? {
            val start = SystemClock.elapsedRealtime()
            if (!sessionActive || ScreenRecordService.isRecording) {
                PickPerf.mark(
                    "screenCapture_frame_skip",
                    "active=$sessionActive recording=${ScreenRecordService.isRecording}",
                )
                return null
            }
            val readyStart = SystemClock.elapsedRealtime()
            if (!awaitReady()) {
                PickPerf.markStepDuration("screenCapture_awaitReady_timeout", readyStart)
                return null
            }
            PickPerf.markStepDuration("screenCapture_awaitReady_done", readyStart)
            return withContext(Dispatchers.IO) {
                val frameStart = SystemClock.elapsedRealtime()
                val bitmap = instance?.captureFrameInternal()
                PickPerf.markStepDuration(
                    "screenCapture_frame_done",
                    frameStart,
                    "bitmap=${bitmap != null}",
                )
                PickPerf.markStepDuration("screenCapture_service_end", start, "bitmap=${bitmap != null}")
                bitmap
            }
        }
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 4208
        private const val ACTION_START = "com.slideindex.app.action.SCREEN_CAPTURE_START"
        private const val ACTION_STOP = "com.slideindex.app.action.SCREEN_CAPTURE_STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val IMAGE_READER_MAX_IMAGES = 3
        private const val FRAME_RETRY_COUNT = 8
        private const val FRAME_RETRY_DELAY_MS = 40L
        private const val READY_TIMEOUT_MS = 5_000L

        val sessionActive: Boolean
            get() = SessionState.sessionActive

        fun start(context: Context, resultCode: Int, data: Intent) {
            SessionState.resetForStart()
            val app = context.applicationContext
            val intent = Intent(app, ScreenCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            app.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            app.startService(intent)
        }

        suspend fun captureDisplayBitmap(context: Context): Bitmap? {
            if (!sessionActive) {
                PickPerf.mark("screenCapture_service_skip", "sessionInactive")
                return null
            }
            return SessionState.captureDisplayBitmap()
        }
    }
}

private fun imageToBitmap(image: Image): Bitmap {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val rowPadding = rowStride - pixelStride * image.width
    val bitmap = createBitmap(
        image.width + rowPadding / pixelStride,
        image.height,
        Bitmap.Config.ARGB_8888,
    )
    bitmap.copyPixelsFromBuffer(buffer)
    return if (bitmap.width == image.width) {
        bitmap
    } else {
        Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height).also {
            bitmap.recycle()
        }
    }
}
