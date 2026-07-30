package com.slideindex.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.slideindex.app.nativeengine.NativeEngineEntryPoint
import com.slideindex.app.nativeengine.NativeEnginePackDownloadController
import com.slideindex.app.nativeengine.NativeEnginePackDownloadPhase
import com.slideindex.app.nativeengine.NativeEnginePackDownloadState
import com.slideindex.app.nativeengine.NativeEnginePackDownloader
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NativeEnginePackDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var lastStartId = -1

    private val downloader: NativeEnginePackDownloader by lazy {
        EntryPointAccessors.fromApplication(applicationContext, NativeEngineEntryPoint::class.java)
            .nativeEnginePackDownloader()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        startForegroundCompat(NativeEnginePackDownloadController.state.value)

        if (job?.isActive == true) {
            val requestedPackId = intent?.getStringExtra(EXTRA_PACK_ID).orEmpty()
            if (requestedPackId.isNotBlank() &&
                requestedPackId != NativeEnginePackDownloadController.activePackId
            ) {
                NativeEnginePackDownloadController.update(
                    NativeEnginePackDownloadState(
                        packId = requestedPackId,
                        phase = NativeEnginePackDownloadPhase.FAILED,
                        errorMessage = "another_download_in_progress",
                    ),
                )
            }
            return START_NOT_STICKY
        }

        val packId = intent?.getStringExtra(EXTRA_PACK_ID).orEmpty()
        val wifiOnly = intent?.getBooleanExtra(EXTRA_WIFI_ONLY, false) == true
        if (packId.isBlank()) {
            stopSelfResult(lastStartId)
            return START_NOT_STICKY
        }

        NativeEnginePackDownloadController.onStart(packId)
        NativeEnginePackDownloadController.update(
            NativeEnginePackDownloadState(
                packId = packId,
                phase = NativeEnginePackDownloadPhase.DOWNLOADING,
            ),
        )

        job = scope.launch {
            downloader.executeDownload(packId, wifiOnly)
            val finalState = NativeEnginePackDownloadController.state.value
            when (finalState?.phase) {
                NativeEnginePackDownloadPhase.READY -> stopForegroundCompat()
                NativeEnginePackDownloadPhase.FAILED,
                NativeEnginePackDownloadPhase.CANCELLED,
                -> stopForegroundCompat()
                else -> stopForegroundCompat()
            }
            NativeEnginePackDownloadController.clearActive()
            stopSelfResult(lastStartId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        NativeEnginePackDownloadController.clearActive()
        super.onDestroy()
    }

    private fun startForegroundCompat(state: NativeEnginePackDownloadState?) {
        val notification = NativeEnginePackDownloadNotifications.buildDownloadNotification(this, state)
        ServiceCompat.startForeground(
            this,
            NativeEnginePackDownloadNotifications.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    companion object {
        private const val EXTRA_PACK_ID = "extra_pack_id"
        private const val EXTRA_WIFI_ONLY = "extra_wifi_only"

        fun start(context: Context, packId: String, wifiOnly: Boolean) {
            val intent = Intent(context, NativeEnginePackDownloadService::class.java).apply {
                putExtra(EXTRA_PACK_ID, packId)
                putExtra(EXTRA_WIFI_ONLY, wifiOnly)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
