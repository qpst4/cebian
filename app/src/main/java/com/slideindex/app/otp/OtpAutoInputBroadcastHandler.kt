package com.slideindex.app.otp

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.slideindex.app.autofill.OtpAutoInputBroadcastContract
import com.slideindex.app.autofill.OtpAutoInputNodeHelper

object OtpAutoInputBroadcastHandler {
    private const val TAG = "OtpAutoInputBroadcast"

    fun onReceive(service: AccessibilityService, intent: Intent) {
        if (intent.action != OtpAutoInputBroadcastContract.ACTION_AUTO_INPUT) return
        val request = OtpAutoInputBroadcastContract.readRequest(intent) ?: return
        Log.i(TAG, "Auto-input request via ${service.javaClass.simpleName}, codeLen=${request.code.length}")
        val root = findAutoFillRoot(service) ?: run {
            sendResult(service, request.attemptId, false, "none", "no_active_window")
            return
        }
        try {
            if (root.packageName?.toString() == service.packageName) {
                sendResult(service, request.attemptId, false, "none", "own_package")
                return
            }
            val result = OtpAutoInputNodeHelper.performAutoInput(
                root = root,
                code = request.code,
                autoEnter = request.autoEnter,
                inputIntervalMs = request.inputIntervalMs,
            )
            sendResult(
                service = service,
                attemptId = request.attemptId,
                success = result.success,
                strategy = result.strategy,
                reason = result.reason,
            )
        } finally {
            releaseNode(root)
        }
    }

    private fun sendResult(
        service: AccessibilityService,
        attemptId: Long,
        success: Boolean,
        strategy: String,
        reason: String,
    ) {
        service.sendBroadcast(
            OtpAutoInputBroadcastContract.buildResultIntent(attemptId, success, strategy, reason),
        )
    }

    private fun findAutoFillRoot(service: AccessibilityService): AccessibilityNodeInfo? {
        val active = service.rootInActiveWindow
        if (active != null && active.packageName?.toString() != service.packageName) {
            return active
        }
        active?.let { releaseNode(it) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
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
