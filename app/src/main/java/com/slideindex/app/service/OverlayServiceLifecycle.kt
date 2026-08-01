package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.SecureSettingsHelper
import kotlinx.coroutines.flow.first

/** 根据 [serviceEnabled] 与权限状态启停 [OverlayService]（磁贴、快捷方式等无 Activity 场景复用）。 */
object OverlayServiceLifecycle {
    suspend fun syncFromSettings(context: Context, settingsRepository: SettingsRepository) {
        val appContext = context.applicationContext
        val settings = settingsRepository.settings.first()
        if (settings.accessibilityKeepAliveEnabled &&
            SecureSettingsHelper.hasWriteSecureSettings(appContext) &&
            settings.serviceEnabled
        ) {
            SecureSettingsHelper.ensureAccessibilityEnabled(appContext)
        }
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
}
