package com.slideindex.app.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.slideindex.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service that records the screen using the official MediaProjection + MediaRecorder APIs.
 */
class ScreenRecordService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var outputUri: Uri? = null
    private var outputPfd: android.os.ParcelFileDescriptor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = readResultData(intent)
                if (resultCode == Activity.RESULT_OK && data != null) {
                    startForeground(NOTIFICATION_ID, buildNotification(recording = true))
                    startRecording(resultCode, data)
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRecordingInternal(notify = false)
        super.onDestroy()
    }

    private fun startRecording(resultCode: Int, data: Intent) {
        if (isRecording) return
        val projectionManager = getSystemService(MediaProjectionManager::class.java) ?: return
        val projection = projectionManager.getMediaProjection(resultCode, data) ?: return
        mediaProjection = projection
        projection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    mainHandler.post { stopRecordingInternal(notify = true) }
                }
            },
            mainHandler,
        )

        val wm = getSystemService(WindowManager::class.java) ?: return
        val bounds = wm.currentWindowMetrics.bounds
        val width = bounds.width().coerceAtLeast(2).let { if (it % 2 != 0) it - 1 else it }
        val height = bounds.height().coerceAtLeast(2).let { if (it % 2 != 0) it - 1 else it }
        val density = resources.displayMetrics.densityDpi

        val recorder = createMediaRecorder()
        val (uri, pfd) = createOutputTarget() ?: run {
            recorder.release()
            stopSelf()
            return
        }
        outputUri = uri
        outputPfd = pfd
        recorder.setOutputFile(pfd.fileDescriptor)
        runCatching { recorder.prepare() }.onFailure { error ->
            Log.e(TAG, "MediaRecorder prepare failed", error)
            recorder.release()
            closeOutput()
            stopSelf()
            return
        }
        mediaRecorder = recorder

        virtualDisplay = projection.createVirtualDisplay(
            "SlideIndexScreenRecord",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            recorder.surface,
            null,
            null,
        )
        runCatching { recorder.start() }.onFailure { error ->
            Log.e(TAG, "MediaRecorder start failed", error)
            stopRecordingInternal(notify = true)
            return
        }
        isRecording = true
    }

    private fun createMediaRecorder(): MediaRecorder {
        val wm = getSystemService(WindowManager::class.java) ?: throw IllegalStateException("no WindowManager")
        val bounds = wm.currentWindowMetrics.bounds
        val width = bounds.width().coerceAtLeast(2).let { if (it % 2 != 0) it - 1 else it }
        val height = bounds.height().coerceAtLeast(2).let { if (it % 2 != 0) it - 1 else it }
        return MediaRecorder().apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(width, height)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(6_000_000)
        }
    }

    @SuppressLint("Recycle") // Closed later via outputPfd in finalizeOutput()/releaseResources()
    private fun createOutputTarget(): Pair<Uri, android.os.ParcelFileDescriptor>? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "SlideIndex_$timestamp.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/SlideIndex")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        val pfd = contentResolver.openFileDescriptor(uri, "w") ?: return null
        return uri to pfd
    }

    private fun stopRecording() {
        stopRecordingInternal(notify = true)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopRecordingInternal(notify: Boolean) {
        isRecording = false
        runCatching { mediaRecorder?.stop() }
        runCatching { mediaRecorder?.reset() }
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.stop()
        mediaProjection = null
        finalizeOutput()
        if (notify) {
            runCatching {
                getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, buildNotification(recording = false))
            }
        }
    }

    private fun finalizeOutput() {
        outputUri?.let { uri ->
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            runCatching { contentResolver.update(uri, values, null, null) }
        }
        runCatching { outputPfd?.close() }
        outputPfd = null
        outputUri = null
    }

    private fun closeOutput() {
        runCatching { outputPfd?.close() }
        outputPfd = null
        outputUri = null
    }

    private fun buildNotification(recording: Boolean): Notification {
        ensureChannel()
        val title = if (recording) {
            getString(R.string.screen_record_notification_recording)
        } else {
            getString(R.string.screen_record_notification_saved)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(title)
            .setOngoing(recording)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.screen_record_channel_name),
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

    companion object {
        private const val TAG = "ScreenRecordService"
        private const val CHANNEL_ID = "screen_record"
        private const val NOTIFICATION_ID = 4207
        private const val ACTION_START = "com.slideindex.app.action.SCREEN_RECORD_START"
        private const val ACTION_STOP = "com.slideindex.app.action.SCREEN_RECORD_STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"

        @Volatile
        var isRecording: Boolean = false
            private set

        fun start(context: Context, resultCode: Int, data: Intent) {
            val app = context.applicationContext
            val intent = Intent(app, ScreenRecordService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            app.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, ScreenRecordService::class.java).apply {
                action = ACTION_STOP
            }
            app.startService(intent)
        }
    }
}
