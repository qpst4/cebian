package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.SecureSettingsHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** 根据 [serviceEnabled] 与权限状态启停 [OverlayService]（磁贴、快捷方式、开机恢复等场景复用）。 */
object OverlayServiceLifecycle {
    suspend fun syncFromSettings(
        context: Context,
        settingsRepository: SettingsRepository,
        accessibilityRecoverRetries: Boolean = false,
    ) {
        val appContext = context.applicationContext
        val settings = settingsRepository.settings.first()
        val outcome = if (accessibilityRecoverRetries) {
            recoverAccessibilityBindingWithRetries(appContext, settings)
        } else {
            recoverAccessibilityBinding(appContext, settings)
        }
        if (accessibilityRecoverRetries) {
            AccessibilityRecoverNotifier.maybeShowReopenHint(appContext, outcome, settings)
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

    /**
     * 恢复无障碍绑定与静默授权。
     * 若开启了「辅助功能防被杀」且拥有（或可通过 Shizuku 获取）WRITE_SECURE_SETTINGS，
     * 在任何门禁判定前优先自动写回系统设置，确保冷启动与强停后能自动秒开授权；
     * 只要手势总开关开着且系统里已开启无障碍但实际未连接，就尝试 nudge 抖动重绑。
     */
    fun recoverAccessibilityBinding(context: Context, settings: AppSettings): AccessibilityRecoverOutcome {
        if (!settings.serviceEnabled) return AccessibilityRecoverOutcome.NotNeeded
        val appContext = context.applicationContext

        // 1. 若开启了防被杀，在任何门禁前优先自动写回系统设置，避免因系统断开无障碍而判定为未授权
        if (settings.accessibilityKeepAliveEnabled) {
            if (!SecureSettingsHelper.hasWriteSecureSettings(appContext)) {
                SecureSettingsHelper.grantViaShizuku(appContext)
            }
            if (SecureSettingsHelper.hasWriteSecureSettings(appContext)) {
                SecureSettingsHelper.ensureAccessibilityEnabled(appContext)
            }
        }

        // 2. 检查系统是否处于启用状态
        if (!PermissionHelper.isAccessibilityServiceEnabled(appContext)) {
            return AccessibilityRecoverOutcome.NotNeeded
        }

        // 3. 检查当前实例是否已处于活跃连接状态
        if (SlideIndexAccessibilityService.isConnected()) {
            return AccessibilityRecoverOutcome.Connected
        }

        // 4. 已配置但未连接（假死或未绑定），尝试 nudge 抖动重绑
        if (tryNudgeAccessibilityRebind(appContext)) {
            Log.i(TAG, "recoverAccessibilityBinding: nudged accessibility rebind")
        } else {
            Log.w(
                TAG,
                "recoverAccessibilityBinding: enabled in settings but not connected; " +
                    "WRITE_SECURE_SETTINGS required for silent rebind",
            )
        }
        return if (SlideIndexAccessibilityService.isConnected()) {
            AccessibilityRecoverOutcome.Connected
        } else {
            AccessibilityRecoverOutcome.Failed
        }
    }

    /** 覆盖安装或打开 App 后系统 bind 可能延迟，多试几次。 */
    suspend fun recoverAccessibilityBindingWithRetries(
        context: Context,
        settings: AppSettings,
        retryDelaysMs: LongArray = APP_LAUNCH_REBIND_DELAYS_MS,
    ): AccessibilityRecoverOutcome {
        var outcome = recoverAccessibilityBinding(context, settings)
        if (outcome != AccessibilityRecoverOutcome.Failed) return outcome
        for (delayMs in retryDelaysMs) {
            if (SlideIndexAccessibilityService.isConnected()) {
                return AccessibilityRecoverOutcome.Connected
            }
            delay(delayMs)
            outcome = recoverAccessibilityBinding(context, settings)
            if (outcome != AccessibilityRecoverOutcome.Failed) return outcome
        }
        return if (SlideIndexAccessibilityService.isConnected()) {
            AccessibilityRecoverOutcome.Connected
        } else {
            AccessibilityRecoverOutcome.Failed
        }
    }

    private fun tryNudgeAccessibilityRebind(context: Context): Boolean {
        if (SlideIndexAccessibilityService.isConnected()) return true
        if (!PermissionHelper.isAccessibilityServiceEnabled(context)) return false
        if (!SecureSettingsHelper.hasWriteSecureSettings(context)) {
            SecureSettingsHelper.grantViaShizuku(context)
        }
        if (!SecureSettingsHelper.hasWriteSecureSettings(context)) return false
        return SecureSettingsHelper.nudgeAccessibilityRebind(context)
    }

    private val APP_LAUNCH_REBIND_DELAYS_MS = longArrayOf(800L, 2_000L, 5_000L)

    private const val TAG = "OverlayServiceLifecycle"
}
