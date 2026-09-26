package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.SystemClock
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.overlay.OverlayStatePort
import com.slideindex.app.util.AppProcess
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
    suspend fun recoverAccessibilityBinding(context: Context, settings: AppSettings): AccessibilityRecoverOutcome {
        val appContextForState = context.applicationContext
        // 连接状态的权威值只存在于 :overlay（无障碍实例在那个进程）。
        // 其它进程里 `SlideIndexAccessibilityService.isConnected()` 恒为 false，
        // 直接用它会导致：主进程每次启动都误判「掉线」→ 白白抖断一次绑定（悬浮球/手势短暂消失）
        // → 弹出「边缘手势未连接，请完全关闭后重新打开本应用」，而实际手势一直是好的。
        if (!AppProcess.isOverlay) {
            if (OverlayStatePort.isServiceConnected()) {
                return AccessibilityRecoverOutcome.Connected
            }
            if (!OverlayStatePort.hasServiceState()) {
                // 还没收到过 overlay 的状态广播：状态未知，别乱动（也不要吓用户）。
                return AccessibilityRecoverOutcome.NotNeeded
            }
            // 真的掉线：把恢复请求交给 overlay 执行（重绑/授权只有那边做得了）。
            OverlayStatePort.sendCommand(appContextForState, OverlayStatePort.COMMAND_RECOVER_ACCESSIBILITY)
            return AccessibilityRecoverOutcome.NotNeeded
        }
        if (!settings.serviceEnabled) return AccessibilityRecoverOutcome.NotNeeded
        val appContext = context.applicationContext

        // 1. 若开启了防被杀，在任何门禁前优先自动写回系统设置，避免因系统断开无障碍而判定为未授权
        if (settings.accessibilityKeepAliveEnabled) {
            if (!SecureSettingsHelper.hasWriteSecureSettings(appContext)) {
                SecureSettingsHelper.grantViaShizuku(appContext)
            }
            if (SecureSettingsHelper.hasWriteSecureSettings(appContext)) {
                SecureSettingsHelper.ensureAccessibilityEnabled(appContext)
                // ensureAccessibilityEnabled 只保证「列表里有我」。覆盖安装或被系统杀过之后，
                // 列表里仍然有我、但系统已拒绝重绑（AccessibilityManagerService 记成 crashed），
                // 这种状态必须重写条目才会重新绑定，否则防被杀等于没生效。
                if (!OverlayStatePort.isServiceConnected()) {
                    tryNudgeAccessibilityRebindThrottled(appContext)
                    // 系统 bind 是异步的：等几秒看是否真的连上，否则会把刚发起的重绑误判成失败。
                    awaitAccessibilityConnected()
                }
            }
        }

        // 2. 检查系统是否处于启用状态
        if (!PermissionHelper.isAccessibilityServiceEnabled(appContext)) {
            return AccessibilityRecoverOutcome.NotNeeded
        }

        // 3. 检查当前实例是否已处于活跃连接状态
        if (OverlayStatePort.isServiceConnected()) {
            return AccessibilityRecoverOutcome.Connected
        }

        // 4. 已配置但未连接（假死或未绑定），尝试 nudge 抖动重绑
        if (tryNudgeAccessibilityRebindThrottled(appContext)) {
            Log.i(TAG, "recoverAccessibilityBinding: nudged accessibility rebind")
        } else {
            Log.w(
                TAG,
                "recoverAccessibilityBinding: enabled in settings but not connected; " +
                    "WRITE_SECURE_SETTINGS required for silent rebind",
            )
        }
        awaitAccessibilityConnected()
        return if (OverlayStatePort.isServiceConnected()) {
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
            if (OverlayStatePort.isServiceConnected()) {
                return AccessibilityRecoverOutcome.Connected
            }
            delay(delayMs)
            outcome = recoverAccessibilityBinding(context, settings)
            if (outcome != AccessibilityRecoverOutcome.Failed) return outcome
        }
        return if (OverlayStatePort.isServiceConnected()) {
            AccessibilityRecoverOutcome.Connected
        } else {
            AccessibilityRecoverOutcome.Failed
        }
    }

    private suspend fun tryNudgeAccessibilityRebind(context: Context): Boolean {
        if (OverlayStatePort.isServiceConnected()) return true
        if (!PermissionHelper.isAccessibilityServiceEnabled(context)) return false
        if (!SecureSettingsHelper.hasWriteSecureSettings(context)) {
            SecureSettingsHelper.grantViaShizuku(context)
        }
        if (!SecureSettingsHelper.hasWriteSecureSettings(context)) return false
        return SecureSettingsHelper.nudgeAccessibilityRebind(context)
    }

    private val nudgeThrottle = NudgeThrottle()

    private suspend fun tryNudgeAccessibilityRebindThrottled(context: Context): Boolean {
        if (!nudgeThrottle.beginIfDue(SystemClock.elapsedRealtime())) return false
        return tryNudgeAccessibilityRebind(context)
    }

    /** 重绑写入之后等连接真正建立（系统 bind 是异步的，最多等 [timeoutMs]）。 */
    private suspend fun awaitAccessibilityConnected(
        timeoutMs: Long = REBIND_VERIFY_TIMEOUT_MS,
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            if (OverlayStatePort.isServiceConnected()) return true
            if (SystemClock.elapsedRealtime() >= deadline) return false
            delay(REBIND_VERIFY_POLL_MS)
        }
    }

    private val APP_LAUNCH_REBIND_DELAYS_MS = longArrayOf(800L, 2_000L, 5_000L)

    private const val REBIND_VERIFY_TIMEOUT_MS = 5_000L
    private const val REBIND_VERIFY_POLL_MS = 250L

    private const val TAG = "OverlayServiceLifecycle"
}
