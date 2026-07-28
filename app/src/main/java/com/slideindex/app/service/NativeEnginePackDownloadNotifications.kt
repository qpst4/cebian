package com.slideindex.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.slideindex.app.MainActivity
import com.slideindex.app.R
import com.slideindex.app.nativeengine.NativeEnginePackDownloadPhase
import com.slideindex.app.nativeengine.NativeEnginePackDownloadState
import kotlin.math.roundToInt

object NativeEnginePackDownloadNotifications {
    private const val CHANNEL_ID = "native_engine_pack_download"
    const val NOTIFICATION_ID = 3002

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.native_engine_download_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    fun buildDownloadNotification(
        context: Context,
        state: NativeEnginePackDownloadState?,
    ): Notification {
        ensureChannel(context)
        val title = context.getString(R.string.native_engine_download_title)
        val text = when (state?.phase) {
            NativeEnginePackDownloadPhase.DOWNLOADING ->
                context.getString(R.string.native_engine_download_progress)
            NativeEnginePackDownloadPhase.VERIFYING ->
                context.getString(R.string.native_engine_download_verifying)
            NativeEnginePackDownloadPhase.EXTRACTING ->
                context.getString(R.string.native_engine_download_extracting)
            NativeEnginePackDownloadPhase.READY ->
                context.getString(R.string.native_engine_download_done)
            NativeEnginePackDownloadPhase.FAILED ->
                context.getString(R.string.native_engine_download_failed)
            NativeEnginePackDownloadPhase.CANCELLED ->
                context.getString(R.string.native_engine_download_cancelled)
            else -> context.getString(R.string.native_engine_download_starting)
        }
        val progress = state?.progress?.times(100f)?.roundToInt()?.coerceIn(0, 100)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (progress != null) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }
}
