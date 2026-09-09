package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.SecureSettingsHelper
import kotlinx.coroutines.flow.first

/** 根据 [serviceEnabled] 与权限状态启停 [OverlayService]（磁贴、快捷方式、开机恢复等场景复用）。 */
object OverlayServiceLifecycle {
    suspend fun syncFromSettings(context: Context, settingsRepository: SettingsRepository) {
        val appContext = context.applicationContext
        val settings = settingsRepository.settings.first()
        recoverAccessibilityBinding(appContext, settings)
        val shouldRun = settings.serviceEnabled &&
            PermissionHelper.isAccessibilityServiceEnabled(appContext) &&
            PermissionHelper.hasNotificationPermission(appContext)
        val serviceIntent = Intent(appContext, OverlayService::class.java)
        if (shouldRun) {
            appContext.startForegroundService(serviceIntent)
        } else {
            appContext.stopService(serviceIntent)
        }
    }

    /** 无障碍被系统关掉或重启后未 bind 时，尝试写回设置并轻推系统重新连接。 */
    fun recoverAccessibilityBinding(context: Context, settings: AppSettings) {
        if (!settings.serviceEnabled) return
        if (!settings.accessibilityKeepAliveEnabled) return
        if (!SecureSettingsHelper.hasWriteSecureSettings(context)) return
        SecureSettingsHelper.ensureAccessibilityEnabled(context)
        if (!SlideIndexAccessibilityService.isConnected() &&
            PermissionHelper.isAccessibilityServiceEnabled(context)
        ) {
            SecureSettingsHelper.nudgeAccessibilityRebind(context)
        }
    }
}
