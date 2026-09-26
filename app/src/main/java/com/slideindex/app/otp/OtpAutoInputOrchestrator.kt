package com.slideindex.app.otp

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.slideindex.app.autofill.OtpAutoInputBroadcastContract
import com.slideindex.app.otp.OtpCaptureDeduplicator
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak") // Pending context held only for in-flight auto-input attempt
object OtpAutoInputOrchestrator {
    private const val TAG = "OtpAutoInput"

    /** 等注入回执的上限。注入已改成 ASYNC，正常情况下 0.5 秒内必回，这里只兜"钩子没响应"。 */
    private const val INJECT_RESULT_TIMEOUT_MS = 2_000L

    /** 无障碍服务没连着时的失败原因（要有专门文案，用户才知道去哪儿开）。 */
    private const val A11Y_NOT_CONNECTED = "a11y_not_connected"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val statsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var resultReceiverRegistered = false
    private var pendingAttemptId: Long? = null
    private var pendingCode: String? = null
    private var pendingSettings: AppSettings? = null
    private var pendingRecordId: String? = null
    private var statsRecorder: (suspend (Boolean, String, String, String?) -> Unit)? = null

    fun setStatsRecorder(recorder: suspend (Boolean, String, String, String?) -> Unit) {
        statsRecorder = recorder
    }

    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != OtpAutoInputBroadcastContract.ACTION_AUTO_INPUT_RESULT) return
            val attemptId = intent.getLongExtra(OtpAutoInputBroadcastContract.EXTRA_ATTEMPT_ID, -1L)
            if (attemptId != pendingAttemptId) return
            val success = intent.getBooleanExtra(OtpAutoInputBroadcastContract.EXTRA_SUCCESS, false)
            val strategy = intent.getStringExtra(OtpAutoInputBroadcastContract.EXTRA_STRATEGY).orEmpty()
            val reason = intent.getStringExtra(OtpAutoInputBroadcastContract.EXTRA_REASON).orEmpty()
            Log.i(TAG, "Auto-input result: success=$success strategy=$strategy reason=$reason")
            if (success) {
                recordStats(true, strategy, reason)
                clearPendingAttempt()
                return
            }
            // 注入失败（含被开关关掉、无按键事件、权限被拒）：立刻走无障碍，不等超时。
            runAccessibilityStep(attemptId, "inject:${reason.ifBlank { "unknown" }}")
        }
    }

    fun requestAutoFill(
        context: Context,
        code: String,
        settings: AppSettings,
        recordId: String? = null
    ) {
        if (!settings.otpAutoInputEnabled) return
        // 前台应用在"不处理的应用"名单里时整条链路跳过（不注入、也不回退无障碍）。
        if (settings.otpBlockedPackages.isNotEmpty()) {
            val foreground = SlideIndexAccessibilityService.currentForegroundPackage()
            if (foreground != null && foreground in settings.otpBlockedPackages) {
                Log.i(TAG, "Skipping auto-fill: $foreground is in the blocked list")
                val recorder = statsRecorder
                if (recorder != null) {
                    statsScope.launch { recorder(false, "none", "blocked_app", recordId) }
                }
                return
            }
        }
        if (!OtpCaptureDeduplicator.tryConsumeAutoFillRequest(code)) {
            Log.d(TAG, "Skipping duplicate auto-fill request")
            return
        }
        val appContext = context.applicationContext
        ensureResultReceiver(appContext)
        val attemptId = SystemClock.elapsedRealtimeNanos()
        pendingAttemptId = attemptId
        pendingCode = code
        pendingSettings = settings
        pendingRecordId = recordId
        val request = OtpAutoInputBroadcastContract.Request(
            code = code,
            autoEnter = settings.otpAutoConfirmEnabled,
            inputIntervalMs = settings.otpAutoInputIntervalMs.toLong(),
            attemptId = attemptId,
            allowSystemInject = settings.otpLsposedSystemInjectEnabled,
            // 只有开了"提取后自动复制"，剪贴板里才有本次验证码，注入失败时才能退化成 Ctrl+V。
            allowPaste = settings.otpCopyToClipboard,
        )
        val delayMs = settings.otpAutoInputDelayMs.coerceAtLeast(0).toLong()
        mainHandler.postDelayed({
            if (pendingAttemptId != attemptId) return@postDelayed
            if (!settings.otpLsposedSystemInjectEnabled) {
                Log.i(TAG, "System inject disabled by setting, going straight to accessibility")
                runAccessibilityStep(attemptId, "inject_disabled")
                return@postDelayed
            }
            appContext.sendOrderedBroadcast(
                OtpAutoInputBroadcastContract.buildRequestIntent(request),
                null
            )
            mainHandler.postDelayed({
                if (pendingAttemptId != attemptId) return@postDelayed
                Log.w(
                    TAG,
                    "No inject result in ${INJECT_RESULT_TIMEOUT_MS}ms, falling back to accessibility",
                )
                runAccessibilityStep(attemptId, "inject_timeout")
            }, INJECT_RESULT_TIMEOUT_MS)
        }, delayMs)
        Log.i(TAG, "Dispatched ordered auto-input broadcast attemptId=$attemptId")
    }

    /**
     * 注入失败/被关闭/超时之后：在进程内同步做一次无障碍填充，成败都由这里记账。
     *
     * 这是整条链路唯一的"回退"入口——事件触发的直填已经删掉，所以不会出现两条路同时填。
     */
    private fun runAccessibilityStep(attemptId: Long, fallbackFrom: String) {
        if (pendingAttemptId != attemptId) return
        val code = pendingCode
        val settings = pendingSettings
        if (code == null || settings == null) {
            clearPendingAttempt()
            return
        }
        val outcome = SlideIndexAccessibilityService.fillOtpNow(code, settings)
        // 等无障碍这几百毫秒里注入的回执可能到了并已结案，这里不能再覆盖结果。
        if (pendingAttemptId != attemptId) return
        if (outcome == null) {
            Log.w(TAG, "Accessibility service not connected, cannot fill ($fallbackFrom)")
            finalizeFailure(A11Y_NOT_CONNECTED)
            return
        }
        if (outcome.success) {
            Log.i(TAG, "Accessibility fill succeeded via ${outcome.strategy} ($fallbackFrom)")
            recordStats(true, outcome.strategy, outcome.reason)
            clearPendingAttempt()
            return
        }
        Log.w(TAG, "Accessibility fill failed: ${outcome.reason} ($fallbackFrom)")
        // 两条路都填不进去：这时无障碍给的原因更贴近事实（例如"没找到可编辑输入框"）。
        finalizeFailure(outcome.reason.ifBlank { fallbackFrom })
    }

    private fun finalizeFailure(reason: String) {
        recordStats(success = false, strategy = "none", reason = reason)
        clearPendingAttempt()
    }

    /** 有填充尝试在途。 */
    fun isAttemptInFlight(): Boolean = pendingAttemptId != null

    private fun clearPendingAttempt() {
        pendingAttemptId = null
        pendingRecordId = null
        pendingCode = null
        pendingSettings = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun ensureResultReceiver(context: Context) {
        if (resultReceiverRegistered) return
        val filter = IntentFilter(OtpAutoInputBroadcastContract.ACTION_AUTO_INPUT_RESULT)
        ContextCompat.registerReceiver(context, resultReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        resultReceiverRegistered = true
    }

    private fun recordStats(success: Boolean, strategy: String, reason: String) {
        val recorder = statsRecorder ?: return
        val recordId = pendingRecordId
        statsScope.launch {
            recorder(success, strategy, reason, recordId)
        }
    }
}
