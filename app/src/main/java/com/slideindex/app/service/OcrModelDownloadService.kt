package com.slideindex.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.slideindex.app.ocr.OcrEntryPoint
import com.slideindex.app.ocr.OcrModelDownloadController
import com.slideindex.app.ocr.OcrModelDownloadPhase
import com.slideindex.app.ocr.OcrModelDownloadState
import com.slideindex.app.ocr.OcrModelDownloader
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 前台下载 Service：大模型文件后台不被杀，下载状态与设置页 ViewModel 解耦。
 */
class OcrModelDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var lastStartId = -1

    private val downloader: OcrModelDownloader by lazy {
        EntryPointAccessors.fromApplication(applicationContext, OcrEntryPoint::class.java)
            .ocrModelDownloader()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        startForegroundCompat(OcrModelDownloadController.state.value)

        if (job?.isActive == true) {
            val requestedModelId = intent?.getStringExtra(EXTRA_MODEL_ID).orEmpty()
            if (requestedModelId.isNotBlank() &&
                requestedModelId != OcrModelDownloadController.activeModelId
            ) {
                OcrModelDownloadController.update(
                    OcrModelDownloadState(
                        modelId = requestedModelId,
                        phase = OcrModelDownloadPhase.FAILED,
                        errorMessage = "another_download_in_progress",
                    ),
                )
            }
            return START_NOT_STICKY
        }

        val modelId = intent?.getStringExtra(EXTRA_MODEL_ID).orEmpty()
        val wifiOnly = intent?.getBooleanExtra(EXTRA_WIFI_ONLY, false) == true
        if (modelId.isBlank()) {
            stopSelfResult(lastStartId)
            return START_NOT_STICKY
        }

        OcrModelDownloadController.onStart(modelId)
        OcrModelDownloadController.update(
            OcrModelDownloadState(
                modelId = modelId,
                phase = OcrModelDownloadPhase.DOWNLOADING,
            ),
        )

        job = scope.launch {
            var lastNotifiedProgress = -1
            val observer = launch {
                OcrModelDownloadController.state.collectLatest { state ->
                    if (state == null) return@collectLatest
                    val progress = state.progress?.times(100f)?.toInt() ?: -1
                    if (state.phase == OcrModelDownloadPhase.DOWNLOADING &&
                        progress >= 0 &&
                        (progress - lastNotifiedProgress >= NOTIFY_STEP || progress >= 100)
                    ) {
                        lastNotifiedProgress = progress
                        OcrModelDownloadNotifications.notify(this@OcrModelDownloadService, state)
                    }
                    startForegroundCompat(state)
                }
            }

            downloader.executeDownload(modelId, wifiOnly)

            observer.cancel()
            val finalState = OcrModelDownloadController.state.value
            when (finalState?.phase) {
                OcrModelDownloadPhase.READY -> {
                    stopForegroundCompat()
                    OcrModelDownloadNotifications.showDone(this@OcrModelDownloadService)
                }
                OcrModelDownloadPhase.FAILED,
                OcrModelDownloadPhase.CANCELLED,
                -> {
                    stopForegroundCompat()
                    OcrModelDownloadNotifications.showFailed(this@OcrModelDownloadService)
                }
                else -> stopForegroundCompat()
            }
            OcrModelDownloadController.clearActive()
            stopSelfResult(lastStartId)
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        handleTimeout()
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        handleTimeout()
    }

    private fun handleTimeout() {
        job?.cancel()
        val modelId = OcrModelDownloadController.activeModelId
        if (!modelId.isNullOrBlank()) {
            OcrModelDownloadController.update(
                OcrModelDownloadState(
                    modelId = modelId,
                    phase = OcrModelDownloadPhase.FAILED,
                    errorMessage = "download_timeout",
                ),
            )
        }
        OcrModelDownloadController.clearActive()
        stopForegroundCompat()
        stopSelf()
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        OcrModelDownloadController.clearActive()
        super.onDestroy()
    }

    private fun startForegroundCompat(state: OcrModelDownloadState?) {
        val notification = OcrModelDownloadNotifications.buildDownloadNotification(this, state)
        ServiceCompat.startForeground(
            this,
            OcrModelDownloadNotifications.NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    companion object {
        private const val EXTRA_MODEL_ID = "extra_model_id"
        private const val EXTRA_WIFI_ONLY = "extra_wifi_only"
        private const val NOTIFY_STEP = 2

        fun start(context: Context, modelId: String, wifiOnly: Boolean) {
            val intent = Intent(context, OcrModelDownloadService::class.java).apply {
                putExtra(EXTRA_MODEL_ID, modelId)
                putExtra(EXTRA_WIFI_ONLY, wifiOnly)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
