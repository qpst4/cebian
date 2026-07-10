package com.slideindex.app.otp

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.slideindex.app.autofill.OtpAutoInputNodeHelper
import com.slideindex.app.settings.AppSettings
import java.util.concurrent.atomic.AtomicBoolean

object OtpAutoFillController {
    private const val TAG = "OtpAutoFill"
    private const val CODE_TTL_MS = 5 * 60 * 1000L

    @Volatile
    private var pendingCode: String? = null

    @Volatile
    private var pendingPostedAtMs: Long = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val fillInProgress = AtomicBoolean(false)
    private var pendingFillRunnable: Runnable? = null

    fun isFillingActive(): Boolean = fillInProgress.get()

    fun queueCode(code: String) {
        pendingCode = code
        pendingPostedAtMs = System.currentTimeMillis()
        Log.i(TAG, "Queued OTP for auto-fill (${code.length} chars)")
    }

    fun clearPending() {
        pendingCode = null
        pendingPostedAtMs = 0L
    }

    fun hasPendingCode(): Boolean = peekPendingCode() != null

    private fun peekPendingCode(): String? {
        val code = pendingCode ?: return null
        if (System.currentTimeMillis() - pendingPostedAtMs > CODE_TTL_MS) {
            clearPending()
            return null
        }
        return code
    }

    fun scheduleAutoFill(
        service: AccessibilityService,
        settings: AppSettings,
    ) {
        if (!settings.otpAutoInputEnabled) return
        if (peekPendingCode() == null) return
        if (fillInProgress.get()) return

        pendingFillRunnable?.let { mainHandler.removeCallbacks(it) }
        val delayMs = settings.otpAutoInputDelayMs.coerceAtLeast(0).toLong()
        val runnable = Runnable {
            pendingFillRunnable = null
            if (fillInProgress.get()) return@Runnable
            val latestCode = peekPendingCode() ?: return@Runnable
            performAutoFill(service, settings, latestCode)
        }
        pendingFillRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun performAutoFill(
        service: AccessibilityService,
        settings: AppSettings,
        code: String,
    ) {
        if (!fillInProgress.compareAndSet(false, true)) return
        val root = findAutoFillRoot(service)
        if (root == null) {
            finishFill()
            return
        }
        if (root.packageName?.toString() == service.packageName) {
            releaseNode(root)
            finishFill()
            return
        }
        try {
            val result = OtpAutoInputNodeHelper.performAutoInput(
                root = root,
                code = code,
                autoEnter = settings.otpAutoConfirmEnabled,
                inputIntervalMs = settings.otpAutoInputIntervalMs.toLong(),
            )
            if (result.success) {
                pendingCode = null
                Log.i(TAG, "Legacy auto-fill succeeded via ${result.strategy}")
            } else {
                Log.d(TAG, "Legacy auto-fill failed: ${result.reason}")
            }
        } finally {
            releaseNode(root)
            finishFill()
        }
    }

    private fun finishFill() {
        fillInProgress.set(false)
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
