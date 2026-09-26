package com.slideindex.app.otp

/*
 * Portions derived from XposedSmsCode (https://github.com/tianma8023/XposedSmsCode)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.slideindex.app.R
import com.slideindex.app.receiver.OtpCodeCopyReceiver
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.PermissionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 验证码提醒：Toast 提示与系统通知。
 *
 * 对齐上游 XposedSmsCode 的 `ToastAction` 与 `NotifyAction`：
 * - Toast 在提取成功后直接提示验证码，不依赖通知权限；
 * - 通知标题为来源（短信发送方或应用名），正文为验证码，点击复制并自动收起。
 *
 * 与上游的差异：固定通知 ID，新验证码覆盖旧通知而不是堆叠；自动取消与驻留时长
 * 合并为 [AppSettings.otpCodeNotificationRetentionSeconds]（0 = 不自动取消）。
 */
@Singleton
class OtpCodeAlertPresenter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun present(code: String, sourceLabel: String, settings: AppSettings) {
        if (code.isBlank()) return
        if (settings.otpShowCodeToast) {
            showToast(code)
        }
        if (settings.otpCodeNotificationEnabled) {
            postNotification(code, sourceLabel, settings.otpCodeNotificationRetentionSeconds)
        }
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun showToast(code: String) {
        mainHandler.post {
            Toast.makeText(
                context,
                context.getString(R.string.otp_code_toast_text, code),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // 通知前不查权限：本仓库其它通知点同款处理（调用方已保证通知开关可用），失败不致命。
    @android.annotation.SuppressLint("MissingPermission")
    private fun postNotification(code: String, sourceLabel: String, retentionSeconds: Int) {
        if (!PermissionHelper.hasNotificationPermission(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                sourceLabel.ifBlank { context.getString(R.string.otp_code_notification_fallback_title) },
            )
            .setContentText(context.getString(R.string.otp_code_notification_text, code))
            .setContentIntent(copyCodeIntent(code))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply {
                val retentionMs = OtpCodeAlertPolicy.retentionMillis(retentionSeconds)
                if (retentionMs > 0L) setTimeoutAfter(retentionMs)
            }
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun copyCodeIntent(code: String): PendingIntent {
        val intent = Intent(context, OtpCodeCopyReceiver::class.java).apply {
            action = OtpCodeCopyReceiver.ACTION_OTP_COPY_CODE
            putExtra(OtpCodeCopyReceiver.EXTRA_OTP_CODE, code)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel(manager: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.otp_code_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.otp_code_notification_channel_desc)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "otp_code_notification"
        const val NOTIFICATION_ID = 24001
        const val REQUEST_CODE = 24002
    }
}
