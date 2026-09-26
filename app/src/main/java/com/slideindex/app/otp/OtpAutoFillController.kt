package com.slideindex.app.otp

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.slideindex.app.autofill.OtpAutoInputNodeHelper
import com.slideindex.app.settings.AppSettings

/**
 * 无障碍填充（进程内、同步）。
 *
 * 这里以前还有一条"由无障碍事件触发的独立直填"路径：它自己挑时机填、填成功也不回报，
 * 于是会和系统注入同时填，并让记录页显示错误结果（明明填进去了却记成失败）。
 * 现在只保留 [fillNow]，由 [OtpAutoInputOrchestrator] 统一调度与记账：填充只有一条路。
 */
object OtpAutoFillController {
    private const val TAG = "OtpAutoFill"

    data class FillOutcome(
        val success: Boolean,
        val strategy: String,
        val reason: String,
    )

    /** 同步填一次；成功/失败都如实返回。必须在主线程调用，且调用方要先确认无障碍服务已连接。 */
    fun fillNow(
        service: AccessibilityService,
        settings: AppSettings,
        code: String
    ): FillOutcome {
        val root = findAutoFillRoot(service) ?: return FillOutcome(false, "none", "no_active_window")
        return try {
            if (root.packageName?.toString() == service.packageName) {
                FillOutcome(false, "none", "own_package")
            } else {
                val result = OtpAutoInputNodeHelper.performAutoInput(
                    root = root,
                    code = code,
                    autoEnter = settings.otpAutoConfirmEnabled,
                    inputIntervalMs = settings.otpAutoInputIntervalMs.toLong()
                )
                Log.i(
                    TAG,
                    "Accessibility fill: success=${result.success} strategy=${result.strategy} reason=${result.reason}"
                )
                FillOutcome(result.success, result.strategy, result.reason)
            }
        } finally {
            releaseNode(root)
        }
    }

    private fun findAutoFillRoot(service: AccessibilityService): AccessibilityNodeInfo? {
        val active = service.rootInActiveWindow
        if (active != null && active.packageName?.toString() != service.packageName) {
            return active
        }
        active?.let { releaseNode(it) }
        val windows = service.windows ?: return null
        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = window.root ?: continue
            val pkg = root.packageName?.toString()
            if (!pkg.isNullOrBlank() && pkg != service.packageName) {
                return root
            }
            releaseNode(root)
        }
        return null
    }

    private fun releaseNode(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        @Suppress("DEPRECATION")
        node.recycle()
    }
}
