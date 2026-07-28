package com.slideindex.app.update

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DownloadService : Service() {
    companion object {
        private const val EXTRA_VERSION = "extra_version"
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_SIZE = "extra_size"
        private const val NOTIFY_STEP = 2

        fun start(context: Context, version: String, url: String, size: Long) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_VERSION, version)
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_SIZE, size)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var lastStartId = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        startForegroundCompat(DownloadController.flow.value.progress)
        if (job?.isActive == true) {
            return START_NOT_STICKY
        }

        val version = intent?.getStringExtra(EXTRA_VERSION).orEmpty()
        val url = intent?.getStringExtra(EXTRA_URL).orEmpty()
        val size = intent?.getLongExtra(EXTRA_SIZE, 0L) ?: 0L
        if (url.isBlank() || version.isBlank()) {
            stopSelfResult(lastStartId)
            return START_NOT_STICKY
        }

        DownloadController.onStart(version)
        UpdateNotifications.cancelNewVersion(this)

        job = scope.launch {
            val dir = ApkInstaller.updateDir(this@DownloadService)
            val dest = ApkInstaller.apkFile(this@DownloadService, version)
            ApkInstaller.clearOutdatedApks(dir, dest.name)
            var lastNotified = -1
            val success = ApkInstaller.download(url, dest, size) { percent ->
                DownloadController.onProgress(percent)
                if (percent >= 100 || percent - lastNotified >= NOTIFY_STEP) {
                    lastNotified = percent
                    UpdateNotifications.notifyDownloadProgress(this@DownloadService, percent)
                }
            }
            if (success) {
                DownloadController.onFinish()
                stopForegroundCompat()
                UpdateNotifications.showDownloadDone(this@DownloadService, version)
            } else {
                DownloadController.onFailed(version)
                stopForegroundCompat()
                UpdateNotifications.showDownloadFailed(this@DownloadService)
            }
            stopSelfResult(lastStartId)
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        handleTimeout()
    }

    private fun handleTimeout() {
        job?.cancel()
        DownloadController.reset()
        stopForegroundCompat()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun startForegroundCompat(progress: Int) {
        val notification = UpdateNotifications.buildDownloadNotification(this, progress)
        ServiceCompat.startForeground(
            this,
            UpdateNotifications.NOTIFICATION_ID_DOWNLOAD,
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
}
