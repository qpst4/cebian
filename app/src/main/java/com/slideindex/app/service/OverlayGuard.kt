package com.slideindex.app.service

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.slideindex.app.overlay.OverlayStatePort
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.AppProcess
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.SecureSettingsHelper
import kotlinx.coroutines.delay

/**
 * 常驻交互（`:overlay`）看门狗。
 *
 * 多进程之后主进程不再是常驻进程，"App 不开的时候边缘手势没人管"这件事必须由别的机制兜住。
 * 这里做的是**互拉**：由主进程里的 [OverlayWatchdogJobService] 定期巡检，发现 overlay 掉线
 * 或进程疑似已死时：
 *
 * 1. 先 `startForegroundService(OverlayService)` 把 `:overlay` 拉起来；它 onCreate 会自己
 *    走一遍无障碍恢复并立刻回一帧状态；
 * 2. 唤醒无效（进程起不来 / 系统拒绝重绑）时，改写系统无障碍条目强制系统重绑 ——
 *    这一步同时会把承载无障碍服务的 `:overlay` 进程重新拉起来；
 * 3. 仍然不行才发通知提示用户手动处理（可点进系统无障碍设置）。
 *
 * 判定"是不是掉线"一律用 [OverlayStatePort] 的跨进程镜像 + 心跳新鲜度，
 * 绝不用进程内静态（那是本项目踩过的坑：主进程里 `isConnected()` 恒 false，会误报并抖断正常绑定）。
 */
object OverlayGuard {
    enum class GuardResult {
        /** 总开关关着，不需要守护。 */
        Disabled,

        /** 系统设置里没开无障碍，守护不动它。 */
        NotEnabled,

        /** overlay 活着且状态新鲜。 */
        Healthy,

        /** 唤醒 :overlay 之后恢复了。 */
        RecoveredByWake,

        /** 改写系统无障碍条目强制重绑之后恢复了。 */
        ReboundAfterNudge,

        /** 都失败，已发通知提示用户。 */
        Failed,
    }

    suspend fun run(context: Context, settings: AppSettings): GuardResult {
        val appContext = context.applicationContext
        if (AppProcess.isOverlay) return GuardResult.NotEnabled
        if (!settings.serviceEnabled) return GuardResult.Disabled
        if (!PermissionHelper.isAccessibilityServiceEnabled(appContext)) return GuardResult.NotEnabled

        if (OverlayStatePort.isServiceConnected() && OverlayStatePort.isServiceStateFresh()) {
            AccessibilityRecoverNotifier.clearOffline(appContext)
            return GuardResult.Healthy
        }

        // 唤醒 :overlay，并额外要一次状态帧。
        // 只用"心跳"当存活信号太不可靠（心跳 25s 一次，容易在等待窗口里漏掉，
        // 结果把健康的绑定当成掉线去抖断 —— 这正是此前那条误报的翻版）。
        // 命令处理完一定会回一帧状态，所以「收到新状态」= overlay 进程确实活着。
        OverlayServiceLifecycle.wakeOverlayService(appContext)
        OverlayStatePort.sendCommand(appContext, OverlayStatePort.COMMAND_RECOVER_ACCESSIBILITY)
        val overlayAnswered = awaitFreshState(timeoutMs = WAKE_VERIFY_TIMEOUT_MS)
        if (overlayAnswered && OverlayStatePort.isServiceConnected()) {
            Log.w(TAG, "overlay recovered by wake")
            AccessibilityRecoverNotifier.clearOffline(appContext)
            return GuardResult.RecoveredByWake
        }
        if (overlayAnswered && !OverlayStatePort.isServiceConnected()) {
            // overlay 活着、但它自己报告无障碍没连上 —— 这才是真掉线。
            Log.w(TAG, "overlay alive but accessibility offline, forcing rebind")
        }

        // 走到这里只有两种可能：无障碍确实掉线，或 overlay 进程根本没起来（唤醒+命令都没回应）。
        // 两种情况的兜底都是同一个动作：改写系统无障碍条目强制系统重绑，它会顺带把 :overlay 拉起来。
        Log.w(TAG, "overlay still offline after wake, forcing accessibility rebind")
        if (!SecureSettingsHelper.hasWriteSecureSettings(appContext)) {
            SecureSettingsHelper.grantViaShizuku(appContext)
        }
        val nudged = SecureSettingsHelper.hasWriteSecureSettings(appContext) &&
            SecureSettingsHelper.nudgeAccessibilityRebind(appContext)
        val rebound = awaitFreshState(timeoutMs = REBIND_VERIFY_TIMEOUT_MS) &&
            OverlayStatePort.isServiceConnected()
        if (rebound) {
            Log.w(TAG, "overlay recovered by rebind (nudged=$nudged)")
            AccessibilityRecoverNotifier.clearOffline(appContext)
            return if (nudged) GuardResult.ReboundAfterNudge else GuardResult.RecoveredByWake
        }

        Log.e(TAG, "overlay recovery failed (nudged=$nudged)")
        AccessibilityRecoverNotifier.notifyOffline(appContext)
        return GuardResult.Failed
    }

    /**
     * 等到一帧**新鲜的** overlay 状态（而不是历史上收到过的那一帧）。
     *
     * 判断"新鲜"用 [OverlayStatePort.millisSinceServiceState]，阈值比心跳间隔还小，
     * 保证拿到的是本次唤醒之后 overlay 自己报上来的状态。
     */
    private suspend fun awaitFreshState(timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            val ageMs = OverlayStatePort.millisSinceServiceState()
            if (ageMs != null && ageMs <= FRESH_STATE_MAX_AGE_MS) return true
            if (SystemClock.elapsedRealtime() >= deadline) return false
            delay(POLL_INTERVAL_MS)
        }
    }

    private const val WAKE_VERIFY_TIMEOUT_MS = 20_000L
    private const val REBIND_VERIFY_TIMEOUT_MS = 20_000L
    private const val FRESH_STATE_MAX_AGE_MS = 4_000L
    private const val POLL_INTERVAL_MS = 400L
    private const val TAG = "OverlayGuard"
}
